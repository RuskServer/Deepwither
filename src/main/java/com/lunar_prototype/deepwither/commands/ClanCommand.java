package com.lunar_prototype.deepwither.commands;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.clan.ClanManager;
import com.lunar_prototype.deepwither.commands.api.DeepwitherCommand;
import com.lunar_prototype.deepwither.commands.util.CommandUtil;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

public final class ClanCommand implements DeepwitherCommand {
    private static final String ARG_CLAN_NAME = "clan_name";
    private static final String ARG_PLAYER = "player";
    private final ClanManager clanManager;

    public ClanCommand(Deepwither plugin) {
        this.clanManager = plugin.getClanManager();
    }

    @NotNull
    @Override
    public String name() {
        return "clan";
    }

    @NotNull
    @Override
    public LiteralCommandNode<CommandSourceStack> node() {
        return literal(this.name())
            .then(literal("create")
                .requires(this::isNotJoined)
                .then(argument(ARG_CLAN_NAME, StringArgumentType.string())
                    .executes(this::executeCreate))
            .then(literal("invite")
                .requires(this::isJoined)
                .then(argument(ARG_PLAYER, ArgumentTypes.player())
                    .executes(this::executeInvite)))
            .then(literal("join")
                .requires(this::isNotJoined)
                .executes(this::executeJoin))
            .then(literal("info")
                .requires(this::isJoined)
                .executes(this::executeInfo))
            .then(literal("leave")
                .requires(this::isJoined)
                .executes(this::executeLeave))
            .then(literal("disband")
                .requires(this::isJoined)
                .executes(this::executeDisband))
            )
            .build();
    }

    private int executeCreate(CommandContext<CommandSourceStack> context) {
        CommandUtil.asPlayer(context).ifPresent(player -> {
            clanManager.createClan(player, StringArgumentType.getString(context, ARG_CLAN_NAME));
        });

        return Command.SINGLE_SUCCESS;
    }

    private int executeInvite(CommandContext<CommandSourceStack> context) {
        CommandUtil.asPlayer(context).ifPresent(player -> {
            var targets = CommandUtil.resolvePlayerSelector(context, ARG_PLAYER);
            if (!targets.isEmpty()) {
                clanManager.invitePlayer(player, targets.getFirst());
            }
        });

        return Command.SINGLE_SUCCESS;
    }

    private int executeJoin(CommandContext<CommandSourceStack> context) {
        CommandUtil.asPlayer(context).ifPresent(clanManager::joinClan);
        return Command.SINGLE_SUCCESS;
    }

    private int executeInfo(CommandContext<CommandSourceStack> context) {
        CommandUtil.asPlayer(context).ifPresent(player -> {
            var clan = clanManager.getClanByPlayer(player.getUniqueId());
            if (clan == null) return;

            player.sendMessage(Component.text("=== " + clan.getName() + " ===", NamedTextColor.GOLD));
            player.sendMessage(Component.text("リーダー: " + Bukkit.getOfflinePlayer(clan.getOwner()).getName(), NamedTextColor.GRAY));
            player.sendMessage(Component.text("メンバー数: " + clan.getMembers().size(), NamedTextColor.GRAY));
        });

        return Command.SINGLE_SUCCESS;
    }

    private int executeLeave(CommandContext<CommandSourceStack> context) {
        CommandUtil.asPlayer(context).ifPresent(clanManager::leaveClan);
        return Command.SINGLE_SUCCESS;
    }

    private int executeDisband(CommandContext<CommandSourceStack> context) {
        CommandUtil.asPlayer(context).ifPresent(clanManager::disbandClan);
        return Command.SINGLE_SUCCESS;
    }

    private boolean isJoined(CommandSourceStack sourceStack) {
        if (sourceStack.getSender() instanceof Player player) {
            return clanManager.getClanByPlayer(player.getUniqueId()) != null;
        }

        return false;
    }

    private boolean isNotJoined(CommandSourceStack sourceStack) {
        return !isJoined(sourceStack);
    }
}
