package com.lunar_prototype.deepwither.party;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.JDA;
import github.scarsz.discordsrv.dependencies.jda.api.Permission;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Category;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Member;
import github.scarsz.discordsrv.dependencies.jda.api.entities.VoiceChannel;
import github.scarsz.discordsrv.dependencies.jda.api.requests.restaction.ChannelAction;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.EnumSet;
import java.util.UUID;

public class PartyVoiceManager {

    private static final String CATEGORY_ID = "1491355316502266006";

    /**
     * パーティー用のVCを作成します。
     * 作成時、@everyone から VOICE_CONNECT を剥奪します。
     */
    public void createVoiceChannel(Party party, Player leader) {
        if (party.getDiscordVoiceChannelId() != null) return;

        JDA jda = DiscordSRV.getPlugin().getJda();
        if (jda == null) return; // DiscordSRV not ready

        Category category = jda.getCategoryById(CATEGORY_ID);
        if (category == null) {
            Bukkit.getLogger().warning("[PartyVoiceManager] Category ID " + CATEGORY_ID + " not found!");
            return;
        }

        String channelName = "🔊 Party - " + leader.getName();

        ChannelAction<VoiceChannel> action = category.createVoiceChannel(channelName);
        
        // everyoneのVOICE_CONNECTを拒否（VIEW_CHANNELには触れないことで「見えるが入れない」状態にする）
        action.addPermissionOverride(category.getGuild().getPublicRole(), null, EnumSet.of(Permission.VOICE_CONNECT));

        action.queue(vc -> {
            party.setDiscordVoiceChannelId(vc.getId());
            // 既存メンバー全員に権限を付与
            for (UUID memberId : party.getMemberIds()) {
                grantVoiceAccess(party, memberId);
            }
        });
    }

    /**
     * パーティーが解散した際などにVCを削除します。
     */
    public void deleteVoiceChannel(Party party) {
        String vcId = party.getDiscordVoiceChannelId();
        if (vcId == null) return;

        JDA jda = DiscordSRV.getPlugin().getJda();
        if (jda == null) return;

        VoiceChannel vc = jda.getVoiceChannelById(vcId);
        if (vc != null) {
            vc.delete().queue(
                    success -> party.setDiscordVoiceChannelId(null),
                    error -> Bukkit.getLogger().warning("[PartyVoiceManager] Failed to delete VC " + vcId)
            );
        } else {
            party.setDiscordVoiceChannelId(null);
        }
    }

    /**
     * 特定のメンバーに対して、VCの接続権限（VOICE_CONNECT）を付与します。
     */
    public void grantVoiceAccess(Party party, UUID playerUuid) {
        String vcId = party.getDiscordVoiceChannelId();
        if (vcId == null) return;

        String discordId = DiscordSRV.getPlugin().getAccountLinkManager().getDiscordId(playerUuid);
        if (discordId == null) return; // Discord未連携

        JDA jda = DiscordSRV.getPlugin().getJda();
        if (jda == null) return;

        VoiceChannel vc = jda.getVoiceChannelById(vcId);
        if (vc == null) return;

        vc.getGuild().retrieveMemberById(discordId).queue(
                member -> {
                    // ChannelManagerの競合を防ぐためバッチを使わず単一で上書きする
                    vc.upsertPermissionOverride(member).setAllow(Permission.VOICE_CONNECT).queue();
                },
                error -> {} // メンバーがギルドにいない場合などのエラー無視
        );
    }

    /**
     * 特定のメンバーから、VCの接続権限（VOICE_CONNECT）を剥奪します。
     */
    public void revokeVoiceAccess(Party party, UUID playerUuid) {
        String vcId = party.getDiscordVoiceChannelId();
        if (vcId == null) return;

        String discordId = DiscordSRV.getPlugin().getAccountLinkManager().getDiscordId(playerUuid);
        if (discordId == null) return;

        JDA jda = DiscordSRV.getPlugin().getJda();
        if (jda == null) return;

        VoiceChannel vc = jda.getVoiceChannelById(vcId);
        if (vc == null) return;

        vc.getGuild().retrieveMemberById(discordId).queue(
                member -> {
                    vc.getManager().removePermissionOverride(member).queue();
                    // 接続中の場合は切断（キック）する
                    if (member.getVoiceState() != null && member.getVoiceState().inVoiceChannel() &&
                        member.getVoiceState().getChannel() != null && member.getVoiceState().getChannel().getId().equals(vcId)) {
                        vc.getGuild().kickVoiceMember(member).queue();
                    }
                },
                error -> {} 
        );
    }
}
