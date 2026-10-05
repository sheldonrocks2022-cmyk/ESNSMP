package com.esn.smp.gameplay;

import com.esn.smp.data.ESNDataStore;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class ESNFunExpansion implements Listener, CommandExecutor, AutoCloseable {
    private static final String MAIN = ChatColor.DARK_PURPLE + "ESN Fun Hub";
    private static final String PREFIX = ChatColor.DARK_AQUA + "ESN Fun • ";
    private static final String DAILY = PREFIX + "Daily Contracts";
    private static final String ARCADE = PREFIX + "Arcade";
    private static final String MARKET = PREFIX + "Market";
    private static final String TEMPLE = PREFIX + "Temple Trial";
    private static final String WORLD = PREFIX + "World";
    private static final String COMP = PREFIX + "Competition";
    private static final String PROG = PREFIX + "Progression";
    private static final String SOCIAL = PREFIX + "Social";
    private static final String EVENTS = PREFIX + "Events";
    private static final String ECON = PREFIX + "Economy";

    private final JavaPlugin plugin;
    private final ESNDataStore economy;
    private final Connection db;
    private final NamespacedKey loreFragment;
    private final NamespacedKey customFish;
    private final NamespacedKey luckyReward;

    private final Map<UUID, UUID> mounts = new HashMap<>();
    private final Map<UUID, Long> parkourStart = new HashMap<>();
    private final Set<UUID> mazeRuns = new HashSet<>();
    private final Map<UUID, Integer> templeStage = new HashMap<>();
    private final Map<UUID, Integer> templeTarget = new HashMap<>();
    private final Map<UUID, Challenge> challenges = new HashMap<>();
    private final Map<UUID, Duel> duels = new HashMap<>();
    private final Map<UUID, Location> pendingReturn = new HashMap<>();
    private final LinkedHashSet<UUID> matchmaking = new LinkedHashSet<>();
    private final LinkedHashSet<UUID> tournamentQueue = new LinkedHashSet<>();
    private final Deque<Match> tournamentMatches = new ArrayDeque<>();
    private final List<UUID> tournamentWinners = new ArrayList<>();
    private final Map<UUID, UUID> predictions = new HashMap<>();
    private final Map<UUID, ArrayDeque<Hit>> combatLog = new HashMap<>();
    private final Map<UUID, List<Hit>> lastRecap = new HashMap<>();
    private final Map<UUID, Map<UUID, Double>> bossDamage = new HashMap<>();
    private final Set<UUID> damageMeterEnabled = new HashSet<>();

    private boolean tournamentActive = false;
    private Match currentTournamentMatch;
    private String marketFocus = "ORES";
    private double marketMultiplier = 1.5;
    private long nextMarketRotation = 0L;

    private boolean relicAuctionActive = false;
    private long relicAuctionEnds = 0L;
    private long relicAuctionHigh = 0L;
    private UUID relicAuctionBidder;
    private String relicAuctionItem = "Celestial Relic";

    public ESNFunExpansion(JavaPlugin plugin, ESNDataStore economy) throws Exception {
        this.plugin = plugin;
        this.economy = economy;
        loreFragment = new NamespacedKey(plugin, "fun_lore_fragment");
        customFish = new NamespacedKey(plugin, "fun_custom_fish");
        luckyReward = new NamespacedKey(plugin, "fun_lucky_reward");

        File f = new File(plugin.getDataFolder(), "fun-expansion.db");
        db = DriverManager.getConnection("jdbc:sqlite:" + f.getAbsolutePath());
        try (Statement s = db.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS fun_players(
                    uuid TEXT PRIMARY KEY,
                    asc_xp INTEGER DEFAULT 0,
                    asc_level INTEGER DEFAULT 0,
                    reputation INTEGER DEFAULT 0,
                    daily_day TEXT DEFAULT '',
                    daily_kills INTEGER DEFAULT 0,
                    daily_ores INTEGER DEFAULT 0,
                    daily_fish INTEGER DEFAULT 0,
                    daily_claimed INTEGER DEFAULT 0,
                    vote_day TEXT DEFAULT '',
                    vote_streak INTEGER DEFAULT 0,
                    prediction_tokens INTEGER DEFAULT 0,
                    parkour_best INTEGER DEFAULT 0
                )
            """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS fun_global(
                    k TEXT PRIMARY KEY,
                    v INTEGER DEFAULT 0
                )
            """);
        }

        rotateMarket();
        Bukkit.getScheduler().runTaskLater(plugin, this::ensureWorldContent, 80L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickSystems, 20L * 60L, 20L * 60L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::rotateMarketIfDue, 20L * 300L, 20L * 300L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::maybeStartRelicAuction, 20L * 600L, 20L * 600L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);

        if (name.equals("votecredit")) {
            if (!(sender.hasPermission("esnsmp.admin") || !(sender instanceof Player))) {
                sender.sendMessage(ChatColor.RED + "Staff/console only.");
                return true;
            }
            if (args.length < 1) {
                sender.sendMessage(ChatColor.YELLOW + "/votecredit <player>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "That player must be online.");
                return true;
            }
            creditVote(target);
            sender.sendMessage(ChatColor.GREEN + "Vote streak credited for " + target.getName() + ".");
            return true;
        }

        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }

        try {
            switch (name) {
                case "fun" -> openMain(p);
                case "dailycontracts" -> openDaily(p);
                case "ascension" -> openProgression(p);
                case "reputation" -> openProgression(p);
                case "mount" -> toggleMount(p);
                case "parkour" -> startParkour(p);
                case "maze" -> startMaze(p);
                case "temple" -> startTemple(p);
                case "secretboss" -> summonSecretBoss(p);
                case "duel" -> duelCommand(p, args);
                case "tournament" -> tournamentCommand(p, args);
                case "matchmaking" -> toggleMatchmaking(p);
                case "predict" -> predict(p, args);
                case "killcam" -> showKillcam(p);
                case "damagemeter" -> toggleDamageMeter(p);
                case "market" -> openMarket(p);
                case "arcade" -> openArcade(p);
                case "vote" -> showVote(p);
                case "weekend" -> showWeekend(p);
                case "worldthreat" -> showThreat(p);
                case "seasonworld" -> showSeason(p);
                case "seasonfinale" -> seasonFinale(p);
                case "luckyblocks" -> p.sendMessage(ChatColor.GOLD + "Lucky Blocks are active: rare ore breaks can trigger treasure, mobs, keys, runes, or jackpots.");
                case "relicauction" -> relicAuctionCommand(p, args);
                case "funadmin" -> funAdmin(p, args);
            }
        } catch (Exception ex) {
            p.sendMessage(ChatColor.RED + "That ESN fun system failed safely. Staff can check console.");
            plugin.getLogger().warning("[ESNFunExpansion] " + name + ": " + ex.getMessage());
        }
        return true;
    }

    private void openMain(Player p) {
        Inventory v = Bukkit.createInventory(null, 54, MAIN);
        v.setItem(10, item(Material.ENDER_EYE, ChatColor.LIGHT_PURPLE + "Adventure & Rifts", ChatColor.GRAY + "Dungeons, rifts, bosses, hunts, treasure"));
        v.setItem(12, item(Material.NETHER_STAR, ChatColor.GOLD + "Progression", ChatColor.GRAY + "Skills, prestige, ascension, contracts, season"));
        v.setItem(14, item(Material.DIAMOND_SWORD, ChatColor.RED + "Competition", ChatColor.GRAY + "Duels, matchmaking, tournaments, KOTH"));
        v.setItem(16, item(Material.GRASS_BLOCK, ChatColor.GREEN + "World Activities", ChatColor.GRAY + "Parkour, maze, temple, fishing, mounts"));
        v.setItem(20, item(Material.GOLD_INGOT, ChatColor.YELLOW + "Economy & Arcade", ChatColor.GRAY + "Market, auctions, shop, stalls, arcade"));
        v.setItem(22, item(Material.PLAYER_HEAD, ChatColor.AQUA + "Social", ChatColor.GRAY + "Guilds, parties, community goals, pets"));
        v.setItem(24, item(Material.CLOCK, ChatColor.LIGHT_PURPLE + "Events & Seasons", ChatColor.GRAY + "Chaos, supply drops, weekend, season finale"));
        v.setItem(31, item(Material.WRITABLE_BOOK, ChatColor.GOLD + "Daily Contracts", ChatColor.GRAY + "Kill, mine and fish goals with rewards"));
        v.setItem(33, item(Material.COMPASS, ChatColor.AQUA + "World Threat", ChatColor.GRAY + "Threat scales elites and endgame encounters"));
        v.setItem(39, item(Material.BARRIER, ChatColor.RED + "Close"));
        if (p.hasPermission("esnsmp.admin")) v.setItem(41, item(Material.COMMAND_BLOCK, ChatColor.DARK_RED + "Admin Tools", ChatColor.GRAY + "/funadmin status"));
        p.openInventory(v);
    }

    private void openCategory(Player p, String title) {
        Inventory v = Bukkit.createInventory(null, 54, PREFIX + title);
        int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34};
        List<MenuAction> actions = switch (title) {
            case "Adventure" -> List.of(
                a("Dynamic Dungeons", Material.TRIAL_KEY, "dungeon"),
                a("Rifts", Material.ENDER_EYE, "rift"),
                a("Boss Rush", Material.WITHER_SKELETON_SKULL, "bossrush"),
                a("Treasure Maps", Material.MAP, "treasure"),
                a("Secret Boss", Material.ECHO_SHARD, "secretboss"),
                a("Boss Encyclopedia", Material.BOOK, "bosscodex"),
                a("Bounty Hunts", Material.TARGET, "bountyboard"),
                a("Artifacts", Material.NETHER_STAR, "artifacts"),
                a("Rune Forge", Material.ENCHANTING_TABLE, "runeforge"),
                a("Set Bonuses", Material.NETHERITE_CHESTPLATE, "setbonus"),
                a("Ultimate Ability", Material.BEACON, "ultimate")
            );
            case "Progression" -> List.of(
                a("Skill Tree", Material.EXPERIENCE_BOTTLE, "skilltree"),
                a("Prestige", Material.NETHER_STAR, "prestige"),
                a("Ascension", Material.END_CRYSTAL, "ascension"),
                a("Daily Contracts", Material.WRITABLE_BOOK, "dailycontracts"),
                a("Weekly Contracts", Material.BOOK, "contracts2"),
                a("Achievements", Material.TOTEM_OF_UNDYING, "achievements"),
                a("Collections", Material.CHEST, "collections"),
                a("Titles", Material.NAME_TAG, "titlemenu"),
                a("Reputation", Material.EMERALD, "reputation"),
                a("Season Pass", Material.NETHER_STAR, "seasonpass"),
                a("Realm Progression", Material.COMPASS, "realm")
            );
            case "Competition" -> List.of(
                a("Challenge a Player", Material.DIAMOND_SWORD, "duel"),
                a("Matchmaking Queue", Material.IRON_SWORD, "matchmaking"),
                a("Tournament Queue", Material.GOLDEN_SWORD, "tournament join"),
                a("Tournament Status", Material.CLOCK, "tournament status"),
                a("King of the Hill", Material.BEACON, "koth"),
                a("Predictions", Material.PAPER, "predict"),
                a("Killcam Recap", Material.SPYGLASS, "killcam"),
                a("Damage Meter", Material.RECOVERY_COMPASS, "damagemeter"),
                a("Championship", Material.DRAGON_HEAD, "championship status")
            );
            case "World" -> List.of(
                a("Parkour Tower", Material.SCAFFOLDING, "parkour"),
                a("Procedural Maze", Material.MOSSY_STONE_BRICKS, "maze"),
                a("Puzzle Temple", Material.CHISELED_STONE_BRICKS, "temple"),
                a("Fishing Overhaul", Material.FISHING_ROD, "fishingevent"),
                a("Mining Skills", Material.DIAMOND_PICKAXE, "skilltree"),
                a("Farming Skills", Material.WHEAT, "skilltree"),
                a("Mount", Material.SADDLE, "mount"),
                a("Companion Pets", Material.BONE, "pets"),
                a("Lucky Blocks", Material.GOLD_BLOCK, "luckyblocks"),
                a("Discoveries & Lore", Material.WRITTEN_BOOK, "discoveries")
            );
            case "Economy" -> List.of(
                a("Dynamic Market", Material.EMERALD, "market"),
                a("Relic Auction", Material.NETHER_STAR, "relicauction"),
                a("Auction House", Material.ENDER_CHEST, "ah"),
                a("Player Shops", Material.OAK_SIGN, "stall"),
                a("Black Market", Material.BLACKSTONE, "blackmarket"),
                a("Traveling Merchant", Material.WANDERING_TRADER_SPAWN_EGG, "merchant"),
                a("ESN Arcade", Material.GOLD_NUGGET, "arcade"),
                a("Server Shop", Material.GOLD_INGOT, "shop")
            );
            case "Social" -> List.of(
                a("Guilds / Clans", Material.WHITE_BANNER, "guild"),
                a("Guild HQ", Material.BEACON, "guildhq"),
                a("Party System", Material.PLAYER_HEAD, "party"),
                a("Community Goals", Material.BELL, "community"),
                a("Leaderboards", Material.DIAMOND, "leaderboard"),
                a("Voting Streak", Material.PAPER, "vote"),
                a("Player Profile", Material.BOOK, "profile")
            );
            default -> List.of(
                a("Random Events", Material.CLOCK, "event"),
                a("Chaos Nights", Material.CRYING_OBSIDIAN, "chaos"),
                a("Supply Drop", Material.CHEST, "supplydrop"),
                a("World Threat", Material.WITHER_SKELETON_SKULL, "worldthreat"),
                a("Weekend Modifier", Material.CAKE, "weekend"),
                a("Season World Theme", Material.RECOVERY_COMPASS, "seasonworld"),
                a("Season Finale", Material.DRAGON_EGG, "seasonfinale"),
                a("Event Calendar", Material.CLOCK, "calendar")
            );
        };
        for (int i = 0; i < actions.size() && i < slots.length; i++) {
            MenuAction m = actions.get(i);
            v.setItem(slots[i], item(m.material(), ChatColor.GOLD + m.name(), ChatColor.GRAY + "/" + m.command()));
        }
        v.setItem(45, item(Material.ARROW, ChatColor.YELLOW + "Back"));
        v.setItem(49, item(Material.BARRIER, ChatColor.RED + "Close"));
        p.openInventory(v);
    }

    private void openProgression(Player p) throws Exception {
        resetDailyIfNeeded(p);
        int xp = getInt(p, "asc_xp"), level = getInt(p, "asc_level"), rep = getInt(p, "reputation");
        Inventory v = Bukkit.createInventory(null, 27, PROG);
        v.setItem(10, item(Material.END_CRYSTAL, ChatColor.LIGHT_PURPLE + "Ascension " + level,
            ChatColor.GRAY + "XP: " + xp + "/1000", ChatColor.GRAY + "Permanent server-wide progression"));
        v.setItem(12, item(Material.EMERALD, ChatColor.GREEN + "Reputation: " + rep,
            ChatColor.GRAY + reputationName(rep), ChatColor.GRAY + "Hero, neutral, or outlaw paths"));
        v.setItem(14, item(Material.WRITABLE_BOOK, ChatColor.GOLD + "Daily Contracts", ChatColor.GRAY + "Click to view today's goals"));
        v.setItem(16, item(Material.EXPERIENCE_BOTTLE, ChatColor.AQUA + "Skill Tree", ChatColor.GRAY + "Click to open your RPG skills"));
        v.setItem(22, item(Material.ARROW, ChatColor.YELLOW + "Back"));
        p.openInventory(v);
    }

    private void openDaily(Player p) throws Exception {
        resetDailyIfNeeded(p);
        int kills = getInt(p, "daily_kills"), ores = getInt(p, "daily_ores"), fish = getInt(p, "daily_fish"), claimed = getInt(p, "daily_claimed");
        Inventory v = Bukkit.createInventory(null, 27, DAILY);
        v.setItem(10, item(Material.IRON_SWORD, ChatColor.RED + "Monster Contract", ChatColor.GRAY + "" + kills + "/40 hostile kills"));
        v.setItem(12, item(Material.DIAMOND_PICKAXE, ChatColor.AQUA + "Mining Contract", ChatColor.GRAY + "" + ores + "/64 ore blocks"));
        v.setItem(14, item(Material.FISHING_ROD, ChatColor.BLUE + "Fishing Contract", ChatColor.GRAY + "" + fish + "/12 catches"));
        boolean ready = kills >= 40 && ores >= 64 && fish >= 12 && claimed == 0;
        v.setItem(16, item(ready ? Material.CHEST : Material.BARRIER,
            ready ? ChatColor.GREEN + "CLAIM DAILY REWARD" : claimed == 1 ? ChatColor.GOLD + "CLAIMED" : ChatColor.RED + "Not Complete",
            ready ? ChatColor.YELLOW + "7,500 Coins + key + 350 Ascension XP" : ChatColor.GRAY + "Finish all three goals."));
        v.setItem(22, item(Material.ARROW, ChatColor.YELLOW + "Back"));
        p.openInventory(v);
    }

    private void openArcade(Player p) {
        Inventory v = Bukkit.createInventory(null, 27, ARCADE);
        v.setItem(10, item(Material.GOLD_NUGGET, ChatColor.GOLD + "Coin Flip • 100 Coins", ChatColor.GRAY + "50/50 • Win 190 Coins"));
        v.setItem(12, item(Material.AMETHYST_SHARD, ChatColor.AQUA + "High Roll • 250 Coins", ChatColor.GRAY + "Roll 5-6 to win 700 Coins"));
        v.setItem(14, item(Material.CHEST, ChatColor.LIGHT_PURPLE + "Treasure Pick • 500 Coins", ChatColor.GRAY + "Chance for 1,500 Coins or a key"));
        v.setItem(22, item(Material.ARROW, ChatColor.YELLOW + "Back"));
        p.openInventory(v);
    }

    private void openMarket(Player p) {
        Inventory v = Bukkit.createInventory(null, 27, MARKET);
        long mins = Math.max(0, (nextMarketRotation - System.currentTimeMillis()) / 60000L);
        v.setItem(11, item(Material.EMERALD, ChatColor.GREEN + "Hot Market: " + marketFocus,
            ChatColor.GRAY + "Bonus: x" + marketMultiplier + " ESN Coin activity payouts",
            ChatColor.GRAY + "Rotates in about " + mins + " minutes"));
        v.setItem(13, item(Material.NETHER_STAR, ChatColor.GOLD + "Relic Auction",
            relicAuctionActive ? ChatColor.YELLOW + "High bid: " + relicAuctionHigh : ChatColor.GRAY + "No auction active",
            ChatColor.GRAY + "/relicauction"));
        v.setItem(15, item(Material.OAK_SIGN, ChatColor.AQUA + "Player Shops", ChatColor.GRAY + "Open your stall/shop system", ChatColor.GRAY + "/stall"));
        v.setItem(22, item(Material.ARROW, ChatColor.YELLOW + "Back"));
        p.openInventory(v);
    }

    private void startTemple(Player p) {
        Location l = loadLocation("fun-expansion.temple");
        if (l != null) p.teleport(l.clone().add(0.5, 1, 0.5));
        templeStage.put(p.getUniqueId(), 0);
        nextTempleTarget(p);
        openTemplePuzzle(p);
    }

    private void openTemplePuzzle(Player p) {
        int stage = templeStage.getOrDefault(p.getUniqueId(), 0);
        Inventory v = Bukkit.createInventory(null, 45, TEMPLE);
        v.setItem(4, item(Material.ENDER_EYE, ChatColor.LIGHT_PURPLE + "Ancient Temple Trial",
            ChatColor.GRAY + "Stage " + (stage + 1) + "/3", ChatColor.GRAY + "Choose the correct rune."));
        v.setItem(20, item(Material.AMETHYST_SHARD, ChatColor.AQUA + "Rune of Tides"));
        v.setItem(22, item(Material.BLAZE_POWDER, ChatColor.RED + "Rune of Flame"));
        v.setItem(24, item(Material.ECHO_SHARD, ChatColor.DARK_PURPLE + "Rune of Void"));
        p.openInventory(v);
    }

    private void nextTempleTarget(Player p) {
        int[] slots = {20,22,24};
        templeTarget.put(p.getUniqueId(), slots[ThreadLocalRandom.current().nextInt(slots.length)]);
    }

    @EventHandler
    public void inventory(InventoryClickEvent e) {
        String title = e.getView().getTitle();
        if (!title.equals(MAIN) && !title.startsWith(PREFIX)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String n = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

        try {
            if (title.equals(MAIN)) {
                switch (n) {
                    case "Adventure & Rifts" -> openCategory(p, "Adventure");
                    case "Progression" -> openCategory(p, "Progression");
                    case "Competition" -> openCategory(p, "Competition");
                    case "World Activities" -> openCategory(p, "World");
                    case "Economy & Arcade" -> openCategory(p, "Economy");
                    case "Social" -> openCategory(p, "Social");
                    case "Events & Seasons" -> openCategory(p, "Events");
                    case "Daily Contracts" -> openDaily(p);
                    case "World Threat" -> showThreat(p);
                    case "Admin Tools" -> funAdmin(p, new String[]{"status"});
                    case "Close" -> p.closeInventory();
                }
                return;
            }

            if (title.equals(DAILY)) {
                if (n.equals("Back")) { openMain(p); return; }
                if (n.equals("CLAIM DAILY REWARD")) claimDaily(p);
                return;
            }
            if (title.equals(ARCADE)) {
                if (n.equals("Back")) { openCategory(p, "Economy"); return; }
                if (n.startsWith("Coin Flip")) playArcade(p, "flip", 100);
                else if (n.startsWith("High Roll")) playArcade(p, "roll", 250);
                else if (n.startsWith("Treasure Pick")) playArcade(p, "treasure", 500);
                return;
            }
            if (title.equals(MARKET)) {
                if (n.equals("Back")) { openCategory(p, "Economy"); return; }
                if (n.equals("Relic Auction")) relicAuctionCommand(p, new String[0]);
                else if (n.equals("Player Shops")) run(p, "stall");
                return;
            }
            if (title.equals(TEMPLE)) {
                int slot = e.getRawSlot();
                if (!Set.of(20,22,24).contains(slot)) return;
                if (slot == templeTarget.getOrDefault(p.getUniqueId(), -1)) {
                    int stage = templeStage.merge(p.getUniqueId(), 1, Integer::sum);
                    if (stage >= 3) {
                        p.closeInventory();
                        p.getInventory().addItem(loreFragment());
                        addAscensionXp(p, 150);
                        p.sendMessage(ChatColor.GOLD + "Temple solved! You earned an ESN Lore Fragment.");
                        templeStage.remove(p.getUniqueId());
                        templeTarget.remove(p.getUniqueId());
                    } else {
                        nextTempleTarget(p);
                        openTemplePuzzle(p);
                    }
                } else {
                    templeStage.put(p.getUniqueId(), 0);
                    nextTempleTarget(p);
                    p.sendMessage(ChatColor.RED + "Wrong rune. The temple sequence reset.");
                    openTemplePuzzle(p);
                }
                return;
            }
            if (title.equals(PROG)) {
                if (n.equals("Back")) openMain(p);
                else if (n.equals("Daily Contracts")) openDaily(p);
                else if (n.equals("Skill Tree")) run(p, "skilltree");
                return;
            }

            if (n.equals("Back")) { openMain(p); return; }
            if (n.equals("Close")) { p.closeInventory(); return; }
            ItemMeta meta = clicked.getItemMeta();
            if (meta.hasLore()) {
                for (String line : meta.getLore()) {
                    String raw = ChatColor.stripColor(line);
                    if (raw != null && raw.startsWith("/")) {
                        p.closeInventory();
                        run(p, raw.substring(1));
                        return;
                    }
                }
            }
        } catch (Exception ex) {
            p.sendMessage(ChatColor.RED + "Menu action failed safely.");
            plugin.getLogger().warning("[ESNFunExpansion] menu: " + ex.getMessage());
        }
    }

    private void playArcade(Player p, String game, long bet) throws Exception {
        if (!economy.withdraw(p, bet)) {
            p.sendMessage(ChatColor.RED + "You need " + bet + " ESN Coins.");
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long payout = 0;
        if (game.equals("flip")) {
            boolean win = r.nextBoolean();
            payout = win ? 190 : 0;
            p.sendMessage(win ? ChatColor.GREEN + "Coin Flip: WIN!" : ChatColor.RED + "Coin Flip: loss.");
        } else if (game.equals("roll")) {
            int roll = r.nextInt(1, 7);
            payout = roll >= 5 ? 700 : 0;
            p.sendMessage((payout > 0 ? ChatColor.GREEN : ChatColor.RED) + "High Roll: " + roll + (payout > 0 ? " — WIN!" : " — loss."));
        } else {
            int pick = r.nextInt(100);
            if (pick < 20) payout = 1500;
            else if (pick < 32) {
                p.getInventory().addItem(LootDrops.key());
                p.sendMessage(ChatColor.GOLD + "Treasure Pick: Realm key!");
            } else p.sendMessage(ChatColor.RED + "Treasure Pick: empty chest.");
        }
        if (payout > 0) {
            economy.deposit(p, payout);
            p.sendMessage(ChatColor.GOLD + "+" + payout + " ESN Coins.");
        }
        openArcade(p);
    }

    private void claimDaily(Player p) throws Exception {
        resetDailyIfNeeded(p);
        int kills = getInt(p, "daily_kills"), ores = getInt(p, "daily_ores"), fish = getInt(p, "daily_fish"), claimed = getInt(p, "daily_claimed");
        if (claimed == 1 || kills < 40 || ores < 64 || fish < 12) return;
        setInt(p, "daily_claimed", 1);
        economy.deposit(p, 7500);
        p.getInventory().addItem(LootDrops.key());
        addAscensionXp(p, 350);
        p.sendMessage(ChatColor.GREEN + "Daily contracts complete: +7,500 Coins, +1 key, +350 Ascension XP.");
        openDaily(p);
    }

    private void duelCommand(Player p, String[] args) throws Exception {
        if (args.length == 0) {
            Challenge c = challenges.get(p.getUniqueId());
            if (c != null && c.expires() > System.currentTimeMillis()) {
                Player from = Bukkit.getPlayer(c.challenger());
                p.sendMessage(ChatColor.GOLD + "Pending duel from " + (from == null ? "offline player" : from.getName()) +
                    " for " + c.wager() + " Coins. /duel accept");
            } else {
                p.sendMessage(ChatColor.YELLOW + "/duel <player> [coin wager] | /duel accept | /duel deny");
            }
            return;
        }
        if (args[0].equalsIgnoreCase("accept")) {
            Challenge c = challenges.remove(p.getUniqueId());
            if (c == null || c.expires() < System.currentTimeMillis()) {
                p.sendMessage(ChatColor.RED + "No active duel challenge.");
                return;
            }
            Player other = Bukkit.getPlayer(c.challenger());
            if (other == null) {
                p.sendMessage(ChatColor.RED + "Challenger is offline.");
                return;
            }
            startDuel(other, p, c.wager(), false);
            return;
        }
        if (args[0].equalsIgnoreCase("deny")) {
            challenges.remove(p.getUniqueId());
            p.sendMessage(ChatColor.YELLOW + "Duel denied.");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || target.equals(p)) {
            p.sendMessage(ChatColor.RED + "Choose another online player.");
            return;
        }
        long wager = 0;
        if (args.length > 1) {
            try { wager = Math.max(0, Math.min(1_000_000L, Long.parseLong(args[1]))); }
            catch (NumberFormatException ignored) {}
        }
        challenges.put(target.getUniqueId(), new Challenge(p.getUniqueId(), wager, System.currentTimeMillis() + 60_000L));
        p.sendMessage(ChatColor.GREEN + "Duel challenge sent to " + target.getName() + " for " + wager + " Coins.");
        target.sendMessage(ChatColor.GOLD + p.getName() + " challenged you to a duel for " + wager + " Coins. /duel accept");
    }

    private void startDuel(Player a, Player b, long wager, boolean tournament) throws Exception {
        if (duels.containsKey(a.getUniqueId()) || duels.containsKey(b.getUniqueId())) return;
        if (wager > 0) {
            if (!economy.withdraw(a, wager)) { a.sendMessage(ChatColor.RED + "Not enough Coins for that wager."); return; }
            if (!economy.withdraw(b, wager)) {
                economy.deposit(a, wager);
                b.sendMessage(ChatColor.RED + "Not enough Coins for that wager.");
                return;
            }
        }
        Location arena = loadLocation("fun-expansion.duel");
        if (arena == null) {
            ensureWorldContent();
            arena = loadLocation("fun-expansion.duel");
        }
        if (arena == null) return;

        Duel d = new Duel(a.getUniqueId(), b.getUniqueId(), wager, a.getLocation().clone(), b.getLocation().clone(), tournament);
        duels.put(a.getUniqueId(), d);
        duels.put(b.getUniqueId(), d);
        a.teleport(arena.clone().add(-7, 1, 0));
        b.teleport(arena.clone().add(7, 1, 0));
        a.setHealth(Math.min(a.getMaxHealth(), a.getMaxHealth()));
        b.setHealth(Math.min(b.getMaxHealth(), b.getMaxHealth()));
        Bukkit.broadcastMessage(ChatColor.RED + "DUEL: " + a.getName() + " vs " + b.getName() + (wager > 0 ? " • " + wager + " Coins each" : ""));
    }

    private void endDuel(Duel d, UUID winnerId, UUID loserId) {
        duels.remove(d.a());
        duels.remove(d.b());
        Player winner = Bukkit.getPlayer(winnerId);
        Player loser = Bukkit.getPlayer(loserId);
        if (winner != null) {
            Location back = winnerId.equals(d.a()) ? d.aBack() : d.bBack();
            Bukkit.getScheduler().runTaskLater(plugin, () -> { if (winner.isOnline()) winner.teleport(back); }, 2L);
            if (d.wager() > 0) {
                try { economy.deposit(winner, d.wager() * 2L); }
                catch (Exception ex) { plugin.getLogger().warning("Duel payout: " + ex.getMessage()); }
            }
            winner.sendMessage(ChatColor.GOLD + "Duel victory!" + (d.wager() > 0 ? " Pot: " + (d.wager() * 2L) + " Coins." : ""));
            rewardPredictions(winnerId);
        }
        if (loser != null) {
            Location back = loserId.equals(d.a()) ? d.aBack() : d.bBack();
            pendingReturn.put(loserId, back);
        }
        if (d.tournament()) tournamentWinner(winnerId);
    }

    private void toggleMatchmaking(Player p) throws Exception {
        UUID id = p.getUniqueId();
        if (matchmaking.remove(id)) {
            p.sendMessage(ChatColor.YELLOW + "Left matchmaking queue.");
            return;
        }
        matchmaking.add(id);
        p.sendMessage(ChatColor.GREEN + "Joined duel matchmaking.");
        if (matchmaking.size() >= 2) {
            Iterator<UUID> it = matchmaking.iterator();
            UUID a = it.next(); it.remove();
            UUID b = it.next(); it.remove();
            Player pa = Bukkit.getPlayer(a), pb = Bukkit.getPlayer(b);
            if (pa != null && pb != null) startDuel(pa, pb, 0, false);
        }
    }

    private void tournamentCommand(Player p, String[] args) throws Exception {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "join" -> {
                if (tournamentActive) { p.sendMessage(ChatColor.RED + "Tournament already running."); return; }
                tournamentQueue.add(p.getUniqueId());
                p.sendMessage(ChatColor.GREEN + "Joined tournament queue. " + tournamentQueue.size() + " queued.");
            }
            case "leave" -> {
                tournamentQueue.remove(p.getUniqueId());
                p.sendMessage(ChatColor.YELLOW + "Left tournament queue.");
            }
            case "start" -> {
                if (!p.hasPermission("esnsmp.staff")) { p.sendMessage(ChatColor.RED + "Staff only."); return; }
                startTournament();
            }
            default -> p.sendMessage(ChatColor.GOLD + "Tournament: " + (tournamentActive ? "RUNNING" : "waiting") +
                " • queued " + tournamentQueue.size() + " • /tournament join");
        }
    }

    private void startTournament() {
        List<UUID> online = tournamentQueue.stream().filter(id -> Bukkit.getPlayer(id) != null).toList();
        int size = online.size() >= 16 ? 16 : online.size() >= 8 ? 8 : online.size() >= 4 ? 4 : 0;
        if (size == 0) {
            Bukkit.broadcastMessage(ChatColor.RED + "Tournament needs at least 4 queued players.");
            return;
        }
        tournamentActive = true;
        tournamentMatches.clear();
        tournamentWinners.clear();
        predictions.clear();
        List<UUID> round = new ArrayList<>(online.subList(0, size));
        Collections.shuffle(round);
        queueRound(round);
        tournamentQueue.clear();
        Bukkit.broadcastMessage(ChatColor.GOLD + "ESN TOURNAMENT started with " + size + " players!");
        startNextTournamentMatch();
    }

    private void queueRound(List<UUID> round) {
        tournamentMatches.clear();
        for (int i = 0; i + 1 < round.size(); i += 2) tournamentMatches.add(new Match(round.get(i), round.get(i + 1)));
    }

    private void startNextTournamentMatch() {
        if (!tournamentActive) return;
        Match m = tournamentMatches.poll();
        if (m == null) {
            if (tournamentWinners.size() == 1) {
                finishTournament(tournamentWinners.get(0));
                return;
            }
            List<UUID> next = new ArrayList<>(tournamentWinners);
            tournamentWinners.clear();
            queueRound(next);
            startNextTournamentMatch();
            return;
        }
        currentTournamentMatch = m;
        Player a = Bukkit.getPlayer(m.a()), b = Bukkit.getPlayer(m.b());
        if (a == null && b == null) { startNextTournamentMatch(); return; }
        if (a == null) { tournamentWinner(m.b()); return; }
        if (b == null) { tournamentWinner(m.a()); return; }
        try {
            startDuel(a, b, 0, true);
            Bukkit.broadcastMessage(ChatColor.YELLOW + "Tournament match: " + a.getName() + " vs " + b.getName() + " • /predict <player>");
        } catch (Exception ex) {
            plugin.getLogger().warning("Tournament duel: " + ex.getMessage());
            tournamentWinner(m.a());
        }
    }

    private void tournamentWinner(UUID id) {
        if (!tournamentActive) return;
        tournamentWinners.add(id);
        currentTournamentMatch = null;
        Bukkit.getScheduler().runTaskLater(plugin, this::startNextTournamentMatch, 80L);
    }

    private void finishTournament(UUID id) {
        tournamentActive = false;
        Player p = Bukkit.getPlayer(id);
        if (p != null) {
            try { economy.deposit(p, 20000); } catch (Exception ignored) {}
            addAscensionXp(p, 500);
            p.getInventory().addItem(LootDrops.key(), LootDrops.key());
            Bukkit.broadcastMessage(ChatColor.GOLD + p.getName() + " is the ESN Tournament Champion! +20,000 Coins +2 keys.");
        }
        tournamentWinners.clear();
        predictions.clear();
    }

    private void predict(Player p, String[] args) {
        if (!tournamentActive || currentTournamentMatch == null) {
            p.sendMessage(ChatColor.RED + "No tournament match is open for predictions.");
            return;
        }
        if (args.length == 0) {
            Player a = Bukkit.getPlayer(currentTournamentMatch.a()), b = Bukkit.getPlayer(currentTournamentMatch.b());
            p.sendMessage(ChatColor.YELLOW + "Current match: " + (a == null ? "?" : a.getName()) + " vs " + (b == null ? "?" : b.getName()) + " • /predict <player>");
            return;
        }
        Player pick = Bukkit.getPlayerExact(args[0]);
        if (pick == null || (!pick.getUniqueId().equals(currentTournamentMatch.a()) && !pick.getUniqueId().equals(currentTournamentMatch.b()))) {
            p.sendMessage(ChatColor.RED + "Pick one of the current tournament players.");
            return;
        }
        if (duels.containsKey(p.getUniqueId())) {
            p.sendMessage(ChatColor.RED + "Competitors cannot predict their own active duel.");
            return;
        }
        predictions.put(p.getUniqueId(), pick.getUniqueId());
        p.sendMessage(ChatColor.GREEN + "Prediction locked: " + pick.getName() + ". Prediction tokens cannot be purchased.");
    }

    private void rewardPredictions(UUID winner) {
        for (Map.Entry<UUID, UUID> e : new HashMap<>(predictions).entrySet()) {
            if (!e.getValue().equals(winner)) continue;
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) continue;
            addIntQuiet(p, "prediction_tokens", 1);
            p.sendMessage(ChatColor.AQUA + "Correct prediction! +1 Prediction Token.");
        }
        predictions.clear();
    }

    private void showKillcam(Player p) {
        List<Hit> hits = lastRecap.get(p.getUniqueId());
        if (hits == null || hits.isEmpty()) {
            p.sendMessage(ChatColor.GRAY + "No recent killcam recap.");
            return;
        }
        p.sendMessage(ChatColor.RED + "LAST DEATH RECAP");
        for (Hit h : hits) p.sendMessage(ChatColor.GRAY + "• " + h.source() + " dealt " + String.format(Locale.US, "%.1f", h.damage()) + " damage");
    }

    private void toggleDamageMeter(Player p) {
        if (!damageMeterEnabled.add(p.getUniqueId())) {
            damageMeterEnabled.remove(p.getUniqueId());
            p.sendMessage(ChatColor.YELLOW + "Boss damage meter hidden.");
        } else p.sendMessage(ChatColor.GREEN + "Boss damage meter enabled.");
    }

    private void toggleMount(Player p) {
        UUID current = mounts.remove(p.getUniqueId());
        if (current != null) {
            Entity old = Bukkit.getEntity(current);
            if (old != null) old.remove();
            p.sendMessage(ChatColor.YELLOW + "Mount dismissed.");
            return;
        }
        Horse h = p.getWorld().spawn(p.getLocation(), Horse.class);
        h.setTamed(true);
        h.setOwner(p);
        h.setCustomName(ChatColor.AQUA + p.getName() + "'s ESN Mount");
        h.setCustomNameVisible(true);
        h.setAdult();
        h.getInventory().setSaddle(new ItemStack(Material.SADDLE));
        if (h.getAttribute(Attribute.MOVEMENT_SPEED) != null) h.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.32);
        h.addScoreboardTag("esnFunMount");
        h.addPassenger(p);
        mounts.put(p.getUniqueId(), h.getUniqueId());
        p.sendMessage(ChatColor.GREEN + "ESN mount summoned.");
    }

    private void startParkour(Player p) {
        Location l = loadLocation("fun-expansion.parkour");
        if (l == null) { ensureWorldContent(); l = loadLocation("fun-expansion.parkour"); }
        if (l == null) return;
        p.teleport(l.clone().add(0.5, 1.2, 0.5));
        parkourStart.put(p.getUniqueId(), System.currentTimeMillis());
        p.sendMessage(ChatColor.GOLD + "Parkour Tower started. Reach the emerald finish block!");
    }

    private void startMaze(Player p) {
        Location l = loadLocation("fun-expansion.maze");
        if (l == null) { ensureWorldContent(); l = loadLocation("fun-expansion.maze"); }
        if (l == null) return;
        p.teleport(l.clone().add(1.5, 1, 1.5));
        mazeRuns.add(p.getUniqueId());
        p.sendMessage(ChatColor.GREEN + "Procedural Maze started. Find the emerald exit!");
    }

    private void summonSecretBoss(Player p) {
        if (!consumeFragments(p, 3)) {
            p.sendMessage(ChatColor.RED + "The secret altar requires 3 ESN Lore Fragments. Earn them from Parkour, Maze and Temple trials.");
            return;
        }
        Location l = p.getLocation().clone().add(p.getLocation().getDirection().multiply(7));
        l.setY(p.getWorld().getHighestBlockYAt(l.getBlockX(), l.getBlockZ()) + 1);
        WitherSkeleton boss = p.getWorld().spawn(l, WitherSkeleton.class);
        boss.setCustomName(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "THE FORGOTTEN");
        boss.setCustomNameVisible(true);
        boss.setGlowing(true);
        boss.setPersistent(false);
        boss.addScoreboardTag("esnAdventureBoss");
        boss.addScoreboardTag("esnSecretBoss");
        if (boss.getAttribute(Attribute.MAX_HEALTH) != null) {
            boss.getAttribute(Attribute.MAX_HEALTH).setBaseValue(1000);
            boss.setHealth(1000);
        }
        boss.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION, 2));
        Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + p.getName() + " awakened THE FORGOTTEN secret boss!");
    }

    private void relicAuctionCommand(Player p, String[] args) throws Exception {
        if (!relicAuctionActive) {
            p.sendMessage(ChatColor.GRAY + "No Relic Auction is active right now.");
            return;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("bid")) {
            long bid;
            try { bid = Long.parseLong(args[1]); } catch (NumberFormatException ex) { return; }
            if (bid <= relicAuctionHigh || bid < 1000) {
                p.sendMessage(ChatColor.RED + "Bid must be above " + relicAuctionHigh + " Coins and at least 1,000.");
                return;
            }
            if (economy.getBalance(p) < bid) {
                p.sendMessage(ChatColor.RED + "You do not currently have enough ESN Coins.");
                return;
            }
            relicAuctionHigh = bid;
            relicAuctionBidder = p.getUniqueId();
            Bukkit.broadcastMessage(ChatColor.GOLD + p.getName() + " leads the Relic Auction at " + bid + " Coins!");
            return;
        }
        long sec = Math.max(0, (relicAuctionEnds - System.currentTimeMillis()) / 1000L);
        p.sendMessage(ChatColor.GOLD + "RELIC AUCTION • " + relicAuctionItem + " • High " + relicAuctionHigh + " • " + sec + "s left • /relicauction bid <coins>");
    }

    private void maybeStartRelicAuction() {
        if (relicAuctionActive || Bukkit.getOnlinePlayers().isEmpty()) return;
        if (ThreadLocalRandom.current().nextDouble() < 0.35) startRelicAuction();
    }

    private void startRelicAuction() {
        String[] items = {"Celestial Relic", "Void-Touched Artifact", "Ancient Titan Core", "Storm Crown", "Bloodmoon Sigil"};
        relicAuctionItem = items[ThreadLocalRandom.current().nextInt(items.length)];
        relicAuctionActive = true;
        relicAuctionEnds = System.currentTimeMillis() + 10 * 60_000L;
        relicAuctionHigh = 0;
        relicAuctionBidder = null;
        Bukkit.broadcastMessage(ChatColor.GOLD + "RELIC AUCTION OPEN: " + relicAuctionItem + " for 10 minutes! /relicauction");
    }

    private void finishRelicAuction() {
        if (!relicAuctionActive || System.currentTimeMillis() < relicAuctionEnds) return;
        relicAuctionActive = false;
        if (relicAuctionBidder == null) {
            Bukkit.broadcastMessage(ChatColor.GRAY + "Relic Auction closed with no bids.");
            return;
        }
        Player p = Bukkit.getPlayer(relicAuctionBidder);
        if (p == null) {
            Bukkit.broadcastMessage(ChatColor.RED + "Relic Auction winner went offline; auction cancelled safely.");
            return;
        }
        try {
            if (!economy.withdraw(p, relicAuctionHigh)) {
                p.sendMessage(ChatColor.RED + "Relic Auction purchase failed because your balance changed.");
                return;
            }
            ItemStack relic = item(Material.NETHER_STAR, ChatColor.GOLD + relicAuctionItem,
                ChatColor.LIGHT_PURPLE + "Relic Auction Winner", ChatColor.GRAY + "A rare ESN collectible artifact.");
            p.getInventory().addItem(relic);
            Bukkit.broadcastMessage(ChatColor.GOLD + p.getName() + " won " + relicAuctionItem + " for " + relicAuctionHigh + " Coins!");
        } catch (Exception ex) {
            plugin.getLogger().warning("Relic auction finish: " + ex.getMessage());
        }
    }

    private void showVote(Player p) throws Exception {
        ensure(p);
        p.sendMessage(ChatColor.AQUA + "Voting streak: " + getInt(p, "vote_streak") + " days.");
        p.sendMessage(ChatColor.GRAY + "Your vote service can run /votecredit <player> from console after a verified vote.");
    }

    private void creditVote(Player p) {
        try {
            ensure(p);
            String today = LocalDate.now().toString();
            String old = getString(p, "vote_day");
            if (today.equals(old)) return;
            int streak = 1;
            if (!old.isBlank()) {
                try {
                    LocalDate d = LocalDate.parse(old);
                    if (d.plusDays(1).equals(LocalDate.now())) streak = getInt(p, "vote_streak") + 1;
                } catch (Exception ignored) {}
            }
            setString(p, "vote_day", today);
            setInt(p, "vote_streak", streak);
            long reward = 500L + Math.min(5000L, streak * 150L);
            economy.deposit(p, reward);
            if (streak % 7 == 0) p.getInventory().addItem(LootDrops.key());
            p.sendMessage(ChatColor.GREEN + "Vote credited! Streak " + streak + " • +" + reward + " Coins" + (streak % 7 == 0 ? " + key" : ""));
        } catch (Exception ex) {
            plugin.getLogger().warning("Vote credit: " + ex.getMessage());
        }
    }

    private void showWeekend(Player p) {
        if (weekend()) p.sendMessage(ChatColor.GREEN + "Weekend Modifier ACTIVE: 2x Ascension XP and +50% dynamic-market payouts.");
        else p.sendMessage(ChatColor.GRAY + "Weekend Modifier activates Friday-Sunday.");
    }

    private void showThreat(Player p) {
        int t = global("threat");
        p.sendMessage(ChatColor.RED + "World Threat: " + t + "/10" + ChatColor.GRAY + " • higher threat creates stronger mythic enemies and better rewards.");
    }

    private void showSeason(Player p) {
        p.sendMessage(ChatColor.LIGHT_PURPLE + "World Season: " + seasonName() + ChatColor.GRAY + " • themed encounters and finale boss are active this quarter.");
    }

    private void seasonFinale(Player p) {
        if (!p.hasPermission("esnsmp.staff")) {
            p.sendMessage(ChatColor.RED + "Staff starts the server-wide season finale.");
            return;
        }
        Location l = p.getLocation().clone().add(10, 0, 10);
        l.setY(p.getWorld().getHighestBlockYAt(l.getBlockX(), l.getBlockZ()) + 1);
        Ravager b = p.getWorld().spawn(l, Ravager.class);
        b.setCustomName(ChatColor.GOLD + "" + ChatColor.BOLD + "SEASON FINALE • " + seasonName());
        b.setCustomNameVisible(true);
        b.setGlowing(true);
        b.setPersistent(false);
        b.addScoreboardTag("esnAdventureBoss");
        b.addScoreboardTag("esnSeasonFinale");
        if (b.getAttribute(Attribute.MAX_HEALTH) != null) {
            b.getAttribute(Attribute.MAX_HEALTH).setBaseValue(1000);
            b.setHealth(1000);
        }
        Bukkit.broadcastMessage(ChatColor.GOLD + "THE " + seasonName() + " SEASON FINALE HAS BEGUN!");
    }

    private void funAdmin(Player p, String[] args) {
        if (!p.hasPermission("esnsmp.admin")) { p.sendMessage(ChatColor.RED + "Admin only."); return; }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("rebuild")) {
            buildWorldContent(true);
            p.sendMessage(ChatColor.GREEN + "Fun-world structures rebuilt.");
        } else if (sub.equals("auction")) {
            startRelicAuction();
        } else {
            p.sendMessage(ChatColor.AQUA + "Fun expansion status: structures=" + plugin.getConfig().getBoolean("fun-expansion.generated", false)
                + " threat=" + global("threat") + " tournament=" + tournamentActive + " market=" + marketFocus);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void breakBlock(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Material m = e.getBlock().getType();
        boolean ore = m.name().contains("_ORE") || m.equals(Material.ANCIENT_DEBRIS);
        boolean crop = Set.of(Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS, Material.NETHER_WART).contains(m);
        try {
            resetDailyIfNeeded(p);
            if (ore) {
                addInt(p, "daily_ores", 1);
                addAscensionXp(p, 4);
                marketPayout(p, "ORES", 6);
                if (ThreadLocalRandom.current().nextDouble() < 0.004) luckyEvent(p, e.getBlock().getLocation());
            }
            if (crop) {
                addAscensionXp(p, 2);
                marketPayout(p, "CROPS", 3);
                if (ThreadLocalRandom.current().nextDouble() < 0.012) {
                    p.getWorld().dropItemNaturally(e.getBlock().getLocation(), new ItemStack(Material.GOLDEN_CARROT, 3));
                    p.sendActionBar(ChatColor.GOLD + "GIANT CROP! Bonus harvest.");
                }
            }
        } catch (Exception ignored) {}
    }

    @EventHandler
    public void fish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        Player p = e.getPlayer();
        try {
            resetDailyIfNeeded(p);
            addInt(p, "daily_fish", 1);
            addAscensionXp(p, 8);
            marketPayout(p, "FISH", 15);
        } catch (Exception ignored) {}

        double r = ThreadLocalRandom.current().nextDouble();
        if (r < 0.025) {
            ItemStack fish = customFish("Celestial Koi", Material.TROPICAL_FISH, "LEGENDARY");
            p.getInventory().addItem(fish);
            p.sendMessage(ChatColor.GOLD + "LEGENDARY CATCH: Celestial Koi!");
        } else if (r < 0.08) {
            p.getInventory().addItem(customFish("Voidfin", Material.COD, "EPIC"));
            p.sendMessage(ChatColor.LIGHT_PURPLE + "EPIC CATCH: Voidfin.");
        } else if (r < 0.105) {
            Drowned sea = p.getWorld().spawn(p.getLocation().clone().add(4, 0, 4), Drowned.class);
            sea.setCustomName(ChatColor.DARK_AQUA + "Abyssal Angler");
            sea.addScoreboardTag("esnMythic");
            if (sea.getAttribute(Attribute.MAX_HEALTH) != null) {
                sea.getAttribute(Attribute.MAX_HEALTH).setBaseValue(100);
                sea.setHealth(100);
            }
            p.sendMessage(ChatColor.RED + "Your catch awakened an Abyssal Angler!");
        }
    }

    @EventHandler
    public void creature(CreatureSpawnEvent e) {
        if (!(e.getEntity() instanceof Monster m)) return;
        int threat = global("threat");
        if (threat <= 0 || ThreadLocalRandom.current().nextDouble() >= threat * 0.005) return;
        m.addScoreboardTag("esnMythic");
        m.setCustomName(ChatColor.RED + "Threat " + threat + " " + m.getType().name().replace('_', ' '));
        if (m.getAttribute(Attribute.MAX_HEALTH) != null) {
            double hp = Math.min(250, Math.max(40, m.getAttribute(Attribute.MAX_HEALTH).getBaseValue() * (1.5 + threat * .15)));
            m.getAttribute(Attribute.MAX_HEALTH).setBaseValue(hp);
            m.setHealth(hp);
        }
        m.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, PotionEffect.INFINITE_DURATION, Math.min(3, threat / 3)));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void damage(EntityDamageByEntityEvent e) {
        Player attacker = damager(e.getDamager());
        if (e.getEntity() instanceof Player victim) {
            String src = attacker != null ? attacker.getName() : e.getDamager().getName();
            ArrayDeque<Hit> q = combatLog.computeIfAbsent(victim.getUniqueId(), k -> new ArrayDeque<>());
            q.addLast(new Hit(src, e.getFinalDamage(), System.currentTimeMillis()));
            while (q.size() > 5) q.removeFirst();
        }
        if (attacker != null && e.getEntity() instanceof LivingEntity target && isBoss(target)) {
            bossDamage.computeIfAbsent(target.getUniqueId(), k -> new HashMap<>())
                .merge(attacker.getUniqueId(), Math.max(0, e.getFinalDamage()), Double::sum);
        }

        if (attacker != null && e.getEntity() instanceof Monster target) {
            ItemStack main = attacker.getInventory().getItemInMainHand();
            ItemStack off = attacker.getInventory().getItemInOffHand();
            String a = display(main), b = display(off);
            if (a.contains("LIGHTNING") && b.contains("VOID") && ThreadLocalRandom.current().nextDouble() < 0.12) {
                attacker.getWorld().strikeLightningEffect(target.getLocation());
                for (Entity z : target.getNearbyEntities(4, 3, 4)) if (z instanceof Monster mm && !mm.equals(target)) mm.damage(8, attacker);
                attacker.sendActionBar(ChatColor.AQUA + "SYNERGY: Voidstorm Chain");
            } else if (a.contains("IMMORTAL") && b.contains("RIFT") && ThreadLocalRandom.current().nextDouble() < 0.10) {
                attacker.setHealth(Math.min(attacker.getMaxHealth(), attacker.getHealth() + 4));
                attacker.sendActionBar(ChatColor.DARK_PURPLE + "SYNERGY: Immortal Rift");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void death(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();
        Player killer = dead.getKiller();

        if (dead instanceof Player p) {
            ArrayDeque<Hit> q = combatLog.remove(p.getUniqueId());
            if (q != null) lastRecap.put(p.getUniqueId(), new ArrayList<>(q));
            Duel d = duels.get(p.getUniqueId());
            if (d != null) {
                UUID winner = d.a().equals(p.getUniqueId()) ? d.b() : d.a();
                endDuel(d, winner, p.getUniqueId());
            }
            return;
        }

        if (killer != null) {
            try {
                resetDailyIfNeeded(killer);
                if (dead instanceof Monster) {
                    addInt(killer, "daily_kills", 1);
                    addAscensionXp(killer, dead.getScoreboardTags().contains("esnMythic") ? 35 : 6);
                    marketPayout(killer, "MONSTERS", dead.getScoreboardTags().contains("esnMythic") ? 80 : 8);
                }
                if (isBoss(dead)) {
                    addAscensionXp(killer, 120);
                    setGlobal("threat", Math.min(10, global("threat") + 1));
                    addInt(killer, "reputation", dead.getScoreboardTags().contains("esnSecretBoss") ? 30 : 10);
                }
            } catch (Exception ignored) {}

            if (dead.getScoreboardTags().contains("esnSecretBoss")) {
                try { economy.deposit(killer, 25000); } catch (Exception ignored) {}
                killer.getInventory().addItem(LootDrops.key(), LootDrops.key(), LootDrops.key());
                Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + killer.getName() + " defeated THE FORGOTTEN!");
            }
            if (dead.getScoreboardTags().contains("esnSeasonFinale")) {
                try { economy.deposit(killer, 100000); } catch (Exception ignored) {}
                for (Player online : Bukkit.getOnlinePlayers()) online.getInventory().addItem(LootDrops.key());
                setGlobal("threat", 0);
                Bukkit.broadcastMessage(ChatColor.GOLD + killer.getName() + " ended the season finale! Everyone online received a key.");
            }
        }

        if (isBoss(dead)) {
            Map<UUID, Double> map = bossDamage.remove(dead.getUniqueId());
            if (map != null && !map.isEmpty()) {
                List<Map.Entry<UUID, Double>> top = map.entrySet().stream()
                    .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed()).limit(5).toList();
                double total = map.values().stream().mapToDouble(Double::doubleValue).sum();
                for (UUID id : map.keySet()) {
                    Player p = Bukkit.getPlayer(id);
                    if (p == null || !damageMeterEnabled.contains(id)) continue;
                    p.sendMessage(ChatColor.GOLD + "BOSS DAMAGE METER");
                    int rank = 1;
                    for (Map.Entry<UUID, Double> z : top) {
                        Player who = Bukkit.getPlayer(z.getKey());
                        p.sendMessage(ChatColor.GRAY + "#" + rank++ + " " + (who == null ? z.getKey().toString().substring(0, 8) : who.getName())
                            + " — " + Math.round(z.getValue()) + " (" + Math.round(z.getValue() / Math.max(1, total) * 100) + "%)");
                    }
                }
            }
        }
    }

    @EventHandler
    public void respawn(PlayerRespawnEvent e) {
        Location l = pendingReturn.remove(e.getPlayer().getUniqueId());
        if (l != null) e.setRespawnLocation(l);
    }

    @EventHandler
    public void move(PlayerMoveEvent e) {
        if (e.getTo() == null || e.getFrom().getBlock().equals(e.getTo().getBlock())) return;
        Player p = e.getPlayer();
        Location parkour = loadLocation("fun-expansion.parkour-finish");
        if (parkourStart.containsKey(p.getUniqueId()) && parkour != null && sameWorldNear(p.getLocation(), parkour, 2.2)) {
            long start = parkourStart.remove(p.getUniqueId());
            long ms = System.currentTimeMillis() - start;
            try {
                long best = getLong(p, "parkour_best");
                if (best == 0 || ms < best) setLong(p, "parkour_best", ms);
                economy.deposit(p, 2000);
            } catch (Exception ignored) {}
            p.getInventory().addItem(loreFragment());
            addAscensionXp(p, 175);
            p.sendMessage(ChatColor.GOLD + "Parkour complete in " + String.format(Locale.US, "%.1f", ms / 1000.0) + "s! +2,000 Coins + Lore Fragment.");
        }

        Location maze = loadLocation("fun-expansion.maze-finish");
        if (mazeRuns.contains(p.getUniqueId()) && maze != null && sameWorldNear(p.getLocation(), maze, 2.2)) {
            mazeRuns.remove(p.getUniqueId());
            try { economy.deposit(p, 2000); } catch (Exception ignored) {}
            p.getInventory().addItem(loreFragment());
            addAscensionXp(p, 175);
            p.sendMessage(ChatColor.GREEN + "Maze escaped! +2,000 Coins + Lore Fragment.");
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        matchmaking.remove(id);
        tournamentQueue.remove(id);
        parkourStart.remove(id);
        mazeRuns.remove(id);
        templeStage.remove(id);
        templeTarget.remove(id);
        UUID mount = mounts.remove(id);
        if (mount != null) {
            Entity z = Bukkit.getEntity(mount);
            if (z != null) z.remove();
        }
        Duel d = duels.get(id);
        if (d != null) {
            UUID winner = d.a().equals(id) ? d.b() : d.a();
            endDuel(d, winner, id);
        }
    }

    private void luckyEvent(Player p, Location l) {
        int r = ThreadLocalRandom.current().nextInt(100);
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, l.clone().add(.5, .5, .5), 60, 1, 1, 1, .15);
        if (r < 25) {
            p.getInventory().addItem(LootDrops.key());
            p.sendMessage(ChatColor.GOLD + "LUCKY BLOCK: Realm key!");
        } else if (r < 45) {
            try { economy.deposit(p, 5000); } catch (Exception ignored) {}
            p.sendMessage(ChatColor.GOLD + "LUCKY BLOCK JACKPOT: +5,000 Coins!");
        } else if (r < 70) {
            ItemStack i = item(Material.AMETHYST_SHARD, ChatColor.LIGHT_PURPLE + "Lucky Crystal", ChatColor.GRAY + "Rare world-event collectible");
            i.getItemMeta().getPersistentDataContainer().set(luckyReward, PersistentDataType.BYTE, (byte) 1);
            p.getInventory().addItem(i);
            p.sendMessage(ChatColor.LIGHT_PURPLE + "LUCKY BLOCK: Lucky Crystal!");
        } else {
            for (int i = 0; i < 3; i++) {
                Zombie z = p.getWorld().spawn(l.clone().add(i - 1, 1, 0), Zombie.class);
                z.setCustomName(ChatColor.RED + "Lucky Mimic");
                z.addScoreboardTag("esnMythic");
            }
            p.sendMessage(ChatColor.RED + "LUCKY BLOCK: MIMIC AMBUSH!");
        }
    }

    private void addAscensionXp(Player p, int amount) {
        try {
            if (weekend()) amount *= 2;
            ensure(p);
            int xp = getInt(p, "asc_xp") + amount;
            int lv = getInt(p, "asc_level");
            while (xp >= 1000) {
                xp -= 1000;
                lv++;
                economy.deposit(p, 2500L + lv * 250L);
                p.sendMessage(ChatColor.LIGHT_PURPLE + "ASCENSION " + lv + " reached! Permanent progression reward granted.");
                if (lv % 10 == 0) p.getInventory().addItem(LootDrops.key());
            }
            setInt(p, "asc_xp", xp);
            setInt(p, "asc_level", lv);
        } catch (Exception ex) {
            plugin.getLogger().warning("Ascension XP: " + ex.getMessage());
        }
    }

    private void marketPayout(Player p, String activity, long base) {
        try {
            if (!marketFocus.equals(activity)) return;
            double mul = marketMultiplier * (weekend() ? 1.5 : 1.0);
            long amount = Math.max(1L, Math.round(base * mul));
            economy.deposit(p, amount);
            if (ThreadLocalRandom.current().nextDouble() < 0.08) p.sendActionBar(ChatColor.GREEN + "Hot Market +" + amount + " Coins");
        } catch (Exception ignored) {}
    }

    private void rotateMarketIfDue() {
        if (System.currentTimeMillis() >= nextMarketRotation) rotateMarket();
        finishRelicAuction();
    }

    private void rotateMarket() {
        String[] focus = {"ORES", "CROPS", "FISH", "MONSTERS"};
        marketFocus = focus[ThreadLocalRandom.current().nextInt(focus.length)];
        marketMultiplier = ThreadLocalRandom.current().nextBoolean() ? 1.5 : 2.0;
        nextMarketRotation = System.currentTimeMillis() + 6 * 60 * 60_000L;
        if (!Bukkit.getOnlinePlayers().isEmpty())
            Bukkit.broadcastMessage(ChatColor.GREEN + "ESN MARKET ROTATION: " + marketFocus + " payouts are now x" + marketMultiplier + "!");
    }

    private void tickSystems() {
        challenges.entrySet().removeIf(e -> e.getValue().expires() < System.currentTimeMillis());
        finishRelicAuction();
        combatLog.entrySet().removeIf(e -> Bukkit.getPlayer(e.getKey()) == null);
        bossDamage.entrySet().removeIf(e -> {
            Entity z = Bukkit.getEntity(e.getKey());
            return z == null || !z.isValid() || z.isDead();
        });
    }

    private void ensureWorldContent() {
        if (!plugin.getConfig().getBoolean("fun-expansion.generated", false)) buildWorldContent(false);
    }

    private void buildWorldContent(boolean force) {
        if (!force && plugin.getConfig().getBoolean("fun-expansion.generated", false)) return;
        if (Bukkit.getWorlds().isEmpty()) return;
        World w = Bukkit.getWorlds().get(0);
        Location s = w.getSpawnLocation();

        Location parkour = safeBase(w, s.getBlockX() + 420, s.getBlockZ(), 70);
        Location maze = safeBase(w, s.getBlockX() - 420, s.getBlockZ(), 55);
        Location temple = safeBase(w, s.getBlockX(), s.getBlockZ() + 420, 55);
        Location duel = safeBase(w, s.getBlockX(), s.getBlockZ() - 420, 55);

        buildParkour(parkour);
        MazeResult mazeResult = buildMaze(maze);
        buildTemple(temple);
        buildDuelArena(duel);

        saveLocation("fun-expansion.parkour", parkour);
        saveLocation("fun-expansion.parkour-finish", parkour.clone().add(0, 61, 0));
        saveLocation("fun-expansion.maze", maze);
        saveLocation("fun-expansion.maze-finish", mazeResult.finish());
        saveLocation("fun-expansion.temple", temple.clone().add(0, 1, -6));
        saveLocation("fun-expansion.duel", duel);
        plugin.getConfig().set("fun-expansion.generated", true);
        plugin.saveConfig();
        plugin.getLogger().info("[ESNSMP] Fun expansion world content generated.");
    }

    private Location safeBase(World w, int x, int z, int lift) {
        int y = Math.min(w.getMaxHeight() - 80, w.getHighestBlockYAt(x, z) + lift);
        return new Location(w, x, y, z);
    }

    private void buildParkour(Location o) {
        World w = o.getWorld();
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) w.getBlockAt(o.getBlockX()+x,o.getBlockY(),o.getBlockZ()+z).setType(Material.SMOOTH_STONE);
        int[][] offsets = {{0,0},{3,1},{-2,4},{4,5},{1,8},{-4,10},{0,12},{4,14},{-2,17},{3,20},{0,22},{-4,25},{1,28},{4,31},{-1,34},{-4,37},{2,40},{4,44},{0,48},{-3,52},{0,56},{0,61}};
        for (int[] q : offsets) {
            Material m = q[1] == 61 ? Material.EMERALD_BLOCK : (q[1] % 2 == 0 ? Material.AMETHYST_BLOCK : Material.QUARTZ_BLOCK);
            w.getBlockAt(o.getBlockX()+q[0], o.getBlockY()+q[1], o.getBlockZ()).setType(m);
        }
        for (int y = 0; y <= 62; y++) {
            w.getBlockAt(o.getBlockX()+7,o.getBlockY()+y,o.getBlockZ()+7).setType(Material.GLASS);
            w.getBlockAt(o.getBlockX()-7,o.getBlockY()+y,o.getBlockZ()-7).setType(Material.GLASS);
        }
    }

    private MazeResult buildMaze(Location o) {
        int size = 21;
        int[][] grid = new int[size][size];
        for (int[] row : grid) Arrays.fill(row, 1);
        Random r = new Random(0xE5F00DL ^ System.currentTimeMillis());
        carve(grid, 1, 1, r);
        grid[1][0] = 0;
        grid[size-2][size-1] = 0;

        World w = o.getWorld();
        for (int x = 0; x < size; x++) for (int z = 0; z < size; z++) {
            w.getBlockAt(o.getBlockX()+x,o.getBlockY(),o.getBlockZ()+z).setType(Material.SMOOTH_STONE);
            for (int y = 1; y <= 3; y++)
                w.getBlockAt(o.getBlockX()+x,o.getBlockY()+y,o.getBlockZ()+z).setType(grid[x][z] == 1 ? Material.DEEPSLATE_BRICKS : Material.AIR);
        }
        Location finish = new Location(w, o.getBlockX()+size-2+.5, o.getBlockY()+1, o.getBlockZ()+size-2+.5);
        w.getBlockAt(size + o.getBlockX()-2, o.getBlockY()+1, size + o.getBlockZ()-2).setType(Material.EMERALD_BLOCK);
        return new MazeResult(finish);
    }

    private void carve(int[][] g, int x, int z, Random r) {
        g[x][z] = 0;
        List<int[]> dirs = new ArrayList<>(List.of(new int[]{2,0},new int[]{-2,0},new int[]{0,2},new int[]{0,-2}));
        Collections.shuffle(dirs, r);
        for (int[] d : dirs) {
            int nx=x+d[0], nz=z+d[1];
            if (nx<=0||nz<=0||nx>=g.length-1||nz>=g.length-1||g[nx][nz]==0) continue;
            g[x+d[0]/2][z+d[1]/2]=0;
            carve(g,nx,nz,r);
        }
    }

    private void buildTemple(Location o) {
        World w = o.getWorld();
        for (int x=-8;x<=8;x++) for(int z=-8;z<=8;z++) {
            w.getBlockAt(o.getBlockX()+x,o.getBlockY(),o.getBlockZ()+z).setType(Material.STONE_BRICKS);
            boolean edge = Math.abs(x)==8 || Math.abs(z)==8;
            for(int y=1;y<=6;y++) w.getBlockAt(o.getBlockX()+x,o.getBlockY()+y,o.getBlockZ()+z).setType(edge?Material.CHISELED_STONE_BRICKS:Material.AIR);
        }
        for(int z=-8;z<=-6;z++) for(int y=1;y<=3;y++) w.getBlockAt(o.getBlockX(),o.getBlockY()+y,o.getBlockZ()+z).setType(Material.AIR);
        w.getBlockAt(o.getBlockX(),o.getBlockY()+1,o.getBlockZ()+2).setType(Material.GOLD_BLOCK);
        w.getBlockAt(o.getBlockX(),o.getBlockY()+2,o.getBlockZ()+2).setType(Material.END_PORTAL_FRAME);
    }

    private void buildDuelArena(Location o) {
        World w=o.getWorld();
        for(int x=-12;x<=12;x++) for(int z=-12;z<=12;z++) {
            w.getBlockAt(o.getBlockX()+x,o.getBlockY(),o.getBlockZ()+z).setType((x+z)%2==0?Material.POLISHED_DEEPSLATE:Material.SMOOTH_STONE);
            if(Math.abs(x)==12||Math.abs(z)==12) for(int y=1;y<=4;y++) w.getBlockAt(o.getBlockX()+x,o.getBlockY()+y,o.getBlockZ()+z).setType(Material.GLASS);
            else for(int y=1;y<=4;y++) w.getBlockAt(o.getBlockX()+x,o.getBlockY()+y,o.getBlockZ()+z).setType(Material.AIR);
        }
    }

    private void saveLocation(String key, Location l) {
        plugin.getConfig().set(key+".world", l.getWorld().getName());
        plugin.getConfig().set(key+".x", l.getX());
        plugin.getConfig().set(key+".y", l.getY());
        plugin.getConfig().set(key+".z", l.getZ());
        plugin.saveConfig();
    }

    private Location loadLocation(String key) {
        String world = plugin.getConfig().getString(key+".world");
        if (world == null) return null;
        World w = Bukkit.getWorld(world);
        if (w == null) return null;
        return new Location(w, plugin.getConfig().getDouble(key+".x"), plugin.getConfig().getDouble(key+".y"), plugin.getConfig().getDouble(key+".z"));
    }

    private boolean sameWorldNear(Location a, Location b, double d) {
        return a.getWorld() != null && a.getWorld().equals(b.getWorld()) && a.distanceSquared(b) <= d*d;
    }

    private ItemStack loreFragment() {
        ItemStack i = new ItemStack(Material.PAPER);
        ItemMeta m = i.getItemMeta();
        m.setDisplayName(ChatColor.GOLD + "ESN Lore Fragment");
        m.setLore(List.of(ChatColor.LIGHT_PURPLE + "A fragment of the hidden ESN story.", ChatColor.GRAY + "Three fragments awaken a secret boss."));
        m.getPersistentDataContainer().set(loreFragment, PersistentDataType.BYTE, (byte)1);
        i.setItemMeta(m);
        return i;
    }

    private boolean consumeFragments(Player p, int needed) {
        int count=0;
        for(ItemStack i:p.getInventory().getContents()) if(isFragment(i)) count+=i.getAmount();
        if(count<needed) return false;
        for(int slot=0;slot<p.getInventory().getSize()&&needed>0;slot++){
            ItemStack i=p.getInventory().getItem(slot);
            if(!isFragment(i)) continue;
            int take=Math.min(needed,i.getAmount());
            i.setAmount(i.getAmount()-take);
            needed-=take;
            if(i.getAmount()<=0)p.getInventory().setItem(slot,null);
        }
        return true;
    }

    private boolean isFragment(ItemStack i) {
        return i!=null&&i.hasItemMeta()&&i.getItemMeta().getPersistentDataContainer().has(loreFragment,PersistentDataType.BYTE);
    }

    private ItemStack customFish(String name, Material mat, String rarity) {
        ItemStack i=new ItemStack(mat);
        ItemMeta m=i.getItemMeta();
        m.setDisplayName((rarity.equals("LEGENDARY")?ChatColor.GOLD:ChatColor.LIGHT_PURPLE)+name);
        m.setLore(List.of(ChatColor.GRAY+"Custom ESN Fish",ChatColor.AQUA+"Rarity: "+rarity));
        m.getPersistentDataContainer().set(customFish,PersistentDataType.STRING,rarity);
        i.setItemMeta(m);
        return i;
    }

    private boolean isBoss(LivingEntity e) {
        Set<String> t=e.getScoreboardTags();
        return t.contains("esnWorldBoss")||t.contains("esnBiomeBoss")||t.contains("esnSwampBoss")||t.contains("esnAdventureBoss")||t.contains("esnSeasonFinale")||t.contains("esnSecretBoss");
    }

    private Player damager(Entity e) {
        if(e instanceof Player p)return p;
        if(e instanceof Projectile pr&&pr.getShooter() instanceof Player p)return p;
        return null;
    }

    private String display(ItemStack i) {
        if(i==null||!i.hasItemMeta()||!i.getItemMeta().hasDisplayName())return "";
        return ChatColor.stripColor(i.getItemMeta().getDisplayName()).toUpperCase(Locale.ROOT);
    }

    private boolean weekend() {
        DayOfWeek d=LocalDate.now().getDayOfWeek();
        return d==DayOfWeek.FRIDAY||d==DayOfWeek.SATURDAY||d==DayOfWeek.SUNDAY;
    }

    private String seasonName() {
        int m=LocalDate.now().getMonthValue();
        return m<=3?"FROSTBORN":m<=6?"BLOOM":m<=9?"SOLAR":"BLOODMOON";
    }

    private String reputationName(int rep) {
        if(rep>=100)return "HERO";
        if(rep<=-100)return "OUTLAW";
        return "NEUTRAL";
    }

    private void resetDailyIfNeeded(Player p)throws Exception{
        ensure(p);
        String today=LocalDate.now().toString(), day=getString(p,"daily_day");
        if(today.equals(day))return;
        try(PreparedStatement q=db.prepareStatement("UPDATE fun_players SET daily_day=?,daily_kills=0,daily_ores=0,daily_fish=0,daily_claimed=0 WHERE uuid=?")){
            q.setString(1,today);q.setString(2,p.getUniqueId().toString());q.executeUpdate();
        }
    }

    private void ensure(Player p)throws Exception{
        try(PreparedStatement q=db.prepareStatement("INSERT OR IGNORE INTO fun_players(uuid) VALUES(?)")){
            q.setString(1,p.getUniqueId().toString());q.executeUpdate();
        }
    }

    private int getInt(Player p,String col)throws Exception{
        ensure(p);
        try(PreparedStatement q=db.prepareStatement("SELECT "+col+" FROM fun_players WHERE uuid=?")){
            q.setString(1,p.getUniqueId().toString());
            try(ResultSet r=q.executeQuery()){return r.next()?r.getInt(1):0;}
        }
    }

    private long getLong(Player p,String col)throws Exception{
        ensure(p);
        try(PreparedStatement q=db.prepareStatement("SELECT "+col+" FROM fun_players WHERE uuid=?")){
            q.setString(1,p.getUniqueId().toString());
            try(ResultSet r=q.executeQuery()){return r.next()?r.getLong(1):0L;}
        }
    }

    private String getString(Player p,String col)throws Exception{
        ensure(p);
        try(PreparedStatement q=db.prepareStatement("SELECT "+col+" FROM fun_players WHERE uuid=?")){
            q.setString(1,p.getUniqueId().toString());
            try(ResultSet r=q.executeQuery()){return r.next()?Optional.ofNullable(r.getString(1)).orElse(""):"";}
        }
    }

    private void setInt(Player p,String col,int v)throws Exception{
        ensure(p);
        try(PreparedStatement q=db.prepareStatement("UPDATE fun_players SET "+col+"=? WHERE uuid=?")){
            q.setInt(1,v);q.setString(2,p.getUniqueId().toString());q.executeUpdate();
        }
    }

    private void setLong(Player p,String col,long v)throws Exception{
        ensure(p);
        try(PreparedStatement q=db.prepareStatement("UPDATE fun_players SET "+col+"=? WHERE uuid=?")){
            q.setLong(1,v);q.setString(2,p.getUniqueId().toString());q.executeUpdate();
        }
    }

    private void setString(Player p,String col,String v)throws Exception{
        ensure(p);
        try(PreparedStatement q=db.prepareStatement("UPDATE fun_players SET "+col+"=? WHERE uuid=?")){
            q.setString(1,v);q.setString(2,p.getUniqueId().toString());q.executeUpdate();
        }
    }

    private void addInt(Player p,String col,int n)throws Exception{setInt(p,col,getInt(p,col)+n);}
    private void addIntQuiet(Player p,String col,int n){try{addInt(p,col,n);}catch(Exception ignored){}}

    private int global(String k){
        try{
            try(PreparedStatement q=db.prepareStatement("SELECT v FROM fun_global WHERE k=?")){
                q.setString(1,k);
                try(ResultSet r=q.executeQuery()){return r.next()?r.getInt(1):0;}
            }
        }catch(Exception e){return 0;}
    }

    private void setGlobal(String k,int v){
        try(PreparedStatement q=db.prepareStatement("INSERT INTO fun_global(k,v) VALUES(?,?) ON CONFLICT(k) DO UPDATE SET v=excluded.v")){
            q.setString(1,k);q.setInt(2,v);q.executeUpdate();
        }catch(Exception ignored){}
    }

    private ItemStack item(Material m,String name,String... lore){
        ItemStack i=new ItemStack(m);ItemMeta im=i.getItemMeta();im.setDisplayName(name);im.setLore(Arrays.asList(lore));i.setItemMeta(im);return i;
    }

    private MenuAction a(String name,Material material,String command){return new MenuAction(name,material,command);}
    private void run(Player p,String command){p.closeInventory();Bukkit.dispatchCommand(p,command);}

    @Override
    public void close() throws Exception {
        mounts.values().forEach(id->{Entity e=Bukkit.getEntity(id);if(e!=null)e.remove();});
        mounts.clear(); parkourStart.clear(); mazeRuns.clear(); templeStage.clear(); templeTarget.clear();
        challenges.clear(); duels.clear(); pendingReturn.clear(); matchmaking.clear(); tournamentQueue.clear();
        tournamentMatches.clear(); tournamentWinners.clear(); predictions.clear(); combatLog.clear(); lastRecap.clear();
        bossDamage.clear(); damageMeterEnabled.clear();
        if(db!=null&&!db.isClosed())db.close();
    }

    private record MenuAction(String name,Material material,String command){}
    private record Challenge(UUID challenger,long wager,long expires){}
    private record Duel(UUID a,UUID b,long wager,Location aBack,Location bBack,boolean tournament){}
    private record Match(UUID a,UUID b){}
    private record Hit(String source,double damage,long at){}
    private record MazeResult(Location finish){}
}
