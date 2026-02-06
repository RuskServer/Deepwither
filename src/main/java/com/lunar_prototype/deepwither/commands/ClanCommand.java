package com.lunar_prototype.deepwither.commands;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.clan.ClanManager;
import com.lunar_prototype.deepwither.commands.api.DeepwitherCommand;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import org.jetbrains.annotations.NotNull;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

public final class ClanCommand implements DeepwitherCommand {
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
            .then(literal("create") // 入ってない
                .requires()
                .then(argument("clan_name", StringArgumentType.string()))
            .then(literal("invite") // 入ってる
                .then(argument("player", ArgumentTypes.player())))
            .then(literal("join")) // 入ってない時だけ
            .then(literal("info")) // 入ってる
            .then(literal("leave")) // 入ってる
            .then(literal("disband")) // オーナーのみ
            )
            .build();
    }

    private boolean isJoined(CommandContext<CommandSourceStack> context) {
        clanManager.
    }

    private boolean isIndependent(CommandContext<CommandSourceStack> context) {
        return !isJoined(context);
    }

    private boolean isOwner(CommandContext<CommandSourceStack> context) {
    }

//        player.sendMessage("§f/clan create <name> §7- クランを作成");
//        player.sendMessage("§f/clan invite <player> §7- プレイヤーを招待");
//        player.sendMessage("§f/clan join §7- 招待を受ける");
//        player.sendMessage("§f/clan info §7- 所属クランの情報");
//        player.sendMessage("§f/clan leave §7- クランを脱退");
//        player.sendMessage("§f/clan disband §7- クランを解散（リーダー用）");
}
