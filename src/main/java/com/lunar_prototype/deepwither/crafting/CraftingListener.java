package com.lunar_prototype.deepwither.crafting;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.FabricationGrade;
import com.lunar_prototype.deepwither.ItemFactory;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public class CraftingListener implements Listener {

    private final Deepwither plugin;

    public CraftingListener(Deepwither plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        String title = e.getView().getTitle();
        if (!title.startsWith(CraftingGUI.TITLE_PREFIX)) {
            return;
        }

        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        // --- 詳細画面での「製作開始」処理 ---
        if (title.startsWith(RecipeDetailGUI.DETAIL_PREFIX)) {
            if (clicked.getType() == Material.ANVIL) {
                String recipeId = clicked.getItemMeta().getPersistentDataContainer().get(CraftingGUI.RECIPE_KEY, PersistentDataType.STRING);
                if (plugin.getCraftingManager().startCrafting(player, recipeId)) {
                    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1f);
                    player.closeInventory(); // 製作開始したら閉じる
                } else {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                }
                return;
            }
            // 戻るボタン
            if (clicked.getItemMeta().getPersistentDataContainer().has(CraftingGUI.NAV_ACTION_KEY, PersistentDataType.STRING)) {
                plugin.getCraftingGUI().openRecipeList(player);
                return;
            }
        }

        // --- ナビゲーション & タブ処理 ---
        if (clicked.getItemMeta().getPersistentDataContainer().has(CraftingGUI.NAV_ACTION_KEY, PersistentDataType.STRING)) {
            String action = clicked.getItemMeta().getPersistentDataContainer().get(CraftingGUI.NAV_ACTION_KEY, PersistentDataType.STRING);
            int page = clicked.getItemMeta().getPersistentDataContainer().getOrDefault(CraftingGUI.PAGE_KEY, PersistentDataType.INTEGER, 0);
            int gradeId = clicked.getItemMeta().getPersistentDataContainer().getOrDefault(CraftingGUI.GRADE_TAB_KEY, PersistentDataType.INTEGER, 1);
            FabricationGrade grade = FabricationGrade.fromId(gradeId);

            if (action.equals("prev")) plugin.getCraftingGUI().openRecipeList(player, grade, page - 1);
            if (action.equals("next")) plugin.getCraftingGUI().openRecipeList(player, grade, page + 1);
            if (action.equals("to_queue")) plugin.getCraftingGUI().openQueueList(player);
            if (action.equals("to_recipe")) plugin.getCraftingGUI().openRecipeList(player);
            return;
        }

        // --- タブ切り替え ---
        if (clicked.getItemMeta().getPersistentDataContainer().has(CraftingGUI.GRADE_TAB_KEY, PersistentDataType.INTEGER)
                && !clicked.getItemMeta().getPersistentDataContainer().has(CraftingGUI.NAV_ACTION_KEY, PersistentDataType.STRING)) { // navボタンと重複しないように

            int gradeId = clicked.getItemMeta().getPersistentDataContainer().get(CraftingGUI.GRADE_TAB_KEY, PersistentDataType.INTEGER);
            plugin.getCraftingGUI().openRecipeList(player, FabricationGrade.fromId(gradeId), 0);
            return;
        }

        // --- レシピ選択 ---
        if (title.contains("Craft -")) { // レシピ一覧画面
            NamespacedKey key = CraftingGUI.RECIPE_KEY;
            if (clicked.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.STRING)) {

                String recipeId = clicked.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
                CraftingRecipe recipe = plugin.getCraftingManager().getRecipe(recipeId);

                if (recipe == null) return;

                // ロック確認
                CraftingData data = plugin.getCraftingManager().getData(player);
                boolean isLocked = (recipe.getGrade() != FabricationGrade.STANDARD) && !data.hasRecipe(recipe.getId());

                if (isLocked) {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    player.sendMessage(ChatColor.RED + "この設計図はまだ習得していません。");
                    return;
                }

                // --- クリック種類による分岐 ---
                if (e.getClick().isLeftClick()) {
                    // 左クリック: 詳細画面を開く
                    new RecipeDetailGUI().openDetail(player, recipe);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);

                } else if (e.getClick().isRightClick()) {
                    // 右クリック: 即時製作開始 (元のロジック)
                    if (plugin.getCraftingManager().startCrafting(player, recipeId)) {
                        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1f);
                        // 製作開始のフィードバックメッセージなど
                        player.sendMessage(ChatColor.GREEN + "製作を開始しました: " + recipeId);
                    } else {
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    }
                }
            }
        }

        // --- キュー受取 ---
        if (title.contains("Queue")) {
            NamespacedKey key = CraftingGUI.JOB_KEY;
            if (clicked.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                String uuidStr = clicked.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
                UUID jobId = UUID.fromString(uuidStr);

                plugin.getCraftingManager().claimJob(player, jobId);
                plugin.getCraftingGUI().openQueueList(player); // 画面更新
            }
        }
    }

    // --- 設計図の使用 ---
    @EventHandler
    public void onUseBlueprint(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = e.getItem();
        if (item == null || !item.hasItemMeta()) return;

        if (item.getItemMeta().getPersistentDataContainer().has(CraftingManager.BLUEPRINT_KEY, PersistentDataType.STRING)) {
            e.setCancelled(true);
            String recipeId = item.getItemMeta().getPersistentDataContainer().get(CraftingManager.BLUEPRINT_KEY, PersistentDataType.STRING);

            plugin.getCraftingManager().unlockRecipe(e.getPlayer(), recipeId);

            item.setAmount(item.getAmount() - 1); // 消費
        }
    }

    @EventHandler
    public void onUseRecipeBook(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = e.getItem();
        if (item == null || !item.hasItemMeta()) return;

        // ItemFactoryで定義したKeyを持っているかチェック
        if (item.getItemMeta().getPersistentDataContainer().has(ItemFactory.RECIPE_BOOK_KEY, PersistentDataType.INTEGER)) {
            e.setCancelled(true); // 誤設置などを防ぐ

            Player player = e.getPlayer();
            int targetGrade = item.getItemMeta().getPersistentDataContainer().get(ItemFactory.RECIPE_BOOK_KEY, PersistentDataType.INTEGER);

            // ランダム習得処理を実行
            String learnedItemName = plugin.getCraftingManager().unlockRandomRecipe(player, targetGrade);

            if (learnedItemName != null) {
                // 成功: アイテムを1つ減らす
                item.setAmount(item.getAmount() - 1);

                // メッセージと音
                player.sendMessage(ChatColor.GOLD + "=================================");
                player.sendMessage(ChatColor.AQUA + " 新しいレシピを習得した！");
                player.sendMessage(ChatColor.WHITE + " 作成可能: " + ChatColor.YELLOW + learnedItemName);
                player.sendMessage(ChatColor.GOLD + "=================================");
                player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                player.playSound(player.getLocation(), org.bukkit.Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);
            } else {
                // 失敗 (すべて習得済み): アイテムは消費しない
                player.sendMessage(ChatColor.RED + "この等級のレシピは全て習得済みです！");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            }
        }
    }
}