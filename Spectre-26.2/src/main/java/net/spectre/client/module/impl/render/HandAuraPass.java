package net.spectre.client.module.impl.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
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
import net.spectre.client.util.ShaderOverlay;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.BufferUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
final class HandAuraPass {
   private static final Logger LOGGER = LoggerFactory.getLogger("Spectre");
   private static final RenderPipeline PIPELINE = RenderPipeline.builder()
      .withLocation(Identifier.fromNamespaceAndPath("spectre", "pipeline/hand_aura"))
      .withVertexShader("core/screenquad")
      .withFragmentShader(Identifier.fromNamespaceAndPath("spectre", "core/hand_aura"))
      .withBindGroupLayout(com.mojang.blaze3d.pipeline.BindGroupLayout.builder().withSampler("CaptureSampler").build())
      .withBindGroupLayout(com.mojang.blaze3d.pipeline.BindGroupLayout.builder().withSampler("HaloSampler").build())
      .withBindGroupLayout(com.mojang.blaze3d.pipeline.BindGroupLayout.builder().withUniform("HandAuraConfig", UniformType.UNIFORM_BUFFER).build())
      .withColorTargetState(new com.mojang.blaze3d.pipeline.ColorTargetState(java.util.Optional.of(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA), com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, 7))
      
      .withDepthStencilState(java.util.Optional.empty())
      .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
      .build();
   private static final Supplier<String> PASS_LABEL = () -> "Spectre hand aura";
   private static final Supplier<String> CONFIG_LABEL = () -> "Spectre hand aura config";
   private static final int CONFIG_SIZE = 48;
   private static TextureTarget hand;
   private static AuraBlur blur;
   private static GpuBuffer config;
   private static GpuBufferSlice configSlice;
   private static ByteBuffer configData;
   private static boolean capturing;
   private static boolean invalidShaderLogged;

   private HandAuraPass() {
   }

   static boolean begin() {
      if (!capturing && RenderSystem.outputColorTextureOverride == null && RenderSystem.outputDepthTextureOverride == null) {
         Minecraft mc = Minecraft.getInstance();
         RenderTarget main = mc.gameRenderer.mainRenderTarget();
         if (mc.level != null
            && !mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.isPanoramicMode
            && main.getColorTexture() != null
            && main.width > 0
            && main.height > 0
            && !ShaderOverlay.shaderPackInUse()) {
            GpuDevice device = RenderSystem.getDevice();
            if (!shaderReady(device)) {
               return false;
            } else {
               if (hand == null) {
                  allocate(main.width, main.height);
               } else if (hand.width != main.width || hand.height != main.height) {
                  hand.resize(main.width, main.height);
               }

               device.createCommandEncoder().clearColorAndDepthTextures(hand.getColorTexture(), new org.joml.Vector4f(), hand.getDepthTexture(), 0.0);
               RenderSystem.outputColorTextureOverride = hand.getColorTextureView();
               RenderSystem.outputDepthTextureOverride = hand.getDepthTextureView();
               capturing = true;
               return true;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   static boolean end() {
      if (!capturing) {
         return false;
      } else {
         capturing = false;
         RenderSystem.outputColorTextureOverride = null;
         RenderSystem.outputDepthTextureOverride = null;
         return true;
      }
   }

   static void compose(int tintArgb, float aura, float reachPx, float rim, float rimPx, int sheenArgb, float sheen, float phase) {
      RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
      CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
      GpuTextureView halo = aura > 0.0F ? blur.spread(encoder, hand.getColorTextureView(), hand.width, hand.height, reachPx) : hand.getColorTextureView();
      putColor(0, tintArgb, aura);
      putColor(16, sheenArgb, sheen);
      configData.putFloat(32, rim);
      configData.putFloat(36, rimPx);
      configData.putFloat(40, phase);
      configData.putFloat(44, 0.0F);
      encoder.writeToBuffer(configSlice, configData);

      try (RenderPass pass = encoder.createRenderPass(PASS_LABEL, main.getColorTextureView(), java.util.Optional.empty())) {
         pass.setPipeline(PIPELINE);
         pass.setUniform("HandAuraConfig", configSlice);
         pass.bindTexture("CaptureSampler", hand.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
         pass.bindTexture("HaloSampler", halo, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
         pass.draw(0, 3, 0, 1);
      }
   }

   private static void putColor(int offset, int argb, float weight) {
      configData.putFloat(offset, (argb >> 16 & 0xFF) / 255.0F);
      configData.putFloat(offset + 4, (argb >> 8 & 0xFF) / 255.0F);
      configData.putFloat(offset + 8, (argb & 0xFF) / 255.0F);
      configData.putFloat(offset + 12, weight);
   }

   private static boolean shaderReady(GpuDevice device) {
      if (device.precompilePipeline(PIPELINE).isValid() && AuraBlur.compiled(device)) {
         invalidShaderLogged = false;
         return true;
      } else {
         if (!invalidShaderLogged) {
            invalidShaderLogged = true;
            LOGGER.warn("Hand Aura shaders did not compile; the hand is drawn as usual");
         }

         return false;
      }
   }

   private static void allocate(int width, int height) {
      hand = new TextureTarget("Spectre hand aura", width, height, true, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM);
      blur = new AuraBlur("Spectre hand aura");
      config = RenderSystem.getDevice().createBuffer(CONFIG_LABEL, 136, 48L);
      configSlice = config.slice();
      configData = BufferUtils.createByteBuffer(48);
   }

   static void release() {
      if (capturing) {
         end();
      }

      if (hand != null) {
         hand.destroyBuffers();
         hand = null;
      }

      if (blur != null) {
         blur.release();
         blur = null;
      }

      if (config != null) {
         config.close();
         config = null;
      }

      configSlice = null;
      configData = null;
   }
}
