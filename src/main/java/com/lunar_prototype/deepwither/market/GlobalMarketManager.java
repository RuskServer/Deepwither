package com.lunar_prototype.deepwither.market;

import com.lunar_prototype.deepwither.DatabaseManager;
import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class GlobalMarketManager implements IManager {

    private final Deepwither plugin;
    private final List<MarketListing> allListings = Collections.synchronizedList(new ArrayList<>());
    private final Map<UUID, Double> earnings = new ConcurrentHashMap<>();
    private final DatabaseManager databaseManager;

    public GlobalMarketManager(Deepwither plugin,DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    @Override
    public void init() throws Exception {
        loadAllData();
        loadEarnings();
    }

    public void listItem(Player seller, ItemStack item, double price, boolean unitSale) {
        MarketListing listing = new MarketListing(seller.getUniqueId(), item.clone(), price, unitSale);
        allListings.add(listing);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> saveListingToDB(listing));
    }


    public boolean buyItem(Player buyer, MarketListing listing) {
        return buyItem(buyer, listing, listing.getItem().getAmount());
    }

    public boolean buyItem(Player buyer, MarketListing listing, int amount) {
        if (!allListings.contains(listing)) {
            buyer.sendMessage(Component.text("このアイテムは既に売り切れています。", NamedTextColor.RED));
            return false;
        }

        if (amount <= 0 || amount > listing.getItem().getAmount()) {
            buyer.sendMessage(Component.text("無効な数量です。", NamedTextColor.RED));
            return false;
        }

        double totalPrice = listing.isUnitSale() ? listing.getPrice() * amount : listing.getPrice();
        var econ = Deepwither.getEconomy();

        if (!econ.has(buyer, totalPrice)) {
            buyer.sendMessage(Component.text("所持金が足りません。", NamedTextColor.RED));
            return false;
        }

        if (buyer.getInventory().firstEmpty() == -1 && !buyer.getInventory().containsAtLeast(listing.getItem(), 1)) {
            buyer.sendMessage(Component.text("インベントリが一杯です。", NamedTextColor.RED));
            return false;
        }

        var res = econ.withdrawPlayer(buyer, totalPrice);
        if (!res.transactionSuccess()) return false;

        ItemStack toGive = listing.getItem().clone();
        toGive.setAmount(amount);
        buyer.getInventory().addItem(toGive);

        if (listing.isUnitSale() && listing.getItem().getAmount() > amount) {
            // 一部購入
            listing.getItem().setAmount(listing.getItem().getAmount() - amount);
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> updateListingInDB(listing));
        } else {
            // 全量購入
            allListings.remove(listing);
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> deleteListingFromDB(listing.getId()));
        }

        addEarnings(listing.getSellerId(), totalPrice);

        buyer.sendMessage(Component.text("購入が完了しました！", NamedTextColor.GREEN));
        playerPurchaseEffect(buyer, listing, amount, totalPrice);

        return true;
    }

    public void cancelListing(Player player, MarketListing listing) {
        if (!listing.getSellerId().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("自分の出品のみ取り消せます。", NamedTextColor.RED));
            return;
        }

        allListings.remove(listing);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> deleteListingFromDB(listing.getId()));

        if (player.getInventory().firstEmpty() != -1) {
            player.getInventory().addItem(listing.getItem());
            player.sendMessage(Component.text("[Market] ", NamedTextColor.AQUA).append(Component.text("出品を取り消し、アイテムを回収しました。", NamedTextColor.GREEN)));
        } else {
            // インベントリが一杯の場合はメールで送るなどの処理が理想だが、現状は足元にドロップ
            player.getWorld().dropItemNaturally(player.getLocation(), listing.getItem());
            player.sendMessage(Component.text("[Market] ", NamedTextColor.AQUA).append(Component.text("出品を取り消しましたが、インベントリが一杯のため足元にドロップしました。", NamedTextColor.YELLOW)));
        }
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_USE, 1f, 1.5f);
    }

    private void playerPurchaseEffect(Player buyer, MarketListing listing, int amount, double price) {
        buyer.playSound(buyer.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        buyer.sendActionBar(Component.text("購入完了: ", NamedTextColor.GREEN)
                .append(Component.text(amount + "個 ", NamedTextColor.WHITE))
                .append(Component.text("-" + String.format("%.1f", price) + " G", NamedTextColor.GOLD)));

        Player seller = Bukkit.getPlayer(listing.getSellerId());
        if (seller != null && seller.isOnline()) {
            seller.sendMessage(Component.text("[Market] ", NamedTextColor.AQUA)
                    .append(Component.text("出品したアイテムが購入されました！ (+", NamedTextColor.WHITE))
                    .append(Component.text(String.format("%.1f", price) + " G", NamedTextColor.GOLD))
                    .append(Component.text(")", NamedTextColor.WHITE)));
            seller.playSound(seller.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.8f);
        }

        if (price >= 5000) {
            Bukkit.broadcast(Component.text("[Market] ", NamedTextColor.AQUA)
                    .append(Component.text(buyer.getName(), NamedTextColor.YELLOW))
                    .append(Component.text(" が ", NamedTextColor.WHITE))
                    .append(Component.text(String.format("%.1f", price) + " G", NamedTextColor.GOLD))
                    .append(Component.text(" の高額取引を行いました！", NamedTextColor.WHITE)));
        }
    }


    public void claimEarnings(Player player) {
        UUID uuid = player.getUniqueId();
        final double[] captured = new double[1];
        earnings.compute(uuid, (k, v) -> {
            captured[0] = (v == null ? 0.0 : v);
            return 0.0;
        });
        double amount = captured[0];

        if (amount <= 0) {
            player.sendMessage(Component.text("[Market] ", NamedTextColor.AQUA).append(Component.text("回収できる売上金はありません。", NamedTextColor.RED)));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "UPDATE market_earnings SET amount = amount - ? WHERE uuid = ?";
            try (java.sql.Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setDouble(1, amount);
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("売上金のDB更新中にエラーが発生しました: " + e.getMessage());
                e.printStackTrace();
            }
        });

        var response = Deepwither.getEconomy().depositPlayer(player, amount);

        if (response.transactionSuccess()) {
            player.sendMessage(Component.text("[Market] ", NamedTextColor.AQUA)
                    .append(Component.text("売上金 ", NamedTextColor.WHITE))
                    .append(Component.text(String.format("%.2f", amount) + " G", NamedTextColor.GOLD))
                    .append(Component.text(" を回収しました！", NamedTextColor.WHITE)));
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        } else {
            // エコノミー処理が失敗した場合、値を元に戻す（DBも戻す必要があるが、簡単のためここではマップのみ）
            earnings.merge(uuid, amount, Double::sum);
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                String sql = "UPDATE market_earnings SET amount = amount + ? WHERE uuid = ?";
                try (java.sql.Connection conn = databaseManager.getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setDouble(1, amount);
                    ps.setString(2, uuid.toString());
                    ps.executeUpdate();
                } catch (SQLException e) { e.printStackTrace(); }
            });
            player.sendMessage(Component.text("[Market] ", NamedTextColor.RED).append(Component.text("入金処理に失敗しました。管理者にお問い合わせください: " + response.errorMessage, NamedTextColor.WHITE)));
        }
    }

    public List<MarketListing> search(String query) {
        String lowerQuery = query.toLowerCase();
        synchronized (allListings) {
            return allListings.stream()
                    .filter(l -> {
                        String itemName = l.getItem().hasItemMeta() && l.getItem().getItemMeta().hasDisplayName()
                                ? l.getItem().getItemMeta().getDisplayName()
                                : l.getItem().getType().toString();
                        return itemName.toLowerCase().contains(lowerQuery);
                    })
                    .collect(Collectors.toList());
        }
    }

    public List<OfflinePlayer> getActiveSellers() {
        long oneMonthAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000L);
        synchronized (allListings) {
            return allListings.stream()
                    .map(MarketListing::getSellerId)
                    .distinct()
                    .map(Bukkit::getOfflinePlayer)
                    .filter(p -> p.getLastPlayed() >= oneMonthAgo || p.isOnline())
                    .collect(Collectors.toList());
        }
    }

    public List<MarketListing> getListingsByPlayer(UUID uuid) {
        synchronized (allListings) {
            return allListings.stream()
                    .filter(l -> l.getSellerId().equals(uuid))
                    .collect(Collectors.toList());
        }
    }

    private void saveListingToDB(MarketListing listing) {
        String sql = "INSERT INTO market_listings (id, seller_uuid, item_stack, price, listed_date, unit_sale) VALUES (?, ?, ?, ?, ?, ?)";
        try (java.sql.Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, listing.getId().toString());
            ps.setString(2, listing.getSellerId().toString());
            ps.setString(3, serializeItem(listing.getItem()));
            ps.setDouble(4, listing.getPrice());
            ps.setLong(5, listing.getListedDate());
            ps.setBoolean(6, listing.isUnitSale());
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void updateListingInDB(MarketListing listing) {
        String sql = "UPDATE market_listings SET item_stack = ? WHERE id = ?";
        try (java.sql.Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, serializeItem(listing.getItem()));
            ps.setString(2, listing.getId().toString());
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }


    private void deleteListingFromDB(UUID listingId) {
        String sql = "DELETE FROM market_listings WHERE id = ?";
        try (java.sql.Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, listingId.toString());
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void addEarnings(UUID sellerId, double amount) {
        earnings.merge(sellerId, amount, Double::sum);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (java.sql.Connection conn = databaseManager.getConnection()) {
                // 存在チェック
                boolean exists = false;
                try (PreparedStatement checkPs = conn.prepareStatement("SELECT 1 FROM market_earnings WHERE uuid = ?")) {
                    checkPs.setString(1, sellerId.toString());
                    try (ResultSet rs = checkPs.executeQuery()) {
                        exists = rs.next();
                    }
                }

                if (exists) {
                    // 累積 UPDATE
                    try (PreparedStatement ps = conn.prepareStatement("UPDATE market_earnings SET amount = amount + ? WHERE uuid = ?")) {
                        ps.setDouble(1, amount);
                        ps.setString(2, sellerId.toString());
                        ps.executeUpdate();
                    }
                } else {
                    // INSERT
                    try (PreparedStatement ps = conn.prepareStatement("INSERT INTO market_earnings (uuid, amount) VALUES (?, ?)")) {
                        ps.setString(1, sellerId.toString());
                        ps.setDouble(2, amount);
                        ps.executeUpdate();
                    } catch (SQLException e) {
                        if (e.getSQLState().startsWith("23")) {
                            // 同時実行時は UPDATE
                            try (PreparedStatement ps = conn.prepareStatement("UPDATE market_earnings SET amount = amount + ? WHERE uuid = ?")) {
                                ps.setDouble(1, amount);
                                ps.setString(2, sellerId.toString());
                                ps.executeUpdate();
                            }
                        } else {
                            throw e;
                        }
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    private void loadAllData() throws SQLException {
        allListings.clear();
        ensureColumnExists("market_listings", "unit_sale", "BOOLEAN DEFAULT FALSE");
        String sql = "SELECT * FROM market_listings";
        try (java.sql.Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                MarketListing listing = new MarketListing(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("seller_uuid")),
                        deserializeItem(rs.getString("item_stack")),
                        rs.getDouble("price"),
                        rs.getLong("listed_date"),
                        rs.getBoolean("unit_sale")
                );
                allListings.add(listing);
            }
        }
    }

    private void ensureColumnExists(String tableName, String columnName, String definition) {
        try (java.sql.Connection conn = databaseManager.getConnection()) {
            var meta = conn.getMetaData();
            var rs = meta.getColumns(null, null, tableName, columnName);
            if (!rs.next()) {
                String sql = "ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("DBカラム確認中にエラーが発生しました (" + columnName + "): " + e.getMessage());
        }
    }


    private void loadEarnings() throws SQLException {
        earnings.clear();
        String sql = "SELECT * FROM market_earnings";
        try (java.sql.Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                earnings.put(UUID.fromString(rs.getString("uuid")), rs.getDouble("amount"));
            }
        }
    }

    private String serializeItem(ItemStack item) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            dataOutput.writeObject(item);
            dataOutput.close();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) { return ""; }
    }

    private ItemStack deserializeItem(String data) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            ItemStack item = (ItemStack) dataInput.readObject();
            dataInput.close();
            return item;
        } catch (Exception e) { return null; }
    }

    public List<MarketListing> getAllListings() { return Collections.unmodifiableList(allListings); }
}
