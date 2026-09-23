package com.esn.smp.gameplay;
import org.bukkit.*;import org.bukkit.entity.Player;import org.bukkit.event.*;import org.bukkit.event.player.*;import org.bukkit.inventory.*;import org.bukkit.inventory.meta.BookMeta;import org.bukkit.persistence.PersistentDataType;import org.bukkit.plugin.java.JavaPlugin;import java.util.*;
public final class WelcomeGuide implements Listener{
 private final JavaPlugin p;private final NamespacedKey key;
 public WelcomeGuide(JavaPlugin p){this.p=p;key=new NamespacedKey(p,"welcome_guide");}
 public ItemStack book(){ItemStack b=new ItemStack(Material.WRITTEN_BOOK);BookMeta m=(BookMeta)b.getItemMeta();m.setTitle("ESN SMP Welcome Guide");m.setAuthor("ESN SMP");m.setGeneration(BookMeta.Generation.ORIGINAL);m.setPages(
 "§6§lWELCOME TO ESN SMP\n\n§0Your adventure starts here. This guide covers the current ESN progression systems.\n\n§2Java/Bedrock supported.",
 "§6§lSTART HERE\n\n§0/spawn - Spawn\n/menu - Main menu\n/journal - Adventure Journal\n/tutorial - Quick tutorial\n/profile - Profile\n/rpgprofile - RPG Profile",
 "§5§lREALMS 1-100\n\n§0/realm opens Realm progression and the §d100 Realm Exclusive rewards§0.\n\nUnlock a new Realm, then click its reward to claim its unique exclusive item.",
 "§d§lSEASON PASS\n\n§0/seasonpass opens the 100-tier Season Pass.\n\n§0Tier 1: §bAngel Wings §0with Unbreaking 200.\n\n§0Earn Season XP, unlock tiers and click rewards to claim them.",
 "§b§lACHIEVEMENT BOOK\n\n§0/achievements2 opens the clickable Achievement Book.\n\n§0Complete achievements to unlock exclusive items, ESN Coins and Realm crate keys.",
 "§a§lDISCOVERY & STORY\n\n§0/discoveries - World discoveries\n/story - NPC storyline\n/titlemenu - Titles\n/guildhq - Guild HQ\n\n§0Discover biomes and bosses to advance Realm progression.",
 "§2§lLAND & TRAVEL\n\n§0/claim - Protect land\n/sethome - Set home\n/home - Return home\n/homes - Homes\n/rtp - Random teleport\n/warps - Warps\n/tpa - Player teleport",
 "§a§lECONOMY\n\n§0/balance\n/pay <player> <amount>\n/shop\n/daily\n/ah\n/leaderboard\n\n§0Earn ESN Coins from gameplay and reward systems.",
 "§e§lSKILLS & JOBS\n\n§0/jobs - Jobs\n/skills - Skill XP\n/skilltree - RPG Skill Tree\n/prestige - Prestige\n/collections - Collections\n\n§0Skill Points and Skill XP are separate progression systems.",
 "§5§lCRATES & GEAR\n\n§0/crates - Crates\n/megacratekey - Admin key command\n/blacksmith\n/forge\n/runes\n/relics\n/salvage\n/reforge\n/gearupgrade",
 "§c§lBOSSES & ADVENTURE\n\n§0/boss\n/bosscodex\n/dungeon\n/raid\n/adventure\n/contracts2\n/events\n/calendar\n\n§0Bosses and adventures feed several progression systems.",
 "§9§lSOCIAL & SAFETY\n\n§0/guild\n/party\n/tradegui\n/report <player> <reason>\n\n§0Use claims and secure trading to protect your progress.",
 "§6§lESN COMMUNITY\n\n§0Discord:\n§9discord.gg/RPUpwYAQD8\n\n§0Important announcements and server updates are posted there.",
 "§6§lREADY?\n\n§0Start with §a/menu§0, §b/journal§0, §d/seasonpass§0 and §5/realm§0.\n\n§0Keep this book as your current ESN SMP reference."
);m.getPersistentDataContainer().set(key,PersistentDataType.BYTE,(byte)1);b.setItemMeta(m);return b;}
 private boolean has(Player x){for(ItemStack i:x.getInventory().getContents())if(i!=null&&i.hasItemMeta()&&i.getItemMeta().getPersistentDataContainer().has(key,PersistentDataType.BYTE))return true;return false;}
 @EventHandler public void join(PlayerJoinEvent e){Player x=e.getPlayer();if(!x.hasPlayedBefore()||!has(x)){Bukkit.getScheduler().runTaskLater(p,()->{if(!x.isOnline()||has(x))return;Map<Integer,ItemStack> left=x.getInventory().addItem(book());left.values().forEach(i->x.getWorld().dropItemNaturally(x.getLocation(),i));x.sendMessage("§6§lESN SMP §eWelcome Guide added to your inventory!");},20L);}}
}
