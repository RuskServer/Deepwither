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
     * @deprecated 等級システム廃止のため grade 引数は無視され、STANDARD と同等に動作します。
     * @param id アイテムID
     * @param grade 製造等級 (廃止済み・無視される)
     * @return ItemStack、見つからない場合はnull
     */
    @Nullable
    @Deprecated
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
     * @deprecated 等級システム廃止のため no-op です。元のアイテムをそのまま返します。
     */
    @Nullable
    @Deprecated
    ItemStack updateGrade(ItemStack item, FabricationGrade newGrade);

    /**
     * @deprecated 等級システム廃止のため no-op です。元のアイテムをそのまま返します。
     */
    @Nullable
    @Deprecated
    ItemStack upgradeGrade(ItemStack item);

    /**
     * アイテムのランダムモディファイアーを再抽選します。
     * @param item 対象アイテム
     * @return 更新されたItemStack
     */
    @Nullable
    ItemStack rerollModifiers(ItemStack item);

    /**
     * アーティファクトのフルセット種別を設定します。
     * @param item 対象アイテム
     * @param artifactFullsetType フルセット種別
     * @return 更新されたItemStack
     */
    @Nullable
    ItemStack setArtifactFullsetType(ItemStack item, @Nullable String artifactFullsetType);

    /**
     * アーティファクトのフルセット種別を取得します。
     * @param item 対象アイテム
     * @return フルセット種別、未設定ならnull
     */
    @Nullable
    String getArtifactFullsetType(ItemStack item);

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
         * @deprecated 等級システム廃止のため grade 引数は無視されます。
         */
        @Deprecated
        void give(String id, FabricationGrade grade);
    }
}
