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
import org.bukkit.configuration.ConfigurationSection;
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
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        initializeDatabase();
        ensureConfigDefaults();
        ensureSecretKeyFile();
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
            }
        }
    }

    private void ensureConfigDefaults() {
        var cfg = plugin.getConfig();
        boolean changed = false;
        changed |= setDefault("stripe-store.enabled", false);
        changed |= setDefault("stripe-store.secret-key", "");
        changed |= setDefault("stripe-store.poll-interval-seconds", 30);
        changed |= setDefault("stripe-store.username-field-key", "minecraft_username");
        changed |= setDefault("stripe-store.username-field-label", "Minecraft Username");
        changed |= setDefault("stripe-store.activation-time", 0L);
        if (cfg.getConfigurationSection("stripe-store.products") == null) {
            cfg.createSection("stripe-store.products");
            changed = true;
        }
        if (changed) plugin.saveConfig();
    }

    private boolean setDefault(String path, Object value) {
        if (plugin.getConfig().contains(path)) return false;
        plugin.getConfig().set(path, value);
        return true;
    }

    public void start() {
        String secret = secretKey();
        if (secret.isBlank()) {
            lastError = "Stripe secret key is missing";
            plugin.getLogger().warning("[ESN Store] Stripe bridge is waiting for plugins/ESNSMP/stripe-key.txt. Paste ONLY your sk_test_... or sk_live_... key into that file, save it, then restart the server.");
            return;
        }

        long activation = plugin.getConfig().getLong("stripe-store.activation-time", 0L);
        if (activation <= 0L) {
            activation = System.currentTimeMillis() / 1000L;
            plugin.getConfig().set("stripe-store.activation-time", activation);
            plugin.saveConfig();
            plugin.getLogger().info("[ESN Store] Activation time initialized. Orders created before this moment will not be auto-delivered.");
        }

        long seconds = Math.max(15L, plugin.getConfig().getLong("stripe-store.poll-interval-seconds", 30L));
        running = true;
        pollTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::pollSafely, 40L, seconds * 20L);
        plugin.getLogger().info("[ESN Store] Stripe bridge started. Poll interval: " + seconds + "s.");
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
            plugin.getLogger().info("[ESN Store] Created plugins/ESNSMP/stripe-key.txt for easy Stripe setup.");
        } catch (Exception ex) {
            plugin.getLogger().warning("[ESN Store] Could not create stripe-key.txt: " + ex.getMessage());
        }
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

        String legacy = plugin.getConfig().getString("stripe-store.secret-key", "").trim();
        return isStripeSecret(legacy) ? legacy : "";
    }

    private boolean isStripeSecret(String value) {
        if (value == null) return false;
        String key = value.trim();
        return key.startsWith("sk_test_") || key.startsWith("sk_live_");
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

        long activation = plugin.getConfig().getLong("stripe-store.activation-time", 0L);
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
                    .header("User-Agent", "ESNSMP-StripeStore/1.0")
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

            boolean hasMore = root.path("has_more").asBoolean(false);
            if (!hasMore) break;
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

        ConfigurationSection product = productSection(productKey);
        if (product == null) {
            warnOnce(sessionId, "No ESN product mapping for Stripe key/payment link: " + productKey);
            return;
        }

        String username = extractUsername(session);
        if (!MC_NAME.matcher(username).matches()) {
            warnOnce(sessionId, "Missing or invalid Minecraft username on Stripe session " + sessionId);
            return;
        }

        String itemId = product.getString("item-id", "").trim().toLowerCase(Locale.ROOT);
        int amount = Math.max(1, Math.min(2304, product.getInt("amount", 1)));
        if (itemId.isBlank() || ESNItemsCommand.createStoreItem(itemId) == null) {
            warnOnce(sessionId, "Product mapping " + productKey + " has an unknown ESN item-id: " + itemId);
            return;
        }

        long created = session.path("created").asLong(System.currentTimeMillis() / 1000L);
        if (insertPending(sessionId, username, productKey, itemId, amount, created)) {
            plugin.getLogger().info("[ESN Store] Queued paid Stripe order " + sessionId + " for Minecraft player " + username + ".");
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = Bukkit.getPlayerExact(username);
                if (player != null && player.isOnline()) deliverPending(player, false);
            });
        }
    }

    private ConfigurationSection productSection(String key) {
        if (key == null || key.isBlank()) return null;
        return plugin.getConfig().getConfigurationSection("stripe-store.products." + key);
    }

    private String extractUsername(JsonNode session) {
        String metadata = session.path("metadata").path("minecraft_username").asText("").trim();
        if (!metadata.isBlank()) return metadata;

        String wantedKey = plugin.getConfig().getString("stripe-store.username-field-key", "minecraft_username");
        String wantedLabel = plugin.getConfig().getString("stripe-store.username-field-label", "Minecraft Username");

        JsonNode fields = session.path("custom_fields");
        if (fields.isArray()) {
            for (JsonNode field : fields) {
                String key = field.path("key").asText("");
                String label = field.path("label").path("custom").asText("");
                if (!wantedKey.equalsIgnoreCase(key) && !wantedLabel.equalsIgnoreCase(label)) continue;

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
                    plugin.getLogger().severe("[ESN Store] Cannot deliver " + order.sessionId() + ": unknown item " + order.itemId());
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

        if (command.getName().equalsIgnoreCase("storestatus")) {
            boolean enabled = !secretKey().isBlank();
            sender.sendMessage(ChatColor.GOLD + "ESN Store Stripe Bridge");
            sender.sendMessage(ChatColor.GRAY + "Key detected: " + ChatColor.WHITE + enabled);
            sender.sendMessage(ChatColor.GRAY + "Running: " + ChatColor.WHITE + running);
            sender.sendMessage(ChatColor.GRAY + "Pending deliveries: " + ChatColor.WHITE + pendingCount());
            sender.sendMessage(ChatColor.GRAY + "Last successful poll: " + ChatColor.WHITE +
                    (lastSuccessfulPoll == 0L ? "never" : ((System.currentTimeMillis() - lastSuccessfulPoll) / 1000L) + "s ago"));
            if (!lastError.isBlank()) sender.sendMessage(ChatColor.RED + "Last error: " + lastError);
            return true;
        }

        return false;
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

    private record StoreOrder(String sessionId, String itemId, int amount) {
    }
}
