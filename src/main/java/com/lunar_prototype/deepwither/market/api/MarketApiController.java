package com.lunar_prototype.deepwither.market.api;

import com.google.gson.Gson;
import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.api.patch.PatchNoteDTO;
import com.lunar_prototype.deepwither.market.GlobalMarketManager;
import com.lunar_prototype.deepwither.market.MarketListing;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.JDA;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Message;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@DependsOn({GlobalMarketManager.class})
public class MarketApiController implements IManager {

    private final Deepwither plugin;
    private GlobalMarketManager marketManager;
    private HttpServer server;
    private final Gson gson = new Gson();
    private static final String PATCH_CHANNEL_ID = "1454165342317318214";

    public MarketApiController(Deepwither plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        this.marketManager = plugin.getGlobalMarketManager();
        start(9093);
    }

    @Override
    public void shutdown() {
        stop();
    }

    public void start(int port) {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);

            // マーケットAPI
            server.createContext("/api/market", new MarketHandler());
            server.createContext("/api/market/all", new AllListingsHandler());

            // パッチノートAPI
            server.createContext("/api/patches/latest", new PatchNoteHandler());

            server.setExecutor(null);
            server.start();
            plugin.getLogger().info("Market & Patch API Server started on port " + port);
        } catch (IOException e) {
            plugin.getLogger().severe("APIサーバーの起動に失敗しました: " + e.getMessage());
        }
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    /**
     * パッチノート取得用ハンドラー
     */
    private class PatchNoteHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCommonHeaders(exchange);

            JDA jda = DiscordSRV.getPlugin().getJda();
            if (jda == null) {
                sendResponse(exchange, 503, Map.of("error", "Bot is not ready"));
                return;
            }

            TextChannel channel = jda.getTextChannelById(PATCH_CHANNEL_ID);
            if (channel == null) {
                sendResponse(exchange, 404, Map.of("error", "Channel not found"));
                return;
            }

            try {
                // JDA 4系では retrievePast(1).complete() は List<Message> を返します
                List<Message> history = channel.getHistory().retrievePast(1).complete();
                if (history.isEmpty()) {
                    sendResponse(exchange, 200, Map.of("title", "No Update", "content", "ログが見つかりませんでした。"));
                    return;
                }

                Message latestMsg = history.get(0);
                PatchNoteDTO dto = parsePatchNote(latestMsg.getContentRaw());
                sendResponse(exchange, 200, dto);
            } catch (Exception e) {
                sendResponse(exchange, 500, Map.of("error", e.getMessage()));
            }
        }
    }

    private PatchNoteDTO parsePatchNote(String content) {
        String[] lines = content.trim().split("\n");
        String title = lines.length > 0 ? lines[0] : "New Update";
        
        int firstNewLine = content.indexOf("\n");
        String body = firstNewLine != -1 ? content.substring(firstNewLine).trim() : "";
        
        String cleanContent = body.replaceAll("(?s)```(diff)?", "").replace("```", "").trim();
        
        return new PatchNoteDTO(title, cleanContent, List.of("Update", "Live"));
    }

    /**
     * 全出品アイテムを返すハンドラー
     */
    private class AllListingsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCommonHeaders(exchange);

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
     * 特定プレイヤー用マーケットハンドラー
     */
    private class MarketHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
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
