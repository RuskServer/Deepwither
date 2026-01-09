package com.lunar_prototype.deepwither.commands;

import com.lunar_prototype.deepwither.commands.api.DeepWitherCommand;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class CommandRegistrar {
    public static void registerCommands(@SuppressWarnings("NullableProblems") @NotNull LifecycleEventManager<Plugin> manager, List<DeepWitherCommand> commands) {
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            commands.forEach(command -> {
                event.registrar().register(command.getNode(), command.getDescription());
            });
        });
    }

    private CommandRegistrar() {}
}
