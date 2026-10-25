package net.spectre.client.gui.mainmenu;

import imgui.ImDrawList;
import imgui.ImFont;
import imgui.ImGui;
import imgui.ImGuiInputTextCallbackData;
import imgui.callback.ImGuiInputTextCallback;
import imgui.type.ImString;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import net.spectre.client.gui.SpectreDraw;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class DuskWidgets {
   public static final float BUTTON_H = 42.0F;
   public static final float RING = 42.0F;
   public static final float GAP = 11.0F;
   public static final float TAG_H = 24.0F;
   public static final float FIELD_H = 40.0F;
   public static final float PANEL_PAD = 22.0F;
   public static final float DIALOG_W = 560.0F;
   private static final float PANEL_ROUNDING = 8.0F;
   private static final float ROW_ROUNDING = 6.0F;
   private static final float FIELD_ROUNDING = 0.0F;
   private static final float FIELD_PAD = 14.0F;
   private static final float SCROLL_STEP = 64.0F;
   private static final float SCROLLBAR_W = 3.0F;
   private static final float SCROLLBAR_INSET = 5.0F;
   private static final float SCROLL_THUMB_MIN = 28.0F;
   private static final float SCROLLBAR_GRAB = 14.0F;
   private static final float BUTTON_TRACK = 3.0F;
   private static final float TAG_TRACK = 4.0F;
   private static final float DIALOG_FADE = 0.18F;
   private static final float DIALOG_VEIL = 0.62F;
   private static final float LINE_SPACING = 1.35F;
   private static final float EASE = 12.0F;
   private final Map<Object, DuskWidgets.Anim> anims = new HashMap<>();
   private final Map<Object, DuskWidgets.Scroll> scrolls = new HashMap<>();
   private final ArrayDeque<DuskWidgets.Scroll> openAreas = new ArrayDeque<>();
   private final ArrayDeque<float[]> clips = new ArrayDeque<>();
   private MenuFrame f;
   private int frame;
   private boolean click;
   private boolean doubleClick;
   private boolean mouseDown;
   private float wheel;
   private boolean blocked;
   private float alpha = 1.0F;

   public void begin(MenuFrame current) {
      this.f = current;
      this.frame++;
      this.anims.values().removeIf(anim -> anim.frame < this.frame - 1);
      this.click = current.clicked();
      this.doubleClick = this.click && ImGui.isMouseDoubleClicked(0);
      this.mouseDown = ImGui.isMouseDown(0);
      this.wheel = ImGui.getIO().getMouseWheel();
      this.blocked = false;
      this.alpha = 1.0F;
      this.openAreas.clear();
      this.clips.clear();
   }

   public void setBlocked(boolean blocked) {
      this.blocked = blocked;
   }

   public void end() {
      while (!this.openAreas.isEmpty()) {
         this.openAreas.pop();
         this.popClip();
      }
   }

   public MenuFrame frame() {
      return this.f;
   }

   public void alpha(float alpha) {
      this.alpha = alpha;
   }

   public float alpha() {
      return this.alpha;
   }

   public boolean hovered(float x, float y, float w, float h) {
      if (this.blocked) {
         return false;
      } else {
         float mx = this.f.mouseX();
         float my = this.f.mouseY();
         if (!(mx < x) && !(mx > x + w) && !(my < y) && !(my > y + h)) {
            float[] clip = this.clips.peek();
            return clip == null || mx >= clip[0] && mx <= clip[2] && my >= clip[1] && my <= clip[3];
         } else {
            return false;
         }
      }
   }

   public boolean pressed(float x, float y, float w, float h) {
      if (this.click && this.hovered(x, y, w, h)) {
         this.click = false;
         return true;
      } else {
         return false;
      }
   }

   public float hover(Object key, boolean target) {
      DuskWidgets.Anim anim = this.anims.computeIfAbsent(key, k -> new DuskWidgets.Anim());
      anim.value = anim.value + ((target ? 1.0F : 0.0F) - anim.value) * Math.min(1.0F, this.f.dt() * 12.0F);
      anim.frame = this.frame;
      return anim.value;
   }

   public float hoverOf(Object key) {
      DuskWidgets.Anim anim = this.anims.get(key);
      return anim != null ? anim.value : 0.0F;
   }

   public void tooltip(String text, float centerX, float y) {
      this.f.tooltip(text, centerX, y);
   }

   public boolean button(Object key, String label, float x, float y, float w, float h) {
      return this.button(key, label, x, y, w, h, true);
   }

   public boolean button(Object key, String label, float x, float y, float w, float h, boolean enabled) {
      boolean over = enabled && this.hovered(x, y, w, h);
      boolean clicked = over && this.pressed(x, y, w, h);
      float k = this.hover(key, over);
      cartoucheButton(this.f.bg(), this.f.menuFont(), x, y, w, h, label.toUpperCase(Locale.ROOT), enabled, k, this.f.scale(), this.alpha);
      return clicked;
   }

   public boolean iconButton(Object key, DuskWidgets.Icon icon, String tooltip, float x, float y, float size) {
      boolean over = this.hovered(x, y, size, size);
      boolean clicked = over && this.pressed(x, y, size, size);
      float k = this.hover(key, over);
      float s = this.f.scale();
      ringButton(this.f.bg(), icon, x, y, size, k, s, this.alpha);
      if (tooltip != null && k > 0.5F) {
         this.f.tooltip(tooltip, x + size * 0.5F, y + size + 8.0F * s);
      }

      return clicked;
   }

   public void panel(float x, float y, float w, float h, String title) {
      float s = this.f.scale();
      panel(this.f.bg(), x, y, w, h, s, this.alpha);
      if (title != null) {
         float plate = 24.0F * s;
         tag(this.f.bg(), this.f.tagFont(), x + w * 0.5F, y - plate * 0.5F, plate, title.toUpperCase(Locale.ROOT), s, this.alpha);
      }
   }

   public int row(Object key, float x, float y, float w, float h, boolean selected) {
      boolean over = this.hovered(x, y, w, h);
      int clicks = 0;
      if (over && this.pressed(x, y, w, h)) {
         clicks = this.doubleClick ? 2 : 1;
      }

      float k = this.hover(key, over);
      row(this.f.bg(), x, y, w, h, k, selected, this.f.scale(), this.alpha);
      return clicks;
   }

   public boolean textField(DuskWidgets.TextField field, float x, float y, float w, float h) {
      ImDrawList dl = this.f.bg();
      float s = this.f.scale();
      boolean over = this.hovered(x, y, w, h);
      if (over && this.click) {
         this.click = false;
      }

      float k = this.hover(field, over);
      field.focus = field.focus + ((field.active ? 1.0F : 0.0F) - field.focus) * Math.min(1.0F, this.f.dt() * 12.0F);
      field(dl, x, y, w, h, k, field.focus, s, this.alpha);
      ImFont font = this.f.bodyFont();
      float padX = 14.0F * s;
      float padY = Math.max(0.0F, (h - this.f.textHeight(font)) * 0.5F);
      if (this.blocked) {
         field.active = false;
         String text = field.text();
         boolean empty = text.isEmpty();
         String shown = empty ? field.placeholder : (field.password ? "*".repeat(text.codePointCount(0, text.length())) : text);
         dl.pushClipRect(x + padX, y, x + w - padX, y + h, true);
         this.f.text(dl, font, x + padX, y + padY, empty ? -7372954 : -923950, this.alpha, shown);
         dl.popClipRect();
         return false;
      } else {
         ImGui.setCursorScreenPos(x, y);
         if (font != null) {
            ImGui.pushFont(font);
         }

         ImGui.pushStyleVar(11, padX, padY);
         ImGui.pushStyleVar(13, 0.0F);
         ImGui.pushStyleVar(12, 0.0F);
         ImGui.pushStyleColor(7, 0);
         ImGui.pushStyleColor(8, 0);
         ImGui.pushStyleColor(9, 0);
         ImGui.pushStyleColor(53, 0);
         ImGui.pushStyleColor(0, SpectreDraw.col(-923950, this.alpha));
         ImGui.pushStyleColor(1, SpectreDraw.col(-7372954, this.alpha));
         ImGui.pushStyleColor(51, SpectreDraw.col(-871878, 0.35F * this.alpha));

         boolean empty;
         try {
            ImGui.setNextItemWidth(w);
            if (field.focusRequested) {
               ImGui.setKeyboardFocusHere();
               field.focusRequested = false;
            }

            boolean submitted = ImGui.inputTextWithHint(field.id, field.placeholder, field.buffer, field.flags(), field.limiter);
            field.active = ImGui.isItemActive();
            empty = submitted;
         } finally {
            ImGui.popStyleColor(7);
            ImGui.popStyleVar(3);
            if (font != null) {
               ImGui.popFont();
            }
         }

         return empty;
      }
   }

   public float beginScroll(Object key, float x, float y, float w, float h, float contentHeight) {
      DuskWidgets.Scroll area = this.scrolls.computeIfAbsent(key, k -> new DuskWidgets.Scroll());
      float s = this.f.scale();
      area.x = x;
      area.y = y;
      area.w = w;
      area.h = h;
      area.content = contentHeight;
      float max = Math.max(0.0F, contentHeight - h);
      if (this.wheel != 0.0F && this.hovered(x, y, w, h)) {
         area.target = area.target - this.wheel * 64.0F * s;
         this.wheel = 0.0F;
      }

      if (max > 0.0F) {
         float[] thumb = this.thumb(area, s);
         if (Float.isNaN(area.grab)) {
            float grabX = thumb[0] + thumb[2] * 0.5F - 14.0F * s * 0.5F;
            if (this.pressed(grabX, thumb[1], 14.0F * s, thumb[3])) {
               area.grab = this.f.mouseY() - thumb[1];
            }
         } else if (this.mouseDown && !this.blocked) {
            float travel = h - 10.0F * s - thumb[3];
            float along = (this.f.mouseY() - area.grab - (y + 5.0F * s)) / Math.max(1.0F, travel);
            area.target = area.offset = along * max;
         } else {
            area.grab = Float.NaN;
         }
      } else {
         area.grab = Float.NaN;
      }

      area.target = Math.max(0.0F, Math.min(max, area.target));
      area.offset = area.offset + (area.target - area.offset) * Math.min(1.0F, this.f.dt() * 16.0F);
      if (Math.abs(area.target - area.offset) < 0.5F) {
         area.offset = area.target;
      }

      area.offset = Math.max(0.0F, Math.min(max, area.offset));
      this.openAreas.push(area);
      this.pushClip(x, y, x + w, y + h);
      return Math.round(area.offset);
   }

   public void endScroll() {
      DuskWidgets.Scroll area = this.openAreas.pop();
      this.popClip();
      float max = area.content - area.h;
      if (!(max <= 0.0F)) {
         float s = this.f.scale();
         float[] thumb = this.thumb(area, s);
         float trackY = area.y + 5.0F * s;
         float trackH = area.h - 10.0F * s;
         ImDrawList dl = this.f.bg();
         boolean over = this.hovered(thumb[0] - 14.0F * s * 0.5F, thumb[1], 14.0F * s, thumb[3]);
         float k = this.hover(area, over || !Float.isNaN(area.grab));
         dl.addRectFilled(thumb[0], trackY, thumb[0] + thumb[2], trackY + trackH, SpectreDraw.col(-871878, 0.12F * this.alpha), thumb[2]);
         dl.addRectFilled(
            thumb[0],
            thumb[1],
            thumb[0] + thumb[2],
            thumb[1] + thumb[3],
            SpectreDraw.col(SpectreDraw.mix(-871878, -11654, k), (0.55F + 0.35F * k) * this.alpha),
            thumb[2]
         );
      }
   }

   public void setScroll(Object key, float offset) {
      DuskWidgets.Scroll area = this.scrolls.computeIfAbsent(key, k -> new DuskWidgets.Scroll());
      area.offset = area.target = offset;
   }

   public void pushClip(float x0, float y0, float x1, float y1) {
      float[] outer = this.clips.peek();
      if (outer != null) {
         x0 = Math.max(x0, outer[0]);
         y0 = Math.max(y0, outer[1]);
         x1 = Math.min(x1, outer[2]);
         y1 = Math.min(y1, outer[3]);
      }

      this.clips.push(new float[]{x0, y0, x1, y1});
      ImGui.pushClipRect(x0, y0, x1, y1, true);
   }

   public void popClip() {
      this.clips.pop();
      ImGui.popClipRect();
   }

   public void dialog(DuskWidgets.Dialog dialog) {
      ImDrawList dl = this.f.bg();
      float s = this.f.scale();
      float sw = this.f.width();
      float sh = this.f.height();
      if (Float.isNaN(dialog.openedAt)) {
         dialog.openedAt = this.f.time();
      }

      float fade = Math.min(1.0F, (this.f.time() - dialog.openedAt) / 0.18F);
      float a = 1.0F - (1.0F - fade) * (1.0F - fade) * (1.0F - fade);
      float saved = this.alpha;
      this.alpha = a;

      try {
         DuskScene.veil(dl, sw, sh, 0.62F * a);
         ImFont font = this.f.bodyFont();
         float pad = 22.0F * s;
         float gap = 11.0F * s;
         float bh = 42.0F * s;
         float w = Math.min(560.0F * s, sw - 48.0F * s);
         float inner = w - pad * 2.0F;
         List<String> lines = dialog.body.isEmpty() ? List.of() : wrap(font, dialog.body, inner);
         float lineH = this.f.textHeight(font) * 1.35F;
         float bodyH = lines.size() * lineH;
         float contentH = dialog.content != null ? dialog.contentHeight * s : 0.0F;
         float top = pad + 24.0F * s * 0.5F;
         float h = top + bodyH + (contentH > 0.0F ? (lines.isEmpty() ? 0.0F : gap) + contentH : 0.0F) + gap * 2.0F + bh + pad;
         float x = (sw - w) * 0.5F;
         float y = (sh - h) * 0.5F + (1.0F - a) * 8.0F * s;
         this.panel(x, y, w, h, dialog.title);
         float cy = y + top;

         for (String line : lines) {
            this.f.text(dl, font, x + (w - this.f.textWidth(font, line)) * 0.5F, cy, -923950, 0.9F * a, line);
            cy += lineH;
         }

         if (dialog.content != null) {
            if (!lines.isEmpty()) {
               cy += gap;
            }

            dialog.content.draw(this, dialog, x + pad, cy, inner, contentH);
         }

         List<DuskWidgets.Dialog.Choice> choices = dialog.choices();
         int n = choices.size();
         float bw = (inner - gap * (n - 1)) / Math.max(1, n);
         float by = y + h - pad - bh;

         for (int i = 0; i < n; i++) {
            DuskWidgets.Dialog.Choice choice = choices.get(i);
            if (this.button(choice, choice.label, x + pad + i * (bw + gap), by, bw, bh)) {
               dialog.press(choice);
            }
         }
      } finally {
         this.alpha = saved;
      }
   }

   public static void cartouche(ImDrawList dl, float x, float y, float w, float h, float k, float s, float alpha) {
      float r = h * 0.5F;
      float outer = Math.max(1.2F, 1.8F * s);
      float inner = Math.max(1.0F, s);
      float inset = 4.0F * s;
      dl.addRectFilled(x + 2.0F * s, y + 3.0F * s, x + w + 2.0F * s, y + h + 3.0F * s, SpectreDraw.col(-16777216, 0.5F * alpha), r);
      dl.addRectFilled(x, y, x + w, y + h, SpectreDraw.col(SpectreDraw.mix(-16120059, -871878, k), (0.9F + 0.1F * k) * alpha), r);
      dl.addRect(x, y, x + w, y + h, SpectreDraw.col(-871878, alpha), r, 0, outer);
      dl.addRect(
         x + inset,
         y + inset,
         x + w - inset,
         y + h - inset,
         SpectreDraw.col(SpectreDraw.mix(-871878, -16777216, k), (0.55F + 0.25F * k) * alpha),
         r - inset,
         0,
         inner
      );
   }

   public static void cartoucheButton(ImDrawList dl, ImFont font, float x, float y, float w, float h, String label, boolean enabled, float k, float s, float a) {
      float alpha = a * (enabled ? 1.0F : 0.45F);
      cartouche(dl, x, y, w, h, k, s, alpha);
      float size = font != null ? font.getFontSize() : 15.0F * s;
      int textColor = enabled ? SpectreDraw.mix(-1326998, -16777216, k) : -7372954;
      SpectreDraw.drawSpaced(dl, font, x + w * 0.5F, y + (h - size) * 0.5F + s, label, 3.0F * s, textColor, alpha, k < 0.5F);
   }

   public static void shenRing(ImDrawList dl, float cx, float cy, float r, float k, float s, float a) {
      float outer = Math.max(1.2F, 1.8F * s);
      float inner = Math.max(1.0F, s);
      dl.addCircleFilled(cx + 2.0F * s, cy + 3.0F * s, r, SpectreDraw.col(-16777216, 0.5F * a), 32);
      dl.addCircleFilled(cx, cy, r, SpectreDraw.col(SpectreDraw.mix(-16120059, -871878, k), (0.9F + 0.1F * k) * a), 32);
      dl.addCircle(cx, cy, r, SpectreDraw.col(-871878, a), 32, outer);
      dl.addCircle(cx, cy, r - 3.5F * s, SpectreDraw.col(SpectreDraw.mix(-871878, -16777216, k), (0.5F + 0.3F * k) * a), 32, inner);
      dl.addLine(cx - r * 0.75F, cy + r + 2.5F * s, cx + r * 0.75F, cy + r + 2.5F * s, SpectreDraw.col(-871878, a), outer);
   }

   public static void ringButton(ImDrawList dl, DuskWidgets.Icon icon, float x, float y, float size, float k, float s, float a) {
      float cx = x + size * 0.5F;
      float cy = y + size * 0.5F - 2.0F * s;
      float r = size * 0.44F;
      shenRing(dl, cx, cy, r, k, s, a);
      icon.draw(dl, cx, cy, r * 0.5F, SpectreDraw.col(SpectreDraw.mix(-1326998, -16777216, k), a), Math.max(1.2F, 1.6F * s));
   }

   public static float tag(ImDrawList dl, ImFont font, float cx, float y, float h, String text, float s, float a) {
      float spacing = 4.0F * s;
      float w = SpectreDraw.spacedWidth(font, text, spacing) + 28.0F * s;
      float x = cx - w * 0.5F;
      dl.addRectFilled(x, y, x + w, y + h, SpectreDraw.col(-871878, a), 2.0F * s);
      dl.addRect(x + 2.5F * s, y + 2.5F * s, x + w - 2.5F * s, y + h - 2.5F * s, SpectreDraw.col(-16777216, 0.45F * a), s, 0, Math.max(1.0F, s));
      float size = font != null ? font.getFontSize() : 15.0F * s;
      SpectreDraw.drawSpaced(dl, font, cx, y + (h - size) * 0.5F + s, text, spacing, -16777216, a, false);
      return w;
   }

   public static void panel(ImDrawList dl, float x, float y, float w, float h, float s, float a) {
      float r = 8.0F * s;
      float inset = 4.0F * s;
      dl.addRectFilled(x + 3.0F * s, y + 4.0F * s, x + w + 3.0F * s, y + h + 4.0F * s, SpectreDraw.col(-16777216, 0.45F * a), r);
      dl.addRectFilled(x, y, x + w, y + h, SpectreDraw.col(-16120059, a), r);
      dl.addRect(x, y, x + w, y + h, SpectreDraw.col(-871878, 0.9F * a), r, 0, Math.max(1.0F, 1.4F * s));
      dl.addRect(x + inset, y + inset, x + w - inset, y + h - inset, SpectreDraw.col(-871878, 0.3F * a), r - inset, 0, Math.max(1.0F, s));
   }

   public static void row(ImDrawList dl, float x, float y, float w, float h, float k, boolean selected, float s, float a) {
      float r = 6.0F * s;
      float line = Math.max(1.0F, s);
      if (selected) {
         float inset = 3.0F * s;
         dl.addRectFilled(x, y, x + w, y + h, SpectreDraw.col(-871878, (0.11F + 0.03F * k) * a), r);
         dl.addRect(x, y, x + w, y + h, SpectreDraw.col(-871878, 0.85F * a), r, 0, Math.max(1.0F, 1.2F * s));
         dl.addRect(x + inset, y + inset, x + w - inset, y + h - inset, SpectreDraw.col(-871878, 0.25F * a), r - inset, 0, line);
      } else {
         dl.addRectFilled(x, y, x + w, y + h, SpectreDraw.col(-16777216, 0.22F * a), r);
         if (k > 0.01F) {
            dl.addRectFilled(x, y, x + w, y + h, SpectreDraw.col(-871878, 0.06F * k * a), r);
            dl.addRect(x, y, x + w, y + h, SpectreDraw.col(-871878, 0.45F * k * a), r, 0, line);
         }
      }
   }

   public static void field(ImDrawList dl, float x, float y, float w, float h, float k, float focus, float s, float a) {
      float r = 0.0F * s;
      dl.addRectFilled(x, y, x + w, y + h, SpectreDraw.col(-16777216, 0.35F * a), r);
      dl.addRect(x, y, x + w, y + h, SpectreDraw.col(SpectreDraw.mix(-923950, -871878, focus), (0.12F + 0.1F * k + 0.5F * focus) * a), r, 0, Math.max(1.0F, s));
   }

   public static void drawTooltip(ImDrawList dl, ImFont font, String text, float cx, float y, float screenW, float screenH, float s) {
      float size = font != null ? font.getFontSize() : 15.0F * s;
      float w = SpectreDraw.textWidth(font, text) + 18.0F * s;
      float h = size + 10.0F * s;
      float margin = 4.0F * s;
      float x = Math.max(margin, Math.min(screenW - w - margin, cx - w * 0.5F));
      y = Math.min(y, screenH - h - margin);
      dl.addRectFilled(x, y, x + w, y + h, SpectreDraw.col(-16120059, 0.96F), 3.0F * s);
      dl.addRect(x, y, x + w, y + h, SpectreDraw.col(-871878, 0.7F), 3.0F * s, 0, Math.max(1.0F, s));
      SpectreDraw.text(dl, font, x + 9.0F * s, y + 5.0F * s, text, SpectreDraw.col(-11654, 1.0F));
   }

   public static List<String> wrap(ImFont font, String text, float width) {
      List<String> lines = new ArrayList<>();

      for (String paragraph : text.split("\n", -1)) {
         String line = "";

         for (String word : paragraph.split(" +")) {
            if (!word.isEmpty()) {
               String candidate = line.isEmpty() ? word : line + " " + word;
               if (SpectreDraw.textWidth(font, candidate) <= width) {
                  line = candidate;
               } else {
                  if (!line.isEmpty()) {
                     lines.add(line);
                  }

                  while (word.length() > 1 && SpectreDraw.textWidth(font, word) > width) {
                     int cut = fitCount(font, word, width);
                     lines.add(word.substring(0, cut));
                     word = word.substring(cut);
                  }

                  line = word;
               }
            }
         }

         lines.add(line);
      }

      return lines;
   }

   public static String fit(ImFont font, String text, float width) {
      if (SpectreDraw.textWidth(font, text) <= width) {
         return text;
      } else {
         String dots = "...";
         int n = fitCount(font, text, width - SpectreDraw.textWidth(font, dots));
         return text.substring(0, n).stripTrailing() + dots;
      }
   }

   private static int fitCount(ImFont font, String text, float width) {
      int lo = 1;
      int hi = text.length();

      while (lo < hi) {
         int mid = lo + hi + 1 >>> 1;
         if (SpectreDraw.textWidth(font, text.substring(0, mid)) <= width) {
            lo = mid;
         } else {
            hi = mid - 1;
         }
      }

      return Math.min(lo, text.length());
   }

   private float[] thumb(DuskWidgets.Scroll area, float s) {
      float inset = 5.0F * s;
      float barW = 3.0F * s;
      float trackH = area.h - 2.0F * inset;
      float thumbH = Math.min(trackH, Math.max(28.0F * s, trackH * area.h / area.content));
      float max = Math.max(1.0F, area.content - area.h);
      float y = area.y + inset + (trackH - thumbH) * Math.max(0.0F, Math.min(1.0F, area.offset / max));
      return new float[]{area.x + area.w - inset - barW, y, barW, thumbH};
   }

   @Environment(EnvType.CLIENT)
   private static final class Anim {
      float value;
      int frame;
   }

   @Environment(EnvType.CLIENT)
   public static final class Dialog {
      private final String title;
      private final String body;
      private final List<DuskWidgets.Dialog.Choice> actions = new ArrayList<>();
      private DuskWidgets.Dialog.Choice cancel;
      private DuskWidgets.Dialog.Content content;
      private float contentHeight;
      private boolean closed;
      private float openedAt = Float.NaN;

      public Dialog(String title, String body) {
         this.title = title;
         this.body = body;
      }

      public DuskWidgets.Dialog action(String label, Runnable run) {
         this.actions.add(new DuskWidgets.Dialog.Choice(label, run, null));
         return this;
      }

      public DuskWidgets.Dialog submit(String label, BooleanSupplier run) {
         this.actions.add(new DuskWidgets.Dialog.Choice(label, null, run));
         return this;
      }

      public DuskWidgets.Dialog cancel(String label) {
         return this.cancel(label, null);
      }

      public DuskWidgets.Dialog cancel(String label, Runnable run) {
         this.cancel = new DuskWidgets.Dialog.Choice(label, run, null);
         return this;
      }

      public DuskWidgets.Dialog content(float height, DuskWidgets.Dialog.Content content) {
         this.contentHeight = height;
         this.content = content;
         return this;
      }

      public void confirm() {
         if (!this.actions.isEmpty()) {
            this.press(this.actions.get(0));
         }
      }

      public void dismiss() {
         if (this.cancel != null) {
            this.press(this.cancel);
         } else {
            this.close();
         }
      }

      public void close() {
         this.closed = true;
      }

      public boolean isClosed() {
         return this.closed;
      }

      private List<DuskWidgets.Dialog.Choice> choices() {
         if (this.cancel == null) {
            return this.actions;
         } else {
            List<DuskWidgets.Dialog.Choice> all = new ArrayList<>(this.actions);
            all.add(this.cancel);
            return all;
         }
      }

      private void press(DuskWidgets.Dialog.Choice choice) {
         if (!this.closed) {
            if (choice.submit != null) {
               if (choice.submit.getAsBoolean()) {
                  this.closed = true;
               }
            } else {
               if (choice.run != null) {
                  choice.run.run();
               }

               this.closed = true;
            }
         }
      }

      @Environment(EnvType.CLIENT)
      private record Choice(String label, Runnable run, BooleanSupplier submit) {
      }

      @FunctionalInterface
      @Environment(EnvType.CLIENT)
      public interface Content {
         void draw(DuskWidgets var1, DuskWidgets.Dialog var2, float var3, float var4, float var5, float var6);
      }
   }

   @FunctionalInterface
   @Environment(EnvType.CLIENT)
   public interface Icon {
      void draw(ImDrawList var1, float var2, float var3, float var4, int var5, float var6);
   }

   @Environment(EnvType.CLIENT)
   private static final class Scroll {
      float offset;
      float target;
      float grab = Float.NaN;
      float x;
      float y;
      float w;
      float h;
      float content;
   }

   @Environment(EnvType.CLIENT)
   public static final class TextField {
      private static int nextId;
      private final String id;
      private final int maxLength;
      private final ImString buffer;
      private final ImGuiInputTextCallback limiter;
      private String placeholder;
      private boolean password;
      private boolean focusRequested;
      private boolean active;
      private float focus;

      public TextField(int maxLength) {
         this.id = "##dusk_field_" + nextId++;
         this.placeholder = "";
         this.maxLength = maxLength;
         this.buffer = new ImString(maxLength * 3);
         this.limiter = new ImGuiInputTextCallback() {
            public void accept(ImGuiInputTextCallbackData data) {
               TextField.this.limit(data);
            }
         };
      }

      public DuskWidgets.TextField placeholder(String text) {
         this.placeholder = text;
         return this;
      }

      public DuskWidgets.TextField password(boolean masked) {
         this.password = masked;
         return this;
      }

      public DuskWidgets.TextField allowedChars(String chars) {
         this.buffer.inputData.allowedChars = chars;
         return this;
      }

      public String text() {
         return this.buffer.get();
      }

      public void setText(String text) {
         if (text.codePointCount(0, text.length()) > this.maxLength) {
            text = text.substring(0, text.offsetByCodePoints(0, this.maxLength));
         }

         this.buffer.set(text);
      }

      public void focus() {
         this.focusRequested = true;
      }

      public boolean focused() {
         return this.active;
      }

      public int maxLength() {
         return this.maxLength;
      }

      private int flags() {
         return 4194368 | (this.password ? 1024 : 0);
      }

      private void limit(ImGuiInputTextCallbackData data) {
         String text = data.getBuf();
         int over = text.codePointCount(0, text.length()) - this.maxLength;
         if (over > 0) {
            byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
            int caret = Math.min(Math.max(0, data.getCursorPos()), utf8.length);
            String before = new String(utf8, 0, caret, StandardCharsets.UTF_8);
            int cut = Math.min(over, before.codePointCount(0, before.length()));
            if (cut > 0) {
               int start = before.substring(0, before.offsetByCodePoints(before.length(), -cut)).getBytes(StandardCharsets.UTF_8).length;
               data.deleteChars(start, caret - start);
               over -= cut;
            }

            if (over > 0) {
               String rest = data.getBuf();
               int keep = rest.substring(0, rest.offsetByCodePoints(0, this.maxLength)).getBytes(StandardCharsets.UTF_8).length;
               data.deleteChars(keep, data.getBufTextLen() - keep);
            }
         }
      }
   }
}
