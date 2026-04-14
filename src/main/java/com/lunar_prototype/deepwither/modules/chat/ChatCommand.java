package com.lunar_prototype.deepwither.modules.chat;

import com.lunar_prototype.deepwither.PlayerSettingsManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ChatCommand implements CommandExecutor {

    private final PlayerSettingsManager settingsManager;

    public ChatCommand(PlayerSettingsManager settingsManager) {
        this.settingsManager = settingsManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        settingsManager.toggle(player, PlayerSettingsManager.SettingType.JAPANESE_CONVERSION);
        boolean enabled = settingsManager.isEnabled(player, PlayerSettingsManager.SettingType.JAPANESE_CONVERSION);
        
        player.sendMessage(Component.text("[Chat] ", NamedTextColor.AQUA)
                .append(Component.text("日本語変換を ", NamedTextColor.GRAY))
                .append(Component.text(enabled ? "ON" : "OFF", enabled ? NamedTextColor.GREEN : NamedTextColor.RED))
                .append(Component.text(" にしました。", NamedTextColor.GRAY)));

        return true;
    }
}
