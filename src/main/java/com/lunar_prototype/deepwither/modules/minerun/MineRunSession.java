package com.lunar_prototype.deepwither.modules.minerun;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class MineRunSession {

    private final UUID playerId;
    private final Location previousLocation;
    private final int tier;
    private final Location dungeonCenter;
    
    // Shared Instance Link
    private final MineRunManager.MineRunInstance sharedInstance;

    // Time tracking
    private int remainingSeconds;
    
    // Identity Backup
    private final ItemStack[] inventoryContents;
    private final ItemStack[] armorContents;
    private final ItemStack[] extraContents;
    private final float exp;
    private final int level;
    private final double health;
    private final GameMode gameMode;

    public MineRunSession(Player player, MineRunManager.MineRunInstance instance, int durationSeconds) {
        this.playerId = player.getUniqueId();
        this.previousLocation = player.getLocation().clone();
        this.sharedInstance = instance;
        this.tier = instance.tier;
        this.dungeonCenter = instance.safeSpawn.clone();
        this.remainingSeconds = durationSeconds;

        // Backup
        this.inventoryContents = player.getInventory().getContents().clone();
        this.armorContents = player.getInventory().getArmorContents().clone();
        this.extraContents = player.getInventory().getExtraContents().clone();
        this.exp = player.getExp();
        this.level = player.getLevel();
        this.health = player.getHealth();
        this.gameMode = player.getGameMode();
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public Location getPreviousLocation() {
        return previousLocation;
    }

    public int getTier() {
        return tier;
    }

    public Location getDungeonCenter() {
        return dungeonCenter;
    }

    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    public void setRemainingSeconds(int seconds) {
        this.remainingSeconds = seconds;
    }

    public MineRunManager.MineRunInstance getSharedInstance() {
        return sharedInstance;
    }

    public void decrementTime() {
        if (remainingSeconds > 0) {
            remainingSeconds--;
        }
    }

    public void rollback(Player player) {
        player.getInventory().setContents(inventoryContents);
        player.getInventory().setArmorContents(armorContents);
        player.getInventory().setExtraContents(extraContents);
        player.setExp(exp);
        player.setLevel(level);
        player.setGameMode(gameMode);
        player.teleport(previousLocation);
    }
}
