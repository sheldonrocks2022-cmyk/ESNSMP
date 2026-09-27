package com.esn.smp.gameplay;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class BossHealthBars implements Listener {
    private final JavaPlugin plugin;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private int discoveryCycles = 119;

    public BossHealthBars(JavaPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::pulse, 40L, 40L);
    }

    private void pulse() {
        if (++discoveryCycles >= 120) {
            discoveryCycles = 0;
            discover();
        }
        tick();
    }

    private boolean boss(LivingEntity entity) {
        Set<String> tags = entity.getScoreboardTags();
        return tags.contains("esnSwampBoss") || tags.contains("esnBiomeBoss") || tags.contains("esnWorldBoss");
    }

    private void register(LivingEntity entity) {
        if (!entity.isValid() || entity.isDead() || !boss(entity)) return;
        bars.computeIfAbsent(entity.getUniqueId(), id -> Bukkit.createBossBar(
                entity.getCustomName() == null ? "ESN BOSS" : entity.getCustomName(),
                entity.getScoreboardTags().contains("esnWorldBoss") ? BarColor.PURPLE : BarColor.RED,
                BarStyle.SEGMENTED_10));
    }

    private void discover() {
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) register(entity);
        }
    }

    @EventHandler
    public void spawn(CreatureSpawnEvent event) {
        LivingEntity entity = event.getEntity();
        // Boss tags are applied by the spawning systems immediately after creation.
        // A tiny delay avoids needing a frequent full-world entity scan.
        Bukkit.getScheduler().runTaskLater(plugin, () -> register(entity), 2L);
    }

    private void tick() {
        Iterator<Map.Entry<UUID, BossBar>> iterator = bars.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, BossBar> entry = iterator.next();
            Entity raw = Bukkit.getEntity(entry.getKey());
            if (!(raw instanceof LivingEntity entity) || !entity.isValid() || entity.isDead() || !boss(entity)) {
                entry.getValue().removeAll();
                iterator.remove();
                continue;
            }

            BossBar bar = entry.getValue();
            double max = EndgameSystems.virtualMax(plugin, entity);
            double hp = EndgameSystems.virtualHealth(plugin, entity);
            Set<String> tags = entity.getScoreboardTags();
            int stages = tags.contains("esnWorldBoss") ? 40 : 5;
            int stage = 1;
            for (String tag : tags) {
                if (!tag.startsWith("esnStage_")) continue;
                try {
                    stage = Math.max(stage, Integer.parseInt(tag.substring(9)));
                } catch (NumberFormatException ignored) {
                }
            }

            String title = (entity.getCustomName() == null ? "ESN BOSS" : entity.getCustomName())
                    + ChatColor.GOLD + " • Stage " + stage + "/" + stages;
            if (!title.equals(bar.getTitle())) bar.setTitle(title);

            double progress = Math.max(0.0, Math.min(1.0, hp / Math.max(1.0, max)));
            if (Math.abs(bar.getProgress() - progress) > 0.0001) bar.setProgress(progress);

            World world = entity.getWorld();
            Location bossLocation = entity.getLocation();

            List<Player> current = bar.getPlayers();
            for (int i = current.size() - 1; i >= 0; i--) {
                Player player = current.get(i);
                if (player.getWorld() != world || player.getLocation().distanceSquared(bossLocation) > 22500.0) {
                    bar.removePlayer(player);
                }
            }

            List<Player> remaining = bar.getPlayers();
            for (Player player : world.getPlayers()) {
                if (player.getLocation().distanceSquared(bossLocation) <= 22500.0 && !remaining.contains(player)) {
                    bar.addPlayer(player);
                }
            }
        }
    }

    @EventHandler
    public void death(EntityDeathEvent event) {
        BossBar bar = bars.remove(event.getEntity().getUniqueId());
        if (bar != null) bar.removeAll();
    }
}
