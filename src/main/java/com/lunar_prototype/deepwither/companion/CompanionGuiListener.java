package com.lunar_prototype.deepwither.companion;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

@DependsOn({CompanionManager.class})
public class CompanionGuiListener implements Listener, IManager {

    private final CompanionManager manager;
    private final CompanionGui gui;

    public CompanionGuiListener(CompanionManager manager) {
        this.manager = manager;
        this.gui = new CompanionGui(manager); // 簡易的にここで生成
    }

    @Override
    public void init() throws Exception {
        Bukkit.getPluginManager().registerEvents(this, Deepwither.getInstance());
    }

    // 外部からGUIを開く用
    public void open(Player p) {
        gui.openGui(p);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        String title = PlainTextComponentSerializer.plainText().serialize(e.getView().title());
        if (!title.equals(CompanionGui.GUI_TITLE)) return;

        e.setCancelled(true); // 基本キャンセル（背景などを触れないように）

        Player p = (Player) e.getWhoClicked();
        int slot = e.getRawSlot();
        Inventory inv = e.getInventory();

        // 自分のインベントリ(下側)の操作は許可するが、シフトクリックは制御が必要
        if (slot >= inv.getSize()) {
            e.setCancelled(false);
            // シフトクリックでコンパニオンアイテムスロットに入れようとした時のチェックなどは必要なら追加
            return;
        }

        // --- コンパニオンアイテムスロット (SLOT_ITEM) ---
        if (slot == CompanionGui.SLOT_ITEM) {
            // 召喚中はロック！！
            if (manager.isSpawned(p)) {
                p.sendMessage("§cコンパニオン召喚中は装備を変更できません。");
                p.playSound(p.getLocation(), Sound.BLOCK_CHEST_LOCKED, 1, 1);
                return;
            }

            // 召喚していないなら出し入れ自由
            e.setCancelled(false);
        }

        // --- アクションボタン (SLOT_ACTION) ---
        if (slot == CompanionGui.SLOT_ACTION) {
            ItemStack itemInSlot = inv.getItem(CompanionGui.SLOT_ITEM);

            if (manager.isSpawned(p)) {
                // 帰還処理
                manager.despawnCompanion(p);
                p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1, 0.5f);
                gui.updateActionButton(inv, p); // ボタン更新
            } else {
                // 召喚処理
                String companionId = manager.getCompanionIdFromItem(itemInSlot);

                if (companionId != null) {
                    // IDが有効かチェック
                    // (manager内に `isValidId(String id)` みたいなのがあると良いですが一旦try-catchやnullチェックで)
                    manager.spawnCompanion(p, companionId);
                    p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
                    gui.updateActionButton(inv, p); // ボタン更新
                } else {
                    p.sendMessage("§c有効なコンパニオンアイテムがセットされていません。");
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1, 1);
                }
            }
        }
    }

    // ドラッグ操作によるアイテム移動の防止
    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        String title = PlainTextComponentSerializer.plainText().serialize(e.getView().title());
        if (!title.equals(CompanionGui.GUI_TITLE)) return;

        Player p = (Player) e.getWhoClicked();

        // コンパニオンスロットが含まれている場合
        if (e.getRawSlots().contains(CompanionGui.SLOT_ITEM)) {
            // 召喚中ならドラッグでの変更も禁止
            if (manager.isSpawned(p)) {
                e.setCancelled(true);
            }
        } else {
            // 他のスロットへのドラッグは禁止 (背景など)
            // ただし自分のインベントリ領域ならOKにする判定が必要
            boolean involvesGui = e.getRawSlots().stream().anyMatch(s -> s < e.getInventory().getSize());
            if (involvesGui) {
                // ここでは単純化のため、アイテムスロット以外へのドラッグは全禁止
                if (!e.getRawSlots().contains(CompanionGui.SLOT_ITEM)) {
                    e.setCancelled(true);
                }
            }
        }
    }

    // GUIを閉じた時にアイテムを保存
    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        String title = PlainTextComponentSerializer.plainText().serialize(e.getView().title());
        if (!title.equals(CompanionGui.GUI_TITLE)) return;

        Player p = (Player) e.getPlayer();
        Inventory inv = e.getInventory();

        // スロットにあるアイテムを取得して保存 (nullなら削除扱いで保存)
        ItemStack item = inv.getItem(CompanionGui.SLOT_ITEM);
        manager.saveStoredItem(p.getUniqueId(), item);
    }
}