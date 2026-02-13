package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@DependsOn({PlayerSettingsManager.class})
public class SettingsGUI implements Listener, IManager {

    private final Deepwither plugin;
    private final PlayerSettingsManager settingsManager;
    private static final String GUI_TITLE = "§8System Settings";

    /**
     * Creates a SettingsGUI, stores the plugin and settings manager references.
     *
     * @param plugin the Deepwither plugin instance used for registration and context
     * @param settingsManager the PlayerSettingsManager responsible for per-player settings
     */
    public SettingsGUI(Deepwither plugin, PlayerSettingsManager settingsManager) {
        this.plugin = plugin;
        this.settingsManager = settingsManager;
    }

    @Override
    public void init() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

    /**
     * Opens the system settings GUI for the given player, presenting toggle controls for various player settings, an item rarity filter, and a back button.
     *
     * The GUI is a 36-slot inventory titled "§8System Settings" and includes toggle items for given/taken damage, mitigation, special logs, pickup logs, a rarity filter entry, and a button to return to the main menu.
     *
     * @param player the player who will be shown the settings GUI
     */
    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 36, GUI_TITLE);

        // 背景
        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 36; i++) inv.setItem(i, glass);

        // 各設定ボタンの配置
        inv.setItem(10, createToggleItem(player, PlayerSettingsManager.SettingType.SHOW_GIVEN_DAMAGE, Material.IRON_SWORD));
        inv.setItem(12, createToggleItem(player, PlayerSettingsManager.SettingType.SHOW_TAKEN_DAMAGE, Material.LEATHER_CHESTPLATE));
        inv.setItem(14, createToggleItem(player, PlayerSettingsManager.SettingType.SHOW_MITIGATION, Material.SHIELD));
        inv.setItem(16, createToggleItem(player, PlayerSettingsManager.SettingType.SHOW_SPECIAL_LOG, Material.ENCHANTED_BOOK));
        inv.setItem(18, createToggleItem(player, PlayerSettingsManager.SettingType.SHOW_PICKUP_LOG, Material.HOPPER));

        // レアリティフィルター設定
        inv.setItem(20, createRarityFilterItem(player));

        // 戻るボタン
        ItemStack back = createItem(Material.ARROW, "§c戻る", "§7メインメニューへ");
        inv.setItem(35, back);

        player.openInventory(inv);
    }

    /**
     * Creates an inventory item that represents a toggleable player setting with its current state.
     *
     * The item's display name includes the setting's display name and a status tag ("§a[ON]" or "§c[OFF]").
     *
     * @param player the player whose setting state is shown
     * @param type the setting type represented by the item
     * @param mat the material to use for the item icon
     * @return an ItemStack configured to show the setting name, current state, and a hint that it can be clicked to toggle
     */
    private ItemStack createToggleItem(Player player, PlayerSettingsManager.SettingType type, Material mat) {
        boolean enabled = settingsManager.isEnabled(player, type);
        String status = enabled ? "§a[ON]" : "§c[OFF]";

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§e" + type.getDisplayName() + " " + status);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7クリックして切り替え");
        lore.add("§7現在の状態: " + status);
        meta.setLore(lore);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Create the inventory item that represents the player's item-notification rarity filter.
     *
     * The returned ItemStack is a diamond with a display name "アイテム通知フィルター", lore that shows
     * the player's current rarity filter (formatted for in-game color codes) and a hint to right-click to open
     * the rarity settings, and item flags hiding attributes and enchantments.
     *
     * @param player the player whose current rarity filter is shown on the item
     * @return an ItemStack configured to display and open the player's rarity filter settings
     */
    private ItemStack createRarityFilterItem(Player player) {
        String currentRarity = settingsManager.getRarityFilter(player);
        String displayRarity = currentRarity.replace("&", "§");

        ItemStack item = new ItemStack(Material.DIAMOND);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§eアイテム通知フィルター");
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7現在: " + displayRarity + " §7以下を非表示");
        lore.add("");
        lore.add("§7クリック: 設定を開く");
        meta.setLore(lore);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Creates an ItemStack with the given material, display name, and optional lore lines.
     *
     * @param mat  the material for the item
     * @param name the display name to show on the item
     * @param lore optional lore lines to attach to the item (each element is one line)
     * @return the ItemStack configured with the specified display name and lore
     */
    private ItemStack createItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore.length > 0) meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Routes inventory click events from the settings GUIs to their respective handlers based on the open inventory's title.
     *
     * @param e the InventoryClickEvent to inspect and dispatch
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        String title = e.getView().getTitle();

        if (title.equals(GUI_TITLE)) {
            handleSettingsGUIClick(e);
        } else if (title.equals("§8アイテム通知フィルター")) {
            handleRarityFilterMenuClick(e);
        }
    }

    /**
     * Handles clicks inside the main settings GUI.
     *
     * Cancels the click and, depending on the clicked slot, performs one of:
     * - Slot 35: plays a button sound and runs the "menu" command.
     * - Slot 20: opens the rarity filter submenu.
     * - Slots 10, 12, 14, 16, 18: toggles the corresponding player setting, plays a button sound, and reopens the settings GUI.
     *
     * @param e the InventoryClickEvent originating from the settings GUI
     */
    private void handleSettingsGUIClick(InventoryClickEvent e) {
        e.setCancelled(true);

        if (!(e.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        int slot = e.getSlot();

        // 戻るボタン
        if (slot == 35) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            player.performCommand("menu");
            return;
        }

        // レアリティフィルター設定
        if (slot == 20) {
            openRarityFilterMenu(player);
            return;
        }

        PlayerSettingsManager.SettingType type = null;
        if (slot == 10) type = PlayerSettingsManager.SettingType.SHOW_GIVEN_DAMAGE;
        else if (slot == 12) type = PlayerSettingsManager.SettingType.SHOW_TAKEN_DAMAGE;
        else if (slot == 14) type = PlayerSettingsManager.SettingType.SHOW_MITIGATION;
        else if (slot == 16) type = PlayerSettingsManager.SettingType.SHOW_SPECIAL_LOG;
        else if (slot == 18) type = PlayerSettingsManager.SettingType.SHOW_PICKUP_LOG;

        if (type != null) {
            settingsManager.toggle(player, type);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            open(player);
        }
    }

    /**
     * Handle clicks inside the "§8アイテム通知フィルター" rarity filter GUI.
     *
     * Cancels the click and interprets slot 26 as the Back button (plays a button sound and reopens the main settings GUI). Slots 10, 12, 14, 16, and 18 select a rarity: the selected rarity is saved to the player's settings, a button sound is played, and the rarity filter menu is refreshed to reflect the change.
     *
     * @param e the InventoryClickEvent from the rarity filter menu
     */
    private void handleRarityFilterMenuClick(InventoryClickEvent e) {
        e.setCancelled(true);

        if (!(e.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        int slot = e.getSlot();

        // 戻るボタン
        if (slot == 26) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            open(player);
            return;
        }

        // レアリティ選択
        String[] rarities = {"&f&lコモン", "&a&lアンコモン", "&b&lレア", "&d&lエピック", "&6&lレジェンダリー"};
        int[] slots = {10, 12, 14, 16, 18};

        for (int i = 0; i < slots.length; i++) {
            if (slot == slots[i]) {
                settingsManager.setRarityFilter(player, rarities[i]);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                openRarityFilterMenu(player);
                return;
            }
        }
    }

    /**
     * Opens the "§8アイテム通知フィルター" menu for the given player, presenting selectable rarity options and a back button.
     *
     * The menu displays five rarity choices with a visual indicator for the currently selected filter and an option to return to the settings menu.
     *
     * @param player the player who will be shown the rarity filter menu
     */
    private void openRarityFilterMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, "§8アイテム通知フィルター");

        // 背景
        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, glass);

        // レアリティ選択ボタン
        String[] rarities = {"&f&lコモン", "&a&lアンコモン", "&b&lレア", "&d&lエピック", "&6&lレジェンダリー"};
        int[] slots = {10, 12, 14, 16, 18};

        String currentRarity = settingsManager.getRarityFilter(player);

        for (int i = 0; i < rarities.length; i++) {
            String rarity = rarities[i];
            String displayRarity = rarity.replace("&", "§");
            boolean isSelected = rarity.equals(currentRarity);

            Material mat = switch (i) {
                case 0 -> Material.QUARTZ;
                case 1 -> Material.EMERALD;
                case 2 -> Material.LAPIS_LAZULI;
                case 3 -> Material.AMETHYST_SHARD;
                case 4 -> Material.GOLD_BLOCK;
                default -> Material.STONE;
            };

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName((isSelected ? "§a✓ " : "  ") + displayRarity + " §7以下を非表示");
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("§7クリックして選択");
            meta.setLore(lore);

            item.setItemMeta(meta);
            inv.setItem(slots[i], item);
        }

        // 戻るボタン
        ItemStack back = createItem(Material.ARROW, "§c戻る", "§7設定に戻る");
        inv.setItem(26, back);

        player.openInventory(inv);
    }
}