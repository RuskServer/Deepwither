package com.lunar_prototype.deepwither.commands;

import com.lunar_prototype.deepwither.core.Constants;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public final class CommandUtil {
    private CommandUtil() {}

    public static boolean hasPermission(CommandSourceStack sourceStack, String permission) {
        return sourceStack.getSender().hasPermission(permission);
    }

    public static boolean hasAdminPermission(CommandSourceStack sourceStack) {
        return hasPermission(sourceStack, Constants.Permissions.ADMIN);
    }

    public static boolean isPlayerSender(@NotNull CommandSourceStack source) {
        return source.getSender() instanceof Player;
    }

    @NotNull
    public static Optional<Player> getSenderAsPlayer(@NotNull CommandContext<CommandSourceStack> context) {
        return Optional.ofNullable(context.getSource().getSender() instanceof Player player ? player : null);
    }
}
