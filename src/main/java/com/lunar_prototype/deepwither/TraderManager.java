package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.data.TraderOffer;
import com.lunar_prototype.deepwither.data.TraderOffer.ItemType;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

public class TraderManager {

    private final JavaPlugin plugin;
    private final ItemFactory itemFactory; // ItemFactoryの依存性
    private final Map<String, Map<Integer, List<TraderOffer>>> traderOffers = new HashMap<>();
    private final Map<String, Integer> sellPrices = new HashMap<>(); // [Item ID/Material] -> [Price]
    private final Map<String, Integer> dailyTaskLimits = new HashMap<>();
    private final Map<String, String> traderNames = new HashMap<>();

    private final File tradersFolder;
    private final File sellFile;

    public TraderManager(JavaPlugin plugin, ItemFactory itemFactory) {
        this.plugin = plugin;
        this.itemFactory = itemFactory;
        this.tradersFolder = new File(plugin.getDataFolder(), "traders");
        this.sellFile = new File(plugin.getDataFolder(), "trader_sell.yml");

        this.tradersFolder.mkdirs();
        loadAllTraders();
        loadSellOffers();
    }

    // --- トレーダー購入オファーのロード ---

    private void loadAllTraders() {
        traderOffers.clear();
        dailyTaskLimits.clear(); // ★ 制限マップもクリア
        File[] traderFiles = tradersFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (traderFiles == null) return;

        for (File file : traderFiles) {
            String traderId = file.getName().replace(".yml", "");
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

            String displayName = config.getString("trader_name", traderId);
            traderNames.put(traderId, displayName);

            // ★ 1. デイリータスク制限を読み込む
            int limit = config.getInt("task_limit", 1); // デフォルトは 1 回
            if (limit > 0) {
                dailyTaskLimits.put(traderId, limit);
            } else {
                plugin.getLogger().warning("Trader " + traderId + ": task_limit に無効な値 (" + limit + ") が設定されています。デフォルト値 1 を使用します。");
                dailyTaskLimits.put(traderId, 1);
            }

            // 2. 購入オファーを読み込む (既存ロジック)
            Map<Integer, List<TraderOffer>> creditTiers = parseBuyOffers(traderId, config);
            traderOffers.put(traderId, creditTiers);

            plugin.getLogger().info("Trader loaded: " + traderId + " (Task Limit: " + dailyTaskLimits.get(traderId) + ")");
        }
    }

    private Map<Integer, List<TraderOffer>> parseBuyOffers(String traderId, YamlConfiguration config) {
        Map<Integer, List<TraderOffer>> tiers = new HashMap<>();

        if (!config.isConfigurationSection("credit_tiers")) return tiers;

        for (String creditStr : config.getConfigurationSection("credit_tiers").getKeys(false)) {
            try {
                int creditLevel = Integer.parseInt(creditStr);
                List<TraderOffer> offers = new ArrayList<>();

                // credit_tiers.[creditStr].buy_offers のリストを取得
                List<Map<?, ?>> offersList = config.getMapList("credit_tiers." + creditStr + ".buy_offers");

                for (Map<?, ?> offerMap : offersList) {
                    TraderOffer offer = createTraderOffer(offerMap);
                    // ここでロードされたアイテムをセット（GUI生成時の負荷軽減のため）
                    loadOfferItem(offer);
                    offers.add(offer);
                }
                tiers.put(creditLevel, offers);

            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Trader " + traderId + ": 無効な信用度レベル: " + creditStr);
            }
        }
        return tiers;
    }

    private TraderOffer createTraderOffer(Map<?, ?> offerMap) {
        String itemTypeStr = (String) offerMap.get("item_type");
        TraderOffer.ItemType type = ItemType.valueOf(itemTypeStr.toUpperCase());

        String id = (String) offerMap.get("material"); // バニラの場合
        if (type == ItemType.CUSTOM) {
            id = (String) offerMap.get("custom_id"); // カスタムの場合
        }

        // amount: デフォルト値 1
        Object amountObj = offerMap.get("amount");
        int amount = (amountObj instanceof Number)
                ? ((Number) amountObj).intValue()
                : 1; // 値がなければデフォルト値 1

        // cost: デフォルト値 0
        Object costObj = offerMap.get("cost");
        int cost = (costObj instanceof Number)
                ? ((Number) costObj).intValue()
                : 0; // 値がなければデフォルト値 0

        // required_credit: デフォルト値 0
        Object requiredCreditObj = offerMap.get("required_credit");
        int requiredCredit = (requiredCreditObj instanceof Number)
                ? ((Number) requiredCreditObj).intValue()
                : 0; // 値がなければデフォルト値 0

        return new TraderOffer(id, type, amount, cost, requiredCredit);
    }

    // --- 売却オファーのロード ---

    private void loadSellOffers() {
        if (!sellFile.exists()) {
            // デフォルトファイルを保存するか、空で続行
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(sellFile);

        List<Map<?, ?>> sellList = config.getMapList("sell_items");
        if (sellList == null) return;

        for (Map<?, ?> itemMap : sellList) {
            String itemTypeStr = (String) itemMap.get("item_type");
            TraderOffer.ItemType type = ItemType.valueOf(itemTypeStr.toUpperCase());

            String id = (String) itemMap.get("material"); // バニラの場合
            if (type == ItemType.CUSTOM) {
                id = (String) itemMap.get("custom_id"); // カスタムの場合
            }

            Object priceObj = itemMap.get("sell_price");
            int price = (priceObj instanceof Number)
                    ? ((Number) priceObj).intValue()
                    : 0;

            // Item ID/Material名をキーとして保存
            sellPrices.put(id, price);
        }
    }

    // --- ユーティリティメソッド ---

    /**
     * TraderOfferのアイテムをロードし、ItemStackをセットする
     */
    private void loadOfferItem(TraderOffer offer) {
        ItemStack item = null;
        if (offer.getItemType() == ItemType.VANILLA) {
            Material material = Material.matchMaterial(offer.getId());
            if (material != null) {
                item = new ItemStack(material, offer.getAmount());
            } else {
                plugin.getLogger().warning("不明なマテリアル: " + offer.getId());
            }
        } else if (offer.getItemType() == ItemType.CUSTOM) {
            // ItemLoaderを使ってカスタムアイテムをロード
            // ItemLoader.loadSingleItem(id, this.itemFactory, itemFolder) の処理を想定
            File itemFolder = new File(plugin.getDataFolder(), "items");

            item = ItemLoader.loadSingleItem(offer.getId(), this.itemFactory,itemFolder);
            if (item != null) {
                item.setAmount(offer.getAmount());
            } else {
                plugin.getLogger().warning("不明なカスタムアイテムID: " + offer.getId());
            }
        }

        if (item != null) {
            offer.setLoadedItem(item);
        }
    }

    // --- ゲッター ---

    /**
     * プレイヤーの信用度に関係なく、指定されたトレーダーの全オファーを信用度ティアレベルの低い順に取得する。
     */
    public List<TraderOffer> getAllOffers(String traderId) {
        List<TraderOffer> allOffers = new ArrayList<>();
        Map<Integer, List<TraderOffer>> tiers = traderOffers.getOrDefault(traderId, null);

        if (tiers == null) return allOffers;

        // ティアレベルのキーセットを取得し、昇順でソートして処理する
        tiers.keySet().stream()
                .sorted() // ティアレベル（信用度）の低い順に並び替える
                .forEach(creditLevel -> {
                    // ソートされた順に、該当ティアのオファーリストをallOffersに追加する (1回のみ)
                    allOffers.addAll(tiers.get(creditLevel));
                });

        return allOffers;
    }

    public int getSellPrice(String id) {
        return sellPrices.getOrDefault(id, 0);
    }

    /**
     * 指定されたトレーダーIDがロード済みであるかを確認する。
     * @param traderId 確認するトレーダーのID
     * @return 存在すれば true
     */
    public boolean traderExists(String traderId) {
        return traderOffers.containsKey(traderId);
    }

    /**
     * ロードされている全てのトレーダーIDのSetを返す。
     */
    public Set<String> getAllTraderIds() {
        return traderOffers.keySet(); // traderOffers マップのキーがトレーダーIDなのでこれを使用
    }

    public int getDailyTaskLimit(String traderId) {
        // loadAllTraders() で設定されたマップから取得
        return dailyTaskLimits.getOrDefault(traderId, 1);
    }

    /**
     * ★ 追加: トレーダーの表示名を取得する
     * @param traderId トレーダーのID
     * @return 設定された名前、なければID
     */
    public String getTraderName(String traderId) {
        return traderNames.getOrDefault(traderId, traderId);
    }
}