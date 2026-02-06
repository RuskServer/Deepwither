package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

@DependsOn({TraderManager.class, StatManager.class})
public class SellGUI implements Listener, IManager {

    private final JavaPlugin plugin;

    public SellGUI(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

    public static final String SELL_GUI_TITLE = "§8[売却] §f総合売却所";
    // 識別用のキー（DeepWither.javaでNamespacedKeyとして定義すべき）
    private static final String CUSTOM_ID_KEY = "custom_id";

    /**
     * 各StatTypeのFlat値1単位あたりの売却単価 (G)
     */
    private static final Map<StatType, Double> FLAT_PRICE_MULTIPLIERS = new EnumMap<>(StatType.class);

    /**
     * 各StatTypeのPercent値1%あたりの売却単価 (G)
     */
    private static final Map<StatType, Double> PERCENT_PRICE_MULTIPLIERS = new EnumMap<>(StatType.class);

    // ★ StatMapの初期化ブロック
    static {
        // Flat値の単価設定
        FLAT_PRICE_MULTIPLIERS.put(StatType.ATTACK_DAMAGE, 20.0);
        FLAT_PRICE_MULTIPLIERS.put(StatType.DEFENSE, 20.0);
        FLAT_PRICE_MULTIPLIERS.put(StatType.MAX_HEALTH, 20.0);
        FLAT_PRICE_MULTIPLIERS.put(StatType.PROJECTILE_DAMAGE, 20.0);
        FLAT_PRICE_MULTIPLIERS.put(StatType.MAGIC_DAMAGE, 20.0);
        FLAT_PRICE_MULTIPLIERS.put(StatType.MAGIC_RESIST, 40.0);
        FLAT_PRICE_MULTIPLIERS.put(StatType.MAGIC_PENETRATION, 50.0);
        // 新しいFlatステータスはここに追加するだけでOK

        // Percent値の単価設定
        PERCENT_PRICE_MULTIPLIERS.put(StatType.MAX_HEALTH, 100.0); // HP増加(Percent)はFlatより高価に
        PERCENT_PRICE_MULTIPLIERS.put(StatType.CRIT_DAMAGE, 75.0);
        // 新しいPercentステータスはここに追加するだけでOK
    }

    /**
     * 売却GUIを生成し、プレイヤーに開かせる
     */
    public static void openSellGUI(Player player, TraderManager manager) {
        // 9の倍数、売却スロット + 戻るボタンなどのレイアウトを想定
        Inventory gui = Bukkit.createInventory(player, 27, SELL_GUI_TITLE);

        // GUIのレイアウト設定 (境界線や説明アイテムなど)
        ItemStack filler = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        ItemStack backButton = createGuiItem(Material.ARROW, ChatColor.YELLOW + "<< 戻る");

        // 境界線を配置
        for (int i = 0; i < 9; i++) {
            gui.setItem(i, filler);
            gui.setItem(26 - i, filler);
        }

        // メインエリア（中央部分）を空けて、売却エリアとする (例: 9-17, 18-25)

        // 戻るボタンを配置 (例: 22スロット目)
        gui.setItem(22, backButton);

        player.openInventory(gui);
    }

    private static ItemStack createGuiItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }


    /**
     * GUIクリック時の処理（リスナー）
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (e.getClickedInventory() == null) return;

        // 売却GUIであるかチェック
        if (!e.getView().getTitle().equals(SELL_GUI_TITLE)) return;

        TraderManager manager = Deepwither.getInstance().getTraderManager();
        Economy econ = Deepwither.getEconomy();

        // ----------------------------------------------------
        // --- 制御アイテムの処理 ---
        // ----------------------------------------------------
        if (e.getClickedInventory().equals(e.getView().getTopInventory())) {

            // 戻るボタン（例: ARROW）
            if (e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.ARROW) {
                e.setCancelled(true);
                // ひとつ前のGUI（TraderGUI）に戻るロジックを実装する必要があります
                // 現状、ここでは単純に閉じるか、メインメニューに戻ると仮定します。
                player.closeInventory();
                player.sendMessage(ChatColor.YELLOW + "取引を終了しました。");
                return;
            }

            // 境界線パネル
            if (e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.GRAY_STAINED_GLASS_PANE) {
                e.setCancelled(true); // 境界線は触れない
                return;
            }
        }

        // ----------------------------------------------------
        // ★ 追加/修正: シフトクリックによる売却ロジック
        // ----------------------------------------------------

        // クリックしたのがプレイヤーインベントリ (Bottom Inventory) からTop Inventoryへ移動する場合
        if (e.getClickedInventory().equals(e.getView().getBottomInventory()) &&
                e.isShiftClick()) {

            // シフトクリックで売却できるかチェック (このアイテムは e.getCurrentItem() にある)
            ItemStack itemToSell = e.getCurrentItem();
            if (itemToSell == null || itemToSell.getType() == Material.AIR) return;

            // **GUIの売却可能エリアに空きがあるか**をチェックする必要があるが、
            // 即時売却方式では、GUIの空き状況は無視して処理を進められる。

            e.setCancelled(true); // シフトクリックによるバニラの移動処理をキャンセル

            // 共通の売却処理を呼び出す
            processSell(player, itemToSell, manager, econ, e);
            return; // 処理完了
        }

        // ----------------------------------------------------
        // --- ドラッグ＆ドロップによる売却ロジック (既存) ---
        // ----------------------------------------------------

        // クリックしたのがTop Inventoryであり、カーソルにアイテムがある場合 (置く動作)
        if (e.getClickedInventory().equals(e.getView().getTopInventory()) &&
                e.getCursor() != null && e.getCursor().getType() != Material.AIR) {

            // GUIの売却可能エリア (スロット 9から17) であるかチェック
            if (e.getSlot() > 8 && e.getSlot() < 18) {
                e.setCancelled(true); // 即座に売却処理を行うため、アイテムがスロットに入るのをキャンセル

                ItemStack itemToSell = e.getCursor().clone();

                // 共通の売却処理を呼び出す
                processSell(player, itemToSell, manager, econ, e);
            }
        }
    }


    /**
     * 売却処理の共通ロジック
     */
    private void processSell(Player player, ItemStack itemToSell, TraderManager manager, Economy econ, InventoryClickEvent e) {
        int amount = itemToSell.getAmount();

        // 1. アイテムのIDと価格を取得
        String itemId = getItemId(itemToSell);
        int pricePerItem = manager.getSellPrice(itemId);

        if (pricePerItem > 0) {
            // 売却リストに価格がある場合
            int totalCost = pricePerItem * amount;
            econ.depositPlayer(player, totalCost);

            // シフトクリックの場合は currentItem、ドラッグ&ドロップの場合は cursor を削除
            if (e.isShiftClick()) {
                e.setCurrentItem(null);
            } else {
                e.getCursor().setAmount(0);
            }

            player.sendMessage(ChatColor.GREEN + itemToSell.getItemMeta().getDisplayName() + " x" + amount +
                    " を売却し、" + econ.format(totalCost) + " を獲得しました。");

        } else {
            // 売却リストにない場合、StatMap評価を行う
            pricePerItem = calculatePriceByStats(itemToSell);

            if (pricePerItem > 0) {
                // ステータス評価で価格が算出された場合
                int totalCost = pricePerItem * amount;
                econ.depositPlayer(player, totalCost);

                // シフトクリックの場合は currentItem、ドラッグ&ドロップの場合は cursor を削除
                if (e.isShiftClick()) {
                    e.setCurrentItem(null);
                } else {
                    e.getCursor().setAmount(0);
                }

                player.sendMessage(ChatColor.GREEN + itemToSell.getItemMeta().getDisplayName() + " x" + amount +
                        " を §eステータス評価§fにより売却し、" + econ.format(totalCost) + " を獲得しました。");
            } else {
                // 評価でも価格がつかなかった場合
                player.sendMessage(ChatColor.RED + "このアイテムは売却できません。");
            }
        }
    }

    /**
     * ItemStackから売却リスト照合用のID（Material名またはCustom ID）を取得する。
     */
    private String getItemId(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return "";

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item.getType().name(); // メタがない場合はマテリアル名

        // 1. PDC (Persistent Data Container) からカスタムIDを取得
        // DeepWither.getInstance() などからNamespacedKeyを取得する必要がある
        // ここでは仮に 'custom_id' という文字列キーを使用
        if (meta.getPersistentDataContainer().has(
                new org.bukkit.NamespacedKey(Deepwither.getInstance(), CUSTOM_ID_KEY), PersistentDataType.STRING)) {

            return meta.getPersistentDataContainer().get(
                    new org.bukkit.NamespacedKey(Deepwither.getInstance(), CUSTOM_ID_KEY), PersistentDataType.STRING);
        }

        // 2. バニラアイテムとしてMaterial名を返す
        return item.getType().name();
    }

    /**
     * アイテムに付与されているカスタムステータスに基づいて価格を計算する。
     * @param item 評価対象のアイテム
     * @return 計算された合計価格（1個あたり）
     */
    private int calculatePriceByStats(ItemStack item) {
        // StatManagerのメソッドを使用してStatMapを取得
        final StatMap stats = StatManager.readStatsFromItem(item);

        double totalValue = 0;

        // StatMapに存在する全てのStatTypeを反復処理
        for (StatType type : StatType.values()) {
            double flatValue = stats.getFlat(type);
            double percentValue = stats.getPercent(type);

            // 1. Flat値の評価
            if (flatValue > 0) {
                // 定義された単価を取得 (なければ 0.0)
                double multiplier = FLAT_PRICE_MULTIPLIERS.getOrDefault(type, 0.0);
                totalValue += flatValue * multiplier;
            }

            // 2. Percent値の評価
            if (percentValue > 0) {
                // 定義された単価を取得 (なければ 0.0)
                double multiplier = PERCENT_PRICE_MULTIPLIERS.getOrDefault(type, 0.0);
                totalValue += percentValue * multiplier;
            }
        }

        // 計算結果を整数に丸め、最小価格を保証
        return Math.max(1, (int) Math.round(totalValue));
    }
}