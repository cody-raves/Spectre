package net.spectre.client.builder;

import java.util.*;

/** Game-independent snapshot. Original states and auxiliary NBT are retained. */
public record Schematic(String name, int dataVersion, List<Region> regions, Map<String,Object> nbt) {
    public record Pos(int x, int y, int z) {
        public Pos add(Pos other) { return new Pos(Math.addExact(x,other.x),Math.addExact(y,other.y),Math.addExact(z,other.z)); }
        public Pos rotate(int quarterTurns) {
            return switch(Math.floorMod(quarterTurns,4)) {
                case 1 -> new Pos(-z,y,x); case 2 -> new Pos(-x,y,-z); case 3 -> new Pos(z,y,-x); default -> this;
            };
        }
    }
    public record Block(Pos pos, String state) { }
    public record Region(String name, Pos min, int width, int height, int length, List<String> palette, int[] states) {
        public Region {
            palette=List.copyOf(palette); states=states.clone();
            if ((long)width*height*length!=states.length) throw new IllegalArgumentException("Region size mismatch");
        }
        @Override public int[] states() { return states.clone(); }
        public String state(int x,int y,int z) { return palette.get(states[x+z*width+y*width*length]); }
    }
    public Schematic { regions=List.copyOf(regions); nbt=Collections.unmodifiableMap(nbt); }
    public long volume() { return regions.stream().mapToLong(r->(long)r.width*r.height*r.length).sum(); }
    public List<Block> blocks() {
        Map<Pos,String> all=new LinkedHashMap<>();
        for(Region r:regions) for(int y=0;y<r.height;y++) for(int z=0;z<r.length;z++) for(int x=0;x<r.width;x++) {
            String state=r.state(x,y,z); Pos pos=r.min.add(new Pos(x,y,z));
            String previous=all.putIfAbsent(pos,state);
            if(previous!=null&&!previous.equals(state)) throw new IllegalArgumentException("Conflicting overlapping regions at "+pos);
        }
        return all.entrySet().stream().filter(e->!isAir(e.getValue())).map(e->new Block(e.getKey(),e.getValue())).toList();
    }
    public static boolean isAir(String state) { return state.equals("minecraft:air")||state.equals("minecraft:cave_air")||state.equals("minecraft:void_air"); }
}
