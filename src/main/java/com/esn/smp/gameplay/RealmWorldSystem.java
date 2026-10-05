package com.esn.smp.gameplay;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;

public final class RealmWorldSystem implements Listener, CommandExecutor {
    private static final String MENU_TITLE = ChatColor.DARK_PURPLE + "ESN Realms";
    private final JavaPlugin plugin;

    public RealmWorldSystem(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("realms.enabled", true)) {
            plugin.getLogger().info("[ESN Realms] Disabled in config.");
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> initializeNext(0, 0));

        long interval = 20L * 60L * 5L;
        Bukkit.getScheduler().runTaskTimer(plugin, this::unloadIdleWorlds, interval, interval);
    }

    private void initializeNext(int index, int created) {
        RealmType[] realms = RealmType.values();
        if (index >= realms.length) {
            plugin.saveConfig();
            plugin.getLogger().info("[ESN Realms] Ready. " + created + " realm world(s) created; existing worlds loaded safely.");
            return;
        }

        RealmType type = realms[index];
        int newCreated = created;
        try {
            boolean existed = worldFolder(type).exists();
            World world = ensureWorld(type);
            if (world != null) {
                if (!plugin.getConfig().getBoolean("realms.worlds." + type.key + ".initialized", false)) {
                    buildSpawnStructure(world, type);
                    plugin.getConfig().set("realms.worlds." + type.key + ".initialized", true);
                    plugin.saveConfig();
                }
                if (!existed) newCreated++;
            }
        } catch (Exception ex) {
            plugin.getLogger().severe("[ESN Realms] Failed to initialize " + type.display + ": " + ex.getMessage());
        }

        int nextCreated = newCreated;
        Bukkit.getScheduler().runTaskLater(plugin, () -> initializeNext(index + 1, nextCreated), 20L);
    }

    public void shutdown() {
        for (RealmType type : RealmType.values()) {
            World world = Bukkit.getWorld(type.worldName);
            if (world != null) world.save();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("realmadmin")) {
            return adminCommand(sender, args);
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("[ESN Realms] Use /realmadmin status from console.");
            return true;
        }

        if (args.length == 0) {
            openMenu(player);
            return true;
        }

        RealmType type = RealmType.from(args[0]);
        if (type == null) {
            player.sendMessage(ChatColor.RED + "Unknown realm. Use /realms to open the Realm Nexus menu.");
            return true;
        }

        travel(player, type);
        return true;
    }

    private boolean adminCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("esnsmp.admin")) {
            sender.sendMessage(ChatColor.RED + "ESN admin only.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sender.sendMessage(ChatColor.DARK_PURPLE + "ESN REALMS STATUS");
            for (RealmType type : RealmType.values()) {
                World loaded = Bukkit.getWorld(type.worldName);
                boolean exists = worldFolder(type).exists();
                sender.sendMessage(ChatColor.GRAY + "• " + type.display + ": " +
                        (loaded != null ? ChatColor.GREEN + "LOADED" : exists ? ChatColor.YELLOW + "UNLOADED" : ChatColor.RED + "MISSING"));
            }
            sender.sendMessage(ChatColor.GRAY + "Realm 100: " +
                    (realm100Unlocked() ? ChatColor.GREEN + "UNLOCKED" : ChatColor.RED + "LOCKED"));
            return true;
        }

        if (args[0].equalsIgnoreCase("unlock100")) {
            plugin.getConfig().set("realms.realm100-unlocked", true);
            plugin.saveConfig();
            sender.sendMessage(ChatColor.GREEN + "Realm 100 unlocked.");
            return true;
        }

        if (args[0].equalsIgnoreCase("lock100")) {
            plugin.getConfig().set("realms.realm100-unlocked", false);
            plugin.saveConfig();
            sender.sendMessage(ChatColor.YELLOW + "Realm 100 locked.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "/realmadmin <status|tp|rebuild|unlock100|lock100> [realm]");
            return true;
        }

        RealmType type = RealmType.from(args[1]);
        if (type == null) {
            sender.sendMessage(ChatColor.RED + "Unknown realm: " + args[1]);
            return true;
        }

        if (args[0].equalsIgnoreCase("tp")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Players only for /realmadmin tp.");
                return true;
            }
            travel(player, type, true);
            return true;
        }

        if (args[0].equalsIgnoreCase("rebuild")) {
            World world = ensureWorld(type);
            if (world == null) {
                sender.sendMessage(ChatColor.RED + "Could not load " + type.display + ".");
                return true;
            }
            buildSpawnStructure(world, type);
            plugin.getConfig().set("realms.worlds." + type.key + ".initialized", true);
            plugin.saveConfig();
            sender.sendMessage(ChatColor.GREEN + "Rebuilt the official spawn structure for " + type.display + ".");
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "/realmadmin <status|tp|rebuild|unlock100|lock100> [realm]");
        return true;
    }

    public void openMenu(Player player) {
        Inventory menu = Bukkit.createInventory(null, 45, MENU_TITLE);

        menu.setItem(4, menuItem(RealmType.NEXUS.icon, ChatColor.LIGHT_PURPLE + "Realm Nexus",
                ChatColor.GRAY + "The central hub of ESN Realms",
                ChatColor.YELLOW + "Click to travel"));

        menu.setItem(10, menuItem(RealmType.STORM.icon, ChatColor.AQUA + "Storm Kingdom",
                ChatColor.GRAY + "Floating islands • lightning • Storm Titan",
                ChatColor.YELLOW + "Click to travel"));

        menu.setItem(12, menuItem(RealmType.ABYSS.icon, ChatColor.DARK_PURPLE + "The Abyss",
                ChatColor.GRAY + "Deep caverns • corruption • Abyss Reaper",
                ChatColor.YELLOW + "Click to travel"));

        menu.setItem(14, menuItem(RealmType.FROST.icon, ChatColor.WHITE + "Frostlands",
                ChatColor.GRAY + "Glaciers • mountains • Frost King",
                ChatColor.YELLOW + "Click to travel"));

        menu.setItem(16, menuItem(RealmType.INFERNAL.icon, ChatColor.RED + "Infernal Empire",
                ChatColor.GRAY + "Volcanoes • lava • Inferno Emperor",
                ChatColor.YELLOW + "Click to travel"));

        boolean unlocked = realm100Unlocked() || player.hasPermission("esnsmp.admin");
        menu.setItem(31, menuItem(unlocked ? RealmType.REALM100.icon : Material.BARRIER,
                unlocked ? ChatColor.GOLD + "Realm 100" : ChatColor.RED + "Realm 100 — LOCKED",
                unlocked ? ChatColor.GRAY + "The Four Realms endgame raid world" : ChatColor.GRAY + "Complete the Four Realms to unlock",
                unlocked ? ChatColor.YELLOW + "Click to travel" : ChatColor.DARK_GRAY + "Not yet available"));

        menu.setItem(40, menuItem(Material.COMPASS, ChatColor.GREEN + "Realm Status",
                ChatColor.GRAY + "Worlds are generated and managed by ESNSMP"));
        menu.setItem(44, menuItem(Material.BARRIER, ChatColor.RED + "Close"));

        player.openInventory(menu);
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(MENU_TITLE)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return;

        String name = ChatColor.stripColor(item.getItemMeta().getDisplayName());
        if (name == null) return;

        switch (name) {
            case "Realm Nexus" -> travel(player, RealmType.NEXUS);
            case "Storm Kingdom" -> travel(player, RealmType.STORM);
            case "The Abyss" -> travel(player, RealmType.ABYSS);
            case "Frostlands" -> travel(player, RealmType.FROST);
            case "Infernal Empire" -> travel(player, RealmType.INFERNAL);
            case "Realm 100" -> travel(player, RealmType.REALM100);
            case "Realm 100 — LOCKED" -> player.sendMessage(ChatColor.RED + "Realm 100 is still locked.");
            case "Close" -> player.closeInventory();
        }
    }

    private void travel(Player player, RealmType type) {
        travel(player, type, false);
    }

    private void travel(Player player, RealmType type, boolean adminBypass) {
        if (type == RealmType.REALM100 && !adminBypass && !realm100Unlocked() && !player.hasPermission("esnsmp.admin")) {
            player.sendMessage(ChatColor.RED + "Realm 100 is locked. Complete the Four Realms first.");
            return;
        }

        player.closeInventory();
        player.sendMessage(ChatColor.GRAY + "Preparing " + ChatColor.WHITE + type.display + ChatColor.GRAY + "...");
        Bukkit.getScheduler().runTask(plugin, () -> {
            World world = ensureWorld(type);
            if (world == null) {
                player.sendMessage(ChatColor.RED + "That realm could not be loaded. Tell ESN staff to check the console.");
                return;
            }
            Location spawn = spawn(type, world);
            boolean teleported = player.teleport(spawn);
            if (teleported) {
                player.sendMessage(ChatColor.DARK_PURPLE + "[ESN Realms] " + ChatColor.GREEN + "Entered " + type.display + ".");
                plugin.getLogger().info("[ESN Realms] ENTER player=" + player.getName() + " realm=" + type.key);
            } else {
                player.sendMessage(ChatColor.RED + "Teleport failed.");
            }
        });
    }

    private World ensureWorld(RealmType type) {
        World loaded = Bukkit.getWorld(type.worldName);
        if (loaded != null) return loaded;

        long seed = plugin.getConfig().getLong("realms.worlds." + type.key + ".seed", defaultSeed(type));
        plugin.getConfig().set("realms.worlds." + type.key + ".seed", seed);

        WorldCreator creator = new WorldCreator(type.worldName);
        creator.environment(World.Environment.NORMAL);
        creator.generateStructures(false);
        creator.seed(seed);
        creator.generator(new RealmGenerator(type, seed));

        World world = creator.createWorld();
        if (world == null) return null;

        configureWorld(world, type);
        return world;
    }

    private void configureWorld(World world, RealmType type) {
        world.setAutoSave(true);
        world.setDifficulty(Difficulty.HARD);
        world.setPVP(true);
        world.getWorldBorder().setCenter(0, 0);
        world.getWorldBorder().setSize(type == RealmType.NEXUS ? 1000.0 : 6000.0);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_PATROL_SPAWNING, false);
        world.setGameRule(GameRule.DO_TRADER_SPAWNING, false);
        world.setGameRule(GameRule.DO_INSOMNIA, false);

        switch (type) {
            case STORM -> {
                world.setTime(18000);
                world.setStorm(true);
                world.setThundering(true);
            }
            case ABYSS -> world.setTime(18000);
            case FROST -> {
                world.setTime(6000);
                world.setStorm(true);
            }
            case INFERNAL -> world.setTime(18000);
            case REALM100 -> world.setTime(13000);
            case NEXUS -> world.setTime(6000);
        }

        Location spawn = spawn(type, world);
        world.setSpawnLocation(spawn.getBlockX(), spawn.getBlockY(), spawn.getBlockZ());
    }

    private Location spawn(RealmType type, World world) {
        return new Location(world, 0.5, type.spawnY + 2.0, 0.5, 0f, 0f);
    }

    private void buildSpawnStructure(World world, RealmType type) {
        int y = type.spawnY;
        int radius = type == RealmType.NEXUS ? 18 : 9;

        for (int x = -radius - 2; x <= radius + 2; x++) {
            for (int z = -radius - 2; z <= radius + 2; z++) {
                for (int yy = y + 1; yy <= y + 10; yy++) {
                    world.getBlockAt(x, yy, z).setType(Material.AIR, false);
                }
            }
        }

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double d = Math.sqrt((double) x * x + (double) z * z);
                if (d <= radius) {
                    world.getBlockAt(x, y - 1, z).setType(type.foundation, false);
                    world.getBlockAt(x, y, z).setType(d > radius - 2 ? type.trim : type.floor, false);
                }
            }
        }

        int pillar = Math.max(5, radius - 3);
        buildPillar(world, pillar, y, pillar, type.trim);
        buildPillar(world, -pillar, y, pillar, type.trim);
        buildPillar(world, pillar, y, -pillar, type.trim);
        buildPillar(world, -pillar, y, -pillar, type.trim);

        world.getBlockAt(0, y + 1, 0).setType(type.centerpiece, false);

        if (type == RealmType.NEXUS) {
            buildGate(world, 0, y + 1, -12, RealmType.STORM.trim);
            buildGate(world, 12, y + 1, 0, RealmType.INFERNAL.trim);
            buildGate(world, 0, y + 1, 12, RealmType.FROST.trim);
            buildGate(world, -12, y + 1, 0, RealmType.ABYSS.trim);
            buildGate(world, 0, y + 1, -5, RealmType.REALM100.trim);
        } else {
            buildRealmLandmarks(world, type);
        }

        world.setSpawnLocation(0, y + 2, 0);
        world.save();
        plugin.getLogger().info("[ESN Realms] Built " + type.display + " spawn structure.");
    }

    private void buildPillar(World world, int x, int y, int z, Material material) {
        for (int yy = y + 1; yy <= y + 6; yy++) world.getBlockAt(x, yy, z).setType(material, false);
        world.getBlockAt(x, y + 7, z).setType(Material.SEA_LANTERN, false);
    }

    private void buildGate(World world, int x, int y, int z, Material material) {
        boolean northSouth = Math.abs(z) > Math.abs(x);
        for (int h = 0; h <= 5; h++) {
            if (northSouth) {
                world.getBlockAt(x - 2, y + h, z).setType(material, false);
                world.getBlockAt(x + 2, y + h, z).setType(material, false);
            } else {
                world.getBlockAt(x, y + h, z - 2).setType(material, false);
                world.getBlockAt(x, y + h, z + 2).setType(material, false);
            }
        }
        for (int w = -2; w <= 2; w++) {
            if (northSouth) world.getBlockAt(x + w, y + 5, z).setType(material, false);
            else world.getBlockAt(x, y + 5, z + w).setType(material, false);
        }
    }

    private void buildRealmLandmarks(World world, RealmType type) {
        int y = type.spawnY - 2;
        switch (type) {
            case STORM -> {
                buildFortress(world, 92, y, 0, Material.STONE_BRICKS, Material.CUT_COPPER, Material.LIGHTNING_ROD);
                buildBossArena(world, -92, y, 0, Material.STONE_BRICKS, Material.COPPER_BLOCK);
            }
            case ABYSS -> {
                buildFortress(world, 92, y, 0, Material.DEEPSLATE_TILES, Material.SCULK, Material.CRYING_OBSIDIAN);
                buildBossArena(world, -92, y, 0, Material.DEEPSLATE_BRICKS, Material.SCULK);
            }
            case FROST -> {
                buildFortress(world, 92, y, 0, Material.PACKED_ICE, Material.BLUE_ICE, Material.SEA_LANTERN);
                buildBossArena(world, -92, y, 0, Material.SNOW_BLOCK, Material.BLUE_ICE);
            }
            case INFERNAL -> {
                buildFortress(world, 92, y, 0, Material.NETHER_BRICKS, Material.BLACKSTONE, Material.MAGMA_BLOCK);
                buildBossArena(world, -92, y, 0, Material.POLISHED_BLACKSTONE_BRICKS, Material.MAGMA_BLOCK);
            }
            case REALM100 -> {
                buildFortress(world, 92, y, 0, Material.END_STONE_BRICKS, Material.PURPUR_BLOCK, Material.END_PORTAL_FRAME);
                buildBossArena(world, -92, y, 0, Material.OBSIDIAN, Material.PURPUR_BLOCK);
            }
            default -> {
            }
        }
    }

    private void buildFortress(World world, int cx, int y, int cz, Material wall, Material accent, Material crown) {
        int half = 14;
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                world.getBlockAt(cx + x, y - 1, cz + z).setType(Material.DEEPSLATE, false);
                world.getBlockAt(cx + x, y, cz + z).setType(Math.abs(x) == half || Math.abs(z) == half ? accent : wall, false);
                for (int yy = 1; yy <= 10; yy++) world.getBlockAt(cx + x, y + yy, cz + z).setType(Material.AIR, false);
            }
        }

        for (int x = -half; x <= half; x++) {
            for (int h = 1; h <= 7; h++) {
                world.getBlockAt(cx + x, y + h, cz - half).setType(wall, false);
                world.getBlockAt(cx + x, y + h, cz + half).setType(wall, false);
            }
        }
        for (int z = -half; z <= half; z++) {
            for (int h = 1; h <= 7; h++) {
                world.getBlockAt(cx - half, y + h, cz + z).setType(wall, false);
                world.getBlockAt(cx + half, y + h, cz + z).setType(wall, false);
            }
        }

        for (int x = -2; x <= 2; x++) {
            for (int h = 1; h <= 5; h++) world.getBlockAt(cx + x, y + h, cz - half).setType(Material.AIR, false);
        }

        int[] corners = {-half, half};
        for (int ox : corners) {
            for (int oz : corners) {
                for (int x = -2; x <= 2; x++) {
                    for (int z = -2; z <= 2; z++) {
                        for (int h = 1; h <= 11; h++) {
                            boolean shell = Math.abs(x) == 2 || Math.abs(z) == 2 || h == 11;
                            if (shell) world.getBlockAt(cx + ox + x, y + h, cz + oz + z).setType(h == 11 ? accent : wall, false);
                        }
                    }
                }
                world.getBlockAt(cx + ox, y + 12, cz + oz).setType(crown, false);
            }
        }

        for (int h = 1; h <= 9; h++) world.getBlockAt(cx, y + h, cz).setType(accent, false);
        world.getBlockAt(cx, y + 10, cz).setType(crown, false);
    }

    private void buildBossArena(World world, int cx, int y, int cz, Material floor, Material accent) {
        int radius = 17;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double d = Math.sqrt((double) x * x + (double) z * z);
                if (d <= radius) {
                    world.getBlockAt(cx + x, y - 1, cz + z).setType(Material.DEEPSLATE, false);
                    world.getBlockAt(cx + x, y, cz + z).setType(d >= radius - 2 ? accent : floor, false);
                    for (int h = 1; h <= 8; h++) world.getBlockAt(cx + x, y + h, cz + z).setType(Material.AIR, false);
                }
            }
        }

        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2.0 * i / 8.0;
            int x = cx + (int) Math.round(Math.cos(angle) * 14);
            int z = cz + (int) Math.round(Math.sin(angle) * 14);
            for (int h = 1; h <= 6; h++) world.getBlockAt(x, y + h, z).setType(accent, false);
            world.getBlockAt(x, y + 7, z).setType(Material.SEA_LANTERN, false);
        }
    }

    private void unloadIdleWorlds() {
        if (!plugin.getConfig().getBoolean("realms.unload-idle-worlds", true)) return;

        for (RealmType type : RealmType.values()) {
            if (type == RealmType.NEXUS) continue;
            World world = Bukkit.getWorld(type.worldName);
            if (world == null || !world.getPlayers().isEmpty()) continue;

            try {
                world.save();
                if (Bukkit.unloadWorld(world, true)) {
                    plugin.getLogger().info("[ESN Realms] Unloaded idle world " + type.worldName + " to save RAM.");
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("[ESN Realms] Could not unload " + type.worldName + ": " + ex.getMessage());
            }
        }
    }

    private boolean realm100Unlocked() {
        return plugin.getConfig().getBoolean("realms.realm100-unlocked", false);
    }

    private File worldFolder(RealmType type) {
        return new File(Bukkit.getWorldContainer(), type.worldName);
    }

    private long defaultSeed(RealmType type) {
        long base = plugin.getConfig().getLong("realms.seed", 0x45534E5354554449L);
        return base ^ ((long) type.key.hashCode() * 0x9E3779B97F4A7C15L);
    }

    private ItemStack menuItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    private enum RealmType {
        NEXUS("nexus", "esn_nexus", "Realm Nexus", Material.RECOVERY_COMPASS, 80,
                Material.POLISHED_DEEPSLATE, Material.POLISHED_BLACKSTONE_BRICKS, Material.AMETHYST_BLOCK, Material.BEACON),
        STORM("storm", "esn_storm", "Storm Kingdom", Material.LIGHTNING_ROD, 128,
                Material.STONE, Material.STONE_BRICKS, Material.COPPER_BLOCK, Material.LIGHTNING_ROD),
        ABYSS("abyss", "esn_abyss", "The Abyss", Material.ECHO_SHARD, 70,
                Material.DEEPSLATE, Material.DEEPSLATE_TILES, Material.SCULK, Material.CRYING_OBSIDIAN),
        FROST("frost", "esn_frost", "Frostlands", Material.BLUE_ICE, 115,
                Material.STONE, Material.PACKED_ICE, Material.BLUE_ICE, Material.SEA_LANTERN),
        INFERNAL("infernal", "esn_infernal", "Infernal Empire", Material.MAGMA_BLOCK, 90,
                Material.BLACKSTONE, Material.POLISHED_BLACKSTONE, Material.MAGMA_BLOCK, Material.RESPAWN_ANCHOR),
        REALM100("100", "esn_realm100", "Realm 100", Material.NETHER_STAR, 100,
                Material.OBSIDIAN, Material.END_STONE_BRICKS, Material.PURPUR_BLOCK, Material.END_PORTAL_FRAME);

        private final String key;
        private final String worldName;
        private final String display;
        private final Material icon;
        private final int spawnY;
        private final Material foundation;
        private final Material floor;
        private final Material trim;
        private final Material centerpiece;

        RealmType(String key, String worldName, String display, Material icon, int spawnY,
                  Material foundation, Material floor, Material trim, Material centerpiece) {
            this.key = key;
            this.worldName = worldName;
            this.display = display;
            this.icon = icon;
            this.spawnY = spawnY;
            this.foundation = foundation;
            this.floor = floor;
            this.trim = trim;
            this.centerpiece = centerpiece;
        }

        private static RealmType from(String input) {
            String value = input.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
            return switch (value) {
                case "nexus", "hub" -> NEXUS;
                case "storm", "stormkingdom", "lightning" -> STORM;
                case "abyss", "theabyss", "void" -> ABYSS;
                case "frost", "frostlands", "ice" -> FROST;
                case "infernal", "infernalempire", "fire" -> INFERNAL;
                case "100", "realm100", "r100", "final" -> REALM100;
                default -> null;
            };
        }
    }

    private static final class RealmGenerator extends ChunkGenerator {
        private final RealmType type;
        private final long seed;

        private RealmGenerator(RealmType type, long seed) {
            this.type = type;
            this.seed = seed;
        }

        @Override
        public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData data) {
            int baseX = chunkX << 4;
            int baseZ = chunkZ << 4;

            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    int x = baseX + lx;
                    int z = baseZ + lz;
                    switch (type) {
                        case NEXUS -> nexusColumn(data, lx, lz, x, z);
                        case STORM -> stormColumn(data, lx, lz, x, z);
                        case ABYSS -> abyssColumn(data, lx, lz, x, z);
                        case FROST -> frostColumn(data, lx, lz, x, z);
                        case INFERNAL -> infernalColumn(data, lx, lz, x, z);
                        case REALM100 -> realm100Column(data, lx, lz, x, z);
                    }
                }
            }
        }

        @Override
        public int getBaseHeight(WorldInfo worldInfo, Random random, int x, int z, HeightMap heightMap) {
            return switch (type) {
                case NEXUS -> 80;
                case STORM -> stormTop(x, z);
                case ABYSS -> 50;
                case FROST -> frostTop(x, z);
                case INFERNAL -> infernalTop(x, z);
                case REALM100 -> realm100Top(x, z);
            };
        }

        @Override public boolean shouldGenerateSurface() { return false; }
        @Override public boolean shouldGenerateCaves() { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs() { return false; }
        @Override public boolean shouldGenerateStructures() { return false; }

        private void nexusColumn(ChunkData data, int lx, int lz, int x, int z) {
            double d = Math.sqrt((double) x * x + (double) z * z);
            if (d > 86) return;
            int y = 76 + (int) Math.round(noise2(seed + 11, x / 50.0, z / 50.0) * 2.0);
            set(data, lx, y - 3, lz, Material.DEEPSLATE);
            set(data, lx, y - 2, lz, Material.DEEPSLATE);
            set(data, lx, y - 1, lz, Material.POLISHED_DEEPSLATE);
            set(data, lx, y, lz, d > 75 ? Material.AMETHYST_BLOCK : Material.POLISHED_BLACKSTONE_BRICKS);
        }

        private void stormColumn(ChunkData data, int lx, int lz, int x, int z) {
            double dist = Math.sqrt((double) x * x + (double) z * z);
            double central = Math.max(0.0, 1.0 - dist / 95.0);
            double broad = noise2(seed + 101, x / 70.0, z / 70.0);
            double detail = noise2(seed + 102, x / 27.0, z / 27.0);
            double density = central * 1.25 + broad * 0.75 + detail * 0.25 - 0.05;
            if (density < 0.18) return;

            int top = stormTop(x, z);
            int thickness = 8 + (int) Math.min(28, Math.max(0, density * 18));
            int bottom = top - thickness;
            for (int y = bottom; y <= top; y++) {
                Material material = y == top ? (detail > 0.25 ? Material.MOSS_BLOCK : Material.GRASS_BLOCK)
                        : y > top - 4 ? Material.STONE : Material.DEEPSLATE;
                set(data, lx, y, lz, material);
            }
        }

        private int stormTop(int x, int z) {
            double dist = Math.sqrt((double) x * x + (double) z * z);
            double central = Math.max(0.0, 1.0 - dist / 95.0);
            return 108 + (int) Math.round(noise2(seed + 103, x / 85.0, z / 85.0) * 18.0 + central * 18.0);
        }

        private void abyssColumn(ChunkData data, int lx, int lz, int x, int z) {
            int floor = 38 + (int) Math.round(noise2(seed + 201, x / 65.0, z / 65.0) * 10.0);
            int ceiling = 145 + (int) Math.round(noise2(seed + 202, x / 80.0, z / 80.0) * 14.0);

            for (int y = data.getMinHeight(); y <= floor; y++) {
                Material m = y == floor ? Material.SCULK : y > floor - 5 ? Material.DEEPSLATE : Material.TUFF;
                set(data, lx, y, lz, m);
            }
            for (int y = ceiling; y < Math.min(data.getMaxHeight(), ceiling + 28); y++) {
                set(data, lx, y, lz, y == ceiling ? Material.SCULK : Material.DEEPSLATE);
            }

            double pillar = noise2(seed + 203, x / 31.0, z / 31.0);
            if (pillar > 0.72) {
                int radiusFactor = (int) ((pillar - 0.72) * 100);
                if (radiusFactor > 1) {
                    for (int y = floor + 1; y < ceiling; y++) {
                        set(data, lx, y, lz, y % 7 == 0 ? Material.SCULK : Material.DEEPSLATE_TILES);
                    }
                }
            }
        }

        private void frostColumn(ChunkData data, int lx, int lz, int x, int z) {
            int top = frostTop(x, z);
            int min = Math.max(data.getMinHeight(), top - 70);
            for (int y = min; y <= top; y++) {
                Material m;
                if (y == top) m = Material.SNOW_BLOCK;
                else if (y > top - 5) m = Material.PACKED_ICE;
                else if (y > top - 14) m = Material.STONE;
                else m = Material.DEEPSLATE;
                set(data, lx, y, lz, m);
            }
        }

        private int frostTop(int x, int z) {
            double a = noise2(seed + 301, x / 90.0, z / 90.0);
            double b = Math.abs(noise2(seed + 302, x / 38.0, z / 38.0));
            double central = Math.max(0, 1.0 - Math.sqrt((double) x * x + (double) z * z) / 100.0);
            return 82 + (int) Math.round(a * 26 + b * 24 + central * 18);
        }

        private void infernalColumn(ChunkData data, int lx, int lz, int x, int z) {
            int top = infernalTop(x, z);
            int lava = 52;
            for (int y = Math.max(data.getMinHeight(), top - 65); y <= top; y++) {
                Material m = y == top ? (noise2(seed + 403, x / 12.0, z / 12.0) > 0.45 ? Material.MAGMA_BLOCK : Material.BLACKSTONE)
                        : y > top - 7 ? Material.BASALT : Material.BLACKSTONE;
                set(data, lx, y, lz, m);
            }
            if (top < lava) {
                for (int y = top + 1; y <= lava; y++) set(data, lx, y, lz, Material.LAVA);
            }
        }

        private int infernalTop(int x, int z) {
            double central = Math.max(0, 1.0 - Math.sqrt((double) x * x + (double) z * z) / 90.0);
            return 55 + (int) Math.round(noise2(seed + 401, x / 65.0, z / 65.0) * 25.0
                    + Math.abs(noise2(seed + 402, x / 28.0, z / 28.0)) * 15.0 + central * 26.0);
        }

        private void realm100Column(ChunkData data, int lx, int lz, int x, int z) {
            int top = realm100Top(x, z);
            Material surface;
            Material under;
            if (x >= 0 && z < 0) {
                surface = Material.COPPER_BLOCK;
                under = Material.STONE;
            } else if (x < 0 && z < 0) {
                surface = Material.BLUE_ICE;
                under = Material.PACKED_ICE;
            } else if (x < 0) {
                surface = Material.SCULK;
                under = Material.DEEPSLATE;
            } else {
                surface = Material.MAGMA_BLOCK;
                under = Material.BLACKSTONE;
            }

            double center = Math.sqrt((double) x * x + (double) z * z);
            if (center < 26) {
                surface = Material.PURPUR_BLOCK;
                under = Material.END_STONE_BRICKS;
            }

            for (int y = Math.max(data.getMinHeight(), top - 55); y <= top; y++) {
                set(data, lx, y, lz, y == top ? surface : under);
            }
        }

        private int realm100Top(int x, int z) {
            double central = Math.max(0, 1.0 - Math.sqrt((double) x * x + (double) z * z) / 90.0);
            return 72 + (int) Math.round(noise2(seed + 501, x / 58.0, z / 58.0) * 22.0 + central * 24.0);
        }

        private void set(ChunkData data, int x, int y, int z, Material material) {
            if (y >= data.getMinHeight() && y < data.getMaxHeight()) data.setBlock(x, y, z, material);
        }

        private static double noise2(long seed, double x, double z) {
            int x0 = fastFloor(x);
            int z0 = fastFloor(z);
            int x1 = x0 + 1;
            int z1 = z0 + 1;
            double tx = fade(x - x0);
            double tz = fade(z - z0);
            double a = hash(seed, x0, z0);
            double b = hash(seed, x1, z0);
            double c = hash(seed, x0, z1);
            double d = hash(seed, x1, z1);
            double ab = lerp(a, b, tx);
            double cd = lerp(c, d, tx);
            return lerp(ab, cd, tz);
        }

        private static int fastFloor(double value) {
            int i = (int) value;
            return value < i ? i - 1 : i;
        }

        private static double fade(double t) {
            return t * t * (3.0 - 2.0 * t);
        }

        private static double lerp(double a, double b, double t) {
            return a + (b - a) * t;
        }

        private static double hash(long seed, int x, int z) {
            long h = seed;
            h ^= (long) x * 0x9E3779B97F4A7C15L;
            h ^= (long) z * 0xC2B2AE3D27D4EB4FL;
            h ^= h >>> 30;
            h *= 0xBF58476D1CE4E5B9L;
            h ^= h >>> 27;
            h *= 0x94D049BB133111EBL;
            h ^= h >>> 31;
            return ((h >>> 11) * 0x1.0p-53) * 2.0 - 1.0;
        }
    }
}
