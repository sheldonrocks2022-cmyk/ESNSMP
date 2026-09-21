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
 public MegaCastle(JavaPlugin plugin){this.plugin=plugin;key=new NamespacedKey(plugin,"mega_castle_core");}
 public boolean onCommand(CommandSender s,Command c,String l,String[] a){
  if(!(s instanceof Player p)){s.sendMessage("Players only.");return true;}
  if(!p.hasPermission("esnsmp.staff")){p.sendMessage(ChatColor.RED+"Staff only.");return true;}
  ItemStack core=new ItemStack(Material.LODESTONE); ItemMeta m=core.getItemMeta();
  m.setDisplayName(ChatColor.GOLD+""+ChatColor.BOLD+"ESN MEGA CASTLE CORE");
  m.setLore(List.of(ChatColor.GRAY+"Builds the fully furnished ESN Citadel.",ChatColor.RED+"WARNING: clears the entire 230x230 build zone.",ChatColor.YELLOW+"Terrain and structures in its path are removed.",ChatColor.YELLOW+"One castle may build at a time."));
  m.getPersistentDataContainer().set(key,PersistentDataType.BYTE,(byte)1);core.setItemMeta(m);p.getInventory().addItem(core);
  p.sendMessage(ChatColor.GOLD+"Citadel Core granted. Place it on open terrain.");return true;
 }
 @EventHandler public void place(BlockPlaceEvent e){
  ItemMeta m=e.getItemInHand().getItemMeta(); if(m==null||!m.getPersistentDataContainer().has(key,PersistentDataType.BYTE))return;
  Player p=e.getPlayer(); if(!p.hasPermission("esnsmp.staff")){e.setCancelled(true);p.sendMessage(ChatColor.RED+"Staff only.");return;}
  if(building){e.setCancelled(true);p.sendMessage(ChatColor.RED+"A castle build is already running.");return;}
  Location o=e.getBlockPlaced().getLocation(); if(o.getBlockY()<20||o.getBlockY()>240){e.setCancelled(true);p.sendMessage(ChatColor.RED+"Unsafe build height.");return;}
  building=true;p.sendMessage(ChatColor.GOLD+"Constructing the fully detailed ESN Mega Castle...");build(o.clone().add(0,-1,0),p);
 }
 private record Change(int x,int y,int z,Material m){}
 private void add(List<Change>q,int x,int y,int z,Material m){q.add(new Change(x,y,z,m));}
 private void fill(List<Change>q,int x1,int x2,int y1,int y2,int z1,int z2,Material m){for(int x=x1;x<=x2;x++)for(int y=y1;y<=y2;y++)for(int z=z1;z<=z2;z++)add(q,x,y,z,m);}
 private void floor(List<Change>q,int x1,int x2,int y,int z1,int z2,Material m){fill(q,x1,x2,y,y,z1,z2,m);}
 private void shell(List<Change>q,int x1,int x2,int z1,int z2,int y1,int y2,Material m){for(int y=y1;y<=y2;y++)for(int x=x1;x<=x2;x++)for(int z=z1;z<=z2;z++)if(y==y1||y==y2||x==x1||x==x2||z==z1||z==z2)add(q,x,y,z,m);}
 private void room(List<Change>q,int x1,int x2,int z1,int z2,int y,Material wall,Material fl){floor(q,x1,x2,y,z1,z2,fl);for(int h=1;h<=6;h++){for(int x=x1;x<=x2;x++){add(q,x,y+h,z1,wall);add(q,x,y+h,z2,wall);}for(int z=z1;z<=z2;z++){add(q,x1,y+h,z,wall);add(q,x2,y+h,z,wall);}}floor(q,x1,x2,y+7,z1,z2,Material.DARK_OAK_PLANKS);for(int h=1;h<=3;h++){add(q,(x1+x2)/2,y+h,z1,Material.AIR);add(q,(x1+x2)/2,y+h,z2,Material.AIR);}}
 private void cylinder(List<Change>q,int cx,int cz,int r,int y1,int y2,Material m){for(int y=y1;y<=y2;y++)for(int x=-r;x<=r;x++)for(int z=-r;z<=r;z++){double d=Math.sqrt(x*x+z*z);if(d>=r-1&&d<=r+.5)add(q,cx+x,y,cz+z,m);}}
 private void pillars(List<Change>q,int x1,int x2,int z1,int z2,int y1,int y2){for(int x=x1;x<=x2;x+=8)for(int z:new int[]{z1,z2})fill(q,x,x,y1,y2,z,z,Material.POLISHED_DEEPSLATE);}
 private void furniture(List<Change>q,int x,int y,int z,String type){
  if(type.equals("bed")){add(q,x,y,z,Material.RED_BED);add(q,x+2,y,z,Material.BARREL);add(q,x+2,y+1,z,Material.LANTERN);}
  if(type.equals("table")){add(q,x,y,z,Material.OAK_FENCE);add(q,x,y+1,z,Material.OAK_PRESSURE_PLATE);}
  if(type.equals("armor")){add(q,x,y,z,Material.ARMOR_STAND);add(q,x+2,y,z,Material.SMITHING_TABLE);}
 }
 private void build(Location o,Player owner){
  List<Change>q=new ArrayList<>();int R=115;
  // Clean construction envelope and lay a deliberate courtyard instead of random flooring.
  for(int x=-R;x<R;x++)for(int z=-R;z<R;z++)for(int y=-8;y<=46;y++)add(q,x,y,z,Material.AIR);
  floor(q,-114,114,0,-114,114,Material.STONE_BRICKS);
  // Outer curtain walls, battlements, four gatehouses and corner towers.
  for(int y=1;y<=15;y++){for(int x=-115;x<=115;x++){add(q,x,y,-115,Material.DEEPSLATE_BRICKS);add(q,x,y,115,Material.DEEPSLATE_BRICKS);}for(int z=-114;z<=114;z++){add(q,-115,y,z,Material.DEEPSLATE_BRICKS);add(q,115,y,z,Material.DEEPSLATE_BRICKS);}}
  for(int x=-115;x<=115;x+=4){add(q,x,16,-115,Material.POLISHED_DEEPSLATE);add(q,x,16,115,Material.POLISHED_DEEPSLATE);}
  for(int z=-115;z<=115;z+=4){add(q,-115,16,z,Material.POLISHED_DEEPSLATE);add(q,115,16,z,Material.POLISHED_DEEPSLATE);}
  int[][]ts={{-104,-104},{104,-104},{-104,104},{104,104}};for(int[]t:ts){cylinder(q,t[0],t[1],11,0,31,Material.DEEPSLATE_BRICKS);floor(q,t[0]-9,t[0]+9,8,t[1]-9,t[1]+9,Material.DARK_OAK_PLANKS);floor(q,t[0]-9,t[0]+9,17,t[1]-9,t[1]+9,Material.DARK_OAK_PLANKS);floor(q,t[0]-9,t[0]+9,26,t[1]-9,t[1]+9,Material.DARK_OAK_PLANKS);}
  // Grand south gate: arched opening, portcullis frame and gatehouse.
  fill(q,-15,15,1,22,-114,-104,Material.STONE_BRICKS);fill(q,-6,6,1,10,-115,-103,Material.AIR);for(int x=-6;x<=6;x+=2)fill(q,x,x,1,9,-104,-104,Material.IRON_BARS);
  // Central keep with towers, floors, windows, roofline and connected grand staircase.
  shell(q,-40,40,-38,38,1,40,Material.STONE_BRICKS);for(int y:new int[]{1,10,19,28,37})floor(q,-39,39,y,-37,37,Material.DARK_OAK_PLANKS);
  int[][]kt={{-36,-34},{36,-34},{-36,34},{36,34}};for(int[]t:kt)cylinder(q,t[0],t[1],7,1,46,Material.POLISHED_DEEPSLATE);
  for(int y:new int[]{6,15,24,33})for(int x=-30;x<=30;x+=10){add(q,x,y,-38,Material.BLUE_STAINED_GLASS);add(q,x,y,38,Material.BLUE_STAINED_GLASS);}
  for(int y=2;y<=35;y++){int x=30-(y%9);add(q,x,y,30,Material.STONE_BRICK_STAIRS);}
  fill(q,-5,5,2,11,-38,-37,Material.AIR);
  // Ground-floor throne hall with columns, dais, carpet, chandeliers and side seating.
  floor(q,-22,22,2,-28,10,Material.POLISHED_BLACKSTONE);fill(q,-2,2,3,3,5,28,Material.RED_CARPET);for(int z=-22;z<=4;z+=7){for(int x:new int[]{-17,17})fill(q,x,x,3,8,z,z,Material.QUARTZ_PILLAR);}
  floor(q,-7,7,3,13,20,Material.POLISHED_DEEPSLATE);add(q,0,4,18,Material.GOLD_BLOCK);add(q,0,5,18,Material.RED_WOOL);for(int x:new int[]{-5,5})for(int z=14;z<=19;z+=5)add(q,x,4,z,Material.LANTERN);
  // Dining hall + kitchen.
  room(q,-36,-5,-34,-8,2,Material.STONE_BRICKS,Material.SPRUCE_PLANKS);for(int z=-29;z<=-13;z+=5)for(int x=-31;x<=-10;x+=7)furniture(q,x,3,z,"table");
  room(q,5,36,-34,-8,2,Material.STONE_BRICKS,Material.POLISHED_ANDESITE);for(int x=10;x<=30;x+=5){add(q,x,3,-28,Material.SMOKER);add(q,x,3,-22,Material.BARREL);add(q,x,3,-16,Material.CRAFTING_TABLE);}
  // Library and map/war room.
  room(q,-36,-5,12,34,2,Material.STONE_BRICKS,Material.OAK_PLANKS);for(int z=16;z<=30;z+=3)for(int x:new int[]{-33,-28,-23,-18,-13,-8})add(q,x,3,z,Material.BOOKSHELF);add(q,-20,3,23,Material.LECTERN);
  room(q,5,36,12,34,2,Material.STONE_BRICKS,Material.DARK_OAK_PLANKS);for(int x=12;x<=30;x+=6)furniture(q,x,3,23,"table");add(q,20,3,30,Material.CARTOGRAPHY_TABLE);
  // Upper-floor royal suite, guest rooms, barracks, armory and alchemy/enchanting rooms.
  room(q,-36,-5,-34,-8,11,Material.STONE_BRICKS,Material.SPRUCE_PLANKS);for(int x=-31;x<=-11;x+=10)furniture(q,x,12,-27,"bed");
  for(int x:new int[]{5,21})for(int z:new int[]{-34,-20}){room(q,x,x+14,z,z+12,11,Material.STONE_BRICKS,Material.OAK_PLANKS);furniture(q,x+4,12,z+4,"bed");}
  room(q,-36,-5,12,34,11,Material.STONE_BRICKS,Material.POLISHED_ANDESITE);for(int x=-32;x<=-10;x+=5)furniture(q,x,12,18,"armor");
  room(q,5,36,12,34,11,Material.STONE_BRICKS,Material.DARK_OAK_PLANKS);for(int x=10;x<=30;x+=5){add(q,x,12,18,Material.BREWING_STAND);add(q,x,12,25,Material.BOOKSHELF);}add(q,20,12,30,Material.ENCHANTING_TABLE);
  // Barracks floor: 24 furnished sleeping stations.
  for(int x=-34;x<=30;x+=8)for(int z=-30;z<=26;z+=14)furniture(q,x,21,z,"bed");
  // Storage floor: organized aisles, not stacked decorative chests.
  for(int x=-34;x<=34;x+=6)for(int z=-30;z<=30;z+=6){add(q,x,30,z,Material.BARREL);add(q,x,31,z,Material.BARREL);}
  // West forge/blacksmith complex.
  room(q,-94,-55,-30,30,1,Material.DEEPSLATE_BRICKS,Material.POLISHED_ANDESITE);for(int z=-24;z<=24;z+=6){add(q,-88,2,z,Material.BLAST_FURNACE);add(q,-80,2,z,Material.ANVIL);add(q,-72,2,z,Material.SMITHING_TABLE);add(q,-64,2,z,Material.GRINDSTONE);}
  // East grand arena: combat floor, tiered spectator seating and entrances.
  floor(q,55,99,1,-34,34,Material.SMOOTH_SANDSTONE);for(int ring=0;ring<5;ring++){int x1=51-ring,x2=103+ring,z1=-38-ring,z2=38+ring;for(int x=x1;x<=x2;x++){add(q,x,2+ring,z1,Material.STONE_BRICK_STAIRS);add(q,x,2+ring,z2,Material.STONE_BRICK_STAIRS);}for(int z=z1;z<=z2;z++){add(q,x1,2+ring,z,Material.STONE_BRICK_STAIRS);add(q,x2,2+ring,z,Material.STONE_BRICK_STAIRS);}}
  fill(q,75,79,2,7,-44,-38,Material.AIR);fill(q,75,79,2,7,38,44,Material.AIR);
  // Chapel and indoor garden/fountain.
  room(q,-100,-60,55,100,1,Material.STONE_BRICKS,Material.SMOOTH_STONE);for(int z=64;z<=92;z+=7)for(int x:new int[]{-94,-66})add(q,x,2,z,Material.WHITE_CANDLE);fill(q,-82,-78,2,5,91,95,Material.QUARTZ_BLOCK);
  room(q,55,100,55,100,1,Material.STONE_BRICKS,Material.MOSS_BLOCK);fill(q,75,80,2,2,75,80,Material.WATER);fill(q,77,78,2,6,77,78,Material.QUARTZ_PILLAR);for(int x=61;x<=94;x+=11)for(int z=61;z<=94;z+=11)add(q,x,2,z,Material.FLOWERING_AZALEA);
  // Dungeon/prison and reinforced treasury under the keep, with corridors.
  shell(q,-38,38,-34,34,-8,-1,Material.REINFORCED_DEEPSLATE);floor(q,-37,37,-7,-33,33,Material.DEEPSLATE_TILES);
  for(int x=-34;x<=-4;x+=10){room(q,x,x+7,-30,-18,-7,Material.DEEPSLATE_BRICKS,Material.DEEPSLATE_TILES);fill(q,x+2,x+5,-6,-3,-18,-18,Material.IRON_BARS);}
  room(q,5,34,-30,-5,-7,Material.REINFORCED_DEEPSLATE,Material.GOLD_BLOCK);for(int x=9;x<=30;x+=5)for(int z=-26;z<=-9;z+=5)add(q,x,-6,z,Material.BARREL);
  // Secret passage from vault toward west wing.
  fill(q,-55,4,-6,-4,-7,-4,Material.AIR);floor(q,-55,4,-7,-7,-4,Material.POLISHED_DEEPSLATE);
  // Courtyard roads, fountain, market/storage sheds and lighting.
  floor(q,-8,8,1,-104,-39,Material.POLISHED_ANDESITE);floor(q,-8,8,1,39,104,Material.POLISHED_ANDESITE);floor(q,-104,-41,1,-5,5,Material.POLISHED_ANDESITE);floor(q,41,104,1,-5,5,Material.POLISHED_ANDESITE);
  fill(q,-5,5,1,1,44,54,Material.WATER);fill(q,-1,1,1,7,48,50,Material.QUARTZ_PILLAR);
  for(int x=-100;x<=100;x+=20)for(int z=-100;z<=100;z+=20){add(q,x,2,z,Material.STONE_BRICK_WALL);add(q,x,3,z,Material.LANTERN);}
  // Decorative keep buttresses and roof crenellations.
  pillars(q,-40,40,-38,38,2,38);for(int x=-40;x<=40;x+=4){add(q,x,41,-38,Material.STONE_BRICKS);add(q,x,41,38,Material.STONE_BRICKS);}for(int z=-38;z<=38;z+=4){add(q,-40,41,z,Material.STONE_BRICKS);add(q,40,41,z,Material.STONE_BRICKS);}
  final int total=q.size();new BukkitRunnable(){int i=0,last=-1;public void run(){try{int budget=1200;while(budget-->0&&i<total){Change c=q.get(i++);Block b=o.clone().add(c.x,c.y,c.z).getBlock();b.setType(c.m,false);}int pct=i*100/total;if(pct/10!=last/10){last=pct;owner.sendMessage(ChatColor.YELLOW+"Citadel construction: "+pct+"%");}if(i>=total){building=false;owner.sendMessage(ChatColor.GREEN+"ESN Mega Castle Citadel complete.");cancel();}}catch(Exception ex){building=false;plugin.getLogger().severe("Castle build stopped safely: "+ex.getMessage());owner.sendMessage(ChatColor.RED+"Castle build stopped safely. Check console.");cancel();}}}.runTaskTimer(plugin,1L,1L);
 }
}
