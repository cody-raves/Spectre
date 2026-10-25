package net.spectre.client.gui;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.buffers.GpuBufferSlice.MappedView;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.platform.Lighting.Entry;
import com.mojang.blaze3d.platform.NativeImage.Format;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import imgui.ImDrawList;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import net.spectre.client.imgui.ImGuiManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.data.AtlasIds;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4fStack;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class ItemIcons {
   private static final Logger LOGGER = LoggerFactory.getLogger("Spectre");
   private static final int SHEET_SIDE = 512;
   private static final int MIN_PX = 8;
   private static final int MAX_PX = 96;
   private static final int MAX_SHEETS = 8;
   private static final int BUSY_FRAMES = 2;
   private static final int IDLE_FRAMES = 600;
   private static final int PAINTS_PER_FRAME = 6;
   private static final ItemIcons.Cell BLANK = new ItemIcons.Cell();
   private static final Int2ObjectOpenHashMap<ItemIcons.Sheet> SHEETS = new Int2ObjectOpenHashMap();
   private static final List<ItemIcons.Sheet> RETIRED = new ArrayList<>();
   private static final Reference2ObjectOpenHashMap<Item, ItemStack> PLAIN = new Reference2ObjectOpenHashMap();
   private static final ReferenceOpenHashSet<Item> BROKEN = new ReferenceOpenHashSet();
   private static final ItemLook MODEL = new ItemLook();
   private static final int[] VIEWPORT = new int[4];
   private static GpuTexture depth;
   private static GpuTextureView depthView;
   private static ProjectionMatrixBuffer projection;
   private static GpuBuffer readback;
   private static ByteBuffer straightened;
   private static boolean straightenFailed;
   private static GpuTexture itemAtlas;
   private static int frame = Integer.MIN_VALUE;
   private static int retiredFrame;
   private static int paintsLeft;
   private static boolean disabled;

   private ItemIcons() {
   }

   static float pixels(float size) {
      float snapped = size < 16.0F ? Math.round(size) : 16.0F * (float)Math.floor(size / 16.0F + 0.25F);
      return Math.max(8.0F, Math.min(96.0F, snapped));
   }

   static void draw(ImDrawList dl, Item item, float x, float y, float size, float alpha) {
      if (item != null && item != Items.AIR) {
         ItemStack plain = (ItemStack)PLAIN.get(item);
         if (plain == null) {
            plain = item.getDefaultInstance();
            PLAIN.put(item, plain);
         }

         draw(dl, plain, x, y, size, alpha);
      }
   }

   public static void draw(ImDrawList dl, ItemStack stack, float x, float y, float size, float alpha) {
      if (stack != null && !stack.isEmpty() && !(alpha <= 0.004F)) {
         startFrame();
         int px = (int)pixels(size);
         float x0 = Math.round(x);
         float y0 = Math.round(y);

         ItemIcons.Cell cell;
         try {
            cell = cell(stack, px);
         } finally {
            MODEL.clear();
         }

         if (cell != null) {
            if (cell.texture == 0) {
               float r = Math.max(1.0F, px * 0.18F);
               dl.addRectFilled(x0 + 1.0F, y0 + 1.0F, x0 + px - 1.0F, y0 + px - 1.0F, HudChrome.scaleAlpha(452984831, alpha), r);
            } else {
               dl.addImage(cell.texture, x0, y0, x0 + px, y0 + px, cell.u0, cell.v0, cell.u1, cell.v1, HudChrome.scaleAlpha(-1, alpha));
            }
         }
      }
   }

   private static void startFrame() {
      int now = ImGuiManager.getInstance().frameCount();
      if (now != frame) {
         frame = now;
         paintsLeft = 6;
         if (!RETIRED.isEmpty() && now != retiredFrame) {
            for (ItemIcons.Sheet sheet : RETIRED) {
               sheet.release();
            }

            RETIRED.clear();
         }

         GpuTexture atlas = currentAtlas();
         if (atlas == itemAtlas) {
            Iterator<ItemIcons.Sheet> it = SHEETS.values().iterator();

            while (it.hasNext()) {
               ItemIcons.Sheet sheet = it.next();
               if (now - sheet.lastUsed > 600) {
                  sheet.release();
                  it.remove();
               }
            }
         } else {
            itemAtlas = atlas;
            ObjectIterator var5 = SHEETS.values().iterator();

            while (var5.hasNext()) {
               ItemIcons.Sheet sheet = (ItemIcons.Sheet)var5.next();
               sheet.release();
            }

            SHEETS.clear();
            BROKEN.clear();
         }
      }
   }

   private static GpuTexture currentAtlas() {
      try {
         return Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.ITEMS).getTexture();
      } catch (RuntimeException var1) {
         return null;
      }
   }

   private static ItemIcons.Cell cell(ItemStack stack, int px) {
      ItemIcons.Sheet sheet = sheet(px);
      if (sheet == null) {
         return null;
      } else {
         sheet.lastUsed = frame;
         if (!resolve(stack)) {
            return BLANK;
         } else {
            ItemIcons.Cell cell = (ItemIcons.Cell)sheet.cells.get(MODEL.identity);
            if (cell != null) {
               return cell;
            } else if (disabled) {
               return BLANK;
            } else if (paintsLeft <= 0) {
               return null;
            } else {
               paintsLeft--;
               if (sheet.full()) {
                  retire(sheet);
                  sheet = new ItemIcons.Sheet(px);
                  sheet.lastUsed = frame;
                  SHEETS.put(px, sheet);
               }

               cell = paint(sheet, seeThrough(stack.getItem()));
               sheet.cells.put(Arrays.asList(MODEL.identity.toArray()), cell);
               return cell;
            }
         }
      }
   }

   private static ItemIcons.Sheet sheet(int px) {
      ItemIcons.Sheet sheet = (ItemIcons.Sheet)SHEETS.get(px);
      if (sheet != null) {
         return sheet;
      } else {
         if (SHEETS.size() >= 8) {
            ItemIcons.Sheet oldest = stalest();
            if (oldest == null || frame - oldest.lastUsed < 2) {
               return null;
            }

            retire(oldest);
         }

         sheet = new ItemIcons.Sheet(px);
         SHEETS.put(px, sheet);
         return sheet;
      }
   }

   private static ItemIcons.Sheet stalest() {
      ItemIcons.Sheet oldest = null;
      ObjectIterator var1 = SHEETS.values().iterator();

      while (var1.hasNext()) {
         ItemIcons.Sheet sheet = (ItemIcons.Sheet)var1.next();
         if (oldest == null || sheet.lastUsed < oldest.lastUsed) {
            oldest = sheet;
         }
      }

      return oldest;
   }

   private static void retire(ItemIcons.Sheet sheet) {
      SHEETS.remove(sheet.px);
      RETIRED.add(sheet);
      retiredFrame = frame;
   }

   private static boolean resolve(ItemStack stack) {
      if (BROKEN.contains(stack.getItem())) {
         return false;
      } else if (MODEL.resolve(stack)) {
         return true;
      } else {
         BROKEN.add(stack.getItem());
         return false;
      }
   }

   private static boolean seeThrough(Item item) {
      return item instanceof BlockItem block
         ? net.spectre.client.util.RenderBridge.translucent(block.getBlock().defaultBlockState())
         : false;
   }

   private static ItemIcons.Cell paint(ItemIcons.Sheet sheet, boolean seeThrough) {
      if (MODEL.isEmpty()) {
         return BLANK;
      } else {
         try {
            if (sheet.color == null) {
               allocate(sheet);
            }

            int px = sheet.px;
            int perRow = 512 / px;
            int index = sheet.filled++;
            int left = index % perRow * px;
            int top = index / perRow * px;
            int glTop = 512 - top - px;
            render(MODEL, sheet.color, sheet.colorView, depth, depthView, left, top, px);
            if (seeThrough) {
               straighten(sheet.color, left, glTop, px);
            }

            ItemIcons.Cell cell = new ItemIcons.Cell();
            cell.texture = ((GlTexture)sheet.color).glId();
            cell.u0 = left / 512.0F;
            cell.u1 = (left + px) / 512.0F;
            cell.v0 = (512 - top) / 512.0F;
            cell.v1 = glTop / 512.0F;
            return cell;
         } catch (RuntimeException var9) {
            disabled = true;
            net.spectre.client.util.RenderBridge.clearIcons();
            LOGGER.warn("HUD item icons could not be drawn; showing plain slots instead", var9);
            return BLANK;
         }
      }
   }

   private static void allocate(ItemIcons.Sheet sheet) {
      GpuDevice device = RenderSystem.getDevice();
      if (depth == null) {
         depth = device.createTexture("Spectre HUD icon depth", GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_DST, GpuFormat.D32_FLOAT, 512, 512, 1, 1);
         depthView = device.createTextureView(depth);
      }

      sheet.color = device.createTexture("Spectre HUD icons " + sheet.px, 15, GpuFormat.RGBA8_UNORM, 512, 512, 1, 1);
      sheet.colorView = device.createTextureView(sheet.color);
      device.createCommandEncoder().clearColorAndDepthTextures(sheet.color, new org.joml.Vector4f(), depth, 0.0);
      if (sheet.color instanceof GlTexture gl) {
         nearest(gl);
      }
   }

   public static void render(
      ItemStackRenderState model, GpuTexture target, GpuTextureView targetView, GpuTexture targetDepth, GpuTextureView targetDepthView, int x, int y, int cell
   ) {
      Minecraft mc = Minecraft.getInstance();
      int side = target.getWidth(0);
      int glY = side - y - cell;
      RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(target, new org.joml.Vector4f(), targetDepth, 0.0, x, glY, cell, cell);
      GpuTextureView colorBefore = RenderSystem.outputColorTextureOverride;
      GpuTextureView depthBefore = RenderSystem.outputDepthTextureOverride;
      GpuBufferSlice lightsBefore = RenderSystem.getShaderLights();
      GpuBufferSlice projectionBefore = RenderSystem.getProjectionMatrixBuffer();
      ProjectionType projectionTypeBefore = RenderSystem.getProjectionType();
      GL11.glGetIntegerv(2978, VIEWPORT);
      GlStateManager._viewport(VIEWPORT[0], VIEWPORT[1], VIEWPORT[2], VIEWPORT[3]);
      Matrix4fStack modelView = RenderSystem.getModelViewStack();
      modelView.pushMatrix();
      modelView.identity();

      try {
         RenderSystem.outputColorTextureOverride = targetView;
         RenderSystem.outputDepthTextureOverride = targetDepthView;
         if (projection == null) {
            projection = new ProjectionMatrixBuffer("Spectre icons");
         }

         var iconProjection = new net.minecraft.client.renderer.Projection();
         iconProjection.setupOrtho(-1000, 1000, side, side, true);
         RenderSystem.setProjectionMatrix(projection.getBuffer(iconProjection), ProjectionType.ORTHOGRAPHIC);
         Entry light = model.usesBlockLight() ? Entry.ITEMS_3D : Entry.ITEMS_FLAT;
         mc.gameRenderer.lighting().setupFor(light);
         RenderSystem.enableScissorForRenderTypeDraws(x, glY, cell, cell);
         PoseStack pose = new PoseStack();
         pose.translate(x + cell * 0.5F, y + cell * 0.5F, 0.0F);
         pose.scale(cell, -cell, cell);
         model.submit(pose, net.spectre.client.util.RenderBridge.icons(), 15728880, OverlayTexture.NO_OVERLAY, 0);
         net.spectre.client.util.RenderBridge.flushIcons();
         
      } finally {
         RenderSystem.disableScissorForRenderTypeDraws();
         RenderSystem.outputColorTextureOverride = colorBefore;
         RenderSystem.outputDepthTextureOverride = depthBefore;
         RenderSystem.setShaderLights(lightsBefore);
         RenderSystem.setProjectionMatrix(projectionBefore, projectionTypeBefore);
         modelView.popMatrix();
         GlStateManager._viewport(VIEWPORT[0], VIEWPORT[1], VIEWPORT[2], VIEWPORT[3]);
      }
   }

   static void straighten(GpuTexture page, int x, int y, int cell) {
      if (!straightenFailed) {
         int bytes = cell * cell * 4;
         long whole = (long)page.getWidth(0) * page.getHeight(0) * 4L;

         try {
            GpuDevice device = RenderSystem.getDevice();
            if (readback == null || readback.size() < whole) {
               if (readback != null) {
                  readback.close();
               }

               readback = device.createBuffer(() -> "Spectre icon readback", 9, whole);
            }

            if (straightened == null || straightened.capacity() < bytes) {
               straightened = BufferUtils.createByteBuffer(bytes);
            }

            CommandEncoder encoder = device.createCommandEncoder();
            encoder.copyTextureToBuffer(page, readback, 0L, () -> {}, 0, x, y, cell, cell);

            try (GpuFence copied = encoder.createFence()) {
               GL11.glFlush();
               if (!copied.awaitCompletion(1000000000L)) {
                  throw new IllegalStateException("icon read-back timed out");
               }
            }

            straightened.clear().limit(bytes);

            try (MappedView view = readback.slice(0L, bytes).map(true, false)) {
               ByteBuffer read = view.data();

               for (int i = 0; i < bytes; i += 4) {
                  int a = read.get(i + 3) & 255;

                  for (int ch = 0; ch < 3; ch++) {
                     int v = a == 0 ? 0 : Math.min(255, ((read.get(i + ch) & 255) * 255 + a / 2) / a);
                     straightened.put(i + ch, (byte)v);
                  }

                  straightened.put(i + 3, (byte)a);
               }
            }

            encoder.writeToTexture(page, straightened, 0, 0, x, y, cell, cell);
         } catch (RuntimeException var19) {
            straightenFailed = true;
            LOGGER.warn("Couldn't read icons back; see-through ones stay darker", var19);
         }
      }
   }

   static void nearest(GlTexture gl) {
      int bound = GL11.glGetInteger(32873);
      GL11.glBindTexture(3553, gl.glId());
      GL11.glTexParameteri(3553, 10241, 9728);
      GL11.glTexParameteri(3553, 10240, 9728);
      GL11.glBindTexture(3553, bound);
   }

   @Environment(EnvType.CLIENT)
   private static final class Cell {
      int texture;
      float u0;
      float v0;
      float u1;
      float v1;
   }

   @Environment(EnvType.CLIENT)
   private static final class Sheet {
      final int px;
      final Object2ObjectOpenHashMap<Object, ItemIcons.Cell> cells = new Object2ObjectOpenHashMap();
      GpuTexture color;
      GpuTextureView colorView;
      int filled;
      int lastUsed;

      Sheet(int px) {
         this.px = px;
      }

      boolean full() {
         int perRow = 512 / this.px;
         return this.filled >= perRow * perRow;
      }

      void release() {
         if (this.colorView != null) {
            this.colorView.close();
         }

         if (this.color != null) {
            this.color.close();
         }

         this.colorView = null;
         this.color = null;
         this.cells.clear();
      }
   }
}
