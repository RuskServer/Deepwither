package com.lunar_prototype.deepwither.commands;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.ResetGUI;
import com.lunar_prototype.deepwither.commands.api.DeepwitherCommand;
import com.lunar_prototype.deepwither.commands.util.CommandUtil;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.NotNull;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

public final class ResetStatGuiCommand implements DeepwitherCommand {
    private static final String ARG_PLAYER = "player";
    private final ResetGUI gui;

    public ResetStatGuiCommand(Deepwither plugin) {
        this.gui = new ResetGUI(plugin);
    }

    @NotNull
    @Override
    public String name() {
        return "resetstat";
    }

    @NotNull
    @Override
    public LiteralCommandNode<CommandSourceStack> node() {
        return literal(name())
            .requires(CommandUtil::isAdmin)
            .then(argument(ARG_PLAYER, ArgumentTypes.players())
                .executes(context -> {
                    CommandUtil.asPlayer(context).ifPresent(player -> {
                        for (var target : CommandUtil.resolvePlayerSelector(context, ARG_PLAYER)) {
                            gui.open(target);
                            player.sendMessage(player.displayName().append(Component.text("にリセットメニューを開きました", NamedTextColor.GREEN)));
                        }
                    });

                    return Command.SINGLE_SUCCESS;
                })
            )
            .build();
    }
}
