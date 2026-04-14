package com.lunar_prototype.deepwither.mail;

import com.lunar_prototype.deepwither.DatabaseManager;
import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import com.lunar_prototype.deepwither.util.InventoryHelper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@DependsOn({DatabaseManager.class})
public class MailManager implements IManager {

    private final Deepwither plugin;
    private final DatabaseManager databaseManager;
    private final Map<UUID, Map<UUID, MailMessage>> mailboxes = new ConcurrentHashMap<>();

    public MailManager(Deepwither plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    @Override
    public void init() throws Exception {
        loadAllMails();
    }

    @Override
    public void shutdown() {
    }

    public MailMessage sendMail(UUID recipientId, String title, List<String> bodyLines, List<ItemStack> rewards) {
        List<String> rewardPayload = new ArrayList<>();
        if (rewards != null) {
            for (ItemStack reward : rewards) {
                if (reward == null || reward.getType() == Material.AIR || reward.getAmount() <= 0) {
                    continue;
                }
                rewardPayload.add(serializeItem(reward.clone()));
            }
        }

        MailMessage mail = new MailMessage(recipientId, title, bodyLines, rewardPayload);
        mailboxes.computeIfAbsent(recipientId, ignored -> new ConcurrentHashMap<>()).put(mail.getId(), mail);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> saveMail(mail));
        return mail;
    }

    public MailMessage sendMail(UUID recipientId, String title, String body, ItemStack... rewards) {
        List<String> lines = new ArrayList<>();
        if (body != null && !body.isBlank()) {
            Collections.addAll(lines, body.split("\\R"));
        }
        List<ItemStack> rewardList = new ArrayList<>();
        if (rewards != null) {
            Collections.addAll(rewardList, rewards);
        }
        return sendMail(recipientId, title, lines, rewardList);
    }

    public List<MailMessage> getInbox(UUID recipientId) {
        Map<UUID, MailMessage> mailbox = mailboxes.get(recipientId);
        if (mailbox == null || mailbox.isEmpty()) {
            return List.of();
        }
        return mailbox.values().stream()
                .sorted(Comparator.comparingLong(MailMessage::getCreatedAt).reversed())
                .toList();
    }

    public int getInboxCount(UUID recipientId) {
        Map<UUID, MailMessage> mailbox = mailboxes.get(recipientId);
        return mailbox == null ? 0 : mailbox.size();
    }

    public boolean openMail(Player player, UUID mailId) {
        UUID recipientId = player.getUniqueId();
        Map<UUID, MailMessage> mailbox = mailboxes.get(recipientId);
        if (mailbox == null) {
            player.sendMessage(Component.text("[メール] ", NamedTextColor.AQUA)
                    .append(Component.text("受信メールはありません。", NamedTextColor.GRAY)));
            return false;
        }

        MailMessage mail = mailbox.remove(mailId);
        if (mail == null) {
            player.sendMessage(Component.text("[メール] ", NamedTextColor.AQUA)
                    .append(Component.text("そのメールは既に開封済みです。", NamedTextColor.RED)));
            return false;
        }

        if (mailbox.isEmpty()) {
            mailboxes.remove(recipientId, mailbox);
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> deleteMail(mailId));

        player.sendMessage(Component.text("========== [ メール ] ==========", NamedTextColor.GOLD));
        player.sendMessage(Component.text(mail.getTitle(), NamedTextColor.YELLOW));
        player.sendMessage(Component.empty());

        for (String line : mail.getBodyLines()) {
            player.sendMessage(Component.text(line, NamedTextColor.WHITE));
        }

        if (!mail.getBodyLines().isEmpty()) {
            player.sendMessage(Component.empty());
        }

        int delivered = 0;
        int dropped = 0;
        for (String rewardPayload : mail.getRewardItems()) {
            ItemStack reward = deserializeItem(rewardPayload);
            if (reward == null || reward.getType() == Material.AIR || reward.getAmount() <= 0) {
                continue;
            }

            ItemStack remaining = reward.clone();
            InventoryHelper.applyStrategy(InventoryHelper.VANILLA, player.getInventory(), remaining);
            if (remaining.isEmpty()) {
                delivered++;
            } else {
                dropped++;
                player.getWorld().dropItemNaturally(player.getLocation(), remaining);
            }
        }

        if (delivered > 0) {
            InventoryHelper.playPickupSound(player);
            player.sendMessage(Component.text("[メール] ", NamedTextColor.AQUA)
                    .append(Component.text("報酬を受け取りました。", NamedTextColor.GREEN)));
        } else {
            player.sendMessage(Component.text("[メール] ", NamedTextColor.AQUA)
                    .append(Component.text("受け取れる報酬はありませんでした。", NamedTextColor.GRAY)));
        }

        if (dropped > 0) {
            player.sendMessage(Component.text("[メール] ", NamedTextColor.AQUA)
                    .append(Component.text("インベントリに入らなかった報酬は足元にドロップしました。", NamedTextColor.YELLOW)));
        }

        player.sendMessage(Component.text("================================", NamedTextColor.GOLD));
        return true;
    }

    private void loadAllMails() throws SQLException {
        mailboxes.clear();
        try (java.sql.Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT mail_json FROM player_mailbox");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                MailMessage mail = databaseManager.getGson().fromJson(rs.getString("mail_json"), MailMessage.class);
                if (mail == null) {
                    continue;
                }
                mail.normalize();
                if (mail.getId() == null || mail.getRecipientId() == null) {
                    continue;
                }
                mailboxes.computeIfAbsent(mail.getRecipientId(), ignored -> new ConcurrentHashMap<>())
                        .put(mail.getId(), mail);
            }
        }
    }

    private void saveMail(MailMessage mail) {
        String mailIdStr = mail.getId().toString();
        String recipientIdStr = mail.getRecipientId().toString();
        String mailJson = databaseManager.getGson().toJson(mail);

        try (java.sql.Connection conn = databaseManager.getConnection()) {
            // 存在チェック
            boolean exists = false;
            try (PreparedStatement checkPs = conn.prepareStatement("SELECT 1 FROM player_mailbox WHERE mail_id = ?")) {
                checkPs.setString(1, mailIdStr);
                try (ResultSet rs = checkPs.executeQuery()) {
                    exists = rs.next();
                }
            }

            if (exists) {
                // UPDATE
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE player_mailbox SET recipient_uuid = ?, mail_json = ? WHERE mail_id = ?")) {
                    ps.setString(1, recipientIdStr);
                    ps.setString(2, mailJson);
                    ps.setString(3, mailIdStr);
                    ps.executeUpdate();
                }
            } else {
                // INSERT
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO player_mailbox (mail_id, recipient_uuid, mail_json) VALUES (?, ?, ?)")) {
                    ps.setString(1, mailIdStr);
                    ps.setString(2, recipientIdStr);
                    ps.setString(3, mailJson);
                    ps.executeUpdate();
                } catch (SQLException e) {
                    if (e.getSQLState().startsWith("23")) {
                        try (PreparedStatement ps = conn.prepareStatement(
                                "UPDATE player_mailbox SET recipient_uuid = ?, mail_json = ? WHERE mail_id = ?")) {
                            ps.setString(1, recipientIdStr);
                            ps.setString(2, mailJson);
                            ps.setString(3, mailIdStr);
                            ps.executeUpdate();
                        }
                    } else {
                        throw e;
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("メール保存に失敗しました: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void deleteMail(UUID mailId) {
        try (java.sql.Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM player_mailbox WHERE mail_id = ?")) {
            ps.setString(1, mailId.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("メール削除に失敗しました: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String serializeItem(ItemStack item) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            dataOutput.writeObject(item);
            dataOutput.close();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            return "";
        }
    }

    private ItemStack deserializeItem(String data) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            ItemStack item = (ItemStack) dataInput.readObject();
            dataInput.close();
            return item;
        } catch (Exception e) {
            return null;
        }
    }
}
