package com.esn.smp.command;

import com.esn.smp.spawn.SpawnManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class ESNSpawnCommand implements CommandExecutor {
    private final SpawnManager spawnManager;

    public ESNSpawnCommand(SpawnManager spawnManager) {
        this.spawnManager = spawnManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("esnsmp.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.GOLD + "/esnspawn build");
            sender.sendMessage(ChatColor.GOLD + "/esnspawn rebuild");
            sender.sendMessage(ChatColor.GOLD + "/esnspawn status");
            sender.sendMessage(ChatColor.GOLD + "/esnspawn rollback");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "build" -> spawnManager.buildSpawn(sender);
            case "rebuild" -> spawnManager.rebuildSpawn(sender);
            case "status" -> sender.sendMessage(ChatColor.AQUA + "ESN spawn: " + spawnManager.status());
            case "rollback" -> spawnManager.rollback(sender);
            default -> sender.sendMessage(ChatColor.RED + "Unknown option. Use build, rebuild, status, or rollback.");
        }
        return true;
    }
}
