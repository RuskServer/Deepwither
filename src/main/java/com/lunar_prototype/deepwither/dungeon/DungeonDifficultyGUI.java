package com.lunar_prototype.deepwither.dungeon;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.dungeon.instance.DungeonInstanceManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class DungeonDifficultyGUI implements Listener {
    private final String dungeonId;
    private final FileConfiguration config;
    private static final String CUSTOM_ID_KEY = "custom_id";

    public DungeonDifficultyGUI(String dungeonId, FileConfiguration config) {
        this.dungeonId = dungeonId;
        this.config = config;
    }

    public void open(Player player) {
        Component title = Component.text("難易度を選択: " + dungeonId, NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false);
        Inventory gui = Bukkit.createInventory(null, 27, title);

        gui.setItem(11, createIcon(Material.IRON_SWORD, Component.text("通常モード", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false), 
                Component.text("推奨レベル: " + config.getInt("difficulty.normal.mob_level"), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        gui.setItem(15, createIcon(Material.NETHERITE_SWORD, Component.text("高難易度モード", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false), 
                Component.text("推奨レベル: " + config.getInt("difficulty.hard.mob_level"), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false), 
                Component.text("要: 専用の鍵", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)));

        Bukkit.getPluginManager().registerEvents(this, Deepwither.getInstance());
        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (!title.startsWith("難易度を選択")) return;
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();

        if (clicked == null || !clicked.hasItemMeta()) return;

        String dispName = PlainTextComponentSerializer.plainText().serialize(clicked.getItemMeta().displayName());

        if (dispName.contains("通常モード")) {
            startDungeon(player, "normal");
        } else if (dispName.contains("高難易度モード")) {
            if (checkAndConsumeKey(player)) {
                startDungeon(player, "hard");
            } else {
                player.sendMessage(Component.text("このダンジョンに入るには専用의鍵が必要です！", NamedTextColor.RED));
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (title.equals("難易度を選択: " + dungeonId)) {
            HandlerList.unregisterAll(this);
        }
    }

    private boolean checkAndConsumeKey(Player player) {
        String requiredKey = config.getString("difficulty.hard.key_id");
        NamespacedKey key = new NamespacedKey(Deepwither.getInstance(), CUSTOM_ID_KEY);

        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || !item.hasItemMeta()) continue;
            String id = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);

            if (requiredKey.equals(id)) {
                item.setAmount(item.getAmount() - 1);
                return true;
            }
        }
        return false;
    }

    private void startDungeon(Player player, String difficulty) {
        player.closeInventory();
        HandlerList.unregisterAll(this);
        DungeonInstanceManager.getInstance().createDungeonInstance(player, dungeonId, difficulty);
    }

    private ItemStack createIcon(Material material, Component name, Component... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            List<Component> nonItalicLore = new ArrayList<>();
            for (Component l : loreLines) {
                nonItalicLore.add(l.decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(nonItalicLore);
            item.setItemMeta(meta);
        }
        return item;
    }
}