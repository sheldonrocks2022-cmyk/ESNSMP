package com.esn.smp.gameplay;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class DiscordReminder implements Listener {
    private static final String INVITE = "https://discord.gg/RPUpwYAQD8";

    private final BossBar bar;
    private final BarColor[] colors = {
            BarColor.PURPLE, BarColor.BLUE, BarColor.GREEN, BarColor.YELLOW,
            BarColor.RED, BarColor.PINK, BarColor.WHITE
    };
    private final String[] titles = {
            ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "✦ JOIN THE ESN DISCORD ✦ " + ChatColor.WHITE + "discord.gg/RPUpwYAQD8",
            ChatColor.AQUA + "" + ChatColor.BOLD + "✦ JOIN THE ESN DISCORD ✦ " + ChatColor.WHITE + "discord.gg/RPUpwYAQD8",
            ChatColor.GREEN + "" + ChatColor.BOLD + "✦ JOIN THE ESN DISCORD ✦ " + ChatColor.WHITE + "discord.gg/RPUpwYAQD8",
            ChatColor.YELLOW + "" + ChatColor.BOLD + "✦ JOIN THE ESN DISCORD ✦ " + ChatColor.WHITE + "discord.gg/RPUpwYAQD8",
            ChatColor.RED + "" + ChatColor.BOLD + "✦ JOIN THE ESN DISCORD ✦ " + ChatColor.WHITE + "discord.gg/RPUpwYAQD8",
            ChatColor.GOLD + "" + ChatColor.BOLD + "✦ JOIN THE ESN DISCORD ✦ " + ChatColor.WHITE + "discord.gg/RPUpwYAQD8",
            ChatColor.WHITE + "" + ChatColor.BOLD + "✦ JOIN THE ESN DISCORD ✦ " + ChatColor.WHITE + "discord.gg/RPUpwYAQD8"
    };
    private final Component broadcastMessage = Component
            .text("✦ JOIN THE ESN DISCORD ✦ ", NamedTextColor.LIGHT_PURPLE)
            .decorate(TextDecoration.BOLD)
            .append(Component.text("Click here to join: ", NamedTextColor.GOLD))
            .append(Component.text("discord.gg/RPUpwYAQD8", NamedTextColor.AQUA)
                    .decorate(TextDecoration.UNDERLINED)
                    .clickEvent(ClickEvent.openUrl(INVITE)));

    private int frame;

    public DiscordReminder(JavaPlugin plugin) {
        bar = Bukkit.createBossBar("", BarColor.PURPLE, BarStyle.SOLID);
        bar.setProgress(1.0);
        for (Player player : Bukkit.getOnlinePlayers()) bar.addPlayer(player);

        plugin.getServer().getScheduler().runTaskTimer(plugin, this::animate, 0L, 10L);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::broadcast, 6000L, 6000L);
    }

    private void animate() {
        if (bar.getPlayers().isEmpty()) return;
        int i = frame++ % colors.length;
        bar.setColor(colors[i]);
        bar.setTitle(titles[i]);
    }

    private void broadcast() {
        if (Bukkit.getOnlinePlayers().isEmpty()) return;
        for (Player player : Bukkit.getOnlinePlayers()) player.sendMessage(broadcastMessage);
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        bar.addPlayer(event.getPlayer());
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        bar.removePlayer(event.getPlayer());
    }

    public void shutdown() {
        bar.removeAll();
    }
}
