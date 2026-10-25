package net.spectre.client.map;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.spectre.client.builder.*;
import net.spectre.client.config.ThemeStore;
import net.spectre.client.module.ModuleRegistry;

/** Disposable creative recording. Target blocks are placed only by the real builder. */
public final class BuilderDemoClient implements ClientModInitializer {
    public static boolean cameraActive;
    public static double cameraAngle=.85;
    private int phase,ticks;
    private long started=System.nanoTime(),orbitStart;
    private CompletableFuture<?> setup;
    private final List<Schematic.Block> plan=new ArrayList<>();
    private final Schematic.Pos origin=new Schematic.Pos(0,-60,0);
    private static final boolean FIRST_PERSON=Boolean.getBoolean("spectre.builderFirstPerson");
    private void block(int x,int y,int z,String state){plan.add(new Schematic.Block(new Schematic.Pos(x,y,z),"minecraft:"+state));}
    public void onInitializeClient(){
        ThemeStore.get().communityPopup.set(false);ThemeStore.get().applyPreset(ThemeStore.PRESETS.getFirst());
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }
    private void tick(Minecraft mc){try{
        if(phase==99)return;
        if(System.nanoTime()-started>900_000_000_000L)throw new IllegalStateException("Demo exceeded fifteen minutes");
        if(mc.gui.overlay()!=null)return;ticks++;
        if(phase==0){
            if(ticks<60)return;
            mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(2);mc.options.renderDistance().set(8);mc.options.simulationDistance().set(5);
            for(var module:ModuleRegistry.all())module.setEnabled(false);
            mc.createWorldOpenFlows().createFreshLevel("builder-demo-"+System.currentTimeMillis(),
                new LevelSettings("Spectre builder demo",GameType.CREATIVE,new LevelSettings.DifficultySettings(net.minecraft.world.Difficulty.PEACEFUL,false,false),true,WorldDataConfiguration.DEFAULT),
                new WorldOptions(2611262L,false,false),r->r.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.gui.screen());
            phase=1;ticks=0;return;
        }
        if(mc.player==null||mc.level==null)return;
        if(phase==1){
            if(ticks<60)return;
            for(int z=0;z<5;z++)for(int x=0;x<5;x++)block(x,0,z,"light_gray_concrete");
            for(int y=1;y<=2;y++)for(int z=0;z<5;z++)for(int x=0;x<5;x++){
                if((x==0||x==4)&&(z==0||z==4))block(x,y,z,"polished_deepslate");
                else if(z==4||x==0||x==4)block(x,y,z,"black_stained_glass");
                else if(z==0&&x!=2)block(x,y,z,"polished_deepslate");
            }
            for(int z=0;z<5;z++)for(int x=0;x<5;x++)block(x,3,z,x==0||x==4||z==0||z==4?"cyan_concrete":"glass");
            var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
            setup=server.submit(()->{
                var p=server.getPlayerList().getPlayer(id);var level=p.level();
                // Only flatten the job site. Every target position remains air.
                for(int x=-15;x<=18;x++)for(int z=-15;z<=18;z++)for(int y=-61;y<=-55;y++)level.setBlock(new BlockPos(x,y,z),y==-61?Blocks.SMOOTH_STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
                // Prepared walking access, wholly outside the schematic's target cells.
                for(int x=-2;x<=6;x++)for(int z=-2;z<=6;z++)if(x==-2||x==6||z==-2||z==6)
                    level.setBlock(new BlockPos(x,-59,z),Blocks.OAK_PLANKS.defaultBlockState(),3);
                for(int x=-1;x<=5;x++)for(int z=-1;z<=5;z++)if(x==-1||x==5||z==-1||z==5)
                    level.setBlock(new BlockPos(x,-60,z),Blocks.OAK_PLANKS.defaultBlockState(),3);
                for(int x:new int[]{-3,7})for(int z:new int[]{-2,6})level.setBlock(new BlockPos(x,-60,z),Blocks.OAK_PLANKS.defaultBlockState(),3);
                for(int z:new int[]{-3,7})for(int x:new int[]{-2,6})level.setBlock(new BlockPos(x,-60,z),Blocks.OAK_PLANKS.defaultBlockState(),3);
                p.teleportTo(7.5,-60,7.5);server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"time set noon");return true;
            });phase=2;ticks=0;return;
        }
        if(phase==2){
            if(ticks<40||!setup.isDone())return;setup.join();
            var materials=new LinkedHashSet<Item>();for(var b:plan)materials.add(BuilderMaterials.state(b.state()).getBlock().asItem());
            int slot=0;for(var item:materials){var stack=new ItemStack(item,64);mc.player.getInventory().setItem(slot,stack);mc.gameMode.handleCreativeModeItemAdd(stack,36+slot++);}
            mc.gui.setScreen(null);SchematicPreview.INSTANCE.set("Spectre starter base",plan,origin,0);SchematicPreview.INSTANCE.confirm();
            mc.options.setCameraType(FIRST_PERSON?net.minecraft.client.CameraType.FIRST_PERSON:net.minecraft.client.CameraType.THIRD_PERSON_BACK);mc.player.setYRot(135);mc.player.setXRot(22);
            cameraActive=!FIRST_PERSON;ShowcaseCapture.begin();phase=3;ticks=0;return;
        }
        if(phase==3){if(ticks<100)return;ShowcaseCapture.mark("Construction");CreativeBuilder.INSTANCE.start(plan,origin,0);phase=4;ticks=0;return;}
        if(phase==4){
            var builder=CreativeBuilder.INSTANCE;
            if(builder.running()){if(ticks%100==0)System.out.println("BUILDER_DEMO: "+builder.completed()+"/"+builder.total()+" "+builder.status());return;}
            if(!builder.status().startsWith("Complete:"))throw new IllegalStateException(builder.status()+" "+builder.diagnostic());
            ShowcaseCapture.mark("Finished base");SchematicPreview.INSTANCE.clear();orbitStart=System.nanoTime();
            if(FIRST_PERSON){var server=mc.getSingleplayerServer();var id=mc.player.getUUID();server.execute(()->server.getPlayerList().getPlayer(id).teleportTo(8.5,-60,-5.5));}
            phase=5;ticks=0;return;
        }
        if(phase==5){
            double elapsed=(System.nanoTime()-orbitStart)/1e9;cameraAngle=.85+elapsed*.45;
            if(FIRST_PERSON){mc.player.setYRot(41);mc.player.setXRot(-5);}
            if(elapsed<12)return;
            AtlasSmokeClient.captureBack(mc,"builder-demo-finished.png");ShowcaseCapture.finish();
            Files.writeString(Path.of("builder-demo-pass.txt"),CreativeBuilder.INSTANCE.status()+"\nActual creative executor; empty job site; no target blocks preplaced; continuous raw capture.\n");
            cameraActive=false;phase=99;mc.stop();
        }
    }catch(Throwable e){e.printStackTrace();try{Files.writeString(Path.of("builder-demo-failure.txt"),e.toString());ShowcaseCapture.finish();}catch(Throwable ignored){}phase=99;mc.stop();}}
}
