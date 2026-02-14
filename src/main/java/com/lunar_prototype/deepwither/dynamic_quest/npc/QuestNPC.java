package com.lunar_prototype.deepwither.dynamic_quest.npc;

import com.lunar_prototype.deepwither.dynamic_quest.obj.DynamicQuest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Villager;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

public class QuestNPC {
    private final UUID id;
    private final DynamicQuest quest;
    private LivingEntity entity;
    private final Location location;

    public QuestNPC(DynamicQuest quest, Location location) {
        this.id = UUID.randomUUID();
        this.quest = quest;
        this.location = location;
    }

    public void spawn() {
        if (location.getWorld() == null) return;

        // Ensure chunk is loaded
        if (!location.getChunk().isLoaded()) {
            location.getChunk().load();
        }

        this.entity = (LivingEntity) location.getWorld().spawnEntity(location, EntityType.VILLAGER);
        Villager villager = (Villager) entity;

        villager.setAI(false); // No movement
        villager.setInvulnerable(true); // Cannot be killed
        villager.setRemoveWhenFarAway(false); // Persistent
        
        // Visuals based on Persona
        switch (quest.getPersona()) {
            case T_01_VETERAN:
                villager.setProfession(Villager.Profession.WEAPONSMITH);
                villager.setVillagerType(Villager.Type.SNOW); // Military look?
                break;
            case T_02_TIMID_CITIZEN:
                villager.setProfession(Villager.Profession.SHEPHERD);
                villager.setVillagerType(Villager.Type.PLAINS);
                break;
            case T_03_ROUGH_OUTLAW:
                villager.setProfession(Villager.Profession.BUTCHER);
                villager.setVillagerType(Villager.Type.SWAMP);
                break;
            case T_04_CALCULATING_INFORMANT:
                villager.setProfession(Villager.Profession.LIBRARIAN);
                villager.setVillagerType(Villager.Type.DESERT);
                break;
        }

        Component name = Component.text(quest.getPersona().getDisplayName(), NamedTextColor.YELLOW)
                .append(Component.text(" [Quest]", NamedTextColor.GOLD));
        
        villager.customName(name);
        villager.setCustomNameVisible(true);
    }

    public void despawn() {
        if (entity != null && !entity.isDead()) {
            entity.remove();
        }
    }

    public boolean isEntity(UUID entityId) {
        return entity != null && entity.getUniqueId().equals(entityId);
    }

    public DynamicQuest getQuest() {
        return quest;
    }

    public LivingEntity getEntity() {
        return entity;
    }
}
