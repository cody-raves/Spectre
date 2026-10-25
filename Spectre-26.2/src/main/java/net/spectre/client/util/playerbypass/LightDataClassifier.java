package net.spectre.client.util.playerbypass;

import java.util.BitSet;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;

@Environment(EnvType.CLIENT)
public final class LightDataClassifier {
   private static final int NIBBLES_PER_SECTION = 2048;

   private LightDataClassifier() {
   }

   public static LightSectionStatus classifyPayload(byte[] payload) {
      if (payload != null && payload.length == 2048) {
         boolean allZero = true;
         boolean allMax = true;

         for (byte b : payload) {
            if (b != 0) {
               allZero = false;
            }

            if (b != -1) {
               allMax = false;
            }

            if (!allZero && !allMax) {
               return LightSectionStatus.NORMAL;
            }
         }

         if (allZero) {
            return LightSectionStatus.ZEROED_PRESENT;
         } else {
            return allMax ? LightSectionStatus.UNIFORM_MAX : LightSectionStatus.NORMAL;
         }
      } else {
         return LightSectionStatus.UNKNOWN;
      }
   }

   public static void apply(ClientboundLightUpdatePacketData data, ChunkLightSnapshot snapshot) {
      applyTrack(data.getSkyYMask(), data.getEmptySkyYMask(), data.getSkyUpdates(), snapshot.sky);
   }

   static void applyTrack(BitSet mask, BitSet emptyMask, List<byte[]> updates, LightSectionStatus[] out) {
      int next = 0;

      for (int i = 0; i < out.length; i++) {
         if (mask.get(i)) {
            byte[] payload = next < updates.size() ? updates.get(next) : null;
            next++;
            out[i] = classifyPayload(payload);
         } else if (emptyMask.get(i)) {
            out[i] = LightSectionStatus.EMPTY_MASK;
         }
      }
   }
}
