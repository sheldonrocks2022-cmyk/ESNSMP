package com.esn.smp.gameplay;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.*;

public final class ESNItemsCommand implements CommandExecutor {
 private static final Map<String,java.util.function.Supplier<ItemStack>> ITEMS=new LinkedHashMap<>();
 static {
  for(int t=0;t<MegaCrates.COUNT;t++){final int q=t;String z="realm"+String.format("%03d",t+1);ITEMS.put(z+"key",()->MegaCrates.key(q));for(int s=0;s<MegaCrates.ITEMS;s++){final int w=s;String[] suf={"blade","axe","pick","shovel","hoe","helmet","chest","legs","boots","bow","crossbow"};ITEMS.put(z+suf[s],()->MegaCrates.item(q,w));}}
  for(int t=0;t<ExtendedCrates.LOW.length;t++){final int q=t;String z=ExtendedCrates.LOW[t].toLowerCase().replace(" ","");ITEMS.put(z+"key",()->ExtendedCrates.key(false,q));for(int s=0;s<9;s++){final int w=s;ITEMS.put(z+(s==0?"blade":s==1?"axe":s==2?"pick":s==3?"shovel":s==4?"hoe":s==5?"helmet":s==6?"chest":s==7?"legs":"boots"),()->ExtendedCrates.item(false,q,w));}}
  for(int t=0;t<ExtendedCrates.HIGH.length;t++){final int q=t;String z=ExtendedCrates.HIGH[t].toLowerCase().replace(" ","");ITEMS.put(z+"key",()->ExtendedCrates.key(true,q));for(int s=0;s<9;s++){final int w=s;ITEMS.put(z+(s==0?"blade":s==1?"axe":s==2?"pick":s==3?"shovel":s==4?"hoe":s==5?"helmet":s==6?"chest":s==7?"legs":"boots"),()->ExtendedCrates.item(true,q,w));}}
  for(int t=0;t<10;t++){final int q=t;String z=MythicCrates.TIERS[t].toLowerCase();ITEMS.put(z+"key",()->MythicCrates.crateKey(q));for(int s=0;s<9;s++){final int w=s;ITEMS.put(z+(s==0?"blade":s==1?"axe":s==2?"pick":s==3?"shovel":s==4?"hoe":s==5?"helmet":s==6?"chest":s==7?"legs":"boots"),()->MythicCrates.item(q,w));}} ITEMS.put("key",LootDrops::key); ITEMS.put("abysskey",LootDrops::abyssKey); ITEMS.put("dragonkey",LootDrops::dragonKey); ITEMS.put("eternalkey",EternalGear::key); ITEMS.put("eternalsword",EternalGear::sword); ITEMS.put("eternalaxe",EternalGear::axe); ITEMS.put("eternalpick",EternalGear::pick); ITEMS.put("eternalshovel",EternalGear::shovel); ITEMS.put("eternalhoe",EternalGear::hoe); ITEMS.put("eternalhelmet",EternalGear::helmet); ITEMS.put("eternalchest",EternalGear::chest); ITEMS.put("eternallegs",EternalGear::legs); ITEMS.put("eternalboots",EternalGear::boots);
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
  ITEMS.put("abyssblade",CustomItems::abyssBlade); ITEMS.put("abyssaxe",CustomItems::abyssAxe); ITEMS.put("abysspick",CustomItems::abyssPick); ITEMS.put("abysshelmet",CustomItems::abyssHelmet); ITEMS.put("abysschest",CustomItems::abyssChest); ITEMS.put("abysslegs",CustomItems::abyssLegs); ITEMS.put("abyssboots",CustomItems::abyssBoots);
  ITEMS.put("dragonblade",CustomItems::dragonBlade); ITEMS.put("dragonaxe",CustomItems::dragonAxe); ITEMS.put("dragonhelmet",CustomItems::dragonHelmet); ITEMS.put("dragonchest",CustomItems::dragonChest); ITEMS.put("dragonlegs",CustomItems::dragonLegs); ITEMS.put("dragonboots",CustomItems::dragonBoots);
  ITEMS.put("cosmicblade",CustomItems::cosmicBlade); ITEMS.put("cosmicstaff",CustomItems::cosmicStaff); ITEMS.put("cosmichelmet",CustomItems::cosmicHelmet); ITEMS.put("cosmicchest",CustomItems::cosmicChest); ITEMS.put("cosmiclegs",CustomItems::cosmicLegs); ITEMS.put("cosmicboots",CustomItems::cosmicBoots);
  ITEMS.put("voidshard",CustomItems::voidShard); ITEMS.put("titanfragment",CustomItems::titanFragment); ITEMS.put("celestialcrystal",CustomItems::celestialCrystal); ITEMS.put("bloodstone",CustomItems::bloodstone); ITEMS.put("arcanedust",CustomItems::arcaneDust); ITEMS.put("cosmiccore",CustomItems::cosmicCore);
  ITEMS.put("angelwings",ESNItemsCommand::seasonAngelWings); ITEMS.put("infernoscepter",()->seasonMagic(Material.BLAZE_ROD,"Inferno Scepter","inferno","Launches a burning blast.")); ITEMS.put("stormcrystal",()->seasonMagic(Material.AMETHYST_SHARD,"Storm Crystal","storm","Calls lightning where you aim.")); ITEMS.put("tideheart",()->seasonMagic(Material.HEART_OF_THE_SEA,"Tideheart","tide","Heals and empowers you.")); ITEMS.put("voidrelic",()->seasonMagic(Material.ECHO_SHARD,"Void Relic","void","Blink through space.")); ITEMS.put("celestialstar",()->seasonMagic(Material.NETHER_STAR,"Celestial Star","celestial","Unleashes celestial power."));
  ITEMS.put(RiftwalkerBundle.BLADE_ID,RiftwalkerBundle::riftBlade); ITEMS.put(RiftwalkerBundle.WINGS_ID,RiftwalkerBundle::riftWings); ITEMS.put(RiftwalkerBundle.BOOTS_ID,RiftwalkerBundle::phaseBoots); ITEMS.put(RiftwalkerBundle.BOW_ID,RiftwalkerBundle::riftBow); ITEMS.put(RiftwalkerBundle.CORE_ID,RiftwalkerBundle::riftCore); ITEMS.put(RiftwalkerBundle.COMPASS_ID,RiftwalkerBundle::voidCompass);
  ITEMS.put(ImmortalWardenBundle.HELMET_ID,ImmortalWardenBundle::helmet); ITEMS.put(ImmortalWardenBundle.CHEST_ID,ImmortalWardenBundle::chestplate); ITEMS.put(ImmortalWardenBundle.LEGS_ID,ImmortalWardenBundle::leggings); ITEMS.put(ImmortalWardenBundle.BOOTS_ID,ImmortalWardenBundle::boots); ITEMS.put(ImmortalWardenBundle.BLADE_ID,ImmortalWardenBundle::blade); ITEMS.put(ImmortalWardenBundle.BOW_ID,ImmortalWardenBundle::bow); ITEMS.put(ImmortalWardenBundle.CORE_ID,ImmortalWardenBundle::core); ITEMS.put(ImmortalWardenBundle.TOTEM_ID,ImmortalWardenBundle::totem);
 }
 private static ItemStack seasonAngelWings(){ItemStack i=new ItemStack(Material.ELYTRA);ItemMeta m=i.getItemMeta();m.setDisplayName(ChatColor.WHITE+""+ChatColor.BOLD+"✦ ANGEL WINGS ✦");m.setLore(List.of(ChatColor.AQUA+"Season Pass Reward I",ChatColor.GRAY+"A celestial relic from ESN.",ChatColor.GOLD+"Unbreaking CC"));i.setItemMeta(m);i.addUnsafeEnchantment(Enchantment.UNBREAKING,200);return i;}
 private static ItemStack seasonMagic(Material mat,String name,String id,String lore){ItemStack i=new ItemStack(mat);ItemMeta m=i.getItemMeta();m.setDisplayName(ChatColor.LIGHT_PURPLE+""+ChatColor.BOLD+name);m.setLore(List.of(ChatColor.AQUA+lore,ChatColor.GRAY+"Right-click to unleash its magic."));NamespacedKey key=NamespacedKey.fromString("esnsmp:season_magic");if(key!=null)m.getPersistentDataContainer().set(key,PersistentDataType.STRING,id);i.setItemMeta(m);i.addUnsafeEnchantment(Enchantment.UNBREAKING,10);return i;}
 public static ItemStack createStoreItem(String itemId){
  if(itemId==null)return null;
  var supplier=ITEMS.get(itemId.toLowerCase(Locale.ROOT));
  return supplier==null?null:supplier.get();
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