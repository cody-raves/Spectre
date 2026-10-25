package net.spectre.client.gui.mainmenu;

import imgui.ImDrawList;
import imgui.ImFont;
import net.spectre.client.config.ThemeStore;
import net.spectre.client.gui.SpectreDraw;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Util;

@Environment(EnvType.CLIENT)
public final class CommunityPopup {
   public static final String INVITE = "discord.gg/spectre";
   public static final String INVITE_URL = "https://discord.gg/spectre";
   private static final float OPEN_DELAY = 0.6F;
   private static final float MARK_RING = 54.0F;
   private static final float MARK_GAP = 14.0F;
   private static final float LINE_GAP = 3.0F;
   private static final float CAPTION_TRACK = 4.0F;
   private static final float CHECK_BOX = 16.0F;
   private static final float CHECK_LABEL_GAP = 9.0F;
   private static final float CHECK_REACH = 5.0F;
   private static final float ROW_GAP = 18.0F;
   private static final float CONTENT_H = 88.0F;
   private static final float[] CLYDE = clydeOutline();
   private static final float CLYDE_EYE_X = 0.332F;
   private static final float CLYDE_EYE_Y = 0.076F;
   private static final float CLYDE_EYE_RX = 0.18F;
   private static final float CLYDE_EYE_RY = 0.2F;
   private static boolean offered;
   private final DuskWidgets ui = new DuskWidgets();
   private final MenuFrame frame = new MenuFrame();
   private DuskWidgets.Dialog dialog;
   private boolean dontShowAgain;
   private long openedAt;

   public void update(float menuTime, boolean live) {
      if (this.dialog != null && this.dialog.isClosed()) {
         this.dialog = null;
      }

      if (this.dialog == null) {
         if (live && !offered && menuTime >= 0.6F) {
            offered = true;
            if (ThemeStore.get().communityPopup.get()) {
               this.open();
            }
         }
      }
   }

   public boolean isOpen() {
      return this.dialog != null && !this.dialog.isClosed();
   }

   public void render(ImDrawList dl, float width, float height, float scale, float dt, float mouseX, float mouseY, boolean clicked) {
      if (this.isOpen()) {
         float time = (float)(System.nanoTime() - this.openedAt) / 1.0E9F;
         this.frame.begin(dl, dl, width, height, scale, time, dt, mouseX, mouseY, clicked);
         this.ui.begin(this.frame);
         this.ui.dialog(this.dialog);
      }
   }

   public boolean keyPressed(int key) {
      if (!this.isOpen()) {
         return false;
      } else {
         if (key == 256) {
            this.dialog.dismiss();
         }

         return true;
      }
   }

   private void open() {
      this.dontShowAgain = false;
      this.openedAt = System.nanoTime();
      this.dialog = new DuskWidgets.Dialog("Join Spectre Community", "Updates, configs and support. Come say hi.")
         .content(88.0F, this::drawContent)
         .action("Join", this::join)
         .cancel("Later", this::answered);
   }

   private void join() {
      Util.getPlatform().openUri("https://discord.gg/spectre");
      this.answered();
   }

   private void answered() {
      if (this.dontShowAgain) {
         ThemeStore theme = ThemeStore.get();
         theme.communityPopup.set(false);
         theme.save();
      }
   }

   private void drawContent(DuskWidgets ui, DuskWidgets.Dialog dialog, float x, float y, float w, float h) {
      MenuFrame f = ui.frame();
      ImDrawList dl = f.bg();
      float s = f.scale();
      float a = ui.alpha();
      ImFont captionFont = f.tagFont();
      ImFont inviteFont = f.menuFont();
      String caption = "DISCORD";
      float ring = 54.0F * s;
      float track = 4.0F * s;
      float captionW = f.spacedWidth(captionFont, caption, track);
      float inviteW = f.textWidth(inviteFont, "discord.gg/spectre");
      float groupW = ring + 14.0F * s + Math.max(captionW, inviteW);
      float gx = x + (w - groupW) * 0.5F;
      boolean over = ui.hovered(gx, y, groupW, ring);
      if (ui.pressed(gx, y, groupW, ring)) {
         dialog.confirm();
      }

      float k = ui.hover("invite", over);
      float cx = gx + ring * 0.5F;
      float cy = y + ring * 0.5F - 2.0F * s;
      float r = ring * 0.44F;
      DuskWidgets.shenRing(dl, cx, cy, r, k, s, a);
      discordMark(dl, cx, cy, r * 1.1F, SpectreDraw.col(SpectreDraw.mix(-1326998, -16777216, k), a), SpectreDraw.col(SpectreDraw.mix(-16120059, -871878, k), a));
      float captionH = f.textHeight(captionFont);
      float inviteH = f.textHeight(inviteFont);
      float tx = gx + ring + 14.0F * s;
      float ty = cy - (captionH + 3.0F * s + inviteH) * 0.5F;
      f.spacedText(dl, captionFont, tx, ty, track, -7372954, a, caption);
      float inviteY = ty + captionH + 3.0F * s;
      float underY = inviteY + inviteH + s;
      f.text(dl, inviteFont, tx, inviteY, SpectreDraw.mix(-871878, -11654, k), a, "discord.gg/spectre");
      if (k > 0.01F) {
         dl.addLine(tx, underY, tx + inviteW * k, underY, SpectreDraw.col(-11654, k * a), Math.max(1.0F, s));
      }

      ImFont labelFont = f.tagFont();
      String label = "Don't show again";
      float box = 16.0F * s;
      float reach = 5.0F * s;
      float rowW = box + 9.0F * s + f.textWidth(labelFont, label);
      float bx = x + (w - rowW) * 0.5F;
      float by = y + h - box;
      boolean overCheck = ui.hovered(bx, by - reach, rowW, box + 2.0F * reach);
      if (ui.pressed(bx, by - reach, rowW, box + 2.0F * reach)) {
         this.dontShowAgain = !this.dontShowAgain;
      }

      float kc = ui.hover("dont-show-again", overCheck);
      checkBox(dl, bx, by, box, this.dontShowAgain, kc, s, a);
      int labelColor = SpectreDraw.mix(this.dontShowAgain ? -1326998 : -7372954, -11654, kc);
      f.text(dl, labelFont, bx + box + 9.0F * s, by + (box - f.textHeight(labelFont)) * 0.5F + s, labelColor, a, label);
   }

   private static void checkBox(ImDrawList dl, float x, float y, float size, boolean checked, float k, float s, float a) {
      float r = 3.0F * s;
      if (!checked) {
         dl.addRectFilled(x, y, x + size, y + size, SpectreDraw.col(-16777216, 0.55F * a), r);
         dl.addRect(x, y, x + size, y + size, SpectreDraw.col(-871878, (0.45F + 0.4F * k) * a), r, 0, Math.max(1.0F, 1.2F * s));
      } else {
         dl.addRectFilled(x, y, x + size, y + size, SpectreDraw.col(SpectreDraw.mix(-871878, -11654, k), a), r);
         dl.pathClear();
         dl.pathLineTo(x + size * 0.24F, y + size * 0.52F);
         dl.pathLineTo(x + size * 0.43F, y + size * 0.71F);
         dl.pathLineTo(x + size * 0.77F, y + size * 0.31F);
         dl.pathStroke(SpectreDraw.col(-16777216, a), 0, Math.max(1.4F, 2.0F * s));
      }
   }

   private static void discordMark(ImDrawList dl, float cx, float cy, float w, int ink, int hole) {
      float u = w * 0.5F;
      dl.pathClear();

      for (int i = 0; i < CLYDE.length; i += 2) {
         dl.pathLineTo(cx + CLYDE[i] * u, cy + CLYDE[i + 1] * u);
      }

      dl.pathFillConcave(ink);
      int segments = 16;

      for (int side = -1; side <= 1; side += 2) {
         float ex = cx + side * 0.332F * u;
         float ey = cy + 0.076F * u;
         dl.pathClear();

         for (int i = 0; i < segments; i++) {
            double angle = i * Math.PI * 2.0 / segments;
            dl.pathLineTo(ex + (float)Math.cos(angle) * 0.18F * u, ey + (float)Math.sin(angle) * 0.2F * u);
         }

         dl.pathFillConvex(hole);
      }
   }

   private static float[] clydeOutline() {
      int cheekSteps = 6;
      int chinSteps = 4;
      float[] half = new float[2 * (4 + cheekSteps + 3 + chinSteps)];
      int n = 0;
      n = put(half, n, 0.0F, -0.667F);
      n = put(half, n, 0.229F, -0.65F);
      n = put(half, n, 0.282F, -0.758F);
      n = put(half, n, 0.694F, -0.631F);

      for (int i = 1; i <= cheekSteps; i++) {
         float t = (float)i / cheekSteps;
         float v = 1.0F - t;
         float b0 = v * v * v;
         float b1 = 3.0F * v * v * t;
         float b2 = 3.0F * v * t * t;
         float b3 = t * t * t;
         n = put(half, n, b0 * 0.694F + b1 * 0.938F + b2 * 1.03F + b3 * 0.992F, b0 * -0.631F + b1 * -0.272F + b2 * 0.103F + b3 * 0.504F);
      }

      n = put(half, n, 0.749F, 0.653F);
      n = put(half, n, 0.486F, 0.758F);
      n = put(half, n, 0.344F, 0.531F);

      for (int i = 1; i <= chinSteps; i++) {
         float cx = 0.344F * (1.0F - (float)i / chinSteps);
         n = put(half, n, cx, -0.606F + (float)Math.sqrt(1.413721F - cx * cx));
      }

      int points = n / 2;
      float[] outline = new float[2 * (2 * points - 2)];
      System.arraycopy(half, 0, outline, 0, n);

      for (int i = points - 2; i >= 1; i--) {
         n = put(outline, n, -half[2 * i], half[2 * i + 1]);
      }

      return outline;
   }

   private static int put(float[] points, int n, float x, float y) {
      points[n] = x;
      points[n + 1] = y;
      return n + 2;
   }
}
