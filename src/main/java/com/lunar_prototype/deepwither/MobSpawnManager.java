package com.lunar_prototype.deepwither;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class MobSpawnManager {

    private final Deepwither plugin;

    // 設定値
    private final String targetWorldName = "Aether"; // 設定ファイルから読み込むべき
    private final long spawnIntervalTicks = 8 * 20L; // 5秒 * 20ティック

    // Key: ティア番号 (1, 2, ...)
    private final Map<Integer, MobTierConfig> mobTierConfigs = new HashMap<>();

    public MobSpawnManager(Deepwither plugin) {
        this.plugin = plugin;

        // 設定の読み込みとタイマーの開始
        loadMobTierConfigs();
        startSpawnScheduler();
    }

    // ----------------------------------------------------
    // --- 設定読み込み (実際は config.yml から読み込む) ---
    // ----------------------------------------------------
    private void loadMobTierConfigs() {
        mobTierConfigs.clear(); // 既存データをクリア

        // config.yml から "mob_spawns" セクションを取得
        ConfigurationSection mobSpawnsSection = plugin.getConfig().getConfigurationSection("mob_spawns");

        if (mobSpawnsSection == null) {
            plugin.getLogger().warning("config.yml に 'mob_spawns' セクションが見つかりません。Mobスポーンは機能しません。");
            return;
        }

        // ティア番号のキー（"1", "2", ...）をループ
        for (String tierKey : mobSpawnsSection.getKeys(false)) {
            try {
                int tierNumber = Integer.parseInt(tierKey);
                ConfigurationSection tierSection = mobSpawnsSection.getConfigurationSection(tierKey);

                if (tierSection == null) continue;

                // regular_mobs リストを取得
                List<String> regularMobs = tierSection.getStringList("regular_mobs");

                // bandit_mobs リストを取得
                List<String> banditMobs = tierSection.getStringList("bandit_mobs");

                if (regularMobs.isEmpty() && banditMobs.isEmpty()) {
                    plugin.getLogger().warning("Tier " + tierKey + " の Mob リストが空です。スキップします。");
                    continue;
                }

                // MobTierConfig インスタンスを作成し、マップに格納
                MobTierConfig config = new MobTierConfig(regularMobs, banditMobs);
                mobTierConfigs.put(tierNumber, config);

                plugin.getLogger().info("Mob Tier " + tierNumber + " の設定をロードしました。");

            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Mob設定の無効なティアキーが見つかりました: " + tierKey + " (整数である必要があります)");
            }
        }
    }

    // ----------------------------------------------------
    // --- スポーンスケジューラ ---
    // ----------------------------------------------------
    private void startSpawnScheduler() {
        new BukkitRunnable() {
            @Override
            public void run() {
                World targetWorld = Bukkit.getWorld(targetWorldName);
                if (targetWorld == null) return;

                // Aetherワールドにいる全プレイヤーを対象に処理
                for (Player player : targetWorld.getPlayers()) {
                    processPlayerSpawn(player);
                }
            }
        }.runTaskTimer(plugin, 20L, spawnIntervalTicks); // 1秒後から開始し、5秒ごとに繰り返す
    }

    // ----------------------------------------------------
    // --- スポーンロジック本体 ---
    // ----------------------------------------------------
    private void processPlayerSpawn(Player player) {
        Location playerLoc = player.getLocation();

        // 1. Safezoneチェック: "safezone" を含むリージョン内ではスポーンしない
        if (isSafeZone(playerLoc)) {
            return;
        }

        // 2. ティア (層) チェック: "t1", "t2" などのリージョン名からティア番号を取得
        int tier = getTierFromLocation(playerLoc);

        if (tier == 0) {
            return; // ティア領域外
        }

        MobTierConfig config = mobTierConfigs.get(tier);
        if (config == null || config.getRegularMobs().isEmpty()) {
            plugin.getLogger().warning("Tier " + tier + " のMob設定が見つかりません。");
            return;
        }

        // 3. 沸かせるMobの決定
        List<String> regularMobs = config.getRegularMobs();
        String mobType = regularMobs.get(plugin.getRandom().nextInt(regularMobs.size()));

        // 4. スポーン位置の決定 (プレイヤーから15ブロック以内)
        Location spawnLoc = getRandomSpawnLocation(playerLoc, 15);

        // 5. スポーン処理

        if (mobType.equalsIgnoreCase("bandit")) {
            // 4-A. Bandit の特別処理: 1-3体をランダムでスポーン
            List<String> banditList = config.getBanditMobs();
            if (banditList.isEmpty()) return;

            // 1, 2, or 3人
            int numBandits = plugin.getRandom().nextInt(3) + 1;
            for (int i = 0; i < numBandits; i++) {
                String banditMobId = banditList.get(plugin.getRandom().nextInt(banditList.size()));

                // MythicMobs APIでスポーン
                spawnMythicMob(banditMobId, spawnLoc);
            }

        } else {
            // 4-B. 通常のMob処理
            spawnMythicMob(mobType, spawnLoc);
        }
    }

    // ----------------------------------------------------
    // --- ヘルパーメソッド ---
    // ----------------------------------------------------

    /**
     * MythicMobsのMobをスポーンさせる
     */
    private void spawnMythicMob(String mobId, Location loc) {
        MythicBukkit.inst().getMobManager().spawnMob(mobId,loc);
    }

    /**
     * 中心点から指定された半径内のランダムな地表位置を取得する
     */
    private Location getRandomSpawnLocation(Location center, int radius) {
        Random random = plugin.getRandom();

        // 1. X/Z座標のランダム化 (センターの周囲 radius 範囲)
        double x = center.getX() + (random.nextDouble() * 2 * radius) - radius;
        double z = center.getZ() + (random.nextDouble() * 2 * radius) - radius;

        World world = center.getWorld();

        // 2. Y座標の探索開始地点を決定
        // プレイヤーのY座標±3ブロックの範囲から探索を開始する
        int startY = (int) Math.min(world.getMaxHeight() - 2, center.getY() + 3);

        // 3. 地下に向かってスポーン可能な位置を探索
        for (int y = startY; y > (world.getMinHeight() + 1); y--) {

            Location checkLoc = new Location(world, x, y, z);

            // プレイヤーのY座標より高すぎる場所はスキップ（任意）
            // if (y > center.getY() + 10) continue;

            // 空間のチェック：スポーン位置(y)とその上が空気ブロックであること
            // Mobがハマらないように、高さ2ブロックの空間を保証
            if (checkLoc.getBlock().getType().isAir() &&
                    checkLoc.clone().add(0, 1, 0).getBlock().getType().isAir()) {

                // 空間が見つかったら、その下のブロックをチェック
                Location blockBelow = checkLoc.clone().subtract(0, 1, 0);

                // スポーン可能条件:
                // 1. スポーン位置とその上が空気である
                // 2. スポーン位置の直下が固体ブロック、または水ではない（落下防止のため固体推奨）
                //    -> ここでは、直下が空気でないことを確認する (足場があることを確認)
                if (!blockBelow.getBlock().getType().isAir() && blockBelow.getBlock().isSolid()) {

                    // スポーン位置を中央に補正し、Found!
                    return new Location(world, x + 0.5, y + 0.0, z + 0.5);
                }
            }
        }

        // 4. スポーンに適した場所が見つからなかった場合、デフォルトとしてプレイヤーの頭上を返すか、スポーンをキャンセルする。
        // ここでは、スポーン位置が見つからなかったという警告をログに出し、スポーンロジック側で null チェックを推奨。
        // 例として、探索範囲で見つからなかった場合は null を返すようにします。
        // plugin.getLogger().info("警告: Mobスポーンに適した地下空間が見つかりませんでした。");
        return null;
    }

    // MobSpawnManagerの内部クラスとして定義
    private static class MobTierConfig {
        private final List<String> regularMobs;
        private final List<String> banditMobs; // Banditの場合にここから選ばれる

        public MobTierConfig(List<String> regularMobs, List<String> banditMobs) {
            this.regularMobs = regularMobs;
            this.banditMobs = banditMobs;
        }

        public List<String> getRegularMobs() { return regularMobs; }
        public List<String> getBanditMobs() { return banditMobs; }
    }

    /**
     * 指定されたLocationが、名前に「safezone」を含むリージョン内にあるかを判定します。
     */
    private boolean isSafeZone(Location loc) {
        // WorldGuardのAPI経由でリージョンコンテナを取得
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();

        // クエリを作成し、現在の場所がどのリージョンに含まれるかを取得
        RegionQuery query = container.createQuery();
        ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(loc));

        // 適用可能なリージョンを全てチェック
        for (ProtectedRegion region : set) {
            // リージョンID（名前）が「safezone」を含んでいるか（大文字小文字を無視）
            if (region.getId().toLowerCase().contains("safezone")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 現在のLocationを含むリージョンIDから、層を示す数字 (t1 -> 1, t2 -> 2) を抽出して返す。
     * Safezoneなどのスポーンを抑制する領域では 0 を返す。
     *
     * @param loc チェックする場所
     * @return 抽出された層の番号 (1以上)。層を示すリージョンがない場合は 0 を返す。
     */
    public int getTierFromLocation(Location loc) {
        // WorldGuardのAPI経由でリージョンコンテナを取得
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();

        // クエリを作成し、現在の場所がどのリージョンに含まれるかを取得
        RegionQuery query = container.createQuery();
        ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(loc));

        int maxTier = 0; // 最も高いティア番号を保持

        // 適用可能なリージョンを全てチェック
        for (ProtectedRegion region : set) {
            String id = region.getId().toLowerCase();

            // 1. スポーンを抑制するリージョンをチェック（Safezone）
            if (id.contains("safezone")) {
                // Safezoneにいる場合は、必ず 0 を返す
                return 0;
            }

            // 2. ティア (t1, t2, ...) の抽出
            // 正規表現: "t"の後に続く数字を抽出
            // 例: "aether_t2_zone" -> 2 を抽出

            // "t" (または "tier") の後に数字が続くパターンを探す
            int tierIndex = id.indexOf("t");
            if (tierIndex != -1 && tierIndex + 1 < id.length()) {
                char nextChar = id.charAt(tierIndex + 1);

                if (Character.isDigit(nextChar)) {
                    // "t" の次の文字から数字としてパースを試みる
                    StringBuilder tierStr = new StringBuilder();
                    int i = tierIndex + 1;
                    while (i < id.length() && Character.isDigit(id.charAt(i))) {
                        tierStr.append(id.charAt(i));
                        i++;
                    }

                    try {
                        int tier = Integer.parseInt(tierStr.toString());
                        if (tier > maxTier) {
                            maxTier = tier; // 最も高いティアを優先
                        }
                    } catch (NumberFormatException ignored) {
                        // 数字が大きすぎる、または不正な形式の場合は無視
                    }
                }
            }
        }

        // Safezoneでなければ、見つかった最も高いティア番号を返す
        return maxTier;
    }
}

