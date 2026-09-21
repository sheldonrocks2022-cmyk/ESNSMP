package com.esn.smp.spawn;

import com.esn.smp.auction.AuctionHouse;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerInteractEvent;

public final class HubServiceListener implements Listener {
    private final SpawnManager spawn;
    private final AuctionHouse auctions;
    public HubServiceListener(SpawnManager spawn,AuctionHouse auctions){this.spawn=spawn;this.auctions=auctions;}

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
                    switch(s){
                        case AUCTION -> auctions.open(p,0);
                        case SHOP -> Bukkit.dispatchCommand(p,"shop");
                        case CRATES -> Bukkit.dispatchCommand(p,"crates");
                        case QUESTS -> Bukkit.dispatchCommand(p,"quests");
                        case WARPS -> Bukkit.dispatchCommand(p,"warps");
                        case LEADERBOARDS -> Bukkit.dispatchCommand(p,"leaderboard");
                        case INFO -> Bukkit.dispatchCommand(p,"menu");
                    }
                }catch(Exception ex){p.sendMessage(ChatColor.RED+"That station is temporarily unavailable.");}
                return;
            }
        }
    }
}
