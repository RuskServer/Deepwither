package com.lunar_prototype.deepwither.textai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.lunar_prototype.deepwither.seeker.LiquidNeuron;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * EMDA_LanguageAI v4.0 - Resonance Tuning
 * 1. External Dictionary: 外部ファイルからのデータロード
 * 2. Resonance Trainer: CSVデータによるスパルタ教育モード
 * 3. Self-Organization: 状況タグから単語ベクトルを自動生成
 */
public class EMDALanguageAI {
    private final LiquidNeuron logic = new LiquidNeuron(0.1);
    private final LiquidNeuron emotion = new LiquidNeuron(0.15);
    private final LiquidNeuron context = new LiquidNeuron(0.08);

    private static class WordNode {
        String text;
        float[] vector; // [0]:Logic, [1]:Emotion, [2]:Urgency/Context

        WordNode(String text, float[] v) {
            this.text = text;
            this.vector = v;
        }
    }

    private Map<Long, List<WordNode>> vDictionary = new ConcurrentHashMap<>();
    private final Map<String, Float> wordFatigueMap = new HashMap<>();
    private final File dictionaryFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static class DictionaryData {
        Map<Long, List<WordNode>> data;
    }

    public EMDALanguageAI(File dataFolder) {
        if (!dataFolder.exists()) dataFolder.mkdirs();
        this.dictionaryFile = new File(dataFolder, "dictionary.json");
        loadDictionary();
    }

    /**
     * [新理論] Resonance Tuning (スパルタ教育) - 非同期ログ出力版
     * CSVを回して単語に「LNNの指紋」を焼き付け、バックグラウンドで保存
     */
    public void trainFromCSVAsync(File csvFile) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            long startTime = System.currentTimeMillis();
            int count = 0;
            Map<Long, Integer> stats = new HashMap<>();

            System.out.println("[EMDA-AI] >>> Resonance Tuning 開始: " + csvFile.getName());

            try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length < 2) continue;

                    // 状況タグの解析
                    String[] tags = parts[0].replaceAll("[\\[\\] ]", "").split(":");
                    if (tags.length < 3) continue;

                    float tL = Float.parseFloat(tags[0]);
                    float tE = Float.parseFloat(tags[1]);
                    float tU = Float.parseFloat(tags[2]);
                    String phrase = parts[1].trim();

                    // 1. LNN状況シミュレート
                    logic.update(tL, tU);
                    emotion.update(tE, tU);
                    context.update(1.0, tU);

                    // 2. 指紋生成
                    float[] fingerprint = {(float)logic.get(), (float)emotion.get(), (float)context.get()};

                    // 3. カテゴリ自動分類
                    long cat = (tE > 0.6) ? 2L : 1L;
                    if (phrase.contains("システム") || phrase.contains("実装")) cat = 100L;
                    if (phrase.endsWith("？") || phrase.endsWith("。")) cat = 300L;

                    addVWord(cat, phrase, fingerprint);
                    stats.put(cat, stats.getOrDefault(cat, 0) + 1);
                    count++;

                    // 100行ごとに進捗を表示
                    if (count % 100 == 0) {
                        System.out.println("[EMDA-AI] 学習進行中... " + count + " 行完了");
                    }
                }

                saveDictionary(); // 学習結果を永続化

                long duration = System.currentTimeMillis() - startTime;
                System.out.println("[EMDA-AI] <<< Resonance Tuning 完了！");
                System.out.println("[EMDA-AI] 処理時間: " + duration + "ms");
                System.out.println("[EMDA-AI] 合計学習フレーズ: " + count);
                stats.forEach((cat, c) -> System.out.println("  - カテゴリ [" + cat + "]: " + c + "語"));

            } catch (Exception e) {
                System.err.println("[EMDA-AI] 非同期学習中にエラーが発生しました: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private void addVWord(long cat, String txt, float[] v) {
        vDictionary.computeIfAbsent(cat, k -> new ArrayList<>())
                .add(new WordNode(txt, v));
    }

    /**
     * 文章生成ロジック (v3.1継承・最適化)
     */
    public String generateResponse(String input, double urgency) {
        // 入力から現在のLNNポテンシャルを更新
        updateLNNState(input, urgency);

        float[] query = {(float)logic.get(), (float)emotion.get(), (float)context.get()};
        StringBuilder sb = new StringBuilder();

        // 1秒の猶予を活かした共鳴選択
        if (query[2] > 0.5) sb.append(attentionSelect(300L, query)).append("、");

        long mainCat = (query[1] > 0.6) ? 2L : 1L;
        if (input.contains("アプデ") || input.contains("Ver")) {
            sb.append(attentionSelect(100L, query));
            sb.append(attentionSelect(200L, query));
        } else {
            sb.append(attentionSelect(mainCat, query));
        }

        return sb.toString();
    }

    private String attentionSelect(long cat, float[] query) {
        List<WordNode> nodes = vDictionary.get(cat);
        if (nodes == null || nodes.isEmpty()) return "";

        return nodes.stream().max(Comparator.comparingDouble(node -> {
            double dotProduct = 0;
            for (int i = 0; i < query.length; i++) dotProduct += query[i] * node.vector[i];
            float fatigue = wordFatigueMap.getOrDefault(node.text, 0.0f);
            return dotProduct * (1.0 - fatigue);
        })).map(n -> n.text).orElse("");
    }

    private void updateLNNState(String input, double urgency) {
        logic.update(input.matches(".*(Ver|実装|修正).*") ? 1.0 : 0.0, urgency);
        emotion.update(input.matches(".*(!|\\?|どけ|殺).*") ? 1.0 : 0.0, urgency);
        context.update(1.0, urgency);
    }

    /**
     * [完成] JSONロード処理
     * サーバー起動時に自己組織化されたベクトルを復元
     */
    private void loadDictionary() {
        if (!dictionaryFile.exists()) {
            System.out.println("[EMDA-AI] dictionary.json が見つかりません。新規作成します。");
            return;
        }

        try (Reader reader = new FileReader(dictionaryFile)) {
            java.lang.reflect.Type type = new TypeToken<Map<Long, List<WordNode>>>(){}.getType();
            Map<Long, List<WordNode>> loadedData = gson.fromJson(reader, type);
            if (loadedData != null) {
                this.vDictionary = new ConcurrentHashMap<>(loadedData);
                System.out.println("[EMDA-AI] " + vDictionary.size() + " カテゴリの辞書をロードしました。");
            }
        } catch (IOException e) {
            System.err.println("[EMDA-AI] 辞書のロードに失敗しました: " + e.getMessage());
        }
    }

    /**
     * [完成] JSON保存処理
     * 学習結果や動的な追加を永続化
     */
    public void saveDictionary() {
        try (Writer writer = new FileWriter(dictionaryFile)) {
            gson.toJson(vDictionary, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}