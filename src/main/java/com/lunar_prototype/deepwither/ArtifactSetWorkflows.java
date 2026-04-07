package com.lunar_prototype.deepwither;

import net.kyori.adventure.text.Component;
import org.bukkit.Particle;
import org.bukkit.Sound;

public final class ArtifactSetWorkflows {
    private ArtifactSetWorkflows() {}

    public static ItemFactory.ArtifactSetWorkflow magicBarrierFullBlock(Component message) {
        return context -> {
            if (!context.isMagicDamage()) {
                return;
            }

            context.cancelDamage();
            context.setDamage(0.0);
            context.playSound(Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.1f);
            context.spawnParticle(Particle.ENCHANT, 32, 0.6, 1.0, 0.6, 0.15);
            if (message != null) {
                context.sendMessage(message);
            }
        };
    }

    public static ItemFactory.ArtifactSetWorkflow magicBarrierWithAbsorption(double absorptionAmount, Component message) {
        return context -> {
            if (!context.isMagicDamage()) {
                return;
            }

            context.cancelDamage();
            context.setDamage(0.0);
            if (absorptionAmount > 0) {
                context.setAbsorptionAmount((float) Math.max(context.getAbsorptionAmount(), absorptionAmount));
            }
            context.playSound(Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.1f);
            context.spawnParticle(Particle.ENCHANT, 24, 0.5, 0.8, 0.5, 0.12);
            context.spawnParticle(Particle.WAX_ON, 12, 0.35, 0.6, 0.35, 0.05);
            if (message != null) {
                context.sendMessage(message);
            }
        };
    }

    public static ItemFactory.ArtifactSetWorkflow reduceDamageOnTrigger(double reductionPercent, Component message) {
        return context -> {
            if (reductionPercent <= 0) {
                return;
            }

            double reduced = context.getDamage() * (1.0 - reductionPercent);
            context.setDamage(Math.max(0.0, reduced));
            context.playSound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);
            context.spawnParticle(Particle.CRIT, 16, 0.4, 0.8, 0.4, 0.08);
            if (message != null) {
                context.sendMessage(message);
            }
        };
    }
}
