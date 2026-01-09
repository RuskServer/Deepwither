package com.lunar_prototype.deepwither.commands;

import com.lunar_prototype.deepwither.booster.BoosterManager;
import com.lunar_prototype.deepwither.commands.api.DeepWitherCommand;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.NotNull;

import static io.papermc.paper.command.brigadier.Commands.*;

public class BoosterCommand implements DeepWitherCommand {
    private static final String COMMAND_NAME = "expbooster";
    private static final String ARG_PLAYER = "player";
    private static final String ARG_MULTIPLIER = "multiplier";
    private static final String ARG_MINUTES = "minutes";

    private final BoosterManager boosterManager;

    public BoosterCommand(BoosterManager boosterManager) {
        this.boosterManager = boosterManager;
    }

    private int executes(@NotNull CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var sender = context.getSource().getSender();
        var target = context.getArgument(ARG_PLAYER, PlayerSelectorArgumentResolver.class).resolve(context.getSource()).getFirst();
        var multiplier = DoubleArgumentType.getDouble(context, ARG_MULTIPLIER);
        var minutes = IntegerArgumentType.getInteger(context, ARG_MINUTES);

        this.boosterManager.addBooster(target, multiplier, minutes);
        sender.sendMessage(Component.text("Applied %fx EXP Booster to %s for %dm.".formatted(multiplier, target.getName(), minutes), NamedTextColor.GREEN));
        target.sendMessage(Component.empty()
            .append(Component.text("EXP BOOSTER! ", NamedTextColor.GOLD, TextDecoration.BOLD))
            .append(Component.text("You gained a %.3fx EXP multiplier for %d minutes!".formatted(multiplier, minutes), NamedTextColor.YELLOW)));

        return Command.SINGLE_SUCCESS;
    }

    @NotNull
    @Override
    public LiteralCommandNode<CommandSourceStack> getNode() {
        return literal(COMMAND_NAME)
            .requires(CommandUtil::hasAdminPermission)
            .then(literal("give")
                .then(argument(ARG_PLAYER, ArgumentTypes.player())
                    .then(argument(ARG_MULTIPLIER, DoubleArgumentType.doubleArg(0)) // 追加: 下限を追加
                        .then(argument(ARG_MINUTES, IntegerArgumentType.integer(0)) // 同一
                            .executes(this::executes)))))
            .build();
    }

    @NotNull
    @Override
    public String getDescription() {
        return "経験値ブースターを付与します";
    }
}
