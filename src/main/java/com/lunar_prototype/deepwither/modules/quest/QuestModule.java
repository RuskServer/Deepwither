package com.lunar_prototype.deepwither.modules.quest;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.core.engine.IModule;
import com.lunar_prototype.deepwither.core.engine.ServiceContainer;
import com.lunar_prototype.deepwither.modules.economy.trader.DailyTaskManager;
import com.lunar_prototype.deepwither.data.FileDailyTaskDataStore;
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
     * FileDailyTaskDataStore and DailyTaskManager.</p>
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

            // Managers
            DailyTaskManager dailyTaskManager = new DailyTaskManager(plugin, fileDailyTaskDataStore);
            container.registerInstance(DailyTaskManager.class, dailyTaskManager);

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to configure QuestModule components.");
            e.printStackTrace();
        }
    }

    /**
     * Initializes quest-related data stores and manager components retrieved from the service container.
     */
    @Override
    public void start() {
        ServiceContainer container = plugin.getBootstrap().getContainer();
        try {
            plugin.getLogger().info("Initializing Quest Managers...");

            container.get(FileDailyTaskDataStore.class).init();
            container.get(DailyTaskManager.class).init();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Shuts down quest-related managers and data stores retrieved from the plugin's service container.
     */
    @Override
    public void stop() {
        ServiceContainer container = plugin.getBootstrap().getContainer();
        try {
            container.get(DailyTaskManager.class).shutdown();
            container.get(FileDailyTaskDataStore.class).shutdown();
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to stop QuestModule components.");
            e.printStackTrace();
        }
    }
}