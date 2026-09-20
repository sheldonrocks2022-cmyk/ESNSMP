package com.esn.smp.listener;

import com.esn.smp.ESNSMPPlugin;
import com.esn.smp.spawn.SpawnManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import com.esn.smp.gameplay.CustomItems;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public final class SpawnListener implements Listener {
    private final ESNSMPPlugin plugin;
    private final SpawnManager spawnManager;

    public SpawnListener(ESNSMPPlugin plugin, SpawnManager spawnManager) {
        this.plugin = plugin;
        this.spawnManager = spawnManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!event.getPlayer().hasPlayedBefore()
                && plugin.getConfig().getBoolean("spawn.teleport-new-players", true)) {
            Location spawn = spawnManager.getSpawn();
            if (spawn != null) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!event.getPlayer().isOnline()) {
                        return;
                    }
                    event.getPlayer().teleport(spawn);
                    String title = color(plugin.getConfig().getString("branding.title", "&6&lESN &f&lSMP"));
                    String subtitle = color(plugin.getConfig().getString("branding.subtitle", "&7Survive • Build • Become Legendary"));
                    event.getPlayer().sendTitle(title, subtitle, 10, 70, 20);
                    event.getPlayer().sendMessage(color(plugin.getConfig().getString(
                            "branding.welcome-message", "&8[&6ESN&8] &fWelcome to &6ESN SMP&f!")));
                });
            }
        }
    }

    @EventHandler
    public void giveSpawnCompass(PlayerJoinEvent event) {
        Location spawn=spawnManager.getSpawn(); if(spawn==null)return;
        boolean has=false; for(ItemStack i:event.getPlayer().getInventory().getContents()) if(i!=null&&i.getType()==Material.COMPASS&&i.hasItemMeta()&&(ChatColor.GOLD+"ESN World Spawn").equals(i.getItemMeta().getDisplayName())) {has=true;break;}
        if(!has) event.getPlayer().getInventory().addItem(CustomItems.spawnCompass(spawn));
        event.getPlayer().setCompassTarget(spawn);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (!plugin.getConfig().getBoolean("spawn.teleport-on-respawn", true)) {
            return;
        }
        Location spawn = spawnManager.getSpawn();
        if (spawn != null) {
            event.setRespawnLocation(spawn);
        }
    }

    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }
}
