package net.spectre.client.map;

import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.spectre.client.imgui.ImGuiManager;
import net.spectre.client.gui.ClickGuiRenderer;
import net.spectre.client.gui.GuiPalette;
import net.spectre.client.gui.ImGuiScreen;
import net.spectre.client.gui.WorkspaceMascot;

/** Native Spectre map: cached GPU terrain and frame-rate camera input. */
public final class HiveMapScreen extends Screen {
    private final AtlasCamera camera=new AtlasCamera();
    private LocalAtlas atlas=new LocalAtlas();
    private AtlasGpu gpu=new AtlasGpu();
    private AtlasMesh.Layer layer=AtlasMesh.Layer.SURFACE;
    private final int[] ceiling={64},depth={32},radius={2};
    private AtlasMesh.Mesh ready;
    private boolean clear,closed;
    private int dragButton=-1;
    private String renderError="";
    private long resourceGeneration=AtlasResources.generation();
    public HiveMapScreen(){super(Component.literal("Spectre Hive Map"));locate();}
    private void locate(){
        var mc=Minecraft.getInstance();if(mc.player==null)return;
        camera.x=mc.player.getX();camera.z=mc.player.getZ();camera.y=mc.player.getY()-3;
        ceiling[0]=(int)Math.floor(mc.player.getY())+1;
    }
    @Override protected void init(){ImGuiManager.getInstance().activate(this::draw);}
    @Override public void tick(){
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null){onClose();return;}
        if(resourceGeneration!=AtlasResources.generation()){atlas.close();atlas=new LocalAtlas();gpu.close();gpu=new AtlasGpu();ready=null;resourceGeneration=AtlasResources.generation();}
        int min=mc.level.getMinY(),max=mc.level.getMaxY();ceiling[0]=Math.clamp(ceiling[0],min,max);
        int low=layer==AtlasMesh.Layer.SURFACE?min:Math.max(min,ceiling[0]-depth[0]+1);
        int high=layer==AtlasMesh.Layer.SURFACE?max:ceiling[0];
        if(atlas.configure(mc.level,layer,low,high)){clear=true;ready=null;}
        if(ready==null)ready=atlas.tick(camera.chunkX(),camera.chunkZ(),radius[0]);
    }
    private void draw(){
        if(closed)return;
        // GLFW escape remains available even when ImGui consumes the key callback.
        if(org.lwjgl.glfw.GLFW.glfwGetKey(Minecraft.getInstance().getWindow().handle(),256)==1){onClose();return;}
        var io=ImGui.getIO();float w=io.getDisplaySizeX(),h=io.getDisplaySizeY();
        float s=Math.min(ImGuiManager.getInstance().uiScale(),Math.min(w/1000f,h/620f));
        float ww=Math.min(w-32,1320*s),wh=Math.min(h-32,830*s),x=(w-ww)/2,y=(h-wh)/2;
        ClickGuiRenderer.applyTheme();
        boolean font=ClickGuiRenderer.pushFont(ImGuiManager.getInstance().moduleFont());
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding,18);
        ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding,10);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding,16,16);
        ImGui.pushStyleColor(ImGuiCol.WindowBg,color(0xff0c121b));
        ImGui.pushStyleColor(ImGuiCol.ChildBg,color(0xff101823));
        ImGui.pushStyleColor(ImGuiCol.Button,color(0xff182230));
        int action=0;
        try{
            ImGui.getBackgroundDrawList().addRectFilled(0,0,w,h,color(0x99050a10));
            ClickGuiRenderer.drawShadow(x,y,ww,wh,18,8);
            ImGui.setNextWindowPos(x,y);ImGui.setNextWindowSize(ww,wh);
            ImGui.begin("##spectre_hive_map",ImGuiWindowFlags.NoDecoration|ImGuiWindowFlags.NoMove|ImGuiWindowFlags.NoSavedSettings|ImGuiWindowFlags.NoScrollbar|ImGuiWindowFlags.NoScrollWithMouse);
            var dl=ImGui.getWindowDrawList();
            dl.addRect(x,y,x+ww,y+wh,color(0xff2c3b4c),18,0,1);
            dl.addLine(x,y+76,x+ww,y+76,color(0xff2c3b4c),1);
            dl.addLine(x,y+wh-38,x+ww,y+wh-38,color(0xff2c3b4c),1);
            dl.addCircleFilled(x+35,y+35,17,color(0x2256dccb),32);
            WorkspaceMascot.drawBrand(dl,x+35,y+35,22,1);
            dl.addText(x+62,y+20,GuiPalette.accent(),"SPECTRE");
            dl.addText(x+62,y+42,color(0xffa8b7c8),"HIVE MAP  /  TERRAIN EXPLORER");
            ImGui.setCursorPos(ww-200,21);
            if(ImGui.button("Modules",110,34))action=1;
            ImGui.sameLine();if(ImGui.button("Close",58,34))action=2;
            if(action==0){
                float side=Math.min(270*s,ww*.26f),bodyH=wh-130;
                ImGui.setCursorPos(16,90);
                ImGui.beginChild("##map_controls",side,bodyH,false);
                controls(s);
                ImGui.endChild();
                ImGui.setCursorPos(side+28,90);
                ImGui.beginChild("##map_terrain",ww-side-44,bodyH,false,ImGuiWindowFlags.NoScrollbar|ImGuiWindowFlags.NoScrollWithMouse);
                ImGui.text(layerName());ImGui.sameLine();
                muted("   "+gpu.size()+" / "+atlas.wanted().size()+" chunks");
                ImGui.spacing();
                viewport();
                ImGui.endChild();
            }
            dl.addText(x+22,y+wh-25,color(0xff8799aa),"SPECTRE   /   YOUR WORKSPACE");
            String hint="Left drag: pan   /   Right drag: orbit   /   Scroll: zoom";
            float hintWidth=ImGui.calcTextSize(hint).x;
            if(ww>hintWidth+285)dl.addText(x+ww-hintWidth-22,y+wh-25,color(0xff8799aa),hint);
            ImGui.end();
        }finally{
            ImGui.popStyleColor(3);ImGui.popStyleVar(3);
            if(font)ImGui.popFont();ClickGuiRenderer.removeTheme();
        }
        if(action==1)Minecraft.getInstance().gui.setScreen(new ImGuiScreen());
        else if(action==2)onClose();
    }
    private static int color(int argb){return GuiPalette.abgr(argb);}
    private static void muted(String text){ImGui.pushStyleColor(ImGuiCol.Text,color(0xff91a2b4));ImGui.textWrapped(text);ImGui.popStyleColor();}
    private static void section(String label){ImGui.spacing();ImGui.pushStyleColor(ImGuiCol.Text,GuiPalette.accent());ImGui.text(label);ImGui.popStyleColor();ImGui.spacing();}
    private String layerName(){return switch(layer){case SURFACE->"Surface";case CAVES->"Underground caves";case SLICE->"Y slice / cutaway";};}
    private void layerButton(String label,AtlasMesh.Layer value,float s){
        boolean selected=layer==value;
        if(selected){ImGui.pushStyleColor(ImGuiCol.Button,GuiPalette.accent());ImGui.pushStyleColor(ImGuiCol.Text,color(0xff0c1820));}
        boolean clicked=ImGui.button(label+"##layer",ImGui.getContentRegionAvailX(),32*s);
        if(selected)ImGui.popStyleColor(2);
        if(clicked){
            layer=value;var mc=Minecraft.getInstance();
            if(layer==AtlasMesh.Layer.SURFACE&&mc.player!=null)camera.y=mc.player.getY()-3;
            else{camera.pitch=1.12;camera.y=ceiling[0]-depth[0]*.4;}
        }
    }
    private void controls(float s){
        var mc=Minecraft.getInstance();
        section("TERRAIN VIEW");
        if(mc.level!=null)muted(mc.level.dimension().identifier().toString().replace("minecraft:",""));
        ImGui.spacing();
        layerButton("Surface",AtlasMesh.Layer.SURFACE,s);
        layerButton("Underground caves",AtlasMesh.Layer.CAVES,s);
        layerButton("Y slice / cutaway",AtlasMesh.Layer.SLICE,s);
        if(layer!=AtlasMesh.Layer.SURFACE&&mc.level!=null){
            section("UNDERGROUND");
            ImGui.setNextItemWidth(-1);
            if(ImGui.sliderInt("##ceiling",ceiling,mc.level.getMinY(),mc.level.getMaxY(),"Ceiling Y: %d"))camera.y=ceiling[0]-depth[0]*.4;
            if(ImGui.button("-16")){ceiling[0]-=16;camera.y-=16;}ImGui.sameLine();
            if(ImGui.button("-1")){ceiling[0]--;camera.y--;}ImGui.sameLine();
            if(ImGui.button("+1")){ceiling[0]++;camera.y++;}ImGui.sameLine();
            if(ImGui.button("+16")){ceiling[0]+=16;camera.y+=16;}
            ImGui.setNextItemWidth(-1);
            if(ImGui.sliderInt("##depth",depth,8,96,"Depth: %d blocks"))camera.y=ceiling[0]-depth[0]*.4;
        }
        section("SURROUNDING CHUNKS");ImGui.setNextItemWidth(-1);
        ImGui.sliderInt("##radius",radius,1,4,"Chunk radius: %d");
        muted((radius[0]*2+1)+" x "+(radius[0]*2+1)+" chunks around the camera");
        section("CAMERA");
        float bw=ImGui.getContentRegionAvailX();
        if(ImGui.button("Locate me",bw,30*s))locate();
        if(ImGui.button("Reset camera",bw,30*s)){camera.yaw=.72;camera.pitch=layer==AtlasMesh.Layer.SURFACE?.78:1.12;camera.zoom=6;}
        if(ImGui.button("Refresh terrain",bw,30*s)){atlas.reset();clear=true;ready=null;}
        section("POSITION");
        muted(String.format(java.util.Locale.ROOT,"X %.0f   Z %.0f",camera.x,camera.z));
        muted("Center chunk: "+camera.chunkX()+", "+camera.chunkZ());
        if(mc.player!=null)muted("Your chunk: "+mc.player.chunkPosition().x()+", "+mc.player.chunkPosition().z());
        ImGui.spacing();muted("Loaded terrain only. Gaps are chunks your client has not received.");
        if(!atlas.error.isBlank())ImGui.textWrapped(atlas.error);
        if(!renderError.isBlank())ImGui.textWrapped(renderError);
    }
    private void viewport(){
        var mc=Minecraft.getInstance();var io=ImGui.getIO();
        float mw=Math.max(1,ImGui.getContentRegionAvailX()),mh=Math.max(1,ImGui.getContentRegionAvailY());
        float left=ImGui.getCursorScreenPosX(),top=ImGui.getCursorScreenPosY();
        boolean inside=io.getMousePosX()>=left&&io.getMousePosX()<left+mw&&io.getMousePosY()>=top&&io.getMousePosY()<top+mh;
        if(inside){if(ImGui.isMouseClicked(0))dragButton=0;else if(ImGui.isMouseClicked(1))dragButton=1;camera.scroll(io.getMouseWheel());}
        if(dragButton>=0){
            if(!ImGui.isMouseDown(dragButton))dragButton=-1;
            else if(dragButton==1)camera.orbit(io.getMouseDeltaX(),io.getMouseDeltaY());
            else camera.pan(io.getMouseDeltaX(),io.getMouseDeltaY());
        }
        try {
            if(clear){gpu.clear();clear=false;}
            gpu.retain(atlas.wanted());
            if(ready!=null){gpu.upload(ready);ready=null;}
            int tex=gpu.render(camera,Math.max(1,(int)mw),Math.max(1,(int)mh));
            ImGui.image(tex,mw,mh,0,1,1,0);
            if(mc.player!=null){
                var dl=ImGui.getWindowDrawList();
                float[] point=project(mc.player.getX(),mc.player.getY(),mc.player.getZ(),left,top,mw,mh);
                dl.addCircleFilled(point[0],point[1],6,0xff163024,16);
                dl.addCircleFilled(point[0],point[1],4,0xff9fffe0,16);
                dl.addText(point[0]+9,point[1]-8,0xffe8fff7,"You");
                var chunk=mc.player.chunkPosition();
                for(int edge=0;edge<4;edge++){
                    int[] xs={0,16,16,0},zs={0,0,16,16};int next=(edge+1)%4;
                    float[] a=project(chunk.x()*16.0+xs[edge],mc.player.getY(),chunk.z()*16.0+zs[edge],left,top,mw,mh);
                    float[] b=project(chunk.x()*16.0+xs[next],mc.player.getY(),chunk.z()*16.0+zs[next],left,top,mw,mh);
                    dl.addLine(a[0],a[1],b[0],b[1],0xaa9fffe0,1.5f);
                }
            }
        }catch(RuntimeException ex){renderError="Map renderer: "+ex.getMessage();ready=null;ImGui.textWrapped(renderError);}
    }
    private float[] project(double x,double y,double z,float left,float top,float w,float h){
        double dx=x-camera.x,dz=z-camera.z;
        double rx=Math.cos(camera.yaw)*dx-Math.sin(camera.yaw)*dz;
        double rz=Math.sin(camera.yaw)*dx+Math.cos(camera.yaw)*dz;
        return new float[]{left+w*.5f+(float)(rx*camera.zoom),top+h*.5f-(float)((Math.cos(camera.pitch)*(y-camera.y)-Math.sin(camera.pitch)*rz)*camera.zoom)};
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mx,int my,float delta){}
    @Override public void extractBackground(GuiGraphicsExtractor graphics,int mx,int my,float delta){}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void removed(){if(closed)return;closed=true;ImGuiManager.getInstance().deactivate();atlas.close();gpu.close();}
}
