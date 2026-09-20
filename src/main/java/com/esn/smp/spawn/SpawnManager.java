package com.esn.smp.spawn;

import com.esn.smp.ESNSMPPlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class SpawnManager {
    private static final int HUB_RADIUS = 28;
    private static final int CLEAR_HEIGHT = 9;

    private final ESNSMPPlugin plugin;
    private boolean building;

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
        double radius = plugin.getConfig().getDouble("spawn.protection-radius", 36.0);
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

        building = true;
        World world = center.getWorld();
        Location previousWorldSpawn = world.getSpawnLocation();
        File backupFile = createBackupFile();

        plugin.getConfig().set("spawn.previous-world-spawn.world", previousWorldSpawn.getWorld().getName());
        plugin.getConfig().set("spawn.previous-world-spawn.x", previousWorldSpawn.getX());
        plugin.getConfig().set("spawn.previous-world-spawn.y", previousWorldSpawn.getY());
        plugin.getConfig().set("spawn.previous-world-spawn.z", previousWorldSpawn.getZ());
        plugin.saveConfig();

        sender.sendMessage(ChatColor.GOLD + "Building ESN SMP spawn with rollback backup...");

        try (BackupWriter backup = new BackupWriter(backupFile, world)) {
            buildHub(center, backup);
            buildLogo(center, backup);
            buildLighting(center, backup);

            world.setSpawnLocation(center.getBlockX(), center.getBlockY(), center.getBlockZ());
            plugin.getConfig().set("spawn.generated", true);
            plugin.getConfig().set("spawn.last-backup", backupFile.getAbsolutePath());
            plugin.saveConfig();

            sender.sendMessage(ChatColor.GREEN + "ESN SMP spawn build complete.");
            sender.sendMessage(ChatColor.GRAY + "Backup: " + backupFile.getName());
            plugin.getLogger().info("ESN spawn build complete at " + format(center));
        } catch (Exception ex) {
            sender.sendMessage(ChatColor.RED + "Spawn build stopped safely. A rollback backup was preserved.");
            plugin.getLogger().severe("Spawn build failed: " + ex.getMessage());
            ex.printStackTrace();
        } finally {
            building = false;
        }
    }

    private void buildHub(Location center, BackupWriter backup) throws IOException {
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        for (int dx = -HUB_RADIUS; dx <= HUB_RADIUS; dx++) {
            for (int dz = -HUB_RADIUS; dz <= HUB_RADIUS; dz++) {
                double distance = Math.sqrt((dx * dx) + (dz * dz));
                if (distance > HUB_RADIUS) {
                    continue;
                }

                for (int dy = 0; dy < CLEAR_HEIGHT; dy++) {
                    backup.set(world.getBlockAt(cx + dx, cy + dy, cz + dz), Material.AIR);
                }

                Material floor;
                if (distance >= HUB_RADIUS - 2) {
                    floor = Material.STONE_BRICKS;
                } else if (Math.abs(dx) <= 2 || Math.abs(dz) <= 2) {
                    floor = Material.SMOOTH_STONE;
                } else if (((cx + dx) + (cz + dz)) % 7 == 0) {
                    floor = Material.MOSSY_STONE_BRICKS;
                } else {
                    floor = Material.POLISHED_ANDESITE;
                }
                backup.set(world.getBlockAt(cx + dx, cy - 1, cz + dz), floor);
            }
        }

        // Raised center medallion.
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                if ((dx * dx) + (dz * dz) <= 36) {
                    Material material = ((dx + dz) & 1) == 0 ? Material.DEEPSLATE_TILES : Material.POLISHED_DEEPSLATE;
                    backup.set(world.getBlockAt(cx + dx, cy, cz + dz), material);
                }
            }
        }

        // Golden ESN cross accent embedded into the medallion.
        for (int i = -4; i <= 4; i++) {
            backup.set(world.getBlockAt(cx + i, cy, cz), Material.YELLOW_TERRACOTTA);
            backup.set(world.getBlockAt(cx, cy, cz + i), Material.YELLOW_TERRACOTTA);
        }

        // Make the exact teleport point safe and open.
        backup.set(world.getBlockAt(cx, cy, cz), Material.SMOOTH_STONE);
        backup.set(world.getBlockAt(cx, cy + 1, cz), Material.AIR);
        backup.set(world.getBlockAt(cx, cy + 2, cz), Material.AIR);
    }

    private void buildLogo(Location center, BackupWriter backup) throws IOException {
        World world = center.getWorld();
        int cx = center.getBlockX();
        int baseY = center.getBlockY() + 1;
        int planeZ = center.getBlockZ() - 21;
        int logoCenterY = baseY + 12;

        // Circular stone frame with a dark disk, echoing the supplied ESN SMP logo.
        for (int x = -14; x <= 14; x++) {
            for (int y = -14; y <= 14; y++) {
                double d = Math.sqrt((x * x) + (y * y));
                Block block = world.getBlockAt(cx + x, logoCenterY + y, planeZ);
                if (d <= 12.3) {
                    backup.set(block, Material.BLACK_CONCRETE);
                } else if (d <= 14.0) {
                    backup.set(block, ((x + y) & 1) == 0 ? Material.STONE_BRICKS : Material.MOSSY_STONE_BRICKS);
                }
            }
        }

        // Warm sunset glow behind the lettering.
        for (int x = -9; x <= 9; x++) {
            for (int y = 5; y <= 9; y++) {
                if (x * x + y * y <= 110) {
                    backup.set(world.getBlockAt(cx + x, logoCenterY + y, planeZ + 1),
                            y >= 8 ? Material.ORANGE_CONCRETE : Material.YELLOW_TERRACOTTA);
                }
            }
        }

        String[][] top = {
                {"11111","10000","11110","10000","11111"}, // E
                {"11111","10000","11111","00001","11111"}, // S
                {"10001","11001","10101","10011","10001"}  // N
        };
        String[][] bottom = {
                {"11111","10000","11111","00001","11111"}, // S
                {"10001","11011","10101","10001","10001"}, // M
                {"11110","10001","11110","10000","10000"}  // P
        };

        drawWord(world, backup, cx - 9, logoCenterY + 5, planeZ + 2, top, Material.YELLOW_TERRACOTTA);
        drawWord(world, backup, cx - 9, logoCenterY - 2, planeZ + 2, bottom, Material.LIGHT_GRAY_CONCRETE);

        // Stone supports under the monument.
        for (int x : new int[]{-11, 11}) {
            for (int y = baseY; y <= logoCenterY - 10; y++) {
                backup.set(world.getBlockAt(cx + x, y, planeZ), Material.STONE_BRICKS);
            }
        }
    }

    private void drawWord(World world, BackupWriter backup, int startX, int topY, int z,
                          String[][] letters, Material material) throws IOException {
        int cursor = startX;
        for (String[] letter : letters) {
            for (int row = 0; row < letter.length; row++) {
                for (int col = 0; col < letter[row].length(); col++) {
                    if (letter[row].charAt(col) == '1') {
                        backup.set(world.getBlockAt(cursor + col, topY - row, z), material);
                    }
                }
            }
            cursor += 7;
        }
    }

    private void buildLighting(Location center, BackupWriter backup) throws IOException {
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        int[][] positions = {{15,15},{15,-15},{-15,15},{-15,-15}};
        for (int[] p : positions) {
            for (int y = 0; y < 4; y++) {
                backup.set(world.getBlockAt(cx + p[0], cy + y, cz + p[1]), Material.POLISHED_BLACKSTONE_BRICK_WALL);
            }
            backup.set(world.getBlockAt(cx + p[0], cy + 4, cz + p[1]), Material.LANTERN);
        }

        // Four beacon-like corner markers at the cardinal paths.
        int[][] cardinals = {{0,24},{0,-24},{24,0},{-24,0}};
        for (int[] p : cardinals) {
            backup.set(world.getBlockAt(cx + p[0], cy, cz + p[1]), Material.CHISELED_STONE_BRICKS);
            backup.set(world.getBlockAt(cx + p[0], cy + 1, cz + p[1]), Material.SEA_LANTERN);
        }
    }

    public synchronized void rollback(CommandSender sender) {
        if (building) {
            sender.sendMessage(ChatColor.RED + "Cannot roll back while a spawn build is in progress.");
            return;
        }

        String path = plugin.getConfig().getString("spawn.last-backup");
        if (path == null || path.isBlank()) {
            sender.sendMessage(ChatColor.RED + "No ESN spawn backup is recorded.");
            return;
        }

        File file = new File(path);
        if (!file.isFile()) {
            sender.sendMessage(ChatColor.RED + "The recorded backup file no longer exists.");
            return;
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
                if (line.isBlank()) continue;
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
            plugin.saveConfig();
            sender.sendMessage(ChatColor.GREEN + "ESN spawn rollback complete. Restored " + restored + " blocks.");
        } catch (Exception ex) {
            sender.sendMessage(ChatColor.RED + "Rollback stopped: " + ex.getMessage());
            plugin.getLogger().severe("Spawn rollback failed: " + ex.getMessage());
            ex.printStackTrace();
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
                + ", building=" + building
                + ", location=" + (spawn == null ? "unavailable" : format(spawn));
    }

    public boolean isBuilding() {
        return building;
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
                if (pending >= 32) {
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
