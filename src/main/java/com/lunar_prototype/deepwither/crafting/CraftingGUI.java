package com.lunar_prototype.deepwither.crafting;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.FabricationGrade;
import com.lunar_prototype.deepwither.ItemFactory;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

@DependsOn({CraftingManager.class, ItemFactory.class})
public class CraftingGUI implements IManager {

    public static final Component TITLE_PREFIX = Component.text("Craft - ", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false);

    private final Deepwither plugin;
    public static final NamespacedKey RECIPE_KEY = new NamespacedKey(Deepwither.getInstance(), "gui_recipe_id");
    public static final NamespacedKey JOB_KEY = new NamespacedKey(Deepwither.getInstance(), "gui_job_id");
    public static final NamespacedKey PAGE_KEY = new NamespacedKey(Deepwither.getInstance(), "gui_page");
    /** @deprecated 等級システム廃止のため使用されない。互換性のために残す。 */
    @Deprecated
    public static final NamespacedKey GRADE_TAB_KEY = new NamespacedKey(Deepwither.getInstance(), "gui_grade_tab");
    public static final NamespacedKey NAV_ACTION_KEY = new NamespacedKey(Deepwither.getInstance(), "gui_nav_action");

    public CraftingGUI(Deepwither plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {}

    @Override
    public void shutdown() {}

    // レシピ一覧を開く (Page 0)
    public void openRecipeList(Player player) {
        openRecipeList(player, 0);
    }

    /** @deprecated 等級システム廃止のため grade 引数は無視される。 */
    @Deprecated
    public void openRecipeList(Player player, FabricationGrade grade, int page) {
        openRecipeList(player, page);
    }

    public void openRecipeList(Player player, int page) {
        Component title = TITLE_PREFIX
                .append(Component.text("レシピ一覧"))
                .append(Component.text(" (P." + (page + 1) + ")"))
                .decoration(TextDecoration.ITALIC, false);

        Inventory gui = Bukkit.createInventory(null, 54, title);
        CraftingManager manager = plugin.getCraftingManager();

        // 等級システム廃止: 全レシピを一覧表示
        List<CraftingRecipe> recipes = new ArrayList<>(manager.getRecipesByGrade(FabricationGrade.STANDARD));

        // ページング計算 (1ページあたり45個: 0-44スロット)
        int slotsPerPage = 45;
        int totalPages = (int) Math.ceil((double) recipes.size() / slotsPerPage);
        if (page < 0) page = 0;
        if (page >= totalPages && totalPages > 0) page = totalPages - 1;

        int startIndex = page * slotsPerPage;
        int endIndex = Math.min(startIndex + slotsPerPage, recipes.size());

        for (int i = startIndex; i < endIndex; i++) {
            CraftingRecipe recipe = recipes.get(i);

            // アイコン生成
            ItemStack icon = Deepwither.getInstance().getItemFactory().getCustomItemStack(recipe.getResultItemId());
            if (icon == null) icon = new ItemStack(Material.BARRIER);

            ItemMeta meta = icon.getItemMeta();
            // 名前がなければID
            if (!meta.hasDisplayName()) {
                meta.displayName(Component.text(recipe.getResultItemId(), NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
            } else {
                meta.displayName(meta.displayName().decoration(TextDecoration.ITALIC, false));
            }

            List<Component> lore = meta.lore() == null ? new ArrayList<>() : meta.lore();
            List<Component> nonItalicLore = new ArrayList<>();
            for (Component l : lore) {
                nonItalicLore.add(l.decoration(TextDecoration.ITALIC, false));
            }
            lore = nonItalicLore;

            lore.add(Component.empty());
            lore.add(Component.text("【製作可能】", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("--- 必要素材 ---", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            List<Component> finalLore = lore;
            recipe.getIngredients().forEach((id, amount) -> {
                finalLore.add(Component.text("- " + id + ": ", NamedTextColor.GRAY)
                        .append(Component.text("x" + amount, NamedTextColor.WHITE))
                        .decoration(TextDecoration.ITALIC, false));
            });
            lore.add(Component.empty());
            lore.add(Component.text("時間: " + recipe.getTimeSeconds() + "秒", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));

            meta.lore(lore);
            meta.getPersistentDataContainer().set(RECIPE_KEY, PersistentDataType.STRING, recipe.getId());
            icon.setItemMeta(meta);

            // スロット配置 (0-44)
            gui.setItem(i - startIndex, icon);
        }

        // --- ナビゲーションバー (45-53) ---
        addGlassPane(gui);

        // ページ送り (50, 51)
        if (page > 0) {
            gui.setItem(50, createNavButton(Material.ARROW, Component.text("<< 前のページ", NamedTextColor.YELLOW), "prev", page));
        }
        if (page < totalPages - 1) {
            gui.setItem(51, createNavButton(Material.ARROW, Component.text("次のページ >>", NamedTextColor.YELLOW), "next", page));
        }

        // キュー画面へ (53)
        gui.setItem(53, createNavButton(Material.CHEST, Component.text("進行状況を確認", NamedTextColor.AQUA), "to_queue", 0));

        player.openInventory(gui);
    }

    // 進行状況リスト
    public void openQueueList(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, TITLE_PREFIX.append(Component.text("Queue")).decoration(TextDecoration.ITALIC, false));
        CraftingManager manager = plugin.getCraftingManager();
        CraftingData data = manager.getData(player);

        int slot = 0;
        for (CraftingJob job : data.getJobs()) {
            ItemStack icon = Deepwither.getInstance().getItemFactory().getCustomItemStack(job.getResultItemId());
            if (icon == null) icon = new ItemStack(Material.PAPER);

            ItemMeta meta = icon.getItemMeta();
            List<Component> lore = new ArrayList<>();

            Component name = meta.hasDisplayName() ? meta.displayName() : Component.text(job.getResultItemId());

            if (job.isFinished()) {
                meta.displayName(Component.text("【完成】", NamedTextColor.GREEN).append(name).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("クリックして受け取る", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            } else {
                long remaining = (job.getCompletionTimeMillis() - System.currentTimeMillis()) / 1000;
                meta.displayName(Component.text("【製作中】", NamedTextColor.YELLOW).append(name).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("残り: " + remaining + "秒", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            meta.getPersistentDataContainer().set(JOB_KEY, PersistentDataType.STRING, job.getJobId().toString());
            icon.setItemMeta(meta);
            gui.setItem(slot++, icon);
        }

        addGlassPane(gui);
        // レシピへ戻るボタン (53)
        gui.setItem(53, createNavButton(Material.CRAFTING_TABLE, Component.text("レシピ一覧へ", NamedTextColor.GREEN), "to_recipe", 0));

        player.openInventory(gui);
    }

    private ItemStack createNavButton(Material mat, Component name, String action, int currentPage) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(NAV_ACTION_KEY, PersistentDataType.STRING, action);
        meta.getPersistentDataContainer().set(PAGE_KEY, PersistentDataType.INTEGER, currentPage);
        item.setItemMeta(meta);
        return item;
    }

    private void addGlassPane(Inventory gui) {
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        glass.setItemMeta(meta);
        for (int i = 45; i < 54; i++) {
            if (gui.getItem(i) == null) gui.setItem(i, glass);
        }
    }
}