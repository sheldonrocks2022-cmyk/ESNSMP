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
import com.esn.smp.gameplay.ESN20Core;
import com.esn.smp.gameplay.ESN20Expansion;
import com.esn.smp.gameplay.Fun20Core;
import com.esn.smp.gameplay.MythicCrates;
import com.esn.smp.gameplay.ExtendedCrates;
import com.esn.smp.gameplay.MegaCrates;
import com.esn.smp.listener.SpawnProtectionListener;
import com.esn.smp.spawn.HubServiceListener;
import com.esn.smp.spawn.SpawnManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class ESNSMPPlugin extends JavaPlugin {
    private SpawnManager spawnManager;
    private ESNDataStore dataStore;
    private DiscordReminder discordReminder;
    private V18Core v18Core;
    private V19Core v19Core;
    private ExpansionCore expansionCore;
    private ESN20Core esn20Core;
    private ESN20Expansion esn20Expansion;
    private Fun20Core fun20Core;

    @Override public void onEnable() {
        saveDefaultConfig();
        try {
            dataStore=new ESNDataStore(getDataFolder(),getConfig().getLong("economy.starting-balance",500),
                    getConfig().getInt("auction.max-listings-per-player",10),getConfig().getLong("auction.max-price",1000000000L));
            dataStore.initialize();
        } catch(Exception ex) {
            getLogger().severe("ESN database failed to initialize. Plugin disabled to protect player data: "+ex.getMessage());
            getServer().getPluginManager().disablePlugin(this); return;
        }

        spawnManager=new SpawnManager(this);
        AuctionHouse auctions=new AuctionHouse(dataStore);
        EconomyCommand economy=new EconomyCommand(dataStore);
        SMPGameplay gameplay=new SMPGameplay(dataStore);

        registerCommand("spawn",new SpawnCommand(spawnManager));
        registerCommand("setspawn",new SetSpawnCommand(spawnManager));
        registerCommand("esnspawn",new ESNSpawnCommand(spawnManager));
        registerCommand("ah",auctions);
        registerCommand("balance",economy);
        registerCommand("pay",economy);
        for(String name:new String[]{"menu","shop","crates","daily","quests","leaderboard"}) registerCommand(name,gameplay);
        HomesWarps homesWarps=new HomesWarps(this); for(String name:new String[]{"sethome","home","delhome","homes","rtp","warp","warps","setwarp","delwarp"}) registerCommand(name,homesWarps);
        registerCommand("profile",new ProfileCommand(dataStore)); new ESNScoreboard(this,dataStore);
        registerCommand("esnitems",new ESNItemsCommand());
        Teleports teleports=new Teleports(this); for(String name:new String[]{"tpa","tpahere","tpaccept","tpdeny","back","tptoggle"}) registerCommand(name,teleports); getServer().getPluginManager().registerEvents(teleports,this);
        try { v18Core=new V18Core(this,dataStore); for(String name:new String[]{"level","streak","team","bounty","trade","staff","invsee","ecsee","freeze","warn","mute","history"}) registerCommand(name,v18Core); getServer().getPluginManager().registerEvents(v18Core,this); } catch(Exception ex){ getLogger().severe("v1.8 optional systems failed to initialize; core will stay online: "+ex.getMessage()); }
        v19Core=new V19Core(this,dataStore); for(String name:new String[]{"pass","upgrade","tradegui","tutorial","grave"}) registerCommand(name,v19Core); getServer().getPluginManager().registerEvents(v19Core,this);
        try { expansionCore=new ExpansionCore(this,dataStore); for(String name:new String[]{"jobs","skills","prestige","titles","collections","stats","afk","calendar","blacksmith","salvage","reforge","boss","dungeon","raid","koth","report","note"}) registerCommand(name,expansionCore); getServer().getPluginManager().registerEvents(expansionCore,this); } catch(Exception ex){ getLogger().severe("Expansion systems failed safely: "+ex.getMessage()); }
        try { esn20Core=new ESN20Core(this,dataStore); for(String name:new String[]{"forge","bossdrops","contracts","relics","challenges","rewards"}) registerCommand(name,esn20Core); getServer().getPluginManager().registerEvents(esn20Core,this); } catch(Exception ex){ getLogger().severe("ESN 2.0 systems failed safely: "+ex.getMessage()); }
        try { esn20Expansion=new ESN20Expansion(this,dataStore); for(String name:new String[]{"season2","season","milestones","bestiary2","bestiary","achievements2","achievements","title","party2","party","events2","event","guide","leaderboards2","claimflags"}) registerCommand(name,esn20Expansion); getServer().getPluginManager().registerEvents(esn20Expansion,this); } catch(Exception ex){ getLogger().severe("ESN 2.0 expansion failed safely: "+ex.getMessage()); }
        fun20Core=new Fun20Core(this,dataStore); for(String name:new String[]{"chaos","artifacts","runes","ascend","community","supplydrop","merchant","endless","fishingevent","runeforge","setbonus"}) registerCommand(name,fun20Core); getServer().getPluginManager().registerEvents(fun20Core,this);
        Claims claims=new Claims(this); registerCommand("claim",claims); getServer().getPluginManager().registerEvents(claims,this); getServer().getPluginManager().registerEvents(new AntiCheat(),this);

        getServer().getPluginManager().registerEvents(new SpawnListener(this,spawnManager),this);
        getServer().getPluginManager().registerEvents(new SpawnProtectionListener(spawnManager),this);
        getServer().getPluginManager().registerEvents(auctions,this);
        getServer().getPluginManager().registerEvents(gameplay,this);
        getServer().getPluginManager().registerEvents(new LootDrops(),this);
        getServer().getPluginManager().registerEvents(new MythicCrates(this),this);
        getServer().getPluginManager().registerEvents(new ExtendedCrates(),this);
        getServer().getPluginManager().registerEvents(new MegaCrates(),this);
        getServer().getPluginManager().registerEvents(new WelcomeGuide(this),this);
        discordReminder=new DiscordReminder(this); getServer().getPluginManager().registerEvents(discordReminder,this);
        getServer().getPluginManager().registerEvents(new HubServiceListener(spawnManager,auctions),this);

        getServer().getScheduler().runTask(this,()->{
            try {
                spawnManager.ensureConfigured();
                if(getConfig().getBoolean("spawn.build-incomplete",false)){
                    getLogger().severe("Previous spawn build did not finish. Run /esnspawn rollback before rebuilding.");return;
                }
                if(getConfig().getBoolean("spawn.build-on-first-start",true)&&!getConfig().getBoolean("spawn.generated",false))
                    spawnManager.buildSpawn(getServer().getConsoleSender());
            } catch(Exception ex){getLogger().severe("Spawn initialization failed safely: "+ex.getMessage());}
        });
        getLogger().info("ESNSMP v"+getDescription().getVersion()+" enabled.");
    }

    @Override public void onDisable(){
        if(spawnManager!=null)spawnManager.shutdown();
        if(discordReminder!=null)discordReminder.shutdown();
        if(v18Core!=null)try{v18Core.close();}catch(Exception ex){getLogger().severe("v1.8 database close error: "+ex.getMessage());}
        if(expansionCore!=null)try{expansionCore.close();}catch(Exception ex){getLogger().severe("Expansion database close error: "+ex.getMessage());}
        if(esn20Core!=null)try{esn20Core.close();}catch(Exception ex){getLogger().severe("ESN 2.0 database close error: "+ex.getMessage());}
        if(esn20Expansion!=null)try{esn20Expansion.close();}catch(Exception ex){getLogger().severe("ESN 2.0 expansion database close error: "+ex.getMessage());}
        if(dataStore!=null)try{dataStore.close();}catch(Exception ex){getLogger().severe("Database close error: "+ex.getMessage());}
    }

    private void registerCommand(String name,org.bukkit.command.CommandExecutor executor){
        PluginCommand command=Objects.requireNonNull(getCommand(name),"Missing command in plugin.yml: "+name);
        command.setExecutor(executor);
    }
    public SpawnManager getSpawnManager(){return spawnManager;}
}
