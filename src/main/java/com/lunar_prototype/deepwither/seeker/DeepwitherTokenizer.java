package com.lunar_prototype.deepwither.seeker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeepwitherTokenizer {
    private final Map<String, Integer> vocab = new HashMap<>();
    private final Map<Integer, String> inverseVocab = new HashMap<>();

    public DeepwitherTokenizer() {
        // 1. 特殊トークン
        addToken("<PAD>", 0);
        addToken("<SOS>", 1);
        addToken("<EOS>", 2);
        addToken("<UNK>", 3);

        // 2. BanditContext の構造用トークン (JSONキー)
        addToken("entity", 10);
        addToken("hp_pct", 11);
        addToken("max_hp", 12);
        addToken("inventory", 13);
        addToken("stance", 14);
        addToken("environment", 15);
        addToken("nearby_enemies", 16);
        addToken("nearby_allies", 17);
        addToken("nearest_cover", 18);
        addToken("last_action", 19);
        addToken("personality", 20);

        // 3. 状態・ラベルトークン (Value)
        addToken("low", 30);
        addToken("mid", 31);
        addToken("high", 32);
        addToken("HEALTHY", 33);
        addToken("WOUNDED", 34);
        addToken("DEAD", 35);
        addToken("true", 36);
        addToken("false", 37);

        // 4. 区切り文字
        addToken("{", 40);
        addToken("}", 41);
        addToken("[", 42);
        addToken("]", 43);
        addToken(":", 44);
        addToken(",", 45);
    }

    private void addToken(String token, int id) {
        vocab.put(token, id);
        inverseVocab.put(id, token);
    }

    /**
     * BanditContextのJSON文字列をトークンID配列に変換
     */
    public long[] encode(String json) {
        // 正規表現でキー、値、記号を分離
        String[] rawTokens = json.split("(?=[{},:\\[\\]\" ])|(?<=[{},:\\[\\]\" ])");
        List<Long> ids = new ArrayList<>();

        ids.add(1L); // <SOS>

        for (String t : rawTokens) {
            String clean = t.replace("\"", "").trim();
            if (clean.isEmpty()) continue;

            // 辞書にあるか確認、なければ数値変換を試みる、それも無理なら<UNK>
            if (vocab.containsKey(clean)) {
                ids.add((long) vocab.get(clean));
            } else {
                try {
                    // 数値データ（hp_pctやdistなど）は、モデルの設計に応じて
                    // そのままIDにするか、正規化して専用のID範囲に収める
                    // ここでは簡易的に 1000 + 数値 をIDとする（数値トークン領域）
                    int val = (int) Double.parseDouble(clean);
                    ids.add(1000L + val);
                } catch (NumberFormatException e) {
                    ids.add(3L); // <UNK>
                }
            }
        }

        ids.add(2L); // <EOS>
        return ids.stream().mapToLong(l -> l).toArray();
    }

    /**
     * モデルの出力ID配列を文字列（JSON）に復元
     */
    public String decode(long[] ids) {
        StringBuilder sb = new StringBuilder();
        for (long id : ids) {
            if (id == 2L) break; // <EOS>
            if (id <= 2L) continue; // <PAD>, <SOS> スキップ

            if (inverseVocab.containsKey((int) id)) {
                String token = inverseVocab.get((int) id);
                // JSONとしての整形補助（記号以外はスペースを入れるなど）
                if (token.matches("[{}:,\\[\\]]")) {
                    sb.append(token);
                } else {
                    sb.append("\"").append(token).append("\"");
                }
            } else if (id >= 1000L) {
                // 数値トークン領域の復元
                sb.append(id - 1000L);
            }
        }
        return sb.toString();
    }
}