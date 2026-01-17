package com.lunar_prototype.deepwither.seeker;

import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.style.Styler;
import org.knowm.xchart.style.markers.SeriesMarkers;

import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.stream.IntStream;

/**
 * [TQH-Analytics] CombatAnalyst v1.0
 * 戦闘終了時の脳内状態を可視化し、グラフと統計を生成する。
 */
public class CombatAnalyst {

    public enum Result { VICTORY, DEFEAT }

    public static void review(LiquidBrain brain, String opponentName, Result result) {
        List<LiquidBrain.BrainSnapshot> history = brain.getCombatHistory();
        if (history.isEmpty()) return;

        System.out.println("\n--- [COMBAT POST-MORTEM REPORT] ---");
        System.out.println("Opponent: " + opponentName);
        System.out.println("Outcome: " + (result == Result.VICTORY ? "★ VICTORY" : "† DEFEAT"));
        System.out.println("------------------------------------");

        // 1. 熱力学的タイムライン（簡易グラフ生成）
        System.out.println("System Temperature Timeline:");
        renderTimeline(history);

        // 2. 統計分析
        analyzePhysiology(brain, history, result);

        saveProfessionalGraph(brain,opponentName,result);

        // 3. [2026-01-12] FLASH演出へのフィードバック
        int[] lastColor = brain.getTQHFlashColor();
        String colorHex = String.format("#%02x%02x%02x", lastColor[0], lastColor[1], lastColor[2]);
        System.out.println("Final Neural Color: " + colorHex + " (TQH Phase: " + getPhaseName(brain.systemTemperature) + ")");
        System.out.println("------------------------------------\n");
    }

    /**
     * ASCIIによる温度推移グラフ
     */
    private static void renderTimeline(List<LiquidBrain.BrainSnapshot> history) {
        StringBuilder sb = new StringBuilder("  [T-10s] ");
        // 直近50件を抽出してサンプリング
        int step = Math.max(1, history.size() / 20);
        for (int i = 0; i < history.size(); i += step) {
            float temp = history.get(i).temp();
            if (temp > 1.2f) sb.append("🔥"); // GAS (暴走)
            else if (temp > 0.4f) sb.append("💧"); // LIQUID (流動)
            else sb.append("❄️"); // SOLID (冷徹)
        }
        sb.append(" [NOW]");
        System.out.println(sb.toString());
    }

    private static void analyzePhysiology(LiquidBrain brain, List<LiquidBrain.BrainSnapshot> history, Result result) {
        float avgTemp = (float) history.stream().mapToDouble(LiquidBrain.BrainSnapshot::temp).average().orElse(0);

        if (result == Result.VICTORY) {
            System.out.println("Dominance Analysis:");
            if (avgTemp < 0.4f) System.out.println(" > 冷徹な最適化により、最小限の熱量で相手を制圧しました。");
            else if (avgTemp > 1.0f) System.out.println(" > 圧倒的な熱量（サージ）によって相手の予測を破壊しました。");
            else System.out.println(" > 柔軟な適応（流動相）を維持し、安定して勝利しました。");
        } else {
            System.out.println("Fatality Analysis:");
            if (avgTemp > 1.2f) System.out.println(" > 原因: 熱暴走(GAS)による制御不能。攻撃精度が著しく低下していました。");
            else if (brain.frustration > 0.8f) System.out.println(" > 原因: フラストレーションの蓄積。焦りによる強引な突撃が致命傷です。");
            else System.out.println(" > 原因: 戦術的敗北。予測モデル(VelocityTrust)が相手に完全に適応されていました。");
        }
    }

    public static void saveProfessionalGraph(LiquidBrain brain, String opponentName, Result result) {
        List<LiquidBrain.BrainSnapshot> history = brain.getCombatHistory();

        // 1. データの準備
        double[] xData = IntStream.range(0, history.size()).asDoubleStream().toArray();
        double[] yData = history.stream().mapToDouble(s -> (double)s.temp()).toArray();

        // 2. グラフの作成 (現代的なデザイン)
        XYChart chart = new XYChartBuilder()
                .width(800).height(400)
                .title("TQH Phase Transition: v3.3-Astro-Interception")
                .xAxisTitle("Ticks").yAxisTitle("System Temp")
                .build();

        // 3. スタイルのカスタマイズ
        chart.getStyler().setChartBackgroundColor(new Color(25, 25, 30)); // ダークモード
        chart.getStyler().setChartFontColor(Color.WHITE);
        chart.getStyler().setPlotGridLinesVisible(true);
        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);

        // 相（フェーズ）の色分けをシリーズとして追加
        chart.addSeries("Brain Temp", xData, yData).setMarker(SeriesMarkers.NONE).setLineColor(Color.CYAN);

        // 4. 保存
        try {
            BitmapEncoder.saveBitmap(chart, "./plugins/Deepwither/logs/brain_" + System.currentTimeMillis(), BitmapEncoder.BitmapFormat.PNG);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String getPhaseName(float temp) {
        if (temp > 1.2f) return "GAS (Mantra)";
        if (temp < 0.3f) return "SOLID (Cyan)";
        return "LIQUID (Orange)";
    }
}
