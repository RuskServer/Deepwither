package com.lunar_prototype.deepwither.core.engine;

import com.lunar_prototype.deepwither.Deepwither;
import java.util.logging.Logger;

public class DeepwitherBootstrap {

    private final Deepwither plugin;
    private final ServiceContainer container;
    private final ModuleManager moduleManager;
    private final Logger logger;

    public DeepwitherBootstrap(Deepwither plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.container = new ServiceContainer(logger);
        this.moduleManager = new ModuleManager(container, logger);

        // 基本サービスの登録
        container.registerInstance(Deepwither.class, plugin);
        container.registerInstance(Logger.class, logger);
    }

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

    public void onDisable() {
        logger.info("Shutting down Deepwither Engine...");

        // 停止フェーズ
        moduleManager.stopModules();

        logger.info("Deepwither Engine shutdown complete.");
    }

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
        moduleManager.registerModule(new com.lunar_prototype.deepwither.modules.dynamic_quest.DynamicQuestModule(plugin));

        // Legacy Module (既存機能のラップ)
        moduleManager.registerModule(new LegacyModule(plugin));
        logger.info("Modules registered.");
    }

    public ServiceContainer getContainer() {
        return container;
    }
}
