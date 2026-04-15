package com.lunar_prototype.deepwither.api.skill;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.SkillDefinition;
import com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent;
import com.lunar_prototype.deepwither.core.damage.DamageContext;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hemorrhage Pact
 * Costs 20% of the caster's max HP, then marks every enemy hit during the next 10 seconds.
 * When the mark resolves, each marked target suffers a 4-second bleed that deals 5% max HP per second.
 */
public class HemorrhagePactSkill implements ISkillLogic {

    private static final int MARK_WINDOW_TICKS = 200;
    private static final int BLEED_TICKS = 80;
    private static final int BLEED_INTERVAL_TICKS = 20;
    private static final double SELF_COST_PERCENT = 0.20;
    private static final double BLEED_DAMAGE_PERCENT = 0.05;

    private static final MapHolder ACTIVE_PACTS = new MapHolder();

    @Override
    public boolean cast(LivingEntity caster, SkillDefinition def, int level) {
        if (!(caster instanceof Player player)) {
            return false;
        }

        double maxHp = Deepwither.getInstance().getStatManager().getActualMaxHealth(player);
        double selfDamage = maxHp * SELF_COST_PERCENT;

        DamageContext selfCtx = new DamageContext(null, player, DeepwitherDamageEvent.DamageType.MAGIC, selfDamage);
        selfCtx.setTrueDamage(true);
        Deepwither.getInstance().getDamageProcessor().process(selfCtx);

        UUID casterId = player.getUniqueId();
        HemorrhageState state = ACTIVE_PACTS.start(casterId);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.9f, 0.7f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.8f, 0.6f);
        player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 1.0, 0),
                40, 0.35, 0.6, 0.35, 0.08, new Particle.DustOptions(Color.fromRGB(120, 0, 0), 2.0f));
        player.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, player.getLocation().add(0, 1.0, 0), 12, 0.2, 0.4, 0.2, 0.0);

        BukkitTask resolveTask = new BukkitRunnable() {
            @Override
            public void run() {
                HemorrhageState state = ACTIVE_PACTS.end(casterId);
                if (state == null) {
                    return;
                }

                LivingEntity source = Bukkit.getPlayer(casterId);
                int affected = 0;
                for (UUID targetId : state.targets) {
                    if (!(Bukkit.getEntity(targetId) instanceof LivingEntity target)) {
                        continue;
                    }
                    if (!target.isValid() || target.isDead()) {
                        continue;
                    }
                    startBleed(source, target);
                    affected++;
                }

                if (source instanceof Player p) {
                    p.sendMessage(Component.text(">>> ", NamedTextColor.DARK_GRAY)
                            .append(Component.text("Hemorrhage Pact", NamedTextColor.DARK_RED))
                            .append(Component.text(" resolved on " + affected + " target(s).", NamedTextColor.GRAY)));
                }
            }
        }.runTaskLater(Deepwither.getInstance(), MARK_WINDOW_TICKS);
        state.setResolveTask(resolveTask);

        player.sendMessage(Component.text(">>> ", NamedTextColor.DARK_GRAY)
                .append(Component.text("Hemorrhage Pact", NamedTextColor.DARK_RED))
                .append(Component.text(" activated. Hits within 10 seconds will be bled.", NamedTextColor.GRAY)));

        return true;
    }

    public static boolean isActive(Player player) {
        return ACTIVE_PACTS.contains(player.getUniqueId());
    }

    public static void recordHit(Player attacker, LivingEntity victim) {
        if (attacker == null || victim == null || attacker.equals(victim)) {
            return;
        }
        ACTIVE_PACTS.recordTarget(attacker.getUniqueId(), victim.getUniqueId());
    }

    private void startBleed(LivingEntity attacker, LivingEntity victim) {
        new BukkitRunnable() {
            int ticksLived = 0;

            @Override
            public void run() {
                if (ticksLived >= BLEED_TICKS || !victim.isValid() || victim.isDead()) {
                    cancel();
                    return;
                }

                double bleedDamage = victim.getMaxHealth() * BLEED_DAMAGE_PERCENT;
                DamageContext dot = new DamageContext(attacker, victim, DeepwitherDamageEvent.DamageType.MAGIC, bleedDamage);
                dot.setTrueDamage(true);
                dot.addTag("HEMORRHAGE_BLEED");
                Deepwither.getInstance().getDamageProcessor().process(dot);

                victim.getWorld().spawnParticle(Particle.DUST, victim.getLocation().add(0, 1.0, 0),
                        10, 0.25, 0.35, 0.25, 0.05, new Particle.DustOptions(Color.fromRGB(170, 0, 0), 1.6f));
                victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_GENERIC_HURT, 0.6f, 0.8f);

                ticksLived += BLEED_INTERVAL_TICKS;
            }
        }.runTaskTimer(Deepwither.getInstance(), 0L, BLEED_INTERVAL_TICKS);
    }

    private static final class HemorrhageState {
        private final Set<UUID> targets = ConcurrentHashMap.newKeySet();
        private BukkitTask resolveTask;

        void setResolveTask(BukkitTask resolveTask) {
            this.resolveTask = resolveTask;
        }

        void cancelResolveTask() {
            if (resolveTask != null) {
                resolveTask.cancel();
            }
        }
    }

    private static final class MapHolder {
        private final java.util.concurrent.ConcurrentHashMap<UUID, HemorrhageState> states = new java.util.concurrent.ConcurrentHashMap<>();

        HemorrhageState start(UUID casterId) {
            HemorrhageState previous = states.put(casterId, new HemorrhageState());
            if (previous != null) {
                previous.cancelResolveTask();
            }
            return states.get(casterId);
        }

        void recordTarget(UUID casterId, UUID targetId) {
            HemorrhageState state = states.get(casterId);
            if (state != null) {
                state.targets.add(targetId);
            }
        }

        HemorrhageState end(UUID casterId) {
            return states.remove(casterId);
        }

        boolean contains(UUID casterId) {
            return states.containsKey(casterId);
        }
    }
}
