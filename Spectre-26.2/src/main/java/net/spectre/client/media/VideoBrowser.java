package net.spectre.client.media;

import java.awt.Rectangle;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.opengl.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.*;
import net.dimaskama.mcef.impl.MCEFBrowserImpl;
import org.cef.CefClient;
import org.cef.browser.*;
import org.lwjgl.opengl.GL12;
import org.lwjgl.system.MemoryUtil;

/** Coalesces Chromium paints: at most one pending native frame, uploaded on the game thread. */
final class VideoBrowser extends MCEFBrowserImpl {
    private record Frame(ByteBuffer bytes,int width,int height){}
    private final AtomicReference<Frame> pending=new AtomicReference<>();
    private volatile boolean closed;
    private GpuTexture texture;
    private GpuTextureView view;
    VideoBrowser(CefClient client,String url,CefRequestContext context){super(client,url,false,context,null);}
    @Override public synchronized void onPaint(CefBrowser browser,boolean popup,Rectangle[] dirty,ByteBuffer bytes,int width,int height){
        if(closed||popup||dirty.length==0||width<1||height<1||width>1920||height>1080)return;
        int size=Math.multiplyExact(Math.multiplyExact(width,height),4);
        if(bytes.capacity()<size)return;
        var copy=MemoryUtil.memAlloc(size);MemoryUtil.memCopy(MemoryUtil.memAddress(bytes),MemoryUtil.memAddress(copy),size);
        var previous=pending.getAndSet(new Frame(copy,width,height));if(previous!=null)MemoryUtil.memFree(previous.bytes());
    }
    void upload(){
        var frame=pending.getAndSet(null);if(frame==null)return;
        try{
            if(closed)return;
            if(texture==null||texture.getWidth(0)!=frame.width()||texture.getHeight(0)!=frame.height()){
                releaseTexture();texture=RenderSystem.getDevice().createTexture("Spectre media",GpuTexture.USAGE_COPY_DST|GpuTexture.USAGE_TEXTURE_BINDING,GpuFormat.RGBA8_UNORM,frame.width(),frame.height(),1,1);
                view=RenderSystem.getDevice().createTextureView(texture);
            }
            GlStateManager._bindTexture(((GlTexture)texture).glId());
            GlStateManager._pixelStore(GlConst.GL_UNPACK_ROW_LENGTH,frame.width());
            GlStateManager._pixelStore(GlConst.GL_UNPACK_SKIP_PIXELS,0);GlStateManager._pixelStore(GlConst.GL_UNPACK_SKIP_ROWS,0);
            GlStateManager._pixelStore(GlConst.GL_UNPACK_ALIGNMENT,4);
            GlStateManager._texSubImage2D(GlConst.GL_TEXTURE_2D,0,0,0,frame.width(),frame.height(),GL12.GL_BGRA,GlConst.GL_UNSIGNED_BYTE,frame.bytes());
            GlStateManager._pixelStore(GlConst.GL_UNPACK_ROW_LENGTH,0);
        }finally{MemoryUtil.memFree(frame.bytes());}
    }
    @Override public GpuTexture getTexture(){return texture;}
    @Override public GpuTextureView getTextureView(){return view;}
    private void releaseTexture(){if(view!=null){view.close();view=null;}if(texture!=null){texture.close();texture=null;}}
    @Override public synchronized void close(){
        if(closed)return;closed=true;
        var frame=pending.getAndSet(null);if(frame!=null)MemoryUtil.memFree(frame.bytes());
        releaseTexture();super.close();
    }
}
