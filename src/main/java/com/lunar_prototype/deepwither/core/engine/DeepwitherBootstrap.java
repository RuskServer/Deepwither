package com.lunar_prototype.deepwither.core.engine;

import com.lunar_prototype.deepwither.Deepwither;
import java.util.logging.Logger;

public class DeepwitherBootstrap {

    private final Deepwither plugin;
    private final ServiceContainer container;
    private final ModuleManager moduleManager;
    private final Logger logger;

    /**
     * Creates a bootstrapper that wires core services, prepares the service container and module manager,
     * and registers foundational instances for the Deepwither engine.
     *
     * @param plugin the main Deepwither plugin instance used to obtain configuration and logging
     */
    public DeepwitherBootstrap(Deepwither plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.container = new ServiceContainer(logger);
        this.moduleManager = new ModuleManager(container, logger);

        // 基本サービスの登録
        container.registerInstance(Deepwither.class, plugin);
        container.registerInstance(Logger.class, logger);
    }

    /**
     * Bootstraps and starts the Deepwither engine by registering, configuring, and starting modules.
     *
     * Performs module discovery and registration, runs module configuration, and starts all modules.
     * Logs initialization progress and completion.
     */
    public void onEnable() {
        logger.info("Initializing Deepwither Engine...");

        // モジュールのディスカバリと登録
        registerModules();

        // 構成フェーズ
        moduleManager.configureModules();

        // 開始フェーズ
        moduleManager.startModules();

        logger.info("Deepwither Engine initialized successfully.");
    }

    /**
     * Initiates shutdown of the Deepwither engine.
     *
     * Stops all registered modules to perform an orderly shutdown and release associated resources. 
     */
    public void onDisable() {
        logger.info("Shutting down Deepwither Engine...");

        // 停止フェーズ
        moduleManager.stopModules();

        logger.info("Deepwither Engine shutdown complete.");
    }

    /**
     * Registers the built-in engine modules with the ModuleManager in the bootstrap order.
     *
     * <p>The following modules are registered in sequence: Core, Infrastructure, Combat, Economy,
     * Quest, Integration, DynamicQuest, and Legacy (wrapper for existing functionality). The
     * registration order is intentional to ensure dependencies between modules are satisfied.
     *
     * <p>Currently modules are registered explicitly; future implementations may support classpath
     * scanning or loading modules from external JARs.
     */
    private void registerModules() {
        logger.info("Registering modules...");
        // ここにモジュールを登録していく
        // 将来的にはクラスパススキャンや外部Jar読み込みに対応予定

        moduleManager.registerModule(new com.lunar_prototype.deepwither.modules.core.CoreModule(plugin));
        moduleManager
                .registerModule(new com.lunar_prototype.deepwither.modules.infrastructure.InfrastructureModule(plugin));

        // Phase 3 Modules
        moduleManager.registerModule(new com.lunar_prototype.deepwither.modules.combat.CombatModule(plugin));
        moduleManager.registerModule(new com.lunar_prototype.deepwither.modules.economy.EconomyModule(plugin));
        moduleManager.registerModule(new com.lunar_prototype.deepwither.modules.quest.QuestModule(plugin));
        moduleManager.registerModule(new com.lunar_prototype.deepwither.modules.integration.IntegrationModule(plugin));
        moduleManager.registerModule(new com.lunar_prototype.deepwither.modules.dynamic_quest.DynamicQuestModule(plugin));

        // Legacy Module (既存機能のラップ)
        moduleManager.registerModule(new LegacyModule(plugin));
        logger.info("Modules registered.");
    }

    /**
     * Provides access to the internal ServiceContainer used for dependency registration and retrieval.
     *
     * @return the bootstrap's ServiceContainer instance
     */
    public ServiceContainer getContainer() {
        return container;
    }
}