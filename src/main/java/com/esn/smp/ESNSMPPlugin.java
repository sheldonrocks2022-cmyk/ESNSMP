package com.esn.smp;

import com.esn.smp.auction.AuctionHouse;
import com.esn.smp.command.ESNSpawnCommand;
import com.esn.smp.command.SetSpawnCommand;
import com.esn.smp.command.SpawnCommand;
import com.esn.smp.data.ESNDataStore;
import com.esn.smp.economy.EconomyCommand;
import com.esn.smp.listener.SpawnListener;
import com.esn.smp.gameplay.SMPGameplay;
import com.esn.smp.gameplay.ESNItemsCommand;
import com.esn.smp.gameplay.Claims;
import com.esn.smp.gameplay.AntiCheat;
import com.esn.smp.gameplay.Teleports;
import com.esn.smp.gameplay.LootDrops;
import com.esn.smp.gameplay.DiscordReminder;
import com.esn.smp.gameplay.HomesWarps;
import com.esn.smp.gameplay.ProfileCommand;
import com.esn.smp.gameplay.ESNScoreboard;
import com.esn.smp.gameplay.V18Core;
import com.esn.smp.gameplay.V19Core;
import com.esn.smp.gameplay.ExpansionCore;
import com.esn.smp.gameplay.WelcomeGuide;
import com.esn.smp.gameplay.RamDiagnostics;
import com.esn.smp.gameplay.ESN20Core;
import com.esn.smp.gameplay.ESN20Expansion;
import com.esn.smp.gameplay.Fun20Core;
import com.esn.smp.gameplay.MythicCrates;
import com.esn.smp.gameplay.ExtendedCrates;
import com.esn.smp.gameplay.MegaCrates;
import com.esn.smp.gameplay.BiomeBosses;
import com.esn.smp.gameplay.AdventureSystems;
import com.esn.smp.gameplay.EndgameSystems;
import com.esn.smp.gameplay.BossHealthBars;
import com.esn.smp.gameplay.RPGOverhaul;
import com.esn.smp.gameplay.RiftwalkerBundle;
import com.esn.smp.gameplay.ImmortalWardenBundle;
import com.esn.smp.gameplay.LowMemoryEffectsTicker;
import com.esn.smp.gameplay.ESNAdventureEngine;
import com.esn.smp.gameplay.AdminControlCenter;
import com.esn.smp.gameplay.ServerMenus;
import com.esn.smp.gameplay.JailSystem;
import com.esn.smp.gameplay.AdventureJournal;
import com.esn.smp.gameplay.ESNChatSystem;
import com.esn.smp.gameplay.ESNWorldProgression;
import com.esn.smp.gameplay.ServerChampionship;
import com.esn.smp.gameplay.CommunitySystems;
import com.esn.smp.listener.SpawnProtectionListener;
import com.esn.smp.listener.SpawnMobGuard;
import com.esn.smp.spawn.HubServiceListener;
import com.esn.smp.spawn.SpawnManager;
import com.esn.smp.store.StripeStoreBridge;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Objects;

public final class ESNSMPPlugin extends JavaPlugin {
    // Build verification marker: combined Castle/NPC/Profile/Mod batch.
 private SpawnManager spawnManager;private ESNDataStore dataStore;private ESNWorldProgression worldProgression;private DiscordReminder discordReminder;private V18Core v18Core;private V19Core v19Core;private ExpansionCore expansionCore;private ESN20Core esn20Core;private ESN20Expansion esn20Expansion;private Fun20Core fun20Core;private RPGOverhaul rpgOverhaul;private ESNAdventureEngine adventureEngine;private StripeStoreBridge stripeStoreBridge;
 @Override public void onEnable(){StripeStoreBridge.migrateLegacyStripeKey(getDataFolder());StripeStoreBridge.removeLegacyStripeConfigBlock(new java.io.File(getDataFolder(),"config.yml"));saveDefaultConfig();try{dataStore=new ESNDataStore(getDataFolder(),getConfig().getLong("economy.starting-balance",500),getConfig().getInt("auction.max-listings-per-player",10),getConfig().getLong("auction.max-price",1000000000L));dataStore.initialize();}catch(Exception ex){getLogger().severe("ESN database failed to initialize. Plugin disabled to protect player data: "+ex.getMessage());getServer().getPluginManager().disablePlugin(this);return;}
  for(String cmdName:getDescription().getCommands().keySet()){PluginCommand pc=getCommand(cmdName);if(pc!=null)pc.setExecutor((sender,command,label,args)->{sender.sendMessage(org.bukkit.ChatColor.RED+"ESN system \""+command.getName()+"\" did not initialize. Check the server console for the subsystem error.");getLogger().warning("[ESNSMP] Fallback executor reached for /"+command.getName()+" by "+sender.getName());return true;});}
  spawnManager=new SpawnManager(this);ServerChampionship championship=new ServerChampionship(this);registerCommand("championship",championship);getServer().getPluginManager().registerEvents(championship,this);AuctionHouse auctions=new AuctionHouse(dataStore);EconomyCommand economy=new EconomyCommand(dataStore);SMPGameplay gameplay=new SMPGameplay(dataStore);
  registerCommand("spawn",new SpawnCommand(spawnManager));registerCommand("setspawn",new SetSpawnCommand(spawnManager));registerCommand("esnspawn",new ESNSpawnCommand(spawnManager));registerCommand("ah",auctions);registerCommand("balance",economy);registerCommand("pay",economy);
  for(String name:new String[]{"shop","crates","daily","quests","leaderboard"})registerCommand(name,gameplay);getServer().getPluginManager().registerEvents(gameplay,this);ServerMenus serverMenus=new ServerMenus(this);registerCommand("menu",serverMenus);getServer().getPluginManager().registerEvents(serverMenus,this);AdventureJournal journal=new AdventureJournal(this);registerCommand("journal",journal);getServer().getPluginManager().registerEvents(journal,this);
  HomesWarps homesWarps=new HomesWarps(this);for(String name:new String[]{"sethome","home","delhome","homes","rtp","warp","warps","setwarp","delwarp"})registerCommand(name,homesWarps);
  ProfileCommand profiles=new ProfileCommand(dataStore);registerCommand("profile",profiles);getServer().getPluginManager().registerEvents(profiles,this);new ESNScoreboard(this,dataStore);registerCommand("esnitems",new ESNItemsCommand());RiftwalkerBundle riftwalkerBundle=new RiftwalkerBundle(this);ImmortalWardenBundle immortalWardenBundle=new ImmortalWardenBundle(this);getServer().getPluginManager().registerEvents(riftwalkerBundle,this);getServer().getPluginManager().registerEvents(immortalWardenBundle,this);new LowMemoryEffectsTicker(this,riftwalkerBundle,immortalWardenBundle);registerCommand("esnram",new RamDiagnostics(this));
  Teleports teleports=new Teleports(this);for(String name:new String[]{"tpa","tpahere","tpaccept","tpdeny","back","tptoggle","tpmenu"})registerCommand(name,teleports);getServer().getPluginManager().registerEvents(teleports,this);
  try{v18Core=new V18Core(this,dataStore);for(String name:new String[]{"level","streak","team","bounty","outlaws","trade","staff","invsee","ecsee","freeze","warn","mute","history"})registerCommand(name,v18Core);getServer().getPluginManager().registerEvents(v18Core,this);}catch(Exception ex){getLogger().severe("v1.8 optional systems failed to initialize; core will stay online: "+ex.getMessage());}
  AdminControlCenter adminCenter=new AdminControlCenter(this,gameplay,dataStore);registerCommand("admin",adminCenter);registerCommand("modmenu",adminCenter);getServer().getPluginManager().registerEvents(adminCenter,this);
  JailSystem jail=new JailSystem(this);registerCommand("jail",jail);getServer().getPluginManager().registerEvents(jail,this);CommunitySystems communitySystems=new CommunitySystems(this,dataStore);for(String name:new String[]{"stall","blackmarket","backpack","bunker"})registerCommand(name,communitySystems);getServer().getPluginManager().registerEvents(communitySystems,this);
  v19Core=new V19Core(this,dataStore);for(String name:new String[]{"pass","upgrade","tradegui","tutorial","grave"})registerCommand(name,v19Core);getServer().getPluginManager().registerEvents(v19Core,this);
  try{expansionCore=new ExpansionCore(this,dataStore);for(String name:new String[]{"jobs","skills","prestige","titles","collections","stats","afk","calendar","blacksmith","salvage","reforge","boss","dungeon","raid","koth","report","note"})registerCommand(name,expansionCore);getServer().getPluginManager().registerEvents(expansionCore,this);}catch(Exception ex){getLogger().severe("Expansion systems failed safely: "+ex.getMessage());}
  try{esn20Core=new ESN20Core(this,dataStore);for(String name:new String[]{"forge","bossdrops","contracts","relics","challenges","rewards"})registerCommand(name,esn20Core);getServer().getPluginManager().registerEvents(esn20Core,this);}catch(Exception ex){getLogger().severe("ESN 2.0 systems failed safely: "+ex.getMessage());}
  try{esn20Expansion=new ESN20Expansion(this,dataStore);for(String name:new String[]{"season2","season","milestones","bestiary2","bestiary","achievements2","achievements","title","party2","party","events2","event","guide","leaderboards2","claimflags"})registerCommand(name,esn20Expansion);getServer().getPluginManager().registerEvents(esn20Expansion,this);}catch(Exception ex){getLogger().severe("ESN 2.0 expansion failed safely: "+ex.getMessage());}
  ESNChatSystem chat=new ESNChatSystem(this,v18Core,esn20Expansion);for(String name:new String[]{"msg","reply","staffchat","globalchat","localchat","chattoggle","chatmenu"})registerCommand(name,chat);getServer().getPluginManager().registerEvents(chat,this);
  try{worldProgression=new ESNWorldProgression(this,v18Core,esn20Expansion);for(String name:new String[]{"realm","story","discoveries","guildhq","titlemenu"})registerCommand(name,worldProgression);getServer().getPluginManager().registerEvents(worldProgression,this);}catch(Exception ex){getLogger().severe("World progression failed safely: "+ex.getMessage());}
  fun20Core=new Fun20Core(this,dataStore);for(String name:new String[]{"chaos","artifacts","runes","ascend","community","supplydrop","merchant","endless","fishingevent","runeforge","setbonus"})registerCommand(name,fun20Core);getServer().getPluginManager().registerEvents(fun20Core,this);
  Claims claims=new Claims(this);registerCommand("claim",claims);getServer().getPluginManager().registerEvents(claims,this);getServer().getPluginManager().registerEvents(new AntiCheat(),this);getServer().getPluginManager().registerEvents(new SpawnListener(this,spawnManager),this);getServer().getPluginManager().registerEvents(new SpawnProtectionListener(spawnManager),this);getServer().getPluginManager().registerEvents(new SpawnMobGuard(this),this);getServer().getPluginManager().registerEvents(auctions,this);getServer().getPluginManager().registerEvents(new LootDrops(),this);getServer().getPluginManager().registerEvents(new MythicCrates(this),this);getServer().getPluginManager().registerEvents(new ExtendedCrates(),this);
  MegaCrates megaCrates=new MegaCrates();registerCommand("megacratekey",megaCrates);getServer().getPluginManager().registerEvents(megaCrates,this);getServer().getPluginManager().registerEvents(new BiomeBosses(this),this);AdventureSystems adventure=new AdventureSystems(this);for(String name:new String[]{"mastery","codex","pets","treasure","progression","diagnostics"})registerCommand(name,adventure);getServer().getPluginManager().registerEvents(adventure,this);getServer().getPluginManager().registerEvents(new EndgameSystems(this),this);getServer().getPluginManager().registerEvents(new BossHealthBars(this),this);
  try{rpgOverhaul=new RPGOverhaul(this,dataStore);for(String name:new String[]{"bosscodex","skilltree","contracts2","gearupgrade","seasonpass","rpgprofile","rotation","bosssummon","bossdiag"})registerCommand(name,rpgOverhaul);getServer().getPluginManager().registerEvents(rpgOverhaul,this);}catch(Exception ex){getLogger().severe("RPG overhaul failed safely: "+ex.getMessage());}
  try{adventureEngine=new ESNAdventureEngine(this,dataStore);for(String name:new String[]{"adventure","class","ultimate","rift","bossrush","records","guild","trophies","bountyboard","hunt","artifactfusion","adventureachievements","revive"})registerCommand(name,adventureEngine);getServer().getPluginManager().registerEvents(adventureEngine,this);}catch(Exception ex){getLogger().severe("Adventure engine failed safely: "+ex.getMessage());}
  try{stripeStoreBridge=new StripeStoreBridge(this);registerCommand("storeclaim",stripeStoreBridge);registerCommand("storestatus",stripeStoreBridge);getServer().getPluginManager().registerEvents(stripeStoreBridge,this);stripeStoreBridge.start();}catch(Exception ex){getLogger().severe("Stripe store bridge failed safely; SMP will stay online: "+ex.getMessage());}
  getServer().getPluginManager().registerEvents(new WelcomeGuide(this),this);discordReminder=new DiscordReminder(this);getServer().getPluginManager().registerEvents(discordReminder,this);HubServiceListener hubServices=new HubServiceListener(spawnManager,auctions,gameplay,serverMenus);spawnManager.setHubServices(hubServices);getServer().getPluginManager().registerEvents(hubServices,this);
  getServer().getScheduler().runTask(this,()->{try{spawnManager.ensureConfigured();org.bukkit.Location esnSpawn=spawnManager.getSpawn();if(esnSpawn!=null&&esnSpawn.getWorld()!=null)esnSpawn.getWorld().setSpawnLocation(esnSpawn.getBlockX(),esnSpawn.getBlockY(),esnSpawn.getBlockZ());hubServices.ensureNpcs();if(getConfig().getBoolean("spawn.build-incomplete",false)){getLogger().severe("Previous spawn build did not finish. Run /esnspawn rollback before rebuilding.");return;}if(getConfig().getBoolean("spawn.build-on-first-start",true)&&!getConfig().getBoolean("spawn.generated",false))spawnManager.buildSpawn(getServer().getConsoleSender());}catch(Exception ex){getLogger().severe("Spawn initialization failed safely: "+ex.getMessage());}});
  int declared=getDescription().getCommands().size(),available=0;for(String cmdName:getDescription().getCommands().keySet())if(getCommand(cmdName)!=null)available++;getLogger().info("[ESNSMP] Command audit: "+available+"/"+declared+" declared commands available.");getLogger().info("ESNSMP v"+getDescription().getVersion()+" enabled.");
 }
 @Override public void onDisable(){if(worldProgression!=null)try{worldProgression.close();}catch(Exception ex){getLogger().severe("World progression database close error: "+ex.getMessage());}if(spawnManager!=null)spawnManager.shutdown();if(discordReminder!=null)discordReminder.shutdown();if(v18Core!=null)try{v18Core.close();}catch(Exception ex){getLogger().severe("v1.8 database close error: "+ex.getMessage());}if(expansionCore!=null)try{expansionCore.close();}catch(Exception ex){getLogger().severe("Expansion database close error: "+ex.getMessage());}if(esn20Core!=null)try{esn20Core.close();}catch(Exception ex){getLogger().severe("ESN 2.0 database close error: "+ex.getMessage());}if(esn20Expansion!=null)try{esn20Expansion.close();}catch(Exception ex){getLogger().severe("ESN 2.0 expansion database close error: "+ex.getMessage());}if(rpgOverhaul!=null)try{rpgOverhaul.close();}catch(Exception ex){getLogger().severe("RPG database close error: "+ex.getMessage());}if(adventureEngine!=null)try{adventureEngine.close();}catch(Exception ex){getLogger().severe("Adventure database close error: "+ex.getMessage());}if(stripeStoreBridge!=null)try{stripeStoreBridge.close();}catch(Exception ex){getLogger().severe("Stripe store bridge close error: "+ex.getMessage());}if(dataStore!=null)try{dataStore.close();}catch(Exception ex){getLogger().severe("Database close error: "+ex.getMessage());}}
 private void registerCommand(String name,org.bukkit.command.CommandExecutor executor){PluginCommand command=Objects.requireNonNull(getCommand(name),"Missing command in plugin.yml: "+name);command.setExecutor(executor);}public SpawnManager getSpawnManager(){return spawnManager;} public ESNDataStore getDataStore(){return dataStore;}
}
