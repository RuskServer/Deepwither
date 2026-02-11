package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.outpost.OutpostManager;
import com.lunar_prototype.deepwither.aethelgard.GeneratedQuest;
import com.lunar_prototype.deepwither.aethelgard.QuestGenerator;
import com.lunar_prototype.deepwither.api.IItemFactory;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import io.papermc.paper.block.BlockPredicate;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.*;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.set.RegistryKeySet;
import io.papermc.paper.registry.set.RegistrySet;
import org.bukkit.*;
import org.bukkit.block.BlockType;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.Plugin;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.File;
import java.net.URL;
import java.util.*;
import java.util.logging.Level;

@DependsOn({})
public class ItemFactory implements CommandExecutor, TabCompleter, IManager, IItemFactory {
    private final Plugin plugin;
    private final Map<String, ItemStack> itemMap = new HashMap<>();
    private final NamespacedKey statKey = new NamespacedKey("rpgstats", "statmap");
    public static final NamespacedKey GRADE_KEY = new NamespacedKey(Deepwither.getInstance(), "fabrication_grade");
    public static final NamespacedKey RECIPE_BOOK_KEY = new NamespacedKey(Deepwither.getInstance(), "recipe_book_target_grade");
    public static final NamespacedKey RARITY_KEY = new NamespacedKey(Deepwither.getInstance(), "item_rarity");
    public static final NamespacedKey ITEM_TYPE_KEY = new NamespacedKey(Deepwither.getInstance(), "item_type_name");
    public static final NamespacedKey FLAVOR_TEXT_KEY = new NamespacedKey(Deepwither.getInstance(), "item_flavor_text"); // 文字列結合で保存
    public static final NamespacedKey SET_PARTNER_KEY = new NamespacedKey(Deepwither.getInstance(), "set_partner_id");
    public static final NamespacedKey SPECIAL_ACTION_KEY = new NamespacedKey(Deepwither.getInstance(), "special_action_type");
    public final Map<String, List<String>> rarityPools = new HashMap<>(); // <Rarity, List<ItemID>>
    private static final String KEY_PREFIX = "rpgstats";

    @Override
    public List<String> getItemsByRarity(String rarity) {
        return rarityPools.getOrDefault(rarity, Collections.emptyList());
    }

    @Override
    @Nullable
    public ItemStack getItem(String id) {
        return getCustomItemStack(id);
    }

    @Override
    @Nullable
    public ItemStack getItem(String id, FabricationGrade grade) {
        return getCustomItemStack(id, grade);
    }

    @Override
    @Nullable
    public ItemStack getItem(String id, int amount) {
        return getCustomCountItemStack(id, amount);
    }

    @Override
    public void giveItem(Player player, String id) {
        getCustomItem(player, id);
    }

    @Override
    @Nullable
    public ItemStack updateGrade(ItemStack item, FabricationGrade newGrade) {
        return updateItem(item, newGrade);
    }

    @Override
    @Nullable
    public ItemStack upgradeGrade(ItemStack item) {
        return upgradeItemGrade(item);
    }

    @Override
    @Nullable
    public ItemStack updateStat(ItemStack item, StatType type, double value, boolean isPercent) {
        return updateSpecificStat(item, type, value, isPercent);
    }

    @Override
    @NotNull
    public StatMap getStats(ItemStack item) {
        return readStatsFromItem(item);
    }

    @Override
    public PlayerItem of(Player player) {
        return new PlayerItem() {
            @Override
            public void give(String id) {
                giveItem(player, id);
            }

            @Override
            public void give(String id, FabricationGrade grade) {
                ItemStack item = getItem(id, grade);
                if (item != null) {
                    player.getInventory().addItem(item);
                }
            }
        };
    }

    public ItemFactory(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        loadAllItems();
        PluginCommand command = plugin.getServer().getPluginCommand("giveitem");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
    }

    @Override
    public void shutdown() {
        // 必要に応じてクリーンアップ
    }

    private void loadAllItems() {
        File itemFolder = new File(plugin.getDataFolder(), "items");
        if (!itemFolder.exists()) itemFolder.mkdirs();

        for (File file : Objects.requireNonNull(itemFolder.listFiles())) {
            if (!file.getName().endsWith(".yml")) continue;
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            Map<String, ItemStack> loaded = ItemLoader.loadItems(config,this);
            itemMap.putAll(loaded);
        }
    }

    /**
     * アイテムにステータスを適用し、再構築可能な状態でPDCに保存します。
     * このメソッドは、ItemLoaderから「ベース値（倍率前）」と「モディファイアー」を受け取り、
     * 最終的なステータス (Base * Grade + Modifiers) を計算して適用します。
     *
     * @param item            対象アイテム
     * @param baseStats       ベースステータス (Configからランダムロールされた直後の値、等級倍率適用前)
     * @param modifiers       レアリティ等による追加モディファイアー
     * @param itemType        アイテム種別名 (例: "長剣")
     * @param flavorText      フレーバーテキスト
     * @param tracker         品質トラッカー (Lore表示用)
     * @param rarity          レアリティ文字列
     * @param grade           製造等級 (指定なしならSTANDARD)
     * @return 更新されたItemStack
     */
    public ItemStack applyStatsToItem(ItemStack item, StatMap baseStats, Map<StatType, Double> modifiers,
                                      @Nullable String itemType, @Nullable List<String> flavorText,
                                      ItemLoader.RandomStatTracker tracker, @Nullable String rarity,
                                      @Nullable FabricationGrade grade) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // 1. グレードの確定と保存
        if (grade == null) {
            int gid = meta.getPersistentDataContainer().getOrDefault(GRADE_KEY, PersistentDataType.INTEGER, 1);
            grade = FabricationGrade.fromId(gid);
        } else {
            meta.getPersistentDataContainer().set(GRADE_KEY, PersistentDataType.INTEGER, grade.getId());
        }

        // 2. 最終ステータスの計算: Final = (Base * GradeMultiplier) + Modifiers
        // ただし、特定のStat(Critical Chanceなど)はGrade倍率をかけない場合がある
        StatMap finalStats = new StatMap();
        double multiplier = grade.getMultiplier();
        Set<StatType> ignoredMultipliers = EnumSet.of(StatType.CRIT_CHANCE, StatType.ATTACK_SPEED, StatType.MOVE_SPEED);

        // Base Statの計算と保存
        for (StatType type : baseStats.getAllTypes()) {
            double baseFlat = baseStats.getFlat(type);
            double basePercent = baseStats.getPercent(type);

            // PDCへ保存 (再計算用: rpgstats.base.<type>_flat)
            saveStatValue(meta, "base", type, baseFlat, basePercent);

            // 最終値の計算
            double finalFlat = baseFlat;
            if (!ignoredMultipliers.contains(type) && baseFlat != 0) {
                finalFlat *= multiplier;
            }
            finalStats.setFlat(type, finalFlat);
            finalStats.setPercent(type, basePercent); // Percentには通常倍率は乗らない想定
        }

        // Modifierの加算と保存
        if (modifiers != null) {
            for (Map.Entry<StatType, Double> entry : modifiers.entrySet()) {
                StatType type = entry.getKey();
                double modValue = entry.getValue();

                // PDCへ保存 (再計算用: rpgstats.mod.<type>)
                meta.getPersistentDataContainer().set(
                        new NamespacedKey(KEY_PREFIX, "mod." + type.name().toLowerCase()),
                        PersistentDataType.DOUBLE,
                        modValue
                );

                // 最終値に加算 (Modifierは基本的にFlat値加算)
                finalStats.setFlat(type, finalStats.getFlat(type) + modValue);
            }
        }

        // 3. メタデータ(Flavor, Type, Rarity)の保存
        if (itemType != null) {
            meta.getPersistentDataContainer().set(ITEM_TYPE_KEY, PersistentDataType.STRING, itemType);
        }
        if (rarity != null) {
            meta.getPersistentDataContainer().set(RARITY_KEY, PersistentDataType.STRING, rarity);
        }
        if (flavorText != null && !flavorText.isEmpty()) {
            // リストを特定の区切り文字で結合して保存
            String joinedFlavor = String.join("|~|", flavorText);
            meta.getPersistentDataContainer().set(FLAVOR_TEXT_KEY, PersistentDataType.STRING, joinedFlavor);
        }

        // 4. Loreの生成 (LoreBuilderには最終計算結果を渡す)
        meta.setLore(LoreBuilder.build(finalStats, false, itemType, flavorText, tracker, rarity, modifiers, grade));

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);

        // 5. 最終ステータスをPDCに保存 (ゲーム内ロジック参照用: rpgstats.<type>_flat)
        PersistentDataContainer container = meta.getPersistentDataContainer();
        for (StatType type : finalStats.getAllTypes()) {
            container.set(new NamespacedKey(KEY_PREFIX, type.name().toLowerCase() + "_flat"), PersistentDataType.DOUBLE, finalStats.getFlat(type));
            container.set(new NamespacedKey(KEY_PREFIX, type.name().toLowerCase() + "_percent"), PersistentDataType.DOUBLE, finalStats.getPercent(type));
        }

        item.setItemMeta(meta);
        item.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().addHiddenComponents(DataComponentTypes.ATTRIBUTE_MODIFIERS).build());

        return item;
    }

    // 内部ヘルパー: Base値保存
    private void saveStatValue(ItemMeta meta, String category, StatType type, double flat, double percent) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (flat != 0) {
            pdc.set(new NamespacedKey(KEY_PREFIX, category + "." + type.name().toLowerCase() + "_flat"), PersistentDataType.DOUBLE, flat);
        }
        if (percent != 0) {
            pdc.set(new NamespacedKey(KEY_PREFIX, category + "." + type.name().toLowerCase() + "_percent"), PersistentDataType.DOUBLE, percent);
        }
    }

    /**
     * @deprecated Use {@link #updateGrade(ItemStack, FabricationGrade)} instead.
     */
    @Deprecated
    public ItemStack updateItem(ItemStack item, @Nullable FabricationGrade newGrade) {
        if (item == null || !item.hasItemMeta()) return item;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // 1. 保存された情報の復元
        String itemType = pdc.get(ITEM_TYPE_KEY, PersistentDataType.STRING);
        String rarity = pdc.get(RARITY_KEY, PersistentDataType.STRING);

        // フレーバーテキスト復元
        List<String> flavorText = new ArrayList<>();
        String joinedFlavor = pdc.get(FLAVOR_TEXT_KEY, PersistentDataType.STRING);
        if (joinedFlavor != null) {
            flavorText = Arrays.asList(joinedFlavor.split("\\|~\\|"));
        }

        // BaseStats復元
        StatMap baseStats = new StatMap();
        for (StatType type : StatType.values()) {
            Double flat = pdc.get(new NamespacedKey(KEY_PREFIX, "base." + type.name().toLowerCase() + "_flat"), PersistentDataType.DOUBLE);
            Double percent = pdc.get(new NamespacedKey(KEY_PREFIX, "base." + type.name().toLowerCase() + "_percent"), PersistentDataType.DOUBLE);
            if (flat != null) baseStats.setFlat(type, flat);
            if (percent != null) baseStats.setPercent(type, percent);
        }

        // Modifiers復元
        Map<StatType, Double> modifiers = new HashMap<>();
        for (StatType type : StatType.values()) {
            Double modVal = pdc.get(new NamespacedKey(KEY_PREFIX, "mod." + type.name().toLowerCase()), PersistentDataType.DOUBLE);
            if (modVal != null) {
                modifiers.put(type, modVal);
            }
        }

        // Grade復元または更新
        FabricationGrade gradeToUse;
        if (newGrade != null) {
            gradeToUse = newGrade;
        } else {
            int gid = pdc.getOrDefault(GRADE_KEY, PersistentDataType.INTEGER, 1);
            gradeToUse = FabricationGrade.fromId(gid);
        }

        // ランダムトラッカーは復元できないため、ダミーまたは再計算が必要ですが、
        // LoreBuilderの表示用だけなら、現在のBaseとConfigの定義(不明)との比較が必要です。
        // ここでは簡易的に「保存された情報だけで再構築」するため、新規の空トラッカーを渡します。
        // ※厳密にやりたい場合はトラッカーのratioもPDCに保存する必要があります。
        ItemLoader.RandomStatTracker tracker = new ItemLoader.RandomStatTracker();
        // 改善案: tracker.ratio をPDCに保存しておき、ダミーのtrackerにセットするメソッドを作る

        // 再適用
        return applyStatsToItem(item, baseStats, modifiers, itemType, flavorText, tracker, rarity, gradeToUse);
    }

    /**
     * @deprecated Use {@link #upgradeGrade(ItemStack)} instead.
     */
    @Deprecated
    public ItemStack upgradeItemGrade(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return item;

        // 現在のグレードIDを取得 (デフォルトは1: STANDARD)
        int currentGid = item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(GRADE_KEY, PersistentDataType.INTEGER, 1);

        // 次のグレードを取得 (ID + 1)
        FabricationGrade nextGrade = FabricationGrade.fromId(currentGid + 1);

        // もし次のグレードが現在のグレードと同じなら、既に最大等級なのでそのまま返す
        if (nextGrade.getId() == currentGid) {
            return item;
        }

        // 既存の updateItem を利用して新しいグレードを適用
        return updateItem(item, nextGrade);
    }

    // 既存コード互換用のオーバーロード
    public ItemStack applyStatsToItem(ItemStack item, StatMap stats, @Nullable String itemType, @Nullable List<String> flavorText, ItemLoader.RandomStatTracker tracker, @Nullable String rarity, Map<StatType, Double> appliedModifiers) {
        // ※注意: 以前のコードフローでは ItemLoader が既に Grade倍率 を stats に適用済みの場合がありました。
        // 新しいフローでは applyStatsToItem は「BaseStats」を期待します。
        // 互換性を保つには、ここで呼び出し元がどういう値を渡しているかによりますが、
        // 基本的に ItemLoader 側も修正するため、このオーバーロードは推奨されません。
        return applyStatsToItem(item, stats, appliedModifiers, itemType, flavorText, tracker, rarity, null);
    }

    /**
     * アイテムのモディファイアー（ランダムオプション）のみを現在のレアリティに基づいて再抽選します。
     * Baseステータスやグレードは維持されます。
     *
     * @param item 対象のItemStack
     * @return モディファイアーが更新されたItemStack
     */
    public ItemStack rerollModifiers(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return item;

        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();

        // 1. 必要な情報の復元
        String rarity = pdc.get(RARITY_KEY, PersistentDataType.STRING);
        if (rarity == null) rarity = "コモン"; // デフォルト

        // BaseStatsの復元 (これは維持する)
        StatMap baseStats = restoreBaseStats(pdc);

        // その他のメタデータ復元
        String itemType = pdc.get(ITEM_TYPE_KEY, PersistentDataType.STRING);
        FabricationGrade grade = FabricationGrade.fromId(pdc.getOrDefault(GRADE_KEY, PersistentDataType.INTEGER, 1));

        List<String> flavorText = new ArrayList<>();
        String joinedFlavor = pdc.get(FLAVOR_TEXT_KEY, PersistentDataType.STRING);
        if (joinedFlavor != null) {
            flavorText = Arrays.asList(joinedFlavor.split("\\|~\\|"));
        }

        // 2. モディファイアーの完全再生成
        // ItemLoaderに追加したstaticメソッドを使用
        Map<StatType, Double> newModifiers = ItemLoader.generateRandomModifiers(rarity);

        // 3. 再適用
        // トラッカーは新規作成 (Loreの品質表記はBaseStatsの良し悪しに依存するため、本来はBase生成時の状態が必要だが、ここではリセット)
        ItemLoader.RandomStatTracker tracker = new ItemLoader.RandomStatTracker();

        return applyStatsToItem(item, baseStats, newModifiers, itemType, flavorText, tracker, rarity, grade);
    }

    /**
     * @deprecated Use {@link #updateStat(ItemStack, StatType, double, boolean)} instead.
     */
    @Deprecated
    public ItemStack updateSpecificStat(ItemStack item, StatType type, double value, boolean isPercent) {
        if (item == null || !item.hasItemMeta()) return item;

        // 1. 現在のデータをPDCから復元
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();

        // BaseStatsの復元
        StatMap baseStats = restoreBaseStats(pdc); // ※下部にヘルパーメソッド記載

        // Modifiersの復元
        Map<StatType, Double> modifiers = restoreModifiers(pdc); // ※下部にヘルパーメソッド記載

        // メタデータの復元
        String itemType = pdc.get(ITEM_TYPE_KEY, PersistentDataType.STRING);
        String rarity = pdc.get(RARITY_KEY, PersistentDataType.STRING);
        FabricationGrade grade = FabricationGrade.fromId(pdc.getOrDefault(GRADE_KEY, PersistentDataType.INTEGER, 1));

        // フレーバーテキスト復元
        List<String> flavorText = new ArrayList<>();
        String joinedFlavor = pdc.get(FLAVOR_TEXT_KEY, PersistentDataType.STRING);
        if (joinedFlavor != null) {
            flavorText = Arrays.asList(joinedFlavor.split("\\|~\\|"));
        }

        // 2. 指定ステータスの更新 (Base値に加算)
        if (isPercent) {
            double current = baseStats.getPercent(type);
            baseStats.setPercent(type, current + value);
        } else {
            double current = baseStats.getFlat(type);
            baseStats.setFlat(type, current + value);
        }

        // 3. ランダムトラッカー (更新の場合は空で渡すか、厳密にやるなら管理が必要)
        ItemLoader.RandomStatTracker tracker = new ItemLoader.RandomStatTracker();

        // 4. 再適用して保存
        return applyStatsToItem(item, baseStats, modifiers, itemType, flavorText, tracker, rarity, grade);
    }

    // --- 内部ヘルパーメソッド (PDC読み込みの重複コード排除用) ---

    private StatMap restoreBaseStats(PersistentDataContainer pdc) {
        StatMap stats = new StatMap();
        for (StatType type : StatType.values()) {
            Double flat = pdc.get(new NamespacedKey(KEY_PREFIX, "base." + type.name().toLowerCase() + "_flat"), PersistentDataType.DOUBLE);
            Double percent = pdc.get(new NamespacedKey(KEY_PREFIX, "base." + type.name().toLowerCase() + "_percent"), PersistentDataType.DOUBLE);
            if (flat != null) stats.setFlat(type, flat);
            if (percent != null) stats.setPercent(type, percent);
        }
        return stats;
    }

    private Map<StatType, Double> restoreModifiers(PersistentDataContainer pdc) {
        Map<StatType, Double> modifiers = new HashMap<>();
        for (StatType type : StatType.values()) {
            Double modVal = pdc.get(new NamespacedKey(KEY_PREFIX, "mod." + type.name().toLowerCase()), PersistentDataType.DOUBLE);
            if (modVal != null) {
                modifiers.put(type, modVal);
            }
        }
        return modifiers;
    }

    public StatMap readStatsFromItem(ItemStack item) {
        StatMap stats = new StatMap();
        if (item == null || !item.hasItemMeta()) return stats;

        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        for (StatType type : StatType.values()) {
            Double flat = container.get(new NamespacedKey(KEY_PREFIX, type.name().toLowerCase() + "_flat"), PersistentDataType.DOUBLE);
            Double percent = container.get(new NamespacedKey(KEY_PREFIX, type.name().toLowerCase() + "_percent"), PersistentDataType.DOUBLE);
            if (flat != null) stats.setFlat(type, flat);
            if (percent != null) stats.setPercent(type, percent);
        }
        return stats;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // senderがPlayer型であればplayer変数に代入し、そうでなければnull
        Player player = (sender instanceof Player) ? (Player) sender : null;

        // リロード処理 (コンソール/プレイヤー両方から許可)
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            loadAllItems();
            sender.sendMessage("§aアイテム設定をリロードしました。");
            return true;
        }

        // ★ プレイヤー専用の処理はここでチェックし、コンソールからは実行できないようにする
        if (player == null) {
            // コンソールからの実行の場合、アイテム付与とポイント付与・リセットのみを許可する
            // ただし、これらの処理は必ずターゲットプレイヤーを必要とするように修正が必要

            // アイテム付与（コンソール用）
            if (args.length == 2) {
                String id = args[0];
                String targetName = args[1];

                Player targetPlayer = Bukkit.getPlayer(targetName);
                if (targetPlayer == null) {
                    sender.sendMessage("§cプレイヤー §e" + targetName + " §cは見つかりませんでした。");
                    return true;
                }

                // ItemLoader.loadSingleItem(id, this, itemFolder); の部分は適切に修正してください
                // File itemFolder = new File(Deepwither.getInstance().getDataFolder(), "items"); // Deepwither.getInstance()を適切なプラグインインスタンスに
                // ItemStack item = ItemLoader.loadSingleItem(id, /* PluginInstance */, itemFolder);

                // 例として仮のロード処理
                File itemFolder = new File(Deepwither.getInstance().getDataFolder(), "items"); // 適切なプラグインインスタンスを使用
                ItemStack item = ItemLoader.loadSingleItem(id, this, itemFolder); // Deepwither.getInstance()を適切なプラグインインスタンスに

                if (item == null) {
                    sender.sendMessage("§cそのIDのアイテムは存在しません。");
                    return true;
                }

                targetPlayer.getInventory().addItem(item);
                sender.sendMessage("§aアイテム §e" + id + " §aをプレイヤー §e" + targetPlayer.getName() + " §aに付与しました。");
                targetPlayer.sendMessage("§aアイテム §e" + id + " §aを付与されました。");
                return true;
            }

            // addpoints / addskillpoints / resetpoints (コンソールからは未実装のまま、または別途実装が必要)
            // プレイヤーデータ操作は、引数で対象プレイヤーを指定するロジックが必要だが、
            // プレイヤー名を引数として受け取るロジックがまだ不完全なため、ここでは一旦プレイヤー専用とする。

            sender.sendMessage("§c使い方: <id> <プレイヤー名> | reload");
            return true;
        }

        if (args[0].equalsIgnoreCase("setwarp")) {
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /dw setwarp <warp_id>");
                return true;
            }
            String id = args[1];
            Deepwither.getInstance().getLayerMoveManager().setWarpLocation(id, player.getLocation());
            sender.sendMessage("§aWarp地点(" + id + ")を現在位置に設定しました。");
            return true;
        }

        // ステータスリセット処理
        if (args.length == 1 && args[0].equalsIgnoreCase("genquest")) {
            // サーバーのメインスレッドをブロックしないように、LLM呼び出しを非同期タスクで実行
            Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
                try {
                    GeneratedQuest quest = new QuestGenerator().generateQuest(5);

                    // -----------------------------------------------------------------------
                    // 結果の処理をメインスレッドに戻して安全に行う
                    // -----------------------------------------------------------------------
                    Bukkit.getScheduler().runTask(this.plugin, () -> {
                        // クエストの内容をプレイヤーに表示
                        player.sendMessage("§6--- 冒険者ギルドからの緊急依頼 ---");
                        player.sendMessage("§fタイトル：「§b" + quest.getTitle() + "§f」");
                        player.sendMessage("§e[場所]§r " + quest.getLocationDetails().getLlmLocationText());
                        player.sendMessage("§e[目標]§r " + quest.getTargetMobId() + "を" + quest.getRequiredQuantity() + "体");
                        player.sendMessage(" ");

                        // クエスト本文の表示 (改行を考慮して1行ずつ送る)
                        for (String line : quest.getQuestText().split("\n")) {
                            player.sendMessage("§7" + line);
                        }

                        player.sendMessage(" ");
                        player.sendMessage("§a[報酬]§r 200 ゴールド、経験値 500、小さな回復薬 x1");
                        player.sendMessage("§6-------------------------------------");
                    });

                } catch (Exception e) {
                    // LLM通信でエラーが発生した場合、メインスレッドに戻ってエラーメッセージを送信
                    Bukkit.getScheduler().runTask(this.plugin, () -> {
                        player.sendMessage("§c[ギルド受付]§r 依頼の生成中にエラーが発生しました。時間を置いて再度お試しください。");
                        this.plugin.getLogger().log(Level.SEVERE, "LLMクエスト生成中にエラー:", e);
                    });
                }
            });

            return true;
        }

        // ステータスリセット処理
        if (args.length == 1 && args[0].equalsIgnoreCase("resetpoints")) {
            UUID uuid = player.getUniqueId();
            AttributeManager attrManager = Deepwither.getInstance().getAttributeManager();
            PlayerAttributeData data = attrManager.get(uuid);
            if (data != null) {
                int totalAllocated = 0;
                for (StatType type : StatType.values()) {
                    totalAllocated += data.getAllocated(type);
                    data.setAllocated(type, 0);
                }
                data.addPoints(totalAllocated);
                player.sendMessage("§6すべてのステータスポイントをリセットしました。");
            } else {
                player.sendMessage("§cステータスデータが読み込まれていません。");
            }
            return true;
        }
        //reset
        if (args.length == 1 && args[0].equalsIgnoreCase("reset")) {
            Deepwither.getInstance().getLevelManager().resetLevel(player);
            return true;
        }

        // ステータスリセット処理
        if (args.length == 1 && args[0].equalsIgnoreCase("skilltreeresets")) {
            UUID uuid = player.getUniqueId();
            Deepwither.getInstance().getSkilltreeManager().resetSkillTree(uuid);
            player.sendMessage("§6すべてのステータスポイントをリセットしました。");
            return true;
        }

        // ステータスポイント付与処理
        if (args.length == 2 && args[0].equalsIgnoreCase("addpoints")) {
            try {
                int amount = Integer.parseInt(args[1]);
                UUID uuid = player.getUniqueId();
                AttributeManager attrManager = Deepwither.getInstance().getAttributeManager();
                PlayerAttributeData data = attrManager.get(uuid);
                if (data != null) {
                    data.addPoints(amount);
                    player.sendMessage("§aステータスポイントを §e" + amount + " §a付与しました。");
                } else {
                    player.sendMessage("§cステータスデータが読み込まれていません。");
                }
            } catch (NumberFormatException e) {
                player.sendMessage("§c数値を入力してください。");
            }
            return true;
        }

        // ステータスポイント付与処理
        if (args.length == 2 && args[0].equalsIgnoreCase("spawnoutpost")) {
            OutpostManager.getInstance().startRandomOutpost();
            return true;
        }

        // スキルポイント付与処理
        if (args.length == 2 && args[0].equalsIgnoreCase("addskillpoints")) {
            try {
                int amount = Integer.parseInt(args[1]);
                UUID uuid = player.getUniqueId();
                SkilltreeManager.SkillData data = Deepwither.getInstance().getSkilltreeManager().load(uuid);
                if (data != null) {
                    data.setSkillPoint(data.getSkillPoint() + amount);
                    Deepwither.getInstance().getSkilltreeManager().save(uuid, data);
                    player.sendMessage("§aスキルポイントを §e" + amount + " §a付与しました。");
                } else {
                    player.sendMessage("§cスキルツリーデータが読み込まれていません。");
                }
            } catch (NumberFormatException e) {
                player.sendMessage("§c数値を入力してください。");
            }
            return true;
        }

        // --- 引数の解析 ---
        if (args.length < 1) {
            sender.sendMessage("§c使い方: /giveitem <id> [プレイヤー名] [グレード(1-5)]");
            return true;
        }

        String id = args[0];
        Player targetPlayer = player;
        FabricationGrade grade = FabricationGrade.STANDARD; // デフォルトはFG-1

        // 1. ターゲットプレイヤーの判定
        if (args.length >= 2) {
            Player found = Bukkit.getPlayer(args[1]);
            if (found != null) {
                targetPlayer = found;
            } else if (player == null) {
                // コンソール実行でプレイヤーが見つからない場合
                sender.sendMessage("§cプレイヤー §e" + args[1] + " §cは見つかりませんでした。");
                return true;
            }
        }

        // コンソール実行でターゲットが不明な場合のエラー
        if (targetPlayer == null) {
            sender.sendMessage("§cコンソールから実行する場合はプレイヤー名を指定してください。");
            return true;
        }

        // 2. 製造等級(Fabrication Grade)の判定
        // 引数が3つある場合 (例: /dw hammer player1 5)
        if (args.length >= 3) {
            try {
                int gradeId = Integer.parseInt(args[2]);
                grade = FabricationGrade.fromId(gradeId);
            } catch (NumberFormatException e) {
                sender.sendMessage("§cグレードは数値(1-5)で指定してください。");
                return true;
            }
        }

        // --- アイテムの生成と付与 ---
        File itemFolder = new File(plugin.getDataFolder(), "items");
        // 改修した loadSingleItem を呼び出し
        ItemStack item = ItemLoader.loadSingleItem(id, this, itemFolder, grade);

        if (item == null) {
            sender.sendMessage("§cそのIDのアイテムは存在しません。");
            return true;
        }

        targetPlayer.getInventory().addItem(item);

        String msg = "§aアイテム §e" + id + " " + grade.getDisplayName() + " §aを §e" + targetPlayer.getName() + " §aに付与しました。";
        sender.sendMessage(msg);
        if (!sender.equals(targetPlayer)) {
            targetPlayer.sendMessage("§aアイテム §e" + id + " " + grade.getDisplayName() + " §aを付与されました。");
        }
        return true;
    }


    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> candidates = new ArrayList<>(itemMap.keySet());
            candidates.add("reload");
            candidates.add("resetpoints");
            candidates.add("addpoints");
            candidates.add("addskillpoints");
            candidates.add("skilltreeresets");
            return candidates.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }

        // addpoints の引数補完（ダミー値）
        if (args.length == 2 && args[0].equalsIgnoreCase("addpoints")) {
            return List.of("10", "25", "50");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("addskillpoints")) {
            return List.of("5", "10", "20");
        }

        return Collections.emptyList();
    }

    public void getCustomItem(Player player,String customitemid){
        File itemFolder = new File(plugin.getDataFolder(), "items");
        ItemStack item = ItemLoader.loadSingleItem(customitemid, this, itemFolder);
        if (item == null) {
            player.sendMessage("§cそのIDのアイテムは存在しません。");
            return;
        }
        player.getInventory().addItem(item);
    }

    public ItemStack getCustomItemStack(String customitemid){
        File itemFolder = new File(plugin.getDataFolder(), "items");
        ItemStack item = ItemLoader.loadSingleItem(customitemid, this, itemFolder);
        return item;
    }

    public ItemStack getCustomItemStack(String customitemid, FabricationGrade grade){
        File itemFolder = new File(plugin.getDataFolder(), "items");
        return ItemLoader.loadSingleItem(customitemid, this, itemFolder, grade);
    }

    public ItemStack getCustomCountItemStack(String customitemid,Integer count){
        File itemFolder = new File(plugin.getDataFolder(), "items");
        ItemStack item = ItemLoader.loadSingleItem(customitemid, this, itemFolder);
        if (item != null) {
            item.setAmount(count);
        }
        return item;
    }
}

class ItemLoader {
    private static final Random random = new Random();
    private static final String CUSTOM_ID_KEY = "custom_id";
    public static final NamespacedKey RECOVERY_AMOUNT_KEY = new NamespacedKey(Deepwither.getInstance(), "recovery_amount");
    public static final NamespacedKey COOLDOWN_KEY = new NamespacedKey(Deepwither.getInstance(), "cooldown_seconds");
    public static final NamespacedKey SKILL_CHANCE_KEY = new NamespacedKey(Deepwither.getInstance(), "onhit_chance");
    public static final NamespacedKey SKILL_COOLDOWN_KEY = new NamespacedKey(Deepwither.getInstance(), "onhit_cooldown");
    public static final NamespacedKey SKILL_ID_KEY = new NamespacedKey(Deepwither.getInstance(), "onhit_skillid");
    public static final NamespacedKey CHARGE_ATTACK_KEY = new NamespacedKey(Deepwither.getInstance(), "charge_attack");
    public static final NamespacedKey SPECIAL_ACTION_KEY = new NamespacedKey(Deepwither.getInstance(), "special_action_type");
    public static final NamespacedKey IS_WAND = new NamespacedKey(Deepwither.getInstance(), "is_wand");

    // 品質判定用Enum
    enum QualityRank {
        COMMON("普通の"), UNCOMMON("§a良質な"), RARE("§b希少な"), LEGENDARY("§6伝説の");

        private final String displayName;

        QualityRank(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static QualityRank fromRatio(double ratio) {
            if (ratio >= 0.9) return LEGENDARY;
            if (ratio >= 0.6) return RARE;
            if (ratio >= 0.3) return UNCOMMON;
            return COMMON;
        }
    }

    public static Map<StatType, Double> generateRandomModifiers(String rarity) {
        Map<StatType, Double> modifiers = new HashMap<>();

        // レアリティごとの最大数取得
        int maxModifiers = MAX_MODIFIERS_BY_RARITY.getOrDefault(rarity, 1);
        // 1～最大数の間でランダムな個数を決定
        int modifiersToApply = random.nextInt(maxModifiers) + 1;

        // 重み付きリストの作成
        List<ModifierDefinition> weightedModifiers = new ArrayList<>();
        for (ModifierDefinition def : MODIFIER_DEFINITIONS) {
            for (int j = 0; j < (int) (def.weight * 10); j++) {
                weightedModifiers.add(def);
            }
        }

        Set<StatType> appliedTypes = new HashSet<>();

        for (int m = 0; m < modifiersToApply; m++) {
            if (weightedModifiers.isEmpty()) break;

            ModifierDefinition selectedDef = weightedModifiers.get(random.nextInt(weightedModifiers.size()));

            if (appliedTypes.contains(selectedDef.type)) {
                weightedModifiers.removeIf(def -> def.type == selectedDef.type);
                m--;
                continue;
            }

            double modifierValue = selectedDef.minFlat + random.nextDouble() * (selectedDef.maxFlat - selectedDef.minFlat);
            modifiers.put(selectedDef.type, modifierValue);

            appliedTypes.add(selectedDef.type);
            weightedModifiers.removeIf(def -> def.type == selectedDef.type);
        }

        return modifiers;
    }

    // ランダムステータスの理論値＆実際値を管理するクラス
    static class RandomStatTracker {
        private double maxTotal = 0;
        private double actualTotal = 0;

        Map<StatType, Double> weightMap = Map.of(
                StatType.CRIT_CHANCE, 0.5,  // 会心倍率は数値が小さいから低めに重み
                StatType.CRIT_DAMAGE, 0.3
                // 必要に応じて追加
        );

        public void add(StatType type, double base, double spread, double actual) {
            double weight = weightMap.getOrDefault(type, 1.0); // デフォルト1.0
            maxTotal += (base + spread) * weight;
            actualTotal += actual * weight;
        }

        public double getRatio() {
            if (maxTotal == 0) return 0;
            return actualTotal / maxTotal;
        }
    }

    // --- 新規追加: モディファイアー関連 ---

    // レアリティごとのモディファイアー最大個数
    private static final Map<String, Integer> MAX_MODIFIERS_BY_RARITY = Map.of(
            "&f&lコモン", 1,
            "&a&lアンコモン", 2,
            "&b&lレア", 3,
            "&d&lエピック", 4,
            "&6&lレジェンダリー", 6
    );

    // 付与可能なモディファイアーとその重み（StatTypeと値の範囲）
    private static final List<ModifierDefinition> MODIFIER_DEFINITIONS = List.of(
            new ModifierDefinition(StatType.ATTACK_DAMAGE, 1.0, 3.0, 5.0),
            new ModifierDefinition(StatType.DEFENSE, 1.0, 2.0, 15.0),
            new ModifierDefinition(StatType.CRIT_CHANCE, 0.5, 1.0, 5.0),
            new ModifierDefinition(StatType.CRIT_DAMAGE, 0.3, 5.0, 30.0),
            new ModifierDefinition(StatType.MAX_HEALTH, 1.0, 10.0, 20.0),
            new ModifierDefinition(StatType.MAGIC_DAMAGE, 1.0, 3.0, 5.0),
            new ModifierDefinition(StatType.MAGIC_RESIST, 1.0, 2.0, 5.0),
            new ModifierDefinition(StatType.PROJECTILE_DAMAGE, 1.0, 2.0, 10.0),
            new ModifierDefinition(StatType.MAGIC_BURST_DAMAGE, 1.0, 3.0, 5.0),
            new ModifierDefinition(StatType.MAGIC_AOE_DAMAGE, 1.0, 3.0, 5.0),
            new ModifierDefinition(StatType.ATTACK_SPEED,0.1,0.1,0.2),
            new ModifierDefinition(StatType.REACH,0.1,0.2,0.5),
            new ModifierDefinition(StatType.MAX_MANA,0.5,10,40),
            new ModifierDefinition(StatType.MOVE_SPEED,0.1,0.001,0.005),
            new ModifierDefinition(StatType.COOLDOWN_REDUCTION,0.2,2,5),
            new ModifierDefinition(StatType.HP_REGEN,0.1,1,3)
    );

    // モディファイアー定義用ヘルパークラス
    static class ModifierDefinition {
        final StatType type;
        final double weight;
        final double minFlat;
        final double maxFlat;

        ModifierDefinition(StatType type, double weight, double minFlat, double maxFlat) {
            this.type = type;
            this.weight = weight;
            this.minFlat = minFlat;
            this.maxFlat = maxFlat;
        }
    }

    private static EquipmentSlot getSlotFromMaterial(Material material) {
        String name = material.name().toLowerCase(Locale.ROOT);

        // 防具
        if (name.contains("helmet") || name.contains("skull") || name.contains("head")) return EquipmentSlot.HEAD;
        if (name.contains("chestplate")) return EquipmentSlot.CHEST;
        if (name.contains("leggings")) return EquipmentSlot.LEGS;
        if (name.contains("boots")) return EquipmentSlot.FEET;

        // 武器/ツール (メインハンド)
        if (name.contains("sword") || name.contains("axe") || name.contains("pickaxe") || name.contains("hoe") || name.contains("shovel")) return EquipmentSlot.HAND;

        // その他（例: オフハンド）
        if (material == Material.SHIELD) return EquipmentSlot.OFF_HAND;

        return null;
    }

    public static ItemStack loadSingleItem(String id, ItemFactory factory, File itemFolder, @Nullable FabricationGrade grade) {
        File[] files = itemFolder.listFiles();
        if (files == null) return null;

        for (File file : files) {
            if (!file.getName().endsWith(".yml")) continue;

            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            if (config.contains(id)) {
                // loadItemsにgradeを渡す
                Map<String, ItemStack> map = loadItems(config, factory, grade);
                return map.get(id);
            }
        }
        return null;
    }

    // 既存互換用
    public static ItemStack loadSingleItem(String id, ItemFactory factory, File itemFolder) {
        return loadSingleItem(id, factory, itemFolder, FabricationGrade.STANDARD);
    }

    public static Map<String, ItemStack> loadItems(YamlConfiguration config, ItemFactory factory, @Nullable FabricationGrade forceGrade) {
        Map<String, ItemStack> result = new HashMap<>();

        for (String key : config.getKeys(false)) {
            try {
                String materialName = config.getString(key + ".material", "STONE");
                Material material = Material.matchMaterial(materialName);
                if (material == null) {
                    System.err.println("Invalid material for item: " + key);
                    continue;
                }

                ItemStack item = new ItemStack(material);
                ItemMeta meta = item.getItemMeta();
                if (meta == null) {
                    System.err.println("ItemMeta is null for: " + key);
                    continue;
                }

                if (material == Material.PLAYER_HEAD) {
                    SkullMeta skullMeta = (SkullMeta) meta;
                    String textureUrl = config.getString(key + ".texture-url");
                    if (textureUrl != null) {
                        try {
                            // Create a custom PlayerProfile with the texture URL
                            PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
                            PlayerTextures textures = profile.getTextures();
                            textures.setSkin(new URL(textureUrl));
                            profile.setTextures(textures);
                            skullMeta.setOwnerProfile(profile);
                        } catch (Exception e) {
                            System.err.println("Failed to set custom texture for player head: " + key);
                            e.printStackTrace();
                        }
                    }
                }

                int custom_model_data = config.getInt(key + ".custom_mode_data");
                if (custom_model_data != 0){
                    meta.setCustomModelData(custom_model_data);
                }

                FabricationGrade grade = (forceGrade != null) ? forceGrade : FabricationGrade.STANDARD;
                double multiplier = grade.getMultiplier();

                // ランダムステータスの品質判定用トラッカー初期化
                RandomStatTracker tracker = new RandomStatTracker();

                StatMap baseStats = new StatMap(); // ★ ここには「等級倍率適用前」の値を入れる

                if (config.isConfigurationSection(key + ".stats")) {
                    for (String statKey : config.getConfigurationSection(key + ".stats").getKeys(false)) {
                        StatType type = StatType.valueOf(statKey.toUpperCase());
                        // flat値
                        double flat;
                        if (config.isConfigurationSection(key + ".stats." + statKey + ".flat")) {
                            double base = config.getDouble(key + ".stats." + statKey + ".flat.base", 0);
                            double spread = config.getDouble(key + ".stats." + statKey + ".flat.spread", 0);
                            flat = base + (spread > 0 ? random.nextDouble() * spread : 0);
                            tracker.add(type, base, spread, flat);
                        } else {
                            flat = config.getDouble(key + ".stats." + statKey + ".flat", 0);
                            tracker.add(type, flat, 0, flat);
                        }
                        baseStats.setFlat(type, flat);

                        // percent値
                        double percent;
                        if (config.isConfigurationSection(key + ".stats." + statKey + ".percent")) {
                            double base = config.getDouble(key + ".stats." + statKey + ".percent.base", 0);
                            double spread = config.getDouble(key + ".stats." + statKey + ".percent.spread", 0);
                            percent = base + (spread > 0 ? random.nextDouble() * spread : 0);
                            tracker.add(type, base, spread, percent);
                        } else {
                            percent = config.getDouble(key + ".stats." + statKey + ".percent", 0);
                            tracker.add(type, percent, 0, percent);
                        }
                        baseStats.setPercent(type, percent);
                    }
                }

                // ----------------------------------------------------
                // --- 新規追加: レアリティに基づくモディファイアー処理 ---
                // ----------------------------------------------------
                boolean disableModifiers = config.getBoolean(key + ".disable_modifiers", false);
                Map<StatType, Double> modifiers = new HashMap<>();
                String rarity = config.getString(key + ".rarity", "コモン");

                if (!disableModifiers) {
                    int maxModifiers = MAX_MODIFIERS_BY_RARITY.getOrDefault(rarity, 1);
                    int modifiersToApply = random.nextInt(maxModifiers) + 1;

                    // (省略: Weighted List 生成ロジックは元のコードと同じ)
                    List<ModifierDefinition> weightedModifiers = new ArrayList<>();
                    for (ModifierDefinition def : MODIFIER_DEFINITIONS) {
                        for (int j = 0; j < (int) (def.weight * 10); j++) weightedModifiers.add(def);
                    }
                    Set<StatType> appliedTypes = new HashSet<>();

                    for (int m = 0; m < modifiersToApply; m++) {
                        if (weightedModifiers.isEmpty()) break;
                        ModifierDefinition selectedDef = weightedModifiers.get(random.nextInt(weightedModifiers.size()));
                        if (appliedTypes.contains(selectedDef.type)) {
                            weightedModifiers.removeIf(def -> def.type == selectedDef.type);
                            m--; continue;
                        }
                        double modifierValue = selectedDef.minFlat + random.nextDouble() * (selectedDef.maxFlat - selectedDef.minFlat);

                        // Modifier Map に追加 (Base Stats には足さない)
                        modifiers.put(selectedDef.type, modifierValue);

                        appliedTypes.add(selectedDef.type);
                        weightedModifiers.removeIf(def -> def.type == selectedDef.type);
                    }
                }
                // 品質ランク判定
                QualityRank rank = QualityRank.fromRatio(tracker.getRatio());

                boolean droppable = config.getBoolean(key + ".droppable", false);
                if (droppable) {
                    factory.rarityPools.computeIfAbsent(rarity, k -> new ArrayList<>()).add(key);
                }

                // 名前に品質名をプレフィックス付け
                String originalName = config.getString(key + ".name", key);
                String newName = originalName;
                meta.setDisplayName(newName);
                NamespacedKey pdc_key = new NamespacedKey(Deepwither.getInstance(), CUSTOM_ID_KEY);
                meta.getPersistentDataContainer().set(pdc_key,PersistentDataType.STRING,key);

                String unbreaking = config.getString(key + ".unbreaking","false");
                if ("true".equalsIgnoreCase(unbreaking)){
                    meta.setUnbreakable(true);
                }

                String chargeType = config.getString(key + ".charge_type", null); // YMLで charge_type: hammer と設定
                if (chargeType != null) {
                    meta.getPersistentDataContainer().set(CHARGE_ATTACK_KEY, PersistentDataType.STRING, chargeType);
                }

                String companiontype = config.getString(key + ".companion_type", null);
                if (companiontype != null){
                    meta.getPersistentDataContainer().set(Deepwither.getInstance().getCompanionManager().COMPANION_ID_KEY,PersistentDataType.STRING, companiontype);
                }

                String raid_boss_id = config.getString(key + ".raid_boss_id", null);
                if (raid_boss_id != null){
                    NamespacedKey raid_boss_id_key = new NamespacedKey(Deepwither.getInstance(), "raid_boss_id");
                    meta.getPersistentDataContainer().set(raid_boss_id_key,PersistentDataType.STRING, raid_boss_id);
                }

                String specialAction = config.getString(key + ".special_action");
                if (specialAction != null) {
                    meta.getPersistentDataContainer().set(SPECIAL_ACTION_KEY, PersistentDataType.STRING, specialAction.toUpperCase());
                }

                String itemType = config.getString(key + ".type", null);

                // カテゴリが「杖」または設定で is_wand: true の場合
                if ((itemType != null && itemType.equalsIgnoreCase("杖")) || config.getBoolean(key + ".is_wand")) {
                    if (meta != null) {
                        meta.getPersistentDataContainer().set(IS_WAND, PersistentDataType.BOOLEAN, true);
                    }
                }

                String setPartner = config.getString(key + ".set_partner");
                if (setPartner != null) {
                    meta.getPersistentDataContainer().set(ItemFactory.SET_PARTNER_KEY, PersistentDataType.STRING, setPartner);
                }

                item.setItemMeta(meta);
                List<String> flavorText = config.getStringList(key + ".flavor");
                // Lore + PDC 書き込みをItemFactory側で処理
                item = factory.applyStatsToItem(item, baseStats, modifiers, itemType, flavorText, tracker, rarity, grade);

                double recoveryAmount = config.getDouble(key + ".recovery-amount", 0.0);
                int cooldownSeconds = config.getInt(key + ".cooldown-seconds", 0);

                if (recoveryAmount > 0.0) {
                    ItemMeta meta2 = item.getItemMeta();
                    PersistentDataContainer container = meta2.getPersistentDataContainer();

                    // 回復量（例: DOUBLEで保存）
                    container.set(RECOVERY_AMOUNT_KEY, PersistentDataType.DOUBLE, recoveryAmount);

                    // クールダウン（例: INTEGERで保存）
                    if (cooldownSeconds > 0) {
                        container.set(COOLDOWN_KEY, PersistentDataType.INTEGER, cooldownSeconds);
                    }

                    item.setItemMeta(meta2);
                }

                //   recipe_book_grade: 2  (Grade 2のレシピからランダム)
                //   recipe_book_grade: 0  (全Gradeからランダム)
                int recipeBookGrade = config.getInt(key + ".recipe_book_grade", -1);

                if (recipeBookGrade >= -1) { // -1以外なら設定ありとみなす（0も許可）
                    // recipe_book_gradeキーが存在しない場合は -1 が返るが、
                    // 明示的に設定したい場合は getInt のデフォルト値をチェックする必要があるため、
                    // config.contains チェックの方が安全です。
                    if (config.contains(key + ".recipe_book_grade")) {
                        ItemMeta metaBook = item.getItemMeta();
                        metaBook.getPersistentDataContainer().set(ItemFactory.RECIPE_BOOK_KEY, PersistentDataType.INTEGER, recipeBookGrade);

                        // 分かりやすくLoreに追加しても良い
                        List<String> lore = metaBook.hasLore() ? metaBook.getLore() : new ArrayList<>();
                        String gradeName = (recipeBookGrade == 0) ? "全等級" : "等級 " + recipeBookGrade;
                        lore.add(ChatColor.GOLD + "右クリックで使用: " + ChatColor.WHITE + "未習得の" + gradeName + "レシピを獲得");
                        metaBook.setLore(lore);

                        item.setItemMeta(metaBook);
                    }
                }

                if (config.isConfigurationSection(key + ".on_hit")) {
                    ItemMeta meta3 = item.getItemMeta();
                    PersistentDataContainer container = meta3.getPersistentDataContainer();

                    double chance = config.getDouble(key + ".on_hit.chance", 0.0);
                    int cooldown = config.getInt(key + ".on_hit.cooldown", 0);
                    String skillId = config.getString(key + ".on_hit.mythic_skill_id", null);

                    if (skillId != null) {
                        // PDCに保存
                        container.set(SKILL_CHANCE_KEY, PersistentDataType.DOUBLE, chance);
                        container.set(SKILL_COOLDOWN_KEY, PersistentDataType.INTEGER, cooldown);
                        container.set(SKILL_ID_KEY, PersistentDataType.STRING, skillId);

                        item.setItemMeta(meta3);
                        // System.out.println(key + "にOnHitスキル: " + skillId + " (確率: " + chance + "%) を設定"); // デバッグ用
                    }
                }

                int durability = config.getInt(key + ".durability",0);
                if (durability > 0){
                    item.setData(DataComponentTypes.MAX_DAMAGE,durability);
                }

                String customArmorAssetId = config.getString(key + ".custom_armor");

                if (customArmorAssetId != null) {
                    // 1. 適切な EquipmentSlot を Material から決定する
                    EquipmentSlot slot = getSlotFromMaterial(material);

                    if (slot != null) {

                        NamespacedKey custom_armor_id = NamespacedKey.minecraft(customArmorAssetId);

                        item.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(slot).assetId(custom_armor_id).build());
                    } else {
                        System.err.println("Custom Armor Asset IDが設定されていますが、Material (" + materialName + ") は認識可能な防具/武器ではありません。");
                    }
                }

                if (config.contains(key + ".equipable")) {
                    ConfigurationSection equipSection = config.getConfigurationSection(key + ".equipable");
                    if (equipSection != null) {
                        // 装備スロットの判定 (HEAD, CHEST, LEGS, FEET, etc.)
                        String slotName = equipSection.getString("slot", "HEAD");
                        EquipmentSlot slot = EquipmentSlot.valueOf(slotName.toUpperCase());

                        // Equippableコンポーネントの構築
                        Equippable equippable = Equippable.equippable(slot)
                                .assetId(equipSection.contains("model") ? NamespacedKey.fromString(equipSection.getString("model")) : null)
                                .dispensable(equipSection.getBoolean("dispensable", true))
                                .swappable(equipSection.getBoolean("swappable", true))
                                .cameraOverlay(equipSection.contains("overlay") ? NamespacedKey.fromString(equipSection.getString("overlay")) : null)
                                .build();

                        // コンポーネントをアイテムに適用
                        item.setData(DataComponentTypes.EQUIPPABLE, equippable);
                    }
                }

                String armortrim = config.getString(key + ".armortrim");
                String armortrimmaterial = config.getString(key + ".armortrimmaterial");
                if (armortrim != null && armortrimmaterial != null) {
                    // 1. 文字列をNamespacedKeyに変換
                    NamespacedKey trimKey = NamespacedKey.minecraft(armortrim);
                    NamespacedKey materialKey = NamespacedKey.minecraft(armortrimmaterial);

                    // 2. Registry.TRIM_PATTERNS と Registry.TRIM_MATERIALS からオブジェクトを取得
                    //    注: Registryクラスは Paper 1.20.5+ で非推奨になる可能性があります。
                    TrimPattern trimPattern = Bukkit.getRegistry(TrimPattern.class).get(trimKey);
                    TrimMaterial trimMaterial = Bukkit.getRegistry(TrimMaterial.class).get(materialKey);

                    // 取得したオブジェクトがnullでないことを確認
                    if (trimPattern != null && trimMaterial != null) {
                        // 3. ArmorTrimオブジェクトを作成
                        ArmorTrim armorTrim = new ArmorTrim(trimMaterial, trimPattern);

                        // 4. DataComponentTypes.TRIM に ArmorTrim を直接セット
                        item.setData(DataComponentTypes.TRIM, ItemArmorTrim.itemArmorTrim(armorTrim));

                        // Debugging message to confirm the trim was applied.
                        //System.out.println("Applied armor trim: " + armortrim + " with material: " + armortrimmaterial + " to item " + key);
                    } else {
                        System.err.println("Failed to get trim pattern or material for item: " + key);
                    }
                }

                List<String> canBreakBlocks = config.getStringList(key + ".can_destroy");

                if (!canBreakBlocks.isEmpty()) {

                    RegistryKey<BlockType> blockRegistryKey = RegistryKey.BLOCK;
                    Registry<BlockType> blockRegistry = Bukkit.getRegistry(BlockType.class);
                    List<TypedKey<BlockType>> typedBlockKeys = new ArrayList<>();

                    for (String blockId : canBreakBlocks) {
                        NamespacedKey blockKey;
                        if (!blockId.contains(":")) {
                            blockKey = NamespacedKey.minecraft(blockId.toLowerCase());
                        } else {
                            blockKey = NamespacedKey.fromString(blockId);
                        }

                        if (blockKey != null && blockRegistry.get(blockKey) != null) {
                            typedBlockKeys.add(TypedKey.create(blockRegistryKey, blockKey));
                        } else {
                            System.err.println("無効なブロックID: " + blockId);
                        }
                    }

                    if (!typedBlockKeys.isEmpty()) {
                        // 【修正1】変数の型を RegistryKeySet に変更
                        // RegistrySet.keySet(...) は RegistryKeySet を返します
                        RegistryKeySet<BlockType> blockKeySet = RegistrySet.keySet(blockRegistryKey, typedBlockKeys);

                        // 【修正2】BlockPredicate.predicate() からビルダーを開始
                        // blocks() は RegistryKeySet を要求します
                        BlockPredicate blockPredicate = BlockPredicate.predicate()
                                .blocks(blockKeySet)
                                .build();

                        // 【修正3】List.of() でラップして渡す
                        // itemAdventurePredicate は List<BlockPredicate> を要求します
                        ItemAdventurePredicate canBreakPredicate = ItemAdventurePredicate.itemAdventurePredicate(List.of(blockPredicate));

                        item.setData(DataComponentTypes.CAN_BREAK, canBreakPredicate);
                    }
                }

                result.put(key, item);
            } catch (Exception e) {
                System.err.println("Error loading item '" + key + "': " + e.getMessage());
                e.printStackTrace();
            }
        }
        return result;
    }

    public static Map<String, ItemStack> loadItems(YamlConfiguration config, ItemFactory factory) {
        return loadItems(config, factory, null);
    }
}
