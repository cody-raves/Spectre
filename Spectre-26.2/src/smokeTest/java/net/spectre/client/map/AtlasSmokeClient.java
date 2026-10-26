package net.spectre.client.map;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.opengl.GL33.*;

/** Runs only with -PsmokeTest, in the separate development run directory. */
public final class AtlasSmokeClient implements ClientModInitializer {
    private int ticks,phase,phaseTicks;
    private long started=System.nanoTime();
    private HiveMapScreen screen;
    private int guiIndex;
    private net.spectre.client.gui.ImGuiScreen moduleScreen;
    private static final String[] GUI_SHOTS={"modules","combat","movement","render","client","misc","donut","base-raid","base-builder","builder-workspace","visuals","hud-editor","appearance","profiles","friends"};
    public void onInitializeClient(){
        if(!Boolean.getBoolean("spectre.smoke"))throw new IllegalStateException("Smoke build is not distributable");

        if(ShowcaseCapture.ENABLED){
            net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(mc->ShowcaseCapture.finish());
        }
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }
    private static Object field(Object target,String name)throws Exception{
        var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);
    }
    private static void setField(Object target,String name,Object value)throws Exception{
        var f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);
    }
    private void selectGui()throws Exception{
        Object renderer=field(moduleScreen,"clickGui");
        if(guiIndex==0){
            var search=renderer.getClass().getDeclaredMethod("searchModules",String.class);search.setAccessible(true);
            var all=(java.util.List<?>)search.invoke(renderer,"");
            if(all.size()!=net.spectre.client.module.ModuleRegistry.all().size())throw new IllegalStateException("Empty search hides modules");
            if(!search.invoke(renderer,"FLY").equals(search.invoke(renderer,"fly")))throw new IllegalStateException("Search must ignore case");
            System.out.println("GUI_SMOKE: all "+all.size()+" modules visible; case-insensitive search passed");
        }
        String shot=GUI_SHOTS[guiIndex];
        ShowcaseCapture.mark("GUI: "+shot);
        var builder=net.spectre.client.gui.BuilderPanel.class;
        if(shot.equals("builder-workspace")){
            net.spectre.client.gui.BuilderPanel.open();
            Path sample=net.spectre.client.gui.BuilderPanel.folder().resolve("small-stash-test.schem");
            if(!Files.isRegularFile(sample))try(var fixture=AtlasSmokeClient.class.getResourceAsStream("/builder/small-stash-test.schem")){
                if(fixture==null)throw new IllegalStateException("Missing builder preview fixture");
                Files.copy(fixture,sample);
            }
            var load=builder.getDeclaredMethod("load",Path.class);load.setAccessible(true);load.invoke(null,sample);
        }
        String category=switch(shot){case "combat"->"COMBAT";case "movement"->"MOVEMENT";case "render"->"RENDER";case "client"->"CLIENT";case "misc"->"MISC";case "donut"->"DONUT";case "base-raid"->"BASE_RAID";case "base-builder","builder-workspace"->"BASE_BUILDER";case "visuals"->"VISUALS";default->null;};
        String module=switch(shot){case "combat"->"kill-aura";case "movement"->"fly";case "render"->"fullbright";case "client"->"hud";case "misc"->"auto-eat";case "donut"->"spawner-notifier";case "base-raid"->"quantum";case "base-builder","builder-workspace"->"builder";case "visuals"->"atmosphere";default->null;};
        setField(renderer,"workspaceCategory",net.spectre.client.module.ModuleCategory.values().stream().filter(c->c.name().equals(category)).findFirst().orElse(null));
        var selected=module==null?null:net.spectre.client.module.ModuleRegistry.resolve(module);
        setField(renderer,"workspaceSelected",selected);setField(renderer,"workspaceDetails",selected);
        String tab=switch(shot){case "hud-editor"->"HUD";case "appearance"->"THEME";case "profiles"->"CONFIG";case "friends"->"FRIENDS";default->"MODULES";};
        var current=field(renderer,"tab");
        var switchTab=renderer.getClass().getDeclaredMethod("switchTab",current.getClass());switchTab.setAccessible(true);
        for(Object value:current.getClass().getEnumConstants())if(((Enum<?>)value).name().equals(tab))switchTab.invoke(renderer,value);
    }
    private void tick(Minecraft mc){
        try{
            if(System.nanoTime()-started>240_000_000_000L)throw new IllegalStateException("Smoke test timed out at phase "+phase);
            if(++ticks<40||mc.gui.overlay()!=null)return;
            if(phase==0){
                if(mc.gui.screen() instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen){
                    mc.gui.setScreen(new net.minecraft.client.gui.screens.TitleScreen());phaseTicks=0;return;
                }
                if(phaseTicks==0)ShowcaseCapture.mark("Main menu");
                if(++phaseTicks<(ShowcaseCapture.ENABLED?160:60))return;
                phaseTicks=0;
                System.out.println("BRANDING_SMOKE: menu="+mc.gui.screen().getClass().getName());
                capture(mc,"spectre-menu.png");
                if(Boolean.getBoolean("spectre.menuPreview")){
                    Files.writeString(Path.of("branding-menu-pass.txt"),"PASS: Spectre startup and main menu rendered\n");
                    System.out.println("BRANDING_SMOKE: PASS startup and main menu");mc.stop();phase=6;return;
                }
                phase=1;
                ShowcaseCapture.mark("Entering a local world");
                System.out.println("ATLAS_SMOKE: menu reached; creating disposable world");
                mc.options.renderDistance().set(6);mc.options.simulationDistance().set(5);
                mc.createWorldOpenFlows().createFreshLevel("atlas-smoke-"+System.currentTimeMillis(),
                    new LevelSettings("Atlas disposable test",GameType.CREATIVE,LevelSettings.DifficultySettings.DEFAULT,true,WorldDataConfiguration.DEFAULT),
                    new WorldOptions(2611262L,false,false),
                    registry->registry.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL).value().createWorldDimensions(),mc.gui.screen());
                return;
            }
            if(mc.level==null||mc.player==null)return;
            if(phase==1){
                if(phaseTicks==0)ShowcaseCapture.mark("Gameplay HUD");
                if(++phaseTicks<(ShowcaseCapture.ENABLED?120:80))return;capture(mc,"spectre-hud.png");
                moduleScreen=new net.spectre.client.gui.ImGuiScreen();mc.gui.setScreen(moduleScreen);
                phase=7;phaseTicks=0;selectGui();return;
            }
            if(phase==7){
                if(++phaseTicks<(ShowcaseCapture.ENABLED?100:45))return;
                var disabled=net.spectre.client.gui.ItemIcons.class.getDeclaredField("disabled");disabled.setAccessible(true);
                if(disabled.getBoolean(null))throw new IllegalStateException("HUD item icon rendering failed");
                capture(mc,"spectre-gui-"+GUI_SHOTS[guiIndex]+".png");
                if(++guiIndex<GUI_SHOTS.length){selectGui();phaseTicks=0;return;}
                screen=new HiveMapScreen();mc.gui.setScreen(screen);phase=2;phaseTicks=0;
                ShowcaseCapture.mark("Hive Map: surface");
            }
            if(phase>=2&&phase<=4){
                phaseTicks++;
                var atlas=(LocalAtlas)field(screen,"atlas");var gpu=(AtlasGpu)field(screen,"gpu");
                var error=(String)field(screen,"renderError");
                if(!atlas.error.isEmpty()||!error.isEmpty())throw new IllegalStateException(atlas.error+error);
                var camera=(AtlasCamera)field(screen,"camera");
                if(!ShowcaseCapture.ENABLED&&phaseTicks>60&&phaseTicks<100){camera.orbit(2,0);camera.pan(.5,0);camera.scroll(.01);}
                if(phaseTicks<240||gpu.size()<9)return;
                capture(mc,"atlas-world-"+phase+".png");
                System.out.println("ATLAS_SMOKE: phase "+phase+" rendered "+gpu.size()+" chunks without errors");
                if(phase<4){
                    var mode=HiveMapScreen.class.getDeclaredField("layer");mode.setAccessible(true);mode.set(screen,phase==2?AtlasMesh.Layer.CAVES:AtlasMesh.Layer.SLICE);
                    ((int[])field(screen,"ceiling"))[0]=32;camera.y=20;camera.pitch=1.12;
                    phase++;phaseTicks=0;
                    ShowcaseCapture.mark(phase==3?"Hive Map: underground caves":"Hive Map: Y slice / cutaway");
                }else{
                    mc.gui.setScreen(null);phase=5;phaseTicks=0;
                    ShowcaseCapture.mark("Return to gameplay HUD");
                    if(gpu.size()!=0)throw new IllegalStateException("Closing map retained chunk buffers");
                }
            }else if(phase==5&&++phaseTicks>(ShowcaseCapture.ENABLED?100:30)){
                System.out.println("ATLAS_SMOKE: PASS surface/caves/slice, camera motion, close and cleanup");
                Files.writeString(Path.of("atlas-smoke-pass.txt"),"PASS: 26.2 world, surface/caves/slice, camera motion, close and cleanup\n");
                mc.stop();phase=6;
            }
        }catch(Throwable ex){ex.printStackTrace();System.out.println("ATLAS_SMOKE: FAIL");mc.stop();phase=6;}
    }
    private static void capture(Minecraft mc,String filename)throws Exception{
        capture(mc,filename,GL_FRONT);
    }
    public static void captureBack(Minecraft mc,String filename)throws Exception{
        capture(mc,filename,GL_BACK);
    }
    private static void capture(Minecraft mc,String filename,int buffer)throws Exception{
        captureRegion(mc,filename,buffer,0,0,mc.getWindow().getWidth(),mc.getWindow().getHeight());
    }
    public static void captureRegionBack(Minecraft mc,String filename,int left,int top,int width,int height)throws Exception{
        captureRegion(mc,filename,GL_BACK,left,top,width,height);
    }
    private static void captureRegion(Minecraft mc,String filename,int buffer,int left,int top,int w,int h)throws Exception{
        if(ShowcaseCapture.ENABLED)return;
        int previous=glGetInteger(GL_READ_FRAMEBUFFER_BINDING),pack=glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING),alignment=glGetInteger(GL_PACK_ALIGNMENT);
        int row=glGetInteger(GL_PACK_ROW_LENGTH),skipX=glGetInteger(GL_PACK_SKIP_PIXELS),skipY=glGetInteger(GL_PACK_SKIP_ROWS);
        var pixels=MemoryUtil.memAlloc(w*h*4);
        try{
            glBindFramebuffer(GL_READ_FRAMEBUFFER,0);glReadBuffer(buffer);glBindBuffer(GL_PIXEL_PACK_BUFFER,0);glPixelStorei(GL_PACK_ALIGNMENT,1);
            glPixelStorei(GL_PACK_ROW_LENGTH,0);glPixelStorei(GL_PACK_SKIP_PIXELS,0);glPixelStorei(GL_PACK_SKIP_ROWS,0);
            glReadPixels(left,mc.getWindow().getHeight()-top-h,w,h,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
            var image=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
            for(int y=0;y<h;y++)for(int x=0;x<w;x++){int p=(y*w+x)*4;image.setRGB(x,h-1-y,((pixels.get(p)&255)<<16)|((pixels.get(p+1)&255)<<8)|(pixels.get(p+2)&255));}
            ImageIO.write(image,"png",Path.of(filename).toFile());
        }finally{MemoryUtil.memFree(pixels);glBindFramebuffer(GL_READ_FRAMEBUFFER,previous);glBindBuffer(GL_PIXEL_PACK_BUFFER,pack);glPixelStorei(GL_PACK_ALIGNMENT,alignment);glPixelStorei(GL_PACK_ROW_LENGTH,row);glPixelStorei(GL_PACK_SKIP_PIXELS,skipX);glPixelStorei(GL_PACK_SKIP_ROWS,skipY);}
    }
}
