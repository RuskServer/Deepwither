package com.lunar_prototype.deepwither.modules.combat;

import com.lunar_prototype.deepwither.Deepwither;
import org.bukkit.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import java.util.Random;

/**
 * 独自実装のターコイズ斬撃演出。
 * ArmorStand を利用したコマ送りアニメーションを再現する。
 */
public class TurquoiseSlash {

    private static final Random RANDOM = new Random();

    /**
     * 指定された位置と方向に斬撃エフェクトを生成する。
     *
     * @param origin プレイヤーの目の位置
     * @param direction 視線方向ベクトル
     * @param reach 攻撃のリーチ（前方のオフセットに使用）
     * @param rotation Z軸の回転角度（0, 45, 135, 180など）
     */
    public static void spawn(Location origin, Vector direction, double reach, double rotation) {
        World world = origin.getWorld();
        if (world == null) return;

        // --- 1. パーティクル演出 ---
        world.spawnParticle(Particle.DUST, origin, 20, 1.0, 1.0, 1.0, 0.0,
                new Particle.DustOptions(Color.fromRGB(0, 255, 127), 1.5f));

        // --- 2. ArmorStand の位置計算 ---
        Vector dirH = direction.clone().setY(0).normalize();
        Vector right = new Vector(-dirH.getZ(), 0, dirH.getX()).normalize();

        double f = 3.0; // forward
        double y = 0.0; // vertical offset
        double so = 0.0; // side offset

        // rotation に基づいてオフセットを決定 (MythicMobsのバリアントを再現)
        if (Math.abs(rotation - 0.0) < 1.0) { // turquoise_slash_1
            y = 0.3;
        } else if (Math.abs(rotation - 180.0) < 1.0) { // turquoise_slash_2
            y = -0.5;
        } else if (Math.abs(rotation - 45.0) < 1.0) { // turquoise_slash_3
            y = 0.3;
            so = -0.5;
        } else if (Math.abs(rotation - 135.0) < 1.0) { // turquoise_slash_4
            y = -0.5;
            so = -0.5;
        }

        Location summonLoc = origin.clone().add(dirH.clone().multiply(f)).add(0, y, 0).add(right.clone().multiply(so));
        summonLoc.setDirection(dirH);

        // --- 3. ArmorStand 生成 ---
        ArmorStand stand = (ArmorStand) world.spawnEntity(summonLoc, EntityType.ARMOR_STAND);
        stand.setMarker(true);
        stand.setSmall(false);
        stand.setInvisible(true);
        stand.setInvulnerable(true);
        stand.setBasePlate(false);
        stand.setGravity(false);
        stand.setHeadPose(new EulerAngle(0, 0, Math.toRadians(rotation)));

        // --- 4. アニメーション実行 (7フレーム) ---
        new BukkitRunnable() {
            int frame = 1;

            @Override
            public void run() {
                if (frame > 7) {
                    stand.remove();
                    this.cancel();
                    return;
                }

                // 7600 + (frame-1) の CustomModelData を持つ Paper を頭にセット
                ItemStack modelItem = new ItemStack(Material.PAPER);
                ItemMeta meta = modelItem.getItemMeta();
                if (meta != null) {
                    meta.setCustomModelData(7600 + (frame - 1));
                    meta.setHideTooltip(true);
                    modelItem.setItemMeta(meta);
                }
                stand.getEquipment().setItem(EquipmentSlot.HEAD, modelItem);

                frame++;
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 1L);
    }
}
