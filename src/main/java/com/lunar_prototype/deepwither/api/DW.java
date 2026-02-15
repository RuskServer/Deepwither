package com.lunar_prototype.deepwither.api;

import com.lunar_prototype.deepwither.api.database.IDatabaseManager;
import com.lunar_prototype.deepwither.api.stat.IStatManager;
import com.lunar_prototype.deepwither.core.playerdata.PlayerDataManager;
import org.bukkit.entity.Player;

/**
 * Deepwither API への極小エントリポイント。
 * 冗長な呼び出しを排除し、流れるようなコードを記述可能にします。
 */
public final class DW {

    private static DeepwitherAPI api;

    /**
     * APIの実体をセットします（内部用）
     */
    public static void _setApi(DeepwitherAPI apiInstance) {
        api = apiInstance;
    }

    /**
     * データベース管理APIへアクセスします。
     */
    public static IDatabaseManager db() {
        return api.getDatabaseManager();
    }

    /**
     * キャッシュ管理APIへアクセスします。
     */
    public static com.lunar_prototype.deepwither.core.CacheManager cache() {
        return api.getCacheManager();
    }

    /**
     * プレイヤーデータ管理APIへアクセスします。
     */
    public static PlayerDataManager player() {
        return api.getPlayerDataManager();
    }

    /**
     * 指定されたクラスまたはインターフェースに対応するマネージャーを取得します。
     * 例: IManaManager mana = DW.get(IManaManager.class);
     */
    public static <T> T get(Class<T> clazz) {
        return api.get(clazz);
    }

    /**
     * ステータス管理APIへアクセスします。
     */
    public static IStatManager stats() {
        return api.getStatManager();
    }

    /**
     * 指定したプレイヤーのステータス操作を直接行います。
     * 例: DW.stats(player).heal(10);
     */
    public static IStatManager.PlayerStat stats(Player player) {
        return stats().of(player);
    }

    /**
     * アイテム管理APIへアクセスします。
     */
    public static IItemFactory items() {
        return api.getItemFactory();
    }

    /**
     * 指定したプレイヤーへのアイテム付与などの操作を直接行います。
     * 例: DW.items(player).give("iron_sword");
     */
    public static IItemFactory.PlayerItem items(Player player) {
        return items().of(player);
    }

    // 今後、他の機能も以下のように追加していけます
    // public static IManaManager mana() { return api.getManaManager(); }
}
