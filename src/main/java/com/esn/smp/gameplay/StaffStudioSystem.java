package com.esn.smp.gameplay;

import com.esn.smp.data.ESNDataStore;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("deprecation")
public final class StaffStudioSystem implements Listener, CommandExecutor, AutoCloseable {
    private static final String HUB = ChatColor.DARK_RED + "ESN Staff & Studio";
    private static final String INSPECT = ChatColor.DARK_AQUA + "Inspect • ";
    private static final String PUNISH = ChatColor.DARK_RED + "Punish • ";
    private static final String REALM_STAFF = ChatColor.DARK_PURPLE + "Realm Staff Controls";
    private static final String REALM_ACTION = ChatColor.DARK_PURPLE + "Realm Control • ";
    private static final String EMERGENCY = ChatColor.DARK_RED + "ESN Emergency Controls";
    private static final String STUDIO = ChatColor.DARK_AQUA + "ESN Studios Team";

    private final JavaPlugin plugin;
    private final ESNDataStore economy;
    private final File staffFile;
    private final YamlConfiguration staffData;
    private Connection db;

    private final Map<UUID, PermissionAttachment> rankAttachments = new HashMap<>();
    private final Set<UUID> staffMode = new HashSet<>();
    private final Set<UUID> buildMode = new HashSet<>();
    private final Set<UUID> vanished = new HashSet<>();
    private final Map<UUID, PlayerSnapshot> snapshots = new HashMap<>();
    private final Map<UUID, UUID> selected = new HashMap<>();
    private final Map<UUID, String> punishReasons = new HashMap<>();
    private final Map<UUID, EvidenceSession> evidence = new ConcurrentHashMap<>();
    private final Map<UUID, Long> mutedUntil = new ConcurrentHashMap<>();
    private final Map<UUID, String> mutedReason = new ConcurrentHashMap<>();
    private final Set<UUID> auditSuppressed = new HashSet<>();
    private final Map<String, Deque<Long>> reports = new HashMap<>();
    private final Map<String, UUID> realmBosses = new HashMap<>();
    private final Map<UUID, String> selectedStaffRealm = new HashMap<>();
    private final Map<UUID, PendingConfirmation> pending = new HashMap<>();

    public StaffStudioSystem(JavaPlugin plugin, ESNDataStore economy) throws Exception {
        this.plugin = plugin;
        this.economy = economy;
        this.staffFile = new File(plugin.getDataFolder(), "staff-studio.yml");
        this.staffData = YamlConfiguration.loadConfiguration(staffFile);
        Class.forName("org.sqlite.JDBC");
        this.db = DriverManager.getConnection("jdbc:sqlite:" + new File(plugin.getDataFolder(), "esn-staff.db"));
        try (Statement s = db.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS staff_cases(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "target_uuid TEXT,target_name TEXT,staff_uuid TEXT,staff_name TEXT," +
                    "action TEXT,reason TEXT,status TEXT NOT NULL DEFAULT 'OPEN'," +
                    "created INTEGER NOT NULL,updated INTEGER NOT NULL,evidence TEXT NOT NULL DEFAULT '')");
            s.execute("CREATE TABLE IF NOT EXISTS duty(" +
                    "uuid TEXT PRIMARY KEY,name TEXT,total_millis INTEGER NOT NULL DEFAULT 0," +
                    "session_start INTEGER NOT NULL DEFAULT 0,reports INTEGER NOT NULL DEFAULT 0," +
                    "cases INTEGER NOT NULL DEFAULT 0,assists INTEGER NOT NULL DEFAULT 0,punishments INTEGER NOT NULL DEFAULT 0)");
            s.execute("CREATE TABLE IF NOT EXISTS staff_punishments(" +
                    "target_uuid TEXT PRIMARY KEY,muted_until INTEGER NOT NULL DEFAULT 0," +
                    "muted_reason TEXT NOT NULL DEFAULT '',updated INTEGER NOT NULL DEFAULT 0)");
        }
        loadMutes();
    }

    public void start() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyPermissions(player);
            applyVisualTag(player);
            if (staffData.getBoolean(path(player.getUniqueId(), "vanished"), false)) {
                vanished.add(player.getUniqueId());
                Bukkit.getScheduler().runTask(plugin, () -> applyVanish(player, true));
            }
        }
        plugin.getLogger().info("[ESN Staff] Staff & Studio system online.");
    }

    @Override
    public void close() throws Exception {
        for (UUID id : new HashSet<>(staffMode)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) disableMode(p, false);
        }
        for (UUID id : new HashSet<>(buildMode)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) disableMode(p, true);
        }
        long now = System.currentTimeMillis();
        try (PreparedStatement q = db.prepareStatement(
                "UPDATE duty SET total_millis=total_millis+(?-session_start),session_start=0 WHERE session_start>0")) {
            q.setLong(1, now);
            q.executeUpdate();
        }
        saveStaffData();
        if (db != null) db.close();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        try {
            return switch (name) {
                case "staffhub" -> staffHubCommand(sender, args);
                case "staffmode" -> modeCommand(sender, false);
                case "buildmode" -> modeCommand(sender, true);
                case "vanish" -> vanishCommand(sender);
                case "inspect" -> inspectCommand(sender, args);
                case "punish" -> punishCommand(sender, args);
                case "case" -> caseCommand(sender, args);
                case "cases" -> casesCommand(sender, args);
                case "evidence" -> evidenceCommand(sender, args);
                case "duty" -> dutyCommand(sender, args);
                case "staffstats" -> staffStatsCommand(sender, args);
                case "realmstaff" -> realmStaffCommand(sender, args);
                case "staffrank" -> staffRankCommand(sender, args);
                case "studio" -> studioCommand(sender, args);
                case "emergency" -> emergencyCommand(sender, args);
                case "stafflist" -> staffListCommand(sender);
                default -> true;
            };
        } catch (Exception ex) {
            sender.sendMessage(ChatColor.RED + "ESN staff system action failed safely.");
            plugin.getLogger().severe("[ESN Staff] " + name + ": " + ex.getMessage());
            return true;
        }
    }

    private boolean staffHubCommand(CommandSender sender, String[] args) {
        Player p = player(sender);
        if (p == null) return true;
        if (!isStaff(p)) return noPermission(p);
        if (args.length > 0) {
            Player target = Bukkit.getPlayer(args[0]);
            if (target != null) {
                openInspect(p, target);
                return true;
            }
        }
        openHub(p);
        return true;
    }

    private boolean modeCommand(CommandSender sender, boolean builder) {
        Player p = player(sender);
        if (p == null) return true;
        if (builder) {
            if (!canBuildMode(p)) return noPermission(p);
            if (buildMode.contains(p.getUniqueId())) disableMode(p, true);
            else enableBuildMode(p);
        } else {
            if (!isStaff(p)) return noPermission(p);
            if (staffMode.contains(p.getUniqueId())) disableMode(p, false);
            else enableStaffMode(p);
        }
        return true;
    }

    private boolean vanishCommand(CommandSender sender) {
        Player p = player(sender);
        if (p == null) return true;
        if (rank(p).level < StaffRank.MODERATOR.level) return noPermission(p);
        toggleVanish(p);
        return true;
    }

    private boolean inspectCommand(CommandSender sender, String[] args) {
        Player p = player(sender);
        if (p == null) return true;
        if (rank(p).level < StaffRank.HELPER.level) return noPermission(p);
        if (args.length == 0) {
            p.sendMessage(ChatColor.YELLOW + "/inspect <player>");
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            p.sendMessage(ChatColor.RED + "That player must be online.");
            return true;
        }
        openInspect(p, target);
        return true;
    }

    private boolean punishCommand(CommandSender sender, String[] args) {
        Player p = player(sender);
        if (p == null) return true;
        if (rank(p).level < StaffRank.TRIAL_MODERATOR.level) return noPermission(p);
        if (args.length == 0) {
            p.sendMessage(ChatColor.YELLOW + "/punish <player> [reason]");
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            p.sendMessage(ChatColor.RED + "That player must be online.");
            return true;
        }
        selected.put(p.getUniqueId(), target.getUniqueId());
        punishReasons.put(p.getUniqueId(), args.length > 1
                ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                : "Staff action through ESN Staff Center");
        openPunish(p, target);
        return true;
    }

    private boolean caseCommand(CommandSender sender, String[] args) throws Exception {
        if (!isStaffSender(sender)) return noPermission(sender);
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "/case <id> | /case create <player> <reason> | /case note <id> <note> | /case close <id>");
            return true;
        }
        if (args[0].equalsIgnoreCase("create")) {
            if (!(sender instanceof Player staff) || args.length < 3) {
                sender.sendMessage(ChatColor.YELLOW + "/case create <player> <reason>");
                return true;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            long id = createCase(staff, target, "INVESTIGATION",
                    String.join(" ", Arrays.copyOfRange(args, 2, args.length)), false);
            sender.sendMessage(ChatColor.GREEN + "Created Case #" + id + ".");
            return true;
        }
        if (args[0].equalsIgnoreCase("note")) {
            if (args.length < 3) {
                sender.sendMessage(ChatColor.YELLOW + "/case note <id> <note>");
                return true;
            }
            long id = Long.parseLong(args[1]);
            appendEvidence(id, "[NOTE " + sender.getName() + "] " +
                    String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
            sender.sendMessage(ChatColor.GREEN + "Added note to Case #" + id + ".");
            return true;
        }
        if (args[0].equalsIgnoreCase("close")) {
            if (args.length < 2) return true;
            long id = Long.parseLong(args[1]);
            try (PreparedStatement q = db.prepareStatement(
                    "UPDATE staff_cases SET status='CLOSED',updated=? WHERE id=?")) {
                q.setLong(1, System.currentTimeMillis());
                q.setLong(2, id);
                q.executeUpdate();
            }
            if (sender instanceof Player p) incrementDuty(p, "cases", 1);
            sender.sendMessage(ChatColor.GREEN + "Closed Case #" + id + ".");
            return true;
        }
        showCase(sender, Long.parseLong(args[0]));
        return true;
    }

    private boolean casesCommand(CommandSender sender, String[] args) throws Exception {
        if (!isStaffSender(sender)) return noPermission(sender);
        if (args.length == 0) {
            showRecentCases(sender, null);
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        showRecentCases(sender, target.getUniqueId());
        return true;
    }

    private boolean evidenceCommand(CommandSender sender, String[] args) throws Exception {
        Player staff = player(sender);
        if (staff == null) return true;
        if (rank(staff).level < StaffRank.TRIAL_MODERATOR.level) return noPermission(staff);
        if (args.length == 0) {
            staff.sendMessage(ChatColor.YELLOW + "/evidence <start player|stop|discard|status>");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("start")) {
            if (args.length < 2) {
                staff.sendMessage(ChatColor.YELLOW + "/evidence start <player>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                staff.sendMessage(ChatColor.RED + "Player must be online.");
                return true;
            }
            EvidenceSession session = new EvidenceSession(target.getUniqueId(), target.getName());
            session.lines.add("Session started at " + formatLocation(target.getLocation()));
            evidence.put(staff.getUniqueId(), session);
            staff.sendMessage(ChatColor.GREEN + "Evidence capture started for " + target.getName() + ".");
            return true;
        }
        EvidenceSession session = evidence.get(staff.getUniqueId());
        if (sub.equals("status")) {
            staff.sendMessage(session == null ? ChatColor.GRAY + "No evidence session active."
                    : ChatColor.GOLD + "Evidence: " + session.targetName + " • " + session.lines.size() + " event(s)");
            return true;
        }
        if (sub.equals("discard")) {
            evidence.remove(staff.getUniqueId());
            staff.sendMessage(ChatColor.YELLOW + "Evidence session discarded.");
            return true;
        }
        if (sub.equals("stop")) {
            if (session == null) {
                staff.sendMessage(ChatColor.RED + "No evidence session is active.");
                return true;
            }
            evidence.remove(staff.getUniqueId());
            OfflinePlayer target = Bukkit.getOfflinePlayer(session.target);
            long caseId = createCase(staff, target, "EVIDENCE",
                    "Captured evidence session", false);
            for (String line : session.lines) appendEvidence(caseId, line);
            staff.sendMessage(ChatColor.GREEN + "Evidence saved to Case #" + caseId + ".");
            return true;
        }
        return true;
    }

    private boolean dutyCommand(CommandSender sender, String[] args) throws Exception {
        Player p = player(sender);
        if (p == null) return true;
        if (!isStaff(p)) return noPermission(p);
        ensureDuty(p);
        if (args.length > 0 && args[0].equalsIgnoreCase("assist")) {
            incrementDuty(p, "assists", 1);
            p.sendMessage(ChatColor.GREEN + "Player assist recorded.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("report")) {
            incrementDuty(p, "reports", 1);
            p.sendMessage(ChatColor.GREEN + "Handled report recorded.");
            return true;
        }
        long start = dutyStart(p.getUniqueId());
        if (start > 0) {
            long now = System.currentTimeMillis();
            try (PreparedStatement q = db.prepareStatement(
                    "UPDATE duty SET total_millis=total_millis+(?-session_start),session_start=0 WHERE uuid=?")) {
                q.setLong(1, now);
                q.setString(2, p.getUniqueId().toString());
                q.executeUpdate();
            }
            p.sendMessage(ChatColor.YELLOW + "You are now OFF DUTY.");
        } else {
            try (PreparedStatement q = db.prepareStatement(
                    "UPDATE duty SET session_start=? WHERE uuid=?")) {
                q.setLong(1, System.currentTimeMillis());
                q.setString(2, p.getUniqueId().toString());
                q.executeUpdate();
            }
            p.sendMessage(ChatColor.GREEN + "You are now ON DUTY.");
        }
        return true;
    }

    private boolean staffStatsCommand(CommandSender sender, String[] args) throws Exception {
        if (!isStaffSender(sender)) return noPermission(sender);
        OfflinePlayer target;
        if (args.length == 0 && sender instanceof Player p) target = p;
        else if (args.length > 0) target = Bukkit.getOfflinePlayer(args[0]);
        else return true;
        showDutyStats(sender, target);
        return true;
    }

    private boolean realmStaffCommand(CommandSender sender, String[] args) {
        Player p = player(sender);
        if (p == null) return true;
        if (rank(p).level < StaffRank.ADMIN.level && !canBuildMode(p)) return noPermission(p);
        if (args.length == 0) {
            openRealmStaff(p);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("boss") && args.length >= 3) {
            if (rank(p).level < StaffRank.ADMIN.level) return noPermission(p);
            if (args[1].equalsIgnoreCase("start")) startRealmBoss(p, args[2]);
            else if (args[1].equalsIgnoreCase("stop")) stopRealmBoss(p, args[2]);
            return true;
        }
        if (sub.equals("reset") && args.length >= 2) {
            if (rank(p).level < StaffRank.ADMIN.level) return noPermission(p);
            resetRealmEncounter(p, args[1]);
            return true;
        }
        if (sub.equals("evacuate") && args.length >= 2) {
            if (rank(p).level < StaffRank.SENIOR_ADMIN.level) return noPermission(p);
            evacuateRealm(p, args[1]);
            return true;
        }
        if (sub.equals("buildlock") && args.length >= 3) {
            if (rank(p).level < StaffRank.ADMIN.level) return noPermission(p);
            setRealmBuildLock(p, args[1], args[2].equalsIgnoreCase("on"));
            return true;
        }
        if (sub.equals("perf") && args.length >= 2) {
            showRealmPerformance(p, args[1]);
            return true;
        }
        if (sub.equals("decorate") && args.length >= 2) {
            p.performCommand("realmadmin decorate " + args[1]);
            return true;
        }
        p.sendMessage(ChatColor.YELLOW + "/realmstaff boss <start|stop> <realm>");
        p.sendMessage(ChatColor.YELLOW + "/realmstaff <reset|evacuate|perf|decorate> <realm>");
        p.sendMessage(ChatColor.YELLOW + "/realmstaff buildlock <realm> <on|off>");
        return true;
    }

    private boolean staffRankCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player actor)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length == 0) {
            actor.sendMessage(ChatColor.GOLD + "Your staff rank: " + rank(actor).display);
            actor.sendMessage(ChatColor.GRAY + "Studio role: " + studioRole(actor.getUniqueId()).display);
            return true;
        }
        if (args.length < 3 || !args[0].equalsIgnoreCase("set")) {
            actor.sendMessage(ChatColor.YELLOW + "/staffrank set <player> <rank>");
            actor.sendMessage(ChatColor.GRAY + "Ranks: " + String.join(", ", Arrays.stream(StaffRank.values()).map(r -> r.key).toList()));
            return true;
        }
        if (rank(actor).level < StaffRank.HEAD_ADMIN.level) return noPermission(actor);
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        StaffRank desired = StaffRank.from(args[2]);
        if (desired == null) {
            actor.sendMessage(ChatColor.RED + "Unknown rank.");
            return true;
        }
        setStaffRank(actor, target, desired);
        return true;
    }

    private boolean studioCommand(CommandSender sender, String[] args) {
        Player actor = player(sender);
        if (actor == null) return true;
        if (args.length == 0) {
            openStudio(actor);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("list")) {
            showStudioList(actor);
            return true;
        }
        if (!canManageStudio(actor)) return noPermission(actor);
        if (sub.equals("role") && args.length >= 3) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            StudioRole role = StudioRole.from(args[2]);
            if (role == null) {
                actor.sendMessage(ChatColor.RED + "Unknown Studio role.");
                return true;
            }
            setStudioRole(actor, target, role);
            return true;
        }
        if (sub.equals("realm") && args.length >= 4) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            String action = args[2].toLowerCase(Locale.ROOT);
            String realm = normalizeRealm(args[3]);
            if (realm == null && !args[3].equalsIgnoreCase("all")) {
                actor.sendMessage(ChatColor.RED + "Unknown realm.");
                return true;
            }
            setRealmAssignment(actor, target, action, realm == null ? "all" : realm);
            return true;
        }
        actor.sendMessage(ChatColor.YELLOW + "/studio role <player> <role>");
        actor.sendMessage(ChatColor.YELLOW + "/studio realm <player> <add|remove> <realm|all>");
        actor.sendMessage(ChatColor.YELLOW + "/studio list");
        return true;
    }

    private boolean emergencyCommand(CommandSender sender, String[] args) {
        Player p = player(sender);
        if (p == null) return true;
        if (rank(p).level < StaffRank.SENIOR_ADMIN.level) return noPermission(p);
        if (args.length == 0) {
            openEmergency(p);
            return true;
        }
        String key = args[0].toLowerCase(Locale.ROOT);
        if (key.equals("spawnall")) {
            requireConfirmation(p, "spawnall", () -> sendEveryoneSpawn(p));
            return true;
        }
        if (key.equals("maintenance")) {
            requireConfirmation(p, "maintenance", () -> toggleEmergency(p, "maintenance"));
            return true;
        }
        if (Set.of("chat", "realms", "pvp", "trades", "economy", "events").contains(key)) {
            toggleEmergency(p, key);
            return true;
        }
        p.sendMessage(ChatColor.YELLOW + "/emergency <chat|realms|pvp|trades|economy|events|maintenance|spawnall>");
        return true;
    }

    private boolean staffListCommand(CommandSender sender) {
        sender.sendMessage(ChatColor.DARK_RED + "=== ESN STAFF ===");
        boolean any = false;
        for (Player p : Bukkit.getOnlinePlayers()) {
            StaffRank r = rank(p);
            StudioRole role = studioRole(p.getUniqueId());
            if (r == StaffRank.NONE && role == StudioRole.NONE) continue;
            any = true;
            sender.sendMessage(tagFor(p) + p.getName() + ChatColor.GRAY +
                    (vanished.contains(p.getUniqueId()) && isStaffSender(sender) ? " [VANISHED]" : ""));
        }
        if (!any) sender.sendMessage(ChatColor.GRAY + "No staff or Studio team members are online.");
        return true;
    }

    private void openHub(Player p) {
        Inventory v = Bukkit.createInventory(null, 54, HUB);
        StaffRank r = rank(p);
        StudioRole role = studioRole(p.getUniqueId());
        v.setItem(4, item(Material.NETHER_STAR, ChatColor.GOLD + "ESN " + r.display,
                ChatColor.GRAY + "Studio: " + role.display,
                ChatColor.GRAY + "Duty: " + (isOnDuty(p) ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF")));

        v.setItem(10, item(Material.COMPASS, ChatColor.AQUA + "Staff Mode",
                ChatColor.GRAY + "Toolbar, flight and moderation shortcuts",
                state(staffMode.contains(p.getUniqueId()))));
        v.setItem(11, item(Material.ENDER_EYE, ChatColor.LIGHT_PURPLE + "Vanish",
                ChatColor.GRAY + "Hide from players, mobs and tab",
                state(vanished.contains(p.getUniqueId()))));
        v.setItem(12, item(Material.CLOCK, ChatColor.GREEN + "Duty",
                ChatColor.GRAY + "Track your staff activity",
                state(isOnDuty(p))));
        v.setItem(13, item(Material.WRITABLE_BOOK, ChatColor.GOLD + "Cases",
                ChatColor.GRAY + "View recent moderation cases"));
        v.setItem(14, item(Material.RECOVERY_COMPASS, ChatColor.DARK_PURPLE + "Realm Staff",
                ChatColor.GRAY + "Realm tools, bosses, resets and performance"));
        if (canBuildMode(p)) v.setItem(15, item(Material.GOLDEN_PICKAXE, ChatColor.YELLOW + "Build Mode",
                ChatColor.GRAY + "ESN Studios realm building tools",
                state(buildMode.contains(p.getUniqueId()))));
        if (r.level >= StaffRank.SENIOR_ADMIN.level) v.setItem(16, item(Material.REDSTONE_BLOCK,
                ChatColor.DARK_RED + "Emergency", ChatColor.GRAY + "Server lockdown and safety controls"));
        if (r.level >= StaffRank.HEAD_ADMIN.level) v.setItem(21, item(Material.NAME_TAG,
                ChatColor.RED + "Staff Management", ChatColor.GRAY + "/staffrank set <player> <rank>"));
        if (canManageStudio(p)) v.setItem(22, item(Material.CRAFTING_TABLE,
                ChatColor.AQUA + "ESN Studios Team", ChatColor.GRAY + "Roles and realm assignments"));
        v.setItem(23, item(Material.PAPER, ChatColor.WHITE + "My Stats",
                ChatColor.GRAY + "View duty and moderation statistics"));
        v.setItem(24, item(Material.SHIELD, ChatColor.BLUE + "Moderation Center",
                ChatColor.GRAY + "Open the existing /mod menu"));

        int slot = 27;
        for (Player q : Bukkit.getOnlinePlayers()) {
            if (slot >= 45) break;
            v.setItem(slot++, head(q));
        }
        v.setItem(49, item(Material.BARRIER, ChatColor.RED + "Close"));
        p.openInventory(v);
    }

    private void openInspect(Player staff, Player target) {
        selected.put(staff.getUniqueId(), target.getUniqueId());
        Inventory v = Bukkit.createInventory(null, 54, INSPECT + target.getName());
        long balance = 0;
        try { balance = economy.getBalance(target); } catch (Exception ignored) {}
        v.setItem(4, head(target));
        v.setItem(10, item(Material.COMPASS, ChatColor.AQUA + "Location",
                ChatColor.GRAY + formatLocation(target.getLocation()),
                ChatColor.GRAY + "Mode: " + target.getGameMode()));
        v.setItem(11, item(Material.REDSTONE, ChatColor.RED + "Health",
                ChatColor.GRAY + "Health: " + Math.round(target.getHealth()) + " HP",
                ChatColor.GRAY + "Food: " + target.getFoodLevel()));
        v.setItem(12, item(Material.GOLD_INGOT, ChatColor.GOLD + "Economy",
                ChatColor.GRAY + String.valueOf(balance) + " ESN Coins"));
        v.setItem(13, item(Material.ENDER_EYE, ChatColor.LIGHT_PURPLE + "Realm",
                ChatColor.GRAY + target.getWorld().getName()));
        v.setItem(19, item(Material.CHEST, ChatColor.GOLD + "Inventory",
                ChatColor.GRAY + "Open player inventory"));
        v.setItem(20, item(Material.ENDER_CHEST, ChatColor.DARK_PURPLE + "Ender Chest",
                ChatColor.GRAY + "Open player Ender Chest"));
        v.setItem(21, item(Material.ENDER_PEARL, ChatColor.AQUA + "Teleport To",
                ChatColor.GRAY + "Teleport silently to this player"));
        v.setItem(22, item(Material.LEAD, ChatColor.GREEN + "Summon",
                ChatColor.GRAY + "Bring player to you"));
        v.setItem(23, item(Material.ICE, ChatColor.AQUA + "Freeze",
                ChatColor.GRAY + "Freeze player for investigation"));
        v.setItem(24, item(Material.SPYGLASS, ChatColor.YELLOW + "Start Evidence",
                ChatColor.GRAY + "Capture chat, commands, combat and block actions"));
        v.setItem(28, item(Material.BARRIER, ChatColor.RED + "Punish",
                ChatColor.GRAY + "Open rank-aware punishment menu"));
        v.setItem(29, item(Material.WRITABLE_BOOK, ChatColor.GOLD + "Case History",
                ChatColor.GRAY + "Show recent ESN Staff cases"));
        v.setItem(30, item(Material.PAPER, ChatColor.WHITE + "Moderation History",
                ChatColor.GRAY + "Open legacy /history"));
        v.setItem(49, item(Material.ARROW, ChatColor.YELLOW + "Back"));
        staff.openInventory(v);
    }

    private void openPunish(Player staff, Player target) {
        Inventory v = Bukkit.createInventory(null, 36, PUNISH + target.getName());
        StaffRank r = rank(staff);
        v.setItem(4, head(target));
        v.setItem(10, item(Material.BOOK, ChatColor.YELLOW + "Warn", ChatColor.GRAY + punishReason(staff)));
        if (r.level >= StaffRank.TRIAL_MODERATOR.level) {
            v.setItem(11, item(Material.NAME_TAG, ChatColor.RED + "Mute 30m", ChatColor.GRAY + punishReason(staff)));
            v.setItem(12, item(Material.CLOCK, ChatColor.DARK_RED + "Mute 2h", ChatColor.GRAY + punishReason(staff)));
        }
        if (r.level >= StaffRank.MODERATOR.level) {
            v.setItem(13, item(Material.IRON_BOOTS, ChatColor.YELLOW + "Kick", ChatColor.GRAY + punishReason(staff)));
            v.setItem(14, item(Material.IRON_BARS, ChatColor.DARK_RED + "Jail 30m", ChatColor.GRAY + punishReason(staff)));
        }
        if (r.level >= StaffRank.SENIOR_MODERATOR.level) {
            v.setItem(15, item(Material.CLOCK, ChatColor.RED + "Temp Ban 24h", ChatColor.GRAY + punishReason(staff)));
        }
        if (r.level >= StaffRank.SENIOR_ADMIN.level) {
            v.setItem(16, item(Material.BARRIER, ChatColor.DARK_RED + "Permanent Ban", ChatColor.GRAY + punishReason(staff)));
        }
        if (r.level >= StaffRank.ADMIN.level) {
            v.setItem(22, item(Material.MILK_BUCKET, ChatColor.GREEN + "Clear Punishment",
                    ChatColor.GRAY + "Unmute and unban player"));
        }
        v.setItem(31, item(Material.ARROW, ChatColor.YELLOW + "Back"));
        staff.openInventory(v);
    }

    private void openRealmStaff(Player p) {
        Inventory v = Bukkit.createInventory(null, 45, REALM_STAFF);
        setRealmItem(v, 10, "storm", Material.LIGHTNING_ROD, ChatColor.AQUA + "Storm Kingdom");
        setRealmItem(v, 11, "abyss", Material.ECHO_SHARD, ChatColor.DARK_PURPLE + "The Abyss");
        setRealmItem(v, 12, "frost", Material.BLUE_ICE, ChatColor.WHITE + "Frostlands");
        setRealmItem(v, 13, "infernal", Material.MAGMA_BLOCK, ChatColor.RED + "Infernal Empire");
        setRealmItem(v, 14, "verdant", Material.MOSS_BLOCK, ChatColor.GREEN + "Verdant Wilds");
        setRealmItem(v, 15, "celestial", Material.AMETHYST_SHARD, ChatColor.AQUA + "Celestial Isles");
        setRealmItem(v, 16, "bloodmoon", Material.REDSTONE_BLOCK, ChatColor.DARK_RED + "Bloodmoon Wastes");
        setRealmItem(v, 22, "100", Material.NETHER_STAR, ChatColor.GOLD + "Realm 100");

        boolean unlocked = plugin.getConfig().getBoolean("realms.realm100-unlocked", false);
        v.setItem(29, item(unlocked ? Material.REDSTONE_TORCH : Material.LEVER,
                unlocked ? ChatColor.RED + "Lock Realm 100" : ChatColor.GREEN + "Unlock Realm 100",
                ChatColor.GRAY + "Current: " + (unlocked ? "UNLOCKED" : "LOCKED"),
                ChatColor.YELLOW + "Click to toggle"));
        v.setItem(31, item(Material.BELL, ChatColor.RED + "Realm Event Menu",
                ChatColor.GRAY + "Invasions, minibosses and custom mob cleanup"));
        v.setItem(33, item(Material.COMPASS, ChatColor.AQUA + "Player Realms Hub",
                ChatColor.GRAY + "Open the normal /realms menu"));
        v.setItem(40, item(Material.ARROW, ChatColor.YELLOW + "Back"));
        p.openInventory(v);
    }

    private void openRealmAction(Player p, String realm) {
        selectedStaffRealm.put(p.getUniqueId(), realm);
        Inventory v = Bukkit.createInventory(null, 54, REALM_ACTION + realmDisplay(realm));
        World world = Bukkit.getWorld(worldName(realm));
        v.setItem(4, item(realmIcon(realm), ChatColor.GOLD + realmDisplay(realm),
                ChatColor.GRAY + "World: " + (world == null ? ChatColor.RED + "UNLOADED" : ChatColor.GREEN + "LOADED"),
                ChatColor.GRAY + "Players: " + (world == null ? 0 : world.getPlayers().size()),
                ChatColor.GRAY + "Build lock: " + (realmBuildLocked(realm) ? ChatColor.RED + "ON" : ChatColor.GREEN + "OFF")));

        v.setItem(10, item(Material.ENDER_PEARL, ChatColor.AQUA + "Teleport", ChatColor.GRAY + "Teleport into this realm"));
        v.setItem(11, item(Material.BRICKS, ChatColor.YELLOW + "Rebuild Realm",
                ChatColor.GRAY + "Rebuild official spawn structures and decoration"));
        v.setItem(12, item(Material.FLOWER_POT, ChatColor.GREEN + "Redecorate Realm",
                ChatColor.GRAY + "Reapply the ESN decoration package"));
        v.setItem(13, item(Material.COMPARATOR, ChatColor.GREEN + "Performance",
                ChatColor.GRAY + "Players, chunks, entities and build-lock status"));

        v.setItem(19, item(Material.WITHER_SKELETON_SKULL, ChatColor.RED + "Start Main Boss"));
        v.setItem(20, item(Material.BARRIER, ChatColor.YELLOW + "Stop Main Boss"));
        v.setItem(21, item(Material.BELL, ChatColor.DARK_RED + "Start Invasion"));
        v.setItem(22, item(Material.SKELETON_SKULL, ChatColor.RED + "Spawn Miniboss"));
        v.setItem(23, item(Material.LAVA_BUCKET, ChatColor.YELLOW + "Reset Encounter"));
        v.setItem(24, item(Material.MILK_BUCKET, ChatColor.WHITE + "Clear Realm Mobs"));

        v.setItem(28, item(Material.OAK_DOOR, ChatColor.AQUA + "Evacuate Players"));
        v.setItem(29, item(Material.IRON_BARS,
                realmBuildLocked(realm) ? ChatColor.GREEN + "Unlock Building" : ChatColor.RED + "Lock Building",
                ChatColor.GRAY + "Toggle protected realm build editing"));
        v.setItem(31, item(Material.BOOK, ChatColor.LIGHT_PURPLE + "Creature Guide",
                ChatColor.GRAY + "Open this realm's creature menu"));
        v.setItem(32, item(Material.ANVIL, ChatColor.GOLD + "Realm Forge",
                ChatColor.GRAY + "Open Sigil forging menu"));
        v.setItem(49, item(Material.ARROW, ChatColor.YELLOW + "Back"));
        p.openInventory(v);
    }

    private Material realmIcon(String realm) {
        return switch (realm) {
            case "storm" -> Material.LIGHTNING_ROD;
            case "abyss" -> Material.ECHO_SHARD;
            case "frost" -> Material.BLUE_ICE;
            case "infernal" -> Material.MAGMA_BLOCK;
            case "verdant" -> Material.MOSS_BLOCK;
            case "celestial" -> Material.AMETHYST_SHARD;
            case "bloodmoon" -> Material.REDSTONE_BLOCK;
            case "100" -> Material.NETHER_STAR;
            default -> Material.COMPASS;
        };
    }

    private void openEmergency(Player p) {
        Inventory v = Bukkit.createInventory(null, 36, EMERGENCY);
        v.setItem(10, emergencyItem(Material.PAPER, "chat", "Lock Public Chat"));
        v.setItem(11, emergencyItem(Material.ENDER_EYE, "realms", "Freeze Realm Travel"));
        v.setItem(12, emergencyItem(Material.DIAMOND_SWORD, "pvp", "Disable PvP"));
        v.setItem(13, emergencyItem(Material.EMERALD, "trades", "Disable Trading"));
        v.setItem(14, emergencyItem(Material.GOLD_INGOT, "economy", "Lock Economy"));
        v.setItem(15, emergencyItem(Material.BEACON, "events", "Stop New Events"));
        v.setItem(16, emergencyItem(Material.REDSTONE_BLOCK, "maintenance", "Maintenance Mode"));
        v.setItem(22, item(Material.COMPASS, ChatColor.RED + "SEND EVERYONE TO SPAWN",
                ChatColor.GRAY + "Requires a second confirmation click"));
        v.setItem(31, item(Material.ARROW, ChatColor.YELLOW + "Back"));
        p.openInventory(v);
    }

    private void openStudio(Player p) {
        Inventory v = Bukkit.createInventory(null, 45, STUDIO);
        v.setItem(4, item(Material.CRAFTING_TABLE, ChatColor.AQUA + "ESN Studios",
                ChatColor.GRAY + "Your role: " + studioRole(p.getUniqueId()).display,
                ChatColor.GRAY + "Assigned realms: " + String.join(", ", assignedRealms(p.getUniqueId()))));
        int slot = 10;
        for (Player q : Bukkit.getOnlinePlayers()) {
            StudioRole role = studioRole(q.getUniqueId());
            if (role == StudioRole.NONE) continue;
            if (slot >= 35) break;
            ItemStack h = head(q);
            ItemMeta m = h.getItemMeta();
            List<String> lore = new ArrayList<>(m.getLore() == null ? List.of() : m.getLore());
            lore.add(ChatColor.AQUA + "Studio: " + role.display);
            lore.add(ChatColor.GRAY + "Realms: " + String.join(", ", assignedRealms(q.getUniqueId())));
            m.setLore(lore);
            h.setItemMeta(m);
            v.setItem(slot++, h);
        }
        v.setItem(40, item(Material.ARROW, ChatColor.YELLOW + "Back"));
        p.openInventory(v);
    }

    @EventHandler
    public void inventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        boolean relevant = title.equals(HUB) || title.startsWith(INSPECT) || title.startsWith(PUNISH)
                || title.equals(REALM_STAFF) || title.startsWith(REALM_ACTION) || title.equals(EMERGENCY) || title.equals(STUDIO);
        if (!relevant) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p)) return;
        int slot = event.getRawSlot();

        if (title.equals(HUB)) {
            if (slot == 49) { p.closeInventory(); return; }
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && clicked.getType() == Material.PLAYER_HEAD &&
                    clicked.getItemMeta() instanceof SkullMeta sm && sm.getOwningPlayer() != null) {
                Player target = Bukkit.getPlayer(sm.getOwningPlayer().getUniqueId());
                if (target != null) openInspect(p, target);
                return;
            }
            switch (slot) {
                case 10 -> { p.closeInventory(); p.performCommand("staffmode"); }
                case 11 -> { p.closeInventory(); p.performCommand("vanish"); }
                case 12 -> { p.closeInventory(); p.performCommand("duty"); }
                case 13 -> { p.closeInventory(); p.performCommand("cases"); }
                case 14 -> openRealmStaff(p);
                case 15 -> { p.closeInventory(); p.performCommand("buildmode"); }
                case 16 -> openEmergency(p);
                case 22 -> openStudio(p);
                case 23 -> { p.closeInventory(); p.performCommand("staffstats"); }
                case 24 -> { p.closeInventory(); p.performCommand("modmenu"); }
            }
            return;
        }

        if (title.startsWith(INSPECT)) {
            if (slot == 49) { openHub(p); return; }
            Player target = selectedPlayer(p);
            if (target == null) { openHub(p); return; }
            switch (slot) {
                case 19 -> dispatchOldStaff(p, "invsee " + target.getName());
                case 20 -> dispatchOldStaff(p, "ecsee " + target.getName());
                case 21 -> { p.closeInventory(); p.teleport(target); p.sendMessage(ChatColor.GREEN + "Teleported to " + target.getName() + "."); }
                case 22 -> { target.teleport(p); p.sendMessage(ChatColor.GREEN + "Summoned " + target.getName() + "."); }
                case 23 -> dispatchOldStaff(p, "freeze " + target.getName() + " Investigation");
                case 24 -> { p.closeInventory(); p.performCommand("evidence start " + target.getName()); }
                case 28 -> { selected.put(p.getUniqueId(), target.getUniqueId()); punishReasons.putIfAbsent(p.getUniqueId(), "Staff action through ESN Staff Center"); openPunish(p, target); }
                case 29 -> { p.closeInventory(); try { showRecentCases(p, target.getUniqueId()); } catch (Exception ex) { p.sendMessage(ChatColor.RED + "Could not load cases."); } }
                case 30 -> dispatchOldStaff(p, "history " + target.getName());
            }
            return;
        }

        if (title.startsWith(PUNISH)) {
            if (slot == 31) {
                Player target = selectedPlayer(p);
                if (target != null) openInspect(p, target); else openHub(p);
                return;
            }
            Player target = selectedPlayer(p);
            if (target == null) { openHub(p); return; }
            try {
                switch (slot) {
                    case 10 -> warn(p, target);
                    case 11 -> mute(p, target, 30 * 60_000L);
                    case 12 -> mute(p, target, 2 * 60 * 60_000L);
                    case 13 -> kick(p, target);
                    case 14 -> jail(p, target);
                    case 15 -> tempBan(p, target, 24 * 60 * 60_000L);
                    case 16 -> permBan(p, target);
                    case 22 -> clearPunishment(p, target);
                }
            } catch (Exception ex) {
                p.sendMessage(ChatColor.RED + "Punishment failed safely.");
                plugin.getLogger().warning("[ESN Staff] punishment: " + ex.getMessage());
            }
            return;
        }

        if (title.equals(REALM_STAFF)) {
            if (slot == 40) { openHub(p); return; }
            String realm = switch (slot) {
                case 10 -> "storm"; case 11 -> "abyss"; case 12 -> "frost";
                case 13 -> "infernal"; case 14 -> "verdant"; case 15 -> "celestial";
                case 16 -> "bloodmoon"; case 22 -> "100"; default -> null;
            };
            if (realm != null) { openRealmAction(p, realm); return; }
            if (slot == 29) {
                boolean unlocked = plugin.getConfig().getBoolean("realms.realm100-unlocked", false);
                p.performCommand("realmadmin " + (unlocked ? "lock100" : "unlock100"));
                openRealmStaff(p);
                return;
            }
            if (slot == 31) { p.performCommand("realmevent"); return; }
            if (slot == 33) { p.performCommand("realms"); return; }
            return;
        }

        if (title.startsWith(REALM_ACTION)) {
            String realm = selectedStaffRealm.get(p.getUniqueId());
            if (realm == null) { openRealmStaff(p); return; }
            if (slot == 49) { openRealmStaff(p); return; }
            switch (slot) {
                case 10 -> p.performCommand("realmadmin tp " + realm);
                case 11 -> p.performCommand("realmadmin rebuild " + realm);
                case 12 -> p.performCommand("realmadmin decorate " + realm);
                case 13 -> p.performCommand("realmstaff perf " + realm);
                case 19 -> p.performCommand("realmstaff boss start " + realm);
                case 20 -> p.performCommand("realmstaff boss stop " + realm);
                case 21 -> p.performCommand("realmevent invasion " + realm);
                case 22 -> p.performCommand("realmevent miniboss " + realm);
                case 23 -> p.performCommand("realmstaff reset " + realm);
                case 24 -> p.performCommand("realmevent clear " + realm);
                case 28 -> p.performCommand("realmstaff evacuate " + realm);
                case 29 -> p.performCommand("realmstaff buildlock " + realm + " " + (realmBuildLocked(realm) ? "off" : "on"));
                case 31 -> p.performCommand("realmguide " + realm);
                case 32 -> p.performCommand("realmforge");
            }
            if (slot != 10 && slot != 13 && slot != 31 && slot != 32) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (p.isOnline()) openRealmAction(p, realm);
                }, 1L);
            }
            return;
        }

        if (title.equals(EMERGENCY)) {
            if (slot == 31) { openHub(p); return; }
            String key = switch (slot) {
                case 10 -> "chat"; case 11 -> "realms"; case 12 -> "pvp"; case 13 -> "trades";
                case 14 -> "economy"; case 15 -> "events"; case 16 -> "maintenance"; default -> null;
            };
            if (key != null) {
                p.closeInventory();
                p.performCommand("emergency " + key);
            } else if (slot == 22) {
                p.closeInventory();
                p.performCommand("emergency spawnall");
            }
            return;
        }

        if (title.equals(STUDIO) && slot == 40) openHub(p);
    }

    @EventHandler
    public void interact(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if (event.getAction() == org.bukkit.event.block.Action.PHYSICAL &&
                (vanished.contains(p.getUniqueId()) || buildMode.contains(p.getUniqueId()))) {
            event.setCancelled(true);
            return;
        }
        if (!staffMode.contains(p.getUniqueId()) && !buildMode.contains(p.getUniqueId())) return;
        ItemStack held = event.getItem();
        if (held == null || !held.hasItemMeta() || !held.getItemMeta().hasDisplayName()) return;
        String name = ChatColor.stripColor(held.getItemMeta().getDisplayName());
        if (name == null) return;
        switch (name) {
            case "Staff Players", "Inspect Player" -> openHub(p);
            case "Vanish" -> toggleVanish(p);
            case "Cases" -> { p.closeInventory(); p.performCommand("cases"); }
            case "Duty" -> { p.closeInventory(); p.performCommand("duty"); }
            case "Realm Staff", "Realm Teleporter" -> openRealmStaff(p);
            case "Emergency" -> openEmergency(p);
            case "Exit Staff Mode" -> disableMode(p, false);
            case "Exit Build Mode" -> disableMode(p, true);
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void interactEntity(PlayerInteractEntityEvent event) {
        Player staff = event.getPlayer();
        if (!staffMode.contains(staff.getUniqueId()) || !(event.getRightClicked() instanceof Player target)) return;
        ItemStack held = staff.getInventory().getItemInMainHand();
        if (held.getType() == Material.ICE) {
            event.setCancelled(true);
            dispatchOldStaff(staff, "freeze " + target.getName() + " Staff Mode");
        } else if (held.getType() == Material.BOOK) {
            event.setCancelled(true);
            openInspect(staff, target);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void chat(AsyncPlayerChatEvent event) {
        Player p = event.getPlayer();
        long until = mutedUntil.getOrDefault(p.getUniqueId(), 0L);
        if (until > System.currentTimeMillis()) {
            event.setCancelled(true);
            p.sendMessage(ChatColor.RED + "You are muted for another " + duration(until - System.currentTimeMillis()) +
                    ". Reason: " + mutedReason.getOrDefault(p.getUniqueId(), "Staff action"));
            return;
        }
        if (emergency("chat") && !isStaff(p)) {
            event.setCancelled(true);
            p.sendMessage(ChatColor.RED + "Public chat is temporarily locked by ESN staff.");
        }
        recordEvidenceAsync(p, "CHAT: " + event.getMessage());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void damage(EntityDamageByEntityEvent event) {
        Player attacker = event.getDamager() instanceof Player p ? p :
                event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player p ? p : null;
        Player victim = event.getEntity() instanceof Player p ? p : null;
        if (attacker != null && victim != null && emergency("pvp") && !isStaff(attacker)) {
            event.setCancelled(true);
            attacker.sendMessage(ChatColor.RED + "PvP is temporarily disabled.");
            return;
        }
        if (attacker != null && victim != null) {
            recordEvidence(attacker, "COMBAT: hit " + victim.getName() + " for " + String.format(Locale.US, "%.1f", event.getFinalDamage()));
            recordEvidence(victim, "COMBAT: hit by " + attacker.getName() + " for " + String.format(Locale.US, "%.1f", event.getFinalDamage()));
        }
    }

    @EventHandler
    public void target(EntityTargetLivingEntityEvent event) {
        if (!(event.getTarget() instanceof Player p)) return;
        if (vanished.contains(p.getUniqueId()) || buildMode.contains(p.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler
    public void food(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player p && buildMode.contains(p.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void breakBlock(BlockBreakEvent event) {
        Player p = event.getPlayer();
        recordEvidence(p, "BLOCK_BREAK: " + event.getBlock().getType() + " @ " + compact(event.getBlock().getLocation()));
        if (!isRealmWorld(event.getBlock().getWorld())) return;
        if (!protectedRealmArea(event.getBlock().getLocation())) return;
        if (!canEditRealm(p, realmKey(event.getBlock().getWorld()))) {
            event.setCancelled(true);
            p.sendMessage(ChatColor.RED + "This ESN Realm build area is protected.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void placeBlock(BlockPlaceEvent event) {
        Player p = event.getPlayer();
        recordEvidence(p, "BLOCK_PLACE: " + event.getBlock().getType() + " @ " + compact(event.getBlock().getLocation()));
        if (!isRealmWorld(event.getBlock().getWorld())) return;
        if (!protectedRealmArea(event.getBlock().getLocation())) return;
        if (!canEditRealm(p, realmKey(event.getBlock().getWorld()))) {
            event.setCancelled(true);
            p.sendMessage(ChatColor.RED + "This ESN Realm build area is protected.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void command(PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        String raw = event.getMessage().substring(1);
        String[] parts = raw.split("\\s+");
        String cmd = parts[0].toLowerCase(Locale.ROOT);

        recordEvidence(p, "COMMAND: /" + raw);

        if (emergency("trades") && !isStaff(p) && Set.of("trade", "tradegui", "stall").contains(cmd)) {
            event.setCancelled(true);
            p.sendMessage(ChatColor.RED + "Trading is temporarily disabled.");
            return;
        }
        if (emergency("economy") && !isStaff(p) &&
                Set.of("pay", "ah", "shop", "market", "blackmarket").contains(cmd)) {
            event.setCancelled(true);
            p.sendMessage(ChatColor.RED + "Economy transactions are temporarily locked.");
            return;
        }
        if (emergency("events") && !isStaff(p) &&
                Set.of("event", "events2", "tournament", "duel", "raid", "boss", "bossrush", "koth").contains(cmd)) {
            event.setCancelled(true);
            p.sendMessage(ChatColor.RED + "New events are temporarily disabled.");
            return;
        }

        if (cmd.equals("pay") && parts.length >= 3) {
            try {
                long amount = Long.parseLong(parts[2].replace(",", ""));
                if (amount >= 1_000_000L) staffAlert(ChatColor.GOLD + "ECONOMY ALERT: " + p.getName() +
                        " attempted a " + amount + " Coin transfer to " + parts[1] + ".");
            } catch (NumberFormatException ignored) {}
        }

        if (cmd.equals("report") && parts.length >= 2) trackReport(parts[1]);

        if (isStaff(p) && !auditSuppressed.contains(p.getUniqueId()) &&
                Set.of("warn", "mute", "freeze", "jail", "kick", "ban").contains(cmd) && parts.length >= 2) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(parts[1]);
            String reason = parts.length > 2 ? String.join(" ", Arrays.copyOfRange(parts, 2, parts.length))
                    : "Legacy staff command";
            try { createCase(p, target, cmd.toUpperCase(Locale.ROOT), reason, !cmd.equals("freeze")); }
            catch (Exception ex) { plugin.getLogger().warning("[ESN Staff] command audit: " + ex.getMessage()); }
        }
    }

    @EventHandler
    public void changedWorld(PlayerChangedWorldEvent event) {
        Player p = event.getPlayer();
        if (!isRealmWorld(p.getWorld())) return;
        int count = p.getWorld().getPlayers().size();
        if (count >= 10) staffAlert(ChatColor.LIGHT_PURPLE + "REALM ALERT: " + count +
                " players are now inside " + p.getWorld().getName() + ".");
    }

    @EventHandler
    public void login(PlayerLoginEvent event) {
        if (emergency("maintenance") && rank(event.getPlayer()).level < StaffRank.TRAINEE.level) {
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER,
                    ChatColor.RED + "ESN is temporarily in maintenance mode.");
        }
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        applyPermissions(p);
        loadMute(p.getUniqueId());
        applyVisualTag(p);
        for (UUID id : vanished) {
            Player hidden = Bukkit.getPlayer(id);
            if (hidden != null && !isStaff(p)) p.hidePlayer(plugin, hidden);
        }
        if (staffData.getBoolean(path(p.getUniqueId(), "vanished"), false) && isStaff(p)) {
            vanished.add(p.getUniqueId());
            Bukkit.getScheduler().runTaskLater(plugin, () -> applyVanish(p, true), 2L);
            event.setJoinMessage(null);
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        UUID id = p.getUniqueId();
        if (vanished.contains(id)) event.setQuitMessage(null);
        if (staffMode.contains(id)) disableMode(p, false);
        if (buildMode.contains(id)) disableMode(p, true);
        PermissionAttachment attachment = rankAttachments.remove(id);
        if (attachment != null) p.removeAttachment(attachment);
        selected.remove(id);
        selected.entrySet().removeIf(e -> e.getValue().equals(id));
        pending.remove(id);
    }

    private void enableStaffMode(Player p) {
        if (buildMode.contains(p.getUniqueId())) disableMode(p, true);
        snapshots.put(p.getUniqueId(), PlayerSnapshot.capture(p));
        staffMode.add(p.getUniqueId());
        p.getInventory().clear();
        p.getInventory().setItem(0, item(Material.COMPASS, ChatColor.AQUA + "Staff Players"));
        p.getInventory().setItem(1, item(Material.BOOK, ChatColor.YELLOW + "Inspect Player"));
        p.getInventory().setItem(2, item(Material.ICE, ChatColor.AQUA + "Freeze Tool"));
        p.getInventory().setItem(3, item(Material.ENDER_EYE, ChatColor.LIGHT_PURPLE + "Vanish"));
        p.getInventory().setItem(4, item(Material.WRITABLE_BOOK, ChatColor.GOLD + "Cases"));
        p.getInventory().setItem(5, item(Material.CLOCK, ChatColor.GREEN + "Duty"));
        p.getInventory().setItem(6, item(Material.NETHER_STAR, ChatColor.DARK_PURPLE + "Realm Staff"));
        if (rank(p).level >= StaffRank.SENIOR_ADMIN.level)
            p.getInventory().setItem(7, item(Material.REDSTONE_BLOCK, ChatColor.DARK_RED + "Emergency"));
        p.getInventory().setItem(8, item(Material.BARRIER, ChatColor.RED + "Exit Staff Mode"));
        p.setAllowFlight(true);
        p.sendMessage(ChatColor.GREEN + "Staff Mode enabled.");
    }

    private void enableBuildMode(Player p) {
        if (staffMode.contains(p.getUniqueId())) disableMode(p, false);
        snapshots.put(p.getUniqueId(), PlayerSnapshot.capture(p));
        buildMode.add(p.getUniqueId());
        p.getInventory().clear();
        p.getInventory().setItem(0, item(Material.COMPASS, ChatColor.AQUA + "Realm Teleporter"));
        p.getInventory().setItem(1, item(Material.NETHER_STAR, ChatColor.LIGHT_PURPLE + "Realm Staff"));
        p.getInventory().setItem(7, item(Material.CRAFTING_TABLE, ChatColor.AQUA + "ESN Studios",
                ChatColor.GRAY + studioRole(p.getUniqueId()).display,
                ChatColor.GRAY + "Assigned: " + String.join(", ", assignedRealms(p.getUniqueId()))));
        p.getInventory().setItem(8, item(Material.BARRIER, ChatColor.RED + "Exit Build Mode"));
        p.setGameMode(GameMode.CREATIVE);
        p.setAllowFlight(true);
        p.setFlying(true);
        p.setInvulnerable(true);
        p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false, false));
        p.setFoodLevel(20);
        p.sendMessage(ChatColor.YELLOW + "ESN Studios Build Mode enabled.");
    }

    private void disableMode(Player p, boolean builder) {
        UUID id = p.getUniqueId();
        if (builder) buildMode.remove(id); else staffMode.remove(id);
        PlayerSnapshot snap = snapshots.remove(id);
        if (snap != null) snap.restore(p);
        p.sendMessage((builder ? ChatColor.YELLOW + "Build Mode" : ChatColor.GREEN + "Staff Mode") + " disabled.");
    }

    private void toggleVanish(Player p) {
        UUID id = p.getUniqueId();
        boolean enable = !vanished.contains(id);
        if (enable) vanished.add(id); else vanished.remove(id);
        staffData.set(path(id, "vanished"), enable);
        saveStaffData();
        applyVanish(p, enable);
        p.sendMessage(enable ? ChatColor.LIGHT_PURPLE + "Vanish enabled." : ChatColor.GREEN + "Vanish disabled.");
    }

    private void applyVanish(Player p, boolean enabled) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(p)) continue;
            if (enabled && !isStaff(viewer)) viewer.hidePlayer(plugin, p);
            else viewer.showPlayer(plugin, p);
        }
        p.setCollidable(!enabled);
        if (enabled) p.setSilent(true); else p.setSilent(false);
        applyVisualTag(p);
    }

    private void warn(Player staff, Player target) throws Exception {
        target.sendMessage(ChatColor.RED + "ESN Staff Warning: " + punishReason(staff));
        long id = createCase(staff, target, "WARN", punishReason(staff), true);
        staff.sendMessage(ChatColor.GREEN + "Warned " + target.getName() + " • Case #" + id);
        openInspect(staff, target);
    }

    private void mute(Player staff, Player target, long duration) throws Exception {
        long until = System.currentTimeMillis() + duration;
        mutedUntil.put(target.getUniqueId(), until);
        mutedReason.put(target.getUniqueId(), punishReason(staff));
        saveMute(target.getUniqueId(), until, punishReason(staff));
        target.sendMessage(ChatColor.RED + "You were muted for " + duration(duration) + ". Reason: " + punishReason(staff));
        long id = createCase(staff, target, "MUTE " + duration(duration), punishReason(staff), true);
        staff.sendMessage(ChatColor.GREEN + "Muted " + target.getName() + " • Case #" + id);
        openInspect(staff, target);
    }

    private void kick(Player staff, Player target) throws Exception {
        long id = createCase(staff, target, "KICK", punishReason(staff), true);
        String name = target.getName();
        target.kickPlayer("Kicked by ESN staff.\nReason: " + punishReason(staff));
        staff.sendMessage(ChatColor.GREEN + "Kicked " + name + " • Case #" + id);
        openHub(staff);
    }

    private void jail(Player staff, Player target) throws Exception {
        long id = createCase(staff, target, "JAIL 30m", punishReason(staff), true);
        suppressNextAudit(staff);
        staff.performCommand("jail " + target.getName() + " 1 30m");
        staff.sendMessage(ChatColor.GREEN + "Jailed " + target.getName() + " • Case #" + id);
    }

    private void tempBan(Player staff, Player target, long duration) throws Exception {
        if (rank(staff).level < StaffRank.SENIOR_MODERATOR.level) { noPermission(staff); return; }
        long id = createCase(staff, target, "TEMPBAN " + duration(duration), punishReason(staff), true);
        java.util.Date expires = new java.util.Date(System.currentTimeMillis() + duration);
        Bukkit.getBanList(BanList.Type.NAME).addBan(target.getName(), punishReason(staff), expires, staff.getName());
        target.kickPlayer("Temporarily banned from ESN.\nReason: " + punishReason(staff));
        staff.sendMessage(ChatColor.GREEN + "Temp-banned " + target.getName() + " • Case #" + id);
        openHub(staff);
    }

    private void permBan(Player staff, Player target) throws Exception {
        if (rank(staff).level < StaffRank.SENIOR_ADMIN.level) { noPermission(staff); return; }
        long id = createCase(staff, target, "PERMANENT BAN", punishReason(staff), true);
        Bukkit.getBanList(BanList.Type.NAME).addBan(target.getName(), punishReason(staff), null, staff.getName());
        target.kickPlayer("Permanently banned from ESN.\nReason: " + punishReason(staff));
        staff.sendMessage(ChatColor.GREEN + "Permanently banned " + target.getName() + " • Case #" + id);
        openHub(staff);
    }

    private void clearPunishment(Player staff, Player target) throws Exception {
        mutedUntil.remove(target.getUniqueId());
        mutedReason.remove(target.getUniqueId());
        saveMute(target.getUniqueId(), 0L, "");
        Bukkit.getBanList(BanList.Type.NAME).pardon(target.getName());
        long id = createCase(staff, target, "CLEAR PUNISHMENT", "Staff cleared active mute/ban", true);
        target.sendMessage(ChatColor.GREEN + "Your active ESN mute/ban state was cleared by staff.");
        staff.sendMessage(ChatColor.GREEN + "Cleared punishments • Case #" + id);
        openInspect(staff, target);
    }

    private long createCase(Player staff, OfflinePlayer target, String action, String reason, boolean punishment) throws Exception {
        long now = System.currentTimeMillis();
        long id;
        try (PreparedStatement q = db.prepareStatement(
                "INSERT INTO staff_cases(target_uuid,target_name,staff_uuid,staff_name,action,reason,status,created,updated,evidence) VALUES(?,?,?,?,?,?, 'OPEN', ?, ?, '')",
                Statement.RETURN_GENERATED_KEYS)) {
            q.setString(1, target.getUniqueId().toString());
            q.setString(2, target.getName() == null ? target.getUniqueId().toString() : target.getName());
            q.setString(3, staff.getUniqueId().toString());
            q.setString(4, staff.getName());
            q.setString(5, action);
            q.setString(6, reason == null ? "" : reason);
            q.setLong(7, now);
            q.setLong(8, now);
            q.executeUpdate();
            try (ResultSet rs = q.getGeneratedKeys()) { id = rs.next() ? rs.getLong(1) : -1; }
        }
        ensureDuty(staff);
        incrementDuty(staff, "cases", 1);
        if (punishment) incrementDuty(staff, "punishments", 1);
        staffAlert(ChatColor.RED + "CASE #" + id + ": " + staff.getName() + " → " +
                (target.getName() == null ? target.getUniqueId() : target.getName()) + " • " + action);
        return id;
    }

    private void showCase(CommandSender sender, long id) throws Exception {
        try (PreparedStatement q = db.prepareStatement("SELECT * FROM staff_cases WHERE id=?")) {
            q.setLong(1, id);
            try (ResultSet r = q.executeQuery()) {
                if (!r.next()) { sender.sendMessage(ChatColor.RED + "Case not found."); return; }
                sender.sendMessage(ChatColor.DARK_RED + "=== CASE #" + id + " ===");
                sender.sendMessage(ChatColor.GRAY + "Target: " + ChatColor.WHITE + r.getString("target_name"));
                sender.sendMessage(ChatColor.GRAY + "Staff: " + ChatColor.WHITE + r.getString("staff_name"));
                sender.sendMessage(ChatColor.GRAY + "Action: " + ChatColor.YELLOW + r.getString("action"));
                sender.sendMessage(ChatColor.GRAY + "Reason: " + ChatColor.WHITE + r.getString("reason"));
                sender.sendMessage(ChatColor.GRAY + "Status: " + ChatColor.WHITE + r.getString("status"));
                String ev = r.getString("evidence");
                if (ev != null && !ev.isBlank()) {
                    sender.sendMessage(ChatColor.GOLD + "Evidence:");
                    String[] lines = ev.split("\n");
                    for (int i = Math.max(0, lines.length - 8); i < lines.length; i++)
                        sender.sendMessage(ChatColor.DARK_GRAY + "• " + ChatColor.GRAY + lines[i]);
                }
            }
        }
    }

    private void showRecentCases(CommandSender sender, UUID target) throws Exception {
        String sql = target == null
                ? "SELECT id,target_name,staff_name,action,status FROM staff_cases ORDER BY id DESC LIMIT 12"
                : "SELECT id,target_name,staff_name,action,status FROM staff_cases WHERE target_uuid=? ORDER BY id DESC LIMIT 12";
        try (PreparedStatement q = db.prepareStatement(sql)) {
            if (target != null) q.setString(1, target.toString());
            try (ResultSet r = q.executeQuery()) {
                sender.sendMessage(ChatColor.DARK_RED + "=== ESN CASES ===");
                boolean any = false;
                while (r.next()) {
                    any = true;
                    sender.sendMessage(ChatColor.GOLD + "#" + r.getLong("id") + ChatColor.GRAY + " • " +
                            r.getString("target_name") + " • " + r.getString("action") + " • " +
                            r.getString("status") + ChatColor.DARK_GRAY + " by " + r.getString("staff_name"));
                }
                if (!any) sender.sendMessage(ChatColor.GRAY + "No matching cases.");
            }
        }
    }

    private void appendEvidence(long caseId, String line) throws Exception {
        try (PreparedStatement q = db.prepareStatement(
                "UPDATE staff_cases SET evidence=evidence||?,updated=? WHERE id=?")) {
            q.setString(1, line + "\n");
            q.setLong(2, System.currentTimeMillis());
            q.setLong(3, caseId);
            q.executeUpdate();
        }
    }

    private void recordEvidence(Player target, String line) {
        for (EvidenceSession session : evidence.values()) {
            if (session.target.equals(target.getUniqueId())) session.add(line);
        }
    }

    private void recordEvidenceAsync(Player target, String line) {
        recordEvidence(target, line);
    }

    private void ensureDuty(Player p) throws Exception {
        try (PreparedStatement q = db.prepareStatement(
                "INSERT OR IGNORE INTO duty(uuid,name) VALUES(?,?)")) {
            q.setString(1, p.getUniqueId().toString());
            q.setString(2, p.getName());
            q.executeUpdate();
        }
        try (PreparedStatement q = db.prepareStatement("UPDATE duty SET name=? WHERE uuid=?")) {
            q.setString(1, p.getName());
            q.setString(2, p.getUniqueId().toString());
            q.executeUpdate();
        }
    }

    private long dutyStart(UUID id) throws Exception {
        try (PreparedStatement q = db.prepareStatement("SELECT session_start FROM duty WHERE uuid=?")) {
            q.setString(1, id.toString());
            try (ResultSet r = q.executeQuery()) { return r.next() ? r.getLong(1) : 0L; }
        }
    }

    private boolean isOnDuty(Player p) {
        try {
            ensureDuty(p);
            return dutyStart(p.getUniqueId()) > 0;
        } catch (Exception e) { return false; }
    }

    private void incrementDuty(Player p, String field, int amount) throws Exception {
        if (!Set.of("reports", "cases", "assists", "punishments").contains(field)) return;
        ensureDuty(p);
        try (PreparedStatement q = db.prepareStatement("UPDATE duty SET " + field + "=" + field + "+? WHERE uuid=?")) {
            q.setInt(1, amount);
            q.setString(2, p.getUniqueId().toString());
            q.executeUpdate();
        }
    }

    private void showDutyStats(CommandSender sender, OfflinePlayer target) throws Exception {
        try (PreparedStatement q = db.prepareStatement("SELECT * FROM duty WHERE uuid=?")) {
            q.setString(1, target.getUniqueId().toString());
            try (ResultSet r = q.executeQuery()) {
                if (!r.next()) { sender.sendMessage(ChatColor.GRAY + "No duty stats yet."); return; }
                long total = r.getLong("total_millis");
                long start = r.getLong("session_start");
                if (start > 0) total += System.currentTimeMillis() - start;
                sender.sendMessage(ChatColor.DARK_RED + "=== STAFF STATS: " + r.getString("name") + " ===");
                sender.sendMessage(ChatColor.GRAY + "Duty time: " + ChatColor.WHITE + duration(total));
                sender.sendMessage(ChatColor.GRAY + "Reports handled: " + ChatColor.WHITE + r.getInt("reports"));
                sender.sendMessage(ChatColor.GRAY + "Cases: " + ChatColor.WHITE + r.getInt("cases"));
                sender.sendMessage(ChatColor.GRAY + "Player assists: " + ChatColor.WHITE + r.getInt("assists"));
                sender.sendMessage(ChatColor.GRAY + "Punishments: " + ChatColor.WHITE + r.getInt("punishments"));
            }
        }
    }

    private void loadMutes() throws Exception {
        long now = System.currentTimeMillis();
        try (PreparedStatement q = db.prepareStatement("SELECT target_uuid,muted_until,muted_reason FROM staff_punishments WHERE muted_until>?")) {
            q.setLong(1, now);
            try (ResultSet r = q.executeQuery()) {
                while (r.next()) {
                    UUID id = UUID.fromString(r.getString(1));
                    mutedUntil.put(id, r.getLong(2));
                    mutedReason.put(id, r.getString(3));
                }
            }
        }
    }

    private void loadMute(UUID id) {
        try (PreparedStatement q = db.prepareStatement(
                "SELECT muted_until,muted_reason FROM staff_punishments WHERE target_uuid=?")) {
            q.setString(1, id.toString());
            try (ResultSet r = q.executeQuery()) {
                if (r.next() && r.getLong(1) > System.currentTimeMillis()) {
                    mutedUntil.put(id, r.getLong(1));
                    mutedReason.put(id, r.getString(2));
                }
            }
        } catch (Exception ignored) {}
    }

    private void saveMute(UUID id, long until, String reason) throws Exception {
        try (PreparedStatement q = db.prepareStatement(
                "INSERT INTO staff_punishments(target_uuid,muted_until,muted_reason,updated) VALUES(?,?,?,?) " +
                        "ON CONFLICT(target_uuid) DO UPDATE SET muted_until=excluded.muted_until,muted_reason=excluded.muted_reason,updated=excluded.updated")) {
            q.setString(1, id.toString());
            q.setLong(2, until);
            q.setString(3, reason == null ? "" : reason);
            q.setLong(4, System.currentTimeMillis());
            q.executeUpdate();
        }
    }

    private void setStaffRank(Player actor, OfflinePlayer target, StaffRank desired) {
        StaffRank actorRank = rank(actor);
        StaffRank current = rank(target.getUniqueId());
        Player online = target.getPlayer();
        boolean protectedOwner = current == StaffRank.OWNER || (online != null && online.hasPermission("esnsmp.owner"));

        if (protectedOwner && desired != StaffRank.OWNER) {
            actor.sendMessage(ChatColor.RED + "OWNER access is protected and cannot be removed or downgraded.");
            return;
        }
        if (target.getUniqueId().equals(actor.getUniqueId()) && actorRank == StaffRank.OWNER && desired != StaffRank.OWNER) {
            actor.sendMessage(ChatColor.RED + "You cannot remove your own Owner access.");
            return;
        }
        if (desired == StaffRank.OWNER) {
            actor.sendMessage(ChatColor.RED + "Owner is controlled by the protected esnsmp.owner permission, not this command.");
            return;
        }
        if (actorRank != StaffRank.OWNER && desired.level >= actorRank.level) {
            actor.sendMessage(ChatColor.RED + "You cannot assign a rank equal to or above your own.");
            return;
        }
        if (actorRank != StaffRank.OWNER && current.level >= actorRank.level) {
            actor.sendMessage(ChatColor.RED + "You cannot modify that staff member.");
            return;
        }
        if (desired == StaffRank.CO_OWNER && actorRank != StaffRank.OWNER) {
            actor.sendMessage(ChatColor.RED + "Only the Owner can assign Co-Owner.");
            return;
        }

        staffData.set(path(target.getUniqueId(), "name"), target.getName());
        staffData.set(path(target.getUniqueId(), "rank"), desired.key);
        saveStaffData();
        if (online != null) {
            applyPermissions(online);
            applyVisualTag(online);
            online.sendMessage(ChatColor.GOLD + "Your ESN staff rank is now " + desired.display + ".");
        }
        actor.sendMessage(ChatColor.GREEN + "Set " + (target.getName() == null ? target.getUniqueId() : target.getName()) +
                " to " + desired.display + ".");
        staffAlert(ChatColor.GOLD + "STAFF CHANGE: " + actor.getName() + " set " +
                (target.getName() == null ? target.getUniqueId() : target.getName()) + " → " + desired.display);
    }

    private void setStudioRole(Player actor, OfflinePlayer target, StudioRole role) {
        staffData.set(path(target.getUniqueId(), "name"), target.getName());
        staffData.set(path(target.getUniqueId(), "studio-role"), role.key);
        saveStaffData();
        Player online = target.getPlayer();
        if (online != null) {
            applyPermissions(online);
            applyVisualTag(online);
            online.sendMessage(ChatColor.AQUA + "Your ESN Studios role is now " + role.display + ".");
        }
        actor.sendMessage(ChatColor.GREEN + "Set " + target.getName() + " Studio role to " + role.display + ".");
    }

    private void setRealmAssignment(Player actor, OfflinePlayer target, String action, String realm) {
        Set<String> current = new LinkedHashSet<>(assignedRealms(target.getUniqueId()));
        if (action.equals("add")) current.add(realm);
        else if (action.equals("remove")) current.remove(realm);
        else {
            actor.sendMessage(ChatColor.YELLOW + "Use add or remove.");
            return;
        }
        staffData.set(path(target.getUniqueId(), "realms"), new ArrayList<>(current));
        saveStaffData();
        actor.sendMessage(ChatColor.GREEN + "Updated realm assignments for " + target.getName() + ": " + String.join(", ", current));
    }

    private void showStudioList(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "=== ESN STUDIOS TEAM ===");
        boolean any = false;
        if (staffData.isConfigurationSection("members")) {
            for (String key : Objects.requireNonNull(staffData.getConfigurationSection("members")).getKeys(false)) {
                UUID id;
                try { id = UUID.fromString(key); } catch (Exception ex) { continue; }
                StudioRole role = studioRole(id);
                if (role == StudioRole.NONE) continue;
                any = true;
                String name = staffData.getString("members." + key + ".name", key);
                sender.sendMessage(role.color + "[" + role.display + "] " + ChatColor.WHITE + name +
                        ChatColor.GRAY + " • " + String.join(", ", assignedRealms(id)));
            }
        }
        if (!any) sender.sendMessage(ChatColor.GRAY + "No Studio roles assigned yet.");
    }

    private boolean canManageStudio(Player p) {
        return rank(p).level >= StaffRank.MANAGER.level || studioRole(p.getUniqueId()) == StudioRole.STUDIO_DIRECTOR;
    }

    private boolean canBuildMode(Player p) {
        StudioRole role = studioRole(p.getUniqueId());
        return rank(p).level >= StaffRank.ADMIN.level ||
                Set.of(StudioRole.DEVELOPER, StudioRole.LEAD_DEVELOPER, StudioRole.BUILDER,
                        StudioRole.LEAD_BUILDER, StudioRole.REALM_DESIGNER, StudioRole.QUEST_DESIGNER,
                        StudioRole.EVENT_TEAM, StudioRole.QA_TESTER, StudioRole.STUDIO_DIRECTOR).contains(role);
    }

    private boolean canEditRealm(Player p, String realm) {
        if (rank(p).level >= StaffRank.ADMIN.level) return true;
        if (!buildMode.contains(p.getUniqueId())) return false;
        StudioRole role = studioRole(p.getUniqueId());
        if (role == StudioRole.LEAD_BUILDER || role == StudioRole.STUDIO_DIRECTOR || role == StudioRole.LEAD_DEVELOPER) return true;
        Set<String> assigned = assignedRealms(p.getUniqueId());
        return assigned.contains("all") || assigned.contains(realm);
    }

    private void applyPermissions(Player p) {
        PermissionAttachment old = rankAttachments.remove(p.getUniqueId());
        if (old != null) p.removeAttachment(old);
        StaffRank r = rank(p);
        if (r == StaffRank.OWNER) return;

        PermissionAttachment a = p.addAttachment(plugin);
        rankAttachments.put(p.getUniqueId(), a);
        String[] nodes = {
                "esnsmp.staff", "esnsmp.admin", "esnsmp.anticheat.alerts",
                "esnsmp.staff.trainee", "esnsmp.staff.helper", "esnsmp.staff.trialmod",
                "esnsmp.staff.moderator", "esnsmp.staff.seniormod", "esnsmp.staff.admin",
                "esnsmp.staff.senioradmin", "esnsmp.staff.headadmin", "esnsmp.staff.manager",
                "esnsmp.staff.coowner",
                "esnsmp.studio.developer", "esnsmp.studio.leaddeveloper", "esnsmp.studio.builder",
                "esnsmp.studio.leadbuilder", "esnsmp.studio.realmdesigner", "esnsmp.studio.questdesigner",
                "esnsmp.studio.eventteam", "esnsmp.studio.qatester", "esnsmp.studio.contentteam",
                "esnsmp.studio.studiodirector"
        };
        for (String node : nodes) a.setPermission(node, false);
        if (r.level >= StaffRank.TRAINEE.level) a.setPermission("esnsmp.staff", true);
        if (r.level >= StaffRank.TRAINEE.level) a.setPermission("esnsmp.staff.trainee", true);
        if (r.level >= StaffRank.HELPER.level) a.setPermission("esnsmp.staff.helper", true);
        if (r.level >= StaffRank.TRIAL_MODERATOR.level) a.setPermission("esnsmp.staff.trialmod", true);
        if (r.level >= StaffRank.MODERATOR.level) {
            a.setPermission("esnsmp.staff.moderator", true);
            a.setPermission("esnsmp.anticheat.alerts", true);
        }
        if (r.level >= StaffRank.SENIOR_MODERATOR.level) a.setPermission("esnsmp.staff.seniormod", true);
        if (r.level >= StaffRank.ADMIN.level) {
            a.setPermission("esnsmp.staff.admin", true);
            a.setPermission("esnsmp.admin", true);
        }
        if (r.level >= StaffRank.SENIOR_ADMIN.level) a.setPermission("esnsmp.staff.senioradmin", true);
        if (r.level >= StaffRank.HEAD_ADMIN.level) a.setPermission("esnsmp.staff.headadmin", true);
        if (r.level >= StaffRank.MANAGER.level) a.setPermission("esnsmp.staff.manager", true);
        if (r.level >= StaffRank.CO_OWNER.level) a.setPermission("esnsmp.staff.coowner", true);
        StudioRole studio = studioRole(p.getUniqueId());
        if (studio != StudioRole.NONE) a.setPermission("esnsmp.studio." + studio.key, true);
        p.recalculatePermissions();
    }

    private StaffRank rank(Player p) {
        if (p.hasPermission("esnsmp.owner")) {
            persistOwner(p);
            return StaffRank.OWNER;
        }
        String stored = staffData.getString(path(p.getUniqueId(), "rank"));
        if (stored != null) {
            StaffRank r = StaffRank.from(stored);
            if (r != null) return r;
        }
        StaffRank legacy = p.hasPermission("esnsmp.staff.admin") ? StaffRank.ADMIN
                : p.hasPermission("esnsmp.staff.seniormod") ? StaffRank.SENIOR_MODERATOR
                : p.hasPermission("esnsmp.staff.moderator") ? StaffRank.MODERATOR
                : p.hasPermission("esnsmp.staff.trialmod") ? StaffRank.TRIAL_MODERATOR
                : p.hasPermission("esnsmp.staff.helper") ? StaffRank.HELPER
                : p.hasPermission("esnsmp.staff") ? StaffRank.TRAINEE : StaffRank.NONE;
        if (legacy != StaffRank.NONE) {
            staffData.set(path(p.getUniqueId(), "rank"), legacy.key);
            staffData.set(path(p.getUniqueId(), "name"), p.getName());
            saveStaffData();
        }
        return legacy;
    }

    private StaffRank rank(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p != null) return rank(p);
        String stored = staffData.getString(path(id, "rank"), "none");
        StaffRank r = StaffRank.from(stored);
        return r == null ? StaffRank.NONE : r;
    }

    private void persistOwner(Player p) {
        if (!"owner".equalsIgnoreCase(staffData.getString(path(p.getUniqueId(), "rank"), ""))) {
            staffData.set(path(p.getUniqueId(), "rank"), "owner");
            staffData.set(path(p.getUniqueId(), "name"), p.getName());
            saveStaffData();
        }
    }

    private boolean isStaff(Player p) { return rank(p).level >= StaffRank.TRAINEE.level; }
    private boolean isStaffSender(CommandSender sender) { return !(sender instanceof Player p) || isStaff(p); }

    private StudioRole studioRole(UUID id) {
        StudioRole role = StudioRole.from(staffData.getString(path(id, "studio-role"), "none"));
        return role == null ? StudioRole.NONE : role;
    }

    private Set<String> assignedRealms(UUID id) {
        return new LinkedHashSet<>(staffData.getStringList(path(id, "realms")));
    }

    private void applyVisualTag(Player p) {
        String prefix = tagFor(p);
        p.setPlayerListName(prefix + p.getName() + ChatColor.RESET);
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = "esn" + p.getUniqueId().toString().replace("-", "").substring(0, 10);
        Team team = board.getTeam(teamName);
        if (team == null) team = board.registerNewTeam(teamName);
        team.setPrefix(prefix);
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
        if (!team.hasEntry(p.getName())) team.addEntry(p.getName());
    }

    public String tagFor(Player p) {
        StaffRank r = rank(p);
        if (r != StaffRank.NONE) return r.color + "[" + r.shortName + "] " + ChatColor.RESET;
        StudioRole role = studioRole(p.getUniqueId());
        if (role != StudioRole.NONE) return role.color + "[" + role.shortName + "] " + ChatColor.RESET;
        return "";
    }

    private void startRealmBoss(Player actor, String input) {
        String realm = normalizeRealm(input);
        if (realm == null || realm.equals("nexus")) {
            actor.sendMessage(ChatColor.RED + "Unknown boss realm.");
            return;
        }
        stopRealmBoss(actor, realm);
        World world = Bukkit.getWorld(worldName(realm));
        if (world == null) {
            actor.sendMessage(ChatColor.RED + "Realm is not loaded. Teleport there first.");
            return;
        }
        EntityType type = switch (realm) {
            case "storm" -> EntityType.RAVAGER;
            case "abyss" -> EntityType.WARDEN;
            case "frost" -> EntityType.POLAR_BEAR;
            case "infernal" -> EntityType.WITHER_SKELETON;
            case "verdant" -> EntityType.RAVAGER;
            case "celestial" -> EntityType.ENDERMAN;
            case "bloodmoon" -> EntityType.WITHER_SKELETON;
            case "100" -> EntityType.WITHER;
            default -> EntityType.RAVAGER;
        };
        String title = switch (realm) {
            case "storm" -> "Storm Titan";
            case "abyss" -> "Abyss Reaper";
            case "frost" -> "Frost King";
            case "infernal" -> "Inferno Emperor";
            case "verdant" -> "Wildheart";
            case "celestial" -> "Astral Regent";
            case "bloodmoon" -> "Moon Tyrant";
            case "100" -> "Realm 100 Sovereign";
            default -> "Realm Boss";
        };
        LivingEntity boss = (LivingEntity) world.spawnEntity(new Location(world, -92.5, realmY(realm) + 2, 0.5), type);
        boss.setCustomName(ChatColor.DARK_RED + "[ESN REALM BOSS] " + ChatColor.GOLD + title);
        boss.setCustomNameVisible(true);
        boss.setGlowing(true);
        if (boss instanceof Mob mob) mob.setRemoveWhenFarAway(false);
        double health = realm.equals("100") ? 800 :
                realm.equals("bloodmoon") ? 650 :
                realm.equals("celestial") ? 575 :
                realm.equals("verdant") ? 625 :
                realm.equals("abyss") ? 600 : 450;
        if (boss.getAttribute(Attribute.MAX_HEALTH) != null) boss.getAttribute(Attribute.MAX_HEALTH).setBaseValue(health);
        boss.setHealth(Math.min(health, boss.getAttribute(Attribute.MAX_HEALTH) == null ? boss.getHealth() :
                boss.getAttribute(Attribute.MAX_HEALTH).getValue()));
        realmBosses.put(realm, boss.getUniqueId());
        Bukkit.broadcastMessage(ChatColor.DARK_RED + "[ESN Realms] " + ChatColor.GOLD + title +
                ChatColor.RED + " has awakened in " + realmDisplay(realm) + "!");
        actor.sendMessage(ChatColor.GREEN + "Started " + title + ".");
    }

    private void stopRealmBoss(Player actor, String input) {
        String realm = normalizeRealm(input);
        if (realm == null) return;
        World world = Bukkit.getWorld(worldName(realm));
        if (world == null) return;
        UUID tracked = realmBosses.remove(realm);
        int removed = 0;
        for (Entity e : world.getEntities()) {
            boolean match = tracked != null && e.getUniqueId().equals(tracked);
            String custom = e.getCustomName();
            if (match || (custom != null && ChatColor.stripColor(custom).startsWith("[ESN REALM BOSS]"))) {
                e.remove();
                removed++;
            }
        }
        if (actor != null) actor.sendMessage(ChatColor.YELLOW + "Stopped " + removed + " realm boss entity(s).");
    }

    private void resetRealmEncounter(Player actor, String input) {
        String realm = normalizeRealm(input);
        if (realm == null) return;
        World world = Bukkit.getWorld(worldName(realm));
        if (world == null) { actor.sendMessage(ChatColor.RED + "Realm not loaded."); return; }
        stopRealmBoss(actor, realm);
        Location center = new Location(world, -92.5, realmY(realm) + 2, 0.5);
        int removed = 0;
        for (Entity e : new ArrayList<>(world.getNearbyEntities(center, 45, 25, 45))) {
            if (e instanceof Monster || e instanceof Animals) {
                e.remove();
                removed++;
            }
        }
        actor.sendMessage(ChatColor.GREEN + "Reset realm encounter area. Removed " + removed + " mob(s).");
    }

    private void evacuateRealm(Player actor, String input) {
        String realm = normalizeRealm(input);
        if (realm == null) return;
        World world = Bukkit.getWorld(worldName(realm));
        World nexus = Bukkit.getWorld("esn_nexus");
        if (world == null || nexus == null) {
            actor.sendMessage(ChatColor.RED + "Realm or Nexus is not loaded.");
            return;
        }
        int count = 0;
        for (Player p : new ArrayList<>(world.getPlayers())) {
            p.teleport(nexus.getSpawnLocation());
            p.sendMessage(ChatColor.YELLOW + "This realm was evacuated by ESN staff.");
            count++;
        }
        actor.sendMessage(ChatColor.GREEN + "Evacuated " + count + " player(s).");
    }

    private void setRealmBuildLock(Player actor, String input, boolean locked) {
        String realm = normalizeRealm(input);
        if (realm == null) return;
        plugin.getConfig().set("staff.realm-build-locked." + realm, locked);
        plugin.saveConfig();
        actor.sendMessage((locked ? ChatColor.RED : ChatColor.GREEN) + "Realm build lock " +
                (locked ? "enabled" : "disabled") + " for " + realmDisplay(realm) + ".");
    }

    private void showRealmPerformance(Player p, String input) {
        String realm = normalizeRealm(input);
        if (realm == null) return;
        World world = Bukkit.getWorld(worldName(realm));
        if (world == null) {
            p.sendMessage(ChatColor.YELLOW + realmDisplay(realm) + " is currently unloaded.");
            return;
        }
        p.sendMessage(ChatColor.DARK_PURPLE + "=== " + realmDisplay(realm) + " PERFORMANCE ===");
        p.sendMessage(ChatColor.GRAY + "Players: " + ChatColor.WHITE + world.getPlayers().size());
        p.sendMessage(ChatColor.GRAY + "Loaded chunks: " + ChatColor.WHITE + world.getLoadedChunks().length);
        p.sendMessage(ChatColor.GRAY + "Entities: " + ChatColor.WHITE + world.getEntities().size());
        p.sendMessage(ChatColor.GRAY + "Build lock: " + (realmBuildLocked(realm) ? ChatColor.RED + "ON" : ChatColor.GREEN + "OFF"));
        p.sendMessage(ChatColor.GRAY + "Boss active: " + (realmBosses.containsKey(realm) ? ChatColor.RED + "YES" : ChatColor.GREEN + "NO"));
    }

    private void toggleEmergency(Player p, String key) {
        boolean next = !emergency(key);
        plugin.getConfig().set("staff.emergency." + key, next);
        plugin.saveConfig();
        Bukkit.broadcastMessage(ChatColor.DARK_RED + "[ESN EMERGENCY] " + ChatColor.YELLOW +
                p.getName() + " set " + key.toUpperCase(Locale.ROOT) + " to " + (next ? "LOCKED/ON" : "NORMAL/OFF") + ".");
    }

    private boolean emergency(String key) {
        return plugin.getConfig().getBoolean("staff.emergency." + key, false);
    }

    private void sendEveryoneSpawn(Player actor) {
        World main = Bukkit.getWorlds().get(0);
        int count = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.equals(actor)) continue;
            p.teleport(main.getSpawnLocation());
            p.sendMessage(ChatColor.RED + "ESN staff returned all players to spawn.");
            count++;
        }
        actor.sendMessage(ChatColor.GREEN + "Sent " + count + " player(s) to spawn.");
    }

    private void requireConfirmation(Player p, String action, Runnable run) {
        PendingConfirmation old = pending.get(p.getUniqueId());
        long now = System.currentTimeMillis();
        if (old != null && old.action.equals(action) && old.expires > now) {
            pending.remove(p.getUniqueId());
            run.run();
            return;
        }
        pending.put(p.getUniqueId(), new PendingConfirmation(action, now + 10_000L));
        p.sendMessage(ChatColor.RED + "HIGH-IMPACT ACTION: run/click " + action + " again within 10 seconds to confirm.");
    }

    private void trackReport(String targetName) {
        long now = System.currentTimeMillis();
        String key = targetName.toLowerCase(Locale.ROOT);
        Deque<Long> q = reports.computeIfAbsent(key, k -> new ArrayDeque<>());
        q.addLast(now);
        while (!q.isEmpty() && now - q.peekFirst() > 10 * 60_000L) q.removeFirst();
        if (q.size() == 5) staffAlert(ChatColor.RED + "STAFF ALERT: " + targetName + " received 5 reports within 10 minutes.");
    }

    private void staffAlert(String message) {
        for (Player p : Bukkit.getOnlinePlayers()) if (isStaff(p)) p.sendMessage(ChatColor.DARK_RED + "[ESN STAFF] " + message);
        plugin.getLogger().info(ChatColor.stripColor("[ESN STAFF] " + message));
    }

    private void suppressNextAudit(Player p) {
        auditSuppressed.add(p.getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, () -> auditSuppressed.remove(p.getUniqueId()), 2L);
    }

    private void dispatchOldStaff(Player p, String command) {
        p.closeInventory();
        suppressNextAudit(p);
        p.performCommand(command);
    }

    private boolean protectedRealmArea(Location location) {
        return Math.abs(location.getBlockX()) <= 180 && Math.abs(location.getBlockZ()) <= 180;
    }

    private boolean realmBuildLocked(String realm) {
        return plugin.getConfig().getBoolean("staff.realm-build-locked." + realm, true);
    }

    private boolean isRealmWorld(World world) {
        return world != null && world.getName().startsWith("esn_") &&
                Set.of("esn_nexus", "esn_storm", "esn_abyss", "esn_frost", "esn_infernal",
                        "esn_verdant", "esn_celestial", "esn_bloodmoon", "esn_realm100").contains(world.getName());
    }

    private String realmKey(World world) {
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
            default -> "unknown";
        };
    }

    private String normalizeRealm(String input) {
        if (input == null) return null;
        return switch (input.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "")) {
            case "nexus", "hub" -> "nexus";
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

    private int realmY(String realm) {
        return switch (realm) {
            case "storm" -> 128;
            case "abyss" -> 70;
            case "frost" -> 115;
            case "infernal" -> 90;
            case "verdant" -> 104;
            case "celestial" -> 138;
            case "bloodmoon" -> 96;
            case "100" -> 100;
            case "nexus" -> 80;
            default -> 90;
        };
    }

    private String realmDisplay(String realm) {
        return switch (realm) {
            case "storm" -> "Storm Kingdom";
            case "abyss" -> "The Abyss";
            case "frost" -> "Frostlands";
            case "infernal" -> "Infernal Empire";
            case "verdant" -> "Verdant Wilds";
            case "celestial" -> "Celestial Isles";
            case "bloodmoon" -> "Bloodmoon Wastes";
            case "100" -> "Realm 100";
            case "nexus" -> "Realm Nexus";
            default -> realm;
        };
    }

    private void setRealmItem(Inventory v, int slot, String key, Material material, String name) {
        v.setItem(slot, item(material, name,
                ChatColor.GRAY + "Open full controls for this realm",
                ChatColor.GRAY + "Teleport • rebuild • decorate • bosses",
                ChatColor.GRAY + "events • mobs • evacuation • build lock",
                ChatColor.YELLOW + "Click to manage"));
    }

    private ItemStack emergencyItem(Material material, String key, String label) {
        boolean enabled = emergency(key);
        return item(material, (enabled ? ChatColor.RED : ChatColor.GREEN) + label,
                ChatColor.GRAY + "Current: " + (enabled ? ChatColor.RED + "ACTIVE" : ChatColor.GREEN + "NORMAL"),
                ChatColor.YELLOW + "Click to toggle");
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack head(Player p) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(p);
        meta.setDisplayName(tagFor(p) + p.getName());
        meta.setLore(List.of(
                ChatColor.GRAY + "World: " + p.getWorld().getName(),
                ChatColor.GRAY + "Health: " + Math.round(p.getHealth()),
                ChatColor.GRAY + "Staff: " + rank(p).display,
                ChatColor.GRAY + "Studio: " + studioRole(p.getUniqueId()).display,
                ChatColor.YELLOW + "Click to inspect"));
        item.setItemMeta(meta);
        return item;
    }

    private Player selectedPlayer(Player staff) {
        UUID id = selected.get(staff.getUniqueId());
        return id == null ? null : Bukkit.getPlayer(id);
    }

    private String punishReason(Player p) {
        return punishReasons.getOrDefault(p.getUniqueId(), "Staff action through ESN Staff Center");
    }

    private Player player(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return null;
        }
        return p;
    }

    private boolean noPermission(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "You do not have permission for that ESN staff action.");
        return true;
    }

    private String path(UUID id, String field) {
        return "members." + id + "." + field;
    }

    private void saveStaffData() {
        try { staffData.save(staffFile); }
        catch (Exception ex) { plugin.getLogger().severe("[ESN Staff] Could not save staff-studio.yml: " + ex.getMessage()); }
    }

    private String state(boolean value) {
        return value ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF";
    }

    private String formatLocation(Location l) {
        return l.getWorld().getName() + " " + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ();
    }

    private String compact(Location l) {
        return l.getWorld().getName() + ":" + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }

    private String duration(long millis) {
        long seconds = Math.max(0, millis / 1000);
        long days = seconds / 86400; seconds %= 86400;
        long hours = seconds / 3600; seconds %= 3600;
        long minutes = seconds / 60;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m";
        return seconds + "s";
    }

    private enum StaffRank {
        NONE("none", "Member", "MEMBER", 0, ChatColor.GRAY),
        TRAINEE("trainee", "Trainee", "TRAINEE", 1, ChatColor.GRAY),
        HELPER("helper", "Helper", "HELPER", 2, ChatColor.GREEN),
        TRIAL_MODERATOR("trialmod", "Trial Moderator", "TRIAL MOD", 3, ChatColor.BLUE),
        MODERATOR("moderator", "Moderator", "MOD", 4, ChatColor.LIGHT_PURPLE),
        SENIOR_MODERATOR("seniormod", "Senior Moderator", "SR MOD", 5, ChatColor.DARK_PURPLE),
        ADMIN("admin", "Admin", "ADMIN", 6, ChatColor.RED),
        SENIOR_ADMIN("senioradmin", "Senior Admin", "SR ADMIN", 7, ChatColor.DARK_RED),
        HEAD_ADMIN("headadmin", "Head Admin", "HEAD ADMIN", 8, ChatColor.GOLD),
        MANAGER("manager", "Manager", "MANAGER", 9, ChatColor.DARK_PURPLE),
        CO_OWNER("coowner", "Co-Owner", "CO-OWNER", 10, ChatColor.GOLD),
        OWNER("owner", "Owner", "OWNER", 11, ChatColor.DARK_RED);

        final String key, display, shortName;
        final int level;
        final ChatColor color;
        StaffRank(String key, String display, String shortName, int level, ChatColor color) {
            this.key = key; this.display = display; this.shortName = shortName; this.level = level; this.color = color;
        }
        static StaffRank from(String value) {
            if (value == null) return null;
            String v = value.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
            for (StaffRank r : values()) {
                if (r.key.replace("_", "").equals(v) || r.name().toLowerCase(Locale.ROOT).replace("_", "").equals(v)
                        || r.display.toLowerCase(Locale.ROOT).replace(" ", "").equals(v)) return r;
            }
            return null;
        }
    }

    private enum StudioRole {
        NONE("none", "None", "NONE", ChatColor.GRAY),
        DEVELOPER("developer", "Developer", "DEV", ChatColor.AQUA),
        LEAD_DEVELOPER("leaddeveloper", "Lead Developer", "LEAD DEV", ChatColor.DARK_AQUA),
        BUILDER("builder", "Builder", "BUILDER", ChatColor.YELLOW),
        LEAD_BUILDER("leadbuilder", "Lead Builder", "LEAD BUILD", ChatColor.GOLD),
        REALM_DESIGNER("realmdesigner", "Realm Designer", "REALM DESIGN", ChatColor.LIGHT_PURPLE),
        QUEST_DESIGNER("questdesigner", "Quest Designer", "QUEST", ChatColor.GREEN),
        EVENT_TEAM("eventteam", "Event Team", "EVENT", ChatColor.RED),
        QA_TESTER("qatester", "QA Tester", "QA", ChatColor.WHITE),
        CONTENT_TEAM("contentteam", "Content Team", "CONTENT", ChatColor.BLUE),
        STUDIO_DIRECTOR("studiodirector", "Studio Director", "DIRECTOR", ChatColor.DARK_PURPLE);

        final String key, display, shortName;
        final ChatColor color;
        StudioRole(String key, String display, String shortName, ChatColor color) {
            this.key = key; this.display = display; this.shortName = shortName; this.color = color;
        }
        static StudioRole from(String value) {
            if (value == null) return null;
            String v = value.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
            for (StudioRole r : values()) {
                if (r.key.equals(v) || r.name().toLowerCase(Locale.ROOT).replace("_", "").equals(v)
                        || r.display.toLowerCase(Locale.ROOT).replace(" ", "").equals(v)) return r;
            }
            return null;
        }
    }

    private static final class EvidenceSession {
        final UUID target;
        final String targetName;
        final List<String> lines = Collections.synchronizedList(new ArrayList<>());
        long lastMovement = 0;
        EvidenceSession(UUID target, String targetName) { this.target = target; this.targetName = targetName; }
        void add(String line) {
            if (lines.size() >= 250) lines.remove(0);
            lines.add(System.currentTimeMillis() + " | " + line);
        }
    }

    private record PendingConfirmation(String action, long expires) {}

    private static final class PlayerSnapshot {
        final ItemStack[] contents;
        final GameMode gameMode;
        final boolean allowFlight;
        final boolean flying;
        final boolean invulnerable;
        final int food;
        final float saturation;
        final PotionEffect nightVision;

        private PlayerSnapshot(ItemStack[] contents, GameMode gameMode, boolean allowFlight, boolean flying,
                               boolean invulnerable, int food, float saturation, PotionEffect nightVision) {
            this.contents = contents; this.gameMode = gameMode; this.allowFlight = allowFlight;
            this.flying = flying; this.invulnerable = invulnerable; this.food = food;
            this.saturation = saturation; this.nightVision = nightVision;
        }

        static PlayerSnapshot capture(Player p) {
            return new PlayerSnapshot(p.getInventory().getContents().clone(), p.getGameMode(),
                    p.getAllowFlight(), p.isFlying(), p.isInvulnerable(), p.getFoodLevel(),
                    p.getSaturation(), p.getPotionEffect(PotionEffectType.NIGHT_VISION));
        }

        void restore(Player p) {
            p.getInventory().clear();
            p.getInventory().setContents(contents);
            p.setGameMode(gameMode);
            p.setAllowFlight(allowFlight);
            if (allowFlight) p.setFlying(flying);
            p.setInvulnerable(invulnerable);
            p.setFoodLevel(food);
            p.setSaturation(saturation);
            p.removePotionEffect(PotionEffectType.NIGHT_VISION);
            if (nightVision != null) p.addPotionEffect(nightVision);
        }
    }
}
