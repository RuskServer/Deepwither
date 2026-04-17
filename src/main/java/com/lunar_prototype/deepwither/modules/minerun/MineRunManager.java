package com.lunar_prototype.deepwither.modules.minerun;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MineRunManager implements IManager {

    // --- インスタンス共有情報 ---
    public static class MineRunInstance {
        public Location portalLoc;
        public int tier;
        public Location safeSpawn;
        public int remainingSeconds;
        public boolean isAccepting = true; // ポータルが開いていて入場受付中か
    }

    private final Deepwither plugin;
    private World mineRunWorld;
    private final Map<UUID, MineRunSession> sessions = new HashMap<>(); // 個人ロールバック情報
    private final Map<UUID, BossBar> bossBars = new HashMap<>();
    private final Map<Location, MineRunInstance> activeInstances = new HashMap<>(); // ポータルごとのインスタンス
    
    private BukkitTask tickTask;
    private int instanceGridIndex = 0;

    public MineRunManager(Deepwither plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        // ワールド生成 / ロード
        WorldCreator creator = new WorldCreator("minerun_world");
        creator.generator(new MineRunGenerator());
        mineRunWorld = Bukkit.createWorld(creator);
        
        if (mineRunWorld != null) {
            mineRunWorld.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
            mineRunWorld.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
            mineRunWorld.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false);
            
            // 常に嵐（雷雨）にすることで空を暗くし、不気味な霧の演出の土台とする
            mineRunWorld.setStorm(true);
            mineRunWorld.setThundering(true);
            mineRunWorld.setThunderDuration(Integer.MAX_VALUE);
            mineRunWorld.setWeatherDuration(Integer.MAX_VALUE);
            mineRunWorld.setTime(18000); // 深夜に固定
        }

        startTickTask();
    }

    @Override
    public void shutdown() {
        if (tickTask != null) tickTask.cancel();
        
        // 終了時全員強制送還
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (sessions.containsKey(player.getUniqueId())) {
                endRun(player, false);
            }
        }
        sessions.clear();
        bossBars.clear();
    }

    private void startTickTask() {
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int spawnTickCounter = 0;
            
            @Override
            public void run() {
                spawnTickCounter++;
                boolean spawnTick = (spawnTickCounter % 5 == 0); // 1秒ループなので5秒に1度

                // 1. 各共有インスタンスのタイマーを進行
            for (MineRunInstance instance : activeInstances.values()) {
                if (instance.remainingSeconds > 0) {
                    instance.remainingSeconds--;
                }
            }

            // 2. プレイヤーごとの状態を同期
            for (Map.Entry<UUID, MineRunSession> entry : new HashMap<>(sessions).entrySet()) {
                UUID playerId = entry.getKey();
                MineRunSession session = entry.getValue();
                Player player = Bukkit.getPlayer(playerId);

                if (player == null || !player.isOnline()) {
                    // ログアウトした場合は強制失敗
                    endRunOffline(session);
                    sessions.remove(playerId);
                    bossBars.remove(playerId);
                    continue;
                }

                if (!player.getWorld().equals(mineRunWorld)) {
                    // 何らかの理由で別ワールドに出た場合は終了
                    endRun(player, false);
                    continue;
                }

                // 共有インスタンス（ポータル単位）から残り時間を取得
                MineRunInstance sharedInst = session.getSharedInstance();
                if (sharedInst == null) {
                    // 紐づくインスタンスがなければ終了
                    endRun(player, false);
                    continue;
                }

                session.setRemainingSeconds(sharedInst.remainingSeconds);
                updateBossBar(player, session);

                if (sharedInst.remainingSeconds <= 0) {
                    player.sendMessage(Component.text("[MineRun] 時間切れ！", NamedTextColor.RED));
                    endRun(player, false); // ロールバック
                } else {
                    // 約100秒に1回の確率で歪んだ環境音を流す (1秒ごとのループなので1/100)
                    if (plugin.getRandom().nextInt(100) == 0) {
                        MineRunEffects.playDistortedAmbient(player);
                    }

                    if (spawnTick) {
                        handleMobSpawning(player, sharedInst);
                    }
                }
            }
            
            // 時間切れになったインスタンスの掃除は必要に応じて（全員退出で空になる等）
            }
        }, 20L, 20L); // 1秒毎
    }

    private void handleMobSpawning(Player player, MineRunInstance instance) {
        // 周囲のモブ数をカウント
        long nearbyMonsters = player.getNearbyEntities(20, 20, 20).stream()
                .filter(e -> e instanceof org.bukkit.entity.Monster)
                .count();

        if (nearbyMonsters < 6) {
            // スポーンさせる
            org.bukkit.Location spawnLoc = null;
            java.util.Random rand = plugin.getRandom();
            for (int i = 0; i < 5; i++) {
                double angle = rand.nextDouble() * Math.PI * 2;
                double dist = 10 + rand.nextDouble() * 10;
                int ox = (int) (Math.cos(angle) * dist);
                int oz = (int) (Math.sin(angle) * dist);
                org.bukkit.Location checkLoc = player.getLocation().clone().add(ox, 0, oz);
                checkLoc = findSafeSpawn(checkLoc);
                // 距離が離れすぎていないか簡易チェック
                if (checkLoc.distanceSquared(player.getLocation()) < 900) {
                    spawnLoc = checkLoc;
                    break;
                }
            }

            if (spawnLoc != null) {
                com.lunar_prototype.deepwither.modules.mob.service.MobSpawnerService spawnerService = 
                        com.lunar_prototype.deepwither.api.DW.get(com.lunar_prototype.deepwither.modules.mob.service.MobSpawnerService.class);
                if (spawnerService != null) {
                    String[] t1Mobs = {"melee_zombie2", "melee_skeleton"};
                    String[] t2Mobs = {"FireDemon", "melee_zombie2"};
                    String[] t3Mobs = {"IcePilgrim", "FireDemon"};
                    
                    String[] pool = t1Mobs;
                    if (instance.tier >= 3) pool = t3Mobs;
                    else if (instance.tier >= 2) pool = t2Mobs;
                    
                    String selectedMob = pool[rand.nextInt(pool.length)];
                    spawnerService.spawnMythicMob(selectedMob, spawnLoc, instance.tier);
                }
            }
        }
    }

    public boolean isInMineRun(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public MineRunSession getSession(Player player) {
        return sessions.get(player.getUniqueId());
    }

    /**
     * ポータルが出現した時に呼び出され、新しくインスタンス空間を準備する
     */
    public void registerPortal(Location portalBlockLoc, int tier) {
        instanceGridIndex++;
        // 各インスタンスを X軸方向に 5000 ブロックずつ離した位置に生成
        Location dungeonCenter = new Location(mineRunWorld, instanceGridIndex * 5000.0, 50.0, 0.0);
        Location safeSpawn = findSafeSpawn(dungeonCenter);

        MineRunInstance instance = new MineRunInstance();
        instance.portalLoc = portalBlockLoc.getBlock().getLocation();
        instance.tier = tier;
        instance.safeSpawn = safeSpawn;
        instance.remainingSeconds = 300; // 5分固定

        // チェストと脱出ポータルはここで「インスタンス作成時に1回だけ」生成する
        generateEscapePortal(safeSpawn, tier);

        activeInstances.put(instance.portalLoc, instance);
    }

    /**
     * ポータルが消滅（タイムアウト）した際に呼んで入場受付を終了する
     */
    public void unregisterPortal(Location portalBlockLoc) {
        MineRunInstance instance = activeInstances.get(portalBlockLoc.getBlock().getLocation());
        if (instance != null) {
            instance.isAccepting = false; 
            // 既に中に入っているプレイヤーの処理は残るが、新規入場はできなくなる
        }
    }

    /**
     * 登録されたポータルから入場した際に呼ばれ、共有空間に参加させる
     */
    public void joinRun(Player player, Location portalLoc) {
        if (isInMineRun(player)) return;

        MineRunInstance instance = activeInstances.get(portalLoc.getBlock().getLocation());
        if (instance == null || !instance.isAccepting) {
            player.sendMessage(Component.text("[MineRun] 空間が不安定でうまく侵入できませんでした...。", NamedTextColor.GRAY));
            return;
        }

        // 共有インスタンスを参照するセッションを作成
        MineRunSession session = new MineRunSession(player, instance, instance.remainingSeconds);
        sessions.put(player.getUniqueId(), session);

        // ボスバー作成
        BossBar bar = BossBar.bossBar(
                Component.text("制限時間: " + instance.remainingSeconds + "秒", NamedTextColor.RED),
                1.0f,
                BossBar.Color.RED,
                BossBar.Overlay.NOTCHED_10
        );
        player.showBossBar(bar);
        bossBars.put(player.getUniqueId(), bar);

        player.teleport(instance.safeSpawn);
        // クライアント側で時間を深夜に再送（ワールド設定だけでは同期が遅れる場合があるため）
        player.setPlayerTime(18000, false);
        player.sendMessage(Component.text("[MineRun] ネガティブレイヤーに侵入しました。空間の歪みに注意し、脱出を目指してください。", NamedTextColor.DARK_PURPLE));
    }

    /**
     * 脱出ポータルを見つけた時（成功）、あるいは死亡・時間切れ時（失敗）に呼ばれる
     */
    public void endRun(Player player, boolean success) {
        MineRunSession session = sessions.remove(player.getUniqueId());
        BossBar bar = bossBars.remove(player.getUniqueId());
        if (bar != null) player.hideBossBar(bar);

        if (session == null) return;

        if (success) {
            // 成功時は現在のインベントリ（拾ったアイテム含む）を保持したまま帰還
            player.teleport(session.getPreviousLocation());
            player.sendMessage(Component.text("[MineRun] 無事に脱出した！", NamedTextColor.GREEN));
        } else {
            // 失敗時は入場時の状態にロールバック
            session.rollback(player);
            player.sendMessage(Component.text("[MineRun] 失敗... インベントリと記憶が突入前にロールバックしました。", NamedTextColor.DARK_RED));
        }
    }

    private void endRunOffline(MineRunSession session) {
        // オフラインのプレイヤーでも、次ログインしたときにロールバックされるようにDataを保存すべきだが
        // 現時点では簡易的にオンライン戻り時の位置等に影響しないよう何もせず破棄（※本格対応にはDB等への保存が必要）
        // プレイヤーデータ(YAML)等を取り出して操作するか、ログインイベントで復元するなど。
    }

    private void generateEscapePortal(Location startLoc, int tier) {
        // スタート地点から直線距離で 60〜100 ブロックほど離れた場所のどこかにポータルを生成
        java.util.Random rand = plugin.getRandom();
        double angle = rand.nextDouble() * Math.PI * 2;
        double distance = 60 + rand.nextDouble() * 40;

        int offsetX = (int) (Math.cos(angle) * distance);
        int offsetZ = (int) (Math.sin(angle) * distance);
        
        Location approxPortalLoc = startLoc.clone().add(offsetX, 0, offsetZ);
        Location portalLoc = findSafeSpawn(approxPortalLoc); // ポータルが埋まらないように空洞を探す
        
        // 装飾（下地に黒曜石、真ん中にエンドゲートウェイブロック）
        org.bukkit.block.Block base = portalLoc.clone().add(0, -1, 0).getBlock();
        base.setType(Material.OBSIDIAN);
        portalLoc.getBlock().setType(Material.END_GATEWAY);
        portalLoc.clone().add(0, 1, 0).getBlock().setType(Material.AIR);
        
        // 隣にルートチェストを配置
        Location chestLoc = portalLoc.clone().add(1, 0, 0);
        org.bukkit.block.Block chestBlock = chestLoc.getBlock();
        chestBlock.setType(Material.CHEST);
        
        if (chestBlock.getState() instanceof org.bukkit.block.Chest) {
            org.bukkit.block.Chest chest = (org.bukkit.block.Chest) chestBlock.getState();
            String templateId = "minerun_t" + tier; // Tierに応じたチェストの中身
            
            com.lunar_prototype.deepwither.loot.LootChestManager lootManager = 
                    com.lunar_prototype.deepwither.api.DW.get(com.lunar_prototype.deepwither.loot.LootChestManager.class);
            if (lootManager != null && lootManager.getTemplates().containsKey(templateId)) {
                com.lunar_prototype.deepwither.modules.minidungeon.util.MiniDungeonLootUtil.fillScaledChest(chest, lootManager.getTemplates().get(templateId), 1.0);
            }
        }
        
        plugin.getLogger().info("Generated MineRun Escape Portal at: " + portalLoc.getBlockX() + ", " + portalLoc.getBlockY() + ", " + portalLoc.getBlockZ() + " for Tier " + tier);
    }

    private Location findSafeSpawn(Location center) {
        // 地下深くから探索し、最低2ブロックの空間がある場所を探す
        for (int y = 20; y < 80; y++) {
            Location check = center.clone().add(0, y - center.getY(), 0);
            if (check.getBlock().getType().isAir() && check.clone().add(0, 1, 0).getBlock().getType().isAir()) {
                org.bukkit.block.Block floor = check.clone().add(0, -1, 0).getBlock();
                if (floor.getType().isSolid()) {
                    return check.add(0.5, 0, 0.5);
                }
            }
        }
        // なければ岩盤の上に強制的に作る(石を削る)等
        center.getBlock().setType(Material.AIR);
        center.clone().add(0, 1, 0).getBlock().setType(Material.AIR);
        center.clone().add(0, -1, 0).getBlock().setType(Material.STONE);
        return center.add(0.5, 0, 0.5);
    }

    private void updateBossBar(Player player, MineRunSession session) {
        BossBar bar = bossBars.get(player.getUniqueId());
        if (bar == null) return;

        int remain = session.getRemainingSeconds();
        bar.name(Component.text("制限時間: " + remain + "秒", NamedTextColor.RED));
        
        float progress = (float) remain / 300.0f; // max durations should be cached
        if (progress < 0) progress = 0;
        if (progress > 1) progress = 1;
        bar.progress(progress);
    }
}
