package com.esn.smp.gameplay;
import org.bukkit.*;import org.bukkit.entity.*;import org.bukkit.event.*;import org.bukkit.event.entity.EntityDeathEvent;import org.bukkit.inventory.*;import org.bukkit.inventory.meta.ItemMeta;import java.util.concurrent.ThreadLocalRandom;
public final class LootDrops implements Listener{
 @EventHandler public void death(EntityDeathEvent e){
  LivingEntity mob=e.getEntity();Player killer=mob.getKiller();if(killer==null)return;
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
 public static ItemStack key(){ItemStack i=new ItemStack(Material.TRIPWIRE_HOOK);ItemMeta m=i.getItemMeta();m.setDisplayName(ChatColor.GOLD+"ESN Crate Key");m.setLore(java.util.List.of(ChatColor.GRAY+"Opens ESN and Celestial crates"));i.setItemMeta(m);return i;}
}