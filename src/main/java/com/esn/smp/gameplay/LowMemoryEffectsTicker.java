package com.esn.smp.gameplay;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;

/**
 * One shared passive-effects loop for all ESN premium gear.
 *
 * Gameplay still updates every 10 ticks, but expired state maps are cleaned
 * every 5 seconds instead of being rescanned twice per second. The online
 * player collection is also fetched once per cycle. No features are removed.
 */
public final class LowMemoryEffectsTicker {
    private int cleanupCycles;
    private int voidVisualCycles;

    public LowMemoryEffectsTicker(JavaPlugin plugin,
                                  RiftwalkerBundle riftwalker,
                                  ImmortalWardenBundle warden,
                                  VoidWarriorBundle voidWarrior,
                                  LightningKingBundle lightningKing) {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // Cleanup is housekeeping only; cooldown checks use their timestamps
            // directly, so reducing cleanup frequency cannot extend a cooldown.
            if (++cleanupCycles >= 10) {
                cleanupCycles = 0;
                long now = System.currentTimeMillis();
                riftwalker.cleanupPassiveState(now);
                warden.cleanupPassiveState(now);
                voidWarrior.cleanupPassiveState(now);
                lightningKing.cleanupPassiveState(now);
            }

            Collection<? extends Player> online = Bukkit.getOnlinePlayers();
            if (online.isEmpty()) return;

            boolean showVoidVisuals = ++voidVisualCycles >= 4;
            if (showVoidVisuals) voidVisualCycles = 0;
            for (Player player : online) {
                riftwalker.passiveTick(player);
                warden.passiveTick(player);
                voidWarrior.passiveTick(player, showVoidVisuals);
                lightningKing.passiveTick(player, showVoidVisuals);
            }
        }, 20L, 10L);
    }
}
