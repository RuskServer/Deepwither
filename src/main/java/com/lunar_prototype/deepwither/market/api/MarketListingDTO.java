package com.lunar_prototype.deepwither.market.api;

import com.lunar_prototype.deepwither.market.MarketListing;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class MarketListingDTO {
    private final String id;
    private final String itemName;
    private final String material;
    private final int amount;
    private final double price;
    private final long listedDate;

    public MarketListingDTO(MarketListing listing) {
        this.id = listing.getId().toString();
        ItemStack item = listing.getItem();
        ItemMeta meta = item.getItemMeta();

        // 表示名があれば使用、なければMaterial名
        this.itemName = (meta != null && meta.hasDisplayName())
                ? meta.getDisplayName()
                : item.getType().toString();

        this.material = item.getType().toString();
        this.amount = item.getAmount();
        this.price = listing.getPrice();
        this.listedDate = listing.getListedDate();
    }
}