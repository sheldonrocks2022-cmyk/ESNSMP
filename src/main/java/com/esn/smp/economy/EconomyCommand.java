package com.esn.smp.economy;

import com.esn.smp.data.ESNDataStore;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public final class EconomyCommand implements CommandExecutor {
    private final ESNDataStore data;
    public EconomyCommand(ESNDataStore data){this.data=data;}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!(sender instanceof Player p)){sender.sendMessage("Players only.");return true;}
        try{
            if(command.getName().equalsIgnoreCase("balance")){
                p.sendMessage(ChatColor.GOLD+"Balance: "+data.getBalance(p)+" ESN Coins");return true;
            }
            if(args.length<2){p.sendMessage(ChatColor.YELLOW+"/pay <player> <amount>");return true;}
            Player target=Bukkit.getPlayerExact(args[0]);
            if(target==null||target.equals(p)){p.sendMessage(ChatColor.RED+"Choose another online player.");return true;}
            long amount;try{amount=Long.parseLong(args[1]);}catch(NumberFormatException ex){p.sendMessage(ChatColor.RED+"Amount must be a whole number.");return true;}
            if(amount<=0){p.sendMessage(ChatColor.RED+"Amount must be positive.");return true;}
            if(!data.transfer(p,target,amount)){p.sendMessage(ChatColor.RED+"Not enough ESN Coins.");return true;}
            p.sendMessage(ChatColor.GREEN+"Paid "+target.getName()+" "+amount+" ESN Coins.");
            target.sendMessage(ChatColor.GREEN+p.getName()+" paid you "+amount+" ESN Coins.");
        }catch(Exception ex){p.sendMessage(ChatColor.RED+"Economy action failed safely.");Bukkit.getLogger().severe("[ESNSMP] Economy error: "+ex.getMessage());}
        return true;
    }
}
