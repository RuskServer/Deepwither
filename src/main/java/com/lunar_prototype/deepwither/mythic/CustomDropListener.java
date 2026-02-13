package com.lunar_prototype.deepwither.mythic;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.ItemFactory;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import io.lumine.mythic.api.adapters.AbstractItemStack;
import io.lumine.mythic.api.drops.DropMetadata;
import io.lumine.mythic.api.drops.IItemDrop;
import io.lumine.mythic.bukkit.adapters.item.ItemComponentBukkitItemStack;
import io.lumine.mythic.bukkit.events.MythicDropLoadEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

@DependsOn({ItemFactory.class})
public class CustomDropListener implements Listener, IManager {

    private final JavaPlugin plugin;

    public CustomDropListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        org.bukkit.Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

    @EventHandler
    public void onMythicDropLoad(MythicDropLoadEvent event) {
        // MythicMobsのYAMLファイルで定義されたドロップ名が "ARTIFACT_DROP" の場合
        if (event.getDropName().equalsIgnoreCase("ARTIFACT_DROP")) {
            // あなたのプラグインのカスタムアイテムを生成するロジックを登録
            event.register(new IItemDrop() {
                @Override
                public AbstractItemStack getDrop(DropMetadata data, double amount) {
                    // ここであなたのItemLoaderを使ってアイテムを生成
                    // config.getString("item-id", null, event.getArgument()) でYAMLからアイテムIDを取得
                    String itemId = event.getConfig().getString("item-id");

                    if (itemId == null) {
                        return null; // IDがなければ何もドロップしない
                    }
                    // ItemLoaderを使ってアイテムを動的に生成
                    ItemStack customItem = Deepwither.getInstance().getItemFactoryAPI().getItem(itemId);

                    if (customItem != null) {
                        // MythicMobsが認識できる形式で返す
                        return new ItemComponentBukkitItemStack(customItem);
                    }

                    return null;
                }
            });
        }
    }
}