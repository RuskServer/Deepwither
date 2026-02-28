package com.lunar_prototype.deepwither.modules.economy.trader;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.data.DailyTaskData;
import com.lunar_prototype.deepwither.data.TraderOffer;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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

import java.util.ArrayList;
import java.util.List;

@DependsOn({TraderManager.class, DailyTaskManager.class})
public class TraderGUI implements Listener, IManager {

    private final JavaPlugin plugin;
    private final PurchaseService purchaseService;

    /**
     * Creates a TraderGUI bound to the given plugin and initializes its purchase handling.
     *
     * @param plugin the hosting JavaPlugin instance used for registering listeners and interacting with the server
     */
    public TraderGUI(JavaPlugin plugin) {
        this.plugin = plugin;
        this.purchaseService = new PurchaseService();
    }

    /**
     * Registers this object as an event listener with the Bukkit plugin manager using the configured plugin.
     */
    @Override
    public void init() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

    private static final String SELL_ID_KEY = "sell_price";
    private static final String OFFER_ID_KEY = "offer_id";
    private static final String CUSTOM_ID_KEY = "custom_id";
    private static final String TRADER_ID_KEY = "trader_id";

    /**
     * Opens a purchase GUI for the specified trader and presents the trader's offers to the player.
     *
     * <p>The GUI lists available offers with price, required items, and required credit; offers the
     * player cannot access are shown as locked (non-purchasable) and visually replaced with a gray pane.
     * A sell button and a daily-task button are added to the GUI.</p>
     *
     * @param player the player who will see the GUI
     * @param traderId identifier of the trader whose offers are displayed
     * @param playerCredit the player's current credit used to determine offer access
     * @param manager manager used to retrieve offers and access checks for the trader
     */
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
                    lore.add(Component.text("信用度が不足しています。", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
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

    /**
     * Handles clicks inside the trader "buy" inventory: cancels interactions, routes clicks on the sell button to the sell GUI, processes the daily-task button (start/complete/notify based on task state), and delegates purchases to the purchase handler.
     *
     * The handler ignores non-player clicks and clicks on empty slots or outside inventories.
     *
     * @param e the inventory click event for the buy GUI
     */
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

            if (!e.getClickedInventory().equals(e.getView().getTopInventory())) return;
            purchaseService.processPurchase(player, e.getCurrentItem(), Deepwither.getInstance().getTraderManager());
        }
    }
}
