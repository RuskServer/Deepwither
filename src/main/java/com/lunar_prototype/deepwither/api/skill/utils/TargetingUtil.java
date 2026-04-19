package com.lunar_prototype.deepwither.api.skill.utils;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.api.DeepwitherPartyAPI;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.Set;

public class TargetingUtil {

    /**
     * 支援魔法用のハイブリッド・ターゲッティング。
     * 1. 視線上のパーティーメンバーを優先 (RayTrace)
     * 2. 外れた場合、視線の軌道に最も近いパーティーメンバーをアシスト選択
     * 3. 誰もいない場合は null を返す
     *
     * @param caster 発動者
     * @param maxRange 最大射程
     * @param assistRadius アシストを許容する半径
     * @return ターゲットとなったプレイヤー、または null
     */
    public static Player getSupportTarget(Player caster, double maxRange, double assistRadius) {
        Location eyeLoc = caster.getEyeLocation();
        Vector direction = eyeLoc.getDirection().normalize();
        DeepwitherPartyAPI partyAPI = Deepwither.getInstance().getPartyAPI();

        // 1. RayTrace による直接ヒットの確認 (パーティーメンバー優先)
        RayTraceResult result = caster.getWorld().rayTraceEntities(
                eyeLoc,
                direction,
                maxRange,
                0.5, // 判定の太さ
                entity -> entity instanceof Player && !entity.equals(caster) && partyAPI.isInSameParty(caster, (Player) entity)
        );

        if (result != null && result.getHitEntity() instanceof Player target) {
            return target;
        }

        // 2. アシスト判定: 視線軌道に最も近いパーティーメンバーを探索
        Set<Player> members = partyAPI.getOnlinePartyMembers(caster);
        if (members.isEmpty()) {
            return null; // 自分一人の場合は対象なし
        }

        Player bestTarget = null;
        double minLineDistance = assistRadius; // 許容範囲内で初期化

        for (Player member : members) {
            if (member.equals(caster)) continue;
            if (!member.getWorld().equals(caster.getWorld())) continue;

            Location memberLoc = member.getLocation().add(0, 1.0, 0); // 中心付近を狙う
            double distToPlayer = eyeLoc.distance(memberLoc);

            // 射程外、または真後ろの場合は除外
            if (distToPlayer > maxRange) continue;
            
            Vector toPlayer = memberLoc.toVector().subtract(eyeLoc.toVector());
            if (toPlayer.dot(direction) < 0) continue; // 後ろにいる

            // 点と直線の距離（垂線の長さ）を計算
            // d = |(P-A) x D| / |D|  (Dが単位ベクトルのため分母は1)
            double lineDistance = toPlayer.crossProduct(direction).length();

            if (lineDistance < minLineDistance) {
                minLineDistance = lineDistance;
                bestTarget = member;
            }
        }

        return bestTarget;
    }
}
