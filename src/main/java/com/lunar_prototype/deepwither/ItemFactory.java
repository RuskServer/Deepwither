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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.URL;
import java.util.*;
import java.util.logging.Level;

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
                        {StatType.CRIT_CHANCE, new double[]{2, 0}}
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
        return type.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
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

    private static int generateRandomSocketCount(String rarity) {
        double chance = 0.2; // 基本20%
        int max = 1;

        if (rarity.contains("アンコモン")) {
            chance = 0.3;
            max = 1;
        } else if (rarity.contains("レア")) {
            chance = 0.4;
            max = 2;
        } else if (rarity.contains("エピック")) {
            chance = 0.5;
            max = 2;
        } else if (rarity.contains("レジェンダリー")) {
            chance = 0.6;
            max = 3;
        }

        if (random.nextDouble() < chance) {
            return 1 + random.nextInt(max);
        }
        return 0;
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

                String itemType = config.getString(key + ".type", null);
                String normalizedType = (itemType != null) ? itemType.trim().toLowerCase() : "";
                boolean isGear = itemType != null
                        && !normalizedType.contains("素材")
                        && !normalizedType.contains("アーティファクト")
                        && !normalizedType.contains("スクロール")
                        && !normalizedType.contains("消費アイテム")
                        && !normalizedType.contains("コンパニオン");

                // ----------------------------------------------------
                // --- ランダム生成ロジック (モディファイアー & ソケット) ---
                // ----------------------------------------------------
                boolean disableModifiers = config.getBoolean(key + ".disable_modifiers", false);
                Map<StatType, Double> modifiers = new HashMap<>();
                String rarity = config.getString(key + ".rarity", "コモン");
                int socketsMax = config.getInt(key + ".sockets_max", 0);

                if (!disableModifiers && isGear) {
                    // 1. 刻印レアリティの判定 (約3%)
                    if (random.nextDouble() < 0.03) {
                        rarity = "&7&l刻印";
                        socketsMax = 3 + random.nextInt(3); // 3-5個
                        //Bukkit.getConsoleSender().sendMessage(Component.text("[RuneSystem] 刻印アイテムを生成しました: " + key + " (ソケット: " + socketsMax + ")", NamedTextColor.YELLOW));
                    } else {
                        // 2. 通常のモディファイアー生成
                        String lookupKey = rarity;
                        if (!lookupKey.startsWith("&")) {
                            if (lookupKey.equals("コモン")) lookupKey = "&f&lコモン";
                            else if (lookupKey.equals("アンコモン")) lookupKey = "&a&lアンコモン";
                            else if (lookupKey.equals("レア")) lookupKey = "&b&lレア";
                            else if (lookupKey.equals("エピック")) lookupKey = "&d&lエピック";
                            else if (lookupKey.equals("レジェンダリー")) lookupKey = "&6&lレジェンダリー";
                        }
                        
                        int maxModifiers = MAX_MODIFIERS_BY_RARITY.getOrDefault(lookupKey, 1);
                        int modifiersToApply = random.nextInt(maxModifiers) + 1;
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
                            modifiers.put(selectedDef.type, selectedDef.minFlat + random.nextDouble() * (selectedDef.maxFlat - selectedDef.minFlat));
                            appliedTypes.add(selectedDef.type);
                            weightedModifiers.removeIf(def -> def.type == selectedDef.type);
                        }
                        // 3. 通常のランダムソケット生成
                        if (socketsMax == 0) {
                            socketsMax = generateRandomSocketCount(rarity);
                            if (socketsMax > 0) {
                                //Bukkit.getConsoleSender().sendMessage(Component.text("[RuneSystem] ソケットを付与しました: " + key + " (" + rarity + " / " + socketsMax + "個)", NamedTextColor.GRAY));
                            }
                        }
                    }
                }

                // 品質ランク判定
                QualityRank rank = QualityRank.fromRatio(tracker.getRatio());

                boolean droppable = config.getBoolean(key + ".droppable", false);
                if (droppable) {
                    factory.rarityPools.computeIfAbsent(rarity, k -> new ArrayList<>()).add(key);
                }

                // PDCに保存 (Lore生成の前に必要)
                NamespacedKey customIdKey = new NamespacedKey(Deepwither.getInstance(), "custom_id");
                meta.getPersistentDataContainer().set(customIdKey, PersistentDataType.STRING, key);

                if (socketsMax > 0) {
                    meta.getPersistentDataContainer().set(new NamespacedKey("deepwither", "sockets_max"), PersistentDataType.INTEGER, socketsMax);
                }
                if (config.getBoolean(key + ".is_rune", false)) {
                    meta.getPersistentDataContainer().set(new NamespacedKey("deepwither", "is_rune"), PersistentDataType.BYTE, (byte) 1);
                }

                // 名前の設定
                meta.setDisplayName(config.getString(key + ".name", key));

                if (config.getBoolean(key + ".unbreaking", false)) {
                    meta.setUnbreakable(true);
                }
                String chargeType = config.getString(key + ".charge_type", null); 
                if (chargeType != null) {
                    meta.getPersistentDataContainer().set(CHARGE_ATTACK_KEY, PersistentDataType.STRING, chargeType);
                }
                String companiontype = config.getString(key + ".companion_type", null);
                if (companiontype != null){
                    meta.getPersistentDataContainer().set(Deepwither.getInstance().getCompanionManager().COMPANION_ID_KEY,PersistentDataType.STRING, companiontype);
                }
                String raid_boss_id = config.getString(key + ".raid_boss_id", null);
                if (raid_boss_id != null){
                    meta.getPersistentDataContainer().set(new NamespacedKey(Deepwither.getInstance(), "raid_boss_id"), PersistentDataType.STRING, raid_boss_id);
                }
                String specialAction = config.getString(key + ".special_action");
                if (specialAction != null) {
                    meta.getPersistentDataContainer().set(SPECIAL_ACTION_KEY, PersistentDataType.STRING, specialAction.toUpperCase());
                }
                if ((itemType != null && itemType.contains("杖")) || config.getBoolean(key + ".is_wand")) {
                    meta.getPersistentDataContainer().set(IS_WAND, PersistentDataType.BOOLEAN, true);
                }
                String setPartner = config.getString(key + ".set_partner");
                if (setPartner != null) {
                    meta.getPersistentDataContainer().set(ItemFactory.SET_PARTNER_KEY, PersistentDataType.STRING, setPartner);
                }

                item.setItemMeta(meta);
                List<String> flavorText = config.getStringList(key + ".flavor");
                
                // 重要: Lore + PDC 書き込み
                String artifactFullsetType = config.getString(key + ".artifact_fullset_type", null);
                item = factory.applyStatsToItem(item, baseStats, modifiers, itemType, flavorText, tracker, rarity, artifactFullsetType, grade);

                // --- 以下の古い冗長な処理は削除 ---
                
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

                        String gradeName = (recipeBookGrade == 0) ? "全等級" : "等級 " + recipeBookGrade;
                        metaBook.lore().add(Component.text("右クリックで使用: ", NamedTextColor.GOLD)
                                .append(Component.text("未習得の" + gradeName + "レシピを獲得", NamedTextColor.WHITE))
                                .decoration(TextDecoration.ITALIC, false));

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
