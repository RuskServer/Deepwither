package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.api.DeepwitherPartyAPI;
import com.lunar_prototype.deepwither.booster.BoosterManager;
import com.lunar_prototype.deepwither.command.BoosterCommand;
import com.lunar_prototype.deepwither.command.PvPCommand;
import com.lunar_prototype.deepwither.command.ResetGUICommand;
import com.lunar_prototype.deepwither.companion.CompanionCommand;
import com.lunar_prototype.deepwither.companion.CompanionGuiListener;
import com.lunar_prototype.deepwither.companion.CompanionListener;
import com.lunar_prototype.deepwither.companion.CompanionManager;
import com.lunar_prototype.deepwither.crafting.CraftingGUI;
import com.lunar_prototype.deepwither.crafting.CraftingListener;
import com.lunar_prototype.deepwither.crafting.CraftingManager;
import com.lunar_prototype.deepwither.data.*;
import com.lunar_prototype.deepwither.layer_move.BossKillListener;
import com.lunar_prototype.deepwither.layer_move.LayerMoveManager;
import com.lunar_prototype.deepwither.layer_move.LayerSignListener;
import com.lunar_prototype.deepwither.listeners.PvPWorldListener;
import com.lunar_prototype.deepwither.loot.LootChestListener;
import com.lunar_prototype.deepwither.loot.LootChestManager;
import com.lunar_prototype.deepwither.mythic.ManaShieldMechanic;
import com.lunar_prototype.deepwither.outpost.OutpostConfig;
import com.lunar_prototype.deepwither.outpost.OutpostDamageListener;
import com.lunar_prototype.deepwither.outpost.OutpostManager;
import com.lunar_prototype.deepwither.outpost.OutpostRegionListener;
import com.lunar_prototype.deepwither.party.PartyManager;
import com.lunar_prototype.deepwither.profession.ProfessionDatabase;
import com.lunar_prototype.deepwither.profession.ProfessionManager;
import com.lunar_prototype.deepwither.profiler.CombatAnalyzer;
import com.lunar_prototype.deepwither.quest.*;
import com.lunar_prototype.deepwither.raidboss.RaidBossListener;
import com.lunar_prototype.deepwither.raidboss.RaidBossManager;
import com.lunar_prototype.deepwither.seeker.SeekerAIEngine;
import com.lunar_prototype.deepwither.town.TownBurstManager;
import com.lunar_prototype.deepwither.tutorial.TutorialController;
import com.lunar_prototype.deepwither.util.IManager;
import com.lunar_prototype.deepwither.util.MythicMobSafeZoneManager;
import com.vexsoftware.votifier.model.VotifierEvent;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.events.MythicDropLoadEvent;
import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent;
import io.lumine.mythic.core.mobs.ActiveMob;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
import com.lunar_prototype.deepwither.LevelManager;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class  Deepwither extends JavaPlugin {

    private Logger log;

    private static Deepwither instance;
    public static Deepwither getInstance() { return instance; }
    private Map<UUID, Location> safeZoneSpawns = new HashMap<>();
    private final Map<Class<? extends IManager>, IManager> managers = new LinkedHashMap<>();
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
    private TraderManager traderManager;
    private CreditManager creditManager;
    public ArtifactGUI artifactGUI;
    public ItemFactory itemFactory;
    public StatManager statManager;
    private DailyTaskManager dailyTaskManager;
    private MobSpawnManager mobSpawnManager;
    private ItemNameResolver itemNameResolver;
    private QuestDataStore questDataStore;
    private GuildQuestManager guildQuestManager;
    private PlayerQuestManager playerQuestManager;
    private ExecutorService asyncExecutor;
    private FileDailyTaskDataStore fileDailyTaskDataStore;
    private LootChestManager lootChestManager;
    private TownBurstManager townBurstManager;
    private MythicMobSafeZoneManager mythicMobSafeZoneManager;
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
    private RaidBossManager raidBossManager;
    private LayerMoveManager layerMoveManager;
    private SeekerAIEngine aiEngine;
    private static Economy econ = null;
    private final java.util.Random random = new java.util.Random();
    private OutpostManager outpostManager;

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
    public SkillLoader getSkillLoader(){
        return skillLoader;
    }
    public SkillSlotManager getSkillSlotManager(){
        return skillSlotManager;
    }
    public SkillCastManager getSkillCastManager(){
        return skillCastManager;
    }
    public SkillAssignmentGUI getSkillAssignmentGUI() {
        return skillAssignmentGUI;
    }
    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }
    public ArtifactManager getArtifactManager(){
        return artifactManager;
    }
    public ArtifactGUIListener getArtifactGUIListener(){
        return artifactGUIListener;
    }
    public  ArtifactGUI getArtifactGUI(){
        return artifactGUI;
    }
    public ItemFactory getItemFactory() {return itemFactory;}
    public StatManager getStatManager() {return statManager;}
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
    public ItemNameResolver getItemNameResolver() {
        return itemNameResolver;
    }
    public PlayerQuestManager getPlayerQuestManager() {return playerQuestManager;}
    public CraftingManager getCraftingManager() { return craftingManager; }
    public CraftingGUI getCraftingGUI() { return craftingGUI; }
    public ProfessionManager getProfessionManager(){return professionManager;}
    public ChargeManager getChargeManager() {return chargeManager;}
    public BackpackManager getBackpackManager() {return backpackManager;};
    public DamageManager getDamageManager() {return damageManager;}
    public PlayerSettingsManager getSettingsManager() { return settingsManager; }
    public SettingsGUI getSettingsGUI() { return settingsGUI; }
    public CompanionManager getCompanionManager() { return companionManager; }
    public LayerMoveManager getLayerMoveManager() {return layerMoveManager;}



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
            getLogger().severe(String.format("[%s] - Disabled due to no Vault dependency found!", getDescription().getName()));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.asyncExecutor = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors()
        );

        try {
            // 1. 基盤（Database）の初期化
            this.databaseManager = new DatabaseManager(this);

            // 2. マネージャーの登録とインスタンス化
            setupManagers();

            // 3. 一括初期化実行
            for (IManager manager : managers.values()) {
                try {
                    manager.init();
                } catch (Exception e) {
                    // どのマネージャーが失敗したか明確にする
                    getLogger().severe(manager.getClass().getSimpleName() + " の初期化中にエラーが発生しました。");
                    throw e; // 上の try-catch に飛ばして onEnable 全体を失敗させる
                }
            }
        } catch (Exception e) {
            getLogger().severe("Database initialization failed!");
            getServer().getPluginManager().disablePlugin(this);
        }

        aiEngine = new SeekerAIEngine();

        statManager = new StatManager();
        companionManager = new CompanionManager(this);
        itemFactory = new ItemFactory(this);
        chargeManager = new ChargeManager();
        backpackManager = new BackpackManager(this);
        getServer().getPluginManager().registerEvents(chargeManager, this);
        getServer().getPluginManager().registerEvents(new PlayerStatListener(statManager), this);
        this.settingsManager = new PlayerSettingsManager(this);
        this.settingsGUI = new SettingsGUI(this, settingsManager);
        damageManager = new DamageManager(statManager,settingsManager);
        Bukkit.getPluginManager().registerEvents(damageManager, this);
        Bukkit.getPluginManager().registerEvents(new SkillCastSessionManager(),this);
        Bukkit.getPluginManager().registerEvents(new SkillAssignmentGUI(),this);
        raidBossManager = new RaidBossManager(this);
        getServer().getPluginManager().registerEvents(new RaidBossListener(this,raidBossManager),this);
        manaManager = new ManaManager();
        skillLoader = new SkillLoader();
        File skillsFolder = new File(getDataFolder(), "skills");
        skillLoader.loadAllSkills(skillsFolder);
        skillSlotManager = new SkillSlotManager(getDataFolder());
        skillCastManager = new SkillCastManager();
        cooldownManager = new CooldownManager();
        layerMoveManager = new LayerMoveManager();
        layerMoveManager.load(getDataFolder());
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
        questDataStore = new QuestDataStore(this);
        guildQuestManager = new GuildQuestManager(this,questDataStore);
        itemNameResolver = new ItemNameResolver(this);
        townBurstManager = new TownBurstManager(this);
        this.creditManager = new CreditManager(this);
        this.traderManager = new TraderManager(this, itemFactory);
        this.dailyTaskManager = new DailyTaskManager(this,fileDailyTaskDataStore);
        playerQuestManager = new PlayerQuestManager(this,guildQuestManager,playerQuestDataStore);
        artifactManager = new ArtifactManager(this);
        lootChestManager = new LootChestManager(this);
        artifactGUI = new ArtifactGUI();
        mythicMobSafeZoneManager = new MythicMobSafeZoneManager(this);
        professionManager = new ProfessionManager(this,professionDatabase);
        partyManager = new PartyManager();
        boosterManager = new BoosterManager();
        this.partyAPI = new DeepwitherPartyAPI(partyManager); // ★ 初期化
        this.craftingManager = new CraftingManager(this);
        this.craftingGUI = new CraftingGUI(this);
        getServer().getPluginManager().registerEvents(new CraftingListener(this), this);
        artifactGUIListener = new ArtifactGUIListener(artifactGUI,statManager);
        this.skillAssignmentGUI = new SkillAssignmentGUI(); // 必ず enable 時に初期化
        getServer().getPluginManager().registerEvents(skillAssignmentGUI, this);
        getServer().getPluginManager().registerEvents(artifactGUIListener, this);
        getServer().getPluginManager().registerEvents(artifactGUI, this);
        getServer().getPluginManager().registerEvents(new CustomDropListener(this),this);
        getServer().getPluginManager().registerEvents(new TaskListener(dailyTaskManager), this);
        getServer().getPluginManager().registerEvents(new LootChestListener(this,lootChestManager),this);
        getServer().getPluginManager().registerEvents(new CompanionListener(companionManager),this);
        getServer().getPluginManager().registerEvents(new CompanionGuiListener(companionManager),this);
        getServer().getPluginManager().registerEvents(new LayerSignListener(),this);
        getServer().getPluginManager().registerEvents(new BossKillListener(),this);
        this.getServer().getPluginManager().registerEvents(new CombatAnalyzer(this.companionManager, this), this);

        this.getCommand("artifact").setExecutor(new ArtifactGUICommand(artifactGUI));
        getCommand("trader").setExecutor(new TraderCommand(traderManager));
        getCommand("credit").setExecutor(new CreditCommand(creditManager));
        getCommand("companion").setExecutor(new CompanionCommand(companionManager));
        getServer().getPluginManager().registerEvents(new TraderGUI(), this);
        getServer().getPluginManager().registerEvents(new SellGUI(), this);
        getServer().getPluginManager().registerEvents(new TutorialController(this), this);

        new RegenTask(statManager).start(this);
        guildQuestManager.startup();

        saveDefaultConfig(); // MobExpConfig.yml

        OutpostConfig outpostConfig = new OutpostConfig(this,"outpost.yml");

        OutpostManager.initialize(this, outpostConfig);

        Bukkit.getPluginManager().registerEvents(new MobKillListener(levelManager, getConfig(),OutpostManager.getInstance(),partyManager,boosterManager), this);
        getServer().getPluginManager().registerEvents(new SafeZoneListener(this),this);
        getServer().getPluginManager().registerEvents(new PlayerAnimationListener(),this);
        getServer().getPluginManager().registerEvents(new BackpackListener(this, backpackManager), this);
        this.getCommand("status").setExecutor(new StatusCommand(levelManager, statManager,creditManager,professionManager));
        getServer().getPluginManager().registerEvents(new OutpostRegionListener(OutpostManager.getInstance()),this);
        getServer().getPluginManager().registerEvents(new OutpostDamageListener(OutpostManager.getInstance()),this);

        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onMythicMechanicLoad(MythicMechanicLoadEvent event){
                getLogger().info("MythicMechanicLoadEvent called for mechanic " + event.getMechanicName());

                if(event.getMechanicName().equalsIgnoreCase("CustomDamage"))	{
                    event.register(new CustomDamageMechanics(event.getConfig()));
                    getLogger().info("-- Registered CustomDamage mechanic!");
                }

                if(event.getMechanicName().equalsIgnoreCase("CustomHPDamage"))	{
                    event.register(new CustomHPDamageMechanic(event.getConfig()));
                    getLogger().info("-- Registered CustomHPDamage mechanic!");
                }

                if (event.getMechanicName().equalsIgnoreCase("manaShield")) {
                    event.register(new ManaShieldMechanic(event.getConfig()));
                    getLogger().info("-- Registered manaShield mechanic!");
                }
            }
        },this);

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
                if (attr == null) return;
                NamespacedKey baseAttackSpeed = NamespacedKey.minecraft("base_attack_speed");
                attr.removeModifier(baseAttackSpeed);
            }
        }, 1L, 1L); // 毎秒実行

        this.mobSpawnManager = new MobSpawnManager(this,playerQuestManager);
        townBurstManager.startBurstTask();
        mythicMobSafeZoneManager.startCheckTask();

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new LevelPlaceholderExpansion(levelManager,manaManager,statManager).register();
            getLogger().info("PlaceholderAPI拡張を登録しました。");
        }
        // コマンド登録
        getCommand("attributes").setExecutor(new AttributeCommand());
        try {
            SkilltreeGUI gui = new SkilltreeGUI(this, getDataFolder(),skilltreeManager,skillLoader);
            getCommand("skilltree").setExecutor(gui);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 40Tごとに全ActiveMobをチェックして、特定条件のMobに思考させる
        new BukkitRunnable() {
            @Override
            public void run() {
                for (ActiveMob am : MythicBukkit.inst().getMobManager().getActiveMobs()) {
                    if (am.getMobType().contains("bandit")) { // 名前にbanditを含む場合など
                        aiEngine.tick(am);
                    }
                }
            }
        }.runTaskTimer(this, 0, 40);
        // リスナー登録
        getServer().getPluginManager().registerEvents(new ItemDurabilityFix(),this);
        getServer().getPluginManager().registerEvents(new AttributeGui(), this);
        getServer().getPluginManager().registerEvents(new BlacksmithListener(), this);
        getServer().getPluginManager().registerEvents(new DropPreventionListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerInteractListener(),this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this,playerQuestManager),this);
        Bukkit.getPluginManager().registerEvents(new GUIListener(playerQuestManager), this);
        getServer().getPluginManager().registerEvents(new CustomOreListener(this), this);
        getServer().getPluginManager().registerEvents(new WandManager(),this);

        MenuGUI menuGUI = new MenuGUI(this);
        getCommand("menu").setExecutor(new MenuCommand(menuGUI));
        getServer().getPluginManager().registerEvents(new MenuItemListener(this,menuGUI),this);
        getServer().getPluginManager().registerEvents(new PickupRestricter(),this);
        getServer().getPluginManager().registerEvents(new WeaponHotbarLimiter(),this);

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
        getServer().getPluginManager().registerEvents(new PvPWorldListener(), this);
    }

    @Override
    public void onDisable() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            levelManager.unload(p.getUniqueId());
            attributeManager.unload(p.getUniqueId());
        }
        professionManager.shutdown();
        townBurstManager.stopBurstTask();
        mythicMobSafeZoneManager.stopCheckTask();
        lootChestManager.removeAllLootChests();
        dailyTaskManager.saveAllData();
        skillSlotManager.saveAll();
        artifactManager.saveData();
        guildQuestManager.shutdown();
        saveSafeZoneSpawns();
        List<IManager> managerList = new ArrayList<>(managers.values());
        Collections.reverse(managerList);
        managerList.forEach(IManager::shutdown);
        databaseManager.close();
        shutdownExecutor();
    }

    private void setupManagers() {
        // ここでインスタンスを作成し、同時にフィールドにも代入（既存コード壊し防止）
        this.attributeManager = register(AttributeManager.class, new AttributeManager(databaseManager));
        this.levelManager = register(LevelManager.class, new LevelManager(databaseManager));
        this.skilltreeManager = register(SkilltreeManager.class, new SkilltreeManager(databaseManager, this));
        this.professionDatabase = register(ProfessionDatabase.class,new ProfessionDatabase(this,databaseManager));
        // 新しくSQLite対応させたデータストア (引数にdatabaseManagerを渡す)
        this.fileDailyTaskDataStore = register(FileDailyTaskDataStore.class,
                new FileDailyTaskDataStore(this, databaseManager));

        this.playerQuestDataStore = (PlayerQuestDataStore) register(FilePlayerQuestDataStore.class,
                new FilePlayerQuestDataStore(databaseManager));
    }

    private <T extends IManager> T register(Class<T> clazz, T manager) {
        managers.put(clazz, manager);
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
