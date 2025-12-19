package com.lunar_prototype.deepwither.command;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.UUID;

public class PvPCommand implements CommandExecutor {

    // プレイヤーが元居た場所を記録するMap
    private final HashMap<UUID, Location> backLocations = new HashMap<>();
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
            Location backLoc = backLocations.get(player.getUniqueId());
            if (backLoc != null) {
                player.teleport(backLoc);
                backLocations.remove(player.getUniqueId());
                player.sendMessage("§a元の場所に戻りました。");
            } else {
                // 記録がない場合は初期ワールドのスポーンへ（安全策）
                player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
                player.sendMessage("§e元の場所の記録がないため、初期スポーンに戻りました。");
            }
        } else {
            // PvPワールドへ行く
            backLocations.put(player.getUniqueId(), player.getLocation()); // 現在地を保存
            player.teleport(pvpWorld.getSpawnLocation());
            player.sendMessage("§6§lPvPワールドへ移動しました！");
            player.sendMessage("§7もう一度 /pvp を打つと元の場所に戻ります。");
        }

        return true;
    }
}