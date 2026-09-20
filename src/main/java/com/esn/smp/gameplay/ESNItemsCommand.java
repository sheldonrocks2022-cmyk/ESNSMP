package com.esn.smp.gameplay;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.*;

public final class ESNItemsCommand implements CommandExecutor {
 private static final Map<String,java.util.function.Supplier<ItemStack>> ITEMS=new LinkedHashMap<>();
 static {
  ITEMS.put("key",LootDrops::key);
  ITEMS.put("titanblade",CustomItems::titanBlade); ITEMS.put("titancleaver",CustomItems::titanAxe); ITEMS.put("titandrill",CustomItems::minerDrill); ITEMS.put("titanhelmet",CustomItems::titanHelmet); ITEMS.put("titanchest",CustomItems::titanChestplate); ITEMS.put("titanlegs",CustomItems::titanLeggings); ITEMS.put("titanboots",CustomItems::titanBoots);
  ITEMS.put("voidblade",CustomItems::voidBlade); ITEMS.put("voidbreaker",CustomItems::voidPick); ITEMS.put("voidcrown",CustomItems::voidHelmet); ITEMS.put("voidchest",CustomItems::voidChestplate); ITEMS.put("voidlegs",CustomItems::voidLeggings); ITEMS.put("voidboots",CustomItems::voidBoots);
  ITEMS.put("infernal",CustomItems::infernalBlade); ITEMS.put("infernalaxe",CustomItems::infernalAxe); ITEMS.put("infernalpick",CustomItems::infernalPick); ITEMS.put("infernalhelmet",CustomItems::infernalHelmet); ITEMS.put("infernalchest",CustomItems::infernalChest); ITEMS.put("infernallegs",CustomItems::infernalLegs); ITEMS.put("infernalboots",CustomItems::infernalBoots);
  ITEMS.put("celestialblade",CustomItems::celestialBlade); ITEMS.put("celestialaxe",CustomItems::celestialAxe); ITEMS.put("celestialpick",CustomItems::celestialPick); ITEMS.put("celestialhelmet",CustomItems::celestialHelmet); ITEMS.put("celestialchest",CustomItems::celestialChest); ITEMS.put("celestiallegs",CustomItems::celestialLegs); ITEMS.put("celestialboots",CustomItems::celestialBoots); ITEMS.put("celestialbow",CustomItems::celestialBow);
  ITEMS.put("stormblade",CustomItems::stormBlade); ITEMS.put("stormaxe",CustomItems::stormAxe); ITEMS.put("stormpick",CustomItems::stormPick); ITEMS.put("stormhelmet",CustomItems::stormHelmet); ITEMS.put("stormchest",CustomItems::stormChest); ITEMS.put("stormlegs",CustomItems::stormLegs); ITEMS.put("stormboots",CustomItems::stormBoots);
  ITEMS.put("shadowblade",CustomItems::shadowBlade); ITEMS.put("shadowaxe",CustomItems::shadowAxe); ITEMS.put("shadowpick",CustomItems::shadowPick); ITEMS.put("shadowhelmet",CustomItems::shadowHelmet); ITEMS.put("shadowchest",CustomItems::shadowChest); ITEMS.put("shadowlegs",CustomItems::shadowLegs); ITEMS.put("shadowboots",CustomItems::shadowBoots);
  ITEMS.put("frostblade",CustomItems::frostBlade); ITEMS.put("frostaxe",CustomItems::frostAxe); ITEMS.put("frostpick",CustomItems::frostPick);
  ITEMS.put("dragonbow",CustomItems::dragonBow); ITEMS.put("phantomcrossbow",CustomItems::phantomCrossbow); ITEMS.put("poseidon",CustomItems::oceanTrident); ITEMS.put("reaperscythe",CustomItems::reaperScythe); ITEMS.put("emeraldhammer",CustomItems::emeraldHammer); ITEMS.put("treasurerod",CustomItems::treasureRod);
  ITEMS.put("solarblade",CustomItems::solarBlade); ITEMS.put("solaraxe",CustomItems::solarAxe); ITEMS.put("solarpick",CustomItems::solarPick); ITEMS.put("solarhelmet",CustomItems::solarHelmet); ITEMS.put("solarchest",CustomItems::solarChest); ITEMS.put("solarlegs",CustomItems::solarLegs); ITEMS.put("solarboots",CustomItems::solarBoots);
  ITEMS.put("bloodmoonblade",CustomItems::bloodBlade); ITEMS.put("bloodmoonaxe",CustomItems::bloodAxe); ITEMS.put("bloodmoonpick",CustomItems::bloodPick); ITEMS.put("bloodmoonhelmet",CustomItems::bloodHelmet); ITEMS.put("bloodmoonchest",CustomItems::bloodChest); ITEMS.put("bloodmoonlegs",CustomItems::bloodLegs); ITEMS.put("bloodmoonboots",CustomItems::bloodBoots);
  ITEMS.put("gaiablade",CustomItems::natureBlade); ITEMS.put("gaiaaxe",CustomItems::natureAxe); ITEMS.put("gaiapick",CustomItems::naturePick); ITEMS.put("gaiahelmet",CustomItems::natureHelmet); ITEMS.put("gaiachest",CustomItems::natureChest); ITEMS.put("gaiaboots",CustomItems::natureBoots);
  ITEMS.put("arcaneblade",CustomItems::arcaneBlade); ITEMS.put("arcanestaff",CustomItems::arcaneStaff); ITEMS.put("arcanepick",CustomItems::arcanePick); ITEMS.put("arcanehelmet",CustomItems::arcaneHelmet);
  ITEMS.put("lifecore",CustomItems::lifeApple);
 }
 public boolean onCommand(CommandSender s,Command c,String l,String[] a){
  if(!(s instanceof Player p)){s.sendMessage("Players only.");return true;}
  if(!p.hasPermission("esnsmp.items")){p.sendMessage(ChatColor.RED+"No permission.");return true;}
  if(a.length==0||a[0].equalsIgnoreCase("list")){
   p.sendMessage(ChatColor.GOLD+"ESN custom items ("+ITEMS.size()+"):");
   p.sendMessage(ChatColor.YELLOW+String.join(", ",ITEMS.keySet()));
   p.sendMessage(ChatColor.GRAY+"Use /esnitems <item>");
   return true;
  }
  var supplier=ITEMS.get(a[0].toLowerCase(Locale.ROOT));
  if(supplier==null){p.sendMessage(ChatColor.RED+"Unknown ESN item. Use /esnitems list");return true;}
  ItemStack i=supplier.get(); var left=p.getInventory().addItem(i);
  if(!left.isEmpty())left.values().forEach(x->p.getWorld().dropItemNaturally(p.getLocation(),x));
  p.sendMessage(ChatColor.GREEN+"Added "+(i.hasItemMeta()&&i.getItemMeta().hasDisplayName()?i.getItemMeta().getDisplayName():i.getType().name()));
  return true;
 }
}