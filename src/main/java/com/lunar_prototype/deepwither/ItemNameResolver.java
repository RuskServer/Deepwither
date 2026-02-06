package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

@DependsOn({ItemFactory.class})
/**
 * カスタムアイテムIDから実際のItemStackをロードし、その表示名を取得する責務を持つクラス。
 * QuestGeneratorが直接プラグインやファイル構造に依存するのを避けるためのDependency Injection。
 */
public class ItemNameResolver implements IManager {

    private final JavaPlugin plugin;
    private final File itemFolder;

    /**
     * コンストラクタでメインのJavaPluginインスタンスを受け取ります。
     * @param plugin メインプラグインのインスタンス
     */
    public ItemNameResolver(JavaPlugin plugin) {
        this.plugin = plugin;
        this.itemFolder = new File(plugin.getDataFolder(), "items");
        // フォルダが存在しない場合、作成するロジックは本来必要ですが、ここでは省略
    }

    @Override
    public void init() {}

    @Override
    public void shutdown() {}

    /**
     * カスタムアイテムIDに基づいてItemStackをロードし、その表示名を取得します。
     * ロード失敗時にはフォールバック名を返します。
     *
     * @param itemId ロードするカスタムアイテムのID (例: "SMALL_HEALTH_POTION")
     * @return アイテムの表示名、または失敗時のフォールバック名
     */
    public String resolveItemDisplayName(String itemId) {
        try {
            // ユーザーの提示されたロジックを呼び出す部分
            // ItemLoader.loadSingleItem(id, this, itemFolder)
            ItemStack item = ItemLoader.loadSingleItem(itemId, Deepwither.getInstance().getItemFactory(), itemFolder);

            if (item == null || !item.hasItemMeta()) {
                System.err.println("Item load failed or meta is missing for ID: " + itemId);
                return "[" + itemId + "]"; // ロード失敗時のフォールバック名
            }

            ItemMeta meta = item.getItemMeta();
            if (meta.hasDisplayName()) {
                // 色コードを剥がして表示名を取得
                return ChatColor.stripColor(meta.getDisplayName());
            } else {
                // 表示名がない場合、Material名から生成 (例: POTION -> potion)
                return item.getType().name().toLowerCase().replace('_', ' ');
            }

        } catch (Exception e) {
            System.err.println("Error resolving item name for ID " + itemId + ": " + e.getMessage());
            return "[ロードエラー: " + itemId + "]";
        }
    }
}