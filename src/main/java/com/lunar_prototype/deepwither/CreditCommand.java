package com.lunar_prototype.deepwither;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CreditCommand implements CommandExecutor {

    private final CreditManager creditManager;

    public CreditCommand(CreditManager creditManager) {
        this.creditManager = creditManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("deepwither.credit.admin")) {
            sender.sendMessage(Component.text("権限がありません。", NamedTextColor.RED));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(Component.text("使用方法: /credit <プレイヤー名> <トレーダーID> <増減量>", NamedTextColor.YELLOW));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(Component.text("プレイヤーが見つかりません。", NamedTextColor.RED));
            return true;
        }

        String traderId = args[1];
        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("増減量は数値で指定してください。", NamedTextColor.RED));
            return true;
        }

        creditManager.addCredit(target.getUniqueId(), traderId, amount);

        int currentCredit = creditManager.getCredit(target.getUniqueId(), traderId);

        sender.sendMessage(Component.text(target.getName() + "のトレーダー[" + traderId + "]に対する信用度を " + amount + " 更新しました。", NamedTextColor.GREEN));
        sender.sendMessage(Component.text("現在の信用度: " + currentCredit, NamedTextColor.GREEN));

        if (target != sender) {
            target.sendMessage(Component.text("トレーダー[" + traderId + "]に対する信用度が " + amount + " 変化しました。現在: " + currentCredit, NamedTextColor.YELLOW));
        }

        return true;
    }
}