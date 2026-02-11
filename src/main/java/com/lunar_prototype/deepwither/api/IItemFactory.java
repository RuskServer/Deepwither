package com.lunar_prototype.deepwither.api;

import com.lunar_prototype.deepwither.FabricationGrade;
import com.lunar_prototype.deepwither.StatMap;
import com.lunar_prototype.deepwither.StatType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * アイテムの生成、加工、ステータス管理を行うインターフェース。
 */
public interface IItemFactory {

    /**
     * IDからカスタムアイテムを取得します。
     * @param id アイテムID
     * @return ItemStack、見つからない場合はnull
     */
    @Nullable
    ItemStack getItem(String id);

    /**
     * IDと等級を指定してカスタムアイテムを取得します。
     * @param id アイテムID
     * @param grade 製造等級
     * @return ItemStack、見つからない場合はnull
     */
    @Nullable
    ItemStack getItem(String id, FabricationGrade grade);

    /**
     * IDと数量を指定してカスタムアイテムを取得します。
     * @param id アイテムID
     * @param amount 数量
     * @return ItemStack、見つからない場合はnull
     */
    @Nullable
    ItemStack getItem(String id, int amount);

    /**
     * 指定したプレイヤーにカスタムアイテムを付与します。
     * @param player 対象プレイヤー
     * @param id アイテムID
     */
    void giveItem(Player player, String id);

    /**
     * アイテムの製造グレードを更新します。
     * @param item 対象アイテム
     * @param newGrade 新しいグレード
     * @return 更新されたItemStack
     */
    @Nullable
    ItemStack updateGrade(ItemStack item, FabricationGrade newGrade);

    /**
     * アイテムの製造グレードを1段階上昇させます。
     * @param item 対象アイテム
     * @return 更新されたItemStack
     */
    @Nullable
    ItemStack upgradeGrade(ItemStack item);

    /**
     * アイテムのランダムモディファイアーを再抽選します。
     * @param item 対象アイテム
     * @return 更新されたItemStack
     */
    @Nullable
    ItemStack rerollModifiers(ItemStack item);

    /**
     * アイテムの特定のベースステータスを更新します。
     * @param item 対象アイテム
     * @param type ステータスタイプ
     * @param value 加算値
     * @param isPercent パーセント値かどうか
     * @return 更新されたItemStack
     */
    @Nullable
    ItemStack updateStat(ItemStack item, StatType type, double value, boolean isPercent);

    /**
     * アイテムからステータス情報を読み取ります。
     * @param item 対象アイテム
     * @return StatMap
     */
    @NotNull
    StatMap getStats(ItemStack item);

    /**
     * 指定したレアリティのアイテムIDリストを取得します。
     * @param rarity レアリティ名
     * @return IDリスト
     */
    List<String> getItemsByRarity(String rarity);

    /**
     * 特定のプレイヤーに対するアイテム操作コンテキストを取得します。
     */
    PlayerItem of(Player player);

    /**
     * プレイヤー専用のアイテム操作インターフェース。
     */
    interface PlayerItem {
        /**
         * アイテムを付与します。
         * @param id アイテムID
         */
        void give(String id);

        /**
         * アイテムを指定された等級で付与します。
         * @param id アイテムID
         * @param grade 等級
         */
        void give(String id, FabricationGrade grade);
    }
}
