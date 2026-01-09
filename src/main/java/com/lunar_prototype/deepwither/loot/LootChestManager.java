package com.lunar_prototype.deepwither.loot;

import com.lunar_prototype.deepwither.Deepwither;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class LootChestManager {

    private final Deepwither plugin;
    private final Random random = ThreadLocalRandom.current();

    // Key: WorldGuardの階層名 (例: "t1"), Value: Templateと重みのリスト
    private final Map<Integer, List<WeightedTemplate>> tieredTemplates = new HashMap<>();

    // Spawened chest tracking (Location -> Expire Task)
    private final Map<Location, BukkitTask> activeLootChests = new ConcurrentHashMap<>();

    // Keep templates accessible for manual placement
    private final Map<String, LootChestTemplate> templates = new HashMap<>();

    // config.ymlからロードする設定値
    private int spawnRadius = 30;
    private long spawnIntervalTicks = 3600L; // 3分

    public LootChestManager(Deepwither plugin) {
        this.plugin = plugin;
        loadConfigs();
        startScheduler();
    }

    // WeightedTemplateクラスを内部クラスとして定義
    private static class WeightedTemplate {
        final LootChestTemplate template;
        final int weight;

        WeightedTemplate(LootChestTemplate template, int weight) {
            this.template = template;
            this.weight = weight;
        }
    }

    /**
     * config.ymlとlootchest.ymlから設定をロードし、テンプレートを構築します。
     */
    public void loadConfigs() {
        // --- 1. lootchest.yml からテンプレートをロード ---
        File lootFile = new File(plugin.getDataFolder(), "lootchest.yml");
        if (!lootFile.exists()) {
            plugin.saveResource("lootchest.yml", false);
        }
        YamlConfiguration lootConfig = YamlConfiguration.loadConfiguration(lootFile);

        templates.clear();
        for (String templateName : lootConfig.getKeys(false)) {
            ConfigurationSection section = lootConfig.getConfigurationSection(templateName);
            if (section != null) {
                templates.put(templateName, LootChestTemplate.loadFromConfig(templateName, section));
            }
        }

        // --- 2. config.yml から階層ごとのスポーン設定をロード ---
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection generalSection = config.getConfigurationSection("loot_chest_settings");
        if (generalSection != null) {
            this.spawnRadius = generalSection.getInt("spawn_radius", 30);
            this.spawnIntervalTicks = generalSection.getLong("spawn_interval_ticks", 3600L);
        }

        tieredTemplates.clear();
        ConfigurationSection hierarchySection = config.getConfigurationSection("loot_chest_settings.hierarchy_chests");
        if (hierarchySection != null) {
            for (String tierKey : hierarchySection.getKeys(false)) {
                try {
                    // キーをintに変換 (例: "1" -> 1, "2" -> 2)
                    int tier = Integer.parseInt(tierKey);
                    List<WeightedTemplate> weightedList = new ArrayList<>();

                    for (Map<?, ?> map : hierarchySection.getMapList(tierKey)) {
                        String templateName = (String) map.get("template");
                        int weight = (int) map.get("weight");

                        LootChestTemplate template = templates.get(templateName);
                        if (template != null) {
                            weightedList.add(new WeightedTemplate(template, weight));
                        } else {
                            plugin.getLogger().warning("LootChest template not found: " + templateName);
                        }
                    }
                    tieredTemplates.put(tier, weightedList);

                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Invalid tier key in config.yml: " + tierKey + ". Must be an integer.");
                }
            }
        }
        plugin.getLogger().info("Loaded " + templates.size() + " LootChest templates and " + tieredTemplates.size()
                + " tier definitions.");
    }

    /**
     * 3分周期でルートチェストのスポーン処理を開始します。
     */
    public void startScheduler() {
        if (spawnIntervalTicks <= 0) {
            plugin.getLogger().warning("Loot chest spawn interval is set to 0 or less. Scheduler not started.");
            return;
        }

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                spawnLootChest(player);
            }
        }, 0L, spawnIntervalTicks);
    }

    /**
     * プレイヤーの周囲にルートチェストをスポーンさせます。
     */
    public void spawnLootChest(Player player) {
        // 1. 基本的なチェック（ゲームモード）
        GameMode mode = player.getGameMode();
        if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
            return;
        }

        // 2. スポーン総数制限
        if (activeLootChests.size() >= 500)
            return;

        // ★追加: 密集対策（プレイヤー密度チェック）
        // 半径32ブロック以内に、自分より「名前順で先」のプレイヤーがいる場合はスキップ
        // これにより、密集地帯ではその中の1人だけがスポーン判定を持つことになります
        double checkRadius = 32.0;
        for (Player nearby : player.getWorld().getPlayers()) {
            if (nearby.equals(player))
                continue;

            // 判定対象のプレイヤーがサバイバル/アドベンチャーであることも考慮
            if (nearby.getGameMode() != GameMode.SURVIVAL && nearby.getGameMode() != GameMode.ADVENTURE)
                continue;

            if (nearby.getLocation().distanceSquared(player.getLocation()) < checkRadius * checkRadius) {
                // 自分よりUUIDが小さい（または名前順が先）プレイヤーがいれば、自分は処理を譲る
                // これで「密集地で1人だけ」が選ばれるようになります
                if (nearby.getUniqueId().compareTo(player.getUniqueId()) < 0) {
                    return;
                }
            }
        }

        // 3. 階層の取得
        int tier = getTierFromLocation(player.getLocation());
        if (tier == 0)
            return;

        // ... 以下、既存のテンプレート選択・位置決定ロジック ...
        LootChestTemplate template = selectChestTemplate(tier);
        if (template == null)
            return;

        Location spawnLoc = findSafeSpawnLocation(player.getLocation());
        if (spawnLoc == null)
            return;

        // 設置処理
        Bukkit.getScheduler().runTask(plugin, () -> {
            Block block = spawnLoc.getBlock();
            block.setType(Material.CHEST);
            Chest chestState = (Chest) block.getState();
            fillChest(chestState, template);
            registerChest(chestState);
            player.sendMessage("§6ルートチェストが近くに出現しました！");
        });
    }

    /**
     * WorldGuardリージョンに基づき、階層を取得します。
     * 
     * @param loc プレイヤーの位置
     * @return 最大のTier番号 (0はSafeZoneまたはTierなし)
     */
    public int getTierFromLocation(Location loc) {
        // プレイヤー提供のコードを使用
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();
        ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(loc));

        int maxTier = 0;

        for (ProtectedRegion region : set) {
            String id = region.getId().toLowerCase();

            if (id.contains("safezone")) {
                return 0;
            }

            int tierIndex = id.indexOf("t");
            if (tierIndex != -1 && tierIndex + 1 < id.length()) {
                char nextChar = id.charAt(tierIndex + 1);

                if (Character.isDigit(nextChar)) {
                    StringBuilder tierStr = new StringBuilder();
                    int i = tierIndex + 1;
                    while (i < id.length() && Character.isDigit(id.charAt(i))) {
                        tierStr.append(id.charAt(i));
                        i++;
                    }

                    try {
                        int tier = Integer.parseInt(tierStr.toString());
                        if (tier > maxTier) {
                            maxTier = tier;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return maxTier;
    }

    /**
     * 階層に基づき、スポーンさせるチェストテンプレートを重み抽選で選択します。
     */
    private LootChestTemplate selectChestTemplate(int tier) {
        List<WeightedTemplate> list = tieredTemplates.get(tier);
        if (list == null || list.isEmpty())
            return null;

        int totalWeight = list.stream().mapToInt(t -> t.weight).sum();
        int roll = random.nextInt(totalWeight);

        for (WeightedTemplate wt : list) {
            roll -= wt.weight;
            if (roll < 0) {
                return wt.template;
            }
        }
        return null;
    }

    /**
     * プレイヤーの周囲にチェストをスポーンさせる安全な場所を探します。
     */
    private Location findSafeSpawnLocation(Location playerLoc) {
        World world = playerLoc.getWorld();
        if (world == null)
            return null;

        for (int i = 0; i < 10; i++) { // 10回試行
            // プレイヤーの周囲にランダムな位置を選択
            double angle = random.nextDouble() * 2 * Math.PI;
            double radius = random.nextDouble() * spawnRadius + 5; // 5ブロック以上離す
            double dx = radius * Math.cos(angle);
            double dz = radius * Math.sin(angle);

            Location attemptLoc = playerLoc.clone().add(dx, 0, dz);

            // 高さはプレイヤーの位置±5ブロック程度でランダムに
            int randomY = random.nextInt(11) - 5; // -5 to +5
            attemptLoc.add(0, randomY, 0);

            Block block = attemptLoc.getBlock();
            Block below = block.getRelative(BlockFace.DOWN);

            // 1. 設置場所が空気であること (水、溶岩なども避けるべきだが、ここでは簡略化)
            // 2. 下にブロックがあること
            if (block.getType() == Material.AIR && below.getType().isSolid()) {
                return block.getLocation();
            }
        }
        return null;
    }

    /**
     * ルートチェストの中身を充填します。（デバッグログ追加版）
     * 
     * @param chestState 設置されたチェストのState
     * @param template   使用するテンプレート
     */
    private void fillChest(Chest chestState, LootChestTemplate template) {
        // 【DEBUG 1】処理開始とテンプレート情報
        plugin.getLogger().info("[LootChest Debug] Start filling chest at: "
                + chestState.getLocation().toVector().toString() + " with template: " + template.getName());

        Inventory inv = chestState.getInventory();
        inv.clear();

        // 抽選するアイテム数を決定
        int min = template.getMinItems();
        int max = template.getMaxItems();
        int itemSlots = random.nextInt(max - min + 1) + min;

        // 【DEBUG 2】抽選アイテム数
        plugin.getLogger()
                .info("[LootChest Debug] Desired item slots: " + itemSlots + " (Min: " + min + ", Max: " + max + ")");

        // アイテム抽選
        for (int i = 0; i < itemSlots; i++) {
            double totalChance = template.getTotalChance();
            double roll = random.nextDouble() * totalChance;
            LootEntry selectedEntry = null;

            // 【DEBUG 3】各イテレーションのロール値
            plugin.getLogger().info("[LootChest Debug] -- Iteration " + (i + 1) + "/" + itemSlots + ". Roll value: "
                    + String.format("%.4f", roll) + " (Total Chance: " + String.format("%.4f", totalChance) + ")");

            double currentRoll = roll; // ロール値を一時変数にコピーして減算に使用
            for (LootEntry entry : template.getEntries()) {
                currentRoll -= entry.getChance();
                if (currentRoll <= 0) {
                    selectedEntry = entry;
                    // 【DEBUG 4】選ばれたエントリー
                    plugin.getLogger().info("[LootChest Debug]   -> Selected Entry: " + selectedEntry.getItemId()
                            + " (Chance: " + String.format("%.4f", entry.getChance()) + ")");
                    break;
                }
            }

            if (selectedEntry != null) {
                // 注意: ここで `createItem(random)` を使用していますが、カスタムアイテムの生成に `ItemFactory` が必要であれば、
                // `LootEntry.createItem` メソッドの引数を見直す必要があります。
                ItemStack item = selectedEntry.createItem(random);

                if (item != null) {
                    // 【DEBUG 5】作成されたアイテム情報
                    plugin.getLogger().info(
                            "[LootChest Debug]   -> Item created: " + item.getType().name() + " x" + item.getAmount());

                    // 空きスロットにランダムに追加
                    int slot = random.nextInt(inv.getSize());
                    if (inv.getItem(slot) == null) {
                        inv.setItem(slot, item);
                        // 【DEBUG 6】アイテム配置成功
                        plugin.getLogger().info("[LootChest Debug]   -> Item successfully placed in slot: " + slot);
                    } else {
                        // 【DEBUG 7】スロット占有によるスキップ
                        plugin.getLogger().warning(
                                "[LootChest Debug]   -> Failed to place item. Slot " + slot + " was already occupied.");
                        // 実際には空きスロットを探すロジックが必要
                    }
                } else {
                    // 【DEBUG 8】アイテム作成失敗
                    plugin.getLogger().warning("[LootChest Debug]   -> Item creation failed for entry: "
                            + selectedEntry.getItemId() + ". Check ItemFactory or Material name in lootchest.yml.");
                }
            } else {
                // 【DEBUG 9】抽選失敗
                plugin.getLogger().warning(
                        "[LootChest Debug]   -> No item entry selected. This means either TotalChance is 0, or roll was too high, or all chances were 0.");
            }
        }

        // 最低一個保証のロジック (一つもアイテムが入らなかった場合)
        if (inv.isEmpty() && !template.getEntries().isEmpty()) {
            // 【DEBUG 10】最低保証ロジック発動
            plugin.getLogger().warning(
                    "[LootChest Debug] Primary looting failed (Inventory is empty). Triggering minimum item guarantee.");

            // 最も確率の高いアイテムを強制的に一つ入れるなど
            LootEntry fallback = template.getEntries().get(random.nextInt(template.getEntries().size()));
            ItemStack item = fallback.createItem(random);

            if (item != null) {
                int slot = random.nextInt(inv.getSize());
                inv.setItem(slot, item);
                // 【DEBUG 11】最低保証アイテム配置成功
                plugin.getLogger().info("[LootChest Debug] Fallback item placed: " + item.getType().name() + " x"
                        + item.getAmount() + " in slot: " + slot);
            } else {
                // 【DEBUG 12】最低保証アイテム作成失敗
                plugin.getLogger().severe(
                        "[LootChest Debug] Fallback item creation failed! Loot chest remains empty. Check fallback entry configuration.");
            }
        } else {
            // 【DEBUG 13】最終チェック
            plugin.getLogger().info(
                    "[LootChest Debug] Final check: Inventory is NOT empty or template entries are empty. Completion successful.");
        }
    }

    /**
     * スポーンしたチェストを追跡リストに追加し、消滅タスクをセットします。
     */
    public void registerChest(Chest chest) {
        Location loc = chest.getLocation();

        // PDCでカスタムチェストであることをマーク (リスナーで使用)
        // NamespacedKey key = new NamespacedKey(plugin, "LOOT_CHEST_ID");
        // chest.getPersistentDataContainer().set(key, PersistentDataType.LONG,
        // System.currentTimeMillis());
        // chest.update();

        // 3分後に実行されるタスク
        BukkitTask expireTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            expireChest(loc);
        }, spawnIntervalTicks);

        activeLootChests.put(loc, expireTask);
    }

    /**
     * ルートチェストをワールドから消滅させます。
     */
    public void expireChest(Location loc) {
        // タスクをキャンセルし、マップから削除
        BukkitTask task = activeLootChests.remove(loc);
        if (task != null) {
            task.cancel();
        }

        // メインスレッドでチェストを削除
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (loc.getBlock().getType() == Material.CHEST) {
                // 中身をドロップさせずにブロックを破壊する
                loc.getBlock().setType(Material.AIR);
                // 誰も取らなかったメッセージ
                loc.getWorld().getPlayers().forEach(p -> {
                    if (p.getLocation().distanceSquared(loc) < 25 * 25) {
                        p.sendMessage("§7[情報] 近くのルートチェストが消滅しました。");
                    }
                });
            }
        });
    }

    /**
     * 外部からチェストが空になったことを通知し、即座に消滅させるためのメソッド。
     * 
     * @param loc 消滅させるチェストのLocation
     */
    public void despawnLootChest(Location loc) {
        expireChest(loc); // タスクキャンセルとブロック破壊を実行
    }

    /**
     * 追跡しているチェストかどうかをチェックします。
     * 
     * @param loc 照合するチェストのLocation
     */
    public boolean isActiveLootChest(Location loc) {
        return activeLootChests.containsKey(loc);
    }

    /**
     * サーバー終了時に呼び出され、ワールド上の全てのルートチェストを削除します。
     * このメソッドは、プラグインの onDisable() から呼び出されることを想定しています。
     */
    public void removeAllLootChests() {
        // 1. スポーン周期タスクを停止
        // プラグインに紐づいた全てのタスクをキャンセルし、周期的なスポーンを防ぎます。
        Bukkit.getScheduler().cancelTasks(plugin);

        plugin.getLogger().info("Removing " + activeLootChests.size() + " active loot chests on shutdown...");

        // 2. 追跡マップをイテレートし、ブロックを削除
        for (Location loc : activeLootChests.keySet()) {
            // 既存の消滅タスクをキャンセル
            BukkitTask task = activeLootChests.get(loc);
            if (task != null) {
                task.cancel();
            }

            // メインスレッドでブロックを空気にする処理を実行 (onDisableはメインスレッドで実行される)
            Block block = loc.getBlock();

            // 念のため、現在もチェストであることを確認
            if (block.getType() == Material.CHEST) {
                block.setType(Material.AIR);
            }
        }

        // 3. 追跡マップをクリア
        activeLootChests.clear();
        plugin.getLogger().info("All loot chests removed successfully.");
    }

    /**
     * ダンジョン用に特定の場所にルートチェストを設置します。
     * 自動消滅は無効化し、ダンジョンインスタンスと共に消えるようにします。
     */
    public void placeDungeonLootChest(Location loc, String templateName) {
        LootChestTemplate template = templates.get(templateName);
        if (template == null) {
            plugin.getLogger().warning("Dungeon Loot Chest Template not found: " + templateName);
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            Block block = loc.getBlock();
            block.setType(Material.CHEST);
            if (block.getState() instanceof Chest chest) {
                fillChest(chest, template);
                // ダンジョンチェストは自動消滅させない (インスタンスごと消えるため)
                // もしパーティクル等が必要なら registerChest(chest) を呼ぶが、expireTaskはキャンセルする必要がある
                // ここでは簡易的に何もしない
            }
        });
    }
}