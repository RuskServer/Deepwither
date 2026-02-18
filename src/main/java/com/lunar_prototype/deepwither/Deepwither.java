package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.api.DeepwitherPartyAPI;
import com.lunar_prototype.deepwither.api.IItemFactory;
import com.lunar_prototype.deepwither.booster.BoosterManager;
import com.lunar_prototype.deepwither.clan.ClanChatManager;
import com.lunar_prototype.deepwither.clan.ClanManager;
import com.lunar_prototype.deepwither.command.*;
import com.lunar_prototype.deepwither.commands.DebugCommand;
import com.lunar_prototype.deepwither.companion.CompanionCommand;
import com.lunar_prototype.deepwither.companion.CompanionGuiListener;
import com.lunar_prototype.deepwither.companion.CompanionListener;
import com.lunar_prototype.deepwither.companion.CompanionManager;
import com.lunar_prototype.deepwither.core.CacheManager;
import com.lunar_prototype.deepwither.core.damage.DamageProcessor;
import com.lunar_prototype.deepwither.core.listener.MythicMechanicListener;
import com.lunar_prototype.deepwither.core.listener.PlayerConnectionListener;
import com.lunar_prototype.deepwither.core.playerdata.PlayerDataManager;
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
import com.lunar_prototype.deepwither.mythic.CustomDropListener;
import com.lunar_prototype.deepwither.outpost.OutpostDamageListener;
import com.lunar_prototype.deepwither.outpost.OutpostManager;
import com.lunar_prototype.deepwither.outpost.OutpostRegionListener;
import com.lunar_prototype.deepwither.party.PartyManager;
import com.lunar_prototype.deepwither.profession.ProfessionDatabase;
import com.lunar_prototype.deepwither.profession.ProfessionManager;
import com.lunar_prototype.deepwither.profiler.CombatAnalyzer;
import com.lunar_prototype.deepwither.aethelgard.*;
import com.lunar_prototype.deepwither.api.DeepwitherAPI;
import com.lunar_prototype.deepwither.api.database.IDatabaseManager;
import com.lunar_prototype.deepwither.api.stat.IStatManager;
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
import com.lunar_prototype.deepwither.core.engine.DeepwitherBootstrap;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
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

public final class Deepwither extends JavaPlugin implements DeepwitherAPI {

    private static Deepwither instance;

    public static Deepwither getInstance() {
        return instance;
    }

    @Override
    public <T> T get(Class<T> clazz) {
        return serviceManager.get(clazz);
    }

    @Override
    @Deprecated
    public IStatManager getStatManager() {
        return (IStatManager) serviceManager.get(StatManager.class);
    }

    @Override
    @Deprecated
    public IDatabaseManager getDatabaseManager() {
        return (IDatabaseManager) serviceManager.get(DatabaseManager.class);
    }

    @Deprecated
    public IItemFactory getItemFactoryAPI() {
        return (IItemFactory) serviceManager.get(ItemFactory.class);
    }

    private ServiceManager serviceManager;
    private DeepwitherBootstrap bootstrap; // [NEW] Bootstrap

    // [NEW] LegacyModuleから注入される
    public void setServiceManager(ServiceManager serviceManager) {
        this.serviceManager = serviceManager;
    }

    // [NEW] Bootstrap取得用
    public DeepwitherBootstrap getBootstrap() {
        return bootstrap;
    }

    private FileConfiguration questConfig;
    private CacheManager cacheManager;
    private PlayerDataManager playerDataManager;
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

    public SellGUI getSellGUI() {
        return sellGUI;
    }

    private SellGUI sellGUI;
    public ArtifactGUIListener artifactGUIListener;

    public TraderGUI getTraderGUI() {
        return traderGUI;
    }

    public TraderGUI traderGUI;

    public EMDALanguageAI getAi() {
        return ai;
    }

    private EMDALanguageAI ai;
    private TraderManager traderManager;
    private CreditManager creditManager;
    public ArtifactGUI artifactGUI;
    public ItemFactory itemFactory;

    @Deprecated
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
    private MenuGUI menuGUI;
    private ResetGUI resetGUI;
    private MenuItemListener menuItemListener;
    private SafeZoneListener safeZoneListener;
    private CraftingManager craftingManager;

    private CraftingGUI craftingGUI;
    private ProfessionManager professionManager;
    private PartyManager partyManager;
    private DeepwitherPartyAPI partyAPI;

    public BoosterManager getBoosterManager() {
        return boosterManager;
    }

    public PartyManager getPartyManager() {
        return partyManager;
    }

    private BoosterManager boosterManager;
    private ChargeManager chargeManager;
    private BackpackManager backpackManager;
    private DamageManager damageManager;
    private DamageProcessor damageProcessor;
    private WeaponMechanicManager weaponMechanicManager;
    private PlayerSettingsManager settingsManager;
    private ProfessionDatabase professionDatabase;
    private SettingsGUI settingsGUI;
    private CompanionManager companionManager;
    private PlayerQuestDataStore playerQuestDataStore;
    private FishingManager fishingManager;
    private RaidBossManager raidBossManager;
    private LayerMoveManager layerMoveManager;

    public GlobalMarketManager getGlobalMarketManager() {
        return globalMarketManager;
    }

    private GlobalMarketManager globalMarketManager;
    private MarketSearchHandler marketSearchHandler;
    private LootLevelManager lootLevelManager;
    private LootDropManager lootDropManager;

    public MarketGui getMarketGui() {
        return marketGui;
    }

    private MarketGui marketGui;

    public SeekerAIEngine getAiEngine() {
        return aiEngine;
    }

    private SeekerAIEngine aiEngine;

    public RaidBossManager getRaidBossManager() {
        return raidBossManager;
    }

    public ClanManager getClanManager() {
        return clanManager;
    }

    private ClanManager clanManager;

    private static Economy econ = null;
    private final java.util.Random random = new java.util.Random();
    private OutpostManager outpostManager;
    private RoguelikeBuffManager roguelikeBuffManager;
    private RoguelikeBuffGUI roguelikeBuffGUI;

    public MenuGUI getMenuGUI() {
        return menuGUI;
    }

    public ResetGUI getResetGUI() {
        return resetGUI;
    }

    public MenuItemListener getMenuItemListener() {
        return menuItemListener;
    }

    public SafeZoneListener getSafeZoneListener() {
        return safeZoneListener;
    }

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

    public CacheManager getCacheManager() {
        return cacheManager;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public ArtifactGUIListener getArtifactGUIListener() {
        return artifactGUIListener;
    }

    public ArtifactGUI getArtifactGUI() {
        return artifactGUI;
    }

    @Deprecated
    public ItemFactory getItemFactory() {
        return itemFactory;
    }

    @Deprecated
    public TraderManager getTraderManager() {
        return traderManager;
    }

    @Deprecated
    public CreditManager getCreditManager() {
        return creditManager;
    }

    @Deprecated
    public DailyTaskManager getDailyTaskManager() { // ★ 新規追加
        return dailyTaskManager;
    }

    @Deprecated
    public MobSpawnManager getMobSpawnManager() {
        return mobSpawnManager;
    }

    @Deprecated
    public LootChestManager getLootChestManager() {
        return lootChestManager;
    }

    @Deprecated
    public ItemNameResolver getItemNameResolver() {
        return itemNameResolver;
    }

    @Deprecated
    public PlayerQuestManager getPlayerQuestManager() {
        return playerQuestManager;
    }

    @Deprecated
    public CraftingManager getCraftingManager() {
        return craftingManager;
    }

    @Deprecated
    public CraftingGUI getCraftingGUI() {
        return craftingGUI;
    }

    @Deprecated
    public ProfessionManager getProfessionManager() {
        return professionManager;
    }

    @Deprecated
    public ChargeManager getChargeManager() {
        return chargeManager;
    }

    @Deprecated
    public BackpackManager getBackpackManager() {
        return backpackManager;
    };

    @Deprecated
    public DamageManager getDamageManager() {
        return damageManager;
    }

    @Deprecated
    public DamageProcessor getDamageProcessor() {
        return damageProcessor;
    }

    @Deprecated
    public WeaponMechanicManager getWeaponMechanicManager() {
        return weaponMechanicManager;
    }

    @Deprecated
    public PlayerSettingsManager getSettingsManager() {
        return settingsManager;
    }

    @Deprecated
    public SettingsGUI getSettingsGUI() {
        return settingsGUI;
    }

    @Deprecated
    public CompanionManager getCompanionManager() {
        return companionManager;
    }

    @Deprecated
    public LayerMoveManager getLayerMoveManager() {
        return layerMoveManager;
    }

    @Deprecated
    public FishingManager getFishingManager() {
        return fishingManager;
    }

    @Deprecated
    public MobKillListener getMobKillListener() {
        return mobKillListener;
    }

    @Deprecated
    public MarketSearchHandler getMarketSearchHandler() {
        return marketSearchHandler;
    }

    @Deprecated
    public SkilltreeGUI getSkilltreeGUI() {
        return skilltreeGUI;
    }

    @Deprecated
    public LootDropManager getLootDropManager() {
        return lootDropManager;
    }

    @Deprecated
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
        ConfigurationSerialization.registerClass(com.lunar_prototype.deepwither.modules.dynamic_quest.obj.QuestLocation.class, "com.lunar_prototype.deepwither.modules.dynamic_quest.obj.QuestLocation");
        ConfigurationSerialization.registerClass(com.lunar_prototype.deepwither.modules.dynamic_quest.obj.QuestLocation.class, "com.lunar_prototype.deepwither.dynamic_quest.obj.QuestLocation");
        ConfigurationSerialization.registerClass(com.lunar_prototype.deepwither.modules.dynamic_quest.obj.QuestLocation.class, "QuestLocation");

        // loadSafeZoneSpawns(); // Moved to SafeZoneListener

        if (!setupEconomy()) {
            getLogger().severe(
                    String.format("[%s] - Disabled due to no Vault dependency found!", getDescription().getName()));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.asyncExecutor = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors());

        // [MODIFY] Bootstrapによる初期化へ移行
        // this.serviceManager = new ServiceManager(this);
        com.lunar_prototype.deepwither.api.DW._setApi(this);

        try {
            // Bootstrapの初期化 (ここで LegacyModule -> setupManagers -> serviceManager.startAll
            // が走る)
            this.bootstrap = new DeepwitherBootstrap(this);
            this.bootstrap.onEnable();

        } catch (Exception e) {
            getLogger().severe("Service initialization failed!");
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

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
        this.getCommand("artifact").setExecutor(new ArtifactGUICommand(artifactGUI));
        getCommand("trader").setExecutor(new TraderCommand(traderManager));
        getCommand("credit").setExecutor(new CreditCommand(creditManager));
        getCommand("companion").setExecutor(new CompanionCommand(companionManager));

        saveDefaultConfig(); // MobExpConfig.yml

        this.getCommand("status")
                .setExecutor(new StatusCommand(levelManager, statManager, creditManager, professionManager));

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

        getCommand("menu").setExecutor(new MenuCommand(menuGUI));
        getCommand("skills").setExecutor(new SkillAssignmentCommand());
        getCommand("blacksmith").setExecutor(new BlacksmithCommand());
        getCommand("questnpc").setExecutor(new QuestCommand(this, guildQuestManager));
        getCommand("task").setExecutor(new TaskCommand(this));
        PartyCommand partyCommand = new PartyCommand(partyManager);
        getCommand("party").setExecutor(partyCommand);
        getCommand("party").setTabCompleter(partyCommand);
        getCommand("expbooster").setExecutor(new BoosterCommand(boosterManager));
        getCommand("resetstatusgui").setExecutor(new ResetGUICommand(resetGUI));
        getCommand("pvp").setExecutor(new PvPCommand());
        MarketCommand marketCmd = new MarketCommand(this, globalMarketManager, marketGui);
        getCommand("market").setExecutor(marketCmd);
        getCommand("market").setTabCompleter(marketCmd);
        ClanCommand clanCommand = new ClanCommand(clanManager);
        getCommand("clan").setExecutor(clanCommand);
        getCommand("clan").setTabCompleter(clanCommand);
        getCommand("deepwither").setExecutor(new DeepwitherCommand(this));

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(new DebugCommand().node());
        });
    }

    @Override
    public void onDisable() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            levelManager.unload(p.getUniqueId());
            attributeManager.unload(p.getUniqueId());
        }

        // saveSafeZoneSpawns(); // Managed by SafeZoneListener shutdown

        // [MODIFY] Bootstrapへ委譲
        if (bootstrap != null) {
            bootstrap.onDisable();
        } else if (serviceManager != null) {
            // Fallback
            serviceManager.stopAll();
        }

        shutdownExecutor();
    }

    // [MODIFY] LegacyModuleから呼ばれるため public に変更
    public void setupManagers() {
        // [MOVED] DatabaseManager, CacheManager, PlayerDataManager are now in Modules
        // Retrieve them from ServiceManager (which delegates to Container)
        this.databaseManager = serviceManager.get(DatabaseManager.class);
        this.playerDataManager = serviceManager.get(PlayerDataManager.class);

        // [MOVED (Core Module)]
        this.cacheManager = serviceManager.get(CacheManager.class); // Explicit fallback check if needed but CoreModule
                                                                    // is registered early
        this.statManager = serviceManager.get(StatManager.class);
        this.itemFactory = serviceManager.get(ItemFactory.class);
        this.settingsManager = serviceManager.get(PlayerSettingsManager.class);
        this.chargeManager = serviceManager.get(ChargeManager.class);
        this.cooldownManager = serviceManager.get(CooldownManager.class);

        // --- Core ---

        // --- Group A & B & Base ---
        this.attributeManager = register(new AttributeManager(databaseManager));
        this.levelManager = register(new LevelManager(databaseManager));
        this.skilltreeManager = register(new SkilltreeManager(databaseManager, this));
        this.professionDatabase = register(new ProfessionDatabase(this, databaseManager));
        this.boosterManager = register(new BoosterManager(databaseManager));
        this.globalMarketManager = serviceManager.get(GlobalMarketManager.class);
        this.clanManager = register(new ClanManager(databaseManager));
        this.traderQuestManager = serviceManager.get(TraderQuestManager.class);

        // this.statManager = ... handled above
        this.manaManager = register(new ManaManager());
        // this.cooldownManager = ... handled above
        // this.itemFactory = ... handled above
        this.itemNameResolver = register(new ItemNameResolver(this));

        this.skillLoader = register(new SkillLoader(this));
        this.skillSlotManager = register(new SkillSlotManager(this));
        this.skillCastManager = register(new SkillCastManager());
        // this.chargeManager = ... handled above
        // this.settingsManager = ... handled above

        this.damageProcessor = serviceManager.get(DamageProcessor.class);
        this.weaponMechanicManager = serviceManager.get(WeaponMechanicManager.class);
        this.damageManager = serviceManager.get(DamageManager.class);

        // --- Group C & D ---
        this.artifactManager = register(new ArtifactManager(this));
        this.backpackManager = register(new BackpackManager(this));
        this.creditManager = serviceManager.get(CreditManager.class);
        this.traderManager = serviceManager.get(TraderManager.class);
        this.lootChestManager = register(new LootChestManager(this));
        this.lootLevelManager = register(new LootLevelManager(this));
        this.lootDropManager = register(new LootDropManager(itemFactory));
        this.craftingManager = register(new CraftingManager(this));
        this.marketGui = register(new MarketGui(this));
        this.marketSearchHandler = register(new MarketSearchHandler(this));

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
        this.roguelikeBuffGUI = register(new RoguelikeBuffGUI(this));

        // --- Group E ---
        this.fileDailyTaskDataStore = serviceManager.get(FileDailyTaskDataStore.class); // Explicit cast if generic
                                                                                        // needed
        this.dailyTaskManager = serviceManager.get(DailyTaskManager.class);
        this.questDataStore = serviceManager.get(QuestDataStore.class);
        this.guildQuestManager = serviceManager.get(GuildQuestManager.class);
        this.playerQuestDataStore = serviceManager.get(PlayerQuestDataStore.class); // cast?
        this.playerQuestManager = serviceManager.get(PlayerQuestManager.class);
        this.professionManager = register(new ProfessionManager(this, professionDatabase));
        this.ai = register(new EMDALanguageAI(this));
        this.aiEngine = register(new SeekerAIEngine());
        this.outpostManager = register(new OutpostManager(this));

        // --- UI & Listeners (Managed) ---
        this.artifactGUI = register(new ArtifactGUI());
        this.artifactGUIListener = register(new ArtifactGUIListener(this));
        this.skillAssignmentGUI = register(new SkillAssignmentGUI(this));
        this.settingsGUI = register(new SettingsGUI(this, settingsManager));
        this.menuGUI = register(new MenuGUI(this));
        this.resetGUI = register(new ResetGUI(this));
        this.menuItemListener = register(new MenuItemListener(this));
        this.craftingGUI = register(new CraftingGUI(this));
        this.traderGUI = register(new TraderGUI(this));
        this.sellGUI = register(new SellGUI(this));

        // --- Standalone Listeners (Managed) ---
        register(new PlayerConnectionListener(this));
        register(new MythicMechanicListener(this));
        register(new ArmorSetListener(this));
        register(new ItemUpgradeListener(this));
        register(new PlayerStatListener(this));
        register(new SkillCastSessionManager(this));
        register(new RaidBossListener(this));
        register(new CraftingListener(this));
        register(new CustomDropListener(this));
        register(new TaskListener(this));
        register(new LootChestListener(this));
        register(new CompanionListener(this));
        register(new CompanionGuiListener(companionManager));
        register(new LayerSignListener(this));
        register(new BossKillListener(this));
        register(new PvPWorldListener(this));
        register(new ItemGlowHandler(this));
        register(new DungeonSignListener(this));
        register(new ClanChatManager(this));
        register(new PvPvEChestListener(this));
        register(new OutpostRegionListener(this));
        register(new OutpostDamageListener(this));
        register(new ItemDurabilityFix(this));
        register(new AttributeGui(this));
        register(new BlacksmithListener(this));
        register(new DropPreventionListener(this));
        register(new PlayerInteractListener(this));
        register(new PlayerListener(this, playerQuestManager));
        register(new GUIListener(playerQuestManager));
        register(new CustomOreListener(this));
        register(new WandManager(this));
        register(new FishingListener(this));
        register(new MobKillListener(this));
        register(new TutorialController(this));
        register(new CombatAnalyzer(this));
        this.safeZoneListener = register(new SafeZoneListener(this));
        register(new AnimationListener(this));
        register(new BackpackListener(this, backpackManager));
        register(new CombatExperienceListener(this));
        register(new SeekerAIEngine());
        this.mobSpawnManager = register(new MobSpawnManager(this, playerQuestManager));
        register(new RegenTask(this));
        register(new MarketApiController(this));
        register(new PlayerInventoryRestrictor(this));
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
        return safeZoneListener.getSafeZoneSpawn(playerUUID);
    }

    // リスポーン地点を設定
    public void setSafeZoneSpawn(UUID playerUUID, Location location) {
        safeZoneListener.setSafeZoneSpawn(playerUUID, location);
    }

    // リスポーン地点データをファイルから読み込む

    // リスポーン地点データをファイルに保存する
    public void saveSafeZoneSpawns() {
        safeZoneListener.saveSafeZoneSpawns();
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
