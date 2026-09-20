package com.esn.smp.gameplay;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.*;
public final class CustomItems implements Listener {
 public static ItemStack spawnCompass(Location spawn){ItemStack i=new ItemStack(Material.COMPASS);ItemMeta m=i.getItemMeta();m.setDisplayName(ChatColor.GOLD+"ESN World Spawn");m.setLore(List.of(ChatColor.GRAY+"Points toward the ESN world spawn"));i.setItemMeta(m);return i;}
 public static ItemStack titanBlade(){ItemStack i=new ItemStack(Material.NETHERITE_SWORD);ItemMeta m=i.getItemMeta();m.setDisplayName(ChatColor.DARK_RED+"Titan Blade");m.setLore(List.of(ChatColor.GRAY+"An ESN legendary weapon"));m.addEnchant(Enchantment.SHARPNESS,7,true);m.addEnchant(Enchantment.UNBREAKING,5,true);m.addEnchant(Enchantment.LOOTING,4,true);i.setItemMeta(m);return i;}
 public static ItemStack voidPick(){ItemStack i=new ItemStack(Material.NETHERITE_PICKAXE);ItemMeta m=i.getItemMeta();m.setDisplayName(ChatColor.DARK_PURPLE+"Voidbreaker");m.setLore(List.of(ChatColor.GRAY+"Forged beyond vanilla limits"));m.addEnchant(Enchantment.EFFICIENCY,7,true);m.addEnchant(Enchantment.UNBREAKING,6,true);m.addEnchant(Enchantment.FORTUNE,5,true);i.setItemMeta(m);return i;}
}
