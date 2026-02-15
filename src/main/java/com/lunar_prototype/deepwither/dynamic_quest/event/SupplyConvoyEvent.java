package com.lunar_prototype.deepwither.dynamic_quest.event;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.dynamic_quest.obj.DynamicQuest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Mule;
import org.bukkit.entity.Pillager;
import org.bukkit.entity.Vindicator;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

public class SupplyConvoyEvent {

    private final Location startLocation;
    private final Location endLocation;
    private final List<LivingEntity> convoyMembers = new ArrayList<>();
    private final DynamicQuest quest;
    private LivingEntity leader;
    private BukkitTask task;
    private boolean isActive = false;
    private Deepwither plugin;

    public SupplyConvoyEvent(Deepwither plugin, Location start, Location end, DynamicQuest quest) {
        this.plugin = plugin;
        this.startLocation = start;
        this.endLocation = end;
        this.quest = quest;
    }

    public void start() {
        if (isActive) return;
        isActive = true;

        // Spawn Leader (Mule)
        leader = (LivingEntity) startLocation.getWorld().spawnEntity(startLocation, EntityType.MULE);
        Mule mule = (Mule) leader;
        mule.setCarryingChest(true);
        mule.customName(Component.text("補給物資輸送車", NamedTextColor.GOLD));
        mule.setCustomNameVisible(true);
        mule.getInventory().addItem(new ItemStack(Material.DIAMOND, 5)); // Loot
        convoyMembers.add(leader);

        // Spawn Guards
        for (int i = 0; i < 3; i++) {
            Location guardLoc = startLocation.clone().add(Math.random() * 4 - 2, 0, Math.random() * 4 - 2);
            LivingEntity guard = (LivingEntity) startLocation.getWorld().spawnEntity(guardLoc, EntityType.PILLAGER);
            guard.customName(Component.text("護衛兵", NamedTextColor.RED));
            convoyMembers.add(guard);
        }

        // QRF Task (Reinforcements after 3 mins)
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!isActive || leader.isDead()) return;
                spawnReinforcements();
            }
        }.runTaskLater(plugin, 20L * 60 * 3);

        // Movement & Status Check Task
        task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isActive) {
                    this.cancel();
                    return;
                }

                // Check Win Condition (Convoy reaches destination)
                if (leader.getLocation().distanceSquared(endLocation) < 25) { // 5 blocks
                    endEvent(false); // Player Failed to stop them
                    return;
                }

                // Check Fail Condition (Leader dies)
                if (leader.isDead()) {
                    endEvent(true); // Player Succeeded in destroying them
                    return;
                }

                // Move entities
                for (LivingEntity entity : convoyMembers) {
                    if (entity instanceof Mob && !entity.isDead()) {
                        Mob mob = (Mob) entity;
                        // Basic pathfinding to destination
                        // If in combat, they might ignore this, which is fine
                        if (mob.getTarget() == null) {
                            mob.getPathfinder().moveTo(endLocation);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // Check every second

        plugin.getServer().broadcast(Component.text("[Event] 補給部隊が移動を開始しました！", NamedTextColor.RED));
    }

    private void spawnReinforcements() {
        if (leader.isDead()) return;
        Location spawnLoc = leader.getLocation().add(Math.random() * 10 - 5, 0, Math.random() * 10 - 5);
        for (int i = 0; i < 2; i++) {
            LivingEntity reinforcement = (LivingEntity) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.VINDICATOR);
            reinforcement.customName(Component.text("QRF部隊", NamedTextColor.DARK_RED));
            convoyMembers.add(reinforcement);
        }
        plugin.getServer().broadcast(Component.text("[Event] 敵の増援(QRF)が到着しました！", NamedTextColor.RED));
    }

    public void endEvent(boolean playerWon) {
        isActive = false;
        if (task != null) task.cancel();

        if (playerWon) {
            plugin.getServer().broadcast(Component.text("[Event] 補給部隊が撃破されました！NPCに報告しましょう。", NamedTextColor.GREEN));
            quest.setObjectiveMet(true);
            // Despawn guards but leave the mule drop
            despawnMembers();
        } else {
            plugin.getServer().broadcast(Component.text("[Event] 補給部隊が目的地に到達しました...作戦失敗。", NamedTextColor.DARK_RED));
            despawnMembers();
        }
    }

    private void despawnMembers() {
        for (LivingEntity entity : convoyMembers) {
            if (entity != null && !entity.isDead()) {
                entity.remove();
            }
        }
        convoyMembers.clear();
    }
}
