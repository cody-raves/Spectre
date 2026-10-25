package net.spectre.client.map;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.opengl.GL33.*;
import java.util.*;
import java.nio.ByteBuffer;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;

/** Real GPU rendering in an invisible standalone context; does not touch Minecraft or worlds. */
public final class AtlasGpuChecks {
    public static void main(String[] args)throws Exception {
        if(!GLFW.glfwInit())throw new AssertionError("GLFW initialization failed");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,3);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_CORE_PROFILE);
        long window=GLFW.glfwCreateWindow(960,640,"Spectre atlas validation",0,0);
        if(window==0)throw new AssertionError("GL context unavailable");
        GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
        try(AtlasGpu gpu=new AtlasGpu()) {
            var texture=new AtlasModels.Texture(2,2,new int[]{0xff72a888,0xff587f62,0xff628f72,0xff8fb293});
            var top=new AtlasModels.Quad(new float[]{0,1,0,0,1,1,1,1,1,1,1,0},new float[]{0,0,0,1,1,1,1,0},texture,0xffffff,1,2);
            var model=new AtlasModels.Model(List.of(top));
            int[] cells=new int[4096];
            for(int z=0;z<16;z++)for(int x=0;x<16;x++)for(int y=0;y<4+(x+z)%5;y++)cells[y*256+z*16+x]=1;
            for(int cz=-3;cz<=3;cz++)for(int cx=-3;cx<=3;cx++)gpu.upload(AtlasMesh.build(new AtlasMesh.Snapshot(cx,cz,0,16,cells,new boolean[]{false,true},new boolean[]{false,true},new AtlasModels.Model[]{null,model}),AtlasMesh.Layer.SURFACE,0,15));
            var camera=new AtlasCamera();camera.y=4;camera.zoom=7;
            glViewport(7,9,123,234);glEnable(GL_SCISSOR_TEST);glDisable(GL_DEPTH_TEST);glDepthMask(false);
            if(GL.getCapabilities().GL_ARB_clip_control)org.lwjgl.opengl.ARBClipControl.glClipControl(GL_LOWER_LEFT,org.lwjgl.opengl.ARBClipControl.GL_ZERO_TO_ONE);
            int tex=gpu.render(camera,960,640);
            int[] viewport=new int[4];glGetIntegerv(GL_VIEWPORT,viewport);
            if(!Arrays.equals(viewport,new int[]{7,9,123,234})||!glIsEnabled(GL_SCISSOR_TEST)||glIsEnabled(GL_DEPTH_TEST)||glGetBoolean(GL_DEPTH_WRITEMASK))throw new AssertionError("GL state was not restored");
            if(GL.getCapabilities().GL_ARB_clip_control&&glGetInteger(org.lwjgl.opengl.ARBClipControl.GL_CLIP_DEPTH_MODE)!=org.lwjgl.opengl.ARBClipControl.GL_ZERO_TO_ONE)throw new AssertionError("Minecraft clip depth mode was not restored");
            glBindTexture(GL_TEXTURE_2D,tex);ByteBuffer image=MemoryUtil.memAlloc(960*640*4);
            try {
                glGetTexImage(GL_TEXTURE_2D,0,GL_RGBA,GL_UNSIGNED_BYTE,image);
                var preview=new BufferedImage(960,640,BufferedImage.TYPE_INT_ARGB);int terrain=0;
                for(int y=0;y<640;y++)for(int x=0;x<960;x++){
                    int i=(y*960+x)*4,r=image.get(i)&255,g=image.get(i+1)&255,b=image.get(i+2)&255;
                    if(g>r&&g>b)terrain++;
                    preview.setRGB(x,639-y,0xff000000|r<<16|g<<8|b);
                }
                if(terrain<5000)throw new AssertionError("Terrain did not render: "+terrain);
                File out=new File("../validation/atlas-gpu-preview.png");out.getParentFile().mkdirs();ImageIO.write(preview,"png",out);
            }finally{MemoryUtil.memFree(image);glBindTexture(GL_TEXTURE_2D,0);}
            double[] ms=new double[180];
            for(int i=0;i<ms.length;i++){
                camera.orbit(2,.1);camera.pan(.5,.25);camera.scroll(i%2==0?.2:-.2);
                long start=System.nanoTime();gpu.render(camera,960,640);glFinish();ms[i]=(System.nanoTime()-start)/1e6;
            }
            Arrays.sort(ms);
            if(glGetError()!=GL_NO_ERROR)throw new AssertionError("OpenGL error after rendering");
            gpu.retain(Set.of(AtlasGpu.key(0,0)));if(gpu.size()!=1)throw new AssertionError("Chunk eviction failed");
            gpu.clear();if(gpu.size()!=0)throw new AssertionError("Cache clear failed");
            System.out.printf(Locale.ROOT,"GPU checks passed: %s; 49 cached chunks, 960x640, 180 pan/orbit/zoom frames: median %.2f ms, p95 %.2f ms%n",glGetString(GL_RENDERER),ms[90],ms[171]);
        }finally{GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
}
