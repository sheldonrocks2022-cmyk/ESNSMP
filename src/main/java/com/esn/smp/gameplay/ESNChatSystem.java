package com.esn.smp.gameplay;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("deprecation")
public final class ESNChatSystem implements Listener, CommandExecutor {
 private enum Channel { GLOBAL, LOCAL, STAFF }
 private final JavaPlugin plugin;
 private final Map<UUID,Channel> channels=new ConcurrentHashMap<>();
 private final Map<UUID,UUID> replies=new ConcurrentHashMap<>();
 private final Set<UUID> publicChatOff=ConcurrentHashMap.newKeySet();
 private final Map<UUID,Long> lastChat=new ConcurrentHashMap<>();
 public ESNChatSystem(JavaPlugin plugin){this.plugin=plugin;}

 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
 public void chat(AsyncPlayerChatEvent e){
  Player p=e.getPlayer();Channel channel=channels.getOrDefault(p.getUniqueId(),Channel.GLOBAL);
  long now=System.currentTimeMillis(),last=lastChat.getOrDefault(p.getUniqueId(),0L);
  if(!p.hasPermission("esnsmp.staff")&&now-last<750){e.setCancelled(true);p.sendMessage(ChatColor.RED+"You're chatting too quickly.");return;}
  lastChat.put(p.getUniqueId(),now);e.setCancelled(true);
  String message=safeMessage(p,e.getMessage());
  Bukkit.getScheduler().runTask(plugin,()->deliver(p,channel,message));
 }
 private void deliver(Player p,Channel channel,String message){
  String prefix=rank(p),name=prefix+p.getName()+ChatColor.GRAY+": "+ChatColor.WHITE;
  Collection<? extends Player> targets;
  if(channel==Channel.STAFF){
   if(!p.hasPermission("esnsmp.staff")){channels.put(p.getUniqueId(),Channel.GLOBAL);p.sendMessage(ChatColor.RED+"Staff chat is unavailable.");return;}
   targets=Bukkit.getOnlinePlayers().stream().filter(x->x.hasPermission("esnsmp.staff")).toList();
   name=ChatColor.DARK_AQUA+"[STAFF] "+name;
  }else if(channel==Channel.LOCAL){
   targets=p.getWorld().getPlayers().stream().filter(x->x.getLocation().distanceSquared(p.getLocation())<=10000).toList();
   name=ChatColor.YELLOW+"[LOCAL] "+name;
  }else{
   targets=Bukkit.getOnlinePlayers();name=ChatColor.AQUA+"[GLOBAL] "+name;
  }
  for(Player x:targets)if(!publicChatOff.contains(x.getUniqueId())||x.equals(p)){x.sendMessage(name+message);mention(x,p,message);}
  Bukkit.getConsoleSender().sendMessage(ChatColor.stripColor(name+message));
 }
 private void mention(Player receiver,Player sender,String message){
  if(receiver.equals(sender)||!receiver.hasPermission("esnsmp.chat.mention"))return;
  if(message.toLowerCase(Locale.ROOT).contains(receiver.getName().toLowerCase(Locale.ROOT))){
   receiver.playSound(receiver.getLocation(),Sound.BLOCK_NOTE_BLOCK_PLING,.8f,1.5f);
   receiver.sendActionBar(ChatColor.GOLD+sender.getName()+" mentioned you in chat");
  }
 }
 private String rank(Player p){
  if(p.hasPermission("esnsmp.owner"))return ChatColor.DARK_RED+"[OWNER] "+ChatColor.WHITE;
  if(p.hasPermission("esnsmp.staff.admin"))return ChatColor.RED+"[ADMIN] "+ChatColor.WHITE;
  if(p.hasPermission("esnsmp.staff.seniormod"))return ChatColor.DARK_PURPLE+"[SR MOD] "+ChatColor.WHITE;
  if(p.hasPermission("esnsmp.staff.moderator"))return ChatColor.LIGHT_PURPLE+"[MOD] "+ChatColor.WHITE;
  if(p.hasPermission("esnsmp.staff.trialmod"))return ChatColor.BLUE+"[TRIAL MOD] "+ChatColor.WHITE;
  if(p.hasPermission("esnsmp.staff.helper"))return ChatColor.GREEN+"[HELPER] "+ChatColor.WHITE;
  return ChatColor.GRAY+"[MEMBER] "+ChatColor.WHITE;
 }
 private String safeMessage(Player p,String s){
  if(!p.hasPermission("esnsmp.chat.color"))return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&',s));
  return ChatColor.translateAlternateColorCodes('&',s);
 }
 @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
  if(!(sender instanceof Player p)){sender.sendMessage("Players only.");return true;}
  String c=command.getName().toLowerCase(Locale.ROOT);
  if(c.equals("globalchat")){channels.put(p.getUniqueId(),Channel.GLOBAL);p.sendMessage(ChatColor.AQUA+"Chat channel: GLOBAL");return true;}
  if(c.equals("localchat")){channels.put(p.getUniqueId(),Channel.LOCAL);p.sendMessage(ChatColor.YELLOW+"Chat channel: LOCAL (100 blocks)");return true;}
  if(c.equals("chattoggle")){if(!publicChatOff.add(p.getUniqueId()))publicChatOff.remove(p.getUniqueId());p.sendMessage(ChatColor.GRAY+"Public chat: "+(publicChatOff.contains(p.getUniqueId())?ChatColor.RED+"OFF":ChatColor.GREEN+"ON"));return true;}
  if(c.equals("staffchat")){
   if(!p.hasPermission("esnsmp.staff")){p.sendMessage(ChatColor.RED+"No permission.");return true;}
   if(args.length==0){Channel next=channels.getOrDefault(p.getUniqueId(),Channel.GLOBAL)==Channel.STAFF?Channel.GLOBAL:Channel.STAFF;channels.put(p.getUniqueId(),next);p.sendMessage(ChatColor.DARK_AQUA+"Staff chat "+(next==Channel.STAFF?"enabled":"disabled"));return true;}
   deliver(p,Channel.STAFF,String.join(" ",args));return true;
  }
  if(c.equals("msg")){
   if(args.length<2){p.sendMessage(ChatColor.YELLOW+"/msg <player> <message>");return true;}
   Player target=Bukkit.getPlayer(args[0]);if(target==null){p.sendMessage(ChatColor.RED+"That player is not online.");return true;}
   privateMessage(p,target,String.join(" ",Arrays.copyOfRange(args,1,args.length)));return true;
  }
  if(c.equals("reply")){
   if(args.length==0){p.sendMessage(ChatColor.YELLOW+"/reply <message>");return true;}
   UUID id=replies.get(p.getUniqueId());Player target=id==null?null:Bukkit.getPlayer(id);if(target==null){p.sendMessage(ChatColor.RED+"Nobody online to reply to.");return true;}
   privateMessage(p,target,String.join(" ",args));return true;
  }
  return true;
 }
 private void privateMessage(Player from,Player to,String message){
  String m=safeMessage(from,message);
  from.sendMessage(ChatColor.LIGHT_PURPLE+"[YOU → "+to.getName()+"] "+ChatColor.WHITE+m);
  to.sendMessage(ChatColor.LIGHT_PURPLE+"["+from.getName()+" → YOU] "+ChatColor.WHITE+m);
  replies.put(from.getUniqueId(),to.getUniqueId());replies.put(to.getUniqueId(),from.getUniqueId());
  to.playSound(to.getLocation(),Sound.BLOCK_NOTE_BLOCK_CHIME,.7f,1.3f);
  plugin.getLogger().info("[PM] "+from.getName()+" -> "+to.getName()+": "+ChatColor.stripColor(m));
 }
}