package com.lunar_prototype.deepwither.companion;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import io.lumine.mythic.bukkit.MythicBukkit;
import kr.toxicity.model.api.tracker.EntityTracker;
import kr.toxicity.model.api.tracker.ModelScaler;
import kr.toxicity.model.api.tracker.Tracker;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.*;

@DependsOn({})
public class CompanionManager implements IManager {

    private final Deepwither plugin;
    private final Map<String, CompanionData> companionTypes = new HashMap<>();

    // プレイヤーUUID -> スポーン済みコンパニオンの実体
    private final Map<UUID, ActiveCompanion> activeCompanions = new HashMap<>();

    public final NamespacedKey COMPANION_ID_KEY;
    private File storageFile;
    private YamlConfiguration storageConfig;

    public CompanionManager(Deepwither plugin) {
        this.plugin = plugin;
        this.COMPANION_ID_KEY = new NamespacedKey(plugin, "companion_id");
    }

    @Override
    public void init() {
        // アイテム保存用のファイル
        this.storageFile = new File(plugin.getDataFolder(), "companion_storage.yml");
        if (!storageFile.exists()) {
            try { storageFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        this.storageConfig = YamlConfiguration.loadConfiguration(storageFile);
        loadConfig();
        startCompanionTask();
    }

    @Override
    public void shutdown() {}

    // プレイヤーがセットしているコンパニオンアイテムを取得
    public ItemStack getStoredItem(UUID playerUUID) {
        return storageConfig.getItemStack(playerUUID.toString());
    }

    // コンパニオンアイテムを保存
    public void saveStoredItem(UUID playerUUID, ItemStack item) {
        storageConfig.set(playerUUID.toString(), item);
        try {
            storageConfig.save(storageFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // アイテムからIDを抽出するヘルパーメソッド
    public String getCompanionIdFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(COMPANION_ID_KEY, PersistentDataType.STRING);
    }

    // プレイヤーが現在コンパニオンを出しているか
    public boolean isSpawned(Player player) {
        return activeCompanions.containsKey(player.getUniqueId());
    }

    public void loadConfig() {
        companionTypes.clear(); // リロードに対応
        File configFile = new File(plugin.getDataFolder(), "companions.yml");
        if (!configFile.exists()) {
            plugin.saveResource("companions.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        ConfigurationSection section = config.getConfigurationSection("companions");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(key);
            if (s == null) continue;

            String name = s.getString("name", "Unknown Companion");
            String modelId = s.getString("model_id", "");
            EntityType type = EntityType.valueOf(s.getString("type", "ZOMBIE").toUpperCase());
            double health = s.getDouble("health", 20.0);
            boolean rideable = s.getBoolean("rideable", false);
            double speed = s.getDouble("speed", 0.3);

            CompanionData data = new CompanionData(key, name, modelId, type, health, rideable, speed);

            // スキルの読み込み
            // --- スキルの読み込み (安全な型取得) ---
            List<Map<?, ?>> skillList = s.getMapList("skills");
            for (Map<?, ?> skillMap : skillList) {
                String sName = (String) skillMap.get("skill");
                if (sName == null) continue;

                // 各値を Number として取得してから変換することでキャストエラーを防ぐ
                double chance = 100.0;
                if (skillMap.containsKey("chance")) {
                    Object val = skillMap.get("chance");
                    if (val instanceof Number n) chance = n.doubleValue();
                }

                int cooldown = 0;
                if (skillMap.containsKey("cooldown")) {
                    Object val = skillMap.get("cooldown");
                    if (val instanceof Number n) cooldown = n.intValue();
                }

                double range = 5.0;
                if (skillMap.containsKey("range")) {
                    Object val = skillMap.get("range");
                    if (val instanceof Number n) range = n.doubleValue();
                }

                data.addSkill(sName, chance, cooldown, range);
            }

            companionTypes.put(key, data);
        }
        plugin.getLogger().info(companionTypes.size() + " 個のコンパニオンを読み込みました。");
    }

    // コンパニオンをスポーンさせるメソッド (GUI等から呼ばれる)
    public void spawnCompanion(Player owner, String companionId) {
        if (!companionTypes.containsKey(companionId)) return;

        // 既存のコンパニオンがいれば削除
        despawnCompanion(owner);

        CompanionData data = companionTypes.get(companionId);
        Location spawnLoc = owner.getLocation();

        // ベースEntityのスポーン
        LivingEntity entity = (LivingEntity) owner.getWorld().spawnEntity(spawnLoc, data.getType());

        // ステータス設定
        entity.setCustomName(data.getName());
        entity.setCustomNameVisible(true);
        if (entity.getAttribute(Attribute.MAX_HEALTH) != null) {
            entity.getAttribute(Attribute.MAX_HEALTH).setBaseValue(data.getMaxHealth());
        }
        entity.setHealth(data.getMaxHealth());

        if (entity instanceof Tameable tameable) {
            tameable.setOwner(owner);
            tameable.setTamed(true);
        }

        // 敵対しないようにAI調整 (必要に応じて)
        // entity.setAI(true); // Mobとして動かすためAIはON

        // BetterModel APIの適用
        applyBetterModel(entity, data.getModelId());

        // 管理リストに追加
        activeCompanions.put(owner.getUniqueId(), new ActiveCompanion(owner, entity, data));
    }

    public void despawnCompanion(Player owner) {
        if (activeCompanions.containsKey(owner.getUniqueId())) {
            ActiveCompanion ac = activeCompanions.remove(owner.getUniqueId());
            if (ac.entity != null && !ac.entity.isDead()) {
                // BetterModelの削除処理が必要ならここに入れる
                kr.toxicity.model.api.BetterModel.registry(ac.entity) //Gets tracker registry.
                        .map(reg -> reg.tracker("model")) //Gets tracker by it's name
                        .ifPresent(Tracker::close);
                ac.entity.remove();
            }
        }
    }

    // BetterModel API連携
    private void applyBetterModel(LivingEntity entity, String modelId) {
        try {EntityTracker tracker = kr.toxicity.model.api.BetterModel.model(modelId)
                .map(r -> r.getOrCreate(entity)) //Gets or creates entity tracker by this renderer to some entity.
                .orElse(null);
            tracker.scaler(ModelScaler.entity().multiply(2));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // 定期実行タスク (AI, スキル, 騎乗操作)
    // タスク内からは handleRiding を削除し、以下のように簡略化
    private void startCompanionTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (ActiveCompanion ac : activeCompanions.values()) {
                    if (ac.entity.isDead() || !ac.entity.isValid()) continue;

                    // 騎乗中の処理
                    if (!ac.entity.getPassengers().isEmpty() && ac.currentInput != null) {
                        handleRidingLogic(ac);
                    } else {
                        // 誰も乗っていない時は入力をクリア
                        ac.currentInput = null;
                        handleCombat(ac);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L); // 1チックごとに実行
    }

    private void handleRidingLogic(ActiveCompanion ac) {
        LivingEntity vehicle = ac.entity;
        Player p = ac.owner;
        org.bukkit.Input input = ac.currentInput;

        // 1. 移動入力の計算 (W/S/A/D)
        float forward = 0;
        if (input.isForward()) forward += 1.0f;
        if (input.isBackward()) forward -= 1.0f;
        float sideway = 0;
        if (input.isLeft()) sideway += 1.0f;  // A
        if (input.isRight()) sideway -= 1.0f; // D

        // プレイヤーの視線方向（水平）
        Location pLoc = p.getLocation();
        Vector dir = pLoc.getDirection().setY(0).normalize();
        Vector side = new Vector(-dir.getZ(), 0, dir.getX()).normalize();

        // 最終的な移動方向ベクトル
        Vector moveVec = dir.multiply(forward).add(side.multiply(sideway));

        // 2. 回転（向き）の調整
        if (moveVec.lengthSquared() > 0) {
            // 入力がある場合、その「移動する方向」を向かせる
            // これにより、後退(S)や横移動(A/D)時にモデルが進行方向を向くようになります
            float targetYaw = (float) Math.toDegrees(Math.atan2(-moveVec.getX(), moveVec.getZ()));
            vehicle.setRotation(targetYaw, 0); // コンパニオンのPitchは基本0で固定

            // 3. 速度の適用 (GENERIC_MOVEMENT_SPEED を利用)
            // バニラの速度属性を基準に計算 (例: 0.3 = プレイヤーの歩行速度程度)
            double speedAttr = ac.data.getSpeed();;
            // 属性が0に設定されている場合は、設定ファイル(ac.data.getSpeed())の値を優先
            double finalSpeed = (speedAttr > 0) ? speedAttr : ac.data.getSpeed();

            // setVelocityでの移動 (1チックごとの加速)
            // 摩擦を考慮し、バニラの移動速度属性よりも少し強めにベクトルをかけるのがコツです
            Vector velocity = moveVec.normalize().multiply(finalSpeed * 1.5);
            vehicle.setVelocity(velocity.setY(vehicle.getVelocity().getY()));

        } else {
            // 入力がない時は急停止（慣性削除）
            if (vehicle.isOnGround()) {
                vehicle.setVelocity(new Vector(0, vehicle.getVelocity().getY(), 0));
                // 止まっている時はプレイヤーと同じ方向を向かせる
                vehicle.setRotation(pLoc.getYaw(), 0);
            }
        }

        // 4. ジャンプ処理
        if (input.isJump() && vehicle.isOnGround()) {
            // ジャンプ力も属性や設定から取れるようにすると拡張性が高いです
            vehicle.setVelocity(vehicle.getVelocity().add(new Vector(0, 0.5, 0)));
        }
    }
    private void handleCombat(ActiveCompanion ac) {
        LivingEntity mob = ac.entity;
        LivingEntity target = null;

        // ターゲット取得 (MobAIがターゲットを持っていればそれを使う)
        if (mob instanceof Mob m && m.getTarget() != null && !m.getTarget().isDead()) {
            target = m.getTarget();
        }
        // なければ近くの敵対Mobを探す
        else {
            for (Entity e : mob.getNearbyEntities(10, 5, 10)) {
                if (e instanceof Monster && !e.equals(mob)) { // Monsterのみ、自分以外
                    target = (LivingEntity) e;
                    if (mob instanceof Mob m) m.setTarget(target);
                    break;
                }
            }
        }

        if (target == null) return;

        // スキル発動判定
        long now = System.currentTimeMillis();
        double dist = mob.getLocation().distance(target.getLocation());

        for (CompanionData.CompanionSkill skill : ac.data.getSkills()) {
            // クールダウンチェック
            long lastCast = ac.lastSkillCast.getOrDefault(skill.skillName, 0L);
            if (now - lastCast < skill.cooldown * 1000L) continue;

            // 距離チェック
            if (dist > skill.range) continue;

            // 確率チェック
            if (Math.random() * 100 > skill.chance) continue;

            // MythicMobsスキル発動
            MythicBukkit.inst().getAPIHelper().castSkill(mob, skill.skillName);

            // クールダウン更新
            ac.lastSkillCast.put(skill.skillName, now);

            // 1回のループで1スキルだけ発動して抜ける（スパム防止）
            break;
        }
    }

    // 内部クラス: アクティブなコンパニオンの状態管理
    static class ActiveCompanion {
        Player owner;
        LivingEntity entity;
        CompanionData data;
        Map<String, Long> lastSkillCast = new HashMap<>();
        org.bukkit.Input currentInput = null;

        public ActiveCompanion(Player owner, LivingEntity entity, CompanionData data) {
            this.owner = owner;
            this.entity = entity;
            this.data = data;
        }
    }

    // Getter for Event Listener
    public ActiveCompanion getActiveCompanion(UUID entityUUID) {
        for (ActiveCompanion ac : activeCompanions.values()) {
            if (ac.entity.getUniqueId().equals(entityUUID)) return ac;
        }
        return null;
    }

    public boolean isCompanion(Entity entity) {
        return getActiveCompanion(entity.getUniqueId()) != null;
    }
}