package com.lunar_prototype.deepwither.commands;

import com.lunar_prototype.deepwither.commands.util.CommandUtil;
import com.lunar_prototype.deepwither.commands.api.DeepwitherCommand;
import com.lunar_prototype.deepwither.dungeon.instance.DungeonInstanceManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

import static io.papermc.paper.command.brigadier.Commands.*;

public final class DungeonCommand implements DeepwitherCommand {
    private static final String ARG_INSTANCE_ID = "instance_id";
    private static final String ARG_DUNGEON_TYPE = "dungeon_type";

    @NotNull
    @Override
    public String name() {
        return "dungeon";
    }

    @NotNull
    @Override
    public LiteralCommandNode<CommandSourceStack> node() {
        return literal(this.name())
            .then(literal("leave")
                .executes(DungeonCommand::executeLeave))
            .then(literal("join")
                .requires(CommandUtil::isAdmin)
                .then(argument(ARG_INSTANCE_ID, StringArgumentType.word())
                    .suggests(DungeonCommand::instanceIdSuggestions)
                    .executes(DungeonCommand::executeJoin)))
            .then(literal("generate")
                .requires(CommandUtil::isAdmin)
                .then(argument(ARG_DUNGEON_TYPE, StringArgumentType.word())
                    .suggests(DungeonCommand::dungeonTypeSuggestions)
                    .executes(DungeonCommand::executeGenerate)))
            .build();
    }

    private static CompletableFuture<Suggestions> instanceIdSuggestions(CommandContext<CommandSourceStack> context, @NotNull SuggestionsBuilder builder) {
        for (var instanceId : DungeonInstanceManager.getInstance().instanceIdSet()) {
            builder.suggest(instanceId);
        }

        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> dungeonTypeSuggestions(CommandContext<CommandSourceStack> context, @NotNull SuggestionsBuilder builder) {
        // copy: dungeonsフォルダ内のymlファイル名を取得してリスト化するのが理想
        builder.suggest("silent_terrarium_ruins");
        builder.suggest("ancient_city");

        return builder.buildFuture();
    }

    private static int executeLeave(@NotNull CommandContext<CommandSourceStack> context) {
        CommandUtil.asPlayer(context).ifPresent(player -> {
            DungeonInstanceManager.getInstance()
                .leaveDungeon(player);
        });

        return Command.SINGLE_SUCCESS;
    }

    private static int executeJoin(@NotNull CommandContext<CommandSourceStack> context) {
        CommandUtil.asPlayer(context).ifPresent(player -> {
            DungeonInstanceManager.getInstance()
                .joinDungeon(player, StringArgumentType.getString(context, ARG_INSTANCE_ID));
        });

        return Command.SINGLE_SUCCESS;
    }

    private static int executeGenerate(@NotNull CommandContext<CommandSourceStack> context) {
        CommandUtil.asPlayer(context).ifPresent(player -> {
            DungeonInstanceManager.getInstance()
                .createDungeonInstance(player, StringArgumentType.getString(context, ARG_DUNGEON_TYPE), "normal");
        });

        return Command.SINGLE_SUCCESS;
    }
}
