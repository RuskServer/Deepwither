package com.lunar_prototype.deepwither.textai;

import com.lunar_prototype.deepwither.seeker.LiquidNeuron;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * EMDA_LanguageAI v3.1
 * 1. Semantic Embedding: 単語を多次元ベクトルとして定義
 * 2. LNN Attention: LNNの電位とベクトルの「コサイン類似度」で単語を選択
 * 3. Dynamic Grammer: 文脈(context)の強さに応じて助詞を動的に挿入
 */
public class EMDALanguageAI {
    private final LiquidNeuron logic = new LiquidNeuron(0.1);
    private final LiquidNeuron emotion = new LiquidNeuron(0.15);
    private final LiquidNeuron context = new LiquidNeuron(0.08);

    // 単語ノード：テキストと「意味ベクトル」を保持
    private static class WordNode {
        String text;
        // [0]:論理度, [1]:感情度, [2]:緊急度
        float[] vector;

        WordNode(String text, float l, float e, float u) {
            this.text = text;
            this.vector = new float[]{l, e, u};
        }
    }

    private final Map<Long, List<WordNode>> vDictionary = new ConcurrentHashMap<>();
    private final Map<String, Float> wordFatigueMap = new HashMap<>();

    public EMDALanguageAI() {
        setupSemanticDictionary();
    }

    private void setupSemanticDictionary() {
        // --- カテゴリ100: システム・アセット (主語/対象) ---
        addVWord(100L, "レンダリングパイプライン", 0.95f, 0.1f, 0.4f);
        addVWord(100L, "物理演算エンジン", 0.9f, 0.2f, 0.5f);
        addVWord(100L, "シェーダーキャッシュ", 0.85f, 0.1f, 0.6f);
        addVWord(100L, "パーティクル制御", 0.7f, 0.6f, 0.4f);
        addVWord(100L, "サーバー同期", 0.9f, 0.3f, 0.8f);
        addVWord(100L, "敵AIルーチン", 0.8f, 0.4f, 0.5f);
        addVWord(100L, "メモリリーク", 0.9f, 0.1f, 1.0f);
        addVWord(100L, "新規モーション", 0.5f, 0.7f, 0.4f);
        addVWord(100L, "テクスチャ解像度", 0.7f, 0.5f, 0.3f);
        addVWord(100L, "ユーザーUI", 0.6f, 0.6f, 0.5f);
        addVWord(100L, "データベース", 1.0f, 0.0f, 0.5f);
        addVWord(100L, "ライティング設定", 0.6f, 0.8f, 0.3f);
        addVWord(100L, "デバッグコンソール", 0.9f, 0.1f, 0.7f);
        addVWord(100L, "バックアップデータ", 1.0f, 0.0f, 0.9f);
        addVWord(100L, "プロトタイプモデル", 0.5f, 0.5f, 0.4f);
        addVWord(100L, "最終ビルド", 0.8f, 0.6f, 0.9f);
        addVWord(100L, "スクリプト実行", 0.9f, 0.2f, 0.6f);
        addVWord(100L, "エフェクト素材", 0.4f, 0.8f, 0.3f);
        addVWord(100L, "衝突判定", 0.8f, 0.3f, 0.7f);
        addVWord(100L, "並列処理", 0.95f, 0.1f, 0.5f);
        addVWord(100L, "座標系エラー", 0.8f, 0.2f, 0.8f);
        addVWord(100L, "サウンドバッファ", 0.7f, 0.4f, 0.5f);
        addVWord(100L, "ネットワーク遅延", 0.7f, 0.5f, 0.9f);
        addVWord(100L, "最適化コード", 1.0f, 0.2f, 0.6f);
        addVWord(100L, "入力デバイス", 0.8f, 0.3f, 0.4f);

        // --- カテゴリ200: 動作・状態 (動詞/述語) ---
        addVWord(200L, "を最適化した", 0.9f, 0.3f, 0.5f);
        addVWord(200L, "を破壊した", 0.1f, 0.9f, 0.8f);
        addVWord(200L, "を再構築した", 0.8f, 0.5f, 0.6f);
        addVWord(200L, "を検証している", 0.95f, 0.1f, 0.4f);
        addVWord(200L, "を実装し終えた", 0.7f, 0.8f, 0.6f);
        addVWord(200L, "を見落としていた", 0.3f, 0.7f, 0.9f);
        addVWord(200L, "が正常に動作する", 0.9f, 0.4f, 0.2f);
        addVWord(200L, "がクラッシュした", 0.2f, 0.9f, 1.0f);
        addVWord(200L, "をデプロイした", 0.8f, 0.5f, 0.7f);
        addVWord(200L, "を差し替えた", 0.6f, 0.4f, 0.5f);
        addVWord(200L, "を削除した", 0.7f, 0.3f, 0.6f);
        addVWord(200L, "を追加実装した", 0.7f, 0.7f, 0.5f);
        addVWord(200L, "を統合した", 0.9f, 0.3f, 0.4f);
        addVWord(200L, "を見失った", 0.2f, 0.8f, 0.8f);
        addVWord(200L, "を修正完了した", 0.9f, 0.5f, 0.6f);
        addVWord(200L, "が暴走している", 0.1f, 1.0f, 1.0f);
        addVWord(200L, "を無視した", 0.4f, 0.5f, 0.7f);
        addVWord(200L, "を評価した", 0.9f, 0.2f, 0.3f);
        addVWord(200L, "が静止した", 0.6f, 0.2f, 0.4f);
        addVWord(200L, "をテスト中だ", 0.8f, 0.3f, 0.5f);
        addVWord(200L, "を上書きした", 0.5f, 0.4f, 0.8f);
        addVWord(200L, "を再起動した", 0.7f, 0.4f, 0.8f);
        addVWord(200L, "を承認した", 0.9f, 0.5f, 0.3f);
        addVWord(200L, "が限界に達した", 0.3f, 0.9f, 0.9f);
        addVWord(200L, "をマージした", 0.9f, 0.2f, 0.5f);

        // --- カテゴリ300: 文脈・強調 (副詞/接続詞) ---
        addVWord(300L, "意図せず", 0.4f, 0.6f, 0.8f);
        addVWord(300L, "計画通り", 1.0f, 0.3f, 0.2f);
        addVWord(300L, "ついに", 0.3f, 1.0f, 0.7f);
        addVWord(300L, "残念ながら", 0.4f, 0.8f, 0.5f);
        addVWord(300L, "劇的に", 0.5f, 0.9f, 0.6f);
        addVWord(300L, "淡々と", 0.9f, 0.1f, 0.2f);
        addVWord(300L, "強引に", 0.3f, 0.7f, 0.8f);
        addVWord(300L, "慎重に", 0.9f, 0.3f, 0.4f);
        addVWord(300L, "不意に", 0.2f, 0.7f, 0.9f);
        addVWord(300L, "完全に", 0.8f, 0.6f, 0.5f);
        addVWord(300L, "わずかに", 0.6f, 0.3f, 0.3f);
        addVWord(300L, "至急", 0.5f, 0.6f, 1.0f);
        addVWord(300L, "あえて", 0.6f, 0.6f, 0.4f);
        addVWord(300L, "まさか", 0.1f, 1.0f, 0.8f);
        addVWord(300L, "ようやく", 0.4f, 0.9f, 0.6f);
        addVWord(300L, "常に", 0.9f, 0.2f, 0.2f);
        addVWord(300L, "一度だけ", 0.7f, 0.4f, 0.5f);
        addVWord(300L, "繰り返し", 0.8f, 0.3f, 0.4f);
        addVWord(300L, "自動的に", 0.95f, 0.1f, 0.3f);
        addVWord(300L, "致命的に", 0.4f, 0.8f, 1.0f);
        addVWord(300L, "効率よく", 0.9f, 0.5f, 0.4f);
        addVWord(300L, "泥臭く", 0.3f, 0.8f, 0.5f);
        addVWord(300L, "論理的に", 1.0f, 0.1f, 0.3f);
        addVWord(300L, "感覚的に", 0.2f, 0.9f, 0.4f);
        addVWord(300L, "突発的に", 0.2f, 0.7f, 0.9f);

        // --- カテゴリ400: 形容詞・評価 ---
        addVWord(400L, "高精度な", 0.95f, 0.2f, 0.3f);
        addVWord(400L, "不安定な", 0.3f, 0.6f, 0.8f);
        addVWord(400L, "美しい", 0.2f, 0.9f, 0.2f);
        addVWord(400L, "不可解な", 0.5f, 0.7f, 0.7f);
        addVWord(400L, "画期的な", 0.6f, 0.9f, 0.5f);
        addVWord(400L, "退屈な", 0.5f, 0.2f, 0.1f);
        addVWord(400L, "深刻な", 0.6f, 0.7f, 0.9f);
        addVWord(400L, "快適な", 0.4f, 0.8f, 0.2f);
        addVWord(400L, "旧式の", 0.7f, 0.3f, 0.4f);
        addVWord(400L, "未知の", 0.4f, 0.8f, 0.6f);
        addVWord(400L, "複雑な", 0.8f, 0.4f, 0.6f);
        addVWord(400L, "シンプルな", 0.9f, 0.4f, 0.3f);
        addVWord(400L, "巨大な", 0.5f, 0.7f, 0.5f);
        addVWord(400L, "繊細な", 0.6f, 0.8f, 0.3f);
        addVWord(400L, "暫定的な", 0.8f, 0.3f, 0.6f);
        addVWord(400L, "絶対的な", 1.0f, 0.4f, 0.5f);
        addVWord(400L, "絶望的な", 0.2f, 1.0f, 1.0f);
        addVWord(400L, "理想的な", 0.6f, 0.9f, 0.3f);
        addVWord(400L, "冗長な", 0.8f, 0.2f, 0.4f);
        addVWord(400L, "革新的な", 0.5f, 0.9f, 0.6f);
        addVWord(400L, "堅牢な", 0.95f, 0.3f, 0.4f);
        addVWord(400L, "脆弱な", 0.5f, 0.5f, 0.8f);
        addVWord(400L, "最適な", 0.9f, 0.5f, 0.4f);
        addVWord(400L, "無意味な", 0.4f, 0.4f, 0.5f);
        addVWord(400L, "鮮やかな", 0.3f, 1.0f, 0.4f);
    }

    private void addVWord(long cat, String txt, float l, float e, float u) {
        vDictionary.computeIfAbsent(cat, k -> new ArrayList<>()).add(new WordNode(txt, l, e, u));
    }

    public String generateResponse(String input, double urgency) {
        // 入力解析（簡易的な意図抽出）
        float inL = input.contains("Ver") || input.contains("実装") ? 1.0f : 0.0f;
        float inE = input.contains("!") || input.contains("?") ? 1.0f : 0.0f;

        // LNN更新
        logic.update(inL, urgency);
        emotion.update(inE, urgency);
        context.update((inL + inE) > 0 ? 1.0 : 0.0, urgency);

        return assembleSemanticSentence();
    }

    private String assembleSemanticSentence() {
        // 現在のLNNの状態を「クエリベクトル」とする (AttentionのQuery)
        float[] query = {(float)logic.get(), (float)emotion.get(), (float)context.get()};

        StringBuilder sb = new StringBuilder();

        // 1. 修飾語の選定 (文脈が深い場合のみ)
        if (query[2] > 0.4) {
            sb.append(attentionSelect(300L, query)).append("、");
        }

        // 2. 主体と動作の結合
        sb.append(attentionSelect(100L, query));
        sb.append(attentionSelect(200L, query));

        return sb.toString();
    }

    /**
     * Attentionメカニズムの模倣
     * LNNの現在の「ポテンシャル波形」と最も近いベクトルを持つ単語を抽出
     */
    private String attentionSelect(long cat, float[] query) {
        List<WordNode> nodes = vDictionary.get(cat);
        if (nodes == null) return "";

        return nodes.stream().max(Comparator.comparingDouble(node -> {
            // コサイン類似度に近い計算（ベクトルの内積）
            double score = 0;
            for (int i = 0; i < query.length; i++) {
                score += query[i] * node.vector[i];
            }
            // 疲労度(Fatigue)による抑制
            float f = wordFatigueMap.getOrDefault(node.text, 0.0f);
            return score * (1.0 - f);
        })).map(n -> n.text).orElse("...");
    }
}