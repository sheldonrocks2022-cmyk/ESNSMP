package com.esn.smp.gameplay;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("deprecation")
public final class ESNChatSystem implements Listener, CommandExecutor {
 private enum Channel { GLOBAL, LOCAL, STAFF }
 private static final String MENU=ChatColor.DARK_AQUA+"ESN Chat Menu";
 private final JavaPlugin plugin; private final V18Core progression; private final ESN20Expansion expansion;
 private final Map<UUID,Channel> channels=new ConcurrentHashMap<>(),dummy=new ConcurrentHashMap<>();
 private final Map<UUID,UUID> replies=new ConcurrentHashMap<>();
 private final Set<UUID> publicChatOff=ConcurrentHashMap.newKeySet(),mentionOff=ConcurrentHashMap.newKeySet(),pmOff=ConcurrentHashMap.newKeySet(),soundOff=ConcurrentHashMap.newKeySet();
 private final Map<UUID,Long> lastChat=new ConcurrentHashMap<>();
 public ESNChatSystem(JavaPlugin plugin,V18Core progression,ESN20Expansion expansion){this.plugin=plugin;this.progression=progression;this.expansion=expansion;}

 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void chat(AsyncPlayerChatEvent e){
  Player p=e.getPlayer();Channel ch=channels.getOrDefault(p.getUniqueId(),Channel.GLOBAL);long now=System.currentTimeMillis(),last=lastChat.getOrDefault(p.getUniqueId(),0L);
  if(!p.hasPermission("esnsmp.staff")&&now-last<750){e.setCancelled(true);p.sendMessage(ChatColor.RED+"You're chatting too quickly.");return;}
  lastChat.put(p.getUniqueId(),now);e.setCancelled(true);String message=safeMessage(p,e.getMessage());Bukkit.getScheduler().runTask(plugin,()->deliver(p,ch,message));
 }
 private void deliver(Player p,Channel ch,String message){
  String name=identity(p)+ChatColor.GRAY+": "+ChatColor.WHITE;Collection<? extends Player> targets;
  if(ch==Channel.STAFF){if(!p.hasPermission("esnsmp.staff")){channels.put(p.getUniqueId(),Channel.GLOBAL);p.sendMessage(ChatColor.RED+"Staff chat is unavailable.");return;}targets=Bukkit.getOnlinePlayers().stream().filter(x->x.hasPermission("esnsmp.staff")).toList();name=ChatColor.DARK_AQUA+"[STAFF] "+name;}
  else if(ch==Channel.LOCAL){targets=p.getWorld().getPlayers().stream().filter(x->x.getLocation().distanceSquared(p.getLocation())<=10000).toList();name=ChatColor.YELLOW+"[LOCAL] "+name;}
  else{targets=Bukkit.getOnlinePlayers();name=ChatColor.AQUA+"[GLOBAL] "+name;}
  for(Player x:targets)if(!publicChatOff.contains(x.getUniqueId())||x.equals(p)){x.sendMessage(name+message);mention(x,p,message);}
  Bukkit.getConsoleSender().sendMessage(ChatColor.stripColor(name+message));
 }
 private String identity(Player p){int lv=progression==null?1:progression.chatLevel(p);String title=expansion==null?"":expansion.equippedTitle(p);String champ=ServerChampionship.champion(plugin,p.getUniqueId())?ChatColor.GOLD+"[CHAMPION] ":"";return rank(p)+champ+ChatColor.GOLD+"[Lv. "+lv+"] "+(title==null||title.isBlank()?"":ChatColor.LIGHT_PURPLE+"["+title+"] ")+ChatColor.WHITE+p.getName();}
 private void mention(Player r,Player s,String m){if(r.equals(s)||mentionOff.contains(r.getUniqueId())||!r.hasPermission("esnsmp.chat.mention"))return;if(m.toLowerCase(Locale.ROOT).contains(r.getName().toLowerCase(Locale.ROOT))){if(!soundOff.contains(r.getUniqueId()))r.playSound(r.getLocation(),Sound.BLOCK_NOTE_BLOCK_PLING,.8f,1.5f);r.sendActionBar(ChatColor.GOLD+s.getName()+" mentioned you in chat");}}
 private String rank(Player p){if(p.hasPermission("esnsmp.owner"))return ChatColor.DARK_RED+"[OWNER] ";if(p.hasPermission("esnsmp.staff.admin"))return ChatColor.RED+"[ADMIN] ";if(p.hasPermission("esnsmp.staff.seniormod"))return ChatColor.DARK_PURPLE+"[SR MOD] ";if(p.hasPermission("esnsmp.staff.moderator"))return ChatColor.LIGHT_PURPLE+"[MOD] ";if(p.hasPermission("esnsmp.staff.trialmod"))return ChatColor.BLUE+"[TRIAL MOD] ";if(p.hasPermission("esnsmp.staff.helper"))return ChatColor.GREEN+"[HELPER] ";return ChatColor.GRAY+"[MEMBER] ";}
 private String safeMessage(Player p,String s){return p.hasPermission("esnsmp.chat.color")?ChatColor.translateAlternateColorCodes('&',s):ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&',s));}
 private ItemStack item(Material m,String n,String... lore){ItemStack i=new ItemStack(m);ItemMeta im=i.getItemMeta();im.setDisplayName(n);im.setLore(Arrays.asList(lore));i.setItemMeta(im);return i;}
 private String state(boolean on){return on?ChatColor.GREEN+"ON":ChatColor.RED+"OFF";}
 public void open(Player p){Inventory v=Bukkit.createInventory(null,27,MENU);UUID u=p.getUniqueId();Channel ch=channels.getOrDefault(u,Channel.GLOBAL);v.setItem(10,item(Material.ENDER_PEARL,ChatColor.AQUA+"Global Chat",ChatColor.GRAY+"Current: "+state(ch==Channel.GLOBAL),ChatColor.YELLOW+"Click to use Global"));v.setItem(11,item(Material.COMPASS,ChatColor.YELLOW+"Local Chat",ChatColor.GRAY+"100 block radius",ChatColor.GRAY+"Current: "+state(ch==Channel.LOCAL),ChatColor.YELLOW+"Click to use Local"));if(p.hasPermission("esnsmp.staff"))v.setItem(12,item(Material.REDSTONE_TORCH,ChatColor.DARK_AQUA+"Staff Chat",ChatColor.GRAY+"Current: "+state(ch==Channel.STAFF),ChatColor.YELLOW+"Click to use Staff"));v.setItem(14,item(Material.PAPER,ChatColor.WHITE+"Public Chat",ChatColor.GRAY+"Receiving: "+state(!publicChatOff.contains(u)),ChatColor.YELLOW+"Click to toggle"));v.setItem(15,item(Material.NAME_TAG,ChatColor.GOLD+"Mentions",ChatColor.GRAY+"Alerts: "+state(!mentionOff.contains(u)),ChatColor.YELLOW+"Click to toggle"));v.setItem(16,item(Material.WRITABLE_BOOK,ChatColor.LIGHT_PURPLE+"Private Messages",ChatColor.GRAY+"Receiving: "+state(!pmOff.contains(u)),ChatColor.YELLOW+"Click to toggle"));v.setItem(22,item(Material.NOTE_BLOCK,ChatColor.GREEN+"Chat Sounds",ChatColor.GRAY+"Sounds: "+state(!soundOff.contains(u)),ChatColor.YELLOW+"Click to toggle"));p.openInventory(v);}
 @EventHandler public void menu(InventoryClickEvent e){if(!e.getView().getTitle().equals(MENU))return;e.setCancelled(true);if(!(e.getWhoClicked() instanceof Player p))return;UUID u=p.getUniqueId();switch(e.getRawSlot()){case 10->channels.put(u,Channel.GLOBAL);case 11->channels.put(u,Channel.LOCAL);case 12->{if(p.hasPermission("esnsmp.staff"))channels.put(u,Channel.STAFF);}case 14->toggle(publicChatOff,u);case 15->toggle(mentionOff,u);case 16->toggle(pmOff,u);case 22->toggle(soundOff,u);default->{return;}}p.playSound(p.getLocation(),Sound.UI_BUTTON_CLICK,.6f,1.2f);open(p);}
 private void toggle(Set<UUID>s,UUID u){if(!s.add(u))s.remove(u);}
 @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){if(!(sender instanceof Player p)){sender.sendMessage("Players only.");return true;}String c=command.getName().toLowerCase(Locale.ROOT);
  if(c.equals("chatmenu")){open(p);return true;}if(c.equals("globalchat")){channels.put(p.getUniqueId(),Channel.GLOBAL);p.sendMessage(ChatColor.AQUA+"Chat channel: GLOBAL");return true;}if(c.equals("localchat")){channels.put(p.getUniqueId(),Channel.LOCAL);p.sendMessage(ChatColor.YELLOW+"Chat channel: LOCAL (100 blocks)");return true;}
  if(c.equals("chattoggle")){toggle(publicChatOff,p.getUniqueId());p.sendMessage(ChatColor.GRAY+"Public chat: "+state(!publicChatOff.contains(p.getUniqueId())));return true;}
  if(c.equals("staffchat")){if(!p.hasPermission("esnsmp.staff")){p.sendMessage(ChatColor.RED+"No permission.");return true;}if(args.length==0){Channel n=channels.getOrDefault(p.getUniqueId(),Channel.GLOBAL)==Channel.STAFF?Channel.GLOBAL:Channel.STAFF;channels.put(p.getUniqueId(),n);p.sendMessage(ChatColor.DARK_AQUA+"Staff chat "+(n==Channel.STAFF?"enabled":"disabled"));return true;}deliver(p,Channel.STAFF,String.join(" ",args));return true;}
  if(c.equals("msg")){if(args.length<2){p.sendMessage(ChatColor.YELLOW+"/msg <player> <message>");return true;}Player t=Bukkit.getPlayer(args[0]);if(t==null){p.sendMessage(ChatColor.RED+"That player is not online.");return true;}privateMessage(p,t,String.join(" ",Arrays.copyOfRange(args,1,args.length)));return true;}
  if(c.equals("reply")){if(args.length==0){p.sendMessage(ChatColor.YELLOW+"/reply <message>");return true;}UUID id=replies.get(p.getUniqueId());Player t=id==null?null:Bukkit.getPlayer(id);if(t==null){p.sendMessage(ChatColor.RED+"Nobody online to reply to.");return true;}privateMessage(p,t,String.join(" ",args));return true;}return true;}
 private void privateMessage(Player from,Player to,String message){if(pmOff.contains(to.getUniqueId())&&!from.hasPermission("esnsmp.staff")){from.sendMessage(ChatColor.RED+to.getName()+" is not accepting private messages.");return;}String m=safeMessage(from,message);from.sendMessage(ChatColor.LIGHT_PURPLE+"[YOU → "+to.getName()+"] "+ChatColor.WHITE+m);to.sendMessage(ChatColor.LIGHT_PURPLE+"["+from.getName()+" → YOU] "+ChatColor.WHITE+m);replies.put(from.getUniqueId(),to.getUniqueId());replies.put(to.getUniqueId(),from.getUniqueId());if(!soundOff.contains(to.getUniqueId()))to.playSound(to.getLocation(),Sound.BLOCK_NOTE_BLOCK_CHIME,.7f,1.3f);plugin.getLogger().info("[PM] "+from.getName()+" -> "+to.getName()+": "+ChatColor.stripColor(m));}
}
