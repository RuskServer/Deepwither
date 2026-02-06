package com.lunar_prototype.deepwither.commands;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.commands.util.CommandUtil;
import com.lunar_prototype.deepwither.commands.api.DeepwitherCommand;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

public final class MarketCommand implements DeepwitherCommand {
    private static final String ARG_PRICE = "price";
    private final Deepwither plugin;

    public MarketCommand(Deepwither plugin) {
        this.plugin = plugin;
    }

    @NotNull
    @Override
    public String name() {
        return "market";
    }

    @NotNull
    @Override
    public LiteralCommandNode<CommandSourceStack> node() {
        return literal(this.name())
            .requires(CommandUtil::isPlayer)
            .executes(this::executeOpen)
            .then(literal("sell")
                .then(argument(ARG_PRICE, DoubleArgumentType.doubleArg(0))
                    .executes(this::executeSell)))
            .then(literal("collect")
                .executes(this::executeCollect))
            .build();
    }

    private int executeCollect(CommandContext<CommandSourceStack> context) {
        CommandUtil.asPlayer(context).ifPresent(plugin.getGlobalMarketManager()::claimEarnings);

        return Command.SINGLE_SUCCESS;
    }

    private int executeOpen(CommandContext<CommandSourceStack> context) {
        CommandUtil.asPlayer(context).ifPresent(plugin.getMarketGui()::openMainMenu);

        return Command.SINGLE_SUCCESS;
    }

    private int executeSell(CommandContext<CommandSourceStack> context) {
        CommandUtil.asPlayer(context).ifPresent(player -> {
            var price = DoubleArgumentType.getDouble(context, ARG_PRICE);
            var inventory = player.getInventory();
            var itemStack = inventory.getItemInMainHand();

            if (itemStack.getType() == Material.AIR) {
                player.sendMessage(Component.text("メインハンドにアイテムを持っていません。", NamedTextColor.RED));
                return;
            }

            plugin.getGlobalMarketManager().listItem(player, itemStack, price);
            inventory.setItemInMainHand(null);

            player.sendMessage(Component.text("[Market] アイテムを %.3f G で出品しました！".formatted(price), NamedTextColor.GREEN));
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        });

        return Command.SINGLE_SUCCESS;
    }
}
