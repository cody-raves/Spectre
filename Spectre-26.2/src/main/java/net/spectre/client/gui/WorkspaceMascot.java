package net.spectre.client.gui;

import imgui.ImDrawList;
import imgui.flag.ImDrawListFlags;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleRegistry;

/** Native procedural ghost: articulated limbs, deforming hem and animated prop entrances. */
public final class WorkspaceMascot {
    private static GhostRig rig = new GhostRig();
    private static GhostRig brandRig = new GhostRig();
    private static GhostRig splashRig = new GhostRig();
    private WorkspaceMascot() { }
    private static MascotFocus focus=new MascotFocus();
    public static void noteToggle(ClientModule module){focus.toggled(module.id(),module.isEnabled(),module.enabledByDefault());}
    public static void noteAction(String id){focus.action(id,System.nanoTime()/1_000_000L);}
    public static String activeModule(){
        java.util.List<String> enabled=new java.util.ArrayList<>(),eligible=new java.util.ArrayList<>(),working=new java.util.ArrayList<>();
        for(ClientModule module:ModuleRegistry.all()){
            if(module.isEnabled()){enabled.add(module.id());if(!module.enabledByDefault())eligible.add(module.id());}
            if(module.mascotTaskActive())working.add(module.id());
        }
        return focus.update(enabled,eligible,working,System.nanoTime()/1_000_000L);
    }
    static void drawHudEmblem(ImDrawList dl,float cx,float cy,float size,float alpha){
        float scale=size/180f;
        drawRig(dl,cx-128*scale,cy-128*scale,256*scale,alpha,rig,activeModule());
    }
    static void draw(ImDrawList dl, float x, float y, float size, MascotAnimation.Mood ignored) {
        drawRig(dl,x,y,size,1,rig,activeModule());
    }
    /** The same character as the HUD, in its neutral pose for branding. */
    public static void drawBrand(ImDrawList dl,float cx,float cy,float size,float alpha) {
        float scale=size/120f;
        drawRig(dl,cx-128*scale,cy-142*scale,256*scale,alpha,brandRig,"idle");
    }
    public static void drawSplash(ImDrawList dl,float cx,float cy,float size,float alpha,float progress) {
        float scale=size/160f;
        splashRig.drawSplash(canvas(dl,cx-128*scale,cy-128*scale,256*scale,alpha),
            System.nanoTime()/1_000_000L,progress,GuiPalette.animationSpeed()<=0);
    }
    private static void drawRig(ImDrawList dl,float x,float y,float size,float alpha,GhostRig character,String module) {
        character.draw(canvas(dl,x,y,size,alpha),module,System.nanoTime()/1_000_000L,GuiPalette.animationSpeed()<=0);
    }
    private static GhostRig.Canvas canvas(ImDrawList dl,float x,float y,float size,float alpha) {
        float scale=size/256F;
        return new GhostRig.Canvas() {
            public void polygon(float[] xy,int argb) {
                int color=SpectreWidgets.c(SpectreDraw.col(argb,alpha));
                int[] triangles=GhostMesh.triangles(xy);
                // Fringes on every triangle expose internal edges in the cloth.
                int flags=dl.getFlags();
                dl.setFlags(flags & ~ImDrawListFlags.AntiAliasedFill);
                try { for(int i=0;i<triangles.length;i+=3){int a=triangles[i]*2,b=triangles[i+1]*2,c=triangles[i+2]*2;
                    dl.addTriangleFilled(x+xy[a]*scale,y+xy[a+1]*scale,x+xy[b]*scale,y+xy[b+1]*scale,x+xy[c]*scale,y+xy[c+1]*scale,color);
                }} finally { dl.setFlags(flags); }
            }
            public void line(float[] xy,int argb,float width) {
                int color=SpectreWidgets.c(SpectreDraw.col(argb,alpha));
                for(int i=0;i<xy.length;i+=2){
                    float px=x+xy[i]*scale,py=y+xy[i+1]*scale;
                    dl.addCircleFilled(px,py,width*scale/2,color,12);
                    if(i+2<xy.length)dl.addLine(px,py,x+xy[i+2]*scale,y+xy[i+3]*scale,color,width*scale);
                }
            }
        };
    }
    public static void dispose() { rig=new GhostRig();brandRig=new GhostRig();splashRig=new GhostRig();focus=new MascotFocus(); }
}
