package com.lunar_prototype.deepwither.modules.combat;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

import java.util.List;

public interface HitShape {
    /**
     * 指定されたエンティティが判定内にあるかチェック
     * @param origin 攻撃の起点（プレイヤーの目の位置など）
     * @param direction 攻撃の方向ベクトル
     * @param target 対象エンティティ
     * @param reach リーチ
     * @return ヒットした場合は true
     */
    boolean isHit(Location origin, Vector direction, Entity target, double reach);

    /**
     * 判定の最大リーチを取得
     */
    double getMaxReach(double baseReach);

    /**
     * デバッグ用のパーティクルを描画
     */
    void drawDebug(Location origin, Vector direction, double reach);
}
