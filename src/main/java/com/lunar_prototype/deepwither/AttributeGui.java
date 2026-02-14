package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
    public static final Component GUI_TITLE = Component.text("ステータス割り振り", NamedTextColor.DARK_GREEN);

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

        Inventory gui = Bukkit.createInventory(null, 27, GUI_TITLE);

        StatType[] guiStats = { StatType.STR, StatType.VIT, StatType.MND, StatType.INT, StatType.AGI };

        for (StatType type : guiStats) {
            ItemStack icon = getStatIcon(type, data, player);
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

    private static ItemStack getStatIcon(StatType type, PlayerAttributeData data, Player player) {
        Material mat = switch (type) {
            case STR -> Material.IRON_SWORD;
            case VIT -> Material.GOLDEN_APPLE;
            case MND -> Material.POTION;
            case INT -> Material.BOOK;
            case AGI -> Material.LEATHER_BOOTS;
            default -> Material.BARRIER;
        };

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(type.getDisplayName(), NamedTextColor.GREEN));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("ポイント: ", NamedTextColor.GRAY)
                .append(Component.text(data.getAllocated(type), NamedTextColor.GOLD))
                .append(Component.text(" / ", NamedTextColor.GRAY))
                .append(Component.text(Deepwither.getInstance().getAttributeManager().getMaxAllocatable(player.getUniqueId(), type), NamedTextColor.GOLD)));
        lore.add(Component.text("現在の" + type.getDisplayName() + "レベル: ", NamedTextColor.GRAY)
                .append(Component.text(data.getAllocated(type), NamedTextColor.GOLD, TextDecoration.BOLD)));
        lore.add(Component.empty());
        lore.add(Component.text("効果:", NamedTextColor.DARK_GRAY));
        for (String buff : StatEffectText.getBuffDescription(type, data.getAllocated(type))) {
            lore.add(Component.text("  ✤ ", NamedTextColor.GRAY).append(Component.text(buff, NamedTextColor.BLUE)));
        }
        lore.add(Component.empty());
        lore.add(Component.text("右クリックで1ポイント消費してレベルアップする。", NamedTextColor.YELLOW));
        lore.add(Component.text("◆ 現在所持: ", NamedTextColor.YELLOW).append(Component.text(data.getRemainingPoints() + " ポイント", NamedTextColor.AQUA)));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!e.getView().title().equals(GUI_TITLE)) return;

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
            player.sendMessage(Component.text("ポイントが足りません！", NamedTextColor.RED));
            return;
        }

        if (data.getAllocated(type) >= Deepwither.getInstance().getAttributeManager().getMaxAllocatable(player.getUniqueId(), type)) {
            player.sendMessage(Component.text("上限に達しています！", NamedTextColor.RED));
            return;
        }

        data.addPoint(type);
        player.sendMessage(Component.text(type.getDisplayName() + " に 1ポイント割り振りました！", NamedTextColor.GREEN));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
        open(player);
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