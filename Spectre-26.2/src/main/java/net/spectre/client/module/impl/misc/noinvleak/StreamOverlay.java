package net.spectre.client.module.impl.misc.noinvleak;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.buffers.GpuBufferSlice.MappedView;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.GpuFormat;
import com.sun.jna.Platform;
import imgui.ImDrawList;
import imgui.ImGui;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import net.spectre.client.gui.ItemIcons;
import net.spectre.client.gui.ItemLook;
import net.spectre.client.module.impl.misc.NoInvLeakModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.data.AtlasIds;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class StreamOverlay {
   private static final Logger LOGGER = LoggerFactory.getLogger("Spectre");
   private static final int SLOTS = 41;
   private static final int CURSOR = 41;
   private static final int ENTRIES = 42;
   private static final long SLOT_BITS = 2199023255551L;
   private static final int CELLS_PER_SIDE = 7;
   private static final int CACHE_LIMIT = 4194304;
   private static final long READBACK_TIMEOUT = 1000000000L;
   private static final long BACKGROUND_INTERVAL = 33000000L;
   private static final int FALLBACK_BACKGROUND = -7631989;
   private static final int[] BLANK = new int[0];
   private static final Runnable NOTHING = () -> {};
   private static final ItemStack[] stack = new ItemStack[42];
   private static final int[] guiX = new int[42];
   private static final int[] guiY = new int[42];
   private static long mask;
   private static int hovered = -1;
   private static boolean selectionSeen;
   private static int selectionX;
   private static int selectionY;
   private static long fakeMask;
   private static int fakeScale;
   private static ImDrawList fakeList;
   private static final DigitFont.Sink FAKE_SINK = (x, y, w, h, argb) -> fakeList.addRectFilled(
      x, y, x + w, y + h, argb & -16711936 | argb << 16 & 0xFF0000 | argb >>> 16 & 0xFF
   );
   private static final ItemStack[] shownStack = new ItemStack[42];
   private static final int[] shownX = new int[42];
   private static final int[] shownY = new int[42];
   private static final int[] shownCount = new int[42];
   private static final int[] shownBar = new int[42];
   private static long shownMask;
   private static int shownScale;
   private static boolean shownOpaque;
   private static boolean shownSelection;
   private static int shownSelectionX;
   private static int shownSelectionY;
   private static int shownHover = -1;
   private static int shownWindowX;
   private static int shownWindowY;
   private static GpuTexture shownAtlas;
   private static int boxX;
   private static int boxY;
   private static boolean backgroundValid;
   private static ItemStack cursorStack;
   private static int cursorCount;
   private static int cursorBar;
   private static int cursorScale;
   private static int cursorX;
   private static int cursorY;
   private static final ItemLook look = new ItemLook();
   private static final Object2ObjectOpenHashMap<List<Object>, int[]> icons = new Object2ObjectOpenHashMap();
   private static final int[][] slotIcon = new int[42][];
   private static final int[] pendingSlot = new int[49];
   private static final List<Object>[] pendingKey = newKeys(49);
   private static int cachedBytes;
   private static final int[][] background = new int[41][];
   private static final int[][] backgroundNext = new int[41][];
   private static final int[] syncRegion = new int[4];
   private static long backgroundAt;
   private static GpuFence asyncFence;
   private static GpuBuffer asyncBuffer;
   private static long asyncMask;
   private static int asyncScale;
   private static final int[] asyncX = new int[41];
   private static final int[] asyncY = new int[41];
   private static final int[] asyncRegion = new int[4];
   private static final Canvas canvas = new Canvas();
   private static OverlayWindow slotWindow;
   private static OverlayWindow cursorWindow;
   private static GpuTexture scratch;
   private static GpuTextureView scratchView;
   private static GpuTexture scratchDepth;
   private static GpuTextureView scratchDepthView;
   private static GpuBuffer readback;
   private static int iconPx;
   private static boolean failed;

   private StreamOverlay() {
   }

   public static boolean usable() {
      return Platform.isWindows() && !failed;
   }

   public static void retry() {
      failed = false;
   }

   public static void hotbar(int slot, int x, int y, ItemStack item) {
      if (hotbarVisible()) {
         record(slot, x, y, item);
      }
   }

   public static void container(int slot, int x, int y, ItemStack item, boolean isHovered) {
      record(slot, x, y, item);
      if (isHovered) {
         hovered = slot;
      }
   }

   public static void carried(ItemStack item, int x, int y) {
      record(41, x, y, item);
   }

   public static void selection(int x, int y) {
      if (hotbarVisible()) {
         selectionSeen = true;
         selectionX = x;
         selectionY = y;
      }
   }

   public static void tooltip(ItemStack item, int mouseX, int mouseY) {
      TextPanels.tooltip(item, mouseX, mouseY);
   }

   public static void itemName(ItemStack item, int alpha, int y) {
      TextPanels.itemName(item, alpha, y);
   }

   private static boolean hotbarVisible() {
      Screen screen = Minecraft.getInstance().gui.screen();
      return screen == null || screen instanceof ChatScreen;
   }

   private static void record(int entry, int x, int y, ItemStack item) {
      if (entry >= 0 && entry < 42 && (entry != 41 || !item.isEmpty())) {
         stack[entry] = item;
         guiX[entry] = x;
         guiY[entry] = y;
         mask |= 1L << entry;
      }
   }

   public static boolean hasFakes() {
      return fakeMask != 0L;
   }

   public static void drawFakes() {
      long bits = fakeMask;
      int scale = fakeScale;
      if (bits != 0L && scale > 0) {
         fakeList = ImGui.getBackgroundDrawList();

         for (int slot = 0; slot < 41; slot++) {
            if ((bits >>> slot & 1L) != 0L) {
               ItemStack fake = NoInvLeakModule.fakeFor(slot);
               if (!fake.isEmpty()) {
                  int x = guiX[slot] * scale;
                  int y = guiY[slot] * scale;
                  ItemIcons.draw(fakeList, fake, x, y, 16.0F * scale, 1.0F);
                  DigitFont.decorations(FAKE_SINK, fake, x, y, scale);
               }
            }
         }
      }
   }

   public static void endFrame() {
      long frame = mask;
      mask = 0L;
      int hover = hovered;
      hovered = -1;
      boolean selection = selectionSeen && NoInvLeakModule.hidesSelectedSlot();
      selectionSeen = false;
      if (NoInvLeakModule.overlayWanted() && usable()) {
         try {
            Minecraft mc = Minecraft.getInstance();
            Window win = mc.getWindow();
            if (win.isMinimized()) {
               fakeMask = 0L;
               hideAll();
            } else {
               int scale = win.getGuiScale();
               int windowX = win.getX();
               int windowY = win.getY();
               boolean opaque = NoInvLeakModule.opaqueCells();
               frame = withoutEmpty(frame, opaque && NoInvLeakModule.fillsEmpty());
               GpuTexture atlas = itemAtlas(mc);
               if (atlas != shownAtlas) {
                  OverlayArt.reset();
                  resetIcons(16 * scale);
                  shownAtlas = atlas;
                  shownMask = 0L;
               }

               fakeMask = frame & 2199023255551L;
               fakeScale = scale;
               layers(mc, win, frame, scale, windowX, windowY, opaque, selection, hover);
               TextPanels.update(win, scale, windowX, windowY);
            }
         } catch (LinkageError | RuntimeException var11) {
            failed = true;
            fakeMask = 0L;
            LOGGER.warn("[Spectre] No Inv Leak: stream-only overlay unavailable ({}); items stay hidden", var11.toString());
            net.spectre.client.util.RenderBridge.clearIcons();
            release();
         }

         TextPanels.frameDone();
      } else {
         fakeMask = 0L;
         if (started()) {
            release();
         }

         TextPanels.frameDone();
      }
   }

   public static void stop() {
      if (RenderSystem.isOnRenderThread()) {
         release();
      } else {
         Minecraft.getInstance().execute(StreamOverlay::release);
      }
   }

   private static boolean started() {
      return slotWindow != null || cursorWindow != null || scratch != null || TextPanels.started();
   }

   private static void layers(Minecraft mc, Window win, long frame, int scale, int windowX, int windowY, boolean opaque, boolean selection, int hover) {
      long slotBits = frame & 2199023255551L;
      boolean slotsShown = slotBits != 0L || selection;
      if (!slotsShown && (frame >>> 41 & 1L) == 0L) {
         hideSlotsAndCursor();
      } else {
         int px = 16 * scale;
         if (px != iconPx || cachedBytes > 4194304) {
            resetIcons(px);
         }

         if (scratch == null) {
            allocate(px);
         }

         if (slotWindow == null && slotsShown) {
            slotWindow = new OverlayWindow(win.handle());
         }

         if (cursorWindow == null && (frame >>> 41 & 1L) != 0L) {
            cursorWindow = new OverlayWindow(win.handle());
         }

         boolean layout = slotBits != shownMask
            || scale != shownScale
            || opaque != shownOpaque
            || selection != shownSelection
            || selection && (selectionX != shownSelectionX || selectionY != shownSelectionY);
         boolean content = false;

         for (int i = 0; i < 41; i++) {
            if ((slotBits >>> i & 1L) != 0L) {
               if (guiX[i] != shownX[i] || guiY[i] != shownY[i]) {
                  layout = true;
               } else if (stack[i] != shownStack[i] || stack[i].getCount() != shownCount[i] || bar(stack[i]) != shownBar[i]) {
                  content = true;
               }
            }
         }

         if (layout) {
            backgroundValid = false;
            dropAsync();
         }

         int pending = resolveIcons(frame, px);
         boolean wantBackground = opaque && slotBits != 0L && !backgroundValid;
         boolean backgroundChanged = false;
         if (pending > 0 || wantBackground) {
            synchronousRead(slotBits, scale, wantBackground, pending);
            if (wantBackground) {
               backgroundChanged = true;
               shownHover = hover;
            }
         } else if (opaque && slotBits != 0L) {
            backgroundChanged = takeAsync(slotBits, scale);
         }

         if (!slotsShown) {
            if (slotWindow != null) {
               slotWindow.hide();
            }

            shownMask = 0L;
         } else if (layout || content || backgroundChanged || pending > 0) {
            composeSlots(slotBits, scale, opaque, selection, windowX, windowY);
         } else if (windowX != shownWindowX || windowY != shownWindowY) {
            slotWindow.move(windowX + boxX, windowY + boxY);
            shownWindowX = windowX;
            shownWindowY = windowY;
         }

         if (opaque
            && slotBits != 0L
            && asyncFence == null
            && backgroundValid
            && (hover != shownHover || hotbarVisible() && System.nanoTime() - backgroundAt >= 33000000L)) {
            requestAsync(slotBits, scale);
            shownHover = hover;
         }

         cursor(frame, scale, windowX, windowY);
      }
   }

   private static void hideSlotsAndCursor() {
      if (slotWindow != null) {
         slotWindow.hide();
      }

      if (cursorWindow != null) {
         cursorWindow.hide();
      }

      shownMask = 0L;
      cursorStack = null;
   }

   private static long withoutEmpty(long frame, boolean keepEmpty) {
      if (keepEmpty) {
         return frame;
      } else {
         long bits = frame;

         for (int i = 0; i < 41; i++) {
            if ((bits >>> i & 1L) != 0L && stack[i].isEmpty()) {
               bits &= ~(1L << i);
            }
         }

         return bits;
      }
   }

   private static void hideAll() {
      hideSlotsAndCursor();
      TextPanels.hide();
   }

   private static int bar(ItemStack item) {
      return item.isBarVisible() ? item.getBarColor() << 5 | item.getBarWidth() : -1;
   }

   private static int cellX(int cell, int px) {
      return cell % 7 * px;
   }

   private static int cellY(int cell, int px) {
      return cell / 7 * px;
   }

   private static int resolveIcons(long bits, int px) {
      int pending = 0;

      for (int i = 0; i < 42; i++) {
         if ((bits >>> i & 1L) != 0L) {
            ItemStack item = stack[i];
            if (item != shownStack[i]) {
               slotIcon[i] = null;
            }

            if (slotIcon[i] == null) {
               if (!item.isEmpty() && look.resolve(item)) {
                  int[] icon = (int[])icons.get(look.identity);
                  if (icon != null) {
                     slotIcon[i] = icon;
                  } else {
                     int cell = pending++;
                     ItemIcons.render(look, scratch, scratchView, scratchDepth, scratchDepthView, cellX(cell, px), cellY(cell, px), px);
                     pendingSlot[cell] = i;
                     pendingKey[cell] = Arrays.asList(look.identity.toArray());
                  }

                  look.clear();
               } else {
                  slotIcon[i] = BLANK;
               }
            }
         }
      }

      return pending;
   }

   private static void resetIcons(int px) {
      icons.clear();
      cachedBytes = 0;
      Arrays.fill(slotIcon, null);
      dropAsync();
      backgroundValid = false;
      if (px != iconPx || scratch == null) {
         closeTextures();
         Arrays.fill(background, null);
         Arrays.fill(backgroundNext, null);
      }
   }

   private static void allocate(int px) {
      int side = 7 * px;
      GpuDevice device = RenderSystem.getDevice();
      scratch = device.createTexture("Spectre overlay icons", 15, GpuFormat.RGBA8_UNORM, side, side, 1, 1);
      scratchView = device.createTextureView(scratch);
      scratchDepth = device.createTexture("Spectre overlay icon depth", 8, GpuFormat.D32_FLOAT, side, side, 1, 1);
      scratchDepthView = device.createTextureView(scratchDepth);
      device.createCommandEncoder().clearColorAndDepthTextures(scratch, new org.joml.Vector4f(), scratchDepth, 0.0);
      iconPx = px;
   }

   private static GpuTexture itemAtlas(Minecraft mc) {
      try {
         return mc.getAtlasManager().getAtlasOrThrow(AtlasIds.ITEMS).getTexture();
      } catch (RuntimeException var2) {
         return null;
      }
   }

   private static void synchronousRead(long slotBits, int scale, boolean wantBackground, int pending) {
      int px = 16 * scale;
      int side = 7 * px;
      long scratchBytes = pending > 0 ? (long)side * side * 4L : 0L;
      boolean framed = wantBackground && region(slotBits, scale, syncRegion);
      long frameBytes = framed ? (long)syncRegion[2] * syncRegion[3] * 4L : 0L;
      long needed = framed ? scratchBytes + frameTextureBytes() : scratchBytes;
      long used = scratchBytes + frameBytes;
      if (wantBackground) {
         backgroundValid = true;
      }

      if (needed == 0L) {
         if (wantBackground) {
            unpackBackground(null, 0, syncRegion, false, slotBits, scale);
         }
      } else {
         GpuDevice device = RenderSystem.getDevice();
         if (readback == null || readback.size() < needed) {
            if (readback != null) {
               readback.close();
            }

            readback = device.createBuffer(() -> "Spectre overlay readback", 9, needed);
         }

         CommandEncoder encoder = device.createCommandEncoder();
         if (pending > 0) {
            encoder.copyTextureToBuffer(scratch, readback, 0L, NOTHING, 0);
         }

         if (framed) {
            copyRegion(encoder, readback, scratchBytes, syncRegion);
         }

         try (GpuFence copied = encoder.createFence()) {
            GL11.glFlush();
            if (!copied.awaitCompletion(1000000000L)) {
               throw new IllegalStateException("overlay read-back timed out");
            }
         }

         try (MappedView view = readback.slice(0L, used).map(true, false)) {
            ByteBuffer data = view.data();

            for (int c = 0; c < pending; c++) {
               int[] icon = unpackIcon(data, cellX(c, px), cellY(c, px), side, px);
               icons.put(pendingKey[c], icon);
               pendingKey[c] = null;
               cachedBytes += px * px * 4;
               slotIcon[pendingSlot[c]] = icon;
            }

            if (wantBackground) {
               unpackBackground(data, (int)scratchBytes, syncRegion, framed, slotBits, scale);
               backgroundAt = System.nanoTime();
            }
         }
      }
   }

   private static void requestAsync(long slotBits, int scale) {
      backgroundAt = System.nanoTime();
      if (region(slotBits, scale, asyncRegion)) {
         long frameBytes = frameTextureBytes();
         GpuDevice device = RenderSystem.getDevice();
         if (asyncBuffer == null || asyncBuffer.size() < frameBytes) {
            if (asyncBuffer != null) {
               asyncBuffer.close();
            }

            asyncBuffer = device.createBuffer(() -> "Spectre overlay background", 9, frameBytes);
         }

         CommandEncoder encoder = device.createCommandEncoder();
         copyRegion(encoder, asyncBuffer, 0L, asyncRegion);
         asyncFence = encoder.createFence();
         GL11.glFlush();
         asyncMask = slotBits;
         asyncScale = scale;

         for (int i = 0; i < 41; i++) {
            asyncX[i] = guiX[i];
            asyncY[i] = guiY[i];
         }
      }
   }

   private static boolean takeAsync(long slotBits, int scale) {
      if (asyncFence != null && asyncFence.awaitCompletion(0L)) {
         boolean usable = asyncMask == slotBits && asyncScale == scale;

         for (int i = 0; usable && i < 41; i++) {
            if ((slotBits >>> i & 1L) != 0L && (asyncX[i] != guiX[i] || asyncY[i] != guiY[i])) {
               usable = false;
            }
         }

         boolean changed = false;
         if (usable) {
            long bytes = (long)asyncRegion[2] * asyncRegion[3] * 4L;
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

            try (MappedView view = asyncBuffer.slice(0L, bytes).map(true, false)) {
               changed = unpackBackground(view.data(), 0, asyncRegion, true, slotBits, scale);
            }
         }

         dropAsync();
         return changed;
      } else {
         return false;
      }
   }

   private static void dropAsync() {
      if (asyncFence != null) {
         asyncFence.close();
         asyncFence = null;
      }
   }

   private static long frameTextureBytes() {
      GpuTexture frame = Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTexture();
      return (long)frame.getWidth(0) * frame.getHeight(0) * 4L;
   }

   private static boolean region(long slotBits, int scale, int[] out) {
      Window win = Minecraft.getInstance().getWindow();
      int px = 16 * scale;
      int x0 = Integer.MAX_VALUE;
      int y0 = Integer.MAX_VALUE;
      int x1 = Integer.MIN_VALUE;
      int y1 = Integer.MIN_VALUE;

      for (int i = 0; i < 41; i++) {
         if ((slotBits >>> i & 1L) != 0L) {
            x0 = Math.min(x0, guiX[i] * scale);
            y0 = Math.min(y0, guiY[i] * scale);
            x1 = Math.max(x1, guiX[i] * scale + px);
            y1 = Math.max(y1, guiY[i] * scale + px);
         }
      }

      x0 = Math.max(x0, 0);
      y0 = Math.max(y0, 0);
      x1 = Math.min(x1, win.getWidth());
      y1 = Math.min(y1, win.getHeight());
      if (x1 > x0 && y1 > y0) {
         out[0] = x0;
         out[1] = y0;
         out[2] = x1 - x0;
         out[3] = y1 - y0;
         return true;
      } else {
         return false;
      }
   }

   private static void copyRegion(CommandEncoder encoder, GpuBuffer target, long offset, int[] region) {
      Minecraft mc = Minecraft.getInstance();
      encoder.copyTextureToBuffer(
         mc.gameRenderer.mainRenderTarget().getColorTexture(),
         target,
         offset,
         NOTHING,
         0,
         region[0],
         mc.getWindow().getHeight() - region[1] - region[3],
         region[2],
         region[3]
      );
   }

   private static int[] unpackIcon(ByteBuffer data, int cellX, int cellY, int side, int px) {
      int[] out = new int[px * px];

      for (int y = 0; y < px; y++) {
         int row = ((side - 1 - (cellY + y)) * side + cellX) * 4;

         for (int x = 0; x < px; x++) {
            int at = row + x * 4;
            out[y * px + x] = (data.get(at + 3) & 255) << 24 | (data.get(at) & 255) << 16 | (data.get(at + 1) & 255) << 8 | data.get(at + 2) & 255;
         }
      }

      return out;
   }

   private static boolean unpackBackground(ByteBuffer data, int offset, int[] region, boolean framed, long slotBits, int scale) {
      int px = 16 * scale;
      int rx = region[0];
      int ry = region[1];
      int rw = region[2];
      int rh = region[3];
      boolean changed = false;

      for (int slot = 0; slot < 41; slot++) {
         if ((slotBits >>> slot & 1L) != 0L) {
            int cx = guiX[slot] * scale - rx;
            int cy = guiY[slot] * scale - ry;
            if (framed && cx >= 0 && cy >= 0 && cx + px <= rw && cy + px <= rh) {
               int[] next = backgroundNext[slot];
               if (next == null || next.length != px * px) {
                  next = new int[px * px];
               }

               for (int y = 0; y < px; y++) {
                  int row = offset + ((rh - 1 - (cy + y)) * rw + cx) * 4;

                  for (int x = 0; x < px; x++) {
                     int at = row + x * 4;
                     next[y * px + x] = 0xFF000000 | (data.get(at) & 255) << 16 | (data.get(at + 1) & 255) << 8 | data.get(at + 2) & 255;
                  }
               }

               int[] current = background[slot];
               if (current != null && Arrays.equals(current, next)) {
                  backgroundNext[slot] = next;
               } else {
                  changed = true;
                  background[slot] = next;
                  backgroundNext[slot] = current;
               }
            } else {
               if (background[slot] != null) {
                  changed = true;
               }

               background[slot] = null;
            }
         }
      }

      return changed;
   }

   private static void composeSlots(long slotBits, int scale, boolean opaque, boolean selection, int windowX, int windowY) {
      int px = 16 * scale;
      OverlayArt.Image frame = selection ? OverlayArt.selection() : null;
      boolean drawSelection = frame != null;
      int minX = Integer.MAX_VALUE;
      int minY = Integer.MAX_VALUE;
      int maxX = Integer.MIN_VALUE;
      int maxY = Integer.MIN_VALUE;

      for (int i = 0; i < 41; i++) {
         if ((slotBits >>> i & 1L) != 0L) {
            minX = Math.min(minX, guiX[i]);
            minY = Math.min(minY, guiY[i]);
            maxX = Math.max(maxX, guiX[i] + 17);
            maxY = Math.max(maxY, guiY[i] + 17);
         }
      }

      if (drawSelection) {
         minX = Math.min(minX, selectionX);
         minY = Math.min(minY, selectionY);
         maxX = Math.max(maxX, selectionX + frame.width());
         maxY = Math.max(maxY, selectionY + frame.height());
      }

      if (minX <= maxX) {
         int bx = minX * scale;
         int by = minY * scale;
         int bw = (maxX - minX) * scale;
         int bh = (maxY - minY) * scale;
         canvas.wrap(slotWindow.pixels(bw, bh), slotWindow.stride(), bw, bh);
         if (opaque) {
            for (int ix = 0; ix < 41; ix++) {
               if ((slotBits >>> ix & 1L) != 0L) {
                  int ox = guiX[ix] * scale - bx;
                  int oy = guiY[ix] * scale - by;
                  int[] behind = background[ix];
                  if (behind != null) {
                     canvas.copy(behind, px, px, ox, oy);
                  } else {
                     canvas.rect(ox, oy, px, px, -7631989);
                  }
               }
            }
         }

         if (drawSelection) {
            canvas.blend(frame.pixels(), frame.width(), frame.height(), selectionX * scale - bx, selectionY * scale - by, scale);
         }

         for (int ixx = 0; ixx < 41; ixx++) {
            if ((slotBits >>> ixx & 1L) != 0L) {
               int ox = guiX[ixx] * scale - bx;
               int oy = guiY[ixx] * scale - by;
               int[] icon = slotIcon[ixx];
               if (icon != null && icon != BLANK) {
                  canvas.blend(icon, px, px, ox, oy, 1);
               }

               if (!stack[ixx].isEmpty()) {
                  DigitFont.decorations(canvas, stack[ixx], ox, oy, scale);
               }
            }
         }

         slotWindow.present(windowX + bx, windowY + by, bw, bh);

         for (int ixxx = 0; ixxx < 41; ixxx++) {
            if ((slotBits >>> ixxx & 1L) != 0L) {
               shownStack[ixxx] = stack[ixxx];
               shownX[ixxx] = guiX[ixxx];
               shownY[ixxx] = guiY[ixxx];
               shownCount[ixxx] = stack[ixxx].getCount();
               shownBar[ixxx] = bar(stack[ixxx]);
            }
         }

         shownMask = slotBits;
         shownScale = scale;
         shownOpaque = opaque;
         shownSelection = selection;
         shownSelectionX = selectionX;
         shownSelectionY = selectionY;
         shownWindowX = windowX;
         shownWindowY = windowY;
         boxX = bx;
         boxY = by;
      }
   }

   private static void cursor(long frame, int scale, int windowX, int windowY) {
      if ((frame >>> 41 & 1L) == 0L) {
         if (cursorWindow != null) {
            cursorWindow.hide();
         }

         cursorStack = null;
      } else {
         ItemStack item = stack[41];
         int px = 16 * scale;
         int screenX = windowX + guiX[41] * scale;
         int screenY = windowY + guiY[41] * scale;
         boolean same = item == cursorStack && item.getCount() == cursorCount && bar(item) == cursorBar && scale == cursorScale;
         if (same) {
            if (screenX != cursorX || screenY != cursorY) {
               cursorWindow.move(screenX, screenY);
            }
         } else {
            int size = px + scale;
            canvas.wrap(cursorWindow.pixels(size, size), cursorWindow.stride(), size, size);
            int[] icon = slotIcon[41];
            if (icon != null && icon != BLANK) {
               canvas.blend(icon, px, px, 0, 0, 1);
            }

            DigitFont.decorations(canvas, item, 0, 0, scale);
            cursorWindow.present(screenX, screenY, size, size);
            cursorStack = item;
            cursorCount = item.getCount();
            cursorBar = bar(item);
            cursorScale = scale;
         }

         cursorX = screenX;
         cursorY = screenY;
         shownStack[41] = item;
      }
   }

   private static void closeTextures() {
      if (scratchView != null) {
         scratchView.close();
      }

      if (scratch != null) {
         scratch.close();
      }

      if (scratchDepthView != null) {
         scratchDepthView.close();
      }

      if (scratchDepth != null) {
         scratchDepth.close();
      }

      scratchView = null;
      scratch = null;
      scratchDepthView = null;
      scratchDepth = null;
      iconPx = 0;
   }

   private static void release() {
      dropAsync();
      if (slotWindow != null) {
         slotWindow.destroy();
         slotWindow = null;
      }

      if (cursorWindow != null) {
         cursorWindow.destroy();
         cursorWindow = null;
      }

      TextPanels.release();
      closeTextures();
      if (readback != null) {
         readback.close();
         readback = null;
      }

      if (asyncBuffer != null) {
         asyncBuffer.close();
         asyncBuffer = null;
      }

      icons.clear();
      cachedBytes = 0;
      Arrays.fill(slotIcon, null);
      Arrays.fill(pendingKey, null);
      Arrays.fill(background, null);
      Arrays.fill(backgroundNext, null);
      Arrays.fill(stack, null);
      Arrays.fill(shownStack, null);
      mask = 0L;
      fakeMask = 0L;
      shownMask = 0L;
      shownAtlas = null;
      shownHover = -1;
      cursorStack = null;
      backgroundValid = false;
   }

   private static List<Object>[] newKeys(int n) {
      return new List[n];
   }
}
