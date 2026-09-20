package com.esn.smp.gameplay;
import org.bukkit.*;import org.bukkit.enchantments.Enchantment;import org.bukkit.inventory.*;import org.bukkit.inventory.meta.ItemMeta;import java.util.*;
public final class CustomItems{
 private static ItemStack gear(Material mat,String name,ChatColor color,int prot){ItemStack i=new ItemStack(mat);ItemMeta m=i.getItemMeta();m.setDisplayName(color+name);m.setLore(List.of(ChatColor.GRAY+"ESN legendary equipment"));if(prot>0)m.addEnchant(Enchantment.PROTECTION,prot,true);m.addEnchant(Enchantment.UNBREAKING,10,true);m.addEnchant(Enchantment.MENDING,1,true);i.setItemMeta(m);return i;}
 private static ItemStack weapon(Material mat,String name,ChatColor color,int sharp){ItemStack i=gear(mat,name,color,0);ItemMeta m=i.getItemMeta();m.addEnchant(Enchantment.SHARPNESS,sharp,true);m.addEnchant(Enchantment.LOOTING,5,true);i.setItemMeta(m);return i;}
 public static ItemStack spawnCompass(Location s){ItemStack i=new ItemStack(Material.COMPASS);ItemMeta m=i.getItemMeta();m.setDisplayName(ChatColor.GOLD+"ESN World Spawn");m.setLore(List.of(ChatColor.GRAY+"Points toward the ESN world spawn"));i.setItemMeta(m);return i;}
 public static ItemStack titanBlade(){return weapon(Material.NETHERITE_SWORD,"Titan Blade",ChatColor.DARK_RED,20);}
 public static ItemStack voidBlade(){return weapon(Material.NETHERITE_SWORD,"Void Blade",ChatColor.DARK_PURPLE,18);}
 public static ItemStack titanAxe(){return weapon(Material.NETHERITE_AXE,"Titan Cleaver",ChatColor.DARK_RED,15);}
 public static ItemStack voidPick(){ItemStack i=gear(Material.NETHERITE_PICKAXE,"Voidbreaker",ChatColor.DARK_PURPLE,0);ItemMeta m=i.getItemMeta();m.addEnchant(Enchantment.EFFICIENCY,10,true);m.addEnchant(Enchantment.FORTUNE,5,true);i.setItemMeta(m);return i;}
 public static ItemStack titanHelmet(){return gear(Material.NETHERITE_HELMET,"Titan Helmet",ChatColor.DARK_RED,40);}public static ItemStack titanChestplate(){return gear(Material.NETHERITE_CHESTPLATE,"Titan Chestplate",ChatColor.DARK_RED,40);}public static ItemStack titanLeggings(){return gear(Material.NETHERITE_LEGGINGS,"Titan Leggings",ChatColor.DARK_RED,40);}public static ItemStack titanBoots(){return gear(Material.NETHERITE_BOOTS,"Titan Boots",ChatColor.DARK_RED,40);}
 public static ItemStack voidHelmet(){return gear(Material.NETHERITE_HELMET,"Void Crown",ChatColor.DARK_PURPLE,30);}public static ItemStack voidChestplate(){return gear(Material.NETHERITE_CHESTPLATE,"Void Chestplate",ChatColor.DARK_PURPLE,30);}public static ItemStack voidLeggings(){return gear(Material.NETHERITE_LEGGINGS,"Void Leggings",ChatColor.DARK_PURPLE,30);}public static ItemStack voidBoots(){return gear(Material.NETHERITE_BOOTS,"Void Boots",ChatColor.DARK_PURPLE,30);}
 public static ItemStack infernalBlade(){ItemStack i=weapon(Material.NETHERITE_SWORD,"Infernal Fang",ChatColor.RED,14);ItemMeta m=i.getItemMeta();m.addEnchant(Enchantment.FIRE_ASPECT,5,true);i.setItemMeta(m);return i;}
 public static ItemStack celestialBow(){ItemStack i=gear(Material.BOW,"Celestial Bow",ChatColor.AQUA,0);ItemMeta m=i.getItemMeta();m.addEnchant(Enchantment.POWER,12,true);m.addEnchant(Enchantment.INFINITY,1,true);m.addEnchant(Enchantment.FLAME,3,true);i.setItemMeta(m);return i;}
 public static ItemStack minerDrill(){ItemStack i=gear(Material.NETHERITE_PICKAXE,"Titan Drill",ChatColor.GOLD,0);ItemMeta m=i.getItemMeta();m.addEnchant(Enchantment.EFFICIENCY,12,true);m.addEnchant(Enchantment.FORTUNE,7,true);i.setItemMeta(m);return i;}
 public static ItemStack lifeApple(){ItemStack i=new ItemStack(Material.ENCHANTED_GOLDEN_APPLE);ItemMeta m=i.getItemMeta();m.setDisplayName(ChatColor.LIGHT_PURPLE+"Life Core");m.setLore(List.of(ChatColor.GRAY+"Extremely rare ESN relic"));i.setItemMeta(m);return i;}
 public static ItemStack celestialBlade(){ItemStack i=gear(Material.NETHERITE_SWORD,"Celestial Saber",ChatColor.AQUA,0);ItemMeta x=i.getItemMeta();x.addEnchant(Enchantment.SHARPNESS,16,true);i.setItemMeta(x);return i;}
 public static ItemStack celestialAxe(){ItemStack i=gear(Material.NETHERITE_AXE,"Celestial Reaver",ChatColor.AQUA,0);ItemMeta x=i.getItemMeta();x.addEnchant(Enchantment.SHARPNESS,14,true);i.setItemMeta(x);return i;}
 public static ItemStack celestialPick(){ItemStack i=gear(Material.NETHERITE_PICKAXE,"Celestial Excavator",ChatColor.AQUA,0);ItemMeta x=i.getItemMeta();x.addEnchant(Enchantment.EFFICIENCY,11,true);i.setItemMeta(x);return i;}
 public static ItemStack celestialHelmet(){ItemStack i=gear(Material.NETHERITE_HELMET,"Celestial Halo",ChatColor.AQUA,28);return i;}
 public static ItemStack celestialChest(){ItemStack i=gear(Material.NETHERITE_CHESTPLATE,"Celestial Aegis",ChatColor.AQUA,28);return i;}
 public static ItemStack celestialLegs(){ItemStack i=gear(Material.NETHERITE_LEGGINGS,"Celestial Greaves",ChatColor.AQUA,28);return i;}
 public static ItemStack celestialBoots(){ItemStack i=gear(Material.NETHERITE_BOOTS,"Celestial Walkers",ChatColor.AQUA,28);return i;}
 public static ItemStack infernalAxe(){ItemStack i=gear(Material.NETHERITE_AXE,"Infernal Crusher",ChatColor.RED,0);ItemMeta x=i.getItemMeta();x.addEnchant(Enchantment.SHARPNESS,13,true);i.setItemMeta(x);return i;}
 public static ItemStack infernalPick(){ItemStack i=gear(Material.NETHERITE_PICKAXE,"Infernal Bore",ChatColor.RED,0);ItemMeta x=i.getItemMeta();x.addEnchant(Enchantment.EFFICIENCY,10,true);i.setItemMeta(x);return i;}
 public static ItemStack infernalHelmet(){ItemStack i=gear(Material.NETHERITE_HELMET,"Infernal Helm",ChatColor.RED,25);return i;}
 public static ItemStack infernalChest(){ItemStack i=gear(Material.NETHERITE_CHESTPLATE,"Infernal Plate",ChatColor.RED,25);return i;}
 public static ItemStack infernalLegs(){ItemStack i=gear(Material.NETHERITE_LEGGINGS,"Infernal Legguards",ChatColor.RED,25);return i;}
 public static ItemStack infernalBoots(){ItemStack i=gear(Material.NETHERITE_BOOTS,"Infernal Treads",ChatColor.RED,25);return i;}
 public static ItemStack stormBlade(){ItemStack i=gear(Material.NETHERITE_SWORD,"Stormcaller",ChatColor.BLUE,0);ItemMeta x=i.getItemMeta();x.addEnchant(Enchantment.SHARPNESS,13,true);i.setItemMeta(x);return i;}
 public static ItemStack stormAxe(){ItemStack i=gear(Material.NETHERITE_AXE,"Thunder Splitter",ChatColor.BLUE,0);ItemMeta x=i.getItemMeta();x.addEnchant(Enchantment.SHARPNESS,12,true);i.setItemMeta(x);return i;}
 public static ItemStack stormPick(){ItemStack i=gear(Material.NETHERITE_PICKAXE,"Storm Drill",ChatColor.BLUE,0);ItemMeta x=i.getItemMeta();x.addEnchant(Enchantment.EFFICIENCY,10,true);i.setItemMeta(x);return i;}
 public static ItemStack stormHelmet(){ItemStack i=gear(Material.DIAMOND_HELMET,"Storm Crown",ChatColor.BLUE,20);return i;}
 public static ItemStack stormChest(){ItemStack i=gear(Material.DIAMOND_CHESTPLATE,"Storm Guard",ChatColor.BLUE,20);return i;}
 public static ItemStack stormLegs(){ItemStack i=gear(Material.DIAMOND_LEGGINGS,"Storm Leggings",ChatColor.BLUE,20);return i;}
 public static ItemStack stormBoots(){ItemStack i=gear(Material.DIAMOND_BOOTS,"Storm Striders",ChatColor.BLUE,20);return i;}
 public static ItemStack shadowBlade(){ItemStack i=gear(Material.NETHERITE_SWORD,"Shadowfang",ChatColor.DARK_GRAY,0);ItemMeta x=i.getItemMeta();x.addEnchant(Enchantment.SHARPNESS,15,true);i.setItemMeta(x);return i;}
 public static ItemStack shadowAxe(){ItemStack i=gear(Material.NETHERITE_AXE,"Night Cleaver",ChatColor.DARK_GRAY,0);ItemMeta x=i.getItemMeta();x.addEnchant(Enchantment.SHARPNESS,13,true);i.setItemMeta(x);return i;}
 public static ItemStack shadowPick(){ItemStack i=gear(Material.NETHERITE_PICKAXE,"Abyss Miner",ChatColor.DARK_GRAY,0);ItemMeta x=i.getItemMeta();x.addEnchant(Enchantment.EFFICIENCY,11,true);i.setItemMeta(x);return i;}
 public static ItemStack shadowHelmet(){ItemStack i=gear(Material.NETHERITE_HELMET,"Shadow Hood",ChatColor.DARK_GRAY,26);return i;}
 public static ItemStack shadowChest(){ItemStack i=gear(Material.NETHERITE_CHESTPLATE,"Shadow Carapace",ChatColor.DARK_GRAY,26);return i;}
 public static ItemStack shadowLegs(){ItemStack i=gear(Material.NETHERITE_LEGGINGS,"Shadow Legguards",ChatColor.DARK_GRAY,26);return i;}
 public static ItemStack shadowBoots(){ItemStack i=gear(Material.NETHERITE_BOOTS,"Shadow Steps",ChatColor.DARK_GRAY,26);return i;}
 public static ItemStack frostBlade(){ItemStack i=gear(Material.DIAMOND_SWORD,"Frostbite",ChatColor.AQUA,0);ItemMeta x=i.getItemMeta();x.addEnchant(Enchantment.SHARPNESS,12,true);i.setItemMeta(x);return i;}
 public static ItemStack frostAxe(){ItemStack i=gear(Material.DIAMOND_AXE,"Glacier Axe",ChatColor.AQUA,0);ItemMeta x=i.getItemMeta();x.addEnchant(Enchantment.SHARPNESS,11,true);i.setItemMeta(x);return i;}
 public static ItemStack frostPick(){ItemStack i=gear(Material.DIAMOND_PICKAXE,"Permafrost Pick",ChatColor.AQUA,0);ItemMeta x=i.getItemMeta();x.addEnchant(Enchantment.EFFICIENCY,9,true);i.setItemMeta(x);return i;}
 public static ItemStack dragonBow(){ItemStack i=gear(Material.BOW,"Dragonspine Bow",ChatColor.DARK_GREEN,0);ItemMeta m=i.getItemMeta();m.addEnchant(Enchantment.POWER,10,true);m.addEnchant(Enchantment.PUNCH,4,true);m.addEnchant(Enchantment.FLAME,2,true);i.setItemMeta(m);return i;}
 public static ItemStack phantomCrossbow(){ItemStack i=gear(Material.CROSSBOW,"Phantom Repeater",ChatColor.GRAY,0);ItemMeta m=i.getItemMeta();m.addEnchant(Enchantment.QUICK_CHARGE,5,true);m.addEnchant(Enchantment.MULTISHOT,1,true);i.setItemMeta(m);return i;}
 public static ItemStack oceanTrident(){ItemStack i=gear(Material.TRIDENT,"Poseidon's Wrath",ChatColor.BLUE,0);ItemMeta m=i.getItemMeta();m.addEnchant(Enchantment.IMPALING,10,true);m.addEnchant(Enchantment.LOYALTY,5,true);i.setItemMeta(m);return i;}
 public static ItemStack reaperScythe(){return weapon(Material.NETHERITE_HOE,"Reaper's Scythe",ChatColor.DARK_PURPLE,17);}
 public static ItemStack emeraldHammer(){return weapon(Material.NETHERITE_AXE,"Emerald Warhammer",ChatColor.GREEN,16);}
 public static ItemStack treasureRod(){ItemStack i=gear(Material.FISHING_ROD,"Treasure Seeker",ChatColor.GOLD,0);ItemMeta m=i.getItemMeta();m.addEnchant(Enchantment.LUCK_OF_THE_SEA,8,true);m.addEnchant(Enchantment.LURE,5,true);i.setItemMeta(m);return i;}
 public static ItemStack solarBlade(){ItemStack i=gear(Material.NETHERITE_SWORD,"Solar Edge",ChatColor.GOLD,0);ItemMeta x=i.getItemMeta();x.addEnchant(Enchantment.SHARPNESS,15,true);i.setItemMeta(x);return i;}
 public static ItemStack solarAxe(){ItemStack i=gear(Material.NETHERITE_AXE,"Sunbreaker",ChatColor.GOLD,0);ItemMeta x=i.getItemMeta();x.addEnchant(Enchantment.SHARPNESS,14,true);i.setItemMeta(x);return i;}
 public static ItemStack solarPick(){ItemStack i=gear(Material.NETHERITE_PICKAXE,"Sun Drill",ChatColor.GOLD,0);ItemMeta x=i.getItemMeta();x.addEnchant(Enchantment.EFFICIENCY,11,true);i.setItemMeta(x);return i;}
 public static ItemStack solarHelmet(){ItemStack i=gear(Material.NETHERITE_HELMET,"Solar Crown",ChatColor.GOLD,27);return i;}
 public static ItemStack solarChest(){ItemStack i=gear(Material.NETHERITE_CHESTPLATE,"Solar Plate",ChatColor.GOLD,27);return i;}
 public static ItemStack solarLegs(){ItemStack i=gear(Material.NETHERITE_LEGGINGS,"Solar Greaves",ChatColor.GOLD,27);return i;}
 public static ItemStack solarBoots(){ItemStack i=gear(Material.NETHERITE_BOOTS,"Solar Steps",ChatColor.GOLD,27);return i;}
 public static ItemStack bloodBlade(){ItemStack i=gear(Material.NETHERITE_SWORD,"Bloodmoon Blade",ChatColor.DARK_RED,0);ItemMeta x=i.getItemMeta();x.addEnchant(Enchantment.SHARPNESS,17,true);i.setItemMeta(x);return i;}
 public static ItemStack bloodAxe(){ItemStack i=gear(Material.NETHERITE_AXE,"Bloodmoon Cleaver",ChatColor.DARK_RED,0);ItemMeta x=i.getItemMeta();x.addEnchant(Enchantment.SHARPNESS,15,true);i.setItemMeta(x);return i;}
 public static ItemStack bloodPick(){ItemStack i=gear(Material.NETHERITE_PICKAXE,"Bloodstone Drill",ChatColor.DARK_RED,0);ItemMeta x=i.getItemMeta();x.addEnchant(Enchantment.EFFICIENCY,12,true);i.setItemMeta(x);return i;}
 public static ItemStack bloodHelmet(){ItemStack i=gear(Material.NETHERITE_HELMET,"Bloodmoon Helm",ChatColor.DARK_RED,32);return i;}
 public static ItemStack bloodChest(){ItemStack i=gear(Material.NETHERITE_CHESTPLATE,"Bloodmoon Plate",ChatColor.DARK_RED,32);return i;}
 public static ItemStack bloodLegs(){ItemStack i=gear(Material.NETHERITE_LEGGINGS,"Bloodmoon Guards",ChatColor.DARK_RED,32);return i;}
 public static ItemStack bloodBoots(){ItemStack i=gear(Material.NETHERITE_BOOTS,"Bloodmoon Walkers",ChatColor.DARK_RED,32);return i;}
 public static ItemStack natureBlade(){ItemStack i=gear(Material.DIAMOND_SWORD,"Gaia Blade",ChatColor.GREEN,0);ItemMeta x=i.getItemMeta();x.addEnchant(Enchantment.SHARPNESS,12,true);i.setItemMeta(x);return i;}
 public static ItemStack natureAxe(){ItemStack i=gear(Material.DIAMOND_AXE,"Worldroot Axe",ChatColor.GREEN,0);ItemMeta x=i.getItemMeta();x.addEnchant(Enchantment.SHARPNESS,12,true);i.setItemMeta(x);return i;}
 public static ItemStack naturePick(){ItemStack i=gear(Material.DIAMOND_PICKAXE,"Earthshaper",ChatColor.GREEN,0);ItemMeta x=i.getItemMeta();x.addEnchant(Enchantment.EFFICIENCY,10,true);i.setItemMeta(x);return i;}
 public static ItemStack natureHelmet(){ItemStack i=gear(Material.DIAMOND_HELMET,"Gaia Crown",ChatColor.GREEN,22);return i;}
 public static ItemStack natureChest(){ItemStack i=gear(Material.DIAMOND_CHESTPLATE,"Gaia Heartplate",ChatColor.GREEN,22);return i;}
 public static ItemStack natureBoots(){ItemStack i=gear(Material.DIAMOND_BOOTS,"Gaia Treads",ChatColor.GREEN,22);return i;}
 public static ItemStack arcaneBlade(){ItemStack i=gear(Material.NETHERITE_SWORD,"Arcane Edge",ChatColor.LIGHT_PURPLE,0);ItemMeta x=i.getItemMeta();x.addEnchant(Enchantment.SHARPNESS,16,true);i.setItemMeta(x);return i;}
 public static ItemStack arcaneStaff(){ItemStack i=gear(Material.BLAZE_ROD,"Arcane Staff",ChatColor.LIGHT_PURPLE,0);ItemMeta x=i.getItemMeta();x.addEnchant(Enchantment.KNOCKBACK,8,true);i.setItemMeta(x);return i;}
 public static ItemStack arcanePick(){ItemStack i=gear(Material.NETHERITE_PICKAXE,"Arcane Excavator",ChatColor.LIGHT_PURPLE,0);ItemMeta x=i.getItemMeta();x.addEnchant(Enchantment.EFFICIENCY,12,true);i.setItemMeta(x);return i;}
 public static ItemStack arcaneHelmet(){ItemStack i=gear(Material.NETHERITE_HELMET,"Arcane Visor",ChatColor.LIGHT_PURPLE,29);return i;}
 public static ItemStack relic(Material mat,String name,ChatColor color,String rarity){ItemStack i=gear(mat,name,color,0);ItemMeta m=i.getItemMeta();m.setLore(List.of(color+"Rarity: "+rarity,ChatColor.GRAY+"ESN forged relic"));i.setItemMeta(m);return i;}
 public static ItemStack abyssBlade(){ItemStack i=weapon(Material.NETHERITE_SWORD,"Abyssal Sovereign",ChatColor.DARK_PURPLE,21);return i;}
 public static ItemStack abyssAxe(){return weapon(Material.NETHERITE_AXE,"Abyssal Executioner",ChatColor.DARK_PURPLE,18);}
 public static ItemStack abyssPick(){ItemStack i=relic(Material.NETHERITE_PICKAXE,"Abyssal Excavator",ChatColor.DARK_PURPLE,"DIVINE");ItemMeta m=i.getItemMeta();m.addEnchant(Enchantment.EFFICIENCY,13,true);m.addEnchant(Enchantment.FORTUNE,7,true);i.setItemMeta(m);return i;}
 public static ItemStack abyssHelmet(){return gear(Material.NETHERITE_HELMET,"Abyssal Crown",ChatColor.DARK_PURPLE,36);}
 public static ItemStack abyssChest(){return gear(Material.NETHERITE_CHESTPLATE,"Abyssal Heartplate",ChatColor.DARK_PURPLE,36);}
 public static ItemStack abyssLegs(){return gear(Material.NETHERITE_LEGGINGS,"Abyssal Legguards",ChatColor.DARK_PURPLE,36);}
 public static ItemStack abyssBoots(){return gear(Material.NETHERITE_BOOTS,"Abyssal Walkers",ChatColor.DARK_PURPLE,36);}
 public static ItemStack dragonBlade(){return weapon(Material.NETHERITE_SWORD,"Dragonfire Greatblade",ChatColor.DARK_RED,22);}
 public static ItemStack dragonAxe(){return weapon(Material.NETHERITE_AXE,"Dragonbone Cleaver",ChatColor.DARK_RED,19);}
 public static ItemStack dragonHelmet(){return gear(Material.NETHERITE_HELMET,"Dragonlord Helm",ChatColor.DARK_RED,38);}
 public static ItemStack dragonChest(){return gear(Material.NETHERITE_CHESTPLATE,"Dragonlord Plate",ChatColor.DARK_RED,38);}
 public static ItemStack dragonLegs(){return gear(Material.NETHERITE_LEGGINGS,"Dragonlord Guards",ChatColor.DARK_RED,38);}
 public static ItemStack dragonBoots(){return gear(Material.NETHERITE_BOOTS,"Dragonlord Treads",ChatColor.DARK_RED,38);}
 public static ItemStack cosmicBlade(){return weapon(Material.NETHERITE_SWORD,"Cosmic Riftblade",ChatColor.LIGHT_PURPLE,24);}
 public static ItemStack cosmicStaff(){ItemStack i=relic(Material.BLAZE_ROD,"Cosmic Scepter",ChatColor.LIGHT_PURPLE,"DIVINE");ItemMeta m=i.getItemMeta();m.addEnchant(Enchantment.KNOCKBACK,10,true);i.setItemMeta(m);return i;}
 public static ItemStack cosmicHelmet(){return gear(Material.NETHERITE_HELMET,"Cosmic Halo",ChatColor.LIGHT_PURPLE,40);}
 public static ItemStack cosmicChest(){return gear(Material.NETHERITE_CHESTPLATE,"Cosmic Aegis",ChatColor.LIGHT_PURPLE,40);}
 public static ItemStack cosmicLegs(){return gear(Material.NETHERITE_LEGGINGS,"Cosmic Greaves",ChatColor.LIGHT_PURPLE,40);}
 public static ItemStack cosmicBoots(){return gear(Material.NETHERITE_BOOTS,"Cosmic Steps",ChatColor.LIGHT_PURPLE,40);}
 public static ItemStack voidShard(){return relic(Material.ECHO_SHARD,"Void Shard",ChatColor.DARK_PURPLE,"EPIC");}
 public static ItemStack titanFragment(){return relic(Material.NETHERITE_SCRAP,"Titan Fragment",ChatColor.DARK_RED,"EPIC");}
 public static ItemStack celestialCrystal(){return relic(Material.AMETHYST_SHARD,"Celestial Crystal",ChatColor.AQUA,"LEGENDARY");}
 public static ItemStack bloodstone(){return relic(Material.REDSTONE,"Bloodstone",ChatColor.DARK_RED,"LEGENDARY");}
 public static ItemStack arcaneDust(){return relic(Material.GLOWSTONE_DUST,"Arcane Dust",ChatColor.LIGHT_PURPLE,"MYTHIC");}
 public static ItemStack cosmicCore(){return relic(Material.NETHER_STAR,"Cosmic Core",ChatColor.LIGHT_PURPLE,"DIVINE");}
}
