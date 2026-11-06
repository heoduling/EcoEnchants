package preview;

import com.google.gson.Gson;
import com.willfp.ecoenchants.EcoEnchantsPlugin;
import com.willfp.ecoenchants.mechanics.EnchantingTableSupport;
import com.willfp.ecoenchants.mechanics.AiyatsbusEnchantPoolKt;
import com.willfp.eco.core.items.Items;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.event.*;
import org.bukkit.event.enchantment.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import java.util.*;

/** Isolated test instrumentation. Never included in the delivered plugin. */
public final class PreviewProbe extends JavaPlugin implements Listener {
    final Gson gson = new Gson();
    final Map<UUID,org.bukkit.permissions.PermissionAttachment> permissions = new HashMap<>();
    boolean cancel;
    void log(String label,Object data) { getLogger().info("[PREVIEW] "+label+" "+gson.toJson(data)); }
    @Override public void onEnable() { getServer().getPluginManager().registerEvents(this,this); log("READY",Map.of("version",Bukkit.getVersion())); }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void cancelled(EnchantItemEvent e) { if(cancel) e.setCancelled(true); }
    @EventHandler
    public void quit(org.bukkit.event.player.PlayerQuitEvent e) { permissions.remove(e.getPlayer().getUniqueId()); }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        if(!(sender instanceof ConsoleCommandSender)||args.length<2) return true;
        Player p=Bukkit.getPlayerExact(args[1]); if(p==null) {log("ERROR","Missing player");return true;}
        p.getScheduler().execute(this,()->{
            try {
                switch(args[0]) {
                    case "setup" -> p.teleportAsync(new Location(p.getWorld(),8.5,101,11.5,180,0)).thenAccept(ok->{if(ok)p.getScheduler().execute(this,()->setup(p),null,1);});
                    case "matrix" -> matrix(p);
                    case "families" -> families(p);
                    case "boundaries" -> boundaries(p);
                    case "extra" -> {p.getInventory().setItem(4,new ItemStack(Material.SHIELD));log("EXTRA",state(p));}
                    case "state" -> log("STATE",state(p));
                    case "pool" -> log("POOL",AiyatsbusEnchantPoolKt.tableEnchantCandidates(p).stream().map(c->c.getEnchantment().getKey()+"/"+c.getRarity().getId()+"/"+c.getRarity().getWeight()+"/"+c.getDrawWeight()+"/"+c.getMaxLevel()).toList());
                    case "seed" -> {p.setEnchantmentSeed(Integer.parseInt(args[2]));log("SEED",state(p));}
                    case "xp" -> {p.setLevel(Integer.parseInt(args[2]));p.setExp(0);log("XP",state(p));}
                    case "cancel" -> {cancel=Boolean.parseBoolean(args[2]);log("CANCEL",cancel);}
                    case "items" -> items(p);
                    case "permission" -> {permissions.get(p.getUniqueId()).setPermission("ecoenchants.fromtable.*",Boolean.parseBoolean(args[2]));log("PERMISSION",args[2]);}
                    case "save" -> {p.saveData();log("SAVED",state(p));}
                    case "cow" -> {var cow=p.getWorld().spawn(new Location(p.getWorld(),8.5,101,12.5),org.bukkit.entity.Cow.class);cow.setAI(false);cow.setSilent(true);cow.setGravity(false);log("COW",cow.getEntityId());}
                    default -> throw new IllegalArgumentException(args[0]);
                }
            } catch(Throwable e) {getLogger().log(java.util.logging.Level.SEVERE,"[PREVIEW] FAIL "+args[0],e);}
        },null,1);
        return true;
    }
    void setup(Player p) {
        p.setGameMode(GameMode.SURVIVAL);p.setOp(false);p.setInvulnerable(true);
        var a=permissions.computeIfAbsent(p.getUniqueId(),id->p.addAttachment(this));a.setPermission("ecoenchants.fromtable.*",true);
        World w=p.getWorld();
        for(int x=4;x<=12;x++)for(int z=4;z<=12;z++)w.getBlockAt(x,100,z).setType(Material.STONE,false);
        for(int x=6;x<=10;x++)for(int z=6;z<=10;z++)if(Math.abs(x-8)==2||Math.abs(z-8)==2)w.getBlockAt(x,101,z).setType(Material.BOOKSHELF,false);
        w.getBlockAt(8,101,10).setType(Material.AIR,false);
        w.getBlockAt(8,101,8).setType(Material.ENCHANTING_TABLE,false);
        w.getBlockAt(10,101,11).setType(Material.ANVIL,false);
        p.setLevel(60);p.setExp(0);items(p);log("SETUP",state(p));
    }
    void items(Player p) {
        p.closeInventory();p.getInventory().clear();
        for(int i=0;i<5;i++)p.getInventory().setItem(i,new ItemStack(Material.DIAMOND_SWORD));
        p.getInventory().setItem(5,new ItemStack(Material.BOOK));
        p.getInventory().setItem(6,new ItemStack(Material.DIAMOND_PICKAXE));
        p.getInventory().setItem(7,new ItemStack(Material.BOW));
        p.getInventory().setItem(8,new ItemStack(Material.LAPIS_LAZULI,64));log("ITEMS",state(p));
    }
    List<String> sig(EnchantmentOffer[] offers) {
        List<String> out=new ArrayList<>();for(var o:offers)out.add(o==null?"null":o.getEnchantment().getKey()+"@"+o.getEnchantmentLevel());return out;
    }
    Map<String,Object> state(Player p) {
        Map<String,Object> out=new LinkedHashMap<>();out.put("player",p.getName());out.put("seed",p.getEnchantmentSeed());out.put("level",p.getLevel());out.put("op",p.isOp());
        var inv=p.getOpenInventory();out.put("window",inv.getType().name());
        List<Object> items=new ArrayList<>();for(var s:p.getInventory().getContents())if(s!=null&&!s.getType().isAir())items.add(Map.of("type",s.getType().name(),"amount",s.getAmount(),"data",s.serializeAsBytes().length,"enchants",s.getEnchantments().toString()));out.put("items",items);
        return out;
    }
    void matrix(Player p) {
        int originalSeed=p.getEnchantmentSeed();
        var view=p.openEnchanting(new Location(p.getWorld(),8,101,8),true);
        int checked=0,changed=0,mutated=0; List<Object> groups=new ArrayList<>();
        for(int seed:new int[]{123456789,-123456789,0}) {
            p.setEnchantmentSeed(seed);
            for(Material type:new Material[]{Material.DIAMOND_SWORD,Material.DIAMOND_PICKAXE,Material.BOW,Material.BOOK}) {
                ItemStack base=new ItemStack(type);
                var first=prepare(p,view,base,15);var stableSeed=p.getEnchantmentSeed();Set<List<String>> unique=new HashSet<>();unique.add(first);
                for(int i=0;i<80;i++) {
                    ItemStack variant=base.clone();var meta=variant.getItemMeta();
                    meta.displayName(Component.text("Screen_"+i).decoration(TextDecoration.ITALIC,false));
                    meta.lore(List.of(Component.text("Lore "+i)));meta.getPersistentDataContainer().set(new NamespacedKey(this,"probe"),PersistentDataType.INTEGER,i);
                    meta.setCustomModelData(i+1);if(meta instanceof Damageable d)d.setDamage(i+1);variant.setItemMeta(meta);
                    byte[] before=variant.serializeAsBytes();var result=prepare(p,view,variant,15);unique.add(result);checked++;
                    if(!first.equals(result))changed++;
                    if(!Arrays.equals(before,variant.serializeAsBytes()))mutated++;
                    if(p.getEnchantmentSeed()!=stableSeed)throw new AssertionError("Preview advanced player round");
                }
                groups.add(Map.of("seed",seed,"effectiveSeed",stableSeed,"material",type.name(),"unique",unique.size(),"first",first));
            }
        }
        p.closeInventory();p.setEnchantmentSeed(originalSeed);
        log("MATRIX",Map.of("checked",checked,"different",changed,"mutated",mutated,"groups",groups));
    }
    List<String> prepare(Player p,InventoryView view,ItemStack item,int bonus) {
        var offers=new EnchantmentOffer[3];
        var e=new PrepareItemEnchantEvent(p,(org.bukkit.inventory.view.EnchantmentView)view,p.getWorld().getBlockAt(8,101,8),item,offers,bonus);
        EnchantingTableSupport.INSTANCE.onPrepare(e);
        return sig(e.getOffers());
    }
    void families(Player p) {
        int seed=p.getEnchantmentSeed();p.setEnchantmentSeed(123456789);
        var view=p.openEnchanting(new Location(p.getWorld(),8,101,8),true);
        List<Object> result=new ArrayList<>();
        for(String family:List.of("sword","pickaxe","axe","shovel","hoe","spear","helmet","chestplate","leggings","boots")) {
            var tiers=List.of("sword","pickaxe","axe","shovel","hoe","spear").contains(family)?
                List.of("wooden","stone","copper","iron","golden","diamond","netherite"):
                List.of("leather","chainmail","copper","iron","golden","diamond","netherite");
            Set<List<String>> pools=new HashSet<>(),hints=new HashSet<>();List<Object> items=new ArrayList<>();
            for(String tier:tiers) {
                var type=Material.matchMaterial(tier+"_"+family);if(type==null)throw new AssertionError("Missing material "+tier+"_"+family);
                ItemStack item=new ItemStack(type);
                var pool=AiyatsbusEnchantPoolKt.tableEnchantCandidates(p).stream().filter(c->c.canEnchant(item,List.of())).map(c->c.getEnchantment().getKey().toString()).sorted().toList();
                var hint=prepare(p,view,item,15);pools.add(pool);hints.add(hint);items.add(Map.of("material",type.name(),"pool",pool,"hint",hint));
            }
            result.add(Map.of("family",family,"poolVariants",pools.size(),"previewVariants",hints.size(),"items",items));
        }
        p.closeInventory();p.setEnchantmentSeed(seed);log("FAMILIES",Map.of("seed",123456789,"groups",result));
    }
    void boundaries(Player p) {
        int saved=p.getEnchantmentSeed();p.setEnchantmentSeed(123456789);
        var view=p.openEnchanting(new Location(p.getWorld(),8,101,8),true);
        var sword=new ItemStack(Material.DIAMOND_SWORD);
        var low=prepare(p,view,sword,0);var high=prepare(p,view,sword,15);
        for(int i=0;i<3;i++)if(!low.get(i).split("@")[0].equals(high.get(i).split("@")[0]))throw new AssertionError("Bookshelves changed random draw sequence");
        if(low.equals(high))throw new AssertionError("Bookshelf level fixture ineffective");
        var attachment=permissions.get(p.getUniqueId());
        int before=AiyatsbusEnchantPoolKt.tableEnchantCandidates(p).size();
        for(var e:com.willfp.ecoenchants.enchant.EcoEnchants.INSTANCE.values())attachment.setPermission("ecoenchants.fromtable."+e.getId(),false);
        var denied=AiyatsbusEnchantPoolKt.tableEnchantCandidates(p);
        if(denied.stream().anyMatch(c->c.getEcoEnchant()!=null))throw new AssertionError("Denied custom enchant present");
        var deniedOffers=prepare(p,view,sword,15);
        for(var e:com.willfp.ecoenchants.enchant.EcoEnchants.INSTANCE.values())attachment.unsetPermission("ecoenchants.fromtable."+e.getId());
        var restored=prepare(p,view,sword,15);if(!high.equals(restored))throw new AssertionError("Permission restore changes round");
        var custom=Items.lookup("ecoitems:enderite_sword").getItem();
        var customBase=prepare(p,view,custom,15);byte[] original=custom.serializeAsBytes();
        var renamed=custom.clone();var meta=renamed.getItemMeta();meta.displayName(Component.text("CustomRename"));renamed.setItemMeta(meta);
        var customRenamed=prepare(p,view,renamed,15);if(!customBase.equals(customRenamed))throw new AssertionError("Custom item name rerolls");
        if(!Arrays.equals(original,custom.serializeAsBytes()))throw new AssertionError("Custom data mutated");
        var expected=prepare(p,view,sword,15);p.closeInventory();
        var start=p.getLocation();World other=Bukkit.getWorlds().stream().filter(w->w.getEnvironment()==World.Environment.NETHER).findFirst().orElseThrow();
        p.teleportAsync(new Location(other,8.5,110,11.5)).thenAccept(ok->{if(ok)p.getScheduler().execute(this,()->{
            try {
                var foreign=p.openEnchanting(p.getLocation(),true);var actual=prepare(p,foreign,sword,15);
                if(!expected.equals(actual))throw new AssertionError("World seed rerolls");
                p.closeInventory();p.setEnchantmentSeed(saved);
                log("BOUNDARIES",Map.of("bookshelfLow",low,"bookshelfHigh",high,"poolBefore",before,"poolDenied",denied.size(),"deniedOffers",deniedOffers,"customMaterial",custom.getType().name(),"customStable",customBase.equals(customRenamed),"crossWorldStable",expected.equals(actual)));
                p.teleportAsync(start);
            }catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"[PREVIEW] FAIL boundaries",e);}
        },null,1);});
    }
}
