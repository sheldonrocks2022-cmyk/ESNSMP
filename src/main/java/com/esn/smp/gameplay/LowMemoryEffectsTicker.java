package com.esn.smp.gameplay;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * One shared passive-effects loop for ESN paid gear.
 * This replaces multiple independent full-player scans while preserving
 * the same 10-tick update frequency and gameplay behavior.
 */
public final class LowMemoryEffectsTicker {
    public LowMemoryEffectsTicker(JavaPlugin plugin,
                                  RiftwalkerBundle riftwalker,
                                  ImmortalWardenBundle warden) {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            riftwalker.cleanupPassiveState(now);
            warden.cleanupPassiveState(now);

            if (Bukkit.getOnlinePlayers().isEmpty()) return;

            for (Player player : Bukkit.getOnlinePlayers()) {
                riftwalker.passiveTick(player);
                warden.passiveTick(player);
            }
        }, 20L, 10L);
    }
}
