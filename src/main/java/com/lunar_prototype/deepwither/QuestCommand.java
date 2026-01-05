package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.aethelgard.GuildQuestManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class QuestCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final GuildQuestManager guildQuestManager;

    public QuestCommand(JavaPlugin plugin, GuildQuestManager guildQuestManager) {
        this.plugin = plugin;
        this.guildQuestManager = guildQuestManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "このコマンドはOPからのみ実行可能です。");
            return true;
        }

        if (args.length != 2) {
            sender.sendMessage(ChatColor.RED + "使用方法: /questnpc <CITY_ID> <プレイヤー名>");
            return false;
        }

        String cityId = args[0];
        String playerName = args[1];
        Player targetPlayer = Bukkit.getPlayer(playerName);

        if (targetPlayer == null || !targetPlayer.isOnline()) {
            sender.sendMessage(ChatColor.RED + "プレイヤー '" + playerName + "' がオンラインではありません。");
            return true;
        }

        if (guildQuestManager.getQuestLocation(cityId) == null) {
            sender.sendMessage(ChatColor.RED + "指定されたCITY_ID '" + cityId + "' は存在しません。");
            return true;
        }

        // GUIを作成し、ターゲットプレイヤーに表示
        QuestGUI questGUI = new QuestGUI(guildQuestManager, cityId);
        targetPlayer.openInventory(questGUI.getInventory());
        sender.sendMessage(ChatColor.GREEN + targetPlayer.getName() + " にギルドクエストGUIを表示しました。");

        return true;
    }
}