package com.esn.smp.listener;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class SpawnMobGuard implements Listener {
 private static final double DEFAULT_RADIUS=1000.0;
 private final JavaPlugin plugin;
 public SpawnMobGuard(JavaPlugin plugin){this.plugin=plugin;Bukkit.getScheduler().runTask(plugin,this::purgeAll);Bukkit.getScheduler().runTaskTimer(plugin,this::purgeAll,200L,200L);}
 private boolean protectedArea(Location l){World w=l.getWorld();if(w==null)return false;Location s=((com.esn.smp.ESNSMPPlugin)plugin).getSpawnManager().getSpawn();if(s==null||s.getWorld()==null||!s.getWorld().getUID().equals(w.getUID()))return false;double radius=Math.max(DEFAULT_RADIUS,plugin.getConfig().getDouble("spawn.protection-radius",DEFAULT_RADIUS));double dx=l.getX()-s.getX(),dz=l.getZ()-s.getZ();return dx*dx+dz*dz<=radius*radius;}
 private boolean mob(Entity e){return e instanceof Mob;}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void spawn(EntitySpawnEvent e){if(mob(e.getEntity())&&protectedArea(e.getLocation()))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void creature(CreatureSpawnEvent e){if(protectedArea(e.getLocation()))e.setCancelled(true);}
 @EventHandler public void chunk(ChunkLoadEvent e){Bukkit.getScheduler().runTask(plugin,()->{for(Entity x:e.getChunk().getEntities())if(mob(x)&&protectedArea(x.getLocation()))x.remove();});}
 private void purgeAll(){for(World w:Bukkit.getWorlds())for(Entity x:w.getEntities())if(mob(x)&&protectedArea(x.getLocation()))x.remove();}
}