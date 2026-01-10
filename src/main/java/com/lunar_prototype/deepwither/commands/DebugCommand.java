package com.lunar_prototype.deepwither.commands;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.commands.api.DeepWitherCommand;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import org.jetbrains.annotations.NotNull;

import static io.papermc.paper.command.brigadier.Commands.*;

public final class DebugCommand implements DeepWitherCommand {
    private final Deepwither plugin;

    public DebugCommand(@NotNull Deepwither plugin) {
        this.plugin = plugin;
    }

    private int resetPlayerLevel(@NotNull CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final var players = context.getArgument("player", PlayerSelectorArgumentResolver.class).resolve(context.getSource());

        for (var player : players) {
            this.plugin.getLevelManager().resetLevel(player);
        }

        return Command.SINGLE_SUCCESS;
    }

    private int addPlayerExp(@NotNull CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final var players = context.getArgument("player", PlayerSelectorArgumentResolver.class).resolve(context.getSource());
        final var exp = DoubleArgumentType.getDouble(context, "exp");

        for (var player : players) {
            this.plugin.getLevelManager().addExp(player, exp);
        }

        return Command.SINGLE_SUCCESS;
    }

    @NotNull
    @Override
    public LiteralCommandNode<CommandSourceStack> getNode() {
        return literal("dw:debug")
            .requires(CommandUtil::hasAdminPermission)
            .then(literal("exp")
                .then(literal("reset")
                    .then(argument("player", ArgumentTypes.player())
                        .executes(this::resetPlayerLevel)))
                .then(literal("add"))
                    .then(argument("player", ArgumentTypes.player())
                        .then(argument("exp", DoubleArgumentType.doubleArg())
                            .executes(this::addPlayerExp))))
            .build();
    }
}
