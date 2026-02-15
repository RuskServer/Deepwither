package com.lunar_prototype.deepwither.dynamic_quest;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.SafeZoneListener;
import com.lunar_prototype.deepwither.dynamic_quest.dialogue.DialogueGenerator;
import com.lunar_prototype.deepwither.dynamic_quest.enums.QuestDifficulty;
import com.lunar_prototype.deepwither.dynamic_quest.enums.QuestPersona;
import com.lunar_prototype.deepwither.dynamic_quest.enums.QuestType;
import com.lunar_prototype.deepwither.dynamic_quest.event.SupplyConvoyEvent;
import com.lunar_prototype.deepwither.dynamic_quest.npc.QuestNPC;
import com.lunar_prototype.deepwither.dynamic_quest.obj.DynamicQuest;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import com.lunar_prototype.deepwither.dynamic_quest.obj.QuestLocation;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import io.lumine.mythic.bukkit.MythicBukkit;
import com.lunar_prototype.deepwither.MobSpawnManager;
import com.lunar_prototype.deepwither.api.DW;

@DependsOn({SafeZoneListener.class, MobSpawnManager.class})
public class DynamicQuestManager implements IManager, Listener {

    private final Deepwither plugin;
    private final DialogueGenerator dialogueGenerator;
    private final List<QuestNPC> activeNPCs = new ArrayList<>();
    private final Map<QuestType, List<QuestLocation>> questLocations = new HashMap<>();
    private File locationsFile;
    private FileConfiguration locationsConfig;
    private BukkitTask spawnTask;
    private final Random random = new Random();

    // Configuration
    private static final int NPCS_PER_REGION = 1; // Number of NPCs to try spawning per safezone
    private static final long REFRESH_INTERVAL_TICKS = 20L * 60 * 10; // 10 minutes

    public DynamicQuestManager(Deepwither plugin) {
        this.plugin = plugin;
        this.dialogueGenerator = new DialogueGenerator();
    }

    @Override
    public void init() {
        cleanupOldNPCs();
        loadLocations();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        startSpawnTask();
    }

    @Override
    public void shutdown() {
        stopSpawnTask();
        despawnAll();
    }

    private void loadLocations() {
        questLocations.clear();
        locationsFile = new File(plugin.getDataFolder(), "dynamic_quest_locations.yml");
        if (!locationsFile.exists()) {
            try {
                locationsFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        locationsConfig = YamlConfiguration.loadConfiguration(locationsFile);

        for (QuestType type : QuestType.values()) {
            List<?> list = locationsConfig.getList("locations." + type.name());
            if (list != null) {
                List<QuestLocation> qLocs = new ArrayList<>();
                for (Object obj : list) {
                    if (obj instanceof QuestLocation) {
                        qLocs.add((QuestLocation) obj);
                    }
                }
                questLocations.put(type, qLocs);
            }
        }
        plugin.getLogger().info("[DynamicQuest] Loaded " + questLocations.values().stream().mapToInt(List::size).sum() + " custom locations.");
    }

    public void saveLocations() {
        for (Map.Entry<QuestType, List<QuestLocation>> entry : questLocations.entrySet()) {
            locationsConfig.set("locations." + entry.getKey().name(), entry.getValue());
        }
        try {
            locationsConfig.save(locationsFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addQuestLocation(QuestType type, QuestLocation loc) {
        List<QuestLocation> list = questLocations.computeIfAbsent(type, k -> new ArrayList<>());
        // Remove old if exists
        list.removeIf(l -> l.getName().equalsIgnoreCase(loc.getName()));
        list.add(loc);
        saveLocations();
    }

    public QuestLocation getQuestLocation(QuestType type, String name) {
        List<QuestLocation> list = questLocations.get(type);
        if (list == null) return null;
        return list.stream().filter(l -> l.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    public int getLayerId(Location loc) {
        if (!Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) return 0;
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();
        ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(loc));
        int maxTier = 0;
        for (ProtectedRegion region : set) {
            String id = region.getId().toLowerCase();
            // We ignore safezone check here because we want to know the "surrounding" layer even in safezone
            int tierIndex = id.indexOf("t");
            if (tierIndex != -1 && tierIndex + 1 < id.length()) {
                char nextChar = id.charAt(tierIndex + 1);
                if (Character.isDigit(nextChar)) {
                    StringBuilder tierStr = new StringBuilder();
                    int i = tierIndex + 1;
                    while (i < id.length() && Character.isDigit(id.charAt(i))) {
                        tierStr.append(id.charAt(i));
                        i++;
                    }
                    try {
                        int tier = Integer.parseInt(tierStr.toString());
                        if (tier > maxTier) maxTier = tier;
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return maxTier;
    }

    private void cleanupOldNPCs() {
        NamespacedKey key = new NamespacedKey(plugin, "quest_npc");
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
                    entity.remove();
                }
            }
        }
    }

    public void forceSpawnAt(Location location) {
        spawnNPC(location);
    }

    public int getActiveNPCCount() {
        return activeNPCs.size();
    }

    public void reload() {
        loadLocations();
        refreshNPCs();
    }

    private void refreshNPCs() {
        // Despawn only non-active NPCs
        activeNPCs.removeIf(npc -> {
            if (npc.getQuest().getStatus() == DynamicQuest.QuestStatus.CREATED) {
                npc.despawn();
                return true;
            }
            return false;
        });

        // Find safe zones
        if (!Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
            plugin.getLogger().warning("WorldGuard not enabled, cannot spawn Dynamic Quest NPCs.");
            return;
        }

        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        
        // Iterate through worlds to find regions with "safezone"
        int regionCount = 0;
        for (World world : Bukkit.getWorlds()) {
            RegionManager regionManager = container.get(BukkitAdapter.adapt(world));
            if (regionManager == null) continue;

            List<ProtectedRegion> safeRegions = regionManager.getRegions().values().stream()
                    .filter(r -> r.getId().toLowerCase().contains("safezone"))
                    .collect(Collectors.toList());

            if (safeRegions.isEmpty()) continue;
            regionCount += safeRegions.size();

            // Try to spawn NPCs in EACH found region
            for (ProtectedRegion region : safeRegions) {
                for (int i = 0; i < NPCS_PER_REGION; i++) {
                    Location spawnLoc = getRandomLocationInRegion(world, region);
                    if (spawnLoc != null) {
                        spawnNPC(spawnLoc);
                    }
                }
            }
        }
        plugin.getLogger().info("[DynamicQuest] Spawning cycle complete. Found " + regionCount + " safezone regions. Total NPCs: " + activeNPCs.size());
    }

    private void startSpawnTask() {
        spawnTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshNPCs, 100L, REFRESH_INTERVAL_TICKS);
    }

    private void stopSpawnTask() {
        if (spawnTask != null && !spawnTask.isCancelled()) {
            spawnTask.cancel();
        }
    }

    private Location getRandomLocationInRegion(World world, ProtectedRegion region) {
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();

        for (int i = 0; i < 15; i++) { // Increase attempts slightly
            int x = ThreadLocalRandom.current().nextInt(min.x(), max.x() + 1);
            int z = ThreadLocalRandom.current().nextInt(min.z(), max.z() + 1);

            int minY = Math.max(world.getMinHeight(), min.y());
            int maxY = Math.min(world.getMaxHeight(), max.y());
            
            // Try random Ys inside the region to handle caves/multi-floor structures
            for (int j = 0; j < 10; j++) {
                int y = ThreadLocalRandom.current().nextInt(minY, maxY + 1);
                
                Location loc = new Location(world, x + 0.5, y, z + 0.5); // Check block at y
                
                // Check for valid standing spot:
                // 1. Block at feet (y) is Air or Passable
                // 2. Block at head (y+1) is Air or Passable
                // 3. Block below (y-1) is Solid
                if (loc.getBlock().isPassable() && 
                    loc.clone().add(0, 1, 0).getBlock().isPassable() && 
                    loc.clone().subtract(0, 1, 0).getBlock().getType().isSolid()) {
                    return loc;
                }
            }
        }
        return null;
    }

    private void spawnNPC(Location location) {
        QuestPersona persona = QuestPersona.values()[random.nextInt(QuestPersona.values().length)];
        QuestType type = QuestType.values()[random.nextInt(QuestType.values().length)];
        // Determine difficulty based on some logic, random for now
        QuestDifficulty difficulty = type.getDefaultDifficulty(); 

        int npcLayer = getLayerId(location);

        // Try to pick a custom location
        Location target = null;
        Location startLocForEvent = null;
        List<QuestLocation> qLocs = questLocations.get(type);
        if (qLocs != null && !qLocs.isEmpty()) {
            // Filter by layer
            List<QuestLocation> layerLocs = qLocs.stream()
                    .filter(l -> l.getLayerId() == npcLayer)
                    .collect(Collectors.toList());

            if (!layerLocs.isEmpty()) {
                QuestLocation chosen = layerLocs.get(random.nextInt(layerLocs.size()));
                target = chosen.getPos();
                if (type == QuestType.RAID) {
                    startLocForEvent = chosen.getPos();
                    target = chosen.getPos2() != null ? chosen.getPos2() : chosen.getPos();
                }
            } else if (npcLayer > 0) {
                plugin.getLogger().warning("[DynamicQuest] No locations found for type " + type + " in Layer " + npcLayer + ". Falling back to random generation.");
            }
        }

        if (target == null) {
            // Generate Target Location (Placeholder logic: 100-500 blocks away)
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = 100 + random.nextDouble() * 400;
            int dx = (int) (Math.cos(angle) * distance);
            int dz = (int) (Math.sin(angle) * distance);
            target = location.clone().add(dx, 0, dz);
            target.setY(target.getWorld().getHighestBlockYAt(target.getBlockX(), target.getBlockZ()) + 1);
        }

        String text = dialogueGenerator.generate(persona, type, target);
        
        // Calculate reward
        double baseReward = 100; // Base credits
        double reward = baseReward * difficulty.getRewardMultiplier();

        String objectiveDesc = "クエスト目的";
        DynamicQuest quest = new DynamicQuest(type, difficulty, persona, text, target, objectiveDesc, reward);

        // Type specific initialization
        switch (type) {
            case FETCH:
                Material[] mats = {Material.IRON_INGOT, Material.BREAD, Material.COPPER_INGOT, Material.COAL};
                Material targetMat = mats[random.nextInt(mats.length)];
                int amount = 3 + random.nextInt(8);
                quest.setTargetItem(new ItemStack(targetMat));
                quest.setTargetAmount(amount);
                quest.setObjectiveDescription(targetMat.name() + " を " + amount + "個持ってくる");
                break;
            case DELIVERY:
                ItemStack item = new ItemStack(Material.PAPER);
                item.editMeta(meta -> meta.displayName(Component.text("重要書類", NamedTextColor.GOLD)));
                quest.setTargetItem(item);
                quest.setObjectiveDescription("指定地点まで 重要書類 を運ぶ");
                break;
            case ELIMINATE:
                MobSpawnManager mobManager = DW.get(MobSpawnManager.class);
                List<String> candidates = mobManager != null ? mobManager.getQuestCandidateMobIdsByTier(npcLayer) : new ArrayList<>();
                
                String targetMob = "ZOMBIE";
                if (!candidates.isEmpty()) {
                    targetMob = candidates.get(random.nextInt(candidates.size()));
                }
                
                int killCount = 5 + random.nextInt(6); // 5-10 kills
                quest.setTargetMobId(targetMob);
                quest.setTargetAmount(killCount);
                quest.setObjectiveDescription(targetMob + " を " + killCount + "体討伐する");
                break;
            case SCOUT:
                quest.setObjectiveDescription("指定地点の状況を確認する");
                break;
            case RAID:
                quest.setObjectiveDescription("移動中の補給部隊を襲撃し、輸送車を破壊する");
                break;
        }

        if (startLocForEvent != null) {
            quest.setStartLocation(startLocForEvent);
        }
        
        QuestNPC npc = new QuestNPC(quest, location);
        npc.spawn();
        activeNPCs.add(npc);
    }

    private void despawnAll() {
        for (QuestNPC npc : activeNPCs) {
            npc.despawn();
        }
        activeNPCs.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        Player player = event.getPlayer();

        for (QuestNPC npc : activeNPCs) {
            if (npc.isEntity(clicked.getUniqueId())) {
                event.setCancelled(true);
                handleNPCInteraction(player, npc);
                return;
            }
        }

        // Fallback for PDC tag
        NamespacedKey key = new NamespacedKey(plugin, "quest_npc");
        if (clicked.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
            event.setCancelled(true);
            // If it has the tag but isn't in activeNPCs, it's a "ghost" NPC
            clicked.remove();
            player.sendMessage(Component.text("このNPCは無効化されました。もう一度お試しください。", NamedTextColor.RED));
        }
    }

    private void handleNPCInteraction(Player player, QuestNPC npc) {
        DynamicQuest quest = npc.getQuest();
        
        player.sendMessage(Component.text("--------------------------------", NamedTextColor.GRAY));
        player.sendMessage(Component.text(quest.getPersona().getDisplayName(), NamedTextColor.YELLOW)
                .append(Component.text(": ", NamedTextColor.GRAY)));

        if (quest.getStatus() == DynamicQuest.QuestStatus.CREATED) {
            player.sendMessage(Component.text(quest.getGeneratedDialogue(), NamedTextColor.WHITE));
            
            Component accept = Component.text("[受諾する]", NamedTextColor.GREEN)
                    .clickEvent(ClickEvent.runCommand("/dq accept " + quest.getQuestId()))
                    .hoverEvent(HoverEvent.showText(Component.text("クリックしてクエストを開始")));
            
            Component decline = Component.text("[拒否する]", NamedTextColor.RED)
                    .clickEvent(ClickEvent.runCommand("/dq decline " + quest.getQuestId()))
                    .hoverEvent(HoverEvent.showText(Component.text("この話を忘れる")));

            player.sendMessage(Component.empty());
            player.sendMessage(accept.append(Component.text("  ")).append(decline));
        } else if (quest.getStatus() == DynamicQuest.QuestStatus.ACTIVE) {
            if (quest.getAssignee().equals(player.getUniqueId())) {
                if (isQuestObjectiveComplete(quest)) {
                    player.sendMessage(Component.text("よくやってくれた！これが約束の報酬だ。", NamedTextColor.WHITE));
                    Component report = Component.text("[報告する]", NamedTextColor.GOLD)
                            .clickEvent(ClickEvent.runCommand("/dq report " + quest.getQuestId()))
                            .hoverEvent(HoverEvent.showText(Component.text("クリックして報酬を受け取り、クエストを完了する")));
                    player.sendMessage(Component.empty());
                    player.sendMessage(report);
                } else {
                    player.sendMessage(Component.text("まだ終わっていないようだな。急いでくれ。", NamedTextColor.WHITE));
                    player.sendMessage(Component.text("目標地点: " + quest.getTargetLocation().getBlockX() + ", " + quest.getTargetLocation().getBlockZ(), NamedTextColor.GRAY));
                }
            } else {
                player.sendMessage(Component.text("他を当たってくれ。先客がいるんでな。", NamedTextColor.GRAY));
            }
        }
        player.sendMessage(Component.text("--------------------------------", NamedTextColor.GRAY));
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        for (QuestNPC npc : activeNPCs) {
            DynamicQuest quest = npc.getQuest();
            if (quest.getStatus() == DynamicQuest.QuestStatus.ACTIVE && uuid.equals(quest.getAssignee())) {
                if (quest.getType() == QuestType.SCOUT || quest.getType() == QuestType.DELIVERY) {
                    if (quest.isObjectiveMet()) continue;
                    
                    if (player.getLocation().distanceSquared(quest.getTargetLocation()) < 100) { // 10 blocks
                        quest.setObjectiveMet(true);
                        player.sendMessage(Component.text(">> クエスト目標地点に到達しました！NPCに報告してください。", NamedTextColor.GOLD));
                    }
                }
            }
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null) return;

        String killedMobId = victim.getType().name();
        
        // MythicMob Check
        if (Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
            var activeMob = MythicBukkit.inst().getMobManager().getActiveMob(victim.getUniqueId());
            if (activeMob.isPresent()) {
                killedMobId = activeMob.get().getMobType();
            }
        }

        for (QuestNPC npc : activeNPCs) {
            DynamicQuest quest = npc.getQuest();
            if (quest.getStatus() == DynamicQuest.QuestStatus.ACTIVE && killer.getUniqueId().equals(quest.getAssignee())) {
                if (quest.getType() == QuestType.ELIMINATE) {
                    if (quest.isObjectiveMet()) continue;

                    if (killedMobId.equalsIgnoreCase(quest.getTargetMobId())) {
                        quest.setProgressCount(quest.getProgressCount() + 1);
                        if (quest.getProgressCount() >= quest.getTargetAmount()) {
                            quest.setObjectiveMet(true);
                            killer.sendMessage(Component.text(">> ターゲットの排除が完了しました！NPCに報告してください。", NamedTextColor.GOLD));
                        } else {
                            killer.sendMessage(Component.text(">> " + quest.getTargetMobId() + " を排除 (" + quest.getProgressCount() + "/" + quest.getTargetAmount() + ")", NamedTextColor.YELLOW));
                        }
                    }
                }
            }
        }
    }

    private boolean isQuestObjectiveComplete(DynamicQuest quest) {
        if (quest.getType() == QuestType.FETCH) {
            // Fetch is checked at the moment of reporting (inventory check)
            return true; // We return true here so handleNPCInteraction shows the [Report] button, but reportQuest will do the final check.
        }
        return quest.isObjectiveMet();
    }

    // --- Command Handling ---

    public void acceptQuest(Player player, UUID questId) {
        Optional<QuestNPC> npcOpt = activeNPCs.stream()
                .filter(n -> n.getQuest().getQuestId().equals(questId))
                .findFirst();

        if (npcOpt.isEmpty()) {
            player.sendMessage(Component.text("そのクエストはもう存在しません。", NamedTextColor.RED));
            return;
        }

        QuestNPC npc = npcOpt.get();
        DynamicQuest quest = npc.getQuest();
        
        if (quest.getStatus() != DynamicQuest.QuestStatus.CREATED) {
            player.sendMessage(Component.text("このクエストは既に受諾されています。", NamedTextColor.RED));
            return;
        }

        quest.setStatus(DynamicQuest.QuestStatus.ACTIVE);
        quest.setAssignee(player.getUniqueId());

        player.sendMessage(Component.text("クエストを受諾しました！", NamedTextColor.GREEN));

        if (quest.getType() == QuestType.RAID) {
            Location start = quest.getStartLocation();
            if (start == null) {
                start = player.getLocation().add(random.nextInt(20) - 10, 0, random.nextInt(20) - 10);
                start.setY(start.getWorld().getHighestBlockYAt(start.getBlockX(), start.getBlockZ()) + 1);
            }
            
            new SupplyConvoyEvent(plugin, start, quest.getTargetLocation(), quest).start();
            player.sendMessage(Component.text(">> 補給部隊が移動を開始した！追跡して襲撃しろ！", NamedTextColor.RED));
        } else if (quest.getType() == QuestType.DELIVERY) {
            if (quest.getTargetItem() != null) {
                player.getInventory().addItem(quest.getTargetItem().clone());
                player.sendMessage(Component.text(">> 配送品を受け取りました。指定地点へ届けてください。", NamedTextColor.YELLOW));
            }
        } else {
            player.sendMessage(Component.text(">> クエストを開始しました。目標: " + quest.getObjectiveDescription(), NamedTextColor.YELLOW));
        }
    }

    public void reportQuest(Player player, UUID questId) {
        Optional<QuestNPC> npcOpt = activeNPCs.stream()
                .filter(n -> n.getQuest().getQuestId().equals(questId))
                .findFirst();

        if (npcOpt.isEmpty()) return;

        QuestNPC npc = npcOpt.get();
        DynamicQuest quest = npc.getQuest();

        if (quest.getStatus() == DynamicQuest.QuestStatus.ACTIVE && player.getUniqueId().equals(quest.getAssignee())) {
            
            // Special check for FETCH
            if (quest.getType() == QuestType.FETCH) {
                ItemStack target = quest.getTargetItem();
                int amount = quest.getTargetAmount();
                if (!player.getInventory().containsAtLeast(target, amount)) {
                    player.sendMessage(Component.text("必要なアイテム (" + target.getType().name() + " x" + amount + ") が足りません。", NamedTextColor.RED));
                    return;
                }
                // Remove items
                ItemStack toRemove = target.clone();
                toRemove.setAmount(amount);
                player.getInventory().removeItem(toRemove);
            }

            if (isQuestObjectiveComplete(quest)) {
                quest.setStatus(DynamicQuest.QuestStatus.COMPLETED);
                
                // Give reward
                Deepwither.getEconomy().depositPlayer(player, quest.getRewardAmount());
                player.sendMessage(Component.text("クエスト完了！報酬として " + quest.getRewardAmount() + " クレジットを獲得しました。", NamedTextColor.GREEN));
                
                // Despawn NPC
                activeNPCs.remove(npc);
                npc.despawn();
            } else {
                player.sendMessage(Component.text("まだ報告できる段階ではありません。", NamedTextColor.RED));
            }
        }
    }

    public void declineQuest(Player player, UUID questId) {
        player.sendMessage(Component.text("クエストを拒否しました。", NamedTextColor.GRAY));
    }
}