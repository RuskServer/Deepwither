package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.outpost.OutpostEvent;
import com.lunar_prototype.deepwither.aethelgard.LocationDetails;
import com.lunar_prototype.deepwither.aethelgard.PlayerQuestData;
import com.lunar_prototype.deepwither.aethelgard.PlayerQuestManager;
import com.lunar_prototype.deepwither.aethelgard.QuestProgress;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap; // スレッドセーフなマップを使用
import java.util.stream.Collectors;

public class MobSpawnManager {

    private final Deepwither plugin;
    private final PlayerQuestManager playerQuestManager;
    private final MobLevelManager levelManager;

    // 設定値
    private final String targetWorldName = "Aether";
    private final long spawnIntervalTicks = 7 * 20L;

    private static final int MOB_CAP_PER_PLAYER = 10;
    private static final int COUNT_RADIUS = 20;

    private static final double QUEST_AREA_RADIUS_SQUARED = 20.0 * 20.0;

    private final Map<UUID, Location> spawnLockLocations = new HashMap<>();
    private static final double MOVE_UNLOCK_DISTANCE_SQUARED = 30.0 * 30.0;

    private final Map<UUID, Set<UUID>> spawnedMobsTracker = new ConcurrentHashMap<>();

    private final Map<UUID, String> outpostMobTracker = new ConcurrentHashMap<>();

    private final Set<String> spawnDisabledRegions = ConcurrentHashMap.newKeySet();

    private final Map<Integer, MobTierConfig> mobTierConfigs = new HashMap<>();

    private static final NamespacedKey TRAIT_KEY = new NamespacedKey(Deepwither.getInstance(), "mob_traits");

    public MobSpawnManager(Deepwither plugin, PlayerQuestManager playerQuestManager) {
        this.plugin = plugin;
        this.playerQuestManager = playerQuestManager;
        this.levelManager = new MobLevelManager(plugin);
        plugin.getServer().getPluginManager().registerEvents(levelManager, plugin); // イベント登録

        loadMobTierConfigs();
        startSpawnScheduler();
        startGlobalTraitTicker();
    }

    private enum MobTrait {
        // 初級特性 (Basic)
        BERSERK("§c[狂化]", "Basic"),
        HARDENED("§7[硬化]", "Basic"),
        PIERCING("§e[貫通]", "Basic"),
        SNARING_AURA("§9[鈍足]", "Basic"),

        // 中級特性 (Intermediate)
        MANA_LEECH("§d[魔食]", "Intermediate"),
        DISRUPTIVE("§5[妨害]", "Intermediate"),
        BLINKING("§b[瞬身]", "Intermediate"),
        SUMMONER("§2[召喚]", "Intermediate");

        private final String displayName;
        private final String category;

        MobTrait(String displayName, String category) {
            this.displayName = displayName;
            this.category = category;
        }
    }

    // 特性スポーン確率 (デバッグ用 15%)
    private static final double TRAIT_SPAWN_CHANCE = 0.15;

    private void loadMobTierConfigs() {
        mobTierConfigs.clear(); // 既存データをクリア

        ConfigurationSection mobSpawnsSection = plugin.getConfig().getConfigurationSection("mob_spawns");

        if (mobSpawnsSection == null) {
            plugin.getLogger().warning("config.yml に 'mob_spawns' セクションが見つかりません。Mobスポーンは機能しません。");
            return;
        }

        for (String tierKey : mobSpawnsSection.getKeys(false)) {
            try {
                int tierNumber = Integer.parseInt(tierKey);
                ConfigurationSection tierSection = mobSpawnsSection.getConfigurationSection(tierKey);

                if (tierSection == null)
                    continue;

                int areaLevel = tierSection.getInt("area_level", 999);

                List<String> regularMobs = tierSection.getStringList("regular_mobs");
                List<String> banditMobs = tierSection.getStringList("bandit_mobs");

                if (regularMobs.isEmpty() && banditMobs.isEmpty()) {
                    plugin.getLogger().warning("Tier " + tierKey + " の Mob リストが空です。スキップします。");
                    continue;
                }

                ConfigurationSection bossSection = tierSection.getConfigurationSection("mini_bosses");
                Map<String, Double> miniBosses = new HashMap<>();
                if (bossSection != null) {
                    for (String mobId : bossSection.getKeys(false)) {
                        miniBosses.put(mobId, bossSection.getDouble(mobId));
                    }
                }

                mobTierConfigs.put(tierNumber, new MobTierConfig(areaLevel, regularMobs, banditMobs, miniBosses));

                plugin.getLogger().info("Mob Tier " + tierNumber + " の設定をロードしました。");

            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Mob設定の無効なティアキーが見つかりました: " + tierKey + " (整数である必要があります)");
            }
        }
    }

    private void startSpawnScheduler() {
        new BukkitRunnable() {
            @Override
            public void run() {
                World targetWorld = Bukkit.getWorld(targetWorldName);
                if (targetWorld == null)
                    return;

                for (Player player : targetWorld.getPlayers()) {
                    processPlayerSpawn(player);
                }

                // 【重要】死亡したMobのクリーンアップは別リスナーで処理する必要がある
                cleanupDeadMobs();
            }
        }.runTaskTimer(plugin, 20L, spawnIntervalTicks);
    }

    // ----------------------------------------------------
    // --- スポーンロジック本体 ---
    // ----------------------------------------------------
    private void processPlayerSpawn(Player player) {
        Location playerLoc = player.getLocation();
        UUID playerId = player.getUniqueId();

        // ★追加: プレイヤーのゲームモードをチェック
        GameMode mode = player.getGameMode();
        if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
            return; // サバイバルまたはアドベンチャー以外なら処理を中断
        }

        // -----------------------------------------------------------------
        // ★ 0A: ロック解除チェック (移動したか？)
        // -----------------------------------------------------------------
        Location lockLoc = spawnLockLocations.get(playerId);
        if (lockLoc != null) {
            if (playerLoc.distanceSquared(lockLoc) < MOVE_UNLOCK_DISTANCE_SQUARED) {
                return;
            } else {
                spawnLockLocations.remove(playerId);
            }
        }

        // ★ 0B: 沸き上限チェック & ロック設定
        // countNearbyMobs の代わりに、追跡マップから数を取得する
        int currentMobs = getTrackedMobCount(playerId);

        if (currentMobs >= MOB_CAP_PER_PLAYER) {
            // 上限に達した場合、現在の位置をスポーンロック地点として設定する
            spawnLockLocations.put(playerId, playerLoc);
            return; // スポーンをスキップ
        }

        // 1. Safezoneチェック
        if (isSafeZone(playerLoc)) {
            return;
        }

        if (isOutpostDisabledRegion(playerLoc)) {
            return; // Outpostリージョン内では通常スポーンを完全にスキップ
        }

        // ★ 2. クエストエリア内のスポーンを優先
        if (trySpawnQuestMob(player, playerLoc, currentMobs)) {
            return;
        }

        // 3. ティア (層) チェック
        int tier = getTierFromLocation(playerLoc);

        if (tier == 0) {
            return;
        }

        MobTierConfig config = mobTierConfigs.get(tier);
        if (config == null || config.getRegularMobs().isEmpty()) {
            plugin.getLogger().warning("Tier " + tier + " のMob設定が見つかりません。");
            return;
        }

        // 4. 中ボススポーンチェック
        if (trySpawnMiniBoss(playerLoc, config, playerId)) { // playerId を渡す
            return;
        }

        // 5. 沸かせるMobの決定
        List<String> regularMobs = config.getRegularMobs();
        String mobType = regularMobs.get(plugin.getRandom().nextInt(regularMobs.size()));

        // 6. スポーン位置の決定 (プレイヤーから15ブロック以内)
        Location spawnLoc = getRandomSpawnLocation(playerLoc, 15);
        if (spawnLoc == null)
            return;

        // 7. スポーン処理
        if (mobType.equalsIgnoreCase("bandit")) {
            // Bandit の特別処理: 1-3体をランダムでスポーン (重み付け抽選)
            List<String> banditList = config.getBanditMobs();
            if (banditList.isEmpty())
                return;

            // --- ★ 確率調整のロジックをここに挿入 ★ ---
            int numBandits;
            int weight = plugin.getRandom().nextInt(10); // 0から9までの乱数を生成 (合計10の重み)

            if (weight < 6) {
                numBandits = 1; // 60%の確率
            } else if (weight < 9) { // 6以上9未満 (6, 7, 8)
                numBandits = 2; // 30%の確率
            } else { // 9
                numBandits = 3; // 10%の確率
            }
            // --- ★ 確率調整のロジックここまで ★ ---

            for (int i = 0; i < numBandits; i++) {
                String banditMobId = banditList.get(plugin.getRandom().nextInt(banditList.size()));

                // MythicMobs APIでスポーンし、UUIDを追跡
                UUID mobUuid = spawnMythicMob(banditMobId, spawnLoc, tier);
                trackSpawnedMob(playerId, mobUuid); // ★追跡
            }

        } else {
            // 通常のMob処理
            UUID mobUuid = spawnMythicMob(mobType, spawnLoc, tier);
            trackSpawnedMob(playerId, mobUuid); // ★追跡
        }
    }

    /**
     * ダンジョン用に特定のモブを強制スポーンさせます。
     * リージョン等のチェックを無視し、指定されたレベルを適用します。
     */
    public UUID spawnDungeonMob(Location loc, String mobId, int level) {
        // 1. MythicMobをスポーン
        var activeMob = MythicBukkit.inst().getMobManager().spawnMob(mobId, loc);
        if (activeMob == null || activeMob.getEntity() == null)
            return null;

        org.bukkit.entity.Entity entity = activeMob.getEntity().getBukkitEntity();
        if (!(entity instanceof org.bukkit.entity.LivingEntity livingEntity))
            return entity.getUniqueId();

        // 2. レベル適用
        String mobDisplayName = activeMob.getType().getDisplayName().get();
        if (mobDisplayName == null)
            mobDisplayName = mobId;

        levelManager.applyLevel(livingEntity, mobDisplayName, level);

        // 3. 追跡が必要ならここで追加 (ダンジョンインスタンスが消えるなら不要かもしれないが、念のため)
        // hostId が不明なため、プレイヤー紐付けはしない、もしくは "DUNGEON" のようなダミーキーを使う?
        // とりあえず追跡せずに返す
        return livingEntity.getUniqueId();
    }

    /**
     * 特定のリージョン内での通常スポーンを無効化します。
     */
    public void disableNormalSpawning(String regionId) {
        spawnDisabledRegions.add(regionId.toLowerCase());
    }

    /**
     * 特定のリージョン内での通常スポーンを再度有効化します。
     */
    public void enableNormalSpawning(String regionId) {
        spawnDisabledRegions.remove(regionId.toLowerCase());
    }

    /**
     * 指定されたMobがOutpost Mobである場合に、そのOutpostのリージョンIDを返します。
     * (EventListenerで使用)
     */
    public String getMobOutpostId(Entity mob) {
        return outpostMobTracker.get(mob.getUniqueId());
    }

    /**
     * Outpost Mobの追跡を解除します。
     * (OutpostEventのmobDefeated()から呼ばれることを想定)
     */
    public void untrackOutpostMob(UUID mobUuid) {
        outpostMobTracker.remove(mobUuid);
    }

    // ----------------------------------------------------
    // --- ヘルパーメソッド (Mob追跡関連) ---
    // ----------------------------------------------------

    /**
     * プレイヤーがいるLocationが、現在Outpostイベントで通常スポーンが無効化されているリージョン内にあるかチェックします。
     */
    private boolean isOutpostDisabledRegion(Location loc) {
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();

        ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(loc));

        for (ProtectedRegion region : set) {
            if (spawnDisabledRegions.contains(region.getId().toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 追跡マップから、特定のプレイヤーのためにスポーンしたMobの数を返す。
     */
    private int getTrackedMobCount(UUID playerId) {
        return spawnedMobsTracker.getOrDefault(playerId, Collections.emptySet()).size();
    }

    /**
     * MobのUUIDをスポーン追跡マップに追加する。
     */
    private void trackSpawnedMob(UUID playerId, UUID mobUuid) {
        if (mobUuid == null)
            return;
        spawnedMobsTracker.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet()).add(mobUuid);
    }

    /**
     * MobのUUIDをスポーン追跡マップから削除する。
     * これは MobDeathEvent リスナーから呼び出されるべきです。
     */
    public void untrackMob(UUID mobUuid) {
        // 全てのプレイヤーのSetをチェックし、該当するUUIDを削除する
        for (Set<UUID> mobUuids : spawnedMobsTracker.values()) {
            mobUuids.remove(mobUuid);
        }
    }

    /**
     * サーバー上に存在しないMobのUUIDを追跡マップから削除し、メモリを解放する。
     * このメソッドは、Schedulerで定期的に呼び出されます。
     */
    private void cleanupDeadMobs() {
        for (Map.Entry<UUID, Set<UUID>> entry : spawnedMobsTracker.entrySet()) {
            Set<UUID> mobUuids = entry.getValue();
            mobUuids.removeIf(uuid -> Bukkit.getEntity(uuid) == null);
        }
        // 追跡するMobがいなくなったプレイヤーのエントリを削除
        spawnedMobsTracker.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    // ----------------------------------------------------
    // --- Outpostイベントによるスポーン処理 ---
    // ----------------------------------------------------

    /**
     * Outpostイベントの要求に基づき、Mobをスポーンさせます。
     * 修正: 窒息しない場所を探す再抽選ロジックを追加
     *
     * @param mobId    Mythic Mob ID
     * @param count    スポーンさせる数
     * @param regionId 対象のリージョンID
     * @param fixedY   Y座標の固定値 (outpost.ymlで設定)
     * @param event    OutpostEventの参照
     * @return スポーンさせたMobの総数
     */
    public int spawnOutpostMobs(String mobId, int count, String regionId, double fixedY, OutpostEvent event) {
        World world = Bukkit.getWorld(event.getOutpostData().getWorldName());
        if (world == null)
            return 0;

        int spawnedCount = 0;

        // 安全な場所が見つかるまで再抽選する最大回数
        int maxRetries = 10;

        for (int i = 0; i < count; i++) {
            Location spawnLoc = null;

            // --- 再抽選ロジック開始 ---
            for (int attempt = 0; attempt < maxRetries; attempt++) {
                Location candidate = getRandomLocationInRegion(world, regionId, fixedY);

                // リージョン設定ミス等でnullが返ってきた場合は中断
                if (candidate == null)
                    break;

                // とりあえず候補として保存しておく (最後まで安全な場所が見つからなかった場合のフォールバック)
                spawnLoc = candidate;

                // 安全チェック: 足元と頭の位置が「固体ブロック(Solid)」ではないか確認
                boolean isFeetSafe = !candidate.getBlock().getType().isSolid();
                boolean isHeadSafe = !candidate.clone().add(0, 1, 0).getBlock().getType().isSolid();

                if (isFeetSafe && isHeadSafe) {
                    // 安全な場所が見つかったので、リトライループを抜けてこの座標を採用
                    break;
                }
            }
            // --- 再抽選ロジック終了 ---

            if (spawnLoc != null) {
                // 2. Mythic Mobをスポーン
                UUID mobUuid = spawnMythicMob(mobId, spawnLoc, getTierFromLocation(spawnLoc));

                if (mobUuid != null) {
                    // 3. Outpost Mobとして追跡
                    outpostMobTracker.put(mobUuid, regionId);
                    spawnedCount++;
                }
            }
        }

        return spawnedCount;
    }

    /**
     * 指定されたリージョンIDに紐づくOutpost Mobを全て削除し、追跡を解除します。
     * (ウェーブ時間切れ時やイベント強制終了時に使用)
     * * @param regionId 削除対象のOutpostのリージョンID
     */
    public void removeAllOutpostMobs(String regionId) {
        if (regionId == null)
            return;

        // 削除対象のMob UUIDリストを一時的に保持
        List<UUID> mobsToRemove = new ArrayList<>();

        // 1. 追跡マップを反復処理し、対象のMobを特定
        for (Map.Entry<UUID, String> entry : outpostMobTracker.entrySet()) {
            if (regionId.equalsIgnoreCase(entry.getValue())) {
                mobsToRemove.add(entry.getKey());
            }
        }

        if (mobsToRemove.isEmpty()) {
            Bukkit.getLogger().info("[MobSpawnManager] リージョン '" + regionId + "' に残存Mobはありませんでした。");
            return;
        }

        int removedCount = 0;

        // 2. Mobを削除
        // Mobが存在するワールドを特定できれば、より効率的ですが、ここでは全てのワールドをスキャンします。
        // (OutpostEventが OutpostData から WorldName を取得できるため、本当はそちらを利用すると高速です)

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (mobsToRemove.contains(entity.getUniqueId())) {
                    entity.remove(); // Mobをワールドから削除
                    removedCount++;
                }
            }
        }

        // 3. 追跡マップから削除
        for (UUID mobUuid : mobsToRemove) {
            outpostMobTracker.remove(mobUuid);
        }

        Bukkit.getLogger().info("[MobSpawnManager] リージョン '" + regionId + "' から残存Mobを " + removedCount + " 体削除しました。");
    }

    // ----------------------------------------------------
    // --- ヘルパーメソッド (既存の修正) ---
    // ----------------------------------------------------

    /**
     * MythicMobsのMobをスポーンさせる
     * 
     * @return スポーンしたMobのUUID
     */
    /**
     * MythicMobをスポーンさせ、周囲のプレイヤー状況に応じたレベルを付与する
     * 
     * @param mobId MythicMobsの内部ID
     * @param loc   スポーン場所
     * @param tier  現在のエリアのTier
     * @return スポーンしたエンティティのUUID、失敗時はnull
     */
    private UUID spawnMythicMob(String mobId, Location loc, int tier) {
        // 1. MythicMobをスポーンさせる
        // getEntity().getBukkitEntity() の前に null チェックを挟むのが安全です
        var activeMob = MythicBukkit.inst().getMobManager().spawnMob(mobId, loc);
        if (activeMob == null || activeMob.getEntity() == null)
            return null;

        org.bukkit.entity.Entity entity = activeMob.getEntity().getBukkitEntity();
        if (!(entity instanceof org.bukkit.entity.LivingEntity livingEntity))
            return entity.getUniqueId();

        // 2. 周囲20ブロック以内のサバイバルプレイヤーを検索
        List<Player> nearbyPlayers = loc.getWorld().getPlayers().stream()
                .filter(p -> p.getGameMode() == GameMode.SURVIVAL)
                .filter(p -> p.getLocation().distance(loc) <= 20)
                .toList();

        // 3. レベルの決定
        int spawnLevel;
        MobTierConfig config = mobTierConfigs.get(tier);
        int areaMaxLevel = (config != null) ? config.getAreaLevel() : 1;

        if (!nearbyPlayers.isEmpty()) {
            // 近くにプレイヤーがいる場合：ランダムなプレイヤーのレベルを基準にする
            Player targetPlayer = nearbyPlayers.get(Deepwither.getInstance().getRandom().nextInt(nearbyPlayers.size()));
            // TODO: 独自レベルシステムがある場合は player.getLevel() を書き換える
            spawnLevel = targetPlayer.getLevel();
        } else {
            // 近くにプレイヤーがいない場合：エリアレベルの上限から5下までの範囲でランダム
            int minLvl = Math.max(1, areaMaxLevel - 5);
            spawnLevel = Deepwither.getInstance().getRandom().nextInt((areaMaxLevel - minLvl) + 1) + minLvl;
        }

        // エリア上限を適用 (これを超えないようにする)
        spawnLevel = Math.min(spawnLevel, areaMaxLevel);
        spawnLevel = Math.max(1, spawnLevel);

        // 4. MobLevelManager を通じてステータスと名前を適用
        // MythicMobの設定上の表示名を取得
        String mobDisplayName = activeMob.getType().getDisplayName().get();
        if (mobDisplayName == null)
            mobDisplayName = mobId;

        levelManager.applyLevel(livingEntity, mobDisplayName, spawnLevel);

        applyRandomTraits(livingEntity, spawnLevel, mobDisplayName);

        return livingEntity.getUniqueId();
    }

    private Location getRandomSpawnLocation(Location center, int radius) {
        // ... (元の getRandomSpawnLocation の実装) ...
        Random random = plugin.getRandom();
        double x = center.getX() + (random.nextDouble() * 2 * radius) - radius;
        double z = center.getZ() + (random.nextDouble() * 2 * radius) - radius;
        World world = center.getWorld();
        int startY = (int) Math.min(world.getMaxHeight() - 2, center.getY() + 3);

        for (int y = startY; y > (world.getMinHeight() + 1); y--) {
            Location checkLoc = new Location(world, x, y, z);
            if (checkLoc.getBlock().getType().isAir() &&
                    checkLoc.clone().add(0, 1, 0).getBlock().getType().isAir()) {
                Location blockBelow = checkLoc.clone().subtract(0, 1, 0);
                if (!blockBelow.getBlock().getType().isAir() && blockBelow.getBlock().isSolid()) {
                    return new Location(world, x + 0.5, y + 0.0, z + 0.5);
                }
            }
        }
        return null;
    }

    /**
     * 特性を抽選し、TextDisplay を生成してマウントさせる
     */
    private void applyRandomTraits(LivingEntity entity, int level, String originalName) {
        if (plugin.getRandom().nextDouble() > TRAIT_SPAWN_CHANCE) return;

        List<MobTrait> selectedTraits = getRandomTraitsForLevel(level);
        if (selectedTraits.isEmpty()) return;

        // 内部処理（ダメージ計算等）で使いやすいように Enum の名前を保存
        String traitData = selectedTraits.stream()
                .map(Enum::name)
                .collect(Collectors.joining(","));
        entity.getPersistentDataContainer().set(TRAIT_KEY, PersistentDataType.STRING, traitData);

        // 1. ビジュアル演出 (発光)
        entity.setGlowing(true);

        // 2. 表示テキストの構築 (1行にまとめる)
        String traitText = selectedTraits.stream()
                .map(t -> t.displayName)
                .collect(Collectors.joining(" ")); // スペース区切りで1行に

        // 3. TextDisplay のスポーン
        TextDisplay display = entity.getWorld().spawn(entity.getLocation(), TextDisplay.class, (td) -> {
            td.setText(traitText);
            td.setBillboard(Display.Billboard.CENTER);
            td.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
            td.setShadowed(true);
            td.setAlignment(TextDisplay.TextAlignment.CENTER);

            // 表示位置の微調整 (Transformation)
            // マウント位置(足元)から、モブの頭上あたりに来るように垂直方向(translation.y)を調整
            Transformation transformation = td.getTransformation();
            transformation.getTranslation().set(0, 0.8f, 0);
            td.setTransformation(transformation);
        });

        // 4. モブに乗せる
        entity.addPassenger(display);

        // 5. クリーンアップ監視タスクのみ開始
        startCleanupTask(entity, display);
    }

    public void startGlobalTraitTicker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (World world : Bukkit.getWorlds()) {
                    for (LivingEntity entity : world.getLivingEntities()) {
                        List<String> traits = getTraits(entity); // PDCから取得
                        if (traits.isEmpty()) continue;

                        // --- SNARING_AURA (周囲5マスのプレイヤーに移動速度低下) ---
                        if (traits.contains("SNARING_AURA")) {
                            entity.getNearbyEntities(5, 5, 5).stream()
                                    .filter(e -> e instanceof Player)
                                    .forEach(p -> ((Player)p).addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0)));
                        }

                        // --- SUMMONER (低確率でシルバーフィッシュなどを召喚) ---
                        if (traits.contains("SUMMONER") && Math.random() < 0.05) {
                            entity.getWorld().spawnEntity(entity.getLocation(), EntityType.SILVERFISH);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // 1秒ごとに実行
    }

    private List<String> getTraits(LivingEntity entity) {
        String data = entity.getPersistentDataContainer().get(TRAIT_KEY, PersistentDataType.STRING);
        if (data == null) return List.of();
        return Arrays.asList(data.split(","));
    }

    /**
     * モブが死亡・消滅した際に TextDisplay を削除するだけのタスク
     */
    private void startCleanupTask(LivingEntity mob, TextDisplay display) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!mob.isValid() || mob.isDead()) {
                    display.remove();
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 10L, 10L); // 追従不要なのでチェック頻度を下げてOK
    }

    /**
     * 外部（EventListener等）からモブの特性を確認するためのユーティリティ
     */
    public List<String> getMobTraits(LivingEntity entity) {
        String data = entity.getPersistentDataContainer().get(TRAIT_KEY, PersistentDataType.STRING);
        if (data == null || data.isEmpty()) return Collections.emptyList();
        return Arrays.asList(data.split(","));
    }

    /**
     * レベル帯に応じた特性リストの抽選
     */
    private List<MobTrait> getRandomTraitsForLevel(int level) {
        List<MobTrait> available = new ArrayList<>();
        int count = 0;

        if (level >= 10 && level < 15) {
            count = 1;
            available = getTraitsByCategory("Basic");
        } else if (level >= 15 && level < 25) {
            count = plugin.getRandom().nextInt(3) + 1; // 1-3
            available = getTraitsByCategory("Basic");
        } else if (level >= 25 && level <= 35) {
            count = plugin.getRandom().nextInt(2) + 1; // 1-2
            available = getTraitsByCategory("Intermediate");
        }

        if (available.isEmpty()) return Collections.emptyList();

        Collections.shuffle(available);
        return available.stream().limit(count).collect(Collectors.toList());
    }

    /**
     * カテゴリに応じた特性リストを取得
     */
    private List<MobTrait> getTraitsByCategory(String category) {
        return Arrays.stream(MobTrait.values())
                .filter(t -> t.category.equals(category))
                .collect(Collectors.toList());
    }

    /**
     * WorldGuardリージョン内のランダムなLocationを取得し、Y座標を固定する。
     */
    private Location getRandomLocationInRegion(World world, String regionId, double fixedY) {
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionManager regionManager = container.get(BukkitAdapter.adapt(world));

        if (regionManager == null)
            return null;

        ProtectedRegion region = regionManager.getRegion(regionId);
        if (region == null)
            return null;

        Random random = plugin.getRandom();

        // リージョン境界内でのランダムな座標計算
        int minX = region.getMinimumPoint().getBlockX();
        int maxX = region.getMaximumPoint().getBlockX();
        int minZ = region.getMinimumPoint().getBlockZ();
        int maxZ = region.getMaximumPoint().getBlockZ();

        double x = minX + random.nextDouble() * (maxX - minX + 1);
        double z = minZ + random.nextDouble() * (maxZ - minZ + 1);

        // Y座標を固定値に設定
        return new Location(world, x, fixedY, z);
    }

    /**
     * 確率に基づいて中ボスをスポーンさせることを試みる。
     * 
     * @return 中ボスがスポーンした場合 true
     */
    private boolean trySpawnMiniBoss(Location centerLoc, MobTierConfig config, UUID playerId) {
        Map<String, Double> miniBosses = config.getMiniBosses();
        if (miniBosses.isEmpty())
            return false;

        Random random = plugin.getRandom();
        double roll = random.nextDouble();

        for (Map.Entry<String, Double> entry : miniBosses.entrySet()) {
            String mobId = entry.getKey();
            double chance = entry.getValue();

            if (roll <= chance) {
                Location spawnLoc = getRandomSpawnLocation(centerLoc, 15);

                if (spawnLoc != null) {
                    UUID mobUuid = spawnMythicMob(mobId, spawnLoc, getTierFromLocation(spawnLoc));
                    trackSpawnedMob(playerId, mobUuid); // ★追跡

                    // プレイヤーに通知
                    for (Player p : centerLoc.getWorld().getPlayers()) {
                        if (p.getLocation().distanceSquared(centerLoc) < 40 * 40) {
                            p.sendMessage("§4§l!!! 危険な反応 !!! §c" + mobId + "が付近に出現しました！");
                        }
                    }
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * プレイヤーがクエストエリア内にいるかチェックし、いる場合はクエストMobのスポーンを試みる。
     * 
     * @return クエストエリア内にいる場合は true (通常Mobスポーンを無効化するため)
     */
    private boolean trySpawnQuestMob(Player player, Location playerLoc, int currentMobs) {
        PlayerQuestData playerData = playerQuestManager.getPlayerData(player.getUniqueId());

        if (playerData == null || playerData.getActiveQuests().isEmpty()) {
            return false;
        }

        QuestProgress progress = playerData.getActiveQuests().values().iterator().next();
        LocationDetails locationDetails = progress.getQuestDetails().getLocationDetails();
        Location objectiveLoc = locationDetails.toBukkitLocation();

        if (objectiveLoc == null || !objectiveLoc.getWorld().equals(playerLoc.getWorld())) {
            return false;
        }

        if (objectiveLoc.distanceSquared(playerLoc) <= QUEST_AREA_RADIUS_SQUARED) {

            if (currentMobs >= MOB_CAP_PER_PLAYER) {
                return true;
            }

            String questMobId = progress.getQuestDetails().getTargetMobId();
            Location spawnLoc = getRandomSpawnLocation(playerLoc, 8);

            if (spawnLoc != null) {
                UUID mobUuid = spawnMythicMob(questMobId, spawnLoc, getTierFromLocation(spawnLoc));
                trackSpawnedMob(player.getUniqueId(), mobUuid); // ★追跡
                player.sendMessage("§a[クエストエリア] §e目標 Mob がスポーンしました！");
            }

            return true;
        }

        return false;
    }

    // ... isSafeZone, getTierFromLocation は変更なし ...

    private boolean isSafeZone(Location loc) {
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();
        ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(loc));

        for (ProtectedRegion region : set) {
            String regionId = region.getId().toLowerCase();
            // safezone または kbf が含まれているかチェック
            if (regionId.contains("safezone") || regionId.contains("kbf")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 指定ティアで通常スポーン候補になるMob IDを返します。
     * regular_mobs 内の "bandit" は bandit_mobs に展開されます。
     */
    public List<String> getQuestCandidateMobIdsByTier(int tier) {
        MobTierConfig config = mobTierConfigs.get(tier);
        if (config == null) {
            return Collections.emptyList();
        }

        LinkedHashSet<String> candidates = new LinkedHashSet<>();

        for (String mobId : config.getRegularMobs()) {
            if (mobId == null || mobId.isEmpty()) {
                continue;
            }

            if (mobId.equalsIgnoreCase("bandit")) {
                candidates.addAll(config.getBanditMobs());
            } else {
                candidates.add(mobId);
            }
        }

        if (candidates.isEmpty()) {
            candidates.addAll(config.getBanditMobs());
        }

        return new ArrayList<>(candidates);
    }

    public int getTierFromLocation(Location loc) {
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

    // MobSpawnManagerの内部クラスとして定義
    private static class MobTierConfig {
        private final int areaLevel; // ★ 追加
        private final List<String> regularMobs;
        private final List<String> banditMobs;
        private final Map<String, Double> miniBosses;

        public MobTierConfig(int areaLevel, List<String> regularMobs, List<String> banditMobs,
                Map<String, Double> miniBosses) {
            this.areaLevel = areaLevel; // ★ 追加
            this.regularMobs = regularMobs;
            this.banditMobs = banditMobs;
            this.miniBosses = miniBosses;
        }

        public int getAreaLevel() {
            return areaLevel;
        } // ★ 追加

        public List<String> getRegularMobs() {
            return regularMobs;
        }

        public List<String> getBanditMobs() {
            return banditMobs;
        }

        public Map<String, Double> getMiniBosses() {
            return miniBosses;
        }
    }
}