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

        // セッター (条件設定用)
        public void setDistance(double min, double max) {
            this.minDistance = min;
            this.maxDistance = max;
        }

        public void setRequiredWeapon(String weapon) {
            this.requiredWeapon = weapon;
        }

        public void setRequiredArmor(String armor) {
            this.requiredArmor = armor;
        }

        // ゲッター
        public String getId() {
            return id;
        }

        public String getDisplayName() {
            return displayName;
        }

        public List<String> getDescription() {
            return description;
        }

        public String getType() {
            return type;
        }

        public String getTarget() {
            return target;
        }

        public int getAmount() {
            return amount;
        }

        public String getRequiredQuestId() {
            return requiredQuestId;
        }

        public int getRewardCredit() {
            return rewardCredit;
        }

        public double getMinDistance() {
            return minDistance;
        }

        public double getMaxDistance() {
            return maxDistance;
        }

        public String getRequiredWeapon() {
            return requiredWeapon;
        }

        public String getRequiredArmor() {
            return requiredArmor;
        }
    }

    // --- トレーダー購入オファーのロード ---

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
     * YAMLのquestsセクションをパースする
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

    // --- 売却オファーのロード ---

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
     * プレイヤーの信用度に関係なく、指定されたトレーダーの全オファーを信用度ティアレベルの低い順に取得する。
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
     * トレーダーIDとアイテムのID(ID/Material)を指定して、最初に見つかったオファーを取得する。
     * 
     * @param traderId トレーダーID
     * @param itemId   検索したいアイテムのID (Material名 or custom_id)
     * @return 該当する TraderOffer、見つからない場合は null
     */
    public TraderOffer getOfferById(String traderId, String itemId) {
        return getAllOffers(traderId).stream()
                .filter(offer -> offer.getId().equals(itemId))
                .findFirst()
                .orElse(null);
    }

    /**
     * プレイヤーがそのティアにアクセス可能か判定する
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
     * 特定のトレーダーの特定のクエストデータを取得します。
     * 
     * @param traderId トレーダーのID
     * @param questId  クエストのID
     * @return QuestData。存在しない場合はnullを返します。
     */
    public QuestData getQuestData(String traderId, String questId) {
        Map<String, QuestData> quests = traderQuests.get(traderId);
        if (quests == null)
            return null;
        return quests.get(questId);
    }

    /**
     * 現在ロードされている全トレーダーのクエスト情報を取得します（管理・監視用）。
     * 
     * @return [TraderID -> [QuestID -> QuestData]] のマップ
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