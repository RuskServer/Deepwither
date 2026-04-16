package com.lunar_prototype.deepwither.modules.minidungeon;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.api.DW;
import com.lunar_prototype.deepwither.loot.LootChestManager;
import com.lunar_prototype.deepwither.loot.LootChestTemplate;
import com.lunar_prototype.deepwither.modules.minidungeon.util.MiniDungeonLootUtil;
import com.lunar_prototype.deepwither.modules.mob.framework.CustomMob;
import com.lunar_prototype.deepwither.modules.mob.framework.CustomMobManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class MiniDungeonListener implements Listener {

    private final Deepwither plugin;
    private final MiniDungeonManager dungeonManager;

    public MiniDungeonListener(Deepwither plugin, MiniDungeonManager dungeonManager) {
        this.plugin = plugin;
        this.dungeonManager = dungeonManager;
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent event) {
        dungeonManager.handleMobDeath(event.getEntity());
    }
}
