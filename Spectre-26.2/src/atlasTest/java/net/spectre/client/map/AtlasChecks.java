package net.spectre.client.map;

import java.util.*;

public final class AtlasChecks {
    private static int checks;
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
    private static AtlasModels.Model cube(){
        var texture=new AtlasModels.Texture(1,1,new int[]{0xffffffff});
        float[][] vertices={{1,0,0,1,1,0,1,1,1,1,0,1},{0,0,1,0,1,1,0,1,0,0,0,0},
            {0,1,0,0,1,1,1,1,1,1,1,0},{0,0,1,0,0,0,1,0,0,1,0,1},
            {1,0,1,1,1,1,0,1,1,0,0,1},{0,0,0,0,1,0,1,1,0,1,0,0}};
        List<AtlasModels.Quad> quads=new ArrayList<>();
        for(int i=0;i<6;i++)quads.add(new AtlasModels.Quad(vertices[i],new float[]{0,1,0,0,1,0,1,1},texture,0xffffff,1,i));
        return new AtlasModels.Model(quads);
    }
    private static AtlasMesh.Snapshot snapshot(int[] cells){return new AtlasMesh.Snapshot(-1,2,-16,16,cells,new boolean[]{false,true},new boolean[]{false,true},new AtlasModels.Model[]{null,cube()});}
    public static void main(String[] args){
        var camera=new AtlasCamera();camera.x=-.01;camera.z=-16.01;
        check(camera.chunkX()==-1&&camera.chunkZ()==-2,"negative chunk coordinates use floor");
        camera.yaw=0;camera.pitch=Math.PI/2;camera.zoom=2;camera.x=0;camera.z=0;camera.pan(8,4);
        check(Math.abs(camera.x+4)<1e-6&&Math.abs(camera.z+2)<1e-6,"drag follows screen axes");
        camera.yaw=Math.PI/2;camera.x=0;camera.z=0;camera.pan(8,0);
        check(Math.abs(camera.x)<1e-6&&Math.abs(camera.z-4)<1e-6,"pan follows orbit");
        camera.scroll(1e6);check(camera.zoom==40,"zoom upper bound");camera.scroll(-1e6);check(camera.zoom==1.5,"zoom lower bound");
        camera.orbit(0,1e6);check(camera.pitch==1.5,"pitch upper bound");camera.orbit(0,-1e6);check(camera.pitch==.25,"pitch lower bound");
        int[] cells=new int[4096];cells[8*256+8*16+8]=1;
        var one=AtlasMesh.build(snapshot(cells),AtlasMesh.Layer.SLICE,-16,-1);
        check(one.vertices()==36,"isolated block has six faces");
        check(one.cx()==-1&&one.cz()==2,"chunk identity preserved");
        cells[8*256+8*16+9]=1;
        check(AtlasMesh.build(snapshot(cells),AtlasMesh.Layer.SLICE,-16,-1).vertices()==60,"shared opaque face culled");
        check(AtlasMesh.build(snapshot(cells),AtlasMesh.Layer.SLICE,-7,-1).vertices()==0,"ceiling/floor clipping");
        Arrays.fill(cells,1);
        check(AtlasMesh.build(snapshot(cells),AtlasMesh.Layer.CAVES,-16,-1).vertices()==0,"unknown chunk edges are not caves");
        cells[8*256+8*16+8]=0;
        check(AtlasMesh.build(snapshot(cells),AtlasMesh.Layer.CAVES,-16,-1).vertices()==30,"known cavity has walls/floor but no ceiling");
        check(AtlasMesh.build(snapshot(new int[4096]),AtlasMesh.Layer.SURFACE,-16,-1).vertices()==0,"explicit air clears geometry");
        var stone=snapshot(cells);long start=System.nanoTime();int vertices=0;
        for(int i=0;i<25;i++)vertices+=AtlasMesh.build(stone,AtlasMesh.Layer.SURFACE,-16,-1).vertices();
        System.out.println(checks+" atlas checks passed; 25 surface meshes: "+(System.nanoTime()-start)/1_000_000+" ms, "+vertices+" vertices");
    }
}
