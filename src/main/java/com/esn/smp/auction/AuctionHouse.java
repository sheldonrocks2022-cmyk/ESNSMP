package com.esn.smp.auction;

import com.esn.smp.data.ESNDataStore;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.SQLException;
import java.util.*;

public final class AuctionHouse implements CommandExecutor, Listener {
    private static final String TITLE = ChatColor.DARK_GRAY + "ESN Auction House";
    private final ESNDataStore data;

    public AuctionHouse(ESNDataStore data) { this.data = data; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
        try {
            if (args.length == 0 || args[0].equalsIgnoreCase("browse")) { open(p, 0); return true; }
            if (args[0].equalsIgnoreCase("sell")) {
                if (args.length < 2) { p.sendMessage(ChatColor.YELLOW + "/ah sell <price>"); return true; }
                long price;
                try { price = Long.parseLong(args[1]); } catch (NumberFormatException ex) { p.sendMessage(ChatColor.RED+"Price must be a whole number."); return true; }
                ItemStack held=p.getInventory().getItemInMainHand();
                if (held.getType().isAir()) { p.sendMessage(ChatColor.RED+"Hold the item you want to sell."); return true; }
                ItemStack listing=held.clone();
                ESNDataStore.CreateListingResult r=data.createListing(p,listing,price);
                if (!r.success()) { p.sendMessage(ChatColor.RED+r.message()); return true; }
                p.getInventory().setItemInMainHand(null);
                p.sendMessage(ChatColor.GREEN+"Listed for "+price+" ESN Coins. Listing #"+r.id());
                return true;
            }
            if (args[0].equalsIgnoreCase("claim")) { claim(p); return true; }
            if (args[0].equalsIgnoreCase("cancel") && args.length >= 2) {
                long id;
                try { id=Long.parseLong(args[1]); } catch(NumberFormatException ex){p.sendMessage(ChatColor.RED+"Invalid listing id.");return true;}
                ESNDataStore.CancelResult r=data.cancel(p,id);
                p.sendMessage((r.success()?ChatColor.GREEN:ChatColor.RED)+r.message());
                return true;
            }
            p.sendMessage(ChatColor.YELLOW+"/ah | /ah sell <price> | /ah cancel <id> | /ah claim");
        } catch (Exception ex) {
            p.sendMessage(ChatColor.RED+"Auction action failed safely. Nothing was intentionally lost.");
            Bukkit.getLogger().severe("[ESNSMP] Auction error: "+ex.getMessage());
        }
        return true;
    }

    public void open(Player p,int page) throws SQLException {
        int safe=Math.max(0,page), offset=safe*45;
        List<ESNDataStore.AuctionListing> rows=data.listAuctions(offset,45);
        Inventory inv=Bukkit.createInventory(null,54,TITLE+" | "+(safe+1));
        for(int i=0;i<rows.size();i++){
            ESNDataStore.AuctionListing a=rows.get(i);
            ItemStack item=a.item().clone();
            ItemMeta meta=item.getItemMeta();
            List<String> lore=meta.hasLore()?new ArrayList<>(Objects.requireNonNull(meta.getLore())):new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.GOLD+"Price: "+a.price()+" ESN Coins");
            lore.add(ChatColor.GRAY+"Seller: "+a.sellerName());
            lore.add(ChatColor.DARK_GRAY+"Listing #"+a.id());
            lore.add(ChatColor.GREEN+"Click to buy");
            meta.setLore(lore); item.setItemMeta(meta); inv.setItem(i,item);
        }
        ItemStack claim=button(Material.CHEST,ChatColor.AQUA+"Claim Items",List.of(ChatColor.GRAY+"Pending: "+data.countDeliveries(p.getUniqueId())));
        inv.setItem(49,claim);
        if(safe>0) inv.setItem(45,button(Material.ARROW,ChatColor.YELLOW+"Previous",List.of()));
        if(offset+rows.size()<data.countAuctions()) inv.setItem(53,button(Material.ARROW,ChatColor.YELLOW+"Next",List.of()));
        p.openInventory(inv);
    }

    @EventHandler public void click(InventoryClickEvent e){
        if(!e.getView().getTitle().startsWith(TITLE)) return;
        e.setCancelled(true);
        if(!(e.getWhoClicked() instanceof Player p)) return;
        int page=parsePage(e.getView().getTitle());
        int slot=e.getRawSlot();
        try{
            if(slot==49){claim(p);return;}
            if(slot==45){open(p,Math.max(0,page-1));return;}
            if(slot==53){open(p,page+1);return;}
            if(slot<0||slot>=45)return;
            List<ESNDataStore.AuctionListing> rows=data.listAuctions(page*45,45);
            if(slot>=rows.size())return;
            ESNDataStore.AuctionListing a=rows.get(slot);
            ESNDataStore.PurchaseResult r=data.purchase(p,a.id());
            p.sendMessage((r.success()?ChatColor.GREEN:ChatColor.RED)+r.message());
            open(p,page);
        }catch(Exception ex){
            p.sendMessage(ChatColor.RED+"Auction action failed safely.");
            Bukkit.getLogger().severe("[ESNSMP] Auction GUI error: "+ex.getMessage());
        }
    }

    private void claim(Player p)throws SQLException{
        List<ESNDataStore.Delivery> ds=data.getDeliveries(p.getUniqueId(),50);
        if(ds.isEmpty()){p.sendMessage(ChatColor.YELLOW+"You have no items to claim.");return;}
        int claimed=0;
        for(ESNDataStore.Delivery d:ds){
            HashMap<Integer,ItemStack> left=p.getInventory().addItem(d.item().clone());
            if(left.isEmpty()){data.deleteDelivery(p.getUniqueId(),d.id());claimed++;} else break;
        }
        p.sendMessage(ChatColor.GREEN+"Claimed "+claimed+" item(s)."+(claimed<ds.size()?ChatColor.YELLOW+" Make inventory space to claim the rest.":""));
    }

    private int parsePage(String t){try{return Math.max(0,Integer.parseInt(t.substring(t.lastIndexOf('|')+1).trim())-1);}catch(Exception e){return 0;}}
    private ItemStack button(Material m,String name,List<String> lore){ItemStack i=new ItemStack(m);ItemMeta im=i.getItemMeta();im.setDisplayName(name);im.setLore(lore);i.setItemMeta(im);return i;}
}
