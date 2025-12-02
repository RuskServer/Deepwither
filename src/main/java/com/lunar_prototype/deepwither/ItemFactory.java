package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.quest.GeneratedQuest;
import com.lunar_prototype.deepwither.quest.QuestGenerator;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import io.papermc.paper.datacomponent.item.ItemArmorTrim;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import jdk.jfr.DataAmount;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.Plugin;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.File;
import java.net.URL;
import java.util.*;
import java.util.logging.Level;

public class ItemFactory implements CommandExecutor, TabCompleter {
    private final Plugin plugin;
    private final Map<String, ItemStack> itemMap = new HashMap<>();
    private final NamespacedKey statKey = new NamespacedKey("rpgstats", "statmap");

    public ItemFactory(JavaPlugin plugin) {
        this.plugin = plugin;
        loadAllItems();
        PluginCommand command = plugin.getCommand("giveitem");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
    }

    private void loadAllItems() {
        File itemFolder = new File(plugin.getDataFolder(), "items");
        if (!itemFolder.exists()) itemFolder.mkdirs();

        for (File file : Objects.requireNonNull(itemFolder.listFiles())) {
            if (!file.getName().endsWith(".yml")) continue;
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            Map<String, ItemStack> loaded = ItemLoader.loadItems(config,this);
            itemMap.putAll(loaded);
        }
    }

    public ItemStack applyStatsToItem(ItemStack item, StatMap stats, @Nullable String itemType, @Nullable List<String> flavorText, ItemLoader.RandomStatTracker tracker,@Nullable String rarity,Map<StatType, Double> appliedModifiers) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // Lore 更新（タイプとフレーバーテキスト付き）
        meta.setLore(LoreBuilder.build(stats, false, itemType, flavorText,tracker,rarity,appliedModifiers));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);

        // NBTに保存
        PersistentDataContainer container = meta.getPersistentDataContainer();
        for (StatType type : stats.getAllTypes()) {
            container.set(new NamespacedKey("rpgstats", type.name().toLowerCase() + "_flat"), PersistentDataType.DOUBLE, stats.getFlat(type));
            container.set(new NamespacedKey("rpgstats", type.name().toLowerCase() + "_percent"), PersistentDataType.DOUBLE, stats.getPercent(type));
        }
        item.setItemMeta(meta);

        item.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().addHiddenComponents(DataComponentTypes.ATTRIBUTE_MODIFIERS).build());

        return item;
    }

    public StatMap readStatsFromItem(ItemStack item) {
        StatMap stats = new StatMap();
        if (item == null || !item.hasItemMeta()) return stats;

        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        for (StatType type : StatType.values()) {
            Double flat = container.get(new NamespacedKey("rpgstats", type.name().toLowerCase() + "_flat"), PersistentDataType.DOUBLE);
            Double percent = container.get(new NamespacedKey("rpgstats", type.name().toLowerCase() + "_percent"), PersistentDataType.DOUBLE);
            if (flat != null) stats.setFlat(type, flat);
            if (percent != null) stats.setPercent(type, percent);
        }
        return stats;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // senderがPlayer型であればplayer変数に代入し、そうでなければnull
        Player player = (sender instanceof Player) ? (Player) sender : null;

        // リロード処理 (コンソール/プレイヤー両方から許可)
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            loadAllItems();
            sender.sendMessage("§aアイテム設定をリロードしました。");
            return true;
        }

        // ★ プレイヤー専用の処理はここでチェックし、コンソールからは実行できないようにする
        if (player == null) {
            // コンソールからの実行の場合、アイテム付与とポイント付与・リセットのみを許可する
            // ただし、これらの処理は必ずターゲットプレイヤーを必要とするように修正が必要

            // アイテム付与（コンソール用）
            if (args.length == 2) {
                String id = args[0];
                String targetName = args[1];

                Player targetPlayer = Bukkit.getPlayer(targetName);
                if (targetPlayer == null) {
                    sender.sendMessage("§cプレイヤー §e" + targetName + " §cは見つかりませんでした。");
                    return true;
                }

                // ItemLoader.loadSingleItem(id, this, itemFolder); の部分は適切に修正してください
                // File itemFolder = new File(Deepwither.getInstance().getDataFolder(), "items"); // Deepwither.getInstance()を適切なプラグインインスタンスに
                // ItemStack item = ItemLoader.loadSingleItem(id, /* PluginInstance */, itemFolder);

                // 例として仮のロード処理
                File itemFolder = new File(Deepwither.getInstance().getDataFolder(), "items"); // 適切なプラグインインスタンスを使用
                ItemStack item = ItemLoader.loadSingleItem(id, this, itemFolder); // Deepwither.getInstance()を適切なプラグインインスタンスに

                if (item == null) {
                    sender.sendMessage("§cそのIDのアイテムは存在しません。");
                    return true;
                }

                targetPlayer.getInventory().addItem(item);
                sender.sendMessage("§aアイテム §e" + id + " §aをプレイヤー §e" + targetPlayer.getName() + " §aに付与しました。");
                targetPlayer.sendMessage("§aアイテム §e" + id + " §aを付与されました。");
                return true;
            }

            // addpoints / addskillpoints / resetpoints (コンソールからは未実装のまま、または別途実装が必要)
            // プレイヤーデータ操作は、引数で対象プレイヤーを指定するロジックが必要だが、
            // プレイヤー名を引数として受け取るロジックがまだ不完全なため、ここでは一旦プレイヤー専用とする。

            sender.sendMessage("§c使い方: <id> <プレイヤー名> | reload");
            return true;
        }

        // ステータスリセット処理
        if (args.length == 1 && args[0].equalsIgnoreCase("genquest")) {
            // サーバーのメインスレッドをブロックしないように、LLM呼び出しを非同期タスクで実行
            Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
                try {
                    GeneratedQuest quest = new QuestGenerator().generateQuest(5);

                    // -----------------------------------------------------------------------
                    // 結果の処理をメインスレッドに戻して安全に行う
                    // -----------------------------------------------------------------------
                    Bukkit.getScheduler().runTask(this.plugin, () -> {
                        // クエストの内容をプレイヤーに表示
                        player.sendMessage("§6--- 冒険者ギルドからの緊急依頼 ---");
                        player.sendMessage("§fタイトル：「§b" + quest.getTitle() + "§f」");
                        player.sendMessage("§e[場所]§r " + quest.getLocationDetails().getLlmLocationText());
                        player.sendMessage("§e[目標]§r " + quest.getTargetMobId() + "を" + quest.getRequiredQuantity() + "体");
                        player.sendMessage(" ");

                        // クエスト本文の表示 (改行を考慮して1行ずつ送る)
                        for (String line : quest.getQuestText().split("\n")) {
                            player.sendMessage("§7" + line);
                        }

                        player.sendMessage(" ");
                        player.sendMessage("§a[報酬]§r 200 ゴールド、経験値 500、小さな回復薬 x1");
                        player.sendMessage("§6-------------------------------------");
                    });

                } catch (Exception e) {
                    // LLM通信でエラーが発生した場合、メインスレッドに戻ってエラーメッセージを送信
                    Bukkit.getScheduler().runTask(this.plugin, () -> {
                        player.sendMessage("§c[ギルド受付]§r 依頼の生成中にエラーが発生しました。時間を置いて再度お試しください。");
                        this.plugin.getLogger().log(Level.SEVERE, "LLMクエスト生成中にエラー:", e);
                    });
                }
            });

            return true;
        }

        // ステータスリセット処理
        if (args.length == 1 && args[0].equalsIgnoreCase("resetpoints")) {
            UUID uuid = player.getUniqueId();
            AttributeManager attrManager = Deepwither.getInstance().getAttributeManager();
            PlayerAttributeData data = attrManager.get(uuid);
            if (data != null) {
                int totalAllocated = 0;
                for (StatType type : StatType.values()) {
                    totalAllocated += data.getAllocated(type);
                    data.setAllocated(type, 0);
                }
                data.addPoints(totalAllocated);
                player.sendMessage("§6すべてのステータスポイントをリセットしました。");
            } else {
                player.sendMessage("§cステータスデータが読み込まれていません。");
            }
            return true;
        }

        // ステータスリセット処理
        if (args.length == 1 && args[0].equalsIgnoreCase("skilltreeresets")) {
            UUID uuid = player.getUniqueId();
            Deepwither.getInstance().getSkilltreeManager().resetSkillTree(uuid);
            player.sendMessage("§6すべてのステータスポイントをリセットしました。");
            return true;
        }

        // ステータスポイント付与処理
        if (args.length == 2 && args[0].equalsIgnoreCase("addpoints")) {
            try {
                int amount = Integer.parseInt(args[1]);
                UUID uuid = player.getUniqueId();
                AttributeManager attrManager = Deepwither.getInstance().getAttributeManager();
                PlayerAttributeData data = attrManager.get(uuid);
                if (data != null) {
                    data.addPoints(amount);
                    player.sendMessage("§aステータスポイントを §e" + amount + " §a付与しました。");
                } else {
                    player.sendMessage("§cステータスデータが読み込まれていません。");
                }
            } catch (NumberFormatException e) {
                player.sendMessage("§c数値を入力してください。");
            }
            return true;
        }

        // スキルポイント付与処理
        if (args.length == 2 && args[0].equalsIgnoreCase("addskillpoints")) {
            try {
                int amount = Integer.parseInt(args[1]);
                UUID uuid = player.getUniqueId();
                SkilltreeManager.SkillData data = Deepwither.getInstance().getSkilltreeManager().load(uuid);
                if (data != null) {
                    data.setSkillPoint(data.getSkillPoint() + amount);
                    Deepwither.getInstance().getSkilltreeManager().save(uuid, data);
                    player.sendMessage("§aスキルポイントを §e" + amount + " §a付与しました。");
                } else {
                    player.sendMessage("§cスキルツリーデータが読み込まれていません。");
                }
            } catch (NumberFormatException e) {
                player.sendMessage("§c数値を入力してください。");
            }
            return true;
        }

        String id = args[0];
        Player targetPlayer = player; // デフォルトはコマンド実行者

        // プレイヤー名が指定されているかチェック (args[1]が存在するか)
        if (args.length >= 2) {
            String targetName = args[1];
            // サーバーに接続しているプレイヤーから名前で検索
            Player found = Bukkit.getPlayer(targetName);

            if (found == null) {
                player.sendMessage("§cプレイヤー §e" + targetName + " §cは見つかりませんでした。");
                return true;
            }
            targetPlayer = found;
        }

        File itemFolder = new File(plugin.getDataFolder(), "items");
        ItemStack item = ItemLoader.loadSingleItem(id, this, itemFolder);

        if (item == null) {
            player.sendMessage("§cそのIDのアイテムは存在しません。");
            return true;
        }

        targetPlayer.getInventory().addItem(item);
        targetPlayer.sendMessage("§aアイテム §e" + id + " §aを生成して付与しました。");
        return true;
    }


    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> candidates = new ArrayList<>(itemMap.keySet());
            candidates.add("reload");
            candidates.add("resetpoints");
            candidates.add("addpoints");
            candidates.add("addskillpoints");
            candidates.add("skilltreeresets");
            return candidates.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }

        // addpoints の引数補完（ダミー値）
        if (args.length == 2 && args[0].equalsIgnoreCase("addpoints")) {
            return List.of("10", "25", "50");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("addskillpoints")) {
            return List.of("5", "10", "20");
        }

        return Collections.emptyList();
    }

    public void getCustomItem(Player player,String customitemid){
        File itemFolder = new File(plugin.getDataFolder(), "items");
        ItemStack item = ItemLoader.loadSingleItem(customitemid, this, itemFolder);
        if (item == null) {
            player.sendMessage("§cそのIDのアイテムは存在しません。");
            return;
        }
        player.getInventory().addItem(item);
    }

    public ItemStack getCustomItemStack(String customitemid){
        File itemFolder = new File(plugin.getDataFolder(), "items");
        ItemStack item = ItemLoader.loadSingleItem(customitemid, this, itemFolder);
        return item;
    }

    public ItemStack getCustomCountItemStack(String customitemid,Integer count){
        File itemFolder = new File(plugin.getDataFolder(), "items");
        ItemStack item = ItemLoader.loadSingleItem(customitemid, this, itemFolder);
        item.setAmount(count);
        return item;
    }
}

class ItemLoader {
    private static final Random random = new Random();
    private static final String CUSTOM_ID_KEY = "custom_id";
    public static final NamespacedKey RECOVERY_AMOUNT_KEY = new NamespacedKey(Deepwither.getInstance(), "recovery_amount");
    public static final NamespacedKey COOLDOWN_KEY = new NamespacedKey(Deepwither.getInstance(), "cooldown_seconds");
    public static final NamespacedKey SKILL_CHANCE_KEY = new NamespacedKey(Deepwither.getInstance(), "onhit_chance");
    public static final NamespacedKey SKILL_COOLDOWN_KEY = new NamespacedKey(Deepwither.getInstance(), "onhit_cooldown");
    public static final NamespacedKey SKILL_ID_KEY = new NamespacedKey(Deepwither.getInstance(), "onhit_skillid");

    // 品質判定用Enum
    enum QualityRank {
        COMMON("普通の"), UNCOMMON("§a良質な"), RARE("§b希少な"), LEGENDARY("§6伝説の");

        private final String displayName;

        QualityRank(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static QualityRank fromRatio(double ratio) {
            if (ratio >= 0.9) return LEGENDARY;
            if (ratio >= 0.6) return RARE;
            if (ratio >= 0.3) return UNCOMMON;
            return COMMON;
        }
    }

    // ランダムステータスの理論値＆実際値を管理するクラス
    static class RandomStatTracker {
        private double maxTotal = 0;
        private double actualTotal = 0;

        Map<StatType, Double> weightMap = Map.of(
                StatType.CRIT_CHANCE, 0.5,  // 会心倍率は数値が小さいから低めに重み
                StatType.CRIT_DAMAGE, 0.3
                // 必要に応じて追加
        );

        public void add(StatType type, double base, double spread, double actual) {
            double weight = weightMap.getOrDefault(type, 1.0); // デフォルト1.0
            maxTotal += (base + spread) * weight;
            actualTotal += actual * weight;
        }

        public double getRatio() {
            if (maxTotal == 0) return 0;
            return actualTotal / maxTotal;
        }
    }

    // --- 新規追加: モディファイアー関連 ---

    // レアリティごとのモディファイアー最大個数
    private static final Map<String, Integer> MAX_MODIFIERS_BY_RARITY = Map.of(
            "&f&lコモン", 1,
            "&a&lアンコモン", 2,
            "&b&lレア", 3,
            "&d&lエピック", 4,
            "&6&lレジェンダリー", 6
    );

    // 付与可能なモディファイアーとその重み（StatTypeと値の範囲）
    private static final List<ModifierDefinition> MODIFIER_DEFINITIONS = List.of(
            new ModifierDefinition(StatType.ATTACK_DAMAGE, 1.0, 3.0, 5.0),
            new ModifierDefinition(StatType.DEFENSE, 1.0, 2.0, 5.0),
            new ModifierDefinition(StatType.CRIT_CHANCE, 0.5, 1.0, 3.0),
            new ModifierDefinition(StatType.CRIT_DAMAGE, 0.3, 5.0, 10.0),
            new ModifierDefinition(StatType.MAX_HEALTH, 1.0, 10.0, 20.0),
            new ModifierDefinition(StatType.MAGIC_DAMAGE, 1.0, 3.0, 5.0),
            new ModifierDefinition(StatType.MAGIC_RESIST, 1.0, 2.0, 5.0)
    );

    // モディファイアー定義用ヘルパークラス
    static class ModifierDefinition {
        final StatType type;
        final double weight;
        final double minFlat;
        final double maxFlat;

        ModifierDefinition(StatType type, double weight, double minFlat, double maxFlat) {
            this.type = type;
            this.weight = weight;
            this.minFlat = minFlat;
            this.maxFlat = maxFlat;
        }
    }

    private static EquipmentSlot getSlotFromMaterial(Material material) {
        String name = material.name().toLowerCase(Locale.ROOT);

        // 防具
        if (name.contains("helmet") || name.contains("skull") || name.contains("head")) return EquipmentSlot.HEAD;
        if (name.contains("chestplate")) return EquipmentSlot.CHEST;
        if (name.contains("leggings")) return EquipmentSlot.LEGS;
        if (name.contains("boots")) return EquipmentSlot.FEET;

        // 武器/ツール (メインハンド)
        if (name.contains("sword") || name.contains("axe") || name.contains("pickaxe") || name.contains("hoe") || name.contains("shovel")) return EquipmentSlot.HAND;

        // その他（例: オフハンド）
        if (material == Material.SHIELD) return EquipmentSlot.OFF_HAND;

        return null;
    }

    public static ItemStack loadSingleItem(String id, ItemFactory factory, File itemFolder) {
        for (File file : Objects.requireNonNull(itemFolder.listFiles())) {
            if (!file.getName().endsWith(".yml")) continue;

            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            if (config.contains(id)) {
                Map<String, ItemStack> map = loadItems(config, factory);
                return map.get(id); // 1つだけ生成して返す
            }
        }
        return null;
    }

    public static Map<String, ItemStack> loadItems(YamlConfiguration config, ItemFactory factory) {
        Map<String, ItemStack> result = new HashMap<>();

        for (String key : config.getKeys(false)) {
            try {
                String materialName = config.getString(key + ".material", "STONE");
                Material material = Material.matchMaterial(materialName);
                if (material == null) {
                    System.err.println("Invalid material for item: " + key);
                    continue;
                }

                ItemStack item = new ItemStack(material);
                ItemMeta meta = item.getItemMeta();
                if (meta == null) {
                    System.err.println("ItemMeta is null for: " + key);
                    continue;
                }

                if (material == Material.PLAYER_HEAD) {
                    SkullMeta skullMeta = (SkullMeta) meta;
                    String textureUrl = config.getString(key + ".texture-url");
                    if (textureUrl != null) {
                        try {
                            // Create a custom PlayerProfile with the texture URL
                            PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
                            PlayerTextures textures = profile.getTextures();
                            textures.setSkin(new URL(textureUrl));
                            profile.setTextures(textures);
                            skullMeta.setOwnerProfile(profile);
                        } catch (Exception e) {
                            System.err.println("Failed to set custom texture for player head: " + key);
                            e.printStackTrace();
                        }
                    }
                }

                int custom_model_data = config.getInt(key + ".custom_mode_data");
                if (custom_model_data != 0){
                    meta.setCustomModelData(custom_model_data);
                }

                // ランダムステータスの品質判定用トラッカー初期化
                RandomStatTracker tracker = new RandomStatTracker();

                StatMap stats = new StatMap();
                if (config.isConfigurationSection(key + ".stats")) {
                    for (String statKey : config.getConfigurationSection(key + ".stats").getKeys(false)) {
                        StatType type = StatType.valueOf(statKey.toUpperCase());

                        // flat値処理
                        double flat;
                        if (config.isConfigurationSection(key + ".stats." + statKey + ".flat")) {
                            double base = config.getDouble(key + ".stats." + statKey + ".flat.base", 0);
                            double spread = config.getDouble(key + ".stats." + statKey + ".flat.spread", 0);
                            flat = base + (spread > 0 ? random.nextDouble() * spread : 0);
                            tracker.add(type,base, spread, flat);
                        } else {
                            flat = config.getDouble(key + ".stats." + statKey + ".flat", 0);
                            tracker.add(type,flat, 0, flat);
                        }
                        stats.setFlat(type, flat);

                        // percent値処理
                        double percent;
                        if (config.isConfigurationSection(key + ".stats." + statKey + ".percent")) {
                            double base = config.getDouble(key + ".stats." + statKey + ".percent.base", 0);
                            double spread = config.getDouble(key + ".stats." + statKey + ".percent.spread", 0);
                            percent = base + (spread > 0 ? random.nextDouble() * spread : 0);
                            tracker.add(type,base, spread, percent);
                        } else {
                            percent = config.getDouble(key + ".stats." + statKey + ".percent", 0);
                            tracker.add(type,percent, 0, percent);
                        }
                        stats.setPercent(type, percent);
                    }
                }

                // ----------------------------------------------------
                // --- 新規追加: レアリティに基づくモディファイアー処理 ---
                // ----------------------------------------------------
                boolean disableModifiers = config.getBoolean(key + ".disable_modifiers", false);

                Map<StatType, Double> appliedModifiers = new HashMap<>();
                String rarity = config.getString(key + ".rarity", "コモン"); // レアリティ取得 (大文字化)

                if (!disableModifiers) {
                    int maxModifiers = MAX_MODIFIERS_BY_RARITY.getOrDefault(rarity, 1);
                    int modifiersToApply = random.nextInt(maxModifiers) + 1; // 1個から最大個数までランダムに付与

                    // モディファイアーを抽選・付与
                    Set<StatType> appliedTypes = new HashSet<>();

                    // 重み付き抽選のためのリストを作成
                    List<ModifierDefinition> weightedModifiers = new ArrayList<>();
                    for (ModifierDefinition def : MODIFIER_DEFINITIONS) {
                        for (int j = 0; j < (int) (def.weight * 10); j++) { // 重みを整数に変換してリストに追加
                            weightedModifiers.add(def);
                        }
                    }

                    for (int m = 0; m < modifiersToApply; m++) {
                        if (weightedModifiers.isEmpty()) break;

                        // 重み付きリストからランダムに選択
                        ModifierDefinition selectedDef = weightedModifiers.get(random.nextInt(weightedModifiers.size()));

                        // すでに付与されたStatTypeの場合はスキップ (重複防止)
                        if (appliedTypes.contains(selectedDef.type)) {
                            // 同じタイプのモディファイアーが選ばれた場合は、抽選リストから削除し、mをデクリメントして再試行
                            weightedModifiers.removeIf(def -> def.type == selectedDef.type);
                            m--;
                            continue;
                        }

                        // ランダムな値を生成し、StatMapに追加
                        double modifierValue = selectedDef.minFlat + random.nextDouble() * (selectedDef.maxFlat - selectedDef.minFlat);

                        // 既存のflat値に加算 (モディファイアーは基本的にflat値を想定)
                        stats.setFlat(selectedDef.type, stats.getFlat(selectedDef.type) + modifierValue);
                        appliedModifiers.put(selectedDef.type, modifierValue);

                        // 品質トラッカーには追加しない (モディファイアーは品質判定の対象外とするため)

                        appliedTypes.add(selectedDef.type);
                        // System.out.println(key + "にモディファイアー: " + selectedDef.type + " +" + modifierValue + "を付与"); // デバッグ用

                        // 抽選リストからそのStatTypeをすべて削除し、次に選ばれないようにする
                        weightedModifiers.removeIf(def -> def.type == selectedDef.type);
                    }
                }
                // 品質ランク判定
                QualityRank rank = QualityRank.fromRatio(tracker.getRatio());

                // 名前に品質名をプレフィックス付け
                String originalName = config.getString(key + ".name", key);
                String newName = originalName;
                meta.setDisplayName(newName);
                NamespacedKey pdc_key = new NamespacedKey(Deepwither.getInstance(), CUSTOM_ID_KEY);
                meta.getPersistentDataContainer().set(pdc_key,PersistentDataType.STRING,key);



                item.setItemMeta(meta);

                String itemType = config.getString(key + ".type", null);
                List<String> flavorText = config.getStringList(key + ".flavor");
                // Lore + PDC 書き込みをItemFactory側で処理
                item = factory.applyStatsToItem(item, stats,itemType,flavorText,tracker,rarity,appliedModifiers);

                double recoveryAmount = config.getDouble(key + ".recovery-amount", 0.0);
                int cooldownSeconds = config.getInt(key + ".cooldown-seconds", 0);

                if (recoveryAmount > 0.0) {
                    ItemMeta meta2 = item.getItemMeta();
                    PersistentDataContainer container = meta2.getPersistentDataContainer();

                    // 回復量（例: DOUBLEで保存）
                    container.set(RECOVERY_AMOUNT_KEY, PersistentDataType.DOUBLE, recoveryAmount);

                    // クールダウン（例: INTEGERで保存）
                    if (cooldownSeconds > 0) {
                        container.set(COOLDOWN_KEY, PersistentDataType.INTEGER, cooldownSeconds);
                    }

                    item.setItemMeta(meta2);
                }

                if (config.isConfigurationSection(key + ".on_hit")) {
                    ItemMeta meta3 = item.getItemMeta();
                    PersistentDataContainer container = meta3.getPersistentDataContainer();

                    double chance = config.getDouble(key + ".on_hit.chance", 0.0);
                    int cooldown = config.getInt(key + ".on_hit.cooldown", 0);
                    String skillId = config.getString(key + ".on_hit.mythic_skill_id", null);

                    if (skillId != null) {
                        // PDCに保存
                        container.set(SKILL_CHANCE_KEY, PersistentDataType.DOUBLE, chance);
                        container.set(SKILL_COOLDOWN_KEY, PersistentDataType.INTEGER, cooldown);
                        container.set(SKILL_ID_KEY, PersistentDataType.STRING, skillId);

                        item.setItemMeta(meta3);
                        // System.out.println(key + "にOnHitスキル: " + skillId + " (確率: " + chance + "%) を設定"); // デバッグ用
                    }
                }

                int durability = config.getInt(key + ".durability",0);
                if (durability > 0){
                    item.setData(DataComponentTypes.MAX_DAMAGE,durability);
                }

                String customArmorAssetId = config.getString(key + ".custom_armor");

                if (customArmorAssetId != null) {
                    // 1. 適切な EquipmentSlot を Material から決定する
                    EquipmentSlot slot = getSlotFromMaterial(material);

                    if (slot != null) {

                        NamespacedKey custom_armor_id = NamespacedKey.minecraft(customArmorAssetId);

                        item.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(slot).assetId(custom_armor_id).build());
                    } else {
                        System.err.println("Custom Armor Asset IDが設定されていますが、Material (" + materialName + ") は認識可能な防具/武器ではありません。");
                    }
                }


                String armortrim = config.getString(key + ".armortrim");
                String armortrimmaterial = config.getString(key + ".armortrimmaterial");
                if (armortrim != null && armortrimmaterial != null) {
                    // 1. 文字列をNamespacedKeyに変換
                    NamespacedKey trimKey = NamespacedKey.minecraft(armortrim);
                    NamespacedKey materialKey = NamespacedKey.minecraft(armortrimmaterial);

                    // 2. Registry.TRIM_PATTERNS と Registry.TRIM_MATERIALS からオブジェクトを取得
                    //    注: Registryクラスは Paper 1.20.5+ で非推奨になる可能性があります。
                    TrimPattern trimPattern = Bukkit.getRegistry(TrimPattern.class).get(trimKey);
                    TrimMaterial trimMaterial = Bukkit.getRegistry(TrimMaterial.class).get(materialKey);

                    // 取得したオブジェクトがnullでないことを確認
                    if (trimPattern != null && trimMaterial != null) {
                        // 3. ArmorTrimオブジェクトを作成
                        ArmorTrim armorTrim = new ArmorTrim(trimMaterial, trimPattern);

                        // 4. DataComponentTypes.TRIM に ArmorTrim を直接セット
                        item.setData(DataComponentTypes.TRIM, ItemArmorTrim.itemArmorTrim(armorTrim));

                        // Debugging message to confirm the trim was applied.
                        //System.out.println("Applied armor trim: " + armortrim + " with material: " + armortrimmaterial + " to item " + key);
                    } else {
                        System.err.println("Failed to get trim pattern or material for item: " + key);
                    }
                }

                result.put(key, item);
            } catch (Exception e) {
                System.err.println("Error loading item '" + key + "': " + e.getMessage());
                e.printStackTrace();
            }
        }
        return result;
    }
}
