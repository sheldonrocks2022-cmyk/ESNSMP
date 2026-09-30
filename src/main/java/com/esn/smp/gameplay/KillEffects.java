package com.esn.smp.gameplay;

import com.esn.smp.ESNSMPPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * ESN Store kill effects.
 *
 * Design:
 * - Cosmetic only: no damage, fire, explosions, knockback or block changes.
 * - Store delivery uses redeemable tokens, so the existing Stripe bridge can
 *   deliver these without needing a second purchase system.
 * - Unlocks and the equipped effect live in the player's PersistentDataContainer
 *   and survive restarts/reconnects.
 * - Effects trigger whenever the killer is a player and any living entity dies.
 */
public final class KillEffects implements Listener, CommandExecutor {
    public static final String LIGHTNING_TOKEN_ID = "killeffectlightning";
    public static final String SOUL_TOKEN_ID = "killeffectsoul";
    public static final String VOID_TOKEN_ID = "killeffectvoid";
    public static final String METEOR_TOKEN_ID = "killeffectmeteor";
    public static final String WARDEN_TOKEN_ID = "killeffectwarden";
    public static final String FIRE_TORNADO_TOKEN_ID = "killeffectfiretornado";

    private static final NamespacedKey TOKEN_EFFECT =
            new NamespacedKey("esnsmp", "kill_effect_token");
    private static final NamespacedKey UNLOCKED =
            new NamespacedKey("esnsmp", "kill_effects_unlocked");
    private static final NamespacedKey EQUIPPED =
            new NamespacedKey("esnsmp", "kill_effect_equipped");

    private final ESNSMPPlugin plugin;

    public KillEffects(ESNSMPPlugin plugin) {
        this.plugin = plugin;
    }

    public enum Effect {
        LIGHTNING("lightning", "Lightning Strike", Material.LIGHTNING_ROD),
        SOUL_EXPLOSION("soul", "Soul Explosion", Material.SOUL_LANTERN),
        VOID_IMPLOSION("void", "Void Implosion", Material.ECHO_SHARD),
        METEOR_STRIKE("meteor", "Meteor Strike", Material.FIRE_CHARGE),
        WARDEN_SONIC("warden", "Warden Sonic Burst", Material.SCULK_CATALYST),
        FIRE_TORNADO("firetornado", "Fire Tornado", Material.MAGMA_CREAM);

        private final String id;
        private final String displayName;
        private final Material tokenMaterial;

        Effect(String id, String displayName, Material tokenMaterial) {
            this.id = id;
            this.displayName = displayName;
            this.tokenMaterial = tokenMaterial;
        }

        public String id() {
            return id;
        }

        public String displayName() {
            return displayName;
        }

        public Material tokenMaterial() {
            return tokenMaterial;
        }

        public static Effect fromId(String input) {
            if (input == null) return null;
            String normalized = input.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
            for (Effect effect : values()) {
                String id = effect.id.replace("_", "").replace("-", "");
                if (id.equals(normalized)) return effect;
            }
            if ("soulexplosion".equals(normalized)) return SOUL_EXPLOSION;
            if ("voidimplosion".equals(normalized)) return VOID_IMPLOSION;
            if ("meteorstrike".equals(normalized)) return METEOR_STRIKE;
            if ("wardensonic".equals(normalized) || "sonic".equals(normalized)) return WARDEN_SONIC;
            if ("lightningstrike".equals(normalized)) return LIGHTNING;
            return null;
        }
    }

    public static ItemStack lightningToken() {
        return token(Effect.LIGHTNING);
    }

    public static ItemStack soulToken() {
        return token(Effect.SOUL_EXPLOSION);
    }

    public static ItemStack voidToken() {
        return token(Effect.VOID_IMPLOSION);
    }

    public static ItemStack meteorToken() {
        return token(Effect.METEOR_STRIKE);
    }

    public static ItemStack wardenToken() {
        return token(Effect.WARDEN_SONIC);
    }

    public static ItemStack fireTornadoToken() {
        return token(Effect.FIRE_TORNADO);
    }

    private static ItemStack token(Effect effect) {
        ItemStack item = new ItemStack(effect.tokenMaterial());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "✦ " + effect.displayName().toUpperCase(Locale.ROOT) + " TOKEN ✦");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.YELLOW + "Permanent ESN Kill Effect unlock");
        lore.add(ChatColor.GRAY + "Right-click to redeem this token.");
        lore.add(ChatColor.GRAY + "After redeeming: /killeffects equip " + effect.id());
        lore.add("");
        lore.add(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "ESN STORE COSMETIC");
        lore.add(ChatColor.GRAY + "Cosmetic only — gives no combat advantage.");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(TOKEN_EFFECT, PersistentDataType.STRING, effect.id());
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void redeemToken(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return;
        String id = item.getItemMeta().getPersistentDataContainer().get(TOKEN_EFFECT, PersistentDataType.STRING);
        Effect effect = Effect.fromId(id);
        if (effect == null) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        if (isUnlocked(player, effect)) {
            player.sendMessage(ChatColor.YELLOW + "You already own the " + effect.displayName() + " kill effect.");
            return;
        }

        unlock(player, effect);
        if (item.getAmount() <= 1) player.getInventory().setItemInMainHand(null);
        else item.setAmount(item.getAmount() - 1);

        player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "ESN KILL EFFECT UNLOCKED!");
        player.sendMessage(ChatColor.YELLOW + effect.displayName() + ChatColor.GRAY + " is now permanently unlocked.");
        player.sendMessage(ChatColor.GRAY + "It has been equipped automatically. Use /killeffects anytime.");
        player.sendActionBar(ChatColor.GOLD + effect.displayName() + " unlocked");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) return;

        Effect equipped = getEquipped(killer);
        if (equipped == null || !isUnlocked(killer, equipped)) return;

        Location center = victim.getLocation().clone().add(0, 0.2, 0);
        plugin.getServer().getScheduler().runTask(plugin, () -> play(equipped, center));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            CosmeticsGui.openKillEffects(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("off") || args[0].equalsIgnoreCase("none")) {
            player.getPersistentDataContainer().remove(EQUIPPED);
            player.sendMessage(ChatColor.YELLOW + "Kill effect disabled.");
            return true;
        }

        if (args[0].equalsIgnoreCase("equip")) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.YELLOW + "/killeffects equip <lightning|soul|void|meteor|warden|firetornado>");
                return true;
            }
            Effect effect = Effect.fromId(args[1]);
            if (effect == null) {
                player.sendMessage(ChatColor.RED + "Unknown kill effect.");
                return true;
            }
            if (!isUnlocked(player, effect)) {
                player.sendMessage(ChatColor.RED + "You have not unlocked " + effect.displayName() + ".");
                return true;
            }
            equip(player, effect);
            player.sendMessage(ChatColor.GREEN + "Equipped kill effect: " + ChatColor.GOLD + effect.displayName());
            return true;
        }

        if (args[0].equalsIgnoreCase("preview")) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.YELLOW + "/killeffects preview <effect>");
                return true;
            }
            Effect effect = Effect.fromId(args[1]);
            if (effect == null) {
                player.sendMessage(ChatColor.RED + "Unknown kill effect.");
                return true;
            }
            if (!isUnlocked(player, effect)) {
                player.sendMessage(ChatColor.RED + "You have not unlocked " + effect.displayName() + ".");
                return true;
            }
            Location preview = player.getLocation().clone().add(player.getLocation().getDirection().setY(0).normalize().multiply(2.5));
            play(effect, preview);
            player.sendMessage(ChatColor.GRAY + "Preview: " + ChatColor.GOLD + effect.displayName());
            return true;
        }

        if (args[0].equalsIgnoreCase("unlock") && isAdmin(player)) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.YELLOW + "/killeffects unlock <effect>");
                return true;
            }
            Effect effect = Effect.fromId(args[1]);
            if (effect == null) {
                player.sendMessage(ChatColor.RED + "Unknown kill effect.");
                return true;
            }
            unlock(player, effect);
            player.sendMessage(ChatColor.GREEN + "Admin unlock granted: " + effect.displayName());
            return true;
        }

        player.sendMessage(ChatColor.YELLOW + "/killeffects [list|equip <effect>|preview <effect>|off]");
        return true;
    }

    private void sendStatus(Player player) {
        Effect equipped = getEquipped(player);
        player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "ESN KILL EFFECTS");
        player.sendMessage(ChatColor.GRAY + "Equipped: " + ChatColor.WHITE +
                (equipped == null ? "None" : equipped.displayName()));
        for (Effect effect : Effect.values()) {
            boolean unlocked = isUnlocked(player, effect);
            boolean active = equipped == effect;
            player.sendMessage((unlocked ? ChatColor.GREEN : ChatColor.DARK_GRAY) +
                    (active ? "▶ " : "• ") + effect.displayName() +
                    (unlocked ? ChatColor.GRAY + " — /killeffects equip " + effect.id() : ChatColor.GRAY + " — Locked"));
        }
        player.sendMessage(ChatColor.DARK_GRAY + "Store effects are permanent once redeemed.");
    }

    private static void unlock(Player player, Effect effect) {
        Set<String> unlocked = unlocked(player);
        unlocked.add(effect.id());
        saveUnlocked(player, unlocked);
        equip(player, effect);
    }

    private static void equip(Player player, Effect effect) {
        player.getPersistentDataContainer().set(EQUIPPED, PersistentDataType.STRING, effect.id());
    }

    private static boolean isUnlocked(Player player, Effect effect) {
        return isAdmin(player) || unlocked(player).contains(effect.id());
    }

    private static boolean isAdmin(Player player) {
        return player.isOp() ||
                player.hasPermission("esnsmp.owner") ||
                player.hasPermission("esnsmp.admin") ||
                player.hasPermission("esnsmp.staff.admin") ||
                player.hasPermission("esnsmp.items");
    }

    private static Set<String> unlocked(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        String raw = pdc.getOrDefault(UNLOCKED, PersistentDataType.STRING, "");
        Set<String> result = new LinkedHashSet<>();
        if (raw.isBlank()) return result;
        Arrays.stream(raw.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(x -> !x.isBlank())
                .forEach(result::add);
        return result;
    }

    private static void saveUnlocked(Player player, Set<String> unlocked) {
        player.getPersistentDataContainer().set(UNLOCKED, PersistentDataType.STRING, String.join(",", unlocked));
    }

    private static Effect getEquipped(Player player) {
        String id = player.getPersistentDataContainer().get(EQUIPPED, PersistentDataType.STRING);
        return Effect.fromId(id);
    }

    private void play(Effect effect, Location center) {
        if (center.getWorld() == null) return;
        switch (effect) {
            case LIGHTNING -> lightning(center);
            case SOUL_EXPLOSION -> soulExplosion(center);
            case VOID_IMPLOSION -> voidImplosion(center);
            case METEOR_STRIKE -> meteorStrike(center);
            case WARDEN_SONIC -> wardenSonic(center);
            case FIRE_TORNADO -> fireTornado(center);
        }
    }

    private static void lightning(Location center) {
        World world = center.getWorld();
        if (world == null) return;
        world.strikeLightningEffect(center);
        world.spawnParticle(Particle.ELECTRIC_SPARK, center.clone().add(0, 1.0, 0),
                65, 0.9, 1.4, 0.9, 0.12);
    }

    private static void soulExplosion(Location center) {
        World world = center.getWorld();
        if (world == null) return;
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, center.clone().add(0, 0.8, 0),
                90, 1.4, 1.0, 1.4, 0.08);
        world.spawnParticle(Particle.SCULK_SOUL, center.clone().add(0, 1.1, 0),
                45, 1.0, 1.3, 1.0, 0.05);
    }

    private void voidImplosion(Location center) {
        World world = center.getWorld();
        if (world == null) return;
        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (tick >= 12) {
                    world.spawnParticle(Particle.SCULK_SOUL, center.clone().add(0, 0.8, 0),
                            55, 0.55, 0.7, 0.55, 0.04);
                    cancel();
                    return;
                }
                double radius = 3.0 - (tick * 0.22);
                for (int i = 0; i < 18; i++) {
                    double angle = (Math.PI * 2.0 * i / 18.0) + tick * 0.35;
                    Location point = center.clone().add(Math.cos(angle) * radius, 0.25 + tick * 0.05, Math.sin(angle) * radius);
                    world.spawnParticle(Particle.REVERSE_PORTAL, point, 2, 0.05, 0.05, 0.05, 0.01);
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void meteorStrike(Location center) {
        World world = center.getWorld();
        if (world == null) return;
        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (tick >= 13) {
                    world.spawnParticle(Particle.FLAME, center.clone().add(0, 0.4, 0),
                            100, 1.5, 0.7, 1.5, 0.12);
                    world.spawnParticle(Particle.LAVA, center.clone().add(0, 0.3, 0),
                            28, 1.1, 0.4, 1.1, 0.05);
                    cancel();
                    return;
                }
                double y = 7.0 - tick * 0.55;
                Location meteor = center.clone().add(0.7, y, -0.4);
                world.spawnParticle(Particle.FLAME, meteor, 20, 0.28, 0.28, 0.28, 0.03);
                world.spawnParticle(Particle.LAVA, meteor, 3, 0.18, 0.18, 0.18, 0.01);
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void wardenSonic(Location center) {
        World world = center.getWorld();
        if (world == null) return;
        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (tick >= 9) {
                    cancel();
                    return;
                }
                double radius = 0.5 + tick * 0.45;
                for (int i = 0; i < 28; i++) {
                    double angle = Math.PI * 2.0 * i / 28.0;
                    Location point = center.clone().add(Math.cos(angle) * radius, 0.8, Math.sin(angle) * radius);
                    world.spawnParticle(Particle.SCULK_SOUL, point, 1, 0.03, 0.03, 0.03, 0.0);
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void fireTornado(Location center) {
        World world = center.getWorld();
        if (world == null) return;
        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (tick >= 22) {
                    cancel();
                    return;
                }
                for (int i = 0; i < 4; i++) {
                    double y = i * 0.65;
                    double radius = 0.55 + i * 0.12;
                    double angle = tick * 0.62 + i * 1.55;
                    Location point = center.clone().add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
                    world.spawnParticle(Particle.FLAME, point, 6, 0.12, 0.15, 0.12, 0.02);
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
