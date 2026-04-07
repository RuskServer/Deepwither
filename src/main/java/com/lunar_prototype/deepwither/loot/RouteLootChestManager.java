package com.lunar_prototype.deepwither.loot;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.mail.MailManager;
import com.lunar_prototype.deepwither.party.Party;
import com.lunar_prototype.deepwither.party.PartyManager;
import com.lunar_prototype.deepwither.modules.economy.trader.TaskAreaValidator;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.TileState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@DependsOn({MailManager.class, PartyManager.class})
public class RouteLootChestManager implements IManager, Listener {

    private static final String CONFIG_FILE = "route_loot_chest.yml";
    private static final int DEFAULT_INTERVAL_TICKS = 20 * 60 * 20;
    private static final int DEFAULT_OPEN_SECONDS = 15;
    private static final int DEFAULT_PVP_RADIUS = 32;
    private static final long DEFAULT_EVENT_DURATION_TICKS = 20L * 60 * 30;
    private static final long DEFAULT_LAYER_ANNOUNCE_TICKS = 20L * 60 * 15;
    private static final long DEFAULT_COORD_ANNOUNCE_TICKS = 20L * 60 * 5;
    private static final double DEFAULT_PARTY_BONUS_PER_MEMBER = 0.08;
    private static final double DEFAULT_CONSOLATION_MULTIPLIER = 0.25;
    private static final long DEFAULT_ACTIVITY_HALF_LIFE_TICKS = 20L * 60 * 10;

    private final Deepwither plugin;
    private final TaskAreaValidator tierValidator = new TaskAreaValidator();
    private final NamespacedKey chestIdKey;
    private final NamespacedKey chestKindKey;
    private final NamespacedKey chestTierKey;
    private final NamespacedKey chestSpawnedKey;
    private final NamespacedKey chestLayerKey;
    private final ThreadLocalRandom random = ThreadLocalRandom.current();

    private MailManager mailManager;
    private PartyManager partyManager;

    private final Map<Integer, LayerBinding> layerBindings = new HashMap<>();
    private final Map<UUID, ActivityRecord> activityRecords = new ConcurrentHashMap<>();
    private final Map<UUID, OpeningSession> openingSessions = new ConcurrentHashMap<>();
    private final Map<UUID, EventChest> activeChests = new ConcurrentHashMap<>();
    private PendingEvent pendingEvent;

    private int intervalTicks = DEFAULT_INTERVAL_TICKS;
    private int openSeconds = DEFAULT_OPEN_SECONDS;
    private int pvpRadius = DEFAULT_PVP_RADIUS;
    private long eventDurationTicks = DEFAULT_EVENT_DURATION_TICKS;
    private long layerAnnounceTicks = DEFAULT_LAYER_ANNOUNCE_TICKS;
    private long coordAnnounceTicks = DEFAULT_COORD_ANNOUNCE_TICKS;
    private double partyBonusPerMember = DEFAULT_PARTY_BONUS_PER_MEMBER;
    private double consolationMultiplier = DEFAULT_CONSOLATION_MULTIPLIER;
    private long activityHalfLifeTicks = DEFAULT_ACTIVITY_HALF_LIFE_TICKS;

    public RouteLootChestManager(Deepwither plugin) {
        this.plugin = plugin;
        this.chestIdKey = new NamespacedKey(plugin, "route_loot_chest_id");
        this.chestKindKey = new NamespacedKey(plugin, "route_loot_chest_kind");
        this.chestTierKey = new NamespacedKey(plugin, "route_loot_chest_tier");
        this.chestSpawnedKey = new NamespacedKey(plugin, "route_loot_chest_spawned_at");
        this.chestLayerKey = new NamespacedKey(plugin, "route_loot_chest_layer");
    }

    @Override
    public void init() {
        this.mailManager = plugin.getMailManager();
        this.partyManager = plugin.getPartyManager();
        loadConfig();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        scheduleNextEvent();
    }

    @Override
    public void shutdown() {
        cancelPendingEvent();
        clearActiveChest();
    }

    public boolean isEventPvpAllowed(Location location) {
        EventChest chest = getActiveChest();
        if (chest == null || location == null || location.getWorld() == null) {
            return false;
        }
        return chest.location.getWorld() != null
                && chest.location.getWorld().equals(location.getWorld())
                && chest.location.distanceSquared(location) <= (pvpRadius * pvpRadius);
    }

    public void recordActivity(Player player, double weight) {
        if (player == null || weight <= 0) {
            return;
        }
        activityRecords.compute(player.getUniqueId(), (uuid, record) -> {
            long now = System.currentTimeMillis();
            if (record == null) {
                return new ActivityRecord(weight, now);
            }
            double decayed = record.getScore(now, activityHalfLifeTicks);
            return new ActivityRecord(decayed + weight, now);
        });
    }

    private void loadConfig() {
        File file = new File(plugin.getDataFolder(), CONFIG_FILE);
        if (!file.exists()) {
            plugin.saveResource(CONFIG_FILE, false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        intervalTicks = config.getInt("event.interval_ticks", intervalTicks);
        openSeconds = config.getInt("event.open_seconds", openSeconds);
        pvpRadius = config.getInt("event.pvp_radius", pvpRadius);
        eventDurationTicks = config.getLong("event.duration_ticks", eventDurationTicks);
        layerAnnounceTicks = config.getLong("event.layer_announce_ticks", layerAnnounceTicks);
        coordAnnounceTicks = config.getLong("event.coordinate_announce_ticks", coordAnnounceTicks);
        partyBonusPerMember = config.getDouble("event.party_bonus_per_member", partyBonusPerMember);
        consolationMultiplier = config.getDouble("event.consolation_multiplier", consolationMultiplier);
        activityHalfLifeTicks = config.getLong("event.activity_half_life_ticks", activityHalfLifeTicks);

        layerBindings.clear();
        ConfigurationSection layers = config.getConfigurationSection("layers");
        if (layers != null) {
            for (String tierKey : layers.getKeys(false)) {
                ConfigurationSection layerSection = layers.getConfigurationSection(tierKey);
                if (layerSection == null) {
                    continue;
                }
                LayerBinding binding = LayerBinding.load(tierKey, layerSection);
                if (binding == null) {
                    plugin.getLogger().warning("Invalid route loot chest tier key: " + tierKey);
                    continue;
                }
                for (int tier = binding.minTier; tier <= binding.maxTier; tier++) {
                    if (layerBindings.containsKey(tier)) {
                        plugin.getLogger().warning("Duplicate route loot chest tier mapping detected for tier " + tier + "; skipping key " + tierKey);
                        continue;
                    }
                    layerBindings.put(tier, binding);
                }
            }
        }

        plugin.getLogger().info("RouteLootChestManager loaded with " + layerBindings.size() + " tier mappings.");
    }

    private void scheduleNextEvent() {
        cancelPendingEvent();

        if (intervalTicks <= 0) {
            plugin.getLogger().warning("route_loot_chest.yml has invalid interval_ticks; event spawning disabled.");
            return;
        }

        if (layerAnnounceTicks <= 0 || coordAnnounceTicks <= 0 || coordAnnounceTicks >= layerAnnounceTicks) {
            plugin.getLogger().warning("route_loot_chest.yml has invalid announce timing; coordinate announcement must be before layer announcement and both must be positive.");
            return;
        }

        if (intervalTicks <= layerAnnounceTicks || intervalTicks <= coordAnnounceTicks) {
            plugin.getLogger().warning("route_loot_chest.yml interval_ticks must be larger than the announce offsets to support 15m/5m broadcasts.");
            return;
        }

        PendingEvent event = new PendingEvent(UUID.randomUUID());
        pendingEvent = event;

        long layerDelay = intervalTicks - layerAnnounceTicks;
        long coordDelay = intervalTicks - coordAnnounceTicks;
        long spawnDelay = intervalTicks;

        event.layerAnnouncementTask = Bukkit.getScheduler().runTaskLater(plugin, () -> onLayerAnnouncement(event.id), layerDelay);
        event.coordinateAnnouncementTask = Bukkit.getScheduler().runTaskLater(plugin, () -> onCoordinateAnnouncement(event.id), coordDelay);
        event.spawnTask = Bukkit.getScheduler().runTaskLater(plugin, () -> onSpawnEvent(event.id), spawnDelay);
    }

    private void cancelPendingEvent() {
        if (pendingEvent == null) {
            return;
        }
        if (pendingEvent.layerAnnouncementTask != null) {
            pendingEvent.layerAnnouncementTask.cancel();
        }
        if (pendingEvent.coordinateAnnouncementTask != null) {
            pendingEvent.coordinateAnnouncementTask.cancel();
        }
        if (pendingEvent.spawnTask != null) {
            pendingEvent.spawnTask.cancel();
        }
        pendingEvent = null;
    }

    private void onLayerAnnouncement(UUID eventId) {
        PendingEvent event = pendingEvent;
        if (event == null || !event.id.equals(eventId)) {
            return;
        }

        LayerSelection selection = chooseLayer();
        if (selection == null) {
            Bukkit.broadcast(Component.text("[ルートチェスト] ", NamedTextColor.GRAY)
                    .append(Component.text("次回の戦利品チェストは出現条件を満たしませんでした。", NamedTextColor.WHITE)));
            cancelPendingEvent();
            scheduleNextEvent();
            return;
        }

        event.selection = selection;
        event.binding = layerBindings.get(selection.tier());

        Bukkit.broadcast(Component.text("[ルートチェスト] ", NamedTextColor.GOLD)
                .append(Component.text("次回の戦利品チェストは ", NamedTextColor.WHITE))
                .append(Component.text(selection.layerLabel(), NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text(" に出現予定です。", NamedTextColor.WHITE))
                .append(Component.text(" 人数優先 / 活動スコア ", NamedTextColor.GRAY))
                .append(Component.text(String.format("%.1f", selection.activityScore()), NamedTextColor.AQUA)));
    }

    private void onCoordinateAnnouncement(UUID eventId) {
        PendingEvent event = pendingEvent;
        if (event == null || !event.id.equals(eventId)) {
            return;
        }

        if (event.selection == null) {
            event.selection = chooseLayer();
            if (event.selection == null) {
                Bukkit.broadcast(Component.text("[ルートチェスト] ", NamedTextColor.GRAY)
                        .append(Component.text("出現予定の戦利品チェストを決定できませんでした。", NamedTextColor.WHITE)));
                cancelPendingEvent();
                scheduleNextEvent();
                return;
            }
            event.binding = layerBindings.get(event.selection.tier());
        }

        if (event.binding == null) {
            Bukkit.broadcast(Component.text("[ルートチェスト] ", NamedTextColor.GRAY)
                    .append(Component.text("出現予定地点の候補が見つかりませんでした。", NamedTextColor.WHITE)));
            cancelPendingEvent();
            scheduleNextEvent();
            return;
        }

        CandidateLocation candidate = event.binding.pickCandidate(random);
        if (candidate == null) {
            Bukkit.broadcast(Component.text("[ルートチェスト] ", NamedTextColor.GRAY)
                    .append(Component.text("出現予定地点の候補がありません。", NamedTextColor.WHITE)));
            cancelPendingEvent();
            scheduleNextEvent();
            return;
        }

        Location announceLocation = candidate.toLocation();
        if (!isSpawnLocationValid(announceLocation)) {
            Bukkit.broadcast(Component.text("[ルートチェスト] ", NamedTextColor.GRAY)
                    .append(Component.text("出現予定地点が不正だったため、この回の出現は見送られます。", NamedTextColor.WHITE)));
            cancelPendingEvent();
            scheduleNextEvent();
            return;
        }

        event.candidate = candidate;
        event.spawnLocation = announceLocation;

        Bukkit.broadcast(Component.text("[ルートチェスト] ", NamedTextColor.GOLD)
                .append(Component.text("出現予定座標: ", NamedTextColor.WHITE))
                .append(Component.text(String.format("%s %.1f %.1f %.1f", candidate.world, candidate.x, candidate.y, candidate.z), NamedTextColor.AQUA))
                .append(Component.text(" / ", NamedTextColor.DARK_GRAY))
                .append(Component.text(event.selection.layerLabel(), NamedTextColor.YELLOW, TextDecoration.BOLD)));
    }

    private void onSpawnEvent(UUID eventId) {
        PendingEvent event = pendingEvent;
        pendingEvent = null;
        if (event == null || !event.id.equals(eventId)) {
            scheduleNextEvent();
            return;
        }

        if (getActiveChest() != null) {
            Bukkit.broadcast(Component.text("[ルートチェスト] ", NamedTextColor.GRAY)
                    .append(Component.text("前回の戦利品チェストがまだ有効なため、今回の出現は見送られました。", NamedTextColor.WHITE)));
            scheduleNextEvent();
            return;
        }

        if (event.selection == null) {
            event.selection = chooseLayer();
            if (event.selection == null) {
                Bukkit.broadcast(Component.text("[ルートチェスト] ", NamedTextColor.GRAY)
                        .append(Component.text("戦利品チェストの出現条件を満たしませんでした。", NamedTextColor.WHITE)));
                scheduleNextEvent();
                return;
            }
            event.binding = layerBindings.get(event.selection.tier());
        }

        if (event.binding == null) {
            scheduleNextEvent();
            return;
        }

        CandidateLocation candidate = event.candidate != null ? event.candidate : event.binding.pickCandidate(random);
        if (candidate == null) {
            Bukkit.broadcast(Component.text("[ルートチェスト] ", NamedTextColor.GRAY)
                    .append(Component.text("戦利品チェストの候補地点が見つかりませんでした。", NamedTextColor.WHITE)));
            scheduleNextEvent();
            return;
        }

        Location spawnLocation = candidate.toLocation();
        if (!isSpawnLocationValid(spawnLocation)) {
            plugin.getLogger().warning("Invalid route loot spawn location for tier " + event.selection.tier() + ": " + candidate);
            scheduleNextEvent();
            return;
        }

        spawnChest(event.selection.tier(), event.binding, spawnLocation, event.selection.playerCount(), event.selection.activityScore());
        scheduleNextEvent();
    }

    private LayerSelection chooseLayer() {
        Map<Integer, LayerSelection> layerStats = new HashMap<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) {
                continue;
            }

            int tier = tierValidator.getTierFromLocation(player.getLocation());
            if (!layerBindings.containsKey(tier)) {
                continue;
            }

            double score = getActivityScore(player.getUniqueId());
            layerStats.compute(tier, (k, current) -> {
                if (current == null) {
                    return new LayerSelection(tier, layerBindings.get(tier).rangeLabel, 1, score);
                }
                return new LayerSelection(tier, current.layerLabel, current.playerCount + 1, current.activityScore + score);
            });
        }

        List<LayerSelection> selections = new ArrayList<>(layerStats.values());
        Collections.shuffle(selections, random);
        return selections.stream()
                .filter(sel -> sel.playerCount > 0)
                .sorted(Comparator
                        .comparingInt(LayerSelection::playerCount).reversed()
                        .thenComparing(Comparator.comparingDouble(LayerSelection::activityScore).reversed()))
                .findFirst()
                .orElse(null);
    }

    private double getActivityScore(UUID uuid) {
        ActivityRecord record = activityRecords.get(uuid);
        if (record == null) {
            return 0.0;
        }
        return record.getScore(System.currentTimeMillis(), activityHalfLifeTicks);
    }

    private void spawnChest(int tier, LayerBinding binding, Location location, int playerCount, double activityScore) {
        Block block = location.getBlock();
        if (block.getType() != Material.AIR) {
            Bukkit.broadcast(Component.text("[ルートチェスト] ", NamedTextColor.GRAY)
                    .append(Component.text("出現予定地点が塞がっていたため、今回の出現は見送られました。", NamedTextColor.WHITE)));
            return;
        }

        block.setType(Material.CHEST);
        BlockState state = block.getState();
        UUID chestId = UUID.randomUUID();
        if (state instanceof TileState tileState) {
            tileState.getPersistentDataContainer().set(chestIdKey, PersistentDataType.STRING, chestId.toString());
            tileState.getPersistentDataContainer().set(chestKindKey, PersistentDataType.STRING, "route_loot");
            tileState.getPersistentDataContainer().set(chestTierKey, PersistentDataType.INTEGER, tier);
            tileState.getPersistentDataContainer().set(chestSpawnedKey, PersistentDataType.LONG, System.currentTimeMillis());
            tileState.getPersistentDataContainer().set(chestLayerKey, PersistentDataType.STRING, binding.rangeLabel);
            tileState.update(true, false);
        }

        EventChest chest = new EventChest(chestId, tier, binding.rangeLabel, location.clone(), binding.config);
        chest.spawnedAt = System.currentTimeMillis();
        activeChests.put(chestId, chest);
        chest.expireTask = Bukkit.getScheduler().runTaskLater(plugin, () -> expireChest(chestId), eventDurationTicks);

        Bukkit.broadcast(Component.text("[ルートチェスト] ", NamedTextColor.GOLD)
                .append(Component.text("戦利品チェストが出現しました。", NamedTextColor.WHITE))
                .append(Component.text(" 出現層: ", NamedTextColor.GRAY))
                .append(Component.text(binding.rangeLabel, NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text(" / 人数優先で選出済み: ", NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(playerCount), NamedTextColor.YELLOW))
                .append(Component.text(" / 活動スコア: ", NamedTextColor.GRAY))
                .append(Component.text(String.format("%.1f", activityScore), NamedTextColor.AQUA)));
    }

    private boolean isSpawnLocationValid(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        Block block = location.getBlock();
        Block below = block.getRelative(0, -1, 0);
        return block.getType() == Material.AIR && below.getType().isSolid();
    }

    public void handleChestInteract(Player player, Block block) {
        EventChest chest = getChestAt(block.getLocation());
        if (chest == null) {
            return;
        }

        if (isClaimedByOther(chest, player)) {
            player.sendMessage(Component.text("[ルートチェスト] ", NamedTextColor.RED)
                    .append(Component.text("すでに別のプレイヤーかパーティーが開封中です。", NamedTextColor.WHITE)));
            return;
        }

        if (chest.openingSession != null) {
            player.sendMessage(Component.text("[ルートチェスト] ", NamedTextColor.YELLOW)
                    .append(Component.text("開封中です。攻撃を受けると進捗がリセットされます。", NamedTextColor.WHITE)));
            return;
        }

        if (chest.claimSnapshot == null || chest.claimSnapshot.isEmpty()) {
            chest.claimSnapshot = snapshotRecipients(player);
            chest.claimOwner = player.getUniqueId();
        }

        if (!chest.claimSnapshot.contains(player.getUniqueId())) {
            player.sendMessage(Component.text("[ルートチェスト] ", NamedTextColor.RED)
                    .append(Component.text("このチェストを開ける権利がありません。", NamedTextColor.WHITE)));
            return;
        }

        startOpening(chest, player);
    }

    private boolean isClaimedByOther(EventChest chest, Player player) {
        if (chest.claimSnapshot == null || chest.claimSnapshot.isEmpty()) {
            return false;
        }
        return !chest.claimSnapshot.contains(player.getUniqueId());
    }

    private Set<UUID> snapshotRecipients(Player player) {
        Party party = partyManager.getParty(player);
        if (party == null) {
            return Set.of(player.getUniqueId());
        }
        return party.getOnlineMembers().stream()
                .map(Player::getUniqueId)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private void startOpening(EventChest chest, Player player) {
        if (chest.openingSession != null) {
            return;
        }

        chest.openingSession = new OpeningSession(player.getUniqueId(), openSeconds);
        chest.openingSession.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Player opener = Bukkit.getPlayer(chest.openingSession.openerId);
            if (opener == null || !opener.isOnline() || opener.isDead()) {
                resetOpening(chest, true, "開封が中断されました。");
                return;
            }

            if (!isActiveChest(chest.location)) {
                resetOpening(chest, true, "チェストが消滅しました。");
                return;
            }

            if (opener.getLocation().getWorld() != null && chest.location.getWorld() != null
                    && !opener.getLocation().getWorld().equals(chest.location.getWorld())) {
                resetOpening(chest, true, "チェストから離れました。");
                return;
            }

            if (opener.getLocation().distanceSquared(chest.location) > (pvpRadius * pvpRadius)) {
                resetOpening(chest, true, "開封範囲から外れました。");
                return;
            }

            opener.sendActionBar(Component.text("[ルートチェスト] ", NamedTextColor.GOLD)
                    .append(Component.text("開封中 ", NamedTextColor.WHITE))
                    .append(Component.text(chest.openingSession.remainingSeconds + "s", NamedTextColor.YELLOW))
                    .append(Component.text(" 残り", NamedTextColor.GRAY)));

            chest.openingSession.remainingSeconds--;
            if (chest.openingSession.remainingSeconds <= 0) {
                completeOpening(chest);
            }
        }, 20L, 20L);

        player.sendMessage(Component.text("[ルートチェスト] ", NamedTextColor.GOLD)
                .append(Component.text("開封を開始しました。15秒間そのままでいてください。", NamedTextColor.WHITE)));
    }

    private void completeOpening(EventChest chest) {
        OpeningSession session = chest.openingSession;
        if (session == null) {
            return;
        }

        Player opener = Bukkit.getPlayer(session.openerId);
        if (opener == null) {
            resetOpening(chest, true, "開封に失敗しました。");
            return;
        }

        Set<UUID> recipients = chest.claimSnapshot == null || chest.claimSnapshot.isEmpty()
                ? Set.of(session.openerId)
                : new HashSet<>(chest.claimSnapshot);

        Map<UUID, List<ItemStack>> bundles = buildRewardBundles(chest.layerConfig, recipients);
        sendRewardMails(chest, bundles, recipients, session.openerId);
        clearChestBlock(chest);
        clearActiveChest();
        Bukkit.broadcast(Component.text("[ルートチェスト] ", NamedTextColor.GREEN)
                .append(Component.text("チェストの報酬がメールに送られました。", NamedTextColor.WHITE)));
    }

    private Map<UUID, List<ItemStack>> buildRewardBundles(LayerConfig layer, Set<UUID> recipients) {
        List<UUID> recipientList = new ArrayList<>(recipients);
        Map<UUID, List<ItemStack>> bundles = new LinkedHashMap<>();
        for (UUID recipient : recipientList) {
            bundles.put(recipient, new ArrayList<>());
        }

        List<ItemStack> commonRewards = rollTemplate(layer.commonTemplate);
        double partyMultiplier = 1.0 + Math.max(0, recipientList.size() - 1) * partyBonusPerMember;
        List<ItemStack> boostedCommonRewards = scaleStacks(commonRewards, partyMultiplier);
        distributeStacks(boostedCommonRewards, recipientList, bundles);

        if (layer.rareTemplate != null) {
            List<ItemStack> rareRewards = rollTemplate(layer.rareTemplate);
            Set<UUID> rareWinners = new HashSet<>();
            for (ItemStack rareReward : rareRewards) {
                UUID winner = recipientList.get(random.nextInt(recipientList.size()));
                bundles.get(winner).add(rareReward);
                rareWinners.add(winner);
            }

            if (recipientList.size() > 1 && !rareRewards.isEmpty()) {
                List<UUID> consolationTargets = recipientList.stream()
                        .filter(uuid -> !rareWinners.contains(uuid))
                        .collect(Collectors.toList());
                if (!consolationTargets.isEmpty()) {
                    List<ItemStack> consolation = scaleStacks(rollTemplate(layer.commonTemplate), consolationMultiplier);
                    distributeStacks(consolation, consolationTargets, bundles);
                }
            }
        }

        return bundles;
    }

    private void sendRewardMails(EventChest chest, Map<UUID, List<ItemStack>> bundles, Set<UUID> recipients, UUID openerId) {
        String title = "ルートチェスト報酬 (" + chest.layerName + ")";
        List<String> openerBody = List.of(
                "層帯: " + chest.layerName,
                "対象人数: " + recipients.size(),
                "15秒の開封に成功しました。"
        );
        for (Map.Entry<UUID, List<ItemStack>> entry : bundles.entrySet()) {
            UUID recipient = entry.getKey();
            List<String> body = new ArrayList<>(openerBody);
            if (recipient.equals(openerId)) {
                body.add("あなたが開封を完了しました。");
            } else {
                body.add("パーティー報酬として配布されました。");
            }
            mailManager.sendMail(recipient, title, body, entry.getValue());
        }
    }

    private List<ItemStack> rollTemplate(LootChestTemplate template) {
        List<ItemStack> result = new ArrayList<>();
        if (template == null || template.getEntries().isEmpty()) {
            return result;
        }
        int min = template.getMinItems();
        int max = template.getMaxItems();
        int itemSlots = random.nextInt(Math.max(1, max - min + 1)) + min;
        for (int i = 0; i < itemSlots; i++) {
            double totalChance = template.getTotalChance();
            double roll = random.nextDouble() * totalChance;
            LootEntry selectedEntry = null;
            double currentRoll = roll;
            for (LootEntry entry : template.getEntries()) {
                currentRoll -= entry.getChance();
                if (currentRoll <= 0) {
                    selectedEntry = entry;
                    break;
                }
            }
            if (selectedEntry != null) {
                ItemStack item = selectedEntry.createItem(random);
                if (item != null) {
                    result.add(item);
                }
            }
        }

        if (result.isEmpty()) {
            LootEntry fallback = template.getEntries().get(random.nextInt(template.getEntries().size()));
            ItemStack item = fallback.createItem(random);
            if (item != null) {
                result.add(item);
            }
        }
        return result;
    }

    private List<ItemStack> scaleStacks(List<ItemStack> stacks, double multiplier) {
        List<ItemStack> scaled = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) {
                continue;
            }
            ItemStack clone = stack.clone();
            int amount = (int) Math.floor(clone.getAmount() * multiplier);
            if (amount <= 0) {
                amount = 1;
            }
            clone.setAmount(amount);
            scaled.add(clone);
        }
        return scaled;
    }

    private void distributeStacks(List<ItemStack> stacks, List<UUID> recipients, Map<UUID, List<ItemStack>> bundles) {
        if (recipients.isEmpty()) {
            return;
        }

        for (ItemStack stack : stacks) {
            if (stack == null || stack.getAmount() <= 0) {
                continue;
            }
            int total = stack.getAmount();
            int share = total / recipients.size();
            int remainder = total % recipients.size();

            if (share > 0) {
                for (UUID recipient : recipients) {
                    ItemStack clone = stack.clone();
                    clone.setAmount(share);
                    bundles.computeIfAbsent(recipient, ignored -> new ArrayList<>()).add(clone);
                }
            }

            if (remainder > 0) {
                List<UUID> shuffled = new ArrayList<>(recipients);
                Collections.shuffle(shuffled, random);
                for (int i = 0; i < remainder; i++) {
                    UUID recipient = shuffled.get(i % shuffled.size());
                    ItemStack clone = stack.clone();
                    clone.setAmount(1);
                    bundles.computeIfAbsent(recipient, ignored -> new ArrayList<>()).add(clone);
                }
            }
        }
    }

    private void expireChest(UUID chestId) {
        EventChest chest = activeChests.remove(chestId);
        if (chest == null) {
            return;
        }
        resetOpening(chest, false, null);
        clearChestBlock(chest);
        Bukkit.broadcast(Component.text("[ルートチェスト] ", NamedTextColor.GRAY)
                .append(Component.text("チェストが消滅しました。", NamedTextColor.WHITE)));
    }

    private void resetOpening(EventChest chest, boolean notifyPlayer, String message) {
        OpeningSession session = chest.openingSession;
        if (session != null && session.task != null) {
            session.task.cancel();
        }
        if (notifyPlayer && session != null) {
            Player opener = Bukkit.getPlayer(session.openerId);
            if (opener != null) {
                opener.sendMessage(Component.text("[ルートチェスト] ", NamedTextColor.RED)
                        .append(Component.text(message == null ? "開封が中断されました。" : message, NamedTextColor.WHITE)));
            }
        }
        chest.openingSession = null;
    }

    private void clearChestBlock(EventChest chest) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Block block = chest.location.getBlock();
            if (block.getType() == Material.CHEST) {
                block.setType(Material.AIR);
            }
        });
    }

    private void clearActiveChest() {
        for (EventChest chest : new ArrayList<>(activeChests.values())) {
            if (chest.expireTask != null) {
                chest.expireTask.cancel();
            }
            if (chest.openingSession != null && chest.openingSession.task != null) {
                chest.openingSession.task.cancel();
            }
            activeChests.remove(chest.id);
        }
    }

    private EventChest getActiveChest() {
        return activeChests.values().stream().findFirst().orElse(null);
    }

    private EventChest getChestAt(Location location) {
        if (location == null) {
            return null;
        }
        return activeChests.values().stream()
                .filter(chest -> chest.location.getWorld() != null
                        && chest.location.getWorld().equals(location.getWorld())
                        && chest.location.getBlockX() == location.getBlockX()
                        && chest.location.getBlockY() == location.getBlockY()
                        && chest.location.getBlockZ() == location.getBlockZ())
                .findFirst()
                .orElse(null);
    }

    private boolean isActiveChest(Location location) {
        return getChestAt(location) != null;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onChestInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.CHEST) {
            return;
        }
        EventChest chest = getChestAt(event.getClickedBlock().getLocation());
        if (chest == null) {
            return;
        }
        event.setCancelled(true);
        handleChestInteract(event.getPlayer(), event.getClickedBlock());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        EventChest chest = getChestAt(event.getBlock().getLocation());
        if (chest == null) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(Component.text("[ルートチェスト] ", NamedTextColor.RED)
                .append(Component.text("イベントチェストは破壊できません。", NamedTextColor.WHITE)));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        for (EventChest chest : activeChests.values()) {
            OpeningSession session = chest.openingSession;
            if (session == null || !session.openerId.equals(victim.getUniqueId())) {
                continue;
            }
            Entity damager = event.getDamager();
            if (damager instanceof Player || damager instanceof org.bukkit.entity.Projectile projectile
                    && projectile.getShooter() instanceof Player) {
                resetOpening(chest, true, "攻撃を受けたため開封進捗がリセットされました。");
                return;
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        for (EventChest chest : activeChests.values()) {
            OpeningSession session = chest.openingSession;
            if (session != null && session.openerId.equals(event.getPlayer().getUniqueId())) {
                resetOpening(chest, false, null);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        recordActivity(event.getPlayer(), 1.0);
    }

    public void recordMobKill(Player player) {
        recordActivity(player, 8.0);
    }

    private double getActivityScore(Player player) {
        return getActivityScore(player.getUniqueId());
    }

    private static final class LayerSelection {
        private final int tier;
        private final String layerLabel;
        private final int playerCount;
        private final double activityScore;

        private LayerSelection(int tier, String layerLabel, int playerCount, double activityScore) {
            this.tier = tier;
            this.layerLabel = layerLabel;
            this.playerCount = playerCount;
            this.activityScore = activityScore;
        }

        public int tier() {
            return tier;
        }

        public String layerLabel() {
            return layerLabel;
        }

        public int playerCount() {
            return playerCount;
        }

        public double activityScore() {
            return activityScore;
        }
    }

    private static final class PendingEvent {
        private final UUID id;
        private LayerSelection selection;
        private LayerBinding binding;
        private CandidateLocation candidate;
        private Location spawnLocation;
        private BukkitTask layerAnnouncementTask;
        private BukkitTask coordinateAnnouncementTask;
        private BukkitTask spawnTask;

        private PendingEvent(UUID id) {
            this.id = id;
        }
    }

    private static final class LayerBinding {
        private final String rangeLabel;
        private final int minTier;
        private final int maxTier;
        private final LayerConfig config;

        private LayerBinding(String rangeLabel, int minTier, int maxTier, LayerConfig config) {
            this.rangeLabel = rangeLabel;
            this.minTier = minTier;
            this.maxTier = maxTier;
            this.config = config;
        }

        private static LayerBinding load(String key, ConfigurationSection section) {
            TierRange range = TierRange.parse(key);
            if (range == null) {
                return null;
            }
            return new LayerBinding(range.label(), range.minTier(), range.maxTier(), LayerConfig.load(section));
        }

        private CandidateLocation pickCandidate(ThreadLocalRandom random) {
            return config == null ? null : config.pickCandidate(random);
        }
    }

    private record TierRange(int minTier, int maxTier, String label) {
        private static final Pattern SINGLE_TIER = Pattern.compile("^\\d+$");
        private static final Pattern RANGE_TIER = Pattern.compile("^(\\d+)\\s*-\\s*(\\d+)$");

        private static TierRange parse(String rawKey) {
            if (rawKey == null) {
                return null;
            }
            String key = rawKey.trim();
            Matcher single = SINGLE_TIER.matcher(key);
            if (single.matches()) {
                int tier = Integer.parseInt(key);
                return new TierRange(tier, tier, key);
            }

            Matcher range = RANGE_TIER.matcher(key);
            if (range.matches()) {
                int min = Integer.parseInt(range.group(1));
                int max = Integer.parseInt(range.group(2));
                if (min > max) {
                    int tmp = min;
                    min = max;
                    max = tmp;
                }
                return new TierRange(min, max, min == max ? String.valueOf(min) : min + "-" + max);
            }
            return null;
        }
    }

    private static final class ActivityRecord {
        private final double score;
        private final long lastUpdated;

        private ActivityRecord(double score, long lastUpdated) {
            this.score = score;
            this.lastUpdated = lastUpdated;
        }

        private double getScore(long now, long halfLifeTicks) {
            if (score <= 0) {
                return 0.0;
            }
            long ageMs = Math.max(0L, now - lastUpdated);
            double halfLifeMs = Math.max(1L, halfLifeTicks) * 50.0;
            double decay = Math.pow(0.5, ageMs / halfLifeMs);
            return score * decay;
        }
    }

    private static final class CandidateLocation {
        private final String world;
        private final double x;
        private final double y;
        private final double z;

        private CandidateLocation(String world, double x, double y, double z) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        private Location toLocation() {
            World bukkitWorld = Bukkit.getWorld(world);
            if (bukkitWorld == null) {
                return null;
            }
            return new Location(bukkitWorld, x, y, z);
        }

        @Override
        public String toString() {
            return world + "@" + x + "," + y + "," + z;
        }
    }

    private static final class LayerConfig {
        private final String name;
        private final LootChestTemplate commonTemplate;
        private final LootChestTemplate rareTemplate;
        private final List<CandidateLocation> candidates;

        private LayerConfig(String name, LootChestTemplate commonTemplate, LootChestTemplate rareTemplate, List<CandidateLocation> candidates) {
            this.name = name;
            this.commonTemplate = commonTemplate;
            this.rareTemplate = rareTemplate;
            this.candidates = candidates;
        }

        private CandidateLocation pickCandidate(ThreadLocalRandom random) {
            if (candidates.isEmpty()) {
                return null;
            }
            return candidates.get(random.nextInt(candidates.size()));
        }

        private static LayerConfig load(ConfigurationSection section) {
            String name = section.getString("name", "layer");
            LootChestTemplate common = loadTemplate(name + "_common", section.getConfigurationSection("common_template"));
            LootChestTemplate rare = loadTemplate(name + "_rare", section.getConfigurationSection("rare_template"));
            List<CandidateLocation> candidates = new ArrayList<>();
            List<Map<?, ?>> candidateList = (List<Map<?, ?>>) section.getMapList("candidate_locations");
            for (Map<?, ?> raw : candidateList) {
                String world = Objects.toString(raw.get("world"), null);
                if (world == null) {
                    continue;
                }
                double x = raw.get("x") instanceof Number numberX ? numberX.doubleValue() : 0;
                double y = raw.get("y") instanceof Number numberY ? numberY.doubleValue() : 0;
                double z = raw.get("z") instanceof Number numberZ ? numberZ.doubleValue() : 0;
                candidates.add(new CandidateLocation(world, x, y, z));
            }
            return new LayerConfig(name, common, rare, candidates);
        }

        private static LootChestTemplate loadTemplate(String fallbackName, ConfigurationSection section) {
            if (section == null) {
                return null;
            }
            return LootChestTemplate.loadFromConfig(fallbackName, section);
        }
    }

    private static final class EventChest {
        private final UUID id;
        private final int tier;
        private final String layerName;
        private final Location location;
        private final LayerConfig layerConfig;
        private BukkitTask expireTask;
        private long spawnedAt;
        private UUID claimOwner;
        private Set<UUID> claimSnapshot;
        private OpeningSession openingSession;

        private EventChest(UUID id, int tier, String layerName, Location location, LayerConfig layerConfig) {
            this.id = id;
            this.tier = tier;
            this.layerName = layerName;
            this.location = location;
            this.layerConfig = layerConfig;
        }
    }

    private static final class OpeningSession {
        private final UUID openerId;
        private int remainingSeconds;
        private BukkitTask task;

        private OpeningSession(UUID openerId, int remainingSeconds) {
            this.openerId = openerId;
            this.remainingSeconds = remainingSeconds;
        }
    }
}
