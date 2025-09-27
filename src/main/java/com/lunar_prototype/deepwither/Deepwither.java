package com.lunar_prototype.deepwither;

import io.lumine.mythic.bukkit.events.MythicDropLoadEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import com.lunar_prototype.deepwither.LevelManager;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.logging.Logger;

public final class Deepwither extends JavaPlugin {

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
    public ArtifactGUI artifactGUI;

    public AttributeManager getAttributeManager() {
        return attributeManager;
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

    @Override
    public void onEnable() {
        // Plugin startup logic
        instance = this;
        new ItemFactory(this);
        getServer().getPluginManager().registerEvents(new PlayerStatListener(), this);
        Bukkit.getPluginManager().registerEvents(new DamageManager(), this);
        Bukkit.getPluginManager().registerEvents(new SkillCastSessionManager(),this);
        Bukkit.getPluginManager().registerEvents(new SkillAssignmentGUI(),this);
        manaManager = new ManaManager();
        skillLoader = new SkillLoader();
        File skillsFolder = new File(getDataFolder(), "skills");
        skillLoader.loadAllSkills(skillsFolder);
        skillSlotManager = new SkillSlotManager(getDataFolder());
        skillCastManager = new SkillCastManager();
        cooldownManager = new CooldownManager();
        artifactManager = new ArtifactManager(this);
        artifactGUI = new ArtifactGUI();
        artifactGUIListener = new ArtifactGUIListener(artifactGUI);
        this.skillAssignmentGUI = new SkillAssignmentGUI(); // 必ず enable 時に初期化
        getServer().getPluginManager().registerEvents(skillAssignmentGUI, this);
        getServer().getPluginManager().registerEvents(artifactGUIListener, this);
        getServer().getPluginManager().registerEvents(artifactGUI, this);
        getServer().getPluginManager().registerEvents(new CustomDropListener(this),this);

        this.getCommand("artifact").setExecutor(new ArtifactGUICommand(artifactGUI));

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
            new LevelPlaceholderExpansion(levelManager,manaManager).register();
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
}
