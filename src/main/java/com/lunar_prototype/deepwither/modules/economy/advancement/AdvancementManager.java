package com.lunar_prototype.deepwither.modules.economy.advancement;

import com.fren_gor.ultimateAdvancementAPI.UltimateAdvancementAPI;
import com.fren_gor.ultimateAdvancementAPI.AdvancementTab;
import com.fren_gor.ultimateAdvancementAPI.advancement.BaseAdvancement;
import com.fren_gor.ultimateAdvancementAPI.advancement.RootAdvancement;
import com.fren_gor.ultimateAdvancementAPI.advancement.display.AdvancementDisplay;
import com.fren_gor.ultimateAdvancementAPI.advancement.display.AdvancementFrameType;
import com.fren_gor.ultimateAdvancementAPI.events.PlayerLoadingCompletedEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lunar_prototype.deepwither.DatabaseManager;
import com.lunar_prototype.deepwither.api.DW;
import com.lunar_prototype.deepwither.modules.economy.trader.TraderManager;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * UltimateAdvancementAPI を利用した独自実績管理クラス。
 * バニラの実績は非表示にし、Deepwither独自の実績ツリーを動的に生成・管理する。
 */
@DependsOn({ TraderManager.class })
public class AdvancementManager implements IManager, Listener {

    private final JavaPlugin plugin;
    private final Gson gson;

    // UltimateAdvancementAPI のインスタンス（APIプラグインが存在する場合のみ非null）
    private UltimateAdvancementAPI api;
    private AdvancementTab deepwitherTab;

    // 実績IDとBaseAdvancementインスタンスのマップ（付与時に高速アクセスするため）
    private final Map<String, com.fren_gor.ultimateAdvancementAPI.advancement.Advancement> advancementMap = new HashMap<>();

    public AdvancementManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    @Override
    public void init() {
        if (Bukkit.getPluginManager().getPlugin("UltimateAdvancementAPI") == null) {
            plugin.getLogger().warning("[AdvancementManager] UltimateAdvancementAPI が見つかりません。実績システムは無効化されます。");
            return;
        }

        try {
            this.api = UltimateAdvancementAPI.getInstance(plugin);
            setupAdvancements();
            // PlayerLoadingCompletedEvent を受け取るためイベントリスナーとして登録
            Bukkit.getPluginManager().registerEvents(this, plugin);
            plugin.getLogger().info("[AdvancementManager] 独自実績システムを初期化しました。");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[AdvancementManager] 初期化に失敗しました。", e);
        }
    }

    @Override
    public void shutdown() {
    }

    // ================================================
    // 実績ツリーのセットアップ
    // ================================================

    private void setupAdvancements() {
        this.deepwitherTab = api.createAdvancementTab("deepwither");

        // ルート実績（タブのバックグラウンドになる）
        AdvancementDisplay rootDisplay = new AdvancementDisplay(
                Material.BEDROCK, "Echoes of Aether", AdvancementFrameType.TASK,
                false, false, 0f, 0f,
                "Echoes of Aether の世界へようこそ");
        RootAdvancement root = new RootAdvancement(deepwitherTab, "root", rootDisplay,
                "minecraft:textures/block/stone.png");
        register("root", root);

        // チュートリアル
        AdvancementDisplay tutorialDisplay = new AdvancementDisplay(
                Material.BOOK, "チュートリアルクリア", AdvancementFrameType.TASK,
                true, true, 2f, 0f,
                "基本操作を学んだ証");
        BaseAdvancement tutorialAdv = new BaseAdvancement("tutorial_clear", tutorialDisplay, root);
        register("tutorial_clear", tutorialAdv);

        // 1〜6階層踏破 (x: 3〜8, y: 0)
        com.fren_gor.ultimateAdvancementAPI.advancement.Advancement lastFloor = tutorialAdv;
        for (int i = 1; i <= 6; i++) {
            AdvancementDisplay floorDisplay = new AdvancementDisplay(
                    Material.IRON_BOOTS, "第" + i + "層 踏破", AdvancementFrameType.TASK,
                    true, true, (float) (2 + i), 0f,
                    "第" + i + "層に到達した");
            String floorId = "floor_" + i;
            BaseAdvancement floorAdv = new BaseAdvancement(floorId, floorDisplay, lastFloor);
            register(floorId, floorAdv);
            lastFloor = floorAdv;
        }

        // モブ討伐実績（累計討伐数マイルストーン）(x: 1, y: 1〜5)
        int[] mobMilestones = { 10, 50, 100, 500, 1000 };
        String[] mobTitles = { "モブ退治入門", "モブハンター見習い", "モブハンター", "熟練モブハンター", "伝説のモブハンター" };
        com.fren_gor.ultimateAdvancementAPI.advancement.Advancement lastMob = root;
        for (int i = 0; i < mobMilestones.length; i++) {
            AdvancementDisplay display = new AdvancementDisplay(
                    Material.ZOMBIE_HEAD, mobTitles[i], AdvancementFrameType.TASK,
                    true, true, 1f, (float) (i + 1),
                    "モブを合計" + mobMilestones[i] + "体討伐した");
            String mobId = "mob_kill_" + mobMilestones[i];
            BaseAdvancement mobAdv = new BaseAdvancement(mobId, display, lastMob);
            register(mobId, mobAdv);
            lastMob = mobAdv;
        }

        // トレーダー信用度実績 (全トレーダー走査)
        // レイアウト: x = column+2（0以上）、y = credit段階（1〜5）+ 6開始でmob討伐と分離
        TraderManager traderManager = DW.get(TraderManager.class);
        if (traderManager != null) {
            int column = 0;
            for (String traderId : traderManager.getAllTraderIds()) {
                String traderName = traderManager.getTraderName(traderId);
                com.fren_gor.ultimateAdvancementAPI.advancement.Advancement lastTrader = root;
                for (int credit = 500; credit <= 2500; credit += 500) {
                    // Minecraft ResourceLocation は [a-z0-9/._-] のみ許可
                    String advId = sanitizeKey("trader_" + traderId + "_" + credit);
                    AdvancementDisplay display = new AdvancementDisplay(
                            Material.EMERALD, traderName + " Lv." + (credit / 500), AdvancementFrameType.TASK,
                            true, true, (float) (column + 2), (float) (credit / 500 + 6),
                            traderName + "との信用度が" + credit + "に達した");
                    BaseAdvancement traderAdv = new BaseAdvancement(advId, display, lastTrader);
                    register(advId, traderAdv);
                    lastTrader = traderAdv;
                }
                column++;
            }
        }

        // タブに全実績を登録してデプロイ
        // registerAdvancements(root, baseAdvancements...) の形で呼び出す
        List<BaseAdvancement> baseAdvs = new ArrayList<>();
        for (com.fren_gor.ultimateAdvancementAPI.advancement.Advancement adv : advancementMap.values()) {
            if (adv instanceof BaseAdvancement) {
                baseAdvs.add((BaseAdvancement) adv);
            }
        }
        deepwitherTab.registerAdvancements(root, baseAdvs.toArray(new BaseAdvancement[0]));
        plugin.getLogger().info("[AdvancementManager] " + advancementMap.size() + " 件の実績を登録しました。");
    }

    private void register(String id, com.fren_gor.ultimateAdvancementAPI.advancement.Advancement advancement) {
        advancementMap.put(id, advancement);
    }

    /**
     * Minecraft ResourceLocation のキーとして有効な文字列に変換する。
     * 小文字化し、[a-z0-9_.-] 以外の文字はアンダースコアに置換する。
     */
    private String sanitizeKey(String raw) {
        return raw.toLowerCase().replaceAll("[^a-z0-9_\\-.]", "_");
    }

    // ================================================
    // プレイヤー管理
    // ================================================

    /**
     * UltimateAdvancementAPIがプレイヤーのデータを完全にロードした後に呼ばれるイベント。
     * showTab と遡及付与はここで行うことで、タイミングを確実に保証する。
     */
    @EventHandler
    public void onPlayerLoadingCompleted(PlayerLoadingCompletedEvent event) {
        if (deepwitherTab == null)
            return;
        Player player = event.getPlayer();
        deepwitherTab.showTab(player);

        // レベル1以上のプレイヤーには「ようこそ」と「チュートリアルクリア」を自動付与(遡及)
        try {
            com.lunar_prototype.deepwither.PlayerLevelData levelData = com.lunar_prototype.deepwither.Deepwither
                    .getInstance().getLevelManager().get(player);
            if (levelData != null && levelData.getLevel() >= 1) {
                grantAdvancement(player, "root");
                grantAdvancement(player, "tutorial_clear");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[AdvancementManager] 自動付与チェック中にエラー: " + e.getMessage());
        }
    }

    /**
     * 後方互換: PlayerConnectionListenerから呼ばれていても動作するように残す。
     * 実際の処理は PlayerLoadingCompletedEvent 側で行われる。
     */
    public void onPlayerJoin(Player player) {
        // PlayerLoadingCompletedEventで処理するため、ここでは何もしない
    }

    /**
     * 実績を付与する。
     * 
     * @param player        対象プレイヤー
     * @param advancementId 実績ID（例: "tutorial_clear", "floor_1"）
     */
    public void grantAdvancement(Player player, String advancementId) {
        if (deepwitherTab == null)
            return;
        // 呼び出し元が正規化前のIDを渡しても対応できるようにサニタイズ
        String key = sanitizeKey(advancementId);
        com.fren_gor.ultimateAdvancementAPI.advancement.Advancement advancement = advancementMap.get(key);
        if (advancement == null) {
            plugin.getLogger().warning("[AdvancementManager] 存在しない実績ID: " + advancementId + " (key=" + key + ")");
            return;
        }
        if (!advancement.isGranted(player)) {
            advancement.grant(player);
        }
    }

    // ================================================
    // DB永続化
    // ================================================

    public void load(UUID uuid) {
        DatabaseManager db = DW.get(DatabaseManager.class);
        if (db == null)
            return;

        PlayerAdvancementData data = db.querySingle(
                "SELECT data_json FROM player_advancements WHERE uuid = ?",
                rs -> {
                    try {
                        String json = rs.getString("data_json");
                        return gson.fromJson(json, PlayerAdvancementData.class);
                    } catch (SQLException e) {
                        e.printStackTrace();
                        return null;
                    }
                },
                uuid.toString()).orElse(new PlayerAdvancementData());

        com.lunar_prototype.deepwither.core.PlayerCache pc = DW.cache().getCache(uuid);
        if (pc != null && pc.getData() != null) {
            pc.getData().setAdvancements(data);
        }
    }

    public void save(UUID uuid) {
        com.lunar_prototype.deepwither.core.PlayerCache pc = DW.cache().getCache(uuid);
        if (pc == null || pc.getData() == null || pc.getData().getAdvancements() == null)
            return;

        PlayerAdvancementData data = pc.getData().getAdvancements();
        String json = gson.toJson(data);

        DatabaseManager db = DW.get(DatabaseManager.class);
        if (db != null) {
            db.execute(
                    "INSERT INTO player_advancements (uuid, data_json) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET data_json=excluded.data_json",
                    uuid.toString(), json);
        }
    }

    public boolean isAvailable() {
        return api != null && deepwitherTab != null;
    }
}
