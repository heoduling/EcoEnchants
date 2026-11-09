package optcompare;

import com.google.gson.Gson;
import com.willfp.eco.core.config.Configs;
import com.willfp.eco.core.display.*;
import com.willfp.ecoenchants.EcoEnchantsPlugin;
import com.willfp.ecoenchants.display.*;
import com.willfp.ecoenchants.enchant.*;
import com.willfp.ecoenchants.enchant.impl.EcoEnchantBase;
import com.willfp.ecoenchants.mechanics.*;
import com.willfp.libreforge.*;
import com.willfp.libreforge.conditions.*;
import com.willfp.libreforge.effects.*;
import com.sun.management.ThreadMXBean;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.view.EnchantmentView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import java.lang.management.ManagementFactory;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Supplier;

/** Isolated instrumentation. Every live Bukkit read/write runs on its player owner. */
public final class OptCompare extends JavaPlugin implements Listener {
    final List<String> checks = new ArrayList<>();
    final Map<String,Object> observations = new LinkedHashMap<>();
    final List<Object> batches = new ArrayList<>();
    final ArrayDeque<Runnable> jobs = new ArrayDeque<>();
    volatile Object sink;
    URLClassLoader loader;
    DisplayModule oldDisplay;
    Object oldTable;
    java.lang.reflect.Method oldPrepare;
    java.lang.reflect.Method newPrepare;
    EcoEnchantsPlugin product;
    List<EcoEnchant> custom;
    Player player;
    EnchantmentView view;
    boolean started;
    int comparisons;
    final Comparator<AiyatsbusEnchantCandidate> oldComparator = Comparator
        .comparing((AiyatsbusEnchantCandidate c)->c.getEnchantment().getKey().getNamespace())
        .thenComparing(c->c.getEnchantment().getKey().getKey());

    public static final class CountingStack extends ItemStack {
        int hashes;
        CountingStack(ItemStack source) { super(source); }
        @Override public int hashCode() { hashes++; return super.hashCode(); }
    }
    @Override public void onEnable() {
        try {
            product=(EcoEnchantsPlugin)Bukkit.getPluginManager().getPlugin("EcoEnchants");
            loader=new URLClassLoader(new URL[]{Path.of("../baseline.jar").toUri().toURL()},EnchantDisplay.class.getClassLoader()) {
                @Override protected Class<?> loadClass(String name,boolean resolve) throws ClassNotFoundException {
                    if (!(name.equals("com.willfp.ecoenchants.display.EnchantDisplay") ||
                          name.startsWith("com.willfp.ecoenchants.mechanics.EnchantingTableSupport") ||
                          name.startsWith("com.willfp.ecoenchants.mechanics.PreparedEnchant"))) return super.loadClass(name,resolve);
                    synchronized(getClassLoadingLock(name)) {
                        var found=findLoadedClass(name);
                        if(found==null)found=findClass(name);
                        if(resolve)resolveClass(found);
                        return found;
                    }
                }
            };
            oldDisplay=(DisplayModule)loader.loadClass(EnchantDisplay.class.getName()).getField("INSTANCE").get(null);
            var type=loader.loadClass(EnchantingTableSupport.class.getName());
            oldTable=type.getField("INSTANCE").get(null);
            oldPrepare=type.getMethod("onPrepare",PrepareItemEnchantEvent.class);
            newPrepare=EnchantingTableSupport.class.getMethod("onPrepare",PrepareItemEnchantEvent.class);
            custom=EcoEnchants.INSTANCE.values().stream().filter(EcoEnchant::isEnabled)
                .filter(e->!EnchantDisplayKt.hasDisplayConditions(e.getLevel(1)))
                .sorted(Comparator.comparing(EcoEnchant::getId)).limit(8).toList();
            check(custom.size()==8,"eight real enchants without display conditions");
            observations.put("displayEnchants",custom.stream().map(EcoEnchant::getId).toList());
            getServer().getPluginManager().registerEvents(this,this);
            getLogger().info("[OPT] READY");
        } catch(Throwable error) { fail(error); }
    }
    @EventHandler public void join(PlayerJoinEvent event) {
        if(started || !event.getPlayer().getName().equals("OptPerfA"))return;
        started=true;player=event.getPlayer();
        player.getScheduler().execute(this,()->{
            try { functional(); scheduleBench(); next(); } catch(Throwable error) { fail(error); }
        },null,60);
    }
    void check(boolean passed,String name) {
        if(!passed)throw new AssertionError(name);
        checks.add(name);
    }
    void clearDisplayCache() {
        DisplayCache.INSTANCE.getNameCache().invalidateAll();
        DisplayCache.INSTANCE.getDescriptionCache().invalidateAll();
    }
    ItemStack item(Material material,int count,boolean rich,boolean craft) {
        var stack=new ItemStack(material);
        var meta=stack.getItemMeta();
        if(rich) {
            var lore=new ArrayList<String>();
            for(int i=0;i<24;i++)lore.add("Unrelated source lore "+i);
            meta.setLore(lore);meta.setCustomModelData(117);
            for(int i=0;i<16;i++)meta.getPersistentDataContainer().set(new NamespacedKey(this,"payload"+i),PersistentDataType.STRING,"unchanged-payload-"+i);
        }
        for(int i=0;i<count;i++) {
            if(meta instanceof EnchantmentStorageMeta stored)stored.addStoredEnchant(custom.get(i).getEnchantment(),1,true);
            else meta.addEnchant(custom.get(i).getEnchantment(),1,true);
        }
        stack.setItemMeta(meta);
        return craft?CraftItemStack.asCraftCopy(stack):stack;
    }
    ItemStack display(DisplayModule module,ItemStack source,Player recipient,boolean hidden) {
        var copy=source.clone();
        module.display(copy,recipient,new DisplayProperties(true,false,source),hidden);
        return copy;
    }
    void compare(ItemStack source,Player recipient,boolean hidden,String label) {
        var bytes=source.serializeAsBytes();
        var old=display(oldDisplay,source,recipient,hidden);
        var current=display(EnchantDisplay.INSTANCE,source,recipient,hidden);
        check(old.equals(current),"display "+label);
        check(Arrays.equals(bytes,source.serializeAsBytes()),"source preserved "+label);
        oldDisplay.revert(old);EnchantDisplay.INSTANCE.revert(current);
        check(old.equals(current),"revert "+label);
        comparisons++;
    }
    void functional() throws Exception {
        check(Bukkit.isOwnedByCurrentRegion(player),"live state on player owner");
        player.setOp(true);player.setEnchantmentSeed(123456789);
        observations.put("owner",Map.of("thread",Thread.currentThread().getName(),"virtual",Thread.currentThread().isVirtual(),"primary",Bukkit.isPrimaryThread(),"allocation", "N/A on virtual owner thread"));
        String[] keys={"display.require-enchantable","display.collapse.enabled","display.descriptions.enabled","display.enchantments-below-lore","display.capacity.enabled"};
        var config=product.getConfigYml();var saved=Arrays.stream(keys).map(config::get).toArray();
        try {
            for(int mask=0;mask<16;mask++) {
                config.set(keys[0],(mask&1)!=0);config.set(keys[1],(mask&2)!=0);
                config.set(keys[2],(mask&4)!=0);config.set(keys[3],(mask&8)!=0);config.set(keys[4],(mask&1)==0);
                clearDisplayCache();
                for(var material:new Material[]{Material.DIAMOND_SWORD,Material.ENCHANTED_BOOK,Material.STONE})
                    for(int count:new int[]{0,1,4,8})for(boolean rich:new boolean[]{false,true})
                        for(boolean craft:new boolean[]{false,true})for(int mode=0;mode<3;mode++)
                            compare(item(material,count,rich,craft),mode==2?null:player,mode==1,"matrix "+mask+"/"+material+"/"+count+"/"+rich+"/"+craft+"/"+mode);
            }
        } finally {for(int i=0;i<keys.length;i++)config.set(keys[i],saved[i]);clearDisplayCache();}
        var hashes=new ArrayList<Object>();
        for(int count:new int[]{1,4,8}) {
            var source=item(Material.DIAMOND_SWORD,count,true,false);
            var old=new CountingStack(source);var current=new CountingStack(source);
            oldDisplay.display(old,player,new DisplayProperties(true,false,source),false);
            EnchantDisplay.INSTANCE.display(current,player,new DisplayProperties(true,false,source),false);
            check(old.hashes==count && current.hashes==0,"nonvisual hash counts "+count);
            hashes.add(Map.of("enchants",count,"old",old.hashes,"new",current.hashes));
        }
        observations.put("fullDisplayHashCalls",hashes);
        positiveConditions();
        var location=player.getLocation();location.getBlock().setType(Material.ENCHANTING_TABLE,false);
        view=(EnchantmentView)player.openEnchanting(location,true);
        check(view!=null,"real enchanting view");
        var candidates=AiyatsbusEnchantPoolKt.tableEnchantCandidates(player);
        sorting(candidates);
        int previewComparisons=0;
        var materials=new Material[]{Material.WOODEN_SWORD,Material.COPPER_SWORD,Material.GOLDEN_SWORD,Material.DIAMOND_SWORD,Material.NETHERITE_SWORD,Material.DIAMOND_PICKAXE,Material.DIAMOND_AXE,Material.COPPER_SPEAR,Material.BOW,Material.BOOK,Material.DIAMOND_HELMET,Material.TURTLE_HELMET,Material.SHIELD};
        for(int seed:new int[]{123456789,-123456789,Integer.MAX_VALUE})for(var material:materials)for(int bonus:new int[]{0,1,5,15,16,30}) {
            player.setEnchantmentSeed(seed);
            var source=new ItemStack(material);var before=source.serializeAsBytes();
            check(prepare(true,source,bonus).equals(prepare(false,source,bonus)),"three offers identical "+seed+"/"+material+"/"+bonus);
            check(player.getEnchantmentSeed()==seed,"prepare preserves nonzero seed");
            check(Arrays.equals(before,source.serializeAsBytes()),"prepare preserves source");previewComparisons++;
        }
        observations.put("previewComparisons",previewComparisons);
        observations.put("displayComparisons",comparisons);
    }
    @SuppressWarnings("unchecked") void positiveConditions() throws Exception {
        var sourceFixture=item(Material.DIAMOND_SWORD,1,true,false);
        var enchant=(EcoEnchant)com.willfp.eco.core.fast.FastItemStack.wrap(sourceFixture).getEnchants(true).keySet().iterator().next();
        var original=enchant.getLevel(1);
        var field=EcoEnchantBase.class.getDeclaredField("levels");field.setAccessible(true);
        var levels=(Map<Integer,EcoEnchantLevel>)field.get(original.getEnchant());
        var effect=EcoEnchants.INSTANCE.values().stream().map(e->e.getLevel(1)).flatMap(l->l.getEffects().stream()).findFirst().orElseThrow();
        double health=player.getHealth();var rows=new ArrayList<Object>();
        var oldFormat=product.getConfigYml().get("display.not-met.format");
        try {
            product.getConfigYml().set("display.not-met.format","OPT_UNMET ");
            clearDisplayCache();
            for(String kind:List.of("nonvisual","top-name","top-lines","effect-name","effect-lines")) {
                var condition=new Condition<Object>("opt_fixture") {
                    @Override public boolean isMet(Dispatcher<?> dispatcher,com.willfp.eco.core.config.interfaces.Config config,ProvidedHolder holder,Object data) {
                        return ((Player)dispatcher.getDispatcher()).getHealth()>=10;
                    }
                };
                var lines=kind.endsWith("lines")?List.of("OPT_REQUIRED_HEALTH"):List.<String>of();
                var block=new ConditionBlock<>(condition,Configs.empty(),null,null,lines,kind.endsWith("name"),false);
                var rules=new ConditionList(List.of(block));
                var nested=new EffectBlock(effect.getUuid(),effect.getConfig(),effect.getEffects(),effect.getTriggers(),effect.getArguments(),rules,effect.getMutators(),effect.getFilters(),effect.getShouldDelegateExecution());
                var level=new EcoEnchantLevel(enchant,1,new EffectList(kind.startsWith("effect")?List.of(nested):List.of()),kind.startsWith("effect")?new ConditionList(List.of()):rules);
                levels.put(1,level);
                check(enchant.getLevel(1)==level,"fixture level installed "+kind);
                check(enchant.getEnchantment() instanceof EcoEnchant,"actual enchant delegates level lookup");
                var states=new ArrayList<ItemStack>();
                for(double hp:new double[]{20,4,20}) {
                    player.setHealth(hp);
                    check(Bukkit.isPrimaryThread(),"condition predicate uses live primary-owner path");
                    var source=item(Material.DIAMOND_SWORD,1,true,false);
                    var rendered=(EcoEnchant)com.willfp.eco.core.fast.FastItemStack.wrap(source).getEnchants(true).keySet().iterator().next();
                    check(rendered.getLevel(1)==level,"rendered level fixture "+kind+"/"+rendered.getId());
                    compare(source,player,false,"condition "+kind+"/"+hp);
                    var counting=new CountingStack(source);
                    EnchantDisplay.INSTANCE.display(counting,player,new DisplayProperties(true,false,source),false);
                    check(counting.hashes==(kind.equals("nonvisual")?0:1),"visible condition still constructs holder "+kind+" actual="+counting.hashes+" guard="+EnchantDisplayKt.hasDisplayConditions(enchant.getLevel(1)));
                    states.add(counting);
                }
                check(states.get(0).equals(states.get(2)),"met state restores "+kind);
                check(kind.equals("nonvisual")==states.get(0).equals(states.get(1)),"positive marker sensitivity "+kind);
                rows.add(Map.of("kind",kind,"metAndUnmetDiffer",!states.get(0).equals(states.get(1))));
            }
        } finally {levels.put(1,original);player.setHealth(health);product.getConfigYml().set("display.not-met.format",oldFormat);clearDisplayCache();}
        observations.put("conditionFixtures",rows);
    }
    List<String> prepare(boolean original,ItemStack source,int bonus) {
        var event=new PrepareItemEnchantEvent(player,view,player.getLocation().getBlock(),source,new org.bukkit.enchantments.EnchantmentOffer[3],bonus);
        try {(original?oldPrepare:newPrepare).invoke(original?oldTable:EnchantingTableSupport.INSTANCE,event);}catch(Exception error){throw new RuntimeException(error);}
        return Arrays.stream(event.getOffers()).map(o->o==null?"null":o.getEnchantment().getKey()+"@"+o.getEnchantmentLevel()+"/"+o.getCost()).toList();
    }
    void sorting(List<AiyatsbusEnchantCandidate> candidates) {
        var rows=new ArrayList<Object>();
        for(var material:new Material[]{Material.DIAMOND_SWORD,Material.DIAMOND_PICKAXE,Material.ENCHANTED_BOOK}) {
            var source=new ItemStack(material);var pool=candidates.stream().filter(c->c.canEnchant(source,List.of())).toList();
            int[] reads={0};var old=new ArrayList<>(pool);
            old.sort(Comparator.comparing((AiyatsbusEnchantCandidate c)->{reads[0]++;return c.getEnchantment().getKey().getNamespace();})
                .thenComparing(c->{reads[0]++;return c.getEnchantment().getKey().getKey();}));
            check(old.equals(EnchantingTableSupportKt.sortedForEnchantingPreview(pool)),"actual compiled snapshot sort "+material);
            rows.add(Map.of("material",material.name(),"candidates",pool.size(),"oldGetKeyCalls",reads[0],"newGetKeyCalls",pool.size()));
        }
        var one=candidates.getFirst();
        var equal=new AiyatsbusEnchantCandidate(one.getEnchantment(),one.getEcoEnchant(),one.getRarity(),one.getDrawWeight());
        var tied=EnchantingTableSupportKt.sortedForEnchantingPreview(List.of(equal,one));
        check(tied.get(0)==equal&&tied.get(1)==one,"same-key stable identity order");
        check(EnchantingTableSupportKt.sortedForEnchantingPreview(List.of()).isEmpty(),"empty sort");
        check(EnchantingTableSupportKt.sortedForEnchantingPreview(List.of(one)).getFirst()==one,"singleton sort");
        observations.put("sorting",rows);
    }
    void scheduleBench() {
        // Both versions run on the same real player owner, alternating batch order.
        for(boolean craft:new boolean[]{false,true})for(int count:new int[]{1,4,8}) {
            var source=item(Material.DIAMOND_SWORD,count,true,craft);
            addScenario("display-rich-"+count+(craft?"-craft":"-bukkit"),200,()->display(oldDisplay,source,player,false),()->display(EnchantDisplay.INSTANCE,source,player,false));
        }
        for(var material:new Material[]{Material.DIAMOND_SWORD,Material.DIAMOND_PICKAXE,Material.BOOK}) {
            var source=new ItemStack(material);
            addScenario("prepare-"+material,100,()->prepare(true,source,15),()->prepare(false,source,15));
        }
    }
    void addScenario(String name,int calls,Supplier<Object> old,Supplier<Object> current) {
        for(int round=-8;round<12;round++) {
            final int r=round;
            jobs.add(()->{
                if((r&1)==0){timed(name,r,calls,"old",old);timed(name,r,calls,"new",current);}
                else {timed(name,r,calls,"new",current);timed(name,r,calls,"old",old);}
            });
        }
    }
    void timed(String name,int round,int calls,String version,Supplier<Object> operation) {
        long start=System.nanoTime();for(int i=0;i<calls;i++)sink=operation.get();long nanos=System.nanoTime()-start;
        if(round>=0)batches.add(Map.of("scenario",name,"version",version,"round",round,"calls",calls,"nsPerCall",(double)nanos/calls));
    }
    void next() {
        try {
            if(!jobs.isEmpty()) {jobs.removeFirst().run();player.getScheduler().execute(this,this::next,null,1);return;}
            player.closeInventory();
            var pool=List.copyOf(AiyatsbusEnchantPoolKt.tableEnchantCandidates(player));
            Thread.ofPlatform().name("opt-detached-sort-allocation").start(()->{
                try {allocation(pool);write();getLogger().info("[OPT] COMPLETE "+checks.size());}catch(Throwable error){fail(error);}
            });
        }catch(Throwable error){fail(error);}
    }
    void allocation(List<AiyatsbusEnchantCandidate> pool) {
        // Registry keys are immutable. This thread receives no player, world, or inventory.
        var bean=(ThreadMXBean)ManagementFactory.getThreadMXBean();
        if(!bean.isThreadAllocatedMemorySupported())throw new AssertionError("allocation counter unsupported");
        bean.setThreadAllocatedMemoryEnabled(true);long id=Thread.currentThread().threadId();
        check(!Thread.currentThread().isVirtual() && bean.getThreadAllocatedBytes(id)>=0,"valid platform allocation counter");
        var rows=new ArrayList<Object>();
        for(int round=-8;round<12;round++)for(int order=0;order<2;order++) {
            boolean old=((round+order)&1)==0;long before=bean.getThreadAllocatedBytes(id);long start=System.nanoTime();
            for(int i=0;i<1000;i++) {
                if(old){var sorted=new ArrayList<>(pool);sorted.sort(oldComparator);sink=sorted;}
                else sink=EnchantingTableSupportKt.sortedForEnchantingPreview(pool);
            }
            long nanos=System.nanoTime()-start,bytes=bean.getThreadAllocatedBytes(id)-before;
            if(round>=0)rows.add(Map.of("version",old?"old-equivalent":"new-helper","round",round,"calls",1000,"bytesPerCall",(double)bytes/1000,"nsPerCall",(double)nanos/1000));
        }
        observations.put("detachedSortingAllocation",Map.of("candidates",pool.size(),"scope","sorting-only, old equivalent two-selector comparator versus actual new helper; detached immutable registry keys", "batches",rows));
    }
    void write() throws Exception {
        Files.writeString(Path.of("../evidence/runtime-current.json"),new Gson().toJson(Map.of("checks",checks,"observations",observations,"batches",batches)));
    }
    void fail(Throwable error) {
        observations.put("failure",error.toString());getLogger().log(java.util.logging.Level.SEVERE,"[OPT] FAIL",error);
        try {write();}catch(Exception e){getLogger().severe(e.toString());}
    }
    @Override public void onDisable() {
        try {if(loader!=null)loader.close();}catch(Exception error){getLogger().warning(error.toString());}
    }
}
