package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

@DependsOn({AttributeManager.class})
public class AttributeGui implements Listener, IManager {

    private final JavaPlugin plugin;

    public AttributeGui(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

    public static void open(Player player) {
        PlayerAttributeData data = Deepwither.getInstance().getAttributeManager().get(player.getUniqueId());
        if (data == null) return;

        Inventory gui = Bukkit.createInventory(null, 27, "§aステータス割り振り");

        StatType[] guiStats = {
                StatType.STR, StatType.VIT, StatType.MND, StatType.INT, StatType.AGI
        };

        for (StatType type : guiStats) {
            ItemStack icon = getStatIcon(type,data,player);
            int slot = switch (type) {
                case STR -> 9;
                case VIT -> 11;
                case MND -> 13;
                case INT -> 15;
                case AGI -> 17;
                default -> throw new IllegalStateException("未対応のStatType: " + type);
            };
            gui.setItem(slot, icon);
        }

        player.openInventory(gui);
    }

    private static ItemStack getStatIcon(StatType type, PlayerAttributeData data,Player player) {
        Material mat = switch (type) {
            case STR -> Material.IRON_SWORD;
            case VIT -> Material.GOLDEN_APPLE;
            case MND -> Material.POTION;
            case INT -> Material.BOOK;
            case AGI -> Material.LEATHER_BOOTS;
            default -> Material.BARRIER; // 目立つアイテムを表示して開発者に気づかせる
        };

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§a" + type.getDisplayName());

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7ポイント: §6" + data.getAllocated(type) + " §7/ §6" + Deepwither.getInstance().getAttributeManager().getMaxAllocatable(player.getUniqueId(),type));
        lore.add("§7現在の" + type.getDisplayName() + "レベル: §6§l" + data.getAllocated(type));
        lore.add("");
        lore.add("§8効果:");
        for (String buff : StatEffectText.getBuffDescription(type, data.getAllocated(type))) {
            lore.add("§7✤ §9" + buff);
        }
        lore.add("");
        lore.add("§e右クリックで1ポイント消費してレベルアップする。");
        lore.add("§e◆ 現在所持: §b" + data.getRemainingPoints() + " ポイント");

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!e.getView().getTitle().equals("§aステータス割り振り")) return;

        e.setCancelled(true);

        int slot = e.getRawSlot();
        StatType type = switch (slot) {
            case 9 -> StatType.STR;
            case 11 -> StatType.VIT;
            case 13 -> StatType.MND;
            case 15 -> StatType.INT;
            case 17 -> StatType.AGI;
            default -> null;
        };

        if (type == null) return;

        PlayerAttributeData data = Deepwither.getInstance().getAttributeManager().get(player.getUniqueId());
        if (data == null || data.getRemainingPoints() <= 0) {
            player.sendMessage("§cポイントが足りません！");
            return;
        }

        if (data.getAllocated(type) >= Deepwither.getInstance().getAttributeManager().getMaxAllocatable(player.getUniqueId(),type)){
            player.sendMessage("§c上限に達しています！");
            return;
        }

        data.addPoint(type);
        player.sendMessage("§a" + type.getDisplayName() + " に 1ポイント割り振りました！");
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
        AttributeGui.open(player); // 更新
    }
}

class StatEffectText {
    public static List<String> getBuffDescription(StatType type, int level) {
        List<String> list = new ArrayList<>();
        switch (type) {
            case STR -> list.add("+ " + (level * 1) + "% 攻撃力");
            case VIT -> {
                list.add("+ " + (level * 1) + "% 最大HP");
                list.add("+ " + (level * 0.5) + "% 防御力");
            }
            case MND -> {
                list.add("+ " + (level * 1.5) + "% クリティカルダメージ");
                list.add("+ " + (level * 1.5) + "% 発射体ダメージ");
            }
            case INT -> {
                list.add("+ " + (level * 0.1) + "% CD短縮");
                list.add("+ " + (level * 2) + "% 最大マナ");
            }
            case AGI -> {
                list.add("+ " + (level * 0.2) + "% 会心率");
                list.add("+ " + (level * 0.25) + "% 移動速度");
            }
        }
        return list;
    }
}


