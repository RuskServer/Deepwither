package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.aethelgard.PlayerQuestManager;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

        if (!(holder instanceof com.lunar_prototype.deepwither.QuestGUI questGUI)) {
            return;
        }

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        ItemMeta meta = clickedItem.getItemMeta();

        if (meta.hasLore()) {
            List<Component> lore = meta.lore();
            if (lore == null) return;

            String questIdString = null;
            for (Component line : lore) {
                String plain = PlainTextComponentSerializer.plainText().serialize(line);
                if (plain.contains("QUEST_ID:")) {
                    questIdString = plain;
                    break;
                }
            }

            if (questIdString != null) {
                Matcher matcher = QUEST_ID_PATTERN.matcher(questIdString);
                if (matcher.find()) {
                    try {
                        UUID questId = UUID.fromString(matcher.group(1));
                        String locationId = questGUI.getQuestLocation().getLocationId();

                        boolean success = questManager.claimQuest(player, locationId, questId);
                        if (success) {
                            player.closeInventory();
                        }
                    } catch (IllegalArgumentException e) {
                        player.sendMessage(Component.text("エラー: 無効なクエストIDです。", NamedTextColor.DARK_RED));
                    }
                }
            }
        }
    }
}