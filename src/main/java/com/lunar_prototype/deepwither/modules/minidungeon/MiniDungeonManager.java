package com.lunar_prototype.deepwither.modules.minidungeon;

import com.lunar_prototype.deepwither.Deepwither;
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
                        // 攻略中はクールダウンを進めない
                        updateHologram(dungeon);
                        continue;
                    }

                    if (dungeon.getCooldownTimer() < MAX_COOLDOWN) {
                        dungeon.setCooldownTimer(dungeon.getCooldownTimer() + 1);
                    }
                    updateHologram(dungeon);

                    // 接近検知 (半径5ブロック)
                    Location loc = dungeon.getHologramLocation();
                    if (loc != null && loc.getWorld() != null && loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
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
        };
        tickTask.runTaskTimer(plugin, 20L, 20L); // Every 1 second
    }

    private void updateHologram(MiniDungeon dungeon) {
        if (dungeon.getHologramLocation() == null || dungeon.getHologramLocation().getWorld() == null) {
            return;
        }

        TextDisplay display = getOrCreateHologram(dungeon);
        if (display == null) return;

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
                    new Vector3f(), new AxisAngle4f(), new Vector3f(1.2f, 1.2f, 1.2f), new AxisAngle4f()
            ));
        });

        dungeon.setTextDisplayUuid(display.getUniqueId());
        return display;
    }

    private void removeHologram(MiniDungeon dungeon) {
        if (dungeon.getTextDisplayUuid() != null) {
            Entity ent = Bukkit.getEntity(dungeon.getTextDisplayUuid());
            if (ent != null) ent.remove();
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
            progBar = Component.text("進入不可", NamedTextColor.GRAY);
        } else {
            status = Component.text(" [入室可能] ", NamedTextColor.AQUA);
            
            double ratio = (double) dungeon.getCooldownTimer() / MAX_COOLDOWN;
            int percentage = (int) (ratio * 100);
            
            StringBuilder bars = new StringBuilder("[");
            int totalBars = 20;
            int activeBars = (int) (ratio * totalBars);
            for(int i=0; i<totalBars; i++) {
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

    public void startDungeon(org.bukkit.entity.Player player, MiniDungeon dungeon) {
        if (!dungeon.isValid()) {
            player.sendMessage(Component.text("[Minidungeon] 未設定の項目があります！ (ホログラム: " + (dungeon.getHologramLocation() != null) + ", モブ: " + !dungeon.getMobsToSpawn().isEmpty() + ", チェスト: " + (dungeon.getChestLocation() != null) + ", ルート設定: " + (dungeon.getLootTemplate() != null) + ")", NamedTextColor.RED));
            return;
        }

        if (dungeon.isActive()) return;

        com.lunar_prototype.deepwither.modules.mob.framework.CustomMobManager customMobManager = 
                com.lunar_prototype.deepwither.api.DW.get(com.lunar_prototype.deepwither.modules.mob.framework.CustomMobManager.class);
        if (customMobManager == null) {
            player.sendMessage(Component.text("[Minidungeon] 内部エラー: CustomMobManagerが取得できません。", NamedTextColor.RED));
            return;
        }

        double progress = (double) dungeon.getCooldownTimer() / MAX_COOLDOWN;
        if (progress < 0.01) progress = 0.01;

        dungeon.setStartedProgress(progress);
        dungeon.setActive(true);
        dungeon.clearActiveMobs();

        List<Location> spawns = dungeon.getSpawnLocations();
        Location baseSpawnLoc = dungeon.getHologramLocation();
        int spawnIndex = 0;
        
        for (String mobId : dungeon.getMobsToSpawn()) {
            Location loc;
            if (spawns.isEmpty()) {
                // スポーン位置未指定の場合はホログラム位置の周辺(±2ブロック)にランダム配置
                loc = baseSpawnLoc.clone().add(Math.random() * 4 - 2, 0, Math.random() * 4 - 2);
            } else {
                loc = spawns.get(spawnIndex % spawns.size());
            }

            com.lunar_prototype.deepwither.modules.mob.framework.CustomMob spawnedMob = customMobManager.spawnMob(mobId, loc);
            if (spawnedMob != null && spawnedMob.getEntity() != null) {
                dungeon.addActiveMob(spawnedMob.getEntity().getUniqueId());
            }
            spawnIndex++;
        }

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

    public void handleMobDeath(Entity entity) {
        for (MiniDungeon dungeon : getAllDungeons()) {
            if (dungeon.isActive() && dungeon.getActiveMobs().contains(entity.getUniqueId())) {
                dungeon.removeActiveMob(entity.getUniqueId());
                
                if (dungeon.getActiveMobs().isEmpty()) {
                    handleDungeonClear(dungeon);
                }
                break;
            }
        }
    }

    private void handleDungeonClear(MiniDungeon dungeon) {
        dungeon.setActive(false);
        dungeon.setCooldownTimer(0);
        
        com.lunar_prototype.deepwither.loot.LootChestManager lootManager = 
                com.lunar_prototype.deepwither.api.DW.get(com.lunar_prototype.deepwither.loot.LootChestManager.class);
        if (lootManager == null) return;
        
        com.lunar_prototype.deepwither.loot.LootChestTemplate template = lootManager.getTemplates().get(dungeon.getLootTemplate());
        if (template == null) return;

        double progress = dungeon.getStartedProgress();
        int numChests = Math.max(1, (int) Math.round(3 * progress));
        
        Location baseLoc = dungeon.getChestLocation().clone();
        java.util.concurrent.ThreadLocalRandom rand = java.util.concurrent.ThreadLocalRandom.current();

        for (int i = 0; i < numChests; i++) {
            Location cLoc = baseLoc.clone();
            if (i > 0) {
                cLoc.add(rand.nextInt(3) - 1, 0, rand.nextInt(3) - 1);
            }
            
            org.bukkit.block.Block block = cLoc.getBlock();
            block.setType(org.bukkit.Material.CHEST);
            
            if (block.getState() instanceof org.bukkit.block.Chest) {
                org.bukkit.block.Chest chest = (org.bukkit.block.Chest) block.getState();
                com.lunar_prototype.deepwither.modules.minidungeon.util.MiniDungeonLootUtil.fillScaledChest(chest, template, progress);
            }
        }

        if (baseLoc.getWorld() != null) {
            baseLoc.getWorld().playSound(baseLoc, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            baseLoc.getWorld().getNearbyPlayers(baseLoc, 20).forEach(p -> 
                p.sendMessage(Component.text("ミニダンジョンがクリアされました！報酬チェストが出現しました。", NamedTextColor.AQUA))
            );
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
        if (root == null) return;

        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) continue;

            MiniDungeon dungeon = new MiniDungeon(id);
            dungeon.setHologramLocation(sec.getLocation("hologramLocation"));
            dungeon.setChestLocation(sec.getLocation("chestLocation"));
            
            List<Location> locs = (List<Location>) sec.getList("spawnLocations");
            if (locs != null) {
                locs.forEach(dungeon::addSpawnLocation);
            }

            List<String> mobs = sec.getStringList("mobsToSpawn");
            mobs.forEach(dungeon::addMobToSpawn);

            dungeon.setLootTemplate(sec.getString("lootTemplate"));
            dungeon.setCooldownTimer(sec.getInt("cooldownTimer", MAX_COOLDOWN));

            dungeons.put(id, dungeon);
        }
    }
    
    public int getMaxCooldown() {
        return MAX_COOLDOWN;
    }
}
