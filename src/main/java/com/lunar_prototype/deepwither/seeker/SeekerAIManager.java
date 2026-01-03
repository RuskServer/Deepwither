package com.lunar_prototype.deepwither.seeker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class SeekerAIManager {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // JavaオブジェクトをJSON文字列に変換 (LLMへの送信前)
    public String serializeContext(BanditContext context) {
        return gson.toJson(context);
    }

    // JSON文字列をJavaオブジェクトに変換 (LLMからの受信後)
    public BanditDecision deserializeDecision(String json) {
        try {
            return gson.fromJson(json, BanditDecision.class);
        } catch (Exception e) {
            // パース失敗時のフォールバック処理が必要
            return null;
        }
    }
}
