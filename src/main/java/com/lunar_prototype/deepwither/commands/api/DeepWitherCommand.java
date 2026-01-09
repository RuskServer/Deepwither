package com.lunar_prototype.deepwither.commands.api;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.jetbrains.annotations.NotNull;

public interface DeepWitherCommand {
    @NotNull LiteralCommandNode<CommandSourceStack> getNode();
    default @NotNull String getDescription() {
        return "No description. :O";
    }
}
