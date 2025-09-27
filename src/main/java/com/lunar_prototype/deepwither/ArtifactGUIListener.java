package com.lunar_prototype.deepwither;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ArtifactGUIListener implements Listener {

    // GUIのインスタンスを共有するために、ArtifactGUIクラスのインスタンスを渡す
    private ArtifactGUI artifactGUI;

    public ArtifactGUIListener(ArtifactGUI gui) {
        this.artifactGUI = gui;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals("アーティファクト")) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null) {
            return;
        }

        int slot = event.getSlot();

        // **修正箇所: クリックされたスロットがGUIのアーティファクトスロットかどうかを厳密にチェック**
        boolean isArtifactSlot = false;
        for (int s : ArtifactGUI.ARTIFACT_SLOTS) {
            if (s == slot) {
                isArtifactSlot = true;
                break;
            }
        }

        // **GUI内のクリックを処理**
        if (clickedInventory.equals(event.getView().getTopInventory())) {
            // **修正箇所: アーティファクトスロットを直接クリックした場合はキャンセル**
            if (isArtifactSlot) {
                // Shift-Click, Drag, Pick Upなど、GUI内のアイテムを動かす操作をキャンセル
                if (event.getCursor().getType() == Material.AIR && event.getCurrentItem() != null && event.getCurrentItem().getType() != Material.AIR) {
                    // カーソルが空でアイテムを持ち出そうとした場合
                    if (!isArtifact(event.getCurrentItem())) {
                        // 持ち出そうとしたアイテムがアーティファクトでない場合、強制的にキャンセル
                        event.setCancelled(true);
                        player.sendMessage("§cこのスロットにあるアイテムは持ち出せません。");
                        return;
                    }
                }

                // **カーソルにアイテムがあり、アーティファクトスロットに置こうとする場合**
                if (event.getCursor().getType() != Material.AIR) {
                    if (!isArtifact(event.getCursor())) {
                        event.setCancelled(true);
                        player.sendMessage("§cこのスロットにはアーティファクトのみ装備できます。");
                    }
                }
            } else {
                // アーティファクトスロット以外の場所（GUI内の装飾用アイテムなど）でのクリックは全てキャンセル
                event.setCancelled(true);
            }
        }

        // **プレイヤーインベントリ内のクリックを処理**
        if (clickedInventory.equals(player.getInventory())) {
            // **GUIにアイテムを入れようとした場合**
            if (event.getCursor().getType() != Material.AIR) {
                // アーティファクトでなければキャンセル
                if (!isArtifact(event.getCursor())) {
                    if (isArtifactSlot(event.getSlot())) { // 念のためチェック
                        event.setCancelled(true);
                        player.sendMessage("§cこのスロットにはアーティファクトのみ装備できます。");
                    }
                }
            }
        }

        if (isArtifactSlot && event.getCursor().getType() != Material.AIR) {
            Bukkit.getScheduler().runTaskLater(Deepwither.getInstance(), () -> {
                // アイテムが正常にスロットに置かれたことを確認
                ItemStack itemInSlot = player.getOpenInventory().getItem(slot);
                if (itemInSlot != null && isArtifact(itemInSlot)) {
                    // カーソルにアイテムが残っている場合、それを消去
                    // （これは入れ替え操作で発生）
                    if (player.getItemOnCursor() != null) {
                        player.setItemOnCursor(new ItemStack(Material.AIR));
                    }
                }
            }, 1L);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getView().getTitle().equals("アーティファクト")) {
            Player player = (Player) event.getPlayer();
            Inventory guiInventory = event.getInventory();

            List<ItemStack> artifacts = new ArrayList<>();
            for (int slot : ArtifactGUI.ARTIFACT_SLOTS) {
                ItemStack item = guiInventory.getItem(slot);
                // **修正箇所: プレースホルダーは保存しない**
                if (item != null && item.getType() != Material.GRAY_STAINED_GLASS_PANE) {
                    artifacts.add(item);
                }
            }

            Deepwither.getInstance().getArtifactManager().savePlayerArtifacts(player, artifacts);
            StatManager.updatePlayerStats(player);
        }
    }

    /**
     * アーティファクトスロットかどうかを判定
     */
    private boolean isArtifactSlot(int slot) {
        for (int s : ArtifactGUI.ARTIFACT_SLOTS) {
            if (s == slot) {
                return true;
            }
        }
        return false;
    }

    /**
     * 指定されたアイテムがアーティファクトであるかを判定する。
     * @param item 判定対象のアイテム
     * @return アイテムがアーティファクトであればtrue、そうでなければfalse
     */
    private boolean isArtifact(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta.hasLore()) {
            List<String> lore = meta.getLore();
            for (String line : lore) {
                // "アーティファクト"という文字列が含まれているかをチェック
                if (line.contains("アーティファクト")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 3つのアーティファクトスロットから統計情報を更新
     */
    private void updateArtifactStats(Player player, Inventory guiInventory) {

        for (int slot : ArtifactGUI.ARTIFACT_SLOTS) {
            ItemStack artifactItem = guiInventory.getItem(slot);
            if (artifactItem != null) {
                // ここでアイテムから統計情報を読み取り、プレイヤーに加算
                // 例: StatManager.addStatsFromItem(player, artifactItem);
            }
        }

        // 全ての装備とアーティファクトの合計統計情報を再計算
        StatManager.updatePlayerStats(player);
        double maxMana = StatManager.getTotalStatsFromEquipment(player).getFlat(StatType.MAX_MANA);
        Deepwither.getInstance().getManaManager().get(player.getUniqueId()).setMaxMana(maxMana);
    }
}
