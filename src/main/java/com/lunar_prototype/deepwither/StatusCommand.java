package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.StatType;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;

public class StatusCommand implements CommandExecutor {

    private final LevelManager levelManager;
    private final StatManager statManager;
    private final CreditManager creditManager;

    public StatusCommand(LevelManager levelManager, StatManager statManager,CreditManager creditManager) {
        this.levelManager = levelManager;
        this.statManager = statManager;
        this.creditManager = creditManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cこのコマンドはプレイヤーのみ実行できます。");
            return true;
        }

        // ----------------------------------------------------
        // --- A. レベルと経験値の表示 ---
        // ----------------------------------------------------
        PlayerLevelData levelData = levelManager.get(player);
        if (levelData == null) {
            player.sendMessage("§cレベルデータが見つかりませんでした。");
            return true;
        }

        int currentLevel = levelData.getLevel();
        double currentExp = levelData.getExp();
        double expToNextLevel = levelData.getRequiredExp();
        double expPercent = (double) currentExp / expToNextLevel;
        Economy econ = Deepwither.getEconomy();

        player.sendMessage("§8§m----------------------------------");
        player.sendMessage("§e§l【 プレイヤー ステータス 】");
        player.sendMessage(" ");

        // 所持金情報
        player.sendMessage("§b§l> 所持金");
        player.sendMessage(" §e所持金: §a" + econ.getBalance(player));
        player.sendMessage(" ");

        // レベル情報
        player.sendMessage("§b§l> レベル情報");
        player.sendMessage(" §7レベル: §a" + currentLevel);
        player.sendMessage(" §7経験値: §f" + currentExp + " §7/ §f" + expToNextLevel + " §7(§e" + String.format("%.2f", expPercent * 100) + "%§7)");

        // 経験値バーの表示（任意）
        player.sendMessage(" " + createProgressBar(expPercent, 20, '§', 'a', '7'));
        player.sendMessage(" ");


        // ----------------------------------------------------
        // --- B. カスタムステータスの表示 ---
        // ----------------------------------------------------
        StatMap finalStats = statManager.getTotalStatsFromEquipment(player);

        player.sendMessage("§b§l> 戦闘ステータス");

        // HPの表示 (StatManagerからカスタムHPを取得)
        double currentHp = statManager.getActualCurrentHealth(player);
        double maxHp = statManager.getActualMaxHealth(player);

        // 表示を分かりやすくするため、HP関連は別に処理
        player.sendMessage(" §cHP: §f" + String.format("%.0f", currentHp) + " §7/ §f" + String.format("%.0f", maxHp));

        // 主要なステータス値を表示
        displayStat(player, "攻撃力", StatType.ATTACK_DAMAGE, finalStats, "§c");
        displayStat(player, "魔法攻撃力", StatType.MAGIC_DAMAGE, finalStats, "§c");
        displayStat(player, "防御力", StatType.DEFENSE, finalStats, "§9");
        displayStat(player, "クリダメ", StatType.CRIT_DAMAGE, finalStats, "§6", true);
        displayStat(player, "クリ率", StatType.CRIT_CHANCE, finalStats, "§6", true);
        displayStat(player, "魔力", StatType.MAGIC_DAMAGE, finalStats, "§d");
        displayStat(player, "魔法耐性", StatType.MAGIC_RESIST, finalStats, "§3");
        displayStat(player, "回復力", StatType.HP_REGEN, finalStats, "§a");

        // TraderManagerはプラグインインスタンスから取得することを仮定
        TraderManager traderManager = Deepwither.getInstance().getTraderManager();
        Set<String> allTraderIds = traderManager.getAllTraderIds(); // 全トレーダーIDを取得

        player.sendMessage("§b§l> トレーダー信用度");

        if (allTraderIds.isEmpty()) {
            player.sendMessage(" §7現在、ロードされているトレーダーがいません。");
        } else {
            // 全トレーダーIDをループし、CreditManagerから個別に信用度を取得
            allTraderIds.stream()
                    .sorted() // 見やすいようにソート
                    .forEach(traderId -> {
                        // CreditManagerに getCredit(UUID, traderId) があることを前提
                        // 信用度がない場合は 0 を返す想定
                        int credit = creditManager.getCredit(player.getUniqueId(), traderId);

                        player.sendMessage(" §7" + formatTraderId(traderId) + ": §b" + credit);
                    });
        }


        player.sendMessage("§8§m----------------------------------");

        return true;
    }

    // ステータス表示のヘルパーメソッド
    private void displayStat(Player player, String name, StatType type, StatMap stats, String color) {
        displayStat(player, name, type, stats, color, false);
    }

    // ステータス表示のヘルパーメソッド (パーセンテージ対応)
    private void displayStat(Player player, String name, StatType type, StatMap stats, String color, boolean isPercent) {
        double value = stats.getFinal(type);
        String formattedValue;

        if (isPercent) {
            formattedValue = String.format("%.1f", value) + "%";
        } else {
            formattedValue = String.format("%.0f", value);
        }

        player.sendMessage(" §7" + name + ": " + color + formattedValue);
    }

    // 経験値バー生成のヘルパーメソッド
    private String createProgressBar(double progress, int length, char colorChar, char filledColor, char emptyColor) {
        int filled = (int) Math.floor(progress * length);
        StringBuilder bar = new StringBuilder("§" + colorChar + "[");

        // 塗りつぶされた部分
        for (int i = 0; i < length; i++) {
            if (i < filled) {
                bar.append(colorChar).append(filledColor).append("■");
            } else {
                bar.append(colorChar).append(emptyColor).append("■");
            }
        }
        bar.append(colorChar).append("]");
        return bar.toString();
    }

    // トレーダーIDを見やすい名前に変換するヘルパーメソッド
    private String formatTraderId(String traderId) {
        // 例: lunaris_atelier -> Lunaris Atelier
        if (traderId == null || traderId.isEmpty()) return "Unknown";

        String formatted = traderId.replace('_', ' ');
        // 各単語の先頭を大文字にする処理（シンプル化のため、ここでは最初の一文字のみ大文字化）
        return formatted.substring(0, 1).toUpperCase() + formatted.substring(1);
    }
}