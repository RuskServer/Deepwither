package com.lunar_prototype.deepwither.crafting;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.FabricationGrade;
import com.lunar_prototype.deepwither.ItemFactory;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

@DependsOn({CraftingManager.class, ItemFactory.class})
public class CraftingGUI implements IManager {

    public static final Component TITLE_PREFIX = Component.text("Craft - ", NamedTextColor.DARK_GRAY);

    private final Deepwither plugin;
    public static final NamespacedKey RECIPE_KEY = new NamespacedKey(Deepwither.getInstance(), "gui_recipe_id");
    public static final NamespacedKey JOB_KEY = new NamespacedKey(Deepwither.getInstance(), "gui_job_id");
    public static final NamespacedKey PAGE_KEY = new NamespacedKey(Deepwither.getInstance(), "gui_page");
    public static final NamespacedKey GRADE_TAB_KEY = new NamespacedKey(Deepwither.getInstance(), "gui_grade_tab");
    public static final NamespacedKey NAV_ACTION_KEY = new NamespacedKey(Deepwither.getInstance(), "gui_nav_action");

    public CraftingGUI(Deepwither plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {}

    @Override
    public void shutdown() {}

    // レシピ一覧を開く (デフォルト: Standard, Page 0)
    public void openRecipeList(Player player) {
        openRecipeList(player, FabricationGrade.STANDARD, 0);
    }

    public void openRecipeList(Player player, FabricationGrade grade, int page) {
        Component title = TITLE_PREFIX
                .append(LegacyComponentSerializer.legacySection().deserialize(grade.getDisplayName()))
                .append(Component.text(" (P." + (page + 1) + ")"));
        
        Inventory gui = Bukkit.createInventory(null, 54, title);
        CraftingManager manager = plugin.getCraftingManager();
        CraftingData data = manager.getData(player);

        List<CraftingRecipe> recipes = manager.getRecipesByGrade(grade);

        // ページング計算 (1ページあたり45個: 0-44スロット)
        int slotsPerPage = 45;
        int totalPages = (int) Math.ceil((double) recipes.size() / slotsPerPage);
        if (page < 0) page = 0;
        if (page >= totalPages && totalPages > 0) page = totalPages - 1;

        int startIndex = page * slotsPerPage;
        int endIndex = Math.min(startIndex + slotsPerPage, recipes.size());

        for (int i = startIndex; i < endIndex; i++) {
            CraftingRecipe recipe = recipes.get(i);
            boolean isLocked = (grade != FabricationGrade.STANDARD) && !data.hasRecipe(recipe.getId());

            // アイコン生成
            ItemStack icon;
            if (isLocked) {
                // ロック時はバリア or グレイスケール的な表現
                icon = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            } else {
                // 通常表示 (指定Gradeで生成してプレビュー)
                icon = Deepwither.getInstance().getItemFactory().getCustomItemStack(recipe.getResultItemId(), grade);
                if (icon == null) icon = new ItemStack(Material.BARRIER);
            }

            ItemMeta meta = icon.getItemMeta();
            // 名前がなければID
            if (!meta.hasDisplayName()) {
                meta.displayName(Component.text(recipe.getResultItemId(), NamedTextColor.WHITE));
            }

            List<Component> lore = meta.lore() == null ? new ArrayList<>() : meta.lore();
            lore.add(Component.empty());

            if (isLocked) {
                String plainName = PlainTextComponentSerializer.plainText().serialize(meta.displayName());
                meta.displayName(Component.text("🔒 " + plainName, NamedTextColor.RED));
                lore.add(Component.text("【未習得】", NamedTextColor.RED));
                lore.add(Component.text("必要: 設計図", NamedTextColor.GRAY));
            } else {
                lore.add(Component.text("【製作可能】", NamedTextColor.GREEN));
            }

            lore.add(Component.text("--- 必要素材 ---", NamedTextColor.GOLD));
            recipe.getIngredients().forEach((id, amount) -> {
                lore.add(Component.text("- " + id + ": ", NamedTextColor.GRAY)
                        .append(Component.text("x" + amount, NamedTextColor.WHITE)));
            });
            lore.add(Component.empty());
            lore.add(Component.text("時間: " + recipe.getTimeSeconds() + "秒", NamedTextColor.YELLOW));

            meta.lore(lore);
            meta.getPersistentDataContainer().set(RECIPE_KEY, PersistentDataType.STRING, recipe.getId());
            icon.setItemMeta(meta);

            // スロット配置 (0-44)
            gui.setItem(i - startIndex, icon);
        }

        // --- ナビゲーションバー (45-53) ---
        addGlassPane(gui);

        // Gradeタブ切り替え (45-49)
        int tabSlot = 45;
        for (FabricationGrade g : FabricationGrade.values()) {
            ItemStack tabIcon = new ItemStack(getGradeIconMaterial(g));
            ItemMeta tMeta = tabIcon.getItemMeta();
            boolean isSelected = (g == grade);

            Component tabName = isSelected ? 
                    Component.text("▶ ", NamedTextColor.GREEN).append(LegacyComponentSerializer.legacySection().deserialize(g.getDisplayName())) :
                    Component.text("", NamedTextColor.GRAY).append(LegacyComponentSerializer.legacySection().deserialize(g.getDisplayName()));
            
            tMeta.displayName(tabName);
            if (isSelected) {
                tMeta.addEnchant(Enchantment.DENSITY, 1, true);
                tMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            tMeta.getPersistentDataContainer().set(GRADE_TAB_KEY, PersistentDataType.INTEGER, g.getId());
            tabIcon.setItemMeta(tMeta);
            gui.setItem(tabSlot++, tabIcon);
        }

        // ページ送り (50, 52)
        if (page > 0) {
            gui.setItem(50, createNavButton(Material.ARROW, Component.text("<< 前のページ", NamedTextColor.YELLOW), "prev", page, grade.getId()));
        }
        if (page < totalPages - 1) {
            gui.setItem(51, createNavButton(Material.ARROW, Component.text("次のページ >>", NamedTextColor.YELLOW), "next", page, grade.getId()));
        }

        // キュー画面へ (53)
        gui.setItem(53, createNavButton(Material.CHEST, Component.text("進行状況を確認", NamedTextColor.AQUA), "to_queue", 0, 0));

        player.openInventory(gui);
    }

    // 進行状況リスト (変更は少ないがGrade表示を考慮)
    public void openQueueList(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, TITLE_PREFIX.append(Component.text("Queue")));
        CraftingManager manager = plugin.getCraftingManager();
        CraftingData data = manager.getData(player);

        int slot = 0;
        for (CraftingJob job : data.getJobs()) {
            // JobIDからレシピを参照してGradeを取得
            CraftingRecipe recipe = manager.getRecipe(job.getRecipeId());
            FabricationGrade grade = (recipe != null) ? recipe.getGrade() : FabricationGrade.STANDARD;

            ItemStack icon = Deepwither.getInstance().getItemFactory().getCustomItemStack(job.getResultItemId(), grade);
            if (icon == null) icon = new ItemStack(Material.PAPER);

            ItemMeta meta = icon.getItemMeta();
            List<Component> lore = new ArrayList<>();

            Component name = meta.hasDisplayName() ? meta.displayName() : Component.text(job.getResultItemId());

            if (job.isFinished()) {
                meta.displayName(Component.text("【完成】", NamedTextColor.GREEN).append(name));
                lore.add(Component.text("クリックして受け取る", NamedTextColor.YELLOW));
            } else {
                long remaining = (job.getCompletionTimeMillis() - System.currentTimeMillis()) / 1000;
                meta.displayName(Component.text("【製作中】", NamedTextColor.YELLOW).append(name));
                lore.add(Component.text("残り: " + remaining + "秒", NamedTextColor.GRAY));
            }
            meta.lore(lore);
            meta.getPersistentDataContainer().set(JOB_KEY, PersistentDataType.STRING, job.getJobId().toString());
            icon.setItemMeta(meta);
            gui.setItem(slot++, icon);
        }

        addGlassPane(gui);
        // レシピへ戻るボタン (53)
        gui.setItem(53, createNavButton(Material.CRAFTING_TABLE, Component.text("レシピ一覧へ", NamedTextColor.GREEN), "to_recipe", 0, 1)); // Default to Standard

        player.openInventory(gui);
    }

    private ItemStack createNavButton(Material mat, Component name, String action, int currentPage, int gradeId) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        meta.getPersistentDataContainer().set(NAV_ACTION_KEY, PersistentDataType.STRING, action);
        meta.getPersistentDataContainer().set(PAGE_KEY, PersistentDataType.INTEGER, currentPage);
        meta.getPersistentDataContainer().set(GRADE_TAB_KEY, PersistentDataType.INTEGER, gradeId);
        item.setItemMeta(meta);
        return item;
    }

    private void addGlassPane(Inventory gui) {
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        meta.displayName(Component.text(" "));
        glass.setItemMeta(meta);
        for (int i = 45; i < 54; i++) {
            if (gui.getItem(i) == null) gui.setItem(i, glass);
        }
    }

    private Material getGradeIconMaterial(FabricationGrade g) {
        return switch (g) {
            case STANDARD -> Material.IRON_INGOT;
            case INDUSTRIAL -> Material.GOLD_INGOT;
            case MILITARY -> Material.DIAMOND;
            case ADVANCED -> Material.NETHERITE_INGOT;
            case AETHERBOUND -> Material.NETHER_STAR;
        };
    }
}