package com.lunar_prototype.deepwither.seeker;

import com.lunar_prototype.deepwither.Deepwither;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.style.Styler;
import org.knowm.xchart.style.XYStyler;
import org.knowm.xchart.style.markers.SeriesMarkers;

import java.awt.*;
import java.io.File;
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
        if (history.isEmpty()) return;

        // 1. ディレクトリの準備 (エラー解消の核心)
        File logDir = new File(Deepwither.getInstance().getDataFolder(), "logs");
        if (!logDir.exists()) {
            logDir.mkdirs();
        }

        // 2. データの準備
        double[] xData = IntStream.range(0, history.size()).asDoubleStream().toArray();
        double[] yData = history.stream().mapToDouble(s -> (double) s.temp()).toArray();

        // 3. グラフの作成 (デザインの洗練)
        XYChart chart = new XYChartBuilder()
                .width(1000).height(500) // 視認性のために少し横長に
                .title("TQH Phase Transition: [" + opponentName + "]") // 相手の名前を入れると管理しやすい
                .xAxisTitle("Time (Ticks)")
                .yAxisTitle("System Temp")
                .build();

        // 4. スタイルのカスタマイズ (より「プロフェッショナル」な外観へ)
        XYStyler styler = chart.getStyler();
        styler.setChartBackgroundColor(new Color(20, 20, 25)); // より深い黒
        styler.setPlotBackgroundColor(new Color(30, 30, 35));
        styler.setChartFontColor(Color.LIGHT_GRAY);
        styler.setAnnotationLineColor(Color.WHITE);
        styler.setAnnotationTextFontColor(Color.WHITE);
        styler.setPlotGridLinesColor(new Color(50, 50, 55));
        styler.setLegendPosition(Styler.LegendPosition.OutsideS); // 凡例を下に出してグラフ領域を確保
        styler.setLegendBackgroundColor(new Color(25, 25, 30));

        // 軸の範囲を自動調整（データが少ない時も綺麗に見せる）
        styler.setYAxisMin(0.0);

        // シリーズの追加
        XYSeries series = chart.addSeries("Brain Temp", xData, yData);
        series.setMarker(SeriesMarkers.NONE);
        series.setLineColor(new Color(0, 255, 200)); // 鮮やかなティール
        series.setLineWidth(2.5f);

        // 5. ファイル名と保存
        // タイムスタンプだけでなく、相手の名前と結果を入れると後でログ解析が捗ります
        String resultTag = (result != null) ? result.toString().toLowerCase() : "unknown";
        String fileName = String.format("brain_%s_%s_%d",
                opponentName.replaceAll("[^a-zA-Z0-9]", ""),
                resultTag,
                System.currentTimeMillis());

        try {
            File outputFile = new File(logDir, fileName);
            // BitmapEncoderは内部で拡張子を補完する場合があるためgetAbsolutePathを使用
            BitmapEncoder.saveBitmap(chart, outputFile.getAbsolutePath(), BitmapEncoder.BitmapFormat.PNG);
        } catch (IOException e) {
            Deepwither.getInstance().getLogger().warning("Failed to save combat graph: " + e.getMessage());
        }
    }

    private static String getPhaseName(float temp) {
        if (temp > 1.2f) return "GAS (Mantra)";
        if (temp < 0.3f) return "SOLID (Cyan)";
        return "LIQUID (Orange)";
    }
}
