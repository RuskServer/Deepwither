package com.lunar_prototype.deepwither.commands;

import com.lunar_prototype.deepwither.core.Constants;
import io.papermc.paper.command.brigadier.CommandSourceStack;

public final class CommandUtil {
    private CommandUtil() {}

    public static boolean hasPermission(CommandSourceStack sourceStack, String permission) {
        return sourceStack.getSender().hasPermission(permission);
    }

    public static boolean hasAdminPermission(CommandSourceStack sourceStack) {
        return hasPermission(sourceStack, Constants.Permissions.ADMIN);
    }
}
