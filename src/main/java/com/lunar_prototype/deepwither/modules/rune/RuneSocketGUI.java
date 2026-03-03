package com.lunar_prototype.deepwither.modules.rune;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class RuneSocketGUI implements Listener, IManager {

    private final JavaPlugin plugin;
    private final RuneManager runeManager;
    public static final Component GUI_TITLE = Component.text("ルーン装着", NamedTextColor.DARK_AQUA);

    private static final int ITEM_SLOT = 10;
    private static final int RUNE_SLOT = 13;
    private static final int ACTION_SLOT = 16;

    public RuneSocketGUI(JavaPlugin plugin, RuneManager runeManager) {
        this.plugin = plugin;
        this.runeManager = runeManager;
    }

    @Override
    public void init() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

    public void open(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, GUI_TITLE);

        // Fill background
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.displayName(Component.empty());
        glass.setItemMeta(glassMeta);
        for (int i = 0; i < 27; i++) {
            gui.setItem(i, glass);
        }

        // Empty slots for inputs
        gui.setItem(ITEM_SLOT, null);
        gui.setItem(RUNE_SLOT, null);

        // Action button
        updateActionButton(gui);

        player.openInventory(gui);
    }

    private void updateActionButton(Inventory gui) {
        ItemStack item = gui.getItem(ITEM_SLOT);
        ItemStack rune = gui.getItem(RUNE_SLOT);

        ItemStack action = new ItemStack(Material.ANVIL);
        ItemMeta meta = action.getItemMeta();
        List<Component> lore = new ArrayList<>();

        if (item == null || item.getType().isAir()) {
            meta.displayName(Component.text("アイテムを配置してください", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        } else if (!runeManager.isSocketable(item)) {
            meta.displayName(Component.text("ソケットがないアイテムです", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        } else if (rune == null || rune.getType().isAir()) {
            meta.displayName(Component.text("ルーンを配置してください", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        } else if (!runeManager.isRune(rune)) {
            meta.displayName(Component.text("これはルーンではありません", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        } else if (runeManager.getFilledSockets(item) >= runeManager.getMaxSockets(item)) {
            meta.displayName(Component.text("ソケットがいっぱいです", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        } else {
            meta.displayName(Component.text("ルーンを装着する", NamedTextColor.GREEN, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
            action.setType(Material.SMITHING_TABLE);
        }

        meta.lore(lore);
        action.setItemMeta(meta);
        gui.setItem(ACTION_SLOT, action);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!e.getView().title().equals(GUI_TITLE)) return;
        if (!(e.getWhoClicked() instanceof Player player)) return;

        int slot = e.getRawSlot();

        // Prevent clicking glass or action button (unless it's an action)
        if (slot < 27) {
            if (slot != ITEM_SLOT && slot != RUNE_SLOT && slot != ACTION_SLOT) {
                e.setCancelled(true);
                return;
            }
            
            if (slot == ACTION_SLOT) {
                e.setCancelled(true);
                handleAction(player, e.getInventory());
                return;
            }
        }

        // Update action button on next tick
        Bukkit.getScheduler().runTask(plugin, () -> updateActionButton(e.getInventory()));
    }

    private void handleAction(Player player, Inventory gui) {
        ItemStack item = gui.getItem(ITEM_SLOT);
        ItemStack rune = gui.getItem(RUNE_SLOT);

        if (item == null || rune == null) return;
        if (!runeManager.isSocketable(item) || !runeManager.isRune(rune)) return;
        if (runeManager.getFilledSockets(item) >= runeManager.getMaxSockets(item)) return;

        // Perform attachment
        if (runeManager.attachRune(item, rune)) {
            rune.setAmount(rune.getAmount() - 1);
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
            player.sendMessage(Component.text("ルーンを装着しました！", NamedTextColor.GREEN));
            updateActionButton(gui);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!e.getView().title().equals(GUI_TITLE)) return;
        Player player = (Player) e.getPlayer();
        
        // Return items
        ItemStack item = e.getInventory().getItem(ITEM_SLOT);
        ItemStack rune = e.getInventory().getItem(RUNE_SLOT);
        
        if (item != null) player.getInventory().addItem(item).values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
        if (rune != null) player.getInventory().addItem(rune).values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
    }
}
