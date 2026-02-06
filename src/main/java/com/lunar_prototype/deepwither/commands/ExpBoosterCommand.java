package com.lunar_prototype.deepwither.commands;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.commands.util.CommandUtil;
import com.lunar_prototype.deepwither.commands.api.DeepwitherCommand;
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
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import static io.papermc.paper.command.brigadier.Commands.*;

public final class ExpBoosterCommand implements DeepwitherCommand {
    private static final String ARG_PLAYER = "player";
    private static final String ARG_MULTIPLIER = "multiplier";
    private static final String ARG_MINUTES = "minutes";
    private final Deepwither plugin;

    public ExpBoosterCommand(Deepwither plugin) {
        this.plugin = plugin;
    }

    @NotNull
    @Override
    public String name() {
        return "expbooster";
    }

    @NotNull
    @Override
    public LiteralCommandNode<CommandSourceStack> node() {
        return literal(this.name())
            .requires(CommandUtil::isAdmin)
            .then(literal("give")
                .then(argument(ARG_PLAYER, ArgumentTypes.players())
                    .then(argument(ARG_MULTIPLIER, DoubleArgumentType.doubleArg(0))
                        .then(argument(ARG_MINUTES, IntegerArgumentType.integer(0))
                            .executes(this::executeGive)))))
            .build();
    }

    private int executeGive(@NotNull CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandUtil.asPlayer(context).ifPresent(player -> {
            var multiplier = DoubleArgumentType.getDouble(context, ARG_MULTIPLIER);
            var minutes = IntegerArgumentType.getInteger(context, ARG_MINUTES);

            for (var target : CommandUtil.resolvePlayerSelector(context, ARG_PLAYER)) {
                this.plugin.getBoosterManager().addBooster(target, multiplier, minutes);
                player.sendMessage(Component.text("%sに%d分のExpブースター(x%.2f)を与えました".formatted(target.getName(), minutes, multiplier), NamedTextColor.GREEN));
                target.sendMessage(Component.empty()
                    .append(Component.text("Expブースターを受け取りました!", NamedTextColor.GOLD))
                    .append(Component.text("%d分の間、取得経験値が%.2f倍されます!".formatted(minutes, multiplier), NamedTextColor.YELLOW)));
            }
        });

        return Command.SINGLE_SUCCESS;
    }
}
