package com.esn.smp.gameplay;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import com.esn.smp.ESNSMPPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Admin-only test build of the ESN Void Warrior Bundle.
 *
 * The bundle is intentionally NOT mapped to Stripe yet. It is available through
 * /esnitems for OP/admin testing only. Passive effects share the existing ESN
 * low-memory ticker and no per-player repeating tasks are created.
 */
public final class VoidWarriorBundle implements Listener {
    public static final String CROWN_ID = "voidwarriorcrown";
    public static final String CHEST_ID = "voidwarriorchest";
    public static final String LEGS_ID = "voidwarriorlegs";
    public static final String BOOTS_ID = "voidwarriorboots";
    public static final String BLADE_ID = "voidwarriorblade";

    private static final NamespacedKey VOID_TYPE = new NamespacedKey("esnsmp", "void_warrior_type");
    private static final NamespacedKey ADMIN_TEST = new NamespacedKey("esnsmp", "admin_test_item");
    private static final NamespacedKey MEGA_ITEM = new NamespacedKey("esnsmp", "mega_item");

    private static final long CLEAVE_COOLDOWN_MS = 8_000L;
    private static final long TELEPORT_COOLDOWN_MS = 2_000L;
    private static final long STEP_COOLDOWN_MS = 7_000L;
    private static final long AEGIS_COOLDOWN_MS = 25_000L;
    private static final double TELEPORT_RANGE = 40.0;

    private final ESNSMPPlugin plugin;
    private final Map<UUID, Long> cleaveCooldown = new HashMap<>();
    private final Map<UUID, Long> teleportCooldown = new HashMap<>();
    private final Map<UUID, Long> stepCooldown = new HashMap<>();
    private final Map<UUID, Long> aegisCooldown = new HashMap<>();
    private final Map<UUID, Long> lastSneak = new HashMap<>();

    public VoidWarriorBundle(ESNSMPPlugin plugin) {
        this.plugin = plugin;
    }

    public static ItemStack crown() {
        ItemStack item = fromRealm100(5, CROWN_ID, "VOID CROWN",
                "Night Vision + Resistance I while worn.",
                "Immune to Blindness and Darkness.",
                "Full set upgrades Resistance to II.");
        return item;
    }

    public static ItemStack chestplate() {
        return fromRealm100(6, CHEST_ID, "VOID CHESTPLATE",
                "Maintains 20 Absorption hearts while worn.",
                "Below 40% health: Resistance III for 6 seconds.",
                "Void Shield cooldown: 25 seconds.");
    }

    public static ItemStack leggings() {
        return fromRealm100(7, LEGS_ID, "VOID LEGGINGS",
                "Strength II while worn.",
                "Reduces combat knockback by about 85%.",
                "Full set upgrades Strength to III.");
    }

    public static ItemStack boots() {
        return fromRealm100(8, BOOTS_ID, "VOID BOOTS",
                "Speed II while worn.",
                "Reduces fall damage by 90%.",
                "Double-sneak without the full set: Void Step ~8 blocks.",
                "Void Step cooldown: 7 seconds.");
    }

    public static ItemStack blade() {
        ItemStack item = fromRealm100(0, BLADE_ID, "VOID BLADE",
                "Right-click: VOID ANNIHILATION.",
                "1,500 base cleave damage; 2,000 with full Void set.",
                "Pulls targets inward, then blasts them backward.",
                "Cooldown: 8 seconds.");
        item.removeEnchantment(Enchantment.LOOTING);
        return item;
    }

    private static ItemStack fromRealm100(int slot, String type, String name, String... abilityLore) {
        ItemStack item = MegaCrates.item(99, slot).clone();
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "✦ " + name + " ✦");

        List<String> lore = new ArrayList<>();
        for (String line : abilityLore) lore.add(ChatColor.LIGHT_PURPLE + line);
        lore.add("");
        lore.add(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "✦ VOID WARRIOR BUNDLE ✦");
        lore.add(ChatColor.GRAY + "Stronger than Immortal Warden and Realm 100 gear.");
        lore.add(ChatColor.GRAY + "Admin testing only — not mapped to Stripe yet.");
        lore.add(ChatColor.GOLD + "" + ChatColor.BOLD + "UNBREAKABLE");
        meta.setLore(lore);
        meta.setUnbreakable(true);

        // Remove the Realm-crate identity so MegaCrates never rewrites this custom item.
        meta.getPersistentDataContainer().remove(MEGA_ITEM);
        meta.getPersistentDataContainer().set(VOID_TYPE, PersistentDataType.STRING, type);
        meta.getPersistentDataContainer().set(ADMIN_TEST, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private static String type(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return "";
        return item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(VOID_TYPE, PersistentDataType.STRING, "");
    }

    private static boolean is(ItemStack item, String expected) {
        return expected.equalsIgnoreCase(type(item));
    }

    private static boolean fullSet(Player player) {
        return is(player.getInventory().getHelmet(), CROWN_ID)
                && is(player.getInventory().getChestplate(), CHEST_ID)
                && is(player.getInventory().getLeggings(), LEGS_ID)
                && is(player.getInventory().getBoots(), BOOTS_ID);
    }

    void cleanupPassiveState(long now) {
        clean(cleaveCooldown, now);
        clean(teleportCooldown, now);
        clean(stepCooldown, now);
        clean(aegisCooldown, now);
        lastSneak.entrySet().removeIf(e -> now - e.getValue() > 1_500L);
    }

    private static void clean(Map<UUID, Long> map, long now) {
        map.entrySet().removeIf(e -> e.getValue() <= now);
    }

    void passiveTick(Player player, boolean showAura) {
        boolean crown = is(player.getInventory().getHelmet(), CROWN_ID);
        boolean chest = is(player.getInventory().getChestplate(), CHEST_ID);
        boolean legs = is(player.getInventory().getLeggings(), LEGS_ID);
        boolean boots = is(player.getInventory().getBoots(), BOOTS_ID);
        if (!crown && !chest && !legs && !boots) return;

        boolean full = crown && chest && legs && boots;

        if (crown) {
            ensureEffect(player, PotionEffectType.NIGHT_VISION, 0);
            ensureEffect(player, PotionEffectType.RESISTANCE, full ? 1 : 0);
            if (player.hasPotionEffect(PotionEffectType.BLINDNESS)) {
                player.removePotionEffect(PotionEffectType.BLINDNESS);
            }
            if (player.hasPotionEffect(PotionEffectType.DARKNESS)) {
                player.removePotionEffect(PotionEffectType.DARKNESS);
            }
        }

        if (chest) {
            ensureEffect(player, PotionEffectType.ABSORPTION, 9);
            if (player.getAbsorptionAmount() < 40.0) {
                player.setAbsorptionAmount(40.0);
            }
        }

        if (legs) {
            ensureEffect(player, PotionEffectType.STRENGTH, full ? 2 : 1);
        }

        if (boots) {
            ensureEffect(player, PotionEffectType.SPEED, full ? 2 : 1);
        }

        if (full) {
            ensureEffect(player, PotionEffectType.REGENERATION, 0);

            // The aura is still clearly visible, but visual packets are emitted only
            // every fourth shared passive cycle. This preserves every gameplay effect
            // while reducing particle work/network traffic another 75%.
            if (showAura) {
                Location aura = player.getLocation();
                player.getWorld().spawnParticle(Particle.REVERSE_PORTAL,
                        aura.clone().add(0, 0.45, 0), 2, 0.28, 0.35, 0.28, 0.01);
                player.getWorld().spawnParticle(Particle.SCULK_SOUL,
                        aura.clone().add(0, 1.05, 0), 1, 0.22, 0.38, 0.22, 0.01);
            }
        }
    }

    private static void ensureEffect(Player player, PotionEffectType type, int amplifier) {
        PotionEffect current = player.getPotionEffect(type);
        if (current != null && current.getAmplifier() >= amplifier && current.getDuration() > 40) return;
        player.addPotionEffect(new PotionEffect(type, 80, amplifier, true, false, false), true);
    }

    @EventHandler(ignoreCancelled = true)
    public void preventDurability(PlayerItemDamageEvent event) {
        if (!type(event.getItem()).isBlank()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack held = event.getItem();

        // Keep the blade's own right-click ability intact. With the full set,
        // right-clicking any non-blade item (or empty hand) performs Void Teleport.
        if (is(held, BLADE_ID)) {
            event.setCancelled(true);
            if (!startCooldown(player, cleaveCooldown, CLEAVE_COOLDOWN_MS, "Void Annihilation")) return;
            cleave(player);
            return;
        }

        if (fullSet(player)) {
            if (voidTeleport(player)) event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        Player player = event.getPlayer();

        if (fullSet(player)) {
            voidTeleport(player);
            return;
        }

        if (!is(player.getInventory().getBoots(), BOOTS_ID)) return;

        long now = System.currentTimeMillis();
        UUID id = player.getUniqueId();
        long previous = lastSneak.getOrDefault(id, 0L);
        lastSneak.put(id, now);
        if (now - previous > 450L) return;

        Location target = safeLookTarget(player, 8.0);
        if (target == null) {
            player.sendActionBar(ChatColor.RED + "No safe space for Void Step.");
            return;
        }
        if (!startCooldown(player, stepCooldown, STEP_COOLDOWN_MS, "Void Step")) return;
        teleportWithEffects(player, target, "Void Step");
    }

    @EventHandler(ignoreCancelled = true)
    public void onJump(PlayerJumpEvent event) {
        Player player = event.getPlayer();
        if (fullSet(player)) voidTeleport(player);
    }

    private boolean voidTeleport(Player player) {
        Location target = safeLookTarget(player, TELEPORT_RANGE);
        if (target == null) {
            player.sendActionBar(ChatColor.RED + "No safe Void destination in sight.");
            return false;
        }
        if (!startCooldown(player, teleportCooldown, TELEPORT_COOLDOWN_MS, "Void Teleport")) return false;
        teleportWithEffects(player, target, "VOID TELEPORT");
        return true;
    }

    private static void teleportWithEffects(Player player, Location target, String label) {
        Location from = player.getLocation();
        World world = player.getWorld();
        world.spawnParticle(Particle.REVERSE_PORTAL, from.clone().add(0, 1, 0),
                8, 0.35, 0.55, 0.35, 0.02);
        world.spawnParticle(Particle.SCULK_SOUL, from.clone().add(0, 1, 0),
                4, 0.25, 0.45, 0.25, 0.01);
        if (player.teleport(target)) {
            World destinationWorld = target.getWorld();
            if (destinationWorld != null) {
                destinationWorld.spawnParticle(Particle.REVERSE_PORTAL, target.clone().add(0, 1, 0),
                        8, 0.35, 0.55, 0.35, 0.02);
                destinationWorld.spawnParticle(Particle.SCULK_SOUL, target.clone().add(0, 1, 0),
                        4, 0.25, 0.45, 0.25, 0.01);
            }
            player.sendActionBar(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + label);
        }
    }

    private static Location safeLookTarget(Player player, double maxDistance) {
        Location eye = player.getEyeLocation();
        World world = eye.getWorld();
        if (world == null) return null;

        Vector direction = eye.getDirection().normalize();
        WorldBorder border = world.getWorldBorder();
        Location center = border.getCenter();
        double half = Math.max(1.0, border.getSize() / 2.0 - 1.0);
        double eyeHeight = player.getEyeHeight();

        double bestX = Double.NaN;
        double bestY = 0.0;
        double bestZ = 0.0;

        for (double distance = 1.5; distance <= maxDistance; distance += 0.75) {
            double sightX = eye.getX() + direction.getX() * distance;
            double sightY = eye.getY() + direction.getY() * distance;
            double sightZ = eye.getZ() + direction.getZ() * distance;

            if (Math.abs(sightX - center.getX()) > half || Math.abs(sightZ - center.getZ()) > half) break;
            if (sightY < world.getMinHeight() + 1 || sightY >= world.getMaxHeight() - 1) break;

            Block sightBlock = world.getBlockAt(floor(sightX), floor(sightY), floor(sightZ));
            if (!sightBlock.isPassable()) break;

            double feetY = sightY - eyeHeight;
            int bx = floor(sightX);
            int by = floor(feetY);
            int bz = floor(sightZ);
            if (by <= world.getMinHeight() || by >= world.getMaxHeight() - 2) continue;

            Block feet = world.getBlockAt(bx, by, bz);
            Block head = world.getBlockAt(bx, by + 1, bz);
            Block floor = world.getBlockAt(bx, by - 1, bz);

            if (!feet.isPassable() || !head.isPassable() || !floor.getType().isSolid()) continue;
            if (unsafe(feet.getType()) || unsafe(head.getType()) || unsafe(floor.getType())) continue;

            bestX = sightX;
            bestY = by;
            bestZ = sightZ;
        }

        if (Double.isNaN(bestX)) return null;
        return new Location(world, bestX, bestY, bestZ, player.getLocation().getYaw(), player.getLocation().getPitch());
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private static boolean unsafe(Material material) {
        return material == Material.LAVA
                || material == Material.FIRE
                || material == Material.SOUL_FIRE
                || material == Material.MAGMA_BLOCK
                || material == Material.CACTUS
                || material == Material.CAMPFIRE
                || material == Material.SOUL_CAMPFIRE
                || material == Material.SWEET_BERRY_BUSH
                || material == Material.POWDER_SNOW;
    }

    private void cleave(Player player) {
        boolean full = fullSet(player);
        double damage = full ? 2000.0 : 1500.0;
        Location origin = player.getEyeLocation();
        Vector forward = origin.getDirection().normalize();
        List<LivingEntity> hit = new ArrayList<>(8);

        for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), 8, 5, 8)) {
            if (!(entity instanceof LivingEntity target) || target.equals(player)) continue;

            Vector toTarget = target.getLocation().add(0, 0.8, 0).toVector().subtract(origin.toVector());
            double distance = toTarget.length();
            if (distance <= 0.01 || distance > 8.0) continue;
            if (forward.dot(toTarget.clone().normalize()) < 0.50) continue;

            Vector inward = player.getLocation().toVector().subtract(target.getLocation().toVector());
            if (inward.lengthSquared() > 0.01) {
                target.setVelocity(target.getVelocity().multiply(0.25).add(inward.normalize().multiply(0.45)));
            }

            target.damage(damage, player);
            hit.add(target);
        }

        player.getWorld().spawnParticle(Particle.REVERSE_PORTAL,
                player.getLocation().add(forward.clone().multiply(2.0)).add(0, 1, 0),
                18, 1.6, 0.9, 1.6, 0.05);
        player.getWorld().spawnParticle(Particle.SCULK_SOUL,
                player.getLocation().add(forward.clone().multiply(2.0)).add(0, 1, 0),
                8, 1.2, 0.7, 1.2, 0.03);

        if (!hit.isEmpty()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (LivingEntity target : hit) {
                    if (!target.isValid() || target.isDead()) continue;
                    Vector away = target.getLocation().toVector().subtract(player.getLocation().toVector());
                    if (away.lengthSquared() <= 0.01) continue;
                    Vector blast = away.normalize().multiply(1.25);
                    blast.setY(0.28);
                    target.setVelocity(target.getVelocity().multiply(0.20).add(blast));
                }
            }, 2L);
        }

        player.sendActionBar(ChatColor.LIGHT_PURPLE + "VOID ANNIHILATION — "
                + (int) damage + " base damage — " + hit.size() + " target" + (hit.size() == 1 ? "" : "s"));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void combat(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim
                && is(victim.getInventory().getLeggings(), LEGS_ID)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!victim.isOnline()) return;
                Vector velocity = victim.getVelocity();
                velocity.setX(velocity.getX() * 0.15);
                velocity.setZ(velocity.getZ() * 0.15);
                victim.setVelocity(velocity);
            });
        }

        if (!(event.getDamager() instanceof Player attacker)
                || !(event.getEntity() instanceof LivingEntity target)
                || !fullSet(attacker)) return;

        if (ThreadLocalRandom.current().nextDouble() < 0.20) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2, true, true, true), true);
            attacker.sendActionBar(ChatColor.DARK_PURPLE + "Void Ascension inflicted Slowness III");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void damage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        if (event.getCause() == EntityDamageEvent.DamageCause.FALL
                && is(player.getInventory().getBoots(), BOOTS_ID)) {
            event.setDamage(event.getDamage() * 0.10);
        }

        if (is(player.getInventory().getChestplate(), CHEST_ID)) {
            double predicted = Math.max(0.0, player.getHealth() + player.getAbsorptionAmount() - event.getFinalDamage());
            double threshold = player.getMaxHealth() * 0.40;
            if (predicted <= threshold
                    && startCooldown(player, aegisCooldown, AEGIS_COOLDOWN_MS, null)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 120, 2, true, true, true), true);
                player.sendActionBar(ChatColor.LIGHT_PURPLE + "VOID SHIELD — Resistance III");
            }
        }
    }

    private static boolean startCooldown(Player player, Map<UUID, Long> cooldown,
                                         long durationMs, String label) {
        long now = System.currentTimeMillis();
        Long until = cooldown.get(player.getUniqueId());
        if (until != null && until > now) {
            if (label != null) {
                double seconds = Math.ceil((until - now) / 100.0) / 10.0;
                player.sendActionBar(ChatColor.GRAY + label + " cooldown: " + seconds + "s");
            }
            return false;
        }
        cooldown.put(player.getUniqueId(), now + durationMs);
        return true;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        cleaveCooldown.remove(id);
        teleportCooldown.remove(id);
        stepCooldown.remove(id);
        aegisCooldown.remove(id);
        lastSneak.remove(id);
    }
}
