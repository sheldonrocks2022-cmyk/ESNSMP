package com.esn.smp.spawn;

import com.esn.smp.auction.AuctionHouse;import com.esn.smp.gameplay.SMPGameplay;import com.esn.smp.gameplay.ServerMenus;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Entity;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

public final class HubServiceListener implements Listener {
    private final SpawnManager spawn;
    private final AuctionHouse auctions;
    public HubServiceListener(SpawnManager spawn,AuctionHouse auctions){this.spawn=spawn;this.auctions=auctions;}

    public void respawnNpcs(){Location base=spawn.getSpawn();if(base==null||base.getWorld()==null)return;World w=base.getWorld();for(Entity en:w.getEntities())if(en.getScoreboardTags().contains("esnHubNpc"))en.remove();for(HubService s:HubService.values()){Location l=base.clone().add(s.offsetX(),1,s.offsetZ());Villager v=w.spawn(l,Villager.class);v.setCustomName(ChatColor.GOLD+""+ChatColor.BOLD+s.displayName());v.setCustomNameVisible(true);v.setAI(false);v.setInvulnerable(true);v.setSilent(true);v.setCollidable(false);v.setRemoveWhenFarAway(false);v.addScoreboardTag("esnHubNpc");v.addScoreboardTag("esnService_"+s.name());}}
    @EventHandler public void npc(PlayerInteractEntityEvent e){if(!(e.getRightClicked() instanceof Villager v)||!v.getScoreboardTags().contains("esnHubNpc"))return;e.setCancelled(true);for(HubService s:HubService.values())if(v.getScoreboardTags().contains("esnService_"+s.name())){try{openService(e.getPlayer(),s)}catch(Exception ex){e.getPlayer().sendMessage(ChatColor.RED+"That service is temporarily unavailable.");}return;}}
    @EventHandler public void protect(EntityDamageEvent e){if(e.getEntity().getScoreboardTags().contains("esnHubNpc"))e.setCancelled(true);}

    @EventHandler public void interact(PlayerInteractEvent e){
        if(e.getClickedBlock()==null)return;
        Location base=spawn.getSpawn(); if(base==null||base.getWorld()==null)return;
        Location b=e.getClickedBlock().getLocation();
        if(!b.getWorld().equals(base.getWorld()))return;
        for(HubService s:HubService.values()){
            int x=base.getBlockX()+s.offsetX(), z=base.getBlockZ()+s.offsetZ();
            if(Math.abs(b.getBlockX()-x)<=3&&Math.abs(b.getBlockZ()-z)<=3&&b.getBlockY()>=base.getBlockY()&&b.getBlockY()<=base.getBlockY()+8){
                e.setCancelled(true); Player p=e.getPlayer();
                try{
                    openService(p,s)
                }catch(Exception ex){p.sendMessage(ChatColor.RED+"That station is temporarily unavailable.");}
                return;
            }
        }
    }
}
