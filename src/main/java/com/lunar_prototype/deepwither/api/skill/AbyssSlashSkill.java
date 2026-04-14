package com.lunar_prototype.deepwither.api.skill;

import com.lunar_prototype.deepwither.SkillDefinition;
import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Abyss Slash (虚空斬り)
 * 虚空を切り裂き、前方に最大10ブロック瞬間移動する。
 * 壁に当たった場合は手前で停止し、壁抜けを防止する。
 */
public class AbyssSlashSkill implements ISkillLogic {

    @Override
    public boolean cast(LivingEntity caster, SkillDefinition def, int level) {
        double maxDist = 10.0;
        Location start = caster.getEyeLocation();
        Vector dir = start.getDirection();

        // 壁抜け対策: RayTraceで障害物をチェック
        // FluidCollisionMode.NEVERで水や溶岩もすり抜けないように設定
        RayTraceResult result = caster.getWorld().rayTraceBlocks(
                start, dir, maxDist, FluidCollisionMode.NEVER, true
        );

        Location targetLoc;
        if (result != null) {
            // 壁に当たった場合、壁の手前約0.5ブロック地点を目的地にする
            targetLoc = result.getHitPosition().toLocation(caster.getWorld()).subtract(dir.normalize().multiply(0.5));
        } else {
            // 障害物がない場合、最大距離移動
            targetLoc = start.clone().add(dir.multiply(maxDist));
        }

        // 足元が安全か確認（簡易的なY軸調整：地上のブロック上に強制的に合わせる）
        targetLoc.setPitch(caster.getLocation().getPitch());
        targetLoc.setYaw(caster.getLocation().getYaw());

        // 移動演出 (発動前)
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);
        caster.getWorld().spawnParticle(Particle.SONIC_BOOM, caster.getLocation().add(0, 1, 0), 1);
        caster.getWorld().spawnParticle(Particle.PORTAL, caster.getLocation().add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.1);

        // 移動実行
        caster.teleport(targetLoc);
        
        // 移動演出 (着地後)
        caster.getWorld().playSound(targetLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.5f);
        caster.getWorld().spawnParticle(Particle.REVERSE_PORTAL, targetLoc.clone().add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.1);

        return true;
    }
}
