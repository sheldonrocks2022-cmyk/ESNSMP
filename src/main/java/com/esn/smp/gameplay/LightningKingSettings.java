package com.esn.smp.gameplay;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persistent per-player Lightning King ability controls.
 * All toggles default ON and are stored directly on the player PDC.
 */
public final class LightningKingSettings implements Listener, CommandExecutor {
    public static final String MASTER = "master";
    public static final String THUNDERSTEP = "thunderstep";
    public static final String KINGS_JUDGMENT = "kings_judgment";
    public static final String CHAIN_LIGHTNING = "chain_lightning";
    public static final String STATIC_CHARGE = "static_charge";
    public static final String STORM_AURA = "storm_aura";
    public static final String LIGHTNING_REFLEX = "lightning_reflex";
    public static final String THUNDER_COUNTER = "thunder_counter";
    public static final String STORMBREAKER = "stormbreaker";
    public static final String SKYFALL = "skyfall";
    public static final String THUNDER_CAGE = "thunder_cage";
    public static final String OVERCHARGE = "overcharge";
    public static final String STORM_WINGS = "storm_wings";
    public static final String LIGHTNING_ROD = "lightning_rod";
    public static final String TEMPEST_SHIELD = "tempest_shield";
    public static final String THUNDER_REVIVAL = "thunder_revival";
    public static final String STORMCALL = "stormcall";
    public static final String ELECTROCUTION = "electrocution";
    public static final String LIGHTNING_EXECUTION = "lightning_execution";
    public static final String THUNDER_RUSH = "thunder_rush";
    public static final String WRATH_OF_KING = "wrath_of_king";

    private static final String MENU_TITLE = ChatColor.GOLD + "" + ChatColor.BOLD + "⚡ Lightning King Abilities ⚡";

    private static final Map<Integer, Ability> ABILITIES = new LinkedHashMap<>();

    static {
        add(10, THUNDERSTEP, "Thunderstep", Material.FEATHER);
        add(11, KINGS_JUDGMENT, "King's Judgment", Material.LIGHTNING_ROD);
        add(12, CHAIN_LIGHTNING, "Chain Lightning", Material.IRON_NUGGET);
        add(13, STATIC_CHARGE, "Static Charge", Material.REDSTONE);
        add(14, STORM_AURA, "Storm Aura", Material.BEACON);
        add(15, LIGHTNING_REFLEX, "Lightning Reflex", Material.SUGAR);
        add(16, THUNDER_COUNTER, "Thunder Counter", Material.SHIELD);

        add(19, STORMBREAKER, "Stormbreaker", Material.BOW);
        add(20, SKYFALL, "Skyfall", Material.HEAVY_CORE);
        add(21, THUNDER_CAGE, "Thunder Cage", Material.IRON_BARS);
        add(22, OVERCHARGE, "Overcharge", Material.GLOWSTONE_DUST);
        add(23, STORM_WINGS, "Storm Wings", Material.ELYTRA);
        add(24, LIGHTNING_ROD, "Lightning Rod", Material.END_ROD);
        add(25, TEMPEST_SHIELD, "Tempest Shield", Material.TOTEM_OF_UNDYING);

        add(28, THUNDER_REVIVAL, "Thunder Revival", Material.GOLDEN_APPLE);
        add(29, STORMCALL, "Stormcall", Material.TRIDENT);
        add(30, ELECTROCUTION, "Electrocution", Material.AMETHYST_SHARD);
        add(31, LIGHTNING_EXECUTION, "Lightning Execution", Material.NETHERITE_SWORD);
        add(32, THUNDER_RUSH, "Thunder Rush", Material.RABBIT_FOOT);
        add(33, WRATH_OF_KING, "Wrath of the King", Material.NETHER_STAR);
    }

    private static void add(int slot, String id, String name, Material material) {
        ABILITIES.put(slot, new Ability(id, name, material));
    }

    public static boolean masterEnabled(Player player) {
        return storedEnabled(player, MASTER);
    }

    public static boolean enabled(Player player, String ability) {
        return masterEnabled(player) && storedEnabled(player, ability);
    }

    private static boolean storedEnabled(Player player, String id) {
        Byte value = player.getPersistentDataContainer().get(key(id), PersistentDataType.BYTE);
        return value == null || value != (byte) 0;
    }

    private static void setEnabled(Player player, String id, boolean enabled) {
        player.getPersistentDataContainer().set(key(id), PersistentDataType.BYTE, (byte) (enabled ? 1 : 0));
    }

    private static NamespacedKey key(String id) {
        return new NamespacedKey("esnsmp", "lightning_king_toggle_" + id);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("on")) {
                setEnabled(player, MASTER, true);
                player.sendMessage(ChatColor.GREEN + "Lightning King abilities enabled.");
                return true;
            }
            if (args[0].equalsIgnoreCase("off")) {
                setEnabled(player, MASTER, false);
                player.sendMessage(ChatColor.YELLOW + "Lightning King abilities disabled.");
                return true;
            }
        }

        open(player);
        return true;
    }

    public void open(Player player) {
        Inventory menu = Bukkit.createInventory(null, 54, MENU_TITLE);

        boolean master = masterEnabled(player);
        menu.setItem(4, toggleItem(
                master ? Material.LIME_DYE : Material.RED_DYE,
                ChatColor.GOLD + "" + ChatColor.BOLD + "MASTER ABILITIES",
                master,
                ChatColor.GRAY + "Turns every Lightning King ability",
                ChatColor.GRAY + "on or off at once.",
                ChatColor.DARK_GRAY + "Individual settings are remembered."
        ));

        for (Map.Entry<Integer, Ability> entry : ABILITIES.entrySet()) {
            Ability ability = entry.getValue();
            boolean enabled = storedEnabled(player, ability.id());
            menu.setItem(entry.getKey(), toggleItem(
                    ability.material(),
                    ChatColor.YELLOW + ability.name(),
                    enabled,
                    master ? ChatColor.GRAY + "Click to toggle this ability."
                            : ChatColor.RED + "Master abilities are currently OFF."
            ));
        }

        menu.setItem(49, simpleItem(Material.OAK_DOOR, ChatColor.YELLOW + "Close",
                ChatColor.GRAY + "Close this menu."));
        player.openInventory(menu);
    }

    private static ItemStack toggleItem(Material material, String name, boolean enabled, String... extraLore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);

        java.util.ArrayList<String> lore = new java.util.ArrayList<>();
        lore.add(enabled
                ? ChatColor.GREEN + "" + ChatColor.BOLD + "ON"
                : ChatColor.RED + "" + ChatColor.BOLD + "OFF");
        lore.add("");
        lore.addAll(List.of(extraLore));
        lore.add("");
        lore.add(ChatColor.AQUA + "Click to toggle");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack simpleItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(lore));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(MENU_TITLE)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getRawSlot();
        if (slot == 4) {
            boolean next = !masterEnabled(player);
            setEnabled(player, MASTER, next);
            player.sendActionBar(next
                    ? ChatColor.GREEN + "Lightning King abilities: ON"
                    : ChatColor.RED + "Lightning King abilities: OFF");
            open(player);
            return;
        }

        Ability ability = ABILITIES.get(slot);
        if (ability != null) {
            boolean next = !storedEnabled(player, ability.id());
            setEnabled(player, ability.id(), next);
            player.sendActionBar((next ? ChatColor.GREEN : ChatColor.RED)
                    + ability.name() + ": " + (next ? "ON" : "OFF"));
            open(player);
            return;
        }

        if (slot == 49) player.closeInventory();
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTitle().equals(MENU_TITLE)) event.setCancelled(true);
    }

    private record Ability(String id, String name, Material material) {
    }
}
