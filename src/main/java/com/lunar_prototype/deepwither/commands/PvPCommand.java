package com.lunar_prototype.deepwither.commands;

import com.lunar_prototype.deepwither.commands.util.CommandUtil;
import com.lunar_prototype.deepwither.commands.api.DeepwitherCommand;
import com.lunar_prototype.deepwither.constants.Keys;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

import static io.papermc.paper.command.brigadier.Commands.*;

public final class PvPCommand implements DeepwitherCommand {
    private static final String PVP_WORLD_NAME = "pvp";
    private static final String AETHER_WORLD_NAME = "aether";

    @NotNull
    @Override
    public String name() {
        return "pvp";
    }

    @NotNull
    @Override
    public LiteralCommandNode<CommandSourceStack> node() {
        return literal(this.name())
            .requires(CommandUtil::isPlayer)
            .executes(PvPCommand::exexute)
            .build();
    }

    private static int exexute(CommandContext<CommandSourceStack> context) {
        if (context.getSource().getExecutor() instanceof Player player) {
            tryTeleport(player);
        }

        return Command.SINGLE_SUCCESS;
    }

    private static void tryTeleport(@NotNull Player player) {
        if (player.getWorld().getName().equals(PVP_WORLD_NAME)) {
            var backLocation = loadLocation(player);
            if (backLocation == null) {
                player.sendMessage(Component.text("元の場所の記録がないため、初期スポーンにテレポートします", NamedTextColor.YELLOW));

                var fallbackWorld = Bukkit.getWorld(AETHER_WORLD_NAME);
                if (fallbackWorld == null) {
                    player.sendMessage(Component.text("ぶー！ぶー！初期スポーンが読み込まれていません！なんで！？", NamedTextColor.RED));
                    player.sendMessage(Component.text("オペレーターにメンションしてください...", NamedTextColor.RED));
                } else if (player.teleport(fallbackWorld.getSpawnLocation())) {
                    player.sendMessage(Component.text("PvPワールドから退出しました！", NamedTextColor.GOLD));
                }

                return;
            }

            if (player.teleport(backLocation)) {
                player.sendMessage(Component.text("PvPワールドから退出しました！", NamedTextColor.GOLD));
            } else {
                player.sendMessage(Component.text("テレポートに失敗しました", NamedTextColor.RED));
                player.sendMessage(Component.text("オペレーターにメンションしてください...", NamedTextColor.RED));
            }
        } else {
            saveLocation(player);

            var world = Bukkit.getWorld(PVP_WORLD_NAME);
            if (world != null && player.teleport(world.getSpawnLocation())) {
                player.sendMessage(Component.text("PvPワールドへ移動しました！", NamedTextColor.GOLD));
                player.sendMessage(Component.text("もう一度 /pvp と入力すると元の場所に戻ります", NamedTextColor.GRAY));
                return;
            }

            player.sendMessage(Component.text("テレポートに失敗しました。", NamedTextColor.RED));
        }
    }

    private static void saveLocation(@NotNull Player player) {
        var location = player.getLocation();
        var uuid = location.getWorld().getUID();
        player.getPersistentDataContainer().set(Keys.PVP_BACK_LOCATION, PersistentDataType.LONG_ARRAY, new long[]{
            uuid.getMostSignificantBits(),
            uuid.getLeastSignificantBits(),
            Double.doubleToRawLongBits(location.getX()),
            Double.doubleToRawLongBits(location.getY()),
            Double.doubleToRawLongBits(location.getZ())
        });
    }

    @Nullable
    private static Location loadLocation(@NotNull Player player) {
        var data = player.getPersistentDataContainer().get(Keys.PVP_BACK_LOCATION, PersistentDataType.LONG_ARRAY);
        return data != null && data.length == 5 ? new Location(
            Bukkit.getWorld(new UUID(data[0], data[1])),
            Double.longBitsToDouble(data[2]),
            Double.longBitsToDouble(data[3]),
            Double.longBitsToDouble(data[4])
        ) : null;
    }
}
