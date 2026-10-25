package net.spectre.client.module.impl.render;

import com.mojang.blaze3d.ProjectionType;
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
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.blaze3d.PrimitiveTopology;
import java.nio.ByteBuffer;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;

import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.SubmitNodeStorage;

import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.BufferUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
final class ShaderEspPass {
   private static final Logger LOGGER = LoggerFactory.getLogger("Spectre");
   private static final RenderPipeline PIPELINE = RenderPipeline.builder()
      .withLocation(Identifier.fromNamespaceAndPath("spectre", "pipeline/glow_esp"))
      .withVertexShader("core/screenquad")
      .withFragmentShader(Identifier.fromNamespaceAndPath("spectre", "core/glow_esp"))
      .withBindGroupLayout(com.mojang.blaze3d.pipeline.BindGroupLayout.builder().withSampler("SilhouetteSampler").build())
      .withBindGroupLayout(com.mojang.blaze3d.pipeline.BindGroupLayout.builder().withSampler("HaloSampler").build())
      .withBindGroupLayout(com.mojang.blaze3d.pipeline.BindGroupLayout.builder().withUniform("GlowEspConfig", UniformType.UNIFORM_BUFFER).build())
      .withColorTargetState(new com.mojang.blaze3d.pipeline.ColorTargetState(java.util.Optional.of(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA), com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, 7))
      
      .withDepthStencilState(java.util.Optional.empty())
      .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
      .build();
   private static final Supplier<String> PASS_LABEL = () -> "Spectre glow esp";
   private static final Supplier<String> CONFIG_LABEL = () -> "Spectre glow esp config";
   private static final int CONFIG_SIZE = 32;
   private static final CameraRenderState CAMERA = new CameraRenderState();
   private static final PoseStack POSE = new PoseStack();
   private static final Map<Entity, EntityRenderState> KEPT = new IdentityHashMap<>();
   private static ShaderEspPass.Rig rig;
   private static ProjectionMatrixBuffer projectionBuffer;
   private static TextureTarget mask;
   private static AuraBlur blur;
   private static GpuBuffer config;
   private static GpuBufferSlice configSlice;
   private static ByteBuffer configData;
   private static long keptFrame = Long.MIN_VALUE;
   private static boolean invalidShaderLogged;

   private ShaderEspPass() {
   }

   static void keep(Entity entity, EntityRenderState state, long frame) {
      if (keptFrame != frame) {
         KEPT.clear();
         keptFrame = frame;
      }

      KEPT.put(entity, state);
   }

   static void dropKept() {
      KEPT.clear();
      keptFrame = Long.MIN_VALUE;
   }

   static boolean paint(Camera camera, Matrix4f viewRotation, Matrix4f projection, Entity[] entities, int[] tints, int count, long frame) {
      Minecraft mc = Minecraft.getInstance();
      RenderTarget main = mc.gameRenderer.mainRenderTarget();
      if (count > 0
         && mc.level != null
         && mc.isGameLoadFinished()
         && !mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.isPanoramicMode
         && main.getColorTexture() != null
         && main.width > 0
         && main.height > 0) {
         GpuDevice device = RenderSystem.getDevice();
         if (!shaderReady(device)) {
            return false;
         } else {
            if (mask == null) {
               allocate(main.width, main.height);
            } else if (mask.width != main.width || mask.height != main.height) {
               mask.resize(main.width, main.height);
            }

            if (rig == null) {
               rig = new ShaderEspPass.Rig(mc);
            }

            if (projectionBuffer == null) {
               projectionBuffer = new ProjectionMatrixBuffer("spectre glow esp");
            }

            device.createCommandEncoder().clearColorTexture(mask.getColorTexture(), new org.joml.Vector4f());
            submit(mc, camera, entities, tints, count, frame);
            flush(viewRotation, projection);
            return true;
         }
      } else {
         return false;
      }
   }

   private static void submit(Minecraft mc, Camera camera, Entity[] entities, int[] tints, int count, long frame) {
      EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
      DeltaTracker delta = mc.getDeltaTracker();
      TickRateManager ticks = mc.level.tickRateManager();
      Vec3 eye = camera.position();
      Entity viewer = camera.entity();
      boolean reuse = keptFrame == frame;
      CAMERA.initialized = camera.isInitialized();
      CAMERA.pos = eye;
      CAMERA.blockPos = camera.blockPosition();
      CAMERA.orientation.set(camera.rotation());

      for (int i = 0; i < count; i++) {
         Entity entity = entities[i];
         if (entity != null && !entity.isRemoved()) {
            EntityRenderState state = reuse ? KEPT.get(entity) : null;
            if (state == null) {
               state = dispatcher.extractEntity(entity, delta.getGameTimeDeltaPartialTick(!ticks.isEntityFrozen(entity)));
            }

            silhouetteOnly(state);
            state.outlineColor = tints[i] | 0xFF000000;
            dispatcher.submit(state, CAMERA, state.x - eye.x, state.y - eye.y, state.z - eye.z, POSE, rig.storage);
         }
      }
   }

   private static void silhouetteOnly(EntityRenderState state) {
      state.nameTag = null;
      state.leashStates = null;
      state.displayFireAnimation = false;
      state.shadowPieces.clear();
      if (state instanceof AvatarRenderState avatar) {
         avatar.scoreText = null;
      }
   }

   private static void flush(Matrix4f viewRotation, Matrix4f projection) {
      GpuTextureView previousColor = RenderSystem.outputColorTextureOverride;
      GpuTextureView previousDepth = RenderSystem.outputDepthTextureOverride;
      Matrix4fStack modelView = RenderSystem.getModelViewStack();
      RenderSystem.backupProjectionMatrix();
      RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(projection), ProjectionType.PERSPECTIVE);
      modelView.pushMatrix();
      modelView.mul(viewRotation);
      RenderSystem.outputColorTextureOverride = mask.getColorTextureView();
      RenderSystem.outputDepthTextureOverride = null;

      try {
         try (var frame = rig.features.prepareFrame(rig.storage)) { frame.executeOutline(); }
      } finally {
         RenderSystem.outputColorTextureOverride = previousColor;
         RenderSystem.outputDepthTextureOverride = previousDepth;
         modelView.popMatrix();
         RenderSystem.restoreProjectionMatrix();
         rig.storage = new SubmitNodeStorage();
      }


   }

   static void compose(float strokePx, float aura, float reachPx, float fill, float phase, int sheenArgb, float sheen) {
      RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
      CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
      GpuTextureView halo = blur.spread(encoder, mask.getColorTextureView(), mask.width, mask.height, Math.max(reachPx, strokePx + 2.0F));
      configData.putFloat(0, strokePx);
      configData.putFloat(4, aura);
      configData.putFloat(8, fill);
      configData.putFloat(12, phase);
      configData.putFloat(16, (sheenArgb >> 16 & 0xFF) / 255.0F);
      configData.putFloat(20, (sheenArgb >> 8 & 0xFF) / 255.0F);
      configData.putFloat(24, (sheenArgb & 0xFF) / 255.0F);
      configData.putFloat(28, sheen);
      encoder.writeToBuffer(configSlice, configData);

      try (RenderPass pass = encoder.createRenderPass(PASS_LABEL, main.getColorTextureView(), java.util.Optional.empty())) {
         pass.setPipeline(PIPELINE);
         pass.setUniform("GlowEspConfig", configSlice);
         pass.bindTexture("SilhouetteSampler", mask.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
         pass.bindTexture("HaloSampler", halo, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
         pass.draw(0, 3, 0, 1);
      }
   }

   private static boolean shaderReady(GpuDevice device) {
      if (device.precompilePipeline(PIPELINE).isValid() && AuraBlur.compiled(device)) {
         invalidShaderLogged = false;
         return true;
      } else {
         if (!invalidShaderLogged) {
            invalidShaderLogged = true;
            LOGGER.warn("Glow ESP shaders did not compile; entities are left without outlines");
         }

         return false;
      }
   }

   private static void allocate(int width, int height) {
      mask = new TextureTarget("Spectre glow esp mask", width, height, false, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM);
      blur = new AuraBlur("Spectre glow esp aura");
      config = RenderSystem.getDevice().createBuffer(CONFIG_LABEL, 136, 32L);
      configSlice = config.slice();
      configData = BufferUtils.createByteBuffer(32);
   }

   static void release() {
      dropKept();
      if (rig != null) {
         rig.close();
         rig = null;
      }

      if (mask != null) {
         mask.destroyBuffers();
         mask = null;
      }

      if (blur != null) {
         blur.release();
         blur = null;
      }

      if (config != null) {
         config.close();
         config = null;
      }

      if (projectionBuffer != null) {
         projectionBuffer.close();
         projectionBuffer = null;
      }

      configSlice = null;
      configData = null;
   }

   private static final class Rig {
      SubmitNodeStorage storage = new SubmitNodeStorage();
      final FeatureRenderDispatcher features;
      Rig(Minecraft mc) {
         features = new FeatureRenderDispatcher(mc.gameRenderer.renderBuffers(), mc.getModelManager(), mc.getAtlasManager(), mc.font, mc.gameRenderer.gameRenderState());
      }
      void close() { features.close(); }
   }
}
