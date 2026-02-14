package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

@DependsOn({ItemFactory.class})
public class ItemNameResolver implements IManager {

    private final JavaPlugin plugin;
    private final File itemFolder;

    public ItemNameResolver(JavaPlugin plugin) {
        this.plugin = plugin;
        this.itemFolder = new File(plugin.getDataFolder(), "items");
    }

    @Override
    public void init() {}

    @Override
    public void shutdown() {}

    public String resolveItemDisplayName(String itemId) {
        try {
            ItemStack item = ItemLoader.loadSingleItem(itemId, Deepwither.getInstance().getItemFactory(), itemFolder);

            if (item == null || !item.hasItemMeta()) {
                System.err.println("Item load failed or meta is missing for ID: " + itemId);
                return "[" + itemId + "]";
            }

            ItemMeta meta = item.getItemMeta();
            if (meta.hasDisplayName()) {
                return PlainTextComponentSerializer.plainText().serialize(meta.displayName());
            } else {
                return item.getType().name().toLowerCase().replace('_', ' ');
            }

        } catch (Exception e) {
            System.err.println("Error resolving item name for ID " + itemId + ": " + e.getMessage());
            return "[ロードエラー: " + itemId + "]";
        }
    }
}
