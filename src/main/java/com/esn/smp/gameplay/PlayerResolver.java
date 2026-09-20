package com.esn.smp.gameplay;
import org.bukkit.Bukkit;import org.bukkit.OfflinePlayer;import org.bukkit.entity.Player;
public final class PlayerResolver{
 private PlayerResolver(){}
 public static Player online(String input){
  if(input==null||input.isBlank())return null;
  Player p=Bukkit.getPlayerExact(input);if(p!=null)return p;
  String alt=input.startsWith(".")?input.substring(1):"."+input;
  p=Bukkit.getPlayerExact(alt);if(p!=null)return p;
  for(Player x:Bukkit.getOnlinePlayers())if(x.getName().equalsIgnoreCase(input)||x.getName().equalsIgnoreCase(alt))return x;
  return null;
 }
 public static OfflinePlayer offline(String input){
  Player p=online(input);if(p!=null)return p;
  OfflinePlayer a=Bukkit.getOfflinePlayerIfCached(input);if(a!=null)return a;
  String alt=input.startsWith(".")?input.substring(1):"."+input;
  OfflinePlayer b=Bukkit.getOfflinePlayerIfCached(alt);return b!=null?b:Bukkit.getOfflinePlayer(input);
 }
}