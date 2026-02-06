package com.lunar_prototype.deepwither.commands.util;

import com.lunar_prototype.deepwither.constants.Permissions;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class CommandUtil {
    private CommandUtil() {}

    public static boolean isAdmin(CommandSourceStack source) {
        return source.getSender().hasPermission(Permissions.ADMIN);
    }

    public static boolean isPlayer(@NotNull CommandSourceStack source) {
        return source.getExecutor() instanceof Player;
    }

    public static Optional<Player> asPlayer(@NotNull CommandContext<CommandSourceStack> context) {
        return context.getSource().getExecutor() instanceof Player player
            ? Optional.of(player)
            : Optional.empty();
    }

    public static List<Player> resolvePlayerSelector(@NotNull CommandContext<CommandSourceStack> context, String string) {
        try {
            return context.getArgument(string, PlayerSelectorArgumentResolver.class).resolve(context.getSource());
        } catch (CommandSyntaxException e) {
            return Collections.emptyList();
        }
    }
}
