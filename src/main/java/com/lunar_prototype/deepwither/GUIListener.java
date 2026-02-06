package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.aethelgard.PlayerQuestManager;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * QuestGUIのインベントリクリックイベントを処理します。
 */
@DependsOn({PlayerQuestManager.class})
public class GUIListener implements Listener, IManager {

    private final PlayerQuestManager questManager;
    private static final Pattern QUEST_ID_PATTERN = Pattern.compile("QUEST_ID:([a-fA-F0-9\\-]+)");

    public GUIListener(PlayerQuestManager questManager) {
        this.questManager = questManager;
    }

    @Override
    public void init() {
        Bukkit.getPluginManager().registerEvents(this, Deepwither.getInstance());
    }

    @Override
    public void shutdown() {}

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();

        // 1. QuestGUIであるかチェック
        if (!(holder instanceof com.lunar_prototype.deepwither.QuestGUI questGUI)) {
            return;
        }

        event.setCancelled(true); // GUI内のアイテム操作を無効化

        // 2. クリックされたアイテムを取得
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        ItemMeta meta = clickedItem.getItemMeta();

        // 3. アイテムからQuest IDを抽出
        if (meta.hasLore()) {
            String questIdString = meta.getLore().stream()
                    .filter(line -> line.contains("QUEST_ID:"))
                    .findFirst()
                    .orElse(null);

            if (questIdString != null) {
                Matcher matcher = QUEST_ID_PATTERN.matcher(ChatColor.stripColor(questIdString));
                if (matcher.find()) {
                    try {
                        UUID questId = UUID.fromString(matcher.group(1));
                        String locationId = questGUI.getQuestLocation().getLocationId();

                        // 4. 受注処理をPlayerQuestManagerに依頼
                        boolean success = questManager.claimQuest(player, locationId, questId);

                        if (success) {
                            // 受注成功時
                            player.closeInventory();
                        }
                    } catch (IllegalArgumentException e) {
                        player.sendMessage(ChatColor.DARK_RED + "エラー: 無効なクエストIDです。");
                    }
                }
            }
        }
    }
}
