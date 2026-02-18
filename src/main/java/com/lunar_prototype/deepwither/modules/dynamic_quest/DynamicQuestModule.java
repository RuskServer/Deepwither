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

    public DynamicQuestModule(Deepwither plugin) {
        this.plugin = plugin;
    }

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

    @Override
    public void stop() {
        ServiceContainer container = plugin.getBootstrap().getContainer();
        container.get(QuestNPCManager.class).shutdown();
    }
}
