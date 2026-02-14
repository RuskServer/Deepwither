package com.lunar_prototype.deepwither.command;

import com.lunar_prototype.deepwither.Deepwither;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class PvPCommand implements CommandExecutor {
    private static final NamespacedKey BACK_LOCATION = NamespacedKey.fromString("pvp_back_location", Deepwither.getInstance());
    private final String PVP_WORLD_NAME = "pvp";

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("このコマンドはプレイヤー専用です。", NamedTextColor.RED));
            return true;
        }

        World pvpWorld = Bukkit.getWorld(PVP_WORLD_NAME);
        if (pvpWorld == null) {
            player.sendMessage(Component.text("エラー: ワールド '" + PVP_WORLD_NAME + "' が見つかりません。", NamedTextColor.RED));
            return true;
        }

        if (player.getWorld().getName().equals(PVP_WORLD_NAME)) {
            Location backLoc = loadLocation(player);
            if (backLoc != null) {
                player.teleport(backLoc);
                removeLocation(player);
                player.sendMessage(Component.text("元の場所に戻りました。", NamedTextColor.GREEN));
            } else {
                player.teleport(Bukkit.getWorld("aether").getSpawnLocation());
                player.sendMessage(Component.text("元の場所の記録がないため、初期スポーンに戻りました。", NamedTextColor.YELLOW));
            }
        } else {
            saveLocation(player);
            player.teleport(pvpWorld.getSpawnLocation());
            player.sendMessage(Component.text("PvPワールドへ移動しました！", NamedTextColor.GOLD, TextDecoration.BOLD));
            player.sendMessage(Component.text("もう一度 /pvp を打つと元の場所に戻ります。", NamedTextColor.GRAY));
        }

        return true;
    }

    private static void saveLocation(@NotNull Player player) {
        var location = player.getLocation();
        var worldUuid = player.getWorld().getUID();
        player.getPersistentDataContainer().set(BACK_LOCATION, PersistentDataType.LONG_ARRAY, new long[] {
            worldUuid.getMostSignificantBits(),
            worldUuid.getLeastSignificantBits(),
            Double.doubleToRawLongBits(location.getX()),
            Double.doubleToRawLongBits(location.getY()),
            Double.doubleToRawLongBits(location.getZ())
        });
    }

    @Nullable
    private static Location loadLocation(@NotNull Player player) {
        var longs = player.getPersistentDataContainer().get(BACK_LOCATION, PersistentDataType.LONG_ARRAY);
        if (longs == null || longs.length != 5) return null;

        var world = Bukkit.getWorld(new UUID(longs[0], longs[1]));
        if (world == null) return null;

        return new Location(
            world,
            Double.longBitsToDouble(longs[2]),
            Double.longBitsToDouble(longs[3]),
            Double.longBitsToDouble(longs[4])
        );
    }

    private static void removeLocation(@NotNull Player player) {
        player.getPersistentDataContainer().remove(BACK_LOCATION);
    }
}