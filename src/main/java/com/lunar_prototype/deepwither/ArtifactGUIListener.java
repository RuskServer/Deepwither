package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.api.stat.IStatManager;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
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
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

@DependsOn({ArtifactGUI.class, StatManager.class})
public class ArtifactGUIListener implements Listener, IManager {

    // GUIのインスタンスを共有するために、ArtifactGUIクラスのインスタンスを渡す
    private ArtifactGUI artifactGUI;
    private IStatManager statManager;
    private final JavaPlugin plugin;

    public ArtifactGUIListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        this.artifactGUI = Deepwither.getInstance().getArtifactGUI();
        this.statManager = Deepwither.getInstance().getStatManager();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().contains("アーティファクト")) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null) return;

        int slot = event.getSlot();
        ItemStack cursorItem = event.getCursor();
        ItemStack currentItem = event.getCurrentItem();

        // 1. 上（GUI）のインベントリをクリックした場合の処理
        if (clickedInventory.equals(event.getView().getTopInventory())) {

            boolean isArtifact = isArtifactSlot(slot);
            boolean isBackpack = (slot == ArtifactGUI.BACKPACK_SLOT);

            // A. 許可されたスロット（アーティファクト or 背中）の場合
            if (isArtifact || isBackpack) {

                // --- 持ち出しの判定 ---
                if (cursorItem.getType() == Material.AIR && currentItem != null && currentItem.getType() != Material.AIR) {
                    // プレースホルダー（ガラス板）なら持ち出しをキャンセル
                    if (currentItem.getType() == Material.CYAN_STAINED_GLASS_PANE ||
                            currentItem.getType() == Material.PURPLE_STAINED_GLASS_PANE) {
                        event.setCancelled(true);
                        player.sendMessage("§cこのプレースホルダーは持ち出せません。");
                        return;
                    }
                    // ここで return しないことで、本物のアイテムなら持ち出し（キャンセルなし）が成立します
                    return;
                }

                // --- 設置・入れ替えの判定 ---
                if (cursorItem.getType() != Material.AIR) {
                    if (isArtifact && !isArtifact(cursorItem)) {
                        event.setCancelled(true);
                        player.sendMessage("§cここにはアーティファクトのみ装備できます。");
                    } else if (isBackpack && !isBackpackItem(cursorItem)) {
                        event.setCancelled(true);
                        player.sendMessage("§cここには背中装備のみ装備できます。");
                    }

                    if (currentItem != null && (currentItem.getType() == Material.CYAN_STAINED_GLASS_PANE ||
                            currentItem.getType() == Material.PURPLE_STAINED_GLASS_PANE)) {

                        // 通常の交換イベントをキャンセル（ガラス板が手に来るのを防ぐ）
                        event.setCancelled(true);

                        // 手動で装備処理を行う
                        // スロットにカーソルのアイテムをセット
                        clickedInventory.setItem(slot, cursorItem);
                        // カーソルのアイテムを消去（手に持っているアイテムをなくす）
                        player.setItemOnCursor(new ItemStack(Material.AIR));

                        // プレイヤーのインベントリ更新（表示ズレ防止のため念のため）
                        player.updateInventory();
                    }
                }

            } else {
                // B. それ以外のスロット（装飾用の枠など）
                event.setCancelled(true);
            }
        }

        // 2. 下（プレイヤーインベントリ）をクリックした場合の処理
        // シフトクリックで変な場所にアイテムが入るのを防ぐ
        if (clickedInventory.equals(player.getInventory())) {
            if (event.isShiftClick()) {
                // シフトクリックは処理が複雑になるため、このGUIでは一旦無効化するのが安定します
                event.setCancelled(true);
                player.sendMessage("§cこのGUIではシフトクリックは無効です。ドラッグして配置してください。");
            }
        }
    }

    // ArtifactGUIListener.java の onInventoryClose メソッド (修正)

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getView().getTitle().contains("アーティファクト")) {
            Player player = (Player) event.getPlayer();
            Inventory guiInventory = event.getInventory();
            Deepwither plugin = Deepwither.getInstance();

            // 1. アーティファクトの取得
            List<ItemStack> artifacts = new ArrayList<>();
            for (int slot : ArtifactGUI.ARTIFACT_SLOTS) {
                ItemStack item = guiInventory.getItem(slot);
                if (item != null && item.getType() != Material.CYAN_STAINED_GLASS_PANE) {
                    artifacts.add(item);
                }
            }

            // 2. 背中装備の取得
            ItemStack backpackItem = guiInventory.getItem(ArtifactGUI.BACKPACK_SLOT);
            ItemStack toSaveBackpack = null;

            if (backpackItem != null && backpackItem.getType() != Material.PURPLE_STAINED_GLASS_PANE) {
                toSaveBackpack = backpackItem;

                // BackpackManagerと連携して表示を更新
                if (backpackItem.hasItemMeta() && backpackItem.getItemMeta().hasCustomModelData()) {
                    int model = backpackItem.getItemMeta().getCustomModelData();
                    plugin.getBackpackManager().equipBackpack(player, model);
                }
            } else {
                // 空なら背中装備を解除
                plugin.getBackpackManager().unequipBackpack(player);
            }

            // 3. まとめて保存
            plugin.getArtifactManager().savePlayerArtifacts(player, artifacts, toSaveBackpack);

            // 統計情報の更新
            statManager.updatePlayerStats(player);
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
     * 背中装備アイテムかどうかを判定する (例: Loreに "背中装備" とあるか)
     */
    private boolean isBackpackItem(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) return false;
        return item.getItemMeta().getLore().stream().anyMatch(line -> line.contains("背中装備"));
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
        statManager.updatePlayerStats(player);
        double maxMana = statManager.getTotalStats(player).getFlat(StatType.MAX_MANA);
        Deepwither.getInstance().getManaManager().get(player.getUniqueId()).setMaxMana(maxMana);
    }
}
