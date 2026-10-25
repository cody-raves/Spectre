package net.spectre.client.media;

import java.net.URI;
import java.util.Locale;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import org.joml.Matrix3x2f;

/** One browser per client, created only by opening Media Player. */
public final class MediaPlayer {
    private static VideoBrowser browser;
    private static String requested="https://www.youtube.com/";
    private static boolean opening,pinned;
    private static int pixelWidth,pixelHeight;
    private static String error;
    private MediaPlayer(){}
    public static void register(){ClientLifecycleEvents.CLIENT_STOPPING.register(mc->{stop();MediaRuntime.shutdown();});}
    public static void open(){Minecraft.getInstance().gui.setScreen(new MediaScreen(Minecraft.getInstance().gui.screen()));}
    public static String url(){return browser==null?requested:browser.getCefBrowser().getURL();}
    public static String normalize(String input){
        String value=input.strip();if(!value.contains("://"))value="https://"+value;
        var uri=URI.create(value);String host=uri.getHost();
        if(!"https".equalsIgnoreCase(uri.getScheme())||host==null||uri.getRawUserInfo()!=null||uri.getPort()!=-1)throw new IllegalArgumentException("Use a YouTube or SoundCloud HTTPS link.");
        host=host.toLowerCase(Locale.ROOT);
        if(!(host.equals("youtu.be")||host.equals("youtube.com")||host.endsWith(".youtube.com")||host.equals("soundcloud.com")||host.endsWith(".soundcloud.com")))throw new IllegalArgumentException("Use a YouTube or SoundCloud link.");
        return uri.toASCIIString();
    }
    public static void load(String input){requested=normalize(input);error=null;if(browser!=null)browser.getCefBrowser().loadURL(requested);else ensure();}
    static void ensure(){
        if(browser!=null||opening)return;opening=true;error=null;
        MediaRuntime.initialize().whenComplete((client,failure)->Minecraft.getInstance().execute(()->{
            opening=false;
            if(failure!=null){error="Browser failed to start. Restart the client to retry.";return;}
            if(!(Minecraft.getInstance().gui.screen() instanceof MediaScreen)&&!pinned)return;
            browser=MediaRuntime.create(client,requested);browser.resize(1280,720);pixelWidth=1280;pixelHeight=720;
        }));
    }
    static VideoBrowser browser(){return browser;}
    static String status(){return error==null?MediaRuntime.progress:error;}
    static int pixelX(double x,int w){return (int)(x*pixelWidth/Math.max(1,w));}
    static int pixelY(double y,int h){return (int)(y*pixelHeight/Math.max(1,h));}
    public static void pin(){pinned=true;if(browser!=null)browser.setFocus(false);}
    public static void stop(){pinned=false;if(browser!=null){browser.close();browser=null;}pixelWidth=0;pixelHeight=0;}
    static void screenClosed(){if(browser!=null)browser.setFocus(false);if(!pinned)stop();}
    static void resume(){pinned=false;ensure();}
    public static void draw(GuiGraphicsExtractor g,int x,int y,int w,int h){
        if(browser==null)return;
        int pw=Math.min(1280,Math.max(320,w*2)),ph=Math.min(720,Math.max(200,h*2));
        if(pw!=pixelWidth||ph!=pixelHeight){pixelWidth=pw;pixelHeight=ph;browser.resize(pw,ph);}
        browser.upload();var view=browser.getTextureView();if(view==null)return;
        g.guiRenderState.addGuiElement(new BlitRenderState(RenderPipelines.GUI_TEXTURED,
            TextureSetup.singleTexture(view,RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)),
            new Matrix3x2f(g.pose()),x,y,x+w,y+h,0,1,0,1,-1,g.scissorStack.peek()));
    }
    public static void extractPinned(GuiGraphicsExtractor g){
        var mc=Minecraft.getInstance();
        if(!pinned||browser==null)return;
        if(mc.level==null||mc.player==null){stop();return;}
        if(mc.gui.screen()!=null||mc.gui.hud.isHidden())return;
        int sw=mc.getWindow().getGuiScaledWidth(),sh=mc.getWindow().getGuiScaledHeight();
        if(sw<380||sh<270)return;
        int w=Math.min(420,sw-24),h=Math.max(200,w*9/16),x=sw-w-12,y=sh-h-38;
        g.nextStratum();g.fill(x-2,y-17,x+w+2,y+h+2,0xFF101823);
        g.text(mc.font,"Media Player · reopen from Spectre",x+5,y-12,0xFF56DCCB,false);draw(g,x,y,w,h);
    }
}
