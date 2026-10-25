package net.spectre.client.imgui;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.platform.Window;
import imgui.ImFont;
import imgui.ImFontConfig;
import imgui.ImFontGlyphRangesBuilder;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.BooleanSupplier;
import net.spectre.client.config.ThemeStore;
import net.spectre.client.gui.GuiPalette;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWMonitorCallback;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

@Environment(EnvType.CLIENT)
public final class ImGuiManager {
   private static final ImGuiManager INSTANCE = new ImGuiManager();
   private static final String CATEGORY_FONT_RESOURCE = "/assets/spectre/fonts/geist-semibold.ttf";
   private static final String MODULE_FONT_RESOURCE = "/assets/spectre/fonts/geist-medium.ttf";
   private static final String HUD_VALUE_FONT_RESOURCE = "/assets/spectre/fonts/geist-semibold.ttf";
   private static final String HUD_TEXT_FONT_RESOURCE = "/assets/spectre/fonts/geist-semibold.ttf";
   private static final String HUD_LABEL_FONT_RESOURCE = "/assets/spectre/fonts/geist-medium.ttf";
   private static final String MENU_FONT_RESOURCE = "/assets/spectre/fonts/rajdhani-bold.ttf";
   public static final float MENU_FONT_SIZE = 24.0F;
   public static final float MENU_TAG_FONT_SIZE = 15.0F;
   public static final float MENU_BODY_FONT_SIZE = 18.0F;
   public static final float MENU_NAME_FONT_SIZE = 20.0F;
   public static final float MENU_ITEM_FONT_SIZE = 32.0F;
   public static final float HUD_VALUE_FONT_SIZE = 18.0F;
   public static final float HUD_TEXT_FONT_SIZE = 17.0F;
   public static final float HUD_LABEL_FONT_SIZE = 14.0F;
   private static final String EXTENDED_LATIN_GLYPHS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789ÇĞİÖŞÜçğıöşüÂâÊêÎîÔôÛûÀÁÄÈÉËÌÍÏÒÓÖÙÚÜÝàáäèéëìíïòóöùúüýÿ−∞";
   private static final float REFERENCE_HEIGHT = 1080.0F;
   private final ImGuiImplGlfw glfw = new ImGuiImplGlfw() {
      @Override
      public void keyCallback(long window, int keycode, int scancode, int action, int mods) {
         boolean tracked = keycode >= 0 && keycode < ImGuiManager.this.imguiKeysDown.length;
         boolean feed = action == 0 ? tracked && ImGuiManager.this.imguiKeysDown[keycode] : ImGuiManager.this.feedsKeyPress(keycode, mods);
         if (!feed) {
            if (ImGuiManager.this.minecraftKeys != null) {
               ImGuiManager.this.minecraftKeys.invoke(window, keycode, scancode, action, mods);
            }
         } else {
            if (tracked) {
               ImGuiManager.this.imguiKeysDown[keycode] = action != 0;
            }

            super.keyCallback(window, keycode, scancode, action, mods);
         }
      }

      @Override
      public void monitorCallback(long monitor, int event) {
         super.monitorCallback(monitor, event);
         if (ImGuiManager.this.minecraftMonitors != null) {
            ImGuiManager.this.minecraftMonitors.invoke(monitor, event);
         }
      }

      @Override
      public void cursorPosCallback(long window, double x, double y) {
         super.cursorPosCallback(window, x, y);
         ImGuiManager.feedScaledCursor(x, y);
      }

      @Override
      protected void updateMouseData() {
         super.updateMouseData();
         long handle = Minecraft.getInstance().getWindow().handle();
         if (GLFW.glfwGetWindowAttrib(handle, 131073) != 0) {
            double[] cx = new double[1];
            double[] cy = new double[1];
            GLFW.glfwGetCursorPos(handle, cx, cy);
            ImGuiManager.feedScaledCursor(cx[0], cy[0]);
         }
      }
   };
   private final ImGuiImplGl3 gl3 = new ImGuiImplGl3();
   private final boolean[] imguiKeysDown = new boolean[349];
   private GLFWKeyCallback minecraftKeys;
   private GLFWMonitorCallback minecraftMonitors;
   private boolean initialized;
   private boolean idleSkipped;
   private Runnable activeRenderer;
   private Runnable hudRenderer;
   private BooleanSupplier hudHasWork = () -> true;
   private Runnable toastRenderer;
   private BooleanSupplier toastHasWork = () -> false;
   private Runnable overlayRenderer;
   private Runnable streamRenderer;
   private BooleanSupplier streamHasWork = () -> false;
   private BooleanSupplier overlayHasWork = () -> false;
   private Runnable alertRenderer;
   private BooleanSupplier alertHasWork = () -> false;
   private float uiScale = 1.0F;
   private float userScale = 1.0F;
   private int frameCount;
   private float frameDelta;
   private ImFont categoryFont;
   private ImFont moduleFont;
   private ImFont titleFont;
   private ImFont smallFont;
   private ImFont hudValueFont;
   private ImFont hudTextFont;
   private ImFont hudLabelFont;
   private ImFont menuFont;
   private ImFont menuTagFont;
   private ImFont menuBodyFont;
   private ImFont menuNameFont;
   private ImFont menuItemFont;
   private short[] glyphRanges;

   private static void feedScaledCursor(double x, double y) {
      float sx = pixelsPerPointX();
      float sy = pixelsPerPointY();
      if (sx != 1.0F || sy != 1.0F) {
         ImGui.getIO().addMousePosEvent((float)(x * sx), (float)(y * sy));
      }
   }

   private static float pixelsPerPointX() {
      Window window = Minecraft.getInstance().getWindow();
      return window.getScreenWidth() > 0 ? (float)window.getWidth() / window.getScreenWidth() : 1.0F;
   }

   private static float pixelsPerPointY() {
      Window window = Minecraft.getInstance().getWindow();
      return window.getScreenHeight() > 0 ? (float)window.getHeight() / window.getScreenHeight() : 1.0F;
   }

   private ImGuiManager() {
   }

   public static ImGuiManager getInstance() {
      return INSTANCE;
   }

   public ImFont categoryFont() {
      return this.categoryFont;
   }

   public ImFont moduleFont() {
      return this.moduleFont;
   }

   public ImFont titleFont() {
      return this.titleFont;
   }

   public ImFont smallFont() {
      return this.smallFont;
   }

   public ImFont hudValueFont() {
      return this.hudValueFont;
   }

   public ImFont hudTextFont() {
      return this.hudTextFont;
   }

   public ImFont hudLabelFont() {
      return this.hudLabelFont;
   }

   public ImFont menuFont() {
      return this.menuFont;
   }

   public ImFont menuTagFont() {
      return this.menuTagFont;
   }

   public ImFont menuBodyFont() {
      return this.menuBodyFont;
   }

   public ImFont menuNameFont() {
      return this.menuNameFont;
   }

   public ImFont menuItemFont() {
      return this.menuItemFont;
   }

   public float uiScale() {
      return this.uiScale;
   }

   public int frameCount() {
      return this.frameCount;
   }

   public float frameDelta() {
      return this.frameDelta;
   }

   public void activate(Runnable renderer) {
      this.activeRenderer = renderer;
   }

   private boolean feedsKeyPress(int keycode, int mods) {
      return this.activeRenderer != null && (keycode != 258 || (mods & 2) == 0);
   }

   public void deactivate() {
      this.activeRenderer = null;
      if (this.initialized) {
         String ini = ImGui.getIO().getIniFilename();
         if (ini != null) {
            ImGui.saveIniSettingsToDisk(ini);
         }
      }
   }

   public void setHudRenderer(Runnable renderer, BooleanSupplier hasWork) {
      this.hudRenderer = renderer;
      this.hudHasWork = hasWork;
   }

   public void setToastRenderer(Runnable renderer, BooleanSupplier hasWork) {
      this.toastRenderer = renderer;
      this.toastHasWork = hasWork;
   }

   public void setStreamRenderer(Runnable renderer, BooleanSupplier hasWork) {
      this.streamRenderer = renderer;
      this.streamHasWork = hasWork;
   }

   public void setOverlayRenderer(Runnable renderer, BooleanSupplier hasWork) {
      this.overlayRenderer = renderer;
      this.overlayHasWork = hasWork;
   }

   public void setAlertRenderer(Runnable renderer, BooleanSupplier hasWork) {
      this.alertRenderer = renderer;
      this.alertHasWork = hasWork;
   }

   public void renderIfActive() {
      boolean hud = this.hudRenderer != null && this.hudHasWork.getAsBoolean();
      boolean toasts = this.toastRenderer != null && this.toastHasWork.getAsBoolean();
      boolean overlay = this.overlayRenderer != null && this.overlayHasWork.getAsBoolean();
      boolean alert = this.alertRenderer != null && this.alertHasWork.getAsBoolean();
      boolean stream = this.streamRenderer != null && this.streamHasWork.getAsBoolean();
      if (this.activeRenderer == null && !hud && !toasts && !overlay && !alert && !stream) {
         if (this.initialized) {
            ImGui.getIO().clearEventsQueue();
            this.idleSkipped = true;
         }
      } else {
         this.initIfNeeded();
         this.rebuildFontsIfScaleChanged();
         if (this.idleSkipped) {
            ImGuiIO io = ImGui.getIO();
            io.clearInputKeys();
            io.clearInputMouse();
            Arrays.fill(this.imguiKeysDown, false);
            this.idleSkipped = false;
         }

         Minecraft mc = Minecraft.getInstance();
         int w = mc.getWindow().getWidth();
         int h = mc.getWindow().getHeight();
         GL30.glBindFramebuffer(36160, 0);
         GL11.glViewport(0, 0, w, h);
         GlStateManager._viewport(0, 0, w, h);
         this.glfw.newFrame();
         this.frameDelta = Math.min(ImGui.getIO().getDeltaTime(), 0.1F);
         ImGui.getIO().setDeltaTime(this.frameDelta);
         this.updateDisplayMetrics();
         this.gl3.newFrame();
         ImGui.newFrame();
         this.frameCount++;
         GuiPalette.refresh();
         if (this.streamRenderer != null) {
            this.streamRenderer.run();
         }

         if (this.hudRenderer != null) {
            this.hudRenderer.run();
         }

         if (this.activeRenderer != null) {
            this.activeRenderer.run();
         }

         if (this.toastRenderer != null) {
            this.toastRenderer.run();
         }

         if (this.alertRenderer != null) {
            this.alertRenderer.run();
         }

         if (this.overlayRenderer != null) {
            this.overlayRenderer.run();
         }

         ImGui.render();
         this.gl3.renderDrawData(ImGui.getDrawData());
      }
   }

   public void dispose() {
      if (this.initialized) {
         net.spectre.client.gui.WorkspaceMascot.dispose();
         this.gl3.shutdown();
         this.glfw.shutdown();
         ImGui.destroyContext();
         this.categoryFont = null;
         this.moduleFont = null;
         this.titleFont = null;
         this.smallFont = null;
         this.hudValueFont = null;
         this.hudTextFont = null;
         this.hudLabelFont = null;
         this.menuFont = null;
         this.menuTagFont = null;
         this.menuBodyFont = null;
         this.menuNameFont = null;
         this.menuItemFont = null;
         this.initialized = false;
      }
   }

   private void initIfNeeded() {
      if (!this.initialized) {
         long windowHandle = Minecraft.getInstance().getWindow().handle();
         ImGui.createContext();
         ImGuiIO io = ImGui.getIO();
         io.setIniFilename(iniPath());
         io.addConfigFlags(1);
         ImGui.styleColorsDark();
         this.uiScale = this.computeScale(this.uiScale);
         this.loadFonts(io);
         this.minecraftKeys = GLFW.glfwSetKeyCallback(windowHandle, null);
         GLFW.glfwSetKeyCallback(windowHandle, this.minecraftKeys);
         this.minecraftMonitors = GLFW.glfwSetMonitorCallback(null);
         GLFW.glfwSetMonitorCallback(this.minecraftMonitors);
         this.glfw.init(windowHandle, true);
         this.gl3.init(null);
         this.initialized = true;
      }
   }

   private void rebuildFontsIfScaleChanged() {
      float scale = this.computeScale(this.uiScale);
      if (scale != this.uiScale) {
         this.uiScale = scale;
         ImGuiIO io = ImGui.getIO();
         io.getFonts().clear();
         this.loadFonts(io);
         this.gl3.destroyFontsTexture();
         this.gl3.createFontsTexture();
      }
   }

   private float computeScale(float current) {
      int height = Minecraft.getInstance().getWindow().getHeight();
      if (height < 200) {
         return current;
      } else {
         if (!this.initialized || !ImGui.getIO().getMouseDown(0)) {
            this.userScale = ThemeStore.get().uiScale.get();
         }

         float raw = Math.max(0.6F, Math.min(3.5F, height / 1080.0F * this.userScale));
         return Math.round(raw * 20.0F) / 20.0F;
      }
   }

   private static String iniPath() {
      try {
         Path dir = FabricLoader.getInstance().getConfigDir().resolve("spectre");
         Files.createDirectories(dir);
         return dir.resolve("gui.ini").toString();
      } catch (RuntimeException | IOException var1) {
         System.err.println("[Spectre] GUI layout will not be saved: " + var1.getMessage());
         return null;
      }
   }

   private void updateDisplayMetrics() {
      Minecraft client = Minecraft.getInstance();
      int w = Math.max(1, client.getWindow().getWidth());
      int h = Math.max(1, client.getWindow().getHeight());
      ImGuiIO io = ImGui.getIO();
      io.setDisplaySize(w, h);
      io.setDisplayFramebufferScale(1.0F, 1.0F);
   }

   private void loadFonts(ImGuiIO io) {
      if (this.glyphRanges == null) {
         this.glyphRanges = buildGlyphRanges(io);
      }

      try {
         io.getFonts().setFreeTypeRenderer(true);
      } catch (Throwable var3) {
         System.err.println("[Spectre] FreeType unavailable, using stb_truetype: " + var3.getMessage());
      }

      float s = this.uiScale;
      this.moduleFont = loadFont(io, "/assets/spectre/fonts/geist-medium.ttf", 16.0F * s, this.glyphRanges);
      this.categoryFont = loadFont(io, "/assets/spectre/fonts/geist-semibold.ttf", 17.0F * s, this.glyphRanges);
      this.titleFont = loadFont(io, "/assets/spectre/fonts/geist-semibold.ttf", 24.0F * s, this.glyphRanges);
      this.smallFont = loadFont(io, "/assets/spectre/fonts/geist-medium.ttf", 13.0F * s, this.glyphRanges);
      this.hudValueFont = loadFont(io, "/assets/spectre/fonts/geist-semibold.ttf", 18.0F * s, this.glyphRanges);
      this.hudTextFont = loadFont(io, "/assets/spectre/fonts/geist-semibold.ttf", 17.0F * s, this.glyphRanges);
      this.hudLabelFont = loadFont(io, "/assets/spectre/fonts/geist-medium.ttf", 14.0F * s, this.glyphRanges);
      this.menuFont = loadFont(io, "/assets/spectre/fonts/rajdhani-bold.ttf", 24.0F * s, this.glyphRanges);
      this.menuTagFont = loadFont(io, "/assets/spectre/fonts/rajdhani-bold.ttf", 15.0F * s, this.glyphRanges);
      this.menuBodyFont = loadFont(io, "/assets/spectre/fonts/geist-medium.ttf", 18.0F * s, this.glyphRanges);
      this.menuNameFont = loadFont(io, "/assets/spectre/fonts/rajdhani-bold.ttf", 20.0F * s, this.glyphRanges);
      this.menuItemFont = loadFont(io, "/assets/spectre/fonts/rajdhani-bold.ttf", 32.0F * s, this.glyphRanges);
      if (this.moduleFont == null && this.categoryFont == null) {
         io.getFonts().addFontDefault();
      } else if (this.moduleFont != null) {
         io.setFontDefault(this.moduleFont);
      }

      if (!io.getFonts().build()) {
         throw new IllegalStateException("ImGui font atlas could not be built");
      }
   }

   private static short[] buildGlyphRanges(ImGuiIO io) {
      ImFontGlyphRangesBuilder builder = new ImFontGlyphRangesBuilder();
      builder.addRanges(io.getFonts().getGlyphRangesDefault());
      builder.addRanges(io.getFonts().getGlyphRangesVietnamese());
      builder.addText("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789ÇĞİÖŞÜçğıöşüÂâÊêÎîÔôÛûÀÁÄÈÉËÌÍÏÒÓÖÙÚÜÝàáäèéëìíïòóöùúüýÿ−∞");
      return builder.buildRanges();
   }

   private static ImFont loadFont(ImGuiIO io, String resourcePath, float sizePixels, short[] ranges) {
      try {
         ImFont var7;
         try (InputStream in = ImGuiManager.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
               System.err.println("[Spectre] Font resource not found: " + resourcePath);
               return null;
            }

            byte[] data = in.readAllBytes();
            ImFontConfig config = new ImFontConfig();

            try {
               config.setOversampleH(3);
               config.setOversampleV(1);
               config.setPixelSnapH(true);
               config.setFontBuilderFlags(8);
               var7 = io.getFonts().addFontFromMemoryTTF(data, Math.round(sizePixels), config, ranges);
            } finally {
               config.destroy();
            }
         }

         return var7;
      } catch (RuntimeException | IOException var16) {
         System.err.println("[Spectre] Failed to load font " + resourcePath + ": " + var16.getMessage());
         return null;
      }
   }
}
