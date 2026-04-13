package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.api.DW;
import com.lunar_prototype.deepwither.api.DeepwitherPartyAPI;
import com.lunar_prototype.deepwither.api.IItemFactory;
import com.lunar_prototype.deepwither.booster.BoosterManager;
import com.lunar_prototype.deepwither.command.*;
import com.lunar_prototype.deepwither.command.DebugCommand;
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
import com.lunar_prototype.deepwither.layer_move.LayerMoveCommand;
import com.lunar_prototype.deepwither.layer_move.LayerMoveManager;
import com.lunar_prototype.deepwither.layer_move.LayerSignListener;
import com.lunar_prototype.deepwither.listeners.*;
import com.lunar_prototype.deepwither.loot.LootChestListener;
import com.lunar_prototype.deepwither.loot.LootChestManager;
import com.lunar_prototype.deepwither.loot.RouteLootChestManager;
import com.lunar_prototype.deepwither.market.GlobalMarketManager;
import com.lunar_prototype.deepwither.market.MarketGui;
import com.lunar_prototype.deepwither.market.MarketSearchHandler;
import com.lunar_prototype.deepwither.market.api.MarketApiController;
import com.lunar_prototype.deepwither.mail.MailInboxGUI;
import com.lunar_prototype.deepwither.mail.MailManager;
import com.lunar_prototype.deepwither.modules.economy.trader.*;
import com.lunar_prototype.deepwither.modules.mine.MiningSkillService;
import com.lunar_prototype.deepwither.modules.mine.MineService;
import com.lunar_prototype.deepwither.mythic.CustomDropListener;
import com.lunar_prototype.deepwither.modules.outpost.OutpostDamageListener;
import com.lunar_prototype.deepwither.modules.outpost.OutpostManager;
import com.lunar_prototype.deepwither.modules.outpost.OutpostRegionListener;
import com.lunar_prototype.deepwither.party.PartyChatListener;
import com.lunar_prototype.deepwither.party.PartyManager;
import com.lunar_prototype.deepwither.profession.ProfessionDatabase;
import com.lunar_prototype.deepwither.profession.ProfessionManager;
import com.lunar_prototype.deepwither.profiler.CombatAnalyzer;
import com.lunar_prototype.deepwither.modules.aethelgard.*;
import com.lunar_prototype.deepwither.api.DeepwitherAPI;
import com.lunar_prototype.deepwither.api.database.IDatabaseManager;
import com.lunar_prototype.deepwither.api.stat.IStatManager;
import com.lunar_prototype.deepwither.dungeon.roguelike.*;
import com.lunar_prototype.deepwither.raidboss.RaidBossListener;
import com.lunar_prototype.deepwither.raidboss.RaidBossManager;
import com.lunar_prototype.deepwither.seeker.CombatExperienceListener;
import com.lunar_prototype.deepwither.seeker.SeekerAIEngine;
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
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class Deepwither extends JavaPlugin implements DeepwitherAPI {

    private static Deepwither instance;

    /**
     * Access the global Deepwither plugin instance.
     *
     * @return the singleton Deepwither instance, or {@code null} if the plugin has not been initialized
     */
    public static Deepwither getInstance() {
        return instance;
    }

    /**
     * Retrieve a registered component or service by its class.
     *
     * @param <T>   the type of the requested component
     * @param clazz the class object of the component to retrieve
     * @return the registered instance for the given class, or `null` if none is registered
     */
    @Override
    public <T> T get(Class<T> clazz) {
        return serviceManager.get(clazz);
    }

    /**
     * Accessor for the registered stat manager kept for legacy callers.
     *
     * @deprecated Use {@link #get(Class) get(StatManager.class)} to obtain the manager from the service registry.
     * @return the registered {@link IStatManager} instance, or {@code null} if none is registered
     */
    @Override
    @Deprecated
    public IStatManager getStatManager() {
        return (IStatManager) serviceManager.get(StatManager.class);
    }

    /**
     * Accesses the registered database manager.
     *
     * @deprecated Use {@code get(DatabaseManager.class)} to obtain the database manager from the service registry.
     * @return the registered {@code IDatabaseManager} instance, or {@code null} if none is registered
     */
    @Override
    @Deprecated
    public IDatabaseManager getDatabaseManager() {
        return (IDatabaseManager) serviceManager.get(DatabaseManager.class);
    }

    /**
     * UI管理マネージャーを取得します。
     * @return UIManager
     */
    @Override
    public com.lunar_prototype.deepwither.core.UIManager getUIManager() {
        return serviceManager.get(com.lunar_prototype.deepwither.core.UIManager.class);
    }

    /**
     * Accesses the registered item factory implementation.
     *
     * @deprecated Use {@code get(ItemFactory.class)} (or the generic {@code get(Class)} API) to obtain the item factory from the service manager.
     * @return the registered {@link IItemFactory} instance, or {@code null} if no item factory is registered
     */
    @Deprecated
    public IItemFactory getItemFactoryAPI() {
        return (IItemFactory) serviceManager.get(ItemFactory.class);
    }

    private ServiceManager serviceManager;
    private DeepwitherBootstrap bootstrap; // [NEW] Bootstrap

    /**
     * Injects the ServiceManager instance used by the plugin, supporting legacy module-based initialization.
     *
     * @param serviceManager the ServiceManager provided by legacy modules or external bootstrapping; stored for later use
     */
    public void setServiceManager(ServiceManager serviceManager) {
        this.serviceManager = serviceManager;
    }

    /**
     * Access the plugin's DeepwitherBootstrap instance.
     *
     * @return the current DeepwitherBootstrap instance, or `null` if it has not been initialized
     */
    public DeepwitherBootstrap getBootstrap() {
        return bootstrap;
    }

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
    private SkillRegistry skillRegistry;
    private com.lunar_prototype.deepwither.api.skill.aura.AuraManager auraManager;
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

    private TraderManager traderManager;
    private CreditManager creditManager;
    public ArtifactGUI artifactGUI;
    public ItemFactory itemFactory;

    /**
     * Accesses the plugin's manager responsible for dungeon extraction operations.
     *
     * @return the DungeonExtractionManager instance used to manage dungeon extraction tasks and state
     */
    public DungeonExtractionManager getDungeonExtractionManager() {
        return dungeonExtractionManager;
    }

    private DungeonExtractionManager dungeonExtractionManager;

    public StatManager statManager;
    private DailyTaskManager dailyTaskManager;
    private MobSpawnManager mobSpawnManager;
    private ItemNameResolver itemNameResolver;
    private MobKillListener mobKillListener;
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
    private MaterialGuideManager materialGuideManager;
    private MaterialGuideGUI materialGuideGUI;
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
    private FishingManager fishingManager;
    private RaidBossManager raidBossManager;
    private LayerMoveManager layerMoveManager;
    private ResourcePackListener resourcePackListener;

    public GlobalMarketManager getGlobalMarketManager() {
        return globalMarketManager;
    }

    private GlobalMarketManager globalMarketManager;
    private MarketSearchHandler marketSearchHandler;
    private LootLevelManager lootLevelManager;
    private LootDropManager lootDropManager;
    private MailManager mailManager;
    private RouteLootChestManager routeLootChestManager;

    public MarketGui getMarketGui() {
        return marketGui;
    }

    private MarketGui marketGui;
    private MailInboxGUI mailInboxGUI;

    public SeekerAIEngine getAiEngine() {
        return aiEngine;
    }

    private SeekerAIEngine aiEngine;

    public RaidBossManager getRaidBossManager() {
        return raidBossManager;
    }

    private static Economy econ = null;
    private final java.util.Random random = new java.util.Random();
    private OutpostManager outpostManager;
    private RoguelikeBuffManager roguelikeBuffManager;
    private RoguelikeBuffGUI roguelikeBuffGUI;

    public MenuGUI getMenuGUI() {
        return menuGUI;
    }

    public MailManager getMailManager() {
        return mailManager;
    }

    public MailInboxGUI getMailInboxGUI() {
        return mailInboxGUI;
    }

    public RouteLootChestManager getRouteLootChestManager() {
        return routeLootChestManager;
    }

    public ResetGUI getResetGUI() {
        return resetGUI;
    }

    /**
     * Provides the MenuItemListener responsible for handling menu item interactions.
     *
     * @return the MenuItemListener instance used to handle menu item interactions
     */
    public MenuItemListener getMenuItemListener() {
        return menuItemListener;
    }

    /**
     * Provides the SafeZoneListener instance used to handle safe-zone related events.
     *
     * @return the SafeZoneListener responsible for safe-zone event handling
     */
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

    public SkillRegistry getSkillRegistry() {
        return skillRegistry;
    }

    public com.lunar_prototype.deepwither.api.skill.aura.AuraManager getAuraManager() {
        return auraManager;
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

    /**
     * Provides access to the plugin's Artifact GUI manager.
     *
     * @return the ArtifactGUI instance that manages artifact-related GUIs and player interactions
     */
    public ArtifactGUI getArtifactGUI() {
        return artifactGUI;
    }

    /**
     * Deprecated accessor for the plugin's ItemFactory.
     *
     * @deprecated Retained for legacy compatibility; use the service registry–based API instead.
     * @return the registered ItemFactory
     */
    @Deprecated
    public ItemFactory getItemFactory() {
        return itemFactory;
    }

    /**
     * Access the registered TraderManager.
     *
     * @return the TraderManager instance, or null if it has not been initialized
     * @deprecated Deprecated — retained for binary compatibility.
     */
    @Deprecated
    public TraderManager getTraderManager() {
        return traderManager;
    }

    /**
     * Provides access to the plugin's CreditManager.
     *
     * @deprecated Use {@link #get(Class)} to obtain the CreditManager from the service container instead.
     * @return the CreditManager instance
     */
    @Deprecated
    public CreditManager getCreditManager() {
        return creditManager;
    }

    /**
     * Provides access to the plugin's DailyTaskManager.
     *
     * @deprecated Access managers through {@link #get(Class)} or the ServiceManager; this compatibility accessor may be removed in a future release.
     * @return the DailyTaskManager instance used by the plugin
     */
    @Deprecated
    public DailyTaskManager getDailyTaskManager() { // ★ 新規追加
        return dailyTaskManager;
    }

    @Deprecated
    public MobSpawnManager getMobSpawnManager() {
        return DW.get(MobSpawnManager.class);
    }

    /**
     * Accesses the plugin's LootChestManager instance.
     *
     * @return the registered LootChestManager
     * @deprecated Use {@link Deepwither#get(Class)} or obtain the manager from the ServiceManager instead.
     */
    @Deprecated
    public LootChestManager getLootChestManager() {
        return lootChestManager;
    }

    /**
     * Accesses the plugin's item name resolver used to determine display names for items.
     *
     * @deprecated Use the service lookup API instead, e.g. {@code Deepwither.getInstance().get(ItemNameResolver.class)}.
     * @return the registered ItemNameResolver, or {@code null} if none is set
     */
    @Deprecated
    public ItemNameResolver getItemNameResolver() {
        return itemNameResolver;
    }

    /**
     * Access the plugin's PlayerQuestManager.
     *
     * @deprecated Use {@code get(PlayerQuestManager.class)} or obtain the manager from the ServiceManager instead.
     * @return the PlayerQuestManager instance, or {@code null} if it has not been registered
     */
    @Deprecated
    public PlayerQuestManager getPlayerQuestManager() {
        return serviceManager.get(PlayerQuestManager.class);
    }

    /**
     * Accesses the plugin's CraftingManager instance.
     *
     * @deprecated Use {@link #get(Class)} with {@code CraftingManager.class} (service lookup) instead.
     * @return the CraftingManager instance, or {@code null} if it has not been initialized
         */
    @Deprecated
    public CraftingManager getCraftingManager() {
        return craftingManager;
    }

    public MaterialGuideManager getMaterialGuideManager() {
        return materialGuideManager;
    }

    public MaterialGuideGUI getMaterialGuideGUI() {
        return materialGuideGUI;
    }

    /**
     * Accesses the cached CraftingGUI instance.
     *
     * @deprecated This legacy accessor is retained for backward compatibility; obtain the CraftingGUI from the service registry or the bootstrap-provided components instead.
     * @return the cached CraftingGUI instance, or {@code null} if it has not been initialized
     */
    @Deprecated
    public CraftingGUI getCraftingGUI() {
        return craftingGUI;
    }

    /**
     * Access the legacy ProfessionManager instance.
     *
     * @return the registered ProfessionManager instance, or {@code null} if it has not been initialized
     * @deprecated Use the service registry via {@link #get(Class)} (for example, {@code get(ProfessionManager.class)}) to obtain the ProfessionManager.
     */
    @Deprecated
    public ProfessionManager getProfessionManager() {
        return professionManager;
    }

    /**
     * Access the plugin's ChargeManager instance.
     *
     * @return the registered ChargeManager instance, or {@code null} if it has not been initialized
     * @deprecated Managers are now provided via the service manager; use {@code Deepwither#get(Class)} or obtain the manager from the ServiceManager instead.
     */
    @Deprecated
    public ChargeManager getChargeManager() {
        return chargeManager;
    }

    /**
     * Access the plugin's BackpackManager.
     *
     * @deprecated Use {@link #get(Class)} to obtain the manager from the service manager instead.
     * @return the registered BackpackManager instance, or null if not available
     */
    @Deprecated
    public BackpackManager getBackpackManager() {
        return backpackManager;
    };

    /**
     * Accesses the plugin's DamageManager instance.
     *
     * @return the registered DamageManager instance
     * @deprecated Use the service registry (for example, {@code get(DamageManager.class)}) to obtain managers instead of direct getters.
     */
    @Deprecated
    public DamageManager getDamageManager() {
        return damageManager;
    }

    /**
     * Accessor for the plugin's DamageProcessor instance.
     *
     * @deprecated DamageProcessor instances are provided by the service manager; this legacy accessor will be removed.
     * @return the DamageProcessor used by the plugin, or `null` if it has not been initialized.
     */
    @Deprecated
    public DamageProcessor getDamageProcessor() {
        return damageProcessor;
    }

    /**
     * Access the configured WeaponMechanicManager instance.
     *
     * @return the registered WeaponMechanicManager instance
     * @deprecated Obtain the manager from the service container instead, e.g. {@code Deepwither.get(WeaponMechanicManager.class)}.
     */
    @Deprecated
    public WeaponMechanicManager getWeaponMechanicManager() {
        return weaponMechanicManager;
    }

    /**
     * Access the plugin's PlayerSettingsManager.
     *
     * @return the PlayerSettingsManager instance
     * @deprecated Use the service lookup API (for example, {@code get(PlayerSettingsManager.class)}) instead of this legacy accessor.
     */
    @Deprecated
    public PlayerSettingsManager getSettingsManager() {
        return settingsManager;
    }

    /**
     * Retrieves the SettingsGUI instance used by the plugin.
     *
     * @deprecated Use {@link #get(Class)} (for example, {@code get(SettingsGUI.class)}) or obtain the component from the ServiceManager.
     * @return the SettingsGUI instance
     */
    @Deprecated
    public SettingsGUI getSettingsGUI() {
        return settingsGUI;
    }

    /**
     * Provides access to the plugin's CompanionManager instance.
     *
     * @deprecated Use Deepwither#get(Class) or the ServiceManager to obtain the CompanionManager; this getter will be removed in a future release.
     * @return the CompanionManager instance used by the plugin
     */
    @Deprecated
    public CompanionManager getCompanionManager() {
        return companionManager;
    }

    /**
     * Access the LayerMoveManager instance.
     *
     * @return the LayerMoveManager held by this plugin instance
     * @deprecated Managers are now provided and retrieved via the service container/bootstrap. Use {@code Deepwither.get(LayerMoveManager.class)} (or obtain the manager from the ServiceManager/DeepwitherBootstrap) instead.
     */
    @Deprecated
    public LayerMoveManager getLayerMoveManager() {
        return layerMoveManager;
    }

    /**
     * Gets the plugin's FishingManager instance.
     *
     * @return the FishingManager instance, or null if not initialized
     * @deprecated Manager instances are provided via the service manager/bootstrap; obtain the FishingManager from the service registry or API instead of using this deprecated accessor.
     */
    @Deprecated
    public FishingManager getFishingManager() {
        return fishingManager;
    }

    /**
     * Gets the registered MobKillListener.
     *
     * @return the registered {@link MobKillListener}, or {@code null} if it has not been initialized
     * @deprecated Deprecated; obtain listeners from the plugin's service manager or API instead
     */
    @Deprecated
    public MobKillListener getMobKillListener() {
        return mobKillListener;
    }

    /**
     * Accesses the legacy MarketSearchHandler instance.
     *
     * @deprecated Retrieve the handler from the service manager or via {@code get(Class)} on the Deepwither API instead of using this legacy accessor.
     * @return the registered MarketSearchHandler, or {@code null} if none is set
     */
    @Deprecated
    public MarketSearchHandler getMarketSearchHandler() {
        return marketSearchHandler;
    }

    /**
     * Accesses the plugin's Skilltree GUI instance.
     *
     * @deprecated Use service-managed retrieval (for example via Deepwither#get(Class)) instead of this legacy accessor.
     * @return the SkilltreeGUI instance, or null if it has not been initialized
     */
    @Deprecated
    public SkilltreeGUI getSkilltreeGUI() {
        return skilltreeGUI;
    }

    /**
     * Access the plugin's LootDropManager instance.
     *
     * @deprecated Obtain the manager from the service container instead of using this legacy accessor; use the ServiceManager or Deepwither#get(Class) to retrieve `LootDropManager`.
     * @return the registered LootDropManager, or `null` if it has not been initialized
     */
    @Deprecated
    public LootDropManager getLootDropManager() {
        return lootDropManager;
    }

    /**
     * Provides access to the plugin's LootLevelManager.
     *
     * @return the LootLevelManager instance, or null if it has not been initialized or registered
     * @deprecated Direct accessors are deprecated; obtain managers from the service registry instead
     */
    @Deprecated
    public LootLevelManager getLootLevelManager() {
        return lootLevelManager;
    }

    /**
     * Initializes the plugin and boots core subsystems, command handlers, scheduled tasks, and external integrations.
     *
     * <p>On success this sets the global plugin instance, configures the async executor, initializes the bootstrap and service layer, loads guild quest configuration and components, initializes the party API, registers command executors and tab completers, schedules recurring mana regeneration and attack-speed reset tasks, starts the MythicBukkit AI tick loop for bandits, and registers PlaceholderAPI expansions when available.</p>
     */
    @Override
    public void onEnable() {
        // Plugin startup logic
        instance = this;
        ConfigurationSerialization.registerClass(DailyTaskData.class);

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

        this.partyAPI = new DeepwitherPartyAPI(partyManager); // ★ 初期化
        this.getCommand("artifact").setExecutor(new ArtifactGUICommand(artifactGUI));
        getCommand("trader").setExecutor(new TraderCommand(traderManager));
        getCommand("credit").setExecutor(new CreditCommand(creditManager));
        getCommand("companion").setExecutor(new CompanionCommand(companionManager));

        Bukkit.getPluginManager().registerEvents(new PartyChatListener(partyManager),this);
        Bukkit.getPluginManager().registerEvents(new com.lunar_prototype.deepwither.listeners.SkillAuraListener(), this);

        ItemCommand itemCommand = new ItemCommand(this);
        getCommand("giveitem").setExecutor(itemCommand);
        getCommand("giveitem").setTabCompleter(itemCommand);

        saveDefaultConfig(); // MobExpConfig.yml

        this.getCommand("status")
                .setExecutor(new StatusCommand(levelManager, statManager, creditManager, professionManager,
                        get(FishingManager.class), get(MiningSkillService.class)));

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

        initializeResourcePack();
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
        getCommand("deepwither").setExecutor(new DeepwitherCommand(this));
        LayerMoveCommand layerMoveCommand = new LayerMoveCommand();
        getCommand("layermove").setExecutor(layerMoveCommand);
        getCommand("layermove").setTabCompleter(layerMoveCommand);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(new DebugCommand().node());
        });
    }

    /**
     * Performs plugin shutdown tasks: unloads level and attribute data for online players,
     * delegates shutdown to the bootstrap if present (falling back to the service manager),
     * and shuts down the asynchronous executor.
     */
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

    /**
     * Initializes core subsystems, UI components, and event listeners for the plugin.
     *
     * <p>Retrieves required services from the configured ServiceManager and constructs or
     * registers remaining manager, GUI, and listener instances so they are available
     * through the plugin's service container.</p>
     *
     * <p>This method is public to allow invocation by legacy modules during bootstrap.</p>
     */
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
        register(new com.lunar_prototype.deepwither.core.UIManager(settingsManager));
        this.chargeManager = serviceManager.get(ChargeManager.class);
        this.cooldownManager = serviceManager.get(CooldownManager.class);

        // --- Core ---

        // --- Group A & B & Base ---
        this.attributeManager = register(serviceManager.get(AttributeManager.class));
        this.levelManager = register(serviceManager.get(LevelManager.class));
        this.skilltreeManager = register(serviceManager.get(SkilltreeManager.class));
        this.professionDatabase = register(serviceManager.get(ProfessionDatabase.class));
        this.boosterManager = register(serviceManager.get(BoosterManager.class));
        this.globalMarketManager = serviceManager.get(GlobalMarketManager.class);

        // this.statManager = ... handled above
        this.manaManager = register(serviceManager.get(ManaManager.class));
        // this.cooldownManager = ... handled above
        // this.itemFactory = ... handled above
        this.itemNameResolver = register(serviceManager.get(ItemNameResolver.class));

        this.skillLoader = register(serviceManager.get(SkillLoader.class));
        this.skillSlotManager = register(serviceManager.get(SkillSlotManager.class));
        this.skillCastManager = register(serviceManager.get(SkillCastManager.class));
        this.skillRegistry = register(serviceManager.get(SkillRegistry.class));
        this.auraManager = register(serviceManager.get(com.lunar_prototype.deepwither.api.skill.aura.AuraManager.class));
        // this.chargeManager = ... handled above
        // this.settingsManager = ... handled above

        this.damageProcessor = serviceManager.get(DamageProcessor.class);
        this.weaponMechanicManager = serviceManager.get(WeaponMechanicManager.class);
        this.damageManager = serviceManager.get(DamageManager.class);

        // --- Group C & D ---
        this.artifactManager = register(serviceManager.get(ArtifactManager.class));
        this.backpackManager = register(serviceManager.get(BackpackManager.class));
        this.creditManager = serviceManager.get(CreditManager.class);
        this.traderManager = serviceManager.get(TraderManager.class);
        this.lootChestManager = register(serviceManager.get(LootChestManager.class));
        this.lootLevelManager = register(serviceManager.get(LootLevelManager.class));
        this.lootDropManager = register(serviceManager.get(LootDropManager.class));
        this.mailManager = register(serviceManager.get(MailManager.class));
        this.routeLootChestManager = register(serviceManager.get(RouteLootChestManager.class));
        this.craftingManager = register(serviceManager.get(CraftingManager.class));
        this.marketGui = register(serviceManager.get(MarketGui.class));
        this.marketSearchHandler = register(serviceManager.get(MarketSearchHandler.class));

        this.companionManager = register(serviceManager.get(CompanionManager.class));
        this.raidBossManager = register(serviceManager.get(RaidBossManager.class));
        this.layerMoveManager = register(serviceManager.get(LayerMoveManager.class));
        this.pvPvEDungeonManager = register(serviceManager.get(PvPvEDungeonManager.class));

        register(serviceManager.get(com.lunar_prototype.deepwither.dungeon.instance.DungeonInstanceManager.class));
        this.dungeonExtractionManager = register(serviceManager.get(DungeonExtractionManager.class));
        this.fishingManager = register(serviceManager.get(FishingManager.class));
        this.townBurstManager = register(serviceManager.get(TownBurstManager.class));
        this.mythicMobSafeZoneManager = register(serviceManager.get(MythicMobSafeZoneManager.class));
        this.partyManager = register(serviceManager.get(PartyManager.class));
        this.roguelikeBuffManager = register(serviceManager.get(RoguelikeBuffManager.class));
        this.roguelikeBuffGUI = register(serviceManager.get(RoguelikeBuffGUI.class));
        register(serviceManager.get(com.lunar_prototype.deepwither.fasttravel.FastTravelManager.class));

        // --- Group E ---
        this.fileDailyTaskDataStore = serviceManager.get(FileDailyTaskDataStore.class); // Explicit cast if generic
                                                                                        // needed
        this.dailyTaskManager = serviceManager.get(DailyTaskManager.class);

        // Mine Module Bridge
        serviceManager.register(serviceManager.get(MineService.class));

        // Aethelgard Managers
        GuildQuestManager guildQuestManager = serviceManager.get(GuildQuestManager.class);
        PlayerQuestManager playerQuestManager = serviceManager.get(PlayerQuestManager.class);

        this.professionManager = register(serviceManager.get(ProfessionManager.class));
        this.aiEngine = register(serviceManager.get(SeekerAIEngine.class));

        // --- UI & Listeners (Managed) ---
        this.artifactGUI = register(serviceManager.get(ArtifactGUI.class));
        this.artifactGUIListener = register(serviceManager.get(ArtifactGUIListener.class));
        this.skillAssignmentGUI = register(serviceManager.get(SkillAssignmentGUI.class));
        this.settingsGUI = register(serviceManager.get(SettingsGUI.class));
        this.mailInboxGUI = register(serviceManager.get(MailInboxGUI.class));
        this.menuGUI = register(serviceManager.get(MenuGUI.class));
        this.resetGUI = register(serviceManager.get(ResetGUI.class));
        this.menuItemListener = register(serviceManager.get(MenuItemListener.class));
        this.craftingGUI = register(serviceManager.get(CraftingGUI.class));
        this.traderGUI = register(serviceManager.get(TraderGUI.class));
        this.sellGUI = register(serviceManager.get(SellGUI.class));
        
        this.materialGuideManager = register(serviceManager.get(MaterialGuideManager.class));
        // MaterialGuideGUI might not be an IManager, just fetching it
        this.materialGuideGUI = serviceManager.get(MaterialGuideGUI.class);

        // --- Standalone Listeners (Managed) ---
        register(serviceManager.get(PlayerConnectionListener.class));
        register(serviceManager.get(MythicMechanicListener.class));
        register(serviceManager.get(ArmorSetListener.class));
        register(serviceManager.get(ArtifactSetListener.class));
        register(serviceManager.get(ItemUpgradeListener.class));
        register(serviceManager.get(PlayerStatListener.class));
        register(serviceManager.get(SkillCastSessionManager.class));
        register(serviceManager.get(RaidBossListener.class));
        register(serviceManager.get(CraftingListener.class));
        register(serviceManager.get(CustomDropListener.class));
        register(serviceManager.get(TaskListener.class));
        register(serviceManager.get(LootChestListener.class));
        register(serviceManager.get(CompanionListener.class));
        register(serviceManager.get(CompanionGuiListener.class));
        register(serviceManager.get(LayerSignListener.class));
        register(serviceManager.get(BossKillListener.class));
        register(serviceManager.get(PvPWorldListener.class));
        register(serviceManager.get(ItemGlowHandler.class));
        register(serviceManager.get(VoteListener.class));
        register(serviceManager.get(DungeonSignListener.class));
        register(serviceManager.get(PvPvEChestListener.class));
        register(serviceManager.get(ItemDurabilityFix.class));
        register(serviceManager.get(AttributeGui.class));
        register(serviceManager.get(BlacksmithListener.class));
        register(serviceManager.get(DropPreventionListener.class));
        register(serviceManager.get(PlayerInteractListener.class));
        register(serviceManager.get(PlayerListener.class));
        register(serviceManager.get(WandManager.class));
        register(serviceManager.get(FishingListener.class));
        register(serviceManager.get(MobKillListener.class));
        register(serviceManager.get(TutorialController.class));
        register(serviceManager.get(CombatAnalyzer.class));
        this.safeZoneListener = register(serviceManager.get(SafeZoneListener.class));
        register(serviceManager.get(AnimationListener.class));
        register(serviceManager.get(BackpackListener.class));
        register(serviceManager.get(CombatExperienceListener.class));
        // SeekerAIEngine is already registered above, removing the duplicate.
        register(serviceManager.get(RegenTask.class));
        register(serviceManager.get(MarketApiController.class));
        register(serviceManager.get(PlayerInventoryRestrictor.class));
        resourcePackListener = new ResourcePackListener(this);
        Bukkit.getPluginManager().registerEvents(resourcePackListener, this);
        new com.lunar_prototype.deepwither.core.listener.EnvironmentListener(this);
    }

    /**
     * Register the given manager with the plugin's service container and return it.
     *
     * @param manager the manager instance to register with the ServiceManager
     * @param <T>     the manager type
     * @return        the same manager instance that was registered
     */
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

    /**
     * Set the respawn location for a player inside a safe zone.
     *
     * @param playerUUID the UUID of the player whose safe-zone spawn will be set
     * @param location   the world location to use as the player's safe-zone respawn point
     */
    public void setSafeZoneSpawn(UUID playerUUID, Location location) {
        safeZoneListener.setSafeZoneSpawn(playerUUID, location);
    }

    // リスポーン地点データをファイルから読み込む

    /**
     * Persists safe-zone respawn point data to disk.
     *
     * Writes the current safe-zone spawn locations into the plugin's storage so they are preserved across restarts.
     */
    public void saveSafeZoneSpawns() {
        safeZoneListener.saveSafeZoneSpawns();
    }

    private void initializeResourcePack() {
        String url = getConfig().getString("resource-pack.url");
        if (url == null || url.isEmpty()) return;

        asyncExecutor.submit(() -> {
            try {
                long start = System.currentTimeMillis();
                java.net.URL resourcePackUrl = new java.net.URI(url).toURL();
                java.net.URLConnection connection = resourcePackUrl.openConnection();
                try (java.io.InputStream is = connection.getInputStream()) {
                    java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = is.read(buffer)) != -1) {
                        md.update(buffer, 0, n);
                    }
                    byte[] hash = md.digest();
                    resourcePackListener.setResourcePackHash(hash);
                    long end = System.currentTimeMillis();
                    getLogger().info("Successfully computed resource pack hash in " + (end - start) + "ms");
                }
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Failed to compute resource pack hash: " + e.getMessage());
            }
        });
    }

}

