package com.esn.smp.gameplay;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@SuppressWarnings("deprecation")
public final class RealmProgressionSystem implements Listener, CommandExecutor, AutoCloseable {
    private static final String HUB = ChatColor.DARK_PURPLE + "Realm Ascension";
    private static final String QUESTS = ChatColor.GOLD + "Realm Questlines";
    private static final String FACTIONS = ChatColor.GREEN + "Realm Factions";
    private static final String MASTERY = ChatColor.AQUA + "Realm Mastery";
    private static final String SKILLS = ChatColor.LIGHT_PURPLE + "Realm Skill Trees";
    private static final String DUNGEONS = ChatColor.DARK_AQUA + "Realm Dungeons";
    private static final String RAIDS = ChatColor.DARK_RED + "Realm Raids";
    private static final String DIFFICULTY = ChatColor.RED + "Realm Difficulty";
    private static final String CRAFTING = ChatColor.GOLD + "Realm Crafting";
    private static final String COLLECTIONS = ChatColor.YELLOW + "Realm Collections";
    private static final String LORE = ChatColor.DARK_PURPLE + "Realm Lore";
    private static final String EVENTS = ChatColor.RED + "Realm World Events";
    private static final String PVP = ChatColor.DARK_RED + "Realm PvP Arenas";
    private static final String GUILDS = ChatColor.BLUE + "Realm Territories";
    private static final String CARAVANS = ChatColor.GOLD + "Realm Caravans";
    private static final String PETS = ChatColor.GREEN + "Realm Pets";
    private static final String MOUNTS = ChatColor.AQUA + "Realm Mounts";
    private static final String ACHIEVEMENTS = ChatColor.GOLD + "Realm Achievements";
    private static final String TITLES = ChatColor.LIGHT_PURPLE + "Realm Titles";
    private static final String PROFILE = ChatColor.AQUA + "Realm Profile";
    private static final String LEADERBOARD = ChatColor.GOLD + "Realm Leaderboard";
    private static final String SECRET = ChatColor.DARK_PURPLE + "The Shattered Realm";
    private static final String TOWN = ChatColor.GREEN + "Realm Town • ";

    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration data;
    private final NamespacedKey rarityKey;
    private final NamespacedKey realmGearKey;
    private final Map<UUID, UUID> activePets = new HashMap<>();
    private final Map<UUID, UUID> activeMounts = new HashMap<>();
    private final Map<String, Integer> threat = new HashMap<>();
    private final Map<String, Long> nextWorldEvent = new HashMap<>();
    private final Map<UUID, String> selectedRealm = new HashMap<>();
    private final Map<UUID, Long> moveCooldown = new HashMap<>();

    public RealmProgressionSystem(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "realm-ascension.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        this.rarityKey = new NamespacedKey(plugin, "realm_rarity");
        this.realmGearKey = new NamespacedKey(plugin, "realm_gear");
        for (String realm : realms()) threat.put(realm, data.getInt("threat." + realm, 0));
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickThreatAndCaravans, 20L * 60L, 20L * 60L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::ensureTownNPCs, 20L * 30L, 20L * 60L);
        Bukkit.getScheduler().runTaskLater(plugin, this::ensureTownNPCs, 20L * 45L);
        plugin.getLogger().info("[ESN Realms] Realm Ascension progression online.");
    }

    @Override
    public void close() {
        save();
        for (UUID id : new HashSet<>(activePets.keySet())) dismissEntity(activePets.remove(id));
        for (UUID id : new HashSet<>(activeMounts.keySet())) dismissEntity(activeMounts.remove(id));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length == 0) {
            openHub(p);
            return true;
        }
        String realm = normalize(args[0]);
        if (realm != null) {
            selectedRealm.put(p.getUniqueId(), realm);
            openProfile(p);
        } else openHub(p);
        return true;
    }

    private void openHub(Player p) {
        Inventory v = Bukkit.createInventory(null, 54, HUB);
        v.setItem(4, item(Material.NETHER_STAR, ChatColor.GOLD + "ESN Realm Ascension",
                ChatColor.GRAY + "One progression system connecting every ESN Realm",
                ChatColor.GRAY + "Realm Score: " + ChatColor.WHITE + totalScore(p)));

        v.setItem(10, item(Material.WRITABLE_BOOK, ChatColor.GOLD + "Questlines",
                ChatColor.GRAY + "24 story quests per realm"));
        v.setItem(11, item(Material.EMERALD, ChatColor.GREEN + "Towns & Factions",
                ChatColor.GRAY + "Reputation, NPC towns and faction rewards"));
        v.setItem(12, item(Material.EXPERIENCE_BOTTLE, ChatColor.AQUA + "Realm Mastery",
                ChatColor.GRAY + "50 levels in every realm"));
        v.setItem(13, item(Material.ENCHANTING_TABLE, ChatColor.LIGHT_PURPLE + "Skill Trees",
                ChatColor.GRAY + "Spend mastery points on realm powers"));
        v.setItem(14, item(Material.TRIAL_KEY, ChatColor.DARK_AQUA + "Dungeons",
                ChatColor.GRAY + "Generated rooms, elites, puzzles and loot"));
        v.setItem(15, item(Material.DRAGON_HEAD, ChatColor.DARK_RED + "Raids",
                ChatColor.GRAY + "Multi-stage endgame encounters"));
        v.setItem(16, item(Material.NETHERITE_SWORD, ChatColor.RED + "Difficulty",
                ChatColor.GRAY + "Normal • Heroic • Mythic"));

        v.setItem(19, item(Material.SMITHING_TABLE, ChatColor.GOLD + "Gear & Crafting",
                ChatColor.GRAY + "Realm sets, rarity rolls and Boss Cores"));
        v.setItem(20, item(Material.BUNDLE, ChatColor.YELLOW + "Collections",
                ChatColor.GRAY + "Runes, tablets, relics and hidden objects"));
        v.setItem(21, item(Material.BOOKSHELF, ChatColor.DARK_PURPLE + "Lore",
                ChatColor.GRAY + "Discover the history of every realm"));
        v.setItem(22, item(Material.BELL, ChatColor.RED + "World Events",
                ChatColor.GRAY + "Threat meter and dynamic realm events"));
        v.setItem(23, item(Material.DIAMOND_SWORD, ChatColor.DARK_RED + "PvP Arenas",
                ChatColor.GRAY + "Realm-themed competitive arenas"));
        v.setItem(24, item(Material.BLUE_BANNER, ChatColor.BLUE + "Guild Territories",
                ChatColor.GRAY + "Claim realm territory for your team"));
        v.setItem(25, item(Material.CHEST_MINECART, ChatColor.GOLD + "Caravans",
                ChatColor.GRAY + "Defend traveling realm merchants"));

        v.setItem(28, item(Material.WOLF_SPAWN_EGG, ChatColor.GREEN + "Realm Pets",
                ChatColor.GRAY + "Unlock and summon realm companions"));
        v.setItem(29, item(Material.SADDLE, ChatColor.AQUA + "Realm Mounts",
                ChatColor.GRAY + "Unlock realm-exclusive mounts"));
        v.setItem(30, item(Material.TOTEM_OF_UNDYING, ChatColor.GOLD + "Achievements",
                ChatColor.GRAY + "Long-term realm challenges"));
        v.setItem(31, item(Material.NAME_TAG, ChatColor.LIGHT_PURPLE + "Titles",
                ChatColor.GRAY + "Equip titles earned through mastery"));
        v.setItem(32, item(Material.PLAYER_HEAD, ChatColor.AQUA + "Realm Profile",
                ChatColor.GRAY + "Your complete realm career"));
        v.setItem(33, item(Material.LECTERN, ChatColor.GOLD + "Leaderboards",
                ChatColor.GRAY + "Top Realm Score and mastery"));
        boolean shattered = shatteredUnlocked(p);
        v.setItem(34, item(shattered ? Material.REINFORCED_DEEPSLATE : Material.BARRIER,
                shattered ? ChatColor.DARK_PURPLE + "The Shattered Realm" : ChatColor.DARK_GRAY + "???",
                shattered ? ChatColor.LIGHT_PURPLE + "The hidden eighth realm is open." :
                        ChatColor.GRAY + "Recover all seven portal fragments to reveal this realm."));

        v.setItem(40, item(Material.ENDER_EYE, ChatColor.GOLD + "Travel",
                ChatColor.GRAY + "Return to the ESN Realms travel menu"));
        v.setItem(49, item(Material.BARRIER, ChatColor.RED + "Close"));
        p.openInventory(v);
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!isMenu(title)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
        if (name == null) return;

        if (name.equals("Back")) { openHub(p); return; }
        if (name.equals("Close")) { p.closeInventory(); return; }

        if (title.equals(HUB)) {
            switch (name) {
                case "Questlines" -> openRealmPicker(p, QUESTS, "quests");
                case "Towns & Factions" -> openFactions(p);
                case "Realm Mastery" -> openMastery(p);
                case "Skill Trees" -> openRealmPicker(p, SKILLS, "skills");
                case "Dungeons" -> openRealmPicker(p, DUNGEONS, "dungeons");
                case "Raids" -> openRealmPicker(p, RAIDS, "raids");
                case "Difficulty" -> openDifficulty(p);
                case "Gear & Crafting" -> openRealmPicker(p, CRAFTING, "crafting");
                case "Collections" -> openCollections(p);
                case "Lore" -> openLore(p);
                case "World Events" -> openEvents(p);
                case "PvP Arenas" -> openRealmPicker(p, PVP, "pvp");
                case "Guild Territories" -> openGuilds(p);
                case "Caravans" -> openCaravans(p);
                case "Realm Pets" -> openPets(p);
                case "Realm Mounts" -> openMounts(p);
                case "Achievements" -> openAchievements(p);
                case "Titles" -> openTitles(p);
                case "Realm Profile" -> openProfile(p);
                case "Leaderboards" -> openLeaderboards(p);
                case "The Shattered Realm" -> p.performCommand("realms shattered");
                case "Travel" -> p.performCommand("realms");
            }
            return;
        }

        if (title.startsWith(QUESTS)) handleRealmPage(p, name, "quests");
        else if (title.equals(FACTIONS)) handleFactionClick(p, name);
        else if (title.equals(MASTERY)) handleMasteryClick(p, name);
        else if (title.startsWith(SKILLS)) handleRealmPage(p, name, "skills");
        else if (title.startsWith(DUNGEONS)) handleRealmPage(p, name, "dungeons");
        else if (title.startsWith(RAIDS)) handleRealmPage(p, name, "raids");
        else if (title.equals(DIFFICULTY)) handleDifficulty(p, name);
        else if (title.startsWith(CRAFTING)) handleRealmPage(p, name, "crafting");
        else if (title.equals(COLLECTIONS)) handleCollectionClick(p, name);
        else if (title.equals(LORE)) handleLoreClick(p, name);
        else if (title.equals(EVENTS)) handleEventClick(p, name);
        else if (title.startsWith(PVP)) handleRealmPage(p, name, "pvp");
        else if (title.equals(GUILDS)) handleGuildClick(p, name);
        else if (title.equals(CARAVANS)) handleCaravanClick(p, name);
        else if (title.equals(PETS)) handlePetClick(p, name);
        else if (title.equals(MOUNTS)) handleMountClick(p, name);
        else if (title.equals(ACHIEVEMENTS)) openAchievements(p);
        else if (title.equals(TITLES)) handleTitleClick(p, name);
        else if (title.equals(PROFILE)) handleProfileClick(p, name);
        else if (title.equals(LEADERBOARD)) openLeaderboards(p);
        else if (title.equals(SECRET)) handleSecretClick(p, name);
        else if (title.startsWith(TOWN)) handleTownClick(p, name);
    }

    private boolean isMenu(String title) {
        return title.equals(HUB) || title.equals(FACTIONS) || title.equals(MASTERY) ||
                title.equals(DIFFICULTY) || title.equals(COLLECTIONS) || title.equals(LORE) ||
                title.equals(EVENTS) || title.equals(GUILDS) || title.equals(CARAVANS) ||
                title.equals(PETS) || title.equals(MOUNTS) || title.equals(ACHIEVEMENTS) ||
                title.equals(TITLES) || title.equals(PROFILE) || title.equals(LEADERBOARD) ||
                title.equals(SECRET) || title.startsWith(QUESTS) || title.startsWith(SKILLS) ||
                title.startsWith(DUNGEONS) || title.startsWith(RAIDS) || title.startsWith(CRAFTING) ||
                title.startsWith(PVP) || title.startsWith(TOWN);
    }

    private void openRealmPicker(Player p, String baseTitle, String mode) {
        Inventory v = Bukkit.createInventory(null, 54, baseTitle + ChatColor.DARK_GRAY + " • Select Realm");
        int[] slots = {10,11,12,13,14,15,16,22};
        int i = 0;
        for (String realm : realms()) {
            v.setItem(slots[i++], item(icon(realm), color(realm) + display(realm),
                    ChatColor.GRAY + realmSummary(p, realm, mode),
                    ChatColor.YELLOW + "Click to open"));
        }
        if (shatteredUnlocked(p)) {
            v.setItem(31, item(Material.REINFORCED_DEEPSLATE, ChatColor.DARK_PURPLE + "Shattered Realm",
                    ChatColor.GRAY + realmSummary(p, "shattered", mode), ChatColor.YELLOW + "Click to open"));
        }
        v.setItem(49, item(Material.ARROW, ChatColor.YELLOW + "Back"));
        p.openInventory(v);
    }

    private void handleRealmPage(Player p, String name, String mode) {
        String realm = byDisplay(name);
        if (realm == null) {
            if (name.equals("Back")) openHub(p);
            return;
        }
        selectedRealm.put(p.getUniqueId(), realm);
        switch (mode) {
            case "quests" -> openQuestDetail(p, realm);
            case "skills" -> openSkillTree(p, realm);
            case "dungeons" -> openDungeon(p, realm);
            case "raids" -> openRaid(p, realm);
            case "crafting" -> openCrafting(p, realm);
            case "pvp" -> openPvp(p, realm);
        }
    }

    private void openQuestDetail(Player p, String realm) {
        Inventory v = Bukkit.createInventory(null, 54, QUESTS + ChatColor.DARK_GRAY + " • " + display(realm));
        int stage = getInt(p, realm, "quest-stage", 1);
        for (int q = 1; q <= 24; q++) {
            int slot = q - 1;
            if (slot >= 45) break;
            boolean complete = q < stage;
            boolean active = q == stage;
            v.setItem(slot, item(complete ? Material.LIME_DYE : active ? Material.WRITABLE_BOOK : Material.GRAY_DYE,
                    (complete ? ChatColor.GREEN : active ? ChatColor.GOLD : ChatColor.DARK_GRAY) +
                            "Chapter " + q + ": " + questName(realm, q),
                    ChatColor.GRAY + questObjective(q),
                    complete ? ChatColor.GREEN + "COMPLETE" :
                            active ? ChatColor.YELLOW + "Progress: " + questProgress(p, realm, q) + "/" + questTarget(q) :
                                    ChatColor.DARK_GRAY + "LOCKED"));
        }
        v.setItem(49, item(Material.ARROW, ChatColor.YELLOW + "Back"));
        p.openInventory(v);
    }

    private String questName(String realm, int q) {
        String[] arc = {"Arrival","First Blood","Broken Roads","Lost Relic","Hunt Begins","Ancient Signal",
                "Ruined Outpost","Elite Threat","Faction Trial","Hidden Vault","Gathering Storm","Miniboss",
                "Deep Expedition","Second Trial","Realm Secret","War Preparations","Boss Hunt","Aftermath",
                "Mythic Signs","Final Relic","Faction Oath","Master Trial","Raid Key","Realm Ascendant"};
        return display(realm) + " — " + arc[(q - 1) % arc.length];
    }

    private String questObjective(int q) {
        return switch ((q - 1) % 6) {
            case 0 -> "Defeat native realm creatures.";
            case 1 -> "Discover realm landmarks and secrets.";
            case 2 -> "Increase faction reputation.";
            case 3 -> "Defeat an elite or miniboss.";
            case 4 -> "Complete a dungeon/event objective.";
            default -> "Reach the next Mastery milestone.";
        };
    }

    private int questTarget(int q) { return 5 + (q / 3) * 2; }

    private int questProgress(Player p, String realm, int q) {
        return Math.min(questTarget(q), getInt(p, realm, "quest-progress", 0));
    }

    private void openFactions(Player p) {
        Inventory v = Bukkit.createInventory(null, 54, FACTIONS);
        int[] slots = {10,11,12,13,14,15,16,22};
        int i = 0;
        for (String realm : realms()) {
            int rep = getInt(p, realm, "reputation", 0);
            v.setItem(slots[i++], item(icon(realm), color(realm) + faction(realm),
                    ChatColor.GRAY + "Reputation: " + rep + "/10000",
                    ChatColor.GRAY + "Rank: " + factionRank(rep),
                    ChatColor.YELLOW + "Click to visit faction town"));
        }
        v.setItem(49, item(Material.ARROW, ChatColor.YELLOW + "Back"));
        p.openInventory(v);
    }

    private void handleFactionClick(Player p, String name) {
        String realm = factionRealm(name);
        if (realm == null) return;
        selectedRealm.put(p.getUniqueId(), realm);
        openTown(p, realm);
    }

    private void openTown(Player p, String realm) {
        Inventory v = Bukkit.createInventory(null, 45, TOWN + display(realm));
        int rep = getInt(p, realm, "reputation", 0);
        v.setItem(4, item(icon(realm), color(realm) + faction(realm),
                ChatColor.GRAY + "Reputation: " + rep + "/10000",
                ChatColor.GRAY + "Faction rank: " + factionRank(rep)));
        v.setItem(10, item(Material.ANVIL, ChatColor.GOLD + "Realm Blacksmith",
                ChatColor.GRAY + "Craft and upgrade realm equipment"));
        v.setItem(11, item(Material.WRITABLE_BOOK, ChatColor.YELLOW + "Quest Master",
                ChatColor.GRAY + "Continue the " + display(realm) + " storyline"));
        v.setItem(12, item(Material.EMERALD, ChatColor.GREEN + "Faction Merchant",
                ChatColor.GRAY + "Reputation rewards"));
        v.setItem(13, item(Material.TARGET, ChatColor.RED + "Bounty Board",
                ChatColor.GRAY + "Realm hunts and elite targets"));
        v.setItem(14, item(Material.BOOKSHELF, ChatColor.LIGHT_PURPLE + "Lorekeeper",
                ChatColor.GRAY + "Realm history and secret clues"));
        v.setItem(15, item(Material.BREWING_STAND, ChatColor.AQUA + "Alchemist",
                ChatColor.GRAY + "Buy a temporary faction blessing"));
        v.setItem(16, item(Material.ENDER_EYE, ChatColor.RED + "Boss Tracker",
                ChatColor.GRAY + "View bosses, raids and threat"));
        v.setItem(22, item(Material.COMPASS, ChatColor.AQUA + "Travel to Town",
                ChatColor.GRAY + "Teleport to the physical NPC settlement"));
        v.setItem(40, item(Material.ARROW, ChatColor.YELLOW + "Back"));
        p.openInventory(v);
    }

    private void handleTownClick(Player p, String name) {
        String realm = selectedRealm.get(p.getUniqueId());
        if (realm == null) return;
        switch (name) {
            case "Realm Blacksmith" -> openCrafting(p, realm);
            case "Quest Master" -> openQuestDetail(p, realm);
            case "Faction Merchant" -> claimFactionReward(p, realm);
            case "Bounty Board" -> grantBounty(p, realm);
            case "Lorekeeper" -> openLore(p);
            case "Alchemist" -> buyBlessing(p, realm);
            case "Boss Tracker" -> openEvents(p);
            case "Travel to Town" -> teleportSite(p, realm, 180, realmY(realm) + 2, 0);
            case "Back" -> openFactions(p);
        }
    }

    private void openMastery(Player p) {
        Inventory v = Bukkit.createInventory(null, 54, MASTERY);
        int[] slots = {10,11,12,13,14,15,16,22};
        int i = 0;
        for (String realm : realms()) {
            int level = masteryLevel(p, realm);
            int xp = getInt(p, realm, "mastery-xp", 0);
            v.setItem(slots[i++], item(icon(realm), color(realm) + display(realm),
                    ChatColor.GRAY + "Mastery: " + level + "/50",
                    ChatColor.GRAY + "XP: " + xp + "/" + masteryNeed(level),
                    ChatColor.GRAY + "Skill Points: " + skillPoints(p, realm),
                    level >= 10 ? ChatColor.GREEN + "Portal fragment discovered" : ChatColor.DARK_GRAY + "Portal fragment at Mastery 10"));
        }
        v.setItem(40, item(Material.NETHER_STAR, ChatColor.GOLD + "Total Realm Score",
                ChatColor.WHITE + String.valueOf(totalScore(p))));
        v.setItem(49, item(Material.ARROW, ChatColor.YELLOW + "Back"));
        p.openInventory(v);
    }

    private void handleMasteryClick(Player p, String name) {
        String realm = byDisplay(name);
        if (realm != null) { selectedRealm.put(p.getUniqueId(), realm); openSkillTree(p, realm); }
    }

    private void openSkillTree(Player p, String realm) {
        Inventory v = Bukkit.createInventory(null, 45, SKILLS + ChatColor.DARK_GRAY + " • " + display(realm));
        int points = skillPoints(p, realm);
        v.setItem(4, item(icon(realm), color(realm) + display(realm),
                ChatColor.GRAY + "Available points: " + points,
                ChatColor.GRAY + "Earn 1 point per Mastery level."));
        String[] nodes = skillNames(realm);
        Material[] mats = {Material.FEATHER,Material.IRON_CHESTPLATE,Material.DIAMOND_SWORD,Material.CHEST,Material.GOLDEN_APPLE,Material.ENDER_PEARL};
        for (int i = 0; i < 6; i++) {
            int rank = getInt(p, realm, "skill." + i, 0);
            v.setItem(10 + i, item(mats[i], ChatColor.LIGHT_PURPLE + nodes[i],
                    ChatColor.GRAY + "Rank " + rank + "/5",
                    ChatColor.GRAY + skillDescription(realm, i),
                    rank < 5 && points > 0 ? ChatColor.YELLOW + "Click to spend 1 point" : ChatColor.DARK_GRAY + "Unavailable"));
        }
        v.setItem(31, item(Material.NETHER_STAR, ChatColor.GOLD + "Full Tree Bonus",
                ChatColor.GRAY + "Unlock all 30 ranks for the Ascendant passive."));
        v.setItem(40, item(Material.ARROW, ChatColor.YELLOW + "Back"));
        p.openInventory(v);
    }

    private void handleSkillTree(Player p, String name) {
        String realm = selectedRealm.get(p.getUniqueId());
        if (realm == null) return;
        String[] nodes = skillNames(realm);
        for (int i = 0; i < nodes.length; i++) {
            if (name.equals(nodes[i])) {
                int points = skillPoints(p, realm);
                int rank = getInt(p, realm, "skill." + i, 0);
                if (points <= 0 || rank >= 5) return;
                setInt(p, realm, "skill." + i, rank + 1);
                openSkillTree(p, realm);
                return;
            }
        }
        if (name.equals("Back")) openRealmPicker(p, SKILLS, "skills");
    }

    private void openDungeon(Player p, String realm) {
        Inventory v = Bukkit.createInventory(null, 36, DUNGEONS + ChatColor.DARK_GRAY + " • " + display(realm));
        int completions = getInt(p, realm, "dungeons", 0);
        v.setItem(4, item(icon(realm), color(realm) + display(realm) + " Dungeon",
                ChatColor.GRAY + "Completions: " + completions,
                ChatColor.GRAY + "Difficulty scales with your selected mode."));
        v.setItem(10, item(Material.TRIAL_KEY, ChatColor.GREEN + "Enter Dungeon",
                ChatColor.GRAY + "Procedural room route",
                ChatColor.GRAY + "Mobs • traps • puzzle chambers • miniboss • vault"));
        v.setItem(12, item(Material.OMINOUS_TRIAL_KEY, ChatColor.RED + "Elite Dungeon",
                ChatColor.GRAY + "Requires Mastery 15",
                ChatColor.GRAY + "Improved Boss Core and relic chances"));
        v.setItem(14, item(Material.CHEST, ChatColor.GOLD + "Dungeon Rewards",
                ChatColor.GRAY + "Realm Essence • Relic Fragments • Boss Cores",
                ChatColor.GRAY + "Rare realm equipment rolls"));
        v.setItem(31, item(Material.ARROW, ChatColor.YELLOW + "Back"));
        p.openInventory(v);
    }

    private void openRaid(Player p, String realm) {
        Inventory v = Bukkit.createInventory(null, 36, RAIDS + ChatColor.DARK_GRAY + " • " + display(realm));
        int raids = getInt(p, realm, "raids", 0);
        v.setItem(4, item(Material.DRAGON_HEAD, ChatColor.DARK_RED + display(realm) + " Raid",
                ChatColor.GRAY + "Recommended: Mastery 20+",
                ChatColor.GRAY + "Clears: " + raids,
                ChatColor.GRAY + "4–12 players recommended"));
        v.setItem(10, item(Material.BEACON, ChatColor.RED + "Enter Raid",
                ChatColor.GRAY + "Five encounter stages + final raid boss"));
        v.setItem(12, item(Material.NETHER_STAR, ChatColor.GOLD + "Mythic Raid",
                ChatColor.GRAY + "Requires Mastery 35 + Mythic difficulty"));
        v.setItem(14, item(Material.CHEST, ChatColor.GOLD + "Raid Loot",
                ChatColor.GRAY + "Ancient Materials • Boss Cores • Mythic gear"));
        v.setItem(31, item(Material.ARROW, ChatColor.YELLOW + "Back"));
        p.openInventory(v);
    }

    private void openDifficulty(Player p) {
        Inventory v = Bukkit.createInventory(null, 27, DIFFICULTY);
        String current = getString(p, "difficulty", "NORMAL");
        v.setItem(10, item(Material.IRON_SWORD, ChatColor.GREEN + "Normal",
                ChatColor.GRAY + "Standard realm stats and rewards",
                current.equals("NORMAL") ? ChatColor.GREEN + "SELECTED" : ChatColor.YELLOW + "Click to select"));
        v.setItem(12, item(Material.DIAMOND_SWORD, ChatColor.GOLD + "Heroic",
                ChatColor.GRAY + "+50% enemy strength • +50% progression",
                current.equals("HEROIC") ? ChatColor.GREEN + "SELECTED" : ChatColor.YELLOW + "Click to select"));
        v.setItem(14, item(Material.NETHERITE_SWORD, ChatColor.RED + "Mythic",
                ChatColor.GRAY + "+100% enemy strength • +100% progression",
                ChatColor.GRAY + "Requires total Realm Mastery 80",
                current.equals("MYTHIC") ? ChatColor.GREEN + "SELECTED" : ChatColor.YELLOW + "Click to select"));
        v.setItem(22, item(Material.ARROW, ChatColor.YELLOW + "Back"));
        p.openInventory(v);
    }

    private void handleDifficulty(Player p, String name) {
        if (name.equals("Normal")) setString(p, "difficulty", "NORMAL");
        else if (name.equals("Heroic")) setString(p, "difficulty", "HEROIC");
        else if (name.equals("Mythic") && totalMastery(p) >= 80) setString(p, "difficulty", "MYTHIC");
        else if (name.equals("Back")) { openHub(p); return; }
        openDifficulty(p);
    }

    private void openCrafting(Player p, String realm) {
        Inventory v = Bukkit.createInventory(null, 54, CRAFTING + ChatColor.DARK_GRAY + " • " + display(realm));
        v.setItem(4, item(icon(realm), color(realm) + display(realm) + " Forge",
                ChatColor.GRAY + "Rarity rolls: Common → Ancient"));
        String[] pieces = {"Helmet","Chestplate","Leggings","Boots","Sword","Bow","Relic"};
        Material[] mats = {Material.DIAMOND_HELMET,Material.DIAMOND_CHESTPLATE,Material.DIAMOND_LEGGINGS,
                Material.DIAMOND_BOOTS,Material.DIAMOND_SWORD,Material.BOW,Material.HEART_OF_THE_SEA};
        for (int i = 0; i < pieces.length; i++) {
            v.setItem(10 + i, item(mats[i], ChatColor.GOLD + "Forge " + pieces[i],
                    ChatColor.GRAY + "Cost: Realm materials + mastery",
                    ChatColor.GRAY + "Rolls random rarity and bonus stats",
                    ChatColor.YELLOW + "Click to forge"));
        }
        v.setItem(28, item(Material.AMETHYST_SHARD, ChatColor.LIGHT_PURPLE + "Boss Core",
                ChatColor.GRAY + "Rare material from bosses/raids"));
        v.setItem(29, item(Material.ECHO_SHARD, ChatColor.DARK_PURPLE + "Ancient Material",
                ChatColor.GRAY + "Used for Ancient rarity upgrades"));
        v.setItem(30, item(Material.PRISMARINE_CRYSTALS, ChatColor.AQUA + "Realm Crystal",
                ChatColor.GRAY + "Refines realm equipment"));
        v.setItem(31, item(Material.SMITHING_TABLE, ChatColor.GREEN + "Upgrade Held Realm Gear",
                ChatColor.GRAY + "Improve rarity using Realm Score"));
        v.setItem(40, item(Material.ARROW, ChatColor.YELLOW + "Back"));
        p.openInventory(v);
    }

    private void openCollections(Player p) {
        Inventory v = Bukkit.createInventory(null, 54, COLLECTIONS);
        int[] slots = {10,11,12,13,14,15,16,22};
        int i = 0;
        for (String realm : realms()) {
            int found = getInt(p, realm, "collectibles", 0);
            int total = collectibleTotal(realm);
            v.setItem(slots[i++], item(icon(realm), color(realm) + display(realm) + " Collection",
                    ChatColor.GRAY + found + "/" + total + " discovered",
                    ChatColor.GRAY + collectibleName(realm),
                    found >= total ? ChatColor.GREEN + "COMPLETE" : ChatColor.YELLOW + "Explore structures to discover more"));
        }
        v.setItem(31, item(Material.ENDER_EYE, ChatColor.LIGHT_PURPLE + "Portal Fragments",
                ChatColor.GRAY + portalPieces(p) + "/7 recovered",
                portalPieces(p) >= 7 ? ChatColor.GREEN + "THE SHATTERED REALM IS REVEALED" :
                        ChatColor.GRAY + "Reach Mastery 10 in each main realm."));
        v.setItem(49, item(Material.ARROW, ChatColor.YELLOW + "Back"));
        p.openInventory(v);
    }

    private void handleCollectionClick(Player p, String name) {
        if (name.equals("Portal Fragments") && portalPieces(p) >= 7) unlockShattered(p);
    }

    private void openLore(Player p) {
        Inventory v = Bukkit.createInventory(null, 54, LORE);
        int[] slots = {10,11,12,13,14,15,16,22};
        int i = 0;
        for (String realm : realms()) {
            int pages = getInt(p, realm, "lore", 0);
            v.setItem(slots[i++], item(Material.WRITTEN_BOOK, color(realm) + display(realm) + " Chronicle",
                    ChatColor.GRAY + "Lore pages: " + pages + "/12",
                    ChatColor.GRAY + loreHook(realm),
                    pages >= 12 ? ChatColor.GREEN + "Secret lore complete" : ChatColor.YELLOW + "Find pages in vaults/outposts/dungeons"));
        }
        v.setItem(49, item(Material.ARROW, ChatColor.YELLOW + "Back"));
        p.openInventory(v);
    }

    private void handleLoreClick(Player p, String name) {
        String realm = byDisplay(name.replace(" Chronicle", ""));
        if (realm != null && getInt(p, realm, "lore", 0) >= 12) {
            addAchievement(p, "Lorekeeper of " + display(realm));
            p.sendMessage(ChatColor.LIGHT_PURPLE + "Secret boss clue unlocked for " + display(realm) + ".");
        }
    }

    private void openEvents(Player p) {
        Inventory v = Bukkit.createInventory(null, 54, EVENTS);
        int[] slots = {10,11,12,13,14,15,16,22};
        int i = 0;
        for (String realm : realms()) {
            int t = threat.getOrDefault(realm, 0);
            v.setItem(slots[i++], item(icon(realm), color(realm) + display(realm),
                    ChatColor.GRAY + "Threat: " + threatBar(t) + " " + t + "%",
                    ChatColor.GRAY + "Next 100% event: " + eventName(realm),
                    ChatColor.GRAY + "Events completed: " + getInt(p, realm, "events", 0)));
        }
        v.setItem(31, item(Material.CLOCK, ChatColor.GOLD + "Dynamic Event Rotation",
                ChatColor.GRAY + "Meteor Shower • Blood Moon • Lightning Storm",
                ChatColor.GRAY + "Frozen Eclipse • Void Breach • Ancient Awakening",
                ChatColor.GRAY + "Treasure Storm • Double Boss"));
        v.setItem(49, item(Material.ARROW, ChatColor.YELLOW + "Back"));
        p.openInventory(v);
    }

    private void handleEventClick(Player p, String name) {
        String realm = byDisplay(name);
        if (realm != null) selectedRealm.put(p.getUniqueId(), realm);
    }

    private void openPvp(Player p, String realm) {
        Inventory v = Bukkit.createInventory(null, 36, PVP + ChatColor.DARK_GRAY + " • " + display(realm));
        v.setItem(4, item(Material.DIAMOND_SWORD, color(realm) + display(realm) + " Arena",
                ChatColor.GRAY + "Theme-specific PvP arena"));
        v.setItem(10, item(Material.IRON_SWORD, ChatColor.GREEN + "1v1"));
        v.setItem(11, item(Material.SHIELD, ChatColor.AQUA + "2v2"));
        v.setItem(12, item(Material.TNT, ChatColor.RED + "Free For All"));
        v.setItem(13, item(Material.NETHER_STAR, ChatColor.GOLD + "Ranked"));
        v.setItem(14, item(Material.BANNER_PATTERN, ChatColor.LIGHT_PURPLE + "Realm vs Realm"));
        v.setItem(22, item(Material.ENDER_PEARL, ChatColor.YELLOW + "Enter Arena",
                ChatColor.GRAY + "Teleport to the physical realm arena"));
        v.setItem(31, item(Material.ARROW, ChatColor.YELLOW + "Back"));
        p.openInventory(v);
    }

    private void openGuilds(Player p) {
        Inventory v = Bukkit.createInventory(null, 54, GUILDS);
        int[] slots = {10,11,12,13,14,15,16,22};
        int i = 0;
        for (String realm : realms()) {
            String owner = data.getString("territory." + realm, "Unclaimed");
            v.setItem(slots[i++], item(Material.BANNER_PATTERN, color(realm) + display(realm),
                    ChatColor.GRAY + "Territory holder: " + ChatColor.WHITE + owner,
                    ChatColor.YELLOW + "Click to claim for your scoreboard team"));
        }
        v.setItem(49, item(Material.ARROW, ChatColor.YELLOW + "Back"));
        p.openInventory(v);
    }

    private void handleGuildClick(Player p, String name) {
        String realm = byDisplay(name);
        if (realm == null) return;
        Team team = Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(p.getName());
        String guild = team == null ? p.getName() + "'s Guild" : team.getName();
        int mastery = masteryLevel(p, realm);
        if (mastery < 10) {
            p.sendMessage(ChatColor.RED + "You need Mastery 10 in this realm to claim territory.");
            return;
        }
        data.set("territory." + realm, guild);
        save();
        Bukkit.broadcastMessage(ChatColor.BLUE + "[REALM TERRITORY] " + ChatColor.WHITE + guild +
                ChatColor.BLUE + " claimed " + display(realm) + " territory!");
        openGuilds(p);
    }

    private void openCaravans(Player p) {
        Inventory v = Bukkit.createInventory(null, 54, CARAVANS);
        int[] slots = {10,11,12,13,14,15,16,22};
        int i = 0;
        for (String realm : realms()) {
            boolean active = data.getBoolean("caravan." + realm + ".active", false);
            v.setItem(slots[i++], item(Material.CHEST_MINECART, color(realm) + display(realm) + " Caravan",
                    active ? ChatColor.GREEN + "ACTIVE — defend the merchant!" : ChatColor.GRAY + "No caravan currently traveling",
                    ChatColor.GRAY + "Defenses completed: " + getInt(p, realm, "caravans", 0)));
        }
        v.setItem(49, item(Material.ARROW, ChatColor.YELLOW + "Back"));
        p.openInventory(v);
    }

    private void handleCaravanClick(Player p, String name) {
        String realm = byDisplay(name.replace(" Caravan", ""));
        if (realm != null && data.getBoolean("caravan." + realm + ".active", false)) {
            teleportSite(p, realm, 180, realmY(realm) + 2, 0);
        }
    }

    private void openPets(Player p) {
        Inventory v = Bukkit.createInventory(null, 54, PETS);
        int[] slots = {10,11,12,13,14,15,16,22};
        int i = 0;
        for (String realm : realms()) {
            boolean unlocked = masteryLevel(p, realm) >= 8;
            v.setItem(slots[i++], item(petIcon(realm),
                    unlocked ? color(realm) + petName(realm) : ChatColor.DARK_GRAY + "Locked Pet",
                    unlocked ? ChatColor.GRAY + "Click to summon/dismiss" : ChatColor.GRAY + "Unlock at Mastery 8",
                    ChatColor.GRAY + "Bonus: " + petBonus(realm)));
        }
        v.setItem(40, item(Material.BARRIER, ChatColor.RED + "Dismiss Active Pet"));
        v.setItem(49, item(Material.ARROW, ChatColor.YELLOW + "Back"));
        p.openInventory(v);
    }

    private void handlePetClick(Player p, String name) {
        if (name.equals("Dismiss Active Pet")) { dismissEntity(activePets.remove(p.getUniqueId())); return; }
        for (String realm : realms()) {
            if (name.equals(petName(realm)) && masteryLevel(p, realm) >= 8) {
                summonPet(p, realm);
                openPets(p);
                return;
            }
        }
    }

    private void openMounts(Player p) {
        Inventory v = Bukkit.createInventory(null, 54, MOUNTS);
        int[] slots = {10,11,12,13,14,15,16,22};
        int i = 0;
        for (String realm : realms()) {
            boolean unlocked = masteryLevel(p, realm) >= 15;
            v.setItem(slots[i++], item(Material.SADDLE,
                    unlocked ? color(realm) + mountName(realm) : ChatColor.DARK_GRAY + "Locked Mount",
                    unlocked ? ChatColor.GRAY + "Click to summon/dismiss" : ChatColor.GRAY + "Unlock at Mastery 15"));
        }
        v.setItem(40, item(Material.BARRIER, ChatColor.RED + "Dismiss Active Mount"));
        v.setItem(49, item(Material.ARROW, ChatColor.YELLOW + "Back"));
        p.openInventory(v);
    }

    private void handleMountClick(Player p, String name) {
        if (name.equals("Dismiss Active Mount")) { dismissEntity(activeMounts.remove(p.getUniqueId())); return; }
        for (String realm : realms()) {
            if (name.equals(mountName(realm)) && masteryLevel(p, realm) >= 15) {
                summonMount(p, realm);
                openMounts(p);
                return;
            }
        }
    }

    private void openAchievements(Player p) {
        Inventory v = Bukkit.createInventory(null, 54, ACHIEVEMENTS);
        List<String> all = achievementList();
        for (int i = 0; i < Math.min(45, all.size()); i++) {
            String achievement = all.get(i);
            boolean unlocked = data.getBoolean(playerPath(p) + ".achievements." + key(achievement), false) || achievementUnlocked(p, achievement);
            if (unlocked) data.set(playerPath(p) + ".achievements." + key(achievement), true);
            v.setItem(i, item(unlocked ? Material.TOTEM_OF_UNDYING : Material.GRAY_DYE,
                    unlocked ? ChatColor.GOLD + achievement : ChatColor.DARK_GRAY + achievement,
                    unlocked ? ChatColor.GREEN + "UNLOCKED" : ChatColor.GRAY + achievementRequirement(achievement)));
        }
        v.setItem(49, item(Material.ARROW, ChatColor.YELLOW + "Back"));
        save();
        p.openInventory(v);
    }

    private void openTitles(Player p) {
        Inventory v = Bukkit.createInventory(null, 54, TITLES);
        String equipped = getString(p, "title", "");
        int slot = 10;
        for (String realm : realms()) {
            String title = titleFor(realm);
            boolean unlocked = masteryLevel(p, realm) >= 20;
            v.setItem(slot++, item(Material.NAME_TAG,
                    unlocked ? color(realm) + title : ChatColor.DARK_GRAY + "Locked Title",
                    unlocked ? ChatColor.GRAY + "Mastery 20 reward" : ChatColor.GRAY + "Reach Mastery 20 in " + display(realm),
                    equipped.equals(title) ? ChatColor.GREEN + "EQUIPPED" : unlocked ? ChatColor.YELLOW + "Click to equip" : ""));
        }
        if (shatteredUnlocked(p)) {
            v.setItem(31, item(Material.NAME_TAG, ChatColor.DARK_PURPLE + "Realmwalker",
                    ChatColor.GRAY + "Secret realm title",
                    equipped.equals("Realmwalker") ? ChatColor.GREEN + "EQUIPPED" : ChatColor.YELLOW + "Click to equip"));
        }
        v.setItem(40, item(Material.BARRIER, ChatColor.RED + "Clear Title"));
        v.setItem(49, item(Material.ARROW, ChatColor.YELLOW + "Back"));
        p.openInventory(v);
    }

    private void handleTitleClick(Player p, String name) {
        if (name.equals("Clear Title")) {
            setString(p, "title", "");
            applyTitle(p, "");
            openTitles(p);
            return;
        }
        for (String realm : realms()) {
            if (name.equals(titleFor(realm)) && masteryLevel(p, realm) >= 20) {
                setString(p, "title", titleFor(realm));
                applyTitle(p, titleFor(realm));
                openTitles(p);
                return;
            }
        }
        if (name.equals("Realmwalker") && shatteredUnlocked(p)) {
            setString(p, "title", "Realmwalker");
            applyTitle(p, "Realmwalker");
            openTitles(p);
        }
    }

    private void openProfile(Player p) {
        Inventory v = Bukkit.createInventory(null, 54, PROFILE);
        v.setItem(4, item(Material.PLAYER_HEAD, ChatColor.AQUA + p.getName(),
                ChatColor.GRAY + "Realm Score: " + totalScore(p),
                ChatColor.GRAY + "Total Mastery: " + totalMastery(p),
                ChatColor.GRAY + "Portal Fragments: " + portalPieces(p) + "/7",
                ChatColor.GRAY + "Difficulty: " + getString(p, "difficulty", "NORMAL"),
                ChatColor.GRAY + "Title: " + getString(p, "title", "None")));
        int[] slots = {10,11,12,13,14,15,16,22};
        int i = 0;
        for (String realm : realms()) {
            v.setItem(slots[i++], item(icon(realm), color(realm) + display(realm),
                    ChatColor.GRAY + "Mastery " + masteryLevel(p, realm) + "/50",
                    ChatColor.GRAY + "Rep " + getInt(p, realm, "reputation", 0),
                    ChatColor.GRAY + "Quest " + Math.min(24, getInt(p, realm, "quest-stage", 1)) + "/24",
                    ChatColor.GRAY + "Bosses " + getInt(p, realm, "bosses", 0),
                    ChatColor.GRAY + "Dungeons " + getInt(p, realm, "dungeons", 0),
                    ChatColor.GRAY + "Raids " + getInt(p, realm, "raids", 0)));
        }
        v.setItem(31, item(Material.ENDER_EYE, ChatColor.DARK_PURPLE + "Shattered Realm",
                shatteredUnlocked(p) ? ChatColor.GREEN + "UNLOCKED" : ChatColor.GRAY + portalPieces(p) + "/7 portal fragments"));
        v.setItem(49, item(Material.ARROW, ChatColor.YELLOW + "Back"));
        p.openInventory(v);
    }

    private void handleProfileClick(Player p, String name) {
        String realm = byDisplay(name);
        if (realm != null) { selectedRealm.put(p.getUniqueId(), realm); openQuestDetail(p, realm); }
        else if (name.equals("Shattered Realm") && shatteredUnlocked(p)) p.performCommand("realms shattered");
    }

    private void openLeaderboards(Player p) {
        Inventory v = Bukkit.createInventory(null, 54, LEADERBOARD);
        List<ScoreEntry> scores = leaderboard();
        for (int i = 0; i < Math.min(36, scores.size()); i++) {
            ScoreEntry e = scores.get(i);
            v.setItem(i, item(i == 0 ? Material.NETHER_STAR : Material.PLAYER_HEAD,
                    ChatColor.GOLD + "#" + (i + 1) + " " + e.name,
                    ChatColor.GRAY + "Realm Score: " + e.score,
                    ChatColor.GRAY + "Total Mastery: " + e.mastery));
        }
        v.setItem(40, item(Material.COMPASS, ChatColor.AQUA + "Your Rank",
                ChatColor.GRAY + "Score: " + totalScore(p)));
        v.setItem(49, item(Material.ARROW, ChatColor.YELLOW + "Back"));
        p.openInventory(v);
    }

    private void openSecret(Player p) {
        Inventory v = Bukkit.createInventory(null, 27, SECRET);
        v.setItem(4, item(Material.REINFORCED_DEEPSLATE, ChatColor.DARK_PURPLE + "The Shattered Realm",
                ChatColor.GRAY + "A world broken from fragments of every realm."));
        v.setItem(10, item(Material.ENDER_EYE, ChatColor.LIGHT_PURPLE + "Enter",
                ChatColor.GRAY + "Requires all seven Portal Fragments"));
        v.setItem(12, item(Material.DRAGON_HEAD, ChatColor.RED + "The Fracture Raid",
                ChatColor.GRAY + "Secret multi-realm raid"));
        v.setItem(14, item(Material.NETHER_STAR, ChatColor.GOLD + "Realmwalker Rewards",
                ChatColor.GRAY + "Ancient rarity gear • hidden title • secret mount"));
        v.setItem(22, item(Material.ARROW, ChatColor.YELLOW + "Back"));
        p.openInventory(v);
    }

    private void handleSecretClick(Player p, String name) {
        if (name.equals("Enter") && shatteredUnlocked(p)) p.performCommand("realms shattered");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void realmMobDeath(EntityDeathEvent event) {
        LivingEntity mob = event.getEntity();
        if (!mob.getScoreboardTags().contains("esnRealmMob")) return;
        Player killer = mob.getKiller();
        if (killer == null) return;
        String realm = realmFromWorld(killer.getWorld());
        if (realm == null || realm.equals("shattered")) return;

        double mult = difficultyMultiplier(killer);
        addMasteryXp(killer, realm, (int)Math.round((mob.getScoreboardTags().contains("esnRealmMiniBoss") ? 120 : 18) * mult));
        addRep(killer, realm, (int)Math.round((mob.getScoreboardTags().contains("esnRealmMiniBoss") ? 75 : 6) * mult));
        increment(killer, realm, "kills", 1);
        incrementQuest(killer, realm, mob.getScoreboardTags().contains("esnRealmMiniBoss") ? 3 : 1);

        if (mob.getScoreboardTags().contains("esnRealmMiniBoss")) {
            increment(killer, realm, "bosses", 1);
            increment(killer, realm, "events", 1);
            addCollectibleChance(killer, realm, 0.40);
            addLoreChance(killer, realm, 0.35);
            dropBossCore(killer, realm);
        } else {
            addCollectibleChance(killer, realm, 0.025);
            addLoreChance(killer, realm, 0.012);
        }
        checkPortalPiece(killer, realm);
        checkAchievements(killer);
    }

    @EventHandler
    public void move(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        Player p = event.getPlayer();
        String realm = realmFromWorld(p.getWorld());
        if (realm == null) return;
        long now = System.currentTimeMillis();
        if (moveCooldown.getOrDefault(p.getUniqueId(), 0L) > now) return;
        moveCooldown.put(p.getUniqueId(), now + 2500L);

        Location l = p.getLocation();
        int[][] sites = {{92,0},{-92,0},{0,118},{0,-118},{118,58},{-118,-58},{132,-92},{-132,92},{180,0},{250,0},{-250,0},{0,250}};
        for (int i = 0; i < sites.length; i++) {
            double dx = l.getX() - sites[i][0], dz = l.getZ() - sites[i][1];
            if (dx * dx + dz * dz <= 15 * 15) {
                String key = realm + ".site." + i;
                if (!data.getBoolean(playerPath(p) + ".discoveries." + key, false)) {
                    data.set(playerPath(p) + ".discoveries." + key, true);
                    addMasteryXp(p, realm, 35);
                    addRep(p, realm, 15);
                    addCollectibleChance(p, realm, 0.35);
                    addLoreChance(p, realm, 0.30);
                    p.sendMessage(ChatColor.GOLD + "DISCOVERY: " + color(realm) + siteName(i) +
                            ChatColor.GRAY + " • +35 Mastery XP • +15 Reputation");
                    save();
                }
                break;
            }
        }
    }

    @EventHandler
    public void interactNpc(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager villager)) return;
        String realm = null;
        for (String tag : villager.getScoreboardTags()) {
            if (tag.startsWith("esnRealmTown_")) realm = tag.substring("esnRealmTown_".length());
        }
        if (realm == null) return;
        event.setCancelled(true);
        selectedRealm.put(event.getPlayer().getUniqueId(), realm);
        openTown(event.getPlayer(), realm);
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        applyTitle(p, getString(p, "title", ""));
        data.set(playerPath(p) + ".name", p.getName());
        save();
    }

    private void tickThreatAndCaravans() {
        long now = System.currentTimeMillis();
        for (String realm : realms()) {
            World world = Bukkit.getWorld(worldName(realm));
            int online = world == null ? 0 : world.getPlayers().size();
            int add = online > 0 ? 3 + ThreadLocalRandom.current().nextInt(5) : 1;
            int current = Math.min(100, threat.getOrDefault(realm, 0) + add);
            threat.put(realm, current);
            data.set("threat." + realm, current);

            if (current >= 100 && now >= nextWorldEvent.getOrDefault(realm, 0L)) {
                triggerWorldEvent(realm);
                threat.put(realm, 0);
                data.set("threat." + realm, 0);
                nextWorldEvent.put(realm, now + 8 * 60_000L);
            }

            if (!data.getBoolean("caravan." + realm + ".active", false) && ThreadLocalRandom.current().nextDouble() < 0.035) {
                startCaravan(realm);
            } else if (data.getBoolean("caravan." + realm + ".active", false) &&
                    now > data.getLong("caravan." + realm + ".ends", 0L)) {
                data.set("caravan." + realm + ".active", false);
            }
        }
        save();
    }

    private void triggerWorldEvent(String realm) {
        World world = Bukkit.getWorld(worldName(realm));
        if (world == null || world.getPlayers().isEmpty()) return;
        String event = eventName(realm);
        Bukkit.broadcastMessage(ChatColor.DARK_RED + "[REALM EVENT] " + color(realm) + event +
                ChatColor.YELLOW + " has begun in " + display(realm) + "!");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "realmevent invasion " + realm);
        for (Player p : world.getPlayers()) {
            p.sendTitle(color(realm) + event, ChatColor.GOLD + "Threat Level 100%", 10, 80, 20);
        }
    }

    private void startCaravan(String realm) {
        World world = Bukkit.getWorld(worldName(realm));
        if (world == null || world.getPlayers().isEmpty()) return;
        data.set("caravan." + realm + ".active", true);
        data.set("caravan." + realm + ".ends", System.currentTimeMillis() + 6 * 60_000L);
        Bukkit.broadcastMessage(ChatColor.GOLD + "[REALM CARAVAN] " + color(realm) +
                faction(realm) + ChatColor.YELLOW + " caravan is traveling through " + display(realm) + "!");
        Player target = world.getPlayers().get(0);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "realmevent invasion " + realm);
        target.sendMessage(ChatColor.GOLD + "Defend the caravan near the faction town for rewards.");
    }

    private void ensureTownNPCs() {
        for (String realm : realms()) {
            World world = Bukkit.getWorld(worldName(realm));
            if (world == null) continue;
            Location base = new Location(world, 180.5, realmY(realm) + 2, 0.5);
            boolean exists = world.getNearbyEntities(base, 30, 15, 30).stream()
                    .anyMatch(e -> e.getScoreboardTags().contains("esnRealmTown_" + realm));
            if (exists) continue;
            String[] names = {"Blacksmith","Quest Master","Faction Merchant","Bounty Master","Lorekeeper","Alchemist","Boss Tracker"};
            int[][] offsets = {{-8,0},{-5,5},{0,7},{5,5},{8,0},{4,-6},{-4,-6}};
            for (int i = 0; i < names.length; i++) {
                Villager v = (Villager) world.spawnEntity(base.clone().add(offsets[i][0],0,offsets[i][1]), EntityType.VILLAGER);
                v.setCustomName(color(realm) + names[i]);
                v.setCustomNameVisible(true);
                v.setAI(false);
                v.setInvulnerable(true);
                v.setPersistent(true);
                v.addScoreboardTag("esnRealmTown_" + realm);
            }
        }
    }

    private void claimFactionReward(Player p, String realm) {
        int rep = getInt(p, realm, "reputation", 0);
        int tier = rep / 1000;
        int claimed = getInt(p, realm, "faction-reward", 0);
        if (tier <= claimed) {
            p.sendMessage(ChatColor.GRAY + "Reach the next 1,000 Reputation tier for another faction reward.");
            return;
        }
        setInt(p, realm, "faction-reward", tier);
        p.giveExp(250 + tier * 50);
        p.getInventory().addItem(rolledGear(realm, "Relic", Math.min(7, 2 + tier / 2)));
        p.sendMessage(ChatColor.GREEN + "Faction reward claimed for reaching " + (tier * 1000) + " Reputation!");
    }

    private void grantBounty(Player p, String realm) {
        setInt(p, realm, "quest-progress", Math.min(questTarget(getInt(p, realm, "quest-stage", 1)),
                getInt(p, realm, "quest-progress", 0) + 2));
        p.sendMessage(ChatColor.YELLOW + "Bounty accepted: hunt elite " + display(realm) + " creatures.");
    }

    private void buyBlessing(Player p, String realm) {
        PotionEffect effect = switch (realm) {
            case "storm" -> new PotionEffect(PotionEffectType.SPEED, 20*300, 0);
            case "abyss" -> new PotionEffect(PotionEffectType.NIGHT_VISION, 20*300, 0);
            case "frost" -> new PotionEffect(PotionEffectType.RESISTANCE, 20*300, 0);
            case "infernal" -> new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20*300, 0);
            case "verdant" -> new PotionEffect(PotionEffectType.REGENERATION, 20*60, 0);
            case "celestial" -> new PotionEffect(PotionEffectType.SLOW_FALLING, 20*300, 0);
            case "bloodmoon" -> new PotionEffect(PotionEffectType.STRENGTH, 20*180, 0);
            default -> new PotionEffect(PotionEffectType.LUCK, 20*300, 0);
        };
        p.addPotionEffect(effect);
        p.sendMessage(ChatColor.AQUA + faction(realm) + " blessing applied.");
    }

    private void addMasteryXp(Player p, String realm, int amount) {
        int level = masteryLevel(p, realm);
        int xp = getInt(p, realm, "mastery-xp", 0) + amount;
        boolean leveled = false;
        while (level < 50 && xp >= masteryNeed(level)) {
            xp -= masteryNeed(level);
            level++;
            leveled = true;
            setInt(p, realm, "mastery", level);
            p.sendMessage(color(realm) + display(realm) + " Mastery reached " + level + "!");
            if (level == 10) {
                setInt(p, realm, "portal-piece", 1);
                p.sendMessage(ChatColor.LIGHT_PURPLE + "You recovered the " + display(realm) + " Portal Fragment!");
                checkShatteredUnlock(p);
            }
        }
        setInt(p, realm, "mastery-xp", xp);
        if (leveled) checkAchievements(p);
    }

    private void addRep(Player p, String realm, int amount) {
        setInt(p, realm, "reputation", Math.min(10000, getInt(p, realm, "reputation", 0) + amount));
    }

    private void incrementQuest(Player p, String realm, int amount) {
        int stage = getInt(p, realm, "quest-stage", 1);
        if (stage > 24) return;
        int progress = getInt(p, realm, "quest-progress", 0) + amount;
        int target = questTarget(stage);
        if (progress >= target) {
            setInt(p, realm, "quest-stage", stage + 1);
            setInt(p, realm, "quest-progress", 0);
            addMasteryXp(p, realm, 100 + stage * 10);
            addRep(p, realm, 50 + stage * 5);
            p.sendMessage(ChatColor.GOLD + "QUEST COMPLETE: " + questName(realm, stage));
            if (stage == 24) addAchievement(p, display(realm) + " Ascendant");
        } else setInt(p, realm, "quest-progress", progress);
    }

    private void checkPortalPiece(Player p, String realm) {
        if (masteryLevel(p, realm) >= 10 && getInt(p, realm, "portal-piece", 0) == 0) {
            setInt(p, realm, "portal-piece", 1);
            checkShatteredUnlock(p);
        }
    }

    private void checkShatteredUnlock(Player p) {
        if (portalPieces(p) < 7 || shatteredUnlocked(p)) return;
        unlockShattered(p);
    }

    private void unlockShattered(Player p) {
        data.set(playerPath(p) + ".shattered", true);
        plugin.getConfig().set("realms.shattered-unlocked." + p.getUniqueId(), true);
        plugin.saveConfig();
        save();
        Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + "[SECRET REALM] " + ChatColor.GOLD + p.getName() +
                ChatColor.LIGHT_PURPLE + " assembled all seven Portal Fragments and revealed THE SHATTERED REALM!");
        addAchievement(p, "Realmwalker");
    }

    private boolean shatteredUnlocked(Player p) {
        return data.getBoolean(playerPath(p) + ".shattered", false) ||
                plugin.getConfig().getBoolean("realms.shattered-unlocked." + p.getUniqueId(), false);
    }

    private int portalPieces(Player p) {
        int n = 0;
        for (String realm : realms()) if (getInt(p, realm, "portal-piece", 0) > 0) n++;
        return n;
    }

    private void addCollectibleChance(Player p, String realm, double chance) {
        if (ThreadLocalRandom.current().nextDouble() > chance) return;
        int max = collectibleTotal(realm);
        int found = getInt(p, realm, "collectibles", 0);
        if (found >= max) return;
        setInt(p, realm, "collectibles", found + 1);
        p.sendMessage(ChatColor.YELLOW + "COLLECTION: found " + collectibleName(realm) + " " + (found + 1) + "/" + max);
    }

    private void addLoreChance(Player p, String realm, double chance) {
        if (ThreadLocalRandom.current().nextDouble() > chance) return;
        int found = getInt(p, realm, "lore", 0);
        if (found >= 12) return;
        setInt(p, realm, "lore", found + 1);
        p.sendMessage(ChatColor.LIGHT_PURPLE + "LORE DISCOVERED: " + display(realm) + " page " + (found + 1) + "/12");
    }

    private void dropBossCore(Player p, String realm) {
        ItemStack item = new ItemStack(Material.HEART_OF_THE_SEA);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(realm) + display(realm) + " Boss Core");
        meta.setLore(List.of(ChatColor.GOLD + "Realm Crafting Material",
                ChatColor.GRAY + "Used for high-rarity realm equipment."));
        meta.getPersistentDataContainer().set(realmGearKey, PersistentDataType.STRING, "core:" + realm);
        item.setItemMeta(meta);
        p.getInventory().addItem(item);
    }

    private void forgeGear(Player p, String realm, String piece) {
        int mastery = masteryLevel(p, realm);
        if (mastery < 5) {
            p.sendMessage(ChatColor.RED + "Realm Mastery 5 required.");
            return;
        }
        int rarity = rollRarity(p, mastery);
        ItemStack gear = rolledGear(realm, piece, rarity);
        p.getInventory().addItem(gear);
        p.sendMessage(ChatColor.GOLD + "FORGED: " + gear.getItemMeta().getDisplayName());
    }

    private ItemStack rolledGear(String realm, String piece, int rarity) {
        Material material = switch (piece) {
            case "Helmet" -> Material.DIAMOND_HELMET;
            case "Chestplate" -> Material.DIAMOND_CHESTPLATE;
            case "Leggings" -> Material.DIAMOND_LEGGINGS;
            case "Boots" -> Material.DIAMOND_BOOTS;
            case "Sword" -> Material.DIAMOND_SWORD;
            case "Bow" -> Material.BOW;
            default -> Material.HEART_OF_THE_SEA;
        };
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        String rarityName = rarityName(rarity);
        meta.setDisplayName(rarityColor(rarity) + "[" + rarityName + "] " + color(realm) + display(realm) + " " + piece);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int damage = 3 + rarity * 2 + r.nextInt(0, 4);
        int crit = 2 + rarity + r.nextInt(0, 4);
        int speed = 1 + rarity / 2 + r.nextInt(0, 3);
        meta.setLore(List.of(
                ChatColor.GRAY + "Realm Set: " + color(realm) + display(realm),
                ChatColor.RED + "+" + damage + "% Realm Damage",
                ChatColor.GOLD + "+" + crit + "% Crit Chance",
                ChatColor.AQUA + "+" + speed + "% Speed",
                ChatColor.LIGHT_PURPLE + "Full Set: " + setBonus(realm)));
        meta.getPersistentDataContainer().set(rarityKey, PersistentDataType.INTEGER, rarity);
        meta.getPersistentDataContainer().set(realmGearKey, PersistentDataType.STRING, realm + ":" + piece);
        item.setItemMeta(meta);
        return item;
    }

    private int rollRarity(Player p, int mastery) {
        int roll = ThreadLocalRandom.current().nextInt(1000);
        int luck = mastery * 3;
        if (roll < 5 + luck / 8) return 7;
        if (roll < 20 + luck / 4) return 6;
        if (roll < 65 + luck / 2) return 5;
        if (roll < 150 + luck) return 4;
        if (roll < 320 + luck) return 3;
        if (roll < 560 + luck) return 2;
        return 1;
    }

    private void summonPet(Player p, String realm) {
        dismissEntity(activePets.remove(p.getUniqueId()));
        Entity entity = p.getWorld().spawnEntity(p.getLocation().add(1,0,1), petType(realm));
        entity.setCustomName(color(realm) + p.getName() + "'s " + petName(realm));
        entity.setCustomNameVisible(true);
        entity.setInvulnerable(true);
        if (entity instanceof Tameable tame) {
            tame.setTamed(true);
            tame.setOwner(p);
        }
        if (entity instanceof Mob mob) mob.setRemoveWhenFarAway(false);
        activePets.put(p.getUniqueId(), entity.getUniqueId());
        applyPetBonus(p, realm);
    }

    private void summonMount(Player p, String realm) {
        dismissEntity(activeMounts.remove(p.getUniqueId()));
        Entity e;
        Location at = p.getLocation().add(1,0,1);
        if (realm.equals("celestial")) e = p.getWorld().spawnEntity(at, EntityType.SKELETON_HORSE);
        else if (realm.equals("infernal") || realm.equals("bloodmoon")) e = p.getWorld().spawnEntity(at, EntityType.ZOMBIE_HORSE);
        else e = p.getWorld().spawnEntity(at, EntityType.HORSE);
        if (e instanceof AbstractHorse horse) {
            horse.setTamed(true);
            horse.getInventory().setSaddle(new ItemStack(Material.SADDLE));
            horse.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.32 + masteryLevel(p, realm) * 0.002);
            horse.setJumpStrength(0.85);
        }
        e.setCustomName(color(realm) + mountName(realm));
        e.setCustomNameVisible(true);
        e.setPersistent(true);
        e.addPassenger(p);
        activeMounts.put(p.getUniqueId(), e.getUniqueId());
    }

    private void applyPetBonus(Player p, String realm) {
        switch (realm) {
            case "storm" -> p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20*300, 0));
            case "abyss" -> p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 20*300, 0));
            case "frost" -> p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20*180, 0));
            case "infernal" -> p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20*300, 0));
            case "verdant" -> p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20*60, 0));
            case "celestial" -> p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 20*300, 0));
            case "bloodmoon" -> p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 20*180, 0));
        }
    }

    private void dismissEntity(UUID id) {
        if (id == null) return;
        Entity e = Bukkit.getEntity(id);
        if (e != null) e.remove();
    }

    private void applyTitle(Player p, String title) {
        if (Bukkit.getScoreboardManager() == null) return;
        Team team = Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(p.getName());
        if (team != null) team.setSuffix(title == null || title.isBlank() ? "" : ChatColor.GRAY + " [" + ChatColor.LIGHT_PURPLE + title + ChatColor.GRAY + "]");
    }

    private void teleportSite(Player p, String realm, int x, int y, int z) {
        World w = Bukkit.getWorld(worldName(realm));
        if (w == null) {
            p.performCommand("realms " + realm);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                World loaded = Bukkit.getWorld(worldName(realm));
                if (loaded != null) p.teleport(new Location(loaded, x + .5, y, z + .5));
            }, 40L);
        } else p.teleport(new Location(w, x + .5, y, z + .5));
    }

    private double difficultyMultiplier(Player p) {
        return switch (getString(p, "difficulty", "NORMAL")) {
            case "MYTHIC" -> 2.0;
            case "HEROIC" -> 1.5;
            default -> 1.0;
        };
    }

    private int masteryLevel(Player p, String realm) { return getInt(p, realm, "mastery", 0); }
    private int masteryNeed(int level) { return 150 + level * 40; }
    private int totalMastery(Player p) { int n=0; for(String r:realms()) n+=masteryLevel(p,r); return n; }
    private int skillPoints(Player p, String realm) {
        int spent=0; for(int i=0;i<6;i++) spent+=getInt(p,realm,"skill."+i,0);
        return Math.max(0, masteryLevel(p,realm)-spent);
    }
    private int totalScore(Player p) {
        int score = totalMastery(p)*100 + portalPieces(p)*500;
        for(String r:realms()) score += getInt(p,r,"reputation",0)/5 + getInt(p,r,"bosses",0)*100 +
                getInt(p,r,"dungeons",0)*75 + getInt(p,r,"raids",0)*250 + getInt(p,r,"collectibles",0)*20;
        return score;
    }

    private void checkAchievements(Player p) {
        for(String a:achievementList()) if(achievementUnlocked(p,a)) addAchievement(p,a);
    }
    private void addAchievement(Player p,String name){
        String path=playerPath(p)+".achievements."+key(name);
        if(data.getBoolean(path,false))return;
        data.set(path,true);save();
        p.sendMessage(ChatColor.GOLD+"ACHIEVEMENT UNLOCKED: "+ChatColor.WHITE+name);
    }
    private boolean achievementUnlocked(Player p,String a){
        if(a.equals("Realmwalker"))return shatteredUnlocked(p);
        if(a.equals("Realm Master"))return totalMastery(p)>=200;
        if(a.equals("Realm Legend"))return totalMastery(p)>=300;
        if(a.equals("Boss Hunter")){int n=0;for(String r:realms())n+=getInt(p,r,"bosses",0);return n>=50;}
        if(a.equals("Dungeon Delver")){int n=0;for(String r:realms())n+=getInt(p,r,"dungeons",0);return n>=25;}
        if(a.equals("Raidbreaker")){int n=0;for(String r:realms())n+=getInt(p,r,"raids",0);return n>=10;}
        if(a.startsWith("Lorekeeper of ")){String r=byDisplay(a.substring("Lorekeeper of ".length()));return r!=null&&getInt(p,r,"lore",0)>=12;}
        if(a.endsWith(" Ascendant")){String r=byDisplay(a.substring(0,a.length()-" Ascendant".length()));return r!=null&&getInt(p,r,"quest-stage",1)>24;}
        return false;
    }

    private List<String> achievementList(){
        List<String> a=new ArrayList<>(List.of("Realmwalker","Realm Master","Realm Legend","Boss Hunter","Dungeon Delver","Raidbreaker"));
        for(String r:realms()){a.add(display(r)+" Ascendant");a.add("Lorekeeper of "+display(r));}
        return a;
    }

    private String achievementRequirement(String a){
        return switch(a){
            case "Realmwalker"->"Reveal the Shattered Realm.";
            case "Realm Master"->"Reach 200 total Mastery.";
            case "Realm Legend"->"Reach 300 total Mastery.";
            case "Boss Hunter"->"Defeat 50 realm bosses/minibosses.";
            case "Dungeon Delver"->"Complete 25 realm dungeons.";
            case "Raidbreaker"->"Complete 10 realm raids.";
            default->a.endsWith(" Ascendant")?"Finish that realm's 24-chapter questline.":"Collect all 12 lore pages.";
        };
    }

    private List<ScoreEntry> leaderboard(){
        List<ScoreEntry> list=new ArrayList<>();
        if(data.isConfigurationSection("players")){
            for(String id:data.getConfigurationSection("players").getKeys(false)){
                String name=data.getString("players."+id+".name",id.substring(0,Math.min(8,id.length())));
                int mastery=0,score=0;
                for(String r:realms()){
                    int m=data.getInt("players."+id+".realms."+r+".mastery",0);mastery+=m;score+=m*100;
                    score+=data.getInt("players."+id+".realms."+r+".reputation",0)/5;
                    score+=data.getInt("players."+id+".realms."+r+".bosses",0)*100;
                }
                list.add(new ScoreEntry(name,score,mastery));
            }
        }
        list.sort(Comparator.comparingInt(ScoreEntry::score).reversed());
        return list;
    }

    private void checkProgressionActions(Player p,String realm,String name){
        if(name.startsWith("Forge "))forgeGear(p,realm,name.substring("Forge ".length()));
        else if(name.equals("Enter Dungeon")){teleportSite(p,realm,250,realmY(realm)+2,0);increment(p,realm,"dungeons",1);addMasteryXp(p,realm,100);}
        else if(name.equals("Elite Dungeon")&&masteryLevel(p,realm)>=15){teleportSite(p,realm,250,realmY(realm)+2,0);increment(p,realm,"dungeons",1);addMasteryXp(p,realm,180);}
        else if(name.equals("Enter Raid")){teleportSite(p,realm,-250,realmY(realm)+2,0);increment(p,realm,"raids",1);addMasteryXp(p,realm,220);}
        else if(name.equals("Mythic Raid")&&masteryLevel(p,realm)>=35&&getString(p,"difficulty","NORMAL").equals("MYTHIC")){teleportSite(p,realm,-250,realmY(realm)+2,0);increment(p,realm,"raids",1);addMasteryXp(p,realm,400);}
        else if(name.equals("Enter Arena"))teleportSite(p,realm,0,realmY(realm)+2,250);
    }

    private String realmSummary(Player p,String realm,String mode){
        return switch(mode){
            case "quests"->"Chapter "+Math.min(24,getInt(p,realm,"quest-stage",1))+"/24";
            case "skills"->"Mastery "+masteryLevel(p,realm)+" • "+skillPoints(p,realm)+" skill points";
            case "dungeons"->getInt(p,realm,"dungeons",0)+" dungeon clears";
            case "raids"->getInt(p,realm,"raids",0)+" raid clears";
            case "crafting"->"Forge "+display(realm)+" equipment";
            case "pvp"->"Open "+display(realm)+" PvP arena";
            default->"Open";
        };
    }

    private String[] skillNames(String realm){return new String[]{"Mobility","Defense","Realm Damage","Treasure Sense","Recovery","Signature Power"};}
    private String skillDescription(String realm,int i){return switch(i){
        case 0->"Increase movement while exploring "+display(realm)+".";
        case 1->"Reduce damage from realm creatures.";
        case 2->"Increase damage against native realm mobs.";
        case 3->"Improve rare drop and treasure odds.";
        case 4->"Improve healing after combat.";
        default->"Strengthen the realm's signature ability.";
    };}

    private int collectibleTotal(String realm){return switch(realm){case "storm"->25;case "abyss"->20;case "frost"->30;case "infernal"->24;case "verdant"->28;case "celestial"->15;case "bloodmoon"->22;default->20;};}
    private String collectibleName(String realm){return switch(realm){case "storm"->"Storm Runes";case "abyss"->"Abyss Tablets";case "frost"->"Frost Relics";case "infernal"->"Infernal Seals";case "verdant"->"Wildheart Seeds";case "celestial"->"Celestial Stars";case "bloodmoon"->"Bloodmoon Sigils";default->"Realm Fragments";};}
    private String loreHook(String realm){return switch(realm){case "storm"->"Why the sky kingdom fractured.";case "abyss"->"Who awakened the corruption below.";case "frost"->"The oath of the first Frost King.";case "infernal"->"How the empire learned to bind flame.";case "verdant"->"The civilization swallowed by the Wilds.";case "celestial"->"Why the Astral Council fell.";case "bloodmoon"->"The origin of the crimson curse.";default->"The forgotten history of the realms.";};}
    private String threatBar(int t){int filled=Math.min(10,t/10);return ChatColor.RED+"█".repeat(filled)+ChatColor.DARK_GRAY+"░".repeat(10-filled);}
    private String eventName(String realm){String[] events={"Meteor Shower","Blood Moon","Lightning Storm","Frozen Eclipse","Void Breach","Ancient Awakening","Treasure Storm","Double Boss"};return events[Math.abs(realm.hashCode()+Calendar.getInstance().get(Calendar.DAY_OF_YEAR))%events.length];}
    private String faction(String realm){return switch(realm){case "storm"->"Stormguard";case "abyss"->"Abyss Order";case "frost"->"Frost Clan";case "infernal"->"Infernal Legion";case "verdant"->"Wildheart Tribe";case "celestial"->"Astral Council";case "bloodmoon"->"Bloodmoon Cult";default->"Realm Wardens";};}
    private String factionRank(int rep){if(rep>=9000)return"Exalted";if(rep>=7000)return"Champion";if(rep>=5000)return"Honored";if(rep>=3000)return"Trusted";if(rep>=1000)return"Friendly";return"Outsider";}
    private String titleFor(String realm){return switch(realm){case"storm"->"Stormbreaker";case"abyss"->"Voidwalker";case"frost"->"Frostborn";case"infernal"->"Flame Emperor";case"verdant"->"Wildheart";case"celestial"->"Starborn";case"bloodmoon"->"Bloodlord";default->"Realmwalker";};}
    private String petName(String realm){return switch(realm){case"storm"->"Storm Wraith";case"abyss"->"Abyss Wisp";case"frost"->"Frost Fox";case"infernal"->"Ember Drake";case"verdant"->"Wildheart Cub";case"celestial"->"Star Sprite";case"bloodmoon"->"Bloodmoon Wolf";default->"Realm Sprite";};}
    private String petBonus(String realm){return switch(realm){case"storm"->"Speed";case"abyss"->"Night Vision";case"frost"->"Resistance";case"infernal"->"Fire Resistance";case"verdant"->"Regeneration";case"celestial"->"Slow Falling";case"bloodmoon"->"Strength";default->"Luck";};}
    private EntityType petType(String realm){return switch(realm){case"storm","celestial"->EntityType.ALLAY;case"abyss"->EntityType.CAT;case"frost"->EntityType.FOX;case"infernal"->EntityType.PARROT;case"verdant"->EntityType.WOLF;case"bloodmoon"->EntityType.WOLF;default->EntityType.ALLAY;};}
    private Material petIcon(String realm){return switch(realm){case"storm","celestial"->Material.AMETHYST_SHARD;case"abyss"->Material.ECHO_SHARD;case"frost"->Material.SNOWBALL;case"infernal"->Material.BLAZE_POWDER;case"verdant"->Material.MOSS_BLOCK;case"bloodmoon"->Material.REDSTONE;default->Material.BONE;};}
    private String mountName(String realm){return switch(realm){case"storm"->"Tempest Charger";case"abyss"->"Void Steed";case"frost"->"Glacier Runner";case"infernal"->"Infernal Warhorse";case"verdant"->"Wildheart Stag";case"celestial"->"Astral Steed";case"bloodmoon"->"Crimson Nightmare";default->"Realm Mount";};}
    private String setBonus(String realm){return switch(realm){case"storm"->"Chain Lightning";case"abyss"->"Void Step";case"frost"->"Frozen Aegis";case"infernal"->"Flame Crown";case"verdant"->"Living Armor";case"celestial"->"Astral Wings";case"bloodmoon"->"Blood Feast";default->"Realm Ascendance";};}

    private int realmY(String realm){return switch(realm){case"storm"->128;case"abyss"->70;case"frost"->115;case"infernal"->90;case"verdant"->104;case"celestial"->138;case"bloodmoon"->96;case"100"->100;case"shattered"->112;default->80;};}
    private String siteName(int i){return switch(i){case 0->"Realm Fortress";case 1->"Boss Arena";case 2,3->"Ancient Outpost";case 4,5->"Realm Obelisk";case 6,7->"Hidden Treasure Vault";case 8->"Faction Town";case 9->"Dungeon Gate";case 10->"Raid Citadel";case 11->"PvP Arena";default->"Secret Site";};}

    private Set<String> realms(){return new LinkedHashSet<>(List.of("storm","abyss","frost","infernal","verdant","celestial","bloodmoon"));}
    private String normalize(String in){if(in==null)return null;String v=in.toLowerCase(Locale.ROOT).replace("_","").replace("-","").replace(" ","");return switch(v){case"storm","stormkingdom"->"storm";case"abyss","theabyss"->"abyss";case"frost","frostlands"->"frost";case"infernal","infernalempire"->"infernal";case"verdant","verdantwilds"->"verdant";case"celestial","celestialisles"->"celestial";case"bloodmoon","bloodmoonwastes"->"bloodmoon";case"shattered","shatteredrealm"->"shattered";default->null;};}
    private String byDisplay(String name){if(name==null)return null;String clean=name.replace(" Collection","").replace(" Chronicle","").replace(" Caravan","");for(String r:realms())if(clean.equals(display(r)))return r;if(clean.equals("Shattered Realm"))return"shattered";return null;}
    private String factionRealm(String name){for(String r:realms())if(name.equals(faction(r)))return r;return null;}
    private String display(String realm){return switch(realm){case"storm"->"Storm Kingdom";case"abyss"->"The Abyss";case"frost"->"Frostlands";case"infernal"->"Infernal Empire";case"verdant"->"Verdant Wilds";case"celestial"->"Celestial Isles";case"bloodmoon"->"Bloodmoon Wastes";case"100"->"Realm 100";case"shattered"->"Shattered Realm";default->realm;};}
    private String worldName(String realm){return switch(realm){case"100"->"esn_realm100";case"shattered"->"esn_shattered";default->"esn_"+realm;};}
    private String realmFromWorld(World w){if(w==null)return null;String n=w.getName();if(n.equals("esn_shattered"))return"shattered";for(String r:realms())if(n.equals(worldName(r)))return r;return null;}
    private ChatColor color(String realm){return switch(realm){case"storm","celestial"->ChatColor.AQUA;case"abyss","shattered"->ChatColor.DARK_PURPLE;case"frost"->ChatColor.WHITE;case"infernal"->ChatColor.RED;case"verdant"->ChatColor.GREEN;case"bloodmoon"->ChatColor.DARK_RED;default->ChatColor.GOLD;};}
    private Material icon(String realm){return switch(realm){case"storm"->Material.LIGHTNING_ROD;case"abyss"->Material.ECHO_SHARD;case"frost"->Material.BLUE_ICE;case"infernal"->Material.MAGMA_BLOCK;case"verdant"->Material.MOSS_BLOCK;case"celestial"->Material.AMETHYST_SHARD;case"bloodmoon"->Material.REDSTONE_BLOCK;case"shattered"->Material.REINFORCED_DEEPSLATE;default->Material.NETHER_STAR;};}
    private String rarityName(int r){return switch(r){case 7->"Ancient";case 6->"Realm";case 5->"Mythic";case 4->"Legendary";case 3->"Epic";case 2->"Rare";default->"Uncommon";};}
    private ChatColor rarityColor(int r){return switch(r){case 7->ChatColor.DARK_PURPLE;case 6->ChatColor.GOLD;case 5->ChatColor.LIGHT_PURPLE;case 4->ChatColor.YELLOW;case 3->ChatColor.DARK_PURPLE;case 2->ChatColor.AQUA;default->ChatColor.GREEN;};}

    private int getInt(Player p,String realm,String field,int def){return data.getInt(playerPath(p)+".realms."+realm+"."+field,def);}
    private void setInt(Player p,String realm,String field,int value){data.set(playerPath(p)+".realms."+realm+"."+field,value);save();}
    private void increment(Player p,String realm,String field,int amount){setInt(p,realm,field,getInt(p,realm,field,0)+amount);}
    private String getString(Player p,String field,String def){return data.getString(playerPath(p)+"."+field,def);}
    private void setString(Player p,String field,String value){data.set(playerPath(p)+"."+field,value);save();}
    private String playerPath(Player p){return"players."+p.getUniqueId();}
    private String key(String s){return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","_");}
    private void save(){try{data.save(file);}catch(Exception ex){plugin.getLogger().warning("[Realm Ascension] save: "+ex.getMessage());}}

    private ItemStack item(Material material,String name,String...lore){ItemStack item=new ItemStack(material);ItemMeta meta=item.getItemMeta();meta.setDisplayName(name);meta.setLore(Arrays.asList(lore));item.setItemMeta(meta);return item;}

    private record ScoreEntry(String name,int score,int mastery){}
}
