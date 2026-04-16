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

        // 基本サービスの登録
        container.registerInstance(Deepwither.class, plugin);
        // 親クラスやインターフェース型でも解決できるように登録
        container.registerInstance(org.bukkit.plugin.java.JavaPlugin.class, plugin);
        container.registerInstance(com.lunar_prototype.deepwither.api.DeepwitherAPI.class, plugin);
        
        container.registerInstance(Logger.class, logger);
        container.registerInstance(ModuleRegistrar.class, new ModuleRegistrar(plugin, logger));

        this.moduleManager = new ModuleManager(container, logger);
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

        // オーラマネージャーの定期処理タスクの開始 (毎回20tick = 1秒ごと)
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                com.lunar_prototype.deepwither.api.skill.aura.AuraManager auraManager = plugin.getAuraManager();
                if (auraManager != null) {
                    auraManager.tick();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);

        // 武器の視覚演出タスクの開始 (2tick毎)
        new com.lunar_prototype.deepwither.WeaponEffectTask(plugin).runTaskTimer(plugin, 5L, 2L);
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
        
        // 1. Core & Foundation
        moduleManager.registerModule(new com.lunar_prototype.deepwither.modules.core.CoreModule(plugin));
        moduleManager
                .registerModule(new com.lunar_prototype.deepwither.modules.infrastructure.InfrastructureModule(plugin));

        // 2. Functional Modules
        moduleManager.registerModule(new com.lunar_prototype.deepwither.modules.combat.CombatModule(plugin));
        moduleManager.registerModule(new com.lunar_prototype.deepwither.modules.economy.EconomyModule(plugin));
        moduleManager.registerModule(new com.lunar_prototype.deepwither.modules.chat.ChatModule(plugin));
        moduleManager.registerModule(new com.lunar_prototype.deepwither.modules.quest.QuestModule(plugin));
        moduleManager.registerModule(new com.lunar_prototype.deepwither.modules.aethelgard.AethelgardModule(plugin));
        moduleManager.registerModule(new com.lunar_prototype.deepwither.modules.mob.MobModule(plugin));
        moduleManager.registerModule(new com.lunar_prototype.deepwither.modules.integration.IntegrationModule(plugin));
        moduleManager.registerModule(new com.lunar_prototype.deepwither.modules.outpost.OutpostModule(plugin));
        moduleManager.registerModule(new com.lunar_prototype.deepwither.modules.minidungeon.MiniDungeonModule(plugin));
        moduleManager.registerModule(new com.lunar_prototype.deepwither.modules.minerun.MineRunModule(plugin));

        // 3. Legacy Module (既存機能のインスタンス生成と登録)
        // 他の全てのモジュールがコンテナにManagerを登録した「後」に実行し、
        // 依存関係（DamageProcessorなど）を安全に get できるようにする
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
