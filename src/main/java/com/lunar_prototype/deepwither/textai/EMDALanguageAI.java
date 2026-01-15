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
    private final LiquidNeuron valence = new LiquidNeuron(0.12); // 正負 (Positive/Negative)

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
     * [改良版] Resonance Tuning (スパルタ教育)
     * より厳密なトリミングとパース処理
     */
    public void trainFromCSVAsync(File csvFile) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            long startTime = System.currentTimeMillis();
            int count = 0;
            int skipCount = 0;
            Map<Long, Integer> stats = new HashMap<>();

            try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
                String line;
                br.readLine(); // ヘッダースキップ

                while ((line = br.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;

                    // カンマ分割の際、前後の空白や引用符をより強力に除去
                    String[] parts = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
                    if (parts.length < 2) {
                        skipCount++;
                        continue;
                    }

                    // タグ文字列の正規化：引用符、括弧、空白をすべて除去
                    String rawTag = parts[0].replaceAll("[\\[\\]\"\\s]", "");
                    // セリフの正規化：前後の引用符と空白を除去
                    String phrase = parts[1].trim().replaceAll("^\"|\"$", "").trim();

                    if (phrase.isEmpty()) {
                        skipCount++;
                        continue;
                    }

                    float[] v = convertTagToVector(rawTag);
                    logic.update(v[0], v[2]);
                    emotion.update(v[1], v[2]);
                    context.update(1.0, v[2]);

                    float[] fingerprint = {(float)logic.get(), (float)emotion.get(), (float)context.get()};

                    // カテゴリ分類のロジック
                    long cat;

                    if (v[0] > 0.7 || phrase.contains("システム") || phrase.contains("実装")) {
                        // 【知識層】
                        cat = (v[1] < 0.4) ? 101L : 102L; // 冷静なら報告、感情が動いていれば不具合・調整系
                    }
                    else if (phrase.endsWith("？") || phrase.contains("なのだ") || phrase.contains("だろう")) {
                        // 【修飾・問いかけ層】
                        cat = (v[1] > 0.5) ? 302L : 301L; // 感情的なら驚き、冷静なら肯定
                    }
                    else if (v[2] > 0.6) {
                        // 【アクション・動的なセリフ】
                        cat = (v[1] > 0.6) ? 202L : 201L; // 怒り/興奮なら激しい動き、そうでなければ通常動作
                    }
                    else {
                        // 【挨拶・日常会話】
                        cat = (v[1] > 0.7) ? 402L : 401L; // 感情値の高さで「敵対」か「友好」を分ける
                    }

                    addVWord(cat, phrase, fingerprint);
                    stats.put(cat, stats.getOrDefault(cat, 0) + 1);
                    count++;
                }

                saveDictionary();
                System.out.println("[EMDA-AI] Tuning 完了: " + count + "件成功 (Time: " + (System.currentTimeMillis() - startTime) + "ms)");
            } catch (Exception e) {
                System.err.println("[EMDA-AI] 学習エラー: " + e.getMessage());
            }
        });
    }

    /**
     * 文字列タグを [Logic, Emotion, Urgency] の数値に変換するマッパー
     */
    private float[] convertTagToVector(String rawTag) {
        float l = 0.5f, e = 0.5f, u = 0.2f;
        String tagLower = rawTag.toLowerCase();

        if (tagLower.contains("battle")) { l = 0.2f; u = 0.8f; }
        if (tagLower.contains("daily") || tagLower.contains("relax")) { l = 0.1f; u = 0.1f; }
        if (tagLower.contains("calm")) { e = 0.1f; }
        if (tagLower.contains("angry") || tagLower.contains("pinch")) { e = 0.9f; u = 0.9f; }
        if (tagLower.contains("friendly") || tagLower.contains("victory")) { e = 0.3f; l = 0.8f; }

        return new float[]{l, e, u};
    }

    private void addVWord(long cat, String txt, float[] v) {
        vDictionary.computeIfAbsent(cat, k -> new ArrayList<>())
                .add(new WordNode(txt, v));
    }

    /**
     * 文章生成ロジック (v4.2 Valence連動版)
     * @param input   入力メッセージ
     * @param urgency 緊急度 (0.0 - 1.0)
     * @param val     感情価 (1.0: 友好/快, 0.0: 敵対/不快)
     */
    public String generateResponse(String input, double urgency, double val) {
        // 1. 引数の valence を LNN に直接注入
        updateLNNStateWithValence(input, urgency, val);

        float[] q = {
                (float)logic.get(),
                (float)emotion.get(),
                (float)valence.get(), // 追加した Valence ニューロン
                (float)context.get()
        };
        StringBuilder sb = new StringBuilder();

        // 2. Valence に基づく枕詞の選択 (301: 肯定的, 302: 否定的)
        long prefixCat = (q[2] > 0.5) ? 301L : 302L;
        String prefix = attentionSelect(prefixCat, q);
        if (!prefix.isEmpty()) sb.append(prefix).append("、");

        // 3. メインコンテンツの分岐
        if (q[0] > 0.7) {
            // 論理・システム報告
            sb.append(attentionSelect(q[2] > 0.5 ? 101L : 102L, q));
            sb.append(attentionSelect(201L, q));
        } else {
            // 4. 感情・挨拶 (ここが「挨拶への煽り」を防ぐキモ)
            long targetCat;
            if (q[1] > 0.6) {
                // 高揚状態: 歓喜(403) か 激怒(402)
                targetCat = (q[2] > 0.5) ? 403L : 402L;
            } else {
                // 平穏状態: 友好(401) か 警戒(404)
                targetCat = (q[2] > 0.5) ? 401L : 404L;
            }
            sb.append(attentionSelect(targetCat, q));
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

    /**
     * LNN状態更新 (Valence対応)
     */
    private void updateLNNStateWithValence(String input, double urgency, double val) {
        logic.update(input.matches(".*(Ver|実装|修正).*") ? 1.0 : 0.0, urgency);
        emotion.update(input.matches(".*(!|\\?|どけ|殺).*") ? 1.0 : 0.0, urgency);

        // 引数で受け取った valence を直接ニューロンに反映
        this.valence.update(val, urgency);

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