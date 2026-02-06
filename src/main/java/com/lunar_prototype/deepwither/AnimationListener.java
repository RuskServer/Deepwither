package com.lunar_prototype.deepwither;

import io.lumine.mythic.bukkit.MythicBukkit;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

@DependsOn({ItemFactory.class})
public class AnimationListener implements Listener, IManager {

    private final JavaPlugin plugin;

    public AnimationListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        org.bukkit.Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

    // ---------------------------------------------------------
    // プレイヤー用: クリックした瞬間（空振りでも発動）
    // ---------------------------------------------------------
    @EventHandler
    public void onPlayerArmSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;

        // プレイヤーのエフェクト処理を実行
        handleWeaponEffect(event.getPlayer());
    }

    // ---------------------------------------------------------
    // モブ用: 攻撃を当てた瞬間
    // ---------------------------------------------------------
    @EventHandler
    public void onMobAttack(EntityDamageByEntityEvent event) {
        // 攻撃者が「生きているエンティティ」でなければ無視（矢などは除外）
        if (!(event.getDamager() instanceof LivingEntity)) return;

        LivingEntity attacker = (LivingEntity) event.getDamager();

        // プレイヤーは PlayerAnimationEvent で処理済みなので、ここでは除外する
        // (これをしないとプレイヤーが殴った時にエフェクトが2重に出ます)
        if (attacker instanceof Player) return;

        // モブのエフェクト処理を実行
        handleWeaponEffect(attacker);
    }

    // ---------------------------------------------------------
    // 共通処理ロジック
    // ---------------------------------------------------------
    private void handleWeaponEffect(LivingEntity entity) {
        ItemStack weapon;

        // 装備取得（PlayerとMobで共通のインターフェース）
        if (entity.getEquipment() == null) return;
        weapon = entity.getEquipment().getItemInMainHand();

        if (weapon == null || weapon.getType() == Material.AIR) return;

        // --- 重い武器 ---
        if (isHeavyWeapon(weapon)) {
            spawnHeavySwingEffect(entity);
            return;
        }

        // --- 槍 ---
        if (isSpearWeapon(weapon)) {
            spawnSpearThrustEffect(entity);
            return;
        }

        if (isScytheWeapon(weapon)) {
            spawnScytheSlashEffect(entity);
            return;
        }

        // --- 通常剣 ---
        if (isSwordWeapon(weapon)) {
            // MythicMobsのスキルキャスト
            castMythicSkill(entity, "turquoise_slash");

            entity.getWorld().playSound(entity.getLocation(),
                    Sound.ENTITY_PLAYER_ATTACK_SWEEP,
                    1f, 1f);
        }
    }

    /* ---------------- 判定系 ---------------- */

    private boolean hasCategory(ItemStack item, String category) {
        if (!item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (!meta.getPersistentDataContainer().has(ItemFactory.ITEM_TYPE_KEY)) return false;

        if (meta.getPersistentDataContainer().get(ItemFactory.ITEM_TYPE_KEY, PersistentDataType.STRING)
                .equals(category)) {
            return true;
        }

        return false;
    }

    private boolean isSpearWeapon(ItemStack item) {
        return hasCategory(item, "槍");
    }

    private boolean isSwordWeapon(ItemStack item) {
        return hasCategory(item, "剣") || hasCategory(item, "大剣");
    }

    private boolean isHeavyWeapon(ItemStack item) {
        return hasCategory(item, "ハルバード")
                || hasCategory(item, "斧")
                || hasCategory(item, "マチェット")
                || hasCategory(item, "メイス")
                || hasCategory(item, "ハンマー");
    }

    private boolean isScytheWeapon(ItemStack item) {
        return hasCategory(item, "鎌");
    }

    /* ---------------- 演出系 (Player -> LivingEntity に変更) ---------------- */

    private void spawnScytheSlashEffect(LivingEntity entity) {
        Location start = entity.getEyeLocation().subtract(0, 0.3, 0); // 少し下げて腰～胸の高さに
        Vector dir = start.getDirection().normalize();

        // 右から左へなぎ払う（約120度の範囲）
        // -60度(右)から+60度(左)へ展開
        for (double angle = -60; angle <= 60; angle += 10) {
            double radians = Math.toRadians(angle);

            // 視線方向(dir)を軸に、水平方向に回転させる計算
            double x = dir.getX() * Math.cos(radians) - dir.getZ() * Math.sin(radians);
            double z = dir.getX() * Math.sin(radians) + dir.getZ() * Math.cos(radians);

            // 鎌のリーチ（2.5ブロック程度）
            Location particleLoc = start.clone().add(new Vector(x, dir.getY(), z).multiply(2.5));

            // パーティクルの種類：SWEEP_ATTACK（なぎ払い）や雲、またはカスタム
            entity.getWorld().spawnParticle(
                    Particle.SWEEP_ATTACK,
                    particleLoc,
                    1,
                    0, 0, 0,
                    0
            );

            // 軌跡を強調するためのサブパーティクル
            entity.getWorld().spawnParticle(
                    Particle.SQUID_INK, // 黒いエフェクト（鎌らしい不気味さ）
                    particleLoc,
                    1,
                    0.02, 0.02, 0.02,
                    0.01
            );
        }

        // 効果音：少し重みのある風切り音
        entity.getWorld().playSound(
                entity.getLocation(),
                Sound.ENTITY_PLAYER_ATTACK_SWEEP,
                1.0f,
                0.7f // ピッチを下げて重厚感を出す
        );

        // MythicMobsのスキルがあれば連携（例: 闇属性の斬撃など）
        // castMythicSkill(entity, "dark_scythe_slash");
    }

    // 槍の突き
    private void spawnSpearThrustEffect(LivingEntity entity) {
        Location start = entity.getEyeLocation();
        Vector dir = start.getDirection().normalize();

        for (double i = 0; i <= 2.8; i += 0.2) {
            Location point = start.clone().add(dir.clone().multiply(i));
            entity.getWorld().spawnParticle(
                    Particle.CRIT,
                    point,
                    1,
                    0, 0, 0,
                    0
            );
        }

        entity.getWorld().playSound(
                entity.getLocation(),
                Sound.ENTITY_PLAYER_ATTACK_STRONG,
                0.6f,
                0.9f
        );
    }

    // 重い武器の振り下ろし
    private void spawnHeavySwingEffect(LivingEntity entity) {
        Location eyeLoc = entity.getEyeLocation();
        Vector dir = eyeLoc.getDirection().normalize();

        // 1. 縦の振り下ろし軌跡 (視界を遮らないよう、少し前方にオフセット)
        // プレイヤーの目の前から足元へ向かってパーティクルを並べる
        for (double i = 0.0; i <= 1.5; i += 0.2) {
            // 腕の振りに合わせて、少しずつ下にずらしていく
            Location point = eyeLoc.clone()
                    .add(dir.clone().multiply(1.5)) // 1.5ブロック前方
                    .subtract(0, i, 0);            // 上から下へ

            // 鋭い火花のようなエフェクト
            entity.getWorld().spawnParticle(
                    Particle.CRIT,
                    point,
                    3,       // 個数は少なめで密度を上げる
                    0.01, 0.01, 0.01,
                    0.1      // 少しだけ飛び散らせる
            );
        }

        // 2. 着弾地点の判定 (足元の少し前方)
        Location impactLoc = entity.getLocation().add(dir.clone().multiply(1.5));

        // 3. 地面への衝撃波 (横方向の広がり)
        // 視界の邪魔にならない足元に、破片とフラッシュを出す
        entity.getWorld().spawnParticle(
                Particle.FLASH,
                impactLoc.clone().add(0, 0.1, 0),
                1,               // 個数
                0, 0, 0,         // オフセット
                0,               // 速度/extra
                Color.WHITE      // 【✅ 追加】ここにColorを指定（白が標準的）
        );

        // 叩きつけた際の土煙/石片
        entity.getWorld().spawnParticle(
                Particle.BLOCK,
                impactLoc,
                15,
                0.2, 0.1, 0.2, // 横に広げる
                0.1,
                Material.STONE.createBlockData() // 実際は足元のブロックを取得しても良い
        );

        // 4. 重低音の強調
        entity.getWorld().playSound(
                entity.getLocation(),
                Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, // 金属が叩きつけられる音
                0.8f,
                0.5f // 低ピッチ
        );
        entity.getWorld().playSound(
                entity.getLocation(),
                Sound.ENTITY_GENERIC_EXPLODE, // 爆発音を小さく混ぜて重量感を出す
                0.5f,
                1.2f
        );
    }

    /* ---------------- MythicMobs ---------------- */

    private void castMythicSkill(LivingEntity caster, String skillName) {
        // MythicMobs API HelperはEntityに対してもキャスト可能です
        MythicBukkit.inst().getAPIHelper().castSkill(caster, skillName);
    }
}