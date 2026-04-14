package com.lunar_prototype.deepwither.util;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class RomajiToHiragana {

    private static final TreeMap<String, String> ROMAJI_MAP = new TreeMap<>((a, b) -> b.length() - a.length() != 0 ? b.length() - a.length() : a.compareTo(b));

    static {
        // 基本母音
        ROMAJI_MAP.put("a", "あ"); ROMAJI_MAP.put("i", "い"); ROMAJI_MAP.put("u", "う"); ROMAJI_MAP.put("e", "え"); ROMAJI_MAP.put("o", "お");
        // か行
        ROMAJI_MAP.put("ka", "か"); ROMAJI_MAP.put("ki", "き"); ROMAJI_MAP.put("ku", "く"); ROMAJI_MAP.put("ke", "け"); ROMAJI_MAP.put("ko", "こ");
        // さ行
        ROMAJI_MAP.put("sa", "さ"); ROMAJI_MAP.put("si", "し"); ROMAJI_MAP.put("su", "す"); ROMAJI_MAP.put("se", "せ"); ROMAJI_MAP.put("so", "そ");
        ROMAJI_MAP.put("shi", "し");
        // た行
        ROMAJI_MAP.put("ta", "た"); ROMAJI_MAP.put("ti", "ち"); ROMAJI_MAP.put("tu", "つ"); ROMAJI_MAP.put("te", "て"); ROMAJI_MAP.put("to", "と");
        ROMAJI_MAP.put("chi", "ち"); ROMAJI_MAP.put("tsu", "つ");
        // な行
        ROMAJI_MAP.put("na", "な"); ROMAJI_MAP.put("ni", "に"); ROMAJI_MAP.put("nu", "ぬ"); ROMAJI_MAP.put("ne", "ね"); ROMAJI_MAP.put("no", "の");
        // は行
        ROMAJI_MAP.put("ha", "は"); ROMAJI_MAP.put("hi", "ひ"); ROMAJI_MAP.put("hu", "ふ"); ROMAJI_MAP.put("he", "へ"); ROMAJI_MAP.put("ho", "ほ");
        ROMAJI_MAP.put("fu", "ふ");
        // ま行
        ROMAJI_MAP.put("ma", "ま"); ROMAJI_MAP.put("mi", "み"); ROMAJI_MAP.put("mu", "む"); ROMAJI_MAP.put("me", "め"); ROMAJI_MAP.put("mo", "も");
        // や行
        ROMAJI_MAP.put("ya", "や"); ROMAJI_MAP.put("yu", "ゆ"); ROMAJI_MAP.put("yo", "よ");
        // ら行
        ROMAJI_MAP.put("ra", "ら"); ROMAJI_MAP.put("ri", "り"); ROMAJI_MAP.put("ru", "る"); ROMAJI_MAP.put("re", "れ"); ROMAJI_MAP.put("ro", "ろ");
        // わ行
        ROMAJI_MAP.put("wa", "わ"); ROMAJI_MAP.put("wo", "を"); ROMAJI_MAP.put("nn", "ん"); ROMAJI_MAP.put("n", "ん");
        
        // 濁音・半濁音
        ROMAJI_MAP.put("ga", "が"); ROMAJI_MAP.put("gi", "ぎ"); ROMAJI_MAP.put("gu", "ぐ"); ROMAJI_MAP.put("ge", "げ"); ROMAJI_MAP.put("go", "ご");
        ROMAJI_MAP.put("za", "ざ"); ROMAJI_MAP.put("zi", "じ"); ROMAJI_MAP.put("zu", "ず"); ROMAJI_MAP.put("ze", "ぜ"); ROMAJI_MAP.put("zo", "ぞ");
        ROMAJI_MAP.put("ji", "じ");
        ROMAJI_MAP.put("da", "だ"); ROMAJI_MAP.put("di", "ぢ"); ROMAJI_MAP.put("du", "づ"); ROMAJI_MAP.put("de", "で"); ROMAJI_MAP.put("do", "ど");
        ROMAJI_MAP.put("ba", "ば"); ROMAJI_MAP.put("bi", "び"); ROMAJI_MAP.put("bu", "ぶ"); ROMAJI_MAP.put("be", "べ"); ROMAJI_MAP.put("bo", "ぼ");
        ROMAJI_MAP.put("pa", "ぱ"); ROMAJI_MAP.put("pi", "ぴ"); ROMAJI_MAP.put("pu", "ぷ"); ROMAJI_MAP.put("pe", "ぺ"); ROMAJI_MAP.put("po", "ぽ");

        // 拗音
        ROMAJI_MAP.put("kya", "きゃ"); ROMAJI_MAP.put("kyu", "きゅう"); ROMAJI_MAP.put("kyo", "きょ");
        ROMAJI_MAP.put("sya", "しゃ"); ROMAJI_MAP.put("syu", "しゅ"); ROMAJI_MAP.put("syo", "しょ");
        ROMAJI_MAP.put("sha", "しゃ"); ROMAJI_MAP.put("shu", "しゅ"); ROMAJI_MAP.put("sho", "しょ");
        ROMAJI_MAP.put("tya", "ちゃ"); ROMAJI_MAP.put("tyu", "ちゅ"); ROMAJI_MAP.put("tyo", "ちょ");
        ROMAJI_MAP.put("cha", "ちゃ"); ROMAJI_MAP.put("chu", "ちゅ"); ROMAJI_MAP.put("cho", "ちょ");
        ROMAJI_MAP.put("nya", "にゃ"); ROMAJI_MAP.put("nyu", "にゅう"); ROMAJI_MAP.put("nyo", "にょ");
        ROMAJI_MAP.put("hya", "ひゃ"); ROMAJI_MAP.put("hyu", "ひゅう"); ROMAJI_MAP.put("hyo", "ひょ");
        ROMAJI_MAP.put("mya", "みゃ"); ROMAJI_MAP.put("myu", "みゅう"); ROMAJI_MAP.put("myo", "みょ");
        ROMAJI_MAP.put("rya", "りゃ"); ROMAJI_MAP.put("ryu", "りゅう"); ROMAJI_MAP.put("ryo", "りょ");
        ROMAJI_MAP.put("gya", "ぎゃ"); ROMAJI_MAP.put("gyu", "ぎゅう"); ROMAJI_MAP.put("gyo", "ぎょ");
        ROMAJI_MAP.put("zya", "じゃ"); ROMAJI_MAP.put("zyu", "じゅ"); ROMAJI_MAP.put("zyo", "じょ");
        ROMAJI_MAP.put("ja", "じゃ"); ROMAJI_MAP.put("ju", "じゅ"); ROMAJI_MAP.put("jo", "じょ");
        ROMAJI_MAP.put("bya", "びゃ"); ROMAJI_MAP.put("byu", "びゅう"); ROMAJI_MAP.put("byo", "びょ");
        ROMAJI_MAP.put("pya", "ぴゃ"); ROMAJI_MAP.put("pyu", "ぴゅう"); ROMAJI_MAP.put("pyo", "ぴょ");

        // 小書き文字 (ぁぃぅぇぉ等)
        ROMAJI_MAP.put("xa", "ぁ"); ROMAJI_MAP.put("xi", "ぃ"); ROMAJI_MAP.put("xu", "ぅ"); ROMAJI_MAP.put("xe", "ぇ"); ROMAJI_MAP.put("xo", "ぉ");
        ROMAJI_MAP.put("la", "ぁ"); ROMAJI_MAP.put("li", "ぃ"); ROMAJI_MAP.put("lu", "ぅ"); ROMAJI_MAP.put("le", "ぇ"); ROMAJI_MAP.put("lo", "ぉ");
        ROMAJI_MAP.put("xya", "ゃ"); ROMAJI_MAP.put("xyu", "ゅ"); ROMAJI_MAP.put("xyo", "ょ");
        ROMAJI_MAP.put("lya", "ゃ"); ROMAJI_MAP.put("lyu", "ゅ"); ROMAJI_MAP.put("lyo", "ょ");
        ROMAJI_MAP.put("xtu", "っ"); ROMAJI_MAP.put("ltu", "っ");

        // その他
        ROMAJI_MAP.put("-", "ー");
    }

    public static String convert(String text) {
        if (text == null || text.isEmpty()) return text;

        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            boolean found = false;
            
            // 促音 (っ) の処理: 同じ子音が続く場合 (nn以外)
            if (i + 1 < text.length() && text.charAt(i) == text.charAt(i + 1) && 
                isConsonant(text.charAt(i)) && text.charAt(i) != 'n') {
                result.append("っ");
                i++;
                continue;
            }

            // マッピング検索
            for (Map.Entry<String, String> entry : ROMAJI_MAP.entrySet()) {
                String key = entry.getKey();
                if (text.startsWith(key, i)) {
                    result.append(entry.getValue());
                    i += key.length();
                    found = true;
                    break;
                }
            }

            if (!found) {
                result.append(text.charAt(i));
                i++;
            }
        }
        return result.toString();
    }

    private static boolean isConsonant(char c) {
        return "bcdfghjklmnpqrstvwxyz".indexOf(Character.toLowerCase(c)) != -1;
    }
}
