package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.data.*;
import com.lunar_prototype.deepwither.loot.LootChestListener;
import com.lunar_prototype.deepwither.loot.LootChestManager;
import com.lunar_prototype.deepwither.quest.*;
import com.lunar_prototype.deepwither.town.TownBurstManager;
import com.lunar_prototype.deepwither.util.MythicMobSafeZoneManager;
import io.lumine.mythic.bukkit.events.MythicDropLoadEvent;
import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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
    private File safeZoneSpawnsFile;
    private FileConfiguration safeZoneSpawnsConfig;
    private FileConfiguration questConfig;
    private LevelManager levelManager;
    private AttributeManager attributeManager;
    private SkilltreeManager skilltreeManager;
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
    private static Economy econ = null;
    private final java.util.Random random = new java.util.Random();

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

        PlayerQuestDataStore playerQuestDataStore = new FilePlayerQuestDataStore(this);

        statManager = new StatManager();
        itemFactory = new ItemFactory(this);
        getServer().getPluginManager().registerEvents(new PlayerStatListener(statManager), this);
        Bukkit.getPluginManager().registerEvents(new DamageManager(statManager), this);
        Bukkit.getPluginManager().registerEvents(new SkillCastSessionManager(),this);
        Bukkit.getPluginManager().registerEvents(new SkillAssignmentGUI(),this);
        manaManager = new ManaManager();
        skillLoader = new SkillLoader();
        File skillsFolder = new File(getDataFolder(), "skills");
        skillLoader.loadAllSkills(skillsFolder);
        skillSlotManager = new SkillSlotManager(getDataFolder());
        skillCastManager = new SkillCastManager();
        cooldownManager = new CooldownManager();
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
        fileDailyTaskDataStore = new  FileDailyTaskDataStore(this);
        this.dailyTaskManager = new DailyTaskManager(this,fileDailyTaskDataStore);
        artifactManager = new ArtifactManager(this);
        lootChestManager = new LootChestManager(this);
        artifactGUI = new ArtifactGUI();
        mythicMobSafeZoneManager = new MythicMobSafeZoneManager(this);
        artifactGUIListener = new ArtifactGUIListener(artifactGUI,statManager);
        this.skillAssignmentGUI = new SkillAssignmentGUI(); // 必ず enable 時に初期化
        getServer().getPluginManager().registerEvents(skillAssignmentGUI, this);
        getServer().getPluginManager().registerEvents(artifactGUIListener, this);
        getServer().getPluginManager().registerEvents(artifactGUI, this);
        getServer().getPluginManager().registerEvents(new CustomDropListener(this),this);
        getServer().getPluginManager().registerEvents(new TaskListener(dailyTaskManager), this);
        getServer().getPluginManager().registerEvents(new LootChestListener(this,lootChestManager),this);

        this.getCommand("artifact").setExecutor(new ArtifactGUICommand(artifactGUI));
        getCommand("trader").setExecutor(new TraderCommand(traderManager));
        getCommand("credit").setExecutor(new CreditCommand(creditManager));
        getServer().getPluginManager().registerEvents(new TraderGUI(), this);
        getServer().getPluginManager().registerEvents(new SellGUI(), this);

        new RegenTask(statManager).start(this);
        guildQuestManager.startup();
        playerQuestManager = new PlayerQuestManager(this,guildQuestManager,playerQuestDataStore);

        saveDefaultConfig(); // MobExpConfig.yml
        try {
            levelManager = new LevelManager(new File(getDataFolder(), "levels.db"));
        } catch (SQLException e) {
            getLogger().severe("SQLite初期化に失敗");
            return;
        }
        try {
            attributeManager = new AttributeManager(new File(getDataFolder(), "levels.db"));
        } catch (SQLException e) {
            getLogger().severe("SQLite初期化に失敗");
            return;
        }
        try {
            skilltreeManager = new SkilltreeManager(new File(getDataFolder(), "levels.db"),this);
        } catch (SQLException e) {
            getLogger().severe("SQLite初期化に失敗");
            return;
        }

        Bukkit.getPluginManager().registerEvents(new MobKillListener(levelManager, getConfig()), this);
        getServer().getPluginManager().registerEvents(new SafeZoneListener(this),this);
        getServer().getPluginManager().registerEvents(new PlayerAnimationListener(),this);
        this.getCommand("status").setExecutor(new StatusCommand(levelManager, statManager,creditManager));

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
            }
            @EventHandler
            public void onQuit(PlayerQuitEvent e) {
                levelManager.unload(e.getPlayer().getUniqueId());
                attributeManager.unload(e.getPlayer().getUniqueId());
                dailyTaskManager.saveAndUnloadPlayer(e.getPlayer().getUniqueId());
            }
        }, this);

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                ManaData mana = Deepwither.getInstance().getManaManager().get(p.getUniqueId());
                double regenAmount = mana.getMaxMana() * 0.02; // 2%
                mana.regen(regenAmount);
            }
        }, 20L, 20L); // 毎秒実行

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
        // リスナー登録
        getServer().getPluginManager().registerEvents(new ItemDurabilityFix(),this);
        getServer().getPluginManager().registerEvents(new AttributeGui(), this);
        getServer().getPluginManager().registerEvents(new BlacksmithListener(), this);
        getServer().getPluginManager().registerEvents(new DropPreventionListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerInteractListener(),this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this,playerQuestManager),this);
        Bukkit.getPluginManager().registerEvents(new GUIListener(playerQuestManager), this);
        getServer().getPluginManager().registerEvents(new CustomOreListener(this), this);

        getCommand("skills").setExecutor(new SkillAssignmentCommand());
        getCommand("blacksmith").setExecutor(new BlacksmithCommand());
        getCommand("questnpc").setExecutor(new QuestCommand(this, guildQuestManager));
        getCommand("task").setExecutor(new TaskCommand(this));
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        for (Player p : Bukkit.getOnlinePlayers()) {
            levelManager.unload(p.getUniqueId());
            attributeManager.unload(p.getUniqueId());
        }
        townBurstManager.stopBurstTask();
        mythicMobSafeZoneManager.stopCheckTask();
        lootChestManager.removeAllLootChests();
        dailyTaskManager.saveAllData();
        skillSlotManager.saveAll();
        artifactManager.saveData();
        guildQuestManager.shutdown();
        saveSafeZoneSpawns();
        shutdownExecutor();
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
