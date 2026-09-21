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
  Location o=e.getBlockPlaced().getLocation(); if(o.getBlockY()<20||o.getBlockY()>245-75){e.setCancelled(true);p.sendMessage(ChatColor.RED+"Unsafe build height.");return;}
  building=true;p.sendMessage(ChatColor.GOLD+"Constructing the fully detailed ESN Mega Castle...");build(o.clone().add(0,-1,0),p);
 }
 private record Change(int x,int y,int z,Material m){}
 private void add(List<Change>q,int x,int y,int z,Material m){q.add(new Change(x,y,z,m));}
 private void fill(List<Change>q,int x1,int x2,int y1,int y2,int z1,int z2,Material m){for(int x=x1;x<=x2;x++)for(int y=y1;y<=y2;y++)for(int z=z1;z<=z2;z++)add(q,x,y,z,m);}
 private void floor(List<Change>q,int x1,int x2,int y,int z1,int z2,Material m){fill(q,x1,x2,y,y,z1,z2,m);}
 private void shell(List<Change>q,int x1,int x2,int z1,int z2,int y1,int y2,Material m){for(int y=y1;y<=y2;y++)for(int x=x1;x<=x2;x++)for(int z=z1;z<=z2;z++)if(y==y1||y==y2||x==x1||x==x2||z==z1||z==z2)add(q,x,y,z,m);}
 private void room(List<Change>q,int x1,int x2,int z1,int z2,int y,Material wall,Material fl){floor(q,x1,x2,y,z1,z2,fl);for(int h=1;h<=6;h++){for(int x=x1;x<=x2;x++){add(q,x,y+h,z1,wall);add(q,x,y+h,z2,wall);}for(int z=z1;z<=z2;z++){add(q,x1,y+h,z,wall);add(q,x2,y+h,z,wall);}}floor(q,x1,x2,y+7,z1,z2,Material.DARK_OAK_PLANKS);for(int h=1;h<=3;h++){add(q,(x1+x2)/2,y+h,z1,Material.AIR);add(q,(x1+x2)/2,y+h,z2,Material.AIR);}}
 private void cylinder(List<Change>q,int cx,int cz,int r,int y1,int y2,Material m){for(int y=y1;y<=y2;y++)for(int x=-r;x<=r;x++)for(int z=-r;z<=r;z++){double d=Math.sqrt(x*x+z*z);if(d>=r-1&&d<=r+.5)add(q,cx+x,y,cz+z,m);}}
 private void roof(List<Change>q,int cx,int cz,int rx,int rz,int y){int max=Math.min(rx,rz);for(int n=0;n<=max;n++){int x1=cx-rx+n,x2=cx+rx-n,z1=cz-rz+n,z2=cz+rz-n,yy=y+n/2;Material rm=(n%4<2)?Material.DEEPSLATE_TILES:Material.DARK_OAK_PLANKS;for(int x=x1;x<=x2;x++){add(q,x,yy,z1,rm);add(q,x,yy,z2,rm);}for(int z=z1;z<=z2;z++){add(q,x1,yy,z,rm);add(q,x2,yy,z,rm);}}fill(q,cx,cx,y+max/2,y+max/2+4,cz,cz,Material.DARK_OAK_LOG);}
 private void tower(List<Change>q,int cx,int cz,int r,int y1,int y2){cylinder(q,cx,cz,r,y1,y2,Material.STONE_BRICKS);for(int y=y1+5;y<y2;y+=7){floor(q,cx-r+1,cx+r-1,y,cz-r+1,cz+r-1,Material.DARK_OAK_PLANKS);for(int yy=y1+1;yy<y2;yy++)add(q,cx,yy,cz,Material.LADDER);}for(int y=y1+5;y<y2;y+=7){add(q,cx,y,cz-r,Material.BLUE_STAINED_GLASS);add(q,cx,y,cz+r,Material.BLUE_STAINED_GLASS);add(q,cx-r,y,cz,Material.BLUE_STAINED_GLASS);add(q,cx+r,y,cz,Material.BLUE_STAINED_GLASS);}for(int a=-r;a<=r;a+=3){add(q,cx+a,y2+1,cz-r,Material.STONE_BRICKS);add(q,cx+a,y2+1,cz+r,Material.STONE_BRICKS);add(q,cx-r,y2+1,cz+a,Material.STONE_BRICKS);add(q,cx+r,y2+1,cz+a,Material.STONE_BRICKS);}roof(q,cx,cz,r+3,r+3,y2+2);}
 private void facade(List<Change>q,int x1,int x2,int z,int y1,int y2){for(int x=x1;x<=x2;x+=6){fill(q,x,x,y1,y2,z,z,Material.POLISHED_DIORITE);if(x+2<=x2){add(q,x+2,y1+4,z,Material.BLUE_STAINED_GLASS);add(q,x+2,y1+5,z,Material.BLUE_STAINED_GLASS);}}for(int x=x1;x<=x2;x+=3)add(q,x,y2+1,z,Material.STONE_BRICK_WALL);}
 private void tree(List<Change>q,int x,int y,int z){fill(q,x,x,y,y+5,z,z,Material.SPRUCE_LOG);for(int yy=y+3;yy<=y+7;yy++)for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++)if(Math.abs(dx)+Math.abs(dz)<=5-(yy-y-3)/2)add(q,x+dx,yy,z+dz,Material.SPRUCE_LEAVES);}
 private void spire(List<Change>q,int cx,int cz,int r,int y){for(int layer=0;layer<=r;layer++){int rr=Math.max(0,r-layer),yy=y+layer;for(int x=-rr;x<=rr;x++)for(int z=-rr;z<=rr;z++)if(Math.abs(x)==rr||Math.abs(z)==rr)add(q,cx+x,yy,cz+z,layer%2==0?Material.DARK_OAK_PLANKS:Material.SPRUCE_PLANKS);}fill(q,cx,cx,y+r+1,y+r+5,cz,cz,Material.DARK_OAK_FENCE);add(q,cx,y+r+6,cz,Material.BLUE_WOOL);}
 private void arch(List<Change>q,int cx,int y,int z,int w,int h){for(int x=-w;x<=w;x++)add(q,cx+x,y+h,z,Material.POLISHED_DIORITE);for(int yy=0;yy<h;yy++){add(q,cx-w,y+yy,z,Material.POLISHED_DIORITE);add(q,cx+w,y+yy,z,Material.POLISHED_DIORITE);}for(int x=-w+1;x<w;x++)for(int yy=0;yy<h;yy++)add(q,cx+x,y+yy,z,Material.AIR);}
 private void banner(List<Change>q,int x,int y,int z){fill(q,x,x,y,y+4,z,z,Material.BLUE_WOOL);add(q,x,y, z,Material.GOLD_BLOCK);}
 private void pillars(List<Change>q,int x1,int x2,int z1,int z2,int y1,int y2){for(int x=x1;x<=x2;x+=8)for(int z:new int[]{z1,z2})fill(q,x,x,y1,y2,z,z,Material.POLISHED_DEEPSLATE);}
 private void furniture(List<Change>q,int x,int y,int z,String type){
  if(type.equals("bed")){add(q,x,y,z,Material.RED_WOOL);add(q,x+1,y,z,Material.RED_WOOL);add(q,x,y+1,z,Material.RED_CARPET);add(q,x+1,y+1,z,Material.RED_CARPET);add(q,x+2,y,z,Material.BARREL);add(q,x+2,y+1,z,Material.LANTERN);}
  if(type.equals("table")){add(q,x,y,z,Material.OAK_FENCE);add(q,x,y+1,z,Material.OAK_PRESSURE_PLATE);}
  if(type.equals("armor")){add(q,x,y,z,Material.POLISHED_BLACKSTONE_WALL);add(q,x,y+1,z,Material.IRON_BLOCK);add(q,x+2,y,z,Material.SMITHING_TABLE);}
 }
 private boolean occupiedWard(int x,int z){return (Math.abs(x)<=58&&Math.abs(z)<=58)||(x>=48&&z>=-48&&z<=48)||(x<=-48&&z>=-38&&z<=38)||(x<=-55&&z>=48)||(x>=48&&z>=48)||(z<=-44&&Math.abs(x)<=22)||(Math.abs(x)<=12)||(Math.abs(z)<=12);}\n private void build(Location o,Player owner){
  List<Change>q=new ArrayList<>();int R=115;
  // Clean construction envelope and lay a deliberate courtyard instead of random flooring.
  for(int x=-R;x<=R;x++)for(int z=-R;z<=R;z++)for(int y=-8;y<=75;y++)add(q,x,y,z,Material.AIR);
  fill(q,-115,115,-3,-1,-115,115,Material.DEEPSLATE_BRICKS);floor(q,-115,115,0,-115,115,Material.STONE_BRICKS);
  // Outer curtain walls, battlements, four gatehouses and corner towers.
  for(int y=1;y<=15;y++){for(int x=-115;x<=115;x++){add(q,x,y,-115,Material.DEEPSLATE_BRICKS);add(q,x,y,115,Material.DEEPSLATE_BRICKS);}for(int z=-114;z<=114;z++){add(q,-115,y,z,Material.DEEPSLATE_BRICKS);add(q,115,y,z,Material.DEEPSLATE_BRICKS);}}
  for(int x=-115;x<=115;x+=4){add(q,x,16,-115,Material.POLISHED_DEEPSLATE);add(q,x,16,115,Material.POLISHED_DEEPSLATE);}
  for(int z=-115;z<=115;z+=4){add(q,-115,16,z,Material.POLISHED_DEEPSLATE);add(q,115,16,z,Material.POLISHED_DEEPSLATE);}
  int[][]ts={{-104,-104},{104,-104},{-104,104},{104,104},{-104,-55},{104,-55},{-104,55},{104,55},{-55,-104},{55,-104},{-55,104},{55,104}};for(int[]t:ts){tower(q,t[0],t[1],9,0,30);spire(q,t[0],t[1],8,32);}
  // Grand south gate: arched opening, portcullis frame and gatehouse.
  fill(q,-15,15,1,22,-114,-104,Material.STONE_BRICKS);fill(q,-6,6,1,10,-115,-103,Material.AIR);for(int x=-6;x<=6;x+=2)fill(q,x,x,1,9,-104,-104,Material.IRON_BARS);
  // Central keep with towers, floors, windows, roofline and connected grand staircase.
  shell(q,-40,40,-38,38,1,40,Material.STONE_BRICKS);for(int y:new int[]{1,10,19,28,37})floor(q,-39,39,y,-37,37,Material.DARK_OAK_PLANKS);
  int[][]kt={{-36,-34},{36,-34},{-36,34},{36,34},{0,-34},{0,34}};for(int[]t:kt){tower(q,t[0],t[1],7,1,46);spire(q,t[0],t[1],7,48);}roof(q,0,0,42,40,42);spire(q,0,0,13,49);facade(q,-38,38,-39,3,37);facade(q,-38,38,39,3,37);
  for(int y:new int[]{6,15,24,33})for(int x=-30;x<=30;x+=10){add(q,x,y,-38,Material.BLUE_STAINED_GLASS);add(q,x,y,38,Material.BLUE_STAINED_GLASS);}
  for(int y=2;y<=35;y++){int x=30-(y%9);add(q,x,y,30,Material.STONE_BRICK_STAIRS);}
  fill(q,-5,5,2,11,-38,-37,Material.AIR);arch(q,0,2,-39,7,11);for(int x:new int[]{-28,-18,-8,8,18,28}){arch(q,x,5,-39,2,5);banner(q,x,12,-40);}
  // Monumental layered central palace: extra terraces, towers, galleries and chapel-like crown.
  shell(q,-52,52,-50,50,1,28,Material.STONE_BRICKS);for(int y:new int[]{8,16,24})floor(q,-51,51,y,-49,49,Material.DARK_OAK_PLANKS);facade(q,-50,50,-51,3,27);facade(q,-50,50,51,3,27);
  int[][] crown={{-48,-46},{48,-46},{-48,46},{48,46},{-24,-48},{24,-48},{-24,48},{24,48}};for(int[]t:crown){tower(q,t[0],t[1],6,1,34);spire(q,t[0],t[1],6,36);}
  for(int ring=0;ring<3;ring++){int a=58+ring*13;for(int x=-a;x<=a;x+=13){fill(q,x,x,2,10,-a,-a,Material.STONE_BRICKS);fill(q,x,x,2,10,a,a,Material.STONE_BRICKS);}for(int z=-a;z<=a;z+=13){fill(q,-a,-a,2,10,z,z,Material.STONE_BRICKS);fill(q,a,a,2,10,z,z,Material.STONE_BRICKS);}}
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
  // Layered inner ward buildings and galleries create the dense reference silhouette.
  int[][] wings={{-92,-48,-92,-48},{48,92,-92,-48},{-92,-48,48,92},{48,92,48,92}};for(int[]w:wings){shell(q,w[0],w[1],w[2],w[3],1,18,Material.STONE_BRICKS);roof(q,(w[0]+w[1])/2,(w[2]+w[3])/2,23,23,19);facade(q,w[0]+2,w[1]-2,w[2]-1,3,16);}
  for(int x=-96;x<=96;x+=16){fill(q,x,x,2,12,-114,-111,Material.POLISHED_DIORITE);fill(q,x,x,2,12,111,114,Material.POLISHED_DIORITE);}for(int z=-96;z<=96;z+=16){fill(q,-114,-111,2,12,z,z,Material.POLISHED_DIORITE);fill(q,111,114,2,12,z,z,Material.POLISHED_DIORITE);}
  // Dense landscaped inner wards inspired by a high-detail medieval citadel.
  for(int x=-92;x<=92;x+=16)for(int z=-92;z<=92;z+=18)if((Math.abs(x)>45||Math.abs(z)>45)&&!occupiedWard(x,z))tree(q,x,2,z);
  for(int x=-108;x<=108;x+=12){add(q,x,2,-108,Material.OAK_LEAVES);add(q,x,2,108,Material.OAK_LEAVES);}for(int z=-108;z<=108;z+=12){add(q,-108,2,z,Material.OAK_LEAVES);add(q,108,2,z,Material.OAK_LEAVES);}
  // Formal gardens: hedges, fountains, paths and dozens of trees fill otherwise empty wards.
  for(int x=-100;x<=100;x+=10)for(int z=-100;z<=100;z+=10)if((Math.abs(x)>55||Math.abs(z)>55)&&!occupiedWard(x,z)){add(q,x,2,z,Material.OAK_LEAVES);if((x+z)%20==0)add(q,x,3,z,Material.FLOWERING_AZALEA_LEAVES);}for(int[]f:new int[][]{{-72,0},{72,0},{0,72},{0,-72}}){fill(q,f[0]-4,f[0]+4,1,1,f[1]-4,f[1]+4,Material.WATER);fill(q,f[0],f[0],2,7,f[1],f[1],Material.QUARTZ_PILLAR);add(q,f[0],8,f[1],Material.SEA_LANTERN);}
  // Courtyard roads, fountain, market/storage sheds and lighting.
  floor(q,-8,8,1,-104,-39,Material.POLISHED_ANDESITE);floor(q,-8,8,1,39,104,Material.POLISHED_ANDESITE);floor(q,-104,-41,1,-5,5,Material.POLISHED_ANDESITE);floor(q,41,104,1,-5,5,Material.POLISHED_ANDESITE);
  fill(q,-5,5,1,1,44,54,Material.WATER);fill(q,-1,1,1,7,48,50,Material.QUARTZ_PILLAR);
  for(int x=-100;x<=100;x+=20)for(int z=-100;z<=100;z+=20){add(q,x,2,z,Material.STONE_BRICK_WALL);add(q,x,3,z,Material.LANTERN);}
  // Decorative keep buttresses and roof crenellations.
  pillars(q,-40,40,-38,38,2,38);for(int x=-40;x<=40;x+=4){add(q,x,41,-38,Material.STONE_BRICKS);add(q,x,41,38,Material.STONE_BRICKS);}for(int z=-38;z<=38;z+=4){add(q,-40,41,z,Material.STONE_BRICKS);add(q,40,41,z,Material.STONE_BRICKS);}
  // MAX DETAIL PASS: gatehouse towers, inner curtain, bridge, cloister, markets, stables and defensive lighting.
  for(int[]t:new int[][]{{-18,-108},{18,-108},{-18,108},{18,108}}){tower(q,t[0],t[1],7,1,28);spire(q,t[0],t[1],7,30);}
  // Inner curtain creates a true layered citadel rather than disconnected buildings.
  for(int y=2;y<=12;y++){for(int x=-62;x<=62;x++){if(Math.abs(x)>8){add(q,x,y,-62,Material.STONE_BRICKS);add(q,x,y,62,Material.STONE_BRICKS);}}for(int z=-61;z<=61;z++){add(q,-62,y,z,Material.STONE_BRICKS);add(q,62,y,z,Material.STONE_BRICKS);}}
  for(int x=-62;x<=62;x+=4){add(q,x,13,-62,Material.STONE_BRICK_WALL);add(q,x,13,62,Material.STONE_BRICK_WALL);}for(int z=-62;z<=62;z+=4){add(q,-62,13,z,Material.STONE_BRICK_WALL);add(q,62,13,z,Material.STONE_BRICK_WALL);}
  // Grand bridge from south gate to keep with parapets and lanterns.
  floor(q,-7,7,1,-103,-40,Material.POLISHED_ANDESITE);for(int z=-100;z<=-44;z++){add(q,-8,2,z,Material.STONE_BRICK_WALL);add(q,8,2,z,Material.STONE_BRICK_WALL);}for(int z=-96;z<=-48;z+=12){add(q,-8,3,z,Material.LANTERN);add(q,8,3,z,Material.LANTERN);}
  // Cloister galleries surrounding the central keep.
  for(int x=-50;x<=50;x+=5)for(int z:new int[]{-54,54}){fill(q,x,x,2,8,z,z,Material.POLISHED_DIORITE);add(q,x,9,z,Material.STONE_BRICKS);}for(int z=-49;z<=49;z+=5)for(int x:new int[]{-54,54}){fill(q,x,x,2,8,z,z,Material.POLISHED_DIORITE);add(q,x,9,z,Material.STONE_BRICKS);}
  // Stable and market quarters are fully floored and furnished.
  room(q,-108,-70,-92,-48,1,Material.STONE_BRICKS,Material.SPRUCE_PLANKS);for(int z=-86;z<=-54;z+=8){for(int x=-103;x<=-76;x+=9){add(q,x,2,z,Material.HAY_BLOCK);add(q,x+2,2,z,Material.OAK_FENCE);add(q,x+3,2,z,Material.WATER_CAULDRON);}}
  room(q,70,108,-92,-48,1,Material.STONE_BRICKS,Material.STONE_BRICKS);for(int x=76;x<=102;x+=8)for(int z=-86;z<=-56;z+=10){add(q,x,2,z,Material.BARREL);add(q,x+1,2,z,Material.CRAFTING_TABLE);add(q,x,3,z,Material.LANTERN);}
  // Additional residential/guard buildings make the wards dense and complete.
  for(int[]b:new int[][]{{-108,-76,42,70},{76,108,42,70},{-108,-76,74,106},{76,108,74,106}}){shell(q,b[0],b[1],b[2],b[3],1,14,Material.STONE_BRICKS);floor(q,b[0]+1,b[1]-1,1,b[2]+1,b[3]-1,Material.OAK_PLANKS);roof(q,(b[0]+b[1])/2,(b[2]+b[3])/2,(b[1]-b[0])/2+1,(b[3]-b[2])/2+1,15);}
  // Exterior detail bands, windows and torches on all major curtain faces.
  for(int x=-108;x<=108;x+=8){add(q,x,8,-114,Material.CHISELED_STONE_BRICKS);add(q,x,8,114,Material.CHISELED_STONE_BRICKS);if(x%16==0){add(q,x,10,-114,Material.SEA_LANTERN);add(q,x,10,114,Material.SEA_LANTERN);}}for(int z=-108;z<=108;z+=8){add(q,-114,8,z,Material.CHISELED_STONE_BRICKS);add(q,114,8,z,Material.CHISELED_STONE_BRICKS);if(z%16==0){add(q,-114,10,z,Material.SEA_LANTERN);add(q,114,10,z,Material.SEA_LANTERN);}}
  // Fully connected underground service corridors beneath all four wings.
  fill(q,-100,100,-6,-4,-3,3,Material.DEEPSLATE_BRICKS);fill(q,-3,3,-6,-4,-100,100,Material.DEEPSLATE_BRICKS);fill(q,-98,98,-5,-5,-2,2,Material.AIR);fill(q,-2,2,-5,-5,-98,98,Material.AIR);for(int x=-90;x<=90;x+=15){add(q,x,-4,0,Material.SEA_LANTERN);}for(int z=-90;z<=90;z+=15){add(q,0,-4,z,Material.SEA_LANTERN);}
  // Final structural integrity pass: seal every unintended foundation/wall gap before block placement.
  fill(q,-115,115,-3,-1,-115,115,Material.DEEPSLATE_BRICKS);floor(q,-115,115,0,-115,115,Material.STONE_BRICKS);
  for(int y=1;y<=15;y++){for(int x=-115;x<=115;x++){if(!(x>=-6&&x<=6)){add(q,x,y,-115,Material.DEEPSLATE_BRICKS);}add(q,x,y,115,Material.DEEPSLATE_BRICKS);}for(int z=-114;z<=114;z++){add(q,-115,y,z,Material.DEEPSLATE_BRICKS);add(q,115,y,z,Material.DEEPSLATE_BRICKS);}}
  // Re-open only the intentional south gate after sealing the curtain wall.
  fill(q,-6,6,1,10,-115,-103,Material.AIR);for(int x=-6;x<=6;x+=2)fill(q,x,x,1,9,-104,-104,Material.IRON_BARS);
  // Guaranteed solid floor plates under all major occupied structures.
  floor(q,-52,52,1,-50,50,Material.STONE_BRICKS);floor(q,-100,-55,1,-35,35,Material.POLISHED_ANDESITE);floor(q,50,105,1,-42,42,Material.SMOOTH_STONE);floor(q,-105,-55,1,50,105,Material.SMOOTH_STONE);floor(q,50,105,1,50,105,Material.MOSS_BLOCK);
  final int total=q.size();new BukkitRunnable(){int i=0,last=-1;public void run(){try{int budget=1200;while(budget-->0&&i<total){Change c=q.get(i++);if(!c.m.isBlock()){plugin.getLogger().warning("Skipped non-block castle material: "+c.m);continue;}Block b=o.clone().add(c.x,c.y,c.z).getBlock();b.setType(c.m,false);}int pct=i*100/total;if(pct/10!=last/10){last=pct;owner.sendMessage(ChatColor.YELLOW+"Citadel construction: "+pct+"%");}if(i>=total){building=false;owner.sendMessage(ChatColor.GREEN+"ESN Mega Castle Citadel complete.");cancel();}}catch(Exception ex){building=false;plugin.getLogger().severe("Castle build stopped safely: "+ex.getMessage());owner.sendMessage(ChatColor.RED+"Castle build stopped safely. Check console.");cancel();}}}.runTaskTimer(plugin,1L,1L);
 }
}
