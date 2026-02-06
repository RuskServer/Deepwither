package com.lunar_prototype.deepwither.llm;

import com.google.gson.Gson;
import com.lunar_prototype.deepwither.aethelgard.LocationDetails;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

/**
 * 外部のLLM推論サーバーと通信するためのクライアントクラス。
 */
public class LlmClient {

    // LLMサーバーのAPIエンドポイント (llama.cppなどを想定)
    private final String apiEndpoint = "http://192.168.11.23:9090/completion";
    private final HttpClient httpClient;
    private static final Gson GSON = new Gson();

    public LlmClient() {
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * LLMにプロンプトを送信し、生成されたテキストを受け取ります。
     * * @param prompt LLMに渡す指示文
     * @return LLMが生成したテキスト。通信失敗時は null を返す
     */
    public String generateText(String prompt) {
        // 構造体を使ってリクエストボディを構成
        Map<String, Object> requestData = Map.of(
                "prompt", prompt,
                "n_predict", 256,
                "temperature", 0.7,
                "stop", List.of("<END>")
        );

        String requestBody = GSON.toJson(requestData);

        System.out.println("LLM Request to: " + apiEndpoint);
        System.out.println("Request Body: " + requestBody); // 送信するJSONを確認

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiEndpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("LLM Response Status: " + response.statusCode());

            if (response.statusCode() == 200) {
                // デバッグ: 応答ボディ全体を表示
                System.out.println("LLM Raw Response Body:\n" + response.body());
                return parseLlmResponse(response.body());
            } else {
                System.err.println("LLM API呼び出しに失敗: ステータスコード " + response.statusCode());
                System.err.println("応答本文: " + response.body());
                return null;
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("LLMサーバーへの接続エラー: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private String parseLlmResponse(String responseBody) {
        try {
            LlmResponse response = GSON.fromJson(responseBody, LlmResponse.class);

            if (response != null && response.content != null) {
                String result = response.content.trim();
                System.out.println("LLM Parsed Content successfully.");
                return result;
            } else {
                System.err.println("JSONパース成功、しかし 'content' フィールドが null または空です。");
                // デバッグ: 応答の content フィールドが null だった場合のログ
                System.out.println("LlmResponse content field was null for body: " + responseBody);
                return null;
            }
        } catch (Exception e) {
            System.err.println("JSON応答のパース中にエラーが発生しました。");
            e.printStackTrace();
            return null;
        }
    }

    // ... (既存の fallbackTextGenerator メソッドは省略せず残す)
    public String fallbackTextGenerator(LocationDetails location, String targetMob, String motivation) {
        // 事前定義されたテンプレート（運用者が用意）を埋める
        return String.format(
                "【ギルドからの通達】\n" +
                        "現在、システムエラーにより詳細な依頼文を生成できません。%sにて、%sによる%sが確認されています。緊急対応をお願いします。",
                location, targetMob, motivation
        );
    }

    private static class LlmResponse {
        // LLMが生成したメインのテキストコンテンツ
        public String content;

        // その他、durationやtokensなどの情報があっても無視できます
        // public int generation_duration;
    }
}