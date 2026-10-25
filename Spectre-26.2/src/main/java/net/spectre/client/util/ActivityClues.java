package net.spectre.client.util;

import java.util.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Conservative vanilla generation clues, not a probability that a base exists. */
public final class ActivityClues {
    public enum Kind {
        KELP("Kelp"), CAVE_VINES("Cave vines"), VINES("Vines"), AMETHYST("Amethyst"),
        BAMBOO("Bamboo"), COCOA("Cocoa"), BEES("Bee nests / hives");
        public final String label;Kind(String label){this.label=label;}
    }
    public static Kind kind(BlockState state){
        if(state.is(Blocks.KELP)||state.is(Blocks.KELP_PLANT))return Kind.KELP;
        if(state.is(Blocks.CAVE_VINES)||state.is(Blocks.CAVE_VINES_PLANT))return Kind.CAVE_VINES;
        if(state.is(Blocks.VINE))return Kind.VINES;
        if(state.is(Blocks.AMETHYST_CLUSTER)||state.is(Blocks.SMALL_AMETHYST_BUD)||state.is(Blocks.MEDIUM_AMETHYST_BUD)||state.is(Blocks.LARGE_AMETHYST_BUD))return Kind.AMETHYST;
        if(state.is(Blocks.BAMBOO)||state.is(Blocks.BAMBOO_SAPLING))return Kind.BAMBOO;
        if(state.is(Blocks.COCOA))return Kind.COCOA;
        if(state.is(Blocks.BEE_NEST)||state.is(Blocks.BEEHIVE))return Kind.BEES;
        return null;
    }
    public static final class Summary {
        private final EnumMap<Kind,Integer> counts=new EnumMap<>(Kind.class);
        private int honeyNests,hives;
        public void add(BlockState state){
            var kind=kind(state);if(kind==null)return;counts.merge(kind,1,Integer::sum);
            if(state.is(Blocks.BEE_NEST)&&state.getValue(BeehiveBlock.HONEY_LEVEL)>0)honeyNests++;
            if(state.is(Blocks.BEEHIVE))hives++;
        }
        // Plant age states are not length measurements. Spatial growth checks
        // live in SuspectObjects and do not infer time spent by a player.
        public int score(){return Math.min(4,honeyNests*2)+Math.min(4,hives*4);}
        public boolean empty(){return counts.isEmpty();}
        public int count(Kind kind){return counts.getOrDefault(kind,0);}
        public String evidence(){
            var lines=new ArrayList<String>();
            if(honeyNests>0)lines.add("Nests with honey: "+honeyNests);
            if(hives>0)lines.add("Crafted beehives: "+hives);
            return lines.isEmpty()?"Natural growth alone is inconclusive":String.join(" | ",lines);
        }
        public String context(){
            var lines=new ArrayList<String>();counts.forEach((kind,count)->lines.add(kind.label+": "+count));
            return String.join(" / ",lines);
        }
    }
}
