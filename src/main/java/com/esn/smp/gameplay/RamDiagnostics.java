package com.esn.smp.gameplay;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;

public final class RamDiagnostics implements CommandExecutor {
    private final JavaPlugin plugin;

    public RamDiagnostics(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("esnsmp.admin")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }

        Runtime rt = Runtime.getRuntime();
        long used = rt.totalMemory() - rt.freeMemory();
        long committed = rt.totalMemory();
        long max = rt.maxMemory();

        int loadedChunks = 0;
        int forcedChunks = 0;
        int entities = 0;
        int living = 0;
        int custom = 0;
        int bosses = 0;
        int adventure = 0;
        int trials = 0;
        int encounter = 0;
        int hubNpcs = 0;

        for (World world : Bukkit.getWorlds()) {
            Chunk[] chunks = world.getLoadedChunks();
            loadedChunks += chunks.length;
            try {
                forcedChunks += world.getForceLoadedChunks().size();
            } catch (Throwable ignored) {
            }

            for (Entity entity : world.getEntities()) {
                entities++;
                if (entity instanceof LivingEntity) living++;
                var tags = entity.getScoreboardTags();
                if (tags.isEmpty()) continue;

                boolean esn = tags.stream().anyMatch(t -> t.startsWith("esn") || t.startsWith("ESN"));
                if (esn) custom++;
                if (tags.contains("esnWorldBoss") || tags.contains("esnBiomeBoss") || tags.contains("esnSwampBoss")) bosses++;
                if (tags.contains("esnAdventureBoss") || tags.contains("esnRiftBoss") || tags.contains("esnRushBoss")) adventure++;
                if (tags.contains("esn_realm_trial")) trials++;
                if (tags.contains("esnRaid") || tags.contains("esnRaidBoss") || tags.contains("esnDungeonBoss")) encounter++;
                if (tags.contains("esnHubNpc")) hubNpcs++;
            }
        }

        sender.sendMessage(ChatColor.GOLD + "=== ESNSMP RAM Diagnostics ===");
        sender.sendMessage(ChatColor.YELLOW + "Heap used: " + mb(used) + " MB"
                + ChatColor.GRAY + " | committed " + mb(committed) + " MB"
                + " | max " + mb(max) + " MB");
        sender.sendMessage(ChatColor.YELLOW + "Worlds: " + Bukkit.getWorlds().size()
                + ChatColor.GRAY + " | loaded chunks " + loadedChunks
                + " | force-loaded " + forcedChunks);
        sender.sendMessage(ChatColor.YELLOW + "Entities: " + entities
                + ChatColor.GRAY + " | living " + living
                + " | ESN-tagged " + custom);
        sender.sendMessage(ChatColor.YELLOW + "Bosses: " + bosses
                + ChatColor.GRAY + " | adventure " + adventure
                + " | trials " + trials
                + " | encounter mobs " + encounter
                + " | hub NPCs " + hubNpcs);
        sender.sendMessage(ChatColor.GRAY + "If heap/chunks/entities keep increasing between checks, send these numbers before resetting.");
        plugin.getLogger().info("[ESNRAM] heapUsedMB=" + mb(used)
                + " committedMB=" + mb(committed)
                + " maxMB=" + mb(max)
                + " loadedChunks=" + loadedChunks
                + " forcedChunks=" + forcedChunks
                + " entities=" + entities
                + " living=" + living
                + " esnTagged=" + custom
                + " bosses=" + bosses
                + " adventure=" + adventure
                + " trials=" + trials
                + " encounters=" + encounter
                + " hubNpcs=" + hubNpcs);
        return true;
    }

    private static long mb(long bytes) {
        return bytes / (1024L * 1024L);
    }
}
