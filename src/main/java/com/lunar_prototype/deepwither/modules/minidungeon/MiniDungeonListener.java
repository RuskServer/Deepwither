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
    public void onHologramInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return; // Ignore off-hand
        Entity clicked = event.getRightClicked();
        
        if (!(clicked instanceof TextDisplay)) return;

        for (MiniDungeon dungeon : dungeonManager.getAllDungeons()) {
            if (clicked.getUniqueId().equals(dungeon.getTextDisplayUuid())) {
                startDungeon(event.getPlayer(), dungeon);
                return;
            }
        }
    }

    private void startDungeon(Player player, MiniDungeon dungeon) {
        if (!dungeon.isValid()) {
            player.sendMessage(Component.text("このダンジョンは完全に設定されていません。", NamedTextColor.RED));
            return;
        }

        if (dungeon.isActive()) {
            player.sendMessage(Component.text("誰かが既に攻略中です！", NamedTextColor.RED));
            return;
        }

        CustomMobManager customMobManager = DW.get(CustomMobManager.class);
        if (customMobManager == null) {
            player.sendMessage(Component.text("内部エラー: モブマネージャーが見つかりません。", NamedTextColor.RED));
            return;
        }

        // Start Math
        double progress = (double) dungeon.getCooldownTimer() / dungeonManager.getMaxCooldown();
        if (progress < 0.01) progress = 0.01; // safety minimum

        dungeon.setStartedProgress(progress);
        dungeon.setActive(true);
        dungeon.clearActiveMobs();

        // Spawn Mobs
        List<Location> spawns = dungeon.getSpawnLocations();
        int spawnIndex = 0;
        
        for (String mobId : dungeon.getMobsToSpawn()) {
            Location loc = spawns.get(spawnIndex % spawns.size());
            CustomMob spawnedMob = customMobManager.spawnMob(mobId, loc);
            if (spawnedMob != null && spawnedMob.getEntity() != null) {
                dungeon.addActiveMob(spawnedMob.getEntity().getUniqueId());
            }
            spawnIndex++;
        }

        if (dungeon.getActiveMobs().isEmpty()) {
            player.sendMessage(Component.text("設定エラー: 指定されたモブをスポーンできませんでした。", NamedTextColor.RED));
            dungeon.setActive(false);
            return;
        }

        int percent = (int) (progress * 100);
        player.sendMessage(Component.text("ミニダンジョンを開始しました！ (進行度: " + percent + "%)", NamedTextColor.YELLOW));
        
        if (percent < 100) {
            player.sendMessage(Component.text("完全回復していません。報酬の質と量が抑えられます。", NamedTextColor.GRAY));
        }
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        
        for (MiniDungeon dungeon : dungeonManager.getAllDungeons()) {
            if (dungeon.isActive() && dungeon.getActiveMobs().contains(entity.getUniqueId())) {
                dungeon.removeActiveMob(entity.getUniqueId());
                
                // Clear check
                if (dungeon.getActiveMobs().isEmpty()) {
                    handleDungeonClear(dungeon);
                }
                break;
            }
        }
    }

    private void handleDungeonClear(MiniDungeon dungeon) {
        dungeon.setActive(false);
        dungeon.setCooldownTimer(0); // Reset after clear
        
        LootChestManager lootManager = DW.get(LootChestManager.class);
        if (lootManager == null) return;
        
        LootChestTemplate template = lootManager.getTemplates().get(dungeon.getLootTemplate());
        if (template == null) {
            plugin.getLogger().warning("Invalid template " + dungeon.getLootTemplate() + " for minidungeon " + dungeon.getId());
            return;
        }

        double progress = dungeon.getStartedProgress();
        // Calculate amount of chests (1 to 3 based on progress)
        int numChests = Math.max(1, (int) Math.round(3 * progress));
        
        Location baseLoc = dungeon.getChestLocation().clone();
        ThreadLocalRandom rand = ThreadLocalRandom.current();

        for (int i = 0; i < numChests; i++) {
            // slightly offset chests if multiple
            Location cLoc = baseLoc.clone();
            if (i > 0) {
                cLoc.add(rand.nextInt(3) - 1, 0, rand.nextInt(3) - 1);
            }
            
            Block block = cLoc.getBlock();
            block.setType(Material.CHEST);
            
            if (block.getState() instanceof Chest) {
                Chest chest = (Chest) block.getState();
                MiniDungeonLootUtil.fillScaledChest(chest, template, progress);
            }
        }

        if (baseLoc.getWorld() != null) {
            baseLoc.getWorld().playSound(baseLoc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            baseLoc.getWorld().getNearbyPlayers(baseLoc, 20).forEach(p -> 
                p.sendMessage(Component.text("ミニダンジョンがクリアされました！報酬チェストが出現しました。", NamedTextColor.AQUA))
            );
        }
    }
}
