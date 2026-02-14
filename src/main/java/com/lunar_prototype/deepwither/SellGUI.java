package com.lunar_prototype.deepwither;

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

    public static final Component SELL_GUI_TITLE = Component.text("[売却] ", NamedTextColor.DARK_GRAY).append(Component.text("総合売却所", NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false);
    private static final String CUSTOM_ID_KEY = "custom_id";

    private static final Map<StatType, Double> FLAT_PRICE_MULTIPLIERS = new EnumMap<>(StatType.class);
    private static final Map<StatType, Double> PERCENT_PRICE_MULTIPLIERS = new EnumMap<>(StatType.class);

    static {
        FLAT_PRICE_MULTIPLIERS.put(StatType.ATTACK_DAMAGE, 20.0);
        FLAT_PRICE_MULTIPLIERS.put(StatType.DEFENSE, 20.0);
        FLAT_PRICE_MULTIPLIERS.put(StatType.MAX_HEALTH, 20.0);
        FLAT_PRICE_MULTIPLIERS.put(StatType.PROJECTILE_DAMAGE, 20.0);
        FLAT_PRICE_MULTIPLIERS.put(StatType.MAGIC_DAMAGE, 20.0);
        FLAT_PRICE_MULTIPLIERS.put(StatType.MAGIC_RESIST, 40.0);
        FLAT_PRICE_MULTIPLIERS.put(StatType.MAGIC_PENETRATION, 50.0);

        PERCENT_PRICE_MULTIPLIERS.put(StatType.MAX_HEALTH, 100.0);
        PERCENT_PRICE_MULTIPLIERS.put(StatType.CRIT_DAMAGE, 75.0);
    }

    public static void openSellGUI(Player player, TraderManager manager) {
        Inventory gui = Bukkit.createInventory(player, 27, SELL_GUI_TITLE);

        ItemStack filler = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" ").decoration(TextDecoration.ITALIC, false));
        ItemStack backButton = createGuiItem(Material.ARROW, Component.text("<< 戻る", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));

        for (int i = 0; i < 9; i++) {
            gui.setItem(i, filler);
            gui.setItem(26 - i, filler);
        }

        gui.setItem(22, backButton);
        player.openInventory(gui);
    }

    private static ItemStack createGuiItem(Material material, Component name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (e.getClickedInventory() == null) return;

        if (!e.getView().title().equals(SELL_GUI_TITLE)) return;

        TraderManager manager = Deepwither.getInstance().getTraderManager();
        Economy econ = Deepwither.getEconomy();

        if (e.getClickedInventory().equals(e.getView().getTopInventory())) {
            if (e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.ARROW) {
                e.setCancelled(true);
                player.closeInventory();
                player.sendMessage(Component.text("取引を終了しました。", NamedTextColor.YELLOW));
                return;
            }

            if (e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.GRAY_STAINED_GLASS_PANE) {
                e.setCancelled(true);
                return;
            }
        }

        if (e.getClickedInventory().equals(e.getView().getBottomInventory()) && e.isShiftClick()) {
            ItemStack itemToSell = e.getCurrentItem();
            if (itemToSell == null || itemToSell.getType() == Material.AIR) return;
            e.setCancelled(true);
            processSell(player, itemToSell, manager, econ, e);
            return;
        }

        if (e.getClickedInventory().equals(e.getView().getTopInventory()) &&
                e.getCursor() != null && e.getCursor().getType() != Material.AIR) {
            if (e.getSlot() > 8 && e.getSlot() < 18) {
                e.setCancelled(true);
                ItemStack itemToSell = e.getCursor().clone();
                processSell(player, itemToSell, manager, econ, e);
            }
        }
    }

    private void processSell(Player player, ItemStack itemToSell, TraderManager manager, Economy econ, InventoryClickEvent e) {
        int amount = itemToSell.getAmount();
        String itemId = getItemId(itemToSell);
        int pricePerItem = manager.getSellPrice(itemId);

        if (pricePerItem > 0) {
            int totalCost = pricePerItem * amount;
            econ.depositPlayer(player, totalCost);

            if (e.isShiftClick()) e.setCurrentItem(null);
            else e.getCursor().setAmount(0);

            Component itemDisplayName = itemToSell.hasItemMeta() && itemToSell.getItemMeta().hasDisplayName() ? itemToSell.getItemMeta().displayName() : Component.text(itemToSell.getType().name());
            player.sendMessage(Component.text("", NamedTextColor.GREEN)
                    .append(itemDisplayName)
                    .append(Component.text(" x" + amount + " を売却し、" + econ.format(totalCost) + " を獲得しました。", NamedTextColor.GREEN)));

        } else {
            pricePerItem = calculatePriceByStats(itemToSell);
            if (pricePerItem > 0) {
                int totalCost = pricePerItem * amount;
                econ.depositPlayer(player, totalCost);

                if (e.isShiftClick()) e.setCurrentItem(null);
                else e.getCursor().setAmount(0);

                Component itemDisplayName = itemToSell.hasItemMeta() && itemToSell.getItemMeta().hasDisplayName() ? itemToSell.getItemMeta().displayName() : Component.text(itemToSell.getType().name());
                player.sendMessage(Component.text("", NamedTextColor.GREEN)
                        .append(itemDisplayName)
                        .append(Component.text(" x" + amount + " を ", NamedTextColor.GREEN))
                        .append(Component.text("ステータス評価", NamedTextColor.YELLOW))
                        .append(Component.text("により売却し、" + econ.format(totalCost) + " を獲得しました。", NamedTextColor.GREEN)));
            } else {
                player.sendMessage(Component.text("このアイテムは売却できません。", NamedTextColor.RED));
            }
        }
    }

    private String getItemId(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return "";
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item.getType().name();
        
        NamespacedKey key = new NamespacedKey(Deepwither.getInstance(), CUSTOM_ID_KEY);
        if (meta.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
            return meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        }
        return item.getType().name();
    }

    private int calculatePriceByStats(ItemStack item) {
        final StatMap stats = StatManager.readStatsFromItem(item);
        double totalValue = 0;
        for (StatType type : StatType.values()) {
            double flatValue = stats.getFlat(type);
            double percentValue = stats.getPercent(type);
            if (flatValue > 0) totalValue += flatValue * FLAT_PRICE_MULTIPLIERS.getOrDefault(type, 0.0);
            if (percentValue > 0) totalValue += percentValue * PERCENT_PRICE_MULTIPLIERS.getOrDefault(type, 0.0);
        }
        return Math.max(1, (int) Math.round(totalValue));
    }
}
