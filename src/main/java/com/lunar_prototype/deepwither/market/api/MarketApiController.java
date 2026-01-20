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

            // 従来の特定プレイヤー検索: /api/market?discordId=...
            server.createContext("/api/market", new MarketHandler());

            // 追加: 全出品アイテム取得: /api/market/all
            server.createContext("/api/market/all", new AllListingsHandler());

            server.setExecutor(null);
            server.start();
            plugin.getLogger().info("Market API Server started on port " + port);
        } catch (IOException e) {
            plugin.getLogger().severe("APIサーバーの起動に失敗しました: " + e.getMessage());
        }
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    /**
     * 新規追加: 全出品アイテムを返すハンドラー
     */
    private class AllListingsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCommonHeaders(exchange);

            // 全出品を取得
            List<MarketListing> allListings = marketManager.getAllListings();
            List<MarketListingDTO> dtos = allListings.stream()
                    .map(MarketListingDTO::new)
                    .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("total", dtos.size());
            response.put("listings", dtos);

            sendResponse(exchange, 200, response);
        }
    }

    /**
     * 従来のハンドラー（特定プレイヤー用）
     */
    private class MarketHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // パスが完全に "/api/market" の場合のみ処理 ( /api/market/all に干渉しないため)
            if (!exchange.getRequestURI().getPath().equals("/api/market")) {
                sendResponse(exchange, 404, Map.of("error", "Not Found"));
                return;
            }

            setCommonHeaders(exchange);
            Map<String, String> params = queryToMap(exchange.getRequestURI().getQuery());
            String discordId = params.get("discordId");

            if (discordId == null || discordId.isEmpty()) {
                sendResponse(exchange, 400, Map.of("error", "Missing discordId parameter"));
                return;
            }

            UUID uuid = DiscordSRV.getPlugin().getAccountLinkManager().getUuid(discordId);
            if (uuid == null) {
                sendResponse(exchange, 404, Map.of("error", "No Minecraft account linked to this Discord ID"));
                return;
            }

            List<MarketListing> listings = marketManager.getListingsByPlayer(uuid);
            List<MarketListingDTO> dtos = listings.stream()
                    .map(MarketListingDTO::new)
                    .collect(Collectors.toList());

            sendResponse(exchange, 200, Map.of("uuid", uuid.toString(), "listings", dtos));
        }
    }

    // ヘッダー設定の共通化
    private void setCommonHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
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