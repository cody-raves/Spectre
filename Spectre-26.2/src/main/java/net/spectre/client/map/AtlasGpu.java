package net.spectre.client.map;

import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import java.nio.*;
import java.util.*;
import static org.lwjgl.opengl.GL33.*;

/** Render-thread owned GPU cache, used inside Spectre's existing OpenGL ImGui pass. */
public final class AtlasGpu implements AutoCloseable {
    private record Batch(int vao,int vbo,int texture,int count) {}
    private record Chunk(int x,int z,List<Batch> batches) {}
    private final Map<Long,Chunk> chunks=new LinkedHashMap<>();
    private final Map<AtlasModels.Texture,Integer> textures=new IdentityHashMap<>();
    private int program,fbo,color,depth,width,height;
    private static final String VERT="""
        #version 330 core
        layout(location=0) in vec3 Position;
        layout(location=1) in vec2 UV;
        layout(location=2) in vec3 Color;
        uniform vec3 Offset;
        uniform vec4 Angles;
        uniform vec2 Scale;
        out vec2 texCoord;
        out vec3 tint;
        void main() {
            vec3 p=Position+Offset;
            float rx=Angles.x*p.x-Angles.y*p.z;
            float rz=Angles.y*p.x+Angles.x*p.z;
            float ry=Angles.z*p.y-Angles.w*rz;
            float depth=Angles.w*p.y+Angles.z*rz;
            gl_Position=vec4(rx*Scale.x,ry*Scale.y,-depth/4096.0,1.0);
            texCoord=UV; tint=Color;
        }
        """;
    private static final String FRAG="""
        #version 330 core
        uniform sampler2D Terrain;
        in vec2 texCoord;
        in vec3 tint;
        out vec4 fragColor;
        void main() {
            vec4 texel=texture(Terrain,texCoord);
            if(texel.a<0.31) discard;
            fragColor=vec4(texel.rgb*tint,1.0);
        }
        """;
    public static long key(int x,int z){return ((long)x<<32)|(z&0xffffffffL);}
    public int size(){return chunks.size();}
    public void retain(Set<Long> wanted){
        boolean changed=false;
        var it=chunks.entrySet().iterator();
        while(it.hasNext()){var entry=it.next();if(!wanted.contains(entry.getKey())){delete(entry.getValue());it.remove();changed=true;}}
        if(changed)pruneTextures();
    }
    public void clear(){chunks.values().forEach(AtlasGpu::delete);chunks.clear();pruneTextures();}
    private void pruneTextures(){
        Set<Integer> used=new HashSet<>();
        for(var chunk:chunks.values())for(var batch:chunk.batches)used.add(batch.texture);
        textures.entrySet().removeIf(entry->{if(used.contains(entry.getValue()))return false;glDeleteTextures(entry.getValue());return true;});
    }
    private static void delete(Chunk c){for(Batch b:c.batches){glDeleteBuffers(b.vbo);glDeleteVertexArrays(b.vao);}}
    public void upload(AtlasMesh.Mesh mesh){
        try(State ignored=new State()) {
            List<Batch> batches=new ArrayList<>();
            try {
                for(var batch:mesh.batches()) {
                    int texture=textures.computeIfAbsent(batch.texture(),AtlasGpu::texture);
                    int vao=glGenVertexArrays(),vbo=glGenBuffers();
                    batches.add(new Batch(vao,vbo,texture,batch.vertices().length/8));
                    glBindVertexArray(vao);glBindBuffer(GL_ARRAY_BUFFER,vbo);
                    glBufferData(GL_ARRAY_BUFFER,batch.vertices(),GL_STATIC_DRAW);
                    glEnableVertexAttribArray(0);glVertexAttribPointer(0,3,GL_FLOAT,false,32,0);
                    glEnableVertexAttribArray(1);glVertexAttribPointer(1,2,GL_FLOAT,false,32,12);
                    glEnableVertexAttribArray(2);glVertexAttribPointer(2,3,GL_FLOAT,false,32,20);
                }
                var prior=chunks.put(key(mesh.cx(),mesh.cz()),new Chunk(mesh.cx(),mesh.cz(),batches));
                if(prior!=null)delete(prior);
                pruneTextures();
            }catch(RuntimeException ex){delete(new Chunk(0,0,batches));throw ex;}
        }
    }
    private static int texture(AtlasModels.Texture tex){
        int id=glGenTextures();
        glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,id);
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER,0);
        glPixelStorei(GL_UNPACK_ALIGNMENT,1);glPixelStorei(GL_UNPACK_ROW_LENGTH,0);
        glPixelStorei(GL_UNPACK_SKIP_PIXELS,0);glPixelStorei(GL_UNPACK_SKIP_ROWS,0);
        ByteBuffer data=MemoryUtil.memAlloc(tex.width()*tex.height()*4);
        try {
            for(int argb:tex.pixels())data.put((byte)(argb>>16)).put((byte)(argb>>8)).put((byte)argb).put((byte)(argb>>>24));
            data.flip();
            glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,tex.width(),tex.height(),0,GL_RGBA,GL_UNSIGNED_BYTE,data);
        }finally{MemoryUtil.memFree(data);}
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
        return id;
    }
    public int render(AtlasCamera camera,int w,int h){
        try(State ignored=new State()) {
            if(program==0)program=program();
            if(w!=width || h!=height)resize(w,h);
            glBindFramebuffer(GL_FRAMEBUFFER,fbo);glViewport(0,0,w,h);
            if(ignored.clipControl)org.lwjgl.opengl.ARBClipControl.glClipControl(GL_LOWER_LEFT,org.lwjgl.opengl.ARBClipControl.GL_NEGATIVE_ONE_TO_ONE);
            glDisable(GL_SCISSOR_TEST);glDisable(GL_BLEND);glDisable(GL_FRAMEBUFFER_SRGB);
            glEnable(GL_DEPTH_TEST);glDepthFunc(GL_LEQUAL);glDepthMask(true);
            glEnable(GL_CULL_FACE);glCullFace(GL_BACK);glFrontFace(GL_CCW);
            glColorMask(true,true,true,true);glPolygonMode(GL_FRONT_AND_BACK,GL_FILL);
            glClearColor(.06f,.10f,.14f,1);glClearDepth(1);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
            glUseProgram(program);glActiveTexture(GL_TEXTURE0);glBindSampler(0,0);
            glUniform1i(glGetUniformLocation(program,"Terrain"),0);
            glUniform4f(glGetUniformLocation(program,"Angles"),(float)Math.cos(camera.yaw),(float)Math.sin(camera.yaw),(float)Math.cos(camera.pitch),(float)Math.sin(camera.pitch));
            glUniform2f(glGetUniformLocation(program,"Scale"),(float)(camera.zoom*2/w),(float)(camera.zoom*2/h));
            int offset=glGetUniformLocation(program,"Offset");
            for(Chunk chunk:chunks.values()) {
                glUniform3f(offset,(float)(chunk.x*16.0-camera.x),(float)-camera.y,(float)(chunk.z*16.0-camera.z));
                for(Batch batch:chunk.batches){
                    glBindTexture(GL_TEXTURE_2D,batch.texture);glBindVertexArray(batch.vao);
                    glDrawArrays(GL_TRIANGLES,0,batch.count);
                }
            }
            return color;
        }
    }
    private void resize(int w,int h){
        releaseTarget();width=w;height=h;
        color=glGenTextures();glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,color);
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER,0);
        glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,w,h,0,GL_RGBA,GL_UNSIGNED_BYTE,(ByteBuffer)null);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
        depth=glGenRenderbuffers();glBindRenderbuffer(GL_RENDERBUFFER,depth);glRenderbufferStorage(GL_RENDERBUFFER,GL_DEPTH_COMPONENT24,w,h);
        fbo=glGenFramebuffers();glBindFramebuffer(GL_FRAMEBUFFER,fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,color,0);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER,GL_DEPTH_ATTACHMENT,GL_RENDERBUFFER,depth);
        if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Map framebuffer is incomplete");
    }
    private static int shader(int type,String code){
        int id=glCreateShader(type);glShaderSource(id,code);glCompileShader(id);
        if(glGetShaderi(id,GL_COMPILE_STATUS)==0){String log=glGetShaderInfoLog(id);glDeleteShader(id);throw new IllegalStateException(log);}
        return id;
    }
    private static int program(){
        int vertex=shader(GL_VERTEX_SHADER,VERT),fragment=0,result=0;
        try {
            fragment=shader(GL_FRAGMENT_SHADER,FRAG);result=glCreateProgram();glAttachShader(result,vertex);glAttachShader(result,fragment);glLinkProgram(result);
            if(glGetProgrami(result,GL_LINK_STATUS)==0)throw new IllegalStateException(glGetProgramInfoLog(result));
            return result;
        }catch(RuntimeException ex){if(result!=0)glDeleteProgram(result);throw ex;}
        finally{glDeleteShader(vertex);if(fragment!=0)glDeleteShader(fragment);}
    }
    private void releaseTarget(){if(fbo!=0)glDeleteFramebuffers(fbo);if(color!=0)glDeleteTextures(color);if(depth!=0)glDeleteRenderbuffers(depth);fbo=color=depth=0;}
    @Override public void close(){clear();textures.values().forEach(GL33::glDeleteTextures);textures.clear();releaseTarget();if(program!=0)glDeleteProgram(program);program=0;}

    /** Restore every GL binding/state changed here, including Minecraft's cached state. */
    private static final class State implements AutoCloseable {
        final boolean clipControl=org.lwjgl.opengl.GL.getCapabilities().GL_ARB_clip_control;
        final int clipOrigin=clipControl?glGetInteger(org.lwjgl.opengl.ARBClipControl.GL_CLIP_ORIGIN):0,
            clipDepth=clipControl?glGetInteger(org.lwjgl.opengl.ARBClipControl.GL_CLIP_DEPTH_MODE):0;
        final int draw=glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING),read=glGetInteger(GL_READ_FRAMEBUFFER_BINDING),
            vao=glGetInteger(GL_VERTEX_ARRAY_BINDING),buffer=glGetInteger(GL_ARRAY_BUFFER_BINDING),
            program=glGetInteger(GL_CURRENT_PROGRAM),active=glGetInteger(GL_ACTIVE_TEXTURE),
            renderbuffer=glGetInteger(GL_RENDERBUFFER_BINDING),unpack=glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING),
            alignment=glGetInteger(GL_UNPACK_ALIGNMENT),row=glGetInteger(GL_UNPACK_ROW_LENGTH),
            skipX=glGetInteger(GL_UNPACK_SKIP_PIXELS),skipY=glGetInteger(GL_UNPACK_SKIP_ROWS),
            depthFunc=glGetInteger(GL_DEPTH_FUNC),cullFace=glGetInteger(GL_CULL_FACE_MODE),front=glGetInteger(GL_FRONT_FACE);
        final boolean scissor=glIsEnabled(GL_SCISSOR_TEST),blend=glIsEnabled(GL_BLEND),depthTest=glIsEnabled(GL_DEPTH_TEST),
            cull=glIsEnabled(GL_CULL_FACE),srgb=glIsEnabled(GL_FRAMEBUFFER_SRGB),depthMask=glGetBoolean(GL_DEPTH_WRITEMASK);
        final int[] viewport=new int[4],polygon=new int[2];final float[] clear=new float[4];
        final boolean[] mask=new boolean[4];final double clearDepth=glGetDouble(GL_DEPTH_CLEAR_VALUE);
        final int texture,sampler;
        State(){
            glGetIntegerv(GL_VIEWPORT,viewport);glGetIntegerv(GL_POLYGON_MODE,polygon);glGetFloatv(GL_COLOR_CLEAR_VALUE,clear);
            try(MemoryStack stack=MemoryStack.stackPush()){ByteBuffer b=stack.malloc(4);glGetBooleanv(GL_COLOR_WRITEMASK,b);for(int i=0;i<4;i++)mask[i]=b.get(i)!=0;}
            glActiveTexture(GL_TEXTURE0);texture=glGetInteger(GL_TEXTURE_BINDING_2D);sampler=glGetInteger(GL_SAMPLER_BINDING);
        }
        private static void enable(int cap,boolean enabled){if(enabled)glEnable(cap);else glDisable(cap);}
        public void close(){
            if(clipControl)org.lwjgl.opengl.ARBClipControl.glClipControl(clipOrigin,clipDepth);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER,draw);glBindFramebuffer(GL_READ_FRAMEBUFFER,read);
            glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);glBindVertexArray(vao);glBindBuffer(GL_ARRAY_BUFFER,buffer);
            glUseProgram(program);glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,texture);glBindSampler(0,sampler);glActiveTexture(active);
            glBindRenderbuffer(GL_RENDERBUFFER,renderbuffer);glBindBuffer(GL_PIXEL_UNPACK_BUFFER,unpack);
            glPixelStorei(GL_UNPACK_ALIGNMENT,alignment);glPixelStorei(GL_UNPACK_ROW_LENGTH,row);glPixelStorei(GL_UNPACK_SKIP_PIXELS,skipX);glPixelStorei(GL_UNPACK_SKIP_ROWS,skipY);
            enable(GL_SCISSOR_TEST,scissor);enable(GL_BLEND,blend);enable(GL_DEPTH_TEST,depthTest);enable(GL_CULL_FACE,cull);enable(GL_FRAMEBUFFER_SRGB,srgb);
            glDepthFunc(depthFunc);glDepthMask(depthMask);glCullFace(cullFace);glFrontFace(front);
            glColorMask(mask[0],mask[1],mask[2],mask[3]);glPolygonMode(GL_FRONT_AND_BACK,polygon[0]);
            glClearColor(clear[0],clear[1],clear[2],clear[3]);glClearDepth(clearDepth);
        }
    }
}
