package com.esn.smp.gameplay;
import net.kyori.adventure.text.Component;import net.kyori.adventure.text.event.ClickEvent;import net.kyori.adventure.text.format.NamedTextColor;import net.kyori.adventure.text.format.TextDecoration;import org.bukkit.*;import org.bukkit.boss.*;import org.bukkit.entity.Player;import org.bukkit.event.*;import org.bukkit.event.player.*;import org.bukkit.plugin.java.JavaPlugin;
public final class DiscordReminder implements Listener{
 private static final String INVITE="https://discord.gg/RPUpwYAQD8";private final JavaPlugin plugin;private final BossBar bar;private int frame;
 private final BarColor[] colors={BarColor.PURPLE,BarColor.BLUE,BarColor.GREEN,BarColor.YELLOW,BarColor.RED,BarColor.PINK,BarColor.WHITE};
 private final ChatColor[] text={ChatColor.LIGHT_PURPLE,ChatColor.AQUA,ChatColor.GREEN,ChatColor.YELLOW,ChatColor.RED,ChatColor.GOLD,ChatColor.WHITE};
 public DiscordReminder(JavaPlugin p){plugin=p;bar=Bukkit.createBossBar("",BarColor.PURPLE,BarStyle.SOLID);bar.setProgress(1.0);for(Player x:Bukkit.getOnlinePlayers())bar.addPlayer(x);p.getServer().getScheduler().runTaskTimer(p,()->animate(),0L,10L);p.getServer().getScheduler().runTaskTimer(p,()->broadcast(),6000L,6000L);}
 private void animate(){int i=frame++%colors.length;bar.setColor(colors[i]);bar.setTitle(text[i]+""+ChatColor.BOLD+"✦ JOIN THE ESN DISCORD ✦ "+ChatColor.WHITE+"discord.gg/RPUpwYAQD8");}
 private void broadcast(){Component msg=Component.text("✦ JOIN THE ESN DISCORD ✦ ",NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD).append(Component.text("Click here to join: ",NamedTextColor.GOLD)).append(Component.text("discord.gg/RPUpwYAQD8",NamedTextColor.AQUA).decorate(TextDecoration.UNDERLINED).clickEvent(ClickEvent.openUrl(INVITE)));for(Player p:Bukkit.getOnlinePlayers())p.sendMessage(msg);}
 @EventHandler public void join(PlayerJoinEvent e){bar.addPlayer(e.getPlayer());}
 @EventHandler public void quit(PlayerQuitEvent e){bar.removePlayer(e.getPlayer());}
 public void shutdown(){bar.removeAll();}
}