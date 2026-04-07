package com.lunar_prototype.deepwither.modules.mine;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.core.engine.IModule;
import com.lunar_prototype.deepwither.core.engine.ModuleRegistrar;
import com.lunar_prototype.deepwither.core.engine.ServiceContainer;

public class MineModule implements IModule {

    private final Deepwither plugin;

    public MineModule(Deepwither plugin) {
        this.plugin = plugin;
    }

    @Override
    public void configure(ServiceContainer container) {
        plugin.getLogger().info("MineModule: configure()");

        MiningSkillService miningSkillService = new MiningSkillService(plugin);
        MineService mineService = new MineService(plugin, miningSkillService);
        container.registerInstance(MiningSkillService.class, miningSkillService);
        container.registerInstance(MineService.class, mineService);
    }

    @Override
    public void start() {
        ServiceContainer container = plugin.getBootstrap().getContainer();
        ModuleRegistrar registrar = container.get(ModuleRegistrar.class);
        MineService mineService = container.get(MineService.class);

        mineService.init();
        registrar.registerListener(new MineListener(mineService));
        registrar.registerListener(new MineMobSpawnListener(mineService));
    }

    @Override
    public void stop() {
        ServiceContainer container = plugin.getBootstrap().getContainer();
        MineService mineService = container.get(MineService.class);
        mineService.shutdown();
    }
}
