package net.spectre.client.gui;

import imgui.ImDrawList;
import imgui.ImGui;
import java.util.UUID;
import net.spectre.client.event.impl.Render2DEvent;
import net.spectre.client.util.NameFilter;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

@Environment(EnvType.CLIENT)
public final class StaffGhost {
   private static final float LIFE_SECONDS = 1.5F;
   private static final float TITLE_SECONDS = 1.25F;
   private static final int TITLE_IN = 10;
   private static final int TITLE_STAY = 40;
   private static final int TITLE_OUT = 15;
   private static final float NOTE_SECONDS = 4.5F;
   private static final float NOTE_IN = 0.5F;
   private static final float NOTE_STAY = 2.5F;
   private static final float NOTE_OUT = 1.0F;
   private static final Component NOTE = Component.literal("Goodluck Spectre User, Be Careful");
   private static final int NOTE_COLOR = 16777045;
   private static final float DISTANCE = 1.49F;
   private static final float LIFT = 0.24F;
   private static final float HALF = 0.45F;
   private static final float HAT = 1.125F;
   private static final float HALF_FOV_TAN = (float)Math.tan(Math.toRadians(35.0));
   private static final float NEAR = -0.08F;
   private static final int CELLS = 8;
   private static final float[][] FACES = new float[][]{
      {8.0F, 8.0F, -1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, -1.0F, -1.0F, 1.0F, 1.0F},
      {0.0F, 8.0F, -1.0F, 1.0F, -1.0F, -1.0F, 1.0F, 1.0F, -1.0F, -1.0F, -1.0F, 0.8F},
      {16.0F, 8.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, -1.0F, 1.0F, -1.0F, 1.0F, 0.8F},
      {8.0F, 0.0F, -1.0F, 1.0F, -1.0F, 1.0F, 1.0F, -1.0F, -1.0F, 1.0F, 1.0F, 0.95F},
      {16.0F, 0.0F, -1.0F, -1.0F, 1.0F, 1.0F, -1.0F, 1.0F, -1.0F, -1.0F, -1.0F, 0.6F}
   };
   private static UUID watcher;
   private static boolean hidden;
   private static String title;
   private static int stage;
   private static long startNanos;
   private static long noteNanos;
   private static boolean active;

   private StaffGhost() {
   }

   public static void watching(UUID id, String name, int others) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && name != null) {
         String shown = NameFilter.apply(name);
         watcher = id;
         hidden = !shown.equals(name);
         title = others > 0 ? shown + " +" + others : shown;
         stage = 0;
         startNanos = System.nanoTime();
         noteNanos = 0L;
         active = true;
      }
   }

   public static boolean hasWork() {
      if (active && Minecraft.getInstance().level == null) {
         active = false;
      }

      return active;
   }

   public static void renderNote(Render2DEvent event) {
      if (noteNanos != 0L) {
         float t = (float)(System.nanoTime() - noteNanos) / 1.0E9F;
         if (t >= 4.0F) {
            noteNanos = 0L;
         } else {
            float fade = t < 0.5F ? t / 0.5F : Math.min(1.0F, (4.0F - t) / 1.0F);
            int alpha = Math.round(fade * 255.0F);
            if (alpha > 8) {
               GuiGraphicsExtractor g = event.graphics;
               Font font = Minecraft.getInstance().font;
               g.pose().pushMatrix();
               g.pose().translate(g.guiWidth() / 2.0F, g.guiHeight() / 2.0F);
               g.pose().scale(2.0F, 2.0F);
               g.text(font, NOTE, -font.width(NOTE) / 2, -9 / 2, ARGB.color(alpha, 16777045), true);
               g.pose().popMatrix();
            }
         }
      }
   }

   public static void render() {
      if (hasWork()) {
         Minecraft mc = Minecraft.getInstance();
         float seconds = elapsed();
         if (stage == 0 && seconds >= 1.25F) {
            stage = 1;
            mc.gui.hud.setTimes(10, 40, 15);
            mc.gui.hud.setSubtitle(Component.literal("WATCHING YOU").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            mc.gui.hud.setTitle(Component.literal(title).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
         } else if (stage == 1 && seconds >= 4.5F) {
            stage = 2;
            mc.gui.hud.resetTitleTimes();
            noteNanos = System.nanoTime();
            active = false;
            return;
         }

         if (!(seconds >= 1.5F) && watcher != null) {
            int texture = HudRenderer.getInstance().staffFace(watcher, hidden);
            if (texture != 0) {
               float f = seconds / 1.5F;
               float alpha = 0.05F + 0.6F * (float)Math.sin(f * Math.PI);
               double angle = Math.toRadians(60.0 - 150.0 * f);
               float cos = (float)Math.cos(angle);
               float sin = (float)Math.sin(angle);
               float w = ImGui.getIO().getDisplaySizeX();
               float h = ImGui.getIO().getDisplaySizeY();
               float focal = h * 0.5F / HALF_FOV_TAN;
               ImDrawList dl = ImGui.getForegroundDrawList();
               drawCube(dl, texture, 0.45F, 0.0F, cos, sin, w, h, focal, alpha);
               drawCube(dl, texture, 0.50624996F, 32.0F, cos, sin, w, h, focal, alpha);
            }
         }
      }
   }

   private static void drawCube(ImDrawList dl, int texture, float half, float uShift, float cos, float sin, float w, float h, float focal, float alpha) {
      float[] a = new float[3];
      float[] b = new float[3];
      float[] c = new float[3];
      float[] sx = new float[81];
      float[] sy = new float[sx.length];

      for (float[] face : FACES) {
         corner(face, 2, half, cos, sin, a);
         corner(face, 5, half, cos, sin, b);
         corner(face, 8, half, cos, sin, c);
         float ux = b[0] - a[0];
         float uy = b[1] - a[1];
         float uz = b[2] - a[2];
         float vx = c[0] - a[0];
         float vy = c[1] - a[1];
         float vz = c[2] - a[2];
         float nx = uy * vz - uz * vy;
         float ny = uz * vx - ux * vz;
         float nz = ux * vy - uy * vx;
         float mx = a[0] + (ux + vx) * 0.5F;
         float my = a[1] + (uy + vy) * 0.5F;
         float mz = a[2] + (uz + vz) * 0.5F;
         if (!(nx * mx + ny * my + nz * mz <= 0.0F) && !(a[2] > -0.08F) && !(b[2] > -0.08F) && !(c[2] > -0.08F) && !(c[2] + uz > -0.08F)) {
            for (int j = 0; j <= 8; j++) {
               for (int i = 0; i <= 8; i++) {
                  float s = i / 8.0F;
                  float t = j / 8.0F;
                  float x = a[0] + ux * s + vx * t;
                  float y = a[1] + uy * s + vy * t;
                  float z = a[2] + uz * s + vz * t;
                  sx[j * 9 + i] = w * 0.5F + x / -z * focal;
                  sy[j * 9 + i] = h * 0.5F - y / -z * focal;
               }
            }

            int shade = Math.round(face[11] * 255.0F);
            int col = Math.round(Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F) << 24 | shade << 16 | shade << 8 | shade;
            float u0 = (face[0] + uShift) / 64.0F;
            float v0 = face[1] / 64.0F;
            float texel = 0.015625F;

            for (int j = 0; j < 8; j++) {
               for (int i = 0; i < 8; i++) {
                  int p = j * 9 + i;
                  int q = p + 8 + 1;
                  float cu = u0 + i * texel;
                  float cv = v0 + j * texel;
                  dl.addImageQuad(
                     texture,
                     sx[p],
                     sy[p],
                     sx[p + 1],
                     sy[p + 1],
                     sx[q + 1],
                     sy[q + 1],
                     sx[q],
                     sy[q],
                     cu,
                     cv,
                     cu + texel,
                     cv,
                     cu + texel,
                     cv + texel,
                     cu,
                     cv + texel,
                     col
                  );
               }
            }
         }
      }
   }

   private static void corner(float[] face, int at, float half, float cos, float sin, float[] out) {
      float x = face[at] * half;
      float y = face[at + 1] * half + 0.24F;
      float z = face[at + 2] * half - 1.49F;
      out[0] = x;
      out[1] = y * cos - z * sin;
      out[2] = y * sin + z * cos;
   }

   private static float elapsed() {
      return (float)(System.nanoTime() - startNanos) / 1.0E9F;
   }
}
