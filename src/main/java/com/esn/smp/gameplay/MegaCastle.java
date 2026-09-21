package com.esn.smp.gameplay;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.*;

public final class MegaCastle implements Listener, CommandExecutor {
 private final JavaPlugin plugin; private final NamespacedKey key; private boolean building=false;
 public MegaCastle(JavaPlugin plugin){this.plugin=plugin;this.key=new NamespacedKey(plugin,"mega_castle_core");}
 public boolean onCommand(CommandSender s,Command c,String l,String[] a){
  if(!(s instanceof Player p)){s.sendMessage("Players only.");return true;}
  if(!p.hasPermission("esnsmp.staff")){p.sendMessage(ChatColor.RED+"Staff only.");return true;}
  ItemStack core=new ItemStack(Material.LODESTONE);ItemMeta m=core.getItemMeta();m.setDisplayName(ChatColor.GOLD+""+ChatColor.BOLD+"ESN MEGA CASTLE CORE");
  m.setLore(List.of(ChatColor.GRAY+"Place to construct the ESN Mega Castle.",ChatColor.RED+"WARNING: requires a massive clear area.",ChatColor.YELLOW+"One castle may build at a time."));
  m.getPersistentDataContainer().set(key,PersistentDataType.BYTE,(byte)1);core.setItemMeta(m);p.getInventory().addItem(core);p.sendMessage(ChatColor.GOLD+"Castle Core granted. Place it on open terrain.");return true;
 }
 @EventHandler public void place(BlockPlaceEvent e){
  ItemMeta m=e.getItemInHand().getItemMeta();if(m==null||!m.getPersistentDataContainer().has(key,PersistentDataType.BYTE))return;
  Player p=e.getPlayer();if(!p.hasPermission("esnsmp.staff")){e.setCancelled(true);p.sendMessage(ChatColor.RED+"Staff only.");return;}
  if(building){e.setCancelled(true);p.sendMessage(ChatColor.RED+"A castle build is already running.");return;}
  Location o=e.getBlockPlaced().getLocation();if(o.getBlockY()<20||o.getBlockY()>240){e.setCancelled(true);p.sendMessage(ChatColor.RED+"Unsafe build height.");return;}
  building=true;p.sendMessage(ChatColor.GOLD+"ESN Mega Castle construction started. ~230x230 footprint.");
  build(o.clone().add(0,-1,0),p);
 }
 private record Change(int x,int y,int z,Material m){}
 private void build(Location o,Player owner){
  List<Change> q=new ArrayList<>(); int R=115;
  // Foundation/courtyard.
  for(int x=-R;x<=R;x++)for(int z=-R;z<=R;z++) if(Math.abs(x)==R||Math.abs(z)==R) for(int y=1;y<=10;y++)q.add(new Change(x,y,z,Material.DEEPSLATE_BRICKS));
  for(int x=-R+1;x<R;x++)for(int z=-R+1;z<R;z++)if((x+z)%3==0)q.add(new Change(x,0,z,Material.STONE_BRICKS));
  // Four defensive towers.
  int[][] towers={{-105,-105},{105,-105},{-105,105},{105,105}};
  for(int[] t:towers) cylinder(q,t[0],t[1],10,0,28,Material.DEEPSLATE_BRICKS);
  // Central keep, 61x51, four floors.
  shell(q,-30,30,-25,25,1,34,Material.STONE_BRICKS);
  for(int y:new int[]{1,9,17,25})for(int x=-29;x<=29;x++)for(int z=-24;z<=24;z++)q.add(new Change(x,y,z,Material.SMOOTH_STONE));
  // Great entrance.
  for(int x=-5;x<=5;x++)for(int y=1;y<=9;y++)q.add(new Change(x,y,-25,Material.AIR));
  // Throne hall.
  for(int z=-12;z<=10;z++)for(int x=-12;x<=12;x++)q.add(new Change(x,2,z,Material.POLISHED_BLACKSTONE));
  q.add(new Change(0,3,9,Material.GOLD_BLOCK));q.add(new Change(0,4,9,Material.RED_WOOL));
  // Massive storage hall: 120 double-chest positions.
  for(int z=-19;z<=19;z+=4)for(int x=-25;x<=25;x+=5){q.add(new Change(x,3,z,Material.CHEST));q.add(new Change(x,4,z,Material.CHEST));}
  // Arena on east side: 45x45 bowl with spectator ring.
  for(int x=35;x<=67;x++)for(int z=-22;z<=22;z++){if(Math.abs(x-51)>=15||Math.abs(z)>=20)q.add(new Change(x,1,z,Material.STONE_BRICKS));else q.add(new Change(x,1,z,Material.SAND));}
  for(int x=34;x<=68;x++)for(int z=-23;z<=23;z++)if(x==34||x==68||z==-23||z==23)for(int y=2;y<=8;y++)q.add(new Change(x,y,z,Material.DEEPSLATE_BRICKS));
  // West wing: forge/enchant/brewing.
  for(int x=-67;x<=-36;x++)for(int z=-22;z<=22;z++)q.add(new Change(x,1,z,Material.POLISHED_ANDESITE));
  for(int z=-18;z<=18;z+=6){q.add(new Change(-60,2,z,Material.ANVIL));q.add(new Change(-54,2,z,Material.SMITHING_TABLE));q.add(new Change(-48,2,z,Material.ENCHANTING_TABLE));q.add(new Change(-42,2,z,Material.BREWING_STAND));}
  // Underground vault.
  shell(q,-24,24,-18,18,-8,-1,Material.REINFORCED_DEEPSLATE);
  for(int x=-20;x<=20;x+=4)for(int z=-14;z<=14;z+=4)q.add(new Change(x,-6,z,Material.BARREL));
  // Lighting.
  for(int x=-100;x<=100;x+=12)for(int z=-100;z<=100;z+=12)q.add(new Change(x,2,z,Material.SEA_LANTERN));
  final int total=q.size(); new BukkitRunnable(){int i=0;public void run(){try{int budget=1800;while(budget-->0&&i<total){Change c=q.get(i++);Block b=o.clone().add(c.x,c.y,c.z).getBlock();b.setType(c.m,false);}if(i%18000<1800)owner.sendMessage(ChatColor.YELLOW+"Castle: "+(i*100/total)+"%");if(i>=total){building=false;owner.sendMessage(ChatColor.GREEN+"ESN Mega Castle construction complete.");cancel();}}catch(Exception ex){building=false;plugin.getLogger().severe("Castle build stopped safely: "+ex.getMessage());owner.sendMessage(ChatColor.RED+"Castle build stopped safely. Check console.");cancel();}}}.runTaskTimer(plugin,1L,1L);
 }
 private void shell(List<Change> q,int x1,int x2,int z1,int z2,int y1,int y2,Material m){for(int y=y1;y<=y2;y++)for(int x=x1;x<=x2;x++)for(int z=z1;z<=z2;z++)if(y==y1||y==y2||x==x1||x==x2||z==z1||z==z2)q.add(new Change(x,y,z,m));}
 private void cylinder(List<Change> q,int cx,int cz,int r,int y1,int y2,Material m){for(int y=y1;y<=y2;y++)for(int x=-r;x<=r;x++)for(int z=-r;z<=r;z++){double d=Math.sqrt(x*x+z*z);if(d>=r-1&&d<=r+.5)q.add(new Change(cx+x,y,cz+z,m));}}
}
