package com.lunar_prototype.deepwither.clan;

import com.lunar_prototype.deepwither.DatabaseManager;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@DependsOn({DatabaseManager.class})
public class ClanManager implements IManager {
    private final Map<String, Clan> clans = new HashMap<>();
    private final Map<UUID, String> playerClanMap = new HashMap<>();
    private final Map<UUID, String> pendingInvites = new HashMap<>();
    private final DatabaseManager db;

    public ClanManager(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public void init() throws Exception {
        loadClansFromDatabase();
    }

    private void loadClansFromDatabase() throws SQLException {
        try (Connection conn = db.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM clans")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    String id = rs.getString("id");
                    String name = rs.getString("name");
                    UUID owner = UUID.fromString(rs.getString("owner"));
                    clans.put(id, new Clan(id, name, owner));
                }
            }

            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM clan_members")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                    String clanId = rs.getString("clan_id");
                    if (clans.containsKey(clanId)) {
                        clans.get(clanId).addMember(uuid);
                        playerClanMap.put(uuid, clanId);
                    }
                }
            }
        }
    }

    public boolean createClan(Player owner, String name) {
        if (playerClanMap.containsKey(owner.getUniqueId())) {
            owner.sendMessage(Component.text("既にクランに所属しています。", NamedTextColor.RED));
            return false;
        }

        String id = UUID.randomUUID().toString();
        Clan newClan = new Clan(id, name, owner.getUniqueId());

        clans.put(id, newClan);
        playerClanMap.put(owner.getUniqueId(), id);

        saveClanToDatabase(newClan);
        saveMemberToDatabase(owner.getUniqueId(), id);

        owner.sendMessage(Component.text("クラン ", NamedTextColor.GREEN)
                .append(Component.text(name, NamedTextColor.WHITE))
                .append(Component.text(" を結成しました！", NamedTextColor.GREEN)));
        return true;
    }

    public void leaveClan(Player player) {
        Clan clan = getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(Component.text("クランに所属していません。", NamedTextColor.RED));
            return;
        }

        if (clan.getOwner().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("リーダーは脱退できません。解散するには /clan disband を使用してください。", NamedTextColor.RED));
            return;
        }

        clan.removeMember(player.getUniqueId());
        playerClanMap.remove(player.getUniqueId());
        deleteMemberFromDatabase(player.getUniqueId());

        player.sendMessage(Component.text("クラン ", NamedTextColor.GREEN)
                .append(Component.text(clan.getName(), NamedTextColor.WHITE))
                .append(Component.text(" から脱退しました。", NamedTextColor.GREEN)));
        clan.broadcast(Component.text(player.getName(), NamedTextColor.YELLOW)
                .append(Component.text(" がクランを脱退しました。", NamedTextColor.YELLOW)));
    }

    public void disbandClan(Player leader) {
        Clan clan = getClanByPlayer(leader.getUniqueId());
        if (clan == null || !clan.getOwner().equals(leader.getUniqueId())) {
            leader.sendMessage(Component.text("クランリーダーのみが解散できます。", NamedTextColor.RED));
            return;
        }

        String clanName = clan.getName();
        String clanId = clan.getId();

        for (UUID memberUuid : clan.getMembers()) {
            playerClanMap.remove(memberUuid);
        }
        clans.remove(clanId);
        deleteClanFromDatabase(clanId);

        leader.sendMessage(Component.text("クラン ", NamedTextColor.GREEN)
                .append(Component.text(clanName, NamedTextColor.WHITE))
                .append(Component.text(" を解散しました。", NamedTextColor.GREEN)));
    }

    public void invitePlayer(Player sender, Player target) {
        Clan clan = getClanByPlayer(sender.getUniqueId());

        if (clan == null || !clan.getOwner().equals(sender.getUniqueId())) {
            sender.sendMessage(Component.text("クランリーダーのみが招待できます。", NamedTextColor.RED));
            return;
        }

        if (playerClanMap.containsKey(target.getUniqueId())) {
            sender.sendMessage(Component.text("相手は既に他のクランに所属しています。", NamedTextColor.RED));
            return;
        }

        pendingInvites.put(target.getUniqueId(), clan.getId());

        target.sendMessage(Component.text(sender.getName(), NamedTextColor.YELLOW)
                .append(Component.text(" からクラン ", NamedTextColor.YELLOW))
                .append(Component.text(clan.getName(), NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" への招待が届きました。", NamedTextColor.YELLOW)));
        target.sendMessage(Component.text("/clan join で参加できます。", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text(target.getName() + " に招待を送りました。", NamedTextColor.GREEN));
    }

    public void joinClan(Player player) {
        if (!pendingInvites.containsKey(player.getUniqueId())) {
            player.sendMessage(Component.text("招待が来ていません。", NamedTextColor.RED));
            return;
        }

        String clanId = pendingInvites.remove(player.getUniqueId());
        Clan clan = clans.get(clanId);

        if (clan == null) {
            player.sendMessage(Component.text("そのクランは既に解散したようです。", NamedTextColor.RED));
            return;
        }

        clan.addMember(player.getUniqueId());
        playerClanMap.put(player.getUniqueId(), clanId);
        saveMemberToDatabase(player.getUniqueId(), clanId);

        clan.broadcast(Component.text(player.getName() + " がクランに参加しました！", NamedTextColor.GREEN));
    }

    private void saveClanToDatabase(Clan clan) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO clans (id, name, tag, owner) VALUES (?, ?, ?, ?) ON CONFLICT(id) DO UPDATE SET owner=excluded.owner")) {
            ps.setString(1, clan.getId());
            ps.setString(2, clan.getName());
            ps.setString(3, clan.getTag());
            ps.setString(4, clan.getOwner().toString());
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void saveMemberToDatabase(UUID uuid, String clanId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO clan_members (player_uuid, clan_id) VALUES (?, ?) ON CONFLICT(player_uuid) DO UPDATE SET clan_id=excluded.clan_id")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, clanId);
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void deleteMemberFromDatabase(UUID uuid) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM clan_members WHERE player_uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void deleteClanFromDatabase(String clanId) {
        try (Connection conn = db.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM clans WHERE id = ?")) {
                ps.setString(1, clanId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM clan_members WHERE clan_id = ?")) {
                ps.setString(1, clanId);
                ps.executeUpdate();
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public Clan getClanByPlayer(UUID uuid) {
        String clanId = playerClanMap.get(uuid);
        return clanId != null ? clans.get(clanId) : null;
    }

    @Override
    public void shutdown() {
        clans.values().forEach(this::saveClanToDatabase);
    }
}
