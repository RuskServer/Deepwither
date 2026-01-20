package com.lunar_prototype.deepwither.market.api;

import com.google.gson.Gson;
import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.market.GlobalMarketManager;
import com.lunar_prototype.deepwither.market.MarketListing;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import github.scarsz.discordsrv.DiscordSRV;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class MarketApiController {

    private final Deepwither plugin;
    private final GlobalMarketManager marketManager;
    private HttpServer server;
    private final Gson gson = new Gson();

    public MarketApiController(Deepwither plugin, GlobalMarketManager marketManager) {
        this.plugin = plugin;
        this.marketManager = marketManager;
    }

    public void start(int port) {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/api/market", new MarketHandler());
            server.setExecutor(null); // デフォルトのエグゼキュータ
            server.start();
            plugin.getLogger().info("Market API Server started on port " + port);
        } catch (IOException e) {
            plugin.getLogger().severe("APIサーバーの起動に失敗しました: " + e.getMessage());
        }
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    private class MarketHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // CORSヘッダー（必要に応じて）
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");

            Map<String, String> params = queryToMap(exchange.getRequestURI().getQuery());
            String discordId = params.get("discordId");

            if (discordId == null || discordId.isEmpty()) {
                sendResponse(exchange, 400, Map.of("error", "Missing discordId parameter"));
                return;
            }

            // 1. DiscordSRVでUUIDを取得
            UUID uuid = DiscordSRV.getPlugin().getAccountLinkManager().getUuid(discordId);

            if (uuid == null) {
                sendResponse(exchange, 404, Map.of("error", "No Minecraft account linked to this Discord ID"));
                return;
            }

            // 2. GlobalMarketManagerから情報を取得
            List<MarketListing> listings = marketManager.getListingsByPlayer(uuid);
            List<MarketListingDTO> dtos = listings.stream()
                    .map(MarketListingDTO::new)
                    .collect(Collectors.toList());

            // 3. レスポンス送信
            Map<String, Object> response = new HashMap<>();
            response.put("uuid", uuid.toString());
            response.put("listings", dtos);

            sendResponse(exchange, 200, response);
        }
    }

    private void sendResponse(HttpExchange exchange, int statusCode, Object data) throws IOException {
        byte[] response = gson.toJson(data).getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }

    private Map<String, String> queryToMap(String query) {
        Map<String, String> result = new HashMap<>();
        if (query == null) return result;
        for (String param : query.split("&")) {
            String[] entry = param.split("=");
            if (entry.length > 1) result.put(entry[0], entry[1]);
        }
        return result;
    }
}