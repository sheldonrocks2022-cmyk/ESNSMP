package com.esn.smp.gameplay;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ServerMenus implements Listener, CommandExecutor {
    private static final String TITLE = "ESN Server Hub";
    private static final String SUB = "ESN • ";
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final JavaPlugin plugin;

    public ServerMenus(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private Component component(String text) {
        return LEGACY.deserialize(text);
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(component(name));
        meta.lore(Arrays.stream(lore).map(this::component).toList());
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        open(player);
        return true;
    }

    public void openSystem(Player player, String system) {
        Inventory inventory = Bukkit.createInventory(null, 54, Component.text(SUB + system));
        String[][] options = switch (system) {
            case "Bosses" -> new String[][]{
                    {"World Boss", "boss world"}, {"Boss Codex", "bosscodex"}, {"Boss Drops", "bossdrops"},
                    {"Boss Summon", "bosssummon"}, {"Boss Rush", "bossrush"}
            };
            case "Jobs" -> new String[][]{
                    {"Jobs", "jobs"}, {"Skill XP", "skills"}, {"RPG Skill Tree", "skilltree"},
                    {"Prestige", "prestige"}, {"Stats", "stats"}, {"Collections", "collections"}
            };
            case "Forge" -> new String[][]{
                    {"Forge", "forge"}, {"Blacksmith", "blacksmith"}, {"Salvage", "salvage"},
                    {"Reforge", "reforge"}, {"Gear Upgrade", "gearupgrade"}, {"Rune Forge", "runeforge"}
            };
            case "Progression" -> new String[][]{
                    {"Progression", "progression"}, {"Season Pass", "seasonpass"}, {"Achievements", "achievements"},
                    {"Challenges", "challenges"}, {"Rewards", "rewards"}, {"Milestones", "milestones"}
            };
            case "Adventure" -> new String[][]{
                    {"Adventure", "adventure"}, {"Class", "class"}, {"Rift", "rift"},
                    {"Hunt", "hunt"}, {"Bounty Board", "bountyboard"}, {"Trophies", "trophies"}
            };
            default -> new String[][]{{system, system.toLowerCase()}};
        };

        int slot = 10;
        for (String[] option : options) {
            inventory.setItem(slot++, item(Material.NETHER_STAR, ChatColor.GOLD + option[0], ChatColor.GRAY + "/" + option[1]));
            if (slot == 17) slot = 19;
        }
        inventory.setItem(49, item(Material.ARROW, ChatColor.YELLOW + "Back", ChatColor.GRAY + "Return to server hub"));
        player.openInventory(inventory);
    }

    public void open(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 54, Component.text(TITLE));
        inventory.setItem(10, item(Material.CHEST, ChatColor.GOLD + "Crates", ChatColor.GRAY + "/crates"));
        inventory.setItem(11, item(Material.NETHER_STAR, ChatColor.RED + "Bosses", ChatColor.GRAY + "/boss"));
        inventory.setItem(12, item(Material.ENDER_EYE, ChatColor.LIGHT_PURPLE + "Adventure", ChatColor.GRAY + "/adventure"));
        inventory.setItem(13, item(Material.DIAMOND_SWORD, ChatColor.AQUA + "RPG Profile", ChatColor.GRAY + "/rpgprofile"));
        inventory.setItem(14, item(Material.WRITABLE_BOOK, ChatColor.YELLOW + "Quests", ChatColor.GRAY + "/quests"));
        inventory.setItem(15, item(Material.CLOCK, ChatColor.GREEN + "Events", ChatColor.GRAY + "/event"));
        inventory.setItem(16, item(Material.BEACON, ChatColor.AQUA + "Warps", ChatColor.GRAY + "/warps"));
        inventory.setItem(19, item(Material.ANVIL, ChatColor.GRAY + "Blacksmith", ChatColor.GRAY + "/blacksmith"));
        inventory.setItem(20, item(Material.SMITHING_TABLE, ChatColor.GOLD + "Forge", ChatColor.GRAY + "/forge"));
        inventory.setItem(21, item(Material.ENCHANTING_TABLE, ChatColor.LIGHT_PURPLE + "Runes", ChatColor.GRAY + "/runes"));
        inventory.setItem(22, item(Material.DIAMOND, ChatColor.AQUA + "Relics", ChatColor.GRAY + "/relics"));
        inventory.setItem(23, item(Material.OAK_SIGN, ChatColor.YELLOW + "Jobs", ChatColor.GRAY + "/jobs"));
        inventory.setItem(24, item(Material.EXPERIENCE_BOTTLE, ChatColor.GREEN + "Skills", ChatColor.GRAY + "/skills"));
        inventory.setItem(25, item(Material.PLAYER_HEAD, ChatColor.WHITE + "Profile", ChatColor.GRAY + "/profile"));
        inventory.setItem(28, item(Material.SHIELD, ChatColor.BLUE + "Claims", ChatColor.GRAY + "/claim info"));
        inventory.setItem(29, item(Material.ENDER_CHEST, ChatColor.DARK_AQUA + "Auction House", ChatColor.GRAY + "/ah"));
        inventory.setItem(30, item(Material.GOLD_INGOT, ChatColor.GOLD + "Shop", ChatColor.GRAY + "/shop"));
        inventory.setItem(31, item(Material.BOOK, ChatColor.YELLOW + "Guide", ChatColor.GRAY + "/guide"));
        inventory.setItem(32, item(Material.TOTEM_OF_UNDYING, ChatColor.GOLD + "Season Pass", ChatColor.GRAY + "/seasonpass"));
        inventory.setItem(33, item(Material.DRAGON_HEAD, ChatColor.DARK_PURPLE + "Boss Codex", ChatColor.GRAY + "/bosscodex"));
        inventory.setItem(34, item(Material.COMPASS, ChatColor.AQUA + "Progression", ChatColor.GRAY + "/progression"));
        inventory.setItem(37, item(Material.BUNDLE, ChatColor.GOLD + "Backpack", ChatColor.GRAY + "10 pages • 450-slot persistent storage", ChatColor.GRAY + "/backpack"));
        inventory.setItem(38, item(Material.AMETHYST_SHARD, ChatColor.LIGHT_PURPLE + "Cosmetics", ChatColor.GRAY + "Auras, titles, pets, effects, collections and more", ChatColor.GRAY + "/cosmetics"));
        if (player.hasPermission("esnsmp.owner")) {
            inventory.setItem(39, item(Material.NAME_TAG, ChatColor.DARK_RED + "Owner Tag", ChatColor.GRAY + "Show or hide your visible ESN Owner tag", ChatColor.GRAY + "/ownertag"));
        }
        inventory.setItem(40, item(Material.BARRIER, ChatColor.RED + "Close"));
        inventory.setItem(42, item(Material.FIREWORK_STAR, ChatColor.LIGHT_PURPLE + "Fun Hub", ChatColor.GRAY + "Events, competitions, parkour, maze, arcade and more", ChatColor.GRAY + "/fun"));
        inventory.setItem(43, item(Material.JUKEBOX, ChatColor.AQUA + "Voice Chat", ChatColor.GRAY + "Java + Bedrock proximity voice", ChatColor.GRAY + "/voice"));
        player.openInventory(inventory);
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        String title = PLAIN.serialize(event.getView().title());
        if (!title.equals(TITLE) && !title.startsWith(SUB)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack current = event.getCurrentItem();
        if (current == null || !current.hasItemMeta()) return;

        ItemMeta meta = current.getItemMeta();
        Component displayName = meta.displayName();
        if (displayName == null) return;
        String name = PLAIN.serialize(displayName);

        if (title.startsWith(SUB)) {
            if (name.equals("Back")) {
                open(player);
                return;
            }
            List<Component> lore = meta.lore();
            if (lore != null && !lore.isEmpty()) {
                String line = PLAIN.serialize(lore.getFirst());
                if (line.startsWith("/")) {
                    player.closeInventory();
                    Bukkit.dispatchCommand(player, line.substring(1));
                }
            }
            return;
        }

        Map<String, String> commands = Map.ofEntries(
                Map.entry("Crates", "crates"),
                Map.entry("Bosses", "boss"),
                Map.entry("Adventure", "adventure"),
                Map.entry("RPG Profile", "rpgprofile"),
                Map.entry("Quests", "quests"),
                Map.entry("Events", "event"),
                Map.entry("Warps", "warps"),
                Map.entry("Blacksmith", "blacksmith"),
                Map.entry("Forge", "forge"),
                Map.entry("Runes", "runes"),
                Map.entry("Relics", "relics"),
                Map.entry("Jobs", "jobs"),
                Map.entry("Skills", "skills"),
                Map.entry("Profile", "profile"),
                Map.entry("Claims", "claim info"),
                Map.entry("Auction House", "ah"),
                Map.entry("Shop", "shop"),
                Map.entry("Guide", "guide"),
                Map.entry("Season Pass", "seasonpass"),
                Map.entry("Boss Codex", "bosscodex"),
                Map.entry("Progression", "progression"),
                Map.entry("Backpack", "backpack"),
                Map.entry("Cosmetics", "cosmetics"),
                Map.entry("Owner Tag", "ownertag"),
                Map.entry("Fun Hub", "fun"),
                Map.entry("Voice Chat", "voice")
        );

        if (name.equals("Close")) {
            player.closeInventory();
            return;
        }

        String cmd = commands.get(name);
        if (cmd == null) return;

        if (Set.of("Bosses", "Adventure", "Jobs", "Blacksmith", "Forge", "Runes", "Relics", "Skills", "Season Pass", "Boss Codex", "Progression").contains(name)) {
            String group = switch (name) {
                case "Bosses", "Boss Codex" -> "Bosses";
                case "Adventure" -> "Adventure";
                case "Jobs", "Skills" -> "Jobs";
                case "Blacksmith", "Forge", "Runes", "Relics" -> "Forge";
                default -> "Progression";
            };
            openSystem(player, group);
        } else {
            player.closeInventory();
            Bukkit.dispatchCommand(player, cmd);
        }
    }
}
