package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.io.File;
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
    public void init() {
        // もし起動時にやりたいことがあればここに書く（なければ空でOK）
    }

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
        double exp = (level >= MAX_LEVEL) ? 0 : data.getExp(); // 上限ならEXPを0に

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

        // EXP獲得メッセージはシンプルに
        player.sendMessage("§a+ " + String.format("%.1f", amount) + " EXP"); // amountがdoubleなので、フォーマットを追加

        if (after > before) {
            // --- ★ レベルアップ通知を豪華にする ★ ---

            // 1. タイトル/サブタイトル通知
            String titleText = "§6§lLEVEL UP!";
            String subtitleText = String.format("§e%d §f→ §6§l%d", before, after);

            // タイトル表示 (フェードイン: 10 tick, 表示時間: 70 tick, フェードアウト: 20 tick)
            player.sendTitle(titleText, subtitleText, 10, 70, 20);

            // 2. サウンドエフェクト
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.5f); // 低いピッチで重厚な音
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f); // 達成感を出す

            // 4. チャットメッセージの装飾
            int attrPoints = 2; // 属性ポイント
            int skillPoints = 2; // スキルポイント

            player.sendMessage("§b§m--------------------------------------");
            player.sendMessage("§f§l    »» §6§lレベルアップ！ §f§l««");
            player.sendMessage(String.format("§e   レベル: %d §f→ §a§l%d", before, after));
            player.sendMessage("");
            player.sendMessage(String.format("§7- 獲得したボーナス -"));
            player.sendMessage(String.format("§c  » §c属性ポイント: §f§l%d", attrPoints));
            player.sendMessage(String.format("§d  » §dスキルポイント: §f§l%d", skillPoints));
            player.sendMessage("§b§m--------------------------------------");

            // 5. ポイント付与処理 (既存ロジック)
            Deepwither.getInstance().getAttributeManager().givePoints(player.getUniqueId(), attrPoints);

            UUID uuid = player.getUniqueId();
            SkilltreeManager.SkillData skilldata = Deepwither.getInstance().getSkilltreeManager().load(uuid);
            if (skilldata != null) {
                skilldata.setSkillPoint(skilldata.getSkillPoint() + skillPoints);
                Deepwither.getInstance().getSkilltreeManager().save(uuid, skilldata);
            }
        }

        if (after >= MAX_LEVEL) {
            player.sendMessage("§b§l§m--------------------------------------");
            player.sendMessage("§f§l    »» §3§l最大レベル到達！ §f§l««");
            player.sendMessage("§b   全ての戦いを乗り越えた証！");
            player.sendMessage("§b§l§m--------------------------------------");
        }
    }

    public void updatePlayerDisplay(Player player) {
        PlayerLevelData data = dataMap.get(player.getUniqueId());

        // 例: 現在のカスタムレベルと進捗データを取得 (あなたの実装に依存)
        int currentLevel = data.getLevel();
        double currentExp = data.getExp();
        double expToNextLevel = data.getRequiredExp();

        // ★ 経験値レベル表示を「現在レベル」に設定
        player.setLevel(currentLevel);

        // ★ 経験値バーを「レベルの進捗」に設定
        // 0.0 (空) から 1.0 (満タン) の間で計算
        float progress = (float) (currentExp / expToNextLevel);
        player.setExp(progress);
    }

    /**
     * プレイヤーのレベルと経験値をリセットします。（レベル1、経験値0）
     * @param player リセット対象のプレイヤー
     */
    public void resetLevel(Player player) {
        UUID uuid = player.getUniqueId();

        // 1. データマップ上のデータをリセット
        PlayerLevelData initialData = new PlayerLevelData(1, 0);
        dataMap.put(uuid, initialData);

        // 2. データベース上のデータを更新 (saveメソッドを流用)
        // 注意: saveメソッド内でもMAX_LEVELチェックが入るが、レベル1なので問題なし
        save(uuid);

        // 3. レベルリセットの通知
        player.sendMessage("§c§l§m--------------------------------------");
        player.sendMessage("§f§l    »» §c§lレベルリセット完了 §f§l««");
        player.sendMessage("§7   あなたのレベルと経験値が初期状態に戻りました。");
        player.sendMessage(String.format("§e   レベル: §a§l%d", initialData.getLevel()));
        player.sendMessage("§c§l§m--------------------------------------");
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f); // リセット完了音

        // 4. プレイヤーの表示を更新
        updatePlayerDisplay(player);

        // 5. 属性ポイントとスキルポイントのリセット処理 (!!! 要実装 !!!)
        // 属性ポイントのリセット
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