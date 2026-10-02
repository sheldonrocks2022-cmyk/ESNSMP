package com.esn.smp.gameplay;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived movement authorization used by ESN-exclusive abilities.
 *
 * Future exclusive sets should route any intentional teleport/dash/launch through
 * this class instead of teaching AntiCheat about individual item names.
 * This only suppresses movement checks for a tightly bounded window; mining and
 * every other anti-cheat check remain active.
 */
public final class ExclusiveMovementAuthorization {
    private static final long MAX_WINDOW_MS = 5_000L;
    private static final Map<UUID, Permit> PERMITS = new ConcurrentHashMap<>();

    private ExclusiveMovementAuthorization() {}

    public static void authorize(Player player, long millis, String source) {
        if (player == null) return;
        long duration = Math.max(100L, Math.min(MAX_WINDOW_MS, millis));
        long until = System.currentTimeMillis() + duration;
        PERMITS.merge(player.getUniqueId(),
                new Permit(until, source == null ? "exclusive ability" : source),
                (oldPermit, newPermit) -> oldPermit.expiresAt() >= newPermit.expiresAt() ? oldPermit : newPermit);

        if (PERMITS.size() > 256) {
            long now = System.currentTimeMillis();
            PERMITS.entrySet().removeIf(e -> e.getValue().expiresAt() <= now);
        }
    }

    public static boolean isAuthorized(Player player) {
        if (player == null) return false;
        Permit permit = PERMITS.get(player.getUniqueId());
        if (permit == null) return false;
        if (permit.expiresAt() <= System.currentTimeMillis()) {
            PERMITS.remove(player.getUniqueId(), permit);
            return false;
        }
        return true;
    }

    public static String activeSource(Player player) {
        Permit permit = player == null ? null : PERMITS.get(player.getUniqueId());
        return permit != null && permit.expiresAt() > System.currentTimeMillis() ? permit.source() : null;
    }

    public static boolean teleport(Player player, Location target, String source) {
        authorize(player, 2_000L, source);
        boolean moved = player.teleport(target);
        if (!moved) clear(player);
        return moved;
    }

    public static void setVelocity(Entity entity, Vector velocity, String source) {
        if (entity instanceof Player player) authorize(player, 2_500L, source);
        entity.setVelocity(velocity);
    }

    public static void clear(Player player) {
        if (player != null) PERMITS.remove(player.getUniqueId());
    }

    private record Permit(long expiresAt, String source) {}
}
