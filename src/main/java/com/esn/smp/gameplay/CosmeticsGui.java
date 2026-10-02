package com.esn.smp.gameplay;

import com.esn.smp.ESNSMPPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Inventory GUI for the ESN Store cosmetics platform.
 * Keeps the existing command backend as the source of truth.
 */
@SuppressWarnings("deprecation")
public final class CosmeticsGui implements Listener {
    private static final String MAIN_TITLE = ChatColor.DARK_PURPLE + "ESN Cosmetics";
    private static final String CATEGORY_PREFIX = ChatColor.DARK_AQUA + "ESN Cosmetics • ";
    private static final String KILL_TITLE = ChatColor.DARK_RED + "ESN Kill Effects";
    private static final String ADMIN_TITLE = ChatColor.DARK_RED + "ESN Admin Exclusives";

    private static final NamespacedKey ACTION = new NamespacedKey("esnsmp", "cosmetics_gui_action");
    private static final NamespacedKey UNLOCKS = new NamespacedKey("esnsmp", "cosmetic_unlocks");
    private static final NamespacedKey TOKENS = new NamespacedKey("esnsmp", "cosmetic_tokens_balance");
    private static final NamespacedKey KILL_UNLOCKS = new NamespacedKey("esnsmp", "kill_effects_unlocked");
    private static final NamespacedKey KILL_EQUIPPED = new NamespacedKey("esnsmp", "kill_effect_equipped");

    private static CosmeticsGui instance;

    private record Option(String id, String name, Material material, int price, boolean hidden) {}
    private record CategoryInfo(String key, String name, Material material) {}
    private record KillInfo(String id, String name, Material material) {}

    private static final List<CategoryInfo> CATEGORIES = List.of(
            new CategoryInfo("aura", "Aura Packs", Material.AMETHYST_SHARD),
            new CategoryInfo("title", "Exclusive Titles", Material.NAME_TAG),
            new CategoryInfo("teleport", "Spawn + Teleport Effects", Material.ENDER_PEARL),
            new CategoryInfo("pet", "Companion Pets", Material.LEAD),
            new CategoryInfo("chatcolor", "Chat Colors", Material.CYAN_DYE),
            new CategoryInfo("bracket", "Title Brackets", Material.QUARTZ),
            new CategoryInfo("join", "Join Messages", Material.PAPER),
            new CategoryInfo("emoji", "Cosmetic Emojis", Material.GLOW_INK_SAC),
            new CategoryInfo("weapon", "Weapon Cosmetic Packs", Material.NETHERITE_SWORD),
            new CategoryInfo("armor", "Armor Cosmetic Sets", Material.NETHERITE_CHESTPLATE),
            new CategoryInfo("supporter", "Supporter Ranks", Material.NETHER_STAR),
            new CategoryInfo("collection", "Collections", Material.HEART_OF_THE_SEA)
    );

    private static final Map<String, List<Option>> OPTIONS = new LinkedHashMap<>();

    private static final List<KillInfo> KILLS = List.of(
            new KillInfo("lightning", "Lightning Strike", Material.LIGHTNING_ROD),
            new KillInfo("soul", "Soul Explosion", Material.SOUL_LANTERN),
            new KillInfo("void", "Void Implosion", Material.ECHO_SHARD),
            new KillInfo("meteor", "Meteor Strike", Material.FIRE_CHARGE),
            new KillInfo("warden", "Warden Sonic Burst", Material.SCULK_CATALYST),
            new KillInfo("firetornado", "Fire Tornado", Material.MAGMA_CREAM)
    );

    private static final List<String> ADMIN_EXCLUSIVE_IDS = List.of(
            "angelwings", "infernoscepter", "stormcrystal", "tideheart", "voidrelic", "celestialstar",
            "riftblade", "riftwings", "phaseboots", "riftbow", "riftcore", "voidcompass",
            "wardenhelmet", "wardenchest", "wardenlegs", "wardenboots", "wardenblade", "wardenbow", "immortalcore", "wardentotem",
            "voidwarriorcrown", "voidwarriorchest", "voidwarriorlegs", "voidwarriorboots", "voidwarriorblade",
            "lightningkingcrown", "lightningkingchest", "lightningkinglegs", "lightningkingboots",
            "lightningkingblade", "lightningkingbow", "lightningkingcore"
    );

    static {
        OPTIONS.put("aura", new ArrayList<>(List.of(
                o("void", "Void Aura", Material.ECHO_SHARD, 50),
                o("warden", "Warden Souls", Material.SCULK_CATALYST, 50),
                o("inferno", "Inferno Flames", Material.BLAZE_POWDER, 50),
                o("lightning", "Lightning Aura", Material.LIGHTNING_ROD, 50),
                o("celestial", "Celestial Stars", Material.NETHER_STAR, 50),
                o("frost", "Frost Aura", Material.BLUE_ICE, 50),
                o("dragon", "Dragon Aura", Material.DRAGON_BREATH, 50)
        )));
        OPTIONS.put("title", new ArrayList<>(List.of(
                o("voidwalker", "VOIDWALKER", Material.ECHO_SHARD, 40),
                o("immortal", "IMMORTAL", Material.TOTEM_OF_UNDYING, 40),
                o("celestial", "CELESTIAL", Material.NETHER_STAR, 40),
                o("og", "OG", Material.CLOCK, 40),
                o("bountyhunter", "BOUNTY HUNTER", Material.TARGET, 40),
                o("bossslayer", "BOSS SLAYER", Material.NETHERITE_SWORD, 40),
                o("supporter", "ESN SUPPORTER", Material.EMERALD, 40)
        )));
        OPTIONS.put("teleport", new ArrayList<>(List.of(
                o("portal", "Portal Arrival", Material.ENDER_PEARL, 35),
                o("lightning", "Lightning Arrival", Material.LIGHTNING_ROD, 35),
                o("void", "Void Arrival", Material.ECHO_SHARD, 35),
                o("celestial", "Celestial Beam", Material.END_ROD, 35),
                o("warden", "Warden Darkness Burst", Material.SCULK_SHRIEKER, 35)
        )));
        OPTIONS.put("pet", new ArrayList<>(List.of(
                o("miniwarden", "Mini Warden", Material.SCULK_CATALYST, 75),
                o("voidspirit", "Void Spirit", Material.ECHO_SHARD, 75),
                o("infernospirit", "Inferno Spirit", Material.BLAZE_POWDER, 75),
                o("celestialspirit", "Celestial Spirit", Material.NETHER_STAR, 75),
                o("babydragon", "Baby Dragon", Material.DRAGON_HEAD, 75)
        )));
        OPTIONS.put("chatcolor", new ArrayList<>(List.of(
                o("aqua", "Aqua Name", Material.CYAN_DYE, 30),
                o("purple", "Purple Name", Material.PURPLE_DYE, 30),
                o("gold", "Gold Name", Material.ORANGE_DYE, 30),
                o("green", "Green Name", Material.LIME_DYE, 30),
                o("red", "Red Name", Material.RED_DYE, 30),
                o("rainbow", "Rainbow Name", Material.NETHER_STAR, 60)
        )));
        OPTIONS.put("bracket", new ArrayList<>(List.of(
                o("square", "Square Brackets", Material.IRON_NUGGET, 20),
                o("angle", "Angle Brackets", Material.QUARTZ, 20),
                o("stars", "Star Brackets", Material.AMETHYST_SHARD, 20),
                o("void", "Void Brackets", Material.ECHO_SHARD, 20)
        )));
        OPTIONS.put("join", new ArrayList<>(List.of(
                o("standard", "Supporter Join", Material.PAPER, 25),
                o("celestial", "Celestial Join", Material.END_ROD, 25),
                o("void", "Void Join", Material.ECHO_SHARD, 25),
                o("legend", "Legend Join", Material.NETHER_STAR, 25)
        )));
        OPTIONS.put("emoji", new ArrayList<>(List.of(
                o("pack", "ESN Cosmetic Emoji Pack", Material.NAME_TAG, 40)
        )));
        OPTIONS.put("weapon", new ArrayList<>(List.of(
                o("riftedge", "Rift Edge", Material.NETHERITE_SWORD, 80),
                o("wardenecho", "Warden Echo", Material.NETHERITE_SWORD, 80),
                o("celestialsaber", "Celestial Saber", Material.NETHERITE_SWORD, 80),
                o("infernofang", "Inferno Fang", Material.NETHERITE_SWORD, 80),
                o("frostbite", "Frostbite", Material.NETHERITE_SWORD, 80)
        )));
        OPTIONS.put("armor", new ArrayList<>());
        OPTIONS.put("supporter", new ArrayList<>(List.of(
                o("supporter", "SUPPORTER", Material.EMERALD, 0),
                o("elite", "ELITE", Material.DIAMOND, 0),
                o("legend", "LEGEND", Material.NETHER_STAR, 0)
        )));
        OPTIONS.put("collection", new ArrayList<>(List.of(
                o("halloween", "Halloween Collection", Material.CARVED_PUMPKIN, 200),
                o("christmas", "Christmas Collection", Material.SNOW_BLOCK, 200),
                o("newyear", "New Year Collection", Material.FIREWORK_ROCKET, 200),
                o("anniversary", "ESN Anniversary Collection", Material.CLOCK, 200),
                o("summer", "Summer Collection", Material.SUNFLOWER, 200),
                o("birthday", "ESN Birthday Collection", Material.CAKE, 200),
                o("abyssreaper", "Abyss Reaper Collection", Material.ECHO_SHARD, 200),
                o("dragonlord", "Dragonlord Collection", Material.DRAGON_HEAD, 200),
                o("frostborn", "Frostborn Collection", Material.BLUE_ICE, 200),
                o("solarguardian", "Solar Guardian Collection", Material.GOLDEN_HELMET, 200),
                o("bloodmoon", "Bloodmoon Collection", Material.REDSTONE_BLOCK, 200),
                o("celestialknight", "Celestial Knight Collection", Material.NETHER_STAR, 200),
                o("ancienttitan", "Ancient Titan Collection", Material.NETHERITE_BLOCK, 200)
        )));

        for (String id : List.of("halloween","christmas","newyear","anniversary","summer","birthday",
                "abyssreaper","dragonlord","frostborn","solarguardian","bloodmoon","celestialknight","ancienttitan")) {
            Material auraMaterial = switch (id) {
                case "dragonlord" -> Material.DRAGON_BREATH;
                case "frostborn" -> Material.BLUE_ICE;
                case "solarguardian" -> Material.SUNFLOWER;
                case "bloodmoon" -> Material.REDSTONE;
                case "celestialknight" -> Material.NETHER_STAR;
                case "ancienttitan" -> Material.NETHERITE_BLOCK;
                default -> Material.ECHO_SHARD;
            };
            OPTIONS.get("aura").add(hidden(id, pretty(id) + " Aura", auraMaterial));
            OPTIONS.get("title").add(hidden(id, pretty(id).toUpperCase(Locale.ROOT), Material.NAME_TAG));
            OPTIONS.get("weapon").add(hidden(id, pretty(id) + " Weapon Skin", Material.NETHERITE_SWORD));
            OPTIONS.get("armor").add(hidden(id, pretty(id) + " Armor Set", Material.NETHERITE_CHESTPLATE));
        }
    }

    private final ESNSMPPlugin plugin;

    public CosmeticsGui(ESNSMPPlugin plugin) {
        this.plugin = plugin;
        instance = this;
    }

    public static void open(Player player) {
        if (instance != null) instance.openMain(player);
    }

    public static void openKillEffects(Player player) {
        if (instance != null) instance.openKillMenu(player);
    }

    private static Option o(String id, String name, Material material, int price) {
        return new Option(id, name, material, price, false);
    }

    private static Option hidden(String id, String name, Material material) {
        return new Option(id, name, material, 0, true);
    }

    private void openMain(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, MAIN_TITLE);
        inv.setItem(4, item(Material.SUNFLOWER, ChatColor.GOLD + "" + ChatColor.BOLD + "ESN Cosmetic Tokens",
                List.of(ChatColor.YELLOW + "Balance: " + tokenBalance(player), ChatColor.GRAY + "/cosmetictokens"), "tokens"));

        int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23};
        for (int i = 0; i < CATEGORIES.size(); i++) {
            CategoryInfo category = CATEGORIES.get(i);
            long owned = OPTIONS.getOrDefault(category.key(), List.of()).stream().filter(o -> owns(player, category.key(), o.id())).count();
            inv.setItem(slots[i], item(category.material(), ChatColor.LIGHT_PURPLE + category.name(),
                    List.of(
                            ChatColor.GRAY + "Owned: " + ChatColor.WHITE + owned,
                            ChatColor.GRAY + "Click to browse and equip.",
                            releasedCategory(category.key()) ? ChatColor.GREEN + "Public release enabled" : ChatColor.DARK_GRAY + "Staged release"
                    ), "category:" + category.key()));
        }

        inv.setItem(28, item(Material.LIGHTNING_ROD, ChatColor.RED + "Kill Effects",
                List.of(ChatColor.GRAY + "Lightning, Soul, Void, Meteor,", ChatColor.GRAY + "Warden and Fire Tornado.", ChatColor.YELLOW + "Click to open"), "killmenu"));
        inv.setItem(29, item(Material.TOTEM_OF_UNDYING, ChatColor.GOLD + "Season Pass",
                List.of(ChatColor.GRAY + "Open the ESN Season Pass menu."), "command:seasonpass"));
        inv.setItem(30, item(Material.BUNDLE, ChatColor.DARK_AQUA + "Backpack",
                List.of(ChatColor.GRAY + "10 pages • 450 persistent slots"), "command:backpack"));
        if (isAdmin(player)) {
            inv.setItem(32, item(Material.NETHERITE_BLOCK, ChatColor.DARK_RED + "" + ChatColor.BOLD + "ADMIN EXCLUSIVES",
                    List.of(ChatColor.GOLD + "Automatic access to every cosmetic exclusive.", ChatColor.GRAY + "Click for exclusive gear sets."), "adminexclusives"));
        }
        inv.setItem(49, item(Material.BARRIER, ChatColor.RED + "Close", List.of(), "close"));
        player.openInventory(inv);
    }

    private void openCategory(Player player, String category) {
        List<Option> options = OPTIONS.get(category);
        if (options == null) {
            player.sendMessage(ChatColor.RED + "Unknown cosmetics category.");
            return;
        }
        CategoryInfo info = CATEGORIES.stream().filter(c -> c.key().equals(category)).findFirst().orElse(null);
        Inventory inv = Bukkit.createInventory(null, 54, CATEGORY_PREFIX + category);
        inv.setItem(4, item(Material.SUNFLOWER, ChatColor.GOLD + "Cosmetic Tokens: " + tokenBalance(player),
                List.of(ChatColor.GRAY + "Use tokens on released cosmetics."), "tokens"));

        int slot = 9;
        for (Option option : options) {
            boolean owned = owns(player, category, option.id());
            if (option.hidden() && !owned && !isAdmin(player)) continue;
            if (slot >= 45) break;

            boolean selected = option.id().equals(selected(player, category));
            boolean released = !option.hidden() && released(category, option.id());
            List<String> lore = new ArrayList<>();
            if (isAdmin(player)) lore.add(ChatColor.DARK_RED + "ADMIN ACCESS • ALL EXCLUSIVES");
            else if (owned) lore.add(ChatColor.GREEN + "OWNED");
            else if (released && option.price() > 0) lore.add(ChatColor.AQUA + "Released • " + option.price() + " tokens");
            else lore.add(ChatColor.DARK_GRAY + "STAGED / LOCKED");

            if (selected) lore.add(ChatColor.GOLD + "" + ChatColor.BOLD + "CURRENTLY EQUIPPED");
            if ("weapon".equals(category)) {
                lore.add(ChatColor.GRAY + "Hold a weapon and click to apply.");
                lore.add(ChatColor.GOLD + "Realm 100+ • below ESN exclusives");
            } else if ("armor".equals(category)) {
                lore.add(ChatColor.GRAY + "Click to equip the full 4-piece set.");
                lore.add(ChatColor.GOLD + "225 enchants + 12% full-set defense");
                lore.add(ChatColor.DARK_GRAY + "Above Realm 100 • below ESN exclusives");
            } else if ("collection".equals(category)) {
                lore.add(ChatColor.GRAY + "Unlocks matching aura, title, weapon + armor set.");
            } else lore.add(ChatColor.GRAY + (owned || isAdmin(player) ? "Click to equip." : released ? "Click to buy." : "Not publicly released yet."));

            inv.setItem(slot++, item(option.material(),
                    (selected ? ChatColor.GOLD : owned || isAdmin(player) ? ChatColor.GREEN : ChatColor.GRAY) + option.name(),
                    lore, "cosmetic:" + category + ":" + option.id()));
        }

        inv.setItem(45, item(Material.ARROW, ChatColor.YELLOW + "Back", List.of(ChatColor.GRAY + "Return to cosmetics"), "back"));
        if (!"collection".equals(category)) {
            inv.setItem(49, item(Material.BARRIER, ChatColor.RED + "Disable " + (info == null ? category : info.name()),
                    List.of(ChatColor.GRAY + "Turn off the equipped cosmetic in this category."), "off:" + category));
        }
        inv.setItem(53, item(Material.OAK_DOOR, ChatColor.RED + "Close", List.of(), "close"));
        player.openInventory(inv);
    }

    private void openKillMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, KILL_TITLE);
        String equipped = player.getPersistentDataContainer().get(KILL_EQUIPPED, PersistentDataType.STRING);
        int[] slots = {10,11,12,14,15,16};
        for (int i = 0; i < KILLS.size(); i++) {
            KillInfo effect = KILLS.get(i);
            boolean owned = ownsKill(player, effect.id());
            boolean active = effect.id().equals(equipped);
            List<String> lore = new ArrayList<>();
            lore.add(isAdmin(player) ? ChatColor.DARK_RED + "ADMIN ACCESS • ALL KILL EFFECTS" : owned ? ChatColor.GREEN + "OWNED" : ChatColor.DARK_GRAY + "LOCKED");
            if (active) lore.add(ChatColor.GOLD + "" + ChatColor.BOLD + "CURRENTLY EQUIPPED");
            lore.add(ChatColor.GRAY + "Left-click: equip");
            lore.add(ChatColor.GRAY + "Right-click: preview");
            inv.setItem(slots[i], item(effect.material(), (active ? ChatColor.GOLD : owned ? ChatColor.GREEN : ChatColor.GRAY) + effect.name(),
                    lore, "kill:" + effect.id()));
        }
        inv.setItem(18, item(Material.ARROW, ChatColor.YELLOW + "Back", List.of(), "back"));
        inv.setItem(22, item(Material.BARRIER, ChatColor.RED + "Disable Kill Effect", List.of(), "killoff"));
        inv.setItem(26, item(Material.OAK_DOOR, ChatColor.RED + "Close", List.of(), "close"));
        player.openInventory(inv);
    }

    private void openAdminExclusives(Player player) {
        if (!isAdmin(player)) {
            player.sendMessage(ChatColor.RED + "Admin/owner only.");
            return;
        }
        Inventory inv = Bukkit.createInventory(null, 54, ADMIN_TITLE);
        int slot = 0;
        for (String id : ADMIN_EXCLUSIVE_IDS) {
            ItemStack base = ESNItemsCommand.createStoreItem(id);
            if (base == null || slot >= 45) continue;
            ItemStack show = base.clone();
            show.setAmount(1);
            ItemMeta meta = show.getItemMeta();
            List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.DARK_RED + "" + ChatColor.BOLD + "ADMIN EXCLUSIVE ACCESS");
            lore.add(ChatColor.GRAY + "Click to receive one.");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(ACTION, PersistentDataType.STRING, "give:" + id);
            show.setItemMeta(meta);
            inv.setItem(slot++, show);
        }
        inv.setItem(45, item(Material.ARROW, ChatColor.YELLOW + "Back", List.of(), "back"));
        inv.setItem(49, item(Material.NETHER_STAR, ChatColor.GOLD + "All Cosmetics",
                List.of(ChatColor.GRAY + "Admins automatically have access without redeeming tokens."), "back"));
        inv.setItem(53, item(Material.BARRIER, ChatColor.RED + "Close", List.of(), "close"));
        player.openInventory(inv);
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!isOurMenu(title)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir() || !clicked.hasItemMeta()) return;
        String action = clicked.getItemMeta().getPersistentDataContainer().get(ACTION, PersistentDataType.STRING);
        if (action == null) return;

        if ("close".equals(action)) {
            player.closeInventory();
            return;
        }
        if ("back".equals(action)) {
            openMain(player);
            return;
        }
        if ("tokens".equals(action)) {
            player.sendMessage(ChatColor.GOLD + "ESN Cosmetic Tokens: " + ChatColor.WHITE + tokenBalance(player));
            return;
        }
        if (action.startsWith("command:")) {
            player.closeInventory();
            Bukkit.dispatchCommand(player, action.substring("command:".length()));
            return;
        }
        if ("killmenu".equals(action)) {
            openKillMenu(player);
            return;
        }
        if ("killoff".equals(action)) {
            Bukkit.dispatchCommand(player, "killeffects off");
            openKillMenu(player);
            return;
        }
        if ("adminexclusives".equals(action)) {
            openAdminExclusives(player);
            return;
        }
        if (action.startsWith("give:")) {
            if (!isAdmin(player)) return;
            String id = action.substring("give:".length());
            ItemStack reward = ESNItemsCommand.createStoreItem(id);
            if (reward != null) {
                player.getInventory().addItem(reward).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
                player.sendMessage(ChatColor.GREEN + "Admin exclusive added: " + id);
            }
            return;
        }
        if (action.startsWith("category:")) {
            openCategory(player, action.substring("category:".length()));
            return;
        }
        if (action.startsWith("off:")) {
            String category = action.substring("off:".length());
            Bukkit.dispatchCommand(player, "cosmetics " + category + " off");
            openCategory(player, category);
            return;
        }
        if (action.startsWith("kill:")) {
            String id = action.substring("kill:".length());
            if (!ownsKill(player, id)) {
                player.sendMessage(ChatColor.RED + "You have not unlocked that kill effect.");
                return;
            }
            Bukkit.dispatchCommand(player, "killeffects " + (event.isRightClick() ? "preview " : "equip ") + id);
            if (!event.isRightClick()) openKillMenu(player);
            return;
        }
        if (action.startsWith("cosmetic:")) {
            String[] parts = action.split(":", 3);
            if (parts.length != 3) return;
            String category = parts[1];
            String id = parts[2];
            Option option = OPTIONS.getOrDefault(category, List.of()).stream().filter(o -> o.id().equals(id)).findFirst().orElse(null);
            if (option == null) return;

            if (owns(player, category, id)) {
                Bukkit.dispatchCommand(player, "cosmetics " + category + " " + id);
                if (!"collection".equals(category)) Bukkit.getScheduler().runTask(plugin, () -> openCategory(player, category));
                return;
            }
            if (!option.hidden() && released(category, id) && option.price() > 0) {
                Bukkit.dispatchCommand(player, "cosmetics buy " + category + " " + id);
                Bukkit.getScheduler().runTask(plugin, () -> openCategory(player, category));
                return;
            }
            player.sendMessage(ChatColor.YELLOW + "That cosmetic is staged and not publicly released yet.");
        }
    }

    @EventHandler
    public void drag(InventoryDragEvent event) {
        if (isOurMenu(event.getView().getTitle())) event.setCancelled(true);
    }

    private boolean isOurMenu(String title) {
        return title.equals(MAIN_TITLE) || title.startsWith(CATEGORY_PREFIX) || title.equals(KILL_TITLE) || title.equals(ADMIN_TITLE);
    }

    private ItemStack item(Material material, String name, List<String> lore, String action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(ACTION, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private boolean releasedCategory(String category) {
        return plugin.getConfig().getBoolean("store-cosmetics.released." + category, false);
    }

    private boolean released(String category, String id) {
        return releasedCategory(category) ||
                plugin.getConfig().getBoolean("store-cosmetics.released-items." + category + "." + id, false);
    }

    private static long tokenBalance(Player player) {
        return Math.max(0L, player.getPersistentDataContainer().getOrDefault(TOKENS, PersistentDataType.LONG, 0L));
    }

    private static boolean owns(Player player, String category, String id) {
        if (isAdmin(player)) return true;
        String raw = player.getPersistentDataContainer().getOrDefault(UNLOCKS, PersistentDataType.STRING, "");
        if (raw.isBlank()) return false;
        Set<String> owned = new java.util.LinkedHashSet<>();
        Arrays.stream(raw.split(";")).map(String::trim).filter(s -> !s.isBlank()).forEach(owned::add);
        if (owned.contains(category + ":" + id)) return true;
        return "armor".equals(category) && owned.contains("collection:" + id);
    }

    private static boolean ownsKill(Player player, String id) {
        if (isAdmin(player)) return true;
        String raw = player.getPersistentDataContainer().getOrDefault(KILL_UNLOCKS, PersistentDataType.STRING, "");
        if (raw.isBlank()) return false;
        return Arrays.stream(raw.split(",")).map(String::trim).anyMatch(id::equalsIgnoreCase);
    }

    private static String selected(Player player, String category) {
        return player.getPersistentDataContainer().get(new NamespacedKey("esnsmp", "cosmetic_selected_" + category), PersistentDataType.STRING);
    }

    private static boolean isAdmin(Player player) {
        return player.isOp() ||
                player.hasPermission("esnsmp.owner") ||
                player.hasPermission("esnsmp.admin") ||
                player.hasPermission("esnsmp.staff.admin") ||
                player.hasPermission("esnsmp.items");
    }

    private static String pretty(String id) {
        return switch (id) {
            case "newyear" -> "New Year";
            case "abyssreaper" -> "Abyss Reaper";
            case "dragonlord" -> "Dragonlord";
            case "frostborn" -> "Frostborn";
            case "solarguardian" -> "Solar Guardian";
            case "bloodmoon" -> "Bloodmoon";
            case "celestialknight" -> "Celestial Knight";
            case "ancienttitan" -> "Ancient Titan";
            case "anniversary" -> "ESN Anniversary";
            case "birthday" -> "ESN Birthday";
            default -> Character.toUpperCase(id.charAt(0)) + id.substring(1);
        };
    }
}
