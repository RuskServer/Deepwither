package com.lunar_prototype.deepwither.modules.chat;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.PlayerSettingsManager;
import com.lunar_prototype.deepwither.core.engine.IModule;
import com.lunar_prototype.deepwither.core.engine.ServiceContainer;
import org.bukkit.Bukkit;

public class ChatModule implements IModule {

    private final Deepwither plugin;
    private ChatConverterManager converterManager;
    private ChatListener chatListener;

    public ChatModule(Deepwither plugin) {
        this.plugin = plugin;
    }

    public String getName() {
        return "Chat";
    }

    @Override
    public void configure(ServiceContainer container) {
        this.converterManager = new ChatConverterManager();
        container.registerInstance(ChatConverterManager.class, converterManager);

        PlayerSettingsManager settingsManager = container.get(PlayerSettingsManager.class);
        this.chatListener = new ChatListener(converterManager, settingsManager);
    }

    @Override
    public void start() {
        converterManager.init();
        Bukkit.getPluginManager().registerEvents(chatListener, plugin);
        
        PlayerSettingsManager settingsManager = plugin.getSettingsManager();
        ChatCommand chatCommand = new ChatCommand(settingsManager);
        plugin.getCommand("japan").setExecutor(chatCommand);
    }

    @Override
    public void stop() {
        if (converterManager != null) {
            converterManager.shutdown();
        }
    }
}
