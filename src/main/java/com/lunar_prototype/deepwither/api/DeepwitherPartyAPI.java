package com.lunar_prototype.deepwither.api;

import com.lunar_prototype.deepwither.party.Party;
import com.lunar_prototype.deepwither.party.PartyManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class DeepwitherPartyAPI {

    private final PartyManager partyManager;

    public DeepwitherPartyAPI(PartyManager partyManager) {
        this.partyManager = partyManager;
    }

    /**
     * プレイヤーがパーティーに参加しているか確認します。
     */
    public boolean isInParty(Player player) {
        return partyManager.getParty(player) != null;
    }

    /**
     * プレイヤーが所属しているパーティーのリーダーを取得します。
     * 未参加の場合はnullを返します。
     */
    public Player getPartyLeader(Player player) {
        Party party = partyManager.getParty(player);
        if (party == null) return null;
        return Bukkit.getPlayer(party.getLeaderId());
    }

    /**
     * プレイヤーが所属しているパーティーの全メンバー(UUID)を取得します。
     * 未参加の場合は空のセットを返します。
     */
    public Set<UUID> getPartyMemberIds(Player player) {
        Party party = partyManager.getParty(player);
        if (party == null) return Collections.emptySet();
        // 直接参照を返さずコピーを返す（外部からの変更防止）
        return new HashSet<>(party.getMemberIds());
    }

    /**
     * プレイヤーが所属しているパーティーのオンラインメンバーを取得します。
     */
    public Set<Player> getOnlinePartyMembers(Player player) {
        Party party = partyManager.getParty(player);
        if (party == null) return Collections.emptySet();
        return party.getOnlineMembers();
    }

    /**
     * 指定したプレイヤーと同じパーティーにいるか確認します。
     */
    public boolean isInSameParty(Player player1, Player player2) {
        Party p1 = partyManager.getParty(player1);
        Party p2 = partyManager.getParty(player2);
        return p1 != null && p2 != null && p1.equals(p2); // Partyクラスにequalsの実装がない場合は == で比較
    }
}