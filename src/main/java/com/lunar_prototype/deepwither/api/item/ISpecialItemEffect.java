package com.lunar_prototype.deepwither.api.item;

import com.lunar_prototype.deepwither.core.damage.DamageContext;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * 特定のアイテムID(custom_id)に対する特殊効果を定義するインターフェース。
 */
public interface ISpecialItemEffect {
    /**
     * 装備した瞬間に呼ばれる（ステータスの一時的な書き換えやパーティクルなど）。
     */
    default void onEquip(Player player, ItemStack item) {}
    
    /**
     * 装備を外した瞬間に呼ばれる（ステータスの戻しやエフェクト解除など）。
     */
    default void onUnequip(Player player, ItemStack item) {}
    
    /**
     * 装備中、定期的に呼ばれる（リジェネ、周囲へのダメージなど）。
     */
    default void onTick(Player player, ItemStack item) {}

    /**
     * 攻撃時にパッシブとして発動する効果（追加ダメージ、デバフ付与など）。
     */
    default void onAttack(DamageContext context, ItemStack item) {}
    
    /**
     * ダメージを受けた時に発動する効果（反射、シールド展開など）。
     */
    default void onDefend(DamageContext context, ItemStack item) {}

    /**
     * アイテムのLoreを生成・更新する際に呼ばれる介入ポイント。
     */
    default void modifyLore(List<Component> lore, ItemStack item) {}
}
