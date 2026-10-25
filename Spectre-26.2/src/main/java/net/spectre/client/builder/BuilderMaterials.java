package net.spectre.client.builder;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** Resolves actual game items; unknown states fail rather than becoming substitutions. */
public final class BuilderMaterials {
    public static BlockState state(String text) {
        int open=text.indexOf('[');String id=open<0?text:text.substring(0,open);
        var block=BuiltInRegistries.BLOCK.getOptional(Identifier.parse(id)).orElseThrow(()->new IllegalArgumentException("Unknown block"));
        BlockState state=block.defaultBlockState();
        if(open>=0) {
            if(!text.endsWith("]"))throw new IllegalArgumentException("Invalid block properties");
            Set<String> seen=new HashSet<>();String props=text.substring(open+1,text.length()-1);
            for(String pair:props.isEmpty()?new String[0]:props.split(",")) {
                String[] kv=pair.split("=",-1);if(kv.length!=2||!seen.add(kv[0]))throw new IllegalArgumentException("Invalid property");
                var property=block.getStateDefinition().getProperty(kv[0]);if(property==null)throw new IllegalArgumentException("Unknown property "+kv[0]);
                state=apply(state,property,kv[1]);
            }
        }
        return state;
    }
    private static <T extends Comparable<T>> BlockState apply(BlockState state,Property<T> p,String value) {
        return state.setValue(p,p.getValue(value).orElseThrow(()->new IllegalArgumentException("Unsupported property value")));
    }
    public static MaterialPlan.Supply supply(String text) {
        BlockState state=state(text);var item=state.getBlock().asItem();int units=1;
        if(state.is(net.minecraft.world.level.block.Blocks.WATER)||state.is(net.minecraft.world.level.block.Blocks.LAVA)){
            if(state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LEVEL)!=0)throw new IllegalArgumentException("Flowing fluid must be supplied by a source");
            var bucket=state.is(net.minecraft.world.level.block.Blocks.WATER)?Items.WATER_BUCKET:Items.LAVA_BUCKET;
            return new MaterialPlan.Supply(BuiltInRegistries.ITEM.getKey(bucket).toString(),1,1,true);
        }
        String id=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        item=switch(id){case "minecraft:redstone_wire"->Items.REDSTONE;case "minecraft:wall_torch"->Items.TORCH;case "minecraft:redstone_wall_torch"->Items.REDSTONE_TORCH;case "minecraft:soul_wall_torch"->Items.SOUL_TORCH;default->item;};
        if(text.contains("half=upper")||text.contains("part=head"))units=0;
        if(text.contains("type=double")&&text.substring(0,text.indexOf('[')).endsWith("_slab"))units=2;
        for(String property:List.of("candles","pickles","layers","eggs")) {
            Property<?> p=state.getBlock().getStateDefinition().getProperty(property);
            if(p!=null&&state.getValue(p) instanceof Integer n)units=n;
        }
        if(item==Items.AIR&&units!=0)throw new IllegalArgumentException("Needs a placement recipe or manual setup");
        return new MaterialPlan.Supply(BuiltInRegistries.ITEM.getKey(item).toString(),item.getDefaultInstance().getMaxStackSize(),units,!(state.getBlock() instanceof ShulkerBoxBlock));
    }
}
