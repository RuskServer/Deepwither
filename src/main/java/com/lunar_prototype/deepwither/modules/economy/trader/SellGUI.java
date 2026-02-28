package com.lunar_prototype.deepwither.modules.economy.trader;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.StatManager;
import com.lunar_prototype.deepwither.StatMap;
import com.lunar_prototype.deepwither.StatType;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.Map;

@DependsOn({TraderManager.class, StatManager.class})
public class SellGUI implements Listener, IManager {

    private final JavaPlugin plugin;
    private final PriceCalculator priceCalculator;

    /**
     * Creates a SellGUI associated with the provided plugin and initializes the price calculator.
     *
     * @param plugin the main JavaPlugin instance used to register events and create inventories
     */
    public SellGUI(JavaPlugin plugin) {
        this.plugin = plugin;
        this.priceCalculator = new PriceCalculator();
    }

    /**
     * Registers this SellGUI instance with the Bukkit plugin manager so it will receive event callbacks.
     */
    @Override
    public void init() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Performs shutdown and cleanup for this SellGUI.
     *
     * <p>No cleanup actions are necessary for this implementation.</p>
     */
    @Override
    public void shutdown() {}

    public static final Component SELL_GUI_TITLE = Component.text("[売却] ", NamedTextColor.DARK_GRAY).append(Component.text("総合売却所", NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false);

    /**
     * Opens the selling GUI for the specified player.
     *
     * Creates a 27-slot inventory titled with SELL_GUI_TITLE, fills the top and bottom borders
     * with filler panes, places a back button at slot 22, and opens the inventory for the player.
     *
     * @param player  the player who will receive and view the sell GUI
     * @param manager the TraderManager providing context for subsequent GUI interactions
     */
    public static void openSellGUI(Player player, TraderManager manager) {
        Inventory gui = Bukkit.createInventory(player, 27, SELL_GUI_TITLE);

        ItemStack filler = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" ").decoration(TextDecoration.ITALIC, false));
        ItemStack backButton = createGuiItem(Material.ARROW, Component.text("<< 戻る", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));

        for (int i = 0; i < 9; i++) {
            gui.setItem(i, filler);
            gui.setItem(26 - i, filler);
        }

        gui.setItem(22, backButton);
        player.openInventory(gui);
    }

    private static ItemStack createGuiItem(Material material, Component name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (e.getClickedInventory() == null) return;

        if (!e.getView().title().equals(SELL_GUI_TITLE)) return;

        TraderManager manager = Deepwither.getInstance().getTraderManager();
        Economy econ = Deepwither.getEconomy();

        if (e.getClickedInventory().equals(e.getView().getTopInventory())) {
            if (e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.ARROW) {
                e.setCancelled(true);
                player.closeInventory();
                player.sendMessage(Component.text("取引を終了しました。", NamedTextColor.YELLOW));
                return;
            }

            if (e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.GRAY_STAINED_GLASS_PANE) {
                e.setCancelled(true);
                return;
            }
        }

        if (e.getClickedInventory().equals(e.getView().getBottomInventory()) && e.isShiftClick()) {
            ItemStack itemToSell = e.getCurrentItem();
            if (itemToSell == null || itemToSell.getType() == Material.AIR) return;
            e.setCancelled(true);
            processSell(player, itemToSell, manager, econ, e);
            return;
        }

        if (e.getClickedInventory().equals(e.getView().getTopInventory()) &&
                e.getCursor() != null && e.getCursor().getType() != Material.AIR) {
            if (e.getSlot() > 8 && e.getSlot() < 18) {
                e.setCancelled(true);
                ItemStack itemToSell = e.getCursor().clone();
                processSell(player, itemToSell, manager, econ, e);
            }
        }
    }

    /**
     * Credits the player's account for selling the provided item stack, updates the inventory or cursor,
     * and sends a confirmation or failure message to the player.
     *
     * @param player the player performing the sale
     * @param itemToSell the item stack to sell (its amount and display name are used)
     * @param manager the TraderManager providing context needed to determine sell price
     * @param econ the Economy implementation used to deposit funds and format amounts
     * @param e the inventory click event used to determine click type and update the inventory/cursor
     */
    private void processSell(Player player, ItemStack itemToSell, TraderManager manager, Economy econ, InventoryClickEvent e) {
        int amount = itemToSell.getAmount();
        int pricePerItem = priceCalculator.calculateSellPrice(itemToSell, manager);

        if (pricePerItem > 0) {
            int totalCost = pricePerItem * amount;
            econ.depositPlayer(player, totalCost);

            if (e.isShiftClick()) e.setCurrentItem(null);
            else e.getCursor().setAmount(0);

            Component itemDisplayName = itemToSell.hasItemMeta() && itemToSell.getItemMeta().hasDisplayName() ? itemToSell.getItemMeta().displayName() : Component.text(itemToSell.getType().name());
            player.sendMessage(Component.text("", NamedTextColor.GREEN)
                    .append(itemDisplayName)
                    .append(Component.text(" x" + amount + " を売却し、" + econ.format(totalCost) + " を獲得しました。", NamedTextColor.GREEN)));

        } else {
            player.sendMessage(Component.text("このアイテムは売却できません。", NamedTextColor.RED));
        }
    }
}
