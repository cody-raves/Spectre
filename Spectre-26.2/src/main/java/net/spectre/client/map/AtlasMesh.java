package net.spectre.client.map;

import java.util.*;

/** Pure CPU geometry building. Camera motion never calls this class. */
public final class AtlasMesh {
    public enum Layer { SURFACE, CAVES, SLICE }
    public record Snapshot(int cx, int cz, int minY, int height, int[] cells,
                           boolean[] opaque, boolean[] roof, AtlasModels.Model[] models) {
        int cell(int x, int y, int z) {
            if (x < 0 || x >= 16 || z < 0 || z >= 16 || y < minY || y >= minY + height) return -1;
            return cells[((y-minY)*16+z)*16+x];
        }
    }
    public record Batch(AtlasModels.Texture texture, float[] vertices) {}
    public record Mesh(int cx, int cz, List<Batch> batches, int vertices) {}
    private static final int[][] DIR = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
    private static final int[] TRI = {0,1,2,0,2,3};

    public static Mesh build(Snapshot s, Layer layer, int low, int high) {
        Map<AtlasModels.Texture, Floats> batches = new LinkedHashMap<>();
        int[] roofs = new int[256]; Arrays.fill(roofs, s.minY-1);
        for (int z=0; z<16; z++) for(int x=0; x<16; x++)
            for(int y=s.minY+s.height-1; y>=s.minY; y--) {
                int id=s.cell(x,y,z);
                if(id>0 && s.roof[id]) { roofs[z*16+x]=y; break; }
            }
        int count=0;
        for(int y=Math.max(low,s.minY); y<=Math.min(high,s.minY+s.height-1); y++) {
            if(Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
            for(int z=0; z<16; z++) for(int x=0; x<16; x++) {
                int id=s.cell(x,y,z); if(id<=0 || s.models[id]==null) continue;
                if(layer==Layer.SURFACE && y<roofs[z*16+x]-3) continue;
                int mask=0;
                for(int d=0; d<6; d++) {
                    int nx=x+DIR[d][0], ny=y+DIR[d][1], nz=z+DIR[d][2];
                    int neighbor=s.cell(nx,ny,nz);
                    if(layer==Layer.CAVES) {
                        // Unknown borders and clipped sections are never cave air.
                        if(d==3 || neighbor<0 || ny<low || ny>high || ny>=roofs[nz*16+nx]) continue;
                    } else if(ny<low || ny>high) neighbor=-1;
                    if(neighbor<0 || !s.opaque[neighbor]) mask|=1<<d;
                }
                if(mask==0) continue;
                for(var q:s.models[id].quads()) {
                    if(q.cull()>=0 && (mask & (1<<q.cull()))==0) continue;
                    Floats out=batches.computeIfAbsent(q.texture(),ignored->new Floats());
                    for(int v:TRI) {
                        // Chunk-local coordinates preserve precision near the world border.
                        out.add(x+q.xyz()[v*3], y+q.xyz()[v*3+1], z+q.xyz()[v*3+2],
                            q.uv()[v*2], q.uv()[v*2+1],
                            ((q.tint()>>16)&255)/255f*q.shade(), ((q.tint()>>8)&255)/255f*q.shade(),
                            (q.tint()&255)/255f*q.shade());
                    }
                    count+=6;
                    if(count>1_000_000) throw new IllegalStateException("Chunk geometry limit reached; narrow the Y slice");
                }
            }
        }
        List<Batch> result=new ArrayList<>();
        batches.forEach((tex,data)->result.add(new Batch(tex,data.finish())));
        return new Mesh(s.cx,s.cz,List.copyOf(result),count);
    }
    private static final class Floats {
        float[] data=new float[4096]; int size;
        void add(float a,float b,float c,float d,float e,float f,float g,float h) {
            if(size+8>data.length)data=Arrays.copyOf(data,data.length*2);
            data[size++]=a;data[size++]=b;data[size++]=c;data[size++]=d;
            data[size++]=e;data[size++]=f;data[size++]=g;data[size++]=h;
        }
        float[] finish(){return Arrays.copyOf(data,size);}
    }
}
