package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.api.DeepwitherPartyAPI;
import com.lunar_prototype.deepwither.booster.BoosterManager;
import com.lunar_prototype.deepwither.clan.ClanChatManager;
import com.lunar_prototype.deepwither.clan.ClanManager;
import com.lunar_prototype.deepwither.command.*;
import com.lunar_prototype.deepwither.companion.CompanionCommand;
import com.lunar_prototype.deepwither.companion.CompanionGuiListener;
import com.lunar_prototype.deepwither.companion.CompanionListener;
import com.lunar_prototype.deepwither.companion.CompanionManager;
import com.lunar_prototype.deepwither.crafting.CraftingGUI;
import com.lunar_prototype.deepwither.crafting.CraftingListener;
import com.lunar_prototype.deepwither.crafting.CraftingManager;
import com.lunar_prototype.deepwither.data.*;
import com.lunar_prototype.deepwither.dungeon.DungeonSignListener;
import com.lunar_prototype.deepwither.dungeon.instance.DungeonExtractionManager;
import com.lunar_prototype.deepwither.dungeon.instance.PvPvEDungeonManager;
import com.lunar_prototype.deepwither.dynamic_loot.LootDropManager;
import com.lunar_prototype.deepwither.dynamic_loot.LootLevelManager;
import com.lunar_prototype.deepwither.fishing.FishingListener;
import com.lunar_prototype.deepwither.fishing.FishingManager;
import com.lunar_prototype.deepwither.layer_move.BossKillListener;
import com.lunar_prototype.deepwither.layer_move.LayerMoveManager;
import com.lunar_prototype.deepwither.layer_move.LayerSignListener;
import com.lunar_prototype.deepwither.listeners.ArmorSetListener;
import com.lunar_prototype.deepwither.listeners.ItemGlowHandler;
import com.lunar_prototype.deepwither.listeners.ItemUpgradeListener;
import com.lunar_prototype.deepwither.listeners.PvPWorldListener;
import com.lunar_prototype.deepwither.loot.LootChestListener;
import com.lunar_prototype.deepwither.loot.LootChestManager;
import com.lunar_prototype.deepwither.market.GlobalMarketManager;
import com.lunar_prototype.deepwither.market.MarketGui;
import com.lunar_prototype.deepwither.market.MarketSearchHandler;
import com.lunar_prototype.deepwither.market.api.MarketApiController;
import com.lunar_prototype.deepwither.mythic.ManaShieldMechanic;
import com.lunar_prototype.deepwither.outpost.OutpostConfig;
import com.lunar_prototype.deepwither.outpost.OutpostDamageListener;
import com.lunar_prototype.deepwither.outpost.OutpostManager;
import com.lunar_prototype.deepwither.outpost.OutpostRegionListener;
import com.lunar_prototype.deepwither.party.PartyManager;
import com.lunar_prototype.deepwither.profession.ProfessionDatabase;
import com.lunar_prototype.deepwither.profession.ProfessionManager;
import com.lunar_prototype.deepwither.profiler.CombatAnalyzer;
import com.lunar_prototype.deepwither.aethelgard.*;
import com.lunar_prototype.deepwither.dungeon.roguelike.*;
import com.lunar_prototype.deepwither.raidboss.RaidBossListener;
import com.lunar_prototype.deepwither.raidboss.RaidBossManager;
import com.lunar_prototype.deepwither.seeker.CombatExperienceListener;
import com.lunar_prototype.deepwither.seeker.SeekerAIEngine;
import com.lunar_prototype.deepwither.textai.EMDALanguageAI;
import com.lunar_prototype.deepwither.town.TownBurstManager;
import com.lunar_prototype.deepwither.tutorial.TutorialController;
import com.lunar_prototype.deepwither.util.IManager;
import com.lunar_prototype.deepwither.util.MythicMobSafeZoneManager;
import com.lunar_prototype.deepwither.util.ServiceManager;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent;
import io.lumine.mythic.core.mobs.ActiveMob;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Deepwither extends JavaPlugin {

    private Logger log;

    private static Deepwither instance;
    private MarketApiController apiController;

    public static Deepwither getInstance() {
        return instance;
    }

    private Map<UUID, Location> safeZoneSpawns = new HashMap<>();
    private ServiceManager serviceManager;
    private File safeZoneSpawnsFile;
    private FileConfiguration safeZoneSpawnsConfig;
    private FileConfiguration questConfig;
    private LevelManager levelManager;
    private AttributeManager attributeManager;
    private SkilltreeManager skilltreeManager;
    private DatabaseManager databaseManager;
    private ManaManager manaManager;
    private SkillLoader skillLoader;
    private SkillSlotManager skillSlotManager;
    private SkillCastManager skillCastManager;
    private SkillAssignmentGUI skillAssignmentGUI;
    private CooldownManager cooldownManager;
    private ArtifactManager artifactManager;
    public ArtifactGUIListener artifactGUIListener;

    public EMDALanguageAI getAi() {
        return ai;
    }

    private EMDALanguageAI ai;
    private TraderManager traderManager;
    private CreditManager creditManager;
    public ArtifactGUI artifactGUI;
    public ItemFactory itemFactory;

    public TraderQuestManager getTraderQuestManager() {
        return traderQuestManager;
    }

    public DungeonExtractionManager getDungeonExtractionManager() {
        return dungeonExtractionManager;
    }

    private DungeonExtractionManager dungeonExtractionManager;

    private TraderQuestManager traderQuestManager;
    public StatManager statManager;
    private DailyTaskManager dailyTaskManager;
    private MobSpawnManager mobSpawnManager;
    private ItemNameResolver itemNameResolver;
    private MobKillListener mobKillListener;
    private QuestDataStore questDataStore;
    private GuildQuestManager guildQuestManager;
    private PlayerQuestManager playerQuestManager;
    private ExecutorService asyncExecutor;
    private FileDailyTaskDataStore fileDailyTaskDataStore;
    private LootChestManager lootChestManager;
    private TownBurstManager townBurstManager;
    private MythicMobSafeZoneManager mythicMobSafeZoneManager;

    public PvPvEDungeonManager getPvPvEDungeonManager() {
        return pvPvEDungeonManager;
    }

    private PvPvEDungeonManager pvPvEDungeonManager;
    private SkilltreeGUI skilltreeGUI;
    private CraftingManager craftingManager;
    private CraftingGUI craftingGUI;
    private ProfessionManager professionManager;
    private PartyManager partyManager;
    private DeepwitherPartyAPI partyAPI;
    private BoosterManager boosterManager;
    private ChargeManager chargeManager;
    private BackpackManager backpackManager;
    private DamageManager damageManager;
    private PlayerSettingsManager settingsManager;
    private ProfessionDatabase professionDatabase;
    private SettingsGUI settingsGUI;
    private CompanionManager companionManager;
    private PlayerQuestDataStore playerQuestDataStore;
    private FishingManager fishingManager;
    private RaidBossManager raidBossManager;
    private LayerMoveManager layerMoveManager;
    private GlobalMarketManager globalMarketManager;
    private MarketSearchHandler marketSearchHandler;
    private LootLevelManager lootLevelManager;
    private LootDropManager lootDropManager;
    private MarketGui marketGui;

    public SeekerAIEngine getAiEngine() {
        return aiEngine;
    }

    private SeekerAIEngine aiEngine;
    private ClanManager clanManager;
    private static Economy econ = null;
    private final java.util.Random random = new java.util.Random();
    private OutpostManager outpostManager;
    private RoguelikeBuffManager roguelikeBuffManager;
    private RoguelikeBuffGUI roguelikeBuffGUI;

    public AttributeManager getAttributeManager() {
        return attributeManager;
    }

    public LevelManager getLevelManager() {
        return levelManager;
    }

    public SkilltreeManager getSkilltreeManager() {
        return skilltreeManager;
    }

    public ManaManager getManaManager() {
        return manaManager;
    }

    public SkillLoader getSkillLoader() {
        return skillLoader;
    }

    public SkillSlotManager getSkillSlotManager() {
        return skillSlotManager;
    }

    public SkillCastManager getSkillCastManager() {
        return skillCastManager;
    }

    public SkillAssignmentGUI getSkillAssignmentGUI() {
        return skillAssignmentGUI;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    public ArtifactManager getArtifactManager() {
        return artifactManager;
    }

    public ArtifactGUIListener getArtifactGUIListener() {
        return artifactGUIListener;
    }

    public ArtifactGUI getArtifactGUI() {
        return artifactGUI;
    }

    public ItemFactory getItemFactory() {
        return itemFactory;
    }

    public StatManager getStatManager() {
        return statManager;
    }

    public TraderManager getTraderManager() {
        return traderManager;
    }

    public CreditManager getCreditManager() {
        return creditManager;
    }

    public DailyTaskManager getDailyTaskManager() { // ★ 新規追加
        return dailyTaskManager;
    }

    public MobSpawnManager getMobSpawnManager() {
        return mobSpawnManager;
    }

    public LootChestManager getLootChestManager() {
        return lootChestManager;
    }

    public ItemNameResolver getItemNameResolver() {
        return itemNameResolver;
    }

    public PlayerQuestManager getPlayerQuestManager() {
        return playerQuestManager;
    }

    public CraftingManager getCraftingManager() {
        return craftingManager;
    }

    public CraftingGUI getCraftingGUI() {
        return craftingGUI;
    }

    public ProfessionManager getProfessionManager() {
        return professionManager;
    }

    public ChargeManager getChargeManager() {
        return chargeManager;
    }

    public BackpackManager getBackpackManager() {
        return backpackManager;
    };

    public DamageManager getDamageManager() {
        return damageManager;
    }

    public PlayerSettingsManager getSettingsManager() {
        return settingsManager;
    }

    public SettingsGUI getSettingsGUI() {
        return settingsGUI;
    }

    public CompanionManager getCompanionManager() {
        return companionManager;
    }

    public LayerMoveManager getLayerMoveManager() {
        return layerMoveManager;
    }

    public FishingManager getFishingManager() {
        return fishingManager;
    }

    public MobKillListener getMobKillListener() {
        return mobKillListener;
    }

    public MarketSearchHandler getMarketSearchHandler() {
        return marketSearchHandler;
    }

    public SkilltreeGUI getSkilltreeGUI() {
        return skilltreeGUI;
    }

    public LootDropManager getLootDropManager() {
        return lootDropManager;
    }

    public LootLevelManager getLootLevelManager() {
        return lootLevelManager;
    }

    @Override
    public void onEnable() {
        // Plugin startup logic
        instance = this;
        ConfigurationSerialization.registerClass(RewardDetails.class);
        ConfigurationSerialization.registerClass(LocationDetails.class);
        ConfigurationSerialization.registerClass(GeneratedQuest.class);
        ConfigurationSerialization.registerClass(DailyTaskData.class);

        loadSafeZoneSpawns();

        if (!setupEconomy()) {
            getLogger().severe(
                    String.format("[%s] - Disabled due to no Vault dependency found!", getDescription().getName()));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.asyncExecutor = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors());

        this.serviceManager = new ServiceManager(this);

        try {
            // 1. 基盤の登録
            this.databaseManager = register(new DatabaseManager(this));

            // 2. マネージャーの登録とインスタンス化
            setupManagers();

            // 3. 依存関係を解決して一括初期化
            this.serviceManager.startAll();

        } catch (Exception e) {
            getLogger().severe("Service initialization failed!");
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        aiEngine = new SeekerAIEngine();
        Bukkit.getPluginManager().registerEvents(new CombatExperienceListener(aiEngine), this);

        this.apiController = new MarketApiController(this, this.globalMarketManager);
        this.apiController.start(9093); // ポートは任意

        getServer().getPluginManager().registerEvents(new ArmorSetListener(itemFactory), this);
        getServer().getPluginManager().registerEvents(new ItemUpgradeListener(itemFactory),this);
        getServer().getPluginManager().registerEvents(new PlayerStatListener(statManager), this);
        this.settingsGUI = new SettingsGUI(this, settingsManager);
        Bukkit.getPluginManager().registerEvents(new SkillCastSessionManager(), this);
        getServer().getPluginManager().registerEvents(new RaidBossListener(this, raidBossManager), this);

        // クエスト設定のロード
        loadGuildQuestConfig();

        // クエストコンポーネントの初期化
        if (questConfig != null) {
            ConfigurationSection questComponents = questConfig.getConfigurationSection("quest_components");
            if (questComponents != null) {
                QuestComponentPool.loadComponents(questComponents);
            } else {
                getLogger().severe("guild_quest_config.yml に 'quest_components' セクションが見つかりません！");
            }
        }

        this.partyAPI = new DeepwitherPartyAPI(partyManager); // ★ 初期化
        getServer().getPluginManager().registerEvents(new CraftingListener(this), this);
        artifactGUIListener = new ArtifactGUIListener(artifactGUI, statManager);
        getServer().getPluginManager().registerEvents(skillAssignmentGUI, this);
        getServer().getPluginManager().registerEvents(artifactGUIListener, this);
        getServer().getPluginManager().registerEvents(artifactGUI, this);
        getServer().getPluginManager().registerEvents(new CustomDropListener(this), this);
        getServer().getPluginManager().registerEvents(new TaskListener(dailyTaskManager), this);
        getServer().getPluginManager().registerEvents(new LootChestListener(this, lootChestManager), this);
        getServer().getPluginManager().registerEvents(new CompanionListener(companionManager), this);
        getServer().getPluginManager().registerEvents(new CompanionGuiListener(companionManager), this);
        getServer().getPluginManager().registerEvents(new LayerSignListener(), this);
        getServer().getPluginManager().registerEvents(new BossKillListener(), this);
        this.getServer().getPluginManager().registerEvents(new CombatAnalyzer(this.companionManager, this), this);

//        CommandRegistrar.registerCommands(this.getLifecycleManager(), List.of()); // まだ使わないよ
        this.getCommand("artifact").setExecutor(new ArtifactGUICommand(artifactGUI));
        getCommand("trader").setExecutor(new TraderCommand(traderManager));
        getCommand("credit").setExecutor(new CreditCommand(creditManager));
        getCommand("companion").setExecutor(new CompanionCommand(companionManager));
        getServer().getPluginManager().registerEvents(new TraderGUI(), this);
        getServer().getPluginManager().registerEvents(new SellGUI(), this);
        getServer().getPluginManager().registerEvents(new TutorialController(this), this);

        marketGui = new MarketGui(globalMarketManager);
        getServer().getPluginManager().registerEvents(marketGui, this);
        marketSearchHandler = new MarketSearchHandler(this, marketGui);
        getServer().getPluginManager().registerEvents(marketSearchHandler, this);

        new RegenTask(statManager).start(this);

        saveDefaultConfig(); // MobExpConfig.yml

        mobKillListener = new MobKillListener(levelManager, getConfig(), OutpostManager.getInstance(), partyManager,
                boosterManager);
        Bukkit.getPluginManager().registerEvents(mobKillListener, this);
        getServer().getPluginManager().registerEvents(new SafeZoneListener(this), this);
        getServer().getPluginManager().registerEvents(new AnimationListener(), this);
        getServer().getPluginManager().registerEvents(new BackpackListener(this, backpackManager), this);
        this.getCommand("status")
                .setExecutor(new StatusCommand(levelManager, statManager, creditManager, professionManager));
        getServer().getPluginManager().registerEvents(new OutpostRegionListener(OutpostManager.getInstance()), this);
        getServer().getPluginManager().registerEvents(new OutpostDamageListener(OutpostManager.getInstance()), this);

        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onMythicMechanicLoad(MythicMechanicLoadEvent event) {
                getLogger().info("MythicMechanicLoadEvent called for mechanic " + event.getMechanicName());

                if (event.getMechanicName().equalsIgnoreCase("CustomDamage")) {
                    event.register(new CustomDamageMechanics(event.getConfig()));
                    getLogger().info("-- Registered CustomDamage mechanic!");
                }

                if (event.getMechanicName().equalsIgnoreCase("CustomHPDamage")) {
                    event.register(new CustomHPDamageMechanic(event.getConfig()));
                    getLogger().info("-- Registered CustomHPDamage mechanic!");
                }

                if (event.getMechanicName().equalsIgnoreCase("manaShield")) {
                    event.register(new ManaShieldMechanic(event.getConfig()));
                    getLogger().info("-- Registered manaShield mechanic!");
                }
            }
        }, this);

        // ログイン・ログアウト同期
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onJoin(PlayerJoinEvent e) {
                levelManager.load(e.getPlayer().getUniqueId());
                attributeManager.load(e.getPlayer().getUniqueId());
                skilltreeManager.load(e.getPlayer().getUniqueId());
                dailyTaskManager.loadPlayer(e.getPlayer());
                craftingManager.loadPlayer(e.getPlayer());
                professionManager.loadPlayer(e.getPlayer());
            }

            @EventHandler
            public void onQuit(PlayerQuitEvent e) {
                levelManager.unload(e.getPlayer().getUniqueId());
                attributeManager.unload(e.getPlayer().getUniqueId());
                dailyTaskManager.saveAndUnloadPlayer(e.getPlayer().getUniqueId());
                craftingManager.saveAndUnloadPlayer(e.getPlayer().getUniqueId());
                professionManager.saveAndUnloadPlayer(e.getPlayer());
            }
        }, this);

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                ManaData mana = Deepwither.getInstance().getManaManager().get(p.getUniqueId());
                double regenAmount = mana.getMaxMana() * 0.01; // 1%
                mana.regen(regenAmount);
            }
        }, 20L, 20L); // 毎秒実行

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                AttributeInstance attr = p.getAttribute(Attribute.ATTACK_SPEED);
                if (attr == null)
                    return;
                NamespacedKey baseAttackSpeed = NamespacedKey.minecraft("base_attack_speed");
                attr.removeModifier(baseAttackSpeed);
            }
        }, 1L, 1L); // 毎秒実行

        this.mobSpawnManager = new MobSpawnManager(this, playerQuestManager);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new LevelPlaceholderExpansion(levelManager, manaManager, statManager).register();
            getLogger().info("PlaceholderAPI拡張を登録しました。");
        }
        // コマンド登録
        getCommand("attributes").setExecutor(new AttributeCommand());
        try {
            skilltreeGUI = new SkilltreeGUI(this, getDataFolder(), skilltreeManager, skillLoader);
            getCommand("skilltree").setExecutor(skilltreeGUI);
        } catch (IOException e) {
            e.printStackTrace();
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                long currentTick = Bukkit.getCurrentTick();
                for (ActiveMob am : MythicBukkit.inst().getMobManager().getActiveMobs()) {
                    if (am.getMobType().contains("bandit")) {
                        // 個体ごとに異なるタイミング(40tick周期)で思考させる
                        if ((am.getUniqueId().hashCode() + currentTick) % 20 == 0) {
                            aiEngine.tick(am);
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0, 1);

        // リスナー登録
        getServer().getPluginManager().registerEvents(new ItemDurabilityFix(), this);
        getServer().getPluginManager().registerEvents(new AttributeGui(), this);
        getServer().getPluginManager().registerEvents(new BlacksmithListener(), this);
        getServer().getPluginManager().registerEvents(new DropPreventionListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerInteractListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this, playerQuestManager), this);
        Bukkit.getPluginManager().registerEvents(new GUIListener(playerQuestManager), this);
        getServer().getPluginManager().registerEvents(new CustomOreListener(this), this);
        getServer().getPluginManager().registerEvents(new WandManager(), this);

        // リスナーの登録
        getServer().getPluginManager().registerEvents(new FishingListener(this), this);

        MenuGUI menuGUI = new MenuGUI(this);
        getCommand("menu").setExecutor(new MenuCommand(menuGUI));
        getServer().getPluginManager().registerEvents(new MenuItemListener(this, menuGUI), this);
        getServer().getPluginManager().registerEvents(new PlayerInventoryRestrictor(settingsManager), this);

        getCommand("skills").setExecutor(new SkillAssignmentCommand());
        getCommand("blacksmith").setExecutor(new BlacksmithCommand());
        getCommand("questnpc").setExecutor(new QuestCommand(this, guildQuestManager));
        getCommand("task").setExecutor(new TaskCommand(this));
        PartyCommand partyCommand = new PartyCommand(partyManager);
        getCommand("party").setExecutor(partyCommand);
        getCommand("party").setTabCompleter(partyCommand);
        getCommand("expbooster").setExecutor(new BoosterCommand(boosterManager));
        ResetGUI resetGUI = new ResetGUI(this);
        getCommand("resetstatusgui").setExecutor(new ResetGUICommand(resetGUI));
        getCommand("pvp").setExecutor(new PvPCommand());
        MarketCommand marketCmd = new MarketCommand(this, globalMarketManager, marketGui);
        getCommand("market").setExecutor(marketCmd);
        getCommand("market").setTabCompleter(marketCmd);
        ClanCommand clanCommand = new ClanCommand(clanManager);
        getCommand("clan").setExecutor(clanCommand);
        getCommand("clan").setTabCompleter(clanCommand);
        getCommand("deepwither").setExecutor(new DeepwitherCommand(this));
        getServer().getPluginManager().registerEvents(new PvPWorldListener(), this);
        getServer().getPluginManager().registerEvents(new ItemGlowHandler(this), this);
        getServer().getPluginManager().registerEvents(new DungeonSignListener(), this);
        getServer().getPluginManager().registerEvents(new ClanChatManager(clanManager), this);
        getServer().getPluginManager().registerEvents(traderQuestManager, this);

        // Roguelike
        this.roguelikeBuffGUI = new RoguelikeBuffGUI(this);
        getServer().getPluginManager().registerEvents(new PvPvEChestListener(this), this);
    }

    @Override
    public void onDisable() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            levelManager.unload(p.getUniqueId());
            attributeManager.unload(p.getUniqueId());
        }
        
        saveSafeZoneSpawns();

        if (serviceManager != null) {
            serviceManager.stopAll();
        }

        shutdownExecutor();
    }

    private void setupManagers() {
        // --- Group A & B & Base ---
        this.attributeManager = register(new AttributeManager(databaseManager));
        this.levelManager = register(new LevelManager(databaseManager));
        this.skilltreeManager = register(new SkilltreeManager(databaseManager, this));
        this.professionDatabase = register(new ProfessionDatabase(this, databaseManager));
        this.boosterManager = register(new BoosterManager(databaseManager));
        this.globalMarketManager = register(new GlobalMarketManager(this, databaseManager));
        this.clanManager = register(new ClanManager(databaseManager));
        this.traderQuestManager = register(new TraderQuestManager(this, databaseManager));
        
        this.statManager = register(new StatManager());
        this.manaManager = register(new ManaManager());
        this.cooldownManager = register(new CooldownManager());
        this.itemFactory = register(new ItemFactory(this));
        this.itemNameResolver = register(new ItemNameResolver(this));
        
        this.skillLoader = register(new SkillLoader(this));
        this.skillSlotManager = register(new SkillSlotManager(this));
        this.skillCastManager = register(new SkillCastManager());
        this.chargeManager = register(new ChargeManager(this));
        this.settingsManager = register(new PlayerSettingsManager(this));
        this.damageManager = register(new DamageManager(this, statManager, settingsManager));

        // --- Group C & D ---
        this.artifactManager = register(new ArtifactManager(this));
        this.backpackManager = register(new BackpackManager(this));
        this.creditManager = register(new CreditManager(this));
        this.traderManager = register(new TraderManager(this, itemFactory));
        this.lootChestManager = register(new LootChestManager(this));
        this.lootLevelManager = register(new LootLevelManager(this));
        this.lootDropManager = register(new LootDropManager(itemFactory));
        this.craftingManager = register(new CraftingManager(this));

        this.companionManager = register(new CompanionManager(this));
        this.raidBossManager = register(new RaidBossManager(this));
        this.layerMoveManager = register(new LayerMoveManager(this));
        this.pvPvEDungeonManager = register(new PvPvEDungeonManager(this));
        
        register(new com.lunar_prototype.deepwither.dungeon.instance.DungeonInstanceManager(this));
        this.dungeonExtractionManager = register(new DungeonExtractionManager(this));
        this.fishingManager = register(new FishingManager(this));
        this.townBurstManager = register(new TownBurstManager(this));
        this.mythicMobSafeZoneManager = register(new MythicMobSafeZoneManager(this));
        this.partyManager = register(new PartyManager(this));
        this.roguelikeBuffManager = register(new RoguelikeBuffManager(this));

        // --- Group E ---
        this.fileDailyTaskDataStore = register(new FileDailyTaskDataStore(this, databaseManager));
        this.dailyTaskManager = register(new DailyTaskManager(this, fileDailyTaskDataStore));
        this.questDataStore = register(new QuestDataStore(this));
        this.guildQuestManager = register(new GuildQuestManager(this, questDataStore));
        this.playerQuestDataStore = (PlayerQuestDataStore) register(new FilePlayerQuestDataStore(databaseManager));
        this.playerQuestManager = register(new PlayerQuestManager(this, guildQuestManager, playerQuestDataStore));
        this.professionManager = register(new ProfessionManager(this, professionDatabase));
        this.ai = register(new EMDALanguageAI(this));
        this.outpostManager = register(new OutpostManager(this));
    }


    private <T extends IManager> T register(T manager) {
        serviceManager.register(manager);
        return manager;
    }

    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    public static Economy getEconomy() {
        return econ;
    }

    public java.util.Random getRandom() {
        return random;
    }

    public DeepwitherPartyAPI getPartyAPI() {
        return partyAPI;
    }

    private void shutdownExecutor() {
        this.asyncExecutor.shutdown();
        try {
            // 処理中のタスクが終わるのを最大60秒待つ
            if (!this.asyncExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                this.asyncExecutor.shutdownNow(); // タイムアウトした場合、強制終了
            }
        } catch (InterruptedException e) {
            this.asyncExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // 3. ゲッターの追加
    public ExecutorService getAsyncExecutor() {
        return asyncExecutor;
    }

    public RoguelikeBuffManager getRoguelikeBuffManager() {
        return roguelikeBuffManager;
    }

    public RoguelikeBuffGUI getRoguelikeBuffGUI() {
        return roguelikeBuffGUI;
    }

    // --- データ永続化のメソッド ---

    // リスポーン地点を取得
    public Location getSafeZoneSpawn(UUID playerUUID) {
        return safeZoneSpawns.get(playerUUID);
    }

    // リスポーン地点を設定
    public void setSafeZoneSpawn(UUID playerUUID, Location location) {
        safeZoneSpawns.put(playerUUID, location);
    }

    // リスポーン地点データをファイルから読み込む
    private void loadSafeZoneSpawns() {
        safeZoneSpawnsFile = new File(getDataFolder(), "safeZoneSpawns.yml");
        if (!safeZoneSpawnsFile.exists()) {
            safeZoneSpawnsFile.getParentFile().mkdirs();
            try {
                safeZoneSpawnsFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        safeZoneSpawnsConfig = YamlConfiguration.loadConfiguration(safeZoneSpawnsFile);

        // 設定ファイルからデータをMapに読み込む
        for (String key : safeZoneSpawnsConfig.getKeys(false)) {
            UUID uuid = UUID.fromString(key);
            Location loc = safeZoneSpawnsConfig.getLocation(key);
            if (loc != null) {
                safeZoneSpawns.put(uuid, loc);
            }
        }
    }

    // リスポーン地点データをファイルに保存する
    public void saveSafeZoneSpawns() {
        // Mapのデータを設定ファイルに書き込む
        for (Map.Entry<UUID, Location> entry : safeZoneSpawns.entrySet()) {
            safeZoneSpawnsConfig.set(entry.getKey().toString(), entry.getValue());
        }

        try {
            safeZoneSpawnsConfig.save(safeZoneSpawnsFile);
        } catch (IOException e) {
            getLogger().severe("Could not save safeZoneSpawns.yml!");
            e.printStackTrace();
        }
    }

    /**
     * guild_quest_config.ymlをデータフォルダからロードし、存在しない場合はリソースからコピーします。
     */
    private void loadGuildQuestConfig() {
        File configFile = new File(getDataFolder(), "guild_quest_config.yml");

        if (!configFile.exists()) {
            // ファイルが存在しない場合、リソースからコピーする
            getLogger().info("guild_quest_config.yml が見つかりませんでした。リソースからコピーします。");
            saveResource("guild_quest_config.yml", false);
        }

        // ファイルから設定をロード
        try {
            questConfig = YamlConfiguration.loadConfiguration(configFile);
            getLogger().info("guild_quest_config.yml を正常にロードしました。");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "guild_quest_config.yml のロード中に致命的なエラーが発生しました。", e);
            questConfig = null;
        }
    }
}
