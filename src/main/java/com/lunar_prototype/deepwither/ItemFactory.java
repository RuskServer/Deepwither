package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.api.IItemFactory;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

@DependsOn({})
public class ItemFactory implements IManager, IItemFactory {
    private final Plugin plugin;
    private final Map<String, ItemStack> itemMap = new HashMap<>();
    private final NamespacedKey statKey = new NamespacedKey("rpgstats", "statmap");
    public static final NamespacedKey GRADE_KEY = new NamespacedKey(Deepwither.getInstance(), "fabrication_grade");
    public static final NamespacedKey RECIPE_BOOK_KEY = new NamespacedKey(Deepwither.getInstance(), "recipe_book_target_grade");
    public static final NamespacedKey RARITY_KEY = new NamespacedKey(Deepwither.getInstance(), "item_rarity");
    public static final NamespacedKey ITEM_TYPE_KEY = new NamespacedKey(Deepwither.getInstance(), "item_type_name");
    public static final NamespacedKey ARTIFACT_FULLSET_TYPE = new NamespacedKey(Deepwither.getInstance(), "artifact_fullset_type");
    public static final NamespacedKey FLAVOR_TEXT_KEY = new NamespacedKey(Deepwither.getInstance(), "item_flavor_text");
    public static final NamespacedKey SET_PARTNER_KEY = new NamespacedKey(Deepwither.getInstance(), "set_partner_id");
    public static final NamespacedKey SPECIAL_ACTION_KEY = new NamespacedKey(Deepwither.getInstance(), "special_action_type");
    public final Map<String, List<String>> rarityPools = new HashMap<>();
    private static final Map<String, ArtifactSetEffect> ARTIFACT_SET_EFFECTS = new HashMap<>();
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

    /**
     * Initializes the ItemFactory by loading all item definitions from the plugin's items folder into the internal cache.
     */
    @Override
    public void init() {
        registerDefaultArtifactSetEffects();
        loadAllItems();
    }

    /**
     * Retrieves the set of all loaded custom item ids.
     *
     * @return an unmodifiable set of all loaded custom item ids
     */
    public Set<String> getAllItemIds() {
        return Collections.unmodifiableSet(itemMap.keySet());
    }

    /**
     * Called when the manager is shutting down to perform any necessary cleanup.
     *
     * <p>No-op default implementation; override to release resources or persist state as needed.
     */
    @Override
    public void shutdown() {}

    private void loadAllItems() {
        File itemFolder = new File(plugin.getDataFolder(), "items");
        if (!itemFolder.exists()) itemFolder.mkdirs();

        itemMap.clear();
        int fileCount = 0;

        File[] files = itemFolder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (!file.getName().endsWith(".yml")) continue;
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                Map<String, ItemStack> loaded = ItemLoader.loadItems(config, this);
                if (!loaded.isEmpty()) {
                    itemMap.putAll(loaded);
                    fileCount++;
                }
            }
        }

        Bukkit.getConsoleSender().sendMessage(Component.text("[Deepwither] ", NamedTextColor.GREEN)
                .append(Component.text("アイテムのロードが完了しました: ", NamedTextColor.WHITE))
                .append(Component.text(itemMap.size() + "個", NamedTextColor.AQUA))
                .append(Component.text(" (" + fileCount + "個のファイルを走査)", NamedTextColor.GRAY)));
    }

    public ItemStack applyStatsToItem(ItemStack item, StatMap baseStats, Map<StatType, Double> modifiers,
                                      @Nullable String itemType, @Nullable List<String> flavorText,
                                      ItemLoader.RandomStatTracker tracker, @Nullable String rarity,
                                      @Nullable String artifactFullsetType,
                                      @Nullable FabricationGrade grade) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        if (grade == null) {
            int gid = meta.getPersistentDataContainer().getOrDefault(GRADE_KEY, PersistentDataType.INTEGER, 1);
            grade = FabricationGrade.fromId(gid);
        } else {
            meta.getPersistentDataContainer().set(GRADE_KEY, PersistentDataType.INTEGER, grade.getId());
        }

        StatMap finalStats = new StatMap();
        double multiplier = grade.getMultiplier();
        Set<StatType> ignoredMultipliers = EnumSet.of(StatType.CRIT_CHANCE, StatType.ATTACK_SPEED, StatType.MOVE_SPEED);

        for (StatType type : baseStats.getAllTypes()) {
            double baseFlat = baseStats.getFlat(type);
            double basePercent = baseStats.getPercent(type);
            saveStatValue(meta, "base", type, baseFlat, basePercent);
            double finalFlat = baseFlat;
            if (!ignoredMultipliers.contains(type) && baseFlat != 0) {
                finalFlat *= multiplier;
            }
            finalStats.setFlat(type, finalFlat);
            finalStats.setPercent(type, basePercent);
        }

        if (modifiers != null) {
            for (Map.Entry<StatType, Double> entry : modifiers.entrySet()) {
                StatType type = entry.getKey();
                double modValue = entry.getValue();
                meta.getPersistentDataContainer().set(
                        new NamespacedKey(KEY_PREFIX, "mod." + type.name().toLowerCase()),
                        PersistentDataType.DOUBLE,
                        modValue
                );
                finalStats.setFlat(type, finalStats.getFlat(type) + modValue);
            }
        }

        if (itemType != null) {
            meta.getPersistentDataContainer().set(ITEM_TYPE_KEY, PersistentDataType.STRING, itemType);
        }
        applyArtifactFullsetType(meta, artifactFullsetType);
        if (rarity != null) {
            meta.getPersistentDataContainer().set(RARITY_KEY, PersistentDataType.STRING, rarity);
        }
        if (flavorText != null && !flavorText.isEmpty()) {
            String joinedFlavor = String.join("|~|", flavorText);
            meta.getPersistentDataContainer().set(FLAVOR_TEXT_KEY, PersistentDataType.STRING, joinedFlavor);
        }

        // --- Rune Integration ---
        List<Component> runeLore = new ArrayList<>();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey socketsMaxKey = new NamespacedKey("deepwither", "sockets_max");
        if (pdc.has(socketsMaxKey, PersistentDataType.INTEGER)) {
            int max = pdc.getOrDefault(socketsMaxKey, PersistentDataType.INTEGER, 0);
            for (int i = 0; i < max; i++) {
                NamespacedKey runeKey = new NamespacedKey("deepwither", "rune_" + i);
                if (pdc.has(runeKey, PersistentDataType.STRING)) {
                    String runeId = pdc.get(runeKey, PersistentDataType.STRING);
                    ItemStack runeSample = getItem(runeId);
                    if (runeSample != null) {
                        StatMap runeStats = getStats(runeSample);
                        finalStats.add(runeStats);
                        
                        Component runeDisplayName = runeSample.getItemMeta().hasDisplayName() 
                            ? runeSample.getItemMeta().displayName()
                            : Component.text(runeId);
                        runeLore.add(Component.text("◆ ", NamedTextColor.AQUA).append(runeDisplayName));
                    } else {
                        runeLore.add(Component.text("◇ 空きソケット", NamedTextColor.GRAY));
                    }
                } else {
                    runeLore.add(Component.text("◇ 空きソケット", NamedTextColor.GRAY));
                }
            }
        }

        meta.lore(LoreBuilder.build(finalStats, false, itemType, artifactFullsetType, flavorText, tracker, rarity, modifiers, grade, runeLore));

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);

        PersistentDataContainer container = meta.getPersistentDataContainer();
        for (StatType type : finalStats.getAllTypes()) {
            container.set(new NamespacedKey(KEY_PREFIX, type.name().toLowerCase() + "_flat"), PersistentDataType.DOUBLE, finalStats.getFlat(type));
            container.set(new NamespacedKey(KEY_PREFIX, type.name().toLowerCase() + "_percent"), PersistentDataType.DOUBLE, finalStats.getPercent(type));
        }

        item.setItemMeta(meta);
        item.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().addHiddenComponents(DataComponentTypes.ATTRIBUTE_MODIFIERS).build());

        return item;
    }

    @Nullable
    public ItemStack setArtifactFullsetType(ItemStack item, @Nullable String artifactFullsetType) {
        if (item == null || !item.hasItemMeta()) {
            return item;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        applyArtifactFullsetType(meta, artifactFullsetType);
        item.setItemMeta(meta);
        return item;
    }

    @Nullable
    public String getArtifactFullsetType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }

        String value = pdcString(meta.getPersistentDataContainer(), ARTIFACT_FULLSET_TYPE);
        return value == null ? null : normalizeArtifactType(value);
    }

    private void applyArtifactFullsetType(ItemMeta meta, @Nullable String artifactFullsetType) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (artifactFullsetType == null || artifactFullsetType.isBlank()) {
            pdc.remove(ARTIFACT_FULLSET_TYPE);
            return;
        }

        pdc.set(ARTIFACT_FULLSET_TYPE, PersistentDataType.STRING, normalizeArtifactType(artifactFullsetType));
    }

    private void saveStatValue(ItemMeta meta, String category, StatType type, double flat, double percent) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (flat != 0) {
            pdc.set(new NamespacedKey(KEY_PREFIX, category + "." + type.name().toLowerCase() + "_flat"), PersistentDataType.DOUBLE, flat);
        }
        if (percent != 0) {
            pdc.set(new NamespacedKey(KEY_PREFIX, category + "." + type.name().toLowerCase() + "_percent"), PersistentDataType.DOUBLE, percent);
        }
    }

    @Deprecated
    public ItemStack updateItem(ItemStack item, @Nullable FabricationGrade newGrade) {
        if (item == null || !item.hasItemMeta()) return item;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        String itemType = pdc.get(ITEM_TYPE_KEY, PersistentDataType.STRING);
        String rarity = pdc.get(RARITY_KEY, PersistentDataType.STRING);

        List<String> flavorText = new ArrayList<>();
        String joinedFlavor = pdc.get(FLAVOR_TEXT_KEY, PersistentDataType.STRING);
        if (joinedFlavor != null) {
            flavorText = Arrays.asList(joinedFlavor.split(java.util.regex.Pattern.quote("|~|")));
        }

        StatMap baseStats = new StatMap();
        for (StatType type : StatType.values()) {
            Double flat = pdc.get(new NamespacedKey(KEY_PREFIX, "base." + type.name().toLowerCase() + "_flat"), PersistentDataType.DOUBLE);
            Double percent = pdc.get(new NamespacedKey(KEY_PREFIX, "base." + type.name().toLowerCase() + "_percent"), PersistentDataType.DOUBLE);
            if (flat != null) baseStats.setFlat(type, flat);
            if (percent != null) baseStats.setPercent(type, percent);
        }

        Map<StatType, Double> modifiers = new HashMap<>();
        for (StatType type : StatType.values()) {
            Double modVal = pdc.get(new NamespacedKey(KEY_PREFIX, "mod." + type.name().toLowerCase()), PersistentDataType.DOUBLE);
            if (modVal != null) {
                modifiers.put(type, modVal);
            }
        }

        FabricationGrade gradeToUse;
        if (newGrade != null) {
            gradeToUse = newGrade;
        } else {
            int gid = pdc.getOrDefault(GRADE_KEY, PersistentDataType.INTEGER, 1);
            gradeToUse = FabricationGrade.fromId(gid);
        }

        ItemLoader.RandomStatTracker tracker = new ItemLoader.RandomStatTracker();
        String artifactFullsetType = pdcString(pdc, ARTIFACT_FULLSET_TYPE);
        return applyStatsToItem(item, baseStats, modifiers, itemType, flavorText, tracker, rarity, artifactFullsetType, gradeToUse);
    }

    @Deprecated
    public ItemStack upgradeItemGrade(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return item;
        int currentGid = item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(GRADE_KEY, PersistentDataType.INTEGER, 1);
        FabricationGrade nextGrade = FabricationGrade.fromId(currentGid + 1);
        if (nextGrade.getId() == currentGid) return item;
        return updateItem(item, nextGrade);
    }

    public ItemStack applyStatsToItem(ItemStack item, StatMap stats, @Nullable String itemType, @Nullable List<String> flavorText, ItemLoader.RandomStatTracker tracker, @Nullable String rarity, Map<StatType, Double> appliedModifiers) {
        return applyStatsToItem(item, stats, appliedModifiers, itemType, flavorText, tracker, rarity, null, null);
    }

    public ItemStack applyStatsToItem(ItemStack item, StatMap stats, @Nullable String itemType, @Nullable String artifactFullsetType, @Nullable List<String> flavorText, ItemLoader.RandomStatTracker tracker, @Nullable String rarity, Map<StatType, Double> appliedModifiers) {
        return applyStatsToItem(item, stats, appliedModifiers, itemType, flavorText, tracker, rarity, artifactFullsetType, null);
    }

    public ItemStack rerollModifiers(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return item;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String rarity = pdc.get(RARITY_KEY, PersistentDataType.STRING);
        if (rarity == null) rarity = "コモン";
        StatMap baseStats = restoreBaseStats(pdc);
        String itemType = pdc.get(ITEM_TYPE_KEY, PersistentDataType.STRING);
        FabricationGrade grade = FabricationGrade.fromId(pdc.getOrDefault(GRADE_KEY, PersistentDataType.INTEGER, 1));
        List<String> flavorText = new ArrayList<>();
        String joinedFlavor = pdc.get(FLAVOR_TEXT_KEY, PersistentDataType.STRING);
        if (joinedFlavor != null) flavorText = Arrays.asList(joinedFlavor.split(java.util.regex.Pattern.quote("|~|")));
        Map<StatType, Double> newModifiers = ItemLoader.generateRandomModifiers(rarity);
        ItemLoader.RandomStatTracker tracker = new ItemLoader.RandomStatTracker();
        String artifactFullsetType = pdcString(pdc, ARTIFACT_FULLSET_TYPE);
        return applyStatsToItem(item, baseStats, newModifiers, itemType, flavorText, tracker, rarity, artifactFullsetType, grade);
    }

    @Deprecated
    public ItemStack updateSpecificStat(ItemStack item, StatType type, double value, boolean isPercent) {
        if (item == null || !item.hasItemMeta()) return item;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        StatMap baseStats = restoreBaseStats(pdc);
        Map<StatType, Double> modifiers = restoreModifiers(pdc);
        String itemType = pdc.get(ITEM_TYPE_KEY, PersistentDataType.STRING);
        String rarity = pdc.get(RARITY_KEY, PersistentDataType.STRING);
        FabricationGrade grade = FabricationGrade.fromId(pdc.getOrDefault(GRADE_KEY, PersistentDataType.INTEGER, 1));
        List<String> flavorText = new ArrayList<>();
        String joinedFlavor = pdc.get(FLAVOR_TEXT_KEY, PersistentDataType.STRING);
        if (joinedFlavor != null) flavorText = Arrays.asList(joinedFlavor.split(java.util.regex.Pattern.quote("|~|")));
        if (isPercent) baseStats.setPercent(type, baseStats.getPercent(type) + value); else baseStats.setFlat(type, baseStats.getFlat(type) + value);
        ItemLoader.RandomStatTracker tracker = new ItemLoader.RandomStatTracker();
        String artifactFullsetType = pdcString(pdc, ARTIFACT_FULLSET_TYPE);
        return applyStatsToItem(item, baseStats, modifiers, itemType, flavorText, tracker, rarity, artifactFullsetType, grade);
    }

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

    private String pdcString(PersistentDataContainer pdc, NamespacedKey key) {
        String value = pdc.get(key, PersistentDataType.STRING);
        return (value == null || value.isBlank()) ? null : value;
    }

    /**
     * Extracts stored flat and percent stat values from an ItemStack's persistent data into a StatMap.
     *
     * @param item the ItemStack to read stats from; may be null or have no meta
     * @return a StatMap containing any flat and percent values found for each StatType; if none are present an empty StatMap is returned
     */
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

    /**
     * Gives a custom item to the specified player by item id.
     *
     * If an item with the given id is found, it is added to the player's inventory;
     * otherwise the player receives a red error message indicating the item does not exist.
     *
     * @param player        the player who will receive the item
     * @param customitemid  the custom item identifier to look up and give
     */
    public void getCustomItem(Player player, String customitemid) {
        File itemFolder = new File(plugin.getDataFolder(), "items");
        ItemStack item = ItemLoader.loadSingleItem(customitemid, this, itemFolder);
        if (item == null) {
            player.sendMessage(Component.text("そのIDのアイテムは存在しません。", NamedTextColor.RED));
            return;
        }
        player.getInventory().addItem(item);
    }

    public ItemStack getCustomItemStack(String customitemid) {
        return ItemLoader.loadSingleItem(customitemid, this, new File(plugin.getDataFolder(), "items"));
    }

    public ItemStack getCustomItemStack(String customitemid, FabricationGrade grade) {
        return ItemLoader.loadSingleItem(customitemid, this, new File(plugin.getDataFolder(), "items"), grade);
    }

    public ItemStack getCustomCountItemStack(String customitemid, Integer count) {
        ItemStack item = getCustomItemStack(customitemid);
        if (item != null) item.setAmount(count);
        return item;
    }

    public static void registerArtifactSetEffect(String type, @Nullable StatMap twoSetBonus, @Nullable StatMap threeSetBonus) {
        if (type == null || type.isBlank()) {
            return;
        }
        String normalized = normalizeArtifactType(type);
        ArtifactSetEffect effect = ARTIFACT_SET_EFFECTS.computeIfAbsent(normalized, k -> new ArtifactSetEffect());
        if (twoSetBonus != null) {
            effect.setTwoSetBonus(twoSetBonus);
        }
        if (threeSetBonus != null) {
            effect.setThreeSetBonus(threeSetBonus);
        }
    }

    public static void registerArtifactSetWorkflow(String type,
                                                   int requiredCount,
                                                   ArtifactSetTrigger trigger,
                                                   @Nullable ArtifactSetCondition condition,
                                                   ArtifactSetWorkflow workflow) {
        if (type == null || type.isBlank() || requiredCount <= 0 || trigger == null || workflow == null) {
            return;
        }

        String normalized = normalizeArtifactType(type);
        ArtifactSetEffect effect = ARTIFACT_SET_EFFECTS.computeIfAbsent(normalized, k -> new ArtifactSetEffect());
        effect.addRule(new ArtifactSetRule(requiredCount, trigger, condition, workflow));
    }

    public static StatMap getArtifactSetBonus(String type, int artifactCount) {
        if (type == null || type.isBlank()) {
            return new StatMap();
        }

        ArtifactSetEffect effect = ARTIFACT_SET_EFFECTS.get(normalizeArtifactType(type));
        if (effect == null) {
            return new StatMap();
        }

        return effect.getBonusForCount(artifactCount);
    }

    public static boolean hasArtifactSetBonus(String type, int artifactCount) {
        return !getArtifactSetBonus(type, artifactCount).getAllTypes().isEmpty();
    }

    public static List<ArtifactSetRule> getArtifactSetRules(String type) {
        if (type == null || type.isBlank()) {
            return Collections.emptyList();
        }

        ArtifactSetEffect effect = ARTIFACT_SET_EFFECTS.get(normalizeArtifactType(type));
        if (effect == null) {
            return Collections.emptyList();
        }

        return effect.getRules();
    }

    public static boolean hasArtifactSetWorkflow(String type, int artifactCount) {
        if (type == null || type.isBlank()) {
            return false;
        }

        ArtifactSetEffect effect = ARTIFACT_SET_EFFECTS.get(normalizeArtifactType(type));
        if (effect == null) {
            return false;
        }

        for (ArtifactSetRule rule : effect.getRules()) {
            if (rule.getRequiredCount() <= artifactCount) {
                return true;
            }
        }
        return false;
    }

    public static Component getArtifactSetDisplayName(String type) {
        String normalized = normalizeArtifactType(type);
        if (normalized == null) {
            return Component.text("Unknown", NamedTextColor.GOLD);
        }

        return switch (normalized) {
            case "abyss_pulsation" -> Component.text("虚空の脈動 / Abyss Pulsation", NamedTextColor.GOLD);
            case "celestial_resonance" -> Component.text("星天の共鳴 / Celestial Resonance", NamedTextColor.GOLD);
            case "fault_line" -> Component.text("境界の断層 / Fault Line", NamedTextColor.GOLD);
            case "astral_steel_guard" -> Component.text("星鉄の守護 / Astral Steel Guard", NamedTextColor.GOLD);
            case "lunar_skirmisher" -> Component.text("月影の遊撃 / Lunar Skirmisher", NamedTextColor.GOLD);
            case "eternal_hearts" -> Component.text("不朽の双心 / Eternal Hearts", NamedTextColor.GOLD);
            default -> Component.text(type, NamedTextColor.GOLD);
        };
    }

    public static List<Component> getArtifactSetLoreLines(String type) {
        String normalized = normalizeArtifactType(type);
        if (normalized == null) {
            return Collections.emptyList();
        }

        return switch (normalized) {
            case "abyss_pulsation" -> List.of(
                    Component.text("2セット: 最大HP +10%, 魔法ダメージ +8%", NamedTextColor.AQUA),
                    Component.text("3セット: 魔法被弾時、8秒CDで完全遮断障壁を展開", NamedTextColor.LIGHT_PURPLE),
                    Component.text("         障壁発動時、周囲3ブロックをノックバックし盲目を付与", NamedTextColor.GRAY)
            );
            case "celestial_resonance" -> List.of(
                    Component.text("2セット: 最大マナ +60, 魔法AoEダメージ +15", NamedTextColor.AQUA),
                    Component.text("3セット: 魔法攻撃時、マナ75%以上なら魔法ダメージの5%を確定ダメージ化", NamedTextColor.LIGHT_PURPLE),
                    Component.text("         魔法バースト発動時、25%で1回だけ再発動", NamedTextColor.GRAY)
            );
            case "fault_line" -> List.of(
                    Component.text("2セット: 近接攻撃力 +12, クリティカル率 +2%", NamedTextColor.AQUA),
                    Component.text("3セット: クリティカル時、10秒CDで対象の防御を20%無視", NamedTextColor.LIGHT_PURPLE),
                    Component.text("         クリティカル後3秒間、移動速度 +10%", NamedTextColor.GRAY)
            );
            case "astral_steel_guard" -> List.of(
                    Component.text("2セット: 防御力 +25, HP回復 +5", NamedTextColor.AQUA),
                    Component.text("3セット: 物理被弾時、受けたダメージの10%をマナへ変換", NamedTextColor.LIGHT_PURPLE),
                    Component.text("         HP30%以下で60秒CDの再生IIを付与", NamedTextColor.GRAY),
                    Component.text("         魔法抵抗力 +15", NamedTextColor.GRAY)
            );
            case "lunar_skirmisher" -> List.of(
                    Component.text("2セット: 移動速度 +0.02, 遠距離ダメージ +10", NamedTextColor.AQUA),
                    Component.text("3セット: 移動速度が高い時、5%の完全回避を付与", NamedTextColor.LIGHT_PURPLE),
                    Component.text("         ダッシュジャンプ後の初撃に魔法追撃を付与", NamedTextColor.GRAY)
            );
            case "eternal_hearts" -> List.of(
                    Component.text("2セット: 最大HP +25%, ノックバック耐性 +0.2", NamedTextColor.AQUA),
                    Component.text("3セット: 致死ダメージを1回だけHP1で耐える", NamedTextColor.LIGHT_PURPLE),
                    Component.text("         発動時、周囲の敵を強く弾き飛ばす (300秒CD)", NamedTextColor.GRAY)
            );
            default -> Collections.emptyList();
        };
    }

    private static void registerDefaultArtifactSetEffects() {
        if (!ARTIFACT_SET_EFFECTS.isEmpty()) {
            return;
        }

        registerArtifactSetEffect("abyss_pulsation",
                statMapOf(new Object[][]{
                        {StatType.MAX_HEALTH, new double[]{0, 10}},
                        {StatType.MAGIC_DAMAGE, new double[]{0, 8}}
                }),
                null);
        registerArtifactSetWorkflow("abyss_pulsation", 3, ArtifactSetTrigger.DAMAGE_TAKEN,
                ctx -> ctx.isMagicDamage() && ArtifactSetWorkflows.tryUseCooldown(ctx.getPlayer(), "abyss_pulsation_guard", 8000L),
                ArtifactSetWorkflows.magicBarrierFullBlockWithAoe(Component.text("虚空の守護障壁が発動した。", NamedTextColor.AQUA)));

        registerArtifactSetEffect("celestial_resonance",
                statMapOf(new Object[][]{
                        {StatType.MAX_MANA, new double[]{60, 0}},
                        {StatType.MAGIC_AOE_DAMAGE, new double[]{15, 0}}
                }),
                null);
        registerArtifactSetWorkflow("celestial_resonance", 3, ArtifactSetTrigger.ATTACK_HIT,
                ctx -> ctx.isMagicDamage(),
                ctx -> {
                    if (ctx.hasTag("ARTIFACT_CELESTIAL_BURST_REPLAY")) {
                        return;
                    }
                    Player player = ctx.getPlayer();
                    double maxMana = Deepwither.getInstance().getManaManager().get(player.getUniqueId()).getMaxMana();
                    double currentMana = Deepwither.getInstance().getManaManager().get(player.getUniqueId()).getCurrentMana();
                    if (maxMana > 0 && (currentMana / maxMana) >= 0.75) {
                        double bonus = ctx.getBaseDamage() * 0.05;
                        if (bonus > 0) {
                            ctx.put("celestial_true_damage", bonus);
                        }
                    }
                    if (!ctx.hasTag("BURST")) {
                        return;
                    }
                    if (!ArtifactSetWorkflows.tryRollChance(25.0)) {
                        return;
                    }
                    ctx.put("celestial_burst_repeat", Boolean.TRUE);
                    ctx.sendMessage(Component.text("過負荷連鎖が発動した。", NamedTextColor.LIGHT_PURPLE));
                });

        registerArtifactSetEffect("fault_line",
                statMapOf(new Object[][]{
                        {StatType.ATTACK_DAMAGE, new double[]{12, 0}},
                        {StatType.CRIT_CHANCE, new double[]{1, 0}}
                }),
                null);
        registerArtifactSetWorkflow("fault_line", 3, ArtifactSetTrigger.CRIT,
                ctx -> !ctx.isMagicDamage() && !ctx.isProjectile(),
                ctx -> {
                    if (!ArtifactSetWorkflows.tryUseCooldown(ctx.getPlayer(), "fault_line_rift_edge", 10000L)) {
                        return;
                    }
                    ctx.setDefenseBypassPercent(Math.max(ctx.getDefenseBypassPercent(), 20.0));
                    ArtifactSetWorkflows.grantSpeedBoost(ctx.getPlayer(), 60, 0.10);
                    ctx.sendMessage(Component.text("次元の亀裂が走った。", NamedTextColor.RED));
                });

        registerArtifactSetEffect("astral_steel_guard",
                statMapOf(new Object[][]{
                        {StatType.DEFENSE, new double[]{25, 0}},
                        {StatType.HP_REGEN, new double[]{5, 0}},
                        {StatType.MAGIC_RESIST, new double[]{15, 0}}
                }),
                null);
        registerArtifactSetWorkflow("astral_steel_guard", 3, ArtifactSetTrigger.DAMAGE_TAKEN,
                ctx -> !ctx.isMagicDamage(),
                ctx -> {
                    double damage = ctx.getDamage();
                    if (damage <= 0) {
                        return;
                    }
                    ArtifactSetWorkflows.grantMana(ctx.getPlayer(), damage * 0.10);
                    ctx.sendMessage(Component.text("星鉄のリサイクラーが作動した。", NamedTextColor.AQUA));
                });
        registerArtifactSetWorkflow("astral_steel_guard", 3, ArtifactSetTrigger.DAMAGE_TAKEN,
                ctx -> {
                    Player player = ctx.getPlayer();
                    double maxHp = Deepwither.getInstance().getStatManager().getActualMaxHealth(player);
                    double currentHp = Deepwither.getInstance().getStatManager().getActualCurrentHealth(player);
                    return currentHp > 0 && (currentHp - ctx.getDamage()) <= (maxHp * 0.30);
                },
                ctx -> ArtifactSetWorkflows.grantOncePerCooldownRegeneration(ctx.getPlayer(), "astral_steel_guard_regen", 60000L, 200, 1));

        registerArtifactSetEffect("lunar_skirmisher",
                statMapOf(new Object[][]{
                        {StatType.MOVE_SPEED, new double[]{0.02, 0}},
                        {StatType.PROJECTILE_DAMAGE, new double[]{10, 0}}
                }),
                null);
        registerArtifactSetWorkflow("lunar_skirmisher", 3, ArtifactSetTrigger.DAMAGE_TAKEN,
                ctx -> {
                    var attr = ctx.getPlayer().getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED);
                    return attr != null && attr.getValue() >= 0.12;
                },
                ctx -> {
                    if (!ArtifactSetWorkflows.tryRollChance(5.0)) {
                        return;
                    }
                    ctx.cancelDamage();
                    ctx.sendMessage(Component.text("月影の残像がダメージを逸らした。", NamedTextColor.GRAY));
                });
        registerArtifactSetWorkflow("lunar_skirmisher", 3, ArtifactSetTrigger.ATTACK_HIT,
                ctx -> !ctx.isMagicDamage() && !ctx.isProjectile(),
                ctx -> {
                    if (ctx.hasTag("ARTIFACT_LUNAR_ECHO_REPLAY")) {
                        return;
                    }
                    Player player = ctx.getPlayer();
                    if (player.isOnGround() || !player.isSprinting() || player.getVelocity().getY() <= 0.08) {
                        return;
                    }
                    if (!ArtifactSetWorkflows.tryUseCooldown(player, "lunar_skirmisher_echo", 1000L)) {
                        return;
                    }
                    double bonus = Deepwither.getInstance().getStatManager().getTotalStats(player).getFinal(StatType.PROJECTILE_DAMAGE) * 0.15;
                    if (bonus > 0) {
                        ctx.put("lunar_echo_bonus_magic", bonus);
                        ctx.sendMessage(Component.text("月影の残像が追撃を生んだ。", NamedTextColor.YELLOW));
                    }
                });

        registerArtifactSetEffect("eternal_hearts",
                statMapOf(new Object[][]{
                        {StatType.MAX_HEALTH, new double[]{0, 25}},
                        {StatType.KNOCKBACK_RESISTANCE, new double[]{0.2, 0}}
                }),
                null);
        registerArtifactSetWorkflow("eternal_hearts", 3, ArtifactSetTrigger.DAMAGE_TAKEN,
                ctx -> {
                    Player player = ctx.getPlayer();
                    double currentHp = Deepwither.getInstance().getStatManager().getActualCurrentHealth(player);
                    return ctx.getDamage() >= currentHp;
                },
                ctx -> {
                    if (!ArtifactSetWorkflows.tryUseCooldown(ctx.getPlayer(), "eternal_hearts_last_stand", 300000L)) {
                        return;
                    }
                    double currentHp = Deepwither.getInstance().getStatManager().getActualCurrentHealth(ctx.getPlayer());
                    ctx.setDamage(Math.max(0.0, currentHp - 1.0));
                    ArtifactSetWorkflows.knockBackNearbyEnemies(ctx.getPlayer(), 4.0, 1.1);
                    ctx.getPlayer().getWorld().playSound(ctx.getPlayer().getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
                    ctx.sendMessage(Component.text("不朽の双心が死を拒んだ。", NamedTextColor.GOLD));
                });
    }

    private static StatMap statMapOf(Object[][] entries) {
        StatMap map = new StatMap();
        if (entries == null) {
            return map;
        }
        for (Object[] entry : entries) {
            if (entry.length < 2 || !(entry[0] instanceof StatType statType) || !(entry[1] instanceof double[] values) || values.length < 2) {
                continue;
            }
            double flat = values[0];
            double percent = values[1];
            if (flat != 0) {
                map.setFlat(statType, flat);
            }
            if (percent != 0) {
                map.setPercent(statType, percent);
            }
        }
        return map;
    }

    private static String normalizeArtifactType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }

        String normalized = type.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        return switch (normalized) {
            case "虚空の脈動", "abyss_pulsation", "abyss pulsation" -> "abyss_pulsation";
            case "星天の共鳴", "celestial_resonance", "celestial resonance" -> "celestial_resonance";
            case "境界の断層", "fault_line", "fault line" -> "fault_line";
            case "星鉄の守護", "astral_steel_guard", "astral steel guard" -> "astral_steel_guard";
            case "月影の遊撃", "lunar_skirmisher", "lunar skirmisher" -> "lunar_skirmisher";
            case "不朽の双心", "eternal_hearts", "eternal hearts" -> "eternal_hearts";
            default -> normalized;
        };
    }

    public enum ArtifactSetTrigger {
        DAMAGE_TAKEN,
        ATTACK_HIT,
        CRIT
    }

    @FunctionalInterface
    public interface ArtifactSetCondition {
        boolean test(ArtifactSetContext context);
    }

    @FunctionalInterface
    public interface ArtifactSetWorkflow {
        void execute(ArtifactSetContext context);
    }

    public static final class ArtifactSetContext {
        private final Player player;
        private final com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent damageEvent;
        private final com.lunar_prototype.deepwither.core.damage.DamageContext damageContext;
        private final String artifactType;
        private final int artifactCount;
        private final Map<String, Object> values = new HashMap<>();

        public ArtifactSetContext(Player player,
                                  com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent damageEvent,
                                  String artifactType,
                                  int artifactCount) {
            this.player = player;
            this.damageEvent = damageEvent;
            this.damageContext = null;
            this.artifactType = artifactType;
            this.artifactCount = artifactCount;
        }

        public ArtifactSetContext(Player player,
                                  com.lunar_prototype.deepwither.core.damage.DamageContext damageContext,
                                  String artifactType,
                                  int artifactCount) {
            this.player = player;
            this.damageEvent = null;
            this.damageContext = damageContext;
            this.artifactType = artifactType;
            this.artifactCount = artifactCount;
        }

        public Player getPlayer() {
            return player;
        }

        public com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent getDamageEvent() {
            return damageEvent;
        }

        public com.lunar_prototype.deepwither.core.damage.DamageContext getDamageContext() {
            return damageContext;
        }

        public String getArtifactType() {
            return artifactType;
        }

        public int getArtifactCount() {
            return artifactCount;
        }

        public double getDamage() {
            if (damageEvent != null) {
                return damageEvent.getDamage();
            }
            if (damageContext != null) {
                return damageContext.getFinalDamage();
            }
            return 0.0;
        }

        public double getBaseDamage() {
            if (damageContext != null) {
                return damageContext.getBaseDamage();
            }
            return getDamage();
        }

        public void setDamage(double damage) {
            if (damageEvent != null) {
                damageEvent.setDamage(damage);
            } else if (damageContext != null) {
                damageContext.setFinalDamage(damage);
            }
        }

        public void cancelDamage() {
            if (damageEvent != null) {
                damageEvent.setCancelled(true);
            } else if (damageContext != null) {
                damageContext.setFinalDamage(0.0);
                damageContext.put("_artifact_cancelled", Boolean.TRUE);
            }
        }

        public boolean isMagicDamage() {
            if (damageEvent != null) {
                return damageEvent.isMagic();
            }
            return damageContext != null && damageContext.isMagic();
        }

        public com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent.DamageType getDamageType() {
            if (damageEvent != null) {
                return damageEvent.getType();
            }
            return damageContext != null ? damageContext.getDamageType() : null;
        }

        public org.bukkit.entity.LivingEntity getAttacker() {
            if (damageEvent != null) {
                return damageEvent.getAttacker();
            }
            return damageContext != null ? damageContext.getAttacker() : null;
        }

        public org.bukkit.entity.LivingEntity getVictim() {
            if (damageEvent != null) {
                return damageEvent.getVictim();
            }
            return damageContext != null ? damageContext.getVictim() : null;
        }

        public void sendMessage(Component message) {
            player.sendMessage(message);
        }

        public void playSound(Sound sound, float volume, float pitch) {
            player.getWorld().playSound(player.getLocation(), sound, volume, pitch);
        }

        public void spawnParticle(Particle particle, int count, double offsetX, double offsetY, double offsetZ, double extra) {
            player.getWorld().spawnParticle(particle, player.getLocation().add(0, 1.0, 0), count, offsetX, offsetY, offsetZ, extra);
        }

        public float getAbsorptionAmount() {
            return (float) player.getAbsorptionAmount();
        }

        public void setAbsorptionAmount(float amount) {
            player.setAbsorptionAmount(amount);
        }

        public boolean isCriticalHit() {
            return damageContext != null && damageContext.isCrit();
        }

        public boolean isProjectile() {
            return damageContext != null && damageContext.isProjectile();
        }

        public boolean hasTag(String tag) {
            return damageContext != null && damageContext.hasTag(tag);
        }

        public void addTag(String tag) {
            if (damageContext != null) {
                damageContext.addTag(tag);
            }
        }

        public void setDefenseBypassPercent(double defenseBypassPercent) {
            if (damageContext != null) {
                damageContext.setDefenseBypassPercent(defenseBypassPercent);
            }
        }

        public double getDefenseBypassPercent() {
            return damageContext != null ? damageContext.getDefenseBypassPercent() : 0.0;
        }

        public void put(String key, Object value) {
            values.put(key, value);
            if (damageContext != null) {
                damageContext.put(key, value);
            }
        }

        @SuppressWarnings("unchecked")
        public <T> T get(String key) {
            if (damageContext != null) {
                T value = damageContext.get(key);
                if (value != null) {
                    return value;
                }
            }
            return (T) values.get(key);
        }
    }

    public static final class ArtifactSetRule {
        private final int requiredCount;
        private final ArtifactSetTrigger trigger;
        private final ArtifactSetCondition condition;
        private final ArtifactSetWorkflow workflow;

        public ArtifactSetRule(int requiredCount,
                               ArtifactSetTrigger trigger,
                               @Nullable ArtifactSetCondition condition,
                               ArtifactSetWorkflow workflow) {
            this.requiredCount = requiredCount;
            this.trigger = trigger;
            this.condition = condition;
            this.workflow = workflow;
        }

        public boolean matches(ArtifactSetTrigger trigger, ArtifactSetContext context) {
            if (this.trigger != trigger || context.getArtifactCount() < requiredCount) {
                return false;
            }
            return condition == null || condition.test(context);
        }

        public void execute(ArtifactSetContext context) {
            workflow.execute(context);
        }

        public int getRequiredCount() {
            return requiredCount;
        }
    }

    private static final class ArtifactSetEffect {
        private StatMap twoSetBonus;
        private StatMap threeSetBonus;
        private final List<ArtifactSetRule> rules = new ArrayList<>();

        private void setTwoSetBonus(@Nullable StatMap bonus) {
            this.twoSetBonus = bonus;
        }

        private void setThreeSetBonus(@Nullable StatMap bonus) {
            this.threeSetBonus = bonus;
        }

        private void addRule(ArtifactSetRule rule) {
            rules.add(rule);
        }

        private StatMap getBonusForCount(int count) {
            StatMap bonus = new StatMap();
            if (count >= 2 && twoSetBonus != null) {
                bonus.add(twoSetBonus);
            }
            if (count >= 3 && threeSetBonus != null) {
                bonus.add(threeSetBonus);
            }
            return bonus;
        }

        private List<ArtifactSetRule> getRules() {
            return Collections.unmodifiableList(rules);
        }
    }
}