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
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ESN Lightning King exclusive set.
 *
 * This is intentionally above Void Warrior in the ESN gear hierarchy.
 * Passive work is driven by LowMemoryEffectsTicker so the set does not create
 * per-player repeating tasks.
 */
public final class LightningKingBundle implements Listener {
    public static final String CROWN_ID = "lightningkingcrown";
    public static final String CHEST_ID = "lightningkingchest";
    public static final String LEGS_ID = "lightningkinglegs";
    public static final String BOOTS_ID = "lightningkingboots";
    public static final String BLADE_ID = "lightningkingblade";
    public static final String BOW_ID = "lightningkingbow";
    public static final String CORE_ID = "lightningkingcore";

    private static final NamespacedKey LIGHTNING_TYPE = new NamespacedKey("esnsmp", "lightning_king_type");
    private static final NamespacedKey ADMIN_TEST = new NamespacedKey("esnsmp", "admin_test_item");
    private static final NamespacedKey MEGA_ITEM = new NamespacedKey("esnsmp", "mega_item");
    private static final NamespacedKey LIGHTNING_PROJECTILE = new NamespacedKey("esnsmp", "lightning_king_projectile");

    private static final long JUDGMENT_COOLDOWN_MS = 6_000L;
    private static final long STEP_COOLDOWN_MS = 3_000L;
    private static final long CAGE_COOLDOWN_MS = 10_000L;
    private static final long SKYFALL_COOLDOWN_MS = 9_000L;
    private static final long WINGS_COOLDOWN_MS = 6_000L;
    private static final long OVERCHARGE_COOLDOWN_MS = 22_000L;
    private static final long WRATH_COOLDOWN_MS = 45_000L;
    private static final long SHIELD_COOLDOWN_MS = 18_000L;
    private static final long REVIVAL_COOLDOWN_MS = 120_000L;
    private static final long REFLEX_COOLDOWN_MS = 2_500L;

    private static final double STEP_RANGE = 28.0;
    private static final double JUDGMENT_DAMAGE = 4_500.0;
    private static final double CHAIN_DAMAGE = 1_200.0;
    private static final double STORMBREAKER_DAMAGE = 3_000.0;

    private final ESNSMPPlugin plugin;

    private final Map<UUID, Long> judgmentCooldown = new HashMap<>();
    private final Map<UUID, Long> stepCooldown = new HashMap<>();
    private final Map<UUID, Long> cageCooldown = new HashMap<>();
    private final Map<UUID, Long> skyfallCooldown = new HashMap<>();
    private final Map<UUID, Long> wingsCooldown = new HashMap<>();
    private final Map<UUID, Long> overchargeCooldown = new HashMap<>();
    private final Map<UUID, Long> wrathCooldown = new HashMap<>();
    private final Map<UUID, Long> shieldCooldown = new HashMap<>();
    private final Map<UUID, Long> revivalCooldown = new HashMap<>();
    private final Map<UUID, Long> reflexCooldown = new HashMap<>();
    private final Map<UUID, Long> overchargeUntil = new HashMap<>();
    private final Map<UUID, Long> wrathUntil = new HashMap<>();
    private final Map<UUID, Long> lastSneak = new HashMap<>();
    private final Map<UUID, Integer> staticCharge = new HashMap<>();
    private final Map<UUID, Integer> electrocution = new HashMap<>();
    private final Set<UUID> internalDamage = new HashSet<>();

    public LightningKingBundle(ESNSMPPlugin plugin) {
        this.plugin = plugin;
    }

    public static ItemStack crown() {
        return realmPiece(5, CROWN_ID, "LIGHTNING KING CROWN",
                "Lightning Reflex + lightning/fire immunity.",
                "Full set: King of the Storm passive.");
    }

    public static ItemStack chestplate() {
        return realmPiece(6, CHEST_ID, "LIGHTNING KING CHESTPLATE",
                "Tempest Shield and Thunder Revival.",
                "Maintains 60 Absorption hearts.");
    }

    public static ItemStack leggings() {
        return realmPiece(7, LEGS_ID, "LIGHTNING KING LEGGINGS",
                "Thunder Counter + massive knockback resistance.",
                "Full set: Strength V and storm aura.");
    }

    public static ItemStack boots() {
        return realmPiece(8, BOOTS_ID, "LIGHTNING KING BOOTS",
                "Double-sneak: THUNDERSTEP.",
                "Sneak-jump: SKYFALL. Fall damage immunity.");
    }

    public static ItemStack blade() {
        ItemStack item = realmPiece(0, BLADE_ID, "LIGHTNING KING BLADE",
                "Right-click: KING'S JUDGMENT + CHAIN LIGHTNING.",
                "Sneak + right-click: THUNDER CAGE.",
                "Left-click air/block: OVERCHARGE.",
                "Melee builds Static Charge and Lightning Execution.");
        item.addUnsafeEnchantment(Enchantment.SHARPNESS, 350);
        item.addUnsafeEnchantment(Enchantment.UNBREAKING, 350);
        item.removeEnchantment(Enchantment.LOOTING);
        return item;
    }

    public static ItemStack bow() {
        ItemStack item = realmPiece(9, BOW_ID, "LIGHTNING KING BOW",
                "Arrows become STORMBREAKER bolts.",
                "Hits chain lightning and build Electrocution.");
        item.addUnsafeEnchantment(Enchantment.POWER, 350);
        item.addUnsafeEnchantment(Enchantment.PUNCH, 10);
        item.addUnsafeEnchantment(Enchantment.INFINITY, 1);
        item.addUnsafeEnchantment(Enchantment.UNBREAKING, 350);
        return item;
    }

    public static ItemStack core() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + "⚡ LIGHTNING KING CORE ⚡");
        meta.setLore(List.of(
                ChatColor.GOLD + "Right-click: WRATH OF THE KING + STORMCALL.",
                ChatColor.AQUA + "Left-click: STORM WINGS.",
                ChatColor.YELLOW + "The ultimate relic of the Lightning King set.",
                "",
                ChatColor.GOLD + "" + ChatColor.BOLD + "⚡ LIGHTNING KING EXCLUSIVE ⚡",
                ChatColor.GRAY + "Built to overpower the Void Warrior set.",
                ChatColor.GRAY + "Admin testing only — not mapped to Stripe yet.",
                ChatColor.GOLD + "" + ChatColor.BOLD + "UNBREAKABLE"
        ));
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(LIGHTNING_TYPE, PersistentDataType.STRING, CORE_ID);
        meta.getPersistentDataContainer().set(ADMIN_TEST, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        item.addUnsafeEnchantment(Enchantment.UNBREAKING, 350);
        return item;
    }

    private static ItemStack realmPiece(int slot, String type, String name, String... abilityLore) {
        ItemStack item = MegaCrates.item(99, slot).clone();
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + "⚡ " + name + " ⚡");

        List<String> lore = new ArrayList<>();
        for (String line : abilityLore) lore.add(ChatColor.AQUA + line);
        lore.add("");
        lore.add(ChatColor.GOLD + "" + ChatColor.BOLD + "⚡ LIGHTNING KING EXCLUSIVE ⚡");
        lore.add(ChatColor.GRAY + "The strongest ESN SMP exclusive set.");
        lore.add(ChatColor.GRAY + "Designed far above Void Warrior.");
        lore.add(ChatColor.GRAY + "Admin testing only — not mapped to Stripe yet.");
        lore.add(ChatColor.GOLD + "" + ChatColor.BOLD + "UNBREAKABLE");
        meta.setLore(lore);
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().remove(MEGA_ITEM);
        meta.getPersistentDataContainer().set(LIGHTNING_TYPE, PersistentDataType.STRING, type);
        meta.getPersistentDataContainer().set(ADMIN_TEST, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);

        if (slot >= 5 && slot <= 8) {
            item.addUnsafeEnchantment(Enchantment.PROTECTION, 350);
            item.addUnsafeEnchantment(Enchantment.UNBREAKING, 350);
        }
        return item;
    }

    private static String type(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return "";
        return item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(LIGHTNING_TYPE, PersistentDataType.STRING, "");
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
        clean(judgmentCooldown, now);
        clean(stepCooldown, now);
        clean(cageCooldown, now);
        clean(skyfallCooldown, now);
        clean(wingsCooldown, now);
        clean(overchargeCooldown, now);
        clean(wrathCooldown, now);
        clean(shieldCooldown, now);
        clean(revivalCooldown, now);
        clean(reflexCooldown, now);
        clean(overchargeUntil, now);
        clean(wrathUntil, now);
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
        long now = System.currentTimeMillis();
        boolean overcharged = overchargeUntil.getOrDefault(player.getUniqueId(), 0L) > now;
        boolean wrath = wrathUntil.getOrDefault(player.getUniqueId(), 0L) > now;

        if (crown) {
            ensureEffect(player, PotionEffectType.RESISTANCE, full ? 3 : 1);
        }
        if (chest && player.getAbsorptionAmount() < 120.0) {
            player.setAbsorptionAmount(120.0);
        }
        if (legs) {
            ensureEffect(player, PotionEffectType.STRENGTH, full ? 4 : 2);
        }
        if (boots) {
            ensureEffect(player, PotionEffectType.SPEED, full ? 4 : 2);
        }

        if (!full) return;

        ensureEffect(player, PotionEffectType.REGENERATION, 2);
        if (overcharged || wrath) {
            ensureEffect(player, PotionEffectType.SPEED, wrath ? 7 : 6);
            ensureEffect(player, PotionEffectType.STRENGTH, wrath ? 7 : 6);
            ensureEffect(player, PotionEffectType.RESISTANCE, wrath ? 5 : 4);
        }

        // Storm Aura + Thunder Rush.
        double auraDamage = player.isSprinting() ? 110.0 : 65.0;
        for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), 4.5, 3.5, 4.5)) {
            if (!(entity instanceof LivingEntity target) || target.equals(player)) continue;
            if (entity instanceof Player other && other.getUniqueId().equals(player.getUniqueId())) continue;
            dealLightningDamage(player, target, auraDamage);
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 2, true, false, false), true);
            if (player.isSprinting()) {
                Vector away = target.getLocation().toVector().subtract(player.getLocation().toVector());
                if (away.lengthSquared() > 0.01) {
                    Vector push = away.normalize().multiply(0.35);
                    push.setY(0.12);
                    target.setVelocity(target.getVelocity().multiply(0.45).add(push));
                }
            }
        }

        if (player.isSprinting()) {
            ensureEffect(player, PotionEffectType.SPEED, 6);
        }

        if (showAura) {
            Location at = player.getLocation().add(0, 1.0, 0);
            player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, at, 12, 0.8, 1.0, 0.8, 0.08);
            player.getWorld().spawnParticle(Particle.END_ROD, at, 4, 0.5, 0.8, 0.5, 0.03);

            // Lightning Rod: every visual cycle the nearest hostile/other living target is struck.
            LivingEntity rodTarget = nearestTarget(player, 12.0, null);
            if (rodTarget != null) {
                player.getWorld().strikeLightningEffect(rodTarget.getLocation());
                dealLightningDamage(player, rodTarget, wrath ? 1_500.0 : 900.0);
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

        Player player = event.getPlayer();
        ItemStack held = event.getItem();
        Action action = event.getAction();

        if (is(held, BLADE_ID)) {
            if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                event.setCancelled(true);
                if (player.isSneaking()) thunderCage(player);
                else kingsJudgment(player);
                return;
            }
            if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
                if (fullSet(player)) {
                    event.setCancelled(true);
                    overcharge(player);
                }
                return;
            }
        }

        if (is(held, CORE_ID)) {
            if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                event.setCancelled(true);
                wrathOfKing(player);
                return;
            }
            if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
                event.setCancelled(true);
                stormWings(player);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        Player player = event.getPlayer();
        if (!is(player.getInventory().getBoots(), BOOTS_ID)) return;

        long now = System.currentTimeMillis();
        UUID id = player.getUniqueId();
        long previous = lastSneak.getOrDefault(id, 0L);
        lastSneak.put(id, now);
        if (now - previous > 450L) return;

        thunderStep(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onJump(PlayerJumpEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking() || !fullSet(player)) return;
        if (!startCooldown(player, skyfallCooldown, SKYFALL_COOLDOWN_MS, "Skyfall")) return;

        Vector launch = player.getLocation().getDirection().setY(0).normalize().multiply(0.55);
        launch.setY(1.55);
        player.setVelocity(launch);
        player.sendActionBar(ChatColor.YELLOW + "" + ChatColor.BOLD + "SKYFALL");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || player.isDead()) return;
            Location impact = player.getLocation();
            player.setVelocity(new Vector(0, -3.0, 0));
            player.setFallDistance(0f);
            impact.getWorld().spawnParticle(Particle.FLASH, impact.clone().add(0, 0.5, 0), 2);
            impact.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, impact.clone().add(0, 0.5, 0),
                    80, 4.5, 0.8, 4.5, 0.15);
            for (Entity entity : impact.getWorld().getNearbyEntities(impact, 8, 5, 8)) {
                if (!(entity instanceof LivingEntity target) || target.equals(player)) continue;
                dealLightningDamage(player, target, 1_800.0);
                Vector away = target.getLocation().toVector().subtract(impact.toVector());
                if (away.lengthSquared() > 0.01) {
                    Vector knock = away.normalize().multiply(1.8);
                    knock.setY(0.75);
                    target.setVelocity(knock);
                }
            }
        }, 14L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBowShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!is(event.getBow(), BOW_ID)) return;
        if (!(event.getProjectile() instanceof Arrow arrow)) return;

        arrow.getPersistentDataContainer().set(LIGHTNING_PROJECTILE, PersistentDataType.BYTE, (byte) 1);
        arrow.setVelocity(arrow.getVelocity().multiply(1.45));
        player.sendActionBar(ChatColor.AQUA + "STORMBREAKER BOLT");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void combat(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player internalSource
                && internalDamage.contains(internalSource.getUniqueId())) return;

        // Stormbreaker bow hit.
        if (event.getDamager() instanceof Arrow arrow
                && arrow.getPersistentDataContainer().has(LIGHTNING_PROJECTILE, PersistentDataType.BYTE)
                && arrow.getShooter() instanceof Player shooter
                && event.getEntity() instanceof LivingEntity bowTarget) {
            event.setDamage(Math.max(event.getDamage(), STORMBREAKER_DAMAGE));
            bowTarget.getWorld().strikeLightningEffect(bowTarget.getLocation());
            chainLightning(shooter, bowTarget, 10, 900.0);
            addElectrocution(shooter, bowTarget);
            return;
        }

        if (!(event.getDamager() instanceof Player attacker)
                || !(event.getEntity() instanceof LivingEntity target)) {
            // Thunder Counter can still trigger for non-player attackers.
            if (event.getEntity() instanceof Player victim && fullSet(victim)
                    && event.getDamager() instanceof LivingEntity source) {
                thunderCounter(victim, source);
            }
            return;
        }

        if (fullSet(attacker) && !internalDamage.contains(attacker.getUniqueId())) {
            int charge = staticCharge.merge(attacker.getUniqueId(), 1, Integer::sum);
            if (charge >= 3) {
                staticCharge.put(attacker.getUniqueId(), 0);
                event.setDamage(event.getDamage() + 2_500.0);
                target.getWorld().strikeLightningEffect(target.getLocation());
                attacker.sendActionBar(ChatColor.YELLOW + "" + ChatColor.BOLD + "STATIC CHARGE DETONATED");
            }

            addElectrocution(attacker, target);

            double predicted = target.getHealth() - event.getFinalDamage();
            if (predicted <= target.getMaxHealth() * 0.30) {
                event.setDamage(event.getDamage() + Math.max(3_500.0, target.getMaxHealth() * 0.45));
                target.getWorld().strikeLightningEffect(target.getLocation());
                attacker.sendActionBar(ChatColor.GOLD + "" + ChatColor.BOLD + "LIGHTNING EXECUTION");
            }
        }

        if (event.getEntity() instanceof Player victim && fullSet(victim)) {
            thunderCounter(victim, attacker);
        }
    }

    private void addElectrocution(Player attacker, LivingEntity target) {
        int stacks = electrocution.merge(target.getUniqueId(), 1, Integer::sum);
        if (stacks < 4) return;

        electrocution.put(target.getUniqueId(), 0);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 6, true, true, true), true);
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 4, true, true, true), true);
        target.getWorld().strikeLightningEffect(target.getLocation());
        dealLightningDamage(attacker, target, 1_600.0);
        attacker.sendActionBar(ChatColor.AQUA + "" + ChatColor.BOLD + "ELECTROCUTION");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void damage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        boolean boots = is(player.getInventory().getBoots(), BOOTS_ID);
        boolean crown = is(player.getInventory().getHelmet(), CROWN_ID);
        boolean full = fullSet(player);

        if (boots && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
            player.setFallDistance(0f);
            return;
        }

        if (crown && (event.getCause() == EntityDamageEvent.DamageCause.LIGHTNING
                || event.getCause() == EntityDamageEvent.DamageCause.FIRE
                || event.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK
                || event.getCause() == EntityDamageEvent.DamageCause.LAVA
                || event.getCause() == EntityDamageEvent.DamageCause.HOT_FLOOR)) {
            event.setCancelled(true);
            return;
        }

        if (!full) return;

        // Thunder Revival gets first priority on lethal damage.
        if (event.getFinalDamage() >= player.getHealth() + player.getAbsorptionAmount()) {
            if (startCooldown(player, revivalCooldown, REVIVAL_COOLDOWN_MS, null)) {
                event.setCancelled(true);
                player.setHealth(Math.max(1.0, Math.min(player.getMaxHealth(), player.getMaxHealth() * 0.65)));
                player.setAbsorptionAmount(120.0);
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 120, 7, true, true, true), true);
                player.getWorld().strikeLightningEffect(player.getLocation());
                blastFrom(player, 9.0, 1_500.0, 2.0);
                player.sendActionBar(ChatColor.GOLD + "" + ChatColor.BOLD + "THUNDER REVIVAL");
                return;
            }
        }

        // Lightning Reflex: a strong periodic automatic dodge.
        if (ThreadLocalRandom.current().nextDouble() < 0.30
                && startCooldown(player, reflexCooldown, REFLEX_COOLDOWN_MS, null)) {
            event.setCancelled(true);
            lightningReflex(player);
            return;
        }

        // Tempest Shield when projected health falls under 60%.
        double predicted = player.getHealth() + player.getAbsorptionAmount() - event.getFinalDamage();
        if (predicted <= player.getMaxHealth() * 0.60
                && startCooldown(player, shieldCooldown, SHIELD_COOLDOWN_MS, null)) {
            player.setAbsorptionAmount(Math.max(player.getAbsorptionAmount(), 160.0));
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 160, 5, true, true, true), true);
            player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, player.getLocation().add(0, 1, 0),
                    60, 1.2, 1.3, 1.2, 0.12);
            player.sendActionBar(ChatColor.AQUA + "" + ChatColor.BOLD + "TEMPEST SHIELD");
        }
    }

    private void kingsJudgment(Player player) {
        if (!startCooldown(player, judgmentCooldown, JUDGMENT_COOLDOWN_MS, "King's Judgment")) return;
        LivingEntity target = nearestTarget(player, 42.0, null);
        if (target == null) {
            player.sendActionBar(ChatColor.RED + "No target for King's Judgment.");
            judgmentCooldown.remove(player.getUniqueId());
            return;
        }

        target.getWorld().strikeLightningEffect(target.getLocation());
        target.getWorld().spawnParticle(Particle.FLASH, target.getLocation().add(0, 1, 0), 3);
        dealLightningDamage(player, target, JUDGMENT_DAMAGE);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 8, true, true, true), true);
        chainLightning(player, target, 12, CHAIN_DAMAGE);
        player.sendActionBar(ChatColor.GOLD + "" + ChatColor.BOLD + "KING'S JUDGMENT");
    }

    private void thunderCage(Player player) {
        if (!startCooldown(player, cageCooldown, CAGE_COOLDOWN_MS, "Thunder Cage")) return;
        LivingEntity target = nearestTarget(player, 24.0, null);
        if (target == null) {
            player.sendActionBar(ChatColor.RED + "No target for Thunder Cage.");
            cageCooldown.remove(player.getUniqueId());
            return;
        }

        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 120, 12, true, true, true), true);
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 120, 5, true, true, true), true);

        for (int pulse = 0; pulse < 4; pulse++) {
            long delay = pulse * 10L;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!target.isValid() || target.isDead()) return;
                Location at = target.getLocation();
                at.getWorld().strikeLightningEffect(at);
                at.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, at.clone().add(0, 1, 0),
                        40, 1.3, 1.5, 1.3, 0.08);
                dealLightningDamage(player, target, 900.0);
                target.setVelocity(target.getVelocity().multiply(0.12));
            }, delay);
        }

        player.sendActionBar(ChatColor.YELLOW + "" + ChatColor.BOLD + "THUNDER CAGE");
    }

    private void thunderStep(Player player) {
        if (!startCooldown(player, stepCooldown, STEP_COOLDOWN_MS, "Thunderstep")) return;
        Location target = safeLookTarget(player, STEP_RANGE);
        if (target == null) {
            player.sendActionBar(ChatColor.RED + "No safe Thunderstep destination.");
            stepCooldown.remove(player.getUniqueId());
            return;
        }

        Location from = player.getLocation();
        Vector travel = target.toVector().subtract(from.toVector());
        double distance = travel.length();
        Vector direction = distance > 0.01 ? travel.normalize() : new Vector(0, 0, 0);

        // Damage targets along the dash path.
        int samples = Math.max(1, (int) Math.ceil(distance / 2.0));
        Set<UUID> hit = new HashSet<>();
        for (int i = 0; i <= samples; i++) {
            Location sample = from.clone().add(direction.clone().multiply(Math.min(distance, i * 2.0)));
            for (Entity entity : sample.getWorld().getNearbyEntities(sample, 2.2, 2.5, 2.2)) {
                if (!(entity instanceof LivingEntity living) || living.equals(player)) continue;
                if (!hit.add(living.getUniqueId())) continue;
                dealLightningDamage(player, living, 1_100.0);
                living.getWorld().strikeLightningEffect(living.getLocation());
            }
        }

        from.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, from.clone().add(0, 1, 0),
                50, 0.8, 1.0, 0.8, 0.1);
        player.teleport(target);
        target.getWorld().spawnParticle(Particle.FLASH, target.clone().add(0, 1, 0), 2);
        target.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, target.clone().add(0, 1, 0),
                50, 0.8, 1.0, 0.8, 0.1);
        player.sendActionBar(ChatColor.AQUA + "" + ChatColor.BOLD + "THUNDERSTEP");
    }

    private void overcharge(Player player) {
        if (!startCooldown(player, overchargeCooldown, OVERCHARGE_COOLDOWN_MS, "Overcharge")) return;
        long until = System.currentTimeMillis() + 12_000L;
        overchargeUntil.put(player.getUniqueId(), until);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 240, 6, true, true, true), true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 240, 6, true, true, true), true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 240, 4, true, true, true), true);
        player.getWorld().strikeLightningEffect(player.getLocation());
        player.sendActionBar(ChatColor.YELLOW + "" + ChatColor.BOLD + "OVERCHARGE — 12s");
    }

    private void stormWings(Player player) {
        if (!fullSet(player)) {
            player.sendActionBar(ChatColor.RED + "Storm Wings requires the full Lightning King armor set.");
            return;
        }
        if (!startCooldown(player, wingsCooldown, WINGS_COOLDOWN_MS, "Storm Wings")) return;

        Vector boost = player.getEyeLocation().getDirection().normalize().multiply(3.2);
        boost.setY(Math.max(1.0, boost.getY() + 1.0));
        player.setVelocity(boost);
        player.setFallDistance(0f);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 180, 0, true, false, true), true);
        player.getWorld().strikeLightningEffect(player.getLocation());
        player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, player.getLocation().add(0, 1, 0),
                80, 1.2, 1.0, 1.2, 0.16);
        blastFrom(player, 5.0, 600.0, 1.2);
        player.sendActionBar(ChatColor.AQUA + "" + ChatColor.BOLD + "STORM WINGS");
    }

    private void wrathOfKing(Player player) {
        if (!fullSet(player)) {
            player.sendActionBar(ChatColor.RED + "Wrath of the King requires the full Lightning King armor set.");
            return;
        }
        if (!startCooldown(player, wrathCooldown, WRATH_COOLDOWN_MS, "Wrath of the King")) return;

        long until = System.currentTimeMillis() + 15_000L;
        wrathUntil.put(player.getUniqueId(), until);
        overchargeUntil.put(player.getUniqueId(), until);
        player.setAbsorptionAmount(Math.max(player.getAbsorptionAmount(), 200.0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 300, 7, true, true, true), true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 300, 7, true, true, true), true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 300, 5, true, true, true), true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 300, 4, true, true, true), true);
        player.sendActionBar(ChatColor.GOLD + "" + ChatColor.BOLD + "WRATH OF THE KING — STORMCALL");

        // Seven heavy storm pulses over the 15-second ultimate.
        for (int pulse = 0; pulse < 7; pulse++) {
            long delay = pulse * 40L;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline() || player.isDead()) return;
                Location center = player.getLocation();
                center.getWorld().strikeLightningEffect(center);
                center.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, center.clone().add(0, 1, 0),
                        120, 8.0, 3.0, 8.0, 0.18);
                List<LivingEntity> targets = nearbyTargets(player, 18.0);
                int count = 0;
                for (LivingEntity target : targets) {
                    if (count++ >= 14) break;
                    target.getWorld().strikeLightningEffect(target.getLocation());
                    dealLightningDamage(player, target, 1_400.0);
                    Vector away = target.getLocation().toVector().subtract(center.toVector());
                    if (away.lengthSquared() > 0.01) {
                        Vector blast = away.normalize().multiply(1.0);
                        blast.setY(0.35);
                        target.setVelocity(blast);
                    }
                }
            }, delay);
        }
    }

    private void lightningReflex(Player player) {
        Location safe = safeSideStep(player, 4.5);
        Location from = player.getLocation();
        if (safe != null) player.teleport(safe);
        from.getWorld().strikeLightningEffect(from);
        player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, player.getLocation().add(0, 1, 0),
                35, 0.7, 1.0, 0.7, 0.1);
        player.sendActionBar(ChatColor.AQUA + "" + ChatColor.BOLD + "LIGHTNING REFLEX");
    }

    private void thunderCounter(Player victim, LivingEntity attacker) {
        if (internalDamage.contains(victim.getUniqueId())) return;
        if (ThreadLocalRandom.current().nextDouble() >= 0.50) return;

        attacker.getWorld().strikeLightningEffect(attacker.getLocation());
        dealLightningDamage(victim, attacker, 1_000.0);
        Vector away = attacker.getLocation().toVector().subtract(victim.getLocation().toVector());
        if (away.lengthSquared() > 0.01) {
            Vector blast = away.normalize().multiply(0.9);
            blast.setY(0.25);
            attacker.setVelocity(blast);
        }
        victim.sendActionBar(ChatColor.YELLOW + "THUNDER COUNTER");
    }

    private void chainLightning(Player source, LivingEntity first, int maxTargets, double damage) {
        LivingEntity current = first;
        Set<UUID> hit = new HashSet<>();
        hit.add(first.getUniqueId());

        for (int i = 0; i < maxTargets; i++) {
            LivingEntity next = nearestLiving(current.getLocation(), 9.0, source, hit);
            if (next == null) break;
            hit.add(next.getUniqueId());
            current.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,
                    current.getLocation().add(0, 1, 0), 12, 0.5, 0.7, 0.5, 0.08);
            next.getWorld().strikeLightningEffect(next.getLocation());
            dealLightningDamage(source, next, damage);
            current = next;
        }
    }

    private LivingEntity nearestTarget(Player player, double range, Set<UUID> excluded) {
        Location eye = player.getEyeLocation();
        Vector forward = eye.getDirection().normalize();
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;

        for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), range, range, range)) {
            if (!(entity instanceof LivingEntity target) || target.equals(player)) continue;
            if (excluded != null && excluded.contains(target.getUniqueId())) continue;

            Vector delta = target.getLocation().add(0, 0.8, 0).toVector().subtract(eye.toVector());
            double distance = delta.length();
            if (distance <= 0.01 || distance > range) continue;
            double dot = forward.dot(delta.clone().normalize());
            if (dot < 0.55) continue;

            double score = distance - (dot * 5.0);
            if (score < bestScore) {
                bestScore = score;
                best = target;
            }
        }
        return best;
    }

    private static LivingEntity nearestLiving(Location center, double radius, Player source, Set<UUID> excluded) {
        return center.getWorld().getNearbyEntities(center, radius, radius, radius).stream()
                .filter(e -> e instanceof LivingEntity)
                .map(e -> (LivingEntity) e)
                .filter(e -> !e.equals(source))
                .filter(e -> excluded == null || !excluded.contains(e.getUniqueId()))
                .min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(center)))
                .orElse(null);
    }

    private static List<LivingEntity> nearbyTargets(Player source, double radius) {
        List<LivingEntity> targets = new ArrayList<>();
        for (Entity entity : source.getWorld().getNearbyEntities(source.getLocation(), radius, radius / 2.0, radius)) {
            if (entity instanceof LivingEntity living && !living.equals(source)) targets.add(living);
        }
        targets.sort(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(source.getLocation())));
        return targets;
    }

    private void blastFrom(Player source, double radius, double damage, double force) {
        Location center = source.getLocation();
        for (LivingEntity target : nearbyTargets(source, radius)) {
            dealLightningDamage(source, target, damage);
            Vector away = target.getLocation().toVector().subtract(center.toVector());
            if (away.lengthSquared() > 0.01) {
                Vector blast = away.normalize().multiply(force);
                blast.setY(0.4);
                target.setVelocity(blast);
            }
        }
    }

    private void dealLightningDamage(Player source, LivingEntity target, double damage) {
        if (!target.isValid() || target.isDead() || target.equals(source)) return;
        UUID sourceId = source.getUniqueId();
        boolean added = internalDamage.add(sourceId);
        try {
            target.damage(damage, source);
        } finally {
            if (added) internalDamage.remove(sourceId);
        }
    }

    private static boolean startCooldown(Player player, Map<UUID, Long> cooldown, long durationMs, String label) {
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

    private static Location safeSideStep(Player player, double distance) {
        Vector direction = player.getLocation().getDirection().setY(0);
        if (direction.lengthSquared() <= 0.01) return null;
        Vector right = new Vector(-direction.getZ(), 0, direction.getX()).normalize();

        Location a = safeAt(player, player.getLocation().clone().add(right.clone().multiply(distance)));
        if (a != null) return a;
        return safeAt(player, player.getLocation().clone().subtract(right.multiply(distance)));
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

        Location best = null;
        for (double distance = 1.5; distance <= maxDistance; distance += 0.75) {
            double sightX = eye.getX() + direction.getX() * distance;
            double sightY = eye.getY() + direction.getY() * distance;
            double sightZ = eye.getZ() + direction.getZ() * distance;

            if (Math.abs(sightX - center.getX()) > half || Math.abs(sightZ - center.getZ()) > half) break;
            if (sightY < world.getMinHeight() + 1 || sightY >= world.getMaxHeight() - 1) break;

            Block sightBlock = world.getBlockAt(floor(sightX), floor(sightY), floor(sightZ));
            if (!sightBlock.isPassable()) break;

            Location candidate = new Location(world, sightX, sightY - eyeHeight, sightZ,
                    player.getLocation().getYaw(), player.getLocation().getPitch());
            Location safe = safeAt(player, candidate);
            if (safe != null) best = safe;
        }
        return best;
    }

    private static Location safeAt(Player player, Location candidate) {
        World world = candidate.getWorld();
        if (world == null) return null;

        int bx = floor(candidate.getX());
        int by = floor(candidate.getY());
        int bz = floor(candidate.getZ());
        if (by <= world.getMinHeight() || by >= world.getMaxHeight() - 2) return null;

        Block feet = world.getBlockAt(bx, by, bz);
        Block head = world.getBlockAt(bx, by + 1, bz);
        Block floor = world.getBlockAt(bx, by - 1, bz);
        if (!feet.isPassable() || !head.isPassable() || !floor.getType().isSolid()) return null;
        if (unsafe(feet.getType()) || unsafe(head.getType()) || unsafe(floor.getType())) return null;

        return new Location(world, candidate.getX(), by, candidate.getZ(),
                player.getLocation().getYaw(), player.getLocation().getPitch());
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

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        judgmentCooldown.remove(id);
        stepCooldown.remove(id);
        cageCooldown.remove(id);
        skyfallCooldown.remove(id);
        wingsCooldown.remove(id);
        overchargeCooldown.remove(id);
        wrathCooldown.remove(id);
        shieldCooldown.remove(id);
        revivalCooldown.remove(id);
        reflexCooldown.remove(id);
        overchargeUntil.remove(id);
        wrathUntil.remove(id);
        lastSneak.remove(id);
        staticCharge.remove(id);
        electrocution.remove(id);
        internalDamage.remove(id);
    }
}
