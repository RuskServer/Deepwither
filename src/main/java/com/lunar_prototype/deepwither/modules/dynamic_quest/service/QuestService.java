package com.lunar_prototype.deepwither.modules.dynamic_quest.service;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.modules.dynamic_quest.enums.QuestType;
import com.lunar_prototype.deepwither.modules.dynamic_quest.npc.QuestNPC;
import com.lunar_prototype.deepwither.modules.dynamic_quest.obj.DynamicQuest;
import com.lunar_prototype.deepwither.modules.dynamic_quest.event.SupplyConvoyEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.UUID;

public class QuestService {

    private final Deepwither plugin;
    private final QuestNPCManager npcManager;

    public QuestService(Deepwither plugin, QuestNPCManager npcManager) {
        this.plugin = plugin;
        this.npcManager = npcManager;
    }

    public void acceptQuest(Player player, UUID questId) {
        Optional<QuestNPC> npcOpt = npcManager.getActiveNPCs().stream()
                .filter(n -> n.getQuest().getQuestId().equals(questId))
                .findFirst();

        if (npcOpt.isEmpty()) {
            player.sendMessage(Component.text("そのクエストはもう存在しません。", NamedTextColor.RED));
            return;
        }

        QuestNPC npc = npcOpt.get();
        DynamicQuest quest = npc.getQuest();
        
        if (quest.getStatus() != DynamicQuest.QuestStatus.CREATED) {
            player.sendMessage(Component.text("このクエストは既に受諾されています。", NamedTextColor.RED));
            return;
        }

        quest.setStatus(DynamicQuest.QuestStatus.ACTIVE);
        quest.setAssignee(player.getUniqueId());

        player.sendMessage(Component.text("クエストを受諾しました！", NamedTextColor.GREEN));

        if (quest.getType() == QuestType.RAID) {
            Location start = quest.getStartLocation();
            if (start == null) {
                start = player.getLocation().add((Math.random() * 20) - 10, 0, (Math.random() * 20) - 10);
                start.setY(start.getWorld().getHighestBlockYAt(start.getBlockX(), start.getBlockZ()) + 1);
            }
            new SupplyConvoyEvent(plugin, start, quest.getTargetLocation(), quest).start();
            player.sendMessage(Component.text(">> 補給部隊が移動を開始した！追跡して襲撃しろ！", NamedTextColor.RED));
        } else if (quest.getType() == QuestType.DELIVERY) {
            if (quest.getTargetItem() != null) {
                player.getInventory().addItem(quest.getTargetItem().clone());
                player.sendMessage(Component.text(">> 配送品を受け取りました。指定地点へ届けてください。", NamedTextColor.YELLOW));
            }
        } else {
            player.sendMessage(Component.text(">> クエストを開始しました。目標: " + quest.getObjectiveDescription(), NamedTextColor.YELLOW));
        }
    }

    public void reportQuest(Player player, UUID questId) {
        Optional<QuestNPC> npcOpt = npcManager.getActiveNPCs().stream()
                .filter(n -> n.getQuest().getQuestId().equals(questId))
                .findFirst();

        if (npcOpt.isEmpty()) return;

        QuestNPC npc = npcOpt.get();
        DynamicQuest quest = npc.getQuest();

        if (quest.getStatus() == DynamicQuest.QuestStatus.ACTIVE && player.getUniqueId().equals(quest.getAssignee())) {
            if (quest.getType() == QuestType.FETCH) {
                ItemStack target = quest.getTargetItem();
                int amount = quest.getTargetAmount();
                if (!player.getInventory().containsAtLeast(target, amount)) {
                    player.sendMessage(Component.text("必要なアイテム (" + target.getType().name() + " x" + amount + ") が足りません。", NamedTextColor.RED));
                    return;
                }
                ItemStack toRemove = target.clone();
                toRemove.setAmount(amount);
                player.getInventory().removeItem(toRemove);
                quest.setObjectiveMet(true);
            }

            if (quest.isObjectiveMet()) {
                quest.setStatus(DynamicQuest.QuestStatus.COMPLETED);
                Deepwither.getEconomy().depositPlayer(player, quest.getRewardAmount());
                player.sendMessage(Component.text("クエスト完了！報酬として " + quest.getRewardAmount() + " クレジットを獲得しました。", NamedTextColor.GREEN));
                npcManager.removeNPC(npc);
            } else {
                player.sendMessage(Component.text("まだ報告できる段階ではありません。", NamedTextColor.RED));
            }
        }
    }
}
