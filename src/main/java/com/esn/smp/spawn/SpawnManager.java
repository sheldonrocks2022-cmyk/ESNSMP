package com.esn.smp.spawn;

import com.esn.smp.ESNSMPPlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class SpawnManager {
    private static final int HUB_RADIUS = 80;
    private static final int CLEAR_HEIGHT = 10;
    private static final int LOGO_RADIUS = 26;
    private static final int BLOCKS_PER_TICK = 2400;

    private final ESNSMPPlugin plugin;
    private boolean building;
    private BukkitTask buildTask;
    private BackupWriter activeBackup;

    public SpawnManager(ESNSMPPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void ensureConfigured() {
        FileConfiguration cfg = plugin.getConfig();
        if (cfg.getBoolean("spawn.configured", false) && getSpawn() != null) {
            return;
        }

        World world = Bukkit.getWorld(cfg.getString("spawn.world", "world"));
        if (world == null) {
            List<World> worlds = Bukkit.getWorlds();
            if (worlds.isEmpty()) {
                throw new IllegalStateException("No loaded worlds are available.");
            }
            world = worlds.get(0);
        }

        Location vanilla = world.getSpawnLocation();
        int x = vanilla.getBlockX();
        int z = vanilla.getBlockZ();
        int y = world.getHighestBlockYAt(x, z) + 1;
        Location safe = new Location(world, x + 0.5, y, z + 0.5, 0.0f, 0.0f);
        setSpawn(safe, false);
        plugin.getLogger().info("Configured ESN spawn at " + format(safe));
    }

    public Location getSpawn() {
        FileConfiguration cfg = plugin.getConfig();
        String worldName = cfg.getString("spawn.world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(
                world,
                cfg.getDouble("spawn.x", world.getSpawnLocation().getX()),
                cfg.getDouble("spawn.y", world.getSpawnLocation().getY()),
                cfg.getDouble("spawn.z", world.getSpawnLocation().getZ()),
                (float) cfg.getDouble("spawn.yaw", 0.0),
                (float) cfg.getDouble("spawn.pitch", 0.0)
        );
    }

    public void setSpawn(Location location, boolean setWorldSpawn) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("Spawn location must have a world.");
        }

        FileConfiguration cfg = plugin.getConfig();
        cfg.set("spawn.world", location.getWorld().getName());
        cfg.set("spawn.x", location.getX());
        cfg.set("spawn.y", location.getY());
        cfg.set("spawn.z", location.getZ());
        cfg.set("spawn.yaw", location.getYaw());
        cfg.set("spawn.pitch", location.getPitch());
        cfg.set("spawn.configured", true);
        plugin.saveConfig();

        if (setWorldSpawn) {
            location.getWorld().setSpawnLocation(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }
    }

    public boolean isProtected(Location location) {
        Location spawn = getSpawn();
        if (spawn == null || location == null || location.getWorld() == null
                || !location.getWorld().getUID().equals(spawn.getWorld().getUID())) {
            return false;
        }

        double radius = plugin.getConfig().getDouble("spawn.protection-radius", 100.0);
        double dx = location.getX() - spawn.getX();
        double dz = location.getZ() - spawn.getZ();
        return (dx * dx) + (dz * dz) <= radius * radius;
    }

    public synchronized void buildSpawn(CommandSender sender) {
        if (building) {
            sender.sendMessage(ChatColor.RED + "The ESN spawn is already being built.");
            return;
        }

        ensureConfigured();
        Location center = getSpawn();
        if (center == null || center.getWorld() == null) {
            sender.sendMessage(ChatColor.RED + "Cannot build spawn because its world is not loaded.");
            return;
        }

        World world = center.getWorld();
        Location previousWorldSpawn = world.getSpawnLocation();
        File backupFile = createBackupFile();

        plugin.getConfig().set("spawn.previous-world-spawn.world", previousWorldSpawn.getWorld().getName());
        plugin.getConfig().set("spawn.previous-world-spawn.x", previousWorldSpawn.getX());
        plugin.getConfig().set("spawn.previous-world-spawn.y", previousWorldSpawn.getY());
        plugin.getConfig().set("spawn.previous-world-spawn.z", previousWorldSpawn.getZ());
        plugin.getConfig().set("spawn.last-backup", backupFile.getAbsolutePath());
        plugin.getConfig().set("spawn.build-incomplete", true);
        plugin.saveConfig();

        List<BlockChange> changes = prepareBuild(center);
        sender.sendMessage(ChatColor.GOLD + "Building the massive ESN SMP spawn...");
        sender.sendMessage(ChatColor.GRAY + "Size: " + (HUB_RADIUS * 2 + 1) + " blocks across. Changes are applied safely in batches.");

        try {
            this.activeBackup = new BackupWriter(backupFile, world);
        } catch (UncheckedIOException ex) {
            plugin.getConfig().set("spawn.build-incomplete", false);
            plugin.saveConfig();
            sender.sendMessage(ChatColor.RED + "Could not create the rollback backup. Spawn was not changed.");
            plugin.getLogger().severe("Could not start spawn backup: " + ex.getMessage());
            return;
        }

        building = true;
        final int[] cursor = {0};

        buildTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try {
                int processed = 0;
                while (cursor[0] < changes.size() && processed < BLOCKS_PER_TICK) {
                    BlockChange change = changes.get(cursor[0]++);
                    Block block = world.getBlockAt(change.x(), change.y(), change.z());
                    activeBackup.set(block, change.material());
                    processed++;
                }

                if (cursor[0] >= changes.size()) {
                    finishSuccessfulBuild(sender, center, backupFile);
                }
            } catch (Exception ex) {
                failBuild(sender, ex);
            }
        }, 1L, 1L);
    }

    public synchronized void rebuildSpawn(CommandSender sender) {
        if (building) {
            sender.sendMessage(ChatColor.RED + "Wait for the current spawn operation to finish first.");
            return;
        }

        if (plugin.getConfig().getBoolean("spawn.generated", false)
                || plugin.getConfig().getBoolean("spawn.build-incomplete", false)) {
            sender.sendMessage(ChatColor.YELLOW + "Restoring the previous spawn before creating the larger design...");
            if (!rollbackNow(sender)) {
                sender.sendMessage(ChatColor.RED + "Rebuild cancelled because the old spawn could not be restored safely.");
                return;
            }
        }

        buildSpawn(sender);
    }

    private List<BlockChange> prepareBuild(Location center) {
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        List<BlockChange> changes = new ArrayList<>(240000);

        // Massive circular plaza: 161 blocks across.
        for (int dx = -HUB_RADIUS; dx <= HUB_RADIUS; dx++) {
            for (int dz = -HUB_RADIUS; dz <= HUB_RADIUS; dz++) {
                int distanceSquared = (dx * dx) + (dz * dz);
                if (distanceSquared > HUB_RADIUS * HUB_RADIUS) {
                    continue;
                }

                double distance = Math.sqrt(distanceSquared);

                for (int dy = 0; dy < CLEAR_HEIGHT; dy++) {
                    changes.add(new BlockChange(cx + dx, cy + dy, cz + dz, Material.AIR));
                }

                Material floor;
                if (distance >= HUB_RADIUS - 3) {
                    floor = Material.DEEPSLATE_BRICKS;
                } else if (Math.abs(dx) <= 3 || Math.abs(dz) <= 3) {
                    floor = Material.SMOOTH_STONE;
                } else if (distance >= 50 && distance <= 53) {
                    floor = Material.STONE_BRICKS;
                } else if (((cx + dx) + (cz + dz)) % 11 == 0) {
                    floor = Material.MOSSY_STONE_BRICKS;
                } else {
                    floor = Material.POLISHED_ANDESITE;
                }
                changes.add(new BlockChange(cx + dx, cy - 1, cz + dz, floor));
            }
        }

        addCenterMedallion(changes, cx, cy, cz);
        addLogoMonument(changes, cx, cy, cz);
        addGateways(changes, cx, cy, cz);
        addTowers(changes, cx, cy, cz);
        addRoadLighting(changes, cx, cy, cz);
        addServiceStations(changes, cx, cy, cz);
        addServiceDistrict(changes, cx, cy, cz);

        // Exact player landing spot.
        changes.add(new BlockChange(cx, cy, cz, Material.SMOOTH_STONE));
        changes.add(new BlockChange(cx, cy + 1, cz, Material.AIR));
        changes.add(new BlockChange(cx, cy + 2, cz, Material.AIR));

        return changes;
    }

    private void addCenterMedallion(List<BlockChange> changes, int cx, int cy, int cz) {
        int radius = 14;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 <= radius * radius) {
                    Material material;
                    if (d2 >= 121) {
                        material = Material.GILDED_BLACKSTONE;
                    } else if (((dx + dz) & 1) == 0) {
                        material = Material.DEEPSLATE_TILES;
                    } else {
                        material = Material.POLISHED_DEEPSLATE;
                    }
                    changes.add(new BlockChange(cx + dx, cy, cz + dz, material));
                }
            }
        }

        for (int i = -9; i <= 9; i++) {
            changes.add(new BlockChange(cx + i, cy, cz, Material.YELLOW_TERRACOTTA));
            changes.add(new BlockChange(cx, cy, cz + i, Material.YELLOW_TERRACOTTA));
        }
    }

    private void addLogoMonument(List<BlockChange> changes, int cx, int cy, int cz) {
        int planeZ = cz - 58;
        int logoCenterY = cy + 27;

        // Giant stone-ring ESN SMP monument: ~53 blocks across.
        for (int x = -LOGO_RADIUS; x <= LOGO_RADIUS; x++) {
            for (int y = -LOGO_RADIUS; y <= LOGO_RADIUS; y++) {
                double d = Math.sqrt((x * x) + (y * y));
                if (d <= LOGO_RADIUS - 3) {
                    changes.add(new BlockChange(cx + x, logoCenterY + y, planeZ, Material.BLACK_CONCRETE));
                } else if (d <= LOGO_RADIUS) {
                    Material ring = ((x + y) & 1) == 0 ? Material.STONE_BRICKS : Material.MOSSY_STONE_BRICKS;
                    changes.add(new BlockChange(cx + x, logoCenterY + y, planeZ, ring));
                }
            }
        }

        // Sunset glow from the supplied logo.
        for (int x = -19; x <= 19; x++) {
            for (int y = 8; y <= 19; y++) {
                if ((x * x) + (y * y) <= 520) {
                    Material glow = y >= 15 ? Material.ORANGE_CONCRETE : Material.YELLOW_TERRACOTTA;
                    changes.add(new BlockChange(cx + x, logoCenterY + y, planeZ + 1, glow));
                }
            }
        }

        String[][] top = {
                {"11111","10000","11110","10000","11111"},
                {"11111","10000","11111","00001","11111"},
                {"10001","11001","10101","10011","10001"}
        };
        String[][] bottom = {
                {"11111","10000","11111","00001","11111"},
                {"10001","11011","10101","10001","10001"},
                {"11110","10001","11110","10000","10000"}
        };

        drawWord(changes, cx - 20, logoCenterY + 10, planeZ + 2, top, Material.YELLOW_TERRACOTTA, 2);
        drawWord(changes, cx - 20, logoCenterY - 3, planeZ + 2, bottom, Material.LIGHT_GRAY_CONCRETE, 2);

        // Heavy stone supports and lit base.
        for (int supportX : new int[]{-21, -18, 18, 21}) {
            for (int y = cy; y <= logoCenterY - 21; y++) {
                changes.add(new BlockChange(cx + supportX, y, planeZ, Material.DEEPSLATE_BRICKS));
            }
        }

        for (int x = -24; x <= 24; x++) {
            changes.add(new BlockChange(cx + x, cy, planeZ + 1, Material.STONE_BRICKS));
            if (x % 6 == 0) {
                changes.add(new BlockChange(cx + x, cy + 1, planeZ + 1, Material.SEA_LANTERN));
            }
        }
    }

    private void drawWord(List<BlockChange> changes, int startX, int topY, int z,
                          String[][] letters, Material material, int scale) {
        int cursor = startX;
        for (String[] letter : letters) {
            for (int row = 0; row < letter.length; row++) {
                for (int col = 0; col < letter[row].length(); col++) {
                    if (letter[row].charAt(col) != '1') {
                        continue;
                    }
                    for (int sx = 0; sx < scale; sx++) {
                        for (int sy = 0; sy < scale; sy++) {
                            changes.add(new BlockChange(
                                    cursor + col * scale + sx,
                                    topY - row * scale - sy,
                                    z,
                                    material
                            ));
                        }
                    }
                }
            }
            cursor += 7 * scale;
        }
    }

    private void addGateways(List<BlockChange> changes, int cx, int cy, int cz) {
        int[][] gates = {{0, 72}, {72, 0}, {-72, 0}};
        for (int[] gate : gates) {
            boolean xGate = gate[0] != 0;
            for (int side : new int[]{-1, 1}) {
                for (int y = 0; y <= 11; y++) {
                    int gx = cx + gate[0] + (xGate ? 0 : side * 6);
                    int gz = cz + gate[1] + (xGate ? side * 6 : 0);
                    changes.add(new BlockChange(gx, cy + y, gz, Material.DEEPSLATE_BRICKS));
                }
            }

            for (int span = -6; span <= 6; span++) {
                int gx = cx + gate[0] + (xGate ? 0 : span);
                int gz = cz + gate[1] + (xGate ? span : 0);
                changes.add(new BlockChange(gx, cy + 11, gz, Material.STONE_BRICKS));
                changes.add(new BlockChange(gx, cy + 12, gz, Material.DEEPSLATE_TILE_SLAB));
            }
        }
    }

    private void addTowers(List<BlockChange> changes, int cx, int cy, int cz) {
        int[][] towers = {{48,48},{48,-48},{-48,48},{-48,-48}};
        for (int[] tower : towers) {
            int tx = cx + tower[0];
            int tz = cz + tower[1];
            for (int y = 0; y <= 14; y++) {
                for (int ox = -2; ox <= 2; ox++) {
                    for (int oz = -2; oz <= 2; oz++) {
                        boolean shell = Math.abs(ox) == 2 || Math.abs(oz) == 2;
                        if (shell) {
                            Material m = y % 5 == 0 ? Material.CHISELED_STONE_BRICKS : Material.STONE_BRICKS;
                            changes.add(new BlockChange(tx + ox, cy + y, tz + oz, m));
                        }
                    }
                }
            }
            changes.add(new BlockChange(tx, cy + 15, tz, Material.SEA_LANTERN));
        }
    }

    private void addRoadLighting(List<BlockChange> changes, int cx, int cy, int cz) {
        for (int distance = 18; distance <= 66; distance += 12) {
            int[][] lamps = {
                    {distance, 6}, {distance, -6}, {-distance, 6}, {-distance, -6},
                    {6, distance}, {-6, distance}, {6, -distance}, {-6, -distance}
            };
            for (int[] p : lamps) {
                for (int y = 0; y < 4; y++) {
                    changes.add(new BlockChange(cx + p[0], cy + y, cz + p[1], Material.POLISHED_BLACKSTONE_BRICK_WALL));
                }
                changes.add(new BlockChange(cx + p[0], cy + 4, cz + p[1], Material.LANTERN));
            }
        }

        int[][] cardinals = {{0,68},{68,0},{-68,0}};
        for (int[] p : cardinals) {
            changes.add(new BlockChange(cx + p[0], cy, cz + p[1], Material.CHISELED_STONE_BRICKS));
            changes.add(new BlockChange(cx + p[0], cy + 1, cz + p[1], Material.SEA_LANTERN));
        }
    }

    private void addServiceDistrict(List<BlockChange> changes, int cx, int cy, int cz) {
        for (HubService service : HubService.values()) {
            int sx = cx + service.offsetX();
            int sz = cz + service.offsetZ();

            // Raised 11x11 service pad.
            for (int dx = -5; dx <= 5; dx++) {
                for (int dz = -5; dz <= 5; dz++) {
                    Material floor = (Math.abs(dx) == 5 || Math.abs(dz) == 5)
                            ? Material.DEEPSLATE_BRICKS : Material.SMOOTH_STONE;
                    changes.add(new BlockChange(sx + dx, cy, sz + dz, floor));
                }
            }

            // Four corner columns and a unique interactive core.
            int[][] corners = {{-5,-5},{-5,5},{5,-5},{5,5}};
            for (int[] corner : corners) {
                for (int y = 1; y <= 5; y++) {
                    changes.add(new BlockChange(sx + corner[0], cy + y, sz + corner[1], Material.STONE_BRICKS));
                }
                changes.add(new BlockChange(sx + corner[0], cy + 6, sz + corner[1], Material.LANTERN));
            }

            for (int y = 1; y <= 3; y++) {
                changes.add(new BlockChange(sx, cy + y, sz, service.coreMaterial()));
            }
            changes.add(new BlockChange(sx, cy + 4, sz, Material.SEA_LANTERN));

            // Gold trim makes service stations visible from the central hub.
            for (int i = -3; i <= 3; i++) {
                changes.add(new BlockChange(sx + i, cy, sz - 4, Material.YELLOW_TERRACOTTA));
            }
        }
    }

    private void addServiceStations(List<BlockChange> changes, int cx, int cy, int cz) {
        for (HubService service : HubService.values()) {
            int sx=cx+service.offsetX(), sz=cz+service.offsetZ();
            for(int dx=-4;dx<=4;dx++) for(int dz=-4;dz<=4;dz++)
                changes.add(new BlockChange(sx+dx,cy,sz+dz,Math.abs(dx)==4||Math.abs(dz)==4?Material.DEEPSLATE_TILES:Material.POLISHED_ANDESITE));
            for(int y=1;y<=6;y++) for(int side:new int[]{-4,4}) {
                changes.add(new BlockChange(sx+side,cy+y,sz-4,Material.STONE_BRICKS));
                changes.add(new BlockChange(sx+side,cy+y,sz+4,Material.STONE_BRICKS));
            }
            for(int x=-4;x<=4;x++) {
                changes.add(new BlockChange(sx+x,cy+7,sz-4,Material.DEEPSLATE_BRICKS));
                changes.add(new BlockChange(sx+x,cy+7,sz+4,Material.DEEPSLATE_BRICKS));
            }
            for(int z=-4;z<=4;z++) {
                changes.add(new BlockChange(sx-4,cy+7,sz+z,Material.DEEPSLATE_BRICKS));
                changes.add(new BlockChange(sx+4,cy+7,sz+z,Material.DEEPSLATE_BRICKS));
            }
            changes.add(new BlockChange(sx,cy+1,sz,service.coreMaterial()));
            changes.add(new BlockChange(sx,cy+2,sz,Material.SEA_LANTERN));
        }
    }

    private synchronized void finishSuccessfulBuild(CommandSender sender, Location center, File backupFile) {
        try {
            closeActiveBackup();
            center.getWorld().setSpawnLocation(center.getBlockX(), center.getBlockY(), center.getBlockZ());

            plugin.getConfig().set("spawn.generated", true);
            plugin.getConfig().set("spawn.build-incomplete", false);
            plugin.getConfig().set("spawn.design-version", 3);
            plugin.getConfig().set("spawn.last-backup", backupFile.getAbsolutePath());
            plugin.saveConfig();

            sender.sendMessage(ChatColor.GREEN + "Massive ESN SMP spawn build complete.");
            sender.sendMessage(ChatColor.GRAY + "Rollback backup: " + backupFile.getName());
            plugin.getLogger().info("Large ESN spawn build complete at " + format(center));
        } catch (Exception ex) {
            plugin.getLogger().severe("Could not finalize ESN spawn build: " + ex.getMessage());
            sender.sendMessage(ChatColor.RED + "Build completed but finalization failed. Keep the rollback backup.");
        } finally {
            if (buildTask != null) {
                buildTask.cancel();
                buildTask = null;
            }
            building = false;
        }
    }

    private synchronized void failBuild(CommandSender sender, Exception ex) {
        try {
            closeActiveBackup();
        } catch (IOException closeError) {
            plugin.getLogger().severe("Could not close spawn backup after build failure: " + closeError.getMessage());
        }

        if (buildTask != null) {
            buildTask.cancel();
            buildTask = null;
        }
        building = false;

        sender.sendMessage(ChatColor.RED + "Spawn build stopped safely. Use /esnspawn rollback before trying again.");
        plugin.getLogger().severe("Spawn build failed: " + ex.getMessage());
        ex.printStackTrace();
    }

    public synchronized void rollback(CommandSender sender) {
        if (building) {
            sender.sendMessage(ChatColor.RED + "Cannot roll back while a spawn build is in progress.");
            return;
        }
        rollbackNow(sender);
    }

    private boolean rollbackNow(CommandSender sender) {
        String path = plugin.getConfig().getString("spawn.last-backup");
        if (path == null || path.isBlank()) {
            sender.sendMessage(ChatColor.RED + "No ESN spawn backup is recorded.");
            return false;
        }

        File file = new File(path);
        if (!file.isFile()) {
            sender.sendMessage(ChatColor.RED + "The recorded backup file no longer exists.");
            return false;
        }

        building = true;
        int restored = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(Files.newInputStream(file.toPath())), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null || !header.startsWith("#world=")) {
                throw new IOException("Backup header is invalid.");
            }

            String worldName = header.substring("#world=".length());
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                throw new IOException("Backup world is not loaded: " + worldName);
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                String[] parts = line.split("\\|", 5);
                if (parts.length != 5) {
                    throw new IOException("Malformed backup record.");
                }

                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[2]);
                Material material = Material.matchMaterial(parts[3]);
                if (material == null) {
                    throw new IOException("Unknown material in backup: " + parts[3]);
                }

                Block block = world.getBlockAt(x, y, z);
                block.setType(material, false);
                BlockData data = Bukkit.createBlockData(parts[4]);
                block.setBlockData(data, false);
                restored++;
            }

            restorePreviousWorldSpawn(world);
            plugin.getConfig().set("spawn.generated", false);
            plugin.getConfig().set("spawn.build-incomplete", false);
            plugin.saveConfig();

            sender.sendMessage(ChatColor.GREEN + "ESN spawn rollback complete. Restored " + restored + " blocks.");
            return true;
        } catch (Exception ex) {
            sender.sendMessage(ChatColor.RED + "Rollback stopped: " + ex.getMessage());
            plugin.getLogger().severe("Spawn rollback failed: " + ex.getMessage());
            ex.printStackTrace();
            return false;
        } finally {
            building = false;
        }
    }

    private void restorePreviousWorldSpawn(World world) {
        FileConfiguration cfg = plugin.getConfig();
        String previousWorld = cfg.getString("spawn.previous-world-spawn.world");
        if (previousWorld == null || !previousWorld.equals(world.getName())) {
            return;
        }

        int x = (int) Math.floor(cfg.getDouble("spawn.previous-world-spawn.x"));
        int y = (int) Math.floor(cfg.getDouble("spawn.previous-world-spawn.y"));
        int z = (int) Math.floor(cfg.getDouble("spawn.previous-world-spawn.z"));
        world.setSpawnLocation(x, y, z);
    }

    public String status() {
        Location spawn = getSpawn();
        return "configured=" + plugin.getConfig().getBoolean("spawn.configured", false)
                + ", generated=" + plugin.getConfig().getBoolean("spawn.generated", false)
                + ", incomplete=" + plugin.getConfig().getBoolean("spawn.build-incomplete", false)
                + ", design=v" + plugin.getConfig().getInt("spawn.design-version", 1)
                + ", building=" + building
                + ", size=" + (HUB_RADIUS * 2 + 1) + "x" + (HUB_RADIUS * 2 + 1)
                + ", location=" + (spawn == null ? "unavailable" : format(spawn));
    }

    public boolean isBuilding() {
        return building;
    }

    public synchronized void shutdown() {
        if (buildTask != null) {
            buildTask.cancel();
            buildTask = null;
        }

        try {
            closeActiveBackup();
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not close active spawn backup during shutdown: " + ex.getMessage());
        }

        building = false;
    }

    private void closeActiveBackup() throws IOException {
        if (activeBackup != null) {
            activeBackup.close();
            activeBackup = null;
        }
    }

    private File createBackupFile() {
        File dir = new File(plugin.getDataFolder(), "backups");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Could not create backup directory: " + dir);
        }
        return new File(dir, "spawn-backup-" + Instant.now().toEpochMilli() + ".txt.gz");
    }

    private static String format(Location location) {
        return location.getWorld().getName() + " "
                + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    private record BlockChange(int x, int y, int z, Material material) {
    }

    private static final class BackupWriter implements AutoCloseable {
        private final BufferedWriter writer;
        private final Set<String> recorded = new HashSet<>();
        private int pending;

        private BackupWriter(File file, World world) {
            try {
                this.writer = new BufferedWriter(new OutputStreamWriter(
                        new GZIPOutputStream(Files.newOutputStream(file.toPath())), StandardCharsets.UTF_8));
                writer.write("#world=" + world.getName());
                writer.newLine();
                writer.flush();
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        private void set(Block block, Material material) throws IOException {
            if (block.getType() == material) {
                return;
            }

            String key = block.getX() + "," + block.getY() + "," + block.getZ();
            if (recorded.add(key)) {
                writer.write(block.getX() + "|" + block.getY() + "|" + block.getZ()
                        + "|" + block.getType().name() + "|" + block.getBlockData().getAsString());
                writer.newLine();
                pending++;

                if (pending >= 128) {
                    writer.flush();
                    pending = 0;
                }
            }

            block.setType(material, false);
        }

        @Override
        public void close() throws IOException {
            writer.flush();
            writer.close();
        }
    }
}
