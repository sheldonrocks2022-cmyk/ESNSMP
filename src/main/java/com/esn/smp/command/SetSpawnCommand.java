package com.esn.smp.command;

import com.esn.smp.spawn.SpawnManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class SetSpawnCommand implements CommandExecutor {
    private final SpawnManager spawnManager;

    public SetSpawnCommand(SpawnManager spawnManager) {
        this.spawnManager = spawnManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can set the ESN spawn.");
            return true;
        }

        if (!player.hasPermission("esnsmp.admin")) {
            player.sendMessage(ChatColor.RED + "You do not have permission.");
            return true;
        }

        spawnManager.setSpawn(player.getLocation(), true);
        player.sendMessage(ChatColor.GREEN + "ESN SMP spawn location updated.");
        player.sendMessage(ChatColor.GRAY + "Run /esnspawn build to rebuild the custom hub here.");
        return true;
    }
}
