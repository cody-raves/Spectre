package net.spectre.client.map;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import it.unimi.dsi.fastutil.longs.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.spectre.client.builder.BuilderMaterials;
import net.spectre.client.module.impl.render.SusChunkFinderModule;
import net.spectre.client.module.setting.*;
import net.spectre.client.util.ActivityClues;
import net.spectre.client.util.LevelUtil;
import net.spectre.client.util.SuspectObjects;
import net.spectre.client.util.SuspectObjects.Category;

/** Opt-in real integrated-server packets; all fixtures live in a new disposable world. */
public final class SusChunkSmokeClient implements ClientModInitializer {
    private final SusChunkFinderModule module=new SusChunkFinderModule();
    private final List<String> report=new ArrayList<>();
    private final List<Step> steps=new ArrayList<>();
    private int phase,ticks,index,failures;
    private boolean cameraTest;
    private Vec3 lockedPosition,lockedLook;
    private CompletableFuture<?> operation;
    private final long started=System.nanoTime();
    private record Step(String name,Runnable action,Runnable check,int delay){}
    private static final long NATURAL=ChunkPos.pack(0,0),EVIDENCE=ChunkPos.pack(1,0),CLASSIC=ChunkPos.pack(0,2);
    public void onInitializeClient(){ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
    private void require(boolean condition,String why){if(!condition)throw new AssertionError(why);}
    @SuppressWarnings("unchecked") private <T>T field(String name){try{var f=SusChunkFinderModule.class.getDeclaredField(name);f.setAccessible(true);return (T)f.get(module);}catch(Exception e){throw new RuntimeException(e);}}
    private Long2IntOpenHashMap values(String name){return field(name);}
    private Map<Long,ActivityClues.Summary> reports(){return field("activityReports");}
    private int score(long key){var s=reports().get(key);return s==null?-1:s.score();}
    private Setting setting(String id){return module.settings().stream().filter(s->s.id().equals(id)).findFirst().orElseThrow();}
    private void mode(String value){((ModeSetting)setting("method")).set(value);}
    private void bool(String id,boolean value){((BooleanSetting)setting(id)).set(value);}
    private void number(String id,int value){((NumberSetting)setting(id)).set(value);}
    private void step(String name,Runnable action,Runnable check){steps.add(new Step(name,action,check,35));}
    private void server(Consumer<ServerLevel> work){var mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();var id=mc.player.getUUID();operation=server.submit(()->{work.accept(server.getPlayerList().getPlayer(id).level());return true;});}
    private void teleport(double x,double y,double z){if(cameraTest){lockedPosition=new Vec3(x,y,z);lockedLook=null;}var mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();var id=mc.player.getUUID();operation=server.submit(()->{server.getPlayerList().getPlayer(id).teleportTo(x,y,z);return true;});}
    private void put(ServerLevel level,int x,int y,int z,String state){level.setBlock(new BlockPos(x,y,z),BuilderMaterials.state(state),2);}
    private void capture(String name){try{AtlasSmokeClient.captureBack(Minecraft.getInstance(),name);}catch(Exception e){throw new RuntimeException(e);}}
    private void fixtures(ServerLevel level){
        level.getGameRules().set(GameRules.RANDOM_TICK_SPEED,0,level.getServer());
        for(int x=-16;x<48;x++)for(int z=-16;z<48;z++)level.setBlock(new BlockPos(x,63,z),Blocks.STONE.defaultBlockState(),2);
        // Contain the water so scheduled fluid ticks cannot wash away other fixtures.
        for(int x:new int[]{2,18})for(int y=64;y<=65;y++)for(var d:Direction.Plane.HORIZONTAL)
            level.setBlock(new BlockPos(x, y,4).relative(d),Blocks.GLASS.defaultBlockState(),2);
        put(level,-8,-1,0,"minecraft:stone");put(level,-8,0,0,"minecraft:torch");
        // Physically supported natural-generation examples. Growth is frozen for deterministic assertions.
        put(level,2,64,4,"minecraft:water");put(level,2,65,4,"minecraft:water");put(level,2,64,4,"minecraft:kelp[age=23]");
        put(level,4,65,4,"minecraft:stone");put(level,4,64,4,"minecraft:cave_vines[age=25,berries=true]");
        put(level,6,64,5,"minecraft:stone");put(level,6,64,4,"minecraft:vine[south=true]");
        put(level,8,64,4,"minecraft:amethyst_cluster[facing=up]");
        put(level,10,63,4,"minecraft:dirt");put(level,10,64,4,"minecraft:bamboo[age=1,stage=1]");
        put(level,12,64,5,"minecraft:jungle_log");put(level,12,64,4,"minecraft:cocoa[age=2,facing=south]");
        put(level,14,64,4,"minecraft:bee_nest[honey_level=0]");
        put(level,18,64,4,"minecraft:water");put(level,18,65,4,"minecraft:water");put(level,18,64,4,"minecraft:kelp[age=25]");
        put(level,20,64,4,"minecraft:bee_nest[honey_level=5]");put(level,22,64,4,"minecraft:beehive");
    }
    private void plan(Minecraft mc){
        step("Natural examples: all seven categories present, zero evidence points, hidden by default",()->{mode("Activity clues");module.setEnabled(true);},()->{
            require(score(NATURAL)==0,"Natural score "+score(NATURAL));
            for(var kind:ActivityClues.Kind.values())require(reports().get(NATURAL).count(kind)>0,"Missing fixture "+kind);
            require(!values("visible").containsKey(NATURAL),"Natural chunk falsely flagged");
            require(score(EVIDENCE)==6&&values("visible").get(EVIDENCE)==6,"Expected 6 bee evidence points, got "+score(EVIDENCE));
            require(!reports().containsKey(ChunkPos.pack(-1,0)),"Empty control chunk scored");capture("sus-chunk-activity.png");
        });
        step("Evidence threshold hides 6-point chunk at 8",()->number("clue-threshold",8),()->require(!values("visible").containsKey(EVIDENCE),"Threshold ignored"));
        step("Natural-context option exposes zero-point natural chunk",()->{number("clue-threshold",2);bool("natural-context",true);},()->{require(values("visible").containsKey(NATURAL),"Natural context missing");capture("sus-chunk-natural.png");});
        step("Disabling bees rescans evidence to zero points for a short kelp tip",()->{bool("natural-context",false);bool("clue-bees",false);},()->{require(score(EVIDENCE)==0,"Bee toggle stale: "+score(EVIDENCE));require(!values("visible").containsKey(EVIDENCE),"Below-threshold clue visible");});
        step("Disabling kelp removes the remaining evidence report",()->bool("clue-kelp",false),()->require(!reports().containsKey(EVIDENCE),"Disabled categories remain"));
        step("Re-enabling categories restores evidence",()->{bool("clue-bees",true);bool("clue-kelp",true);},()->require(score(EVIDENCE)==6,"Toggle restore failed"));
        step("Real section updates: honey removed and hive broken reduce score to zero",()->server(l->{put(l,20,64,4,"minecraft:bee_nest[honey_level=0]");put(l,22,64,4,"minecraft:air");}),()->require(score(EVIDENCE)==0,"Removal update stale: "+score(EVIDENCE)));
        step("Real single-block update: honey appears and score becomes two",()->server(l->put(l,20,64,4,"minecraft:bee_nest[honey_level=1]")),()->require(score(EVIDENCE)==2,"Honey update stale: "+score(EVIDENCE)));
        step("Breaking every clue clears report and marker",()->server(l->{put(l,18,64,4,"minecraft:water");put(l,20,64,4,"minecraft:air");}),()->require(!reports().containsKey(EVIDENCE)&&!values("visible").containsKey(EVIDENCE),"Removed clues remain"));
        step("Adding hive in previously empty chunk is detected",()->server(l->put(l,22,64,4,"minecraft:beehive")),()->require(score(EVIDENCE)==4,"New clue not scanned"));
        steps.add(new Step("Teleport unload clears activity reports",()->teleport(600,-59,600),()->{require(!LevelUtil.isLoaded(mc.level,0,0),"Fixture still loaded");require(!reports().containsKey(NATURAL)&&!reports().containsKey(EVIDENCE),"Unloaded reports retained");},120));
        steps.add(new Step("Returning reloads activity reports",()->teleport(24,64,-6),()->{require(score(NATURAL)==0&&score(EVIDENCE)==4,"Reload missing reports");},120));
        step("Disable clears all state",()->module.setEnabled(false),()->require(reports().isEmpty()&&values("visible").isEmpty()&&((LongOpenHashSet)field("scannedChunks")).isEmpty(),"Disable did not clear state"));
        step("Enable rescans loaded chunks",()->module.setEnabled(true),()->require(score(EVIDENCE)==4,"Enable rescan failed"));
        step("Classic initial scan finds grown clusters",()->{
            server(l->{for(int x=2;x<=8;x+=2)put(l,x,64,34,"minecraft:amethyst_cluster[facing=up]");});
            module.setEnabled(false);mode("Classic");number("spread-radius",2);number("packet-sensitivity",1);
        },()->{});
        step("Classic initial heat and markers",()->{module.setEnabled(true);mc.player.setYRot(0);mc.player.setXRot(12);},()->{require(values("chunkScore").get(CLASSIC)==4,"Expected four grown clusters");require(!values("visible").isEmpty(),"Classic markers missing");capture("sus-chunk-classic.png");});
        step("Classic sensitivity applies after underground block update",()->{number("packet-sensitivity",6);server(l->put(l,4,0,34,"minecraft:stone"));},()->require(!values("visible").containsKey(CLASSIC),"Sensitivity or underground-block cache stale"));
        step("Lowering Classic sensitivity restores marker",()->number("packet-sensitivity",1),()->require(values("visible").containsKey(CLASSIC),"Sensitivity refresh missing"));
        step("Changing spread radius rebuilds contribution field",()->number("spread-radius",3),()->require(values("heat").get(ChunkPos.pack(3,2))==5,"Radius rebuild failed"));
        step("Restoring radius removes out-of-range contribution",()->number("spread-radius",2),()->require(values("heat").get(ChunkPos.pack(3,2))==0,"Old radius heat retained"));
        step("Classic live addition refreshes score",()->server(l->put(l,10,64,34,"minecraft:amethyst_cluster[facing=up]")),()->require(values("chunkScore").get(CLASSIC)==5,"Cluster addition stale: "+values("chunkScore").get(CLASSIC)));
        step("Classic ungrown bud introduces veto",()->server(l->{put(l,12,63,34,"minecraft:budding_amethyst");put(l,12,64,34,"minecraft:small_amethyst_bud[facing=up]");}),()->require(values("vetoes").get(CLASSIC)>0&&!values("visible").containsKey(CLASSIC),"Ungrown veto missing"));
        step("Classic mature bud removes veto and adds heat",()->server(l->put(l,12,64,34,"minecraft:amethyst_cluster[facing=up]")),()->require(values("vetoes").get(CLASSIC)==0&&values("chunkScore").get(CLASSIC)==6,"Growth left stale veto/heat"));
        step("Classic removing clusters removes their heat",()->server(l->{for(int x=2;x<=12;x+=2)put(l,x,64,34,"minecraft:air");}),()->require(values("chunkScore").get(CLASSIC)==0&&values("heat").get(CLASSIC)==1,"Removed clusters left stale heat"));
        step("Bud on ordinary support across chunk boundary counts as placed",()->server(l->{put(l,16,64,40,"minecraft:stone");put(l,15,64,40,"minecraft:small_amethyst_bud[facing=west]");}),()->require(values("chunkScore").get(CLASSIC)==1&&values("vetoes").get(CLASSIC)==0,"Boundary placed-bud classification wrong"));
        step("Neighbor-chunk support change introduces ungrown veto",()->server(l->put(l,16,64,40,"minecraft:budding_amethyst")),()->require(values("chunkScore").get(CLASSIC)==0&&values("vetoes").get(CLASSIC)>0,"Neighbor support update not scanned"));
        step("Replacing neighbor support removes veto",()->server(l->put(l,16,64,40,"minecraft:stone")),()->require(values("chunkScore").get(CLASSIC)==1&&values("vetoes").get(CLASSIC)==0,"Neighbor support left stale veto"));
        step("Boundary fixture removal clears heat",()->server(l->put(l,15,64,40,"minecraft:air")),()->require(values("chunkScore").get(CLASSIC)==0,"Boundary removal not scanned"));
        steps.add(new Step("Classic unload clears all heat, vetoes and scanned-neighbor membership",()->teleport(600,-59,600),()->require(values("heat").isEmpty()&&values("vetoes").isEmpty()&&!((LongOpenHashSet)field("scannedChunks")).contains(NATURAL),"Classic unloaded contribution retained"),120));
        steps.add(new Step("Classic reload restores current source score without duplication",()->teleport(24,64,-6),()->require(values("chunkScore").get(NATURAL)==1&&values("heat").get(NATURAL)==1,"Reload score duplicated/stale"),120));
        step("ANBS ordinary vanilla light does not flag this fixture",()->mode("ANBS+ Scan"),()->require(values("heat").isEmpty()&&values("visible").isEmpty(),"Ordinary fixture produced ANBS positive"));
        step("Switch back to activity clears Classic/ANBS state",()->mode("Activity clues"),()->require(score(EVIDENCE)==4&&values("heat").isEmpty()&&values("vetoes").isEmpty(),"Mode state leaked"));
        step("Bee score caps at 8; short aged kelp adds no points",()->{},()->{var s=new ActivityClues.Summary();for(int i=0;i<20;i++){s.add(BuilderMaterials.state("minecraft:kelp[age=25]"));s.add(BuilderMaterials.state("minecraft:bee_nest[honey_level=5]"));s.add(Blocks.BEEHIVE.defaultBlockState());}require(s.score()==8,"Score cap failed");});
    }
    private List<SuspectObjects.Marker> markers(){return field("markers");}
    private int objects(Category category){return markers().stream().filter(m->m.category()==category).flatMap(m->m.icons().values().stream()).mapToInt(Integer::intValue).sum();}
    private SuspectObjects.Marker formation(){return markers().stream().filter(m->m.category()==Category.GEODES&&m.bounds().minZ==25).findFirst().orElseThrow();}
    private void checkCrystalIcons(SuspectObjects.Marker marker,int small,int medium,int large,int clusters){
        var items=List.of(net.minecraft.world.item.Items.SMALL_AMETHYST_BUD,net.minecraft.world.item.Items.MEDIUM_AMETHYST_BUD,net.minecraft.world.item.Items.LARGE_AMETHYST_BUD,net.minecraft.world.item.Items.AMETHYST_CLUSTER);
        int[] expected={small,medium,large,clusters};
        for(int i=0;i<items.size();i++)require(marker.icons().getOrDefault(SuspectObjects.Icon.item(items.get(i)),0)==expected[i],"Wrong crystal stage count: "+items.get(i));
    }
    private void aim(Minecraft mc,Vec3 target){if(cameraTest)lockedLook=target;var d=target.subtract(mc.player.getEyePosition());mc.player.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));mc.player.setXRot((float)-Math.toDegrees(Math.atan2(d.y,d.horizontalDistance())));}
    private void objectPlan(Minecraft mc){
        step("Base object fixture arrives through normal packets",()->{
            cameraTest=true;mode("Classic");server(l->{
                for(int x=0;x<6;x++)for(int z=12;z<15;z++)put(l,x,64,z,"minecraft:piston[facing=up]");
                for(int x=0;x<6;x++)for(int z=16;z<18;z++)put(l,x,64,z,"minecraft:hopper");
                for(int x=8;x<11;x++)for(int z=12;z<15;z++)put(l,x,64,z,x==10&&z==14?"minecraft:chest":"minecraft:barrel");
                for(int x=8;x<11;x++)for(int z=16;z<19;z++)put(l,x,64,z,"minecraft:comparator");
                put(l,11,64,12,"minecraft:ender_chest");put(l,11,64,14,"minecraft:spawner");
                l.addFreshEntity(new net.minecraft.world.entity.decoration.ArmorStand(l,6.5,64,15.5));
                for(int x=14;x<=18;x++)for(int y=64;y<=67;y++)for(int z=25;z<=29;z++)
                    if(x==14||x==18||y==64||y==67||z==25||z==29)put(l,x,y,z,"minecraft:amethyst_block");
                for(int x=14;x<=18;x++)for(int z=25;z<=28;z++){
                    put(l,x,67,z,"minecraft:budding_amethyst");put(l,x,68,z,x==18?"minecraft:"+(z==25?"small_amethyst_bud":z==26?"medium_amethyst_bud":"large_amethyst_bud")+"[facing=up]":"minecraft:amethyst_cluster[facing=up]");
                }
                for(int x=4;x<=7;x++)for(int z=-47;z<=-43;z++)put(l,x,63,z,"minecraft:stone");
                for(int cx:new int[]{-12,-10}){
                    int length=cx==-12?18:3;
                    for(int y=64;y<=64+length;y++){for(var d:Direction.Plane.HORIZONTAL)l.setBlock(new BlockPos(cx,y,25).relative(d),Blocks.GLASS.defaultBlockState(),2);put(l,cx,y,25,"minecraft:water");}
                    for(int y=64;y<64+length;y++)put(l,cx,y,25,y==63+length?"minecraft:kelp[age=25]":"minecraft:kelp_plant");
                }
                for(int cx:new int[]{-6,-4}){put(l,cx,63,25,"minecraft:dirt");int length=cx==-6?15:4;for(int y=64;y<64+length;y++)put(l,cx,y,25,"minecraft:bamboo[age=1,stage=1]");}
                for(int y=64;y<84;y++){put(l,-12,y,31,"minecraft:stone");put(l,-12,y,30,"minecraft:vine[south=true]");}
                put(l,-6,88,30,"minecraft:stone");for(int y=87;y>=64;y--)put(l,-6,y,30,y==64?"minecraft:cave_vines[age=25]":"minecraft:cave_vines_plant");
            });
        },()->{require(objects(Category.MACHINES)==18,"Machine count "+objects(Category.MACHINES));require(objects(Category.HOPPERS)==12,"Hopper count");require(objects(Category.CONTAINERS)==9,"Container count");require(objects(Category.ENDER_STORAGE)==1&&objects(Category.SPAWNERS)==1&&objects(Category.DISPLAYS)==1,"Special objects missing");
            require(markers().stream().flatMap(m->m.icons().entrySet().stream()).filter(e->e.getKey().item()==net.minecraft.world.item.Items.COMPARATOR).mapToInt(Map.Entry::getValue).sum()==9,"Comparator count");
        });
        step("Move to object inspection viewpoint",()->teleport(5.5,64,-5),()->{});
        step("Look card uses exact object counts and real inventory icons",()->aim(mc,new Vec3(5.5,64.5,12.5)),()->{
            SuspectObjects.Focus focus=field("focused");require(focus!=null,"Aim panel did not open");
            var counts=new EnumMap<Category,Integer>(Category.class);for(var m:focus.nearby())counts.merge(m.category(),m.icons().values().stream().mapToInt(Integer::intValue).sum(),Integer::sum);
            require(counts.getOrDefault(Category.MACHINES,0)==18&&counts.getOrDefault(Category.HOPPERS,0)==12&&counts.getOrDefault(Category.CONTAINERS,0)==9,"Panel counts wrong: "+counts);
            capture("sus-objects-panel.png");
        });
        step("Looking away dismisses the panel",()->aim(mc,mc.player.getEyePosition().add(0,0,-10)),()->require(field("focused")==null,"Panel stayed after looking away"));
        step("Move to long-distance inspection viewpoint",()->teleport(5.5,64,-45),()->{});
        step("Readable anchored icon card beyond 50 blocks",()->aim(mc,new Vec3(5.5,64.5,12.5)),()->{SuspectObjects.Focus f=field("focused");require(f!=null&&f.distance()>50,"Long-distance focus missing");capture("sus-objects-distance.png");});
        step("Range limit hides distant object inspection",()->{number("object-range",16);aim(mc,new Vec3(5.5,64.5,12.5));},()->require(field("focused")==null,"Out-of-range panel visible"));
        step("Return to formation viewpoint",()->{number("object-range",128);teleport(5.5,64,-5);},()->{});
        step("Connected amethyst spans chunk border as one formation",()->{number("object-range",128);aim(mc,new Vec3(16.5,66,25));},()->{
            var formations=markers().stream().filter(m->m.category()==Category.GEODES&&m.bounds().minZ==25).toList();
            require(formations.size()==1,"Geode split into "+formations.size()+" markers");var box=formations.getFirst().bounds();
            require(box.minX==14&&box.maxX==19&&box.maxY==69&&box.maxZ==30,"Wrong geode bounds "+box);
            require(field("focused")!=null,"Geode look panel absent");require(formations.getFirst().detail().contains("16 mature / 4 buds")&&formations.getFirst().detail().contains("80%"),"Maturity breakdown wrong");
            require(formations.getFirst().detail().contains("1 small / 1 medium / 2 large"),"Missing bud stage breakdown");
            checkCrystalIcons(formations.getFirst(),1,1,2,16);capture("sus-geode-panel.png");
        });
        step("Geode below configured maturity percentage is hidden",()->number("mature-percent",90),()->require(markers().stream().noneMatch(m->m.category()==Category.GEODES),"Immature geode still outlined"));
        step("Geode below minimum mature-cluster count is hidden",()->{number("mature-percent",75);number("mature-clusters",17);},()->require(markers().stream().noneMatch(m->m.category()==Category.GEODES),"Too few mature crystals still flagged"));
        for(String stage:List.of("small_amethyst_bud","medium_amethyst_bud","large_amethyst_bud")){
            step("Live crystal stage: "+stage+" is counted separately",()->{
                number("mature-clusters",12);bool("natural-context",true);
                server(l->{for(int x=14;x<=18;x++)for(int z=25;z<=28;z++)put(l,x,68,z,"minecraft:"+stage+"[facing=up]");});
            },()->{
                var formation=formation();checkCrystalIcons(formation,stage.startsWith("small")?20:0,stage.startsWith("medium")?20:0,stage.startsWith("large")?20:0,0);
                require(formation.detail().startsWith("0 mature / 20 buds - 0%"),"Unfinished buds classified as mature");
            });
            step("Only "+stage+" does not qualify as overgrown",()->bool("natural-context",false),()->require(markers().stream().noneMatch(m->m.category()==Category.GEODES),"Immature formation falsely flagged"));
        }
        step("Twelve full clusters among eight large buds stay below maturity threshold",()->server(l->{for(int x=14;x<=16;x++)for(int z=25;z<=28;z++)put(l,x,68,z,"minecraft:amethyst_cluster[facing=up]");}),()->require(markers().stream().noneMatch(m->m.category()==Category.GEODES),"60 percent formation falsely flagged"));
        step("Live growth reaches exact 75 percent threshold",()->server(l->{for(int z=25;z<=27;z++)put(l,17,68,z,"minecraft:amethyst_cluster[facing=up]");}),()->{checkCrystalIcons(formation(),0,0,5,15);require(formation().detail().contains("75%"),"Growth ratio not updated");});
        step("Mixed four-stage fixture restored through live updates",()->server(l->{put(l,17,68,28,"minecraft:amethyst_cluster[facing=up]");put(l,18,68,25,"minecraft:small_amethyst_bud[facing=up]");put(l,18,68,26,"minecraft:medium_amethyst_bud[facing=up]");}),()->checkCrystalIcons(formation(),1,1,2,16));
        step("Growth uses measured lengths and combined nearby clue types",()->{number("mature-clusters",12);aim(mc,new Vec3(-6,72,25));},()->{
            var plants=markers().stream().filter(m->m.category()==Category.GROWTH&&m.detail().contains("blocks long")).toList();
            require(plants.size()==4,"Expected four long plants, got "+plants.size());
            for(String length:List.of("18 blocks","15 blocks","20 blocks","24 blocks"))require(plants.stream().anyMatch(m->m.detail().startsWith(length)),"Missing measured length "+length);
            require(plants.stream().noneMatch(m->m.bounds().minX==-10||m.bounds().minX==-4),"Short plants falsely flagged");
            SuspectObjects.Focus f=field("focused");require(f!=null&&f.nearby().stream().anyMatch(m->m.category()==Category.HOPPERS)&&f.nearby().stream().filter(m->m.category()==Category.GROWTH).count()>=3,"Combined growth and machine evidence absent");capture("sus-growth-panel.png");
        });
        step("Cave vines show vine texture and measured length without berries",()->aim(mc,new Vec3(-5.5,75,30.5)),()->{
            var vine=markers().stream().filter(m->m.label().equals("Cave vines growth")).findFirst().orElseThrow();
            require(vine.icons().getOrDefault(SuspectObjects.CAVE_VINES_ICON,0)==24&&SuspectObjects.CAVE_VINES_ICON.item()==null,"Cave vine evidence uses a berry item");
            require(vine.detail().startsWith("24 blocks long"),"Cave vine length wrong");
            require(markers().stream().noneMatch(m->m.bounds().minX==4&&m.bounds().minZ==4),"One berry-bearing cave vine falsely flagged");
            SuspectObjects.Focus f=field("focused");require(f!=null&&f.target().label().equals("Cave vines growth"),"Cave vine panel absent");capture("sus-growth-panel.png");
        });
        step("Adding berries does not change cave-vine evidence",()->server(l->{for(int y=64;y<=87;y++)put(l,-6,y,30,y==64?"minecraft:cave_vines[age=25,berries=true]":"minecraft:cave_vines_plant[berries=true]");}),()->{
            var vines=markers().stream().filter(m->m.label().equals("Cave vines growth")).toList();
            require(vines.size()==1&&vines.getFirst().icons().getOrDefault(SuspectObjects.CAVE_VINES_ICON,0)==24&&vines.getFirst().detail().startsWith("24 blocks long"),"Berries affected growth detection");
        });
        step("Berry-bearing cave vines still fail a longer length threshold",()->number("cave-vine-length",25),()->require(markers().stream().noneMatch(m->m.label().equals("Cave vines growth")),"Berries bypassed length threshold"));
        step("Restoring threshold and removing berries keeps the long vine",()->{number("cave-vine-length",20);server(l->{for(int y=64;y<=87;y++)put(l,-6,y,30,y==64?"minecraft:cave_vines[age=25]":"minecraft:cave_vines_plant");});},()->require(markers().stream().anyMatch(m->m.label().equals("Cave vines growth")&&m.detail().startsWith("24 blocks long")),"Berry-free long vine disappeared"));
        step("Bamboo threshold updates the measured-growth filter",()->number("bamboo-length",16),()->require(markers().stream().noneMatch(m->m.label().equals("Bamboo growth")),"Shorter bamboo still shown"));
        step("Restoring bamboo threshold restores its outline",()->number("bamboo-length",14),()->require(markers().stream().anyMatch(m->m.label().equals("Bamboo growth")),"Bamboo did not return"));
        step("Object category toggle removes hopper outlines",()->bool("objects-hoppers",false),()->require(objects(Category.HOPPERS)==0,"Hoppers still highlighted"));
        step("Object category restore rescans hoppers",()->bool("objects-hoppers",true),()->require(objects(Category.HOPPERS)==12,"Hoppers did not return"));
        step("Breaking a container updates icon count",()->server(l->put(l,8,64,12,"minecraft:air")),()->require(objects(Category.CONTAINERS)==8,"Removed container count stale"));
        steps.add(new Step("Object outlines disappear after chunks unload",()->teleport(600,-59,600),()->require(markers().isEmpty(),"Object outlines retained after unload"),120));
    }
    private void tick(Minecraft mc){
        if(phase==99)return;
        try{
            if(System.nanoTime()-started>600_000_000_000L)throw new IllegalStateException("Test timeout");
            ticks++;if(mc.gui.overlay()!=null)return;
            if(phase==0){if(ticks<80)return;mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(6);mc.options.simulationDistance().set(5);
                mc.createWorldOpenFlows().createFreshLevel("sus-chunk-test-"+System.currentTimeMillis(),new LevelSettings("Sus Chunk disposable verification",GameType.CREATIVE,LevelSettings.DifficultySettings.DEFAULT,true,WorldDataConfiguration.DEFAULT),new WorldOptions(2611262L,false,false),r->r.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.gui.screen());phase=1;ticks=0;return;}
            if(mc.level==null||mc.player==null)return;
            if(phase==1){if(ticks<60)return;server(this::fixtures);phase=2;ticks=0;return;}
            if(phase==2){if(!operation.isDone())return;operation.join();teleport(24,64,-6);phase=3;ticks=0;return;}
            if(phase==3){if(ticks<60||!operation.isDone())return;operation.join();operation=null;mc.player.setYRot(0);mc.player.setXRot(8);plan(mc);objectPlan(mc);phase=4;ticks=0;return;}
            if(phase==4){
                if(cameraTest){mc.mouseHandler.releaseMouse();mc.options.keyUp.setDown(false);mc.options.keyDown.setDown(false);mc.options.keyLeft.setDown(false);mc.options.keyRight.setDown(false);mc.options.keyJump.setDown(false);mc.player.setDeltaMovement(Vec3.ZERO);if(lockedPosition!=null)mc.player.setPos(lockedPosition);if(lockedLook!=null)aim(mc,lockedLook);}
                if(index==steps.size()){module.setEnabled(false);report.add("Scope: Minecraft 26.2 integrated server. No remote 1.21.11 proxy, DonutFolia brand or positive ANBS anti-xray anomaly tested.");Files.write(Path.of(failures==0?"sus-chunk-pass.txt":"sus-chunk-failure.txt"),report);phase=99;mc.stop();return;}
                var step=steps.get(index);
                if(ticks==1){operation=null;step.action.run();}
                if(ticks<step.delay||operation!=null&&!operation.isDone())return;
                try{if(operation!=null)operation.join();step.check.run();report.add("PASS: "+step.name);System.out.println("SUS_TEST PASS: "+step.name);}catch(Throwable e){failures++;report.add("FAIL: "+step.name+" — "+e);System.out.println("SUS_TEST FAIL: "+step.name+" — "+e);}
                index++;ticks=0;
            }
        }catch(Throwable e){e.printStackTrace();try{report.add("FATAL: "+e);Files.write(Path.of("sus-chunk-failure.txt"),report);}catch(Exception ignored){}phase=99;module.setEnabled(false);mc.stop();}
    }
}
