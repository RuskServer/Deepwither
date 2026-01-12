package com.lunar_prototype.deepwither.clan;

import com.lunar_prototype.deepwither.clan.Clan;
import com.lunar_prototype.deepwither.clan.ClanManager;
import com.lunar_prototype.deepwither.util.GoogleImeConverter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class ClanChatManager implements Listener {
    private final ClanManager clanManager;

    public ClanChatManager(ClanManager clanManager) {
        this.clanManager = clanManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String originalMessage = event.getMessage();

        // 1. Google IME による変換
        String convertedMessage = GoogleImeConverter.convert(originalMessage);

        // 2. 所属クランの取得
        Clan clan = clanManager.getClanByPlayer(player.getUniqueId());
        String clanTag = (clan != null) ? "§b[" + clan.getTag() + "]§r " : "";

        // 3. チャットフォーマットの再構築
        // 変換されたメッセージを表示し、後ろに元のローマ字を括弧書きで添える
        String finalMessage;
        if (originalMessage.equals(convertedMessage)) {
            finalMessage = originalMessage;
        } else {
            finalMessage = convertedMessage + " §7(" + originalMessage + ")";
        }

        // フォーマット設定: [ClanTag] PlayerName: Message
        event.setFormat(clanTag + "%1$s: %2$s");
        event.setMessage(finalMessage);
    }
}