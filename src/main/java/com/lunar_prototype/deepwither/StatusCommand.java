package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.api.stat.IStatManager;
import com.lunar_prototype.deepwither.profession.PlayerProfessionData;
import com.lunar_prototype.deepwither.profession.ProfessionManager;
import com.lunar_prototype.deepwither.profession.ProfessionType;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class StatusCommand implements CommandExecutor {

    private final LevelManager levelManager;
    private final IStatManager statManager;
    private final CreditManager creditManager;
    private final ProfessionManager professionManager; // ★追加

    public StatusCommand(LevelManager levelManager, IStatManager statManager, CreditManager creditManager, ProfessionManager professionManager) {
        this.levelManager = levelManager;
        this.statManager = statManager;
        this.creditManager = creditManager;
        this.professionManager = professionManager; // ★初期化
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cこのコマンドはプレイヤーのみ実行できます。");
            return true;
        }

        // データの取得
        PlayerLevelData levelData = levelManager.get(player);
        if (levelData == null) {
            player.sendMessage("§cデータロード中です。しばらくお待ちください。");
            return true;
        }

        Economy econ = Deepwither.getEconomy();
        StatMap finalStats = statManager.getTotalStats(player);

        // --- レイアウト開始 ---
        player.sendMessage("§8§m---------------------------------------");
        player.sendMessage("       §e§l【 プレイヤー ステータス 】");
        player.sendMessage("");

        // ----------------------------------------------------
        // [1] 基本情報 (レベル・所持金)
        // ----------------------------------------------------
        int currentLevel = levelData.getLevel();
        double currentExp = levelData.getExp();
        double expToNextLevel = levelData.getRequiredExp();
        double expPercent = (currentExp / expToNextLevel) * 100;

        player.sendMessage(" §b§l[ 基本情報 ]");
        player.sendMessage(String.format("  §7Lv: §a%d §7(§e%.1f%%§7)", currentLevel, expPercent));
        player.sendMessage("  §7所持金  : §6" + econ.format(econ.getBalance(player)));
        player.sendMessage("");

        // ----------------------------------------------------
        // [2] 戦闘ステータス
        // ----------------------------------------------------
        double currentHp = statManager.getActualCurrentHealth(player);
        double maxHp = statManager.getActualMaxHealth(player);

        player.sendMessage(" §c§l[ 戦闘ステータス ]");
        player.sendMessage("  §7HP: §f" + String.format("%.0f", currentHp) + " §7/ §f" + String.format("%.0f", maxHp));

        // 2カラムで表示するようなイメージで整形
        String atk = getStatString("攻撃力", StatType.ATTACK_DAMAGE, finalStats, "§c", false);
        String def = getStatString("防御力", StatType.DEFENSE, finalStats, "§9", false);
        player.sendMessage("  " + atk + "   " + def);

        String matk = getStatString("魔攻力", StatType.MAGIC_DAMAGE, finalStats, "§d", false);
        String mres = getStatString("魔耐性", StatType.MAGIC_RESIST, finalStats, "§3", false);
        player.sendMessage("  " + matk + "   " + mres);

        String maoeatk = getStatString("魔AoE", StatType.MAGIC_AOE_DAMAGE, finalStats, "§d", false);
        String mburstres = getStatString("魔バースト", StatType.MAGIC_BURST_DAMAGE, finalStats, "§3", false);
        player.sendMessage("  " + maoeatk + "   " + mburstres);

        String critD = getStatString("クリ倍率", StatType.CRIT_DAMAGE, finalStats, "§6", true);
        String critC = getStatString("クリ率", StatType.CRIT_CHANCE, finalStats, "§6", true);
        player.sendMessage("  " + critD + "   " + critC);

        player.sendMessage("  " + getStatString("回復力", StatType.HP_REGEN, finalStats, "§a", false));
        player.sendMessage("");

        // ----------------------------------------------------
        // [3] 職業スキル (★新規追加)
        // ----------------------------------------------------
        player.sendMessage(" §a§l[ 職業スキル ]");
        PlayerProfessionData profData = professionManager.getData(player);

        for (ProfessionType type : ProfessionType.values()) {
            long totalExp = profData.getExp(type);
            int profLevel = professionManager.getLevel(totalExp);

            // 次のレベルまでの進捗計算（簡易版: ProfessionManagerの計算式に依存）
            // 正確な進捗バーを出すには「現在のレベルの開始Exp」と「次のレベルの開始Exp」が必要ですが、
            // ここでは簡易的に「現在の総Exp / (現在のレベル+1になるための総Exp)」を表示するか、
            // 以前のExp計算ロジックを公開する必要があります。
            // 今回はシンプルに「Lv.XX (Total Exp: YYY)」とします。

            String typeName = formatProfessionName(type);
            player.sendMessage(String.format("  §7%s: §aLv.%d §7(Total: %d xp)", typeName, profLevel, totalExp));
        }
        player.sendMessage("");

        // ----------------------------------------------------
        // [4] トレーダー信用度
        // ----------------------------------------------------
        player.sendMessage(" §e§l[ 信用度 ]");
        TraderManager traderManager = Deepwither.getInstance().getTraderManager();
        Set<String> allTraderIds = traderManager.getAllTraderIds();

        if (allTraderIds.isEmpty()) {
            player.sendMessage("  §7(データなし)");
        } else {
            // IDリストを変換・保持
            List<String> ids = new ArrayList<>(allTraderIds);

            // 2個ずつループして表示
            for (int i = 0; i < ids.size(); i += 2) {
                String id1 = ids.get(i);
                String name1 = traderManager.getTraderName(id1); // 日本語名を取得
                int credit1 = creditManager.getCredit(player.getUniqueId(), id1);
                String entry1 = String.format("  §7%s: §b%d", name1, credit1);

                // 2つ目の要素があるか確認
                if (i + 1 < ids.size()) {
                    String id2 = ids.get(i + 1);
                    String name2 = traderManager.getTraderName(id2); // 日本語名を取得
                    int credit2 = creditManager.getCredit(player.getUniqueId(), id2);
                    // 1つ目の表示幅を揃える（例: 全角8文字分程度を想定した空白調整）
                    player.sendMessage(String.format("%-20s  §7%s: §b%d", entry1, name2, credit2));
                } else {
                    player.sendMessage(entry1);
                }
            }
        }

        player.sendMessage("§8§m---------------------------------------");
        return true;
    }

    // --- ヘルパーメソッド群 ---

    // ステータス文字列生成（メッセージ送信ではなく文字列を返すように変更）
    private String getStatString(String name, StatType type, StatMap stats, String color, boolean isPercent) {
        double value = stats.getFinal(type);
        String valStr = isPercent ? String.format("%.1f%%", value) : String.format("%.0f", value);
        return String.format("§7%s: %s%s", name, color, valStr);
    }

    // トレーダーIDの整形
    private String formatTraderId(String traderId) {
        if (traderId == null || traderId.isEmpty()) return "Unknown";
        // アンダースコアをスペースに＆単語ごとにキャピタライズなどの処理があればここに追加
        return traderId;
    }

    // 職業名の整形
    private String formatProfessionName(ProfessionType type) {
        switch (type) {
            case MINING: return "採掘";
            case FISHING: return "釣り";
            // case FISHING: return "釣り"; // 将来用
            default: return type.name();
        }
    }
}