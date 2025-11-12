package com.lunar_prototype.deepwither;

import io.lumine.mythic.bukkit.events.MythicDropLoadEvent;
import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
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
import java.util.logging.Logger;

public final class  Deepwither extends JavaPlugin {

    private Logger log;

    private static Deepwither instance;
    public static Deepwither getInstance() { return instance; }
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
    public void setStatManager(StatManager statManager) {this.statManager = statManager;}

    public TraderManager getTraderManager() {
        return traderManager;
    }

    public CreditManager getCreditManager() {
        return creditManager;
    }
    public DailyTaskManager getDailyTaskManager() { // ★ 新規追加
        return dailyTaskManager;
    }

    @Override
    public void onEnable() {
        // Plugin startup logic
        instance = this;

        if (!setupEconomy()) {
            getLogger().severe(String.format("[%s] - Disabled due to no Vault dependency found!", getDescription().getName()));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

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
        this.creditManager = new CreditManager(this);
        this.traderManager = new TraderManager(this, itemFactory);
        this.dailyTaskManager = new DailyTaskManager(this);
        artifactManager = new ArtifactManager(this);
        artifactGUI = new ArtifactGUI();
        artifactGUIListener = new ArtifactGUIListener(artifactGUI,statManager);
        this.skillAssignmentGUI = new SkillAssignmentGUI(); // 必ず enable 時に初期化
        getServer().getPluginManager().registerEvents(skillAssignmentGUI, this);
        getServer().getPluginManager().registerEvents(artifactGUIListener, this);
        getServer().getPluginManager().registerEvents(artifactGUI, this);
        getServer().getPluginManager().registerEvents(new CustomDropListener(this),this);
        getServer().getPluginManager().registerEvents(new TaskListener(dailyTaskManager), this);

        this.getCommand("artifact").setExecutor(new ArtifactGUICommand(artifactGUI));
        getCommand("trader").setExecutor(new TraderCommand(traderManager));
        getCommand("credit").setExecutor(new CreditCommand(creditManager));
        getServer().getPluginManager().registerEvents(new TraderGUI(), this);
        getServer().getPluginManager().registerEvents(new SellGUI(), this);

        new RegenTask(statManager).start(this);

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
        getServer().getPluginManager().registerEvents(new SafeZoneListener(),this);
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
            }
        },this);

        // ログイン・ログアウト同期
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onJoin(PlayerJoinEvent e) {
                levelManager.load(e.getPlayer().getUniqueId());
                attributeManager.load(e.getPlayer().getUniqueId());
                skilltreeManager.load(e.getPlayer().getUniqueId());
            }
            @EventHandler
            public void onQuit(PlayerQuitEvent e) {
                levelManager.unload(e.getPlayer().getUniqueId());
                attributeManager.unload(e.getPlayer().getUniqueId());
            }
        }, this);

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                ManaData mana = Deepwither.getInstance().getManaManager().get(p.getUniqueId());
                double regenAmount = mana.getMaxMana() * 0.02; // 2%
                mana.regen(regenAmount);
            }
        }, 20L, 20L); // 毎秒実行

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new LevelPlaceholderExpansion(levelManager,manaManager,statManager).register();
            getLogger().info("PlaceholderAPI拡張を登録しました。");
        }
        // コマンド登録
        getCommand("attributes").setExecutor(new AttributeCommand());
        try {
            SkilltreeGUI gui = new SkilltreeGUI(this, getDataFolder(),skilltreeManager);
            getCommand("skilltree").setExecutor(gui);
        } catch (IOException e) {
            e.printStackTrace();
        }
        // リスナー登録
        getServer().getPluginManager().registerEvents(new AttributeGui(), this);
        getCommand("skills").setExecutor(new SkillAssignmentCommand());
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        for (Player p : Bukkit.getOnlinePlayers()) {
            levelManager.unload(p.getUniqueId());
            attributeManager.unload(p.getUniqueId());
        }
        skillSlotManager.saveAll();
        artifactManager.saveData();
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
}
