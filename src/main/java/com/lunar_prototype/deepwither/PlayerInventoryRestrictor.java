package com.lunar_prototype.deepwither;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static com.lunar_prototype.deepwither.util.InventoryHelper.*;

public class PlayerInventoryRestrictor implements Listener {
    private static final List<InventoryStrategy> WEAPON = List.of(MERGE_MAINHAND, MERGE_OFFHAND, MERGE_STORAGE, PLACE_STORAGE);
    private static final List<InventoryStrategy> REVERSE = List.of(MERGE_MAINHAND, MERGE_OFFHAND, MERGE_STORAGE, MERGE_HOTBAR, PLACE_STORAGE, PLACE_HOTBAR);
    private static final List<InventoryStrategy> QUICKMOVE = List.of(MERGE_HOTBAR, PLACE_HOTBAR);
    private static final List<InventoryStrategy> CANCEL = List.of();

    private static final Component MULTIPLE_WEAPONS = Component.translatable("message.deepwither.restrictor.multiple_weapons", "ホットバーに武器は1つしか置けません。", TextColor.color(0xFF5555));
    private static final Component OVERFLOW_WEAPONS = Component.translatable("message.deepwither.restrictor.overflow_weapons", "これ以上持てない！", TextColor.color(0xFF5555));

    private final PlayerSettingsManager playerSettingsManager;

    public PlayerInventoryRestrictor(@NotNull PlayerSettingsManager playerSettingsManager) {
        this.playerSettingsManager = playerSettingsManager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(@NotNull InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        switch (event.getAction()) {
            case PLACE_ALL, PLACE_ONE -> {
                var clickedSlot = event.getSlot();

                if (event.getClickedInventory() instanceof PlayerInventory
                    && HOTBAR_HEAD <= clickedSlot && clickedSlot <= HOTBAR_TAIL
                    && isWeapon(event.getCursor())
                    && !isWeapon(event.getCurrentItem())
                    && hasWeaponInHotbar(player.getInventory())
                ) {
                    player.sendMessage(MULTIPLE_WEAPONS);
                    event.setCancelled(true);
                }
            }
            case MOVE_TO_OTHER_INVENTORY -> {
                var source = event.getCurrentItem();
                if (source == null) return;

                if (event.getSlotType() == InventoryType.SlotType.RESULT && isWeapon(source)) {
                    // fixme: クラフト時、ホットバーに2本目の武器を入れれるバグがある
                    // 暫定的な対処: シフトクリックでクラフトできないように
                    event.setCancelled(true);
                    return;
                }

                var clicked = event.getClickedInventory();
                if (clicked == null) return;

                var inventory = player.getInventory();

                // いまのところgui以外の外部インベントリがなさそう
                // プレイヤーのインベントリ内のみ処理
                if (clicked.getType() == InventoryType.PLAYER && event.getView().getTopInventory().getType() == InventoryType.CRAFTING) {
                    var slot = event.getSlot();

                    if (slot < HOTBAR_HEAD || HOTBAR_TAIL < slot) {
                        var isWeapon = isWeapon(source);
                        var hasWeaponInHotbar = hasWeaponInHotbar(inventory);

                        applyStrategy(isWeapon && hasWeaponInHotbar ? CANCEL : QUICKMOVE, inventory, source);

                        if (isWeapon && !source.isEmpty() && hasWeaponInHotbar) {
                            player.sendMessage(MULTIPLE_WEAPONS);
                        }

                        event.setCancelled(true);
                    }
                }
            }
            case HOTBAR_SWAP ->  {
                var slot = event.getSlot();
                var inventory = player.getInventory();
                var button = event.getHotbarButton();
                if (button == -1) {
                    if (event.getClickedInventory() instanceof PlayerInventory && HOTBAR_HEAD <= slot && slot <= HOTBAR_TAIL
                        && isWeapon(inventory.getItemInOffHand())
                        && !isWeapon(inventory.getItemInMainHand())
                        && hasWeaponInHotbar(inventory)
                    ) {
                        player.sendMessage(MULTIPLE_WEAPONS);
                        event.setCancelled(true);
                    }
                } else if (event.getClickedInventory() instanceof PlayerInventory && slot != button && hasWeaponInHotbar(inventory)
                    && (HOTBAR_HEAD <= slot && slot <= HOTBAR_TAIL && isWeapon(inventory.getItem(button)) && !isWeapon(inventory.getItem(slot))
                    || STORAGE_HEAD <= slot && slot <= STORAGE_TAIL && !isWeapon(inventory.getItem(button)) && isWeapon(inventory.getItem(slot)))
                ) {
                    player.sendMessage(MULTIPLE_WEAPONS);
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwap(@NotNull PlayerSwapHandItemsEvent event) {
        var player = event.getPlayer();
        if (isWeapon(event.getMainHandItem()) && hasWeaponInHotbar(player.getInventory()) && !isWeapon(event.getOffHandItem())) {
            player.sendMessage(MULTIPLE_WEAPONS);
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(@NotNull InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && isWeapon(event.getOldCursor()) && hasWeaponInHotbar(player.getInventory())) {
            var view = event.getView();

            var rawSlots = new HashSet<>(event.getRawSlots());
            if (rawSlots.removeIf(raw -> {
                var inv = view.getInventory(raw);

                if (inv != null && inv.getType() == InventoryType.PLAYER) {
                    var slot = view.convertSlot(raw);
                    return HOTBAR_HEAD <= slot && slot <= HOTBAR_TAIL;
                }

                return false;
            })) {
                player.sendMessage(MULTIPLE_WEAPONS);
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClose(@NotNull InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        var view = event.getView();
        var source = view.getCursor();
        var inventory = player.getInventory();

        applyStrategy(isWeapon(source) ? hasWeaponInHotbar(inventory) ? WEAPON : VANILLA : REVERSE, inventory, source);

        if (!source.isEmpty()) {
            player.sendMessage(OVERFLOW_WEAPONS);
            player.getWorld().dropItemNaturally(player.getLocation(), source);
            view.setCursor(null);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickup(@NotNull EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        event.setCancelled(true);

        var picked = event.getItem();
        var source = picked.getItemStack().add(event.getRemaining());
        var cloned = source.clone();
        var amount = source.getAmount();
        var inventory = player.getInventory();

        applyStrategy(isWeapon(source) ? hasWeaponInHotbar(inventory) ? WEAPON : VANILLA : REVERSE, inventory, source);

        var newAmount = source.getAmount();
        if (newAmount < amount) {
            sendPickupMessage(player, cloned, amount - newAmount);
            playPickupSound(player);

            if (source.isEmpty()) {
                picked.remove();
            } else {
                picked.setItemStack(source);
            }
        }
    }

    private static boolean isWeapon(@Nullable ItemStack itemStack) {
        if (itemStack == null || isScrollItem(itemStack)) return false;

        var material = itemStack.getType();

        return Tag.ITEMS_SWORDS.isTagged(material)
            || Tag.ITEMS_AXES.isTagged(material)
            || material == Material.BOW
            || material == Material.MACE
            || material == Material.TRIDENT
            || material == Material.CROSSBOW
            || material == Material.STICK
            || material == Material.FEATHER;
    }

    private static boolean hasWeaponInHotbar(@NotNull Inventory i) {
        return isWeapon(i.getItem(0)) || isWeapon(i.getItem(1)) || isWeapon(i.getItem(2))
            || isWeapon(i.getItem(3)) || isWeapon(i.getItem(4)) || isWeapon(i.getItem(5))
            || isWeapon(i.getItem(6)) || isWeapon(i.getItem(7)) || isWeapon(i.getItem(8));
    }

    private static boolean isScrollItem(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() != Material.STICK) return false;

        var lore = itemStack.lore();
        if (lore == null || lore.isEmpty()) return false;

        var serializer = PlainTextComponentSerializer.plainText();

        for (var line : lore) {
            if (serializer.serialize(line).contains("カテゴリ:スクロール")) {
                return true;
            }
        }

        return false;
    }

    private void sendPickupMessage(@NotNull Player player, @NotNull ItemStack itemStack, int amount) {
        if (!playerSettingsManager.isEnabled(player, PlayerSettingsManager.SettingType.SHOW_PICKUP_LOG)) {
            return;
        }

        // レアリティフィルター確認
        if (!shouldShowPickupMessage(itemStack, player)) {
            return;
        }

        player.sendMessage(Component.text()
            .append(Component.text("[+] ").color(TextColor.color(Color.GRAY.asRGB())))
            .append(Optional.ofNullable(itemStack.getItemMeta())
                .map(ItemMeta::displayName)
                .orElse(Component.translatable(itemStack)))
            .append(Component.text(" x").color(TextColor.color(Color.WHITE.asRGB())))
            .append(Component.text(amount))
        );
    }

    private boolean shouldShowPickupMessage(@NotNull ItemStack itemStack, @NotNull Player player) {
        if (!itemStack.hasItemMeta()) {
            return true;
        }

        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return true;
        }

        String itemRarity = meta.getPersistentDataContainer().get(ItemFactory.RARITY_KEY, PersistentDataType.STRING);
        if (itemRarity == null) {
            return true;
        }

        String filterRarity = playerSettingsManager.getRarityFilter(player);
        return isRarityGreaterOrEqual(itemRarity, filterRarity);
    }

    private boolean isRarityGreaterOrEqual(@NotNull String itemRarity, @NotNull String filterRarity) {
        String[] rarityOrder = {"&f&lコモン", "&a&lアンコモン", "&b&lレア", "&d&lエピック", "&6&lレジェンダリー"};

        int itemRarityIndex = -1;
        int filterRarityIndex = -1;

        for (int i = 0; i < rarityOrder.length; i++) {
            if (rarityOrder[i].equals(itemRarity)) {
                itemRarityIndex = i;
            }
            if (rarityOrder[i].equals(filterRarity)) {
                filterRarityIndex = i;
            }
        }

        // itemRarityIndexがfilterRarityIndexより大きければ、より高いレアリティなので表示する
        return itemRarityIndex > filterRarityIndex;
    }
}
