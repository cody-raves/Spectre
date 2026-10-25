package net.spectre.client.module.impl.render.character.gltf;

import java.io.IOException;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public interface ImageDecoder {
   ImageDecoder.Picture decode(byte[] var1) throws IOException;

   @Environment(EnvType.CLIENT)
   public record Picture(int width, int height, int[] argb) {
   }
}
