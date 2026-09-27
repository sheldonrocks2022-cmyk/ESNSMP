package com.esn.smp.gameplay;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

public final class JailSystem implements Listener, CommandExecutor {
    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration db;
    private final String title = ChatColor.DARK_RED + "ESN Prison • Select Player";
    private final Map<UUID, String> selectedSentence = new HashMap<>();

    // Hot-path jail state is cached in memory. YAML remains the persistent source
    // of truth across restarts, but movement/command events never hit it repeatedly.
    private final Set<UUID> jailedPlayers = new HashSet<>();
    private final Map<UUID, Integer> jailCells = new HashMap<>();
    private final Map<UUID, Long> jailExpires = new HashMap<>();

    public JailSystem(JavaPlugin plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "jails.yml");
        db = YamlConfiguration.loadConfiguration(file);
        for (Player player : Bukkit.getOnlinePlayers()) hydrate(player);
        Bukkit.getScheduler().runTaskLater(plugin, this::finishPrison, 60L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::expire, 20L, 20L);
    }

    private void hydrate(Player player) {
        UUID id = player.getUniqueId();
        String key = id.toString();
        if (db.getBoolean("jailed." + key, false)) {
            jailedPlayers.add(id);
            jailCells.put(id, db.getInt("cell." + key, 0));
            jailExpires.put(id, db.getLong("expires." + key, 0L));
        } else {
            jailedPlayers.remove(id);
            jailCells.remove(id);
            jailExpires.remove(id);
        }
    }

    private void save() {
        try {
            db.save(file);
        } catch (Exception e) {
            plugin.getLogger().severe("Jail data save failed: " + e.getMessage());
        }
    }

    private boolean staff(Player player) {
        return player.hasPermission("esnsmp.staff") || player.hasPermission("esnsmp.owner");
    }

    private boolean jailed(Player player) {
        return jailedPlayers.contains(player.getUniqueId());
    }

    private Location cell(int n) {
        Location spawn = ((com.esn.smp.ESNSMPPlugin) plugin).getSpawnManager().getSpawn();
        if (spawn == null) return Bukkit.getWorlds().get(0).getSpawnLocation();
        int bx = 137 + (n % 4) * 12;
        int bz = 125 + (n / 4) * 17;
        return spawn.clone().add(bx, 2, bz);
    }

    private long duration(String value) {
        if (value == null) return 30 * 60000L;
        try {
            char unit = Character.toLowerCase(value.charAt(value.length() - 1));
            long n = Long.parseLong(Character.isDigit(unit) ? value : value.substring(0, value.length() - 1));
            if (Character.isDigit(unit)) return n * 60000L;
            return n * (unit == 's' ? 1000L : unit == 'm' ? 60000L : unit == 'h' ? 3600000L : unit == 'd' ? 86400000L : -1);
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!staff(player)) {
            player.sendMessage(ChatColor.RED + "Staff only.");
            return true;
        }
        if (args.length == 0) {
            open(player);
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Player must be online.");
            return true;
        }
        if (args.length > 1 && args[1].equalsIgnoreCase("release")) {
            release(target);
            player.sendMessage(ChatColor.GREEN + "Released " + target.getName());
            return true;
        }

        int cell = 0;
        String time = "30m";
        if (args.length > 1) {
            try {
                cell = Math.max(0, Math.min(11, Integer.parseInt(args[1]) - 1));
            } catch (Exception ignored) {
                time = args[1];
            }
        }
        if (args.length > 2) time = args[2];

        long ms = duration(time);
        if (ms < 1000) {
            player.sendMessage(ChatColor.RED + "Invalid time. Examples: 30m, 2h, 1d.");
            return true;
        }

        jail(target, cell, ms);
        player.sendMessage(ChatColor.RED + "Jailed " + target.getName() + " in cell " + (cell + 1) + " for " + time + ".");
        return true;
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    private void open(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 54, title);
        int slot = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            ItemStack head = item(Material.PLAYER_HEAD, ChatColor.RED + target.getName(),
                    ChatColor.GRAY + "Click: cell selection", ChatColor.YELLOW + "GUI sentence: 30 minutes");
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(target);
            head.setItemMeta(meta);
            inventory.setItem(slot++, head);
            if (slot >= 45) break;
        }
        player.openInventory(inventory);
    }

    private void cells(Player player, Player target) {
        Inventory inventory = Bukkit.createInventory(null, 36, ChatColor.DARK_RED + "Cell for " + target.getName());
        String time = selectedSentence.getOrDefault(player.getUniqueId(), "30m");
        for (int i = 0; i < 12; i++) {
            inventory.setItem(i, item(Material.IRON_BARS, ChatColor.RED + "Cell " + (i + 1), ChatColor.GRAY + "Sentence: " + time));
        }
        inventory.setItem(18, item(Material.CLOCK, ChatColor.YELLOW + "15 Minutes"));
        inventory.setItem(19, item(Material.CLOCK, ChatColor.YELLOW + "30 Minutes"));
        inventory.setItem(20, item(Material.CLOCK, ChatColor.GOLD + "1 Hour"));
        inventory.setItem(21, item(Material.CLOCK, ChatColor.GOLD + "2 Hours"));
        inventory.setItem(22, item(Material.CLOCK, ChatColor.RED + "1 Day"));
        inventory.setItem(31, item(Material.LIME_CONCRETE, ChatColor.GREEN + "Release", ChatColor.GRAY + "Release " + target.getName()));
        player.openInventory(inventory);
    }

    private void jail(Player target, int cell, long ms) {
        UUID id = target.getUniqueId();
        String key = id.toString();
        if (!jailed(target)) {
            db.set("inventory." + key + ".storage", Arrays.asList(target.getInventory().getStorageContents()));
            db.set("inventory." + key + ".armor", Arrays.asList(target.getInventory().getArmorContents()));
            db.set("inventory." + key + ".offhand", target.getInventory().getItemInOffHand());
        }

        target.getInventory().clear();
        target.getInventory().setArmorContents(new ItemStack[4]);
        target.getInventory().setItemInOffHand(null);

        long end = System.currentTimeMillis() + ms;
        jailedPlayers.add(id);
        jailCells.put(id, cell);
        jailExpires.put(id, end);

        db.set("jailed." + key, true);
        db.set("cell." + key, cell);
        db.set("expires." + key, end);
        save();

        target.teleport(cell(cell));
        target.sendMessage(ChatColor.DARK_RED + "You have been jailed for " + format(ms) + ". Your inventory was secured.");
    }

    private String format(long ms) {
        long seconds = Math.max(0, ms / 1000);
        long days = seconds / 86400;
        seconds %= 86400;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;
        return (days > 0 ? days + "d " : "")
                + (hours > 0 ? hours + "h " : "")
                + (minutes > 0 ? minutes + "m " : "")
                + (seconds > 0 ? seconds + "s" : "");
    }

    private void release(Player target) {
        UUID uuid = target.getUniqueId();
        String id = uuid.toString();
        String base = "inventory." + id;

        target.getInventory().clear();

        List<?> storage = db.getList(base + ".storage");
        if (storage != null) {
            ItemStack[] items = new ItemStack[target.getInventory().getStorageContents().length];
            for (int i = 0; i < items.length && i < storage.size(); i++) {
                if (storage.get(i) instanceof ItemStack stack) items[i] = stack;
            }
            target.getInventory().setStorageContents(items);
        }

        List<?> armor = db.getList(base + ".armor");
        if (armor != null) {
            ItemStack[] items = new ItemStack[4];
            for (int i = 0; i < items.length && i < armor.size(); i++) {
                if (armor.get(i) instanceof ItemStack stack) items[i] = stack;
            }
            target.getInventory().setArmorContents(items);
        }

        ItemStack offhand = db.getItemStack(base + ".offhand");
        if (offhand != null) target.getInventory().setItemInOffHand(offhand);

        jailedPlayers.remove(uuid);
        jailCells.remove(uuid);
        jailExpires.remove(uuid);

        db.set(base, null);
        db.set("jailed." + id, false);
        db.set("expires." + id, null);
        save();

        Location spawn = ((com.esn.smp.ESNSMPPlugin) plugin).getSpawnManager().getSpawn();
        if (spawn != null) target.teleport(spawn);
        target.sendMessage(ChatColor.GREEN + "Your jail sentence is complete.");
    }

    private void expire() {
        if (jailExpires.isEmpty()) return;
        long now = System.currentTimeMillis();
        List<Player> due = null;
        for (Map.Entry<UUID, Long> entry : jailExpires.entrySet()) {
            long end = entry.getValue();
            if (end <= 0 || now < end) continue;
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) continue;
            if (due == null) due = new ArrayList<>(2);
            due.add(player);
        }
        if (due != null) for (Player player : due) release(player);
    }

    private void finishPrison() {
        for (int n = 0; n < 12; n++) {
            Location center = cell(n);
            World world = center.getWorld();
            if (world == null) continue;
            int bx = center.getBlockX(), by = center.getBlockY() - 1, bz = center.getBlockZ();
            for (int dx = -4; dx <= 4; dx++) {
                for (int dy = 0; dy <= 5; dy++) {
                    for (int dz = -5; dz <= 5; dz++) {
                        boolean wall = Math.abs(dx) == 4 || dy == 0 || dy == 5 || Math.abs(dz) == 5;
                        Material material = wall ? Material.DEEPSLATE_BRICKS : Material.AIR;
                        if (dz == -5 && Math.abs(dx) <= 1 && dy >= 1 && dy <= 3) material = Material.IRON_BARS;
                        world.getBlockAt(bx + dx, by + dy, bz + dz).setType(material, false);
                    }
                }
            }
            world.getBlockAt(bx - 2, by + 1, bz + 2).setType(Material.RED_BED, false);
            world.getBlockAt(bx + 2, by + 4, bz).setType(Material.SEA_LANTERN, false);
            world.getBlockAt(bx, by + 1, bz - 5).setType(Material.IRON_DOOR, false);
        }
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        String viewTitle = event.getView().getTitle();
        if (!viewTitle.equals(title) && !viewTitle.startsWith(ChatColor.DARK_RED + "Cell for ")) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getCurrentItem() == null) return;
        if (!staff(player)) {
            player.closeInventory();
            return;
        }

        if (viewTitle.equals(title)) {
            if (event.getCurrentItem().getItemMeta() instanceof SkullMeta meta && meta.getOwningPlayer() != null) {
                Player target = Bukkit.getPlayer(meta.getOwningPlayer().getUniqueId());
                if (target != null) cells(player, target);
            }
            return;
        }

        String name = ChatColor.stripColor(viewTitle).substring("Cell for ".length());
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) return;

        if (event.getSlot() == 31) {
            release(target);
            player.closeInventory();
        } else if (event.getSlot() >= 18 && event.getSlot() <= 22) {
            String[] times = {"15m", "30m", "1h", "2h", "1d"};
            selectedSentence.put(player.getUniqueId(), times[event.getSlot() - 18]);
            cells(player, target);
        } else if (event.getSlot() < 12) {
            String time = selectedSentence.getOrDefault(player.getUniqueId(), "30m");
            jail(target, event.getSlot(), duration(time));
            selectedSentence.remove(player.getUniqueId());
            player.closeInventory();
        }
    }

    @EventHandler
    public void move(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (!jailedPlayers.contains(id) || event.getTo() == null) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        Location center = cell(jailCells.getOrDefault(id, 0));
        Location to = event.getTo();
        if (to.getWorld() != center.getWorld()
                || Math.abs(to.getX() - center.getX()) > 3
                || Math.abs(to.getZ() - center.getZ()) > 4
                || Math.abs(to.getY() - center.getY()) > 4) {
            player.teleport(center);
        }
    }

    @EventHandler
    public void command(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (!jailedPlayers.contains(id) || player.hasPermission("esnsmp.owner")) return;
        event.setCancelled(true);
        long end = jailExpires.getOrDefault(id, 0L);
        event.getPlayer().sendMessage(ChatColor.RED + "Commands disabled while jailed. Remaining: " + format(end - System.currentTimeMillis()));
    }

    @EventHandler
    public void breakBlock(BlockBreakEvent event) {
        if (jailed(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler
    public void placeBlock(BlockPlaceEvent event) {
        if (jailed(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        hydrate(player);
        if (!jailed(player)) return;

        UUID id = player.getUniqueId();
        long end = jailExpires.getOrDefault(id, 0L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (end > 0 && System.currentTimeMillis() >= end) {
                release(player);
            } else {
                player.teleport(cell(jailCells.getOrDefault(id, 0)));
                player.sendMessage(ChatColor.RED + "Jail time remaining: " + format(end - System.currentTimeMillis()));
            }
        }, 2L);
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        selectedSentence.remove(id);
        jailedPlayers.remove(id);
        jailCells.remove(id);
        jailExpires.remove(id);
    }
}
