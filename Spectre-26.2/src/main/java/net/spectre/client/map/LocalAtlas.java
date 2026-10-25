package net.spectre.client.map;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import java.util.*;
import java.util.concurrent.*;

/** Loaded terrain only. Copies live containers on the client thread; meshes immutable copies on one worker. */
public final class LocalAtlas implements AutoCloseable {
    private record Cells(int cx,int cz,int minY,int height,int[] cells,BlockState[] palette) {}
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"spectre-atlas-mesh");t.setDaemon(true);return t;});
    private final AtlasModels models=new AtlasModels();
    private final Map<Long,Long> refreshed=new HashMap<>();
    private Future<Cells> cellsFuture;
    private Future<AtlasMesh.Mesh> meshFuture;
    private Cells cells;
    private int paletteIndex;
    private LevelChunk capturing;
    private final List<PalettedContainer<BlockState>> sections=new ArrayList<>();
    private int sectionIndex,minY;
    private ClientLevel level;
    private AtlasMesh.Layer layer=AtlasMesh.Layer.SURFACE;
    private int low,high;
    private Set<Long> wanted=Set.of();
    private boolean closed;
    private long retryAt;
    public String error="";
    public Set<Long> wanted(){return wanted;}
    public int loaded(){return refreshed.size();}
    public void reset(){
        if(cellsFuture!=null)cellsFuture.cancel(true);if(meshFuture!=null)meshFuture.cancel(true);
        cellsFuture=null;meshFuture=null;cells=null;capturing=null;sections.clear();refreshed.clear();error="";retryAt=0;
    }
    public boolean configure(ClientLevel current,AtlasMesh.Layer mode,int from,int to){
        if(current==level && mode==layer && from==low && to==high)return false;
        reset();level=current;layer=mode;low=from;high=to;return true;
    }
    public AtlasMesh.Mesh tick(int centerX,int centerZ,int radius){
        if(closed || level==null)return null;
        List<Long> order=new ArrayList<>();
        for(int dz=-radius;dz<=radius;dz++)for(int dx=-radius;dx<=radius;dx++)order.add(AtlasGpu.key(centerX+dx,centerZ+dz));
        order.sort(Comparator.comparingDouble(k->Math.pow((int)(k>>32)-centerX,2)+Math.pow((int)(long)k-centerZ,2)));
        Set<Long> available=new LinkedHashSet<>();
        for(long key:order)if(chunk(key)!=null)available.add(key);
        wanted=Set.copyOf(available);refreshed.keySet().retainAll(wanted);
        if(System.currentTimeMillis()<retryAt)return null;
        try {
            if(meshFuture!=null && meshFuture.isDone()) {
                var mesh=meshFuture.get();meshFuture=null;
                long key=AtlasGpu.key(mesh.cx(),mesh.cz());
                if(wanted.contains(key)){refreshed.put(key,System.currentTimeMillis());error="";return mesh;}
            }
            if(cellsFuture!=null && cellsFuture.isDone()){cells=cellsFuture.get();cellsFuture=null;paletteIndex=1;}
            if(cells!=null){
                long deadline=System.nanoTime()+2_000_000;
                do {if(paletteIndex>=cells.palette.length)break;models.prepare(cells.palette[paletteIndex++]);}while(System.nanoTime()<deadline);
                if(paletteIndex>=cells.palette.length){
                    AtlasModels.Model[] baked=new AtlasModels.Model[cells.palette.length];
                    boolean[] opaque=new boolean[baked.length],roof=new boolean[baked.length];
                    for(int i=1;i<baked.length;i++){
                        var state=cells.palette[i];baked[i]=models.models.get(state.toString());opaque[i]=state.isSolidRender();
                        String name=state.toString();roof[i]=opaque[i]&&!name.contains("_log")&&!name.contains("_wood")&&!name.contains("mushroom_stem");
                    }
                    var snapshot=new AtlasMesh.Snapshot(cells.cx,cells.cz,cells.minY,cells.height,cells.cells,opaque,roof,baked);
                    var selected=layer;int from=low,to=high;
                    meshFuture=worker.submit(()->AtlasMesh.build(snapshot,selected,from,to));cells=null;
                }
            }
            if(cellsFuture!=null || meshFuture!=null || cells!=null)return null;
            if(capturing==null){
                long now=System.currentTimeMillis();
                // Finish nearby missing chunks before periodic updates; no unbounded task queue.
                for(long key:available)if(!refreshed.containsKey(key)){capturing=chunk(key);break;}
                if(capturing==null)for(long key:available)if(now-refreshed.getOrDefault(key,0L)>5000){capturing=chunk(key);break;}
                if(capturing==null)return null;
                sectionIndex=0;sections.clear();minY=level.getMinY();
            }
            long key=AtlasGpu.key(capturing.getPos().x(),capturing.getPos().z());
            if(!wanted.contains(key)||chunk(key)!=capturing){capturing=null;sections.clear();return null;}
            long deadline=System.nanoTime()+2_000_000;int copied=0;
            while(sectionIndex<capturing.getSections().length && copied++<8){
                var section=capturing.getSections()[sectionIndex++];
                sections.add(section.hasOnlyAir()?null:section.getStates().copy());
                if(System.nanoTime()>deadline)break;
            }
            if(sectionIndex==capturing.getSections().length){
                int cx=capturing.getPos().x(),cz=capturing.getPos().z(),bottom=minY;
                var copies=new ArrayList<>(sections);capturing=null;sections.clear();
                cellsFuture=worker.submit(()->decode(cx,cz,bottom,copies));
            }
        }catch(Exception ex){reset();error="Map update: "+Objects.toString(ex.getCause(),ex.toString());retryAt=System.currentTimeMillis()+5000;}
        return null;
    }
    private LevelChunk chunk(long key){return level.getChunkSource().getChunk((int)(key>>32),(int)key,ChunkStatus.FULL,false);}
    private static Cells decode(int cx,int cz,int minY,List<PalettedContainer<BlockState>> sections){
        int[] data=new int[sections.size()*4096];
        List<BlockState> palette=new ArrayList<>();palette.add(null);
        Map<BlockState,Integer> lookup=new IdentityHashMap<>();
        for(int s=0;s<sections.size();s++){
            if(Thread.currentThread().isInterrupted())throw new CancellationException();
            var section=sections.get(s);if(section==null)continue;
            for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++){
                var state=section.get(x,y,z);if(state.isAir())continue;
                Integer id=lookup.get(state);if(id==null){id=palette.size();palette.add(state);lookup.put(state,id);}
                data[s*4096+y*256+z*16+x]=id;
            }
        }
        return new Cells(cx,cz,minY,sections.size()*16,data,palette.toArray(BlockState[]::new));
    }
    @Override public void close(){closed=true;reset();worker.shutdownNow();}
}
