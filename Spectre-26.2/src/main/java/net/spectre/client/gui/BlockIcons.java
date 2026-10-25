package net.spectre.client.gui;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.GpuFormat;
import imgui.ImDrawList;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import net.spectre.client.imgui.ImGuiManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.data.AtlasIds;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
final class BlockIcons {
   private static final Logger LOGGER = LoggerFactory.getLogger("Spectre");
   private static final float SPRITE_TEXELS = 16.0F;
   private static final int PAGE_SIDE = 1024;
   private static final int RENDERS_PER_FRAME = 48;
   private static final int MAX_SHEETS = 4;
   private static final int WATER_RGB = 4159204;
   private static final Int2ObjectOpenHashMap<BlockIcons.Sheet> SHEETS = new Int2ObjectOpenHashMap();
   private static final ReferenceOpenHashSet<GpuTexture> NEAREST = new ReferenceOpenHashSet();
   private static GpuTexture atlas;
   private static final ItemStackRenderState ITEM = new ItemStackRenderState();
   private static int budgetFrame = -1;
   private static int rendersLeft;
   private static boolean itemsFailed;

   private BlockIcons() {
   }

   static void validate() {
      GpuTexture current;
      try {
         current = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getTexture();
      } catch (RuntimeException var2) {
         current = null;
      }

      if (current != atlas) {
         atlas = current;
         clearSheets();
      }
   }

   static void forget() {
      clearSheets();
   }

   static float size(float target) {
      return Math.max(1, (int)(target / 16.0F)) * 16.0F;
   }

   static void draw(ImDrawList dl, Block block, float x, float y, float size) {
      int px = Math.round(size);
      BlockIcons.Sheet sheet = sheet(px);
      BlockIcons.Icon icon = (BlockIcons.Icon)sheet.icons.get(block);
      if (icon == null) {
         icon = resolve(sheet, block);
         if (icon == null) {
            return;
         }

         sheet.icons.put(block, icon);
      }

      float x0 = Math.round(x);
      float y0 = Math.round(y);
      if (icon.texture != 0) {
         dl.addImage(icon.texture, x0, y0, x0 + px, y0 + px, icon.u0, icon.v0, icon.u1, icon.v1, SpectreWidgets.c(icon.tint));
      } else {
         float r = px * 0.2F;
         dl.addRectFilled(x0, y0, x0 + px, y0 + px, SpectreWidgets.c(-12240588), r);
         dl.addRect(x0, y0, x0 + px, y0 + px, SpectreWidgets.c(536870911), r, 0, 1.0F);
      }
   }

   private static BlockIcons.Sheet sheet(int px) {
      BlockIcons.Sheet sheet = (BlockIcons.Sheet)SHEETS.get(px);
      if (sheet == null) {
         if (SHEETS.size() >= 4) {
            clearSheets();
         }

         sheet = new BlockIcons.Sheet(px);
         SHEETS.put(px, sheet);
      }

      return sheet;
   }

   private static void clearSheets() {
      ObjectIterator var0 = SHEETS.values().iterator();

      while (var0.hasNext()) {
         BlockIcons.Sheet sheet = (BlockIcons.Sheet)var0.next();
         sheet.close();
      }

      SHEETS.clear();
      NEAREST.clear();
   }

   private static BlockIcons.Icon resolve(BlockIcons.Sheet sheet, Block block) {
      Item item = block.asItem();
      Block shown = block;
      if (item == Items.AIR) {
         item = standIn(block);
         if (item instanceof BlockItem placed) {
            shown = placed.getBlock();
         }
      }

      if (item == Items.AIR || itemsFailed || sheet.cell > 256) {
         return sprite(block);
      } else if (!takeRender()) {
         return null;
      } else {
         Minecraft mc = Minecraft.getInstance();

         try {
            mc.getItemModelResolver().updateForTopItem(ITEM, new ItemStack(item), ItemDisplayContext.GUI, mc.level, null, 0);
         } catch (RuntimeException var9) {
            ITEM.clear();
            return sprite(block);
         }

         if (ITEM.isEmpty()) {
            return sprite(block);
         } else {
            BlockIcons.Icon icon = new BlockIcons.Icon();
            boolean seeThrough = net.spectre.client.util.RenderBridge.translucent(shown.defaultBlockState());

            try {
               renderItem(sheet, icon, seeThrough);
               return icon;
            } catch (RuntimeException var8) {
               itemsFailed = true;
               net.spectre.client.util.RenderBridge.clearIcons();
               LOGGER.warn("Block item icons failed, using block textures instead", var8);
               return sprite(block);
            }
         }
      }
   }

   private static Item standIn(Block block) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null) {
         try {
            ItemStack picked = block.defaultBlockState().getCloneItemStack(mc.level, BlockPos.ZERO, false);
            if (!picked.isEmpty()) {
               return picked.getItem();
            }
         } catch (RuntimeException var3) {
         }
      }

      if (block == Blocks.MOVING_PISTON) {
         return Items.PISTON;
      } else if (block == Blocks.END_PORTAL) {
         return Items.ENDER_EYE;
      } else {
         return block == Blocks.END_GATEWAY ? Items.ENDER_PEARL : Items.AIR;
      }
   }

   private static boolean takeRender() {
      int frame = ImGuiManager.getInstance().frameCount();
      if (frame != budgetFrame) {
         budgetFrame = frame;
         rendersLeft = 48;
      }

      if (rendersLeft <= 0) {
         return false;
      } else {
         rendersLeft--;
         return true;
      }
   }

   private static void renderItem(BlockIcons.Sheet sheet, BlockIcons.Icon icon, boolean seeThrough) {
      int cell = sheet.cell;
      int perRow = 1024 / cell;
      if (sheet.pages.isEmpty() || sheet.used >= perRow * perRow) {
         addPage(sheet);
      }

      int slot = sheet.used++;
      int x = slot % perRow * cell;
      int y = slot / perRow * cell;
      int glY = 1024 - y - cell;
      GpuTexture page = sheet.pages.get(sheet.pages.size() - 1);
      GpuTextureView view = sheet.views.get(sheet.views.size() - 1);

      try {
         ItemIcons.render(ITEM, page, view, sheet.depth, sheet.depthView, x, y, cell);
      } finally {
         ITEM.clear();
      }

      if (seeThrough) {
         ItemIcons.straighten(page, x, glY, cell);
      }

      icon.texture = ((GlTexture)page).glId();
      icon.u0 = x / 1024.0F;
      icon.u1 = (x + cell) / 1024.0F;
      icon.v0 = (1024 - y) / 1024.0F;
      icon.v1 = glY / 1024.0F;
   }

   private static void addPage(BlockIcons.Sheet sheet) {
      GpuDevice device = RenderSystem.getDevice();
      if (sheet.depth == null) {
         sheet.depth = device.createTexture("Spectre block icons depth", 8, GpuFormat.D32_FLOAT, 1024, 1024, 1, 1);
         sheet.depthView = device.createTextureView(sheet.depth);
      }

      GpuTexture page = device.createTexture("Spectre block icons " + sheet.cell + "px", 15, GpuFormat.RGBA8_UNORM, 1024, 1024, 1, 1);
      CommandEncoder encoder = device.createCommandEncoder();
      encoder.clearColorAndDepthTextures(page, new org.joml.Vector4f(), sheet.depth, 0.0);
      sheet.pages.add(page);
      sheet.views.add(device.createTextureView(page));
      sheet.used = 0;
      if (page instanceof GlTexture gl && NEAREST.add(page)) {
         ItemIcons.nearest(gl);
      }
   }

   private static BlockIcons.Icon sprite(Block block) {
      BlockIcons.Icon icon = new BlockIcons.Icon();
      Minecraft mc = Minecraft.getInstance();
      BlockState state = block.defaultBlockState();

      try {
         TextureAtlasSprite sprite = mc.getModelManager().getBlockStateModelSet().getParticleMaterial(state).sprite();
         if (MissingTextureAtlasSprite.getLocation().equals(sprite.contents().name())) {
            return icon;
         }

         if (!(mc.getTextureManager().getTexture(sprite.atlasLocation()).getTexture() instanceof GlTexture gl && !gl.isClosed())) {
            return icon;
         }

         if (NEAREST.add(gl)) {
            ItemIcons.nearest(gl);
         }

         icon.texture = gl.glId();
         icon.u0 = sprite.getU0();
         icon.v0 = sprite.getV0();
         icon.u1 = sprite.getU1();
         icon.v1 = sprite.getV1();
      } catch (RuntimeException var8) {
         return icon;
      }

      if (!state.is(Blocks.GRASS_BLOCK)) {
         int rgb = -1;

         try {
            rgb = net.spectre.client.util.RenderBridge.tint(state);
         } catch (RuntimeException var7) {
         }

         if (rgb == -1 && (state.is(Blocks.WATER) || state.is(Blocks.BUBBLE_COLUMN))) {
            rgb = 4159204;
         }

         if (rgb != -1) {
            icon.tint = 0xFF000000 | (rgb & 0xFF) << 16 | rgb & 0xFF00 | rgb >> 16 & 0xFF;
         }
      }

      return icon;
   }

   @Environment(EnvType.CLIENT)
   private static final class Icon {
      int texture;
      float u0;
      float v0;
      float u1;
      float v1;
      int tint = -1;
   }

   @Environment(EnvType.CLIENT)
   private static final class Sheet {
      final int cell;
      final Reference2ObjectOpenHashMap<Block, BlockIcons.Icon> icons = new Reference2ObjectOpenHashMap();
      final List<GpuTexture> pages = new ArrayList<>();
      final List<GpuTextureView> views = new ArrayList<>();
      GpuTexture depth;
      GpuTextureView depthView;
      int used;

      Sheet(int cell) {
         this.cell = cell;
      }

      void close() {
         for (GpuTextureView view : this.views) {
            view.close();
         }

         for (GpuTexture page : this.pages) {
            page.close();
         }

         if (this.depthView != null) {
            this.depthView.close();
         }

         if (this.depth != null) {
            this.depth.close();
         }

         this.views.clear();
         this.pages.clear();
         this.depth = null;
         this.depthView = null;
      }
   }
}
