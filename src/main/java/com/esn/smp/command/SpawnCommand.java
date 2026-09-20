package com.esn.smp.command;

import com.esn.smp.spawn.SpawnManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class SpawnCommand implements CommandExecutor {
    private final SpawnManager spawnManager;

    public SpawnCommand(SpawnManager spawnManager) {
        this.spawnManager = spawnManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use /spawn.");
            return true;
        }

        Location spawn = spawnManager.getSpawn();
        if (spawn == null) {
            player.sendMessage(ChatColor.RED + "ESN spawn is unavailable because its world is not loaded.");
            return true;
        }

        player.teleport(spawn);
        player.sendMessage(ChatColor.GOLD + "Teleported to ESN SMP spawn.");
        return true;
    }
}
