package com.lunar_prototype.deepwither.commands;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.commands.util.CommandUtil;
import com.lunar_prototype.deepwither.commands.api.DeepwitherCommand;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.NotNull;

import static io.papermc.paper.command.brigadier.Commands.literal;

public final class ReloadCommand implements DeepwitherCommand {
    @NotNull
    @Override
    public String name() {
        return "reload";
    }

    @NotNull
    @Override
    public LiteralCommandNode<CommandSourceStack> node() {
        return literal(this.name())
            .requires(CommandUtil::isAdmin)
            .executes(ReloadCommand::executeReload)
            .build();
    }

    private static int executeReload(CommandContext<CommandSourceStack> context) {
        context.getSource().getSender().sendMessage(Component.text("Deepwitherの設定をリロードしました。", NamedTextColor.GREEN));
        Deepwither.getInstance().getSkilltreeGUI().reload();
        return Command.SINGLE_SUCCESS;
    }
}
