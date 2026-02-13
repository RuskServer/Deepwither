package com.lunar_prototype.deepwither.command;

import com.lunar_prototype.deepwither.Deepwither;
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

    //    // プレイヤーが元居た場所を記録するMap
//    private final HashMap<UUID, Location> backLocations = new HashMap<>();
    private final String PVP_WORLD_NAME = "pvp";

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cこのコマンドはプレイヤー専用です。");
            return true;
        }

        World pvpWorld = Bukkit.getWorld(PVP_WORLD_NAME);
        if (pvpWorld == null) {
            player.sendMessage("§cエラー: ワールド '" + PVP_WORLD_NAME + "' が見つかりません。");
            return true;
        }

        // 現在PvPワールドにいるかチェック
        if (player.getWorld().getName().equals(PVP_WORLD_NAME)) {
            // 元の場所に戻す
            Location backLoc = loadLocation(player);
            if (backLoc != null) {
                player.teleport(backLoc);
                removeLocation(player);
                player.sendMessage("§a元の場所に戻りました。");
            } else {
                // 記録がない場合は初期ワールドのスポーンへ（安全策）
                player.teleport(Bukkit.getWorld("aether").getSpawnLocation());
                player.sendMessage("§e元の場所の記録がないため、初期スポーンに戻りました。");
            }
        } else {
            // PvPワールドへ行く
            saveLocation(player); // 現在地を保存
            player.teleport(pvpWorld.getSpawnLocation());
            player.sendMessage("§6§lPvPワールドへ移動しました！");
            player.sendMessage("§7もう一度 /pvp を打つと元の場所に戻ります。");
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
