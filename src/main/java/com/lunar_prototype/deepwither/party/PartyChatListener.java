package com.lunar_prototype.deepwither.party;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class PartyChatListener implements Listener {

    private final PartyManager partyManager;

    public PartyChatListener(PartyManager partyManager) {
        this.partyManager = partyManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        
        // プレイヤーがパーティーチャットモードかどうか確認
        if (!partyManager.isInPartyChatMode(player.getUniqueId())) {
            return;
        }

        // パーティーを取得
        Party party = partyManager.getParty(player);
        if (party == null) {
            // モードだけ残っている場合は解除して終了
            // (既に PartyManager 側で離脱時に解除しているが念のため)
            return;
        }

        // イベントをキャンセルして独自に出力
        event.setCancelled(true);

        // メッセージの構築
        Component originalMessage = event.message();
        Component partyMessage = Component.text("[Party] ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text(player.getName(), NamedTextColor.WHITE))
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(originalMessage.color(NamedTextColor.WHITE));

        // パーティーメンバー（オンライン）に送信
        party.getOnlineMembers().forEach(member -> member.sendMessage(partyMessage));
        
        // コンソールにも一応出しておく
        org.bukkit.Bukkit.getLogger().info("[PartyChat] " + player.getName() + ": " + PlainTextComponentSerializer.plainText().serialize(originalMessage));
    }
}
