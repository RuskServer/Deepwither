package com.lunar_prototype.deepwither.dungeon;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.dungeon.instance.DungeonInstanceManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class DungeonDifficultyGUI implements Listener {
    private final String dungeonId;
    private final FileConfiguration config;
    private static final String CUSTOM_ID_KEY = "custom_item_id"; // 仮定

    public DungeonDifficultyGUI(String dungeonId, FileConfiguration config) {
        this.dungeonId = dungeonId;
        this.config = config;
    }

    public void open(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, "難易度を選択: " + dungeonId);

        // 通常モードのアイコン設定
        gui.setItem(11, createIcon(Material.IRON_SWORD, "§a通常モード", "§7推奨レベル: " + config.getInt("difficulty.normal.mob_level")));
        // 高難易度モードのアイコン設定
        gui.setItem(15, createIcon(Material.NETHERITE_SWORD, "§c高難易度モード", "§7推奨レベル: " + config.getInt("difficulty.hard.mob_level"), "§e要: 専用の鍵"));

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().startsWith("難易度を選択")) return;
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();

        if (clicked == null) return;

        if (clicked.getItemMeta().getDisplayName().contains("通常モード")) {
            startDungeon(player, "normal");
        } else if (clicked.getItemMeta().getDisplayName().contains("高難易度モード")) {
            if (checkAndConsumeKey(player)) {
                startDungeon(player, "hard");
            } else {
                player.sendMessage("§cこのダンジョンに入るには専用の鍵が必要です！");
            }
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
        DungeonInstanceManager.getInstance().createDungeonInstance(player, dungeonId, difficulty);
    }
}
