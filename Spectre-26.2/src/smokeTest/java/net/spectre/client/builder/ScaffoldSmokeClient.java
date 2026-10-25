package net.spectre.client.builder;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.spectre.client.config.ThemeStore;
import net.spectre.client.module.ModuleRegistry;

/** Native placement/climb/cleanup test. Server writes only the initial test terrain. */
public final class ScaffoldSmokeClient implements ClientModInitializer {
    private int phase,ticks;private long started=System.nanoTime();
    private CompletableFuture<?> task;private List<Schematic.Block> blocks;
    private final List<String> report=new ArrayList<>();
    private final ScaffoldAccess bridge=new ScaffoldAccess();
    private boolean usedSupports,climbed;private double maxHeight=Double.NEGATIVE_INFINITY;private int maxSupports;
    private void next(int phase){this.phase=phase;ticks=0;}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public void onInitializeClient(){ThemeStore.get().communityPopup.set(false);ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
    private void supply(Minecraft mc,boolean scaffold){
        var stone=new ItemStack(Items.STONE,64);mc.player.getInventory().setItem(0,stone);mc.gameMode.handleCreativeModeItemAdd(stone,36);
        var stack=scaffold?new ItemStack(Items.SCAFFOLDING,64):ItemStack.EMPTY;
        mc.player.getInventory().setItem(1,stack);mc.gameMode.handleCreativeModeItemAdd(stack,37);mc.player.getInventory().setSelectedSlot(0);
    }
    private void tick(Minecraft mc){try{
        if(phase==99)return;if(System.nanoTime()-started>900_000_000_000L)throw new AssertionError("Scaffolding test exceeded fifteen minutes");
        if(mc.gui.overlay()!=null)return;ticks++;
        if(phase==0){
            if(ticks<60)return;mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(2);mc.options.renderDistance().set(6);mc.options.simulationDistance().set(5);mc.options.setCameraType(CameraType.FIRST_PERSON);
            for(var m:ModuleRegistry.all())m.setEnabled(false);
            mc.createWorldOpenFlows().createFreshLevel("scaffolding-test-"+System.currentTimeMillis(),new LevelSettings("Scaffolding verification",GameType.CREATIVE,new LevelSettings.DifficultySettings(net.minecraft.world.Difficulty.PEACEFUL,false,false),true,WorldDataConfiguration.DEFAULT),new WorldOptions(2611262L,false,false),r->r.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.gui.screen());next(1);return;
        }
        if(mc.player==null||mc.level==null)return;
        require(!mc.player.getAbilities().flying,"Scaffolding enabled flight");
        if(phase==1){
            if(ticks<40)return;var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
            task=server.submit(()->{var p=server.getPlayerList().getPlayer(id);var l=p.level();
                for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++)for(int y=-61;y<=-36;y++)l.setBlock(new BlockPos(x,y,z),y==-61?Blocks.SMOOTH_STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
                for(int y=-60;y<=-58;y++)l.setBlock(new BlockPos(9,y,9),Blocks.SCAFFOLDING.defaultBlockState(),3);
                p.teleportTo(4.5,-60,6.5);return true;});next(2);return;
        }
        if(phase==2){
            if(!task.isDone()||ticks<30)return;task.join();supply(mc,false);
            if("1".equals(System.getenv("SPECTRE_SCAFFOLD_BRIDGE_ONLY"))){report.add("Bridge-only diagnostic run; tall-build fixture skipped");next(5);return;}
            CreativeBuilder.INSTANCE.start(List.of(new Schematic.Block(new Schematic.Pos(0,10,0),"minecraft:stone")),new Schematic.Pos(0,-60,0),0);next(3);return;
        }
        if(phase==3){
            if(CreativeBuilder.INSTANCE.running()){require(ticks<200,"Missing-scaffolding check did not stop");return;}
            require(CreativeBuilder.INSTANCE.status().startsWith("Stopped:"),"Missing access unexpectedly succeeded");
            require(CreativeBuilder.INSTANCE.temporaryScaffolding()==0,"Created scaffolding without inventory");
            report.add("Missing scaffolding stops cleanly without creating access blocks");
            supply(mc,true);blocks=new ArrayList<>();for(int y=0;y<12;y++)for(int x:new int[]{0,4})for(int z:new int[]{0,4})blocks.add(new Schematic.Block(new Schematic.Pos(x,y,z),"minecraft:stone"));
            CreativeBuilder.INSTANCE.automaticScaffolding(true);CreativeBuilder.INSTANCE.removeScaffolding(true);
            CreativeBuilder.INSTANCE.start(blocks,new Schematic.Pos(0,-60,0),0);next(4);return;
        }
        if(phase==4){
            var b=CreativeBuilder.INSTANCE;usedSupports|=b.temporaryScaffolding()>0;maxSupports=Math.max(maxSupports,b.temporaryScaffolding());maxHeight=Math.max(maxHeight,mc.player.getY());climbed|=mc.player.getY()>-56;
            if(ticks%100==0)System.out.println("SCAFFOLD_TEST: "+b.completed()+"/"+b.total()+" feet="+mc.player.position()+" ground="+mc.player.onGround()+" motion="+mc.player.getDeltaMovement()+" "+b.status()+" "+b.diagnostic());
            if(b.running())return;
            require(b.status().startsWith("Complete:"),b.status()+" "+b.diagnostic());require(usedSupports&&climbed,"Tall build never used real scaffolding/climbing");
            require(maxSupports<=20,"Did not reuse the two access columns for four nearby structures: "+maxSupports);
            var server=mc.getSingleplayerServer();task=server.submit(()->{var l=server.overworld();for(var s:blocks){var p=s.pos();require(l.getBlockState(new BlockPos(p.x(),p.y()-60,p.z())).is(Blocks.STONE),"Tall schematic differs from planned state");}
                for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)for(int y=-60;y<=-40;y++)require(!l.getBlockState(new BlockPos(x,y,z)).is(Blocks.SCAFFOLDING),"Owned support left after cleanup");
                for(int y=-60;y<=-58;y++)require(l.getBlockState(new BlockPos(9,y,9)).is(Blocks.SCAFFOLDING),"Touched pre-existing scaffolding");return true;});next(5);return;
        }
        if(phase==5){
            if(!task.isDone())return;task.join();if(blocks!=null)report.add("Four isolated 12-high columns (48 blocks) built with shared access (peak "+maxSupports+" scaffolding blocks); highest feet Y "+maxHeight+"; exact target states preserved; own supports removed; pre-existing tower untouched");
            var server=mc.getSingleplayerServer();var id=mc.player.getUUID();task=server.submit(()->{var p=server.getPlayerList().getPlayer(id);var l=p.level();
                for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++)for(int y=l.getMinY();y<=-36;y++)l.setBlock(new BlockPos(x,y,z),(x<=-1||x>=5)&&y==-61?Blocks.SMOOTH_STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
                p.teleportTo(5.5,-60,2.5);return true;});next(6);return;
        }
        if(phase==6){
            if(!task.isDone()||ticks<30)return;task.join();supply(mc,true);
            var target=new BlockPos(7,-52,0);require(bridge.request(mc,List.of(target),Set.of(target)),"Could not plan the far-shore access tower");next(60);return;
        }
        if(phase==60){
            if(bridge.active()){bridge.tick(mc);return;}
            var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
            task=server.submit(()->{var p=server.getPlayerList().getPlayer(id);require(p.level().getBlockState(new BlockPos(5,-60,0)).is(Blocks.SCAFFOLDING),"Far-shore tower not built at the planned access column");p.teleportTo(-3.5,-60,.5);return true;});next(61);return;
        }
        if(phase==61){
            if(!task.isDone()||ticks<30)return;task.join();
            var target=new BlockPos(5,-53,0);require(bridge.request(mc,List.of(target),Set.of(target)),"Could not plan a supported connection to the existing far-shore tower");next(7);return;
        }
        if(phase==7){
            if(bridge.active()){bridge.tick(mc);if(ticks%50==0)System.out.println("SCAFFOLD_BRIDGE: "+bridge.status()+" feet="+mc.player.position()+" velocity="+mc.player.getDeltaMovement()+" jump="+mc.options.keyJump.isDown()+" "+bridge.diagnostic());return;}
            require(mc.player.getX()>4&&mc.player.getY()>-55,"Did not cross between the two towers");
            var server=mc.getSingleplayerServer();task=server.submit(()->{var l=server.overworld();int horizontal=0;for(int x=0;x<5;x++)for(int z=-3;z<=3;z++)for(int y=-58;y<-50;y++){
                var s=l.getBlockState(new BlockPos(x,y,z));if(s.is(Blocks.SCAFFOLDING)){require(s.getValue(ScaffoldingBlock.DISTANCE)<7,"Unstable span");horizontal++;}
            }require(horizontal>=5,"No complete horizontal scaffold bridge was built");
                for(int x=-1;x<=5;x++)require(l.getBlockState(new BlockPos(x,-55,0)).is(Blocks.SCAFFOLDING),"Connection between access towers is incomplete");
                require(l.getBlockState(new BlockPos(-1,-60,0)).is(Blocks.SCAFFOLDING)&&l.getBlockState(new BlockPos(5,-60,0)).is(Blocks.SCAFFOLDING),"Span did not connect two native access towers");return true;});next(8);return;
        }
        if(phase==8){if(!task.isDone())return;task.join();require(bridge.beginCleanup(mc),"Bridge cleanup did not start");next(9);return;}
        if(phase==9){
            if(bridge.active()){bridge.tick(mc);return;}
            require(bridge.count()==0,"Temporary bridge was not removed");require(mc.player.getY()==-60,"Did not descend to the original shore before cleanup");
            report.add("Two native access towers connected by an elevated span over a five-block gap; every span cell stable; crossed between towers without flight; descended and removed both towers and their cross-supported bridge");
            require(!mc.options.keyShift.isDown()&&!mc.options.keyJump.isDown()&&!mc.options.keyUp.isDown(),"Movement keys left held after cleanup");
            Files.writeString(Path.of("scaffold-smoke-pass.txt"),String.join("\n",report));phase=99;mc.stop();
        }
    }catch(Throwable e){e.printStackTrace();try{Files.writeString(Path.of("scaffold-smoke-failure.txt"),e.toString());CreativeBuilder.INSTANCE.stop();bridge.interrupt(mc);}catch(Throwable ignored){}phase=99;mc.stop();}}
}
