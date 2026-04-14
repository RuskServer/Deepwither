package com.lunar_prototype.deepwither.modules.chat;

import com.lunar_prototype.deepwither.PlayerSettingsManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class ChatListener implements Listener {

    private final ChatConverterManager converterManager;
    private final PlayerSettingsManager settingsManager;

    public ChatListener(ChatConverterManager converterManager, PlayerSettingsManager settingsManager) {
        this.converterManager = converterManager;
        this.settingsManager = settingsManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        
        // 設定がOFFなら何もしない
        if (!settingsManager.isEnabled(player, PlayerSettingsManager.SettingType.JAPANESE_CONVERSION)) {
            return;
        }

        String originalMessage = PlainTextComponentSerializer.plainText().serialize(event.originalMessage());
        
        // ローマ字判定
        if (!converterManager.isRomaji(originalMessage)) {
            return;
        }

        // イベントをキャンセルして独自に再送信
        event.setCancelled(true);

        converterManager.convert(originalMessage).thenAccept(converted -> {
            Component message;
            if (converted.equals(originalMessage)) {
                // 変換に変化がない場合は元のメッセージ
                message = event.originalMessage();
            } else {
                // 変換後のメッセージにホバーで元のメッセージを表示
                message = Component.text(converted)
                        .hoverEvent(HoverEvent.showText(Component.text("Original: " + originalMessage, NamedTextColor.GRAY)));
            }

            // チャットメッセージを構築して全員に送信
            // rendererを使用してサーバーのチャットフォーマットを再現
            Component finalMessage = event.renderer().render(player, player.displayName(), message, player);

            // 全てのビューワーに送信 (コンソール含む)
            for (org.bukkit.command.CommandSender viewer : org.bukkit.Bukkit.getOnlinePlayers()) {
                viewer.sendMessage(finalMessage);
            }
            org.bukkit.Bukkit.getConsoleSender().sendMessage(finalMessage);
        });
    }
}
