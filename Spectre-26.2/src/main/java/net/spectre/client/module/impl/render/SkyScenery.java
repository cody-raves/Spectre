package net.spectre.client.module.impl.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.util.ARGB;
import org.joml.Vector3f;

/** Bounded decorative sky geometry. It never creates entities, chunks or server traffic. */
final class SkyScenery {
    private final SkyMesh objects=new SkyMesh("Spectre sky visitors",SkyMesh.PAINT,256);
    private final SkyMesh moon=new SkyMesh("Spectre ghost moon",SkyMesh.PAINT,256);
    private final SkyMesh moonGlow=new SkyMesh("Spectre ghost moon glow",SkyMesh.GLOW,512);
    private final SkyMesh sun=new SkyMesh("Spectre spectral sun",SkyMesh.PAINT,256);
    private final SkyMesh corona=new SkyMesh("Spectre solar corona",SkyMesh.GLOW,768);
    private int sunTint;
    private int moonTint;
    void render(PoseStack pose,long now,float visibility,int tint,boolean satellites,boolean ufos,int density){
        if(visibility<.01F||(!satellites&&!ufos))return;
        float time=(now%3_600_000_000_000L)/1_000_000_000F;
        BufferBuilder b=objects.begin();
        if(satellites)for(int i=0;i<density;i++){
            float a=time*.012F+i*2.1F,el=.45F+(float)Math.sin(a*.7F)*.25F;
            var centre=new Vector3f((float)Math.cos(a)*(float)Math.cos(el),(float)Math.sin(el),(float)Math.sin(a)*(float)Math.cos(el)).mul(95);
            quad(b,centre,-.16F,-.22F,.16F,.22F,0xFFCFDCE7);
            quad(b,centre,-.85F,-.28F,-.25F,.28F,0xFF377DAB);quad(b,centre,.25F,-.28F,.85F,.28F,0xFF377DAB);
            quad(b,centre,-.65F,-.02F,.65F,.02F,0xFFE4F4FF);
        }
        if(ufos)for(int i=0;i<density;i++){
            float cycle=(time+i*17)%90,phase=cycle/90;
            if(cycle>15)continue;
            float a=phase*2.7F+i*1.7F,el=.7F+(float)Math.sin(phase*12)*.15F;
            var centre=new Vector3f((float)Math.cos(a)*(float)Math.cos(el),(float)Math.sin(el),(float)Math.sin(a)*(float)Math.cos(el)).mul(94);
            int metal=0xFF000000|ARGB.srgbLerp(.25F,tint,0xB5C9D7);
            quad(b,centre,-1.8F,-.18F,1.8F,.18F,metal);
            quad(b,centre,-1.1F,.18F,1.1F,.5F,0xFF718797);
            quad(b,centre,-.6F,.5F,.6F,.85F,0xFF9BD8DF);
            for(int light=0;light<5;light++)quad(b,centre,-1.4F+light*.65F,-.28F,-1.2F+light*.65F,-.1F,0xFF000000|tint);
        }
        objects.upload(b);objects.draw(pose,visibility);
    }
    private static void quad(BufferBuilder b,Vector3f c,float x0,float y0,float x1,float y1,int color){
        var right=new Vector3f(-c.z,0,c.x).normalize();var up=new Vector3f(c).normalize().cross(right).normalize();
        point(b,c,right,up,x0,y0,color);point(b,c,right,up,x1,y0,color);point(b,c,right,up,x1,y1,color);point(b,c,right,up,x0,y1,color);
    }
    private static void point(BufferBuilder b,Vector3f c,Vector3f right,Vector3f up,float x,float y,int color){SkyMesh.vertex(b,c.x+right.x*x+up.x*y,c.y+right.y*x+up.y*y,c.z+right.z*x+up.z*y,color);}
    void sun(PoseStack pose,float visibility,int tint,float glow){
        if(visibility<=.002F)return;
        if(!sun.isBuilt()||sunTint!=tint){
            sunTint=tint;var b=sun.begin();
            int white=0xFFFFFCF2,edge=0xFF000000|ARGB.srgbLerp(.55F,tint,0xFFF6CE);
            ring(b,0,8,white,white);ring(b,8,10.5F,white,0xFF000000|ARGB.srgbLerp(.75F,tint,0xFFF6CE));
            ring(b,10.5F,12,0xFF000000|ARGB.srgbLerp(.75F,tint,0xFFF6CE),edge);
            sun.upload(b);
        }
        // Only a small corona mesh changes each frame; no textures or entities are created.
        var b=corona.begin();
        for(int i=0;i<8;i++){
            float inner=12+i*2.5F,outer=inner+2.5F;
            int a=Math.round(100*(float)Math.pow(1-i/8F,2)),next=Math.round(100*(float)Math.pow(1-(i+1)/8F,2));
            ring(b,inner,outer,(a<<24)|tint,(next<<24)|tint);
        }
        double time=(System.nanoTime()%3_600_000_000_000L)/1_000_000_000.0;
        for(int i=0;i<24;i++){
            double a=i*Math.PI*2/24+time*.008;
            float length=17+4*(float)Math.sin(i*2.4+time*.3),width=.012F+(i%3)*.004F;
            radial(b,12,a-width,0x60000000|tint);radial(b,12,a+width,0x60000000|tint);
            radial(b,length,a+width*.1, tint);radial(b,length,a-width*.1,tint);
        }
        corona.upload(b);corona.draw(pose,visibility*glow);sun.draw(pose,visibility);
    }
    private static void ring(BufferBuilder b,float inner,float outer,int innerColor,int outerColor){
        for(int i=0;i<64;i++){
            double a=i*Math.PI*2/64,next=(i+1)*Math.PI*2/64;
            radial(b,inner,a,innerColor);radial(b,inner,next,innerColor);
            radial(b,outer,next,outerColor);radial(b,outer,a,outerColor);
        }
    }
    private static void radial(BufferBuilder b,float radius,double angle,int color){SkyMesh.vertex(b,(float)Math.cos(angle)*radius,100,(float)Math.sin(angle)*radius,color);}
    void moon(PoseStack pose,float visibility,int tint,float glow){
        if(!moon.isBuilt()||moonTint!=tint){moonTint=tint;buildMoon(tint);}
        pose.pushPose();
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90));
        pose.scale(.65F,1,.65F);
        moonGlow.draw(pose,visibility*glow);moon.draw(pose,visibility);
        pose.popPose();
    }
    private void buildMoon(int tint){
        var b=moon.begin();
        ghost(b,1,0xFFF0FCFF);
        for(int row=0;row<16;row++){float z=-6+row*.5F,w=2.5F*(float)Math.sqrt(Math.max(0,1-Math.pow((z+2)/4,2)));flat(b,-6-w,z,-6+w,z+.5F,0xFF10212C);flat(b,6-w,z,6+w,z+.5F,0xFF10212C);}
        flat(b,-1.5F,5,1.5F,8,0xFF10212C);moon.upload(b);
        b=moonGlow.begin();
        for(int layer=4;layer>=1;layer--)ghost(b,1+layer*.07F,((5-layer)*5<<24)|tint);
        moonGlow.upload(b);
    }
    private static void ghost(BufferBuilder b,float scale,int color){
        for(int row=0;row<50;row++){
            float z=-18+row*.6F,w=z<0?(float)Math.sqrt(Math.max(0,18*18-z*z)):18;
            flat(b,-w*scale,z*scale,w*scale,(z+.6F)*scale,color);
        }
        for(int row=0;row<14;row++){
            float z=12+row*.6F,w=6*(float)Math.sqrt(Math.max(0,1-Math.pow((z-12)/8.4F,2)));
            for(int foot=-1;foot<=1;foot++)flat(b,(foot*12-w)*scale,z*scale,(foot*12+w)*scale,(z+.6F)*scale,color);
        }
    }
    private static void flat(BufferBuilder b,float x0,float z0,float x1,float z1,int color){SkyMesh.vertex(b,x0,100,z0,color);SkyMesh.vertex(b,x1,100,z0,color);SkyMesh.vertex(b,x1,100,z1,color);SkyMesh.vertex(b,x0,100,z1,color);}
    void close(){objects.close();moon.close();moonGlow.close();sun.close();corona.close();}
}
