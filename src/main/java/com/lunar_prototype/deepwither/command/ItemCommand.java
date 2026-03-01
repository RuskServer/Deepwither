package com.lunar_prototype.deepwither.command;

import com.lunar_prototype.deepwither.*;
import com.lunar_prototype.deepwither.PlayerAttributeData;
import com.lunar_prototype.deepwither.SkillData;
import com.lunar_prototype.deepwither.aethelgard.GeneratedQuest;
import com.lunar_prototype.deepwither.aethelgard.QuestGenerator;
import com.lunar_prototype.deepwither.outpost.OutpostManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.logging.Level;

public class ItemCommand implements CommandExecutor, TabCompleter {

    private final Deepwither plugin;
    private final ItemFactory itemFactory;

    public ItemCommand(Deepwither plugin) {
        this.plugin = plugin;
        this.itemFactory = plugin.get(ItemFactory.class);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        Player player = (sender instanceof Player) ? (Player) sender : null;

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            itemFactory.init(); // ItemFactory has loadAllItems called in init()
            sender.sendMessage(Component.text("アイテム設定をリロードしました。", NamedTextColor.GREEN));
            return true;
        }

        if (player == null) {
            if (args.length == 2) {
                String id = args[0];
                String targetName = args[1];
                Player targetPlayer = Bukkit.getPlayer(targetName);
                if (targetPlayer == null) {
                    sender.sendMessage(Component.text("プレイヤー ", NamedTextColor.RED)
                            .append(Component.text(targetName, NamedTextColor.YELLOW))
                            .append(Component.text(" は見つかりませんでした。", NamedTextColor.RED)));
                    return true;
                }
                File itemFolder = new File(plugin.getDataFolder(), "items");
                ItemStack item = itemFactory.getItem(id);
                if (item == null) {
                    sender.sendMessage(Component.text("そのIDのアイテムは存在しません。", NamedTextColor.RED));
                    return true;
                }
                targetPlayer.getInventory().addItem(item);
                sender.sendMessage(Component.text("アイテム ", NamedTextColor.GREEN)
                        .append(Component.text(id, NamedTextColor.YELLOW))
                        .append(Component.text(" をプレイヤー ", NamedTextColor.GREEN))
                        .append(Component.text(targetPlayer.getName(), NamedTextColor.YELLOW))
                        .append(Component.text(" に付与しました。", NamedTextColor.GREEN)));
                targetPlayer.sendMessage(Component.text("アイテム ", NamedTextColor.GREEN)
                        .append(Component.text(id, NamedTextColor.YELLOW))
                        .append(Component.text(" を付与されました。", NamedTextColor.GREEN)));
                return true;
            }
            sender.sendMessage(Component.text("使い方: <id> <プレイヤー名> | reload", NamedTextColor.RED));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("setwarp")) {
            if (args.length < 2) {
                sender.sendMessage(Component.text("Usage: /dw setwarp <warp_id>", NamedTextColor.RED));
                return true;
            }
            String id = args[1];
            plugin.getLayerMoveManager().setWarpLocation(id, player.getLocation());
            sender.sendMessage(Component.text("Warp地点(" + id + ")を現在位置に設定しました。", NamedTextColor.GREEN));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("genquest")) {
            Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
                try {
                    GeneratedQuest quest = new QuestGenerator().generateQuest(5);
                    Bukkit.getScheduler().runTask(this.plugin, () -> {
                        player.sendMessage(Component.text("---", NamedTextColor.GOLD).append(Component.text("冒険者ギルドからの緊急依頼", NamedTextColor.GOLD)));
                        player.sendMessage(Component.text("タイトル：「", NamedTextColor.WHITE).append(Component.text(quest.getTitle(), NamedTextColor.AQUA)).append(Component.text("」", NamedTextColor.WHITE)));
                        player.sendMessage(Component.text("[場所] ", NamedTextColor.YELLOW).append(Component.text(quest.getLocationDetails().getLlmLocationText(), NamedTextColor.WHITE)));
                        player.sendMessage(Component.text("[目標] ", NamedTextColor.YELLOW).append(Component.text(quest.getTargetMobId() + "を" + quest.getRequiredQuantity() + "体", NamedTextColor.WHITE)));
                        player.sendMessage(Component.empty());
                        for (String line : quest.getQuestText().split(" ")) {
                            player.sendMessage(Component.text(line, NamedTextColor.GRAY));
                        }
                        player.sendMessage(Component.empty());
                        player.sendMessage(Component.text("[報酬] ", NamedTextColor.GREEN).append(Component.text("200 ゴールド、経験値 500、小さな回復薬 x1", NamedTextColor.WHITE)));
                        player.sendMessage(Component.text("-------------------------------------", NamedTextColor.GOLD));
                    });
                } catch (Exception e) {
                    Bukkit.getScheduler().runTask(this.plugin, () -> {
                        player.sendMessage(Component.text("[ギルド受付] ", NamedTextColor.RED).append(Component.text("依頼の生成中にエラーが発生しました。時間を置いて再度お試しください。", NamedTextColor.WHITE)));
                        this.plugin.getLogger().log(Level.SEVERE, "LLMクエスト生成中にエラー:", e);
                    });
                }
            });
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("resetpoints")) {
            UUID uuid = player.getUniqueId();
            AttributeManager attrManager = plugin.getAttributeManager();
            PlayerAttributeData data = attrManager.get(uuid);
            if (data != null) {
                int totalAllocated = 0;
                for (StatType type : StatType.values()) {
                    totalAllocated += data.getAllocated(type);
                    data.setAllocated(type, 0);
                }
                data.addPoints(totalAllocated);
                player.sendMessage(Component.text("すべてのステータスポイントをリセットしました。", NamedTextColor.GOLD));
            } else {
                player.sendMessage(Component.text("ステータスデータが読み込まれていません。", NamedTextColor.RED));
            }
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reset")) {
            plugin.getLevelManager().resetLevel(player);
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("skilltreeresets")) {
            UUID uuid = player.getUniqueId();
            plugin.getSkilltreeManager().resetSkillTree(uuid);
            player.sendMessage(Component.text("すべてのステータスポイントをリセットしました。", NamedTextColor.GOLD));
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("addpoints")) {
            try {
                int amount = Integer.parseInt(args[1]);
                UUID uuid = player.getUniqueId();
                AttributeManager attrManager = plugin.getAttributeManager();
                PlayerAttributeData data = attrManager.get(uuid);
                if (data != null) {
                    data.addPoints(amount);
                    player.sendMessage(Component.text("ステータスポイントを ", NamedTextColor.GREEN).append(Component.text(amount, NamedTextColor.YELLOW)).append(Component.text(" 付与しました。", NamedTextColor.GREEN)));
                } else {
                    player.sendMessage(Component.text("ステータスデータが読み込まれていません。", NamedTextColor.RED));
                }
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text("数値を入力してください。", NamedTextColor.RED));
            }
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("spawnoutpost")) {
            OutpostManager.getInstance().startRandomOutpost();
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("addskillpoints")) {
            try {
                int amount = Integer.parseInt(args[1]);
                UUID uuid = player.getUniqueId();
                SkillData data = plugin.getSkilltreeManager().load(uuid);
                if (data != null) {
                    data.setSkillPoint(data.getSkillPoint() + amount);
                    plugin.getSkilltreeManager().save(uuid, data);
                    player.sendMessage(Component.text("スキルポイントを ", NamedTextColor.GREEN).append(Component.text(amount, NamedTextColor.YELLOW)).append(Component.text(" 付与しました。", NamedTextColor.GREEN)));
                } else {
                    player.sendMessage(Component.text("スキルツリーデータが読み込まれていません。", NamedTextColor.RED));
                }
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text("数値を入力してください。", NamedTextColor.RED));
            }
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(Component.text("使い方: /giveitem <id> [プレイヤー名] [グレード(1-5)]", NamedTextColor.RED));
            return true;
        }

        String id = args[0];
        Player targetPlayer = player;
        FabricationGrade grade = FabricationGrade.STANDARD;

        if (args.length >= 2) {
            Player found = Bukkit.getPlayer(args[1]);
            if (found != null) {
                targetPlayer = found;
            } else {
                sender.sendMessage(Component.text("プレイヤー ", NamedTextColor.RED).append(Component.text(args[1], NamedTextColor.YELLOW)).append(Component.text(" は見つかりませんでした。", NamedTextColor.RED)));
                return true;
            }
        }

        if (targetPlayer == null) {
            sender.sendMessage(Component.text("コンソールから実行する場合はプレイヤー名を指定してください。", NamedTextColor.RED));
            return true;
        }

        if (args.length >= 3) {
            try {
                grade = FabricationGrade.fromId(Integer.parseInt(args[2]));
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("グレードは数値(1-5)で指定してください。", NamedTextColor.RED));
                return true;
            }
        }

        ItemStack item = itemFactory.getItem(id, grade);

        if (item == null) {
            sender.sendMessage(Component.text("そのIDのアイテムは存在しません。", NamedTextColor.RED));
            return true;
        }

        targetPlayer.getInventory().addItem(item);

        Component successMsg = Component.text("アイテム ", NamedTextColor.GREEN)
                .append(Component.text(id, NamedTextColor.YELLOW))
                .append(Component.text(" "))
                .append(Component.text(grade.getDisplayName()))
                .append(Component.text(" を ", NamedTextColor.GREEN))
                .append(Component.text(targetPlayer.getName(), NamedTextColor.YELLOW))
                .append(Component.text(" に付与しました。", NamedTextColor.GREEN));
        sender.sendMessage(successMsg);
        if (!sender.equals(targetPlayer)) {
            targetPlayer.sendMessage(Component.text("アイテム ", NamedTextColor.GREEN)
                    .append(Component.text(id, NamedTextColor.YELLOW))
                    .append(Component.text(" "))
                    .append(Component.text(grade.getDisplayName()))
                    .append(Component.text(" を付与されました。", NamedTextColor.GREEN)));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> candidates = new ArrayList<>(itemFactory.getAllItemIds());
            candidates.add("reload");
            candidates.add("resetpoints");
            candidates.add("addpoints");
            candidates.add("addskillpoints");
            candidates.add("skilltreeresets");
            return candidates.stream().filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("addpoints")) return List.of("10", "25", "50");
        if (args.length == 2 && args[0].equalsIgnoreCase("addskillpoints")) return List.of("5", "10", "20");
        return Collections.emptyList();
    }
}
