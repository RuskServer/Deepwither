package com.lunar_prototype.deepwither.crafting;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.FabricationGrade;
import com.lunar_prototype.deepwither.ItemFactory;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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

@DependsOn({CraftingManager.class, CraftingGUI.class})
public class CraftingListener implements Listener, IManager {

    private final Deepwither plugin;

    public CraftingListener(Deepwither plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        org.bukkit.Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        Component titleComp = e.getView().title();
        String title = PlainTextComponentSerializer.plainText().serialize(titleComp);
        
        if (!title.startsWith("Craft - ") && !title.startsWith("Confirm - ")) {
            return;
        }

        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        if (title.startsWith("Confirm - ")) {
            if (clicked.getType() == org.bukkit.Material.ANVIL) {
                String recipeId = clicked.getItemMeta().getPersistentDataContainer().get(CraftingGUI.RECIPE_KEY, PersistentDataType.STRING);
                if (plugin.getCraftingManager().startCrafting(player, recipeId)) {
                    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1f);
                    player.closeInventory();
                } else {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                }
                return;
            }
            if (clicked.getItemMeta().getPersistentDataContainer().has(CraftingGUI.NAV_ACTION_KEY, PersistentDataType.STRING)) {
                plugin.getCraftingGUI().openRecipeList(player);
                return;
            }
        }

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

        if (clicked.getItemMeta().getPersistentDataContainer().has(CraftingGUI.GRADE_TAB_KEY, PersistentDataType.INTEGER)
                && !clicked.getItemMeta().getPersistentDataContainer().has(CraftingGUI.NAV_ACTION_KEY, PersistentDataType.STRING)) {

            int gradeId = clicked.getItemMeta().getPersistentDataContainer().get(CraftingGUI.GRADE_TAB_KEY, PersistentDataType.INTEGER);
            plugin.getCraftingGUI().openRecipeList(player, FabricationGrade.fromId(gradeId), 0);
            return;
        }

        if (title.contains("Craft -")) {
            NamespacedKey key = CraftingGUI.RECIPE_KEY;
            if (clicked.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.STRING)) {

                String recipeId = clicked.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
                CraftingRecipe recipe = plugin.getCraftingManager().getRecipe(recipeId);

                if (recipe == null) return;

                CraftingData data = plugin.getCraftingManager().getData(player);
                boolean isLocked = (recipe.getGrade() != FabricationGrade.STANDARD) && !data.hasRecipe(recipe.getId());

                if (isLocked) {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    player.sendMessage(Component.text("この設計図はまだ習得していません。", NamedTextColor.RED));
                    return;
                }

                if (e.getClick().isLeftClick()) {
                    new RecipeDetailGUI().openDetail(player, recipe);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);

                } else if (e.getClick().isRightClick()) {
                    if (plugin.getCraftingManager().startCrafting(player, recipeId)) {
                        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1f);
                        player.sendMessage(Component.text("製作を開始しました: " + recipeId, NamedTextColor.GREEN));
                    } else {
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    }
                }
            }
        }

        if (title.contains("Queue")) {
            NamespacedKey key = CraftingGUI.JOB_KEY;
            if (clicked.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                String uuidStr = clicked.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
                UUID jobId = UUID.fromString(uuidStr);

                plugin.getCraftingManager().claimJob(player, jobId);
                plugin.getCraftingGUI().openQueueList(player);
            }
        }
    }

    @EventHandler
    public void onUseBlueprint(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = e.getItem();
        if (item == null || !item.hasItemMeta()) return;

        if (item.getItemMeta().getPersistentDataContainer().has(CraftingManager.BLUEPRINT_KEY, PersistentDataType.STRING)) {
            e.setCancelled(true);
            String recipeId = item.getItemMeta().getPersistentDataContainer().get(CraftingManager.BLUEPRINT_KEY, PersistentDataType.STRING);
            plugin.getCraftingManager().unlockRecipe(e.getPlayer(), recipeId);
            item.setAmount(item.getAmount() - 1);
        }
    }

    @EventHandler
    public void onUseRecipeBook(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = e.getItem();
        if (item == null || !item.hasItemMeta()) return;

        if (item.getItemMeta().getPersistentDataContainer().has(ItemFactory.RECIPE_BOOK_KEY, PersistentDataType.INTEGER)) {
            e.setCancelled(true);

            Player player = e.getPlayer();
            int targetGrade = item.getItemMeta().getPersistentDataContainer().get(ItemFactory.RECIPE_BOOK_KEY, PersistentDataType.INTEGER);

            String learnedItemName = plugin.getCraftingManager().unlockRandomRecipe(player, targetGrade);

            if (learnedItemName != null) {
                item.setAmount(item.getAmount() - 1);

                player.sendMessage(Component.text("=================================", NamedTextColor.GOLD));
                player.sendMessage(Component.text(" 新しいレシピを習得した！", NamedTextColor.AQUA));
                player.sendMessage(Component.text(" 作成可能: ", NamedTextColor.WHITE).append(Component.text(learnedItemName, NamedTextColor.YELLOW)));
                player.sendMessage(Component.text("=================================", NamedTextColor.GOLD));
                player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                player.playSound(player.getLocation(), org.bukkit.Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);
            } else {
                player.sendMessage(Component.text("この等級のレシピは全て習得済みです！", NamedTextColor.RED));
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            }
        }
    }
}
