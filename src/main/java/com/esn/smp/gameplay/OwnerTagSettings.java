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

import java.util.List;

/**
 * Lets ESN owners hide or show the visible Owner tag without changing permissions.
 * The setting is stored per player and survives reconnects/restarts.
 */
@SuppressWarnings("deprecation")
public final class OwnerTagSettings implements Listener, CommandExecutor {
    private static final NamespacedKey OWNER_TAG_VISIBLE =
            new NamespacedKey("esnsmp", "owner_tag_visible");
    private static final String TITLE = ChatColor.DARK_RED + "ESN Owner Tag";

    public static boolean visible(Player player) {
        if (!player.hasPermission("esnsmp.owner")) return true;
        Byte value = player.getPersistentDataContainer().get(OWNER_TAG_VISIBLE, PersistentDataType.BYTE);
        return value == null || value != (byte) 0;
    }

    private static void setVisible(Player player, boolean visible) {
        player.getPersistentDataContainer().set(
                OWNER_TAG_VISIBLE, PersistentDataType.BYTE, (byte) (visible ? 1 : 0));
    }

    public static String visibleRank(Player player) {
        if (player.hasPermission("esnsmp.owner")) {
            return visible(player) ? "Owner" : "Member";
        }
        return "Member";
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("esnsmp.owner")) {
            player.sendMessage(ChatColor.RED + "ESN owner only.");
            return true;
        }
        open(player);
        return true;
    }

    private void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE);
        boolean on = visible(player);

        inv.setItem(11, item(Material.LIME_DYE,
                ChatColor.GREEN + "" + ChatColor.BOLD + "OWNER TAG ON",
                ChatColor.GRAY + "Show [OWNER] in ESN chat.",
                ChatColor.GRAY + "Show Owner on the ESN scoreboard.",
                on ? ChatColor.GREEN + "Currently enabled" : ChatColor.YELLOW + "Click to enable"));

        inv.setItem(13, item(on ? Material.NAME_TAG : Material.BARRIER,
                (on ? ChatColor.GREEN : ChatColor.RED) + "" + ChatColor.BOLD +
                        "OWNER TAG: " + (on ? "ON" : "OFF"),
                ChatColor.GRAY + "Only the visible tag changes.",
                ChatColor.GRAY + "Owner permissions stay active."));

        inv.setItem(15, item(Material.RED_DYE,
                ChatColor.RED + "" + ChatColor.BOLD + "OWNER TAG OFF",
                ChatColor.GRAY + "Hide the visible Owner tag.",
                ChatColor.GRAY + "Your owner permissions are NOT removed.",
                !on ? ChatColor.RED + "Currently disabled" : ChatColor.YELLOW + "Click to disable"));

        inv.setItem(22, item(Material.OAK_DOOR,
                ChatColor.YELLOW + "Close",
                ChatColor.GRAY + "Close this menu."));
        player.openInventory(inv);
    }

    private static ItemStack item(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(lore));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(TITLE)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        switch (event.getRawSlot()) {
            case 11 -> {
                setVisible(player, true);
                player.sendMessage(ChatColor.GREEN + "ESN Owner tag enabled.");
                player.sendActionBar(ChatColor.DARK_RED + "Owner Tag: ON");
                open(player);
            }
            case 15 -> {
                setVisible(player, false);
                player.sendMessage(ChatColor.YELLOW + "ESN Owner tag hidden. Your owner permissions are unchanged.");
                player.sendActionBar(ChatColor.DARK_RED + "Owner Tag: OFF");
                open(player);
            }
            case 22 -> player.closeInventory();
            default -> {
            }
        }
    }

    @EventHandler
    public void drag(InventoryDragEvent event) {
        if (event.getView().getTitle().equals(TITLE)) event.setCancelled(true);
    }
}
