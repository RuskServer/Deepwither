package com.lunar_prototype.deepwither.modules.rune;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.ItemFactory;
import com.lunar_prototype.deepwither.core.engine.IModule;
import com.lunar_prototype.deepwither.core.engine.ServiceContainer;
import com.lunar_prototype.deepwither.util.IManager;

public class RuneModule implements IModule {

    private final Deepwither plugin;

    public RuneModule(Deepwither plugin) {
        this.plugin = plugin;
    }

    @Override
    public void configure(ServiceContainer container) {
        plugin.getLogger().info("RuneModule: configure()");

        ItemFactory itemFactory = container.get(ItemFactory.class);
        RuneManager runeManager = new RuneManager(plugin, itemFactory);
        container.registerInstance(RuneManager.class, runeManager);

        RuneSocketGUI runeSocketGUI = new RuneSocketGUI(plugin, runeManager);
        container.registerInstance(RuneSocketGUI.class, runeSocketGUI);
    }

    @Override
    public void start() {
        ServiceContainer container = plugin.getBootstrap().getContainer();
        initSafely(container, RuneManager.class);
        initSafely(container, RuneSocketGUI.class);
        
        plugin.getCommand("rune").setExecutor(new RuneCommand(container.get(RuneSocketGUI.class), container.get(RuneManager.class)));
    }

    @Override
    public void stop() {
        ServiceContainer container = plugin.getBootstrap().getContainer();
        try {
            container.get(RuneManager.class).shutdown();
            container.get(RuneSocketGUI.class).shutdown();
        } catch (Exception e) {
            // Log if needed
        }
    }

    private <T extends IManager> void initSafely(ServiceContainer container, Class<T> clazz) {
        try {
            container.get(clazz).init();
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize " + clazz.getSimpleName());
            e.printStackTrace();
        }
    }
}
