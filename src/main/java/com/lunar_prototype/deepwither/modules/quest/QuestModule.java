package com.lunar_prototype.deepwither.modules.quest;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.core.engine.IModule;
import com.lunar_prototype.deepwither.core.engine.ServiceContainer;
import com.lunar_prototype.deepwither.DailyTaskManager;
import com.lunar_prototype.deepwither.aethelgard.GuildQuestManager;
import com.lunar_prototype.deepwither.aethelgard.PlayerQuestManager;
import com.lunar_prototype.deepwither.data.QuestDataStore;
import com.lunar_prototype.deepwither.data.FileDailyTaskDataStore;
import com.lunar_prototype.deepwither.data.FilePlayerQuestDataStore;
import com.lunar_prototype.deepwither.data.PlayerQuestDataStore;
import com.lunar_prototype.deepwither.DatabaseManager;

public class QuestModule implements IModule {

    private final Deepwither plugin;

    public QuestModule(Deepwither plugin) {
        this.plugin = plugin;
    }

    @Override
    public void configure(ServiceContainer container) {
        plugin.getLogger().info("QuestModule: configure()");

        try {
            DatabaseManager dbManager = container.get(DatabaseManager.class);

            // Data Stores
            FileDailyTaskDataStore fileDailyTaskDataStore = new FileDailyTaskDataStore(plugin, dbManager);
            container.registerInstance(FileDailyTaskDataStore.class, fileDailyTaskDataStore);

            QuestDataStore questDataStore = new QuestDataStore(plugin);
            container.registerInstance(QuestDataStore.class, questDataStore);

            FilePlayerQuestDataStore filePlayerQuestDataStore = new FilePlayerQuestDataStore(dbManager);
            container.registerInstance(PlayerQuestDataStore.class, filePlayerQuestDataStore);
            // Also register as implementation class if needed, checking Deepwither usage

            // Managers
            DailyTaskManager dailyTaskManager = new DailyTaskManager(plugin, fileDailyTaskDataStore);
            container.registerInstance(DailyTaskManager.class, dailyTaskManager);

            GuildQuestManager guildQuestManager = new GuildQuestManager(plugin, questDataStore);
            container.registerInstance(GuildQuestManager.class, guildQuestManager);

            PlayerQuestManager playerQuestManager = new PlayerQuestManager(plugin, guildQuestManager,
                    filePlayerQuestDataStore);
            container.registerInstance(PlayerQuestManager.class, playerQuestManager);

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to configure QuestModule components.");
            e.printStackTrace();
        }
    }

    @Override
    public void start() {
        ServiceContainer container = plugin.getBootstrap().getContainer();
        try {
            plugin.getLogger().info("Initializing Quest Managers...");

            // Init DataStores if they implement IManager
            container.get(FileDailyTaskDataStore.class).init();
            container.get(QuestDataStore.class).init();
            // FilePlayerQuestDataStore implements IManager? Likely yes.
            ((FilePlayerQuestDataStore) container.get(PlayerQuestDataStore.class)).init();

            // Init Managers
            container.get(DailyTaskManager.class).init();
            container.get(GuildQuestManager.class).init();
            container.get(PlayerQuestManager.class).init();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void stop() {
        ServiceContainer container = plugin.getBootstrap().getContainer();
        try {
            container.get(PlayerQuestManager.class).shutdown();
            container.get(GuildQuestManager.class).shutdown();
            container.get(DailyTaskManager.class).shutdown();
            // Shutdown Stores
            ((FilePlayerQuestDataStore) container.get(PlayerQuestDataStore.class)).shutdown();
            container.get(QuestDataStore.class).shutdown();
            container.get(FileDailyTaskDataStore.class).shutdown();
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to stop QuestModule components.");
            e.printStackTrace();
        }
    }
}
