package com.lunar_prototype.deepwither.core.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import com.lunar_prototype.deepwither.Deepwither;

/**
 * モジュール単位のリスナー登録とコマンド登録をまとめて管理するレジストリ。
 *
 * <p>
 * モジュール実装は {@link ModuleManager} が設定する現在のモジュール文脈の中で
 * このクラスを利用することで、Bukkit の直接呼び出しを散在させずに登録処理を書けます。
 * </p>
 */
public class ModuleRegistrar {

    private final Deepwither plugin;
    private final Logger logger;
    private final Map<IModule, ModuleRegistrationState> registrations = new LinkedHashMap<>();
    private final ThreadLocal<IModule> currentModule = new ThreadLocal<>();

    /**
     * Creates a registrar bound to the plugin instance and logger used for module lifecycle management.
     *
     * @param plugin the main Deepwither plugin instance
     * @param logger the logger used for warnings and lifecycle messages
     */
    public ModuleRegistrar(Deepwither plugin, Logger logger) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Binds the current thread to the given module so no-argument registration calls can infer ownership.
     *
     * @param module the module currently being started or configured
     */
    public void beginModule(IModule module) {
        currentModule.set(Objects.requireNonNull(module, "module"));
    }

    /**
     * Clears the current module binding for the active thread.
     */
    public void endModule() {
        currentModule.remove();
    }

    /**
     * Registers a listener for the module currently bound to this thread.
     *
     * @param listener the listener to register
     * @return true if the listener was newly registered; false if it had already been registered for the module
     */
    public boolean registerListener(Listener listener) {
        return registerListener(requireCurrentModule(), listener);
    }

    /**
     * Registers a listener for the specified module.
     *
     * @param module the module that owns the listener
     * @param listener the listener to register
     * @return true if the listener was newly registered; false if it had already been registered for the module
     */
    public boolean registerListener(IModule module, Listener listener) {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(listener, "listener");

        ModuleRegistrationState state = stateFor(module);
        if (!state.listeners.add(listener)) {
            logger.warning("Duplicate listener registration ignored for module "
                    + module.getClass().getSimpleName() + ": " + listener.getClass().getSimpleName());
            return false;
        }

        Bukkit.getPluginManager().registerEvents(listener, plugin);
        logger.info("Registered listener for " + module.getClass().getSimpleName() + ": "
                + listener.getClass().getSimpleName());
        return true;
    }

    /**
     * Registers a command executor for the module currently bound to this thread.
     *
     * @param commandName the command name declared in plugin.yml
     * @param executor the command executor
     * @return true if the command was found and registered
     */
    public boolean registerCommand(String commandName, CommandExecutor executor) {
        return registerCommand(requireCurrentModule(), commandName, executor, null);
    }

    /**
     * Registers a command executor and tab completer for the module currently bound to this thread.
     *
     * @param commandName the command name declared in plugin.yml
     * @param executor the command executor
     * @param tabCompleter the tab completer
     * @return true if the command was found and registered
     */
    public boolean registerCommand(String commandName, CommandExecutor executor, TabCompleter tabCompleter) {
        return registerCommand(requireCurrentModule(), commandName, executor, tabCompleter);
    }

    /**
     * Registers a command executor for the specified module.
     *
     * @param module the module that owns the command registration
     * @param commandName the command name declared in plugin.yml
     * @param executor the command executor
     * @return true if the command was found and registered
     */
    public boolean registerCommand(IModule module, String commandName, CommandExecutor executor) {
        return registerCommand(module, commandName, executor, null);
    }

    /**
     * Registers a command executor and tab completer for the specified module.
     *
     * @param module the module that owns the command registration
     * @param commandName the command name declared in plugin.yml
     * @param executor the command executor
     * @param tabCompleter the tab completer
     * @return true if the command was found and registered
     */
    public boolean registerCommand(IModule module, String commandName, CommandExecutor executor, TabCompleter tabCompleter) {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(commandName, "commandName");
        Objects.requireNonNull(executor, "executor");

        PluginCommand command = plugin.getCommand(commandName);
        if (command == null) {
            logger.warning("Command not found in plugin.yml for module " + module.getClass().getSimpleName()
                    + ": " + commandName);
            return false;
        }

        ModuleRegistrationState state = stateFor(module);
        command.setExecutor(executor);
        command.setTabCompleter(tabCompleter);
        state.commands.put(commandName, command);

        logger.info("Registered command for " + module.getClass().getSimpleName() + ": " + commandName);
        return true;
    }

    /**
     * Removes all listener and command registrations owned by the specified module.
     *
     * @param module the module whose registrations should be cleared
     */
    public void cleanupModule(IModule module) {
        Objects.requireNonNull(module, "module");

        ModuleRegistrationState state = registrations.remove(module);
        if (state == null) {
            return;
        }

        try {
            for (Listener listener : state.listeners) {
                HandlerList.unregisterAll(listener);
            }
        } catch (Exception e) {
            logger.warning("Failed to unregister listeners for " + module.getClass().getSimpleName() + ": " + e);
        }

        try {
            for (PluginCommand command : state.commands.values()) {
                command.setExecutor(null);
                command.setTabCompleter(null);
            }
        } catch (Exception e) {
            logger.warning("Failed to clear commands for " + module.getClass().getSimpleName() + ": " + e);
        }

        logger.info("Cleared registrations for " + module.getClass().getSimpleName());
    }

    /**
     * Removes all tracked registrations for every module.
     */
    public void cleanupAll() {
        List<IModule> modules = new ArrayList<>(registrations.keySet());
        for (IModule module : modules) {
            cleanupModule(module);
        }
    }

    private IModule requireCurrentModule() {
        IModule module = currentModule.get();
        if (module == null) {
            throw new IllegalStateException("No module is currently bound to this thread.");
        }
        return module;
    }

    private ModuleRegistrationState stateFor(IModule module) {
        return registrations.computeIfAbsent(module, ignored -> new ModuleRegistrationState());
    }

    private static final class ModuleRegistrationState {
        private final Set<Listener> listeners = new LinkedHashSet<>();
        private final Map<String, PluginCommand> commands = new LinkedHashMap<>();
    }
}
