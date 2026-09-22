package com.esn.smp.listener;

import com.esn.smp.spawn.SpawnManager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Monster;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.projectiles.ProjectileSource;

public final class SpawnProtectionListener implements Listener {
    private final SpawnManager spawnManager;

    public SpawnProtectionListener(SpawnManager spawnManager) {
        this.spawnManager = spawnManager;
    }

    private boolean bypass(Player player) {
        return player.hasPermission("esnsmp.spawn.bypass");
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (spawnManager.isProtected(event.getBlock().getLocation()) && !bypass(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (spawnManager.isProtected(event.getBlock().getLocation()) && !bypass(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (spawnManager.isProtected(event.getBlockClicked().getRelative(event.getBlockFace()).getLocation())
                && !bypass(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (spawnManager.isProtected(event.getBlockClicked().getLocation()) && !bypass(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onIgnite(BlockIgniteEvent event) {
        if (spawnManager.isProtected(event.getBlock().getLocation())) {
            if (event.getPlayer() == null || !bypass(event.getPlayer())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onExplosion(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> spawnManager.isProtected(block.getLocation()));
    }

    @EventHandler
    public void onBlockExplosion(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> spawnManager.isProtected(block.getLocation()));
    }

    @EventHandler
    public void onFlow(BlockFromToEvent event) {
        if (spawnManager.isProtected(event.getToBlock().getLocation())
                && !spawnManager.isProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(block ->
                spawnManager.isProtected(block.getRelative(event.getDirection()).getLocation()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> spawnManager.isProtected(block.getLocation())
                || spawnManager.isProtected(block.getRelative(event.getDirection()).getLocation()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onMobSpawn(CreatureSpawnEvent event) {
        if (event.getEntity() instanceof Monster && spawnManager.isProtected(event.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPvP(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !spawnManager.isProtected(victim.getLocation())) {
            return;
        }

        Player attacker = null;
        if (event.getDamager() instanceof Player player) {
            attacker = player;
        } else if (event.getDamager() instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player player) {
                attacker = player;
            }
        }

        if (attacker != null && !bypass(attacker)) {
            event.setCancelled(true);
        }
    }
}
