package com.lunar_prototype.deepwither.modules.minidungeon;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.modules.mob.service.MobConfigService;
import com.lunar_prototype.deepwither.modules.mob.service.MobLevelService;
import com.lunar_prototype.deepwither.modules.mob.util.MobRegionService;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MiniDungeonManager implements IManager {

    private final Deepwither plugin;
    private final Map<String, MiniDungeon> dungeons = new HashMap<>();
    private File dataFile;
    private FileConfiguration config;
    private BukkitRunnable tickTask;

    private static final int MAX_COOLDOWN = 300; // 5 minutes

    public MiniDungeonManager(Deepwither plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        this.dataFile = new File(plugin.getDataFolder(), "minidungeons.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to create minidungeons.yml");
            }
        }
        this.config = YamlConfiguration.loadConfiguration(dataFile);
        load();

        startTickTask();
    }

    @Override
    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        clearAllHolograms();
        save();
    }

    public MiniDungeon getDungeon(String id) {
        return dungeons.get(id);
    }

    public Collection<MiniDungeon> getAllDungeons() {
        return dungeons.values();
    }

    public void createDungeon(String id) {
        dungeons.put(id, new MiniDungeon(id));
    }

    public void removeDungeon(String id) {
        MiniDungeon dungeon = dungeons.remove(id);
        if (dungeon != null && dungeon.getTextDisplayUuid() != null) {
            removeHologram(dungeon);
        }
    }

    // --- Tick and Hologram Area ---

    private void startTickTask() {
        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (MiniDungeon dungeon : dungeons.values()) {
                    if (dungeon.isActive()) {
                        // 攻略中のタイムアウト・放棄チェック
                        checkAbandonment(dungeon);
                        updateHologram(dungeon);
                        continue;
                    }

                    if (dungeon.getCooldownTimer() < MAX_COOLDOWN) {
                        dungeon.setCooldownTimer(dungeon.getCooldownTimer() + 1);
                    }
                    updateHologram(dungeon);

                    // 接近検知 (半径5ブロック)
                    // 帰り道などで連続して湧くのを防ぐため、最低でも30秒(10%ほど)はクールダウンが貯まらないと開始しない
                    if (dungeon.getCooldownTimer() >= 30) {
                        Location loc = dungeon.getHologramLocation();
                        if (loc != null && loc.getWorld() != null
                                && loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                            for (org.bukkit.entity.Player player : loc.getNearbyPlayers(5.0)) {
                                // クリエイティブモード等でのテストも考慮し、スペクテイター以外なら発動
                                if (player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                                    startDungeon(player, dungeon);
                                    break; // 1人見つかれば開始
                                }
                            }
                        }
                    }
                }
            }
        };
        tickTask.runTaskTimer(plugin, 20L, 20L); // Every 1 second
    }

    private void updateHologram(MiniDungeon dungeon) {
        if (dungeon.getHologramLocation() == null || dungeon.getHologramLocation().getWorld() == null) {
            return;
        }

        TextDisplay display = getOrCreateHologram(dungeon);
        if (display == null)
            return;

        display.text(buildHologramText(dungeon));
    }

    private TextDisplay getOrCreateHologram(MiniDungeon dungeon) {
        Location loc = dungeon.getHologramLocation().clone().add(0, 0.5, 0);

        // チャンクがアンロードされている場合は処理をスキップ（無限増殖・チャンクロードの防止）
        if (!loc.isWorldLoaded() || !loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
            return null;
        }

        if (dungeon.getTextDisplayUuid() != null) {
            Entity ent = Bukkit.getEntity(dungeon.getTextDisplayUuid());
            if (ent instanceof TextDisplay) {
                if (ent.isValid()) {
                    return (TextDisplay) ent;
                } else {
                    ent.remove();
                }
            } else if (ent != null) {
                ent.remove();
            }
        }

        // 近くの迷子TextDisplayをクリーンアップ（再起動時の重複対策など）
        for (Entity e : loc.getWorld().getNearbyEntities(loc, 1.0, 1.0, 1.0)) {
            if (e instanceof TextDisplay && e.getScoreboardTags().contains("minidungeon_" + dungeon.getId())) {
                e.remove();
            }
        }

        TextDisplay display = loc.getWorld().spawn(loc, TextDisplay.class, ent -> {
            ent.setBillboard(Billboard.CENTER);
            ent.setDefaultBackground(false);
            ent.setBackgroundColor(org.bukkit.Color.fromARGB(100, 0, 0, 0));
            ent.addScoreboardTag("minidungeon_" + dungeon.getId());
            // Scale
            ent.setTransformation(new Transformation(
                    new Vector3f(), new AxisAngle4f(), new Vector3f(1.2f, 1.2f, 1.2f), new AxisAngle4f()));
        });

        dungeon.setTextDisplayUuid(display.getUniqueId());
        return display;
    }

    private void removeHologram(MiniDungeon dungeon) {
        if (dungeon.getTextDisplayUuid() != null) {
            Entity ent = Bukkit.getEntity(dungeon.getTextDisplayUuid());
            if (ent != null)
                ent.remove();
            dungeon.setTextDisplayUuid(null);
        }
    }

    private void clearAllHolograms() {
        for (MiniDungeon dungeon : dungeons.values()) {
            removeHologram(dungeon);
        }
    }

    private Component buildHologramText(MiniDungeon dungeon) {
        Component title = Component.text("=== ミニダンジョン ===", NamedTextColor.GOLD, TextDecoration.BOLD);
        Component status;
        Component progBar;

        if (dungeon.isActive()) {
            status = Component.text(" [!] 攻略中 [!] ", NamedTextColor.RED);
            progBar = Component.text("討伐数: ", NamedTextColor.GRAY)
                    .append(Component.text(dungeon.getCurrentKills(), NamedTextColor.YELLOW))
                    .append(Component.text(" / ", NamedTextColor.GRAY))
                    .append(Component.text(dungeon.getTotalKillsRequired(), NamedTextColor.GOLD));
        } else {
            status = Component.text(" [入室可能] ", NamedTextColor.AQUA);

            double ratio = (double) dungeon.getCooldownTimer() / MAX_COOLDOWN;
            int percentage = (int) (ratio * 100);

            StringBuilder bars = new StringBuilder("[");
            int totalBars = 20;
            int activeBars = (int) (ratio * totalBars);
            for (int i = 0; i < totalBars; i++) {
                if (i < activeBars) {
                    bars.append("|");
                } else {
                    bars.append(":");
                }
            }
            bars.append("] " + percentage + "%");

            NamedTextColor barColor = NamedTextColor.YELLOW;
            if (percentage >= 100) {
                barColor = NamedTextColor.GREEN;
            } else if (percentage < 30) {
                barColor = NamedTextColor.RED;
            }

            progBar = Component.text(bars.toString(), barColor);
        }

        return Component.text().append(title).appendNewline()
                .append(status).appendNewline()
                .append(progBar).build();
    }

    // --- Core Dungeon Logic ---

    private void checkAbandonment(MiniDungeon dungeon) {
        Location loc = dungeon.getHologramLocation();
        if (loc == null || loc.getWorld() == null)
            return;

        // チャンクがアンロードされている場合＝誰も周辺にいないので放棄扱い
        if (!loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
            forceResetDungeon(dungeon, "チャンクアンロード（誰もいない）");
            return;
        }

        // 半径50ブロック以内に、生存している有効なプレイヤーがいるか確認
        boolean hasPlayer = false;
        for (org.bukkit.entity.Player p : loc.getNearbyPlayers(50.0)) {
            if (p.getGameMode() != org.bukkit.GameMode.SPECTATOR && !p.isDead()) {
                hasPlayer = true;
                break;
            }
        }

        if (!hasPlayer) {
            forceResetDungeon(dungeon, "プレイヤーが離脱/死亡したため");
        }
    }

    private void forceResetDungeon(MiniDungeon dungeon, String reason) {
        dungeon.setActive(false);
        dungeon.setCooldownTimer(0);

        // 残っているモブを掃除
        for (java.util.UUID uuid : dungeon.getActiveMobs()) {
            org.bukkit.entity.Entity ent = org.bukkit.Bukkit.getEntity(uuid);
            if (ent != null) {
                ent.remove();
            }
        }
        dungeon.clearActiveMobs();

        Location loc = dungeon.getHologramLocation();
        if (loc != null && loc.getWorld() != null) {
            plugin.getLogger().info("[Minidungeon] ダンジョン " + dungeon.getId() + " がリセットされました。理由: " + reason);
        }
    }

    public void startDungeon(org.bukkit.entity.Player player, MiniDungeon dungeon) {
        if (!dungeon.isValid()) {
            player.sendMessage(Component
                    .text("[Minidungeon] 未設定の項目があります！ (ホログラム: " + (dungeon.getHologramLocation() != null) + ", モブ: "
                            + !dungeon.getMobsToSpawn().isEmpty() + ", チェスト: " + (dungeon.getChestLocation() != null)
                            + ", ルート設定: " + (dungeon.getLootTemplate() != null) + ")", NamedTextColor.RED));
            return;
        }

        if (dungeon.isActive())
            return;

        double progress = (double) dungeon.getCooldownTimer() / MAX_COOLDOWN;
        if (progress < 0.01)
            progress = 0.01;

        dungeon.setStartedProgress(progress);
        dungeon.setActive(true);
        dungeon.clearActiveMobs();
        dungeon.setCurrentKills(0);
        dungeon.setMobsSpawnedSoFar(0);

        spawnDungeonMobs(dungeon);

        if (dungeon.getActiveMobs().isEmpty()) {
            dungeon.setActive(false);
            return;
        }

        int percent = (int) (progress * 100);
        player.sendMessage(Component.text("ミニダンジョンを開始しました！ (進行度: " + percent + "%)", NamedTextColor.YELLOW));

        if (percent < 100) {
            player.sendMessage(Component.text("完全回復していません。報酬の質と量が抑えられます。", NamedTextColor.GRAY));
        }
    }

    private void spawnDungeonMobs(MiniDungeon dungeon) {
        com.lunar_prototype.deepwither.modules.mob.framework.CustomMobManager customMobManager = com.lunar_prototype.deepwither.api.DW
                .get(com.lunar_prototype.deepwither.modules.mob.framework.CustomMobManager.class);
        if (customMobManager == null) return;

        MobRegionService regionService = com.lunar_prototype.deepwither.api.DW.get(MobRegionService.class);
        MobConfigService configService = com.lunar_prototype.deepwither.api.DW.get(MobConfigService.class);
        MobLevelService levelService = com.lunar_prototype.deepwither.api.DW.get(MobLevelService.class);

        int spawnLevel = 1;
        if (regionService != null && configService != null) {
            int tier = regionService.getTierFromLocation(dungeon.getHologramLocation());
            MobConfigService.MobTierConfig tierConfig = configService.getTierConfig(tier);
            int areaLevel = (tierConfig != null) ? tierConfig.getAreaLevel() : 1;

            // 周囲のプレイヤーの平均レベル等も考慮できるが、ここでは簡易的にホログラム周辺のプレイヤーから取得
            // (startDungeonの引数から引き継ぐのが理想だが、ここでは一旦 1 or 周辺プレイヤーを再取得)
            spawnLevel = 1;
            for (org.bukkit.entity.Player p : dungeon.getHologramLocation().getNearbyPlayers(20.0)) {
                spawnLevel = Math.max(spawnLevel, p.getLevel());
            }
            spawnLevel = Math.min(spawnLevel, areaLevel);
            spawnLevel = Math.max(1, spawnLevel);
        }

        List<Location> spawns = dungeon.getSpawnLocations();
        Location baseSpawnLoc = dungeon.getHologramLocation();
        List<String> mobIds = dungeon.getMobsToSpawn();
        
        int toSpawnCount = mobIds.size();
        // まだスポーンさせていない残りの必要数を確認
        int remainingToKill = dungeon.getTotalKillsRequired() - dungeon.getCurrentKills();
        // 現在のアクティブモブも含めた「生存＋未スポーン」の総数が必要キル数を超えないように
        int currentTotalAccounted = dungeon.getActiveMobs().size() + (dungeon.getTotalKillsRequired() - dungeon.getMobsSpawnedSoFar());
        
        // シンプルに：1ウェーブ分（mobIds.size()）をスポーンさせる。ただし総スポーン数が制限を超えないように。
        for (int i = 0; i < toSpawnCount; i++) {
            if (dungeon.getMobsSpawnedSoFar() >= dungeon.getTotalKillsRequired()) break;
            
            String mobId = mobIds.get(i % mobIds.size());
            Location loc;
            if (spawns.isEmpty()) {
                loc = baseSpawnLoc.clone().add(Math.random() * 4 - 2, 0, Math.random() * 4 - 2);
            } else {
                loc = spawns.get(i % spawns.size());
            }

            com.lunar_prototype.deepwither.modules.mob.framework.CustomMob spawnedMob = customMobManager.spawnMob(mobId, loc);
            if (spawnedMob != null && spawnedMob.getEntity() != null) {
                LivingEntity entity = spawnedMob.getEntity();
                dungeon.addActiveMob(entity.getUniqueId());
                dungeon.setMobsSpawnedSoFar(dungeon.getMobsSpawnedSoFar() + 1);

                if (levelService != null) {
                    String baseName = mobId;
                    if (entity.customName() != null) {
                        baseName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(entity.customName());
                    }
                    levelService.applyLevel(entity, baseName, spawnLevel);
                }
            }
        }
    }

    public void handleMobDeath(Entity entity) {
        for (MiniDungeon dungeon : getAllDungeons()) {
            if (dungeon.isActive() && dungeon.getActiveMobs().contains(entity.getUniqueId())) {
                dungeon.removeActiveMob(entity.getUniqueId());
                dungeon.setCurrentKills(dungeon.getCurrentKills() + 1);

                if (dungeon.getCurrentKills() >= dungeon.getTotalKillsRequired()) {
                    handleDungeonClear(dungeon);
                } else if (dungeon.getActiveMobs().isEmpty()) {
                    // 全滅したがまだキル数が足りない場合、次をスポーン
                    spawnDungeonMobs(dungeon);
                }
                break;
            }
        }
    }

    private void handleDungeonClear(MiniDungeon dungeon) {
        dungeon.setActive(false);
        dungeon.setCooldownTimer(0);

        com.lunar_prototype.deepwither.loot.LootChestManager lootManager = com.lunar_prototype.deepwither.api.DW
                .get(com.lunar_prototype.deepwither.loot.LootChestManager.class);
        if (lootManager == null)
            return;

        com.lunar_prototype.deepwither.loot.LootChestTemplate template = lootManager.getTemplates()
                .get(dungeon.getLootTemplate());
        if (template == null)
            return;

        double progress = dungeon.getStartedProgress();
        int numChests = Math.max(1, (int) Math.round(3 * progress));

        Location baseLoc = dungeon.getChestLocation().clone();
        java.util.concurrent.ThreadLocalRandom rand = java.util.concurrent.ThreadLocalRandom.current();

        for (int i = 0; i < numChests; i++) {
            Location cLoc = baseLoc.clone();
            if (i > 0) {
                boolean placed = false;
                for (int attempt = 0; attempt < 8; attempt++) {
                    int dx = rand.nextInt(3) - 1;
                    int dz = rand.nextInt(3) - 1;
                    if (dx == 0 && dz == 0) continue;

                    org.bukkit.block.Block testBlock = baseLoc.clone().add(dx, 0, dz).getBlock();
                    if (testBlock.getType().isAir() || testBlock.getType() == org.bukkit.Material.WATER) {
                        cLoc.add(dx, 0, dz);
                        placed = true;
                        break;
                    }
                }
                // もし周囲に安全に置ける場所が無ければ、元のチェストの上に積む
                if (!placed) {
                    cLoc.add(0, i, 0);
                }
            }

            org.bukkit.block.Block block = cLoc.getBlock();
            block.setType(org.bukkit.Material.CHEST);

            if (block.getState() instanceof org.bukkit.block.Chest) {
                org.bukkit.block.Chest chest = (org.bukkit.block.Chest) block.getState();
                com.lunar_prototype.deepwither.modules.minidungeon.util.MiniDungeonLootUtil.fillScaledChest(chest,
                        template, progress);
            }
        }

        if (baseLoc.getWorld() != null) {
            baseLoc.getWorld().playSound(baseLoc, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            baseLoc.getWorld().getNearbyPlayers(baseLoc, 20).forEach(
                    p -> p.sendMessage(Component.text("ミニダンジョンがクリアされました！報酬チェストが出現しました。", NamedTextColor.AQUA)));
        }

    }

    // --- Save & Load ---

    public void save() {
        config = new YamlConfiguration();
        for (MiniDungeon dungeon : dungeons.values()) {
            String path = "dungeons." + dungeon.getId();
            config.set(path + ".hologramLocation", dungeon.getHologramLocation());
            config.set(path + ".chestLocation", dungeon.getChestLocation());
            config.set(path + ".spawnLocations", dungeon.getSpawnLocations());
            config.set(path + ".mobsToSpawn", dungeon.getMobsToSpawn());
            config.set(path + ".killCount", dungeon.getTotalKillsRequired());
            config.set(path + ".lootTemplate", dungeon.getLootTemplate());
            // Runtime state の保存 (サーバー再起動時にクールダウンを引き継ぐ場合)
            config.set(path + ".cooldownTimer", dungeon.getCooldownTimer());
        }
        try {
            config.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save minidungeons.yml");
        }
    }

    @SuppressWarnings("unchecked")
    public void load() {
        dungeons.clear();
        ConfigurationSection root = config.getConfigurationSection("dungeons");
        if (root == null)
            return;

        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null)
                continue;

            MiniDungeon dungeon = new MiniDungeon(id);
            dungeon.setHologramLocation(sec.getLocation("hologramLocation"));
            dungeon.setChestLocation(sec.getLocation("chestLocation"));

            List<Location> locs = (List<Location>) sec.getList("spawnLocations");
            if (locs != null) {
                locs.forEach(dungeon::addSpawnLocation);
            }

            List<String> mobs = sec.getStringList("mobsToSpawn");
            mobs.forEach(dungeon::addMobToSpawn);

            dungeon.setTotalKillsRequired(sec.getInt("killCount", mobs.size()));
            dungeon.setLootTemplate(sec.getString("lootTemplate"));
            dungeon.setCooldownTimer(sec.getInt("cooldownTimer", MAX_COOLDOWN));

            dungeons.put(id, dungeon);
        }
    }

    public int getMaxCooldown() {
        return MAX_COOLDOWN;
    }
}
