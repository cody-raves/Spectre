package net.spectre.client.module.impl.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.TextureTarget;

import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import java.nio.ByteBuffer;
import java.util.OptionalInt;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.Identifier;
import org.lwjgl.BufferUtils;

@Environment(EnvType.CLIENT)
final class AuraBlur {
   private static final RenderPipeline PIPELINE = RenderPipeline.builder()
      .withLocation(Identifier.fromNamespaceAndPath("spectre", "pipeline/aura_spread"))
      .withVertexShader("core/screenquad")
      .withFragmentShader(Identifier.fromNamespaceAndPath("spectre", "core/aura_spread"))
      .withBindGroupLayout(com.mojang.blaze3d.pipeline.BindGroupLayout.builder().withSampler("InSampler").build())
      .withBindGroupLayout(com.mojang.blaze3d.pipeline.BindGroupLayout.builder().withUniform("AuraSpread", UniformType.UNIFORM_BUFFER).build())
      .withColorTargetState(com.mojang.blaze3d.pipeline.ColorTargetState.DEFAULT)
      
      .withDepthStencilState(java.util.Optional.empty())
      .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
      .build();
   private static final int CONFIG_SIZE = 16;
   private static final float TAPS = 6.0F;
   private final String name;
   private final Supplier<String> passLabel;
   private TextureTarget across;
   private TextureTarget down;
   private GpuBuffer acrossConfig;
   private GpuBuffer downConfig;
   private GpuBufferSlice acrossSlice;
   private GpuBufferSlice downSlice;
   private ByteBuffer configData;

   AuraBlur(String name) {
      this.name = name;
      this.passLabel = () -> name + " spread";
   }

   static boolean compiled(GpuDevice device) {
      return device.precompilePipeline(PIPELINE).isValid();
   }

   GpuTextureView spread(CommandEncoder encoder, GpuTextureView source, int width, int height, float reachPx) {
      int halfWidth = Math.max(1, (width + 1) / 2);
      int halfHeight = Math.max(1, (height + 1) / 2);
      if (this.across == null) {
         this.allocate(halfWidth, halfHeight);
      } else if (this.across.width != halfWidth || this.across.height != halfHeight) {
         this.across.resize(halfWidth, halfHeight);
         this.down.resize(halfWidth, halfHeight);
      }

      float step = Math.max(0.75F, reachPx / 6.0F);
      this.run(encoder, source, this.across, this.acrossSlice, step / width, 0.0F);
      this.run(encoder, this.across.getColorTextureView(), this.down, this.downSlice, 0.0F, step / height);
      return this.down.getColorTextureView();
   }

   private void run(CommandEncoder encoder, GpuTextureView from, TextureTarget into, GpuBufferSlice slice, float du, float dv) {
      this.configData.putFloat(0, du);
      this.configData.putFloat(4, dv);
      encoder.writeToBuffer(slice, this.configData);

      try (RenderPass pass = encoder.createRenderPass(this.passLabel, into.getColorTextureView(), java.util.Optional.empty())) {
         pass.setPipeline(PIPELINE);
         pass.setUniform("AuraSpread", slice);
         pass.bindTexture("InSampler", from, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
         pass.draw(0, 3, 0, 1);
      }
   }

   private void allocate(int width, int height) {
      GpuDevice device = RenderSystem.getDevice();
      this.across = new TextureTarget(this.name + " across", width, height, false, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM);
      this.down = new TextureTarget(this.name + " down", width, height, false, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM);
      this.acrossConfig = device.createBuffer(() -> this.name + " across config", 136, 16L);
      this.downConfig = device.createBuffer(() -> this.name + " down config", 136, 16L);
      this.acrossSlice = this.acrossConfig.slice();
      this.downSlice = this.downConfig.slice();
      this.configData = BufferUtils.createByteBuffer(16);
   }

   void release() {
      if (this.across != null) {
         this.across.destroyBuffers();
         this.across = null;
      }

      if (this.down != null) {
         this.down.destroyBuffers();
         this.down = null;
      }

      if (this.acrossConfig != null) {
         this.acrossConfig.close();
         this.acrossConfig = null;
      }

      if (this.downConfig != null) {
         this.downConfig.close();
         this.downConfig = null;
      }

      this.acrossSlice = null;
      this.downSlice = null;
      this.configData = null;
   }
}
