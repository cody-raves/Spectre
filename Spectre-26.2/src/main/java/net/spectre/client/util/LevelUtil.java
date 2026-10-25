package net.spectre.client.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;

@Environment(EnvType.CLIENT)
public final class LevelUtil {
   private LevelUtil() {
   }

   public static boolean isOverworld(ClientLevel level) {
      return level.dimension() == Level.OVERWORLD || level.dimensionTypeRegistration().is(BuiltinDimensionTypes.OVERWORLD);
   }

   public static boolean isLoaded(ClientLevel level, int chunkX, int chunkZ) {
      return level.getChunkSource().hasChunk(chunkX, chunkZ);
   }
}
