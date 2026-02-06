package com.lunar_prototype.deepwither.commands.api;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

public interface DeepwitherCommand {
    @NotNull String name();

    default @NotNull LiteralCommandNode<CommandSourceStack> node() {
        return Commands.literal(name()).build();
    }

    default @Nullable String description() {
        return null;
    }

    default @NotNull Collection<String> aliases() {
        return Collections.emptyList();
    }
}
