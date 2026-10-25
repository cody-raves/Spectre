package net.spectre.client.util;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/** Received-world evidence, distinct from the older chunk heat inference. */
public final class SuspectObjects {
    public enum Category {
        CONTAINERS("Containers",0xFFFFCA54), ENDER_STORAGE("Ender chests",0xFFB78AFF),
        HOPPERS("Hoppers",0xFF51E5FF), MACHINES("Machines",0xFFFF944D),
        REDSTONE("Redstone",0xFFFF547D), SPAWNERS("Spawners",0xFFFF63F1),
        DISPLAYS("Armor / displays",0xFF70A6FF), GEODES("Amethyst",0xFFD19BFF),
        GROWTH("Growth / bees",0xFF91F56B), ANOMALIES("Light anomalies",0xFFF2F57D);
        public final String label; public final int color;
        Category(String label,int color){this.label=label;this.color=color;}
        public String id(){return name().toLowerCase(Locale.ROOT).replace('_','-');}
    }
    public record Found(BlockPos pos,BlockState state,Category category){}
    /** Some evidence blocks have no inventory item; never substitute their drops as evidence. */
    public record Icon(Item item,Identifier texture,String label){
        public static Icon item(Item item){return new Icon(item,null,item.getDefaultInstance().getHoverName().getString());}
    }
    public static final Icon CAVE_VINES_ICON=new Icon(null,Identifier.withDefaultNamespace("textures/block/cave_vines.png"),"Cave vines");
    public record Marker(AABB bounds,List<AABB> shapes,Category category,Map<Icon,Integer> icons,String label,String detail){
        public Marker(AABB bounds,List<AABB> shapes,Category category,Map<Icon,Integer> icons,String label){this(bounds,shapes,category,icons,label,"");}
    }
    public record Focus(Marker target,List<Marker> nearby,double distance){}

    public static Category category(BlockState state){
        var b=state.getBlock();
        if(b instanceof EnderChestBlock)return Category.ENDER_STORAGE;
        if(b instanceof AbstractChestBlock<?>||b instanceof BarrelBlock||b instanceof ShulkerBoxBlock)return Category.CONTAINERS;
        if(b instanceof HopperBlock)return Category.HOPPERS;
        if(b instanceof PistonBaseBlock||b instanceof AbstractFurnaceBlock||b instanceof DispenserBlock||b instanceof ObserverBlock||b instanceof CrafterBlock||b instanceof BrewingStandBlock)return Category.MACHINES;
        if(b instanceof RedStoneWireBlock||b instanceof DiodeBlock||b instanceof RedstoneTorchBlock||b instanceof LeverBlock||b instanceof ButtonBlock||b instanceof BasePressurePlateBlock||b instanceof TripWireHookBlock||b==Blocks.REDSTONE_BLOCK||b==Blocks.REDSTONE_LAMP||b==Blocks.DAYLIGHT_DETECTOR||b==Blocks.TARGET)return Category.REDSTONE;
        if(b instanceof SpawnerBlock||b instanceof TrialSpawnerBlock)return Category.SPAWNERS;
        if(b==Blocks.AMETHYST_BLOCK||b==Blocks.BUDDING_AMETHYST||ActivityClues.kind(state)==ActivityClues.Kind.AMETHYST)return Category.GEODES;
        if(ActivityClues.kind(state)!=null)return Category.GROWTH;
        return null;
    }
    public static Icon icon(BlockState state){
        if(state.is(Blocks.REDSTONE_WIRE))return Icon.item(Items.REDSTONE);
        if(state.is(Blocks.CAVE_VINES)||state.is(Blocks.CAVE_VINES_PLANT))return CAVE_VINES_ICON;
        if(state.is(Blocks.KELP_PLANT))return Icon.item(Items.KELP);
        if(state.is(Blocks.COCOA))return Icon.item(Items.COCOA_BEANS);
        var item=state.getBlock().asItem();return Icon.item(item==Items.AIR?Items.AMETHYST_SHARD:item);
    }
    public static Marker block(Found found,net.minecraft.client.multiplayer.ClientLevel level){
        var shape=found.state.getShape(level,found.pos);
        var boxes=shape.isEmpty()?List.of(new AABB(found.pos)):shape.toAabbs().stream().map(b->b.move(found.pos)).toList();
        AABB bounds=boxes.getFirst();for(var b:boxes)bounds=bounds.minmax(b);
        var icon=icon(found.state);
        return new Marker(bounds,List.of(bounds),found.category,Map.of(icon,1),icon.label());
    }
    /** Join amethyst across chunk borders; outline the formation, never its chunk. */
    public static List<Marker> geodes(Map<BlockPos,Found> cells,int minimumClusters,int minimumMaturity,boolean includeImmature){
        var remaining=new HashMap<>(cells);var result=new ArrayList<Marker>();var todo=new ArrayDeque<BlockPos>();
        while(!remaining.isEmpty()){
            var start=remaining.keySet().iterator().next();todo.add(start);AABB bounds=null;var counts=new HashMap<Icon,Integer>();int mature=0,small=0,medium=0,large=0,budding=0;
            while(!todo.isEmpty()){
                var pos=todo.removeFirst();var cell=remaining.remove(pos);if(cell==null)continue;
                if(cell.state.is(Blocks.AMETHYST_CLUSTER))mature++;
                else if(cell.state.is(Blocks.SMALL_AMETHYST_BUD))small++;
                else if(cell.state.is(Blocks.MEDIUM_AMETHYST_BUD))medium++;
                else if(cell.state.is(Blocks.LARGE_AMETHYST_BUD))large++;
                if(cell.state.is(Blocks.BUDDING_AMETHYST))budding++;
                bounds=bounds==null?new AABB(pos):bounds.minmax(new AABB(pos));counts.merge(icon(cell.state),1,Integer::sum);
                for(var d:Direction.values()){var neighbor=pos.relative(d);if(remaining.containsKey(neighbor))todo.addLast(neighbor);}
            }
            int buds=small+medium+large;
            int maturity=mature+buds==0?0:100*mature/(mature+buds);
            if(includeImmature||budding>0&&mature>=minimumClusters&&maturity>=minimumMaturity)
                result.add(new Marker(bounds,List.of(bounds),Category.GEODES,Map.copyOf(counts),"Amethyst formation",mature+" mature / "+buds+" buds - "+maturity+"% mature\n"+small+" small / "+medium+" medium / "+large+" large"));
        }return result;
    }
    public static List<Marker> plants(Map<BlockPos,Found> cells,java.util.function.ToIntFunction<ActivityClues.Kind> threshold,boolean includeShort){
        var result=new ArrayList<Marker>();
        for(var entry:cells.entrySet()){
            var kind=ActivityClues.kind(entry.getValue().state);var below=cells.get(entry.getKey().below());
            if(below!=null&&ActivityClues.kind(below.state)==kind)continue;
            var counts=new HashMap<Icon,Integer>();int length=0;var cursor=entry.getKey();
            while(true){var cell=cells.get(cursor);if(cell==null||ActivityClues.kind(cell.state)!=kind)break;counts.merge(icon(cell.state),1,Integer::sum);length++;cursor=cursor.above();}
            if(!includeShort&&length<threshold.applyAsInt(kind))continue;
            var box=new AABB(entry.getKey()).minmax(new AABB(cursor.below()));
            result.add(new Marker(box,List.of(box),Category.GROWTH,Map.copyOf(counts),kind.label+" growth",length+" blocks long - threshold "+threshold.applyAsInt(kind)));
        }return result;
    }
    public static Focus focus(List<Marker> markers,Vec3 eye,Vec3 direction,double range){
        Marker best=null;double bestDistance=Double.MAX_VALUE;boolean exactBest=false;Vec3 end=eye.add(direction.scale(range));
        for(var marker:markers){
            if(marker.bounds.distanceToSqr(eye)>range*range)continue;
            var exact=marker.bounds.clip(eye,end);boolean hit=exact.isPresent()||marker.bounds.contains(eye);
            var close=hit?exact:marker.bounds.inflate(Math.min(2,Math.sqrt(marker.bounds.distanceToSqr(eye))*.035)+.15).clip(eye,end);
            if(!hit&&close.isEmpty())continue;
            double distance=close.map(eye::distanceTo).orElse(0.0);
            if(best==null||hit&&!exactBest||hit==exactBest&&distance<bestDistance){best=marker;bestDistance=distance;exactBest=hit;}
        }
        if(best==null)return null;
        var nearby=new ArrayList<Marker>();var neighborhood=best.bounds.inflate(12);
        for(var marker:markers)if(marker.bounds.intersects(neighborhood)&&marker.bounds.distanceToSqr(eye)<=range*range)nearby.add(marker);
        return new Focus(best,List.copyOf(nearby),Math.sqrt(best.bounds.distanceToSqr(eye)));
    }
}
