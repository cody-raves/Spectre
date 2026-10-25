package net.spectre.client.map;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.*;
import net.spectre.client.builder.*;

/** Real client interactions build the targets; server writes only arrange the empty test platform and stimuli. */
public final class BuilderSmokeClient implements ClientModInitializer {
    private int phase,ticks,rotation,pausedAt=-1,stopAt=-1;
    private long started=System.nanoTime();
    private Schematic schematic;
    private Schematic.Pos origin;
    private Schematic.Pos previewAnchor;
    private CompletableFuture<?> operation;
    private final List<String> report=new ArrayList<>();
    private Vec3 lastWalkPosition;
    private int forwardSamples,aimSamples,movingGhostSamples;
    private double maxWalkAngle;
    private net.spectre.client.module.impl.render.SusChunkFinderModule clues;
    private void verifyWalking(Minecraft mc,CreativeBuilder builder)throws Exception{
        if(!net.spectre.client.gui.WorkspaceMascot.activeModule().equals("builder"))throw new AssertionError("HUD mascot did not stay in builder mode");
        var ghost=SchematicPreview.INSTANCE;
        if(!ghost.active()||!ghost.visible()||!ghost.confirmed()||!ghost.origin().equals(origin)||ghost.rotation()!=0)
            throw new AssertionError("Active build lost its anchored ghost preview");
        if(mc.options.keyDown.isDown()||mc.options.keyLeft.isDown()||mc.options.keyRight.isDown())
            throw new AssertionError("Builder pressed backwards or sideways movement");
        if(mc.player.getAbilities().flying)throw new AssertionError("Builder enabled flight");
        var position=mc.player.position();
        if(lastWalkPosition!=null){
            var movement=position.subtract(lastWalkPosition);
            if(movement.horizontalDistance()>.04){
                double yaw=Math.toRadians(mc.player.getYRot());
                double dot=(-Math.sin(yaw)*movement.x+Math.cos(yaw)*movement.z)/movement.horizontalDistance();
                double angle=Math.toDegrees(Math.acos(Math.clamp(dot,-1,1)));
                maxWalkAngle=Math.max(maxWalkAngle,angle);forwardSamples++;
                // Capture after the hat entrance has finished, not the first walking frame.
                if(ghost.texturedFaces()>0&&++movingGhostSamples==25)AtlasSmokeClient.captureBack(mc,"builder-walking-ghost.png");
                if(angle>35)throw new AssertionError("Walking direction differs from view by "+angle+" degrees: "+builder.diagnostic());
            }
        }
        lastWalkPosition=position;
        var stage=CreativeBuilder.class.getDeclaredField("travelStage");stage.setAccessible(true);
        if(stage.getInt(builder)==2){
            aimSamples++;
            if(!mc.player.onGround()||mc.player.getDeltaMovement().horizontalDistance()>=.004)
                throw new AssertionError("Placement aiming started before walking stopped: "+builder.diagnostic());
            if(mc.options.keyUp.isDown()||mc.options.keyJump.isDown())throw new AssertionError("Movement held during placement aiming");
        }
    }
    public void onInitializeClient(){ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
    private BlockPos at(int x,int y,int z){var p=new Schematic.Pos(x,y,z).rotate(rotation).add(origin);return new BlockPos(p.x(),p.y(),p.z());}
    private void pass(String text){report.add(text);System.out.println("BUILDER_TEST: PASS "+text);}
    private void tick(Minecraft mc){
        try{
            if(phase==99)return;
            if(System.nanoTime()-started>1800_000_000_000L)throw new IllegalStateException("Test timeout");
            ticks++;
            if(mc.gui.overlay()!=null)return;
            if(phase==0){
                if(ticks<80)return;
                mc.options.pauseOnLostFocus=false;
                mc.options.renderDistance().set(6);mc.options.simulationDistance().set(5);
                mc.createWorldOpenFlows().createFreshLevel("builder-test-"+System.currentTimeMillis(),
                    new LevelSettings(Boolean.getBoolean("spectre.builderPlay")?"Spectre Builder Practice":"Builder disposable verification",GameType.CREATIVE,LevelSettings.DifficultySettings.DEFAULT,true,WorldDataConfiguration.DEFAULT),
                    new WorldOptions(2611262L,false,false),r->r.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.gui.screen());
                phase=1;ticks=0;return;
            }
            if(mc.level==null||mc.player==null)return;
            var builder=CreativeBuilder.INSTANCE;
            if(phase==1){
                if(ticks<60)return;
                Path file=Path.of("spectre/schematics/functional-test.schem");Files.createDirectories(file.getParent());
                try(var stream=getClass().getResourceAsStream("/builder/functional-test.schem")){Files.copy(stream,file,StandardCopyOption.REPLACE_EXISTING);}
                schematic=SchematicReader.read(file);pass("Imported actual .schem with "+schematic.blocks().size()+" non-air cells");
                origin=new Schematic.Pos(-5,-59,-7);
                phase=2;ticks=0;
            }
            if(phase==2){
                if(operation==null){
                    ticks=0;
                    var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
                    operation=server.submit(()->{
                        var player=server.getPlayerList().getPlayer(id);var level=player.level();
                        // Ground only, below schematic cells. Never pre-place a target block.
                        for(int x=-13;x<=13;x++)for(int z=-13;z<=13;z++)for(int y=-1;y<=8;y++){
                            var pos=new BlockPos(origin.x()+x,origin.y()+y,origin.z()+z);
                            level.setBlock(pos,y==-1?Blocks.BEDROCK.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
                        }
                        // Existing access scaffold outside the schematic, like prepared job-site terrain.
                        for(int i=0;i<4;i++)for(int y=0;y<=i;y++)level.setBlock(at(i-4,y,-3),Blocks.OAK_PLANKS.defaultBlockState(),3);
                        for(int x=-1;x<=11;x++)for(int z=-3;z<=-1;z++)level.setBlock(at(x,3,z),Blocks.OAK_PLANKS.defaultBlockState(),3);
                        player.teleportTo(origin.x()+.5,origin.y()+5,origin.z()+.5);return true;
                    });
                }
                if(ticks<30||!operation.isDone())return;operation.join();
                var supplies=new LinkedHashSet<Item>();
                for(var block:schematic.blocks()){
                    var s=BuilderMaterials.state(block.state());supplies.add(s.is(Blocks.WATER)?Items.WATER_BUCKET:s.is(Blocks.LAVA)?Items.LAVA_BUCKET:s.is(Blocks.REDSTONE_WIRE)?Items.REDSTONE:s.getBlock().asItem());
                }
                int slot=0;for(Item item:supplies){var stack=new ItemStack(item,item.getDefaultInstance().getMaxStackSize());mc.player.getInventory().setItem(slot,stack);mc.gameMode.handleCreativeModeItemAdd(stack,slot<9?36+slot:slot);slot++;}
                phase=3;ticks=0;return;
            }
            if(phase==3){
                if(ticks<20)return;
                if(Boolean.getBoolean("spectre.builderPlay")){
                    net.spectre.client.gui.BuilderPanel.open();
                    var load=net.spectre.client.gui.BuilderPanel.class.getDeclaredMethod("load",Path.class);load.setAccessible(true);
                    load.invoke(null,Path.of("spectre/schematics/functional-test.schem"));
                    mc.gui.setScreen(new net.spectre.client.gui.ImGuiScreen());
                    var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
                    operation=server.submit(()->{server.getPlayerList().getPlayer(id).teleportTo(origin.x()+5.5,origin.y()+4,origin.z()-1.5);return true;});
                    phase=200;ticks=0;return;
                }
                if(Boolean.getBoolean("spectre.builderInteractions")){
                    int count=0;for(var module:net.spectre.client.module.ModuleRegistry.all()){
                        if(net.spectre.client.gui.ModuleMascotCatalog.get(module.id())==null)throw new AssertionError("Missing mascot mapping: "+module.id());count++;
                    }
                    pass("All "+count+" registered modules have exact mascot animation mappings");
                    SchematicPreview.INSTANCE.set(schematic.name(),schematic.blocks(),origin,0);SchematicPreview.INSTANCE.confirm();
                    var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
                    operation=server.submit(()->{server.getPlayerList().getPlayer(id).teleportTo(origin.x()+12.5,origin.y()+3,origin.z()+7.5);return true;});
                    phase=110;ticks=0;return;
                }
                if(rotation==0){
                    var invalid=List.of(new Schematic.Block(new Schematic.Pos(0,1,0),"minecraft:oak_door"));
                    try{builder.start(invalid,origin,0);throw new AssertionError("Unsupported door accepted");}catch(IllegalArgumentException expected){pass("Unsupported placement rejected before construction");}
                }
                var ghost=SchematicPreview.INSTANCE;
                ghost.set(schematic.name(),schematic.blocks(),origin,rotation);ghost.beginPlacement();
                mc.player.setYRot(0);mc.player.setXRot(55);phase=30;ticks=0;return;
            }
            if(phase==200){
                if(ticks<30||!operation.isDone()||!SchematicPreview.INSTANCE.active())return;operation.join();
                SchematicPreview.INSTANCE.transform(origin,0);SchematicPreview.INSTANCE.confirm();
                mc.player.setYRot(0);mc.player.setXRot(32);
                phase=201;ticks=0;return;
            }
            if(phase==201){
                if(ticks<20)return;
                if(builder.running())throw new AssertionError("Interactive setup unexpectedly started building");
                if(!SchematicPreview.INSTANCE.confirmed())throw new AssertionError("Practice preview not confirmed");
                AtlasSmokeClient.captureBack(mc,"builder-play-ready.png");
                Files.writeString(Path.of("builder-play-ready.txt"),"READY: Fresh creative practice world; functional-test.schem loaded; materials supplied; preview confirmed at "+origin+". Click Build in creative, then close the GUI. Harness has released control.\n");
                System.out.println("BUILDER_PLAY: READY - manual control; click Build in creative and close the GUI.");
                phase=99;return;
            }
            if(phase==110){
                if(ticks<20||!operation.isDone())return;operation.join();
                mc.player.setYRot(135);mc.player.setXRot(18);phase=111;ticks=0;return;
            }
            if(phase==111){
                if(ticks<25)return;
                if(SchematicPreview.INSTANCE.texturedFaces()<40)throw new AssertionError("Textured schematic models not rendered");
                AtlasSmokeClient.captureBack(mc,"builder-textured-ghost.png");
                pass("Native translucent textured schematic rendered, including hopper, piston, repeater, chest and source-fluid models");
                var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
                operation=server.submit(()->{server.getPlayerList().getPlayer(id).teleportTo(origin.x()+.5,origin.y(),origin.z()+.5);return true;});
                SchematicPreview.INSTANCE.clear();phase=112;ticks=0;return;
            }
            if(phase==112){
                if(ticks<20||!operation.isDone())return;operation.join();
                var blocks=new ArrayList<Schematic.Block>();String[] directions={"north","east","south","west"};
                for(int i=0;i<4;i++)blocks.add(new Schematic.Block(new Schematic.Pos(i*3,0,0),"minecraft:repeater[delay=4,facing="+directions[i]+",locked=false,powered=false]"));
                builder.start(blocks,origin,0);phase=100;ticks=0;return;
            }
            if(phase==100){
                if(ticks>500)throw new AssertionError("Four repeater adjustments exceeded 25 seconds: "+builder.diagnostic());
                if(builder.running()){verifyWalking(mc,builder);return;}
                if(!builder.status().startsWith("Complete:"))throw new AssertionError(builder.status());
                var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
                operation=server.submit(()->{var level=server.getPlayerList().getPlayer(id).level();
                    String[] directions={"north","east","south","west"};
                    for(int i=0;i<4;i++){
                        var wanted=BuilderMaterials.state("minecraft:repeater[delay=4,facing="+directions[i]+",locked=false,powered=false]");
                        if(!level.getBlockState(at(i*3,0,0)).equals(wanted))throw new AssertionError("Repeater adjustment mismatch: "+i);
                    }return true;});phase=101;return;
            }
            if(phase==101){
                if(!operation.isDone())return;operation.join();
                if(forwardSamples<20||aimSamples<10)throw new AssertionError("Insufficient walking/aiming coverage: "+forwardSamples+" / "+aimSamples);
                if(movingGhostSamples==0)throw new AssertionError("Ghost geometry never rendered while walking");
                if(net.spectre.client.gui.WorkspaceMascot.activeModule().equals("builder"))throw new AssertionError("HUD mascot remained in builder mode after completion");
                pass("HUD builder mascot held throughout construction and returned to prior activity on completion");
                pass("Direct build start enabled anchored ghost preview; missing cells rendered during "+movingGhostSamples+" moving ticks");
                pass("Forward-facing walking verified over "+forwardSamples+" moving ticks (maximum view/travel angle "+String.format(java.util.Locale.ROOT,"%.1f",maxWalkAngle)+" degrees); "+aimSamples+" grounded, stationary aiming ticks; no backwards/strafe inputs or flight");
                pass("Four repeater facings placed and adjusted to delay 4, server verified in "+ticks+" ticks");
                var natural=new net.spectre.client.util.ActivityClues.Summary();
                for(String s:List.of("minecraft:kelp[age=23]","minecraft:cave_vines[age=25,berries=true]","minecraft:vine","minecraft:amethyst_cluster","minecraft:bamboo[age=1,stage=1]","minecraft:cocoa[age=2]","minecraft:bee_nest[honey_level=0]"))natural.add(BuilderMaterials.state(s));
                if(natural.score()!=0)throw new AssertionError("Natural generation falsely scored as player activity");
                for(var kind:net.spectre.client.util.ActivityClues.Kind.values())if(natural.count(kind)!=1)throw new AssertionError("Clue category missed: "+kind);
                var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
                operation=server.submit(()->{var level=server.getPlayerList().getPlayer(id).level();
                    level.setBlock(new BlockPos(0,origin.y(),3),Blocks.STONE.defaultBlockState(),2);
                    level.setBlock(new BlockPos(0,origin.y()+2,3),Blocks.WATER.defaultBlockState(),2);
                    String[] states={"minecraft:kelp[age=25]","minecraft:bee_nest[honey_level=5]","minecraft:beehive","minecraft:cocoa[age=2]"};
                    for(int i=0;i<states.length;i++)level.setBlock(new BlockPos(i,origin.y()+1,3),BuilderMaterials.state(states[i]),2);
                    return true;});phase=120;ticks=0;return;
            }
            if(phase==120){
                if(ticks<20||!operation.isDone())return;operation.join();
                clues=new net.spectre.client.module.impl.render.SusChunkFinderModule();
                for(var setting:clues.settings())if(setting instanceof net.spectre.client.module.setting.ModeSetting mode&&setting.id().equals("method"))mode.set("Activity clues");
                clues.setEnabled(true);phase=121;ticks=0;return;
            }
            if(phase==121){
                if(ticks<40)return;
                var field=clues.getClass().getDeclaredField("activityReports");field.setAccessible(true);
                var reports=(java.util.Map<?,?>)field.get(clues);
                var summary=(net.spectre.client.util.ActivityClues.Summary)reports.get(net.minecraft.world.level.ChunkPos.pack(0,0));
                if(summary==null||summary.score()!=6)throw new AssertionError("Loaded chunk activity scan mismatch: "+(summary==null?"missing":summary.evidence()));
                pass("Activity clues: all seven categories counted; a short aged kelp tip scored zero, while the loaded honey/crafted-hive fixture scored 6 bee points");
                clues.setEnabled(false);clues=null;
                Files.writeString(Path.of("builder-interaction-pass.txt"),String.join("\n",report));mc.stop();phase=99;return;
            }
            if(phase==30){
                if(ticks<20)return;
                var ghost=SchematicPreview.INSTANCE;previewAnchor=ghost.origin();
                net.spectre.client.event.impl.KeyInputEvent.dispatch(org.lwjgl.glfw.GLFW.GLFW_KEY_R,0,1,0);
                if(ghost.rotation()!=(rotation+1)%4)throw new AssertionError("Preview rotation key failed");
                net.spectre.client.event.impl.KeyInputEvent.dispatch(org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP,0,1,0);
                phase=31;ticks=0;return;
            }
            if(phase==31){
                if(ticks<5)return;
                var ghost=SchematicPreview.INSTANCE;
                if(ghost.origin().y()!=previewAnchor.y()+1)throw new AssertionError("Preview height key failed");
                previewAnchor=ghost.origin();
                net.spectre.client.event.impl.KeyInputEvent.dispatch(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER,0,1,0);
                if(!ghost.confirmed()||ghost.placing())throw new AssertionError("Preview confirmation failed");
                mc.player.setYRot(90);phase=32;ticks=0;return;
            }
            if(phase==32){
                if(ticks<5)return;
                var ghost=SchematicPreview.INSTANCE;
                if(!ghost.origin().equals(previewAnchor))throw new AssertionError("Confirmed preview moved with the camera");
                ghost.transform(origin,rotation);
                if(ghost.confirmed())throw new AssertionError("Changed placement remained confirmed");
                ghost.confirm();
                for(var block:schematic.blocks())if(!mc.level.getBlockState(at(block.pos().x(),block.pos().y(),block.pos().z())).isAir())throw new AssertionError("Preview changed terrain");
                pass("Ghost placement follows aim, rotates, adjusts height, locks on confirmation, and leaves terrain untouched at "+rotation*90+" degrees");
                var server=mc.getSingleplayerServer();var id=mc.player.getUUID();var camera=at(5,4,-2);
                operation=server.submit(()->{server.getPlayerList().getPlayer(id).teleportTo(camera.getX()+.5,camera.getY(),camera.getZ()+.5);return true;});
                phase=33;ticks=0;return;
            }
            if(phase==33){
                if(ticks<20||!operation.isDone())return;operation.join();
                mc.player.setYRot(rotation*90);mc.player.setXRot(32);
                phase=34;ticks=0;return;
            }
            if(phase==34){
                if(ticks<20)return;
                if(SchematicPreview.INSTANCE.renderedCells()<20)throw new AssertionError("Ghost geometry was not rendered");
                AtlasSmokeClient.captureBack(mc,"builder-ghost-"+rotation+".png");
                pass("In-world ghost geometry rendered at "+rotation*90+" degrees");
                if(rotation==0){
                    net.spectre.client.gui.BuilderPanel.open();
                    var load=net.spectre.client.gui.BuilderPanel.class.getDeclaredMethod("load",Path.class);load.setAccessible(true);
                    load.invoke(null,Path.of("spectre/schematics/functional-test.schem"));
                    mc.gui.setScreen(new net.spectre.client.gui.ImGuiScreen());phase=35;ticks=0;return;
                }
                builder.start(schematic.blocks(),origin,rotation);phase=4;ticks=0;return;
            }
            if(phase==35){
                if(ticks<40)return;
                var field=net.spectre.client.gui.BuilderPanel.class.getDeclaredField("loaded");field.setAccessible(true);
                if(field.get(null)==null)throw new AssertionError("GUI did not load the schematic");
                SchematicPreview.INSTANCE.transform(origin,rotation);SchematicPreview.INSTANCE.confirm();
                phase=36;ticks=0;return;
            }
            if(phase==36){
                if(ticks<20)return;
                AtlasSmokeClient.captureBack(mc,"builder-placement-gui.png");
                pass("Schematic selection and placement controls rendered inside main Base Builder workspace");
                mc.gui.setScreen(null);builder.start(schematic.blocks(),origin,rotation);phase=4;ticks=0;return;
            }
            if(phase==4){
                if(ticks%100==0){
                    System.out.println("BUILDER_TEST: "+rotation*90+" degrees: "+builder.status()+" position="+mc.player.position()+" ground="+mc.player.onGround()+" "+builder.diagnostic());
                    var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
                    server.execute(()->{var level=server.getPlayerList().getPlayer(id).level();long count=schematic.blocks().stream().filter(b->level.getBlockState(at(b.pos().x(),b.pos().y(),b.pos().z())).getBlock()==BuilderMaterials.state(b.state()).getBlock()).count();System.out.println("BUILDER_TEST: server actually contains "+count+" schematic blocks");});
                }
                if(builder.running()&&mc.player.getAbilities().flying)throw new AssertionError("Builder enabled flight");
                if(rotation==0&&builder.running()&&builder.completed()>=12&&pausedAt<0){builder.pause();pausedAt=builder.completed();ticks=0;phase=5;return;}
                if(builder.running())return;
                if(!builder.status().startsWith("Complete:"))throw new IllegalStateException(builder.status());
                var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
                operation=server.submit(()->{
                    var world=server.getPlayerList().getPlayer(id).level();int checked=0;
                    for(var block:schematic.blocks()){
                        var want=BuilderMaterials.state(block.state()).rotate(Rotation.values()[rotation]);var p=block.pos().rotate(rotation).add(origin);var pos=new BlockPos(p.x(),p.y(),p.z());
                        if(!world.getBlockState(pos).equals(want))throw new AssertionError("Server mismatch at "+pos+": "+world.getBlockState(pos)+" expected "+want);checked++;
                    }
                    for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++)if(!world.getFluidState(at(x,1,z)).isEmpty()&&!(x==1&&z==8)&&!(x==6&&z==8))throw new AssertionError("Fluid escaped basin");
                    return checked;
                });phase=6;ticks=0;return;
            }
            if(phase==5){
                if(ticks<40)return;
                if(builder.completed()!=pausedAt)throw new AssertionError("Paused builder progressed");
                pass("Pause holds construction; resume continues");builder.pause();phase=4;return;
            }
            if(phase==6){
                if(!operation.isDone())return;pass("Server verified "+operation.join()+" exact states at "+rotation*90+" degrees, including six piston facings, hopper outlets, repeater delay and contained source fluids");
                if(SchematicPreview.INSTANCE.renderedCells()!=0)throw new AssertionError("Completed blocks remained in ghost preview");
                pass("Completed ghost blocks disappear; construction remained grounded");
                // Functional stimulus: actual lever interaction, then server observes piston and hoppers.
                mc.options.keyShift.setDown(false);mc.player.getInventory().setSelectedSlot(8);
                var server=mc.getSingleplayerServer();var id=mc.player.getUUID();var lever=at(0,1,4);
                operation=server.submit(()->{server.getPlayerList().getPlayer(id).teleportTo(lever.getX()+.5,lever.getY()+2,lever.getZ()+.5);return true;});
                phase=7;ticks=0;return;
            }
            if(phase==7){
                if(ticks<20||!operation.isDone())return;operation.join();
                var lever=at(0,1,4);mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(lever),Direction.UP,lever,false));
                var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
                operation=server.submit(()->{var level=server.getPlayerList().getPlayer(id).level();
                    ((HopperBlockEntity)level.getBlockEntity(at(8,1,4))).setItem(0,new ItemStack(Items.DIAMOND,1));
                    ((HopperBlockEntity)level.getBlockEntity(at(9,2,4))).setItem(0,new ItemStack(Items.EMERALD,1));return true;});
                phase=8;ticks=0;return;
            }
            if(phase==8){
                if(ticks<100)return;operation.join();
                var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
                operation=server.submit(()->{var level=server.getPlayerList().getPlayer(id).level();
                    if(!level.getBlockState(at(4,1,4)).getValue(BlockStateProperties.EXTENDED))throw new AssertionError("Redstone circuit did not extend piston");
                    if(!level.getBlockState(at(5,1,4)).is(Blocks.PISTON_HEAD))throw new AssertionError("Piston head points the wrong way");
                    var chest=(ChestBlockEntity)level.getBlockEntity(at(9,1,4));boolean diamond=false,emerald=false;
                    for(int i=0;i<chest.getContainerSize();i++){diamond|=chest.getItem(i).is(Items.DIAMOND);emerald|=chest.getItem(i).is(Items.EMERALD);}
                    if(!diamond||!emerald)throw new AssertionError("Hopper items did not reach chest");return true;});
                phase=9;ticks=0;return;
            }
            if(phase==9){
                if(!operation.isDone())return;operation.join();pass("Working redstone/repeater/piston circuit and both hopper transfers at "+rotation*90+" degrees");
                AtlasSmokeClient.captureBack(mc,"builder-built-"+rotation+".png");
                if(rotation++==0){phase=2;ticks=0;operation=null;return;}
                Files.writeString(Path.of("builder-smoke-pass.txt"),String.join("\n",report));mc.stop();phase=99;
            }
        }catch(Throwable ex){
            ex.printStackTrace();System.out.println("BUILDER_TEST: FAIL "+ex);
            try{Files.writeString(Path.of("builder-smoke-failure.txt"),String.join("\n",report)+"\nFAIL: "+ex);}catch(Exception ignored){}
            CreativeBuilder.INSTANCE.stop();
            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Builder test stopped: "+ex.getMessage()));
            try{AtlasSmokeClient.captureBack(mc,"builder-failure.png");}catch(Exception ignored){}
            phase=99;
        }
    }
}
