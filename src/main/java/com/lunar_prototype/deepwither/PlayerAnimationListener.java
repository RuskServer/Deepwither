package com.lunar_prototype.deepwither;

import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.List;

public class PlayerAnimationListener implements Listener {

    @EventHandler
    public void onPlayerArmSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;

        Player player = event.getPlayer();
        ItemStack weapon = player.getInventory().getItemInMainHand();

        if (weapon == null || weapon.getType() == Material.AIR) return;

        // --- 重い武器 ---
        if (isHeavyWeapon(weapon)) {
            spawnHeavySwingEffect(player);
            return; // ★通常剣エフェクトを使わせない
        }

        // --- 槍 ---
        if (isSpearWeapon(weapon)) {
            spawnSpearThrustEffect(player);
            return;
        }

        // --- 通常剣 ---
        if (isSwordWeapon(weapon)) {
            castMythicSkill(player, "turquoise_slash");
        }
    }

    /* ---------------- 判定系 ---------------- */

    private boolean hasCategory(ItemStack item, String category) {
        if (!item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasLore()) return false;

        for (String line : meta.getLore()) {
            if (line.contains("§7カテゴリ:§f" + category)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSpearWeapon(ItemStack item) {
        return hasCategory(item, "槍");
    }

    private boolean isSwordWeapon(ItemStack item) {
        return hasCategory(item, "剣") || hasCategory(item, "大剣");
    }

    private boolean isHeavyWeapon(ItemStack item) {
        return hasCategory(item, "ハルバード")
                || hasCategory(item, "斧")
                || hasCategory(item, "マチェット")
                || hasCategory(item, "メイス");
    }

    /* ---------------- 演出系 ---------------- */

    // 槍の突き
    private void spawnSpearThrustEffect(Player player) {
        Location start = player.getEyeLocation();
        Vector dir = start.getDirection().normalize();

        for (double i = 0; i <= 2.8; i += 0.2) {
            Location point = start.clone().add(dir.clone().multiply(i));
            player.getWorld().spawnParticle(
                    Particle.CRIT,
                    point,
                    1,
                    0, 0, 0,
                    0
            );
        }

        player.playSound(player.getLocation(),
                Sound.ENTITY_PLAYER_ATTACK_STRONG,
                0.6f, 0.9f);
    }

    // 重い武器の振り下ろし
    private void spawnHeavySwingEffect(Player player) {
        Location base = player.getLocation().add(0, 1.2, 0);
        Vector dir = player.getLocation().getDirection().normalize();

        for (double y = 0; y <= 1.2; y += 0.15) {
            Location loc = base.clone()
                    .add(dir.clone().multiply(1.2))
                    .subtract(0, y, 0);

            player.getWorld().spawnParticle(
                    Particle.EXPLOSION,
                    loc,
                    2,
                    0.05, 0.05, 0.05,
                    0
            );
        }

        player.getWorld().spawnParticle(
                Particle.BLOCK,
                base.clone().add(dir.multiply(1.3)),
                10,
                Material.STONE.createBlockData()
        );

        player.playSound(player.getLocation(),
                Sound.ENTITY_PLAYER_ATTACK_STRONG,
                1.0f, 0.6f);
    }

    /* ---------------- MythicMobs ---------------- */

    private void castMythicSkill(Player player, String skillName) {
        MythicBukkit.inst().getAPIHelper().castSkill(player, skillName);
    }
}
