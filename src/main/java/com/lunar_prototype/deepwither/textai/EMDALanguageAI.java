package com.lunar_prototype.deepwither.textai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.lunar_prototype.deepwither.seeker.LiquidNeuron;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * EMDA_LanguageAI v4.5 - Thermodynamic Resonance
 * TQH（熱力学的Q恒常性）を言語生成に適用。
 * システム温度(systemTemperature)により、語彙の「流動性」と「相転移」を制御する。
 */
public class EMDALanguageAI {
    private final LiquidNeuron logic = new LiquidNeuron(0.1);
    private final LiquidNeuron emotion = new LiquidNeuron(0.15);
    private final LiquidNeuron context = new LiquidNeuron(0.08);
    private final LiquidNeuron valence = new LiquidNeuron(0.12);

    // [TQH] 言語エンジンの内部温度
    private float systemTemperature = 0.5f;
    private static final float THERMAL_DECAY = 0.96f;

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

    public EMDALanguageAI(File dataFolder) {
        if (!dataFolder.exists()) dataFolder.mkdirs();
        this.dictionaryFile = new File(dataFolder, "dictionary.json");
        loadDictionary();
    }

    /**
     * [TQH Integrated] Resonance Tuning
     */
    public void trainFromCSVAsync(File csvFile) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
                String line;
                br.readLine();

                while ((line = br.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    String[] parts = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
                    if (parts.length < 2) continue;

                    String rawTag = parts[0].replaceAll("[\\[\\]\"\\s]", "");
                    String phrase = parts[1].trim().replaceAll("^\"|\"$", "").trim();
                    if (phrase.isEmpty()) continue;

                    float[] v = convertTagToVector(rawTag);

                    // [TQH] 学習時の驚き（タグと現在の状態の乖離）を熱源とする
                    float error = (float) (Math.abs(v[0] - logic.get()) + Math.abs(v[1] - emotion.get()));
                    this.systemTemperature += error * 0.1f;

                    // ニューロン更新に温度を反映
                    logic.update(v[0], v[2], systemTemperature);
                    emotion.update(v[1], v[2], systemTemperature);
                    context.update(1.0, v[2], systemTemperature);

                    float[] fingerprint = {(float)logic.get(), (float)emotion.get(), (float)context.get()};

                    // カテゴリ分類（既存ロジック維持）
                    long cat = classifyCategory(v, phrase);
                    addVWord(cat, phrase, fingerprint);
                }
                saveDictionary();
                this.systemTemperature *= 0.5f; // 学習終了後は冷却
            } catch (Exception e) {
                System.err.println("[EMDA-AI] 学習エラー: " + e.getMessage());
            }
        });
    }

    /**
     * [TQH Integrated] 文章生成
     */
    public String generateResponse(String input, double urgency, double val) {
        // 1. 状態更新（Valenceと温度を同期）
        updateLNNStateWithValence(input, urgency, val);

        // 2. [TQH] 入力の激しさや急な感情の変化を加熱として処理
        this.systemTemperature += (float) (urgency * 0.2);

        float[] q = { (float)logic.get(), (float)emotion.get(), (float)valence.get(), (float)context.get() };
        StringBuilder sb = new StringBuilder();

        // 3. [TQH 相転移] 温度に基づいた語彙選択バイアス
        // 高温(GAS): 語彙が不安定になり、より感情的または断片的な言葉を選ぶ
        // 低温(SOLID): 非常に正確で定型的な、ロジカルな言葉を選ぶ

        long prefixCat = (q[2] > 0.5) ? 301L : 302L;
        String prefix = attentionSelect(prefixCat, q);
        if (!prefix.isEmpty()) sb.append(prefix).append("、");

        if (systemTemperature > 1.2f) {
            // 【気体状態: GAS】 支離滅裂または極めて激情的な反応
            sb.append(attentionSelect(402L, q)); // 激怒/混乱
            sb.append("…！");
        } else if (q[0] > 0.7 && systemTemperature < 0.4f) {
            // 【固体状態: SOLID】 冷徹なシステム報告
            sb.append(attentionSelect(q[2] > 0.5 ? 101L : 102L, q));
        } else {
            // 【液体状態: LIQUID】 通常の流動的会話
            long targetCat = (q[1] > 0.6) ? (q[2] > 0.5 ? 403L : 402L) : (q[2] > 0.5 ? 401L : 404L);
            sb.append(attentionSelect(targetCat, q));
        }

        // 自然冷却
        this.systemTemperature = Math.max(0.0f, systemTemperature * THERMAL_DECAY);

        return sb.toString();
    }

    private String attentionSelect(long cat, float[] query) {
        List<WordNode> nodes = vDictionary.get(cat);
        if (nodes == null || nodes.isEmpty()) return "";

        // [TQH] 温度が高いほど、ドット積の結果にノイズ(Entropy)を混ぜ、
        // ベストな単語以外も選ばれやすくする（＝思考の流動性）
        float entropy = Math.max(0, systemTemperature * 0.2f);

        return nodes.stream().max(Comparator.comparingDouble(node -> {
            double dotProduct = 0;
            for (int i = 0; i < query.length; i++) dotProduct += query[i] * node.vector[i];

            float fatigue = wordFatigueMap.getOrDefault(node.text, 0.0f);
            double noise = (Math.random() * entropy); // 温度依存のゆらぎ

            return (dotProduct + noise) * (1.0 - fatigue);
        })).map(n -> n.text).orElse("");
    }

    private void updateLNNStateWithValence(String input, double urgency, double val) {
        logic.update(input.matches(".*(Ver|実装|修正).*") ? 1.0 : 0.0, urgency, systemTemperature);
        emotion.update(input.matches(".*(!|\\?|どけ|殺).*") ? 1.0 : 0.0, urgency, systemTemperature);
        this.valence.update(val, urgency, systemTemperature);
        context.update(1.0, urgency, systemTemperature);
    }

    // --- 既存のユーティリティメソッド群 ---
    private long classifyCategory(float[] v, String phrase) {
        if (v[0] > 0.7 || phrase.contains("システム")) return (v[1] < 0.4) ? 101L : 102L;
        if (phrase.endsWith("？") || phrase.contains("なのだ")) return (v[1] > 0.5) ? 302L : 301L;
        if (v[2] > 0.6) return (v[1] > 0.6) ? 202L : 201L;
        return (v[1] > 0.7) ? 402L : 401L;
    }

    private float[] convertTagToVector(String rawTag) {
        float l = 0.5f, e = 0.5f, u = 0.2f;
        String tagLower = rawTag.toLowerCase();
        if (tagLower.contains("battle")) { l = 0.2f; u = 0.8f; }
        if (tagLower.contains("daily")) { l = 0.1f; u = 0.1f; }
        if (tagLower.contains("calm")) { e = 0.1f; }
        if (tagLower.contains("angry")) { e = 0.9f; u = 0.9f; }
        return new float[]{l, e, u};
    }

    private void addVWord(long cat, String txt, float[] v) {
        vDictionary.computeIfAbsent(cat, k -> new ArrayList<>()).add(new WordNode(txt, v));
    }

    private void loadDictionary() {
        if (!dictionaryFile.exists()) return;
        try (Reader reader = new FileReader(dictionaryFile)) {
            java.lang.reflect.Type type = new TypeToken<Map<Long, List<WordNode>>>(){}.getType();
            Map<Long, List<WordNode>> loadedData = gson.fromJson(reader, type);
            if (loadedData != null) this.vDictionary = new ConcurrentHashMap<>(loadedData);
        } catch (IOException e) { e.printStackTrace(); }
    }

    public void saveDictionary() {
        try (Writer writer = new FileWriter(dictionaryFile)) { gson.toJson(vDictionary, writer); }
        catch (IOException e) { e.printStackTrace(); }
    }
}