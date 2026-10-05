package com.esn.smp.gameplay;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
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
    private static final String GUIDE_MENU = ChatColor.DARK_PURPLE + "Realm Creature Guide";
    private static final String FORGE_MENU = ChatColor.GOLD + "Realm Forge";
    private static final String EVENT_MENU = ChatColor.DARK_RED + "Realm Event Control";
    private static final String EVENT_ACTION_MENU = ChatColor.RED + "Realm Event • ";
    private final JavaPlugin plugin;
    private final NamespacedKey mobIdKey;
    private final NamespacedKey realmKey;
    private final NamespacedKey essenceKey;
    private final NamespacedKey sigilKey;
    private final NamespacedKey difficultyKey;
    private final Map<UUID, Long> sigilCooldown = new HashMap<>();
    private final Map<String, Long> nextInvasion = new HashMap<>();
    private final Map<UUID, String> selectedEventRealm = new HashMap<>();

    public RealmMobSystem(JavaPlugin plugin) {
        this.plugin = plugin;
        this.mobIdKey = new NamespacedKey(plugin, "realm_mob_id");
        this.realmKey = new NamespacedKey(plugin, "realm_id");
        this.essenceKey = new NamespacedKey(plugin, "realm_essence");
        this.sigilKey = new NamespacedKey(plugin, "realm_sigil");
        this.difficultyKey = new NamespacedKey(plugin, "realm_difficulty");
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
            if (args.length > 0) {
                String realm = normalize(args[0]);
                if (realm == null) p.sendMessage(ChatColor.RED + "Unknown realm.");
                else openGuideDetail(p, realm);
            } else {
                openGuideMenu(p);
            }
            return true;
        }

        if (name.equals("realmforge")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Players only.");
                return true;
            }
            if (args.length < 1) {
                openForgeMenu(p);
                return true;
            }
            String realm = normalize(args[0]);
            if (realm == null) {
                p.sendMessage(ChatColor.RED + "Unknown realm.");
                return true;
            }
            forgeSigil(p, realm);
            return true;
        }

        if (name.equals("realmevent")) {
            if (!sender.hasPermission("esnsmp.admin")) {
                sender.sendMessage(ChatColor.RED + "ESN admin only.");
                return true;
            }
            if (args.length < 2) {
                if (sender instanceof Player p) openEventMenu(p);
                else sender.sendMessage(ChatColor.YELLOW + "/realmevent <invasion|miniboss|clear> <realm>");
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
                case "secret" -> {
                    Player target = world.getPlayers().stream().findAny().orElse(null);
                    if (target == null) sender.sendMessage(ChatColor.RED + "A player must be inside the realm.");
                    else spawnSecretBoss(realm, target);
                }
                case "clear" -> {
                    int removed = clearRealmMobs(world);
                    sender.sendMessage(ChatColor.GREEN + "Removed " + removed + " ESN realm mob(s).");
                }
                default -> sender.sendMessage(ChatColor.YELLOW + "/realmevent <invasion|miniboss|secret|clear> <realm>");
            }
            return true;
        }
        return true;
    }

    private void openGuideMenu(Player p) {
        Inventory v = Bukkit.createInventory(null, 54, GUIDE_MENU);
        int[] slots = {10,11,12,13,14,15,16,22};
        String[] realms = {"storm","abyss","frost","infernal","verdant","celestial","bloodmoon","100"};
        for (int i = 0; i < realms.length; i++) {
            String realm = realms[i];
            v.setItem(slots[i], menuItem(icon(realm), realmColor(realm) + display(realm),
                    ChatColor.GRAY + "Creatures: " + profiles(realm).size(),
                    ChatColor.GRAY + "Miniboss: " + miniBossProfile(realm).name,
                    ChatColor.YELLOW + "Click to view creatures"));
        }
        String current = realmFromWorld(p.getWorld());
        if (shatteredUnlocked(p)) {
            v.setItem(31, menuItem(Material.REINFORCED_DEEPSLATE, ChatColor.DARK_PURPLE + "Shattered Realm",
                    ChatColor.GRAY + "Creatures: " + profiles("shattered").size(),
                    ChatColor.GRAY + "Miniboss: " + miniBossProfile("shattered").name,
                    ChatColor.YELLOW + "Click to view creatures"));
        }
        v.setItem(40, menuItem(Material.COMPASS, ChatColor.GREEN + "Current Realm",
                ChatColor.GRAY + (current == null ? "Not inside an ESN Realm" : display(current))));
        v.setItem(49, menuItem(Material.ARROW, ChatColor.YELLOW + "Back to Realms"));
        p.openInventory(v);
    }

    private void openGuideDetail(Player p, String realm) {
        Inventory v = Bukkit.createInventory(null, 45, ChatColor.DARK_PURPLE + "Guide • " + display(realm));
        List<MobProfile> list = profiles(realm);
        int slot = 10;
        for (MobProfile profile : list) {
            v.setItem(slot++, menuItem(mobIcon(profile.type), profile.color + profile.name,
                    ChatColor.GRAY + profile.description,
                    ChatColor.RED + "Health: " + Math.round(profile.health),
                    ChatColor.GOLD + "Damage: " + String.format(Locale.US, "%.1f", profile.damage)));
        }
        MobProfile mini = miniBossProfile(realm);
        v.setItem(22, menuItem(Material.WITHER_SKELETON_SKULL, ChatColor.DARK_RED + "✦ " + mini.name + " ✦",
                ChatColor.GRAY + mini.description,
                ChatColor.RED + "Miniboss encounter",
                ChatColor.GOLD + "Drops bonus Essence + Relic Fragments"));
        v.setItem(30, menuItem(Material.PRISMARINE_CRYSTALS, realmColor(realm) + "Realm Essence",
                ChatColor.GRAY + "Dropped by native mobs",
                ChatColor.GRAY + "12 Essence + 2 Fragments = Sigil"));
        v.setItem(31, menuItem(Material.PRISMARINE_SHARD, realmColor(realm) + "Relic Fragment",
                ChatColor.GRAY + "Rare elite/miniboss drop",
                ChatColor.GRAY + "Used in the Realm Forge"));
        v.setItem(32, menuItem(icon(realm), realmColor(realm) + display(realm) + " Sigil",
                ChatColor.GRAY + sigilDescription(realm),
                ChatColor.YELLOW + "Use /realmforge or the Forge menu"));
        v.setItem(36, menuItem(Material.ARROW, ChatColor.YELLOW + "Back"));
        p.openInventory(v);
    }

    private void openForgeMenu(Player p) {
        Inventory v = Bukkit.createInventory(null, 54, FORGE_MENU);
        int[] slots = {10,11,12,13,14,15,16,22};
        String[] realms = {"storm","abyss","frost","infernal","verdant","celestial","bloodmoon","100"};
        for (int i = 0; i < realms.length; i++) {
            String realm = realms[i];
            int essence = countTagged(p, realm);
            int fragments = countTagged(p, "fragment:" + realm);
            boolean ready = essence >= 12 && fragments >= 2;
            v.setItem(slots[i], menuItem(icon(realm),
                    (ready ? ChatColor.GREEN : realmColor(realm)) + display(realm) + " Sigil",
                    ChatColor.GRAY + sigilDescription(realm),
                    ChatColor.GRAY + "Essence: " + (essence >= 12 ? ChatColor.GREEN : ChatColor.RED) + essence + "/12",
                    ChatColor.GRAY + "Fragments: " + (fragments >= 2 ? ChatColor.GREEN : ChatColor.RED) + fragments + "/2",
                    ready ? ChatColor.YELLOW + "Click to forge" : ChatColor.DARK_GRAY + "Collect more realm materials"));
        }
        if (shatteredUnlocked(p)) {
            int essence = countTagged(p, "shattered");
            int fragments = countTagged(p, "fragment:shattered");
            boolean ready = essence >= 12 && fragments >= 2;
            v.setItem(31, menuItem(Material.REINFORCED_DEEPSLATE,
                    (ready ? ChatColor.GREEN : ChatColor.DARK_PURPLE) + "Shattered Realm Sigil",
                    ChatColor.GRAY + sigilDescription("shattered"),
                    ChatColor.GRAY + "Essence: " + essence + "/12",
                    ChatColor.GRAY + "Fragments: " + fragments + "/2",
                    ready ? ChatColor.YELLOW + "Click to forge" : ChatColor.DARK_GRAY + "Collect more secret realm materials"));
        }
        v.setItem(40, menuItem(Material.NETHER_STAR, ChatColor.LIGHT_PURPLE + "How Realm Forge Works",
                ChatColor.GRAY + "Kill realm mobs for Essence",
                ChatColor.GRAY + "Elites/minibosses can drop Fragments",
                ChatColor.GRAY + "Forge permanent reusable Sigils"));
        v.setItem(49, menuItem(Material.ARROW, ChatColor.YELLOW + "Back to Realms"));
        p.openInventory(v);
    }

    private void openEventMenu(Player p) {
        if (!p.hasPermission("esnsmp.admin")) {
            p.sendMessage(ChatColor.RED + "ESN admin only.");
            return;
        }
        Inventory v = Bukkit.createInventory(null, 54, EVENT_MENU);
        int[] slots = {10,11,12,13,14,15,16,22};
        String[] realms = {"storm","abyss","frost","infernal","verdant","celestial","bloodmoon","100"};
        for (int i = 0; i < realms.length; i++) {
            String realm = realms[i];
            World world = Bukkit.getWorld(worldName(realm));
            v.setItem(slots[i], menuItem(icon(realm), realmColor(realm) + display(realm),
                    ChatColor.GRAY + "World: " + (world == null ? ChatColor.RED + "UNLOADED" : ChatColor.GREEN + "LOADED"),
                    ChatColor.GRAY + "Players: " + (world == null ? 0 : world.getPlayers().size()),
                    ChatColor.GRAY + "Custom mobs: " + (world == null ? 0 : countRealmMobs(world)),
                    ChatColor.YELLOW + "Click for event actions"));
        }
        v.setItem(31, menuItem(Material.REINFORCED_DEEPSLATE, ChatColor.DARK_PURPLE + "Shattered Realm",
                ChatColor.GRAY + "Secret realm event controls",
                ChatColor.YELLOW + "Click for event actions"));
        v.setItem(40, menuItem(Material.SHIELD, ChatColor.RED + "Admin Realm Events",
                ChatColor.GRAY + "Start invasions, summon minibosses",
                ChatColor.GRAY + "or clear custom realm mobs"));
        v.setItem(49, menuItem(Material.ARROW, ChatColor.YELLOW + "Back to Realms"));
        p.openInventory(v);
    }

    private void openEventActions(Player p, String realm) {
        selectedEventRealm.put(p.getUniqueId(), realm);
        Inventory v = Bukkit.createInventory(null, 27, EVENT_ACTION_MENU + display(realm));
        World world = Bukkit.getWorld(worldName(realm));
        v.setItem(4, menuItem(icon(realm), realmColor(realm) + display(realm),
                ChatColor.GRAY + "Players: " + (world == null ? 0 : world.getPlayers().size()),
                ChatColor.GRAY + "Realm mobs: " + (world == null ? 0 : countRealmMobs(world))));
        v.setItem(10, menuItem(Material.BELL, ChatColor.RED + "Start Invasion",
                ChatColor.GRAY + invasionName(realm),
                ChatColor.YELLOW + "Requires a player inside the realm"));
        v.setItem(12, menuItem(Material.WITHER_SKELETON_SKULL, ChatColor.DARK_RED + "Spawn Miniboss",
                ChatColor.GRAY + miniBossProfile(realm).name,
                ChatColor.YELLOW + "Requires a player inside the realm"));
        v.setItem(13, menuItem(Material.DRAGON_HEAD, ChatColor.DARK_PURPLE + "Awaken Secret Boss",
                ChatColor.GRAY + secretBossProfile(realm).name,
                ChatColor.GRAY + "Normally unlocked through completed realm lore",
                ChatColor.YELLOW + "Admin summon"));
        v.setItem(14, menuItem(Material.BARRIER, ChatColor.YELLOW + "Clear Realm Mobs",
                ChatColor.GRAY + "Remove ESN custom mobs from this realm"));
        v.setItem(16, menuItem(Material.COMPARATOR, ChatColor.GREEN + "Refresh Status"));
        v.setItem(22, menuItem(Material.ARROW, ChatColor.YELLOW + "Back"));
        p.openInventory(v);
    }

    @EventHandler
    public void menuClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        boolean relevant = title.equals(GUIDE_MENU) || title.equals(FORGE_MENU) || title.equals(EVENT_MENU)
                || title.startsWith(ChatColor.DARK_PURPLE + "Guide • ") || title.startsWith(EVENT_ACTION_MENU);
        if (!relevant) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p)) return;
        int slot = event.getRawSlot();

        if (title.equals(GUIDE_MENU)) {
            String realm = realmBySlot(slot);
            if (realm != null) openGuideDetail(p, realm);
            else if (slot == 49) p.performCommand("realms");
            return;
        }
        if (title.startsWith(ChatColor.DARK_PURPLE + "Guide • ")) {
            if (slot == 36) openGuideMenu(p);
            return;
        }
        if (title.equals(FORGE_MENU)) {
            String realm = realmBySlot(slot);
            if (realm != null) {
                forgeSigil(p, realm);
                Bukkit.getScheduler().runTaskLater(plugin, () -> openForgeMenu(p), 1L);
            } else if (slot == 49) p.performCommand("realms");
            return;
        }
        if (title.equals(EVENT_MENU)) {
            if (!p.hasPermission("esnsmp.admin")) { p.closeInventory(); return; }
            String realm = realmBySlot(slot);
            if (realm != null) openEventActions(p, realm);
            else if (slot == 49) p.performCommand("realms");
            return;
        }
        if (title.startsWith(EVENT_ACTION_MENU)) {
            if (!p.hasPermission("esnsmp.admin")) { p.closeInventory(); return; }
            String realm = selectedEventRealm.get(p.getUniqueId());
            if (realm == null) { openEventMenu(p); return; }
            World world = Bukkit.getWorld(worldName(realm));
            if (slot == 22) { openEventMenu(p); return; }
            if (slot == 16) { openEventActions(p, realm); return; }
            if (world == null) {
                p.sendMessage(ChatColor.RED + "That realm is currently unloaded. Teleport there first.");
                openEventActions(p, realm);
                return;
            }
            if (slot == 10) {
                Player target = world.getPlayers().stream().findAny().orElse(null);
                if (target == null) p.sendMessage(ChatColor.RED + "A player must be inside the realm.");
                else startInvasion(realm, world, target);
            } else if (slot == 12) {
                Player target = world.getPlayers().stream().findAny().orElse(null);
                if (target == null) p.sendMessage(ChatColor.RED + "A player must be inside the realm.");
                else spawnMiniBoss(realm, target);
            } else if (slot == 13) {
                Player target = world.getPlayers().stream().findAny().orElse(null);
                if (target == null) p.sendMessage(ChatColor.RED + "A player must be inside the realm.");
                else spawnSecretBoss(realm, target);
            } else if (slot == 14) {
                p.sendMessage(ChatColor.GREEN + "Removed " + clearRealmMobs(world) + " ESN realm mob(s).");
            }
            openEventActions(p, realm);
        }
    }

    private String realmBySlot(int slot) {
        return switch (slot) {
            case 10 -> "storm";
            case 11 -> "abyss";
            case 12 -> "frost";
            case 13 -> "infernal";
            case 14 -> "verdant";
            case 15 -> "celestial";
            case 16 -> "bloodmoon";
            case 22 -> "100";
            case 31 -> "shattered";
            default -> null;
        };
    }

    private ItemStack menuItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    private Material icon(String realm) {
        return switch (realm) {
            case "storm" -> Material.LIGHTNING_ROD;
            case "abyss" -> Material.ECHO_SHARD;
            case "frost" -> Material.BLUE_ICE;
            case "infernal" -> Material.MAGMA_BLOCK;
            case "verdant" -> Material.MOSS_BLOCK;
            case "celestial" -> Material.AMETHYST_SHARD;
            case "bloodmoon" -> Material.REDSTONE_BLOCK;
            case "100" -> Material.NETHER_STAR;
            case "shattered" -> Material.REINFORCED_DEEPSLATE;
            default -> Material.COMPASS;
        };
    }

    private Material mobIcon(EntityType type) {
        return switch (type) {
            case ENDERMAN -> Material.ENDER_EYE;
            case PHANTOM -> Material.PHANTOM_MEMBRANE;
            case SKELETON, STRAY -> Material.BONE;
            case DROWNED -> Material.TRIDENT;
            case HUSK, ZOMBIE -> Material.ROTTEN_FLESH;
            case VEX -> Material.IRON_SWORD;
            case POLAR_BEAR -> Material.SNOWBALL;
            case WITHER_SKELETON -> Material.WITHER_SKELETON_SKULL;
            case BLAZE -> Material.BLAZE_ROD;
            case MAGMA_CUBE -> Material.MAGMA_CREAM;
            case CAVE_SPIDER -> Material.SPIDER_EYE;
            case WOLF -> Material.BONE;
            case RAVAGER -> Material.SADDLE;
            default -> Material.SPAWNER;
        };
    }

    private int countTagged(Player p, String value) {
        int found = 0;
        for (ItemStack item : p.getInventory().getContents()) {
            if (item == null || !item.hasItemMeta()) continue;
            String tag = item.getItemMeta().getPersistentDataContainer().get(essenceKey, PersistentDataType.STRING);
            if (value.equals(tag)) found += item.getAmount();
        }
        return found;
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

    private void spawnSecretBoss(String realm,Player anchor){
        Location at=findSpawn(anchor,18,32);
        if(at==null)at=anchor.getLocation().clone().add(7,0,7);
        MobProfile base=secretBossProfile(realm);
        LivingEntity boss=spawnMob(realm,base,at,true);
        if(boss==null)return;
        boss.addScoreboardTag("esnRealmMiniBoss");
        boss.addScoreboardTag("esnRealmSecretBoss");
        boss.setCustomName(ChatColor.DARK_PURPLE+"☠ "+base.name+" ☠");
        boss.setCustomNameVisible(true);
        boss.setGlowing(true);
        double health=base.health*4.0*Math.max(1.0,0.75+anchor.getWorld().getPlayers().size()*0.30);
        setAttribute(boss,Attribute.MAX_HEALTH,health);
        boss.setHealth(Math.min(health,maxHealth(boss)));
        setAttribute(boss,Attribute.ATTACK_DAMAGE,base.damage*2.0);
        for(Player p:anchor.getWorld().getPlayers()){
            p.sendTitle(ChatColor.DARK_PURPLE+"SECRET BOSS",base.color+base.name,10,80,20);
            p.sendMessage(ChatColor.DARK_PURPLE+"[SECRET BOSS] "+base.color+base.name+
                    ChatColor.LIGHT_PURPLE+" has awakened from the completed Chronicle!");
        }
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

            Player nearestDifficulty = location.getWorld().getPlayers().stream()
                    .min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(location))).orElse(null);
            double difficultyScale=1.0;
            if(nearestDifficulty!=null){
                String diff=nearestDifficulty.getPersistentDataContainer().get(difficultyKey,PersistentDataType.STRING);
                if("HEROIC".equals(diff))difficultyScale=1.5;
                else if("MYTHIC".equals(diff))difficultyScale=2.0;
            }
            double health = profile.health * (elite ? 1.75 : 1.0) * difficultyScale;
            double damage = profile.damage * (elite ? 1.35 : 1.0) * Math.sqrt(difficultyScale);
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
        if(event.getEntity() instanceof LivingEntity boss && boss.getScoreboardTags().contains("esnRealmMiniBoss")){
            checkBossPhase(boss,event.getFinalDamage());
        }
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
            case "shattered" -> {
                int roll=random.nextInt(6);
                if(roll==0) player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS,70,0));
                else if(roll==1) player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER,50,0));
                else if(roll==2) player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION,22,0));
                else if(roll==3) player.setFireTicks(80);
                else if(roll==4) player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,60,2));
                else knockAway(attacker,player,1.0);
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

    private void checkBossPhase(LivingEntity boss,double incoming){
        double max=maxHealth(boss);
        double remaining=Math.max(0,boss.getHealth()-incoming);
        double pct=max<=0?0:remaining/max;
        String realm=boss.getPersistentDataContainer().get(realmKey,PersistentDataType.STRING);
        if(realm==null)return;

        if(pct<=0.75&&!boss.getScoreboardTags().contains("esnPhase75")){
            boss.addScoreboardTag("esnPhase75");
            boss.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,20*120,0,false,false,false));
            announcePhase(boss,realm,"PHASE II","The boss accelerates and the arena destabilizes!");
            boss.getWorld().spawnParticle(Particle.PORTAL,boss.getLocation().add(0,1,0),50,2,1.5,2,0.08);
        }
        if(pct<=0.50&&!boss.getScoreboardTags().contains("esnPhase50")){
            boss.addScoreboardTag("esnPhase50");
            boss.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,20*120,0,false,false,false));
            announcePhase(boss,realm,"PHASE III","Reinforcements pour into the battle!");
            for(int i=0;i<4;i++){
                Location at=boss.getLocation().clone().add(ThreadLocalRandom.current().nextInt(-6,7),0,ThreadLocalRandom.current().nextInt(-6,7));
                spawnMob(realm,chooseProfile(realm),at,true);
            }
        }
        if(pct<=0.25&&!boss.getScoreboardTags().contains("esnPhase25")){
            boss.addScoreboardTag("esnPhase25");
            boss.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,20*120,1,false,false,false));
            boss.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,20*120,1,false,false,false));
            boss.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,20*120,0,false,false,false));
            announcePhase(boss,realm,"FINAL PHASE","ENRAGED — survive the realm's final assault!");
            boss.getWorld().strikeLightningEffect(boss.getLocation());
            boss.getWorld().playSound(boss.getLocation(),Sound.ENTITY_WITHER_SPAWN,1f,1.25f);
        }
    }

    private void announcePhase(LivingEntity boss,String realm,String phase,String subtitle){
        for(Player p:boss.getWorld().getPlayers()){
            p.sendTitle(ChatColor.DARK_RED+phase,colorForRealm(realm)+subtitle,5,45,10);
        }
    }

    private ChatColor colorForRealm(String realm){
        return realmColor(realm);
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


    private void forgeSigil(Player p, String realm) {
        if (!hasTagged(p, realm, 12) || !hasTagged(p, "fragment:" + realm, 2)) {
            p.sendMessage(ChatColor.RED + "You need 12 " + display(realm) + " Essence and 2 Relic Fragments.");
            return;
        }
        consumeTagged(p, realm, 12);
        consumeTagged(p, "fragment:" + realm, 2);
        ItemStack sigil = sigil(realm);
        Map<Integer, ItemStack> overflow = p.getInventory().addItem(sigil);
        overflow.values().forEach(item -> p.getWorld().dropItemNaturally(p.getLocation(), item));
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.35f);
        p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 32, 1.0, 1.0, 1.0, 0.08);
        p.sendMessage(ChatColor.GOLD + "FORGED: " + realmColor(realm) + display(realm) + " Sigil");
    }

    private boolean hasTagged(Player p, String value, int amount) {
        int found = 0;
        for (ItemStack item : p.getInventory().getContents()) {
            if (item == null || !item.hasItemMeta()) continue;
            String tag = item.getItemMeta().getPersistentDataContainer().get(essenceKey, PersistentDataType.STRING);
            if (value.equals(tag)) found += item.getAmount();
        }
        return found >= amount;
    }

    private void consumeTagged(Player p, String value, int amount) {
        int remaining = amount;
        ItemStack[] contents = p.getInventory().getContents();
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack item = contents[slot];
            if (item == null || !item.hasItemMeta()) continue;
            String tag = item.getItemMeta().getPersistentDataContainer().get(essenceKey, PersistentDataType.STRING);
            if (!value.equals(tag)) continue;
            int take = Math.min(remaining, item.getAmount());
            remaining -= take;
            int left = item.getAmount() - take;
            if (left <= 0) p.getInventory().setItem(slot, null);
            else item.setAmount(left);
        }
    }

    private ItemStack sigil(String realm) {
        Material material = switch (realm) {
            case "storm" -> Material.HEART_OF_THE_SEA;
            case "abyss" -> Material.ECHO_SHARD;
            case "frost" -> Material.BLUE_ICE;
            case "infernal" -> Material.FIRE_CHARGE;
            case "verdant" -> Material.SPORE_BLOSSOM;
            case "celestial" -> Material.NETHER_STAR;
            case "bloodmoon" -> Material.REDSTONE_BLOCK;
            case "100" -> Material.DRAGON_EGG;
            case "shattered" -> Material.REINFORCED_DEEPSLATE;
            default -> Material.AMETHYST_SHARD;
        };
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(realmColor(realm) + "✦ " + display(realm) + " Sigil ✦");
        meta.setLore(List.of(
                ChatColor.LIGHT_PURPLE + "Permanent Realm Artifact",
                ChatColor.GRAY + sigilDescription(realm),
                ChatColor.YELLOW + "Right click to activate",
                ChatColor.DARK_GRAY + "Cooldown: " + (realm.equals("100") ? "45s" : "60s")));
        meta.getPersistentDataContainer().set(sigilKey, PersistentDataType.STRING, realm);
        item.setItemMeta(meta);
        return item;
    }

    private String sigilDescription(String realm) {
        return switch (realm) {
            case "storm" -> "Become charged with speed and resistance.";
            case "abyss" -> "Disappear into darkness and gain night sight.";
            case "frost" -> "Gain resistance and extinguish flames.";
            case "infernal" -> "Gain fire resistance and combat strength.";
            case "verdant" -> "Regenerate and absorb incoming damage.";
            case "celestial" -> "Gain speed, slow falling and jump power.";
            case "bloodmoon" -> "Gain strength and regeneration.";
            case "100" -> "Channel a fragment of every Realm at once.";
            case "shattered" -> "Bend fractured realm energy around yourself.";
            default -> "Channel realm energy.";
        };
    }

    @EventHandler(ignoreCancelled = true)
    public void useSigil(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) return;
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;
        String realm = item.getItemMeta().getPersistentDataContainer().get(sigilKey, PersistentDataType.STRING);
        if (realm == null) return;
        event.setCancelled(true);

        Player p = event.getPlayer();
        long now = System.currentTimeMillis();
        long ready = sigilCooldown.getOrDefault(p.getUniqueId(), 0L);
        if (ready > now) {
            p.sendActionBar(ChatColor.RED + "Sigil cooldown: " + Math.max(1, (ready - now) / 1000) + "s");
            return;
        }
        sigilCooldown.put(p.getUniqueId(), now + (realm.equals("100") ? 45_000L : 60_000L));
        activateSigil(p, realm);
    }

    private void activateSigil(Player p, String realm) {
        int normal = 20 * 12;
        switch (realm) {
            case "storm" -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, normal, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, normal, 0));
                p.getWorld().strikeLightningEffect(p.getLocation());
            }
            case "abyss" -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, normal, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, normal, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, normal, 0));
            }
            case "frost" -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, normal, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, normal, 0));
                p.setFireTicks(0);
                p.setFreezeTicks(0);
            }
            case "infernal" -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, normal, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, normal, 0));
            }
            case "verdant" -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 8, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, normal, 1));
            }
            case "celestial" -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, normal, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, normal, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, normal, 1));
            }
            case "bloodmoon" -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, normal, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 8, 0));
            }
            case "shattered" -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, normal, 2));
                p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, normal, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, normal, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 20*6, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, normal, 0));
            }
            case "100" -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, normal, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, normal, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, normal, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, normal, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, normal, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, normal, 1));
            }
        }
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 28, 0.8, 1.0, 0.8, 0.08);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.9f, 1.25f);
        p.sendActionBar(realmColor(realm) + display(realm) + " Sigil activated!");
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
            case "shattered" -> Material.CRYING_OBSIDIAN;
            default -> Material.PRISMARINE_SHARD;
        };
        ItemStack item = new ItemStack(material, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(realmColor(realm) + display(realm) + " Essence");
        meta.setLore(List.of(ChatColor.GRAY + "Condensed energy dropped by ESN realm creatures.",
                ChatColor.DARK_GRAY + "Use /realmforge to create a permanent Realm Sigil."));
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

    private MobProfile secretBossProfile(String realm){
        return switch(realm){
            case "storm"->new MobProfile("tempest_god","Tempest God",EntityType.WARDEN,330,22,ChatColor.AQUA,"The storm given a physical form.");
            case "abyss"->new MobProfile("nameless_below","The Nameless Below",EntityType.WARDEN,360,23,ChatColor.DARK_PURPLE,"The thing the Abyss Order refuses to name.");
            case "frost"->new MobProfile("winters_end","Winter's End",EntityType.RAVAGER,340,22,ChatColor.WHITE,"A beast said to freeze entire kingdoms.");
            case "infernal"->new MobProfile("ashen_emperor","Ashen Emperor",EntityType.WITHER,350,24,ChatColor.RED,"The emperor whose flame outlived his empire.");
            case "verdant"->new MobProfile("rootfather","The Rootfather",EntityType.RAVAGER,365,22,ChatColor.DARK_GREEN,"The oldest living will beneath the Wilds.");
            case "celestial"->new MobProfile("fallen_star","The Fallen Star",EntityType.EVOKER,330,24,ChatColor.AQUA,"A star that answered the Astral Council.");
            case "bloodmoon"->new MobProfile("crimson_eclipse","Crimson Eclipse",EntityType.WITHER,380,25,ChatColor.DARK_RED,"The curse behind the Bloodmoon itself.");
            case "shattered"->new MobProfile("null_king","The Null King",EntityType.WARDEN,500,28,ChatColor.DARK_PURPLE,"The final intelligence inside the fracture.");
            case "100"->new MobProfile("realm_architect","The Realm Architect",EntityType.WITHER,450,27,ChatColor.GOLD,"A hidden maker behind Realm 100.");
            default->miniBossProfile(realm);
        };
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
            case "shattered" -> new MobProfile("fracture_monarch", "Fracture Monarch", EntityType.WARDEN, 420, 26, ChatColor.DARK_PURPLE, "The hidden sovereign of the broken realm.");
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
            case "shattered" -> List.of(
                    new MobProfile("fractured_stalker", "Fractured Stalker", EntityType.ENDERMAN, 92, 16, ChatColor.DARK_PURPLE, "Teleports between unstable fractures."),
                    new MobProfile("rift_devourer", "Rift Devourer", EntityType.RAVAGER, 130, 19, ChatColor.LIGHT_PURPLE, "A massive predator feeding on realm energy."),
                    new MobProfile("broken_oracle", "Broken Oracle", EntityType.EVOKER, 88, 17, ChatColor.AQUA, "Casts remnants of forgotten realm magic."),
                    new MobProfile("void_knight", "Void Knight", EntityType.WITHER_SKELETON, 105, 18, ChatColor.DARK_RED, "Ancient armor animated by the fracture."));
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
            case "shattered" -> "SHATTERED CONVERGENCE";
            case "100" -> "REALM FRACTURE";
            default -> "REALM INVASION";
        };
    }

    private Set<String> realms() {
        return Set.of("storm", "abyss", "frost", "infernal", "verdant", "celestial", "bloodmoon", "100", "shattered");
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
            case "esn_shattered" -> "shattered";
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
            case "shattered", "shatteredrealm", "fracture" -> "shattered";
            default -> null;
        };
    }

    private String worldName(String realm) {
        if (realm.equals("100")) return "esn_realm100";
        if (realm.equals("shattered")) return "esn_shattered";
        return "esn_" + realm;
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
            case "shattered" -> "Shattered Realm";
            default -> realm;
        };
    }

    private ChatColor realmColor(String realm) {
        return switch (realm) {
            case "storm", "celestial" -> ChatColor.AQUA;
            case "abyss", "100", "shattered" -> ChatColor.LIGHT_PURPLE;
            case "frost" -> ChatColor.WHITE;
            case "infernal" -> ChatColor.RED;
            case "verdant" -> ChatColor.GREEN;
            case "bloodmoon" -> ChatColor.DARK_RED;
            default -> ChatColor.GRAY;
        };
    }

    private boolean shatteredUnlocked(Player p) {
        return p.hasPermission("esnsmp.admin") ||
                plugin.getConfig().getBoolean("realms.shattered-unlocked." + p.getUniqueId(), false);
    }

    private record MobProfile(String id, String name, EntityType type, double health, double damage,
                              ChatColor color, String description) {}
}
