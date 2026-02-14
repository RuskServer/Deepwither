package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@DependsOn({DatabaseManager.class})
public class LevelManager implements IManager {
    private static final int MAX_LEVEL = 50;

    private final Map<UUID, PlayerLevelData> dataMap = new HashMap<>();
    private final DatabaseManager db;

    public LevelManager(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public void init() {}

    public void load(UUID uuid) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT level, exp FROM player_levels WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int level = Math.min(rs.getInt("level"), MAX_LEVEL);
                    double exp = rs.getDouble("exp");
                    dataMap.put(uuid, new PlayerLevelData(level, exp));
                } else {
                    dataMap.put(uuid, new PlayerLevelData(1, 0));
                    Deepwither.getInstance().getAttributeManager().givePoints(uuid, 2);
                    SkilltreeManager.SkillData skilldata = Deepwither.getInstance().getSkilltreeManager().load(uuid);
                    if (skilldata != null) {
                        skilldata.setSkillPoint(skilldata.getSkillPoint() + 2);
                        Deepwither.getInstance().getSkilltreeManager().save(uuid, skilldata);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void save(UUID uuid) {
        PlayerLevelData data = dataMap.get(uuid);
        if (data == null) return;

        int level = Math.min(data.getLevel(), MAX_LEVEL);
        double exp = (level >= MAX_LEVEL) ? 0 : data.getExp();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
            INSERT INTO player_levels (uuid, level, exp) VALUES (?, ?, ?)
            ON CONFLICT(uuid) DO UPDATE SET level = excluded.level, exp = excluded.exp
        """)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, level);
            ps.setDouble(3, exp);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void addExp(Player player, double amount) {
        PlayerLevelData data = dataMap.get(player.getUniqueId());
        if (data == null || data.getLevel() >= MAX_LEVEL) return;

        int before = data.getLevel();
        data.addExp(amount);
        int after = data.getLevel();

        player.sendMessage(Component.text("+ " + String.format("%.1f", amount) + " EXP", NamedTextColor.GREEN));

        if (after > before) {
            Title title = Title.title(
                    Component.text("LEVEL UP!", NamedTextColor.GOLD, TextDecoration.BOLD),
                    Component.text(before, NamedTextColor.YELLOW)
                            .append(Component.text(" → ", NamedTextColor.WHITE))
                            .append(Component.text(after, NamedTextColor.GOLD, TextDecoration.BOLD))
            );
            player.showTitle(title);

            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.5f);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

            int attrPoints = 2;
            int skillPoints = 2;

            Component separator = Component.text("--------------------------------------", NamedTextColor.AQUA).decoration(TextDecoration.STRIKETHROUGH, true);
            player.sendMessage(separator);
            player.sendMessage(Component.text("    »» ", NamedTextColor.WHITE, TextDecoration.BOLD)
                    .append(Component.text("レベルアップ！", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text(" ««", NamedTextColor.WHITE, TextDecoration.BOLD)));
            player.sendMessage(Component.text("   レベル: ", NamedTextColor.YELLOW)
                    .append(Component.text(before, NamedTextColor.WHITE))
                    .append(Component.text(" → ", NamedTextColor.WHITE))
                    .append(Component.text(after, NamedTextColor.GREEN, TextDecoration.BOLD)));
            player.sendMessage(Component.empty());
            player.sendMessage(Component.text("- 獲得したボーナス -", NamedTextColor.GRAY));
            player.sendMessage(Component.text("  » ", NamedTextColor.RED).append(Component.text("属性ポイント: ", NamedTextColor.RED)).append(Component.text(attrPoints, NamedTextColor.WHITE, TextDecoration.BOLD)));
            player.sendMessage(Component.text("  » ", NamedTextColor.LIGHT_PURPLE).append(Component.text("スキルポイント: ", NamedTextColor.LIGHT_PURPLE)).append(Component.text(skillPoints, NamedTextColor.WHITE, TextDecoration.BOLD)));
            player.sendMessage(separator);

            Deepwither.getInstance().getAttributeManager().givePoints(player.getUniqueId(), attrPoints);

            UUID uuid = player.getUniqueId();
            SkilltreeManager.SkillData skilldata = Deepwither.getInstance().getSkilltreeManager().load(uuid);
            if (skilldata != null) {
                skilldata.setSkillPoint(skilldata.getSkillPoint() + skillPoints);
                Deepwither.getInstance().getSkilltreeManager().save(uuid, skilldata);
            }
        }

        if (after >= MAX_LEVEL) {
            Component separator = Component.text("--------------------------------------", NamedTextColor.AQUA).decoration(TextDecoration.STRIKETHROUGH, true);
            player.sendMessage(separator);
            player.sendMessage(Component.text("    »» ", NamedTextColor.WHITE, TextDecoration.BOLD)
                    .append(Component.text("最大レベル到達！", NamedTextColor.DARK_AQUA, TextDecoration.BOLD))
                    .append(Component.text(" ««", NamedTextColor.WHITE, TextDecoration.BOLD)));
            player.sendMessage(Component.text("   全ての戦いを乗り越えた証！", NamedTextColor.AQUA));
            player.sendMessage(separator);
        }
    }

    public void updatePlayerDisplay(Player player) {
        PlayerLevelData data = dataMap.get(player.getUniqueId());
        int currentLevel = data.getLevel();
        double currentExp = data.getExp();
        double expToNextLevel = data.getRequiredExp();
        player.setLevel(currentLevel);
        float progress = (float) (currentExp / expToNextLevel);
        player.setExp(progress);
    }

    public void resetLevel(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerLevelData initialData = new PlayerLevelData(1, 0);
        dataMap.put(uuid, initialData);
        save(uuid);

        Component separator = Component.text("--------------------------------------", NamedTextColor.RED).decoration(TextDecoration.STRIKETHROUGH, true);
        player.sendMessage(separator);
        player.sendMessage(Component.text("    »» ", NamedTextColor.WHITE, TextDecoration.BOLD)
                .append(Component.text("レベルリセット完了", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text(" ««", NamedTextColor.WHITE, TextDecoration.BOLD)));
        player.sendMessage(Component.text("   あなたのレベルと経験値が初期状態に戻りました。", NamedTextColor.GRAY));
        player.sendMessage(Component.text("   レベル: ", NamedTextColor.YELLOW).append(Component.text(initialData.getLevel(), NamedTextColor.GREEN, TextDecoration.BOLD)));
        player.sendMessage(separator);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);

        updatePlayerDisplay(player);

        Deepwither.getInstance().getSkilltreeManager().resetSkillTree(player.getUniqueId());
        SkilltreeManager.SkillData skilldata = Deepwither.getInstance().getSkilltreeManager().load(player.getUniqueId());
        skilldata.setSkillPoint(0);
        Deepwither.getInstance().getSkilltreeManager().save(player.getUniqueId(), skilldata);
        PlayerAttributeData data = Deepwither.getInstance().getAttributeManager().get(uuid);
        if (data != null) {
            for (StatType type : StatType.values()) {
                data.setAllocated(type, 0);
            }
            data.addPoints(0);
        }
    }

    public PlayerLevelData get(Player player) {
        return dataMap.get(player.getUniqueId());
    }

    public void unload(UUID uuid) {
        save(uuid);
        dataMap.remove(uuid);
    }
}
