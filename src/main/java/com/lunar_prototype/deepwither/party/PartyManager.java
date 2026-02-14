package com.lunar_prototype.deepwither.party;

import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@DependsOn({})
public class PartyManager implements IManager {
    // プレイヤーUUID -> その人が所属するPartyインスタンス
    private final Map<UUID, Party> playerPartyMap = new HashMap<>();

    // 招待リスト: 招待された人(UUID) -> 招待したリーダー(UUID)
    private final Map<UUID, UUID> pendingInvites = new HashMap<>();
    private final JavaPlugin plugin;

    public PartyManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {}

    @Override
    public void shutdown() {}

    /**
     * パーティーを作成する（既存パーティーがない場合）
     */
    public void createParty(Player leader) {
        if (playerPartyMap.containsKey(leader.getUniqueId())) return;
        Party party = new Party(leader.getUniqueId());
        playerPartyMap.put(leader.getUniqueId(), party);
        leader.sendMessage(Component.text("パーティーを作成しました！", NamedTextColor.GREEN));
    }

    /**
     * プレイヤーを招待する
     */
    public void invitePlayer(Player leader, Player target) {
        // 既に招待済みかチェック
        if (pendingInvites.containsKey(target.getUniqueId())) {
            leader.sendMessage(Component.text("そのプレイヤーは既に招待を受けています。", NamedTextColor.RED));
            return;
        }

        // 招待を登録
        pendingInvites.put(target.getUniqueId(), leader.getUniqueId());

        // 60秒後に招待を無効化
        new BukkitRunnable() {
            @Override
            public void run() {
                if (pendingInvites.containsKey(target.getUniqueId()) &&
                        pendingInvites.get(target.getUniqueId()).equals(leader.getUniqueId())) {
                    pendingInvites.remove(target.getUniqueId());
                    if (target.isOnline()) {
                        target.sendMessage(Component.text(leader.getName() + " からの招待の有効期限が切れました。", NamedTextColor.YELLOW));
                    }
                    if (leader.isOnline()) {
                        leader.sendMessage(Component.text(target.getName() + " への招待の有効期限が切れました。", NamedTextColor.YELLOW));
                    }
                }
            }
        }.runTaskLater(plugin, 20L * 60); // 60秒
    }

    /**
     * 招待を受け入れる
     */
    public void acceptInvite(Player player) {
        if (!pendingInvites.containsKey(player.getUniqueId())) {
            player.sendMessage(Component.text("招待を受けていません。", NamedTextColor.RED));
            return;
        }

        UUID leaderId = pendingInvites.remove(player.getUniqueId());

        // リーダーがまだパーティーを持っているか確認
        // まだ持っていなければ作成（招待時に作成していないケースへの対応）
        if (!playerPartyMap.containsKey(leaderId)) {
            // リーダーがオフラインなら失敗
            Player leader = Bukkit.getPlayer(leaderId);
            if (leader == null || !leader.isOnline()) {
                player.sendMessage(Component.text("招待者がオフラインのため参加できませんでした。", NamedTextColor.RED));
                return;
            }
            createParty(leader);
        }

        Party party = playerPartyMap.get(leaderId);
        joinPartyLogic(player, party);
    }

    /**
     * パーティーに参加させる内部ロジック
     */
    private void joinPartyLogic(Player player, Party party) {
        if (playerPartyMap.containsKey(player.getUniqueId())) {
            player.sendMessage(Component.text("既にパーティーに参加しています。先に脱退してください。", NamedTextColor.RED));
            return;
        }

        party.addMember(player.getUniqueId());
        playerPartyMap.put(player.getUniqueId(), party);

        // 通知
        Component msg = Component.text(player.getName() + " がパーティーに参加しました！", NamedTextColor.GREEN);
        party.getOnlineMembers().forEach(p -> p.sendMessage(msg));
    }

    /**
     * パーティーから離脱する
     */
    public void leaveParty(Player player) {
        Party party = playerPartyMap.get(player.getUniqueId());
        if (party == null) {
            player.sendMessage(Component.text("パーティーに参加していません。", NamedTextColor.RED));
            return;
        }

        // リーダーが抜ける場合は解散（またはリーダー委譲だが今回は解散にする）
        if (party.getLeaderId().equals(player.getUniqueId())) {
            disbandParty(player);
            return;
        }

        // メンバーからの削除
        party.removeMember(player.getUniqueId());
        playerPartyMap.remove(player.getUniqueId());

        player.sendMessage(Component.text("パーティーから脱退しました。", NamedTextColor.YELLOW));
        Component msg = Component.text(player.getName() + " が脱退しました。", NamedTextColor.YELLOW);
        party.getOnlineMembers().forEach(p -> p.sendMessage(msg));
    }

    /**
     * メンバーをキックする（リーダーのみ）
     */
    public void kickMember(Player leader, String targetName) {
        Party party = getParty(leader);
        if (party == null || !party.getLeaderId().equals(leader.getUniqueId())) {
            leader.sendMessage(Component.text("あなたはパーティーリーダーではありません。", NamedTextColor.RED));
            return;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) { // オフライン等の場合
            // ※本来はUUIDで検索するロジックが必要だが簡易実装
            leader.sendMessage(Component.text("プレイヤーが見つかりません。", NamedTextColor.RED));
            return;
        }

        if (!party.isMember(target.getUniqueId())) {
            leader.sendMessage(Component.text("そのプレイヤーはメンバーではありません。", NamedTextColor.RED));
            return;
        }

        if (target.equals(leader)) {
            leader.sendMessage(Component.text("自分自身はキックできません。解散を使用してください。", NamedTextColor.RED));
            return;
        }

        party.removeMember(target.getUniqueId());
        playerPartyMap.remove(target.getUniqueId());

        target.sendMessage(Component.text("パーティーから追放されました。", NamedTextColor.RED));
        Component msg = Component.text(target.getName() + " が追放されました。", NamedTextColor.YELLOW);
        party.getOnlineMembers().forEach(p -> p.sendMessage(msg));
    }

    /**
     * パーティーを解散する
     */
    public void disbandParty(Player leader) {
        Party party = getParty(leader);
        if (party == null || !party.getLeaderId().equals(leader.getUniqueId())) {
            leader.sendMessage(Component.text("解散権限がありません。", NamedTextColor.RED));
            return;
        }

        // 全員に通知 & Mapから削除
        for (UUID memberId : party.getMemberIds()) {
            playerPartyMap.remove(memberId);
            Player p = Bukkit.getPlayer(memberId);
            if (p != null && p.isOnline()) {
                p.sendMessage(Component.text("パーティーが解散されました。", NamedTextColor.RED));
            }
        }
    }

    public Party getParty(Player player) {
        return playerPartyMap.get(player.getUniqueId());
    }
}
