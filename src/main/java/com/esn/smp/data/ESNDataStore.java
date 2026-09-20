package com.esn.smp.data;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ESNDataStore implements AutoCloseable {
    private final File databaseFile;
    private final long startingBalance;
    private final int maxListingsPerPlayer;
    private final long maxAuctionPrice;
    private Connection connection;

    public ESNDataStore(File dataFolder, long startingBalance, int maxListingsPerPlayer, long maxAuctionPrice) {
        this.databaseFile = new File(dataFolder, "esnsmp.db");
        this.startingBalance = Math.max(0L, startingBalance);
        this.maxListingsPerPlayer = Math.max(1, maxListingsPerPlayer);
        this.maxAuctionPrice = Math.max(1L, maxAuctionPrice);
    }

    public synchronized void initialize() throws Exception {
        File parent = databaseFile.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Could not create plugin data directory.");
        }

        Class.forName("org.sqlite.JDBC");
        connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());

        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=FULL");
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS accounts (
                      uuid TEXT PRIMARY KEY,
                      last_name TEXT NOT NULL,
                      balance INTEGER NOT NULL CHECK(balance >= 0)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS auctions (
                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                      seller_uuid TEXT NOT NULL,
                      seller_name TEXT NOT NULL,
                      price INTEGER NOT NULL CHECK(price > 0),
                      item BLOB NOT NULL,
                      created_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_auctions_seller
                    ON auctions(seller_uuid)
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS deliveries (
                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                      owner_uuid TEXT NOT NULL,
                      token TEXT NOT NULL UNIQUE,
                      item BLOB NOT NULL,
                      reason TEXT NOT NULL,
                      created_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS daily_claims (
                      uuid TEXT PRIMARY KEY,
                      last_claim INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_deliveries_owner
                    ON deliveries(owner_uuid)
                    """);
        }
    }

    public synchronized void touch(Player player) throws SQLException {
        ensureAccount(connection, player.getUniqueId(), player.getName());
    }

    public synchronized long getBalance(Player player) throws SQLException {
        ensureAccount(connection, player.getUniqueId(), player.getName());
        return readBalance(connection, player.getUniqueId());
    }

    public synchronized void deposit(Player player,long amount) throws SQLException {
        if(amount<=0)return; ensureAccount(connection,player.getUniqueId(),player.getName());
        updateBalance(connection,player.getUniqueId(),Math.addExact(readBalance(connection,player.getUniqueId()),amount));
    }
    public synchronized boolean withdraw(Player player,long amount) throws SQLException {
        if(amount<=0)return false; ensureAccount(connection,player.getUniqueId(),player.getName());
        long b=readBalance(connection,player.getUniqueId()); if(b<amount)return false; updateBalance(connection,player.getUniqueId(),b-amount); return true;
    }
    public synchronized long claimDaily(Player player) throws SQLException {
        ensureAccount(connection,player.getUniqueId(),player.getName()); long now=System.currentTimeMillis(),last=0;
        try(PreparedStatement p=connection.prepareStatement("SELECT last_claim FROM daily_claims WHERE uuid=?")){p.setString(1,player.getUniqueId().toString());try(ResultSet r=p.executeQuery()){if(r.next())last=r.getLong(1);}}
        long wait=86400000L-(now-last); if(last>0&&wait>0)return wait;
        begin(); try{long b=readBalance(connection,player.getUniqueId());updateBalance(connection,player.getUniqueId(),Math.addExact(b,250));
          try(PreparedStatement p=connection.prepareStatement("INSERT INTO daily_claims(uuid,last_claim) VALUES(?,?) ON CONFLICT(uuid) DO UPDATE SET last_claim=excluded.last_claim")){p.setString(1,player.getUniqueId().toString());p.setLong(2,now);p.executeUpdate();}commit();return 0;
        }catch(Exception ex){rollbackQuietly();throw ex instanceof SQLException s?s:new SQLException(ex);}finally{restoreAutoCommit();}
    }

    public synchronized boolean transfer(Player from, Player to, long amount) throws SQLException {
        if (amount <= 0) return false;

        begin();
        try {
            ensureAccount(connection, from.getUniqueId(), from.getName());
            ensureAccount(connection, to.getUniqueId(), to.getName());

            long fromBalance = readBalance(connection, from.getUniqueId());
            if (fromBalance < amount) {
                rollback();
                return false;
            }

            updateBalance(connection, from.getUniqueId(), fromBalance - amount);
            long toBalance = readBalance(connection, to.getUniqueId());
            updateBalance(connection, to.getUniqueId(), Math.addExact(toBalance, amount));
            commit();
            return true;
        } catch (Exception ex) {
            rollbackQuietly();
            if (ex instanceof SQLException sql) throw sql;
            throw new SQLException("Transfer failed", ex);
        } finally {
            restoreAutoCommit();
        }
    }

    public synchronized CreateListingResult createListing(Player seller, ItemStack item, long price) throws SQLException {
        if (item == null || item.getType().isAir()) {
            return new CreateListingResult(false, -1L, "You must hold an item to list.");
        }
        if (price <= 0 || price > maxAuctionPrice) {
            return new CreateListingResult(false, -1L, "Price must be between 1 and " + maxAuctionPrice + ".");
        }

        ensureAccount(connection, seller.getUniqueId(), seller.getName());

        try (PreparedStatement count = connection.prepareStatement(
                "SELECT COUNT(*) FROM auctions WHERE seller_uuid = ?")) {
            count.setString(1, seller.getUniqueId().toString());
            try (ResultSet rs = count.executeQuery()) {
                if (rs.next() && rs.getInt(1) >= maxListingsPerPlayer) {
                    return new CreateListingResult(false, -1L,
                            "You already have the maximum of " + maxListingsPerPlayer + " active listings.");
                }
            }
        }

        byte[] bytes = item.serializeAsBytes();
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO auctions(seller_uuid,seller_name,price,item,created_at) VALUES(?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            insert.setString(1, seller.getUniqueId().toString());
            insert.setString(2, seller.getName());
            insert.setLong(3, price);
            insert.setBytes(4, bytes);
            insert.setLong(5, System.currentTimeMillis());
            insert.executeUpdate();

            try (ResultSet keys = insert.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Auction listing did not return an id.");
                }
                return new CreateListingResult(true, keys.getLong(1), "Listed successfully.");
            }
        }
    }

    public synchronized List<AuctionListing> listAuctions(int offset, int limit) throws SQLException {
        int safeOffset = Math.max(0, offset);
        int safeLimit = Math.max(1, Math.min(45, limit));
        List<AuctionListing> listings = new ArrayList<>();

        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT id,seller_uuid,seller_name,price,item,created_at
                FROM auctions
                ORDER BY id DESC
                LIMIT ? OFFSET ?
                """)) {
            ps.setInt(1, safeLimit);
            ps.setInt(2, safeOffset);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    listings.add(readListing(rs));
                }
            }
        }
        return listings;
    }

    public synchronized int countAuctions() throws SQLException {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM auctions")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public synchronized PurchaseResult purchase(Player buyer, long auctionId) throws SQLException {
        begin();
        try {
            AuctionListing listing = findAuctionForUpdate(auctionId);
            if (listing == null) {
                rollback();
                return new PurchaseResult(false, "That listing is no longer available.", null);
            }
            if (listing.sellerUuid().equals(buyer.getUniqueId())) {
                rollback();
                return new PurchaseResult(false, "You cannot buy your own listing.", listing);
            }

            ensureAccount(connection, buyer.getUniqueId(), buyer.getName());
            ensureAccount(connection, listing.sellerUuid(), listing.sellerName());

            long buyerBalance = readBalance(connection, buyer.getUniqueId());
            if (buyerBalance < listing.price()) {
                rollback();
                return new PurchaseResult(false, "You do not have enough ESN Coins.", listing);
            }

            long sellerBalance = readBalance(connection, listing.sellerUuid());
            updateBalance(connection, buyer.getUniqueId(), buyerBalance - listing.price());
            updateBalance(connection, listing.sellerUuid(), Math.addExact(sellerBalance, listing.price()));

            insertDelivery(connection, buyer.getUniqueId(), listing.item(), "Auction purchase");
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM auctions WHERE id = ?")) {
                delete.setLong(1, auctionId);
                if (delete.executeUpdate() != 1) {
                    throw new SQLException("Listing changed during purchase.");
                }
            }

            commit();
            return new PurchaseResult(true,
                    "Purchase complete. Use /ah claim to receive the item.", listing);
        } catch (Exception ex) {
            rollbackQuietly();
            if (ex instanceof SQLException sql) throw sql;
            throw new SQLException("Purchase failed", ex);
        } finally {
            restoreAutoCommit();
        }
    }

    public synchronized CancelResult cancel(Player seller, long auctionId) throws SQLException {
        begin();
        try {
            AuctionListing listing = findAuctionForUpdate(auctionId);
            if (listing == null) {
                rollback();
                return new CancelResult(false, "That listing no longer exists.");
            }
            if (!listing.sellerUuid().equals(seller.getUniqueId())) {
                rollback();
                return new CancelResult(false, "That listing does not belong to you.");
            }

            insertDelivery(connection, seller.getUniqueId(), listing.item(), "Cancelled auction");
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM auctions WHERE id = ?")) {
                delete.setLong(1, auctionId);
                if (delete.executeUpdate() != 1) {
                    throw new SQLException("Listing changed during cancellation.");
                }
            }

            commit();
            return new CancelResult(true, "Listing cancelled. Use /ah claim to receive the item.");
        } catch (Exception ex) {
            rollbackQuietly();
            if (ex instanceof SQLException sql) throw sql;
            throw new SQLException("Cancellation failed", ex);
        } finally {
            restoreAutoCommit();
        }
    }

    public synchronized List<Delivery> getDeliveries(UUID owner, int limit) throws SQLException {
        List<Delivery> deliveries = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT id,token,item,reason,created_at
                FROM deliveries
                WHERE owner_uuid = ?
                ORDER BY id ASC
                LIMIT ?
                """)) {
            ps.setString(1, owner.toString());
            ps.setInt(2, Math.max(1, Math.min(50, limit)));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    deliveries.add(new Delivery(
                            rs.getLong("id"),
                            rs.getString("token"),
                            ItemStack.deserializeBytes(rs.getBytes("item")),
                            rs.getString("reason"),
                            rs.getLong("created_at")
                    ));
                }
            }
        }
        return deliveries;
    }

    public synchronized void deleteDelivery(UUID owner, long deliveryId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM deliveries WHERE id = ? AND owner_uuid = ?")) {
            ps.setLong(1, deliveryId);
            ps.setString(2, owner.toString());
            ps.executeUpdate();
        }
    }

    public synchronized int countDeliveries(UUID owner) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM deliveries WHERE owner_uuid = ?")) {
            ps.setString(1, owner.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public synchronized List<BalanceEntry> topBalances(int limit) throws SQLException {
        List<BalanceEntry> entries = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT last_name,balance
                FROM accounts
                ORDER BY balance DESC, last_name ASC
                LIMIT ?
                """)) {
            ps.setInt(1, Math.max(1, Math.min(20, limit)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(new BalanceEntry(rs.getString("last_name"), rs.getLong("balance")));
                }
            }
        }
        return entries;
    }

    private AuctionListing findAuctionForUpdate(long id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT id,seller_uuid,seller_name,price,item,created_at
                FROM auctions WHERE id = ?
                """)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readListing(rs) : null;
            }
        }
    }

    private AuctionListing readListing(ResultSet rs) throws SQLException {
        return new AuctionListing(
                rs.getLong("id"),
                UUID.fromString(rs.getString("seller_uuid")),
                rs.getString("seller_name"),
                rs.getLong("price"),
                ItemStack.deserializeBytes(rs.getBytes("item")),
                rs.getLong("created_at")
        );
    }

    private void insertDelivery(Connection c, UUID owner, ItemStack item, String reason) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO deliveries(owner_uuid,token,item,reason,created_at)
                VALUES(?,?,?,?,?)
                """)) {
            ps.setString(1, owner.toString());
            ps.setString(2, UUID.randomUUID().toString());
            ps.setBytes(3, item.serializeAsBytes());
            ps.setString(4, reason);
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    private void ensureAccount(Connection c, UUID uuid, String name) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO accounts(uuid,last_name,balance)
                VALUES(?,?,?)
                ON CONFLICT(uuid) DO UPDATE SET last_name=excluded.last_name
                """)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name == null || name.isBlank() ? uuid.toString() : name);
            ps.setLong(3, startingBalance);
            ps.executeUpdate();
        }
    }

    private long readBalance(Connection c, UUID uuid) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT balance FROM accounts WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Account missing for " + uuid);
                return rs.getLong(1);
            }
        }
    }

    private void updateBalance(Connection c, UUID uuid, long balance) throws SQLException {
        if (balance < 0) throw new SQLException("Negative balance rejected.");
        try (PreparedStatement ps = c.prepareStatement("UPDATE accounts SET balance = ? WHERE uuid = ?")) {
            ps.setLong(1, balance);
            ps.setString(2, uuid.toString());
            if (ps.executeUpdate() != 1) throw new SQLException("Could not update account.");
        }
    }

    private void begin() throws SQLException {
        connection.setAutoCommit(false);
    }

    private void commit() throws SQLException {
        connection.commit();
    }

    private void rollback() throws SQLException {
        connection.rollback();
    }

    private void rollbackQuietly() {
        try {
            if (connection != null && !connection.getAutoCommit()) connection.rollback();
        } catch (SQLException ignored) {
        }
    }

    private void restoreAutoCommit() {
        try {
            if (connection != null && !connection.getAutoCommit()) connection.setAutoCommit(true);
        } catch (SQLException ignored) {
        }
    }

    @Override
    public synchronized void close() throws SQLException {
        if (connection != null) {
            connection.close();
            connection = null;
        }
    }

    public record AuctionListing(long id, UUID sellerUuid, String sellerName, long price,
                                 ItemStack item, long createdAt) {
    }

    public record CreateListingResult(boolean success, long id, String message) {
    }

    public record PurchaseResult(boolean success, String message, AuctionListing listing) {
    }

    public record CancelResult(boolean success, String message) {
    }

    public record Delivery(long id, String token, ItemStack item, String reason, long createdAt) {
    }

    public record BalanceEntry(String playerName, long balance) {
    }
}
