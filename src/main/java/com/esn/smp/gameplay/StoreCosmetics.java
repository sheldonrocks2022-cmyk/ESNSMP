package com.esn.smp.gameplay;

import com.esn.smp.ESNSMPPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Staged ESN Store cosmetics engine.
 *
 * Public releases are disabled by default in config. Admins/owners can unlock
 * and preview everything immediately, then enable one category or item at a time.
 * All unlocks/selections persist on the Player PDC and survive restarts.
 *
 * Most systems here are cosmetic-only. Weapon Cosmetic Packs are a deliberate
 * combat tier: slightly above Realm 100 weapons, while remaining below ESN
 * store-exclusive weapon bundles.
 */
@SuppressWarnings("deprecation")
public final class StoreCosmetics implements Listener, CommandExecutor, AutoCloseable {
    public enum Category {
        AURA("aura", "Aura Packs"),
        TITLE("title", "Exclusive Titles"),
        TELEPORT("teleport", "Spawn + Teleport Effects"),
        PET("pet", "Companion Pets"),
        CHAT_COLOR("chatcolor", "Chat Colors"),
        BRACKET("bracket", "Title Brackets"),
        JOIN("join", "Join Messages"),
        EMOJI("emoji", "Cosmetic Emojis"),
        WEAPON("weapon", "Weapon Cosmetic Packs"),
        ARMOR("armor", "Armor Cosmetic Sets"),
        SUPPORTER("supporter", "Supporter Ranks"),
        COLLECTION("collection", "Collections");

        private final String key;
        private final String display;

        Category(String key, String display) {
            this.key = key;
            this.display = display;
        }

        public String key() { return key; }
        public String display() { return display; }

        public static Category from(String raw) {
            if (raw == null) return null;
            String s = raw.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
            for (Category category : values()) {
                String key = category.key.replace("_", "").replace("-", "");
                if (key.equals(s)) return category;
            }
            if ("chat".equals(s) || "color".equals(s)) return CHAT_COLOR;
            if ("spawn".equals(s) || "spawnteleport".equals(s)) return TELEPORT;
            if ("rank".equals(s) || "supporterrank".equals(s)) return SUPPORTER;
            if ("collections".equals(s) || "seasonal".equals(s)) return COLLECTION;
            return null;
        }
    }

    public record Cosmetic(Category category, String id, String name, Material tokenMaterial,
                           int tokenPrice, boolean hidden) {
        public String key() { return category.key() + ":" + id; }
        public String storeId() { return "cosmetic_" + category.key() + "_" + id; }
    }

    private static final NamespacedKey TOKEN = new NamespacedKey("esnsmp", "cosmetic_store_token");
    private static final NamespacedKey TOKEN_AMOUNT = new NamespacedKey("esnsmp", "cosmetic_token_amount");
    private static final NamespacedKey UNLOCKS = new NamespacedKey("esnsmp", "cosmetic_unlocks");
    private static final NamespacedKey BALANCE = new NamespacedKey("esnsmp", "cosmetic_tokens_balance");
    private static final NamespacedKey WEAPON_SKIN = new NamespacedKey("esnsmp", "weapon_cosmetic_skin");
    private static final NamespacedKey PROJECTILE_SKIN = new NamespacedKey("esnsmp", "weapon_cosmetic_projectile");
    private static final NamespacedKey MEGA_ITEM = new NamespacedKey("esnsmp", "mega_item");
    private static final NamespacedKey RIFT_TYPE = new NamespacedKey("esnsmp", "riftwalker_type");
    private static final NamespacedKey STORE_EXCLUSIVE = new NamespacedKey("esnsmp", "store_exclusive");
    private static final NamespacedKey WARDEN_TYPE = new NamespacedKey("esnsmp", "immortal_warden_type");
    private static final NamespacedKey VOID_TYPE = new NamespacedKey("esnsmp", "void_warrior_type");
    private static final NamespacedKey ARMOR_SET = new NamespacedKey("esnsmp", "cosmetic_armor_set");
    private static final double COSMETIC_WEAPON_MULTIPLIER = 1.95;
    private static final double COSMETIC_ARMOR_DAMAGE_MULTIPLIER = 0.88;

    private static final Map<String, Cosmetic> COSMETICS = new LinkedHashMap<>();
    private static final Map<Category, List<Cosmetic>> BY_CATEGORY = new EnumMap<>(Category.class);
    private static final Map<String, String[]> COLLECTION_BENEFITS = new LinkedHashMap<>();

    static {
        for (Category category : Category.values()) BY_CATEGORY.put(category, new ArrayList<>());

        // Aura Packs
        add(Category.AURA, "void", "Void Aura", Material.ECHO_SHARD, 50, false);
        add(Category.AURA, "warden", "Warden Souls", Material.SCULK_CATALYST, 50, false);
        add(Category.AURA, "inferno", "Inferno Flames", Material.BLAZE_POWDER, 50, false);
        add(Category.AURA, "lightning", "Lightning Aura", Material.LIGHTNING_ROD, 50, false);
        add(Category.AURA, "celestial", "Celestial Stars", Material.NETHER_STAR, 50, false);
        add(Category.AURA, "frost", "Frost Aura", Material.BLUE_ICE, 50, false);
        add(Category.AURA, "dragon", "Dragon Aura", Material.DRAGON_BREATH, 50, false);

        // Exclusive Titles
        add(Category.TITLE, "voidwalker", "VOIDWALKER", Material.ECHO_SHARD, 40, false);
        add(Category.TITLE, "immortal", "IMMORTAL", Material.TOTEM_OF_UNDYING, 40, false);
        add(Category.TITLE, "celestial", "CELESTIAL", Material.NETHER_STAR, 40, false);
        add(Category.TITLE, "og", "OG", Material.CLOCK, 40, false);
        add(Category.TITLE, "bountyhunter", "BOUNTY HUNTER", Material.TARGET, 40, false);
        add(Category.TITLE, "bossslayer", "BOSS SLAYER", Material.NETHERITE_SWORD, 40, false);
        add(Category.TITLE, "supporter", "ESN SUPPORTER", Material.EMERALD, 40, false);

        // Spawn + Teleport Effects
        add(Category.TELEPORT, "portal", "Portal Arrival", Material.ENDER_PEARL, 35, false);
        add(Category.TELEPORT, "lightning", "Lightning Arrival", Material.LIGHTNING_ROD, 35, false);
        add(Category.TELEPORT, "void", "Void Arrival", Material.ECHO_SHARD, 35, false);
        add(Category.TELEPORT, "celestial", "Celestial Beam", Material.END_ROD, 35, false);
        add(Category.TELEPORT, "warden", "Warden Darkness Burst", Material.SCULK_SHRIEKER, 35, false);

        // Companion Pets
        add(Category.PET, "miniwarden", "Mini Warden", Material.SCULK_CATALYST, 75, false);
        add(Category.PET, "voidspirit", "Void Spirit", Material.ECHO_SHARD, 75, false);
        add(Category.PET, "infernospirit", "Inferno Spirit", Material.BLAZE_POWDER, 75, false);
        add(Category.PET, "celestialspirit", "Celestial Spirit", Material.NETHER_STAR, 75, false);
        add(Category.PET, "babydragon", "Baby Dragon", Material.DRAGON_HEAD, 75, false);

        // Chat Customization
        add(Category.CHAT_COLOR, "aqua", "Aqua Name", Material.CYAN_DYE, 30, false);
        add(Category.CHAT_COLOR, "purple", "Purple Name", Material.PURPLE_DYE, 30, false);
        add(Category.CHAT_COLOR, "gold", "Gold Name", Material.ORANGE_DYE, 30, false);
        add(Category.CHAT_COLOR, "green", "Green Name", Material.LIME_DYE, 30, false);
        add(Category.CHAT_COLOR, "red", "Red Name", Material.RED_DYE, 30, false);
        add(Category.CHAT_COLOR, "rainbow", "Rainbow Name", Material.NETHER_STAR, 60, false);
        add(Category.BRACKET, "square", "Square Brackets", Material.IRON_NUGGET, 20, false);
        add(Category.BRACKET, "angle", "Angle Brackets", Material.QUARTZ, 20, false);
        add(Category.BRACKET, "stars", "Star Brackets", Material.AMETHYST_SHARD, 20, false);
        add(Category.BRACKET, "void", "Void Brackets", Material.ECHO_SHARD, 20, false);
        add(Category.JOIN, "standard", "Supporter Join", Material.PAPER, 25, false);
        add(Category.JOIN, "celestial", "Celestial Join", Material.END_ROD, 25, false);
        add(Category.JOIN, "void", "Void Join", Material.ECHO_SHARD, 25, false);
        add(Category.JOIN, "legend", "Legend Join", Material.NETHER_STAR, 25, false);
        add(Category.EMOJI, "pack", "ESN Cosmetic Emoji Pack", Material.NAME_TAG, 40, false);

        // Base Weapon Cosmetic Packs — Realm 100+ combat tier.
        add(Category.WEAPON, "riftedge", "Rift Edge", Material.NETHERITE_SWORD, 80, false);
        add(Category.WEAPON, "wardenecho", "Warden Echo", Material.NETHERITE_SWORD, 80, false);
        add(Category.WEAPON, "celestialsaber", "Celestial Saber", Material.NETHERITE_SWORD, 80, false);
        add(Category.WEAPON, "infernofang", "Inferno Fang", Material.NETHERITE_SWORD, 80, false);
        add(Category.WEAPON, "frostbite", "Frostbite", Material.NETHERITE_SWORD, 80, false);

        // Supporter Ranks — cosmetic only.
        add(Category.SUPPORTER, "supporter", "SUPPORTER", Material.EMERALD, 0, false);
        add(Category.SUPPORTER, "elite", "ELITE", Material.DIAMOND, 0, false);
        add(Category.SUPPORTER, "legend", "LEGEND", Material.NETHER_STAR, 0, false);

        // Seasonal Collections
        collection("halloween", "Halloween Collection", Material.CARVED_PUMPKIN);
        collection("christmas", "Christmas Collection", Material.SNOW_BLOCK);
        collection("newyear", "New Year Collection", Material.FIREWORK_ROCKET);
        collection("anniversary", "ESN Anniversary Collection", Material.CLOCK);
        collection("summer", "Summer Collection", Material.SUNFLOWER);
        collection("birthday", "ESN Birthday Collection", Material.CAKE);

        // Named Collections
        collection("abyssreaper", "Abyss Reaper Collection", Material.ECHO_SHARD);
        collection("dragonlord", "Dragonlord Collection", Material.DRAGON_HEAD);
        collection("frostborn", "Frostborn Collection", Material.BLUE_ICE);
        collection("solarguardian", "Solar Guardian Collection", Material.GOLDEN_HELMET);
        collection("bloodmoon", "Bloodmoon Collection", Material.REDSTONE_BLOCK);
        collection("celestialknight", "Celestial Knight Collection", Material.NETHER_STAR);
        collection("ancienttitan", "Ancient Titan Collection", Material.NETHERITE_BLOCK);

        // Hidden collection-only aura/title/weapon cosmetics.
        hiddenSet("halloween", "Haunted Mist", "HAUNTED", "Pumpkin Reaper");
        hiddenSet("christmas", "Winter Spark", "FROSTMAS", "North Star");
        hiddenSet("newyear", "Midnight Fireworks", "NEW YEAR", "Midnight Edge");
        hiddenSet("anniversary", "Legacy Aura", "ESN ANNIVERSARY", "Legacy Blade");
        hiddenSet("summer", "Summer Sparks", "SUMMER", "Sunflare");
        hiddenSet("birthday", "Birthday Confetti", "ESN BIRTHDAY", "Celebration Blade");
        hiddenSet("abyssreaper", "Abyss Reaper Aura", "ABYSS REAPER", "Abyss Reaper");
        hiddenSet("dragonlord", "Dragonlord Aura", "DRAGONLORD", "Dragonlord Fang");
        hiddenSet("frostborn", "Frostborn Aura", "FROSTBORN", "Frostborn Blade");
        hiddenSet("solarguardian", "Solar Guardian Aura", "SOLAR GUARDIAN", "Solar Guardian Blade");
        hiddenSet("bloodmoon", "Bloodmoon Aura", "BLOODMOON", "Bloodmoon Blade");
        hiddenSet("celestialknight", "Celestial Knight Aura", "CELESTIAL KNIGHT", "Celestial Knight Blade");
        hiddenSet("ancienttitan", "Ancient Titan Aura", "ANCIENT TITAN", "Titan Relic Blade");

        for (String id : List.of("halloween","christmas","newyear","anniversary","summer","birthday",
                "abyssreaper","dragonlord","frostborn","solarguardian","bloodmoon","celestialknight","ancienttitan")) {
            COLLECTION_BENEFITS.put(id, new String[]{"aura:" + id, "title:" + id, "weapon:" + id, "armor:" + id});
        }
    }

    private static void add(Category category, String id, String name, Material material, int price, boolean hidden) {
        Cosmetic cosmetic = new Cosmetic(category, id, name, material, price, hidden);
        COSMETICS.put(cosmetic.key(), cosmetic);
        BY_CATEGORY.get(category).add(cosmetic);
    }

    private static void collection(String id, String name, Material material) {
        add(Category.COLLECTION, id, name, material, 200, false);
    }

    private static void hiddenSet(String id, String auraName, String titleName, String weaponName) {
        add(Category.AURA, id, auraName, Material.AMETHYST_SHARD, 0, true);
        add(Category.TITLE, id, titleName, Material.NAME_TAG, 0, true);
        add(Category.WEAPON, id, weaponName, Material.NETHERITE_SWORD, 0, true);
        add(Category.ARMOR, id, collectionBaseName(id) + " Armor Set", Material.NETHERITE_CHESTPLATE, 0, true);
    }

    private final ESNSMPPlugin plugin;
    private final Map<UUID, ArmorStand> pets = new HashMap<>();
    private BukkitTask ticker;
    private int pulse;

    public StoreCosmetics(ESNSMPPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (ticker != null) return;
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            pulse++;
            for (Player player : Bukkit.getOnlinePlayers()) {
                tickPet(player);
                if ((pulse & 1) == 0) tickAura(player);
            }
        }, 20L, 10L);
    }

    public static void registerStoreItems(Map<String, Supplier<ItemStack>> items) {
        for (Cosmetic cosmetic : COSMETICS.values()) {
            if (cosmetic.hidden()) continue;
            items.put(cosmetic.storeId(), () -> cosmeticToken(cosmetic));
        }
        items.put("cosmetictokens25", () -> tokenVoucher(25));
        items.put("cosmetictokens100", () -> tokenVoucher(100));
        items.put("cosmetictokens500", () -> tokenVoucher(500));
    }

    private static ItemStack cosmeticToken(Cosmetic cosmetic) {
        ItemStack item = new ItemStack(cosmetic.tokenMaterial());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "✦ " + cosmetic.name() + " UNLOCK ✦");
        List<String> tokenLore = new ArrayList<>();
        tokenLore.add(ChatColor.GOLD + "Permanent ESN cosmetic unlock");
        tokenLore.add(ChatColor.GRAY + cosmetic.category().display());
        tokenLore.add(ChatColor.GRAY + "Right-click to redeem.");
        tokenLore.add("");
        tokenLore.add(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "ESN STORE COSMETIC");
        if (cosmetic.category() == Category.WEAPON || cosmetic.category() == Category.ARMOR || cosmetic.category() == Category.COLLECTION) {
            tokenLore.add(ChatColor.LIGHT_PURPLE + "Combat tier: Realm 100+");
            tokenLore.add(ChatColor.GRAY + "Includes gear below Riftwalker / Warden / Void Warrior exclusives.");
        } else {
            tokenLore.add(ChatColor.GRAY + "Cosmetic only — no combat advantage.");
        }
        meta.setLore(tokenLore);
        meta.getPersistentDataContainer().set(TOKEN, PersistentDataType.STRING, cosmetic.key());
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack tokenVoucher(long amount) {
        ItemStack item = new ItemStack(Material.SUNFLOWER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "✦ " + amount + " ESN COSMETIC TOKENS ✦");
        meta.setLore(List.of(
                ChatColor.YELLOW + "Virtual ESN cosmetic currency",
                ChatColor.GRAY + "Right-click to add these tokens to your balance.",
                ChatColor.GRAY + "Cosmetic use only. No cash-out value."
        ));
        meta.getPersistentDataContainer().set(TOKEN_AMOUNT, PersistentDataType.LONG, amount);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void redeem(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        switch (event.getAction()) {
            case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> {}
            default -> { return; }
        }

        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();

        Long amount = pdc.get(TOKEN_AMOUNT, PersistentDataType.LONG);
        if (amount != null && amount > 0) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            addTokens(player, amount);
            consumeMainHand(player, item);
            player.sendMessage(ChatColor.GOLD + "Added " + amount + " ESN Cosmetic Tokens. Balance: " + getTokens(player));
            return;
        }

        String key = pdc.get(TOKEN, PersistentDataType.STRING);
        Cosmetic cosmetic = COSMETICS.get(key);
        if (cosmetic == null) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        if (has(player, cosmetic)) {
            player.sendMessage(ChatColor.YELLOW + "You already own " + cosmetic.name() + ".");
            return;
        }

        grant(player, cosmetic);
        consumeMainHand(player, item);
        player.sendMessage(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "ESN COSMETIC UNLOCKED!");
        player.sendMessage(ChatColor.GOLD + cosmetic.name() + ChatColor.GRAY + " is now permanently owned.");
        autoEquip(player, cosmetic);
    }

    private static void consumeMainHand(Player player, ItemStack item) {
        if (item.getAmount() <= 1) player.getInventory().setItemInMainHand(null);
        else item.setAmount(item.getAmount() - 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void teleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        String id = selected(player, Category.TELEPORT);
        if (id == null || !has(player, Category.TELEPORT, id)) return;
        Location target = event.getTo() == null ? player.getLocation() : event.getTo().clone();
        Bukkit.getScheduler().runTask(plugin, () -> playArrival(player, id, target));
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String join = selected(player, Category.JOIN);
        if (join != null && has(player, Category.JOIN, join)) {
            event.setJoinMessage(joinMessage(player, join));
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            String id = selected(player, Category.TELEPORT);
            if (id != null && has(player, Category.TELEPORT, id)) playArrival(player, id, player.getLocation());
            refreshPet(player);
        }, 10L);
    }

    @EventHandler
    public void respawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        removePet(player.getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            String id = selected(player, Category.TELEPORT);
            if (id != null && has(player, Category.TELEPORT, id)) playArrival(player, id, player.getLocation());
            refreshPet(player);
        }, 10L);
    }

    @EventHandler
    public void death(PlayerDeathEvent event) {
        removePet(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        removePet(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void shoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player) || !(event.getProjectile() instanceof Projectile projectile)) return;
        ItemStack bow = event.getBow();
        if (exclusiveWeapon(bow)) return;
        String skin = weaponSkin(bow);
        if (skin == null || !has(player, Category.WEAPON, skin)) return;
        normalizeCosmeticWeapon(bow);
        projectile.getPersistentDataContainer().set(PROJECTILE_SKIN, PersistentDataType.STRING, skin);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void weaponHit(EntityDamageByEntityEvent event) {
        Player attacker = null;
        String skin = null;

        if (event.getDamager() instanceof Player player) {
            ItemStack held = player.getInventory().getItemInMainHand();
            if (exclusiveWeapon(held)) return;
            attacker = player;
            skin = weaponSkin(held);
            if (skin != null) normalizeCosmeticWeapon(held);
        } else if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            attacker = player;
            skin = projectile.getPersistentDataContainer().get(PROJECTILE_SKIN, PersistentDataType.STRING);
        }

        if (attacker == null || skin == null || !has(attacker, Category.WEAPON, skin)) return;
        event.setDamage(event.getDamage() * COSMETIC_WEAPON_MULTIPLIER);
        weaponVisual(attacker, event.getEntity(), skin);
    }

    private void tickAura(Player player) {
        String aura = selected(player, Category.AURA);
        if (aura == null || !has(player, Category.AURA, aura) || player.isDead()) return;
        Location loc = player.getLocation().clone().add(0, 0.85, 0);
        World world = loc.getWorld();
        if (world == null) return;

        switch (aura) {
            case "void", "abyssreaper" -> world.spawnParticle(Particle.REVERSE_PORTAL, loc, 7, .45, .7, .45, .02);
            case "warden" -> world.spawnParticle(Particle.SCULK_SOUL, loc, 5, .42, .7, .42, .02);
            case "inferno", "solarguardian", "summer" -> world.spawnParticle(Particle.FLAME, loc, 6, .45, .65, .45, .02);
            case "lightning", "newyear" -> world.spawnParticle(Particle.ELECTRIC_SPARK, loc, 7, .45, .65, .45, .05);
            case "celestial", "celestialknight", "anniversary", "birthday" -> world.spawnParticle(Particle.END_ROD, loc, 5, .45, .7, .45, .01);
            case "frost", "frostborn", "christmas" -> world.spawnParticle(Particle.SNOWFLAKE, loc, 8, .5, .75, .5, .02);
            case "dragon", "dragonlord" -> world.spawnParticle(Particle.DRAGON_BREATH, loc, 6, .45, .7, .45, .02);
            case "bloodmoon", "halloween" -> world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 6, .5, .7, .5, .02);
            case "ancienttitan" -> {
                world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 4, .45, .7, .45, .02);
                world.spawnParticle(Particle.CRIT, loc, 4, .4, .55, .4, .01);
            }
            default -> world.spawnParticle(Particle.END_ROD, loc, 4, .4, .6, .4, .01);
        }
    }

    private void tickPet(Player player) {
        String id = selected(player, Category.PET);
        if (id == null || !has(player, Category.PET, id) || player.isDead()) {
            removePet(player.getUniqueId());
            return;
        }

        ArmorStand stand = pets.get(player.getUniqueId());
        if (stand == null || !stand.isValid() || stand.getWorld() != player.getWorld()) {
            removePet(player.getUniqueId());
            stand = spawnPet(player, id);
            pets.put(player.getUniqueId(), stand);
        }

        Vector facing = player.getLocation().getDirection().setY(0);
        if (facing.lengthSquared() < 0.01) facing = new Vector(0, 0, 1);
        facing.normalize();
        Vector right = new Vector(-facing.getZ(), 0, facing.getX());
        Location target = player.getLocation().clone()
                .add(right.multiply(1.15))
                .subtract(facing.multiply(.55))
                .add(0, 1.15, 0);
        stand.teleport(target);

        if ((pulse & 1) == 0) petParticle(stand.getLocation().clone().add(0, .65, 0), id);
    }

    private ArmorStand spawnPet(Player player, String id) {
        Material helmet = switch (id) {
            case "miniwarden" -> Material.SCULK_CATALYST;
            case "voidspirit" -> Material.ECHO_SHARD;
            case "infernospirit" -> Material.BLAZE_POWDER;
            case "celestialspirit" -> Material.NETHER_STAR;
            case "babydragon" -> Material.DRAGON_HEAD;
            default -> Material.AMETHYST_SHARD;
        };
        Cosmetic cosmetic = find(Category.PET, id);
        return player.getWorld().spawn(player.getLocation(), ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setSmall(true);
            stand.setMarker(true);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setSilent(true);
            stand.setPersistent(false);
            stand.setCustomName(ChatColor.LIGHT_PURPLE + (cosmetic == null ? "ESN Companion" : cosmetic.name()));
            stand.setCustomNameVisible(true);
            if (stand.getEquipment() != null) stand.getEquipment().setHelmet(new ItemStack(helmet));
        });
    }

    private static void petParticle(Location loc, String id) {
        World world = loc.getWorld();
        if (world == null) return;
        Particle particle = switch (id) {
            case "miniwarden" -> Particle.SCULK_SOUL;
            case "voidspirit" -> Particle.REVERSE_PORTAL;
            case "infernospirit" -> Particle.FLAME;
            case "celestialspirit" -> Particle.END_ROD;
            case "babydragon" -> Particle.DRAGON_BREATH;
            default -> Particle.END_ROD;
        };
        world.spawnParticle(particle, loc, 2, .18, .18, .18, .01);
    }

    private void refreshPet(Player player) {
        removePet(player.getUniqueId());
        tickPet(player);
    }

    private void removePet(UUID uuid) {
        ArmorStand stand = pets.remove(uuid);
        if (stand != null && stand.isValid()) stand.remove();
    }

    private static void playArrival(Player player, String id, Location loc) {
        World world = loc.getWorld();
        if (world == null) return;
        Location center = loc.clone().add(0, .4, 0);
        switch (id) {
            case "portal" -> {
                world.spawnParticle(Particle.PORTAL, center, 80, .9, 1.2, .9, .08);
                player.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, .8f, 1.15f);
            }
            case "lightning" -> {
                world.strikeLightningEffect(loc);
                world.spawnParticle(Particle.ELECTRIC_SPARK, center, 55, .8, 1.1, .8, .08);
            }
            case "void" -> {
                world.spawnParticle(Particle.REVERSE_PORTAL, center, 75, .9, 1.1, .9, .06);
                world.spawnParticle(Particle.SCULK_SOUL, center, 20, .55, .8, .55, .03);
            }
            case "celestial" -> {
                for (int y = 0; y < 5; y++) {
                    world.spawnParticle(Particle.END_ROD, loc.clone().add(0, y * .7, 0), 14, .35, .12, .35, .02);
                }
                player.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, .9f, 1.35f);
            }
            case "warden" -> {
                world.spawnParticle(Particle.SCULK_SOUL, center, 70, 1.0, .9, 1.0, .05);
                world.spawnParticle(Particle.SONIC_BOOM, center.clone().add(0, .8, 0), 1, 0, 0, 0, 0);
                player.playSound(loc, Sound.ENTITY_WARDEN_SONIC_BOOM, .7f, 1.1f);
            }
        }
    }

    private static String joinMessage(Player player, String id) {
        return switch (id) {
            case "celestial" -> ChatColor.DARK_AQUA + "✦ " + ChatColor.AQUA + decoratePlayerName(player) + ChatColor.AQUA + " descended into ESN " + ChatColor.DARK_AQUA + "✦";
            case "void" -> ChatColor.DARK_PURPLE + "◈ " + decoratePlayerName(player) + ChatColor.LIGHT_PURPLE + " emerged from the Void " + ChatColor.DARK_PURPLE + "◈";
            case "legend" -> ChatColor.GOLD + "★ " + decoratePlayerName(player) + ChatColor.YELLOW + " has entered the realm " + ChatColor.GOLD + "★";
            default -> ChatColor.GREEN + "✦ " + decoratePlayerName(player) + ChatColor.GRAY + " joined ESN SMP";
        };
    }

    private static void weaponVisual(Player player, Entity victim, String skin) {
        Location loc = victim.getLocation().clone().add(0, .8, 0);
        World world = loc.getWorld();
        if (world == null) return;

        Particle particle;
        Sound sound;
        if (skin.contains("rift") || skin.contains("abyss")) {
            particle = Particle.REVERSE_PORTAL; sound = Sound.ENTITY_ENDERMAN_TELEPORT;
        } else if (skin.contains("warden")) {
            particle = Particle.SCULK_SOUL; sound = Sound.ENTITY_WARDEN_HEARTBEAT;
        } else if (skin.contains("celestial") || skin.contains("anniversary") || skin.contains("birthday")) {
            particle = Particle.END_ROD; sound = Sound.BLOCK_AMETHYST_BLOCK_CHIME;
        } else if (skin.contains("inferno") || skin.contains("solar") || skin.contains("summer")) {
            particle = Particle.FLAME; sound = Sound.ENTITY_BLAZE_SHOOT;
        } else if (skin.contains("frost") || skin.contains("christmas")) {
            particle = Particle.SNOWFLAKE; sound = Sound.BLOCK_GLASS_BREAK;
        } else if (skin.contains("dragon")) {
            particle = Particle.DRAGON_BREATH; sound = Sound.ENTITY_ENDER_DRAGON_FLAP;
        } else if (skin.contains("blood") || skin.contains("halloween")) {
            particle = Particle.SOUL_FIRE_FLAME; sound = Sound.ENTITY_WITHER_HURT;
        } else {
            particle = Particle.CRIT; sound = Sound.ENTITY_PLAYER_ATTACK_CRIT;
        }
        world.spawnParticle(particle, loc, 10, .35, .4, .35, .03);
        player.playSound(player.getLocation(), sound, .35f, 1.25f);
    }

    private static String weaponSkin(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(WEAPON_SKIN, PersistentDataType.STRING);
    }

    private static boolean isWeapon(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        String name = item.getType().name();
        return name.endsWith("_SWORD") || name.endsWith("_AXE") || name.equals("BOW")
                || name.equals("CROSSBOW") || name.equals("TRIDENT") || name.equals("MACE");
    }

    private static void applyWeaponSkin(Player player, Cosmetic cosmetic) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isWeapon(item)) {
            player.sendMessage(ChatColor.RED + "Hold a sword, axe, bow, crossbow, trident, or mace first.");
            return;
        }
        if (exclusiveWeapon(item)) {
            player.sendMessage(ChatColor.RED + "ESN Store Exclusive weapons cannot be converted into cosmetic weapons.");
            player.sendMessage(ChatColor.GRAY + "Riftwalker, Immortal Warden and Void Warrior must remain above the cosmetic tier.");
            return;
        }

        normalizeCosmeticWeapon(item);

        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.removeIf(line -> {
            String plain = ChatColor.stripColor(line);
            return plain.startsWith("[ESN Weapon Cosmetic]")
                    || plain.startsWith("Skin: ")
                    || plain.startsWith("Combat Tier: ")
                    || plain.startsWith("Damage Multiplier: ")
                    || plain.startsWith("MEGA CRATE TIER ")
                    || plain.startsWith("Power ")
                    || plain.startsWith("ENCHANT LEVELS: ")
                    || plain.startsWith("Maximum Realm enchant level:")
                    || plain.startsWith("Sharpness/Looting/Fire/Knockback/Smite/Bane:")
                    || plain.startsWith("Power/Punch/Flame/Infinity:")
                    || plain.startsWith("Unbreaking:");
        });
        lore.add("");
        lore.add(ChatColor.DARK_PURPLE + "[ESN Weapon Cosmetic]");
        lore.add(ChatColor.GRAY + "Skin: " + ChatColor.LIGHT_PURPLE + cosmetic.name());
        lore.add(ChatColor.GOLD + "Combat Tier: Realm 100+");
        lore.add(ChatColor.LIGHT_PURPLE + "Damage Multiplier: 1.95x");
        lore.add(ChatColor.GRAY + "Enchant power: 225");
        lore.add(ChatColor.DARK_GRAY + "Stronger than Realm 100 • Below ESN Store Exclusives");
        meta.setLore(lore);
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "✦ " + cosmetic.name().toUpperCase(Locale.ROOT) + " ✦");
        meta.getPersistentDataContainer().set(WEAPON_SKIN, PersistentDataType.STRING, cosmetic.id());
        meta.getPersistentDataContainer().remove(MEGA_ITEM);
        item.setItemMeta(meta);
        player.sendMessage(ChatColor.GREEN + "Applied weapon cosmetic: " + ChatColor.LIGHT_PURPLE + cosmetic.name());
        player.sendMessage(ChatColor.GOLD + "Weapon upgraded to ESN Cosmetic Tier — above Realm 100, below Store Exclusives.");
    }

    private static boolean exclusiveWeapon(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(RIFT_TYPE, PersistentDataType.STRING)
                || pdc.has(STORE_EXCLUSIVE, PersistentDataType.BYTE)
                || pdc.has(WARDEN_TYPE, PersistentDataType.STRING)
                || pdc.has(VOID_TYPE, PersistentDataType.STRING);
    }

    private static void normalizeCosmeticWeapon(ItemStack item) {
        if (item == null || item.getType().isAir() || exclusiveWeapon(item)) return;

        int templateSlot = switch (item.getType()) {
            case BOW -> 9;
            case CROSSBOW -> 10;
            default -> item.getType().name().endsWith("_AXE") ? 1 : 0;
        };

        ItemStack template = MegaCrates.item(99, templateSlot);
        for (Enchantment enchantment : new ArrayList<>(item.getEnchantments().keySet())) {
            item.removeEnchantment(enchantment);
        }
        for (Map.Entry<Enchantment, Integer> entry : template.getEnchantments().entrySet()) {
            item.addUnsafeEnchantment(entry.getKey(), entry.getValue());
        }

        // Cosmetic-tier weapons are combat upgrades, not farming upgrades.
        item.removeEnchantment(Enchantment.LOOTING);

        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().remove(MEGA_ITEM);
        item.setItemMeta(meta);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void cosmeticArmorDefense(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        String set = fullCosmeticArmorSet(player);
        if (set == null || !has(player, Category.ARMOR, set)) return;
        event.setDamage(event.getDamage() * COSMETIC_ARMOR_DAMAGE_MULTIPLIER);
    }

    private static void equipArmorSet(Player player, Cosmetic cosmetic) {
        if (cosmetic == null || cosmetic.category() != Category.ARMOR) return;

        ItemStack[] oldArmor = {
                player.getInventory().getHelmet(),
                player.getInventory().getChestplate(),
                player.getInventory().getLeggings(),
                player.getInventory().getBoots()
        };
        for (ItemStack old : oldArmor) {
            if (old == null || old.getType().isAir()) continue;
            if (armorSetId(old) != null) continue;
            player.getInventory().addItem(old.clone()).values().forEach(left ->
                    player.getWorld().dropItemNaturally(player.getLocation(), left));
        }

        player.getInventory().setHelmet(cosmeticArmorPiece(cosmetic.id(), 5, "Helmet"));
        player.getInventory().setChestplate(cosmeticArmorPiece(cosmetic.id(), 6, "Chestplate"));
        player.getInventory().setLeggings(cosmeticArmorPiece(cosmetic.id(), 7, "Leggings"));
        player.getInventory().setBoots(cosmeticArmorPiece(cosmetic.id(), 8, "Boots"));
        setSelected(player, Category.ARMOR, cosmetic.id());

        player.sendMessage(ChatColor.GREEN + "Equipped " + ChatColor.LIGHT_PURPLE + cosmetic.name() + ChatColor.GREEN + ".");
        player.sendMessage(ChatColor.GOLD + "Realm 100+ armor tier active: 225 enchants + 12% full-set damage reduction.");
        player.sendMessage(ChatColor.GRAY + "Riftwalker / Immortal Warden / Void Warrior exclusives remain stronger.");
    }

    private static ItemStack cosmeticArmorPiece(String setId, int realmSlot, String pieceName) {
        ItemStack item = MegaCrates.item(99, realmSlot).clone();
        ItemMeta meta = item.getItemMeta();
        String setName = collectionBaseName(setId);

        meta.setDisplayName(armorColor(setId) + "" + ChatColor.BOLD + "✦ " +
                setName.toUpperCase(Locale.ROOT) + " " + pieceName.toUpperCase(Locale.ROOT) + " ✦");
        meta.setLore(List.of(
                ChatColor.DARK_PURPLE + "[ESN Collection Armor]",
                ChatColor.GRAY + "Set: " + armorColor(setId) + setName,
                ChatColor.GOLD + "Combat Tier: Realm 100+",
                ChatColor.LIGHT_PURPLE + "Enchant Power: 225",
                ChatColor.AQUA + "Full Set: 12% incoming damage reduction",
                ChatColor.DARK_GRAY + "Above Realm 100 • Below ESN Store Exclusives",
                ChatColor.GOLD + "" + ChatColor.BOLD + "UNBREAKABLE"
        ));
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().remove(MEGA_ITEM);
        meta.getPersistentDataContainer().set(ARMOR_SET, PersistentDataType.STRING, setId);
        item.setItemMeta(meta);
        return item;
    }

    private static void unequipCosmeticArmor(Player player) {
        ItemStack[] equipped = {
                player.getInventory().getHelmet(),
                player.getInventory().getChestplate(),
                player.getInventory().getLeggings(),
                player.getInventory().getBoots()
        };

        for (int i = 0; i < equipped.length; i++) {
            ItemStack piece = equipped[i];
            if (armorSetId(piece) == null) continue;
            player.getInventory().addItem(piece.clone()).values().forEach(left ->
                    player.getWorld().dropItemNaturally(player.getLocation(), left));
            switch (i) {
                case 0 -> player.getInventory().setHelmet(null);
                case 1 -> player.getInventory().setChestplate(null);
                case 2 -> player.getInventory().setLeggings(null);
                case 3 -> player.getInventory().setBoots(null);
                default -> {}
            }
        }
    }

    private static String fullCosmeticArmorSet(Player player) {
        String helmet = armorSetId(player.getInventory().getHelmet());
        if (helmet == null) return null;
        String chest = armorSetId(player.getInventory().getChestplate());
        String legs = armorSetId(player.getInventory().getLeggings());
        String boots = armorSetId(player.getInventory().getBoots());
        return helmet.equals(chest) && helmet.equals(legs) && helmet.equals(boots) ? helmet : null;
    }

    private static String armorSetId(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(ARMOR_SET, PersistentDataType.STRING);
    }

    private static String collectionBaseName(String id) {
        Cosmetic collection = find(Category.COLLECTION, id);
        if (collection != null) return collection.name().replace(" Collection", "");
        return switch (id) {
            case "newyear" -> "New Year";
            case "abyssreaper" -> "Abyss Reaper";
            case "dragonlord" -> "Dragonlord";
            case "frostborn" -> "Frostborn";
            case "solarguardian" -> "Solar Guardian";
            case "bloodmoon" -> "Bloodmoon";
            case "celestialknight" -> "Celestial Knight";
            case "ancienttitan" -> "Ancient Titan";
            case "anniversary" -> "ESN Anniversary";
            case "birthday" -> "ESN Birthday";
            default -> Character.toUpperCase(id.charAt(0)) + id.substring(1);
        };
    }

    private static ChatColor armorColor(String id) {
        if (id.contains("blood") || id.contains("halloween")) return ChatColor.DARK_RED;
        if (id.contains("frost") || id.contains("christmas")) return ChatColor.AQUA;
        if (id.contains("solar") || id.contains("summer") || id.contains("anniversary") || id.contains("birthday")) return ChatColor.GOLD;
        if (id.contains("celestial") || id.contains("newyear")) return ChatColor.LIGHT_PURPLE;
        if (id.contains("dragon") || id.contains("abyss")) return ChatColor.DARK_PURPLE;
        if (id.contains("titan")) return ChatColor.YELLOW;
        return ChatColor.LIGHT_PURPLE;
    }

    public static String supporterPrefix(Player player) {
        String id = selected(player, Category.SUPPORTER);
        if (id == null || !has(player, Category.SUPPORTER, id)) return "";
        return switch (id) {
            case "legend" -> ChatColor.GOLD + "[LEGEND] ";
            case "elite" -> ChatColor.AQUA + "[ELITE] ";
            default -> ChatColor.GREEN + "[SUPPORTER] ";
        };
    }

    public static String storeTitlePrefix(Player player) {
        String id = selected(player, Category.TITLE);
        Cosmetic cosmetic = find(Category.TITLE, id);
        if (cosmetic == null || !has(player, cosmetic)) return "";

        String bracket = selected(player, Category.BRACKET);
        ChatColor color = titleColor(id);
        return switch (bracket == null ? "square" : bracket) {
            case "angle" -> color + "<" + cosmetic.name() + "> ";
            case "stars" -> color + "✦ " + cosmetic.name() + " ✦ ";
            case "void" -> ChatColor.DARK_PURPLE + "◈ " + color + cosmetic.name() + ChatColor.DARK_PURPLE + " ◈ ";
            default -> color + "[" + cosmetic.name() + "] ";
        };
    }

    private static ChatColor titleColor(String id) {
        if (id == null) return ChatColor.LIGHT_PURPLE;
        if (id.contains("solar") || id.contains("anniversary") || id.contains("birthday") || id.equals("og")) return ChatColor.GOLD;
        if (id.contains("frost") || id.contains("celestial") || id.equals("immortal")) return ChatColor.AQUA;
        if (id.contains("blood") || id.contains("halloween")) return ChatColor.RED;
        if (id.contains("dragon") || id.contains("void") || id.contains("abyss")) return ChatColor.DARK_PURPLE;
        return ChatColor.LIGHT_PURPLE;
    }

    public static String decoratePlayerName(Player player) {
        String id = selected(player, Category.CHAT_COLOR);
        if (id == null || !has(player, Category.CHAT_COLOR, id)) return ChatColor.WHITE + player.getName();
        return switch (id) {
            case "aqua" -> ChatColor.AQUA + player.getName();
            case "purple" -> ChatColor.LIGHT_PURPLE + player.getName();
            case "gold" -> ChatColor.GOLD + player.getName();
            case "green" -> ChatColor.GREEN + player.getName();
            case "red" -> ChatColor.RED + player.getName();
            case "rainbow" -> rainbow(player.getName());
            default -> ChatColor.WHITE + player.getName();
        };
    }

    private static String rainbow(String text) {
        ChatColor[] colors = {ChatColor.RED, ChatColor.GOLD, ChatColor.YELLOW, ChatColor.GREEN, ChatColor.AQUA, ChatColor.LIGHT_PURPLE};
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) out.append(colors[i % colors.length]).append(text.charAt(i));
        return out.toString();
    }

    public static String applyChatEmojis(Player player, String message) {
        String enabled = selected(player, Category.EMOJI);
        if (!"pack".equals(enabled) || !has(player, Category.EMOJI, "pack")) return message;
        return message
                .replace(":star:", "★")
                .replace(":void:", "◈")
                .replace(":heart:", "♥")
                .replace(":spark:", "✦")
                .replace(":sword:", "⚔");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("cosmetictokens")) {
            player.sendMessage(ChatColor.GOLD + "ESN Cosmetic Tokens: " + ChatColor.WHITE + getTokens(player));
            return true;
        }

        if (args.length == 0) {
            CosmeticsGui.open(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("tokens")) {
            player.sendMessage(ChatColor.GOLD + "ESN Cosmetic Tokens: " + ChatColor.WHITE + getTokens(player));
            return true;
        }

        if (args[0].equalsIgnoreCase("buy")) {
            buy(player, args);
            return true;
        }

        if (args[0].equalsIgnoreCase("admin")) {
            admin(player, args);
            return true;
        }

        Category category = Category.from(args[0]);
        if (category == null) {
            player.sendMessage(ChatColor.RED + "Unknown cosmetics category. Use /cosmetics");
            return true;
        }

        if (args.length == 1 || args[1].equalsIgnoreCase("list")) {
            listCategory(player, category);
            return true;
        }

        if (args[1].equalsIgnoreCase("off") || args[1].equalsIgnoreCase("none")) {
            selectionKey(player, category).ifPresent(player.getPersistentDataContainer()::remove);
            if (category == Category.PET) refreshPet(player);
            if (category == Category.ARMOR) unequipCosmeticArmor(player);
            player.sendMessage(ChatColor.YELLOW + category.display() + " disabled.");
            return true;
        }

        String id = args[1].toLowerCase(Locale.ROOT);
        Cosmetic cosmetic = find(category, id);
        if (cosmetic == null) {
            player.sendMessage(ChatColor.RED + "Unknown " + category.display() + " cosmetic.");
            return true;
        }

        if (!has(player, cosmetic)) {
            if (isAdmin(player)) grant(player, cosmetic);
            else {
                player.sendMessage(ChatColor.RED + "You have not unlocked " + cosmetic.name() + ".");
                return true;
            }
        }

        if (category == Category.COLLECTION) {
            player.sendMessage(ChatColor.GREEN + "Owned collection: " + ChatColor.GOLD + cosmetic.name());
            return true;
        }

        if (category == Category.WEAPON) {
            applyWeaponSkin(player, cosmetic);
            return true;
        }

        if (category == Category.ARMOR) {
            equipArmorSet(player, cosmetic);
            return true;
        }

        setSelected(player, category, cosmetic.id());
        if (category == Category.PET) refreshPet(player);
        if (category == Category.EMOJI) player.sendMessage(ChatColor.GREEN + "Cosmetic emoji pack enabled.");
        else player.sendMessage(ChatColor.GREEN + "Equipped " + category.display() + ": " + ChatColor.GOLD + cosmetic.name());
        return true;
    }

    private void overview(Player player) {
        player.sendMessage(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "ESN STORE COSMETICS");
        player.sendMessage(ChatColor.GRAY + "Tokens: " + ChatColor.GOLD + getTokens(player));
        for (Category category : Category.values()) {
            long owned = BY_CATEGORY.get(category).stream().filter(c -> has(player, c)).count();
            player.sendMessage(ChatColor.LIGHT_PURPLE + "• " + category.key() + ChatColor.GRAY +
                    " — " + owned + " owned" + (released(category) ? ChatColor.GREEN + " [RELEASED]" : ChatColor.DARK_GRAY + " [STAGED]"));
        }
        player.sendMessage(ChatColor.GRAY + "Use /cosmetics <category> list");
        player.sendMessage(ChatColor.GRAY + "Use /cosmetics <category> <id> to equip/apply.");
        player.sendMessage(ChatColor.GRAY + "Kill Effects remain under /killeffects.");
    }

    private void listCategory(Player player, Category category) {
        player.sendMessage(ChatColor.GOLD + category.display() + ":");
        for (Cosmetic cosmetic : BY_CATEGORY.get(category)) {
            if (cosmetic.hidden() && !has(player, cosmetic) && !isAdmin(player)) continue;
            boolean owned = has(player, cosmetic);
            boolean released = released(cosmetic);
            String price = cosmetic.tokenPrice() > 0 ? " • " + cosmetic.tokenPrice() + " tokens" : "";
            player.sendMessage((owned ? ChatColor.GREEN : ChatColor.DARK_GRAY) +
                    "• " + cosmetic.id() + " — " + cosmetic.name() + price +
                    (released ? ChatColor.GREEN + " [LIVE]" : ChatColor.DARK_GRAY + " [STAGED]"));
        }
    }

    private void buy(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.YELLOW + "/cosmetics buy <category> <id>");
            return;
        }
        Category category = Category.from(args[1]);
        Cosmetic cosmetic = category == null ? null : find(category, args[2].toLowerCase(Locale.ROOT));
        if (cosmetic == null || cosmetic.hidden()) {
            player.sendMessage(ChatColor.RED + "Unknown cosmetic.");
            return;
        }
        if (!released(cosmetic)) {
            player.sendMessage(ChatColor.YELLOW + "That cosmetic has not been released yet.");
            return;
        }
        if (cosmetic.tokenPrice() <= 0) {
            player.sendMessage(ChatColor.YELLOW + "That cosmetic is not sold for ESN Cosmetic Tokens.");
            return;
        }
        if (has(player, cosmetic)) {
            player.sendMessage(ChatColor.YELLOW + "You already own " + cosmetic.name() + ".");
            return;
        }
        long balance = getTokens(player);
        if (balance < cosmetic.tokenPrice()) {
            player.sendMessage(ChatColor.RED + "You need " + cosmetic.tokenPrice() + " tokens. Balance: " + balance);
            return;
        }
        setTokens(player, balance - cosmetic.tokenPrice());
        grant(player, cosmetic);
        autoEquip(player, cosmetic);
        player.sendMessage(ChatColor.GREEN + "Unlocked " + cosmetic.name() + " for " + cosmetic.tokenPrice() + " ESN Cosmetic Tokens.");
    }

    private void admin(Player player, String[] args) {
        if (!isAdmin(player)) {
            player.sendMessage(ChatColor.RED + "No permission.");
            return;
        }
        if (args.length < 2) {
            adminHelp(player);
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "unlockall" -> {
                Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : player;
                if (target == null) {
                    player.sendMessage(ChatColor.RED + "Player must be online.");
                    return;
                }
                for (Cosmetic cosmetic : COSMETICS.values()) grant(target, cosmetic);
                setTokens(target, Math.max(getTokens(target), 10000L));
                player.sendMessage(ChatColor.GREEN + "Unlocked every staged ESN cosmetic for " + target.getName() + ".");
                target.sendMessage(ChatColor.GOLD + "All ESN cosmetics unlocked for admin testing.");
            }
            case "unlock" -> {
                if (args.length < 5) {
                    player.sendMessage(ChatColor.YELLOW + "/cosmetics admin unlock <player> <category> <id>");
                    return;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                Category category = Category.from(args[3]);
                Cosmetic cosmetic = category == null ? null : find(category, args[4].toLowerCase(Locale.ROOT));
                if (target == null || cosmetic == null) {
                    player.sendMessage(ChatColor.RED + "Online player/category/cosmetic not found.");
                    return;
                }
                grant(target, cosmetic);
                player.sendMessage(ChatColor.GREEN + "Unlocked " + cosmetic.name() + " for " + target.getName() + ".");
            }
            case "tokens" -> {
                if (args.length < 4) {
                    player.sendMessage(ChatColor.YELLOW + "/cosmetics admin tokens <player> <amount>");
                    return;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    player.sendMessage(ChatColor.RED + "Player must be online.");
                    return;
                }
                try {
                    long amount = Long.parseLong(args[3]);
                    addTokens(target, amount);
                    player.sendMessage(ChatColor.GREEN + "Adjusted " + target.getName() + " by " + amount + " cosmetic tokens. New balance: " + getTokens(target));
                } catch (NumberFormatException ex) {
                    player.sendMessage(ChatColor.RED + "Amount must be a number.");
                }
            }
            case "release" -> {
                if (args.length < 4) {
                    player.sendMessage(ChatColor.YELLOW + "/cosmetics admin release <category|category:id> <on|off>");
                    return;
                }
                boolean enabled = args[3].equalsIgnoreCase("on") || args[3].equalsIgnoreCase("true");
                String target = args[2].toLowerCase(Locale.ROOT);
                if (target.contains(":")) {
                    String[] split = target.split(":", 2);
                    Category category = Category.from(split[0]);
                    Cosmetic cosmetic = category == null ? null : find(category, split[1]);
                    if (cosmetic == null || cosmetic.hidden()) {
                        player.sendMessage(ChatColor.RED + "Unknown public cosmetic.");
                        return;
                    }
                    plugin.getConfig().set("store-cosmetics.released-items." + category.key() + "." + cosmetic.id(), enabled);
                    plugin.saveConfig();
                    player.sendMessage(ChatColor.GREEN + cosmetic.name() + " release = " + enabled);
                } else {
                    Category category = Category.from(target);
                    if (category == null) {
                        player.sendMessage(ChatColor.RED + "Unknown category.");
                        return;
                    }
                    plugin.getConfig().set("store-cosmetics.released." + category.key(), enabled);
                    plugin.saveConfig();
                    player.sendMessage(ChatColor.GREEN + category.display() + " release = " + enabled);
                }
            }
            case "status" -> {
                player.sendMessage(ChatColor.GOLD + "ESN Cosmetics Release Status:");
                for (Category category : Category.values()) {
                    player.sendMessage(ChatColor.GRAY + category.key() + ": " + (released(category) ? ChatColor.GREEN + "LIVE" : ChatColor.YELLOW + "STAGED"));
                }
            }
            default -> adminHelp(player);
        }
    }

    private static void adminHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "ESN Cosmetics Admin:");
        player.sendMessage(ChatColor.GRAY + "/cosmetics admin unlockall [player]");
        player.sendMessage(ChatColor.GRAY + "/cosmetics admin unlock <player> <category> <id>");
        player.sendMessage(ChatColor.GRAY + "/cosmetics admin tokens <player> <amount>");
        player.sendMessage(ChatColor.GRAY + "/cosmetics admin release <category|category:id> <on|off>");
        player.sendMessage(ChatColor.GRAY + "/cosmetics admin status");
    }

    private static void autoEquip(Player player, Cosmetic cosmetic) {
        if (cosmetic.category() == Category.COLLECTION) return;
        if (cosmetic.category() == Category.WEAPON || cosmetic.category() == Category.ARMOR) return;
        setSelected(player, cosmetic.category(), cosmetic.id());
    }

    private static void grant(Player player, Cosmetic cosmetic) {
        Set<String> unlocks = unlocks(player);
        if (unlocks.add(cosmetic.key())) saveUnlocks(player, unlocks);

        if (cosmetic.category() == Category.COLLECTION) {
            String[] benefits = COLLECTION_BENEFITS.get(cosmetic.id());
            if (benefits != null) {
                for (String benefit : benefits) {
                    Cosmetic bonus = COSMETICS.get(benefit);
                    if (bonus != null && unlocks.add(bonus.key())) {}
                }
                saveUnlocks(player, unlocks);
            }
        }
    }

    private static boolean has(Player player, Cosmetic cosmetic) {
        return cosmetic != null && has(player, cosmetic.category(), cosmetic.id());
    }

    private static boolean has(Player player, Category category, String id) {
        if (id == null) return false;
        if (isAdmin(player)) return true;
        return unlocks(player).contains(category.key() + ":" + id);
    }

    private static Set<String> unlocks(Player player) {
        String raw = player.getPersistentDataContainer().getOrDefault(UNLOCKS, PersistentDataType.STRING, "");
        Set<String> result = new LinkedHashSet<>();
        if (raw.isBlank()) return result;
        Arrays.stream(raw.split(";")).map(String::trim).filter(s -> !s.isBlank()).forEach(result::add);
        return result;
    }

    private static void saveUnlocks(Player player, Collection<String> unlocks) {
        player.getPersistentDataContainer().set(UNLOCKS, PersistentDataType.STRING, String.join(";", unlocks));
    }

    private static void setSelected(Player player, Category category, String id) {
        selectionKey(player, category).ifPresent(key -> player.getPersistentDataContainer().set(key, PersistentDataType.STRING, id));
    }

    private static String selected(Player player, Category category) {
        return selectionKey(player, category)
                .map(key -> player.getPersistentDataContainer().get(key, PersistentDataType.STRING))
                .orElse(null);
    }

    private static java.util.Optional<NamespacedKey> selectionKey(Player player, Category category) {
        return java.util.Optional.of(new NamespacedKey("esnsmp", "cosmetic_selected_" + category.key()));
    }

    private static Cosmetic find(Category category, String id) {
        if (category == null || id == null) return null;
        return COSMETICS.get(category.key() + ":" + id.toLowerCase(Locale.ROOT));
    }

    private boolean released(Cosmetic cosmetic) {
        if (cosmetic == null || cosmetic.hidden()) return false;
        return plugin.getConfig().getBoolean("store-cosmetics.released." + cosmetic.category().key(), false)
                || plugin.getConfig().getBoolean("store-cosmetics.released-items." + cosmetic.category().key() + "." + cosmetic.id(), false);
    }

    private boolean released(Category category) {
        return plugin.getConfig().getBoolean("store-cosmetics.released." + category.key(), false);
    }

    private static long getTokens(Player player) {
        return Math.max(0L, player.getPersistentDataContainer().getOrDefault(BALANCE, PersistentDataType.LONG, 0L));
    }

    private static void setTokens(Player player, long amount) {
        player.getPersistentDataContainer().set(BALANCE, PersistentDataType.LONG, Math.max(0L, amount));
    }

    private static void addTokens(Player player, long amount) {
        long current = getTokens(player);
        long next;
        try {
            next = Math.addExact(current, amount);
        } catch (ArithmeticException ex) {
            next = amount > 0 ? Long.MAX_VALUE : 0L;
        }
        setTokens(player, next);
    }

    private static boolean isAdmin(Player player) {
        return player.isOp() ||
                player.hasPermission("esnsmp.owner") ||
                player.hasPermission("esnsmp.items") ||
                player.hasPermission("esnsmp.admin") ||
                player.hasPermission("esnsmp.staff.admin");
    }

    @Override
    public void close() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        for (ArmorStand stand : new ArrayList<>(pets.values())) {
            if (stand != null && stand.isValid()) stand.remove();
        }
        pets.clear();
    }
}
