package com.lunar_prototype.deepwither.command;

import com.lunar_prototype.deepwither.Deepwither;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DeepwitherCommand implements CommandExecutor, TabCompleter {

    private final Deepwither plugin;

    public DeepwitherCommand(Deepwither plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (!sender.hasPermission("deepwither.admin")) {
            sender.sendMessage(Component.text("権限がありません。", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "dungeon" -> handleDungeon(sender, args);
            case "spawnmob" -> handleSpawnMob(sender, args);
            case "spawnportal" -> handleSpawnPortal(sender, args);
            case "reload" -> {
                sender.sendMessage(Component.text("Deepwitherの設定をリロードしました。", NamedTextColor.GREEN));
                Deepwither.getInstance().getSkilltreeGUI().reload();
            }
            case "debug" -> handleDebug(sender, args);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void handleDebug(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return;
        if (args.length < 2) {
            player.sendMessage(Component.text("使用法: /dw debug <hit>", NamedTextColor.RED));
            return;
        }

        String type = args[1].toLowerCase();
        if (type.equals("hit")) {
            com.lunar_prototype.deepwither.modules.combat.HitDetectionManager hitManager = 
                    Deepwither.getInstance().get(com.lunar_prototype.deepwither.modules.combat.HitDetectionManager.class);
            if (hitManager != null) {
                hitManager.toggleDebug(player);
            }
        }
    }

    private void handleSpawnMob(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("プレイヤーのみ実行可能です。");
            return;
        }

        if (args.length < 2) {
            player.sendMessage(Component.text("使用法: /dw spawnmob <mob_id> [tier]", NamedTextColor.RED));
            return;
        }

        String mobId = args[1];
        int tier = 1;
        if (args.length >= 3) {
            try {
                tier = Integer.parseInt(args[2]);
            } catch (NumberFormatException ignored) {}
        }

        com.lunar_prototype.deepwither.modules.mob.service.MobSpawnerService spawnerService = plugin.get(com.lunar_prototype.deepwither.modules.mob.service.MobSpawnerService.class);
        if (spawnerService != null) {
            java.util.UUID uuid = spawnerService.spawnMythicMob(mobId, player.getLocation(), tier);
            if (uuid != null) {
                player.sendMessage(Component.text("モブ " + mobId + " (Tier: " + tier + ") をスポーンさせました！", NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text("モブのスポーンに失敗しました。IDを確認してください。", NamedTextColor.RED));
            }
        } else {
            player.sendMessage(Component.text("MobSpawnerService が利用できません。", NamedTextColor.RED));
        }
    }

    private void handleSpawnPortal(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return;

        int tier = 1;
        if (args.length >= 2) {
            try {
                tier = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {}
        }

        // プレイヤーの目の前にポータル(エンドゲートウェイ)を生成
        org.bukkit.Location loc = player.getLocation().add(player.getLocation().getDirection().multiply(2));
        loc.setY(Math.floor(loc.getY()) + 1); // 足元より少し上に
        loc.getBlock().setType(org.bukkit.Material.END_GATEWAY);
        
        player.getWorld().playSound(loc, org.bukkit.Sound.BLOCK_END_PORTAL_SPAWN, 1.0f, 1.0f);
        player.sendMessage(Component.text("[Debug] ネガティブレイヤーポータル(Tier " + tier + ")を生成しました！", NamedTextColor.LIGHT_PURPLE));

        com.lunar_prototype.deepwither.modules.minerun.MineRunManager runManager = 
                Deepwither.getInstance().get(com.lunar_prototype.deepwither.modules.minerun.MineRunManager.class);
        if (runManager != null) {
            runManager.registerPortal(loc, tier);
            
            // 2分後に消去
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (loc.getBlock().getType() == org.bukkit.Material.END_GATEWAY) {
                    loc.getBlock().setType(org.bukkit.Material.AIR);
                }
                runManager.unregisterPortal(loc);
            }, 2400L);
        }
    }

    private void handleDungeon(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return;
        if (args.length < 2) {
            sendDungeonHelp(player);
            return;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "enter" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text("使用法: /dw dungeon enter <ダンジョンタイプ> [難易度]", NamedTextColor.RED));
                    return;
                }
                String dungeonType = args[2];
                String difficulty = (args.length >= 4) ? args[3] : "normal";
                Deepwither.getInstance().getPvPvEDungeonManager().enterPvPvEDungeon(player, dungeonType, difficulty);
            }
            case "generate" -> {
                if (!player.hasPermission("deepwither.admin")) {
                    player.sendMessage(Component.text("権限がありません。", NamedTextColor.RED));
                    return;
                }
                if (args.length < 3) {
                    player.sendMessage(Component.text("使用法: /dw dungeon generate <ダンジョンタイプ>", NamedTextColor.RED));
                    return;
                }
                String dungeonType = args[2];
                com.lunar_prototype.deepwither.dungeon.instance.DungeonInstanceManager.getInstance()
                        .createDungeonInstance(player, dungeonType,"normal");
            }
            case "join" -> {
                if (!player.hasPermission("deepwither.admin")) {
                    player.sendMessage(Component.text("権限がありません。", NamedTextColor.RED));
                    return;
                }
                if (args.length < 3) {
                    player.sendMessage(Component.text("使用法: /dw dungeon join <インスタンスID>", NamedTextColor.RED));
                    return;
                }
                String instanceId = args[2];
                com.lunar_prototype.deepwither.dungeon.instance.DungeonInstanceManager.getInstance()
                        .joinDungeon(player, instanceId);
            }
            case "leave" -> {
                com.lunar_prototype.deepwither.dungeon.instance.DungeonInstanceManager.getInstance()
                        .leaveDungeon(player);
            }
            default -> sendDungeonHelp(player);
        }
    }

    private void sendDungeonHelp(Player player) {
        player.sendMessage(Component.text("[Dungeon Help]", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/dw dungeon enter <type> [diff]", NamedTextColor.AQUA).append(Component.text(" - PvPvEダンジョンに参戦", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/dw dungeon generate <type>", NamedTextColor.WHITE).append(Component.text(" - 新規インスタンス生成", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/dw dungeon leave", NamedTextColor.WHITE).append(Component.text(" - ダンジョンから退出", NamedTextColor.GRAY)));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("[Deepwither Admin Help]", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        sender.sendMessage(Component.text("/dw dungeon ...", NamedTextColor.WHITE).append(Component.text(" - ダンジョン管理コマンド", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/dw spawnmob <id> [tier]", NamedTextColor.WHITE).append(Component.text(" - 指定したモブをスポーン", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/dw spawnportal [tier]", NamedTextColor.LIGHT_PURPLE).append(Component.text(" - MineRunのポータルを目の前に出現", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/dw reload", NamedTextColor.WHITE).append(Component.text(" - 設定リロード", NamedTextColor.GRAY)));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
            @NotNull String[] args) {
        if (args.length == 1) return Arrays.asList("dungeon", "spawnmob", "spawnportal", "sim", "reload");
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("dungeon")) return Arrays.asList("generate", "join", "leave", "enter");
            if (args[0].equalsIgnoreCase("spawnmob")) return Arrays.asList("melee_skeleton", "melee_zombie2", "FireDemon", "IcePilgrim");
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("generate")) {
            return Arrays.asList("silent_terrarium_ruins", "ancient_city");
        }
        return new ArrayList<>();
    }
}
