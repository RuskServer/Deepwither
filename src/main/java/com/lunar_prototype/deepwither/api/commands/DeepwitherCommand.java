package com.lunar_prototype.deepwither.api.commands;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NullMarked;

import java.util.Collection;
import java.util.Collections;

@NullMarked
public interface DeepwitherCommand {
    String name();

    default LiteralCommandNode<CommandSourceStack> node() {
        return Commands.literal(name()).build();
    }

    default @Nullable String description() {
        return null;
    }

    default Collection<String> aliases() {
        return Collections.emptyList();
    }
}
