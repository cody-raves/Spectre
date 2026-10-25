package net.spectre.client.util.seedscan;

import java.util.IdentityHashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

@Environment(EnvType.CLIENT)
public final class VBlock {
   public static final byte AIR = 0;
   public static final byte WATER = 1;
   public static final byte LAVA = 2;
   public static final byte STONE = 3;
   public static final byte GRANITE = 4;
   public static final byte DIORITE = 5;
   public static final byte ANDESITE = 6;
   public static final byte TUFF = 7;
   public static final byte GRAVEL = 8;
   public static final byte DIRT = 9;
   public static final byte CLAY = 10;
   public static final byte ORE = 11;
   public static final byte OTHER = 12;
   public static final byte UNKNOWN = 15;
   public static final int FAMILY_GRANITE = 1;
   public static final int FAMILY_DIORITE = 2;
   public static final int FAMILY_ANDESITE = 4;
   public static final int FAMILY_GRAVEL = 8;
   private static volatile Map<Block, Byte> kinds;

   private VBlock() {
   }

   public static boolean isBase(byte kind) {
      return kind >= 3 && kind <= 7;
   }

   public static boolean isOpen(byte kind) {
      return kind == 0 || kind == 1 || kind == 2;
   }

   public static int familyBit(byte kind) {
      return switch (kind) {
         case 4 -> 1;
         case 5 -> 2;
         case 6 -> 4;
         default -> 0;
         case 8 -> 8;
      };
   }

   public static Block familyBlock(byte kind) {
      return switch (kind) {
         case 4 -> Blocks.GRANITE;
         case 5 -> Blocks.DIORITE;
         case 6 -> Blocks.ANDESITE;
         default -> throw new IllegalArgumentException("not a blob kind: " + kind);
         case 8 -> Blocks.GRAVEL;
      };
   }

   public static byte of(BlockState state) {
      if (state.isAir()) {
         return 0;
      } else {
         Byte kind = kinds().get(state.getBlock());
         return kind != null ? kind : 12;
      }
   }

   private static Map<Block, Byte> kinds() {
      Map<Block, Byte> map = kinds;
      if (map != null) {
         return map;
      } else {
         Map<Block, Byte> var5 = new IdentityHashMap();
         var5.put(Blocks.STONE, (byte)3);
         var5.put(Blocks.DEEPSLATE, (byte)3);
         var5.put(Blocks.GRANITE, (byte)4);
         var5.put(Blocks.DIORITE, (byte)5);
         var5.put(Blocks.ANDESITE, (byte)6);
         var5.put(Blocks.TUFF, (byte)7);
         var5.put(Blocks.GRAVEL, (byte)8);
         var5.put(Blocks.DIRT, (byte)9);
         var5.put(Blocks.CLAY, (byte)10);
         var5.put(Blocks.WATER, (byte)1);
         var5.put(Blocks.LAVA, (byte)2);

         for (Block ore : new Block[]{
            Blocks.COAL_ORE,
            Blocks.DEEPSLATE_COAL_ORE,
            Blocks.IRON_ORE,
            Blocks.DEEPSLATE_IRON_ORE,
            Blocks.COPPER_ORE,
            Blocks.DEEPSLATE_COPPER_ORE,
            Blocks.GOLD_ORE,
            Blocks.DEEPSLATE_GOLD_ORE,
            Blocks.REDSTONE_ORE,
            Blocks.DEEPSLATE_REDSTONE_ORE,
            Blocks.LAPIS_ORE,
            Blocks.DEEPSLATE_LAPIS_ORE,
            Blocks.DIAMOND_ORE,
            Blocks.DEEPSLATE_DIAMOND_ORE,
            Blocks.EMERALD_ORE,
            Blocks.DEEPSLATE_EMERALD_ORE,
            Blocks.RAW_IRON_BLOCK,
            Blocks.RAW_COPPER_BLOCK,
            Blocks.INFESTED_STONE,
            Blocks.INFESTED_DEEPSLATE
         }) {
            var5.put(ore, (byte)11);
         }

         kinds = var5;
         return var5;
      }
   }
}
