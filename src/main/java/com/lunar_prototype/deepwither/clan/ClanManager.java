package com.lunar_prototype.deepwither.clan;

import com.lunar_prototype.deepwither.DatabaseManager;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
        Connection conn = db.getConnection();

        // クラン本体のロード
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM clans")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String id = rs.getString("id");
                String name = rs.getString("name");
                UUID owner = UUID.fromString(rs.getString("owner"));
                clans.put(id, new Clan(id, name, owner));
            }
        } // ここで ps は閉じられるが、conn は閉じられない

        // メンバーのロード
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

    public boolean createClan(Player owner, String name) {
        if (playerClanMap.containsKey(owner.getUniqueId())) {
            owner.sendMessage("§c既にクランに所属しています。");
            return false;
        }

        String id = UUID.randomUUID().toString();
        Clan newClan = new Clan(id, name, owner.getUniqueId());

        clans.put(id, newClan);
        playerClanMap.put(owner.getUniqueId(), id);

        // 非同期保存が理想ですが、まずは直接保存
        saveClanToDatabase(newClan);
        saveMemberToDatabase(owner.getUniqueId(), id);

        owner.sendMessage("§aクラン " + name + " を結成しました！");
        return true;
    }

    /**
     * クランへの招待を送る
     * 永続化の必要がない一時的なメモリデータとして管理します
     */
    public void invitePlayer(Player sender, Player target) {
        // 送信者がクランに所属しているか確認
        Clan clan = getClanByPlayer(sender.getUniqueId());

        // 権限チェック: クランが存在し、送信者がオーナーであること
        if (clan == null || !clan.getOwner().equals(sender.getUniqueId())) {
            sender.sendMessage("§cクランリーダーのみが招待できます。");
            return;
        }

        // ターゲットが既にクランに入っているか確認
        if (playerClanMap.containsKey(target.getUniqueId())) {
            sender.sendMessage("§c相手は既に他のクランに所属しています。");
            return;
        }

        // 招待リストへの登録（メモリ上のみ）
        pendingInvites.put(target.getUniqueId(), clan.getId());

        // メッセージ通知
        target.sendMessage("§e" + sender.getName() + " からクラン §6" + clan.getName() + " §eへの招待が届きました。");
        target.sendMessage("§e/clan join で参加できます。");
        sender.sendMessage("§a" + target.getName() + " に招待を送りました。");
    }

    public void joinClan(Player player) {
        if (!pendingInvites.containsKey(player.getUniqueId())) {
            player.sendMessage("§c招待が来ていません。");
            return;
        }

        String clanId = pendingInvites.remove(player.getUniqueId());
        Clan clan = clans.get(clanId);

        if (clan == null) {
            player.sendMessage("§cそのクランは既に解散したようです。");
            return;
        }

        clan.addMember(player.getUniqueId());
        playerClanMap.put(player.getUniqueId(), clanId);

        saveMemberToDatabase(player.getUniqueId(), clanId);

        clan.broadcast("§a" + player.getName() + " がクランに参加しました！");
    }

    // --- データベース保存用ヘルパー ---

    private void saveClanToDatabase(Clan clan) {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "INSERT INTO clans (id, name, tag, owner) VALUES (?, ?, ?, ?) ON CONFLICT(id) DO UPDATE SET owner=excluded.owner")) {
            ps.setString(1, clan.getId());
            ps.setString(2, clan.getName());
            ps.setString(3, clan.getTag());
            ps.setString(4, clan.getOwner().toString());
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void saveMemberToDatabase(UUID uuid, String clanId) {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "INSERT INTO clan_members (player_uuid, clan_id) VALUES (?, ?) ON CONFLICT(player_uuid) DO UPDATE SET clan_id=excluded.clan_id")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, clanId);
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public Clan getClanByPlayer(UUID uuid) {
        String clanId = playerClanMap.get(uuid);
        return clanId != null ? clans.get(clanId) : null;
    }

    @Override
    public void shutdown() {
        // サーバー停止時、念のため全クランの最新状態を保存
        clans.values().forEach(this::saveClanToDatabase);
    }
}