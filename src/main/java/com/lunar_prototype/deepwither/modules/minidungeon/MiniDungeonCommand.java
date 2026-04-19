package com.lunar_prototype.deepwither.modules.minidungeon;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.api.DW;
import com.lunar_prototype.deepwither.loot.LootChestManager;
import com.lunar_prototype.deepwither.modules.mob.framework.CustomMobManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class MiniDungeonCommand implements CommandExecutor, TabCompleter {

    private final Deepwither plugin;
    private final MiniDungeonManager manager;

    public MiniDungeonCommand(Deepwither plugin, MiniDungeonManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("deepwither.admin")) {
            sender.sendMessage(Component.text("権限がありません。", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(Component.text("使用方法: /minidungeon <create|setholo|addspawn|setchest|addmob|setkillcount|setloot|reload>", NamedTextColor.RED));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            manager.shutdown();
            manager.init();
            sender.sendMessage(Component.text("MiniDungeonManager をリロードしました。", NamedTextColor.GREEN));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("ダンジョンIDを指定してください。", NamedTextColor.RED));
            return true;
        }

        String action = args[0].toLowerCase();
        String id = args[1];

        if (action.equals("create")) {
            if (manager.getDungeon(id) != null) {
                sender.sendMessage(Component.text("ダンジョン " + id + " は既に存在します。", NamedTextColor.RED));
                return true;
            }
            manager.createDungeon(id);
            sender.sendMessage(Component.text("ダンジョン " + id + " を作成しました。", NamedTextColor.GREEN));
            return true;
        }

        MiniDungeon dungeon = manager.getDungeon(id);
        if (dungeon == null) {
            sender.sendMessage(Component.text("ダンジョン " + id + " が見つかりません。", NamedTextColor.RED));
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("プレイヤーからのみ実行可能です。", NamedTextColor.RED));
            return true;
        }
        Player player = (Player) sender;

        switch (action) {
            case "setholo":
                dungeon.setHologramLocation(player.getLocation());
                sender.sendMessage(Component.text("入口ホログラムの位置を設定しました。", NamedTextColor.GREEN));
                break;
            case "addspawn":
                dungeon.addSpawnLocation(player.getLocation());
                sender.sendMessage(Component.text("スポーン位置を追加しました。 (現在: " + dungeon.getSpawnLocations().size() + "箇所)", NamedTextColor.GREEN));
                break;
            case "setchest":
                dungeon.setChestLocation(player.getLocation());
                sender.sendMessage(Component.text("チェスト出現位置を設定しました。", NamedTextColor.GREEN));
                break;
            case "addmob":
                if (args.length < 3) {
                    sender.sendMessage(Component.text("モブIDを指定してください。", NamedTextColor.RED));
                    return true;
                }
                String mobId = args[2];
                CustomMobManager mobManager = DW.get(CustomMobManager.class);
                if (mobManager != null && !mobManager.hasRegistration(mobId)) {
                    sender.sendMessage(Component.text("警告: " + mobId + " は CustomMobManager に登録されていませんが、リストに追加しました。", NamedTextColor.YELLOW));
                }
                dungeon.addMobToSpawn(mobId);
                sender.sendMessage(Component.text("モブ " + mobId + " を追加しました。 (現在: " + dungeon.getMobsToSpawn().size() + "体)", NamedTextColor.GREEN));
                break;
            case "setkillcount":
                if (args.length < 3) {
                    sender.sendMessage(Component.text("キル数を指定してください。", NamedTextColor.RED));
                    return true;
                }
                try {
                    int count = Integer.parseInt(args[2]);
                    dungeon.setTotalKillsRequired(count);
                    sender.sendMessage(Component.text("必要キル数を " + count + " に設定しました。", NamedTextColor.GREEN));
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("有効な数字を指定してください。", NamedTextColor.RED));
                }
                break;
            case "setloot":
                if (args.length < 3) {
                    sender.sendMessage(Component.text("LootTemplate名を指定してください。", NamedTextColor.RED));
                    return true;
                }
                String template = args[2];
                dungeon.setLootTemplate(template);
                sender.sendMessage(Component.text("LootTemplate を " + template + " に設定しました。", NamedTextColor.GREEN));
                break;
            default:
                sender.sendMessage(Component.text("不明なコマンドです。", NamedTextColor.RED));
        }

        manager.save();
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("create", "setholo", "addspawn", "setchest", "addmob", "setkillcount", "setloot", "reload")
                    .stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && !args[0].equalsIgnoreCase("create") && !args[0].equalsIgnoreCase("reload")) {
            return manager.getAllDungeons().stream()
                    .map(MiniDungeon::getId)
                    .filter(s -> s.startsWith(args[1]))
                    .collect(Collectors.toList());
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("setloot")) {
                LootChestManager lootManager = DW.get(LootChestManager.class);
                if (lootManager != null) {
                    return lootManager.getTemplates().keySet().stream()
                            .filter(s -> s.startsWith(args[2]))
                            .collect(Collectors.toList());
                }
            } else if (args[0].equalsIgnoreCase("addmob")) {
                // Return clear instructions if we can't fetch a registry key set
                return Arrays.asList("<mob_id>");
            }
        }

        return new ArrayList<>();
    }
}
