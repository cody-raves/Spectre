package net.spectre.client.map;

import net.minecraft.client.Minecraft;
import org.lwjgl.system.MemoryUtil;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import static org.lwjgl.opengl.GL33.*;

/** Opt-in real-time framebuffer recording. No cuts, stitched images or time scaling. */
public final class ShowcaseCapture {
    public static final boolean ENABLED=Boolean.getBoolean("spectre.showcase");
    private static Process encoder;
    private static OutputStream input;
    private static ByteBuffer pixels;
    private static ByteBuffer resizedSource;
    private static byte[] bytes;
    private static int width,height;
    private static long started,frames;
    private static boolean sized,finished;
    private static final boolean BUILDER_DEMO=Boolean.getBoolean("spectre.builderDemo");
    private static boolean requested=!BUILDER_DEMO;
    public static void begin(){requested=true;}
    private static Object map,layer;
    private static long mapStarted,previousFrame;
    private ShowcaseCapture() {}
    public static void frame(Minecraft mc)throws Exception{
        if(!ENABLED||finished||!requested)return;
        animateMap(mc);
        if(!sized){
            mc.options.fullscreen().set(false);
            mc.getWindow().setWindowed(BUILDER_DEMO?1280:1920,BUILDER_DEMO?720:1080);
            sized=true;return;
        }
        int w=mc.getWindow().getWidth(),h=mc.getWindow().getHeight();
        if(encoder==null){
            if(w<1||h<1)return;
            width=BUILDER_DEMO?1280:1920;height=BUILDER_DEMO?720:1080;pixels=MemoryUtil.memAlloc(width*height*4);bytes=new byte[width*height*4];
            encoder=new ProcessBuilder(System.getProperty("spectre.ffmpeg","ffmpeg"),
                "-hide_banner","-loglevel","warning","-y","-f","rawvideo","-pixel_format","rgba",
                "-video_size",width+"x"+height,"-framerate","30","-i","pipe:0","-an","-vf","vflip",
                "-c:v","libx264","-preset","veryfast","-crf","18","-threads","4",
                "-pix_fmt","yuv420p","-movflags","+faststart",BUILDER_DEMO?"builder-demo-raw.mp4":"spectre-continuous-tour.mp4")
                .redirectError(Path.of("spectre-recording-encoder.log").toFile())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            input=encoder.getOutputStream();started=System.nanoTime();
            Files.writeString(Path.of("spectre-tour-chapters.tsv"),"seconds\tscene\n");
            mark(BUILDER_DEMO?(Boolean.getBoolean("spectre.builderRedstone")?"Import schematic":"Schematic preview"):"Startup splash");
        }
        if(w<1||h<1)return;
        long target=(System.nanoTime()-started)*30/1_000_000_000L+1;
        if(target<=frames)return;
        // A stalled game retains its last displayed frame; preserve that wall-clock time.
        while(frames>0&&frames<target-1){input.write(bytes);frames++;}
        int fbo=glGetInteger(GL_READ_FRAMEBUFFER_BINDING),pack=glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING);
        int alignment=glGetInteger(GL_PACK_ALIGNMENT),row=glGetInteger(GL_PACK_ROW_LENGTH);
        int skipX=glGetInteger(GL_PACK_SKIP_PIXELS),skipY=glGetInteger(GL_PACK_SKIP_ROWS);
        glBindFramebuffer(GL_READ_FRAMEBUFFER,0);int read=glGetInteger(GL_READ_BUFFER);
        try{
            glReadBuffer(GL_BACK);glBindBuffer(GL_PIXEL_PACK_BUFFER,0);
            glPixelStorei(GL_PACK_ALIGNMENT,1);glPixelStorei(GL_PACK_ROW_LENGTH,0);
            glPixelStorei(GL_PACK_SKIP_PIXELS,0);glPixelStorei(GL_PACK_SKIP_ROWS,0);
            pixels.clear();
            if(w==width&&h==height)glReadPixels(0,0,w,h,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
            else{
                int required=Math.multiplyExact(Math.multiplyExact(w,h),4);
                if(resizedSource==null||resizedSource.capacity()!=required){if(resizedSource!=null)MemoryUtil.memFree(resizedSource);resizedSource=MemoryUtil.memAlloc(required);}
                resizedSource.clear();glReadPixels(0,0,w,h,GL_RGBA,GL_UNSIGNED_BYTE,resizedSource);
                org.lwjgl.stb.STBImageResize.stbir_resize_uint8_linear(resizedSource,w,h,0,pixels,width,height,0,org.lwjgl.stb.STBImageResize.STBIR_RGBA);
            }
            pixels.get(bytes);input.write(bytes);frames++;
        }finally{
            glReadBuffer(read);glBindFramebuffer(GL_READ_FRAMEBUFFER,fbo);glBindBuffer(GL_PIXEL_PACK_BUFFER,pack);
            glPixelStorei(GL_PACK_ALIGNMENT,alignment);glPixelStorei(GL_PACK_ROW_LENGTH,row);
            glPixelStorei(GL_PACK_SKIP_PIXELS,skipX);glPixelStorei(GL_PACK_SKIP_ROWS,skipY);
        }
    }
    private static void animateMap(Minecraft mc)throws Exception{
        long now=System.nanoTime();
        float dt=previousFrame==0?0:Math.min(.1f,(now-previousFrame)/1e9f);previousFrame=now;
        if(!(mc.gui.screen() instanceof HiveMapScreen current)){map=null;return;}
        var lf=HiveMapScreen.class.getDeclaredField("layer");lf.setAccessible(true);Object next=lf.get(current);
        if(map!=current||layer!=next){map=current;layer=next;mapStarted=now;}
        float t=(now-mapStarted)/1e9f;
        var cf=HiveMapScreen.class.getDeclaredField("camera");cf.setAccessible(true);var camera=(AtlasCamera)cf.get(current);
        if(t>3&&t<9){camera.orbit(dt*18,0);camera.pan(dt*3,0);}
        if(t>4&&t<7)camera.scroll(dt*.5);
    }
    public static void mark(String scene){
        if(!ENABLED||started==0)return;
        try{Files.writeString(Path.of("spectre-tour-chapters.tsv"),String.format(Locale.ROOT,"%.3f\t%s%n",(System.nanoTime()-started)/1e9,scene),StandardOpenOption.APPEND);}
        catch(IOException e){throw new UncheckedIOException(e);}
    }
    public static void finish(){
        if(!ENABLED||finished)return;finished=true;
        try{
            if(input!=null)input.close();
            if(encoder!=null&&(!encoder.waitFor(45,TimeUnit.SECONDS)||encoder.exitValue()!=0))
                throw new IOException("Video encoder failed; inspect spectre-recording-encoder.log");
            if(frames<30)throw new IOException("No usable video frames captured");
            Files.writeString(Path.of("spectre-recording-pass.txt"),"PASS: "+frames+" frames at 30 fps; one continuous take\n");
        }catch(Exception e){throw new RuntimeException(e);}
        finally{if(pixels!=null)MemoryUtil.memFree(pixels);if(resizedSource!=null)MemoryUtil.memFree(resizedSource);}
    }
}
