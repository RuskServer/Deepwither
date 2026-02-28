package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.data.TraderOffer;
import com.lunar_prototype.deepwither.data.TraderOffer.ItemType;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

@DependsOn({ ItemFactory.class })
public class TraderManager implements IManager {

    private final JavaPlugin plugin;
    private final ItemFactory itemFactory; // ItemFactoryの依存性
    private final Map<String, Map<Integer, List<TraderOffer>>> traderOffers = new HashMap<>();
    private final Map<String, Integer> sellPrices = new HashMap<>(); // [Item ID/Material] -> [Price]
    private final Map<String, Integer> dailyTaskLimits = new HashMap<>();
    private final Map<String, String> traderNames = new HashMap<>();

    private File tradersFolder;
    private File sellFile;

    /**
     * Creates a TraderManager and stores references to the hosting plugin and item factory.
     *
     * @param plugin the JavaPlugin instance used for plugin context (configuration and logging)
     * @param itemFactory factory for creating or retrieving custom item instances
     */
    public TraderManager(JavaPlugin plugin, ItemFactory itemFactory) {
        this.plugin = plugin;
        this.itemFactory = itemFactory;
    }

    /**
     * Initializes trader data paths and loads trader and sell-offer configurations.
     *
     * Creates the traders directory if it does not exist, then loads all trader definitions
     * and the trader sell-offers file into memory.
     */
    @Override
    public void init() {
        this.tradersFolder = new File(plugin.getDataFolder(), "traders");
        this.sellFile = new File(plugin.getDataFolder(), "trader_sell.yml");

        if (!this.tradersFolder.exists()) {
            this.tradersFolder.mkdirs();
        }
        loadAllTraders();
        loadSellOffers();
    }

    /**
     * Performs shutdown and cleanup operations for the TraderManager.
     *
     * <p>Invoked when the plugin is disabling to allow the manager to release resources or persist state.
     */
    @Override
    public void shutdown() {
    }

    /**
     * Load trader YAML files and populate in-memory trader data.
     *
     * Clears existing trader caches for offers, daily task limits, and display names,
     * then scans the traders folder for files ending with ".yml". For each file found,
     * the trader ID is derived from the filename, the display name is read (defaults
     * to the trader ID), the daily task limit is read and normalized to at least 1,
     * and buy offers are parsed and stored.
     *
     * If the traders folder cannot be listed or contains no YAML files, the method
     * returns without modifying state beyond clearing the caches.
     */

    private void loadAllTraders() {
        traderOffers.clear();
        dailyTaskLimits.clear();
        traderNames.clear();

        File[] traderFiles = tradersFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (traderFiles == null)
            return;

        for (File file : traderFiles) {
            String traderId = file.getName().replace(".yml", "");
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

            // 1. 基本情報の読み込み
            String displayName = config.getString("trader_name", traderId);
            traderNames.put(traderId, displayName);

            // デイリータスク制限
            int limit = config.getInt("task_limit", 1);
            dailyTaskLimits.put(traderId, Math.max(1, limit));

            // 2. 購入オファーの読み込み
            Map<Integer, List<TraderOffer>> creditTiers = parseBuyOffers(traderId, config);
            traderOffers.put(traderId, creditTiers);

            plugin.getLogger().info("Trader loaded: " + traderId);
        }
    }

    /**
         * Parse the configuration's "credit_tiers" section into a map of credit level to offer lists.
         *
         * Each credit tier key is parsed as an integer and its "buy_offers" entries are converted into
         * TraderOffer instances with their items loaded. Invalid (non-integer) tier keys are ignored.
         *
         * @param traderId the identifier of the trader whose configuration is being parsed
         * @param config the YAML configuration containing a "credit_tiers" section
         * @return a map from credit level to the list of TraderOffer objects for that level; empty if no "credit_tiers" section exists
         */
    private Map<Integer, List<TraderOffer>> parseBuyOffers(String traderId, YamlConfiguration config) {
        Map<Integer, List<TraderOffer>> tiers = new HashMap<>();

        if (!config.isConfigurationSection("credit_tiers"))
            return tiers;

        for (String creditStr : config.getConfigurationSection("credit_tiers").getKeys(false)) {
            try {
                int creditLevel = Integer.parseInt(creditStr);
                String path = "credit_tiers." + creditStr;

                // --- 既存のアイテム読み込みロジック ---
                List<TraderOffer> offers = new ArrayList<>();
                List<Map<?, ?>> offersList = config.getMapList(path + ".buy_offers");

                for (Map<?, ?> offerMap : offersList) {
                    TraderOffer offer = createTraderOffer(offerMap);
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

    /**
     * Constructs a TraderOffer from a configuration map.
     *
     * Parses item type and identifier, amount, cost, required credit, and an optional
     * list of required items (vanilla materials or custom items via the ItemFactory).
     *
     * Default behavior for missing fields:
     * - amount defaults to 1
     * - cost defaults to 0
     * - required_credit defaults to 0
     *
     * When a "required_items" section is present, each entry may specify a "type"
     * ("CUSTOM" or "VANILLA"), an item identifier ("custom_id" or "material"), and
     * an "amount". Custom required items are resolved through the manager's ItemFactory.
     *
     * @param offerMap configuration map describing the offer
     * @return a populated TraderOffer reflecting the provided configuration
     */
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

        TraderOffer offer = new TraderOffer(id, type, amount, cost, requiredCredit);

        // ★ 追加: 必要アイテムの読み込み
        if (offerMap.containsKey("required_items")) {
            List<Map<?, ?>> reqItemsList = (List<Map<?, ?>>) offerMap.get("required_items");
            List<ItemStack> requiredItems = new ArrayList<>();

            for (Map<?, ?> reqMap : reqItemsList) {
                // 1. typeの取得 (デフォルトは "VANILLA")
                String reqType = "VANILLA";
                Object typeObj = reqMap.get("type");
                if (typeObj instanceof String) {
                    reqType = (String) typeObj;
                }

                // 2. amountの取得 (デフォルトは 1)
                int reqAmount = 1;
                Object amountObj2 = reqMap.get("amount");
                if (amountObj2 instanceof Number) {
                    reqAmount = ((Number) amountObj2).intValue();
                }

                if (reqType.equalsIgnoreCase("CUSTOM")) {
                    String customId = (String) reqMap.get("custom_id");
                    ItemStack is = this.itemFactory.getCustomCountItemStack(customId, reqAmount);
                    if (is != null) {
                        is.setAmount(reqAmount);
                        requiredItems.add(is);
                    }
                } else {
                    Material mat = Material.matchMaterial((String) reqMap.get("material"));
                    if (mat != null) {
                        requiredItems.add(new ItemStack(mat, reqAmount));
                    }
                }
            }
            offer.setRequiredItems(requiredItems);
        }
        return offer;
    }

    /**
     * Loads sell-item price mappings from the configured sell file into the manager's sellPrices map.
     *
     * Reads the "sell_items" list from the YAML file and stores each entry's item identifier (material name for
     * vanilla items or custom_id for custom items) mapped to its sell price. If the sell file does not exist
     * or the list is absent, the method returns without modifying state. If an entry's price is missing or not
     * numeric, a price of 0 is stored.
     */

    private void loadSellOffers() {
        if (!sellFile.exists()) {
            // デフォルトファイルを保存するか、空で続行
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(sellFile);

        List<Map<?, ?>> sellList = config.getMapList("sell_items");
        if (sellList == null)
            return;

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
     * Loads an ItemStack for the given TraderOffer and assigns it to the offer when resolved.
     *
     * Supports VANILLA and CUSTOM offer item types; if the item cannot be resolved, the offer's loaded item is left unset.
     *
     * @param offer the TraderOffer whose loaded ItemStack will be set when successfully loaded
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

            item = ItemLoader.loadSingleItem(offer.getId(), this.itemFactory, itemFolder);
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
     * Get all offers for the given trader ordered by credit tier from lowest to highest.
     *
     * @param traderId the identifier of the trader
     * @return a list of the trader's offers ordered by ascending credit tier; an empty list if the trader is not found
     */
    public List<TraderOffer> getAllOffers(String traderId) {
        List<TraderOffer> allOffers = new ArrayList<>();
        Map<Integer, List<TraderOffer>> tiers = traderOffers.getOrDefault(traderId, null);

        if (tiers == null)
            return allOffers;

        // ティアレベルのキーセットを取得し、昇順でソートして処理する
        tiers.keySet().stream()
                .sorted() // ティアレベル（信用度）の低い順に並び替える
                .forEach(creditLevel -> {
                    // ソートされた順に、該当ティアのオファーリストをallOffersに追加する (1回のみ)
                    allOffers.addAll(tiers.get(creditLevel));
                });

        return allOffers;
    }

    /**
     * Finds the first trader offer for the given trader that matches the specified item ID.
     *
     * @param traderId the trader's identifier
     * @param itemId   the item identifier to search for (Material name or custom_id)
     * @return the matching {@link TraderOffer}, or `null` if no matching offer is found
     */
    public TraderOffer getOfferById(String traderId, String itemId) {
        return getAllOffers(traderId).stream()
                .filter(offer -> offer.getId().equals(itemId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Check whether a player meets the credit requirement to access a trader tier.
     *
     * @param player         the player to check
     * @param traderId       the trader identifier
     * @param requiredCredit the credit level required to access the tier
     * @param playerCredit   the player's current credit with the trader
     * @return               `true` if playerCredit is greater than or equal to requiredCredit, `false` otherwise
     */
    public boolean canAccessTier(Player player, String traderId, int requiredCredit, int playerCredit) {
        return playerCredit >= requiredCredit;
    }

    /**
     * Get the configured sell price for an item identifier.
     *
     * @param id the item identifier (vanilla material name or custom item id)
     * @return the sell price for the given item id, or 0 if no price is configured
     */
    public int getSellPrice(String id) {
        return sellPrices.getOrDefault(id, 0);
    }

    /**
     * 指定されたトレーダーIDがロード済みであるかを確認する。
     * 
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
     * 
     * @param traderId トレーダーのID
     * @return 設定された名前、なければID
     */
    public String getTraderName(String traderId) {
        return traderNames.getOrDefault(traderId, traderId);
    }
}