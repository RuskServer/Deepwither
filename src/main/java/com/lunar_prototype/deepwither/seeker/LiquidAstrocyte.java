package com.lunar_prototype.deepwither.seeker;

import java.util.Arrays;
import java.util.List;

/**
 * [TQH-Glia] LiquidAstrocyte (星状膠細胞)
 * ニューロンネットワークの「空間的な恒常性」を維持する管理者。
 * 特定のエリア（行動群）が過剰発火した際、それを抑制(Dampening)し、
 * システム全体の熱暴走（GAS化による判断不能）を防ぐ役割を持つ。
 */
public class LiquidAstrocyte {

    private final List<LiquidNeuron> monitoredZone;
    private float glutamateBuffer = 0.0f; // 興奮性伝達物質の残滓
    private static final float HOMEOSTATIC_THRESHOLD = 1.8f; // 過剰発火とみなす閾値

    public LiquidAstrocyte(LiquidNeuron... neurons) {
        this.monitoredZone = Arrays.asList(neurons);
    }

    /**
     * 空間メタ制御の実行 (毎Tick呼び出し)
     * @param systemTemp 現在の脳内温度
     */
    public void regulate(float systemTemp) {
        // 1. 空間内の総活動量（Total Activity）を計測
        float totalActivity = 0.0f;
        for (LiquidNeuron neuron : monitoredZone) {
            totalActivity += neuron.getState();
        }

        // 2. グルタミン酸（興奮）の蓄積と自然減衰
        // 活動が高いほどバッファが溜まる（疲労物質のようなもの）
        glutamateBuffer += totalActivity * 0.1f;
        glutamateBuffer *= 0.92f; // 自然分解

        // 3. 恒常性スケーリング (Homeostatic Scaling)
        // 「温度が高い」かつ「エリア全体が興奮しすぎている」場合、強制冷却を行う
        if (systemTemp > 1.0f && (totalActivity > HOMEOSTATIC_THRESHOLD || glutamateBuffer > 2.0f)) {
            suppressOveractivity(0.15f); // 15%の抑制
        }
    }

    /**
     * 過剰発火エリアへの抑制信号 (Inhibition)
     * ニューロンの感度を下げ、状態値を強制的に減衰させる
     */
    private void suppressOveractivity(float strength) {
        for (LiquidNeuron neuron : monitoredZone) {
            // 発火しているニューロンほど強く抑制する（罰則ではなく、沈静化）
            if (neuron.getState() > 0.5f) {
                neuron.applyInhibition(strength);
            }
        }
    }

    /**
     * [可視化用] グリアが現在どれくらい介入しているか (0.0 - 1.0)
     */
    public float getInterventionLevel() {
        return Math.min(1.0f, glutamateBuffer / 3.0f);
    }
}