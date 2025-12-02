package com.lunar_prototype.deepwither;

import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class PlayerAnimationListener implements Listener {

    // 許可されたアイテムの種類を静的セットとして定義します。
    // ここにアイテムを追加するだけで、すぐに対応アイテムを増やせます。
    private static final Set<Material> ALLOWED_WEAPONS = new HashSet<>(Arrays.asList(
            Material.IRON_SWORD,
            Material.IRON_AXE
    ));


    @EventHandler
    public void onPlayerArmSwing(PlayerAnimationEvent event) {
        Player player = event.getPlayer();

        // 1. 腕振りアニメーション (近接攻撃/空振り) のみをチェック
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }

        ItemStack mainHand = player.getInventory().getItemInMainHand();

        // 2. メインハンドがアイテムを持っているかをチェック
        if (mainHand == null || mainHand.getType() == Material.AIR) {
            return;
        }

        // 3. ★【修正点】アイテムが許可された武器のセットに含まれるかチェック
        // set.contains() を使用することで、容易にアイテム追加が可能になります。
        if (!ALLOWED_WEAPONS.contains(mainHand.getType())) {
            return;
        }

        // ----------------------------------------------------
        // ★ MythicMobs スキル呼び出しのトリガー
        // ----------------------------------------------------

        final String MM_SKILL_NAME = "turquoise_slash";
        // スキルを実行
        MythicBukkit.inst().getAPIHelper().castSkill(player, MM_SKILL_NAME);
    }
}