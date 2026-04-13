package com.lunar_prototype.deepwither.modules.aethelgard;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.DatabaseManager;
import com.lunar_prototype.deepwither.LevelManager;
import com.lunar_prototype.deepwither.ItemFactory;
import com.lunar_prototype.deepwither.api.IItemFactory;
import com.lunar_prototype.deepwither.core.engine.IModule;
import com.lunar_prototype.deepwither.core.engine.ModuleRegistrar;
import com.lunar_prototype.deepwither.core.engine.ServiceContainer;
import com.lunar_prototype.deepwither.data.QuestDataStore;
import com.lunar_prototype.deepwither.data.FilePlayerQuestDataStore;
import com.lunar_prototype.deepwither.data.PlayerQuestDataStore;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.serialization.ConfigurationSerialization;

public class AethelgardModule implements IModule {

    private final Deepwither plugin;

    public AethelgardModule(Deepwither plugin) {
        this.plugin = plugin;
    }

    @Override
    public void configure(ServiceContainer container) {
        plugin.getLogger().info("AethelgardModule: configure()");

        // Configuration Serialization
        ConfigurationSerialization.registerClass(RewardDetails.class);
        ConfigurationSerialization.registerClass(LocationDetails.class);
        ConfigurationSerialization.registerClass(GeneratedQuest.class);

        try {
            DatabaseManager dbManager = container.get(DatabaseManager.class);
            LevelManager levelManager = container.get(LevelManager.class);
            ItemFactory itemFactory = container.get(ItemFactory.class);
            Economy economy = Deepwither.getEconomy();

            // Data Stores
            QuestDataStore questDataStore = new QuestDataStore(plugin);
            container.registerInstance(QuestDataStore.class, questDataStore);

            FilePlayerQuestDataStore filePlayerQuestDataStore = new FilePlayerQuestDataStore(dbManager);
            container.registerInstance(PlayerQuestDataStore.class, filePlayerQuestDataStore);

            // Component Pool
            QuestComponentPool componentPool = new QuestComponentPool(plugin);
            container.registerInstance(QuestComponentPool.class, componentPool);

            // Managers
            GuildQuestManager guildQuestManager = new GuildQuestManager(plugin, questDataStore, componentPool);
            container.registerInstance(GuildQuestManager.class, guildQuestManager);

            PlayerQuestManager playerQuestManager = new PlayerQuestManager(
                    plugin, 
                    guildQuestManager, 
                    filePlayerQuestDataStore,
                    levelManager,
                    itemFactory,
                    economy
            );
            container.registerInstance(PlayerQuestManager.class, playerQuestManager);

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to configure AethelgardModule components.");
            e.printStackTrace();
        }
    }

    @Override
    public void start() {
        ServiceContainer container = plugin.getBootstrap().getContainer();
        ModuleRegistrar registrar = container.get(ModuleRegistrar.class);

        try {
            plugin.getLogger().info("Starting Aethelgard Module (Listeners & Commands)...");

            // Commands & Listeners
            PlayerQuestManager playerQuestManager = container.get(PlayerQuestManager.class);
            GuildQuestManager guildQuestManager = container.get(GuildQuestManager.class);

            registrar.registerCommand("questnpc", new QuestCommand(plugin, guildQuestManager));
            registrar.registerListener(new GUIListener(playerQuestManager));

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to start Aethelgard Module.");
            e.printStackTrace();
        }
    }

    @Override
    public void stop() {
        plugin.getLogger().info("Stopping Aethelgard Module...");
    }
}
