package com.lunar_prototype.deepwither.commands;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.commands.api.DeepwitherCommand;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.papermc.paper.command.brigadier.Commands.*;

public final class DeepwitherRootCommand implements DeepwitherCommand {
    private final Map<String, DeepwitherCommand> commands = new HashMap<>();

    public DeepwitherRootCommand(Deepwither plugin) {
        add(new PvPCommand());
        add(new DebugCommand(plugin));
        add(new ReloadCommand());
        add(new DungeonCommand());
        add(new ExpBoosterCommand(plugin));
        add(new ArtifactGuiCommand());
        add(new MarketCommand(plugin));
        add(new ResetStatGuiCommand(plugin));
        add(new ClanCommand(plugin));
    }

    @NotNull
    @Override
    public String name() {
        return "deepwither";
    }

    @NotNull
    @Override
    public LiteralCommandNode<CommandSourceStack> node() {
        var root = literal(this.name());

        for (var command : this.commands.values()) {
            root.then(command.node());
        }

        return root.build();
    }

    @Contract(value = " -> new", pure = true)
    @NotNull
    @Override
    public @Unmodifiable Collection<String> aliases() {
        return List.of("dw");
    }

    private void add(DeepwitherCommand command) {
        this.commands.put(command.name(), command);
    }
}
