package com.lunar_prototype.deepwither;

import io.lumine.mythic.bukkit.MythicBukkit;
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
import org.bukkit.util.Vector;

public class AnimationListener implements Listener {

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
        if (!meta.hasLore()) return false;

        for (String line : meta.getLore()) {
            // §7カテゴリ:§f のカラーコードなどは環境に合わせて微調整してください
            if (line.contains("カテゴリ:" + category) || line.contains("カテゴリ:§f" + category)) {
                return true;
            }
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
        Location base = entity.getLocation().add(0, 1.2, 0);
        Vector dir = entity.getLocation().getDirection().normalize();

        for (double y = 0; y <= 1.2; y += 0.15) {
            Location loc = base.clone()
                    .add(dir.clone().multiply(1.2))
                    .subtract(0, y, 0);

            entity.getWorld().spawnParticle(
                    Particle.EXPLOSION,
                    loc,
                    2,
                    0.05, 0.05, 0.05,
                    0
            );
        }

        entity.getWorld().spawnParticle(
                Particle.BLOCK,
                base.clone().add(dir.multiply(1.3)),
                10,
                Material.STONE.createBlockData()
        );

        entity.getWorld().playSound(
                entity.getLocation(),
                Sound.ENTITY_PLAYER_ATTACK_STRONG,
                1.0f,
                0.6f
        );
    }

    /* ---------------- MythicMobs ---------------- */

    private void castMythicSkill(LivingEntity caster, String skillName) {
        // MythicMobs API HelperはEntityに対してもキャスト可能です
        MythicBukkit.inst().getAPIHelper().castSkill(caster, skillName);
    }
}