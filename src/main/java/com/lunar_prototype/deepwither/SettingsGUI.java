package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
    public static final Component GUI_TITLE = Component.text("System Settings", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false);
    public static final Component RARITY_TITLE = Component.text("アイテム通知フィルター", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false);

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

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 36, GUI_TITLE);

        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" ").decoration(TextDecoration.ITALIC, false));
        for (int i = 0; i < 36; i++) inv.setItem(i, glass);

        inv.setItem(10, createToggleItem(player, PlayerSettingsManager.SettingType.SHOW_GIVEN_DAMAGE, Material.IRON_SWORD));
        inv.setItem(12, createToggleItem(player, PlayerSettingsManager.SettingType.SHOW_TAKEN_DAMAGE, Material.LEATHER_CHESTPLATE));
        inv.setItem(14, createToggleItem(player, PlayerSettingsManager.SettingType.SHOW_MITIGATION, Material.SHIELD));
        inv.setItem(16, createToggleItem(player, PlayerSettingsManager.SettingType.SHOW_SPECIAL_LOG, Material.ENCHANTED_BOOK));
        inv.setItem(18, createToggleItem(player, PlayerSettingsManager.SettingType.SHOW_PICKUP_LOG, Material.HOPPER));

        inv.setItem(20, createRarityFilterItem(player));

        ItemStack back = createItem(Material.ARROW, Component.text("戻る", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false), Component.text("メインメニューへ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        inv.setItem(35, back);

        player.openInventory(inv);
    }

    private ItemStack createToggleItem(Player player, PlayerSettingsManager.SettingType type, Material mat) {
        boolean enabled = settingsManager.isEnabled(player, type);
        Component status = enabled ? Component.text("[ON]", NamedTextColor.GREEN) : Component.text("[OFF]", NamedTextColor.RED);

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(type.getDisplayName() + " ", NamedTextColor.YELLOW).append(status).decoration(TextDecoration.ITALIC, false));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("クリックして切り替え", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("現在の状態: ", NamedTextColor.GRAY).append(status).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createRarityFilterItem(Player player) {
        String currentRarity = settingsManager.getRarityFilter(player);
        Component displayRarity = LegacyComponentSerializer.legacyAmpersand().deserialize(currentRarity);

        ItemStack item = new ItemStack(Material.DIAMOND);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("アイテム通知フィルター", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("現在: ", NamedTextColor.GRAY).append(displayRarity).append(Component.text(" 以下を非表示", NamedTextColor.GRAY)).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("クリック: 設定を開く", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createItem(Material mat, Component name, Component... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        if (lore.length > 0) {
            List<Component> nonItalicLore = new ArrayList<>();
            for (Component l : lore) {
                nonItalicLore.add(l.decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(nonItalicLore);
        }
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        Component title = e.getView().title();

        if (title.equals(GUI_TITLE)) {
            handleSettingsGUIClick(e);
        } else if (title.equals(RARITY_TITLE)) {
            handleRarityFilterMenuClick(e);
        }
    }

    private void handleSettingsGUIClick(InventoryClickEvent e) {
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        int slot = e.getSlot();

        if (slot == 35) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            player.performCommand("menu");
            return;
        }

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

    private void handleRarityFilterMenuClick(InventoryClickEvent e) {
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        int slot = e.getSlot();

        if (slot == 26) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            open(player);
            return;
        }

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

    private void openRarityFilterMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, RARITY_TITLE);

        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" ").decoration(TextDecoration.ITALIC, false));
        for (int i = 0; i < 27; i++) inv.setItem(i, glass);

        String[] rarities = {"&f&lコモン", "&a&lアンコモン", "&b&lレア", "&d&lエピック", "&6&lレジェンダリー"};
        int[] slots = {10, 12, 14, 16, 18};

        String currentRarity = settingsManager.getRarityFilter(player);

        for (int i = 0; i < rarities.length; i++) {
            String rarity = rarities[i];
            Component displayRarity = LegacyComponentSerializer.legacyAmpersand().deserialize(rarity);
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
            meta.displayName(Component.text(isSelected ? "✓ " : "  ", NamedTextColor.GREEN).append(displayRarity).append(Component.text(" 以下を非表示", NamedTextColor.GRAY)).decoration(TextDecoration.ITALIC, false));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Component.text("クリックして選択", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);

            item.setItemMeta(meta);
            inv.setItem(slots[i], item);
        }

        ItemStack back = createItem(Material.ARROW, Component.text("戻る", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false), Component.text("設定に戻る", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        inv.setItem(26, back);

        player.openInventory(inv);
    }
}
