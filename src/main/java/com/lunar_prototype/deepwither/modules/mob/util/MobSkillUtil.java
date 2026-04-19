package com.lunar_prototype.deepwither.modules.mob.util;

import com.lunar_prototype.deepwither.Deepwither;
import org.bukkit.entity.Entity;
import org.bukkit.metadata.FixedMetadataValue;

/**
 * モブのスキルに関する共通ユーティリティ
 */
public class MobSkillUtil {

    private static final String CC_COOLDOWN_PREFIX = "mob_cc_cooldown_";

    /**
     * 指定された種類のCC（行動不能や強ノックバック）が現在適用可能か確認します。
     * 複数体のモブから同時にハメ殺されるのを防ぐためのプレイヤー別クールダウンです。
     *
     * @param target 対象エンティティ
     * @param ccType CCの種類 (例: "knockback", "stun")
     * @param cooldownMs 無敵時間 (ミリ秒)
     * @return 適用可能な場合は true
     */
    public static boolean canApplyCC(Entity target, String ccType, long cooldownMs) {
        String key = CC_COOLDOWN_PREFIX + ccType;
        if (target.hasMetadata(key)) {
            long lastApplied = target.getMetadata(key).get(0).asLong();
            if (System.currentTimeMillis() - lastApplied < cooldownMs) {
                return false;
            }
        }
        return true;
    }

    /**
     * CCのクールダウン（無敵時間）を開始します。
     *
     * @param target 対象エンティティ
     * @param ccType CCの種類
     */
    public static void applyCCCooldown(Entity target, String ccType) {
        String key = CC_COOLDOWN_PREFIX + ccType;
        target.setMetadata(key, new FixedMetadataValue(Deepwither.getInstance(), System.currentTimeMillis()));
    }
}
