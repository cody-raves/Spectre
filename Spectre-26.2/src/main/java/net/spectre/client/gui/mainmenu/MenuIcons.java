package net.spectre.client.gui.mainmenu;

import imgui.ImDrawList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class MenuIcons {
   private MenuIcons() {
   }

   public static void draw(ImDrawList dl, MenuAction action, float cx, float cy, float r, int color, float th) {
      switch (action) {
         case LANGUAGE:
            globe(dl, cx, cy, r, color, th);
            break;
         case ACCESSIBILITY:
            figure(dl, cx, cy, r, color, th);
            break;
         case SPECTRE:
            net.spectre.client.gui.WorkspaceMascot.drawBrand(dl,cx,cy,r*2,(color>>>24)/255f);
            break;
         case MODS:
            folder(dl, cx, cy, r, color, th);
            break;
         case ACCOUNTS:
            person(dl, cx, cy, r, color, th);
            break;
         default:
            dl.addCircleFilled(cx, cy, r * 0.25F, color, 12);
      }
   }

   public static void person(ImDrawList dl, float cx, float cy, float r, int color, float th) {
      dl.addCircle(cx, cy - r * 0.38F, r * 0.42F, color, 24, th);
      dl.pathClear();
      dl.pathArcTo(cx, cy + r * 0.95F, r * 0.82F, (float) Math.PI, (float) (Math.PI * 2), 20);
      dl.pathStroke(color, 0, th);
   }

   public static void ankh(ImDrawList dl, float cx, float cy, float r, int color, float th) {
      ellipse(dl, cx, cy - r * 0.55F, r * 0.36F, r * 0.45F, color, th);
      dl.addLine(cx - r * 0.72F, cy - r * 0.05F, cx + r * 0.72F, cy - r * 0.05F, color, th * 1.2F);
      dl.addLine(cx, cy - r * 0.1F, cx, cy + r * 1.05F, color, th * 1.2F);
   }

   public static void globe(ImDrawList dl, float cx, float cy, float r, int color, float th) {
      dl.addCircle(cx, cy, r, color, 32, th);
      ellipse(dl, cx, cy, r * 0.45F, r, color, th);
      dl.addLine(cx - r, cy, cx + r, cy, color, th);
   }

   public static void figure(ImDrawList dl, float cx, float cy, float r, int color, float th) {
      dl.addCircleFilled(cx, cy - r * 0.8F, r * 0.22F, color, 16);
      dl.addLine(cx - r * 0.85F, cy - r * 0.35F, cx + r * 0.85F, cy - r * 0.35F, color, th);
      dl.addLine(cx, cy - r * 0.35F, cx, cy + r * 0.3F, color, th);
      dl.addLine(cx, cy + r * 0.3F, cx - r * 0.5F, cy + r, color, th);
      dl.addLine(cx, cy + r * 0.3F, cx + r * 0.5F, cy + r, color, th);
   }

   public static void folder(ImDrawList dl, float cx, float cy, float r, int color, float th) {
      float l = cx - r;
      float rt = cx + r;
      float tp = cy - r * 0.7F;
      float bt = cy + r * 0.75F;
      dl.pathClear();
      dl.pathLineTo(l, bt);
      dl.pathLineTo(l, tp);
      dl.pathLineTo(cx - r * 0.2F, tp);
      dl.pathLineTo(cx, tp + r * 0.3F);
      dl.pathLineTo(rt, tp + r * 0.3F);
      dl.pathLineTo(rt, bt);
      dl.pathStroke(color, 1, th);
   }

   public static void ellipse(ImDrawList dl, float cx, float cy, float rx, float ry, int color, float th) {
      dl.pathClear();
      int segments = 28;

      for (int i = 0; i < segments; i++) {
         float ang = (float)(i * Math.PI * 2.0 / segments);
         dl.pathLineTo(cx + (float)Math.cos(ang) * rx, cy + (float)Math.sin(ang) * ry);
      }

      dl.pathStroke(color, 1, th);
   }
}
