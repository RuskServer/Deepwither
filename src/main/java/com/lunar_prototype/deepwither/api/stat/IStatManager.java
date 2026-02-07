package com.lunar_prototype.deepwither.api.stat;

import com.lunar_prototype.deepwither.StatMap;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * プレイヤーのステータス（HP、攻撃力、各種RPGステータス）を管理するAPIインターフェース。
 */
public interface IStatManager {

    /**
     * 特定のプレイヤーに対する操作コンテキストを取得します。
     */
    PlayerStat of(Player player);

    /**
     * プレイヤー専用のステータス操作インターフェース。
     */
    interface PlayerStat {
        double getHP();
        void setHP(double health);
        double getMaxHP();
        void heal(double amount);
        StatMap getAll();
        void update();
    }

    /**
     * プレイヤーのステータスを最新の装備やバフの状態に基づいて更新します。
     * @param player 対象プレイヤー
     */
    void updatePlayerStats(Player player);

    /**
     * プレイヤーの現在の最大HP（カスタムHP）を取得します。
     * @param player 対象プレイヤー
     * @return 最大HP
     */
    double getActualMaxHealth(Player player);

    /**
     * プレイヤーの現在のHP（カスタムHP）を取得します。
     * @param player 対象プレイヤー
     * @return 現在のHP
     */
    double getActualCurrentHealth(Player player);

    /**
     * プレイヤーの現在のHP（カスタムHP）を設定し、バニラのHPバーと同期させます。
     * @param player 対象プレイヤー
     * @param newHealth 設定するHP量
     */
    void setActualCurrentHealth(Player player, double newHealth);

    /**
     * プレイヤーのHP（カスタムHP）を回復させます。
     * @param player 対象プレイヤー
     * @param amount 回復量
     */
    void heal(Player player, double amount);

    /**
     * プレイヤーの現在の全ステータス（装備、ステ振り、バフ込）を取得します。
     * @param player 対象プレイヤー
     * @return 合計ステータス
     */
    StatMap getTotalStats(Player player);

    /**
     * 一時的なバフを適用します。
     * @param playerUUID 対象プレイヤーのUUID
     * @param buff 適用するステータスマップ
     */
    void applyTemporaryBuff(UUID playerUUID, StatMap buff);

    /**
     * 一時的なバフを削除します。
     * @param playerUUID 対象プレイヤーのUUID
     */
    void removeTemporaryBuff(UUID playerUUID);
}
