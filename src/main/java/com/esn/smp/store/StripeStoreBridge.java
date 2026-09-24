package com.esn.smp.store;

import com.esn.smp.ESNSMPPlugin;
import com.esn.smp.gameplay.ESNItemsCommand;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class StripeStoreBridge implements Listener, CommandExecutor, AutoCloseable {
    private static final Pattern MC_NAME = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    private static final String STRIPE_SESSIONS = "https://api.stripe.com/v1/checkout/sessions";
    private static final long POLL_SECONDS = 30L;

    private final ESNSMPPlugin plugin;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();
    private final Object dbLock = new Object();
    private final Set<String> warnedSessions = ConcurrentHashMap.newKeySet();

    private Connection db;
    private BukkitTask pollTask;
    private volatile boolean running;
    private volatile long lastSuccessfulPoll;
    private volatile String lastError = "";

    public StripeStoreBridge(ESNSMPPlugin plugin) throws Exception {
        this.plugin = plugin;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        initializeDatabase();
        ensureSecretKeyFile();
    }

    public static void removeLegacyStripeConfigBlock(File configFile) {
        if (configFile == null || !configFile.isFile()) return;
        try {
            List<String> lines = Files.readAllLines(configFile.toPath(), StandardCharsets.UTF_8);
            List<String> out = new ArrayList<>();
            boolean skipping = false;
            boolean changed = false;
            for (String line : lines) {
                String t = line.trim();
                if (!skipping && t.startsWith("stripe-store:")) {
                    skipping = true;
                    changed = true;
                    continue;
                }
                if (skipping) {
                    if (t.isBlank()
                            || t.startsWith("enabled:")
                            || t.startsWith("secret-key:")
                            || t.startsWith("poll-interval-seconds:")
                            || t.startsWith("username-field-key:")
                            || t.startsWith("username-field-label:")
                            || t.startsWith("activation-time:")
                            || t.startsWith("products:")
                            || t.startsWith("#")) {
                        changed = true;
                        continue;
                    }
                    skipping = false;
                }
                out.add(line);
            }
            if (changed) {
                Files.write(configFile.toPath(), out, StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
        }
    }

    private void initializeDatabase() throws Exception {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            throw new IllegalStateException("Could not create ESNSMP data folder");
        }
        File file = new File(plugin.getDataFolder(), "stripe-store.db");
        db = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        synchronized (dbLock) {
            try (Statement s = db.createStatement()) {
                s.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS stripe_store_orders (
                        session_id TEXT PRIMARY KEY,
                        player_name TEXT NOT NULL,
                        payment_link TEXT NOT NULL,
                        item_id TEXT NOT NULL,
                        amount INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        delivered_at INTEGER,
                        last_error TEXT
                    )
                    """);
                s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_stripe_store_pending_player ON stripe_store_orders(status, player_name)");
                s.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS stripe_store_meta (
                        meta_key TEXT PRIMARY KEY,
                        meta_value TEXT NOT NULL
                    )
                    """);
                s.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS stripe_store_products (
                        product_key TEXT PRIMARY KEY,
                        item_id TEXT NOT NULL,
                        amount INTEGER NOT NULL
                    )
                    """);
            }
        }
    }

    private File secretKeyFile() {
        return new File(plugin.getDataFolder(), "stripe-key.txt");
    }

    private void ensureSecretKeyFile() {
        File file = secretKeyFile();
        if (file.exists()) return;
        try {
            Files.writeString(
                    file.toPath(),
                    "PASTE_STRIPE_SECRET_KEY_HERE\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW
            );
            plugin.getLogger().info("[ESN Store] Created plugins/ESNSMP/stripe-key.txt.");
        } catch (Exception ex) {
            plugin.getLogger().warning("[ESN Store] Could not create stripe-key.txt: " + ex.getMessage());
        }
    }

    private boolean isStripeSecret(String value) {
        if (value == null) return false;
        String key = value.trim();
        return key.startsWith("sk_test_") || key.startsWith("sk_live_");
    }

    private String secretKey() {
        String env = System.getenv("STRIPE_SECRET_KEY");
        if (isStripeSecret(env)) return env.trim();

        File file = secretKeyFile();
        if (file.isFile()) {
            try {
                String value = Files.readString(file.toPath(), StandardCharsets.UTF_8).trim();
                if (isStripeSecret(value)) return value;
            } catch (Exception ex) {
                lastError = "Could not read stripe-key.txt: " + ex.getMessage();
            }
        }
        return "";
    }

    public void start() {
        String secret = secretKey();
        if (secret.isBlank()) {
            lastError = "Stripe secret key is missing";
            plugin.getLogger().warning("[ESN Store] Paste ONLY your Stripe secret key into plugins/ESNSMP/stripe-key.txt, save it, then restart the server.");
            return;
        }

        long activation = getMetaLong("activation_time", 0L);
        if (activation <= 0L) {
            activation = System.currentTimeMillis() / 1000L;
            setMeta("activation_time", Long.toString(activation));
            plugin.getLogger().info("[ESN Store] Stripe activation time initialized.");
        }

        running = true;
        pollTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::pollSafely, 40L, POLL_SECONDS * 20L);
        plugin.getLogger().info("[ESN Store] Stripe bridge started. Poll interval: " + POLL_SECONDS + "s.");
    }

    private void pollSafely() {
        if (!running) return;
        try {
            pollStripe();
            lastSuccessfulPoll = System.currentTimeMillis();
            lastError = "";
        } catch (Exception ex) {
            lastError = ex.getClass().getSimpleName() + ": " + String.valueOf(ex.getMessage());
            plugin.getLogger().warning("[ESN Store] Stripe poll failed safely: " + lastError);
        }
    }

    private void pollStripe() throws Exception {
        String secret = secretKey();
        if (secret.isBlank()) throw new IllegalStateException("Stripe secret key is missing");

        long activation = getMetaLong("activation_time", 0L);
        String startingAfter = null;

        for (int page = 0; page < 10; page++) {
            StringBuilder url = new StringBuilder(STRIPE_SESSIONS)
                    .append("?limit=100&status=complete&created%5Bgte%5D=")
                    .append(activation);
            if (startingAfter != null) {
                url.append("&starting_after=")
                        .append(URLEncoder.encode(startingAfter, StandardCharsets.UTF_8));
            }

            HttpRequest request = HttpRequest.newBuilder(URI.create(url.toString()))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + secret)
                    .header("User-Agent", "ESNSMP-StripeStore/1.1")
                    .GET()
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                String body = response.body() == null ? "" : response.body().replaceAll("\\s+", " ");
                if (body.length() > 240) body = body.substring(0, 240);
                throw new IllegalStateException("Stripe HTTP " + response.statusCode() + (body.isBlank() ? "" : " - " + body));
            }

            JsonNode root = json.readTree(response.body());
            JsonNode data = root.path("data");
            if (!data.isArray() || data.isEmpty()) break;

            for (JsonNode session : data) ingestSession(session);

            if (!root.path("has_more").asBoolean(false)) break;
            startingAfter = data.get(data.size() - 1).path("id").asText("");
            if (startingAfter.isBlank()) break;
        }
    }

    private void ingestSession(JsonNode session) {
        String sessionId = session.path("id").asText("");
        if (sessionId.isBlank() || orderExists(sessionId)) return;
        if (!"paid".equalsIgnoreCase(session.path("payment_status").asText(""))) return;

        String paymentLink = session.path("payment_link").asText("");
        String productKey = session.path("metadata").path("esn_product").asText("");
        if (productKey.isBlank()) productKey = paymentLink;

        ProductMapping product = findProduct(productKey);
        if (product == null) {
            warnOnce(sessionId, "No product mapping for Stripe key/payment link: " + productKey);
            return;
        }

        String username = extractUsername(session);
        if (!MC_NAME.matcher(username).matches()) {
            warnOnce(sessionId, "Missing or invalid Minecraft username on Stripe session " + sessionId);
            return;
        }

        if (ESNItemsCommand.createStoreItem(product.itemId()) == null) {
            warnOnce(sessionId, "Mapped ESN item no longer exists: " + product.itemId());
            return;
        }

        long created = session.path("created").asLong(System.currentTimeMillis() / 1000L);
        if (insertPending(sessionId, username, productKey, product.itemId(), product.amount(), created)) {
            plugin.getLogger().info("[ESN Store] Queued paid Stripe order " + sessionId + " for Minecraft player " + username + ".");
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = Bukkit.getPlayerExact(username);
                if (player != null && player.isOnline()) deliverPending(player, false);
            });
        }
    }

    private String extractUsername(JsonNode session) {
        String metadata = session.path("metadata").path("minecraft_username").asText("").trim();
        if (!metadata.isBlank()) return metadata;

        JsonNode fields = session.path("custom_fields");
        if (fields.isArray()) {
            for (JsonNode field : fields) {
                String key = field.path("key").asText("");
                String label = field.path("label").path("custom").asText("");
                if (!"minecraft_username".equalsIgnoreCase(key) && !"Minecraft Username".equalsIgnoreCase(label)) continue;

                String value = field.path("text").path("value").asText("");
                if (value.isBlank()) value = field.path("numeric").path("value").asText("");
                if (value.isBlank()) value = field.path("dropdown").path("value").asText("");
                return value.trim();
            }
        }
        return "";
    }

    private void warnOnce(String sessionId, String message) {
        if (warnedSessions.add(sessionId)) plugin.getLogger().warning("[ESN Store] " + message);
    }

    private long getMetaLong(String key, long fallback) {
        synchronized (dbLock) {
            try (PreparedStatement p = db.prepareStatement("SELECT meta_value FROM stripe_store_meta WHERE meta_key=?")) {
                p.setString(1, key);
                try (ResultSet r = p.executeQuery()) {
                    if (!r.next()) return fallback;
                    try {
                        return Long.parseLong(r.getString(1));
                    } catch (NumberFormatException ex) {
                        return fallback;
                    }
                }
            } catch (Exception ex) {
                return fallback;
            }
        }
    }

    private void setMeta(String key, String value) {
        synchronized (dbLock) {
            try (PreparedStatement p = db.prepareStatement("""
                INSERT INTO stripe_store_meta(meta_key,meta_value) VALUES(?,?)
                ON CONFLICT(meta_key) DO UPDATE SET meta_value=excluded.meta_value
                """)) {
                p.setString(1, key);
                p.setString(2, value);
                p.executeUpdate();
            } catch (Exception ex) {
                throw new IllegalStateException("Store metadata update failed", ex);
            }
        }
    }

    private ProductMapping findProduct(String productKey) {
        if (productKey == null || productKey.isBlank()) return null;
        synchronized (dbLock) {
            try (PreparedStatement p = db.prepareStatement("SELECT item_id,amount FROM stripe_store_products WHERE product_key=?")) {
                p.setString(1, productKey);
                try (ResultSet r = p.executeQuery()) {
                    return r.next() ? new ProductMapping(r.getString(1), r.getInt(2)) : null;
                }
            } catch (Exception ex) {
                throw new IllegalStateException("Store product lookup failed", ex);
            }
        }
    }

    private void mapProduct(String productKey, String itemId, int amount) {
        synchronized (dbLock) {
            try (PreparedStatement p = db.prepareStatement("""
                INSERT INTO stripe_store_products(product_key,item_id,amount) VALUES(?,?,?)
                ON CONFLICT(product_key) DO UPDATE SET item_id=excluded.item_id, amount=excluded.amount
                """)) {
                p.setString(1, productKey);
                p.setString(2, itemId);
                p.setInt(3, amount);
                p.executeUpdate();
            } catch (Exception ex) {
                throw new IllegalStateException("Store product mapping failed", ex);
            }
        }
    }

    private boolean unmapProduct(String productKey) {
        synchronized (dbLock) {
            try (PreparedStatement p = db.prepareStatement("DELETE FROM stripe_store_products WHERE product_key=?")) {
                p.setString(1, productKey);
                return p.executeUpdate() > 0;
            } catch (Exception ex) {
                throw new IllegalStateException("Store product unmap failed", ex);
            }
        }
    }

    private List<String> productMappings() {
        List<String> out = new ArrayList<>();
        synchronized (dbLock) {
            try (PreparedStatement p = db.prepareStatement("SELECT product_key,item_id,amount FROM stripe_store_products ORDER BY product_key");
                 ResultSet r = p.executeQuery()) {
                while (r.next()) out.add(r.getString(1) + " -> " + r.getString(2) + " x" + r.getInt(3));
            } catch (Exception ex) {
                out.add("Could not read product mappings: " + ex.getMessage());
            }
        }
        return out;
    }

    private boolean orderExists(String sessionId) {
        synchronized (dbLock) {
            try (PreparedStatement p = db.prepareStatement("SELECT 1 FROM stripe_store_orders WHERE session_id=?")) {
                p.setString(1, sessionId);
                try (ResultSet r = p.executeQuery()) {
                    return r.next();
                }
            } catch (Exception ex) {
                throw new IllegalStateException("Store database lookup failed", ex);
            }
        }
    }

    private boolean insertPending(String sessionId, String playerName, String paymentLink, String itemId, int amount, long created) {
        synchronized (dbLock) {
            try (PreparedStatement p = db.prepareStatement("""
                INSERT OR IGNORE INTO stripe_store_orders
                (session_id,player_name,payment_link,item_id,amount,status,created_at)
                VALUES(?,?,?,?,?,'PENDING',?)
                """)) {
                p.setString(1, sessionId);
                p.setString(2, playerName);
                p.setString(3, paymentLink);
                p.setString(4, itemId);
                p.setInt(5, amount);
                p.setLong(6, created);
                return p.executeUpdate() > 0;
            } catch (Exception ex) {
                throw new IllegalStateException("Store database insert failed", ex);
            }
        }
    }

    private List<StoreOrder> pendingFor(Player player) {
        List<StoreOrder> out = new ArrayList<>();
        synchronized (dbLock) {
            try (PreparedStatement p = db.prepareStatement("""
                SELECT session_id,item_id,amount FROM stripe_store_orders
                WHERE status='PENDING' AND player_name = ? COLLATE NOCASE
                ORDER BY created_at ASC
                """)) {
                p.setString(1, player.getName());
                try (ResultSet r = p.executeQuery()) {
                    while (r.next()) out.add(new StoreOrder(r.getString(1), r.getString(2), r.getInt(3)));
                }
            } catch (Exception ex) {
                throw new IllegalStateException("Store database pending lookup failed", ex);
            }
        }
        return out;
    }

    private int pendingCount() {
        synchronized (dbLock) {
            try (PreparedStatement p = db.prepareStatement("SELECT COUNT(*) FROM stripe_store_orders WHERE status='PENDING'");
                 ResultSet r = p.executeQuery()) {
                return r.next() ? r.getInt(1) : 0;
            } catch (Exception ex) {
                return -1;
            }
        }
    }

    private void markDelivered(String sessionId) {
        synchronized (dbLock) {
            try (PreparedStatement p = db.prepareStatement("""
                UPDATE stripe_store_orders
                SET status='DELIVERED', delivered_at=?, last_error=NULL
                WHERE session_id=? AND status='PENDING'
                """)) {
                p.setLong(1, System.currentTimeMillis() / 1000L);
                p.setString(2, sessionId);
                p.executeUpdate();
            } catch (Exception ex) {
                throw new IllegalStateException("Store database delivery update failed", ex);
            }
        }
    }

    private void markError(String sessionId, String error) {
        synchronized (dbLock) {
            try (PreparedStatement p = db.prepareStatement("UPDATE stripe_store_orders SET last_error=? WHERE session_id=?")) {
                p.setString(1, error);
                p.setString(2, sessionId);
                p.executeUpdate();
            } catch (Exception ignored) {
            }
        }
    }

    private int deliverPending(Player player, boolean tellIfNone) {
        if (!player.isOnline()) return 0;
        List<StoreOrder> orders = pendingFor(player);
        if (orders.isEmpty()) {
            if (tellIfNone) player.sendMessage(ChatColor.GRAY + "You do not have any pending ESN Store purchases.");
            return 0;
        }

        int delivered = 0;
        for (StoreOrder order : orders) {
            try {
                ItemStack base = ESNItemsCommand.createStoreItem(order.itemId());
                if (base == null) {
                    markError(order.sessionId(), "Unknown item-id: " + order.itemId());
                    continue;
                }

                int remaining = order.amount();
                while (remaining > 0) {
                    ItemStack stack = base.clone();
                    int chunk = Math.min(remaining, Math.max(1, stack.getMaxStackSize()));
                    stack.setAmount(chunk);
                    var leftovers = player.getInventory().addItem(stack);
                    leftovers.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
                    remaining -= chunk;
                }

                markDelivered(order.sessionId());
                delivered++;
                player.sendMessage(ChatColor.GREEN + "ESN Store purchase delivered: " + ChatColor.WHITE + order.itemId() + " x" + order.amount());
                plugin.getLogger().info("[ESN Store] Delivered Stripe order " + order.sessionId() + " to " + player.getName() + ".");
            } catch (Exception ex) {
                markError(order.sessionId(), String.valueOf(ex.getMessage()));
                plugin.getLogger().severe("[ESN Store] Delivery failed for " + order.sessionId() + ": " + ex.getMessage());
            }
        }
        return delivered;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> deliverPending(event.getPlayer(), false), 20L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("storeclaim")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Players only.");
                return true;
            }
            deliverPending(player, true);
            return true;
        }

        if (!command.getName().equalsIgnoreCase("storestatus")) return false;

        if (args.length > 0 && args[0].equalsIgnoreCase("map")) {
            if (!sender.hasPermission("esnsmp.store.admin")) {
                sender.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(ChatColor.YELLOW + "/storestatus map <plink_or_product_key> <esn_item_id> [amount]");
                return true;
            }
            String key = args[1];
            String itemId = args[2].toLowerCase(Locale.ROOT);
            int amount = 1;
            if (args.length >= 4) {
                try {
                    amount = Integer.parseInt(args[3]);
                } catch (NumberFormatException ex) {
                    sender.sendMessage(ChatColor.RED + "Amount must be a number.");
                    return true;
                }
            }
            amount = Math.max(1, Math.min(2304, amount));
            if (ESNItemsCommand.createStoreItem(itemId) == null) {
                sender.sendMessage(ChatColor.RED + "Unknown ESN item ID. Check /esnitems list.");
                return true;
            }
            mapProduct(key, itemId, amount);
            sender.sendMessage(ChatColor.GREEN + "Mapped " + key + " -> " + itemId + " x" + amount);
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("unmap")) {
            if (!sender.hasPermission("esnsmp.store.admin")) {
                sender.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(ChatColor.YELLOW + "/storestatus unmap <plink_or_product_key>");
                return true;
            }
            sender.sendMessage(unmapProduct(args[1]) ? ChatColor.GREEN + "Product mapping removed." : ChatColor.YELLOW + "No mapping found.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("products")) {
            List<String> mappings = productMappings();
            sender.sendMessage(ChatColor.GOLD + "ESN Store product mappings (" + mappings.size() + "):");
            if (mappings.isEmpty()) sender.sendMessage(ChatColor.GRAY + "None yet.");
            else mappings.forEach(x -> sender.sendMessage(ChatColor.GRAY + x));
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "ESN Store Stripe Bridge");
        sender.sendMessage(ChatColor.GRAY + "Key detected: " + ChatColor.WHITE + !secretKey().isBlank());
        sender.sendMessage(ChatColor.GRAY + "Running: " + ChatColor.WHITE + running);
        sender.sendMessage(ChatColor.GRAY + "Pending deliveries: " + ChatColor.WHITE + pendingCount());
        sender.sendMessage(ChatColor.GRAY + "Product mappings: " + ChatColor.WHITE + productMappings().size());
        sender.sendMessage(ChatColor.GRAY + "Last successful poll: " + ChatColor.WHITE +
                (lastSuccessfulPoll == 0L ? "never" : ((System.currentTimeMillis() - lastSuccessfulPoll) / 1000L) + "s ago"));
        if (!lastError.isBlank()) sender.sendMessage(ChatColor.RED + "Last error: " + lastError);
        return true;
    }

    @Override
    public void close() {
        running = false;
        if (pollTask != null) pollTask.cancel();
        synchronized (dbLock) {
            if (db != null) {
                try {
                    db.close();
                } catch (Exception ex) {
                    plugin.getLogger().warning("[ESN Store] Database close error: " + ex.getMessage());
                }
                db = null;
            }
        }
    }

    private record StoreOrder(String sessionId, String itemId, int amount) {}
    private record ProductMapping(String itemId, int amount) {}
}
