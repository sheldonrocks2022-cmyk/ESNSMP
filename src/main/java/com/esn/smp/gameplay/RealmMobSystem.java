package com.esn.smp.gameplay;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class RealmMobSystem implements Listener, CommandExecutor {
    private final JavaPlugin plugin;
    private final NamespacedKey mobIdKey;
    private final NamespacedKey realmKey;
    private final NamespacedKey essenceKey;
    private final Map<String, Long> nextInvasion = new HashMap<>();

    public RealmMobSystem(JavaPlugin plugin) {
        this.plugin = plugin;
        this.mobIdKey = new NamespacedKey(plugin, "realm_mob_id");
        this.realmKey = new NamespacedKey(plugin, "realm_id");
        this.essenceKey = new NamespacedKey(plugin, "realm_essence");
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::spawnTick, 20L * 20L, 20L * 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::eventTick, 20L * 60L, 20L * 60L);
        plugin.getLogger().info("[ESN Realms] Realm mob ecosystem online.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("realmguide")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Players only.");
                return true;
            }
            showGuide(p);
            return true;
        }

        if (name.equals("realmevent")) {
            if (!sender.hasPermission("esnsmp.admin")) {
                sender.sendMessage(ChatColor.RED + "ESN admin only.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(ChatColor.YELLOW + "/realmevent <invasion|miniboss|clear> <realm>");
                return true;
            }
            String realm = normalize(args[1]);
            if (realm == null) {
                sender.sendMessage(ChatColor.RED + "Unknown realm.");
                return true;
            }
            World world = Bukkit.getWorld(worldName(realm));
            if (world == null) {
                sender.sendMessage(ChatColor.RED + "That realm is not loaded.");
                return true;
            }
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "invasion" -> {
                    Player target = world.getPlayers().stream().findAny().orElse(null);
                    if (target == null) sender.sendMessage(ChatColor.RED + "A player must be inside the realm.");
                    else startInvasion(realm, world, target);
                }
                case "miniboss" -> {
                    Player target = world.getPlayers().stream().findAny().orElse(null);
                    if (target == null) sender.sendMessage(ChatColor.RED + "A player must be inside the realm.");
                    else spawnMiniBoss(realm, target);
                }
                case "clear" -> {
                    int removed = clearRealmMobs(world);
                    sender.sendMessage(ChatColor.GREEN + "Removed " + removed + " ESN realm mob(s).");
                }
                default -> sender.sendMessage(ChatColor.YELLOW + "/realmevent <invasion|miniboss|clear> <realm>");
            }
            return true;
        }
        return true;
    }

    private void showGuide(Player p) {
        String realm = realmFromWorld(p.getWorld());
        p.sendMessage(ChatColor.DARK_PURPLE + "=== ESN REALM CREATURE GUIDE ===");
        if (realm == null || realm.equals("nexus")) {
            p.sendMessage(ChatColor.GRAY + "Enter a realm to see its local creatures.");
            p.sendMessage(ChatColor.GRAY + "New realms: " + ChatColor.GREEN + "Verdant Wilds" + ChatColor.GRAY + ", " +
                    ChatColor.AQUA + "Celestial Isles" + ChatColor.GRAY + ", " + ChatColor.DARK_RED + "Bloodmoon Wastes");
            return;
        }
        p.sendMessage(ChatColor.GRAY + "Realm: " + ChatColor.WHITE + display(realm));
        for (MobProfile profile : profiles(realm)) {
            p.sendMessage(profile.color + "• " + profile.name + ChatColor.GRAY + " — " + profile.description);
        }
        p.sendMessage(ChatColor.GOLD + "Elite mobs" + ChatColor.GRAY + " have boosted stats and better essence drops.");
        p.sendMessage(ChatColor.RED + "Realm invasions" + ChatColor.GRAY + " can erupt while players are exploring.");
    }

    private void spawnTick() {
        for (String realm : realms()) {
            World world = Bukkit.getWorld(worldName(realm));
            if (world == null || world.getPlayers().isEmpty()) continue;

            int current = countRealmMobs(world);
            int cap = Math.min(45, 10 + world.getPlayers().size() * 7);
            if (current >= cap) continue;

            applyAmbience(realm, world);
            int attempts = Math.min(4, cap - current);
            for (int i = 0; i < attempts; i++) {
                Player anchor = world.getPlayers().get(ThreadLocalRandom.current().nextInt(world.getPlayers().size()));
                Location at = findSpawn(anchor, 18, 42);
                if (at == null) continue;
                MobProfile profile = chooseProfile(realm);
                spawnMob(realm, profile, at, ThreadLocalRandom.current().nextDouble() < 0.08);
            }
        }
    }


    private void applyAmbience(String realm, World world) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(world.getSpawnLocation()) < 22 * 22) continue;
            if (random.nextDouble() > 0.32) continue;

            switch (realm) {
                case "storm" -> {
                    Location strike = p.getLocation().clone().add(random.nextInt(-9, 10), 0, random.nextInt(-9, 10));
                    world.strikeLightningEffect(strike);
                    world.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.35f, 1.25f);
                }
                case "abyss" -> {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 45, 0));
                    world.spawnParticle(Particle.SCULK_SOUL, p.getLocation().add(0, 1, 0), 8, 1.2, 0.8, 1.2, 0.01);
                }
                case "frost" -> {
                    p.setFreezeTicks(Math.min(p.getMaxFreezeTicks(), p.getFreezeTicks() + 18));
                    world.spawnParticle(Particle.SNOWFLAKE, p.getLocation().add(0, 1, 0), 18, 1.8, 1.0, 1.8, 0.02);
                }
                case "infernal" -> {
                    world.spawnParticle(Particle.FLAME, p.getLocation().add(0, 0.5, 0), 12, 1.5, 0.4, 1.5, 0.03);
                    if (random.nextDouble() < 0.18) p.setFireTicks(Math.max(p.getFireTicks(), 25));
                }
                case "verdant" -> {
                    world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, p.getLocation().add(0, 1, 0), 20, 2.0, 1.0, 2.0, 0.01);
                    if (random.nextDouble() < 0.18) p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 30, 0));
                }
                case "celestial" -> {
                    world.spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 12, 1.4, 1.4, 1.4, 0.015);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 80, 0));
                }
                case "bloodmoon" -> {
                    world.spawnParticle(Particle.CRIMSON_SPORE, p.getLocation().add(0, 1, 0), 18, 1.8, 1.0, 1.8, 0.02);
                    if (random.nextDouble() < 0.22) p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 45, 0));
                }
                case "100" -> {
                    world.spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 24, 2.0, 1.5, 2.0, 0.08);
                    if (random.nextDouble() < 0.16) p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0));
                }
            }
        }
    }

    private void eventTick() {
        long now = System.currentTimeMillis();
        for (String realm : realms()) {
            World world = Bukkit.getWorld(worldName(realm));
            if (world == null || world.getPlayers().isEmpty()) continue;
            long next = nextInvasion.getOrDefault(realm, now + 6 * 60_000L);
            nextInvasion.putIfAbsent(realm, next);
            if (now < next) continue;

            Player target = world.getPlayers().get(ThreadLocalRandom.current().nextInt(world.getPlayers().size()));
            startInvasion(realm, world, target);
            nextInvasion.put(realm, now + (10 + ThreadLocalRandom.current().nextInt(7)) * 60_000L);
        }
    }

    private void startInvasion(String realm, World world, Player target) {
        String title = invasionName(realm);
        for (Player p : world.getPlayers()) {
            p.sendTitle(ChatColor.DARK_RED + title, ChatColor.GOLD + "Defend " + display(realm) + "!", 10, 70, 20);
            p.sendMessage(ChatColor.DARK_RED + "[REALM EVENT] " + ChatColor.GOLD + title +
                    ChatColor.RED + " has begun near " + target.getName() + "!");
        }

        int wave = Math.min(18, 7 + world.getPlayers().size() * 2);
        for (int i = 0; i < wave; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Location at = findSpawn(target, 12, 30);
                if (at != null) spawnMob(realm, chooseProfile(realm), at, ThreadLocalRandom.current().nextDouble() < 0.18);
            }, i * 8L);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> spawnMiniBoss(realm, target), wave * 8L + 30L);
    }

    private void spawnMiniBoss(String realm, Player anchor) {
        Location at = findSpawn(anchor, 16, 30);
        if (at == null) at = anchor.getLocation().clone().add(5, 0, 5);
        MobProfile base = miniBossProfile(realm);
        LivingEntity mob = spawnMob(realm, base, at, true);
        if (mob == null) return;
        mob.addScoreboardTag("esnRealmMiniBoss");
        mob.setCustomName(ChatColor.DARK_RED + "✦ " + base.name + " ✦");
        setAttribute(mob, Attribute.MAX_HEALTH, base.health * 2.5);
        mob.setHealth(Math.min(base.health * 2.5, maxHealth(mob)));
        setAttribute(mob, Attribute.ATTACK_DAMAGE, base.damage * 1.65);
        mob.setGlowing(true);
        for (Player p : anchor.getWorld().getPlayers()) {
            p.sendMessage(ChatColor.DARK_RED + "[MINIBOSS] " + base.color + base.name +
                    ChatColor.RED + " has entered " + display(realm) + "!");
        }
    }

    private LivingEntity spawnMob(String realm, MobProfile profile, Location location, boolean elite) {
        try {
            Entity raw = location.getWorld().spawnEntity(location, profile.type);
            if (!(raw instanceof LivingEntity mob)) {
                raw.remove();
                return null;
            }
            mob.addScoreboardTag("esnRealmMob");
            mob.addScoreboardTag("esnRealm_" + realm);
            mob.getPersistentDataContainer().set(mobIdKey, PersistentDataType.STRING, profile.id);
            mob.getPersistentDataContainer().set(realmKey, PersistentDataType.STRING, realm);
            mob.setCustomName(profile.color + (elite ? "★ " : "") + profile.name);
            mob.setCustomNameVisible(elite);
            mob.setGlowing(elite);
            if (mob instanceof Mob m) {
                m.setRemoveWhenFarAway(true);
                Player nearest = location.getWorld().getPlayers().stream()
                        .min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(location))).orElse(null);
                if (nearest != null) m.setTarget(nearest);
            }

            double health = profile.health * (elite ? 1.75 : 1.0);
            double damage = profile.damage * (elite ? 1.35 : 1.0);
            setAttribute(mob, Attribute.MAX_HEALTH, health);
            mob.setHealth(Math.min(health, maxHealth(mob)));
            setAttribute(mob, Attribute.ATTACK_DAMAGE, damage);
            if (mob.getAttribute(Attribute.MOVEMENT_SPEED) != null) {
                double speed = Math.min(0.48, mob.getAttribute(Attribute.MOVEMENT_SPEED).getBaseValue() * (elite ? 1.12 : 1.0));
                setAttribute(mob, Attribute.MOVEMENT_SPEED, speed);
            }
            return mob;
        } catch (Exception ex) {
            plugin.getLogger().warning("[ESN Realms] Could not spawn " + profile.id + ": " + ex.getMessage());
            return null;
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void hit(EntityDamageByEntityEvent event) {
        LivingEntity attacker = resolveAttacker(event.getDamager());
        if (attacker == null || !attacker.getScoreboardTags().contains("esnRealmMob")) return;
        if (!(event.getEntity() instanceof Player player)) return;

        String realm = attacker.getPersistentDataContainer().get(realmKey, PersistentDataType.STRING);
        String id = attacker.getPersistentDataContainer().get(mobIdKey, PersistentDataType.STRING);
        if (realm == null || id == null) return;

        ThreadLocalRandom random = ThreadLocalRandom.current();
        switch (realm) {
            case "storm" -> {
                if (random.nextDouble() < 0.24) {
                    player.getWorld().strikeLightningEffect(player.getLocation());
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0));
                }
            }
            case "abyss" -> {
                if (random.nextDouble() < 0.25) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0));
                }
            }
            case "frost" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 55, 1));
                if (random.nextDouble() < 0.18) player.setFreezeTicks(Math.min(player.getMaxFreezeTicks(), player.getFreezeTicks() + 70));
            }
            case "infernal" -> {
                player.setFireTicks(Math.max(player.getFireTicks(), 80));
                if (random.nextDouble() < 0.18) knockAway(attacker, player, 0.75);
            }
            case "verdant" -> {
                if (random.nextDouble() < 0.28) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 35, 1));
                }
            }
            case "celestial" -> {
                if (random.nextDouble() < 0.22) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 22, 0));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 80, 0));
                }
            }
            case "bloodmoon" -> {
                if (random.nextDouble() < 0.30) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 50, 0));
                    attacker.setHealth(Math.min(maxHealth(attacker), attacker.getHealth() + Math.max(1.0, event.getFinalDamage() * 0.35)));
                }
            }
            case "100" -> {
                int roll = random.nextInt(4);
                if (roll == 0) player.setFireTicks(70);
                else if (roll == 1) player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 50, 0));
                else if (roll == 2) player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, 1));
                else player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 18, 0));
            }
        }

        if (attacker.getScoreboardTags().contains("esnRealmMiniBoss") && random.nextDouble() < 0.20) {
            knockAway(attacker, player, 1.1);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_BREAK_BLOCK, 0.65f, 1.2f);
        }
    }

    @EventHandler
    public void death(EntityDeathEvent event) {
        LivingEntity mob = event.getEntity();
        if (!mob.getScoreboardTags().contains("esnRealmMob")) return;
        String realm = mob.getPersistentDataContainer().get(realmKey, PersistentDataType.STRING);
        String id = mob.getPersistentDataContainer().get(mobIdKey, PersistentDataType.STRING);
        if (realm == null) return;

        event.getDrops().add(essence(realm, mob.getScoreboardTags().contains("esnRealmMiniBoss") ? 6 :
                mob.getCustomName() != null && ChatColor.stripColor(mob.getCustomName()).startsWith("★") ? 3 : 1));

        Player killer = mob.getKiller();
        if (killer != null && ThreadLocalRandom.current().nextDouble() < 0.08) {
            event.getDrops().add(relicFragment(realm));
            killer.sendMessage(ChatColor.GOLD + "Realm drop: " + ChatColor.WHITE + "you found a " +
                    realmColor(realm) + display(realm) + " Relic Fragment!");
        }

        if (mob.getScoreboardTags().contains("esnRealmMiniBoss") && killer != null) {
            event.getDrops().add(relicFragment(realm));
            event.getDrops().add(relicFragment(realm));
            Bukkit.broadcastMessage(ChatColor.DARK_RED + "[ESN Realms] " + ChatColor.GOLD + killer.getName() +
                    ChatColor.YELLOW + " defeated the " + realmColor(realm) +
                    (ChatColor.stripColor(mob.getCustomName()) == null ? "realm miniboss" : ChatColor.stripColor(mob.getCustomName())) +
                    ChatColor.YELLOW + "!");
        }
    }

    @EventHandler
    public void changedWorld(PlayerChangedWorldEvent event) {
        String realm = realmFromWorld(event.getPlayer().getWorld());
        if (realm == null || realm.equals("nexus")) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline() && event.getPlayer().getWorld().equals(Bukkit.getWorld(worldName(realm)))) {
                event.getPlayer().sendActionBar(realmColor(realm) + display(realm) +
                        ChatColor.GRAY + " • " + profiles(realm).size() + " native creature types • /realmguide");
            }
        }, 20L);
    }

    private ItemStack essence(String realm, int amount) {
        Material material = switch (realm) {
            case "storm" -> Material.COPPER_INGOT;
            case "abyss" -> Material.ECHO_SHARD;
            case "frost" -> Material.PRISMARINE_CRYSTALS;
            case "infernal" -> Material.BLAZE_POWDER;
            case "verdant" -> Material.SLIME_BALL;
            case "celestial" -> Material.AMETHYST_SHARD;
            case "bloodmoon" -> Material.REDSTONE;
            case "100" -> Material.NETHER_STAR;
            default -> Material.PRISMARINE_SHARD;
        };
        ItemStack item = new ItemStack(material, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(realmColor(realm) + display(realm) + " Essence");
        meta.setLore(List.of(ChatColor.GRAY + "Condensed energy dropped by ESN realm creatures.",
                ChatColor.DARK_GRAY + "Keep these — realm crafting is coming."));
        meta.getPersistentDataContainer().set(essenceKey, PersistentDataType.STRING, realm);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack relicFragment(String realm) {
        ItemStack item = new ItemStack(Material.PRISMARINE_SHARD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(realmColor(realm) + display(realm) + " Relic Fragment");
        meta.setLore(List.of(ChatColor.LIGHT_PURPLE + "Rare Realm Material",
                ChatColor.GRAY + "Dropped by elite creatures and minibosses."));
        meta.getPersistentDataContainer().set(essenceKey, PersistentDataType.STRING, "fragment:" + realm);
        item.setItemMeta(meta);
        return item;
    }

    private Location findSpawn(Player anchor, int minDistance, int maxDistance) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = r.nextDouble() * Math.PI * 2.0;
            double distance = r.nextDouble(minDistance, maxDistance + 1.0);
            int x = anchor.getLocation().getBlockX() + (int)Math.round(Math.cos(angle) * distance);
            int z = anchor.getLocation().getBlockZ() + (int)Math.round(Math.sin(angle) * distance);
            int baseY = anchor.getLocation().getBlockY();

            for (int delta = 0; delta <= 18; delta++) {
                for (int sign : new int[]{1, -1}) {
                    int y = baseY + delta * sign;
                    if (y <= anchor.getWorld().getMinHeight() + 2 || y >= anchor.getWorld().getMaxHeight() - 3) continue;
                    Material feet = anchor.getWorld().getBlockAt(x, y, z).getType();
                    Material head = anchor.getWorld().getBlockAt(x, y + 1, z).getType();
                    Material floor = anchor.getWorld().getBlockAt(x, y - 1, z).getType();
                    if (feet.isAir() && head.isAir() && floor.isSolid()) return new Location(anchor.getWorld(), x + 0.5, y, z + 0.5);
                }
            }
        }
        return null;
    }

    private int countRealmMobs(World world) {
        int count = 0;
        for (Entity e : world.getEntities()) if (e.getScoreboardTags().contains("esnRealmMob")) count++;
        return count;
    }

    private int clearRealmMobs(World world) {
        int removed = 0;
        for (Entity e : new ArrayList<>(world.getEntities())) {
            if (e.getScoreboardTags().contains("esnRealmMob")) {
                e.remove();
                removed++;
            }
        }
        return removed;
    }

    private LivingEntity resolveAttacker(Entity damager) {
        if (damager instanceof LivingEntity living) return living;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity living) return living;
        return null;
    }

    private void knockAway(LivingEntity attacker, Player player, double strength) {
        Vector vector = player.getLocation().toVector().subtract(attacker.getLocation().toVector());
        if (vector.lengthSquared() < 0.001) vector = new Vector(1, 0, 0);
        vector.normalize().multiply(strength).setY(0.45);
        player.setVelocity(vector);
    }

    private void setAttribute(LivingEntity entity, Attribute attribute, double value) {
        if (entity.getAttribute(attribute) != null) entity.getAttribute(attribute).setBaseValue(value);
    }

    private double maxHealth(LivingEntity entity) {
        return entity.getAttribute(Attribute.MAX_HEALTH) == null ? 20.0 : entity.getAttribute(Attribute.MAX_HEALTH).getValue();
    }

    private MobProfile chooseProfile(String realm) {
        List<MobProfile> list = profiles(realm);
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    private MobProfile miniBossProfile(String realm) {
        return switch (realm) {
            case "storm" -> new MobProfile("storm_colossus", "Storm Colossus", EntityType.RAVAGER, 240, 18, ChatColor.AQUA, "A living thunder engine.");
            case "abyss" -> new MobProfile("abyss_harbinger", "Abyss Harbinger", EntityType.ENDERMAN, 230, 17, ChatColor.DARK_PURPLE, "A void-born executioner.");
            case "frost" -> new MobProfile("frost_jarl", "Frost Jarl", EntityType.STRAY, 220, 16, ChatColor.WHITE, "An ancient frozen warlord.");
            case "infernal" -> new MobProfile("infernal_executioner", "Infernal Executioner", EntityType.WITHER_SKELETON, 240, 19, ChatColor.RED, "A blackstone champion.");
            case "verdant" -> new MobProfile("wildheart_behemoth", "Wildheart Behemoth", EntityType.RAVAGER, 250, 18, ChatColor.GREEN, "The jungle itself given rage.");
            case "celestial" -> new MobProfile("astral_regent", "Astral Regent", EntityType.ENDERMAN, 235, 18, ChatColor.AQUA, "A fallen ruler of the stars.");
            case "bloodmoon" -> new MobProfile("moon_tyrant", "Moon Tyrant", EntityType.WITHER_SKELETON, 260, 20, ChatColor.DARK_RED, "A cursed monarch empowered by the red moon.");
            case "100" -> new MobProfile("rift_sovereign", "Rift Sovereign", EntityType.RAVAGER, 320, 22, ChatColor.LIGHT_PURPLE, "A creature forged from every realm.");
            default -> profiles(realm).get(0);
        };
    }

    private List<MobProfile> profiles(String realm) {
        return switch (realm) {
            case "storm" -> List.of(
                    new MobProfile("stormling", "Stormling", EntityType.DROWNED, 38, 7, ChatColor.AQUA, "Lightning-charged raider."),
                    new MobProfile("thunder_wraith", "Thunder Wraith", EntityType.PHANTOM, 34, 8, ChatColor.BLUE, "Fast aerial storm predator."),
                    new MobProfile("storm_knight", "Storm Knight", EntityType.SKELETON, 52, 9, ChatColor.DARK_AQUA, "Armored guardian of the sky ruins."));
            case "abyss" -> List.of(
                    new MobProfile("abyss_stalker", "Abyss Stalker", EntityType.ENDERMAN, 58, 10, ChatColor.DARK_PURPLE, "Teleports through corrupted caverns."),
                    new MobProfile("sculk_hunter", "Sculk Hunter", EntityType.HUSK, 48, 9, ChatColor.DARK_GRAY, "Tracks movement through the darkness."),
                    new MobProfile("void_wisp", "Void Wisp", EntityType.VEX, 30, 8, ChatColor.LIGHT_PURPLE, "Small, vicious and difficult to pin down."));
            case "frost" -> List.of(
                    new MobProfile("frost_revenant", "Frost Revenant", EntityType.STRAY, 46, 8, ChatColor.WHITE, "Freezes targets with every strike."),
                    new MobProfile("ice_brute", "Ice Brute", EntityType.POLAR_BEAR, 70, 11, ChatColor.AQUA, "Heavy guardian of frozen passes."),
                    new MobProfile("snow_wraith", "Snow Wraith", EntityType.PHANTOM, 34, 8, ChatColor.GRAY, "Hunts explorers through blizzards."));
            case "infernal" -> List.of(
                    new MobProfile("ember_knight", "Ember Knight", EntityType.WITHER_SKELETON, 54, 11, ChatColor.RED, "Burning melee champion."),
                    new MobProfile("cinder_wraith", "Cinder Wraith", EntityType.BLAZE, 44, 10, ChatColor.GOLD, "Rains fire from above."),
                    new MobProfile("magma_brute", "Magma Brute", EntityType.MAGMA_CUBE, 64, 12, ChatColor.DARK_RED, "A living mass of molten stone."));
            case "verdant" -> List.of(
                    new MobProfile("thorn_stalker", "Thorn Stalker", EntityType.CAVE_SPIDER, 42, 8, ChatColor.GREEN, "Venomous predator hidden in the growth."),
                    new MobProfile("moss_guardian", "Moss Guardian", EntityType.ZOMBIE, 62, 10, ChatColor.DARK_GREEN, "Ancient protector reclaimed by the jungle."),
                    new MobProfile("wildfang", "Wildfang", EntityType.WOLF, 38, 9, ChatColor.YELLOW, "Fast pack hunter of the overgrown ruins."));
            case "celestial" -> List.of(
                    new MobProfile("starborn_sentinel", "Starborn Sentinel", EntityType.ENDERMAN, 56, 10, ChatColor.AQUA, "Astral defender of the floating temples."),
                    new MobProfile("astral_wisp", "Astral Wisp", EntityType.VEX, 32, 9, ChatColor.LIGHT_PURPLE, "A shard of unstable starlight."),
                    new MobProfile("fallen_oracle", "Fallen Oracle", EntityType.SKELETON, 50, 10, ChatColor.WHITE, "A celestial seer twisted by the void."));
            case "bloodmoon" -> List.of(
                    new MobProfile("blood_revenant", "Blood Revenant", EntityType.HUSK, 58, 11, ChatColor.DARK_RED, "Feeds on wounded travelers."),
                    new MobProfile("moonfang", "Moonfang", EntityType.WOLF, 46, 11, ChatColor.RED, "Cursed predator empowered by moonlight."),
                    new MobProfile("crimson_reaper", "Crimson Reaper", EntityType.WITHER_SKELETON, 64, 13, ChatColor.DARK_RED, "Elite executioner of the Bloodmoon."));
            case "100" -> List.of(
                    new MobProfile("rift_guardian", "Rift Guardian", EntityType.ENDERMAN, 72, 13, ChatColor.LIGHT_PURPLE, "Uses powers stolen from every realm."),
                    new MobProfile("fractured_knight", "Fractured Knight", EntityType.WITHER_SKELETON, 76, 14, ChatColor.GOLD, "An endgame warrior fractured by realm energy."),
                    new MobProfile("chaos_wraith", "Chaos Wraith", EntityType.VEX, 44, 12, ChatColor.RED, "Unstable and unpredictable."));
            default -> List.of(new MobProfile("realm_wanderer", "Realm Wanderer", EntityType.ZOMBIE, 30, 6, ChatColor.GRAY, "A lost creature."));
        };
    }

    private String invasionName(String realm) {
        return switch (realm) {
            case "storm" -> "TEMPEST SURGE";
            case "abyss" -> "ABYSSAL BREACH";
            case "frost" -> "WHITEOUT HUNT";
            case "infernal" -> "INFERNAL MARCH";
            case "verdant" -> "WILD GROWTH";
            case "celestial" -> "STARFALL INCURSION";
            case "bloodmoon" -> "BLOODMOON HUNT";
            case "100" -> "REALM FRACTURE";
            default -> "REALM INVASION";
        };
    }

    private Set<String> realms() {
        return Set.of("storm", "abyss", "frost", "infernal", "verdant", "celestial", "bloodmoon", "100");
    }

    private String realmFromWorld(World world) {
        if (world == null) return null;
        return switch (world.getName()) {
            case "esn_nexus" -> "nexus";
            case "esn_storm" -> "storm";
            case "esn_abyss" -> "abyss";
            case "esn_frost" -> "frost";
            case "esn_infernal" -> "infernal";
            case "esn_verdant" -> "verdant";
            case "esn_celestial" -> "celestial";
            case "esn_bloodmoon" -> "bloodmoon";
            case "esn_realm100" -> "100";
            default -> null;
        };
    }

    private String normalize(String input) {
        if (input == null) return null;
        String v = input.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        return switch (v) {
            case "storm", "stormkingdom" -> "storm";
            case "abyss", "theabyss" -> "abyss";
            case "frost", "frostlands" -> "frost";
            case "infernal", "infernalempire" -> "infernal";
            case "verdant", "verdantwilds", "wilds" -> "verdant";
            case "celestial", "celestialisles", "astral" -> "celestial";
            case "bloodmoon", "bloodmoonwastes", "crimson" -> "bloodmoon";
            case "100", "realm100", "r100" -> "100";
            default -> null;
        };
    }

    private String worldName(String realm) {
        return realm.equals("100") ? "esn_realm100" : "esn_" + realm;
    }

    private String display(String realm) {
        return switch (realm) {
            case "storm" -> "Storm Kingdom";
            case "abyss" -> "The Abyss";
            case "frost" -> "Frostlands";
            case "infernal" -> "Infernal Empire";
            case "verdant" -> "Verdant Wilds";
            case "celestial" -> "Celestial Isles";
            case "bloodmoon" -> "Bloodmoon Wastes";
            case "100" -> "Realm 100";
            default -> realm;
        };
    }

    private ChatColor realmColor(String realm) {
        return switch (realm) {
            case "storm", "celestial" -> ChatColor.AQUA;
            case "abyss", "100" -> ChatColor.LIGHT_PURPLE;
            case "frost" -> ChatColor.WHITE;
            case "infernal" -> ChatColor.RED;
            case "verdant" -> ChatColor.GREEN;
            case "bloodmoon" -> ChatColor.DARK_RED;
            default -> ChatColor.GRAY;
        };
    }

    private record MobProfile(String id, String name, EntityType type, double health, double damage,
                              ChatColor color, String description) {}
}
