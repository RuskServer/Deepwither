package com.lunar_prototype.deepwither;

import io.lumine.mythic.api.adapters.AbstractItemStack;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.drops.DropMetadata;
import io.lumine.mythic.api.drops.IItemDrop;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.adapters.BukkitItemStack;
import io.lumine.mythic.bukkit.adapters.item.ItemComponentBukkitItemStack;
import io.lumine.mythic.bukkit.events.MythicDropLoadEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.io.File;

public class CustomDropListener implements Listener {

    private final Deepwither plugin;

    public CustomDropListener(Deepwither plugin) {
        this.plugin = plugin;
    }

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

                    File itemFolder = new File(plugin.getDataFolder(), "items");
                    // ItemLoaderを使ってアイテムを動的に生成
                    ItemStack customItem = ItemLoader.loadSingleItem(itemId, new ItemFactory(Deepwither.getInstance()), itemFolder);

                    if (customItem != null) {
                        // MythicMobsが認識できる形式で返す
                        return new ItemComponentBukkitItemStack(customItem);
                    }

                    return null;
                }
            });
            plugin.getLogger().info("Registered custom artifact drop!");
        }

        if (event.getDropName().equalsIgnoreCase("DYNAMIC_LOOT_DROP")) {
            event.register(new IItemDrop() {
                @Override
                public AbstractItemStack getDrop(DropMetadata data, double amount) {
                    // 1. 倒したプレイヤーを取得
                    if (data.getTrigger().asPlayer() == null) return null;
                    Player killer = (Player) BukkitAdapter.adapt(data.getTrigger().asPlayer());

                    // 2. ルートレベルを取得してアイテムを抽選
                    int level = Deepwither.getInstance().getLootLevelManager().getLootLevel(killer);
                    String itemId = Deepwither.getInstance().getLootDropManager().rollDrop(level);

                    if (itemId != null) {
                        // 3. アイテム生成 (FabricationGradeはドロップに関係ないのでSTANDARD固定)
                        ItemStack item = Deepwither.getInstance().getItemFactory().getCustomItemStack(itemId, FabricationGrade.STANDARD);

                        return new ItemComponentBukkitItemStack(item);
                    }
                    return null;
                }
            });
        }
    }
}