package com.lunar_prototype.deepwither.seeker;

import ai.onnxruntime.*;
import com.google.gson.Gson;
import com.lunar_prototype.deepwither.Deepwither;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.nio.LongBuffer;

public class LLMConnector {
    private final OrtEnvironment env;
    private final OrtSession session;
    private final DeepwitherTokenizer tokenizer;
    private final Gson gson = new Gson();
    private final JavaPlugin plugin;

    public LLMConnector(JavaPlugin plugin, String modelPath) throws OrtException {
        this.plugin = plugin;
        this.env = OrtEnvironment.getEnvironment();
        this.session = env.createSession(modelPath, new OrtSession.SessionOptions());
        this.tokenizer = new DeepwitherTokenizer();
    }

    public void fetchDecisionAsync(BanditContext context, Consumer<BanditDecision> callback) {
        CompletableFuture.supplyAsync(() -> {
            try {
                // 1. シリアライズ & トークナイズ
                String jsonInput = gson.toJson(context);
                long[] inputIds = tokenizer.encode(jsonInput);

                // 2. ONNX用テンソルの作成 [1, sequence_length]
                long[] shape = new long[]{1, inputIds.length};
                try (OnnxTensor inputTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), shape)) {

                    // 3. 推論実行 (モデルの入力名 "input" は自作モデルに合わせる)
                    try (OrtSession.Result results = session.run(Collections.singletonMap("input", inputTensor))) {

                        // 4. 出力のデコード (モデルの出力がトークンID配列 [1, seq_out] の場合)
                        long[] outputIds = (long[]) results.get(0).getValue();
                        String decodedJson = tokenizer.decode(outputIds);

                        return gson.fromJson(decodedJson, BanditDecision.class);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }).thenAccept(decision -> {
            // 5. Bukkitメインスレッドへコールバック
            if (decision != null) {
                Bukkit.getScheduler().runTask(Deepwither.getInstance(), () -> callback.accept(decision));
            }
        });
    }

    /**
     * リソースの解放
     * プラグインの onDisable() 等で呼び出す
     */
    public void close() {
        try {
            if (session != null) {
                session.close();
            }
            if (env != null) {
                env.close();
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to close ONNX Runtime: " + e.getMessage());
        }
    }
}
