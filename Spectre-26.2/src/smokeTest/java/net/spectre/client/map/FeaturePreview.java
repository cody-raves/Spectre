package net.spectre.client.map;

import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.spectre.client.config.ThemeStore;
import net.spectre.client.media.MediaPlayer;
import net.spectre.client.module.*;
import net.spectre.client.module.setting.*;
import net.spectre.client.module.impl.render.*;

/** Local world, client rendering and public media pages only. Never joins a game server. */
final class FeaturePreview {
    private int ticks,stage;
    private long began=System.nanoTime();
    private volatile String videoEvidence="";
    private boolean probeInstalled;
    private final List<String> checks=new ArrayList<>();
    private final AtmosphereModule sky=ModuleRegistry.get(AtmosphereModule.class);
    FeaturePreview(Minecraft mc)throws Exception{
        for(String bad:List.of("javascript:alert(1)","https://youtube.com.example.org/","https://youtube.com@evil.test/","file:///etc/passwd")){
            try{MediaPlayer.normalize(bad);throw new AssertionError("Unsafe media URL accepted");}catch(IllegalArgumentException expected){}
        }
        checks.add("Media address validation rejects executable and spoofed hosts");
        MediaPlayer.normalize("https://www.youtube.com/watch?v=M7lc1UVf-VE");MediaPlayer.normalize("https://soundcloud.com/");
        for(var module:ModuleRegistry.all())module.setEnabled(false);
        ModuleRegistry.get(SpectralHoodModule.class).setEnabled(true);ModuleRegistry.get(FloatingRelicsModule.class).setEnabled(true);
        setting("theme",ModeSetting.class).set("Follow GUI");setting("sky-style",ModeSetting.class).set("Nebula");setting("night-sky",BooleanSetting.class).set(true);sky.setEnabled(true);
        mc.player.setXRot(-70);mc.player.setYRot(0);mc.gui.setScreen(null);
        mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.FANCY);
        setting("star-brightness",NumberSetting.class).set(1.3F);
    }
    private <T extends Setting>T setting(String id,Class<T> type){return type.cast(sky.settings().stream().filter(s->s.id().equals(id)).findFirst().orElseThrow());}
    void tick(Minecraft mc)throws Exception{
        if(stage==99)return;
        if(stage<3||stage==7){mc.player.setXRot(stage==1||stage==2?-35:-80);mc.player.setYRot(stage==1||stage==2?180:0);}
        if(System.nanoTime()-began>600_000_000_000L){Files.writeString(Path.of("feature-preview-limited.txt"),"Browser did not finish within ten minutes\n"+String.join("\n",checks));stage=99;return;}
        if(++ticks<50)return;ticks=0;
        switch(stage){
            case 0 -> {dumpSky();AtlasSmokeClient.captureBack(mc,"feature-sky-teal.png");setting("theme",ModeSetting.class).set("Violet Aurora");setting("sky-style",ModeSetting.class).set("Aurora");stage++;}
            case 1 -> {AtlasSmokeClient.captureBack(mc,"feature-sky-violet.png");ThemeStore.get().accent.setArgb(0xFFFFAA55);setting("theme",ModeSetting.class).set("Follow GUI");setting("sky-style",ModeSetting.class).set("Both");stage++;}
            case 2 -> {AtlasSmokeClient.captureBack(mc,"feature-sky-custom.png");ThemeStore.get().applyPreset(ThemeStore.PRESETS.getFirst());mc.gui.setScreen(new InventoryScreen(mc.player));stage++;}
            case 3 -> {AtlasSmokeClient.captureBack(mc,"feature-ghost-hood.png");mc.gui.screen().onClose();if(Boolean.getBoolean("spectre.skyOnly")){setting("night-sky",BooleanSetting.class).set(false);var server=mc.getSingleplayerServer();server.execute(()->server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"time set noon"));stage=7;return;}MediaPlayer.load("https://www.youtube.com/watch?v=M7lc1UVf-VE");MediaPlayer.open();stage++;}
            case 7 -> {AtlasSmokeClient.captureBack(mc,"feature-sky-day.png");stage=99;Files.writeString(Path.of("sky-preview-ready.txt"),String.join("\n",checks));}
            case 4 -> {
                var f=MediaPlayer.class.getDeclaredField("browser");f.setAccessible(true);var b=(net.dimaskama.mcef.api.MCEFBrowser)f.get(null);
                if(b==null||b.getTextureView()==null)return;
                if(!probeInstalled){
                    b.getCefBrowser().getClient().addDisplayHandler(new org.cef.handler.CefDisplayHandlerAdapter(){
                        @Override public boolean onConsoleMessage(org.cef.browser.CefBrowser browser,org.cef.CefSettings.LogSeverity severity,String text,String source,int line){if(text.startsWith("SPECTRE_MEDIA_CHECK:")){videoEvidence=text;return true;}return false;}
                    });probeInstalled=true;
                }
                b.getCefBrowser().executeJavaScript("(()=>{let v=document.querySelector('video');if(v){v.muted=true;v.play().catch(()=>{});console.log('SPECTRE_MEDIA_CHECK:'+JSON.stringify({ready:v.readyState,time:v.currentTime,paused:v.paused}));}})()",b.getCefBrowser().getURL(),0);
                if(!videoEvidence.contains("\"paused\":false"))return;
                AtlasSmokeClient.captureBack(mc,"feature-youtube.png");checks.add("YouTube HTML video reports playing: "+videoEvidence);
                MediaPlayer.pin();mc.gui.setScreen(null);stage++;
            }
            case 5 -> {AtlasSmokeClient.captureBack(mc,"feature-media-pinned.png");MediaPlayer.open();MediaPlayer.load("https://soundcloud.com/");stage++;}
            case 6 -> {
                var f=MediaPlayer.class.getDeclaredField("browser");f.setAccessible(true);var b=(net.dimaskama.mcef.api.MCEFBrowser)f.get(null);
                if(b==null||b.getCefBrowser().isLoading())return;
                AtlasSmokeClient.captureBack(mc,"feature-soundcloud.png");checks.add("SoundCloud browser navigation completed");
                Files.writeString(Path.of("feature-preview-ready.txt"),String.join("\n",checks));stage=99;
            }
        }
    }
    private void dumpSky()throws Exception{
        var lines=new ArrayList<String>();dump(sky,"sky",lines,0);Files.writeString(Path.of("sky-debug.txt"),String.join("\n",lines));
    }
    private static void dump(Object o,String path,List<String> lines,int depth)throws Exception{
        for(var f:o.getClass().getDeclaredFields()){
            if(java.lang.reflect.Modifier.isStatic(f.getModifiers()))continue;f.setAccessible(true);Object v=f.get(o);
            if(v==null||f.getType().isPrimitive())lines.add(path+"."+f.getName()+"="+v);
            else if(depth<3&&v.getClass().getPackageName().endsWith("impl.render"))dump(v,path+"."+f.getName(),lines,depth+1);
        }
    }
}
