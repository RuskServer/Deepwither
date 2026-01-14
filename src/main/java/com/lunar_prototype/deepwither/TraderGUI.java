package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.data.DailyTaskData;
import com.lunar_prototype.deepwither.data.PlayerQuestData;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TraderGUI implements Listener {

    public static final String BUY_GUI_TITLE = "§8[購入] §f%s"; // %sにトレーダー名が入る
    public static final String SELL_GUI_TITLE = "§8[売却] §f総合売却所"; // 全体共通の売却タイトル
    public static final String QUEST_GUI_TITLE = "§8[クエスト] §f%s";

    private static final String SELL_ID_KEY = "sell_price";
    private static final String OFFER_ID_KEY = "offer_id";
    private static final String CUSTOM_ID_KEY = "custom_id";
    private static final String TRADER_ID_KEY = "trader_id";

    /**
     * 購入GUIを生成し、プレイヤーに開かせる
     */
    public void openBuyGUI(Player player, String traderId, int playerCredit, TraderManager manager) {

        // 1. 全てのオファーを取得
        List<TraderOffer> allOffers = manager.getAllOffers(traderId);

        // 2. GUIのサイズを決定 (下段1列(9スロット)をボタン用に確保するため、最低18、最大54)
        // 商品数に応じて行数を計算し、最後に1列追加する
        int offerRows = (int) Math.ceil(allOffers.size() / 9.0);
        int size = (offerRows + 1) * 9;
        size = Math.min(size, 54);

        // インベントリの作成
        String traderDisplayName = manager.getTraderName(traderId);
        Inventory gui = Bukkit.createInventory(player, size, String.format(BUY_GUI_TITLE, traderDisplayName));

        // ★ 商品を配置できる最大スロット（最終行を除いた範囲）
        final int maxOfferSlots = size - 9;

        // 4. オファーをGUIに配置
        for (int i = 0; i < allOffers.size(); i++) {
            if (i >= maxOfferSlots) break;

            TraderOffer offer = allOffers.get(i);
            int currentSlot = i;

            offer.getLoadedItem().ifPresent(originalItem -> {
                ItemStack displayItem = originalItem.clone();
                ItemMeta meta = displayItem.getItemMeta();
                List<String> lore = meta.getLore() != null ? meta.getLore() : new java.util.ArrayList<>();

                // ★ クエストおよび信用度による解禁判定
                // manager.canAccessTier(player, traderId, requiredCredit, playerCredit) を使用
                boolean isUnlocked = manager.canAccessTier(player, traderId, offer.getRequiredCredit(), playerCredit);

                lore.add("");
                lore.add("§a--- 取引情報 ---");

                if (offer.getCost() > 0) {
                    lore.add("§7価格: §6" + Deepwither.getEconomy().format(offer.getCost()));
                }

                // 必要アイテムの表示
                List<ItemStack> reqItems = offer.getRequiredItems();
                if (reqItems != null && !reqItems.isEmpty()) {
                    lore.add("§7必要アイテム:");
                    for (ItemStack req : reqItems) {
                        String itemName = req.hasItemMeta() && req.getItemMeta().hasDisplayName()
                                ? req.getItemMeta().getDisplayName()
                                : req.getType().name().toLowerCase().replace("_", " ");
                        lore.add(" §8- §f" + itemName + " §7×" + req.getAmount());
                    }
                }

                lore.add("§7必要信用度: §b" + offer.getRequiredCredit());

                // PDCの基本設定
                meta.getPersistentDataContainer().set(new NamespacedKey(Deepwither.getInstance(), SELL_ID_KEY), PersistentDataType.INTEGER, offer.getCost());
                meta.getPersistentDataContainer().set(new NamespacedKey(Deepwither.getInstance(), OFFER_ID_KEY), PersistentDataType.STRING, offer.getId());
                meta.getPersistentDataContainer().set(new NamespacedKey(Deepwither.getInstance(), TRADER_ID_KEY), PersistentDataType.STRING, traderId);

                // ★ 解禁状態に応じたLoreとアイテムの変更
                if (isUnlocked) {
                    lore.add(ChatColor.GREEN + "クリックして購入");
                } else {
                    // ロックされている場合
                    displayItem.setType(Material.GRAY_STAINED_GLASS_PANE); // 見た目をロック状態に変更
                    lore.add("");
                    lore.add(ChatColor.RED + "§l【 ⚠ ロック中 】");
                    lore.add(ChatColor.RED + "信用度が不足しているか、");
                    lore.add(ChatColor.RED + "特定のクエストを完了する必要があります。");

                    // 購入できないようにコストPDCを0にする等の対策
                    meta.getPersistentDataContainer().set(new NamespacedKey(Deepwither.getInstance(), SELL_ID_KEY), PersistentDataType.INTEGER, 0);
                }

                meta.setLore(lore);
                displayItem.setItemMeta(meta);
                gui.setItem(currentSlot, displayItem);
            });
        }

        // --- 5. 下段ボタンの配置 (最終行に並べる) ---
        // 最終行の右端から順に配置
        int lastRowStart = size - 9;

        // 売却ボタン (右端)
        addSellButton(gui, size - 1);

        // デイリータスクボタン (右から2番目)
        addDailyTaskButton(player, gui, size - 2, traderId, Deepwither.getInstance().getDailyTaskManager());

        // クエストリストボタン (右から3番目)
        addQuestListButton(gui, size - 3, traderId);

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

            if (e.getCurrentItem().getItemMeta().getDisplayName().contains("トレーダークエスト")) {
                String traderId = e.getCurrentItem().getItemMeta().getPersistentDataContainer().get(
                        new NamespacedKey(Deepwither.getInstance(), TRADER_ID_KEY), PersistentDataType.STRING);
                openQuestGUI(player, traderId);
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

        // 2. クエスト専用GUIの処理
        if (e.getView().getTitle().startsWith("§8[クエスト]")) {
            e.setCancelled(true);
            ItemStack clicked = e.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;

            ItemMeta meta = clicked.getItemMeta();
            String qId = meta.getPersistentDataContainer().get(new NamespacedKey(Deepwither.getInstance(), "quest_id"), PersistentDataType.STRING);
            String tId = meta.getPersistentDataContainer().get(new NamespacedKey(Deepwither.getInstance(), TRADER_ID_KEY), PersistentDataType.STRING);

            if (qId == null || tId == null) return;

            TraderQuestManager tqm = Deepwither.getInstance().getTraderQuestManager();
            TraderManager tm = Deepwither.getInstance().getTraderManager();
            TraderManager.QuestData quest = tm.getQuestsForTrader(tId).get(qId);

            if (clicked.getType() == Material.BOOK) {
                // 受領
                tqm.acceptQuest(player, tId, qId);
                openQuestGUI(player, tId); // GUI更新
            } else if (clicked.getType() == Material.WRITABLE_BOOK) {
                // 報告/納品
                if ("FETCH".equalsIgnoreCase(quest.getType())) {
                    tqm.handleDelivery(player, tId, qId);
                }
                openQuestGUI(player, tId); // 進捗更新
            }
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

        NamespacedKey pdc_key1 = new NamespacedKey(Deepwither.getInstance(),TRADER_ID_KEY);
        String traderid = null;
        if (meta.getPersistentDataContainer().has(pdc_key1, PersistentDataType.STRING)){
            traderid = meta.getPersistentDataContainer().get(pdc_key1,PersistentDataType.STRING);
        }
        NamespacedKey pdc_key2 = new NamespacedKey(Deepwither.getInstance(),OFFER_ID_KEY);
        String offerid = null;
        if (meta.getPersistentDataContainer().has(pdc_key2, PersistentDataType.STRING)){
            offerid = meta.getPersistentDataContainer().get(pdc_key2,PersistentDataType.STRING);
        }

        if (cost <= 0) {
            player.sendMessage(ChatColor.RED + "このアイテムは購入できません。（価格設定なし）");
            return;
        }

        TraderOffer offer = manager.getOfferById(traderid,offerid);
        if (offer == null) {
            player.sendMessage(ChatColor.RED + "エラー: 指定された商品が見つかりませんでした。");
            return;
        }

        // 2. 残高チェック
        if (!econ.has(player, cost)) {
            player.sendMessage(ChatColor.RED + "残高が不足しています！ 必要額: " + econ.format(cost));
            return;
        }

        // 3. 必要アイテムのチェック
        List<ItemStack> requiredItems = offer.getRequiredItems();
        if (requiredItems != null && !requiredItems.isEmpty()) { // nullと空リストの両方を安全にチェック
            for (ItemStack req : requiredItems) {
                if (req == null) continue; // 万が一リストの中にnullが混じっていてもスキップ

                if (!player.getInventory().containsAtLeast(req, req.getAmount())) {
                    // 表示名があれば使い、なければMaterial名を表示
                    String itemName = req.hasItemMeta() && req.getItemMeta().hasDisplayName()
                            ? req.getItemMeta().getDisplayName()
                            : req.getType().name();

                    player.sendMessage(ChatColor.RED + "必要なアイテムが不足しています: " + itemName + " ×" + req.getAmount());
                    return;
                }
            }
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
        for (ItemStack req : requiredItems) {
            player.getInventory().removeItem(req);
        }
        player.getInventory().addItem(itemToGive);

        player.sendMessage(ChatColor.GREEN + itemToGive.getItemMeta().getDisplayName() + " を " + econ.format(cost) + " で購入しました。");
    }

    /**
     * メインの購入GUIにクエストボタンを追加（openBuyGUIメソッド内を修正）
     */
// size-3 などの空きスロットに配置
    private void addQuestListButton(Inventory gui, int slot, String traderId) {
        ItemStack button = new ItemStack(Material.BOOK);
        ItemMeta meta = button.getItemMeta();
        meta.setDisplayName("§6§lトレーダークエスト");
        List<String> lore = new ArrayList<>();
        lore.add("§7このトレーダーから受けられる");
        lore.add("§7永続的なタスクを確認します。");
        meta.setLore(lore);

        NamespacedKey key = new NamespacedKey(Deepwither.getInstance(), TRADER_ID_KEY);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, traderId);

        button.setItemMeta(meta);
        gui.setItem(slot, button);
    }

    /**
     * クエスト一覧GUIを開く（説明文・報酬表示対応）
     */
    public void openQuestGUI(Player player, String traderId) {
        TraderManager tm = Deepwither.getInstance().getTraderManager();
        TraderQuestManager tqm = Deepwither.getInstance().getTraderQuestManager();
        Map<String, TraderManager.QuestData> quests = tm.getQuestsForTrader(traderId);

        // クエスト数に合わせて行数を計算 (最低3行、最大6行)
        int rowCount = (int) Math.ceil(quests.size() / 9.0) + 1;
        int size = Math.min(Math.max(rowCount, 3), 6) * 9;

        Inventory gui = Bukkit.createInventory(player, size, String.format(QUEST_GUI_TITLE, tm.getTraderName(traderId)));

        for (TraderManager.QuestData quest : quests.values()) {
            ItemStack item;
            ItemMeta meta;
            List<String> lore = new ArrayList<>();

            boolean isCompleted = tqm.isQuestCompleted(player, traderId, quest.getId());
            boolean canAccept = tqm.canAcceptQuest(player, traderId, quest);

            String progressKey = traderId + ":" + quest.getId();
            PlayerQuestData data = tqm.getPlayerData(player);
            boolean isActive = data != null && data.getCurrentProgress().containsKey(progressKey);

            // --- Loreの構築 ---

            // 1. クエスト説明文 (最上部)
            if (quest.getDescription() != null && !quest.getDescription().isEmpty()) {
                for (String line : quest.getDescription()) {
                    lore.add("§7" + line);
                }
                lore.add(""); // 説明とステータスの間に空行
            }

            if (isCompleted) {
                item = new ItemStack(Material.ENCHANTED_BOOK);
                meta = item.getItemMeta();
                meta.setDisplayName("§a§l✔ §f" + quest.getDisplayName());
                lore.add("§7ステータス: §a完了済み");
            } else if (!canAccept) {
                item = new ItemStack(Material.BARRIER);
                meta = item.getItemMeta();
                meta.setDisplayName("§c§l[ロック中] §7" + quest.getDisplayName());
                lore.add("§7ステータス: §c未開放");
                lore.add("§7前提条件: §e" + (quest.getRequiredQuestId() != null ? quest.getRequiredQuestId() : "なし"));
            } else {
                item = new ItemStack(isActive ? Material.WRITABLE_BOOK : Material.BOOK);
                meta = item.getItemMeta();
                meta.setDisplayName((isActive ? "§e§l[進行中] " : "§6§l[受領可能] ") + "§f" + quest.getDisplayName());

                lore.add("§7タイプ: §f" + (quest.getType().equalsIgnoreCase("KILL") ? "討伐" : "納品"));
                lore.add("§7目標: §f" + quest.getTarget() + " §7を §f" + quest.getAmount() + "§7個");

                if (isActive) {
                    int current = data.getCurrentProgress().getOrDefault(progressKey, 0);
                    lore.add("§a現在の進捗: §f" + current + " / " + quest.getAmount());
                    lore.add("");
                    lore.add("§e▶ クリックして報告/納品");
                } else {
                    lore.add("");
                    lore.add("§a▶ クリックして受領する");
                }
            }

            // PDCの設定
            NamespacedKey qKey = new NamespacedKey(Deepwither.getInstance(), "quest_id");
            meta.getPersistentDataContainer().set(qKey, PersistentDataType.STRING, quest.getId());
            NamespacedKey tKey = new NamespacedKey(Deepwither.getInstance(), "trader_id");
            meta.getPersistentDataContainer().set(tKey, PersistentDataType.STRING, traderId);

            meta.setLore(lore);
            item.setItemMeta(meta);
            gui.addItem(item);
        }
        player.openInventory(gui);
    }
}