package com.lunar_prototype.deepwither.commands;

import com.lunar_prototype.deepwither.commands.api.DeepWitherCommand;
import com.lunar_prototype.deepwither.commands.hmm.PvpWorldHandler;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.jetbrains.annotations.NotNull;

public class PvpCommand implements DeepWitherCommand {
    public static final String COMMAND_NAME = "pvp";

    private final PvpWorldHandler pvpWorldHandler;

    public PvpCommand(PvpWorldHandler pvpWorldHandler) {
        this.pvpWorldHandler = pvpWorldHandler;
    }

    @NotNull
    @Override
    public LiteralCommandNode<CommandSourceStack> getNode() {
        return Commands.literal(COMMAND_NAME)
            .requires(CommandUtil::isPlayerSender)
            .executes(this::teleport)
            .build();
    }

    @NotNull
    @Override
    public String getDescription() {
        return "PvPワールドへの往復テレポート";
    }

    private int teleport(CommandContext<CommandSourceStack> context) {
        CommandUtil.getSenderAsPlayer(context).ifPresent(this.pvpWorldHandler::tryTransfer);
        return Command.SINGLE_SUCCESS;
    }
}
