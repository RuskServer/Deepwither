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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@DependsOn({AttributeManager.class, SkilltreeManager.class})
public class ResetGUI implements Listener, IManager {

    private final Deepwither plugin;
    private final String GUI_TITLE = "§8§l【 魂の浄化祭壇 】"; // 教会風タイトル

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

        // 背景装飾
        ItemStack glass = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, glass);
        }

        // --- メニューアイテム ---

        // 1. 能力値(Attribute)リセット
        ItemStack attrReset = createItem(Material.NETHER_STAR, "§b§l能力値の再分配",
                "",
                "§7現在のステータス振りを全てリセットし、",
                "§7ポイントを未割り当て状態に戻します。",
                "",
                "§e▶ クリックして浄化する");
        inv.setItem(11, attrReset);

        // 2. スキルツリー(SkillTree)リセット
        ItemStack skillReset = createItem(Material.ENCHANTED_BOOK, "§a§lスキルの忘却",
                "",
                "§7習得したスキルを全て忘れ、",
                "§7スキルポイントを取り戻します。",
                "",
                "§e▶ クリックして忘却する");
        inv.setItem(15, skillReset);

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 0.8f);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!e.getView().getTitle().equals(GUI_TITLE)) return;
        e.setCancelled(true);

        if (!(e.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        // 誤操作防止のクールダウン等をここに入れるとより安全です

        if (clicked.getType() == Material.NETHER_STAR) {
            // 能力値リセット処理
            processAttributeReset(player);
            player.closeInventory();

        } else if (clicked.getType() == Material.ENCHANTED_BOOK) {
            // スキルリセット処理
            processSkillReset(player);
            player.closeInventory();
        }
    }

    // --- ロジック実装部 ---

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
            player.sendMessage("§cステータスデータが読み込まれていません。");
            return;
        }

        // 演出
        player.sendMessage("§b§l[浄化] §f能力値が初期化され、ポイントが返還されました。");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.5f);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1f);
    }

    private void processSkillReset(Player player) {
        int totalSkillPoints = Deepwither.getInstance().getSkilltreeManager().resetSkillTree(player.getUniqueId());
        // 演出
        player.sendMessage("§a§l[忘却] §f習得したスキルを全て忘れ、スキルポイント §e" + totalSkillPoints + " §fを取り戻しました。");
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_WORK_CLERIC, 1f, 1f);
        player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.5f, 2f);
    }

    // --- ヘルパー ---
    private ItemStack createItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        List<String> list = new ArrayList<>();
        for (String s : lore) list.add(s);
        meta.setLore(list);
        item.setItemMeta(meta);
        return item;
    }
}