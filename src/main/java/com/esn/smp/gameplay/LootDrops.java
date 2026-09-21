package com.esn.smp.gameplay;
import org.bukkit.*;import org.bukkit.entity.*;import org.bukkit.event.*;import org.bukkit.event.entity.EntityDeathEvent;import org.bukkit.inventory.*;import org.bukkit.inventory.meta.ItemMeta;import org.bukkit.persistence.PersistentDataType;import org.bukkit.NamespacedKey;import java.util.concurrent.ThreadLocalRandom;
public final class LootDrops implements Listener{
 private static ItemStack tag(ItemStack i,String id){ItemMeta m=i.getItemMeta();m.getPersistentDataContainer().set(new NamespacedKey("esnsmp","item_id"),PersistentDataType.STRING,id);i.setItemMeta(m);return i;}
 @EventHandler public void death(EntityDeathEvent e){
  LivingEntity mob=e.getEntity();Player killer=mob.getKiller();if(killer==null)return;
  boolean worldBoss=mob.getScoreboardTags().contains("esnWorldBoss"),swampBoss=mob.getScoreboardTags().contains("esnSwampBoss"),biomeBoss=mob.getScoreboardTags().contains("esnBiomeBoss");if(worldBoss||swampBoss||biomeBoss){int amount=worldBoss?4:2;ItemStack regular=key(),abyss=abyssKey(),dragon=dragonKey(),eternal=EternalGear.key();regular.setAmount(amount);abyss.setAmount(amount);dragon.setAmount(amount);eternal.setAmount(amount);e.getDrops().add(regular);e.getDrops().add(abyss);e.getDrops().add(dragon);e.getDrops().add(eternal);killer.sendMessage(ChatColor.GOLD+(worldBoss?"WORLD BOSS: exactly 4 of each core crate key dropped!":"BOSS: exactly 2 of each core crate key dropped!"));return;}double eternalChance=(mob instanceof Monster||mob instanceof Slime||mob instanceof Phantom)?0.40:(mob instanceof Animals||mob instanceof WaterMob||mob instanceof Ambient||mob instanceof Villager||mob instanceof WanderingTrader||mob instanceof IronGolem||mob instanceof Snowman)?0.25:0.0;
  if(eternalChance>0&&ThreadLocalRandom.current().nextDouble()<eternalChance){e.getDrops().add(EternalGear.key());killer.sendMessage(ChatColor.DARK_AQUA+"Eternal Crate Key dropped!");}
  double chance=chance(mob);if(chance<=0)return;
  if(ThreadLocalRandom.current().nextDouble()<chance){e.getDrops().add(key());killer.sendMessage(ChatColor.GOLD+"Rare drop: ESN Crate Key!");}
 }
 private double chance(LivingEntity mob){
  if(mob instanceof Enderman||mob instanceof EnderDragon||mob instanceof Shulker||mob instanceof Endermite||
     mob instanceof Blaze||mob instanceof Ghast||mob instanceof Hoglin||mob instanceof Zoglin||mob instanceof Piglin||
     mob instanceof PiglinBrute||mob instanceof WitherSkeleton||mob instanceof MagmaCube||mob instanceof Strider||mob instanceof Wither)return .60;
  if(mob instanceof Animals||mob instanceof WaterMob||mob instanceof Ambient||mob instanceof Villager||mob instanceof WanderingTrader||mob instanceof IronGolem||mob instanceof Snowman)return .20;
  if(mob instanceof Monster||mob instanceof Slime||mob instanceof Phantom)return .35;
  return 0;
 }
 public static ItemStack key(){ItemStack i=new ItemStack(Material.TRIPWIRE_HOOK);ItemMeta m=i.getItemMeta();m.setDisplayName(ChatColor.GOLD+"ESN Crate Key");m.setLore(java.util.List.of(ChatColor.GRAY+"Opens ESN and Celestial crates"));i.setItemMeta(m);return tag(i,"crate_key");}
 public static ItemStack abyssKey(){ItemStack i=new ItemStack(Material.ECHO_SHARD);ItemMeta m=i.getItemMeta();m.setDisplayName(ChatColor.DARK_PURPLE+"Abyssal Crate Key");m.setLore(java.util.List.of(ChatColor.GRAY+"Opens only the Abyssal Crate"));i.setItemMeta(m);return tag(i,"abyss_key");}
 public static ItemStack dragonKey(){ItemStack i=new ItemStack(Material.DRAGON_BREATH);ItemMeta m=i.getItemMeta();m.setDisplayName(ChatColor.DARK_RED+"Dragonlord Crate Key");m.setLore(java.util.List.of(ChatColor.GRAY+"Opens only the Dragonlord Crate"));i.setItemMeta(m);return tag(i,"dragon_key");}
}
