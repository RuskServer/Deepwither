package com.lunar_prototype.deepwither.commands.hmm;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;

public class PvpWorldHandler {
    // todo: pvpワールドにいる時にサーバーが落ちたら消えるんじゃない?
    private final HashMap<UUID, Location> backLocations = new HashMap<>();
    private final String pvpWorld;
    private final Location fallbackLocation;

    public PvpWorldHandler(String pvpWorld, @NotNull Location fallbackLocation) {
        this.pvpWorld = pvpWorld;
        this.fallbackLocation = fallbackLocation;
    }

    public void tryTransfer(@NotNull Player player) {
        final var uuid = player.getUniqueId();

        if (player.getWorld().getName().equals(pvpWorld)) {
            final var backLocation = Optional.ofNullable(backLocations.get(uuid)).orElseGet(() -> {
                player.sendMessage(Component.text("元の場所の記録がないため、初期スポーンにテレポートします。", NamedTextColor.YELLOW));
                return this.fallbackLocation;
            });

            if (player.teleport(backLocation)) {
                player.sendMessage(Component.text("PvPワールドから退出しました！", NamedTextColor.GOLD, TextDecoration.BOLD));
                backLocations.remove(uuid);
            } else {
                player.sendMessage(Component.text("テレポートに失敗しました。", NamedTextColor.RED));
                player.sendMessage(Component.text("オペレーターにメンションすることを検討してください。", NamedTextColor.RED));
            }
        } else {
            backLocations.put(uuid, player.getLocation());

            Optional.ofNullable(Bukkit.getWorld(this.pvpWorld)).ifPresent(world -> {
                if (player.teleport(world.getSpawnLocation())) {
                    player.sendMessage(Component.text("PvPワールドへ移動しました！", NamedTextColor.GOLD, TextDecoration.BOLD));
                    player.sendMessage(Component.text("もう一度 /pvp を打つと元の場所に戻ります。", NamedTextColor.GRAY));
                    return;
                }

                player.sendMessage(Component.text("テレポートに失敗しました。", NamedTextColor.RED));
            });
        }
    }
}
