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

    // クエスト情報を保持するマップ [TraderID -> [QuestID -> QuestData]]
    private final Map<String, Map<String, QuestData>> traderQuests = new HashMap<>();
    // ティアの解禁条件を保持するマップ [TraderID -> [CreditLevel -> RequiredQuestID]]
    private final Map<String, Map<Integer, String>> tierRequirements = new HashMap<>();

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

    public static class QuestData {
        private final String id;
        private final String displayName;
        private final List<String> description;
        private final String type; // "KILL", "FETCH"
        private final String target; // Mob名 または Material名
        private final int amount;
        private final String requiredQuestId; // 前提条件
        private final int rewardCredit; // 完了時の信用度報酬

        // 拡張条件（Conditions）
        private double minDistance = -1;
        private double maxDistance = -1;
        private String requiredWeapon = null;
        private String requiredArmor = null;

        /**
         * Create a QuestData instance that holds the quest's core attributes and reward.
         *
         * @param id           unique identifier for the quest
         * @param name         display name for the quest
         * @param description  list of description lines; if null an empty list will be used
         * @param type         quest type identifier (defines how the quest is evaluated)
         * @param target       identifier of the quest target (entity, item, or other target value)
         * @param amount       quantity required to complete the quest
         * @param requires     quest id that must be completed before this quest is unlocked, or null if none
         * @param rewardCredit credit awarded to the player when the quest is completed
         */
        public QuestData(String id, String name, List<String> description, String type, String target, int amount,
                String requires, int rewardCredit) {
            this.id = id;
            this.displayName = name;
            this.description = (description != null) ? description : new ArrayList<>();
            this.type = type;
            this.target = target;
            this.amount = amount;
            this.requiredQuestId = requires;
            this.rewardCredit = rewardCredit;
        }

        /**
         * Sets distance constraints for the quest target.
         *
         * @param min minimum distance constraint
         * @param max maximum distance constraint
         */
        public void setDistance(double min, double max) {
            this.minDistance = min;
            this.maxDistance = max;
        }

        /**
         * Sets the identifier of the weapon a player must have to satisfy the quest's requirement.
         *
         * @param weapon the weapon id or material name required to complete the quest, or `null` to clear the requirement
         */
        public void setRequiredWeapon(String weapon) {
            this.requiredWeapon = weapon;
        }

        /**
         * Sets the armor requirement for the quest; this value is used to enforce that a player has a specific armor equipped.
         *
         * @param armor the armor identifier or material name required for the quest, or {@code null} to clear the requirement
         */
        public void setRequiredArmor(String armor) {
            this.requiredArmor = armor;
        }

        /**
         * Gets the trader's unique identifier.
         *
         * @return the trader's identifier
         */
        public String getId() {
            return id;
        }

        /**
         * Gets the display name for this quest.
         *
         * @return the quest's display name.
         */
        public String getDisplayName() {
            return displayName;
        }

        /**
         * Get the quest's description lines.
         *
         * @return the quest description as a list of lines
         */
        public List<String> getDescription() {
            return description;
        }

        /**
         * Gets the quest type identifier.
         *
         * @return the quest type (for example, "KILL")
         */
        public String getType() {
            return type;
        }

        /**
         * Returns the quest's target identifier or subject.
         *
         * @return the quest's target identifier or subject
         */
        public String getTarget() {
            return target;
        }

        /**
         * Get the target count required by this quest.
         *
         * @return the target count for the quest
         */
        public int getAmount() {
            return amount;
        }

        /**
         * Gets the quest ID that must be completed to unlock this quest.
         *
         * @return the required quest ID, or `null` if no prerequisite is configured
         */
        public String getRequiredQuestId() {
            return requiredQuestId;
        }

        /**
         * The credit reward awarded for completing this quest.
         *
         * @return the number of credits granted upon quest completion
         */
        public int getRewardCredit() {
            return rewardCredit;
        }

        /**
         * Minimum distance constraint for the quest.
         *
         * @return the minimum distance constraint for the quest, or a negative value if unset
         */
        public double getMinDistance() {
            return minDistance;
        }

        /**
         * Gets the configured maximum distance constraint for the quest.
         *
         * @return the maximum distance value; 0 if no maximum was configured
         */
        public double getMaxDistance() {
            return maxDistance;
        }

        /**
         * Gets the identifier of the weapon required for the quest, or null if no weapon is required.
         *
         * @return the required weapon identifier, or null if not specified
         */
        public String getRequiredWeapon() {
            return requiredWeapon;
        }

        /**
         * The armor item required to complete the quest, if any.
         *
         * @return the required armor identifier, or null if the quest does not require specific armor
         */
        public String getRequiredArmor() {
            return requiredArmor;
        }
    }

    /**
     * Loads all trader configuration files from the configured traders folder and populates
     * internal maps for trader offers, daily task limits, display names, quests, and tier
     * unlock requirements.
     *
     * Clears existing trader-related caches before loading. If the traders folder contains no
     * YAML files or cannot be listed, the method returns without modifying state.
     */

    private void loadAllTraders() {
        traderOffers.clear();
        dailyTaskLimits.clear();
        traderNames.clear();
        traderQuests.clear(); // 新規追加
        tierRequirements.clear(); // 新規追加

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

            // 2. トレーダークエスト(永続タスク)の読み込み
            if (config.isConfigurationSection("quests")) {
                Map<String, QuestData> quests = parseQuests(traderId, config.getConfigurationSection("quests"));
                traderQuests.put(traderId, quests);
            }

            // 3. 購入オファーとティア解禁条件の読み込み
            // parseBuyOffersを拡張して、ティアごとのrequired_questも取得するように修正
            Map<Integer, List<TraderOffer>> creditTiers = parseBuyOffers(traderId, config);
            traderOffers.put(traderId, creditTiers);

            plugin.getLogger().info("Trader loaded: " + traderId +
                    " (Quests: " + (traderQuests.containsKey(traderId) ? traderQuests.get(traderId).size() : 0) + ")");
        }
    }

    /**
     * Parse a trader's "quests" configuration section into QuestData objects.
     *
     * @param traderId identifier of the trader owning the quests
     * @param section  the ConfigurationSection representing the "quests" subsection for the trader
     * @return a map of quest ID to QuestData preserving the definition order
     */
    private Map<String, QuestData> parseQuests(String traderId, org.bukkit.configuration.ConfigurationSection section) {
        Map<String, QuestData> quests = new LinkedHashMap<>(); // 順番を保持

        for (String questId : section.getKeys(false)) {
            String path = questId + ".";

            String name = section.getString(path + "display_name", questId);
            List<String> description = section.getStringList(path + "description");
            String type = section.getString(path + "type", "KILL");
            String target = section.getString(path + "target", "");
            int amount = section.getInt(path + "amount", 1);
            String requires = section.getString(path + "requires"); // 前提クエストID
            int rewardCredit = section.getInt(path + "reward_credit", 0);

            // クエスト本体の生成
            QuestData qData = new QuestData(questId, name, description, type, target, amount, requires, rewardCredit);

            // 拡張条件（conditionsセクション）の読み込み
            if (section.isConfigurationSection(path + "conditions")) {
                org.bukkit.configuration.ConfigurationSection cond = section
                        .getConfigurationSection(path + "conditions");
                qData.setDistance(
                        cond.getDouble("min_distance", -1),
                        cond.getDouble("max_distance", -1));
                qData.setRequiredWeapon(cond.getString("weapon"));
                qData.setRequiredArmor(cond.getString("armor"));
            }

            quests.put(questId, qData);
        }
        return quests;
    }

    /**
     * Parse the trader's "credit_tiers" configuration into credit-level offer lists and record tier unlock requirements.
     *
     * Parses the "credit_tiers" section of the provided YAML config into a map keyed by credit level (integer)
     * with each value being the list of TraderOffer objects for that tier. Each offer's associated ItemStack is loaded,
     * and any configured required quest IDs for unlocking tiers are stored in this instance's tierRequirements for the trader.
     *
     * @param traderId the identifier of the trader whose config is being parsed
     * @param config the YAML configuration containing a "credit_tiers" section
     * @return a map from credit level to the list of TraderOffer objects for that level; empty if no "credit_tiers" section exists
     */
    private Map<Integer, List<TraderOffer>> parseBuyOffers(String traderId, YamlConfiguration config) {
        Map<Integer, List<TraderOffer>> tiers = new HashMap<>();

        if (!config.isConfigurationSection("credit_tiers"))
            return tiers;

        // このトレーダーのティア条件を格納する一時マップ
        Map<Integer, String> requirements = new HashMap<>();

        for (String creditStr : config.getConfigurationSection("credit_tiers").getKeys(false)) {
            try {
                int creditLevel = Integer.parseInt(creditStr);
                String path = "credit_tiers." + creditStr;

                // ★ 追加: ティアの解禁に必要なクエストIDを読み込む
                String reqQuest = config.getString(path + ".required_quest");
                if (reqQuest != null) {
                    requirements.put(creditLevel, reqQuest);
                }

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

        // 全てのティアを読み終わった後、条件マップを保存
        tierRequirements.put(traderId, requirements);

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
     * Determines whether a player may access a specific trader credit tier.
     *
     * Checks that the player's credit meets or exceeds the tier's required credit and,
     * if the tier has an associated unlock quest, that the player has completed that quest.
     *
     * @param player         the player to check
     * @param traderId       the trader identifier
     * @param requiredCredit the credit level required to access the tier
     * @param playerCredit   the player's current credit with the trader
     * @return               `true` if the player may access the tier, `false` otherwise
     */
    public boolean canAccessTier(Player player, String traderId, int requiredCredit, int playerCredit) {
        // 1. まず信用度が足りているか
        if (playerCredit < requiredCredit)
            return false;

        // 2. ティア解禁に必要なクエストがあるかチェック
        Map<Integer, String> requirements = tierRequirements.get(traderId);
        if (requirements == null || !requirements.containsKey(requiredCredit)) {
            return true; // 必要クエスト設定なし
        }

        String reqQuestId = requirements.get(requiredCredit);
        // TraderQuestManager を通じて完了状況を確認
        return Deepwither.getInstance().getTraderQuestManager().isQuestCompleted(player, traderId, reqQuestId);
    }

    /**
     * 指定されたトレーダーIDに関連付けられたクエスト一覧を取得します。
     * 
     * @param traderId トレーダーのID
     * @return クエストIDをキーとしたQuestDataのマップ。存在しない場合は空のマップを返します。
     */
    public Map<String, QuestData> getQuestsForTrader(String traderId) {
        return traderQuests.getOrDefault(traderId, Collections.emptyMap());
    }

    /**
     * Retrieve the QuestData for a specific trader's quest.
     *
     * @param traderId the trader's identifier
     * @param questId  the quest's identifier
     * @return the QuestData for the given trader and quest ID, or `null` if none exists
     */
    public QuestData getQuestData(String traderId, String questId) {
        Map<String, QuestData> quests = traderQuests.get(traderId);
        if (quests == null)
            return null;
        return quests.get(questId);
    }

    /**
     * Provides an unmodifiable map of all loaded traders to their quest data.
     *
     * @return a map from trader ID to a map of quest ID -> QuestData; the returned outer map and its nested maps are unmodifiable
     */
    public Map<String, Map<String, QuestData>> getAllQuests() {
        return Collections.unmodifiableMap(traderQuests);
    }

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