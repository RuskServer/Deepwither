package com.lunar_prototype.deepwither.mail;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@DependsOn({MailManager.class})
public class MailInboxGUI implements Listener, IManager {

    private static final Component GUI_TITLE = Component.text("メールボックス", NamedTextColor.DARK_AQUA).decoration(TextDecoration.ITALIC, false);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
    private static final int GUI_SIZE = 54;
    private static final int CLOSE_SLOT = 49;
    private final NamespacedKey mailIdKey;
    private final Deepwither plugin;
    private MailManager mailManager;

    public MailInboxGUI(Deepwither plugin) {
        this.plugin = plugin;
        this.mailIdKey = new NamespacedKey(plugin, "mail_id");
    }

    @Override
    public void init() {
        this.mailManager = plugin.getMailManager();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE, GUI_TITLE);
        fillBackground(inv);

        List<MailMessage> mails = mailManager.getInbox(player.getUniqueId());
        List<Integer> slots = getMailSlots();
        int displayCount = Math.min(mails.size(), slots.size());

        for (int i = 0; i < displayCount; i++) {
            inv.setItem(slots.get(i), createMailItem(mails.get(i)));
        }

        inv.setItem(4, createInfoItem(mails.size(), Math.max(0, mails.size() - displayCount)));
        inv.setItem(CLOSE_SLOT, createCloseButton());

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!e.getView().title().equals(GUI_TITLE)) {
            return;
        }
        e.setCancelled(true);

        if (!(e.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (e.getRawSlot() == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }

        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) {
            return;
        }

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR || !clicked.hasItemMeta()) {
            return;
        }

        ItemMeta meta = clicked.getItemMeta();
        String mailId = meta.getPersistentDataContainer().get(mailIdKey, PersistentDataType.STRING);
        if (mailId == null) {
            return;
        }

        boolean opened = mailManager.openMail(player, UUID.fromString(mailId));
        if (opened) {
            player.closeInventory();
        } else {
            open(player);
        }
    }

    private ItemStack createMailItem(MailMessage mail) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(mail.getTitle(), NamedTextColor.YELLOW, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(mailIdKey, PersistentDataType.STRING, mail.getId().toString());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());

        if (!mail.getBodyLines().isEmpty()) {
            lore.add(Component.text("本文: ", NamedTextColor.GRAY)
                    .append(Component.text(preview(mail.getBodyLines().get(0)), NamedTextColor.WHITE))
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("本文: なし", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }

        lore.add(Component.text("報酬: ", NamedTextColor.GOLD)
                .append(Component.text(mail.getRewardItems().size() + " 件", NamedTextColor.YELLOW))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("配達日時: ", NamedTextColor.GRAY)
                .append(Component.text(TIME_FORMAT.format(Instant.ofEpochMilli(mail.getCreatedAt()).atZone(ZoneId.systemDefault())), NamedTextColor.WHITE))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("クリックで開封して受け取る", NamedTextColor.GREEN, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createInfoItem(int mailCount, int hiddenCount) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("[ 受信箱 ]", NamedTextColor.AQUA, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("受信数: ", NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(mailCount), NamedTextColor.GREEN))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("表示しきれない分: ", NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(hiddenCount), NamedTextColor.YELLOW))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("メールをクリックすると開封し、", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("報酬を受け取って削除します。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCloseButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("閉じる", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("メールボックスを閉じます。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    private void fillBackground(Inventory inv) {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        glass.setItemMeta(meta);

        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, glass);
        }
    }

    private List<Integer> getMailSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                slots.add(row * 9 + col);
            }
        }
        return slots;
    }

    private String preview(String text) {
        if (text == null || text.isBlank()) {
            return "なし";
        }
        if (text.length() <= 28) {
            return text;
        }
        return text.substring(0, 28) + "...";
    }
}
