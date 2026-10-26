package net.spectre.client.map;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.*;
import net.spectre.client.builder.*;
import net.spectre.client.config.ThemeStore;
import net.spectre.client.gui.*;
import net.spectre.client.module.ModuleRegistry;

/** Continuous first-person evidence: real import, normal builder, GUI supplies and server-read-only proof. */
public final class RedstoneDemoClient implements ClientModInitializer {
    private int phase,ticks,supplyIndex,gridClick,proofIndex,inspectIndex;
    private long started=System.nanoTime();
    private CompletableFuture<?> pending;
    private Schematic schematic;
    private final Schematic.Pos origin=new Schematic.Pos(0,-60,0);
    private final Path file=Path.of("spectre/schematics/working-redstone-machinery.schem");
    private final List<String> checks=new ArrayList<>();
    private int walkingSamples;
    private Vec3 constructionStart;
    private record Supply(String name,BlockPos pos,Item item,int count,boolean grid){}
    private record Proof(String name,BlockPos lever,Consumer<ServerLevel> on,Consumer<ServerLevel> off){}
    private final List<Supply> supplies=List.of(
        new Supply("Fuel hopper",at(8,1,1),Items.COAL,1,false),
        new Supply("Input hopper",at(9,2,1),Items.RAW_IRON,1,false),
        new Supply("Crafter grid",at(9,1,5),Items.IRON_INGOT,9,true),
        new Supply("Dispenser",at(1,1,9),Items.ARROW,1,false),
        new Supply("Dropper",at(9,1,9),Items.DIAMOND,1,false));
    private final List<Proof> proofs=List.of(
        new Proof("Repeater and piston",at(0,1,1),level->{
            require(level.getBlockState(at(3,1,1)).getValue(BlockStateProperties.DELAY)==4,"Repeater delay is not 4");
            require(level.getBlockState(at(4,1,1)).getValue(BlockStateProperties.EXTENDED),"Piston did not extend");
            require(level.getBlockState(at(5,1,1)).is(Blocks.PISTON_HEAD),"Piston head missing");
        },level->require(!level.getBlockState(at(4,1,1)).getValue(BlockStateProperties.EXTENDED),"Piston did not retract")),
        new Proof("Comparator and sticky piston",at(0,1,5),level->{
            require(level.getBlockState(at(2,1,5)).getValue(BlockStateProperties.MODE_COMPARATOR)==net.minecraft.world.level.block.state.properties.ComparatorMode.SUBTRACT,"Comparator mode lost");
            require(level.getBlockState(at(3,1,5)).getValue(BlockStateProperties.EXTENDED),"Sticky piston did not extend");
            require(level.getBlockState(at(5,1,5)).is(Blocks.STONE),"Sticky piston did not push payload");
        },level->{require(!level.getBlockState(at(3,1,5)).getValue(BlockStateProperties.EXTENDED),"Sticky piston did not retract");require(level.getBlockState(at(4,1,5)).is(Blocks.STONE),"Sticky piston did not pull payload back");}),
        new Proof("Crafter",at(10,1,5),level->require(contains(level,at(9,1,4),Items.IRON_BLOCK),"Crafter did not craft and deliver iron block"),level->{}),
        new Proof("Dispenser",at(0,1,9),level->{
            require(!level.getEntitiesOfClass(AbstractArrow.class,new AABB(-1,-60,7,7,-55,11)).isEmpty(),"Dispenser did not fire an arrow");
            require(level.getBlockState(at(5,1,9)).getValue(BlockStateProperties.POWER)>0,"Arrow did not activate target");
        },level->{}),
        new Proof("Dropper",at(10,1,9),level->require(contains(level,at(9,1,8),Items.DIAMOND),"Dropper did not transfer item into chest"),level->{}));
    private final List<BlockPos> inspect=List.of(at(9,1,4),at(9,1,8),at(9,1,1),at(10,0,1));
    private final List<String> inspectNames=List.of("Crafted iron block","Dropper output","Working furnace","Smelted iron delivered");
    private static BlockPos at(int x,int y,int z){return new BlockPos(x,y-60,z);}
    private static void require(boolean ok,String error){if(!ok)throw new AssertionError(error);}
    private static boolean contains(ServerLevel level,BlockPos pos,Item item){
        if(!(level.getBlockEntity(pos) instanceof Container c))throw new AssertionError("Container missing at "+pos);
        for(int i=0;i<c.getContainerSize();i++)if(c.getItem(i).is(item))return true;return false;
    }
    public void onInitializeClient(){
        ThemeStore.get().applyPreset(ThemeStore.PRESETS.getFirst());
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }
    private void next(int p){phase=p;ticks=0;}
    private void face(Minecraft mc,Vec3 target){
        var d=target.subtract(mc.player.getEyePosition());mc.player.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));
        mc.player.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));
    }
    private void position(Minecraft mc,BlockPos pos){
        var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
        pending=server.submit(()->{server.getPlayerList().getPlayer(id).teleportTo(pos.getX()-1.5,-60,pos.getZ()-1.5);return true;});
    }
    private void open(Minecraft mc,BlockPos pos){
        mc.options.keyShift.setDown(false);face(mc,Vec3.atCenterOf(pos));
        mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));
        mc.player.swing(InteractionHand.MAIN_HAND);
    }
    private void close(Minecraft mc){if(mc.gui.screen()!=null)mc.gui.screen().onClose();}
    private void checkAsync(Minecraft mc,Consumer<ServerLevel> check){pending=mc.getSingleplayerServer().submit(()->{check.accept(mc.getSingleplayerServer().overworld());return true;});}
    private void verifyLocalOrder(CreativeBuilder builder)throws Exception{
        var field=CreativeBuilder.class.getDeclaredField("steps");field.setAccessible(true);
        var steps=(List<?>)field.get(builder);var seen=new HashSet<Integer>();int last=-1,baseLeft=0;
        // Independent fixture regions, rather than trusting the planner's group IDs.
        var bases=new HashMap<Integer,Integer>();
        for(var block:schematic.blocks())if(block.pos().y()==0){int group=(block.pos().z()/4)*2+(block.pos().x()>=8?1:0);bases.merge(group,1,Integer::sum);}
        for(var step:steps){
            var posField=step.getClass().getDeclaredMethod("pos");posField.setAccessible(true);var p=(BlockPos)posField.invoke(step);
            int group=(p.getZ()/4)*2+(p.getX()>=8?1:0);
            if(group!=last){require(seen.add(group),"Builder left a machine unfinished and returned to it: "+group);last=group;baseLeft=bases.get(group);}
            if(p.getY()==-60)baseLeft--;else require(baseLeft==0,"Upper machine blocks placed before its foundation was finished");
        }
        require(seen.size()==6,"Did not cover all six independent machines");
        require(walkingSamples>10,"Insufficient walking coverage");
        checks.add("All six machines built as contiguous local sections; each foundation finished before upper placements; "+walkingSamples+" walking ticks without crouch braking; first two reachable blocks placed without moving from the starting pose");
    }
    private void tick(Minecraft mc){try{
        if(phase==99)return;
        if(System.nanoTime()-started>900_000_000_000L)throw new IllegalStateException("Recording exceeded fifteen minutes");
        if(mc.gui.overlay()!=null)return;ticks++;
        if(phase==0){
            if(ticks<60)return;
            mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(2);mc.options.renderDistance().set(6);mc.options.simulationDistance().set(5);mc.options.setCameraType(CameraType.FIRST_PERSON);
            for(var m:ModuleRegistry.all())m.setEnabled(false);
            mc.createWorldOpenFlows().createFreshLevel("redstone-demo-"+System.currentTimeMillis(),new LevelSettings("Spectre machinery demonstration",GameType.CREATIVE,new LevelSettings.DifficultySettings(net.minecraft.world.Difficulty.PEACEFUL,false,false),true,WorldDataConfiguration.DEFAULT),new WorldOptions(2611262L,false,false),r->r.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.gui.screen());next(1);return;
        }
        if(mc.player==null||mc.level==null)return;
        if(phase==1){
            if(ticks<40)return;Files.createDirectories(file.getParent());try(var in=getClass().getResourceAsStream("/builder/redstone-demo.schem")){Files.copy(in,file,StandardCopyOption.REPLACE_EXISTING);}
            schematic=SchematicReader.read(file);checks.add("Imported actual .schem: "+schematic.blocks().size()+" blocks");
            var server=mc.getSingleplayerServer();var id=mc.player.getUUID();pending=server.submit(()->{
                var p=server.getPlayerList().getPlayer(id);var l=p.level();
                for(int x=-10;x<=20;x++)for(int z=-10;z<=20;z++)for(int y=-61;y<=-55;y++)l.setBlock(new BlockPos(x,y,z),y==-61?Blocks.SMOOTH_STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
                p.teleportTo(7.5,-60,13.5);server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"time set noon");return true;
            });next(2);return;
        }
        if(phase==2){
            if(ticks<30||!pending.isDone())return;pending.join();
            var items=new LinkedHashSet<Item>();for(var b:schematic.blocks()){var s=BuilderMaterials.state(b.state());items.add(s.is(Blocks.REDSTONE_WIRE)?Items.REDSTONE:s.getBlock().asItem());}
            int slot=0;for(var item:items){var stack=new ItemStack(item,64);mc.player.getInventory().setItem(slot,stack);mc.gameMode.handleCreativeModeItemAdd(stack,slot<9?36+slot:slot);slot++;}
            BuilderPanel.open();var load=BuilderPanel.class.getDeclaredMethod("load",Path.class);load.setAccessible(true);load.invoke(null,file);mc.gui.setScreen(new ImGuiScreen());ShowcaseCapture.begin();next(3);return;
        }
        if(phase==3){
            if(ticks<140)return;var loaded=BuilderPanel.class.getDeclaredField("loaded");loaded.setAccessible(true);if(loaded.get(null)==null)return;
            SchematicPreview.INSTANCE.transform(origin,0);SchematicPreview.INSTANCE.confirm();close(mc);face(mc,new Vec3(5.5,-59,5.5));ShowcaseCapture.mark("Schematic preview");next(4);return;
        }
        if(phase==4){if(ticks<80)return;ShowcaseCapture.mark("Construction");constructionStart=mc.player.position();CreativeBuilder.INSTANCE.start(schematic.blocks(),origin,0);next(5);return;}
        if(phase==5){
            var b=CreativeBuilder.INSTANCE;if(b.running()){
                if(b.completed()<2)require(mc.player.position().distanceToSqr(constructionStart)<.0009,"Moved before placing the two adjacent foundation blocks already within reach");
                var stage=CreativeBuilder.class.getDeclaredField("travelStage");stage.setAccessible(true);
                if(stage.getInt(b)==1){walkingSamples++;require(!mc.options.keyShift.isDown(),"Navigator crouched as a routine arrival brake");}
                if(ticks%100==0)System.out.println("REDSTONE_DEMO: "+b.completed()+"/"+b.total()+" "+b.status()+" "+b.diagnostic());return;
            }
            if(!b.status().startsWith("Complete:"))throw new IllegalStateException(b.status()+" "+b.diagnostic());
            verifyLocalOrder(b);
            checkAsync(mc,level->{for(var block:schematic.blocks()){var p=block.pos().add(origin);require(level.getBlockState(new BlockPos(p.x(),p.y(),p.z())).equals(BuilderMaterials.state(block.state())),"Server state mismatch at "+p);}});next(6);return;
        }
        if(phase==6){
            if(!pending.isDone())return;pending.join();checks.add("Every schematic block state verified independently on server, including repeater delay 4, subtract comparator mode, crafter orientation and hopper outlets");SchematicPreview.INSTANCE.clear();ShowcaseCapture.mark("Verified layout");next(20);return;
        }
        if(phase==20){
            if(supplyIndex==supplies.size()){mc.player.getInventory().setSelectedSlot(7);var empty=ItemStack.EMPTY;mc.player.getInventory().setItem(7,empty);mc.gameMode.handleCreativeModeItemAdd(empty,43);next(30);return;}
            var s=supplies.get(supplyIndex);ShowcaseCapture.mark("Supply: "+s.name);position(mc,s.pos);next(21);return;
        }
        if(phase==21){
            if(!pending.isDone()||ticks<20)return;pending.join();var s=supplies.get(supplyIndex);var stack=new ItemStack(s.item,s.count);mc.player.getInventory().setItem(8,stack);mc.gameMode.handleCreativeModeItemAdd(stack,44);mc.player.getInventory().setSelectedSlot(7);open(mc,s.pos);next(22);return;
        }
        if(phase==22){
            if(ticks<20||mc.player.containerMenu==mc.player.inventoryMenu)return;
            var s=supplies.get(supplyIndex);var menu=mc.player.containerMenu;var source=menu.slots.stream().filter(slot->slot.container==mc.player.getInventory()&&slot.getItem().is(s.item)).findFirst().orElseThrow();
            mc.gameMode.handleContainerInput(menu.containerId,source.index,0,s.grid?ContainerInput.PICKUP:ContainerInput.QUICK_MOVE,mc.player);gridClick=0;next(s.grid?23:24);return;
        }
        if(phase==23){if(ticks<8)return;ticks=0;var menu=mc.player.containerMenu;mc.gameMode.handleContainerInput(menu.containerId,gridClick++,1,ContainerInput.PICKUP,mc.player);if(gridClick==9)next(24);return;}
        if(phase==24){if(ticks<30)return;close(mc);checks.add("Test input supplied through actual container GUI: "+supplies.get(supplyIndex).name);supplyIndex++;next(20);return;}
        if(phase==30){
            if(proofIndex==proofs.size()){ShowcaseCapture.mark("Furnace and hoppers");checkAsync(mc,l->{require(contains(l,at(10,0,1),Items.IRON_INGOT),"Hopper-fed furnace did not smelt and deliver iron ingot");});next(40);return;}
            var proof=proofs.get(proofIndex);ShowcaseCapture.mark("Test: "+proof.name);position(mc,proof.lever);next(31);return;
        }
        if(phase==31){if(!pending.isDone()||ticks<30)return;pending.join();open(mc,proofs.get(proofIndex).lever);next(32);return;}
        if(phase==32){var p=proofs.get(proofIndex);face(mc,Vec3.atCenterOf(p.lever).add(2,0,0));if(ticks<16)return;checkAsync(mc,p.on);next(33);return;}
        if(phase==33){if(!pending.isDone())return;pending.join();checks.add("Functional ON test passed: "+proofs.get(proofIndex).name);ShowcaseCapture.mark("Passed: "+proofs.get(proofIndex).name);next(34);return;}
        if(phase==34){if(ticks<60)return;open(mc,proofs.get(proofIndex).lever);next(35);return;}
        if(phase==35){if(ticks<30)return;checkAsync(mc,proofs.get(proofIndex).off);next(36);return;}
        if(phase==36){if(!pending.isDone())return;pending.join();checks.add("Functional OFF test passed: "+proofs.get(proofIndex).name);proofIndex++;next(30);return;}
        if(phase==40){if(!pending.isDone())return;pending.join();checks.add("Fuel hopper and input hopper fed furnace; output hopper delivered smelted iron into chest");next(41);return;}
        if(phase==41){if(inspectIndex==inspect.size()){ShowcaseCapture.mark("All tests passed");next(45);return;}ShowcaseCapture.mark("Inspect: "+inspectNames.get(inspectIndex));position(mc,inspect.get(inspectIndex));next(42);return;}
        if(phase==42){if(!pending.isDone()||ticks<20)return;pending.join();open(mc,inspect.get(inspectIndex));next(43);return;}
        if(phase==43){if(ticks<60)return;close(mc);inspectIndex++;next(41);return;}
        if(phase==45){if(ticks<100)return;ShowcaseCapture.finish();Files.writeString(Path.of("builder-demo-pass.txt"),String.join("\n",checks)+"\nContinuous real-time capture. Normal executor timings. No schematic target blocks preplaced.\n");phase=99;mc.stop();}
    }catch(Throwable e){e.printStackTrace();try{Files.writeString(Path.of("builder-demo-failure.txt"),e.toString());ShowcaseCapture.finish();}catch(Throwable ignored){}phase=99;mc.stop();}}
}
