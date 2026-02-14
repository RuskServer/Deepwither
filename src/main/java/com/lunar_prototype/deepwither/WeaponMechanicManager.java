package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.api.stat.IStatManager;
import com.lunar_prototype.deepwither.core.damage.DamageContext;
import com.lunar_prototype.deepwither.core.damage.DamageProcessor;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.List;

@DependsOn({StatManager.class, ChargeManager.class, PlayerSettingsManager.class})
public class WeaponMechanicManager implements IManager {

    private final Deepwither plugin;
    private final IStatManager statManager;
    private final ChargeManager chargeManager;
    private final PlayerSettingsManager settingsManager;

    public WeaponMechanicManager(Deepwither plugin, IStatManager statManager, ChargeManager chargeManager, PlayerSettingsManager settingsManager) {
        this.plugin = plugin;
        this.statManager = statManager;
        this.chargeManager = chargeManager;
        this.settingsManager = settingsManager;
    }

    @Override
    public void init() {}

    @Override
    public void shutdown() {}

    public void handleWeaponMechanics(DamageContext context, DamageProcessor processor) {
        Player attacker = context.getAttackerAsPlayer();
        if (attacker == null) return;

        ItemStack item = context.getWeapon();
        if (item == null) return;

        // 1. 剣のスイープ攻撃
        if (isSwordWeapon(item)) {
            handleSwordSweep(attacker, context.getVictim(), context.getFinalDamage(), processor);
        }

        // 2. 槍の貫通攻撃
        if (isSpearWeapon(item)) {
            handleSpearCleave(attacker, context.getVictim(), context.getFinalDamage(), processor);
        }

        // 3. ハンマーの溜め攻撃
        handleHammerCrash(attacker, context.getVictim(), context.getFinalDamage(), item, processor);
    }

    private void handleSwordSweep(Player attacker, LivingEntity target, double damage, DamageProcessor processor) {
        if (attacker.getAttackCooldown() < 0.9f) return;

        double rangeH = 3.0;
        double rangeV = 1.5;
        List<Entity> nearbyEntities = target.getNearbyEntities(rangeH, rangeV, rangeH);
        
        target.getWorld().spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0, 1, 0), 1);
        attacker.playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.0f);
        
        double sweepDamage = damage * 0.5;
        for (Entity nearby : nearbyEntities) {
            if (nearby.equals(attacker) || nearby.equals(target)) continue;
            if (nearby instanceof LivingEntity livingTarget) {
                // 新しいコンテキストでダメージを適用 (再帰的にメカニクスは発動させないよう注意)
                DamageContext sweepContext = new DamageContext(attacker, livingTarget, contextToType(processor), sweepDamage);
                processor.process(sweepContext);
                livingTarget.setVelocity(attacker.getLocation().getDirection().multiply(0.3).setY(0.1));
            }
        }
    }

    private void handleSpearCleave(Player attacker, LivingEntity mainTarget, double damage, DamageProcessor processor) {
        spawnSpearThrustEffect(attacker);
        
        double cleaveDmg = damage * 0.5;
        Vector dir = attacker.getLocation().getDirection().normalize();
        Location checkLoc = mainTarget.getLocation().clone().add(dir);
        
        mainTarget.getWorld().getNearbyEntities(checkLoc, 1.5, 1.5, 1.5).stream()
                .filter(e -> e instanceof LivingEntity && e != attacker && e != mainTarget)
                .map(e -> (LivingEntity) e)
                .forEach(target -> {
                    DamageContext cleaveContext = new DamageContext(attacker, target, contextToType(processor), cleaveDmg);
                    processor.process(cleaveContext);
                    attacker.getWorld().spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0, 1, 0), 1);
                    sendLog(attacker, PlayerSettingsManager.SettingType.SHOW_GIVEN_DAMAGE, 
                            Component.text("貫通！ ", NamedTextColor.YELLOW).append(Component.text("+" + Math.round(cleaveDmg), NamedTextColor.RED)));
                });
    }

    private void handleHammerCrash(Player attacker, LivingEntity target, double damage, ItemStack item, DamageProcessor processor) {
        String chargedType = chargeManager.consumeCharge(attacker.getUniqueId());
        String type = item.getItemMeta().getPersistentDataContainer().get(ItemLoader.CHARGE_ATTACK_KEY, PersistentDataType.STRING);
        
        if (type != null && chargedType != null && chargedType.equals("hammer")) {
            // ハンマーの溜め攻撃は既に計算済みのダメージを3倍にするのではなく、
            // ここで追加の範囲ダメージを発生させる
            Location loc = target.getLocation();
            loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1);
            loc.getWorld().spawnParticle(Particle.SONIC_BOOM, loc, 1);
            loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.5f);
            loc.getWorld().playSound(loc, Sound.BLOCK_ANVIL_LAND, 1.0f, 0.1f);

            double range = 4.0;
            double areaDamage = damage * 0.7 * 3.0; // 溜め倍率を考慮
            for (Entity nearby : target.getNearbyEntities(range, range, range)) {
                if (nearby instanceof LivingEntity living && !nearby.equals(attacker)) {
                    if (!nearby.equals(target)) {
                        DamageContext areaContext = new DamageContext(attacker, living, contextToType(processor), areaDamage);
                        processor.process(areaContext);
                    }
                    Vector kb = living.getLocation().toVector().subtract(attacker.getLocation().toVector()).normalize().multiply(1.5).setY(0.6);
                    living.setVelocity(kb);
                }
            }
            sendLog(attacker, PlayerSettingsManager.SettingType.SHOW_SPECIAL_LOG, 
                    Component.text("CRASH!! ", NamedTextColor.RED, TextDecoration.BOLD)
                            .append(Component.text("ハンマーの溜め攻撃を叩き込んだ！", NamedTextColor.GOLD)));
        }
    }

    private void spawnSpearThrustEffect(Player attacker) {
        Location start = attacker.getEyeLocation();
        Vector dir = start.getDirection().normalize();
        double length = 2.8;
        double step = 0.2;
        for (double i = 0; i <= length; i += step) {
            Location point = start.clone().add(dir.clone().multiply(i));
            attacker.getWorld().spawnParticle(Particle.CRIT, point, 1, 0, 0, 0, 0);
        }
        attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.6f, 1.4f);
    }

    private com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent.DamageType contextToType(DamageProcessor processor) {
        // デフォルトで物理として扱う（再帰用）
        return com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent.DamageType.PHYSICAL;
    }

    private void sendLog(Player player, PlayerSettingsManager.SettingType type, Component message) {
        if (settingsManager.isEnabled(player, type)) {
            player.sendMessage(message);
        }
    }

    private boolean isSpearWeapon(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        List<Component> lore = item.lore();
        if (lore == null) return false;
        for (Component line : lore) {
            if (PlainTextComponentSerializer.plainText().serialize(line).contains("カテゴリ:槍")) return true;
        }
        return false;
    }

    private boolean isSwordWeapon(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        List<Component> lore = item.lore();
        if (lore == null) return false;
        for (Component line : lore) {
            String plain = PlainTextComponentSerializer.plainText().serialize(line);
            if (plain.contains("カテゴリ:剣") || plain.contains("カテゴリ:大剣")) return true;
        }
        return false;
    }
}
