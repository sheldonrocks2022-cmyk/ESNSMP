package com.esn.smp.gameplay;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.io.IOException;
import java.time.YearMonth;
import java.util.*;

public final class AdventureJournal implements CommandExecutor, Listener {
 private static final String TITLE=ChatColor.DARK_AQUA+"ESN Adventure Journal";
 private final JavaPlugin plugin;
 private final File file;
 private final YamlConfiguration data;
 public AdventureJournal(JavaPlugin plugin){
  this.plugin=plugin;this.file=new File(plugin.getDataFolder(),"journal.yml");this.data=YamlConfiguration.loadConfiguration(file);
 }
 private ItemStack item(Material m,String name,String... lore){
  ItemStack i=new ItemStack(m);ItemMeta im=i.getItemMeta();im.setDisplayName(name);im.setLore(Arrays.asList(lore));i.setItemMeta(im);return i;
 }
 @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
  if(!(sender instanceof Player p)){sender.sendMessage("Players only.");return true;}
  discoverMonth(p);open(p);return true;
 }
 private void discoverMonth(Player p){
  String month=YearMonth.now().toString(),base="players."+p.getUniqueId(),last=data.getString(base+".last-month","");
  if(month.equals(last))return;
  List<String> months=new ArrayList<>(data.getStringList(base+".months"));
  if(!months.contains(month))months.add(month);
  data.set(base+".months",months);data.set(base+".last-month",month);data.set(base+".discoveries",months.size());
  save();
  Map<Integer,ItemStack> overflow=p.getInventory().addItem(MegaCrates.key(99));
  overflow.values().forEach(i->p.getWorld().dropItemNaturally(p.getLocation(),i));
  p.sendMessage(ChatColor.GOLD+"✦ New Journal Month Discovered: "+ChatColor.WHITE+month);
  p.sendMessage(ChatColor.LIGHT_PURPLE+"Reward: "+ChatColor.GOLD+"1 Realm 100 Crate Key");
  p.playSound(p.getLocation(),Sound.UI_TOAST_CHALLENGE_COMPLETE,1f,1f);
 }
 public void open(Player p){
  Inventory v=Bukkit.createInventory(null,54,TITLE);
  String base="players."+p.getUniqueId();List<String> months=data.getStringList(base+".months");
  v.setItem(4,item(Material.WRITTEN_BOOK,ChatColor.GOLD+"✦ "+p.getName()+"'s Journal ✦",ChatColor.GRAY+"Your central ESNSMP progression hub",ChatColor.AQUA+"Months discovered: "+ChatColor.WHITE+months.size(),ChatColor.LIGHT_PURPLE+"Monthly reward: "+ChatColor.GOLD+"Realm 100 Key"));
  v.setItem(10,item(Material.PLAYER_HEAD,ChatColor.AQUA+"Profile",ChatColor.GRAY+"View your full player profile",ChatColor.YELLOW+"Click: /profile"));
  v.setItem(11,item(Material.DIAMOND_SWORD,ChatColor.RED+"RPG Profile",ChatColor.GRAY+"Combat and RPG progression",ChatColor.YELLOW+"Click: /rpgprofile"));
  v.setItem(12,item(Material.WRITABLE_BOOK,ChatColor.YELLOW+"Quests",ChatColor.GRAY+"Active quests and objectives",ChatColor.YELLOW+"Click: /quests"));
  v.setItem(13,item(Material.COMPASS,ChatColor.GREEN+"Progression",ChatColor.GRAY+"See what comes next",ChatColor.YELLOW+"Click: /progression"));
  v.setItem(14,item(Material.DRAGON_HEAD,ChatColor.DARK_PURPLE+"Boss Codex",ChatColor.GRAY+"Boss discoveries and records",ChatColor.YELLOW+"Click: /bosscodex"));
  v.setItem(15,item(Material.BOOKSHELF,ChatColor.GOLD+"Collections",ChatColor.GRAY+"Collection progress",ChatColor.YELLOW+"Click: /collections"));
  v.setItem(16,item(Material.NETHER_STAR,ChatColor.LIGHT_PURPLE+"Achievements",ChatColor.GRAY+"Unlocked achievements",ChatColor.YELLOW+"Click: /achievements"));
  v.setItem(19,item(Material.CLOCK,ChatColor.GREEN+"Events",ChatColor.GRAY+"Current world event",ChatColor.YELLOW+"Click: /event"));
  v.setItem(20,item(Material.TOTEM_OF_UNDYING,ChatColor.GOLD+"Season Pass",ChatColor.GRAY+"Season progression and rewards",ChatColor.YELLOW+"Click: /seasonpass"));
  v.setItem(21,item(Material.ENDER_EYE,ChatColor.LIGHT_PURPLE+"Adventure",ChatColor.GRAY+"Classes, rifts, hunts and more",ChatColor.YELLOW+"Click: /adventure"));
  v.setItem(22,item(Material.BEACON,ChatColor.AQUA+"Realm Mastery",ChatColor.GRAY+"Your Realm mastery progress",ChatColor.YELLOW+"Click: /mastery"));
  v.setItem(23,item(Material.CHEST,ChatColor.GOLD+"Crates",ChatColor.GRAY+"Browse all crate tiers",ChatColor.YELLOW+"Click: /crates"));
  v.setItem(24,item(Material.EMERALD,ChatColor.GREEN+"Shop",ChatColor.GRAY+"Open the full ESN shop",ChatColor.YELLOW+"Click: /shop"));
  v.setItem(25,item(Material.SHIELD,ChatColor.BLUE+"Guild",ChatColor.GRAY+"Adventure guild progression",ChatColor.YELLOW+"Click: /guild"));
  String current=YearMonth.now().toString();
  v.setItem(31,item(Material.CLOCK,ChatColor.AQUA+"Monthly Discovery",ChatColor.GRAY+"Current month: "+ChatColor.WHITE+current,ChatColor.GRAY+"Discovered: "+(months.contains(current)?ChatColor.GREEN+"YES":ChatColor.RED+"NO"),ChatColor.GRAY+"Total discovered: "+ChatColor.WHITE+months.size(),ChatColor.GOLD+"Discover a new calendar month to earn",ChatColor.LIGHT_PURPLE+"1x Realm 100 Crate Key automatically."));
  v.setItem(40,item(Material.PAPER,ChatColor.YELLOW+"Recent Discoveries",months.isEmpty()?ChatColor.GRAY+"No months discovered yet.":ChatColor.GRAY+String.join(", ",months.subList(Math.max(0,months.size()-5),months.size()))));
  v.setItem(45,item(Material.ARROW,ChatColor.YELLOW+"Server Menu",ChatColor.GRAY+"Click: /menu"));
  v.setItem(49,item(Material.BARRIER,ChatColor.RED+"Close"));
  v.setItem(53,item(Material.COMPASS,ChatColor.AQUA+"Refresh Journal",ChatColor.GRAY+"Refresh all journal information"));
  p.openInventory(v);
 }
 @EventHandler public void click(InventoryClickEvent e){
  if(!e.getView().getTitle().equals(TITLE))return;e.setCancelled(true);
  if(!(e.getWhoClicked() instanceof Player p))return;
  int s=e.getRawSlot();if(s==49){p.closeInventory();return;}if(s==53){discoverMonth(p);open(p);return;}
  Map<Integer,String> cmds=Map.ofEntries(Map.entry(10,"profile"),Map.entry(11,"rpgprofile"),Map.entry(12,"quests"),Map.entry(13,"progression"),Map.entry(14,"bosscodex"),Map.entry(15,"collections"),Map.entry(16,"achievements"),Map.entry(19,"event"),Map.entry(20,"seasonpass"),Map.entry(21,"adventure"),Map.entry(22,"mastery"),Map.entry(23,"crates"),Map.entry(24,"shop"),Map.entry(25,"guild"),Map.entry(45,"menu"));
  String cmd=cmds.get(s);if(cmd!=null){p.closeInventory();Bukkit.dispatchCommand(p,cmd);}
 }
 private void save(){try{data.save(file);}catch(IOException ex){plugin.getLogger().severe("Could not save journal.yml: "+ex.getMessage());}}
}