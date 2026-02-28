package com.lunar_prototype.deepwither.modules.quest;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.core.engine.IModule;
import com.lunar_prototype.deepwither.core.engine.ServiceContainer;
import com.lunar_prototype.deepwither.modules.economy.trader.DailyTaskManager;
import com.lunar_prototype.deepwither.aethelgard.GuildQuestManager;
import com.lunar_prototype.deepwither.aethelgard.PlayerQuestManager;
import com.lunar_prototype.deepwither.data.QuestDataStore;
import com.lunar_prototype.deepwither.data.FileDailyTaskDataStore;
import com.lunar_prototype.deepwither.data.FilePlayerQuestDataStore;
import com.lunar_prototype.deepwither.data.PlayerQuestDataStore;
import com.lunar_prototype.deepwither.DatabaseManager;

public class QuestModule implements IModule {

    private final Deepwither plugin;

    /**
     * Creates a QuestModule bound to the given Deepwither plugin instance.
     *
     * @param plugin the main Deepwither plugin instance used by this module for lifecycle management and access to shared services
     */
    public QuestModule(Deepwither plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers and configures quest-related data stores and managers into the provided service container.
     *
     * <p>The method creates and registers the following components:
     * FileDailyTaskDataStore, QuestDataStore, FilePlayerQuestDataStore (registered as PlayerQuestDataStore),
     * DailyTaskManager, GuildQuestManager, and PlayerQuestManager.</p>
     *
     * @param container the service container used to register the module's components
     */
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

    /**
     * Initializes quest-related data stores and manager components retrieved from the service container.
     *
     * <p>This method obtains the service container from the plugin bootstrap and calls initialization on
     * the registered quest data stores and managers so they are ready for use. Exceptions thrown during
     * initialization are caught and their stack traces are printed.</p>
     */
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

    /**
     * Shuts down quest-related managers and data stores retrieved from the plugin's service container.
     *
     * Shutdown is performed in this order: PlayerQuestManager, GuildQuestManager, DailyTaskManager,
     * FilePlayerQuestDataStore (cast from PlayerQuestDataStore), QuestDataStore, FileDailyTaskDataStore.
     *
     * Any exception encountered during shutdown is logged and the stack trace is printed.
     */
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