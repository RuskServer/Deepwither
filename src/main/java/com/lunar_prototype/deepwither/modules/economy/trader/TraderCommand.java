package com.lunar_prototype.deepwither.modules.economy.trader;

import com.lunar_prototype.deepwither.CreditManager;
import com.lunar_prototype.deepwither.Deepwither;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TraderCommand implements CommandExecutor {

    private final TraderManager traderManager;
    private final CreditManager creditManager; // CreditManagerも必要

    public TraderCommand(TraderManager traderManager) {
        this.traderManager = traderManager;
        // CreditManagerはMainクラスから取得することを想定
        this.creditManager = Deepwither.getInstance().getCreditManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // コマンドはコンソール、NPC、または管理者プレイヤーから実行される

        if (args.length < 2) {
            sender.sendMessage(Component.text("使用方法: /trader <トレーダーID> <ターゲットプレイヤー名>", NamedTextColor.YELLOW));
            return true;
        }

        String traderId = args[0];
        String targetName = args[1];

        Player targetPlayer = Bukkit.getPlayer(targetName);
        if (targetPlayer == null) {
            sender.sendMessage(Component.text("ターゲットプレイヤーが見つかりません: " + targetName, NamedTextColor.RED));
            return true;
        }

        // ターゲットプレイヤーがオフラインの場合はエラーメッセージを出力
        if (!targetPlayer.isOnline()) {
            sender.sendMessage(Component.text(targetName + "はオフラインです。", NamedTextColor.RED));
            return true;
        }

        // TraderManagerにトレーダーが存在するか確認（任意）
        if (!traderManager.traderExists(traderId)) {
            sender.sendMessage(Component.text("トレーダーIDが見つかりません: " + traderId, NamedTextColor.RED));
            return true;
        }

        // 信用度を取得
        int playerCredit = creditManager.getCredit(targetPlayer.getUniqueId(), traderId);

        // GUIを開く
        Deepwither.getInstance().getTraderGUI().openBuyGUI(targetPlayer,traderId,playerCredit,Deepwither.getInstance().getTraderManager());

        return true;
    }
}