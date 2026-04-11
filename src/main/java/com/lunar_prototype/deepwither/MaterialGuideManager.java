package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.fishing.FishingManager;
import com.lunar_prototype.deepwither.loot.LootChestManager;
import com.lunar_prototype.deepwither.loot.LootChestTemplate;
import com.lunar_prototype.deepwither.loot.LootEntry;
import com.lunar_prototype.deepwither.loot.RouteLootChestManager;
import com.lunar_prototype.deepwither.modules.mine.MineService;
import com.lunar_prototype.deepwither.outpost.OutpostManager;
import com.lunar_prototype.deepwither.outpost.OutpostConfig;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@DependsOn({LootChestManager.class, RouteLootChestManager.class, OutpostManager.class, FishingManager.class, MineService.class, ItemFactory.class})
public class MaterialGuideManager implements IManager {

    private final Deepwither plugin;
    // Map<ItemID, Map<Category, Set<SourceName>>>
    private final Map<String, Map<String, Set<String>>> itemSources = new ConcurrentHashMap<>();
    private final List<ItemStack> cachedDisplayItems = Collections.synchronizedList(new ArrayList<>());

    public MaterialGuideManager(Deepwither plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        refreshData();
    }

    public void refreshData() {
        itemSources.clear();
        collectFromLootChests();
        collectFromRouteLootChests();
        collectFromOutposts();
        collectFromFishing();
        collectFromMining();
        updateDisplayCache();
    }

    private void collectFromLootChests() {
        LootChestManager manager = plugin.get(LootChestManager.class);
        if (manager == null) return;

        String category = "ルートチェスト・宝箱";

        // ティア付きチェスト
        manager.getTieredTemplates().forEach((tier, weightedList) -> {
            String sourceName = "第 " + tier + " 層 ルートチェスト";
            for (LootChestManager.WeightedTemplate wt : weightedList) {
                scanTemplate(wt.getTemplate(), category, sourceName);
            }
        });

        // 共通テンプレート
        manager.getTemplates().forEach((name, template) -> {
            String sourceName = "ランダムチェスト (" + name + ")";
            scanTemplate(template, category, sourceName);
        });
    }

    private void collectFromRouteLootChests() {
        RouteLootChestManager manager = plugin.get(RouteLootChestManager.class);
        if (manager == null) return;

        String category = "宝箱イベント";

        manager.getLayerBindings().forEach((tier, binding) -> {
            String sourceName = "第 " + tier + " 層 イベントチェスト (" + binding.rangeLabel + ")";
            if (binding.config.commonTemplate != null) scanTemplate(binding.config.commonTemplate, category, sourceName);
            if (binding.config.rareTemplate != null) scanTemplate(binding.config.rareTemplate, category, sourceName);
        });
    }

    private void collectFromOutposts() {
        OutpostManager manager = plugin.get(OutpostManager.class);
        if (manager == null || manager.getConfig() == null) return;

        String category = "拠点防衛報酬";

        manager.getConfig().getOutposts().forEach((id, data) -> {
            String outpostName = data.getDisplayName();
            OutpostConfig.Rewards rewards = data.getRewards();
            
            for (OutpostConfig.RewardItem item : rewards.getTopContributor()) addSource(item.getCustomItemId(), category, outpostName);
            for (OutpostConfig.RewardItem item : rewards.getAverageContributor()) addSource(item.getCustomItemId(), category, outpostName);
            for (OutpostConfig.RewardItem item : rewards.getMinimumReward()) addSource(item.getCustomItemId(), category, outpostName);
        });
    }

    private void collectFromFishing() {
        FishingManager manager = plugin.get(FishingManager.class);
        if (manager == null) return;

        String category = "釣り";

        manager.getLootTable().forEach((rarity, entries) -> {
            String sourceName = translateRarity(rarity);
            for (FishingManager.LootEntry entry : entries) {
                addSource(entry.getId(), category, sourceName);
            }
        });
    }

    private void collectFromMining() {
        MineService manager = plugin.get(MineService.class);
        if (manager == null) return;

        String category = "採掘";

        manager.getRuleCache().forEach((mat, rule) -> {
            String sourceName = mat.name();
            for (MineService.DropDefinition drop : rule.drops()) {
                addSource(drop.itemId(), category, sourceName);
            }
        });
    }

    private String translateRarity(String rarity) {
        return switch (rarity.toUpperCase()) {
            case "LEGENDARY" -> "レジェンダリー";
            case "EPIC" -> "エピック";
            case "RARE" -> "レア";
            case "UNCOMMON" -> "アンコモン";
            case "COMMON" -> "一般";
            default -> rarity;
        };
    }

    private void scanTemplate(LootChestTemplate template, String category, String sourceName) {
        if (template == null) return;
        for (LootEntry entry : template.getEntries()) {
            if (entry.isCustom()) {
                addSource(entry.getItemId(), category, sourceName);
            }
        }
    }

    private void addSource(String itemId, String category, String sourceName) {
        itemSources.computeIfAbsent(itemId, k -> new ConcurrentHashMap<>())
                   .computeIfAbsent(category, k -> Collections.synchronizedSet(new LinkedHashSet<>()))
                   .add(sourceName);
    }

    public void updateDisplayCache() {
        ItemFactory factory = plugin.getItemFactory();
        List<ItemStack> rawMaterials = new ArrayList<>();
        
        for (String id : factory.getAllItemIds()) {
            ItemStack item = factory.getCustomItemStack(id);
            if (item == null || !item.hasItemMeta()) continue;
            
            String type = item.getItemMeta().getPersistentDataContainer().get(ItemFactory.ITEM_TYPE_KEY, PersistentDataType.STRING);
            if (type != null && type.contains("素材")) {
                rawMaterials.add(item);
            }
        }
        
        rawMaterials.sort(Comparator.comparing(i -> {
            if (i.getItemMeta().hasDisplayName()) return i.getItemMeta().displayName().toString();
            return i.getType().name();
        }));

        List<ItemStack> newCache = new ArrayList<>();
        NamespacedKey customIdKey = new NamespacedKey(plugin, "custom_id");

        for (ItemStack raw : rawMaterials) {
            ItemStack display = raw.clone();
            ItemMeta meta = display.getItemMeta();
            
            String itemId = meta.getPersistentDataContainer().get(customIdKey, PersistentDataType.STRING);
            Map<String, Set<String>> categoriedSources = itemSources.getOrDefault(itemId, Collections.emptyMap());

            List<Component> lore = meta.hasLore() ? meta.lore() : new ArrayList<>();
            if (lore == null) lore = new ArrayList<>();
            
            lore.add(Component.empty());
            lore.add(Component.text("--- 主な入手場所 ---", NamedTextColor.GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
            
            if (categoriedSources.isEmpty()) {
                lore.add(Component.text("現在、入手経路は設定されていません", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            } else {
                // カテゴリ順序をある程度固定（見やすさのため）
                List<String> sortedCategories = new ArrayList<>(categoriedSources.keySet());
                sortedCategories.sort(Comparator.naturalOrder());

                for (String category : sortedCategories) {
                    lore.add(Component.empty());
                    lore.add(Component.text("[ " + category + " ]", NamedTextColor.AQUA, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
                    for (String source : categoriedSources.get(category)) {
                        lore.add(Component.text(" ・ " + source, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
                    }
                }
            }
            
            meta.lore(lore);
            display.setItemMeta(meta);
            newCache.add(display);
        }

        cachedDisplayItems.clear();
        cachedDisplayItems.addAll(newCache);
        plugin.getLogger().info("Material guide display cache updated (" + cachedDisplayItems.size() + " items).");
    }

    public List<ItemStack> getMaterialItems() {
        return cachedDisplayItems;
    }

    public Map<String, Set<String>> getCategorizedSources(String itemId) {
        return itemSources.getOrDefault(itemId, Collections.emptyMap());
    }
}
