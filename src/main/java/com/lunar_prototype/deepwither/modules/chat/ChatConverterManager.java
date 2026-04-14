package com.lunar_prototype.deepwither.modules.chat;

import com.lunar_prototype.deepwither.util.RomajiToHiragana;
import com.lunar_prototype.deepwither.util.IManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class ChatConverterManager implements IManager {

    private final HttpClient httpClient;
    private final Map<String, String> conversionCache = new ConcurrentHashMap<>();
    private static final String API_URL = "http://www.google.com/transliterate?langpair=ja-Hira%7Cja&text=";
    private static final Pattern ROMAJI_PATTERN = Pattern.compile("^[a-zA-Z0-9\\s\\-\\.,!\\?\\(\\)]+$");

    public ChatConverterManager() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    @Override
    public void init() {}

    @Override
    public void shutdown() {
        conversionCache.clear();
    }

    public boolean isRomaji(String text) {
        // アルファベットと記号が主体のメッセージを変換対象とする
        return ROMAJI_PATTERN.matcher(text).matches() && text.length() > 1;
    }

    public CompletableFuture<String> convert(String input) {
        if (conversionCache.containsKey(input)) {
            return CompletableFuture.completedFuture(conversionCache.get(input));
        }

        // 1. ローマ字 -> ひらがな
        String hiragana = RomajiToHiragana.convert(input);

        // 2. ひらがな -> 漢字 (Google API)
        return fetchFromGoogleAPI(hiragana).thenApply(converted -> {
            if (converted != null && !converted.isEmpty()) {
                conversionCache.put(input, converted);
                return converted;
            }
            return hiragana; // 失敗時はひらがなを返す
        });
    }

    private CompletableFuture<String> fetchFromGoogleAPI(String hiragana) {
        try {
            String encoded = URLEncoder.encode(hiragana, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL + encoded))
                    .GET()
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() == 200) {
                            return parseGoogleResponse(response.body());
                        }
                        return hiragana;
                    }).exceptionally(ex -> {
                        ex.printStackTrace();
                        return hiragana;
                    });
        } catch (Exception e) {
            e.printStackTrace();
            return CompletableFuture.completedFuture(hiragana);
        }
    }

    private String parseGoogleResponse(String body) {
        try {
            // Google API response format: [["original", ["candidate1", "candidate2", ...]], ...]
            JsonArray jsonArray = JsonParser.parseString(body).getAsJsonArray();
            StringBuilder result = new StringBuilder();
            for (JsonElement element : jsonArray) {
                JsonArray segment = element.getAsJsonArray();
                JsonArray candidates = segment.get(1).getAsJsonArray();
                if (candidates.size() > 0) {
                    result.append(candidates.get(0).getAsString()); // 最初の候補を使用
                }
            }
            return result.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
