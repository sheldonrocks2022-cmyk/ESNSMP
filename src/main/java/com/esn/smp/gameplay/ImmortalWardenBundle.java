package com.esn.smp.gameplay;

import com.esn.smp.ESNSMPPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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

/**
 * Admin-test version of the ESN Immortal Warden Bundle.
 *
 * The items are registered in /esnitems but intentionally have no Stripe
 * product mapping yet. All durability gear is truly unbreakable.
 */
public final class ImmortalWardenBundle implements Listener {
    public static final String HELMET_ID = "wardenhelmet";
    public static final String CHEST_ID = "wardenchest";
    public static final String LEGS_ID = "wardenlegs";
    public static final String BOOTS_ID = "wardenboots";
    public static final String BLADE_ID = "wardenblade";
    public static final String BOW_ID = "wardenbow";
    public static final String CORE_ID = "immortalcore";
    public static final String TOTEM_ID = "wardentotem";

    private static final NamespacedKey WARDEN_TYPE = new NamespacedKey("esnsmp", "immortal_warden_type");
    private static final NamespacedKey ADMIN_TEST = new NamespacedKey("esnsmp", "admin_test_item");
    private static final NamespacedKey WARDEN_ARROW = new NamespacedKey("esnsmp", "warden_arrow");

    private final ESNSMPPlugin plugin;
    private final Map<String, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Mark> marked = new HashMap<>();

    private record Mark(UUID owner, long expiresAt) {}

    public ImmortalWardenBundle(ESNSMPPlugin plugin) {
        this.plugin = plugin;
    }

    public static ItemStack helmet() {
        ItemStack item = base(Material.NETHERITE_HELMET, HELMET_ID, "IMMORTAL WARDEN HELMET",
                "Night Vision while worn.",
                "Suppresses Blindness and Darkness effects.",
                "Realm 100 armor enchant power: 225.");
        applyHelmetEnchants(item);
        return item;
    }

    public static ItemStack chestplate() {
        ItemStack item = base(Material.NETHERITE_CHESTPLATE, CHEST_ID, "IMMORTAL WARDEN CHESTPLATE",
                "Maintains bonus Absorption while worn.",
                "Low health triggers a short Resistance boost.",
                "Realm 100 armor enchant power: 225.");
        applyChestLegEnchants(item);
        return item;
    }

    public static ItemStack leggings() {
        ItemStack item = base(Material.NETHERITE_LEGGINGS, LEGS_ID, "IMMORTAL WARDEN LEGGINGS",
                "Greatly reduces combat knockback.",
                "Full set grants Strength I.",
                "Realm 100 armor enchant power: 225.");
        applyChestLegEnchants(item);
        return item;
    }

    public static ItemStack boots() {
        ItemStack item = base(Material.NETHERITE_BOOTS, BOOTS_ID, "IMMORTAL WARDEN BOOTS",
                "Speed I while worn.",
                "Reduces fall damage by 75%.",
                "Heavy landings release a Warden shockwave with no block damage.",
                "Realm 100 armor enchant power: 225.");
        applyBootEnchants(item);
        return item;
    }

    public static ItemStack blade() {
        ItemStack item = base(Material.NETHERITE_SWORD, BLADE_ID, "IMMORTAL WARDEN BLADE",
                "Right-click: Warden Cleave.",
                "Marked enemies take extra damage from this blade.",
                "Realm 100 sword enchant power: 225.");
        applySwordEnchants(item);
        return item;
    }

    public static ItemStack bow() {
        ItemStack item = base(Material.BOW, BOW_ID, "WARDEN LONGBOW",
                "Fully charged arrows mark enemies for 8 seconds.",
                "Marked enemies take extra damage from the Warden Blade.",
                "Realm 100 bow enchant power: 225.");
        applyBowEnchants(item);
        return item;
    }

    public static ItemStack core() {
        return base(Material.NETHER_STAR, CORE_ID, "IMMORTAL CORE",
                "Right-click: Last Stand.",
                "Resistance II + Strength II + Absorption for 12 seconds.",
                "Cooldown: 60 seconds.");
    }

    public static ItemStack totem() {
        return base(Material.TOTEM_OF_UNDYING, TOTEM_ID, "WARDEN TOTEM",
                "INFINITE USE — never consumed.",
                "Right-click: Regeneration II + Absorption + Resistance.",
                "Effects last 10 seconds.",
                "Cooldown: 60 seconds.");
    }

    private static ItemStack base(Material material, String type, String name, String... abilityLore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "✦ " + name + " ✦");

        List<String> lore = new ArrayList<>();
        for (String line : abilityLore) lore.add(ChatColor.AQUA + line);
        lore.add("");
        lore.add(ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "✦ ESN STORE EXCLUSIVE ✦");
        lore.add(ChatColor.GRAY + "Part of the Immortal Warden Bundle");
        lore.add(ChatColor.GRAY + "Not obtainable from crates, bosses, or the Season Pass.");
        lore.add(ChatColor.GOLD + "" + ChatColor.BOLD + "UNBREAKABLE");
        meta.setLore(lore);
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(WARDEN_TYPE, PersistentDataType.STRING, type);
        meta.getPersistentDataContainer().set(ADMIN_TEST, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private static String type(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return "";
        return item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(WARDEN_TYPE, PersistentDataType.STRING, "");
    }

    private static boolean is(ItemStack item, String expected) {
        return expected.equalsIgnoreCase(type(item));
    }

    private static void applyHelmetEnchants(ItemStack item) {
        add(item, Enchantment.UNBREAKING);
        add(item, Enchantment.PROTECTION);
        add(item, Enchantment.BLAST_PROTECTION);
        add(item, Enchantment.FIRE_PROTECTION);
        add(item, Enchantment.PROJECTILE_PROTECTION);
        add(item, Enchantment.THORNS);
        add(item, Enchantment.RESPIRATION);
        add(item, Enchantment.AQUA_AFFINITY);
    }

    private static void applyChestLegEnchants(ItemStack item) {
        add(item, Enchantment.UNBREAKING);
        add(item, Enchantment.PROTECTION);
        add(item, Enchantment.BLAST_PROTECTION);
        add(item, Enchantment.FIRE_PROTECTION);
        add(item, Enchantment.PROJECTILE_PROTECTION);
        add(item, Enchantment.THORNS);
    }

    private static void applyBootEnchants(ItemStack item) {
        applyChestLegEnchants(item);
        add(item, Enchantment.FEATHER_FALLING);
        add(item, Enchantment.DEPTH_STRIDER);
        add(item, Enchantment.SOUL_SPEED);
    }

    private static void applySwordEnchants(ItemStack item) {
        add(item, Enchantment.UNBREAKING);
        add(item, Enchantment.SHARPNESS);
        add(item, Enchantment.LOOTING);
        add(item, Enchantment.FIRE_ASPECT);
        add(item, Enchantment.KNOCKBACK);
        add(item, Enchantment.SMITE);
        add(item, Enchantment.BANE_OF_ARTHROPODS);
    }

    private static void applyBowEnchants(ItemStack item) {
        add(item, Enchantment.UNBREAKING);
        add(item, Enchantment.POWER);
        add(item, Enchantment.PUNCH);
        add(item, Enchantment.FLAME);
        add(item, Enchantment.INFINITY);
    }

    private static void add(ItemStack item, Enchantment enchantment) {
        item.addUnsafeEnchantment(enchantment, 225);
    }

    private static void repair(ItemStack item) {
        String itemType = type(item);
        if (itemType.isBlank() || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        if (!meta.isUnbreakable()) {
            meta.setUnbreakable(true);
            item.setItemMeta(meta);
        }

        if (HELMET_ID.equals(itemType)) {
            repairEnchants(item, 8, ImmortalWardenBundle::applyHelmetEnchants,
                    Enchantment.UNBREAKING, Enchantment.PROTECTION, Enchantment.BLAST_PROTECTION,
                    Enchantment.FIRE_PROTECTION, Enchantment.PROJECTILE_PROTECTION, Enchantment.THORNS,
                    Enchantment.RESPIRATION, Enchantment.AQUA_AFFINITY);
        } else if (CHEST_ID.equals(itemType) || LEGS_ID.equals(itemType)) {
            repairEnchants(item, 6, ImmortalWardenBundle::applyChestLegEnchants,
                    Enchantment.UNBREAKING, Enchantment.PROTECTION, Enchantment.BLAST_PROTECTION,
                    Enchantment.FIRE_PROTECTION, Enchantment.PROJECTILE_PROTECTION, Enchantment.THORNS);
        } else if (BOOTS_ID.equals(itemType)) {
            repairEnchants(item, 9, ImmortalWardenBundle::applyBootEnchants,
                    Enchantment.UNBREAKING, Enchantment.PROTECTION, Enchantment.BLAST_PROTECTION,
                    Enchantment.FIRE_PROTECTION, Enchantment.PROJECTILE_PROTECTION, Enchantment.THORNS,
                    Enchantment.FEATHER_FALLING, Enchantment.DEPTH_STRIDER, Enchantment.SOUL_SPEED);
        } else if (BLADE_ID.equals(itemType)) {
            repairEnchants(item, 7, ImmortalWardenBundle::applySwordEnchants,
                    Enchantment.UNBREAKING, Enchantment.SHARPNESS, Enchantment.LOOTING,
                    Enchantment.FIRE_ASPECT, Enchantment.KNOCKBACK, Enchantment.SMITE,
                    Enchantment.BANE_OF_ARTHROPODS);
        } else if (BOW_ID.equals(itemType)) {
            repairEnchants(item, 5, ImmortalWardenBundle::applyBowEnchants,
                    Enchantment.UNBREAKING, Enchantment.POWER, Enchantment.PUNCH,
                    Enchantment.FLAME, Enchantment.INFINITY);
        }
    }

    private static void repairEnchants(ItemStack item, int expectedCount,
                                       java.util.function.Consumer<ItemStack> applier,
                                       Enchantment... expected) {
        Map<Enchantment, Integer> enchants = item.getEnchantments();
        boolean correct = enchants.size() == expectedCount;
        if (correct) {
            for (Enchantment enchantment : expected) {
                if (enchants.getOrDefault(enchantment, 0) != 225) {
                    correct = false;
                    break;
                }
            }
        }
        if (correct) return;

        for (Enchantment enchantment : new ArrayList<>(enchants.keySet())) {
            item.removeEnchantment(enchantment);
        }
        applier.accept(item);
    }

    void cleanupPassiveState(long now) {
        cooldowns.entrySet().removeIf(e -> e.getValue() <= now);
        marked.entrySet().removeIf(e -> e.getValue().expiresAt() <= now);
    }

    void passiveTick(Player player) {
        boolean helmet = is(player.getInventory().getHelmet(), HELMET_ID);
        boolean chest = is(player.getInventory().getChestplate(), CHEST_ID);
        boolean legs = is(player.getInventory().getLeggings(), LEGS_ID);
        boolean boots = is(player.getInventory().getBoots(), BOOTS_ID);
        boolean fullSet = helmet && chest && legs && boots;

        if (helmet) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 40, 0, true, false, false));
            player.removePotionEffect(PotionEffectType.BLINDNESS);
            player.removePotionEffect(PotionEffectType.DARKNESS);
        }

        if (chest && player.getAbsorptionAmount() < 4.0) {
            player.setAbsorptionAmount(4.0);
        }

        if (boots) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 30, 0, true, false, false));
        }

        if (fullSet) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 30, 0, true, false, false));
            player.getWorld().spawnParticle(
                    Particle.SCULK_SOUL, player.getLocation().add(0, 1.0, 0),
                    3, 0.35, 0.55, 0.35, 0.01);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void preventDurability(PlayerItemDamageEvent event) {
        if (!type(event.getItem()).isBlank()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void interact(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        ItemStack item = event.getItem();
        String itemType = type(item);
        if (itemType.isBlank()) return;

        repair(item);
        Player player = event.getPlayer();

        if (BLADE_ID.equals(itemType)) {
            event.setCancelled(true);
            if (!startCooldown(player, "warden_cleave", 12_000L, "Warden Cleave")) return;
            cleave(player);
        } else if (CORE_ID.equals(itemType)) {
            event.setCancelled(true);
            if (!startCooldown(player, "immortal_core", 60_000L, "Last Stand")) return;
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 240, 1, true, true, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 240, 1, true, true, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 240, 2, true, true, true));
            player.setAbsorptionAmount(Math.max(player.getAbsorptionAmount(), 12.0));
            player.getWorld().spawnParticle(Particle.SCULK_SOUL, player.getLocation().add(0, 1, 0), 70, 1.2, 1.2, 1.2, 0.04);
            player.sendMessage(ChatColor.DARK_AQUA + "[Immortal Core] " + ChatColor.AQUA + "Last Stand activated for 12 seconds.");
        } else if (TOTEM_ID.equals(itemType)) {
            event.setCancelled(true);
            if (!startCooldown(player, "warden_totem", 60_000L, "Warden Totem")) return;
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 1, true, true, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 200, 1, true, true, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 200, 0, true, true, true));
            player.setAbsorptionAmount(Math.max(player.getAbsorptionAmount(), 8.0));
            player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, player.getLocation().add(0, 1, 0), 55, 0.9, 1.0, 0.9, 0.03);
            player.sendMessage(ChatColor.DARK_AQUA + "[Warden Totem] " + ChatColor.AQUA + "Protection active for 10 seconds.");
        }
    }

    private void cleave(Player player) {
        Location origin = player.getEyeLocation();
        Vector forward = origin.getDirection().normalize();
        int hits = 0;

        for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), 7, 4, 7)) {
            if (!(entity instanceof LivingEntity target) || target.equals(player)) continue;

            Vector toTarget = target.getLocation().add(0, 0.8, 0).toVector().subtract(origin.toVector());
            double distance = toTarget.length();
            if (distance <= 0.01 || distance > 7.0) continue;

            double dot = forward.dot(toTarget.clone().normalize());
            if (dot < 0.55) continue;

            target.damage(8.0, player);
            Vector push = toTarget.normalize().multiply(0.7);
            push.setY(0.18);
            target.setVelocity(target.getVelocity().add(push));
            hits++;
        }

        player.getWorld().spawnParticle(Particle.SCULK_SOUL,
                player.getLocation().add(forward.clone().multiply(2.0)).add(0, 1, 0),
                55, 1.5, 0.8, 1.5, 0.05);
        player.sendActionBar(ChatColor.AQUA + "Warden Cleave — " + hits + " target" + (hits == 1 ? "" : "s"));
    }

    @EventHandler(ignoreCancelled = true)
    public void bowShoot(EntityShootBowEvent event) {
        if (!is(event.getBow(), BOW_ID) || !(event.getProjectile() instanceof Projectile projectile)) return;
        if (event.getForce() < 0.99f) return;
        projectile.getPersistentDataContainer().set(WARDEN_ARROW, PersistentDataType.BYTE, (byte) 1);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void combat(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        Player attacker = null;
        if (event.getDamager() instanceof Player player) attacker = player;
        else if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player player) attacker = player;

        if (attacker == null) return;

        if (event.getDamager() instanceof Projectile projectile) {
            Byte tag = projectile.getPersistentDataContainer().get(WARDEN_ARROW, PersistentDataType.BYTE);
            if (tag != null && tag == (byte) 1) {
                marked.put(victim.getUniqueId(), new Mark(attacker.getUniqueId(), System.currentTimeMillis() + 8_000L));
                victim.getWorld().spawnParticle(Particle.SCULK_SOUL, victim.getLocation().add(0, 1, 0), 30, 0.5, 0.8, 0.5, 0.02);
                attacker.sendActionBar(ChatColor.DARK_AQUA + "Warden Mark applied for 8s");
            }
        }

        if (event.getDamager() instanceof Player player && is(player.getInventory().getItemInMainHand(), BLADE_ID)) {
            Mark mark = marked.get(victim.getUniqueId());
            if (mark != null && mark.owner().equals(player.getUniqueId()) && mark.expiresAt() > System.currentTimeMillis()) {
                event.setDamage(event.getDamage() * 1.25);
                marked.remove(victim.getUniqueId());
                victim.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, victim.getLocation().add(0, 1, 0), 30, 0.45, 0.65, 0.45, 0.02);
                player.sendActionBar(ChatColor.AQUA + "Warden Mark consumed: +25% damage");
            }
        }

        if (victim instanceof Player playerVictim) {
            boolean chest = is(playerVictim.getInventory().getChestplate(), CHEST_ID);
            boolean legs = is(playerVictim.getInventory().getLeggings(), LEGS_ID);

            if (chest) {
                double predicted = Math.max(0.0, playerVictim.getHealth() - event.getFinalDamage());
                if (predicted <= playerVictim.getMaxHealth() * 0.35
                        && startCooldown(playerVictim, "warden_low_health", 30_000L, null)) {
                    playerVictim.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 1, true, true, true));
                    playerVictim.sendActionBar(ChatColor.DARK_AQUA + "Warden Aegis activated");
                }
            }

            if (legs) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!playerVictim.isOnline()) return;
                    Vector velocity = playerVictim.getVelocity();
                    velocity.setX(velocity.getX() * 0.35);
                    velocity.setZ(velocity.getZ() * 0.35);
                    playerVictim.setVelocity(velocity);
                });
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void fall(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL || !(event.getEntity() instanceof Player player)) return;
        if (!is(player.getInventory().getBoots(), BOOTS_ID)) return;

        double original = event.getDamage();
        event.setDamage(original * 0.25);

        if (original < 6.0) return;
        Location center = player.getLocation();
        player.getWorld().spawnParticle(Particle.SCULK_SOUL, center.add(0, 0.2, 0), 65, 1.4, 0.25, 1.4, 0.06);

        for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), 4.0, 2.5, 4.0)) {
            if (!(entity instanceof LivingEntity target) || target.equals(player)) continue;
            target.damage(4.0, player);

            Vector push = target.getLocation().toVector().subtract(player.getLocation().toVector());
            if (push.lengthSquared() > 0.01) {
                push.normalize().multiply(0.8).setY(0.35);
                target.setVelocity(target.getVelocity().add(push));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void preventTotemConsumption(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (is(player.getInventory().getItemInMainHand(), TOTEM_ID)
                || is(player.getInventory().getItemInOffHand(), TOTEM_ID)) {
            event.setCancelled(true);
            player.sendActionBar(ChatColor.AQUA + "Warden Totem is infinite-use: right-click it to activate.");
        }
    }

    private boolean startCooldown(Player player, String ability, long millis, String display) {
        String key = player.getUniqueId() + ":" + ability;
        long now = System.currentTimeMillis();
        long until = cooldowns.getOrDefault(key, 0L);

        if (until > now) {
            if (display != null) {
                double seconds = Math.ceil((until - now) / 100.0) / 10.0;
                player.sendActionBar(ChatColor.RED + display + " recharging: " + seconds + "s");
            }
            return false;
        }

        cooldowns.put(key, now + millis);
        return true;
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        String prefix = id.toString() + ":";
        cooldowns.keySet().removeIf(key -> key.startsWith(prefix));
        marked.entrySet().removeIf(e -> e.getValue().owner().equals(id) || e.getKey().equals(id));
    }
}
