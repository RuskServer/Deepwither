package com.lunar_prototype.deepwither.commands;

import com.lunar_prototype.deepwither.commands.api.DeepWitherCommand;
import com.lunar_prototype.deepwither.market.GlobalMarketManager;
import com.lunar_prototype.deepwither.market.MarketGui;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

import static io.papermc.paper.command.brigadier.Commands.*;

public class MarketCommand implements DeepWitherCommand {
    private static final String COMMAND_NAME = "market";
    private static final String ARG_PRICE = "price";

    private final GlobalMarketManager marketManager;
    private final MarketGui marketGui;

    public MarketCommand(GlobalMarketManager marketManager, MarketGui marketGui) {
        this.marketManager = marketManager;
        this.marketGui = marketGui;
    }

    @NotNull
    @Override
    public LiteralCommandNode<CommandSourceStack> getNode() {
        return literal(COMMAND_NAME)
            .requires(CommandUtil::isPlayerSender)
            .executes(this::handleOpen)
            .then(literal("collect")
                .executes(this::handleCollect))
            .then(literal("sell")
                .then(argument(ARG_PRICE, DoubleArgumentType.doubleArg(0))
                    .executes(this::handleSell)))
            .build();
    }

    @NotNull
    @Override
    public String getDescription() {
        return "グローバルマーケットを開きます。";
    }

    private int handleCollect(@NotNull CommandContext<CommandSourceStack> context) {
        CommandUtil.getSenderAsPlayer(context).ifPresent(marketManager::claimEarnings);
        return Command.SINGLE_SUCCESS;
    }

    private int handleOpen(@NotNull CommandContext<CommandSourceStack> context) {
        CommandUtil.getSenderAsPlayer(context).ifPresent(marketGui::openMainMenu);
        return Command.SINGLE_SUCCESS;
    }

    private int handleSell(@NotNull CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final var player = CommandUtil.getSenderAsPlayer(context).orElseThrow(() -> new SimpleCommandExceptionType(new LiteralMessage("") /* 到達不可なはず */ ).create());
        final var price = DoubleArgumentType.getDouble(context, ARG_PRICE);
        final var inventory = player.getInventory();
        final var itemStack = inventory.getItemInMainHand();

        if (itemStack.getType() == Material.AIR) {
            throw new SimpleCommandExceptionType(new LiteralMessage("メインハンドにアイテムを持っていません。")).create();
        }

        marketManager.listItem(player, itemStack, price);
        inventory.setItemInMainHand(null);

        player.sendMessage(Component.text("[Market] アイテムを %.3f G で出品しました！".formatted(price), NamedTextColor.GREEN));
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);

        return Command.SINGLE_SUCCESS;
    }
}
