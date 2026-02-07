package com.lunar_prototype.deepwither.market;

import com.lunar_prototype.deepwither.DatabaseManager;
import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.util.IManager;
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
import java.util.stream.Collectors;

public class GlobalMarketManager implements IManager {

    private final Deepwither plugin;
    private final List<MarketListing> allListings = new ArrayList<>();
    private final Map<UUID, Double> earnings = new HashMap<>();
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

    // --- コア機能 (DB連携) ---

    public void listItem(Player seller, ItemStack item, double price) {
        MarketListing listing = new MarketListing(seller.getUniqueId(), item.clone(), price);
        allListings.add(listing);

        // 非同期でDBに保存 (ラグ防止)
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> saveListingToDB(listing));
    }

    public boolean buyItem(Player buyer, MarketListing listing) {
        if (!allListings.contains(listing)) {
            buyer.sendMessage("§cこのアイテムは既に売り切れています。");
            return false;
        }

        double price = listing.getPrice();
        var econ = Deepwither.getEconomy();

        if (!econ.has(buyer, price)) {
            buyer.sendMessage("§c所持金が足りません。");
            return false;
        }

        if (buyer.getInventory().firstEmpty() == -1) {
            buyer.sendMessage("§cインベントリが一杯です。");
            return false;
        }

        // 支払い処理
        var res = econ.withdrawPlayer(buyer, price);
        if (!res.transactionSuccess()) return false;

        buyer.getInventory().addItem(listing.getItem().clone());

        // キャッシュとDBから削除
        allListings.remove(listing);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> deleteListingFromDB(listing.getId()));

        // 売上金処理
        addEarnings(listing.getSellerId(), price);

        buyer.sendMessage("§a購入が完了しました！");
        return true;
    }

    /**
     * プレイヤーの累積売上金を回収し、Vault口座に振り込みます。
     * * @param player 回収を行うプレイヤー
     */
    public void claimEarnings(Player player) {
        UUID uuid = player.getUniqueId();
        double amount = earnings.getOrDefault(uuid, 0.0);

        if (amount <= 0) {
            player.sendMessage("§c[Market] 回収できる売上金はありません。");
            return;
        }

        // 1. メモリ上のキャッシュをリセット (再入金防止のため先に処理)
        earnings.put(uuid, 0.0);

        // 2. データベースの更新 (非同期)
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "UPDATE market_earnings SET amount = 0 WHERE uuid = ?";
            try (java.sql.Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("売上金のDB更新中にエラーが発生しました: " + e.getMessage());
                e.printStackTrace();
            }
        });

        // 3. Vaultでプレイヤーの口座に入金
        var response = Deepwither.getEconomy().depositPlayer(player, amount);

        if (response.transactionSuccess()) {
            player.sendMessage("§a[Market] 売上金 " + String.format("%.2f", amount) + " G を回収しました！");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        } else {
            // 万が一Vault入金に失敗した場合のロールバック（簡易的）
            earnings.put(uuid, amount);
            player.sendMessage("§c[Market] 入金処理に失敗しました。管理者にお問い合わせください: " + response.errorMessage);
        }
    }

    // --- データベース操作 (SQLite) ---

    /**
     * 部分一致によるアイテム検索
     */
    public List<MarketListing> search(String query) {
        String lowerQuery = query.toLowerCase();
        return allListings.stream()
                .filter(l -> {
                    String itemName = l.getItem().hasItemMeta() && l.getItem().getItemMeta().hasDisplayName()
                            ? l.getItem().getItemMeta().getDisplayName()
                            : l.getItem().getType().toString();
                    return itemName.toLowerCase().contains(lowerQuery);
                })
                .collect(Collectors.toList());
    }

    /**
     * 最近ログインしたアクティブな出品者を取得
     */
    public List<OfflinePlayer> getActiveSellers() {
        long oneMonthAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000L);

        return allListings.stream()
                .map(MarketListing::getSellerId)
                .distinct()
                .map(Bukkit::getOfflinePlayer)
                .filter(p -> p.getLastPlayed() >= oneMonthAgo || p.isOnline())
                .collect(Collectors.toList());
    }

    public List<MarketListing> getListingsByPlayer(UUID uuid) {
        return allListings.stream()
                .filter(l -> l.getSellerId().equals(uuid))
                .collect(Collectors.toList());
    }

    private void saveListingToDB(MarketListing listing) {
        String sql = "INSERT INTO market_listings (id, seller_uuid, item_stack, price, listed_date) VALUES (?, ?, ?, ?, ?)";
        try (java.sql.Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, listing.getId().toString());
            ps.setString(2, listing.getSellerId().toString());
            ps.setString(3, serializeItem(listing.getItem())); // Base64シリアライズ
            ps.setDouble(4, listing.getPrice());
            ps.setLong(5, listing.getListedDate());
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
        double newTotal = earnings.getOrDefault(sellerId, 0.0) + amount;
        earnings.put(sellerId, newTotal);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "INSERT INTO market_earnings (uuid, amount) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET amount = excluded.amount";
            try (java.sql.Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, sellerId.toString());
                ps.setDouble(2, newTotal);
                ps.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); }
        });
    }

    private void loadAllData() throws SQLException {
        allListings.clear();
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
                        rs.getLong("listed_date")
                );
                allListings.add(listing);
            }
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

    // --- アイテムのシリアライズ (SQLite保存用) ---

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

    // --- 以下、検索・Getter等は変更なし ---
    public List<MarketListing> getAllListings() { return Collections.unmodifiableList(allListings); }
}