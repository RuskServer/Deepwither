package com.lunar_prototype.deepwither.market;

import org.bukkit.inventory.ItemStack;
import java.util.UUID;

public class MarketListing {
    private final UUID id;
    private final UUID sellerId;
    private final ItemStack item;
    private final double price;
    private final long listedDate;
    private final boolean unitSale;

    public MarketListing(UUID sellerId, ItemStack item, double price, boolean unitSale) {
        this.id = UUID.randomUUID();
        this.sellerId = sellerId;
        this.item = item;
        this.price = price;
        this.listedDate = System.currentTimeMillis();
        this.unitSale = unitSale;
    }

    // Configからの読み込み用コンストラクタ
    public MarketListing(UUID id, UUID sellerId, ItemStack item, double price, long listedDate, boolean unitSale) {
        this.id = id;
        this.sellerId = sellerId;
        this.item = item;
        this.price = price;
        this.listedDate = listedDate;
        this.unitSale = unitSale;
    }

    public UUID getId() { return id; }
    public UUID getSellerId() { return sellerId; }
    public ItemStack getItem() { return item; }
    public double getPrice() { return price; }
    public long getListedDate() { return listedDate; }
    public boolean isUnitSale() { return unitSale; }
}