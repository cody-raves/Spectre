package net.spectre.client.util.seedscan;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class GateHeights {
   public static final int TREE_ALLOWANCE = Integer.getInteger("spectre.treeAllowance", 20);
   public static final int TALL_TREE_ALLOWANCE = Integer.getInteger("spectre.tallTreeAllowance", 34);
   static final int LAND_FLOOR = Integer.getInteger("spectre.landFloor", 57);
   static final int UNSEEN_BELOW = 8;
   static final int UNSEEN_ABOVE = 40;
   private final short[] low = new short[256];
   private final short[] high = new short[256];

   private GateHeights() {
   }

   public static GateHeights of(TerrainChunk terrain, ShownChunk shown, int doubt, int allowance) {
      GateHeights heights = new GateHeights();

      for (int column = 0; column < 256; column++) {
         int x = column & 15;
         int z = column >> 4;
         int model = terrain.oceanFloor(x, z);
         int low;
         int high;
         if (shown == null) {
            low = model - 8;
            high = model + 40;
         } else {
            int now = shown.motionTop(x, z);
            low = Math.min(model, now);
            high = Math.max(model, now);
            if (model >= LAND_FLOOR) {
               high = Math.max(high, model + allowance);
            }
         }

         heights.low[column] = (short)Math.max(low - doubt, -32768);
         heights.high[column] = (short)Math.min(high + doubt, 32767);
      }

      return heights;
   }

   public int low(int x, int z) {
      return this.low[(x & 15) + 16 * (z & 15)];
   }

   public int high(int x, int z) {
      return this.high[(x & 15) + 16 * (z & 15)];
   }
}
