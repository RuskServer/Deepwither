package com.lunar_prototype.deepwither.modules.economy.trader;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.data.TraderOffer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * トレーダーとの購入取引を実行するサービス
 */
public class PurchaseService {

    private static final String OFFER_ID_KEY = "offer_id";
    private static final String CUSTOM_ID_KEY = "custom_id";
    private static final String TRADER_ID_KEY = "trader_id";
    private static final String SELL_ID_KEY = "sell_price";

    /**
     * プレイヤーの購入リクエストを処理します。
     *
     * @param player      購入を試みるプレイヤー
     * @param clickedItem GUI でクリックされたアイテム（取引情報が PDC に格納されている）
     * @param manager     トレーダー情報を管理する TraderManager
     */
    public void processPurchase(Player player, ItemStack clickedItem, TraderManager manager) {
        Economy econ = Deepwither.getEconomy();
        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null) {
            player.sendMessage(Component.text("アイテムメタデータがありません。", NamedTextColor.RED));
            return;
        }

        NamespacedKey sellIdKey = new NamespacedKey(Deepwither.getInstance(), SELL_ID_KEY);
        int cost = meta.getPersistentDataContainer().getOrDefault(sellIdKey, PersistentDataType.INTEGER, 0);

        String traderId = meta.getPersistentDataContainer().get(new NamespacedKey(Deepwither.getInstance(), TRADER_ID_KEY), PersistentDataType.STRING);
        String offerId = meta.getPersistentDataContainer().get(new NamespacedKey(Deepwither.getInstance(), OFFER_ID_KEY), PersistentDataType.STRING);

        if (cost <= 0) {
            player.sendMessage(Component.text("このアイテムは購入できません。（価格設定なし）", NamedTextColor.RED));
            return;
        }

        TraderOffer offer = manager.getOfferById(traderId, offerId);
        if (offer == null) {
            player.sendMessage(Component.text("エラー: 指定された商品が見つかりませんでした。", NamedTextColor.RED));
            return;
        }

        // 資金チェック
        if (!econ.has(player, cost)) {
            player.sendMessage(Component.text("残高が不足しています！ 必要額: " + econ.format(cost), NamedTextColor.RED));
            return;
        }

        // 必要アイテムチェック
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

        // インベントリ空き容量チェック
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(Component.text("インベントリに空きがありません。", NamedTextColor.RED));
            return;
        }

        // アイテム付与
        ItemStack itemToGive = resolveItemToGive(clickedItem, meta);
        if (itemToGive == null) {
            player.sendMessage(Component.text("アイテムの取得に失敗しました。", NamedTextColor.RED));
            return;
        }

        // 取引完了
        econ.withdrawPlayer(player, cost);
        if (requiredItems != null) {
            for (ItemStack req : requiredItems) player.getInventory().removeItem(req);
        }
        player.getInventory().addItem(itemToGive);

        Component itemDisplayName = itemToGive.hasItemMeta() && itemToGive.getItemMeta().hasDisplayName()
                ? itemToGive.getItemMeta().displayName()
                : Component.text(itemToGive.getType().name());
        player.sendMessage(Component.text("", NamedTextColor.GREEN)
                .append(itemDisplayName)
                .append(Component.text(" を " + econ.format(cost) + " で購入しました。", NamedTextColor.GREEN)));
    }

    private ItemStack resolveItemToGive(ItemStack clickedItem, ItemMeta meta) {
        NamespacedKey customIdKey = new NamespacedKey(Deepwither.getInstance(), CUSTOM_ID_KEY);
        if (meta.getPersistentDataContainer().has(customIdKey, PersistentDataType.STRING)) {
            String customId = meta.getPersistentDataContainer().get(customIdKey, PersistentDataType.STRING);
            return Deepwither.getInstance().getItemFactoryAPI().getItem(customId);
        }

        Material material = clickedItem.getType();
        if (material != Material.AIR && material.isItem()) {
            ItemStack item = clickedItem.clone();
            ItemMeta itemMeta = item.getItemMeta();
            if (itemMeta != null) {
                itemMeta.displayName(null);
                itemMeta.lore(null);
                item.setItemMeta(itemMeta);
            }
            return item;
        }
        return null;
    }
}
