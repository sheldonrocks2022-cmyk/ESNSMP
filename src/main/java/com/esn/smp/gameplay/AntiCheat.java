package com.esn.smp.gameplay;
import org.bukkit.*;import org.bukkit.entity.Player;import org.bukkit.event.*;import org.bukkit.event.block.BlockBreakEvent;import org.bukkit.event.player.PlayerMoveEvent;import java.util.*;
public final class AntiCheat implements Listener{
 private final Map<UUID,Deque<Long>> diamonds=new HashMap<>();private final Map<UUID,Integer> flags=new HashMap<>();
 @EventHandler public void ore(BlockBreakEvent e){if(e.getBlock().getType()!=Material.DIAMOND_ORE&&e.getBlock().getType()!=Material.DEEPSLATE_DIAMOND_ORE)return;long n=System.currentTimeMillis();Deque<Long>d=diamonds.computeIfAbsent(e.getPlayer().getUniqueId(),k->new ArrayDeque<>());d.add(n);while(!d.isEmpty()&&n-d.peek()>60000)d.poll();if(d.size()>=12)flag(e.getPlayer(),"unusual diamond mining / possible x-ray");}
 @EventHandler public void move(PlayerMoveEvent e){if(e.getTo()==null||e.getPlayer().getAllowFlight()||e.getPlayer().isInsideVehicle()||e.getPlayer().isGliding()||e.getPlayer().isFlying()||e.getPlayer().isSwimming()||e.getPlayer().getVelocity().getY()>0.5)return;double dy=e.getTo().getY()-e.getFrom().getY();if(dy>1.5)flag(e.getPlayer(),"abnormal vertical movement");}
 private void flag(Player p,String why){int n=flags.merge(p.getUniqueId(),1,Integer::sum);for(Player x:Bukkit.getOnlinePlayers())if(x.hasPermission("esnsmp.anticheat.alerts"))x.sendMessage(ChatColor.RED+"[ESN AntiCheat] "+p.getName()+": "+why+" (flags "+n+")");if(n>=8)p.kickPlayer("ESN AntiCheat: suspicious activity detected. Staff can review this detection.");}
}
