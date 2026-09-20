package com.esn.smp.gameplay;
import org.bukkit.*;import org.bukkit.entity.Player;import org.bukkit.event.*;import org.bukkit.event.player.*;import org.bukkit.inventory.*;import org.bukkit.inventory.meta.BookMeta;import org.bukkit.persistence.PersistentDataType;import org.bukkit.plugin.java.JavaPlugin;import java.util.*;
public final class WelcomeGuide implements Listener{
 private final JavaPlugin p;private final NamespacedKey key;
 public WelcomeGuide(JavaPlugin p){this.p=p;key=new NamespacedKey(p,"welcome_guide");}
 public ItemStack book(){ItemStack b=new ItemStack(Material.WRITTEN_BOOK);BookMeta m=(BookMeta)b.getItemMeta();m.setTitle("ESN SMP Welcome Guide");m.setAuthor("ESN SMP");m.setGeneration(BookMeta.Generation.ORIGINAL);m.setPages(
 "§6§lWELCOME TO ESN SMP\n\n§0Your adventure starts here. Keep this guide for the main commands and systems.\n\n§2Java/Bedrock supported.",
 "§6§lGETTING STARTED\n\n§0/spawn - Spawn\n/menu - Main menu\n/tutorial - Quick tutorial\n/profile - Profile\n/stats - Career stats\n\n§0New players receive temporary PvP protection.",
 "§2§lLAND & HOMES\n\n§0/claim - Protect land\n/sethome - Set home\n/home - Return home\n/homes - Your homes\n/rtp - Random teleport\n/warps - Server warps",
 "§b§lPLAYERS\n\n§0/tpa <player>\n/tpahere <player>\n/tpaccept\n/tpdeny\n/back\n/tptoggle\n\n§0Bedrock names with a leading dot are supported.",
 "§a§lECONOMY\n\n§0/balance\n/pay <player> <amount>\n/shop\n/daily\n/ah\n/leaderboard\n\n§0Earn and spend ESN Coins throughout the SMP.",
 "§d§lPROGRESSION\n\n§0/quests\n/jobs\n/skills\n/level\n/achievements\n/streak\n/pass\n/prestige\n/collections\n/bestiary",
 "§5§lCRATES & GEAR\n\n§0/crates - View crates\n/upgrade - Upgrade gear\n/blacksmith\n/salvage\n/reforge\n\n§0Abyssal and Dragonlord crates require their own themed keys.",
 "§c§lBOSSES\n\n§0Regional bosses live in swamps. Only one can occupy a swamp sector at a time.\n\n§0World Bosses appear at world spawn every 15 minutes and must be defeated within 10 minutes.",
 "§4§lCOMBAT & EVENTS\n\n§0/dungeon\n/raid\n/koth\n/bounty\n/grave\n\n§0Death graves protect your dropped gear temporarily. Watch chat for server events.",
 "§9§lSOCIAL\n\n§0/team\n/party\n/trade\n/tradegui\n/report <player> <reason>\n\n§0Use the systems fairly and respect other players.",
 "§6§lESN COMMUNITY\n\n§0Discord:\n§9discord.gg/RPUpwYAQD8\n\n§0Important announcements, updates and community information are posted there.",
 "§6§lREADY?\n\n§0Use §a/menu §0to explore ESN SMP.\n\n§0Keep this book as your command reference. Have fun and good luck!"
 );m.getPersistentDataContainer().set(key,PersistentDataType.BYTE,(byte)1);b.setItemMeta(m);return b;}
 private boolean has(Player x){for(ItemStack i:x.getInventory().getContents())if(i!=null&&i.hasItemMeta()&&i.getItemMeta().getPersistentDataContainer().has(key,PersistentDataType.BYTE))return true;return false;}
 @EventHandler public void join(PlayerJoinEvent e){Player x=e.getPlayer();if(!x.hasPlayedBefore()||!has(x)){Bukkit.getScheduler().runTaskLater(p,()->{if(!x.isOnline()||has(x))return;Map<Integer,ItemStack> left=x.getInventory().addItem(book());left.values().forEach(i->x.getWorld().dropItemNaturally(x.getLocation(),i));x.sendMessage("§6§lESN SMP §eWelcome Guide added to your inventory!");},20L);}}
}
