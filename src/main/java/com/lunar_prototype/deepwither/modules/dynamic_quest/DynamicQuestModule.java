package com.lunar_prototype.deepwither.modules.dynamic_quest;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.core.engine.IModule;
import com.lunar_prototype.deepwither.core.engine.ServiceContainer;
import com.lunar_prototype.deepwither.modules.dynamic_quest.repository.QuestLocationRepository;
import com.lunar_prototype.deepwither.modules.dynamic_quest.service.QuestNPCManager;
import com.lunar_prototype.deepwither.modules.dynamic_quest.service.QuestService;
import com.lunar_prototype.deepwither.modules.dynamic_quest.listener.QuestListener;
import com.lunar_prototype.deepwither.modules.integration.service.IMobService;
import org.bukkit.Bukkit;

public class DynamicQuestModule implements IModule {

    private final Deepwither plugin;

    /**
     * Create a DynamicQuestModule bound to the provided Deepwither plugin.
     *
     * @param plugin the main plugin instance used to access the bootstrap, service container, and plugin registration facilities
     */
    public DynamicQuestModule(Deepwither plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers dynamic quest components into the provided service container.
     *
     * <p>Creates and registers a QuestLocationRepository, retrieves an existing IMobService
     * from the container to construct a QuestNPCManager, and creates and registers a QuestService.
     * The method expects an IMobService instance to already be registered in the container.
     */
    @Override
    public void configure(ServiceContainer container) {
        QuestLocationRepository repository = new QuestLocationRepository(plugin);
        container.registerInstance(QuestLocationRepository.class, repository);

        // Get IMobService from container (registered by IntegrationModule)
        IMobService mobService = container.get(IMobService.class);
        QuestNPCManager npcManager = new QuestNPCManager(plugin, repository, mobService);
        container.registerInstance(QuestNPCManager.class, npcManager);
        
        QuestService questService = new QuestService(plugin, npcManager);
        container.registerInstance(QuestService.class, questService);
    }

    /**
     * Starts the dynamic quest subsystem: loads quest locations, initializes the NPC manager,
     * registers event listeners, and wires the "dq" command with its executor and tab completer.
     *
     * <p>If the "dq" command is not declared in plugin.yml, a warning is logged instead of registering it.</p>
     */
    @Override
    public void start() {
        ServiceContainer container = plugin.getBootstrap().getContainer();
        
        QuestLocationRepository repository = container.get(QuestLocationRepository.class);
        repository.load();
        
        QuestNPCManager npcManager = container.get(QuestNPCManager.class);
        npcManager.init();
        
        QuestListener listener = new QuestListener(plugin, npcManager);
        Bukkit.getPluginManager().registerEvents(listener, plugin);

        // Register commands
        QuestService questService = container.get(QuestService.class);
        DynamicQuestCommand command = new DynamicQuestCommand(npcManager, questService, repository);
        var dqCommand = plugin.getCommand("dq");
        if (dqCommand != null) {
            dqCommand.setExecutor(command);
            dqCommand.setTabCompleter(new DynamicQuestTabCompleter());
        } else {
            plugin.getLogger().warning("DynamicQuestModule: 'dq' command not found in plugin.yml");
        }
    }

    /**
     * Stops dynamic-quest NPC processing and releases related resources.
     *
     * Retrieves the application's service container and invokes shutdown on the registered
     * QuestNPCManager to cleanly stop NPC-related routines.
     */
    @Override
    public void stop() {
        ServiceContainer container = plugin.getBootstrap().getContainer();
        container.get(QuestNPCManager.class).shutdown();
    }
}