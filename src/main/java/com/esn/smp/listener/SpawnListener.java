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
        boolean firstJoin=!event.getPlayer().hasPlayedBefore();
        if(firstJoin) giveStarterKit(event.getPlayer());
        if (firstJoin
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
                    // Starter kit is granted once before teleport; do not duplicate it here.
                });
            }
        }
    }

    private void giveStarterKit(org.bukkit.entity.Player p) {
        p.getInventory().addItem(new ItemStack(Material.IRON_SWORD),new ItemStack(Material.IRON_PICKAXE),new ItemStack(Material.IRON_AXE),new ItemStack(Material.IRON_SHOVEL));
        if(p.getInventory().getHelmet()==null)p.getInventory().setHelmet(new ItemStack(Material.IRON_HELMET));
        if(p.getInventory().getChestplate()==null)p.getInventory().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
        if(p.getInventory().getLeggings()==null)p.getInventory().setLeggings(new ItemStack(Material.IRON_LEGGINGS));
        if(p.getInventory().getBoots()==null)p.getInventory().setBoots(new ItemStack(Material.IRON_BOOTS));
        Location spawn=spawnManager.getSpawn();if(spawn!=null){p.getInventory().addItem(CustomItems.spawnCompass(spawn));p.setCompassTarget(spawn);}
        p.sendMessage(ChatColor.GREEN+"Starter kit received: iron armor, iron tools, sword and ESN spawn compass!");
    }


