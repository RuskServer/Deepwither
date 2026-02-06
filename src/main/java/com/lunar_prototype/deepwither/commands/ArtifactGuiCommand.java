package com.lunar_prototype.deepwither.commands;

import com.lunar_prototype.deepwither.ArtifactGUI;
import com.lunar_prototype.deepwither.commands.util.CommandUtil;
import com.lunar_prototype.deepwither.commands.api.DeepwitherCommand;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.jetbrains.annotations.NotNull;

import static io.papermc.paper.command.brigadier.Commands.*;

public final class ArtifactGuiCommand implements DeepwitherCommand {
    @NotNull
    @Override
    public String name() {
        return "artifact";
    }

    @NotNull
    @Override
    public LiteralCommandNode<CommandSourceStack> node() {
        return literal(name())
            .requires(CommandUtil::isPlayer)
            .executes(context -> {
                CommandUtil.asPlayer(context).ifPresent(player -> {
                    new ArtifactGUI().openArtifactGUI(player.getPlayer());
                });

                return Command.SINGLE_SUCCESS;
            })
            .build();
    }
}
