package com.esn.smp.gameplay;

import com.esn.smp.data.ESNDataStore;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.*;

public final class SMPGameplay implements CommandExecutor, Listener {
 private final ESNDataStore data; private final Map<UUID,Integer> mined=new HashMap<>(), kills=new HashMap<>();
 public SMPGameplay(ESNDataStore d){data=d;}
 @Override public boolean onCommand(CommandSender s,Command c,String l,String[] a){
  if(!(s instanceof Player p)){s.sendMessage("Players only.");return true;}
  switch(c.getName().toLowerCase()){
   case "menu" -> openMenu(p);
   case "shop" -> openShop(p);
   case "crates" -> openCrates(p);
   case "daily" -> daily(p);
   case "quests" -> quests(p);
   case "leaderboard" -> leaderboard(p);
   case "warps" -> warps(p);
  } return true;
 }
 public void openMenu(Player p){Inventory i=Bukkit.createInventory(null,27,ChatColor.GOLD+"ESN SMP Menu");i.setItem(10,b(Material.EMERALD,ChatColor.GREEN+"Server Shop"));i.setItem(12,b(Material.ENDER_CHEST,ChatColor.LIGHT_PURPLE+"Loot Crates"));i.setItem(14,b(Material.WRITABLE_BOOK,ChatColor.AQUA+"Daily & Quests"));i.setItem(16,b(Material.COMPASS,ChatColor.YELLOW+"Warps"));p.openInventory(i);}
 public void openShop(Player p){Inventory i=Bukkit.createInventory(null,27,ChatColor.DARK_GREEN+"ESN Server Shop");i.setItem(10,b(Material.COOKED_BEEF,ChatColor.GREEN+"Buy 16 Steak - 120 Coins"));i.setItem(12,b(Material.OAK_LOG,ChatColor.GREEN+"Buy 32 Logs - 160 Coins"));i.setItem(14,b(Material.IRON_INGOT,ChatColor.GREEN+"Buy 8 Iron - 240 Coins"));i.setItem(16,b(Material.DIAMOND,ChatColor.GREEN+"Buy 1 Diamond - 500 Coins"));p.openInventory(i);}
 public void openCrates(Player p){Inventory i=Bukkit.createInventory(null,27,ChatColor.DARK_PURPLE+"ESN Loot Crates");i.setItem(13,b(Material.ENDER_CHEST,ChatColor.LIGHT_PURPLE+"ESN Crate",List.of(ChatColor.GRAY+"Requires: ESN Crate Key",ChatColor.YELLOW+"Click with a key in your inventory")));p.openInventory(i);}
 @EventHandler public void click(InventoryClickEvent e){String t=e.getView().getTitle();if(!t.startsWith(ChatColor.GOLD+"ESN SMP")&&!t.startsWith(ChatColor.DARK_GREEN+"ESN Server")&&!t.startsWith(ChatColor.DARK_PURPLE+"ESN Loot"))return;e.setCancelled(true);if(!(e.getWhoClicked() instanceof Player p))return;int s=e.getRawSlot();
  if(t.contains("SMP Menu")){if(s==10)openShop(p);else if(s==12)openCrates(p);else if(s==14){p.closeInventory();daily(p);quests(p);}else if(s==16){p.closeInventory();warps(p);}return;}
  if(t.contains("Server Shop")){if(s==10)buy(p,new ItemStack(Material.COOKED_BEEF,16),120);else if(s==12)buy(p,new ItemStack(Material.OAK_LOG,32),160);else if(s==14)buy(p,new ItemStack(Material.IRON_INGOT,8),240);else if(s==16)buy(p,new ItemStack(Material.DIAMOND),500);return;}
  if(t.contains("Loot Crates")&&s==13)openCrate(p);
 }
 private void buy(Player p,ItemStack item,long price){try{if(!data.withdraw(p,price)){p.sendMessage(ChatColor.RED+"Not enough ESN Coins.");return;}HashMap<Integer,ItemStack> left=p.getInventory().addItem(item);if(!left.isEmpty()){data.deposit(p,price);p.sendMessage(ChatColor.RED+"Inventory full. Purchase refunded.");return;}p.sendMessage(ChatColor.GREEN+"Purchased for "+price+" ESN Coins.");}catch(Exception x){p.sendMessage(ChatColor.RED+"Purchase failed safely.");}}
 private void openCrate(Player p){int slot=findKey(p);if(slot<0){p.sendMessage(ChatColor.RED+"You need an ESN Crate Key.");return;}ItemStack k=p.getInventory().getItem(slot);if(k.getAmount()==1)p.getInventory().setItem(slot,null);else k.setAmount(k.getAmount()-1);ItemStack reward=reward();HashMap<Integer,ItemStack> left=p.getInventory().addItem(reward);if(!left.isEmpty())p.getWorld().dropItemNaturally(p.getLocation(),reward);p.sendMessage(ChatColor.GOLD+"Crate reward: "+reward.getAmount()+"x "+reward.getType().name());}
 private int findKey(Player p){for(int i=0;i<p.getInventory().getSize();i++){ItemStack x=p.getInventory().getItem(i);if(x!=null&&x.getType()==Material.TRIPWIRE_HOOK&&x.hasItemMeta()&&ChatColor.GOLD+"ESN Crate Key".equals(x.getItemMeta().getDisplayName()))return i;}return -1;}
 private ItemStack reward(){int r=new Random().nextInt(100);if(r<2)return CustomItems.titanBlade();if(r<4)return CustomItems.voidPick();if(r<5)return CustomItems.titanChestplate();if(r<6)return CustomItems.voidHelmet();if(r<10)return new ItemStack(Material.NETHERITE_INGOT);if(r<20)return new ItemStack(Material.DIAMOND,3);if(r<50)return new ItemStack(Material.GOLDEN_APPLE,2);return new ItemStack(Material.EXPERIENCE_BOTTLE,16);}
 public ItemStack key(){return b(Material.TRIPWIRE_HOOK,ChatColor.GOLD+"ESN Crate Key",List.of(ChatColor.GRAY+"Opens one ESN Loot Crate"));}
 private void daily(Player p){try{long wait=data.claimDaily(p);if(wait==0){p.getInventory().addItem(key());p.sendMessage(ChatColor.GREEN+"Daily claimed: 250 Coins + 1 ESN Crate Key!");}else p.sendMessage(ChatColor.YELLOW+"Daily available in "+((wait+3599999)/3600000)+"h.");}catch(Exception x){p.sendMessage(ChatColor.RED+"Daily reward unavailable.");}}
 private void quests(Player p){int m=mined.getOrDefault(p.getUniqueId(),0),k=kills.getOrDefault(p.getUniqueId(),0);p.sendMessage(ChatColor.AQUA+"Daily Quests: "+m+"/64 blocks mined | "+k+"/10 hostile mobs defeated");}
 private void leaderboard(Player p){try{p.sendMessage(ChatColor.GOLD+"--- ESN Richest Players ---");int n=1;for(var e:data.topBalances(10))p.sendMessage(ChatColor.YELLOW.toString()+(n++)+". "+e.playerName()+" - "+e.balance()+" Coins");}catch(Exception x){p.sendMessage(ChatColor.RED+"Leaderboard unavailable.");}}
 private void warps(Player p){p.sendMessage(ChatColor.YELLOW+"Warps: "+ChatColor.WHITE+"/spawn "+ChatColor.GRAY+"• Survival exploration begins outside the protected spawn.");}
 @EventHandler public void mine(BlockBreakEvent e){mined.merge(e.getPlayer().getUniqueId(),1,Integer::sum);}
 @EventHandler public void death(EntityDeathEvent e){if(e.getEntity().getKiller()!=null)kills.merge(e.getEntity().getKiller().getUniqueId(),1,Integer::sum);}
 private ItemStack b(Material m,String n){return b(m,n,List.of());}private ItemStack b(Material m,String n,List<String> lore){ItemStack x=new ItemStack(m);ItemMeta im=x.getItemMeta();im.setDisplayName(n);im.setLore(lore);x.setItemMeta(im);return x;}
}
