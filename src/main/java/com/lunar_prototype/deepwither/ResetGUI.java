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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@DependsOn({AttributeManager.class, SkilltreeManager.class})
public class ResetGUI implements Listener, IManager {

    private final Deepwither plugin;
    public static final Component GUI_TITLE = Component.text("【 魂の浄化祭壇 】", NamedTextColor.DARK_GRAY, TextDecoration.BOLD);

    public ResetGUI(Deepwither plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, GUI_TITLE);

        ItemStack glass = createItem(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "));
        for (int i = 0; i < 27; i++) inv.setItem(i, glass);

        ItemStack attrReset = createItem(Material.NETHER_STAR, Component.text("能力値の再分配", NamedTextColor.AQUA, TextDecoration.BOLD),
                Component.empty(),
                Component.text("現在のステータス振りを全てリセットし、", NamedTextColor.GRAY),
                Component.text("ポイントを未割り当て状態に戻します。", NamedTextColor.GRAY),
                Component.empty(),
                Component.text("▶ クリックして浄化する", NamedTextColor.YELLOW));
        inv.setItem(11, attrReset);

        ItemStack skillReset = createItem(Material.ENCHANTED_BOOK, Component.text("スキルの忘却", NamedTextColor.GREEN, TextDecoration.BOLD),
                Component.empty(),
                Component.text("習得したスキルを全て忘れ、", NamedTextColor.GRAY),
                Component.text("スキルポイントを取り戻します。", NamedTextColor.GRAY),
                Component.empty(),
                Component.text("▶ クリックして忘却する", NamedTextColor.YELLOW));
        inv.setItem(15, skillReset);

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 0.8f);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!e.getView().title().equals(GUI_TITLE)) return;
        e.setCancelled(true);

        if (!(e.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        if (clicked.getType() == Material.NETHER_STAR) {
            processAttributeReset(player);
            player.closeInventory();
        } else if (clicked.getType() == Material.ENCHANTED_BOOK) {
            processSkillReset(player);
            player.closeInventory();
        }
    }

    private void processAttributeReset(Player player) {
        UUID uuid = player.getUniqueId();
        AttributeManager attrManager = Deepwither.getInstance().getAttributeManager();
        PlayerAttributeData data = attrManager.get(uuid);
        if (data != null) {
            int totalAllocated = 0;
            for (StatType type : StatType.values()) {
                totalAllocated += data.getAllocated(type);
                data.setAllocated(type, 0);
            }
            data.addPoints(totalAllocated);
        } else {
            player.sendMessage(Component.text("ステータスデータが読み込まれていません。", NamedTextColor.RED));
            return;
        }

        player.sendMessage(Component.text("[浄化] ", NamedTextColor.AQUA, TextDecoration.BOLD).append(Component.text("能力値が初期化され、ポイントが返還されました。", NamedTextColor.WHITE)));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.5f);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1f);
    }

    private void processSkillReset(Player player) {
        int totalSkillPoints = Deepwither.getInstance().getSkilltreeManager().resetSkillTree(player.getUniqueId());
        player.sendMessage(Component.text("[忘却] ", NamedTextColor.GREEN, TextDecoration.BOLD).append(Component.text("習得したスキルを全て忘れ、スキルポイント ", NamedTextColor.WHITE)).append(Component.text(totalSkillPoints, NamedTextColor.YELLOW)).append(Component.text(" を取り戻しました。", NamedTextColor.WHITE)));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_WORK_CLERIC, 1f, 1f);
        player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.5f, 2f);
    }

    private ItemStack createItem(Material mat, Component name, Component... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        List<Component> nonItalicLore = new ArrayList<>();
        for (Component l : lore) {
            nonItalicLore.add(l.decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(nonItalicLore);
        item.setItemMeta(meta);
        return item;
    }
}
