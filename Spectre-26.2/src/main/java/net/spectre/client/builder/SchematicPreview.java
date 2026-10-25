package net.spectre.client.builder;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.*;
import net.minecraft.gizmos.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.*;
import net.spectre.client.event.impl.*;
import net.spectre.client.map.*;
import org.lwjgl.glfw.GLFW;

/** Client-only hologram. Never changes terrain, inventory, or sends placement packets. */
public final class SchematicPreview {
    public static final SchematicPreview INSTANCE=new SchematicPreview();
    private record Cell(BlockPos local,BlockState state) { }
    private record Section(AABB bounds,List<Cell> cells) { }
    private List<Schematic.Block> source=List.of();
    private Map<BlockPos,BlockState> targets=Map.of();
    private List<Section> sections=List.of();
    private final GhostBlocks models=new GhostBlocks();
    private final ArrayDeque<BlockState> prepare=new ArrayDeque<>();
    private ClientLevel world;
    private Schematic.Pos origin=new Schematic.Pos(0,0,0);
    private AABB bounds;
    private int rotation,heightOffset,drawn;
    private long resources=-1;
    private boolean visible,placing,confirmed,hasTarget,limited;
    private String name="Schematic";

    public void install(){
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(mc->models.close());
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        Render3DEvent.TYPE.listen(this,this::render);
        Render2DEvent.TYPE.listen(this,this::hud);
        KeyInputEvent.TYPE.listen(this,this::key);
        MouseInputEvent.TYPE.listen(this,e->{
            if(placing&&Minecraft.getInstance().gui.screen()==null&&(e.button==0||e.button==1))e.setCancelled(true);
        });
    }
    public boolean active(){return world!=null&&world==Minecraft.getInstance().level&&!source.isEmpty();}
    public boolean confirmed(){return active()&&confirmed;}
    public boolean placing(){return placing;}
    public boolean visible(){return visible;}
    public void visible(boolean value){visible=value;}
    public int renderedCells(){return drawn;}
    public int texturedFaces(){return models.renderedFaces();}
    public void renderTextured(Vec3 eye,org.joml.Matrix4f view,org.joml.Matrix4f projection,com.mojang.blaze3d.buffers.GpuBufferSlice fog){
        if(active()&&visible)models.render(eye,view,projection,fog);
    }
    public Schematic.Pos origin(){return origin;}
    public int rotation(){return rotation;}
    public boolean matches(List<Schematic.Block> blocks){return active()&&source==blocks;}
    public void clear(){source=List.of();targets=Map.of();sections=List.of();prepare.clear();models.clear();world=null;visible=placing=confirmed=false;drawn=0;}
    public void set(String label,List<Schematic.Block> blocks,Schematic.Pos at,int turn){
        if(CreativeBuilder.INSTANCE.running())throw new IllegalStateException("Stop the active build before changing its placement");
        var mc=Minecraft.getInstance();if(mc.level==null)throw new IllegalStateException("Enter a world to preview the schematic");
        clear();name=label;source=blocks;world=mc.level;origin=at;rotation=Math.floorMod(turn,4);visible=true;hasTarget=true;
        rebuild();
    }
    public void transform(Schematic.Pos at,int turn){
        if(CreativeBuilder.INSTANCE.running())return;
        boolean rotated=rotation!=Math.floorMod(turn,4);
        if(!origin.equals(at)||rotated)confirmed=false;
        origin=at;rotation=Math.floorMod(turn,4);hasTarget=true;
        if(rotated)rebuild();
    }
    public void beginPlacement(){
        if(!active()||CreativeBuilder.INSTANCE.running())return;
        placing=true;confirmed=false;visible=true;hasTarget=false;heightOffset=0;
        Minecraft.getInstance().gui.setScreen(null);
    }
    public void confirm(){if(active()&&hasTarget){placing=false;confirmed=true;}}
    private void rebuild(){
        var cells=new LinkedHashMap<BlockPos,BlockState>();
        var groups=new LinkedHashMap<BlockPos,List<Cell>>();
        var palette=new HashSet<BlockState>();
        for(var block:source){
            var p=block.pos().rotate(rotation);var pos=new BlockPos(p.x(),p.y(),p.z());
            var state=BuilderMaterials.state(block.state()).rotate(Rotation.values()[rotation]);
            cells.put(pos,state);palette.add(state);
            groups.computeIfAbsent(new BlockPos(p.x()>>4,p.y()>>4,p.z()>>4),k->new ArrayList<>()).add(new Cell(pos,state));
        }
        targets=Map.copyOf(cells);var result=new ArrayList<Section>();bounds=null;
        for(var e:groups.entrySet()){
            var p=e.getKey();var box=new AABB(p.getX()*16,p.getY()*16,p.getZ()*16,p.getX()*16+16,p.getY()*16+16,p.getZ()*16+16);
            result.add(new Section(box,List.copyOf(e.getValue())));
        }
        for(var pos:cells.keySet())bounds=bounds==null?new AABB(pos):bounds.minmax(new AABB(pos));
        sections=List.copyOf(result);prepare.clear();prepare.addAll(palette);
    }
    private void tick(Minecraft mc){
        if(world!=null&&world!=mc.level){clear();return;}
        if(!active())return;
        if(resources!=AtlasResources.generation()){
            resources=AtlasResources.generation();models.clear();prepare.clear();prepare.addAll(new HashSet<>(targets.values()));
        }
        for(int n=0;n<8&&!prepare.isEmpty();n++)models.prepare(prepare.remove());
        if(!placing||mc.player==null||mc.gui.screen()!=null)return;
        Vec3 eye=mc.player.getEyePosition();
        var hit=world.clip(new ClipContext(eye,eye.add(mc.player.getLookAngle().scale(64)),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,mc.player));
        hasTarget=hit.getType()==HitResult.Type.BLOCK;
        if(hasTarget){var p=hit.getBlockPos().relative(hit.getDirection()).offset(0,heightOffset,0);origin=new Schematic.Pos(p.getX(),p.getY(),p.getZ());}
    }
    private void key(KeyInputEvent event){
        if(!placing||Minecraft.getInstance().gui.screen()!=null)return;
        if(!Set.of(GLFW.GLFW_KEY_ENTER,GLFW.GLFW_KEY_KP_ENTER,GLFW.GLFW_KEY_R,GLFW.GLFW_KEY_PAGE_UP,GLFW.GLFW_KEY_PAGE_DOWN,GLFW.GLFW_KEY_ESCAPE).contains(event.key))return;
        event.setCancelled(true);if(event.action!=GLFW.GLFW_PRESS)return;
        switch(event.key){
            case GLFW.GLFW_KEY_ENTER,GLFW.GLFW_KEY_KP_ENTER -> confirm();
            case GLFW.GLFW_KEY_R -> {rotation=(rotation+1)%4;rebuild();}
            case GLFW.GLFW_KEY_PAGE_UP -> heightOffset++;
            case GLFW.GLFW_KEY_PAGE_DOWN -> heightOffset--;
            case GLFW.GLFW_KEY_ESCAPE -> {placing=false;confirmed=false;net.spectre.client.gui.BuilderPanel.open();Minecraft.getInstance().gui.setScreen(new net.spectre.client.gui.ImGuiScreen());}
        }
    }
    private Vec3 offset(){return new Vec3(origin.x(),origin.y(),origin.z());}
    private void render(Render3DEvent event){
        drawn=0;limited=false;models.begin();
        var mc=Minecraft.getInstance();if(!active()||!visible||bounds==null||mc.player==null||placing&&!hasTarget)return;
        Vec3 shift=offset(),eye=mc.gameRenderer.mainCamera().position();
        Gizmos.cuboid(bounds.move(shift).inflate(.006),GizmoStyle.stroke(0xAD74E3DB,1.2F));
        var near=new ArrayList<Section>();
        for(var section:sections){var box=section.bounds.move(shift);if(box.getCenter().distanceToSqr(eye)<128*128&&(event.frustum==null||event.frustum.isVisible(box)))near.add(section);}
        near.sort(Comparator.comparingDouble(s->s.bounds.getCenter().add(shift).distanceToSqr(eye)));
        int faces=0;
        for(var section:near)for(var cell:section.cells){
            var pos=cell.local.offset(origin.x(),origin.y(),origin.z());
            if(!world.hasChunkAt(pos))continue;
            var actual=world.getBlockState(pos);if(actual.equals(cell.state))continue;
            if(Vec3.atCenterOf(pos).distanceToSqr(eye)>96*96)continue;
            if(faces>=24000){limited=true;return;}
            boolean conflict=!actual.isAir();
            var model=models.model(cell.state);
            if(model==null)continue;
            int fill=conflict?0x60FF6B70:0x556DD8DB,stroke=conflict?0xBDFF8585:0x887EEAF0;
            if(model.isEmpty()){
                boolean textured=models.chest(cell.state,pos,conflict);
                var shape=cell.state.getShape(world,pos);
                var boxes=shape.isEmpty()?List.of(new AABB(0,0,0,1,1,1)):shape.toAabbs();
                for(var box:boxes){Gizmos.cuboid(box.move(pos).deflate(.003),textured?GizmoStyle.stroke(stroke,.7F):GizmoStyle.strokeAndFill(stroke,.7F,fill));faces+=6;}
            }else{
                for(var q:model){
                    if(q.cull()!=null){var other=targets.get(cell.local.relative(q.cull()));if(other!=null&&other.isSolidRender())continue;}
                    models.add(q,cell.state,pos,conflict);
                    float[] v=q.xyz();Vec3[] points=new Vec3[4];
                    for(int i=0;i<4;i++)points[i]=new Vec3(pos.getX()+v[i*3],pos.getY()+v[i*3+1],pos.getZ()+v[i*3+2]);
                    Gizmos.rect(points[0],points[1],points[2],points[3],GizmoStyle.stroke(stroke,.6F));faces++;
                }
            }
            if(cell.state.hasProperty(BlockStateProperties.FACING)){
                var direction=cell.state.getValue(BlockStateProperties.FACING);Vec3 center=Vec3.atCenterOf(pos);
                Gizmos.arrow(center,center.add(direction.getStepX()*.65,direction.getStepY()*.65,direction.getStepZ()*.65),0xD9FFFFCF,1.3F);
            }
            drawn++;
        }
    }
    private void hud(Render2DEvent event){
        var mc=Minecraft.getInstance();if(!active()||!visible||mc.gui.screen()!=null)return;
        var g=event.graphics;int y=38;
        String title="SPECTRE / "+name+" / "+rotation*90+"° / "+origin.x()+", "+origin.y()+", "+origin.z();
        String controls=CreativeBuilder.INSTANCE.running()?CreativeBuilder.INSTANCE.status()+" | Ghost position locked":
            placing?(hasTarget?"Enter: confirm position | R: rotate | PgUp/PgDn: height | Esc: menu":"Aim at terrain within 64 blocks | Esc: menu"):
            confirmed?"Position confirmed | Right Shift > Base Builder to build or reposition":"Position changed | Confirm in Base Builder before building";
        String legend="Cyan: missing | Red: occupied | Finished blocks disappear"+(limited?" | Preview detail capped; move closer":"");
        int w=Math.max(mc.font.width(title),Math.max(mc.font.width(controls),mc.font.width(legend)))+20;
        int x=(g.guiWidth()-w)/2;g.fill(x,y-5,x+w,y+38,0xCF101923);
        g.text(mc.font,title,x+10,y,0xFF8FE7DF,true);g.text(mc.font,controls,x+10,y+12,0xFFE5EAF0,true);g.text(mc.font,legend,x+10,y+24,0xFFAABCCB,true);
    }
}
