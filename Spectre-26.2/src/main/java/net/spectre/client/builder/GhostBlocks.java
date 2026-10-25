package net.spectre.client.builder;

import java.util.*;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.model.object.chest.ChestModel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.client.renderer.blockentity.state.ChestRenderState.ChestMaterialType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.core.*;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Native, depth-tested translucent block models. Uses the live resource atlas,
 * including animated textures, and never puts preview blocks into the world. */
final class GhostBlocks {
    record Quad(float[] xyz,float[] uv,Identifier atlas,Direction normal,int tint,float shade,Direction cull) {}
    private record Face(Quad quad,BlockPos pos,int color) {}
    private record Chest(BlockState state,BlockPos pos,boolean conflict) {}
    private final Map<BlockState,List<Quad>> models=new HashMap<>();
    private final Map<Identifier,List<Face>> batches=new LinkedHashMap<>();
    private final List<Chest> chests=new ArrayList<>();
    private final Map<ChestType,ChestModel> chestModels=new EnumMap<>(ChestType.class);
    private ProjectionMatrixBuffer projection;
    private int renderedFaces;
    void begin(){batches.clear();chests.clear();renderedFaces=0;}
    void clear(){begin();models.clear();chestModels.clear();}
    void close(){clear();if(projection!=null){projection.close();projection=null;}}
    int renderedFaces(){return renderedFaces;}
    List<Quad> model(BlockState state){return models.get(state);}
    void prepare(BlockState state){
        if(models.containsKey(state))return;
        var mc=Minecraft.getInstance();var parts=new ArrayList<BlockStateModelPart>();
        mc.getModelManager().getBlockStateModelSet().get(state).collectParts(RandomSource.create(42),parts);
        var quads=new ArrayList<Quad>();
        for(var part:parts)for(int side=-1;side<6;side++){
            Direction cull=side<0?null:Direction.values()[side];
            for(var q:part.getQuads(cull)){
                float[] xyz=new float[12],uv=new float[8];
                for(int i=0;i<4;i++){
                    var p=q.position(i);xyz[i*3]=p.x();xyz[i*3+1]=p.y();xyz[i*3+2]=p.z();
                    uv[i*2]=UVPair.unpackU(q.packedUV(i));uv[i*2+1]=UVPair.unpackV(q.packedUV(i));
                }
                float shade=q.materialInfo().shade()?switch(q.direction()){case UP->1;case DOWN->.55F;case EAST,WEST->.8F;default->.9F;}:1;
                quads.add(new Quad(xyz,uv,q.materialInfo().sprite().atlasLocation(),q.direction(),q.materialInfo().tintIndex(),shade,cull));
            }
        }
        if(quads.isEmpty()&&(state.is(Blocks.WATER)||state.is(Blocks.LAVA))){
            var sprite=mc.getAtlasManager().getAtlasOrThrow(net.minecraft.data.AtlasIds.BLOCKS)
                .getSprite(Identifier.withDefaultNamespace(state.is(Blocks.WATER)?"block/water_still":"block/lava_still"));
            float top=.89F;
            float[][] xyz={{1,0,0,1,top,0,1,top,1,1,0,1},{0,0,1,0,top,1,0,top,0,0,0,0},
                {0,top,0,0,top,1,1,top,1,1,top,0},{0,0,1,0,0,0,1,0,0,1,0,1},
                {1,0,1,1,top,1,0,top,1,0,0,1},{0,0,0,0,top,0,1,top,0,1,0,0}};
            Direction[] directions={Direction.EAST,Direction.WEST,Direction.UP,Direction.DOWN,Direction.SOUTH,Direction.NORTH};
            for(int i=0;i<6;i++)quads.add(new Quad(xyz[i],new float[]{sprite.getU0(),sprite.getV1(),sprite.getU0(),sprite.getV0(),sprite.getU1(),sprite.getV0(),sprite.getU1(),sprite.getV1()},sprite.atlasLocation(),directions[i],state.is(Blocks.WATER)?0:-1,i==2?1:.8F,directions[i]));
        }
        models.put(state,List.copyOf(quads));
    }
    boolean chest(BlockState state,BlockPos pos,boolean conflict){
        if(!(state.getBlock() instanceof ChestBlock)&&!state.is(Blocks.ENDER_CHEST))return false;
        chests.add(new Chest(state,pos,conflict));return true;
    }
    void add(Quad quad,BlockState state,BlockPos pos,boolean conflict){
        int tint=0xffffff;
        if(quad.tint>=0){var source=Minecraft.getInstance().getBlockColors().getTintSource(state,quad.tint);if(source!=null)tint=source.color(state);}
        float r=(tint>>16&255)*quad.shade,g=(tint>>8&255)*quad.shade,b=(tint&255)*quad.shade;
        // Keep the material readable; the cyan/red edge is the main highlight.
        int color=0x88000000|Math.round(r*(conflict?1:.92F))<<16|Math.round(g*(conflict?.58F:1))<<8|Math.round(b*(conflict?.58F:1));
        batches.computeIfAbsent(quad.atlas,k->new ArrayList<>()).add(new Face(quad,pos,color));
    }
    void render(Vec3 eye,Matrix4f view,Matrix4f projectionMatrix,GpuBufferSlice fog){
        if(batches.isEmpty()&&chests.isEmpty())return;
        var mc=Minecraft.getInstance();var submissions=new SubmitNodeStorage();var poses=new PoseStack();
        for(var batch:batches.entrySet())submissions.submitCustomGeometry(poses,RenderTypes.entityTranslucentEmissive(batch.getKey(),false),(pose,out)->{
            for(var face:batch.getValue()){
                var q=face.quad;float x=(float)(face.pos.getX()-eye.x),y=(float)(face.pos.getY()-eye.y),z=(float)(face.pos.getZ()-eye.z);
                for(int i=0;i<4;i++)out.addVertex(pose,x+q.xyz[i*3],y+q.xyz[i*3+1],z+q.xyz[i*3+2])
                    .setColor(face.color).setUv(q.uv[i*2],q.uv[i*2+1]).setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(0xF000F0).setNormal(pose,q.normal.getStepX(),q.normal.getStepY(),q.normal.getStepZ());
            }
        });
        for(var chest:chests){
            var state=chest.state;var type=state.hasProperty(ChestBlock.TYPE)?state.getValue(ChestBlock.TYPE):ChestType.SINGLE;
            var model=chestModels.computeIfAbsent(type,t->new ChestModel(mc.getEntityModels().bakeLayer(ChestRenderer.LAYERS.select(t))));
            var material=state.is(Blocks.ENDER_CHEST)?ChestMaterialType.ENDER_CHEST:state.is(Blocks.TRAPPED_CHEST)?ChestMaterialType.TRAPPED:ChestMaterialType.REGULAR;
            if(state.getBlock() instanceof CopperChestBlock copper)material=switch(copper.getState()){
                case UNAFFECTED->ChestMaterialType.COPPER_UNAFFECTED;case EXPOSED->ChestMaterialType.COPPER_EXPOSED;
                case WEATHERED->ChestMaterialType.COPPER_WEATHERED;case OXIDIZED->ChestMaterialType.COPPER_OXIDIZED;};
            var sprite=mc.getAtlasManager().get(Sheets.chooseSprite(material,type));
            poses.pushPose();poses.translate(chest.pos.getX()-eye.x,chest.pos.getY()-eye.y,chest.pos.getZ()-eye.z);
            poses.mulPose(ChestRenderer.modelTransformation(state.getValue(ChestBlock.FACING)));
            submissions.submitModel(model,0F,poses,RenderTypes.entityTranslucentEmissive(sprite.atlasLocation(),false),0xF000F0,OverlayTexture.NO_OVERLAY,chest.conflict?0x88FF9999:0x88EBFFFF,sprite,0,null);
            poses.popPose();
        }
        if(projection==null)projection=new ProjectionMatrixBuffer("spectre ghost blocks");
        var previousFog=RenderSystem.getShaderFog();var modelView=RenderSystem.getModelViewStack();
        RenderSystem.backupProjectionMatrix();RenderSystem.setProjectionMatrix(projection.getBuffer(projectionMatrix),ProjectionType.PERSPECTIVE);
        modelView.pushMatrix();modelView.mul(view);RenderSystem.setShaderFog(fog);
        try{mc.gameRenderer.featureRenderDispatcher().renderAllFeatures(submissions);renderedFaces=batches.values().stream().mapToInt(List::size).sum()+chests.size()*18;}
        finally{modelView.popMatrix();RenderSystem.restoreProjectionMatrix();RenderSystem.setShaderFog(previousFog);}
    }
}
