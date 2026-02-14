package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.api.stat.IStatManager;
import com.lunar_prototype.deepwither.profession.PlayerProfessionData;
import com.lunar_prototype.deepwither.profession.ProfessionManager;
import com.lunar_prototype.deepwither.profession.ProfessionType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.milkbowl.vault.economy.Economy;
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
    private final ProfessionManager professionManager;

    public StatusCommand(LevelManager levelManager, IStatManager statManager, CreditManager creditManager, ProfessionManager professionManager) {
        this.levelManager = levelManager;
        this.statManager = statManager;
        this.creditManager = creditManager;
        this.professionManager = professionManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("このコマンドはプレイヤーのみ実行できます。", NamedTextColor.RED));
            return true;
        }

        PlayerLevelData levelData = levelManager.get(player);
        if (levelData == null) {
            player.sendMessage(Component.text("データロード中です。しばらくお待ちください。", NamedTextColor.RED));
            return true;
        }

        Economy econ = Deepwither.getEconomy();
        StatMap finalStats = statManager.getTotalStats(player);

        player.sendMessage(Component.text("---------------------------------------", NamedTextColor.DARK_GRAY).decoration(TextDecoration.STRIKETHROUGH, true));
        player.sendMessage(Component.text("       【 プレイヤー ステータス 】", NamedTextColor.YELLOW, TextDecoration.BOLD));
        player.sendMessage(Component.empty());

        int currentLevel = levelData.getLevel();
        double currentExp = levelData.getExp();
        double expToNextLevel = levelData.getRequiredExp();
        double expPercent = (currentExp / expToNextLevel) * 100;

        player.sendMessage(Component.text(" [ 基本情報 ]", NamedTextColor.AQUA, TextDecoration.BOLD));
        player.sendMessage(Component.text("  Lv: ", NamedTextColor.GRAY).append(Component.text(currentLevel, NamedTextColor.GREEN))
                .append(Component.text(String.format(" (%.1f%%)", expPercent), NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("  所持金  : ", NamedTextColor.GRAY).append(Component.text(econ.format(econ.getBalance(player)), NamedTextColor.GOLD)));
        player.sendMessage(Component.empty());

        double currentHp = statManager.getActualCurrentHealth(player);
        double maxHp = statManager.getActualMaxHealth(player);

        player.sendMessage(Component.text(" [ 戦闘ステータス ]", NamedTextColor.RED, TextDecoration.BOLD));
        player.sendMessage(Component.text("  HP: ", NamedTextColor.GRAY)
                .append(Component.text(String.format("%.0f", currentHp), NamedTextColor.WHITE))
                .append(Component.text(" / ", NamedTextColor.GRAY))
                .append(Component.text(String.format("%.0f", maxHp), NamedTextColor.WHITE)));

        Component atk = getStatComponent("攻撃力", StatType.ATTACK_DAMAGE, finalStats, NamedTextColor.RED, false);
        Component def = getStatComponent("防御力", StatType.DEFENSE, finalStats, NamedTextColor.BLUE, false);
        player.sendMessage(Component.text("  ").append(atk).append(Component.text("   ")).append(def));

        Component matk = getStatComponent("魔攻力", StatType.MAGIC_DAMAGE, finalStats, NamedTextColor.LIGHT_PURPLE, false);
        Component mres = getStatComponent("魔耐性", StatType.MAGIC_RESIST, finalStats, NamedTextColor.DARK_AQUA, false);
        player.sendMessage(Component.text("  ").append(matk).append(Component.text("   ")).append(mres));

        Component maoeatk = getStatComponent("魔AoE", StatType.MAGIC_AOE_DAMAGE, finalStats, NamedTextColor.LIGHT_PURPLE, false);
        Component mburstres = getStatComponent("魔バースト", StatType.MAGIC_BURST_DAMAGE, finalStats, NamedTextColor.DARK_AQUA, false);
        player.sendMessage(Component.text("  ").append(maoeatk).append(Component.text("   ")).append(mburstres));

        Component critD = getStatComponent("クリ倍率", StatType.CRIT_DAMAGE, finalStats, NamedTextColor.GOLD, true);
        Component critC = getStatComponent("クリ率", StatType.CRIT_CHANCE, finalStats, NamedTextColor.GOLD, true);
        player.sendMessage(Component.text("  ").append(critD).append(Component.text("   ")).append(critC));

        player.sendMessage(Component.text("  ").append(getStatComponent("回復力", StatType.HP_REGEN, finalStats, NamedTextColor.GREEN, false)));
        player.sendMessage(Component.empty());

        player.sendMessage(Component.text(" [ 職業スキル ]", NamedTextColor.GREEN, TextDecoration.BOLD));
        PlayerProfessionData profData = professionManager.getData(player);

        for (ProfessionType type : ProfessionType.values()) {
            long totalExp = profData.getExp(type);
            int profLevel = professionManager.getLevel(totalExp);
            String typeName = formatProfessionName(type);
            player.sendMessage(Component.text("  " + typeName + ": ", NamedTextColor.GRAY)
                    .append(Component.text("Lv." + profLevel, NamedTextColor.GREEN))
                    .append(Component.text(" (Total: " + totalExp + " xp)", NamedTextColor.GRAY)));
        }
        player.sendMessage(Component.empty());

        player.sendMessage(Component.text(" [ 信用度 ]", NamedTextColor.YELLOW, TextDecoration.BOLD));
        TraderManager traderManager = Deepwither.getInstance().getTraderManager();
        Set<String> allTraderIds = traderManager.getAllTraderIds();

        if (allTraderIds.isEmpty()) {
            player.sendMessage(Component.text("  (データなし)", NamedTextColor.GRAY));
        } else {
            List<String> ids = new ArrayList<>(allTraderIds);
            for (int i = 0; i < ids.size(); i += 2) {
                String id1 = ids.get(i);
                String name1 = traderManager.getTraderName(id1);
                int credit1 = creditManager.getCredit(player.getUniqueId(), id1);
                Component entry1 = Component.text("  " + name1 + ": ", NamedTextColor.GRAY).append(Component.text(credit1, NamedTextColor.AQUA));

                if (i + 1 < ids.size()) {
                    String id2 = ids.get(i + 1);
                    String name2 = traderManager.getTraderName(id2);
                    int credit2 = creditManager.getCredit(player.getUniqueId(), id2);
                    Component entry2 = Component.text("  " + name2 + ": ", NamedTextColor.GRAY).append(Component.text(credit2, NamedTextColor.AQUA));
                    
                    player.sendMessage(entry1.append(Component.text("    ")).append(entry2));
                } else {
                    player.sendMessage(entry1);
                }
            }
        }

        player.sendMessage(Component.text("---------------------------------------", NamedTextColor.DARK_GRAY).decoration(TextDecoration.STRIKETHROUGH, true));
        return true;
    }

    private Component getStatComponent(String name, StatType type, StatMap stats, NamedTextColor color, boolean isPercent) {
        double value = stats.getFinal(type);
        String valStr = isPercent ? String.format("%.1f%%", value) : String.format("%.0f", value);
        return Component.text(name + ": ", NamedTextColor.GRAY).append(Component.text(valStr, color));
    }

    private String formatProfessionName(ProfessionType type) {
        return switch (type) {
            case MINING -> "採掘";
            case FISHING -> "釣り";
            default -> type.name();
        };
    }
}
