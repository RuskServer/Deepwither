package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.data.DailyTaskData;
import com.lunar_prototype.deepwither.data.TraderOffer;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
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
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@DependsOn({TraderManager.class, DailyTaskManager.class})
public class TraderGUI implements Listener, IManager {

    private final JavaPlugin plugin;

    public TraderGUI(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

    public static final String BUY_GUI_TITLE = "[購入] %s";
    public static final String SELL_GUI_TITLE = "[売却] 総合売却所";
    public static final String QUEST_GUI_TITLE = "[クエスト] %s";

    private static final String SELL_ID_KEY = "sell_price";
    private static final String OFFER_ID_KEY = "offer_id";
    private static final String CUSTOM_ID_KEY = "custom_id";
    private static final String TRADER_ID_KEY = "trader_id";

    public void openBuyGUI(Player player, String traderId, int playerCredit, TraderManager manager) {
        List<TraderOffer> allOffers = manager.getAllOffers(traderId);
        int offerRows = (int) Math.ceil(allOffers.size() / 9.0);
        int size = Math.min((offerRows + 1) * 9, 54);

        String traderDisplayName = manager.getTraderName(traderId);
        Component title = Component.text("[購入] ", NamedTextColor.DARK_GRAY).append(Component.text(traderDisplayName, NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false);
        Inventory gui = Bukkit.createInventory(player, size, title);

        final int maxOfferSlots = size - 9;

        for (int i = 0; i < allOffers.size(); i++) {
            if (i >= maxOfferSlots) break;
            TraderOffer offer = allOffers.get(i);
            int currentSlot = i;

            offer.getLoadedItem().ifPresent(originalItem -> {
                ItemStack displayItem = originalItem.clone();
                ItemMeta meta = displayItem.getItemMeta();
                List<Component> lore = meta.lore() != null ? meta.lore() : new ArrayList<>();

                boolean isUnlocked = manager.canAccessTier(player, traderId, offer.getRequiredCredit(), playerCredit);

                lore.add(Component.empty());
                lore.add(Component.text("--- 取引情報 ---", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));

                if (offer.getCost() > 0) {
                    lore.add(Component.text("価格: ", NamedTextColor.GRAY).append(Component.text(Deepwither.getEconomy().format(offer.getCost()), NamedTextColor.GOLD)).decoration(TextDecoration.ITALIC, false));
                }

                List<ItemStack> reqItems = offer.getRequiredItems();
                if (reqItems != null && !reqItems.isEmpty()) {
                    lore.add(Component.text("必要アイテム:", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                    for (ItemStack req : reqItems) {
                        Component itemName = req.hasItemMeta() && req.getItemMeta().hasDisplayName()
                                ? req.getItemMeta().displayName()
                                : Component.text(req.getType().name().toLowerCase().replace("_", " "));
                        lore.add(Component.text(" - ", NamedTextColor.DARK_GRAY)
                                .append(itemName.colorIfAbsent(NamedTextColor.WHITE))
                                .append(Component.text(" ×" + req.getAmount(), NamedTextColor.GRAY))
                                .decoration(TextDecoration.ITALIC, false));
                    }
                }

                lore.add(Component.text("必要信用度: ", NamedTextColor.GRAY).append(Component.text(offer.getRequiredCredit(), NamedTextColor.AQUA)).decoration(TextDecoration.ITALIC, false));

                meta.getPersistentDataContainer().set(new NamespacedKey(Deepwither.getInstance(), SELL_ID_KEY), PersistentDataType.INTEGER, offer.getCost());
                meta.getPersistentDataContainer().set(new NamespacedKey(Deepwither.getInstance(), OFFER_ID_KEY), PersistentDataType.STRING, offer.getId());
                meta.getPersistentDataContainer().set(new NamespacedKey(Deepwither.getInstance(), TRADER_ID_KEY), PersistentDataType.STRING, traderId);

                if (isUnlocked) {
                    lore.add(Component.text("クリックして購入", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
                } else {
                    displayItem.setType(Material.GRAY_STAINED_GLASS_PANE);
                    lore.add(Component.empty());
                    lore.add(Component.text("【 ⚠ ロック中 】", NamedTextColor.RED, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
                    lore.add(Component.text("信用度が不足しているか、", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
                    lore.add(Component.text("特定のクエストを完了する必要があります。", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
                    meta.getPersistentDataContainer().set(new NamespacedKey(Deepwither.getInstance(), SELL_ID_KEY), PersistentDataType.INTEGER, 0);
                }

                meta.lore(lore);
                displayItem.setItemMeta(meta);
                gui.setItem(currentSlot, displayItem);
            });
        }

        addSellButton(gui, size - 1);
        addDailyTaskButton(player, gui, size - 2, traderId, Deepwither.getInstance().getDailyTaskManager());

        player.openInventory(gui);
    }

    private static void addSellButton(Inventory gui, int slot) {
        ItemStack sellButton = new ItemStack(Material.EMERALD);
        ItemMeta meta = sellButton.getItemMeta();
        meta.displayName(Component.text(">> 売却画面へ <<", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("あなたのアイテムを売却します。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        sellButton.setItemMeta(meta);
        gui.setItem(slot, sellButton);
    }

    private void addDailyTaskButton(Player player, Inventory gui, int slot, String traderId, DailyTaskManager taskManager) {
        DailyTaskData data = taskManager.getTaskData(player);
        int[] progress = data.getProgress(traderId);
        int current = progress[0];
        int target = progress[1];

        String targetMobId = data.getTargetMob(traderId);
        String displayMobName = targetMobId.equals("bandit") ? "バンディット" : targetMobId;

        int completedCount = data.getCompletionCount(traderId);
        int limit = Deepwither.getInstance().getTraderManager().getDailyTaskLimit(traderId);

        ItemStack taskButton = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = taskButton.getItemMeta();
        List<Component> lore = new ArrayList<>();

        meta.displayName(Component.text("デイリータスク (" + traderId + ")", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(new NamespacedKey(Deepwither.getInstance(), TRADER_ID_KEY), PersistentDataType.STRING, traderId);

        lore.add(Component.text("残りのタスク完了回数: ", NamedTextColor.GRAY).append(Component.text((limit - completedCount) + "/" + limit, NamedTextColor.AQUA)).decoration(TextDecoration.ITALIC, false));

        if (completedCount >= limit) {
            lore.add(Component.empty());
            lore.add(Component.text(">> 本日のタスク制限に達しました <<", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            taskButton.setType(Material.BARRIER);
        } else if (target != 0) {
            lore.add(Component.text("--- 現在の目標 ---", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(displayMobName + "討伐: ", NamedTextColor.GRAY).append(Component.text(current + "/" + target, NamedTextColor.RED)).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("目標を達成して報告してください。", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.empty());
            lore.add(Component.text("[討伐依頼] ", NamedTextColor.GREEN).append(Component.text("現在のエリア周辺の", NamedTextColor.GRAY)).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("脅威となっている生命体を討伐する。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("クリックでタスクを受注", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        taskButton.setItemMeta(meta);
        gui.setItem(slot, taskButton);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (e.getClickedInventory() == null) return;

        String title = PlainTextComponentSerializer.plainText().serialize(e.getView().title());

        if (title.startsWith("[購入]")) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.AIR) return;

            ItemMeta meta = e.getCurrentItem().getItemMeta();
            String dispName = PlainTextComponentSerializer.plainText().serialize(meta.displayName());

            if (e.getSlot() == e.getInventory().getSize() - 1 && dispName.contains("売却画面へ")) {
                SellGUI.openSellGUI(player, Deepwither.getInstance().getTraderManager());
                return;
            }

            if (e.getSlot() == e.getInventory().getSize() - 2 && dispName.contains("デイリータスク")) {
                DailyTaskManager taskManager = Deepwither.getInstance().getDailyTaskManager();
                String traderId = meta.getPersistentDataContainer().get(new NamespacedKey(Deepwither.getInstance(), TRADER_ID_KEY), PersistentDataType.STRING);
                
                if (traderId == null) {
                    player.sendMessage(Component.text("[タスク] トレーダーIDが見つかりませんでした", NamedTextColor.YELLOW).append(Component.text("", NamedTextColor.GRAY)));
                    return;
                }

                DailyTaskData data = taskManager.getTaskData(player);
                if (data.getProgress(traderId)[1] == 0 && data.getCompletionCount(traderId) <= Deepwither.getInstance().getTraderManager().getDailyTaskLimit(traderId)) {
                    taskManager.startNewTask(player, traderId);
                } else if (data.getProgress(traderId)[0] >= data.getProgress(traderId)[1] && data.getProgress(traderId)[1] > 0) {
                    taskManager.completeTask(player, traderId);
                } else {
                    player.sendMessage(Component.text("[タスク] 現在タスクを進行中です。", NamedTextColor.YELLOW).append(Component.text("", NamedTextColor.GRAY)));
                }
                return;
            }

            handlePurchase(player, e.getCurrentItem(), Deepwither.getInstance().getTraderManager());
        }
    }

    private static void handlePurchase(Player player, ItemStack clickedItem, TraderManager manager) {
        Economy econ = Deepwither.getEconomy();
        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null) {
            player.sendMessage(Component.text("アイテムメタデータがありません。", NamedTextColor.RED));
            return;
        }

        NamespacedKey sellIdKey = new NamespacedKey(Deepwither.getInstance(), SELL_ID_KEY);
        int cost = meta.getPersistentDataContainer().getOrDefault(sellIdKey, PersistentDataType.INTEGER, 0);

        String traderid = meta.getPersistentDataContainer().get(new NamespacedKey(Deepwither.getInstance(), TRADER_ID_KEY), PersistentDataType.STRING);
        String offerid = meta.getPersistentDataContainer().get(new NamespacedKey(Deepwither.getInstance(), OFFER_ID_KEY), PersistentDataType.STRING);

        if (cost <= 0) {
            player.sendMessage(Component.text("このアイテムは購入できません。（価格設定なし）", NamedTextColor.RED));
            return;
        }

        TraderOffer offer = manager.getOfferById(traderid, offerid);
        if (offer == null) {
            player.sendMessage(Component.text("エラー: 指定された商品が見つかりませんでした。", NamedTextColor.RED));
            return;
        }

        if (!econ.has(player, cost)) {
            player.sendMessage(Component.text("残高が不足しています！ 必要額: " + econ.format(cost), NamedTextColor.RED));
            return;
        }

        List<ItemStack> requiredItems = offer.getRequiredItems();
        if (requiredItems != null) {
            for (ItemStack req : requiredItems) {
                if (req == null) continue;
                if (!player.getInventory().containsAtLeast(req, req.getAmount())) {
                    String itemName = req.hasItemMeta() && req.getItemMeta().hasDisplayName()
                            ? PlainTextComponentSerializer.plainText().serialize(req.getItemMeta().displayName())
                            : req.getType().name();
                    player.sendMessage(Component.text("必要なアイテムが不足しています: " + itemName + " ×" + req.getAmount(), NamedTextColor.RED));
                    return;
                }
            }
        }

        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(Component.text("インベントリに空きがありません。", NamedTextColor.RED));
            return;
        }

        ItemStack itemToGive;
        NamespacedKey customIdKey = new NamespacedKey(Deepwither.getInstance(), CUSTOM_ID_KEY);

        if (meta.getPersistentDataContainer().has(customIdKey, PersistentDataType.STRING)) {
            String customId = meta.getPersistentDataContainer().get(customIdKey, PersistentDataType.STRING);
            itemToGive = ItemLoader.loadSingleItem(customId, Deepwither.getInstance().getItemFactory(), new File(Deepwither.getInstance().getDataFolder(), "items"));
        } else {
            Material material = clickedItem.getType();
            if (material != Material.AIR && material.isItem()) {
                itemToGive = clickedItem.clone();
                ItemMeta itemToGiveMeta = itemToGive.getItemMeta();
                if (itemToGiveMeta != null) {
                    itemToGiveMeta.displayName(null);
                    itemToGiveMeta.lore(null);
                    itemToGive.setItemMeta(itemToGiveMeta);
                }
            } else {
                player.sendMessage(Component.text("アイテムの取得に失敗しました。(無効なマテリアル)", NamedTextColor.RED));
                return;
            }
        }

        if (itemToGive == null) {
            player.sendMessage(Component.text("アイテムの取得に失敗しました。", NamedTextColor.RED));
            return;
        }

        econ.withdrawPlayer(player, cost);
        if (requiredItems != null) {
            for (ItemStack req : requiredItems) player.getInventory().removeItem(req);
        }
        player.getInventory().addItem(itemToGive);

        Component itemDisplayName = itemToGive.hasItemMeta() && itemToGive.getItemMeta().hasDisplayName() ? itemToGive.getItemMeta().displayName() : Component.text(itemToGive.getType().name());
        player.sendMessage(Component.text("", NamedTextColor.GREEN).append(itemDisplayName).append(Component.text(" を " + econ.format(cost) + " で購入しました。", NamedTextColor.GREEN)));
    }
}
