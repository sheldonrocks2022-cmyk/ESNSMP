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
import org.bukkit.configuration.file.YamlConfiguration;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class StripeStoreBridge implements Listener, CommandExecutor, AutoCloseable {
    private static final Pattern MC_NAME = Pattern.compile("^\\.?[A-Za-z0-9_]{3,16}$");
    private static final String STRIPE_SESSIONS = "https://api.stripe.com/v1/checkout/sessions";
    private static final long POLL_SECONDS = 30L;
    private static final int MAX_ITEMS_PER_PRODUCT = 30;
    private static final String BUNDLE_PREFIX = "__esn_bundle__:";
    private static final String REALM_KEYS_PLINK = "plink_1UJN5IISwShswuKdH08ewRC2";
    private static final String SEASON_RELICS_PLINK = "plink_1UJk6BISwShswuKdtQ40w2Jb";

    private final ESNSMPPlugin plugin;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();
    private final Object dbLock = new Object();
    private final Object productLock = new Object();
    private final Set<String> warnedSessions = ConcurrentHashMap.newKeySet();

    private Connection db;
    private File productFile;
    private YamlConfiguration productConfig;
    private BukkitTask pollTask;
    private volatile boolean running;
    private volatile long lastSuccessfulPoll;
    private volatile String lastError = "";

    public StripeStoreBridge(ESNSMPPlugin plugin) throws Exception {
        this.plugin = plugin;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        initializeDatabase();
        initializeProductCatalog();
        ensureSecretKeyFile();
    }

    public static void migrateLegacyStripeKey(File dataFolder) {
        if (dataFolder == null) return;
        File configFile = new File(dataFolder, "config.yml");
        File keyFile = new File(dataFolder, "stripe-key.txt");

        // Never overwrite an already configured key file.
        if (keyFile.isFile()) {
            try {
                String existing = Files.readString(keyFile.toPath(), StandardCharsets.UTF_8).trim();
                if (existing.startsWith("sk_test_") || existing.startsWith("sk_live_")) return;
            } catch (Exception ignored) {
            }
        }

        if (!configFile.isFile()) return;
        try {
            List<String> lines = Files.readAllLines(configFile.toPath(), StandardCharsets.UTF_8);
            boolean inStripeStore = false;
            String legacyKey = "";
            for (String line : lines) {
                String trimmed = line.trim();
                if (!inStripeStore && trimmed.equals("stripe-store:")) {
                    inStripeStore = true;
                    continue;
                }
                if (inStripeStore) {
                    if (!line.isBlank() && !Character.isWhitespace(line.charAt(0))) break;
                    if (trimmed.startsWith("secret-key:")) {
                        legacyKey = trimmed.substring("secret-key:".length()).trim();
                        if ((legacyKey.startsWith("\"") && legacyKey.endsWith("\"")) ||
                            (legacyKey.startsWith("'") && legacyKey.endsWith("'"))) {
                            legacyKey = legacyKey.substring(1, legacyKey.length() - 1).trim();
                        }
                        break;
                    }
                }
            }

            if (legacyKey.startsWith("sk_test_") || legacyKey.startsWith("sk_live_")) {
                if (!dataFolder.exists()) dataFolder.mkdirs();
                Files.writeString(keyFile.toPath(), legacyKey + "\n", StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
        } catch (Exception ignored) {
        }
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

    private void initializeProductCatalog() throws Exception {
        productFile = new File(plugin.getDataFolder(), "store-products.yml");
        productConfig = YamlConfiguration.loadConfiguration(productFile);
        boolean changed = false;

        changed |= seedProductIfMissing(REALM_KEYS_PLINK, List.of(
                new ProductMapping("realm100key", 20)
        ));
        changed |= seedProductIfMissing(SEASON_RELICS_PLINK, List.of(
                new ProductMapping("angelwings", 1),
                new ProductMapping("infernoscepter", 1),
                new ProductMapping("stormcrystal", 1),
                new ProductMapping("tideheart", 1),
                new ProductMapping("voidrelic", 1),
                new ProductMapping("celestialstar", 1)
        ));

        // Preserve any product mappings created by older builds.
        synchronized (dbLock) {
            try (PreparedStatement p = db.prepareStatement("SELECT product_key,item_id,amount FROM stripe_store_products");
                 ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    String key = r.getString(1);
                    if (key == null || key.isBlank()) continue;
                    synchronized (productLock) {
                        if (readProductUnlocked(key).isEmpty()) {
                            writeProductUnlocked(key, List.of(new ProductMapping(r.getString(2), r.getInt(3))));
                            changed = true;
                        }
                    }
                }
            }
        }

        if (changed || !productFile.isFile()) saveProductCatalog();
        plugin.getLogger().info("[ESN Store] Loaded " + productMappings().size() +
                " product link(s) from store-products.yml.");
    }

    private boolean seedProductIfMissing(String key, List<ProductMapping> items) {
        synchronized (productLock) {
            if (!readProductUnlocked(key).isEmpty()) return false;
            writeProductUnlocked(key, items);
            return true;
        }
    }

    private List<ProductMapping> readProductUnlocked(String productKey) {
        if (productConfig == null || productKey == null || productKey.isBlank()) return List.of();
        List<?> rows = productConfig.getList("products." + productKey + ".items");
        if (rows == null || rows.isEmpty()) return List.of();

        List<ProductMapping> out = new ArrayList<>();
        for (Object rowObj : rows) {
            if (!(rowObj instanceof Map<?, ?> row)) continue;
            Object idObj = row.get("id");
            Object amountObj = row.get("amount");
            if (idObj == null) continue;
            String id = String.valueOf(idObj).trim().toLowerCase(Locale.ROOT);
            int amount = amountObj instanceof Number n ? n.intValue() : 1;
            amount = Math.max(1, Math.min(2304, amount));
            if (!id.isBlank()) out.add(new ProductMapping(id, amount));
            if (out.size() >= MAX_ITEMS_PER_PRODUCT) break;
        }
        return List.copyOf(out);
    }

    private void writeProductUnlocked(String productKey, List<ProductMapping> items) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ProductMapping item : items) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", item.itemId());
            row.put("amount", item.amount());
            rows.add(row);
        }
        productConfig.set("products." + productKey + ".items", rows);
    }

    private void saveProductCatalog() {
        synchronized (productLock) {
            try {
                productConfig.save(productFile);
            } catch (Exception ex) {
                throw new IllegalStateException("Could not save store-products.yml", ex);
            }
        }
    }

    private List<ProductMapping> findProducts(String productKey) {
        synchronized (productLock) {
            return readProductUnlocked(productKey);
        }
    }

    private String mapProduct(String productKey, String itemId, int amount) {
        synchronized (productLock) {
            List<ProductMapping> items = new ArrayList<>(readProductUnlocked(productKey));
            int existing = -1;
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).itemId().equalsIgnoreCase(itemId)) {
                    existing = i;
                    break;
                }
            }
            if (existing >= 0) {
                items.set(existing, new ProductMapping(itemId, amount));
            } else {
                if (items.size() >= MAX_ITEMS_PER_PRODUCT) return "LIMIT";
                items.add(new ProductMapping(itemId, amount));
            }
            writeProductUnlocked(productKey, items);
            saveProductCatalog();
            return existing >= 0 ? "UPDATED" : "ADDED";
        }
    }

    private boolean unmapProduct(String productKey) {
        synchronized (productLock) {
            String path = "products." + productKey;
            if (!productConfig.contains(path)) return false;
            productConfig.set(path, null);
            saveProductCatalog();
            return true;
        }
    }

    private List<String> productMappings() {
        List<String> out = new ArrayList<>();
        synchronized (productLock) {
            ConfigurationSection root = productConfig == null ? null : productConfig.getConfigurationSection("products");
            if (root == null) return out;
            for (String key : root.getKeys(false)) {
                List<ProductMapping> items = readProductUnlocked(key);
                out.add(key + " -> " + formatBundle(items));
            }
        }
        return out;
    }

    private String formatBundle(List<ProductMapping> items) {
        if (items == null || items.isEmpty()) return "(no items)";
        List<String> parts = new ArrayList<>();
        for (ProductMapping item : items) parts.add(item.itemId() + " x" + item.amount());
        return String.join(", ", parts);
    }

    private String encodeBundle(List<ProductMapping> items) {
        List<String> parts = new ArrayList<>();
        for (ProductMapping item : items) parts.add(item.itemId() + "*" + item.amount());
        return BUNDLE_PREFIX + String.join(",", parts);
    }

    private List<ProductMapping> decodeStoredItems(String storedItemId, int storedAmount) {
        if (storedItemId == null) return List.of();
        if (!storedItemId.startsWith(BUNDLE_PREFIX)) {
            return List.of(new ProductMapping(storedItemId, Math.max(1, storedAmount)));
        }
        String body = storedItemId.substring(BUNDLE_PREFIX.length());
        List<ProductMapping> out = new ArrayList<>();
        if (body.isBlank()) return out;
        for (String token : body.split(",")) {
            String[] bits = token.split("\\*", 2);
            if (bits.length != 2 || bits[0].isBlank()) continue;
            try {
                int amount = Math.max(1, Math.min(2304, Integer.parseInt(bits[1])));
                out.add(new ProductMapping(bits[0].toLowerCase(Locale.ROOT), amount));
            } catch (NumberFormatException ignored) {
            }
            if (out.size() >= MAX_ITEMS_PER_PRODUCT) break;
        }
        return out;
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

            for (JsonNode session : data) ingestSession(session, secret);

            if (!root.path("has_more").asBoolean(false)) break;
            startingAfter = data.get(data.size() - 1).path("id").asText("");
            if (startingAfter.isBlank()) break;
        }
    }


    private List<String> scanRecentSessions(boolean recover) throws Exception {
        String secret = secretKey();
        if (secret.isBlank()) throw new IllegalStateException("Stripe secret key is missing");

        long since = (System.currentTimeMillis() / 1000L) - (7L * 24L * 60L * 60L);
        long activation = getMetaLong("activation_time", 0L);
        String startingAfter = null;
        List<String> results = new ArrayList<>();
        int examined = 0;
        int recovered = 0;

        for (int page = 0; page < 5; page++) {
            StringBuilder url = new StringBuilder(STRIPE_SESSIONS)
                    .append("?limit=100&status=complete&created%5Bgte%5D=")
                    .append(since);
            if (startingAfter != null) {
                url.append("&starting_after=")
                        .append(URLEncoder.encode(startingAfter, StandardCharsets.UTF_8));
            }

            HttpRequest request = HttpRequest.newBuilder(URI.create(url.toString()))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + secret)
                    .header("User-Agent", "ESNSMP-StripeStore/1.2")
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

            for (JsonNode session : data) {
                examined++;
                String sessionId = session.path("id").asText("");
                String paymentStatus = session.path("payment_status").asText("");
                String paymentLink = session.path("payment_link").asText("");
                String productKey = session.path("metadata").path("esn_product").asText("");
                if (productKey.isBlank()) productKey = paymentLink;
                List<ProductMapping> mapping = findProducts(productKey);
                String username = extractUsername(session, secret);
                long created = session.path("created").asLong(0L);

                // Keep output focused on ESN Store-relevant sessions only.
                if (mapping.isEmpty() && username.isBlank()) continue;

                String shortId = sessionId.length() > 12 ? "..." + sessionId.substring(sessionId.length() - 12) : sessionId;
                String prefix = created > 0 && activation > 0 && created < activation ? "pre-activation; " : "";

                if (!"paid".equalsIgnoreCase(paymentStatus)) {
                    results.add(shortId + " - " + prefix + "not paid");
                    continue;
                }
                if (orderExists(sessionId)) {
                    results.add(shortId + " - " + prefix + "already processed");
                    continue;
                }
                if (mapping.isEmpty()) {
                    results.add(shortId + " - " + prefix + "no product mapping (" + productKey + ")");
                    continue;
                }
                if (!MC_NAME.matcher(username).matches()) {
                    results.add(shortId + " - " + prefix + "missing/invalid Minecraft Username" +
                            (username.isBlank() ? "" : " [" + username + "]"));
                    continue;
                }
                String missingItem = "";
                for (ProductMapping item : mapping) {
                    if (ESNItemsCommand.createStoreItem(item.itemId()) == null) {
                        missingItem = item.itemId();
                        break;
                    }
                }
                if (!missingItem.isBlank()) {
                    results.add(shortId + " - " + prefix + "mapped item missing: " + missingItem);
                    continue;
                }

                if (recover) {
                    if (insertPending(sessionId, username, productKey, encodeBundle(mapping), 1, created)) {
                        recovered++;
                        String finalUsername = username;
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            Player player = Bukkit.getPlayerExact(finalUsername);
                            if (player != null && player.isOnline()) deliverPending(player, false);
                        });
                        results.add(shortId + " - RECOVERED for " + username + " -> " + formatBundle(mapping));
                    } else {
                        results.add(shortId + " - already processed");
                    }
                } else {
                    results.add(shortId + " - READY for " + username + " -> " + formatBundle(mapping));
                }
            }

            if (!root.path("has_more").asBoolean(false)) break;
            startingAfter = data.get(data.size() - 1).path("id").asText("");
            if (startingAfter.isBlank()) break;
        }

        results.add(0, "Examined " + examined + " completed Stripe session(s) from the last 7 days" +
                (recover ? "; recovered " + recovered : "") + ".");
        if (results.size() == 1) {
            results.add("No ESN Store-relevant sessions were found.");
        }
        return results;
    }

    private void runRecentScan(CommandSender sender, boolean recover) {
        sender.sendMessage(ChatColor.YELLOW + (recover
                ? "Scanning recent Stripe payments and recovering valid ESN Store orders..."
                : "Scanning recent Stripe payments in read-only debug mode..."));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<String> lines;
            try {
                lines = scanRecentSessions(recover);
            } catch (Exception ex) {
                lines = List.of("Scan failed: " + ex.getClass().getSimpleName() + ": " + String.valueOf(ex.getMessage()));
            }
            List<String> finalLines = lines;
            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(ChatColor.GOLD + (recover ? "ESN Store Rescan" : "ESN Store Debug"));
                int shown = 0;
                for (String line : finalLines) {
                    if (shown >= 12) {
                        sender.sendMessage(ChatColor.GRAY + "...more results omitted");
                        break;
                    }
                    sender.sendMessage(ChatColor.GRAY + line);
                    shown++;
                }
                if (recover) {
                    sender.sendMessage(ChatColor.YELLOW + "If you are online and a purchase was recovered, delivery should happen immediately. Otherwise use /storeclaim.");
                }
            });
        });
    }

    private void ingestSession(JsonNode session, String secret) {
        String sessionId = session.path("id").asText("");
        if (sessionId.isBlank() || orderExists(sessionId)) return;
        if (!"paid".equalsIgnoreCase(session.path("payment_status").asText(""))) return;

        String paymentLink = session.path("payment_link").asText("");
        String productKey = session.path("metadata").path("esn_product").asText("");
        if (productKey.isBlank()) productKey = paymentLink;

        List<ProductMapping> products = findProducts(productKey);
        if (products.isEmpty()) {
            warnOnce(sessionId, "No product mapping for Stripe key/payment link: " + productKey);
            return;
        }

        String username = extractUsername(session, secret);
        if (!MC_NAME.matcher(username).matches()) {
            warnOnce(sessionId, "Missing or invalid Minecraft username on Stripe session " + sessionId);
            return;
        }

        for (ProductMapping product : products) {
            if (ESNItemsCommand.createStoreItem(product.itemId()) == null) {
                warnOnce(sessionId, "Mapped ESN item no longer exists: " + product.itemId());
                return;
            }
        }

        long created = session.path("created").asLong(System.currentTimeMillis() / 1000L);
        if (insertPending(sessionId, username, productKey, encodeBundle(products), 1, created)) {
            plugin.getLogger().info("[ESN Store] Queued paid Stripe order " + sessionId + " for Minecraft player " +
                    username + " (" + products.size() + " item type(s)).");
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = Bukkit.getPlayerExact(username);
                if (player != null && player.isOnline()) deliverPending(player, false);
            });
        }
    }

    private JsonNode retrieveSession(String sessionId, String secret) throws Exception {
        String url = STRIPE_SESSIONS + "/" + URLEncoder.encode(sessionId, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + secret)
                .header("User-Agent", "ESNSMP-StripeStore/1.3")
                .GET()
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            String body = response.body() == null ? "" : response.body().replaceAll("\\s+", " ");
            if (body.length() > 240) body = body.substring(0, 240);
            throw new IllegalStateException("Stripe session lookup HTTP " + response.statusCode() +
                    (body.isBlank() ? "" : " - " + body));
        }
        return json.readTree(response.body());
    }

    private String extractUsername(JsonNode session, String secret) {
        String username = extractUsernameFromNode(session);
        if (!username.isBlank()) return username;

        // Stripe list responses can omit or simplify nested custom-field data.
        // If the username was not present, retrieve the completed Session directly
        // and read the submitted custom-field value from the full object.
        String sessionId = session.path("id").asText("").trim();
        if (!sessionId.isBlank() && secret != null && !secret.isBlank()) {
            try {
                JsonNode fullSession = retrieveSession(sessionId, secret);
                username = extractUsernameFromNode(fullSession);
                if (!username.isBlank()) return username;
            } catch (Exception ex) {
                plugin.getLogger().warning("[ESN Store] Could not retrieve full Stripe session " +
                        sessionId + " for username lookup: " + ex.getMessage());
            }
        }
        return "";
    }

    private String extractUsernameFromNode(JsonNode session) {
        JsonNode metadata = session.path("metadata");
        for (String key : List.of(
                "minecraft_username", "minecraftUsername", "mc_username",
                "mcusername", "minecraft_name", "minecraftname")) {
            String value = metadata.path(key).asText("").trim();
            if (!value.isBlank()) return value;
        }

        JsonNode fields = session.path("custom_fields");
        List<String> mcLikeCandidates = new ArrayList<>();
        if (fields.isArray()) {
            for (JsonNode field : fields) {
                String key = field.path("key").asText("").trim();
                String label = field.path("label").path("custom").asText("").trim();
                if (label.isBlank() && field.path("label").isTextual()) {
                    label = field.path("label").asText("").trim();
                }

                String value = field.path("text").path("value").asText("");
                if (value.isBlank()) value = field.path("numeric").path("value").asText("");
                if (value.isBlank()) value = field.path("dropdown").path("value").asText("");
                if (value.isBlank()) value = field.path("value").asText("");
                value = value.trim();
                if (value.isBlank()) continue;

                String hint = (key + " " + label).toLowerCase(Locale.ROOT)
                        .replace("-", "_")
                        .replace(" ", "_");
                if (hint.contains("minecraft") || hint.contains("mc_username")
                        || hint.contains("mcusername") || hint.contains("gamertag")
                        || hint.contains("player_name") || hint.contains("playername")) {
                    return value;
                }

                if (MC_NAME.matcher(value).matches()) mcLikeCandidates.add(value);
            }
        }

        // Payment Links can generate opaque custom-field keys. When there is only one
        // submitted custom field whose value is a valid Minecraft-style name, use it.
        if (mcLikeCandidates.size() == 1) return mcLikeCandidates.get(0);

        return "";
    }

    private void warnOnce(String sessionId, String message) {
        if (warnedSessions.size() >= 2048) warnedSessions.clear();
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

    // Product mappings are stored in store-products.yml; the legacy SQLite table is read only for migration.

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
                List<ProductMapping> items = decodeStoredItems(order.itemId(), order.amount());
                if (items.isEmpty()) {
                    markError(order.sessionId(), "Stored bundle is empty");
                    continue;
                }

                for (ProductMapping item : items) {
                    ItemStack base = ESNItemsCommand.createStoreItem(item.itemId());
                    if (base == null) throw new IllegalStateException("Unknown item-id: " + item.itemId());

                    int remaining = item.amount();
                    while (remaining > 0) {
                        ItemStack stack = base.clone();
                        int chunk = Math.min(remaining, Math.max(1, stack.getMaxStackSize()));
                        stack.setAmount(chunk);
                        var leftovers = player.getInventory().addItem(stack);
                        leftovers.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
                        remaining -= chunk;
                    }
                }

                markDelivered(order.sessionId());
                delivered++;
                player.sendMessage(ChatColor.GREEN + "ESN Store purchase delivered: " + ChatColor.WHITE + formatBundle(items));
                plugin.getLogger().info("[ESN Store] Delivered Stripe order " + order.sessionId() + " to " +
                        player.getName() + " (" + items.size() + " item type(s)).");
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


        if (args.length > 0 && args[0].equalsIgnoreCase("debug")) {
            if (!sender.hasPermission("esnsmp.store.admin")) {
                sender.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }
            runRecentScan(sender, false);
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("rescan")) {
            if (!sender.hasPermission("esnsmp.store.admin")) {
                sender.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }
            runRecentScan(sender, true);
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("map")) {
            if (!sender.hasPermission("esnsmp.store.admin")) {
                sender.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(ChatColor.YELLOW + "/storestatus map <plink_or_product_key> <esn_item_id> [amount]");
                sender.sendMessage(ChatColor.GRAY + "Adds or updates one item; each payment link can hold up to " + MAX_ITEMS_PER_PRODUCT + " item entries.");
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
            String result = mapProduct(key, itemId, amount);
            if ("LIMIT".equals(result)) {
                sender.sendMessage(ChatColor.RED + "That payment link already has " + MAX_ITEMS_PER_PRODUCT + " item entries.");
                return true;
            }
            sender.sendMessage(ChatColor.GREEN + ("UPDATED".equals(result) ? "Updated " : "Added ") +
                    itemId + " x" + amount + " for " + key);
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
        sender.sendMessage(ChatColor.GRAY + "Admin debug: " + ChatColor.WHITE + "/storestatus debug");
        sender.sendMessage(ChatColor.GRAY + "Recover recent purchase: " + ChatColor.WHITE + "/storestatus rescan");

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
        warnedSessions.clear();
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
