package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.data.DailyTaskData;
import com.lunar_prototype.deepwither.data.TraderOffer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.List;

public class TraderGUI implements Listener {

    public static final String BUY_GUI_TITLE = "§8[購入] §f%s"; // %sにトレーダー名が入る
    public static final String SELL_GUI_TITLE = "§8[売却] §f総合売却所"; // 全体共通の売却タイトル

    private static final String SELL_ID_KEY = "sell_price";
    private static final String CUSTOM_ID_KEY = "custom_id";
    private static final String TRADER_ID_KEY = "trader_id";

    /**
     * 購入GUIを生成し、プレイヤーに開かせる
     */
    public void openBuyGUI(Player player, String traderId, int playerCredit, TraderManager manager) {

        // 1. プレイヤーの信用度に基づき、購入可能なオファーを取得
        List<TraderOffer> availableOffers = manager.getAllOffers(traderId);

        // 2. GUIのサイズを決定 (9の倍数、最大54スロット)
        int size = ((availableOffers.size() / 9) + 1) * 9;
        size = Math.min(size, 54);

        // 3. インベントリを作成
        // ★ 修正箇所: IDではなく、設定から読み込んだ表示名を取得する
        String traderDisplayName = manager.getTraderName(traderId);

        // 3. インベントリを作成 (BUY_GUI_TITLE の %s に表示名を流し込む)
        Inventory gui = Bukkit.createInventory(player, size, String.format(BUY_GUI_TITLE, traderDisplayName));

        // ★ オファーを配置できる最大スロット数を計算
        // 最後の2スロット(size-1とsize-2)はボタン用に予約
        final int maxOfferSlots = size - 2;

        // 4. オファーをGUIに配置
        for (int i = 0; i < availableOffers.size(); i++) {

            // ★ 予約スロットに到達したら、ループを終了する
            if (i >= maxOfferSlots) {
                // オファー数がスロット容量を超えたため、超過分は表示されない
                break;
            }

            TraderOffer offer = availableOffers.get(i);

            int finalI = i;
            offer.getLoadedItem().ifPresent(originalItem -> {
                // ... (アイテムの複製、Loreへの価格/信用度追加、PDC設定の既存ロジック) ...

                ItemStack displayItem = originalItem.clone();
                ItemMeta meta = displayItem.getItemMeta();

                // アイテムロアに価格と必要信用度を追加
                List<String> lore = meta.getLore() != null ? meta.getLore() : new java.util.ArrayList<>();

                lore.add("");
                lore.add("§a--- 取引情報 ---");
                lore.add("§7価格: §6" + Deepwither.getEconomy().format(offer.getCost()));
                lore.add("§7必要信用度: §b" + offer.getRequiredCredit());

                NamespacedKey pdc_key = new NamespacedKey(Deepwither.getInstance(), SELL_ID_KEY);
                meta.getPersistentDataContainer().set(pdc_key, PersistentDataType.INTEGER, offer.getCost());

                if (playerCredit >= offer.getRequiredCredit()) {
                    lore.add(ChatColor.GREEN + "クリックして購入");
                } else {
                    lore.add(ChatColor.RED + "信用度が不足しています");
                    meta.getPersistentDataContainer().set(pdc_key, PersistentDataType.INTEGER, 0);
                }

                meta.setLore(lore);
                displayItem.setItemMeta(meta);

                gui.setItem(finalI, displayItem);
            });
        }

        // 5. 売却ボタンを追加 (最後のスロットに固定)
        addSellButton(gui, size - 1);

        // 6. デイリータスクボタンを追加 (売却ボタンの左隣)
        addDailyTaskButton(player, gui, size - 2, traderId, Deepwither.getInstance().getDailyTaskManager());

        player.openInventory(gui);
    }

    /**
     * 売却画面へ遷移するためのボタンを追加する
     */
    private static void addSellButton(Inventory gui, int slot) {
        // 例: エメラルドを使って売却ボタンを作成
        ItemStack sellButton = new ItemStack(org.bukkit.Material.EMERALD);
        ItemMeta meta = sellButton.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + ">> 売却画面へ <<");
        List<String> lore = new java.util.ArrayList<>();
        lore.add("§7あなたのアイテムを売却します。");
        meta.setLore(lore);
        sellButton.setItemMeta(meta);

        gui.setItem(slot, sellButton);
    }

    private void addDailyTaskButton(Player player, Inventory gui, int slot, String traderId, DailyTaskManager taskManager) {
        DailyTaskData data = taskManager.getTaskData(player);
        int[] progress = data.getProgress(traderId);
        int current = progress[0];
        int target = progress[1];

        // ★追加: ターゲット名を取得
        String targetMobId = data.getTargetMob(traderId);
        String displayMobName = targetMobId.equals("bandit") ? "バンディット" : targetMobId;

        int completedCount = data.getCompletionCount(traderId);
        int limit = Deepwither.getInstance().getTraderManager().getDailyTaskLimit(traderId);

        ItemStack taskButton = new ItemStack(org.bukkit.Material.WRITABLE_BOOK);
        ItemMeta meta = taskButton.getItemMeta();
        List<String> lore = new java.util.ArrayList<>();

        meta.setDisplayName("§eデイリータスク (" + traderId + ")");
        NamespacedKey pdc_key = new NamespacedKey(Deepwither.getInstance(), TRADER_ID_KEY);
        meta.getPersistentDataContainer().set(pdc_key, PersistentDataType.STRING,traderId);

        lore.add("§7残りのタスク完了回数: §b" + (limit - completedCount) + "§7/" + limit);

        if (completedCount >= limit) {
            lore.add("");
            lore.add(ChatColor.RED + ">> 本日のタスク制限に達しました <<");
            taskButton.setType(org.bukkit.Material.BARRIER);
        } else if (target != 0) {
            // タスク進行中
            lore.add("§a--- 現在の目標 ---");
            // ★変更: バンディット固定ではなくターゲット名を表示
            lore.add("§7" + displayMobName + "討伐: §c" + current + "§7/" + target);
            lore.add("");
            lore.add(ChatColor.YELLOW + "目標を達成して報告してください。");
        } else {
            // タスク未開始
            lore.add("");
            // ★変更: タスク受注時の説明文
            lore.add("§a[討伐依頼] §7現在のエリア周辺の");
            lore.add("§7脅威となっている生命体を討伐する。");
            lore.add("");
            lore.add(ChatColor.GREEN + "クリックでタスクを受注");
        }

        meta.setLore(lore);
        taskButton.setItemMeta(meta);

        gui.setItem(slot, taskButton);
    }

    /**
     * GUIクリック時の処理（リスナーとしてメインクラスで登録が必要）
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (e.getClickedInventory() == null) return;

        // 購入GUIであるかチェック
        if (e.getView().getTitle().startsWith(BUY_GUI_TITLE.substring(0, 7))) {
            e.setCancelled(true); // 取引GUIではアイテム移動を禁止

            if (e.getCurrentItem() == null || e.getCurrentItem().getType() == org.bukkit.Material.AIR) return;

            // ----------------------------------------------------
            // --- 売却ボタンの処理 ---
            // ----------------------------------------------------
            if (e.getSlot() == e.getInventory().getSize() - 1 &&
                    e.getCurrentItem().getType() == org.bukkit.Material.EMERALD &&
                    e.getCurrentItem().getItemMeta().getDisplayName().contains("売却画面へ")) {

                // 次のステップで実装する SellGUI を開く
                SellGUI.openSellGUI(player, Deepwither.getInstance().getTraderManager());
                return;
            }

            if (e.getSlot() == e.getInventory().getSize() - 2 &&
                    e.getCurrentItem().getType() == org.bukkit.Material.WRITABLE_BOOK &&
                    e.getCurrentItem().getItemMeta().getDisplayName().contains("デイリータスク")) {

                DailyTaskManager taskManager = Deepwither.getInstance().getDailyTaskManager();
                NamespacedKey pdc_key = new NamespacedKey(Deepwither.getInstance(), TRADER_ID_KEY);
                String traderId;
                if (e.getCurrentItem().getItemMeta().getPersistentDataContainer().has(pdc_key)){
                    traderId = e.getCurrentItem().getItemMeta().getPersistentDataContainer().get(pdc_key,PersistentDataType.STRING);
                }else{
                    player.sendMessage("§e[タスク] §7トレーダーIDが見つかりませんでした");
                    return;
                }

                // タスクが進行中でない ＆ 制限に達していない 場合に新規タスクを開始
                DailyTaskData data = taskManager.getTaskData(player);
                if (data.getProgress(traderId)[1] == 0 && data.getCompletionCount(traderId) <= Deepwither.getInstance().getTraderManager().getDailyTaskLimit(traderId)) {
                    taskManager.startNewTask(player, traderId);
                    // GUIをリフレッシュ
                } else if (data.getProgress(traderId)[0] >= data.getProgress(traderId)[1] && data.getProgress(traderId)[1] > 0) {
                    // タスクが完了している場合は報酬付与（TaskListenerのcompleteTaskを直接呼ぶ代わりに、Manager経由で）
                    // Managerに報酬を付与し、タスクをリセットするメソッドを追加することを推奨。
                    // ここでは便宜上、Managerのロジックを再実行する
                    taskManager.completeTask(player, traderId);
                } else {
                    player.sendMessage("§e[タスク] §7現在タスクを進行中です。");
                }
                return;
            }

            // ----------------------------------------------------
            // --- 商品購入のロジック ---
            // ----------------------------------------------------

            // TraderManagerなどの情報が必要なため、本来はインスタンスメソッドで処理すべきですが、
            // シンプル化のため、ここでは静的メソッド内で処理を継続します。（実運用ではシングルトン等推奨）
            // ... (購入ロジックは次の項目で詳細を記述) ...

            handlePurchase(player, e.getCurrentItem(), Deepwither.getInstance().getTraderManager());
        }
    }

    // TraderGUI.java に追加

    private static void handlePurchase(Player player, ItemStack clickedItem, TraderManager manager) {

        Economy econ = Deepwither.getEconomy();
        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null) {
            player.sendMessage(ChatColor.RED + "アイテムメタデータがありません。");
            return;
        }

        // 1. コストの取得
        int cost = 0;
        // PDCから価格キーを取得するためのNamespacedKeyを定義
        NamespacedKey sellIdKey = new NamespacedKey(Deepwither.getInstance(), SELL_ID_KEY);

        if (meta.getPersistentDataContainer().has(sellIdKey, PersistentDataType.INTEGER)){
            cost = meta.getPersistentDataContainer().get(sellIdKey, PersistentDataType.INTEGER);
        }

        if (cost <= 0) {
            player.sendMessage(ChatColor.RED + "このアイテムは購入できません。（価格設定なし）");
            return;
        }

        // 2. 残高チェック
        if (!econ.has(player, cost)) {
            player.sendMessage(ChatColor.RED + "残高が不足しています！ 必要額: " + econ.format(cost));
            return;
        }

        // 3. インベントリ空きチェック
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(ChatColor.RED + "インベントリに空きがありません。");
            return;
        }

        // 4. 購入アイテムの特定と取得
        ItemStack itemToGive = null;
        NamespacedKey customIdKey = new NamespacedKey(Deepwither.getInstance(), CUSTOM_ID_KEY);

        // PDCからカスタムアイテムIDの有無をチェック
        if (meta.getPersistentDataContainer().has(customIdKey, PersistentDataType.STRING)) {
            // --- ★ カスタムアイテムの場合 ---
            String customId = meta.getPersistentDataContainer().get(customIdKey, PersistentDataType.STRING);
            File itemFolder = new File(Deepwither.getInstance().getDataFolder(), "items");

            // ItemLoaderを使用してカスタムアイテムをロード
            itemToGive = ItemLoader.loadSingleItem(customId, Deepwither.getInstance().getItemFactory(), itemFolder);

        } else {
            // --- ★ バニラアイテムの場合 ---
            Material material = clickedItem.getType();
            if (material != Material.AIR && material.isItem()) {
                // クリックされたアイテムのMaterialと個数をそのまま使用
                itemToGive = clickedItem.clone();

                // GUI表示用のLoreなどを削除する処理（ItemFactoryなどに依存）
                // ItemFactory.cleanPurchaseLore(itemToGive); // 必要であればここに追加

                // ItemMetaからGUI用のメタデータをクリアする
                ItemMeta itemToGiveMeta = itemToGive.getItemMeta();
                if (itemToGiveMeta != null) {
                    itemToGiveMeta.setDisplayName(null); // GUIタイトルを削除
                    itemToGiveMeta.setLore(null);        // GUIロアを削除
                    // PDCからも不要なキーを削除 (SELL_ID_KEYなど)
                    itemToGive.setItemMeta(itemToGiveMeta);
                }

            } else {
                player.sendMessage(ChatColor.RED + "アイテムの取得に失敗しました。(無効なマテリアル)");
                return;
            }
        }

        if (itemToGive == null) {
            player.sendMessage(ChatColor.RED + "アイテムの取得に失敗しました。");
            return;
        }

        // 5. 取引実行
        econ.withdrawPlayer(player, cost);
        player.getInventory().addItem(itemToGive);

        player.sendMessage(ChatColor.GREEN + itemToGive.getItemMeta().getDisplayName() + " を " + econ.format(cost) + " で購入しました。");
    }
}