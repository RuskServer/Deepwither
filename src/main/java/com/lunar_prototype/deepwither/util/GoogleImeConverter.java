package com.lunar_prototype.deepwither.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public class GoogleImeConverter {
    private static final String API_URL = "https://www.google.com/transliterate?langpair=ja-kana|ja&text=";

    public static String convert(String input) {
        if (input == null || input.isEmpty() || !isRoman(input)) return input;

        try {
            String encoded = URLEncoder.encode(input, StandardCharsets.UTF_8);
            URL url = new URL(API_URL + encoded);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String response = reader.lines().collect(Collectors.joining());
                JsonArray jsonArray = JsonParser.parseString(response).getAsJsonArray();

                StringBuilder result = new StringBuilder();
                for (int i = 0; i < jsonArray.size(); i++) {
                    // APIレスポンス: [["入力", ["候補1", "候補2"]], ...]
                    result.append(jsonArray.get(i).getAsJsonArray().get(1).getAsJsonArray().get(0).getAsString());
                }
                return result.toString();
            }
        } catch (Exception e) {
            return input; // エラー時はそのまま返す
        }
    }

    private static boolean isRoman(String input) {
        return input.matches("^[\\w\\s\\d\\.\\?\\!\\-]+$");
    }
}