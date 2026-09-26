package com.esn.smp.gameplay;

import com.esn.smp.ESNSMPPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Store-exclusive Riftwalker Bundle.
 *
 * Design notes:
 * - Items are truly unbreakable via ItemMeta plus a damage-event safety net.
 * - No per-item repeating scans or persistent caches are used.
 * - One lightweight passive task checks online players twice per second.
 */
public final class RiftwalkerBundle implements Listener {
    public static final String BLADE_ID = "riftblade";
    public static final String WINGS_ID = "riftwings";
    public static final String BOOTS_ID = "phaseboots";
    public static final String BOW_ID = "riftbow";
    public static final String CORE_ID = "riftcore";
    public static final String COMPASS_ID = "voidcompass";

    private static final NamespacedKey RIFT_TYPE = NamespacedKey.fromString("esnsmp:riftwalker_type");
    private static final NamespacedKey STORE_EXCLUSIVE = NamespacedKey.fromString("esnsmp:store_exclusive");
    private static final NamespacedKey RIFT_PROJECTILE = NamespacedKey.fromString("esnsmp:rift_projectile");
    private static final NamespacedKey RIFT_BURST = NamespacedKey.fromString("esnsmp:rift_burst");
    private static final NamespacedKey DEATH_WORLD = NamespacedKey.fromString("esnsmp:rift_death_world");
    private static final NamespacedKey DEATH_X = NamespacedKey.fromString("esnsmp:rift_death_x");
    private static final NamespacedKey DEATH_Y = NamespacedKey.fromString("esnsmp:rift_death_y");
    private static final NamespacedKey DEATH_Z = NamespacedKey.fromString("esnsmp:rift_death_z");

    private static final Particle.DustOptions PURPLE_DUST =
            new Particle.DustOptions(Color.fromRGB(118, 30, 190), 1.25f);
    private static final Particle.DustOptions BLACK_DUST =
            new Particle.DustOptions(Color.fromRGB(18, 18, 25), 1.0f);

    private final ESNSMPPlugin plugin;
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastSneak = new ConcurrentHashMap<>();
    private final Map<UUID, Long> dashBonusUntil = new ConcurrentHashMap<>();
    private final Map<String, Long> activationDebounce = new ConcurrentHashMap<>();

    public RiftwalkerBundle(ESNSMPPlugin plugin) {
        this.plugin = plugin;

        // Lightweight passive effects only; one task for the entire server.
        Bukkit.getScheduler().runTaskTimer(plugin, this::passiveTick, 20L, 10L);
    }

    public static ItemStack riftBlade() {
        ItemStack item = base(Material.NETHERITE_SWORD, BLADE_ID, "RIFTBLADE",
                "Right-click: Rift Dash up to 8 blocks.",
                "Your next melee hit after dashing deals bonus damage.",
                "Cooldown: 8 seconds.",
                "Realm 100 enchant power: 225 (Looting intentionally excluded).");
        applyRealm100SwordEnchants(item);
        return item;
    }

    public static ItemStack riftWings() {
        return base(Material.ELYTRA, WINGS_ID, "RIFT WINGS",
                "Sneak while gliding: forward Rift Boost.",
                "Leaves a purple-black rift trail while flying.",
                "Boost cooldown: 6 seconds.");
    }

    public static ItemStack phaseBoots() {
        ItemStack item = base(Material.NETHERITE_BOOTS, BOOTS_ID, "PHASE BOOTS",
                "Permanent Speed I while worn.",
                "Reduces fall damage by 75%.",
                "Double-sneak: Phase Step. Cooldown: 10 seconds.",
                "Realm 100 enchant power: 225.");
        applyRealm100BootEnchants(item);
        return item;
    }

    public static ItemStack riftBow() {
        ItemStack item = base(Material.BOW, BOW_ID, "RIFT BOW",
                "Arrows inflict Slowness II.",
                "Fully charged shots can trigger a Rift Burst.",
                "Rift Burst damages nearby enemies without block damage.");
        item.addUnsafeEnchantment(Enchantment.POWER, 8);
        item.addUnsafeEnchantment(Enchantment.PUNCH, 2);
        return item;
    }

    public static ItemStack riftCore() {
        return base(Material.ECHO_SHARD, CORE_ID, "RIFT CORE",
                "Right-click: activate Rift Armor.",
                "Resistance II + Absorption for 8 seconds.",
                "Cooldown: 45 seconds.");
    }

    public static ItemStack voidCompass() {
        return base(Material.COMPASS, COMPASS_ID, "VOID COMPASS",
                "Right-click: bind compass to your last death location.",
                "Sneak + right-click: show death world and distance.",
                "Your death location is remembered across reconnects.");
    }

    private static ItemStack base(Material material, String type, String name, String... abilityLore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "✦ " + name + " ✦");

        List<String> lore = new ArrayList<>();
        for (String line : abilityLore) lore.add(ChatColor.LIGHT_PURPLE + line);
        lore.add("");
        lore.add(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "✦ ESN STORE EXCLUSIVE ✦");
        lore.add(ChatColor.GRAY + "Part of the Riftwalker Bundle");
        lore.add(ChatColor.GRAY + "Not obtainable from crates, bosses, or the Season Pass.");
        lore.add(ChatColor.GOLD + "" + ChatColor.BOLD + "UNBREAKABLE");
        meta.setLore(lore);
        meta.setUnbreakable(true);

        if (RIFT_TYPE != null) meta.getPersistentDataContainer().set(RIFT_TYPE, PersistentDataType.STRING, type);
        if (STORE_EXCLUSIVE != null) meta.getPersistentDataContainer().set(STORE_EXCLUSIVE, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private static String type(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta() || RIFT_TYPE == null) return "";
        return item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(RIFT_TYPE, PersistentDataType.STRING, "");
    }

    private static boolean is(ItemStack item, String expected) {
        return expected.equalsIgnoreCase(type(item));
    }


    private static void applyRealm100SwordEnchants(ItemStack item) {
        item.addUnsafeEnchantment(Enchantment.UNBREAKING, 225);
        item.addUnsafeEnchantment(Enchantment.SHARPNESS, 225);
        item.addUnsafeEnchantment(Enchantment.FIRE_ASPECT, 225);
        item.addUnsafeEnchantment(Enchantment.KNOCKBACK, 225);
        item.addUnsafeEnchantment(Enchantment.SMITE, 225);
        item.addUnsafeEnchantment(Enchantment.BANE_OF_ARTHROPODS, 225);
        // Intentionally NO Looting on the Riftblade.
    }

    private static void applyRealm100BootEnchants(ItemStack item) {
        item.addUnsafeEnchantment(Enchantment.UNBREAKING, 225);
        item.addUnsafeEnchantment(Enchantment.PROTECTION, 225);
        item.addUnsafeEnchantment(Enchantment.BLAST_PROTECTION, 225);
        item.addUnsafeEnchantment(Enchantment.FIRE_PROTECTION, 225);
        item.addUnsafeEnchantment(Enchantment.PROJECTILE_PROTECTION, 225);
        item.addUnsafeEnchantment(Enchantment.THORNS, 225);
        item.addUnsafeEnchantment(Enchantment.FEATHER_FALLING, 225);
        item.addUnsafeEnchantment(Enchantment.DEPTH_STRIDER, 225);
        item.addUnsafeEnchantment(Enchantment.SOUL_SPEED, 225);
    }

    private static void repairRiftItem(ItemStack item) {
        String itemType = type(item);
        if (itemType.isBlank() || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        meta.setUnbreakable(true);
        item.setItemMeta(meta);

        if (BLADE_ID.equalsIgnoreCase(itemType)) {
            for (Enchantment enchantment : new ArrayList<>(item.getEnchantments().keySet())) {
                item.removeEnchantment(enchantment);
            }
            applyRealm100SwordEnchants(item);
        } else if (BOOTS_ID.equalsIgnoreCase(itemType)) {
            for (Enchantment enchantment : new ArrayList<>(item.getEnchantments().keySet())) {
                item.removeEnchantment(enchantment);
            }
            applyRealm100BootEnchants(item);
        }
    }

    private void passiveTick() {
        long now = System.currentTimeMillis();
        cooldowns.entrySet().removeIf(e -> e.getValue() <= now);
        dashBonusUntil.entrySet().removeIf(e -> e.getValue() <= now);

        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean boots = is(player.getInventory().getBoots(), BOOTS_ID);
            boolean fullSet = boots
                    && is(player.getInventory().getChestplate(), WINGS_ID)
                    && is(player.getInventory().getItemInMainHand(), BLADE_ID);

            if (boots) {
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.SPEED, 30, fullSet ? 1 : 0, true, false, false));
            }

            if (is(player.getInventory().getChestplate(), WINGS_ID) && player.isGliding()) {
                Location at = player.getLocation().add(0, 0.8, 0);
                player.getWorld().spawnParticle(Particle.DUST, at, 5, 0.35, 0.25, 0.35, 0, PURPLE_DUST);
                player.getWorld().spawnParticle(Particle.DUST, at, 3, 0.25, 0.2, 0.25, 0, BLACK_DUST);
            }

            if (fullSet) {
                player.getWorld().spawnParticle(
                        Particle.REVERSE_PORTAL, player.getLocation().add(0, 0.15, 0),
                        4, 0.25, 0.05, 0.25, 0.01);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void preventDurability(PlayerItemDamageEvent event) {
        if (!type(event.getItem()).isBlank()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        String itemType = type(item);
        if (itemType.isBlank()) return;

        repairRiftItem(item);

        long now = System.currentTimeMillis();
        String debounceKey = player.getUniqueId() + ":" + itemType;
        long previous = activationDebounce.getOrDefault(debounceKey, 0L);
        if (now - previous < 150L) return;
        activationDebounce.put(debounceKey, now);

        switch (itemType.toLowerCase(Locale.ROOT)) {
            case BLADE_ID -> {
                event.setCancelled(true);
                Location target = forwardTarget(player, 8.0);
                if (target == null || target.distanceSquared(player.getLocation()) < 1.0) {
                    player.sendActionBar(ChatColor.RED + "No safe space to Rift Dash.");
                    return;
                }
                if (!startCooldown(player, "blade", 8_000L, "Rift Dash")) return;
                player.teleport(target);
                dashBonusUntil.put(player.getUniqueId(), System.currentTimeMillis() + 4_000L);
                riftBurstParticles(player.getLocation(), 28);
                player.sendActionBar(ChatColor.LIGHT_PURPLE + "Rift Dash activated.");
            }
            case CORE_ID -> {
                event.setCancelled(true);
                if (!startCooldown(player, "core", 45_000L, "Rift Core")) return;
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 160, 1, true, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 160, 1, true, true, true));
                player.setAbsorptionAmount(Math.max(player.getAbsorptionAmount(), 8.0));
                riftBurstParticles(player.getLocation().add(0, 1, 0), 60);
                player.sendActionBar(ChatColor.LIGHT_PURPLE + "Rift Armor active for 8 seconds.");
                player.sendMessage(ChatColor.DARK_PURPLE + "[Rift Core] " + ChatColor.LIGHT_PURPLE +
                        "Rift Armor activated: Resistance II + Absorption for 8 seconds.");
            }
            case COMPASS_ID -> {
                event.setCancelled(true);
                useDeathCompass(player, item);
            }
            default -> {
                // Other Riftwalker items use dedicated events.
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        Player player = event.getPlayer();

        if (player.isGliding() && is(player.getInventory().getChestplate(), WINGS_ID)) {
            if (!startCooldown(player, "wings", 6_000L, "Rift Boost")) return;
            Vector direction = player.getLocation().getDirection().normalize().multiply(1.35);
            player.setVelocity(player.getVelocity().multiply(0.35).add(direction));
            riftBurstParticles(player.getLocation(), 24);
            return;
        }

        if (!is(player.getInventory().getBoots(), BOOTS_ID)) return;

        long now = System.currentTimeMillis();
        long previous = lastSneak.getOrDefault(player.getUniqueId(), 0L);
        lastSneak.put(player.getUniqueId(), now);
        if (now - previous > 450L) return;

        Location target = forwardTarget(player, 5.0);
        if (target == null || target.distanceSquared(player.getLocation()) < 1.0) return;
        if (!startCooldown(player, "boots", 10_000L, "Phase Step")) return;

        player.teleport(target);
        riftBurstParticles(player.getLocation(), 20);
        player.sendActionBar(ChatColor.LIGHT_PURPLE + "Phase Step.");
        lastSneak.remove(player.getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!is(player.getInventory().getBoots(), BOOTS_ID)) return;
        event.setDamage(event.getDamage() * 0.25);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBowShoot(EntityShootBowEvent event) {
        if (!is(event.getBow(), BOW_ID)) return;
        if (!(event.getProjectile() instanceof Projectile projectile)) return;

        if (RIFT_PROJECTILE != null) {
            projectile.getPersistentDataContainer().set(RIFT_PROJECTILE, PersistentDataType.BYTE, (byte) 1);
        }
        if (event.getForce() >= 0.99f && ThreadLocalRandom.current().nextDouble() < 0.30 && RIFT_BURST != null) {
            projectile.getPersistentDataContainer().set(RIFT_BURST, PersistentDataType.BYTE, (byte) 1);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        if (event.getDamager() instanceof Player player
                && is(player.getInventory().getItemInMainHand(), BLADE_ID)) {
            Long expires = dashBonusUntil.remove(player.getUniqueId());
            if (expires != null && expires >= System.currentTimeMillis()) {
                event.setDamage(event.getDamage() + 4.0);
                riftBurstParticles(victim.getLocation().add(0, 1, 0), 24);
                player.sendActionBar(ChatColor.LIGHT_PURPLE + "Rift Strike +" + 2 + " hearts");
            }
            return;
        }

        if (!(event.getDamager() instanceof Projectile projectile) || RIFT_PROJECTILE == null) return;
        Byte tagged = projectile.getPersistentDataContainer().get(RIFT_PROJECTILE, PersistentDataType.BYTE);
        if (tagged == null || tagged != (byte) 1) return;

        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1, true, true, true));

        Byte burst = RIFT_BURST == null ? null :
                projectile.getPersistentDataContainer().get(RIFT_BURST, PersistentDataType.BYTE);
        if (burst == null || burst != (byte) 1) return;

        Entity shooter = projectile.getShooter() instanceof Entity e ? e : null;
        Location center = victim.getLocation();
        riftBurstParticles(center.add(0, 1, 0), 42);

        for (Entity nearby : victim.getNearbyEntities(3.5, 3.0, 3.5)) {
            if (!(nearby instanceof LivingEntity living)) continue;
            if (nearby.equals(victim) || nearby.equals(shooter)) continue;
            living.damage(4.0, shooter == null ? projectile : shooter);
            living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, true, true, true));
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Location location = player.getLocation();
        PersistentDataContainer pdc = player.getPersistentDataContainer();

        if (DEATH_WORLD != null) pdc.set(DEATH_WORLD, PersistentDataType.STRING, location.getWorld().getUID().toString());
        if (DEATH_X != null) pdc.set(DEATH_X, PersistentDataType.DOUBLE, location.getX());
        if (DEATH_Y != null) pdc.set(DEATH_Y, PersistentDataType.DOUBLE, location.getY());
        if (DEATH_Z != null) pdc.set(DEATH_Z, PersistentDataType.DOUBLE, location.getZ());
    }

    private void useDeathCompass(Player player, ItemStack compass) {
        Location death = lastDeath(player);
        if (death == null || death.getWorld() == null) {
            player.sendMessage(ChatColor.RED + "Void Compass: no recorded death location yet.");
            return;
        }

        try {
            if (compass.getItemMeta() instanceof CompassMeta meta) {
                meta.setLodestone(death);
                meta.setLodestoneTracked(false);
                compass.setItemMeta(meta);
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Void Compass could not bind its lodestone target for " +
                    player.getName() + ": " + ex.getMessage());
        }

        String worldName = death.getWorld().getName();
        if (player.isSneaking()) {
            if (player.getWorld().equals(death.getWorld())) {
                long distance = Math.round(player.getLocation().distance(death));
                player.sendMessage(ChatColor.DARK_PURPLE + "Void Compass: " + ChatColor.WHITE +
                        distance + " blocks away in " + worldName + " at " +
                        death.getBlockX() + ", " + death.getBlockY() + ", " + death.getBlockZ());
            } else {
                player.sendMessage(ChatColor.DARK_PURPLE + "Void Compass: " + ChatColor.WHITE +
                        "Your last death was in " + worldName + " at " +
                        death.getBlockX() + ", " + death.getBlockY() + ", " + death.getBlockZ());
            }
        } else {
            player.sendActionBar(ChatColor.LIGHT_PURPLE +
                    "Void Compass bound to your last death in " + worldName + ".");
            player.sendMessage(ChatColor.DARK_PURPLE + "[Void Compass] " + ChatColor.LIGHT_PURPLE +
                    "Pointing to your last death in " + worldName + " at " +
                    death.getBlockX() + ", " + death.getBlockY() + ", " + death.getBlockZ() + ".");
        }
    }

    private Location lastDeath(Player player) {
        if (DEATH_WORLD == null || DEATH_X == null || DEATH_Y == null || DEATH_Z == null) return null;
        PersistentDataContainer pdc = player.getPersistentDataContainer();

        String worldId = pdc.get(DEATH_WORLD, PersistentDataType.STRING);
        Double x = pdc.get(DEATH_X, PersistentDataType.DOUBLE);
        Double y = pdc.get(DEATH_Y, PersistentDataType.DOUBLE);
        Double z = pdc.get(DEATH_Z, PersistentDataType.DOUBLE);
        if (worldId == null || x == null || y == null || z == null) return null;

        try {
            World world = Bukkit.getWorld(UUID.fromString(worldId));
            if (world != null) return new Location(world, x, y, z);
        } catch (IllegalArgumentException ignored) {
        }

        // Paper/Bedrock fallback: use the server's own last-death location if available.
        try {
            Object value = player.getClass().getMethod("getLastDeathLocation").invoke(player);
            if (value instanceof Location location && location.getWorld() != null) return location;
        } catch (Exception ignored) {
        }
        return null;
    }

    private boolean startCooldown(Player player, String ability, long millis, String display) {
        String key = player.getUniqueId() + ":" + ability;
        long now = System.currentTimeMillis();
        long until = cooldowns.getOrDefault(key, 0L);

        if (until > now) {
            double seconds = Math.ceil((until - now) / 100.0) / 10.0;
            player.sendActionBar(ChatColor.RED + display + " recharging: " + seconds + "s");
            return false;
        }

        cooldowns.put(key, now + millis);
        return true;
    }

    private Location forwardTarget(Player player, double maxDistance) {
        Location start = player.getLocation();
        Vector direction = start.getDirection().normalize();
        Location best = start.clone();

        for (double distance = 0.5; distance <= maxDistance; distance += 0.5) {
            Location test = start.clone().add(direction.clone().multiply(distance));
            if (!safePlayerSpace(test)) break;
            best = test;
        }
        return best;
    }

    private boolean safePlayerSpace(Location location) {
        return location.getBlock().isPassable()
                && location.clone().add(0, 1, 0).getBlock().isPassable();
    }

    private void riftBurstParticles(Location location, int count) {
        if (location.getWorld() == null) return;
        location.getWorld().spawnParticle(Particle.DUST, location, count / 2, 0.8, 0.7, 0.8, 0.01, PURPLE_DUST);
        location.getWorld().spawnParticle(Particle.DUST, location, Math.max(2, count / 4), 0.6, 0.5, 0.6, 0.01, BLACK_DUST);
        location.getWorld().spawnParticle(Particle.REVERSE_PORTAL, location, Math.max(4, count / 3), 0.7, 0.7, 0.7, 0.04);
    }


    @EventHandler
    public void repairOnJoin(PlayerJoinEvent event) {
        for (ItemStack item : event.getPlayer().getInventory().getContents()) repairRiftItem(item);
        for (ItemStack item : event.getPlayer().getEnderChest().getContents()) repairRiftItem(item);
    }

    @EventHandler
    public void repairOnHeld(PlayerItemHeldEvent event) {
        repairRiftItem(event.getPlayer().getInventory().getItem(event.getNewSlot()));
    }

    @EventHandler
    public void repairOnOpen(InventoryOpenEvent event) {
        for (ItemStack item : event.getInventory().getContents()) repairRiftItem(item);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        String prefix = id.toString() + ":";
        cooldowns.keySet().removeIf(key -> key.startsWith(prefix));
        activationDebounce.keySet().removeIf(key -> key.startsWith(prefix));
        lastSneak.remove(id);
        dashBonusUntil.remove(id);
    }
}
