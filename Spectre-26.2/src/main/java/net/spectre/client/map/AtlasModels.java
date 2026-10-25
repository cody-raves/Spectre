package net.spectre.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Immutable copies of resource-pack models. Never reads terrain from the world. */
public final class AtlasModels {
    public record Texture(int width,int height,int[] pixels) {}
    public record Quad(float[] xyz,float[] uv,Texture texture,int tint,float shade,int cull) {}
    public record Model(List<Quad> quads) {}
    public final Map<String,Model> models=new ConcurrentHashMap<>();
    private final Map<Identifier,Texture> textures=new HashMap<>();
    private static final Direction[] DIRECTIONS={Direction.EAST,Direction.WEST,Direction.UP,Direction.DOWN,Direction.SOUTH,Direction.NORTH};

    public void prepare(String canonical) {
        if(models.containsKey(canonical))return;
        Minecraft mc=Minecraft.getInstance();
        String[] split=canonical.split("\\[",2);
        BlockState state=BuiltInRegistries.BLOCK.getValue(Identifier.parse(split[0])).defaultBlockState();
        if(split.length>1) for(String pair:split[1].replace("]","").split(",")) {
            String[] kv=pair.split("=",2);
            Property<?> p=state.getBlock().getStateDefinition().getProperty(kv[0]);
            if(p!=null&&kv.length==2)state=with(state,p,kv[1]);
        }
        prepare(canonical,state);
    }
    public void prepare(BlockState state) {
        String key=state.toString();
        if(!models.containsKey(key))prepare(key,state);
    }
    private void prepare(String canonical,BlockState state) {
        Minecraft mc=Minecraft.getInstance();
        String[] split={BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()};
        List<BlockStateModelPart> parts=new ArrayList<>();
        mc.getModelManager().getBlockStateModelSet().get(state).collectParts(RandomSource.create(42),parts);
        List<Quad> quads=new ArrayList<>();
        for(BlockStateModelPart part:parts) {
            for(int d=-1;d<6;d++)for(BakedQuad q:part.getQuads(d<0?null:DIRECTIONS[d])) {
                var sprite=q.materialInfo().sprite();
                float[] xyz=new float[12],uv=new float[8];
                for(int i=0;i<4;i++) {
                    var p=q.position(i);xyz[i*3]=p.x();xyz[i*3+1]=p.y();xyz[i*3+2]=p.z();
                    uv[i*2]=(UVPair.unpackU(q.packedUV(i))-sprite.getU0())/(sprite.getU1()-sprite.getU0());
                    uv[i*2+1]=(UVPair.unpackV(q.packedUV(i))-sprite.getV0())/(sprite.getV1()-sprite.getV0());
                }
                int tint=0xffffff;
                if(q.materialInfo().isTinted()) {
                    // Biomes are not captured yet: explicit neutral temperate tint.
                    tint=split[0].equals("minecraft:water")?0x619fd4:canonical.contains("spruce")?0x619961:
                        canonical.contains("birch")?0x80a755:canonical.contains("redstone")?0xb82c20:0x86b85b;
                }
                float shade=switch(q.direction()){case UP->1f;case DOWN->.50f;case EAST->.84f;case WEST->.69f;case SOUTH->.77f;case NORTH->.91f;};
                quads.add(new Quad(xyz,uv,texture(sprite.contents().name()),tint,q.materialInfo().shade()?shade:1,d));
            }
        }
        if(quads.isEmpty()&&(canonical.contains("minecraft:water")||canonical.contains("minecraft:lava"))) {
            boolean water=canonical.contains("minecraft:water");
            Texture tex=texture(Identifier.withDefaultNamespace("block/"+(water?"water_still":"lava_still")));
            float[][] vertices={{1,0,0,1,1,0,1,1,1,1,0,1},{0,0,1,0,1,1,0,1,0,0,0,0},
                {0,1,0,0,1,1,1,1,1,1,1,0},{0,0,1,0,0,0,1,0,0,1,0,1},
                {1,0,1,1,1,1,0,1,1,0,0,1},{0,0,0,0,1,0,1,1,0,1,0,0}};
            for(int d=0;d<6;d++)quads.add(new Quad(vertices[d],new float[]{0,1,0,0,1,0,1,1},tex,water?0x5292bd:0xffffff,d==2?1:.8f,d));
        }
        models.put(canonical,new Model(List.copyOf(quads)));
    }
    private Texture texture(Identifier id) {
        return textures.computeIfAbsent(id,key->{
            var path=Identifier.fromNamespaceAndPath(key.getNamespace(),"textures/"+key.getPath()+".png");
            try(var stream=Minecraft.getInstance().getResourceManager().open(path);var image=NativeImage.read(stream)) {
                int w=image.getWidth(),h=Math.min(w,image.getHeight());
                return new Texture(w,h,Arrays.copyOf(image.getPixels(),w*h));
            }catch(Exception e){return new Texture(1,1,new int[]{0xff99958a});}
        });
    }
    private static <T extends Comparable<T>> BlockState with(BlockState state,Property<T> p,String value) {
        return p.getValue(value).map(v->state.setValue(p,v)).orElse(state);
    }
}

