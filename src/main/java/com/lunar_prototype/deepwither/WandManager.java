package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@DependsOn({ManaManager.class, DamageManager.class, StatManager.class})
public class WandManager implements Listener, IManager {

    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final JavaPlugin plugin;

    public WandManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        // 左クリック（空気中 または ブロック）のみ反応
        if (e.getAction() != Action.LEFT_CLICK_AIR && e.getAction() != Action.LEFT_CLICK_BLOCK) return;

        Player p = e.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();

        // 杖を持っているかチェック
        if (item == null || !item.hasItemMeta() ||
                !item.getItemMeta().getPersistentDataContainer().has(ItemLoader.IS_WAND, PersistentDataType.BOOLEAN)) {
            return;
        }

        // イベントキャンセル（ブロック破壊防止など）
        e.setCancelled(true);

        // --- クールダウン処理 ---
        // 攻撃速度を取得 (StatUtilsがある前提。なければ固定値やPDCから取得)
        // 例: 攻撃速度 1.5 = 1秒間に1.5回 = クールダウン 0.66秒(666ms)
        StatMap stats = StatManager.getTotalStatsFromEquipment(p); // 既存の計算メソッドを使用
        double attackSpeed = stats.getFlat(StatType.ATTACK_SPEED);
        if (attackSpeed <= 0) attackSpeed = 1.0; // デフォルト

        long cooldownMs = (long) (1000.0 / attackSpeed);
        long lastUse = cooldowns.getOrDefault(p.getUniqueId(), 0L);
        long now = System.currentTimeMillis();

        if (now - lastUse < cooldownMs) {
            return; // クールダウン中
        }
        cooldowns.put(p.getUniqueId(), now);

        ManaData mana = Deepwither.getInstance().getManaManager().get(p.getUniqueId());

        if (mana.getCurrentMana() < 20) {
            p.sendMessage(ChatColor.RED + "マナが足りません！");
            return;
        }

        // --- 魔法弾の発射 ---
        shootMagicMissile(p, stats);
    }

    private void shootMagicMissile(Player shooter, StatMap stats) {
        // 発射音
        shooter.playSound(shooter.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.0f, 1.5f);

        // 弾の始点（目の位置）
        Location loc = shooter.getEyeLocation().subtract(0, 0.2, 0);
        // 向き
        Vector direction = loc.getDirection().normalize();

        Deepwither.getInstance().getManaManager().get(shooter.getUniqueId()).consume(20);

        // 魔法攻撃力 + 魔法AoEなどを計算
        double damage = stats.getFinal(StatType.MAGIC_DAMAGE);
        // 最低ダメージ保証
        if (damage <= 0) damage = 5.0;

        // 弾速と射程
        double speed = 1.2;
        double range = 20.0;
        final double finalDamage = damage;

        new BukkitRunnable() {
            double distanceTraveled = 0;

            @Override
            public void run() {
                // 移動
                loc.add(direction.clone().multiply(speed));
                distanceTraveled += speed;

                // 射程限界またはブロック衝突判定
                if (distanceTraveled > range || loc.getBlock().getType().isSolid()) {
                    // 壁に当たったエフェクト
                    loc.getWorld().spawnParticle(Particle.POOF, loc, 5, 0.1, 0.1, 0.1, 0.05);
                    this.cancel();
                    return;
                }

                // 弾のエフェクト (魔弾っぽい見た目)
                loc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, 1, 0, 0, 0, 0);
                loc.getWorld().spawnParticle(Particle.SCRAPE, loc, 1, 0, 0, 0, 0);

                // 当たり判定 (半径0.8ブロック)
                for (Entity target : loc.getWorld().getNearbyEntities(loc, 0.8, 0.8, 0.8)) {
                    if (target instanceof LivingEntity livingTarget && target != shooter) {

                        // ダメージ適用 (DamageManagerを経由させることで防御計算などを適用)
                        Deepwither.getInstance().getDamageManager().applyCustomDamage(livingTarget, finalDamage, shooter);

                        // ヒット演出
                        livingTarget.getWorld().playSound(livingTarget.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 1.0f, 1.2f);
                        livingTarget.getWorld().spawnParticle(Particle.FLASH, livingTarget.getLocation().add(0, 1, 0), 1);

                        this.cancel();
                        return;
                    }
                }
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, 1L);
    }
}