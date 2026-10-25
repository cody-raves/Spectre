package net.spectre.client.module.impl.render.character;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.GpuFormat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.texture.AbstractTexture;

@Environment(EnvType.CLIENT)
final class CharacterTexture extends AbstractTexture {
   CharacterTexture(String label, int width, int height, int[][] mips) {
      GpuDevice device = RenderSystem.getDevice();
      this.texture = device.createTexture(() -> label, 5, GpuFormat.RGBA8_UNORM, width, height, 1, mips.length);
      this.textureView = device.createTextureView(this.texture);
      CommandEncoder encoder = device.createCommandEncoder();
      int w = width;
      int h = height;

      for (int level = 0; level < mips.length; level++) {
         int[] pixels = mips[level];

         try (NativeImage image = new NativeImage(w, h, false)) {
            for (int y = 0; y < h; y++) {
               for (int x = 0; x < w; x++) {
                  image.setPixel(x, y, pixels[y * w + x]);
               }
            }

            encoder.writeToTexture(this.texture, image, level, 0, 0, 0);
         }

         w = Math.max(1, w / 2);
         h = Math.max(1, h / 2);
      }

      this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR, true);
   }
}
