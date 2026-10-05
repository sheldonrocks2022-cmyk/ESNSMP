package com.esn.smp.gameplay;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;

public final class VoiceChatIntegration implements Listener, CommandExecutor {
    private static final String TITLE = "ESN Voice Chat";
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final Pattern CODE = Pattern.compile("[A-Za-z0-9_-]{2,64}");

    private final JavaPlugin plugin;

    public VoiceChatIntegration(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only. Use /plugins or the OpenAudioMc console commands for backend administration.");
            return true;
        }

        String sub = args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "menu", "gui" -> open(player);
            case "connect", "start", "reconnect" -> connect(player, null);
            case "code", "pair" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.AQUA + "Bedrock voice pairing:");
                    player.sendMessage(ChatColor.GRAY + "1. Open " + ChatColor.WHITE + "https://session.openaudiomc.net");
                    player.sendMessage(ChatColor.GRAY + "2. Get a pairing code.");
                    player.sendMessage(ChatColor.GRAY + "3. Run " + ChatColor.YELLOW + "/voice code <code>");
                    return true;
                }
                if (!CODE.matcher(args[1]).matches()) {
                    player.sendMessage(ChatColor.RED + "That voice pairing code is not valid.");
                    return true;
                }
                connect(player, args[1]);
            }
            case "status" -> status(player);
            case "mod", "moderate" -> moderate(player);
            case "help" -> help(player);
            default -> {
                player.sendMessage(ChatColor.RED + "Unknown voice option.");
                help(player);
            }
        }
        return true;
    }

    public void open(Player player) {
        boolean ready = backendReady();
        Inventory menu = Bukkit.createInventory(null, 45, Component.text(TITLE));

        menu.setItem(10, item(Material.JUKEBOX,
                ChatColor.GREEN + "Connect Voice Chat",
                ChatColor.GRAY + "Java / universal connection",
                ChatColor.YELLOW + "Click to get your private browser session"));

        menu.setItem(12, item(Material.SPYGLASS,
                ChatColor.AQUA + "Bedrock / Geyser Pairing",
                ChatColor.GRAY + "Open session.openaudiomc.net first",
                ChatColor.GRAY + "Then use /voice code <code>"));

        menu.setItem(14, item(ready ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
                ready ? ChatColor.GREEN + "Voice Backend Online" : ChatColor.RED + "Voice Backend Offline",
                ready ? ChatColor.GRAY + "Provider: " + backendName() : ChatColor.GRAY + "Staff must install/configure the voice backend"));

        menu.setItem(16, item(Material.WRITABLE_BOOK,
                ChatColor.GOLD + "How Voice Chat Works",
                ChatColor.GRAY + "Proximity voice runs through a secure browser tab.",
                ChatColor.GRAY + "No client mod or resource pack is required."));

        menu.setItem(20, item(Material.COMPASS,
                ChatColor.LIGHT_PURPLE + "Reconnect",
                ChatColor.GRAY + "Generate/open your current voice session again"));

        menu.setItem(22, item(Material.SHIELD,
                ChatColor.BLUE + "Privacy",
                ChatColor.GRAY + "Your microphone only works after browser permission.",
                ChatColor.GRAY + "Close the browser session to disconnect voice."));

        if (player.hasPermission("esnsmp.staff")) {
            menu.setItem(24, item(Material.ECHO_SHARD,
                    ChatColor.DARK_PURPLE + "Staff Moderation Mode",
                    ChatColor.GRAY + "Invisible/listen-only voice moderation",
                    ChatColor.YELLOW + "Staff only"));
        }

        menu.setItem(36, item(Material.ARROW, ChatColor.YELLOW + "Back to ESN Menu", ChatColor.GRAY + "/menu"));
        menu.setItem(44, item(Material.BARRIER, ChatColor.RED + "Close"));
        player.openInventory(menu);
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!PLAIN.serialize(event.getView().title()).equals(TITLE)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack current = event.getCurrentItem();
        if (current == null || !current.hasItemMeta()) return;
        ItemMeta currentMeta = current.getItemMeta();
        Component currentName = currentMeta.displayName();
        if (currentName == null) return;

        String name = PLAIN.serialize(currentName);

        switch (name) {
            case "Connect Voice Chat", "Reconnect" -> {
                player.closeInventory();
                connect(player, null);
            }
            case "Bedrock / Geyser Pairing" -> {
                player.closeInventory();
                player.sendMessage(ChatColor.AQUA + "BEDROCK VOICE CHAT");
                player.sendMessage(ChatColor.GRAY + "1. Open " + ChatColor.WHITE + "https://session.openaudiomc.net");
                player.sendMessage(ChatColor.GRAY + "2. Copy the code it gives you.");
                player.sendMessage(ChatColor.GRAY + "3. In Minecraft run " + ChatColor.YELLOW + "/voice code <code>");
            }
            case "Voice Backend Online", "Voice Backend Offline" -> status(player);
            case "How Voice Chat Works" -> {
                player.closeInventory();
                help(player);
            }
            case "Privacy" -> {
                player.closeInventory();
                player.sendMessage(ChatColor.BLUE + "ESN Voice Privacy");
                player.sendMessage(ChatColor.GRAY + "Voice uses your browser microphone permission. You can mute there or close the voice tab to disconnect.");
            }
            case "Staff Moderation Mode" -> {
                player.closeInventory();
                moderate(player);
            }
            case "Back to ESN Menu" -> {
                player.closeInventory();
                Bukkit.dispatchCommand(player, "menu");
            }
            case "Close" -> player.closeInventory();
        }
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        if (!backendReady()) return;
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.sendMessage(ChatColor.DARK_AQUA + "[ESN Voice] " + ChatColor.AQUA +
                        "Proximity voice is available. Use " + ChatColor.YELLOW + "/voice" + ChatColor.AQUA + " to connect.");
            }
        }, 100L);
    }

    @EventHandler
    public void backendEnabled(PluginEnableEvent event) {
        String name = event.getPlugin().getName();
        if (name.equalsIgnoreCase("OpenAudioMc")) {
            plugin.getLogger().info("[ESN Voice] OpenAudioMc detected. /voice integration is online.");
        }
    }

    private void connect(Player player, String code) {
        PluginCommand audio = audioCommand();
        if (audio == null || !audio.getPlugin().isEnabled()) {
            missingBackend(player);
            return;
        }

        PermissionAttachment attachment = player.addAttachment(plugin);
        attachment.setPermission("openaudiomc.commands.audio", true);
        try {
            String command = code == null ? "audio" : "audio " + code;
            boolean dispatched = Bukkit.dispatchCommand(player, command);
            if (!dispatched) {
                player.sendMessage(ChatColor.RED + "The voice backend did not accept the connection command.");
            }
        } finally {
            player.removeAttachment(attachment);
        }
    }

    private void moderate(Player player) {
        if (!player.hasPermission("esnsmp.staff")) {
            player.sendMessage(ChatColor.RED + "ESN staff only.");
            return;
        }
        if (!backendReady()) {
            missingBackend(player);
            return;
        }

        PluginCommand oa = Bukkit.getPluginCommand("oa");
        if (oa == null || !oa.getPlugin().isEnabled()) {
            player.sendMessage(ChatColor.RED + "Voice moderation commands are unavailable on this backend.");
            return;
        }

        PermissionAttachment attachment = player.addAttachment(plugin);
        attachment.setPermission("openaudiomc.commands.voice", true);
        try {
            Bukkit.dispatchCommand(player, "oa voice mod");
        } finally {
            player.removeAttachment(attachment);
        }
    }

    private void status(Player player) {
        if (backendReady()) {
            player.sendMessage(ChatColor.GREEN + "ESN Voice: ONLINE");
            player.sendMessage(ChatColor.GRAY + "Backend: " + backendName());
            player.sendMessage(ChatColor.GRAY + "Use " + ChatColor.YELLOW + "/voice connect" + ChatColor.GRAY + " for Java/universal voice.");
            player.sendMessage(ChatColor.GRAY + "Bedrock: " + ChatColor.YELLOW + "/voice code <code>");
        } else {
            missingBackend(player);
        }
    }

    private void help(Player player) {
        player.sendMessage(ChatColor.DARK_AQUA + "ESN VOICE CHAT");
        player.sendMessage(ChatColor.YELLOW + "/voice" + ChatColor.GRAY + " — open the Voice Chat menu");
        player.sendMessage(ChatColor.YELLOW + "/voice connect" + ChatColor.GRAY + " — open/generate your browser session");
        player.sendMessage(ChatColor.YELLOW + "/voice code <code>" + ChatColor.GRAY + " — pair a Bedrock/Geyser browser session");
        player.sendMessage(ChatColor.YELLOW + "/voice status" + ChatColor.GRAY + " — check voice availability");
        if (player.hasPermission("esnsmp.staff")) {
            player.sendMessage(ChatColor.YELLOW + "/voice mod" + ChatColor.GRAY + " — toggle OpenAudioMc moderation mode");
        }
    }

    private void missingBackend(Player player) {
        player.sendMessage(ChatColor.RED + "ESN Voice is not ready on this server yet.");
        if (player.hasPermission("esnsmp.admin")) {
            player.sendMessage(ChatColor.YELLOW + "Admin: install and configure OpenAudioMc, then restart the backend.");
            player.sendMessage(ChatColor.GRAY + "If ESN is running through Velocity, install the same OpenAudioMc build on Velocity and this Paper backend.");
        } else {
            player.sendMessage(ChatColor.GRAY + "Tell ESN staff that the voice backend is offline.");
        }
    }

    private boolean backendReady() {
        PluginCommand command = audioCommand();
        return command != null && command.getPlugin().isEnabled();
    }

    private PluginCommand audioCommand() {
        return Bukkit.getPluginCommand("audio");
    }

    private String backendName() {
        PluginCommand command = audioCommand();
        Plugin backend = command == null ? null : command.getPlugin();
        return backend == null ? "Unknown" : backend.getName() + " " + backend.getDescription().getVersion();
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize(name));
        meta.lore(Arrays.stream(lore).map(LEGACY::deserialize).toList());
        item.setItemMeta(meta);
        return item;
    }
}
