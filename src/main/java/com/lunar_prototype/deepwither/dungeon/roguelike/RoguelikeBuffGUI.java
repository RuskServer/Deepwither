package com.lunar_prototype.deepwither.dungeon.roguelike;

import com.lunar_prototype.deepwither.Deepwither;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class RoguelikeBuffGUI implements Listener {

    private final Deepwither plugin;
    private static final String GUI_TITLE = "§8Choose a Buff";

    public RoguelikeBuffGUI(Deepwither plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, GUI_TITLE);

        // 背景
        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++)
            inv.setItem(i, glass);

        // ランダムに3つのバフを選出
        List<RoguelikeBuff> allBuffs = new ArrayList<>(Arrays.asList(RoguelikeBuff.values()));
        Collections.shuffle(allBuffs);
        List<RoguelikeBuff> choices = allBuffs.subList(0, Math.min(3, allBuffs.size()));

        // スロット位置: 11, 13, 15
        int[] slots = { 11, 13, 15 };

        for (int i = 0; i < choices.size(); i++) {
            RoguelikeBuff buff = choices.get(i);
            ItemStack item = createBuffItem(buff);
            inv.setItem(slots[i], item);
        }

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
    }

    private ItemStack createBuffItem(RoguelikeBuff buff) {
        ItemStack item = new ItemStack(buff.getIcon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(buff.getDisplayName());

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(buff.getDescription());
        lore.add("");
        lore.add("§eクリックして獲得！");
        meta.setLore(lore);

        // バフの種類を特定するためにNBTや隠しデータを仕込むのが定石だが、
        // 今回はシンプルにDisplayNameやLore、あるいはスロット位置とセッション管理で判断する。
        // ここではクリックイベントでItemMetaから逆引きするか、あるいは
        // Inventory自体にHolderを持たせるのが良いが、簡易実装として表示名の一致確認を行う。
        // (より堅牢にするならPersistentDataContainer推奨)

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createItem(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!e.getView().getTitle().equals(GUI_TITLE))
            return;
        e.setCancelled(true);

        if (!(e.getWhoClicked() instanceof Player player))
            return;
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta())
            return;

        // クリックされたアイテムからバフを特定
        RoguelikeBuff selectedBuff = null;
        for (RoguelikeBuff buff : RoguelikeBuff.values()) {
            if (clicked.getItemMeta().getDisplayName().equals(buff.getDisplayName())) {
                selectedBuff = buff;
                break;
            }
        }

        if (selectedBuff != null) {
            // バフ適用
            Deepwither.getInstance().getRoguelikeBuffManager().addBuff(player, selectedBuff);
            player.closeInventory();
        }
    }
}
