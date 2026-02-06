package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.Bukkit;
import com.lunar_prototype.deepwither.StatType; // StatTypeへのパスが正しいことを確認

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@DependsOn({})
public class CooldownManager implements IManager {

    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();

    @Override
    public void init() {}

    @Override
    public void shutdown() {}

    // ★ 修正: minCooldown を引数に追加
    public boolean isOnCooldown(UUID uuid, String skillId, double baseCooldown, double minCooldown) {
        // ★ minCooldown を渡す
        double actualCooldown = applyCooldownReduction(uuid, baseCooldown, minCooldown);

        long now = System.currentTimeMillis();
        long last = cooldowns.getOrDefault(uuid, new HashMap<>()).getOrDefault(skillId, 0L);

        // actualCooldown は秒単位なので、1000を掛けてミリ秒にする
        return (now - last) < (actualCooldown * 1000);
    }

    // ★ 修正: minCooldown を引数に追加
    public double getRemaining(UUID uuid, String skillId, double baseCooldown, double minCooldown) {
        // ★ minCooldown を渡す
        double actualCooldown = applyCooldownReduction(uuid, baseCooldown, minCooldown);

        long now = System.currentTimeMillis();
        long last = cooldowns.getOrDefault(uuid, new HashMap<>()).getOrDefault(skillId, 0L);

        double remaining = (actualCooldown * 1000 - (now - last)) / 1000.0;
        return Math.max(0, remaining);
    }

    // ★ 修正: minCooldown を引数に追加
    private double applyCooldownReduction(UUID uuid, double baseCooldown, double minCooldown) {
        // 1. クールダウン短縮ステータスを取得 (例: 20 や 50 が返ってくると想定)
        double reductionValue = StatManager.getTotalStatsFromEquipment(Bukkit.getPlayer(uuid)).getFlat(StatType.COOLDOWN_REDUCTION);

        // ★ 修正点: 100 で割ってパーセンテージ (0.2 など) に変換する
        double reductionRatio = reductionValue / 100.0;

        // 2. 短縮率の安全な上限を適用 (例: 90% = 0.9)
        // ここで 0.9 (90%) を超えていたら 0.9 に丸める
        reductionRatio = Math.min(reductionRatio, 0.9);

        // 3. 短縮後のクールダウン時間を計算
        // reductionRatio が 0.2 なら、(1.0 - 0.2) = 0.8倍 になる
        double reducedCooldown = baseCooldown * (1.0 - reductionRatio);

        // 4. 最小クールダウン時間 (min) を適用
        return Math.max(reducedCooldown, minCooldown);
    }

    // setCooldown はクールダウン開始時刻を記録するだけなので変更なし
    public void setCooldown(UUID uuid, String skillId) {
        cooldowns.computeIfAbsent(uuid, k -> new HashMap<>())
                .put(skillId, System.currentTimeMillis());
    }
}