package net.spectre.client.gui;

import imgui.ImDrawList;
import imgui.ImFont;
import imgui.ImGui;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import net.spectre.client.config.ThemeStore;
import net.spectre.client.imgui.ImGuiManager;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.setting.BlockColorListSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

@Environment(EnvType.CLIENT)
final class BlockListEditor {
   private static final int INLINE_MAX_ROWS = 8;
   private static final float HEADER_H = 40.0F;
   private static final float INLINE_ROW_H = 38.0F;
   private static final float LIST_PAD = 4.0F;
   private static final float EMPTY_H = 44.0F;
   private static final float PICKER_W = 820.0F;
   private static final float PICKER_MAX_H = 760.0F;
   private static final float PICKER_ROW_H = 42.0F;
   private static final float COLUMN_GAP = 28.0F;
   private static final float TAB_BAR_ROOM = 86.0F;
   private static final float ICON_SLOT = 32.0F;
   private static final float COLOR_CONTROLS_W = 88.0F;
   private static final float COLOR_COLUMN_EXTRA = 98.0F;
   private static final float COLOR_WINDOW_GAP = 12.0F;
   private static final float COLOR_WINDOW_W = 290.0F;
   private static final float COLOR_WINDOW_H = 360.0F;
   private static final int ADD_ALL_CONFIRM_OVER = 64;
   private static final long ARM_NANOS = 3000000000L;
   private static final int ARM_RESET = 1;
   private static final int ARM_CLEAR = 2;
   private static final int ARM_ADD = 3;
   private static final int LIST_BG = SpectreWidgets.withAlpha(-13226454, 0.5F);
   private static final int ROW_HOVER = 318767103;
   private static final int SCRIM = 1929379840;
   private static final int TRACER_ACCENT = -12276993;
   private static final String SELECT_LABEL = "Select";
   private static final String EMPTY_TEXT = "No blocks yet";
   private static final String PICKER_TITLE = "Select Blocks";
   private static final String AVAILABLE_TITLE = "All blocks";
   private static final String CHOSEN_TITLE = "Selected";
   private static final String NO_MATCHES = "No blocks match";
   private static final String ALL_CHOSEN = "Every block is selected";
   private static final String NONE_CHOSEN = "Nothing selected yet";
   private static final String SEARCH_HINT = "Search blocks...";
   private static final String RESET_LABEL = "Reset";
   private static final String ADD_SHOWN_LABEL = "Add all shown";
   private static final String CLEAR_LABEL = "Clear all";
   private static final String CONFIRM_LABEL = "Confirm";
   private static final int PICKER_FLAGS = 8639;
   private static final int LEV_INSERT = 1;
   private static final int LEV_SUBSTITUTE = 8;
   private static final int LEV_DELETE = 8;
   private static final Reference2ObjectOpenHashMap<Block, BlockListEditor.BlockInfo> INFOS = new Reference2ObjectOpenHashMap();
   private static final Map<BlockColorListSetting, BlockListEditor.ListState> LISTS = new WeakHashMap<>();
   private static String language;
   private static int iconsFrame = -2;
   private static int namesGeneration;
   private static int[] levPrev = new int[64];
   private static int[] levCur = new int[64];
   private final Reference2ObjectOpenHashMap<BlockColorListSetting, float[]> inlineScroll = new Reference2ObjectOpenHashMap();
   private BlockColorListSetting picker;
   private BlockListEditor.ListState pickerList;
   private String pickerSubtitle;
   private final ImString search = new ImString(64);
   private boolean focusSearch;
   private boolean searchActive;
   private long pickerOpenedAt;
   private boolean pickerJustOpened;
   private final float[] availableScroll = new float[2];
   private final float[] chosenScroll = new float[2];
   private int armedAction;
   private BlockColorListSetting armedFor;
   private long armedAt;
   private BlockColorListSetting colorList;
   private BlockColorListSetting.BlockEntry colorEntry;
   private boolean colorTracer;
   private String colorTitle;
   private final ImBoolean colorOpen = new ImBoolean(false);
   private float colorAtX;
   private float colorAtY;
   private boolean colorFocus;

   float render(ClientModule module, BlockColorListSetting setting, float pad, float contentW, float startY) {
      beginFrame();
      BlockListEditor.ListState list = list(setting);
      refreshChosen(setting, list);
      boolean colors = !setting.colorsHidden();
      ImDrawList dl = ImGui.getWindowDrawList();
      ImFont font = ImGuiManager.getInstance().moduleFont();
      ImFont small = ImGuiManager.getInstance().smallFont();
      float cardX0 = ImGui.getWindowPosX();
      float originY = ImGui.getWindowPosY() - ImGui.getScrollY();
      ImGui.setCursorPos(pad, startY + SpectreWidgets.s(10.0F));
      ImGui.textUnformatted(setting.name());
      float labelCy = originY + startY + SpectreWidgets.s(10.0F) + ImGui.getTextLineHeight() * 0.5F;
      countPill(dl, small, ImGui.getItemRectMaxX() + SpectreWidgets.s(8.0F), labelCy, list.countPill);
      float chipW = SpectreWidgets.textWidth(font, "Select") + SpectreWidgets.s(28.0F);
      float chipH = SpectreWidgets.s(28.0F);
      float chipX = cardX0 + pad + contentW - chipW;
      ImGui.setCursorScreenPos(chipX, labelCy - chipH * 0.5F);
      if (SpectreWidgets.chipButton("##add_blocks", "Select", chipW, chipH, false)) {
         this.openPicker(module, setting, list);
      }

      if (colors) {
         float swW = SpectreWidgets.s(30.0F);
         float swH = SpectreWidgets.s(20.0F);
         if (swatch(dl, "##default_color", setting.defaultColor(), chipX - SpectreWidgets.s(10.0F) - swW, labelCy - swH * 0.5F, swW, swH)) {
            this.openColor(setting, null, false);
         }

         if (ImGui.isItemHovered(32768) && !this.editing(setting, null, false)) {
            ClickGuiRenderer.showTooltip("Default colour, for blocks not on the list");
         }
      }

      float listY = startY + SpectreWidgets.s(40.0F);
      float step = SpectreWidgets.s(38.0F);
      int n = list.chosenCount;
      float boxH = n == 0 ? SpectreWidgets.s(44.0F) : Math.min(n, 8) * step + SpectreWidgets.s(4.0F) * 2.0F;
      float bx = cardX0 + pad;
      float by = originY + listY;
      float r = SpectreWidgets.s(10.0F);
      dl.addRectFilled(bx, by, bx + contentW, by + boxH, SpectreWidgets.c(LIST_BG), r);
      if (n == 0) {
         SpectreWidgets.text(dl, font, bx + SpectreWidgets.s(14.0F), by + (boxH - SpectreWidgets.lineHeight(font)) * 0.5F, -4542808, "No blocks yet");
      } else if (n <= 8) {
         this.drawChosenRows(dl, setting, list, pad, listY + SpectreWidgets.s(4.0F), contentW, step, 0, n, colors, font, small);
      } else {
         ImGui.setCursorPos(pad, listY + SpectreWidgets.s(4.0F));
         if (ImGui.beginChild("##chosen", contentW, boxH - SpectreWidgets.s(4.0F) * 2.0F, false, 24)) {
            ImGui.setCursorPos(0.0F, 0.0F);
            ImGui.dummy(1.0F, n * step);
            float scroll = ImGui.getScrollY();
            int first = Math.max(0, (int)(scroll / step));
            int last = Math.min(n, (int)Math.ceil((scroll + ImGui.getWindowHeight()) / step));
            this.drawChosenRows(ImGui.getWindowDrawList(), setting, list, 0.0F, 0.0F, contentW, step, first, last, colors, font, small);
            ClickGuiRenderer.smoothScroll((float[])this.inlineScroll.computeIfAbsent(setting, k -> new float[2]), frameDelta());
         }

         ImGui.endChild();
      }

      return SpectreWidgets.s(40.0F) + boxH + SpectreWidgets.s(12.0F);
   }

   private void drawChosenRows(
      ImDrawList dl,
      BlockColorListSetting setting,
      BlockListEditor.ListState list,
      float x,
      float y,
      float w,
      float step,
      int first,
      int last,
      boolean colors,
      ImFont font,
      ImFont small
   ) {
      boolean windowHovered = ImGui.isWindowHovered();
      float icon = BlockIcons.size(SpectreWidgets.s(32.0F));
      float btn = SpectreWidgets.s(22.0F);
      float lineH = SpectreWidgets.lineHeight(font);
      float smallH = SpectreWidgets.lineHeight(small);
      BlockListEditor.Row removed = null;

      for (int i = first; i < last; i++) {
         BlockListEditor.Row row = list.chosen[i];
         ImGui.setCursorPos(x, y + i * step);
         float x0 = ImGui.getCursorScreenPosX();
         float y0 = ImGui.getCursorScreenPosY();
         float cy = y0 + step * 0.5F;
         if (windowHovered && ImGui.isMouseHoveringRect(x0, y0, x0 + w, y0 + step)) {
            dl.addRectFilled(
               x0 + SpectreWidgets.s(4.0F),
               y0 + SpectreWidgets.s(1.0F),
               x0 + w - SpectreWidgets.s(4.0F),
               y0 + step - SpectreWidgets.s(1.0F),
               SpectreWidgets.c(318767103),
               SpectreWidgets.s(7.0F)
            );
         }

         ImGui.pushID(row.info.rawId);
         float right = x0 + w - SpectreWidgets.s(10.0F);
         ImGui.setCursorScreenPos(right - btn, cy - btn * 0.5F);
         if (ImGui.invisibleButton("##remove", btn, btn)) {
            removed = row;
         }

         boolean hovered = ImGui.isItemHovered();
         if (hovered) {
            dl.addCircleFilled(right - btn * 0.5F, cy, btn * 0.5F, SpectreWidgets.c(-12240588), 20);
         }

         SpectreWidgets.cross(dl, right - btn * 0.5F, cy, SpectreWidgets.s(3.5F), hovered ? -10856974 : -4542808);
         right -= btn + SpectreWidgets.s(8.0F);
         if (colors) {
            right = this.colorControls(dl, setting, row, right, cy);
         }

         ImGui.popID();
         float ix = x0 + SpectreWidgets.s(8.0F);
         BlockIcons.draw(dl, row.info.block, ix, cy - icon * 0.5F, icon);
         float tx = ix + icon + SpectreWidgets.s(10.0F);
         BlockListEditor.Fit fit = fit(row.inlineFit, row.info, font, small, right - tx);
         SpectreWidgets.text(dl, font, tx, cy - lineH * 0.5F, -461582, fit.text);
         if (fit.hint != null) {
            SpectreWidgets.text(dl, small, tx + fit.textW + SpectreWidgets.s(6.0F), cy - smallH * 0.5F, -4542808, fit.hint);
         }
      }

      if (removed != null) {
         deselect(setting, removed);
      }
   }

   private float colorControls(ImDrawList dl, BlockColorListSetting setting, BlockListEditor.Row row, float right, float cy) {
      float swW = SpectreWidgets.s(30.0F);
      float swH = SpectreWidgets.s(18.0F);
      float box = SpectreWidgets.s(16.0F);
      float gap = SpectreWidgets.s(6.0F);
      float x = right - swW;
      if (swatch(dl, "##tracer_color", row.entry.tracerColor(), x, cy - swH * 0.5F, swW, swH)) {
         this.openColor(setting, row, true);
      }

      if (ImGui.isItemHovered(32768) && !this.editing(setting, row.entry, true)) {
         ClickGuiRenderer.showTooltip("Tracer colour");
      }

      x -= gap + box;
      if (checkbox(dl, "##tracer", row.entry.tracerEnabled().get(), x, cy - box * 0.5F, box)) {
         row.entry.tracerEnabled().set(!row.entry.tracerEnabled().get());
         setting.notifyChanged();
      }

      if (ImGui.isItemHovered(32768)) {
         ClickGuiRenderer.showTooltip("Tracer");
      }

      x -= gap + swW;
      if (swatch(dl, "##box_color", row.entry.color(), x, cy - swH * 0.5F, swW, swH)) {
         this.openColor(setting, row, false);
      }

      if (ImGui.isItemHovered(32768) && !this.editing(setting, row.entry, false)) {
         ClickGuiRenderer.showTooltip("Box colour");
      }

      return x - SpectreWidgets.s(10.0F);
   }

   void renderWindows() {
      if (this.picker != null) {
         this.renderPicker();
      }

      if (this.colorList != null) {
         this.renderColorWindow();
      }
   }

   void close() {
      this.closePicker();
      this.closeColor();
   }

   boolean closePicker() {
      if (this.picker == null) {
         return false;
      } else {
         this.picker = null;
         this.pickerList = null;
         this.disarm();
         return true;
      }
   }

   private void openPicker(ClientModule module, BlockColorListSetting setting, BlockListEditor.ListState list) {
      this.closeColor();
      this.picker = setting;
      this.pickerList = list;
      this.pickerSubtitle = module.name() + " / " + setting.name();
      this.search.set("");
      this.focusSearch = true;
      this.searchActive = false;
      this.pickerOpenedAt = System.nanoTime();
      this.pickerJustOpened = true;
      this.availableScroll[0] = this.availableScroll[1] = this.chosenScroll[0] = this.chosenScroll[1] = 0.0F;
      this.disarm();
   }

   private void renderPicker() {
      beginFrame();
      BlockColorListSetting setting = this.picker;
      BlockListEditor.ListState list = this.pickerList;
      refreshChosen(setting, list);
      boolean colors = !setting.colorsHidden();
      float sw = ImGui.getIO().getDisplaySizeX();
      float sh = ImGui.getIO().getDisplaySizeY();
      float margin = SpectreWidgets.s(16.0F);
      float extra = colors ? SpectreWidgets.s(98.0F) : 0.0F;
      float w = Math.min(sw - margin * 2.0F, SpectreWidgets.s(820.0F) + extra);
      float h = Math.min(sh - SpectreWidgets.s(86.0F) - SpectreWidgets.s(32.0F), SpectreWidgets.s(760.0F));
      float open = ClickGuiRenderer.easeOut(ClickGuiRenderer.elapsed(this.pickerOpenedAt, 200.0F));
      float x = Math.round((sw - w) * 0.5F);
      float y = Math.round(Math.max(margin, (sh - SpectreWidgets.s(86.0F) - h) * 0.5F + SpectreWidgets.s(8.0F)) + SpectreWidgets.s(14.0F) * (1.0F - open));
      float rounding = ClickGuiRenderer.cardRounding();
      ImGui.setNextWindowPos(0.0F, 0.0F, 1);
      ImGui.setNextWindowSize(sw, sh, 1);
      if (this.pickerJustOpened) {
         ImGui.setNextWindowFocus();
      }

      ImGui.pushStyleVar(0, Math.max(0.01F, open));
      ImGui.pushStyleVar(2, 0.0F, 0.0F);
      ImGui.pushStyleVar(14, 0.0F, 0.0F);
      ImGui.pushStyleVar(4, 0.0F);
      boolean closeClicked = false;
      boolean visible = ImGui.begin("##block_picker", 8639);
      if (this.pickerJustOpened) {
         imgui.internal.ImGui.bringWindowToDisplayFront(imgui.internal.ImGui.getCurrentWindow());
      }

      if (visible) {
         ImDrawList dl = ImGui.getWindowDrawList();
         dl.addRectFilled(0.0F, 0.0F, sw, sh, SpectreWidgets.c(1929379840));
         shadow(dl, x, y, w, h, rounding);
         dl.addRectFilled(x, y, x + w, y + h, SpectreWidgets.c(GuiPalette.abgr(ThemeStore.get().cards.color.argbWithAlpha(250))), rounding);
         dl.addRect(x, y, x + w, y + h, SpectreWidgets.c(318767103), rounding, 0, 1.0F);
         ImFont font = ImGuiManager.getInstance().moduleFont();
         ImFont small = ImGuiManager.getInstance().smallFont();
         float pad = SpectreWidgets.s(24.0F);
         float top = y + SpectreWidgets.s(22.0F);
         float btn = SpectreWidgets.s(28.0F);
         ClickGuiRenderer.drawCardTitle("Select Blocks", x + pad, top + btn * 0.5F);
         ImGui.setCursorPos(x + w - pad - btn, top);
         if (SpectreWidgets.iconButton("##close", btn)) {
            closeClicked = true;
         }

         SpectreWidgets.cross(dl, x + w - pad - btn * 0.5F, top + btn * 0.5F, SpectreWidgets.s(4.5F), -461582);
         if (chip(
               "##reset_blocks",
               "Reset",
               this.armed(1, setting),
               setting.isDefault(),
               x + w - pad - btn - SpectreWidgets.s(10.0F),
               top + btn * 0.5F,
               small,
               "Back to the default blocks"
            )
            && this.confirm(1, setting)) {
            setting.resetToDefault();
         }

         float subY = top + btn + SpectreWidgets.s(2.0F);
         SpectreWidgets.text(dl, small, x + pad, subY, -4542808, this.pickerSubtitle);
         ImGui.setCursorPos(x + pad, subY + SpectreWidgets.lineHeight(small) + SpectreWidgets.s(14.0F));
         ImGui.pushStyleVar(11, SpectreWidgets.s(12.0F), SpectreWidgets.s(8.0F));
         ImGui.setNextItemWidth(w - pad * 2.0F);
         if (this.focusSearch) {
            ImGui.setKeyboardFocusHere();
            this.focusSearch = false;
         }

         boolean enter = this.searchActive && (ImGui.isKeyPressed(525, false) || ImGui.isKeyPressed(627, false));
         ImGui.inputTextWithHint("##block_search", "Search blocks...", this.search);
         this.searchActive = ImGui.isItemActive();
         ImGui.popStyleVar();
         float headerY = ImGui.getItemRectMaxY() + SpectreWidgets.s(18.0F);
         String ranked = list.searchKey;
         refreshSearch(list, this.search.get());
         if (list.searchKey != ranked) {
            this.availableScroll[0] = this.availableScroll[1] = this.chosenScroll[0] = this.chosenScroll[1] = 0.0F;
            if (this.armedAction == 3) {
               this.disarm();
            }
         }

         refreshColumns(setting, list);
         if (enter && !list.searchKey.isBlank()) {
            if (list.availableCount > 0) {
               select(setting, list.available[0]);
            }

            this.focusSearch = true;
         }

         float colW = (w - pad * 2.0F - SpectreWidgets.s(28.0F) - extra) * 0.5F;
         float rightW = colW + extra;
         float leftX = x + pad;
         float rightX = leftX + colW + SpectreWidgets.s(28.0F);
         float headerH = SpectreWidgets.s(28.0F);
         float listTop = headerY + headerH + SpectreWidgets.s(10.0F);
         float listBottom = y + h - SpectreWidgets.s(18.0F);
         if (list.availableLabelFor != list.availableCount) {
            list.availableLabelFor = list.availableCount;
            list.availableLabel = Integer.toString(list.availableCount);
         }

         if (list.shownLabelFor != list.shownCount) {
            list.shownLabelFor = list.shownCount;
            list.shownLabel = Integer.toString(list.shownCount);
         }

         columnHeader(dl, font, small, leftX, headerY, headerH, colW, "All blocks", list.availableLabel);
         columnHeader(dl, font, small, rightX, headerY, headerH, rightW, "Selected", list.shownLabel);
         float headerCy = headerY + headerH * 0.5F;
         boolean addShown = chip(
            "##add_shown",
            "Add all shown",
            this.armed(3, setting),
            list.availableCount == 0,
            leftX + colW - SpectreWidgets.s(6.0F),
            headerCy,
            small,
            "Adds every block this column lists"
         );
         boolean clearChosen = chip(
            "##clear_all",
            "Clear all",
            this.armed(2, setting),
            list.chosenCount == 0,
            rightX + rightW - SpectreWidgets.s(6.0F),
            headerCy,
            small,
            "Takes every block off the list"
         );
         float dividerX = Math.round(leftX + colW + SpectreWidgets.s(28.0F) * 0.5F);
         dl.addLine(dividerX, headerY, dividerX, listBottom, SpectreWidgets.c(268435455), 1.0F);
         float listH = Math.max(1.0F, listBottom - listTop);
         String emptyLeft = list.searchKey.isBlank() ? "Every block is selected" : "No blocks match";
         String emptyRight = list.searchKey.isBlank() ? "Nothing selected yet" : "No blocks match";
         BlockListEditor.Row picked = this.column(
            "##available", list.available, list.availableCount, true, null, leftX, listTop, colW, listH, this.availableScroll, emptyLeft, font, small
         );
         BlockListEditor.Row dropped = this.column(
            "##chosen", list.shown, list.shownCount, false, colors ? setting : null, rightX, listTop, rightW, listH, this.chosenScroll, emptyRight, font, small
         );
         if (picked != null) {
            select(setting, picked);
         }

         if (dropped != null) {
            deselect(setting, dropped);
         }

         if (addShown) {
            this.addAllShown(setting, list);
         }

         if (clearChosen) {
            this.clearAll(setting);
         }

         if (!this.pickerJustOpened && ImGui.isMouseClicked(0) && ImGui.isWindowHovered() && !ImGui.isMouseHoveringRect(x, y, x + w, y + h)) {
            closeClicked = true;
         }
      }

      ImGui.end();
      ImGui.popStyleVar(4);
      this.pickerJustOpened = false;
      if (closeClicked) {
         this.closePicker();
      }
   }

   private static boolean chip(String id, String label, boolean armed, boolean disabled, float right, float cy, ImFont small, String tooltip) {
      boolean pushed = ClickGuiRenderer.pushFont(small);
      float chipW = Math.max(SpectreWidgets.textWidth(small, label), SpectreWidgets.textWidth(small, "Confirm")) + SpectreWidgets.s(20.0F);
      float chipH = SpectreWidgets.lineHeight(small) + SpectreWidgets.s(12.0F);
      ImGui.setCursorPos(right - chipW, cy - chipH * 0.5F);
      ImGui.beginDisabled(disabled);
      boolean clicked = SpectreWidgets.chipButton(id, armed ? "Confirm" : label, chipW, chipH, armed);
      ImGui.endDisabled();
      if (ImGui.isItemHovered(65536)) {
         ClickGuiRenderer.showTooltip(tooltip);
      }

      if (pushed) {
         ImGui.popFont();
      }

      return clicked;
   }

   private boolean confirm(int action, BlockColorListSetting setting) {
      if (this.armed(action, setting)) {
         this.disarm();
         return true;
      } else {
         this.armedAction = action;
         this.armedFor = setting;
         this.armedAt = System.nanoTime();
         return false;
      }
   }

   private boolean armed(int action, BlockColorListSetting setting) {
      return this.armedAction == action && this.armedFor == setting && System.nanoTime() - this.armedAt < 3000000000L;
   }

   private void disarm() {
      this.armedAction = 0;
      this.armedFor = null;
   }

   private static void columnHeader(ImDrawList dl, ImFont font, ImFont small, float x, float y, float h, float w, String title, String count) {
      float cy = y + h * 0.5F;
      SpectreWidgets.text(dl, font, x + SpectreWidgets.s(4.0F), cy - SpectreWidgets.lineHeight(font) * 0.5F, -461582, title);
      countPill(dl, small, x + SpectreWidgets.s(4.0F) + SpectreWidgets.textWidth(font, title) + SpectreWidgets.s(8.0F), cy, count);
      dl.addLine(x, y + h + SpectreWidgets.s(4.0F), x + w, y + h + SpectreWidgets.s(4.0F), SpectreWidgets.c(268435455), 1.0F);
   }

   private BlockListEditor.Row column(
      String id,
      BlockListEditor.Row[] rows,
      int count,
      boolean adding,
      BlockColorListSetting colors,
      float x,
      float y,
      float w,
      float h,
      float[] scroll,
      String emptyText,
      ImFont font,
      ImFont small
   ) {
      BlockListEditor.Row clicked = null;
      ImGui.setCursorPos(x, y);
      if (ImGui.beginChild(id, w, h, false, 24)) {
         float step = SpectreWidgets.s(42.0F);
         float rowW = w - SpectreWidgets.s(6.0F);
         float rowH = step - SpectreWidgets.s(2.0F);
         ImGui.setCursorPos(0.0F, 0.0F);
         ImGui.dummy(1.0F, count * step + SpectreWidgets.s(6.0F));
         ImDrawList dl = ImGui.getWindowDrawList();
         if (count == 0) {
            SpectreWidgets.text(dl, font, ImGui.getWindowPosX() + SpectreWidgets.s(8.0F), ImGui.getWindowPosY() + SpectreWidgets.s(12.0F), -4542808, emptyText);
         } else {
            float lineH = SpectreWidgets.lineHeight(font);
            float smallH = SpectreWidgets.lineHeight(small);
            float icon = BlockIcons.size(SpectreWidgets.s(32.0F));
            float btnR = SpectreWidgets.s(11.0F);
            float scrollY = ImGui.getScrollY();
            int first = Math.max(0, (int)(scrollY / step));
            int last = Math.min(count, (int)Math.ceil((scrollY + ImGui.getWindowHeight()) / step));
            int accent = GuiPalette.accent();
            boolean windowHovered = ImGui.isWindowHovered();

            for (int i = first; i < last; i++) {
               BlockListEditor.Row row = rows[i];
               ImGui.setCursorPos(0.0F, i * step);
               float x0 = ImGui.getCursorScreenPosX();
               float y0 = ImGui.getCursorScreenPosY();
               float cy = y0 + rowH * 0.5F;
               float bx = x0 + rowW - SpectreWidgets.s(8.0F) - btnR;
               ImGui.pushID(row.info.rawId);
               float textRight;
               boolean hovered;
               if (colors == null) {
                  if (ImGui.invisibleButton("##row", rowW, rowH)) {
                     clicked = row;
                  }

                  hovered = ImGui.isItemHovered();
                  if (hovered) {
                     dl.addRectFilled(x0, y0, x0 + rowW, y0 + rowH, SpectreWidgets.c(318767103), SpectreWidgets.s(8.0F));
                  }

                  textRight = bx - btnR - SpectreWidgets.s(10.0F);
               } else {
                  if (windowHovered && ImGui.isMouseHoveringRect(x0, y0, x0 + rowW, y0 + rowH)) {
                     dl.addRectFilled(x0, y0, x0 + rowW, y0 + rowH, SpectreWidgets.c(318767103), SpectreWidgets.s(8.0F));
                  }

                  float minusX = bx - btnR - SpectreWidgets.s(6.0F);
                  textRight = this.colorControls(dl, colors, row, minusX - SpectreWidgets.s(4.0F), cy);
                  ImGui.setCursorScreenPos(minusX, y0);
                  if (ImGui.invisibleButton("##minus", x0 + rowW - minusX, rowH)) {
                     clicked = row;
                  }

                  hovered = ImGui.isItemHovered();
                  ImGui.setCursorScreenPos(x0, y0);
                  if (ImGui.invisibleButton("##row", Math.max(1.0F, textRight + SpectreWidgets.s(4.0F) - x0), rowH)) {
                     clicked = row;
                  }

                  hovered |= ImGui.isItemHovered();
               }

               ImGui.popID();
               float ix = x0 + SpectreWidgets.s(6.0F);
               BlockIcons.draw(dl, row.info.block, ix, cy - icon * 0.5F, icon);
               float tx = ix + icon + SpectreWidgets.s(12.0F);
               BlockListEditor.Fit fit = fit(row.pickerFit, row.info, font, small, textRight - tx);
               SpectreWidgets.text(dl, font, tx, cy - lineH * 0.5F, hovered ? -461582 : -2042416, fit.text);
               if (fit.hint != null) {
                  SpectreWidgets.text(dl, small, tx + fit.textW + SpectreWidgets.s(6.0F), cy - smallH * 0.5F, -4542808, fit.hint);
               }

               int fill = !hovered ? -13226454 : (adding ? accent : -10856974);
               int glyph = hovered ? -1 : -4542808;
               dl.addCircleFilled(bx, cy, btnR, SpectreWidgets.c(fill), 24);
               dl.addLine(bx - btnR * 0.45F, cy, bx + btnR * 0.45F, cy, SpectreWidgets.c(glyph), SpectreWidgets.s(1.6F));
               if (adding) {
                  dl.addLine(bx, cy - btnR * 0.45F, bx, cy + btnR * 0.45F, SpectreWidgets.c(glyph), SpectreWidgets.s(1.6F));
               }
            }
         }

         ClickGuiRenderer.smoothScroll(scroll, frameDelta());
      }

      ImGui.endChild();
      return clicked;
   }

   private void openColor(BlockColorListSetting setting, BlockListEditor.Row row, boolean tracer) {
      this.closeColor();
      this.colorList = setting;
      this.colorEntry = row == null ? null : row.entry;
      this.colorTracer = tracer;
      String label = row == null ? "Default Color" : (tracer ? row.info.name + " Tracer" : row.info.name);
      this.colorTitle = "Block Color - " + label + "###block_color";
      this.colorOpen.set(true);
      if (ImGui.isMousePosValid()) {
         this.colorAtX = ImGui.getIO().getMousePosX();
         this.colorAtY = ImGui.getIO().getMousePosY();
      } else {
         this.colorAtX = ImGui.getIO().getDisplaySizeX() * 0.5F;
         this.colorAtY = ImGui.getIO().getDisplaySizeY() * 0.5F;
      }

      this.colorFocus = true;
   }

   private void renderColorWindow() {
      float[] target = this.colorEntry == null ? this.colorList.defaultColor() : (this.colorTracer ? this.colorEntry.tracerColor() : this.colorEntry.color());
      float sw = ImGui.getIO().getDisplaySizeX();
      float sh = ImGui.getIO().getDisplaySizeY();
      boolean leftOf = this.colorAtX + 12.0F + 290.0F > sw;
      boolean above = this.colorAtY + 12.0F + 360.0F > sh;
      ImGui.setNextWindowPos(this.colorAtX + (leftOf ? -12.0F : 12.0F), this.colorAtY + (above ? -12.0F : 12.0F), 8, leftOf ? 1.0F : 0.0F, above ? 1.0F : 0.0F);
      ImGui.setNextWindowSize(270.0F, 330.0F, 4);
      if (this.colorFocus) {
         ImGui.setNextWindowFocus();
         this.colorFocus = false;
      }

      int flags = 352;
      if (ImGui.begin(this.colorTitle, this.colorOpen, flags)) {
         int pickerFlags = 135593984;
         if (ImGui.colorPicker4("##bcpicker", target, pickerFlags)) {
            this.colorList.notifyChanged();
         }

         ImGui.setNextItemWidth(240.0F);
         if (ImGui.colorEdit4("RGBA##bcce", target, pickerFlags)) {
            this.colorList.notifyChanged();
         }

         if (ImGui.button("Close##bccp", 80.0F, 22.0F)) {
            this.colorOpen.set(false);
         }
      }

      ImGui.end();
      if (!this.colorOpen.get()) {
         this.closeColor();
      }
   }

   private boolean editing(BlockColorListSetting setting, BlockColorListSetting.BlockEntry entry, boolean tracer) {
      return this.colorList == setting && this.colorEntry == entry && this.colorTracer == tracer;
   }

   private void closeColor() {
      if (this.colorList != null) {
         this.colorList.notifyChanged();
         this.colorList = null;
         this.colorEntry = null;
         this.colorOpen.set(false);
      }
   }

   private static void select(BlockColorListSetting setting, BlockListEditor.Row row) {
      setting.select(row.entry);
      if (row.follower != null) {
         setting.select(row.follower.entry);
      }
   }

   private static void deselect(BlockColorListSetting setting, BlockListEditor.Row row) {
      setting.deselect(row.entry);
      if (row.follower != null) {
         setting.deselect(row.follower.entry);
      }
   }

   private void addAllShown(BlockColorListSetting setting, BlockListEditor.ListState list) {
      int n = list.availableCount;
      if (n != 0 && (n <= 64 || this.confirm(3, setting))) {
         ArrayList<BlockColorListSetting.BlockEntry> entries = new ArrayList<>(n + 8);

         for (int i = 0; i < n; i++) {
            BlockListEditor.Row row = list.available[i];
            entries.add(row.entry);
            if (row.follower != null) {
               entries.add(row.follower.entry);
            }
         }

         setting.selectAll(entries);
      }
   }

   private void clearAll(BlockColorListSetting setting) {
      if (this.confirm(2, setting)) {
         setting.deselectAll();
      }
   }

   private static void beginFrame() {
      refreshNames();
      int frame = ImGuiManager.getInstance().frameCount();
      if (frame > iconsFrame + 1) {
         BlockIcons.forget();
      }

      iconsFrame = frame;
      BlockIcons.validate();
   }

   private static void refreshNames() {
      String selected = Minecraft.getInstance().getLanguageManager().getSelected();
      if (!selected.equals(language)) {
         language = selected;
         if (INFOS.isEmpty()) {
            for (Block block : BuiltInRegistries.BLOCK) {
               INFOS.put(block, new BlockListEditor.BlockInfo(block));
            }
         }

         Object2IntOpenHashMap<String> uses = new Object2IntOpenHashMap();
         ObjectIterator var5 = INFOS.values().iterator();

         while (var5.hasNext()) {
            BlockListEditor.BlockInfo info = (BlockListEditor.BlockInfo)var5.next();
            info.name = info.block.getName().getString();
            info.nameLower = info.name.toLowerCase(Locale.ROOT);
            if (!info.wallBanner) {
               uses.addTo(info.name, 1);
            }
         }

         var5 = INFOS.values().iterator();

         while (var5.hasNext()) {
            BlockListEditor.BlockInfo info = (BlockListEditor.BlockInfo)var5.next();
            info.hint = !info.wallBanner && uses.getInt(info.name) <= 1 ? null : info.path;
         }

         namesGeneration++;
      }
   }

   private static BlockListEditor.ListState list(BlockColorListSetting setting) {
      BlockListEditor.ListState list = LISTS.get(setting);
      if (list == null) {
         list = new BlockListEditor.ListState(setting);
         LISTS.put(setting, list);
      }

      return list;
   }

   private static void refreshChosen(BlockColorListSetting setting, BlockListEditor.ListState list) {
      if (list.chosenRevision != setting.revision()) {
         list.chosenRevision = setting.revision();
         List<BlockColorListSetting.BlockEntry> selected = setting.selected();
         if (list.chosen.length < selected.size()) {
            list.chosen = new BlockListEditor.Row[selected.size()];
         }

         int n = 0;

         for (int i = 0; i < selected.size(); i++) {
            BlockListEditor.Row row = (BlockListEditor.Row)list.byEntry.get(selected.get(i));
            if (row != null && (row.leader == null || !row.leader.entry.enabled().get())) {
               list.chosen[n++] = row;
            }
         }

         list.chosenCount = n;
         if (list.labelsFor != n) {
            list.labelsFor = n;
            list.countPill = Integer.toString(n);
         }
      }
   }

   private static void refreshSearch(BlockListEditor.ListState list, String raw) {
      if (raw != list.searchKey || list.searchGeneration != namesGeneration) {
         list.searchKey = raw;
         list.searchGeneration = namesGeneration;
         BlockListEditor.Row[] rows = list.pickable;
         String filter = raw.trim().toLowerCase(Locale.ROOT);
         if (filter.isEmpty()) {
            int i = 0;

            while (i < rows.length) {
               list.results[i] = i++;
            }

            list.resultCount = rows.length;
         } else {
            String[] words = filter.split(" ");
            long[] keys = list.sortKeys;
            int count = 0;

            for (int i = 0; i < rows.length; i++) {
               int distance = matchDistance(rows[i].info, filter, words);
               if (distance >= 0) {
                  keys[count++] = (long)distance << 32 | i;
               }
            }

            Arrays.sort(keys, 0, count);

            for (int k = 0; k < count; k++) {
               list.results[k] = (int)keys[k];
            }

            list.resultCount = count;
         }
      }
   }

   private static void refreshColumns(BlockColorListSetting setting, BlockListEditor.ListState list) {
      if (list.columnsRevision != setting.revision() || list.columnsKey != list.searchKey || list.columnsGeneration != namesGeneration) {
         list.columnsRevision = setting.revision();
         list.columnsKey = list.searchKey;
         list.columnsGeneration = namesGeneration;
         int n = 0;

         for (int k = 0; k < list.resultCount; k++) {
            BlockListEditor.Row row = list.pickable[list.results[k]];
            if (!row.entry.enabled().get()) {
               list.available[n++] = row;
            }
         }

         list.availableCount = n;
         if (list.shown.length < list.chosen.length) {
            list.shown = new BlockListEditor.Row[list.chosen.length];
            list.shownKeys = new long[list.chosen.length];
         }

         String filter = list.searchKey.trim().toLowerCase(Locale.ROOT);
         if (filter.isEmpty()) {
            System.arraycopy(list.chosen, 0, list.shown, 0, list.chosenCount);
            list.shownCount = list.chosenCount;
         } else {
            String[] words = filter.split(" ");
            long[] keys = list.shownKeys;
            int count = 0;

            for (int i = 0; i < list.chosenCount; i++) {
               int distance = matchDistance(list.chosen[i].info, filter, words);
               if (distance >= 0) {
                  keys[count++] = (long)distance << 32 | i;
               }
            }

            Arrays.sort(keys, 0, count);

            for (int kx = 0; kx < count; kx++) {
               list.shown[kx] = list.chosen[(int)keys[kx]];
            }

            list.shownCount = count;
         }
      }
   }

   private static int matchDistance(BlockListEditor.BlockInfo info, String filter, String[] words) {
      int found = Math.max(wordsFound(info.nameLower, words), wordsFound(info.idLower, words));
      int nameDistance = levenshtein(filter, info.nameLower);
      int idDistance = levenshtein(filter, info.idLower);
      float relevancy = Math.max(1.0F - (float)nameDistance / info.nameLower.length(), 1.0F - (float)idDistance / info.idLower.length());
      return found <= 0 && !(relevancy >= 0.5F) ? -1 : Math.min(nameDistance, idDistance);
   }

   private static int wordsFound(String text, String[] words) {
      int found = 0;

      for (String word : words) {
         if (!text.contains(word)) {
            return 0;
         }

         if (!word.isEmpty()) {
            for (int at = text.indexOf(word); at >= 0; at = text.indexOf(word, at + word.length())) {
               found++;
            }
         }
      }

      return found;
   }

   private static int levenshtein(String from, String to) {
      int n = from.length();
      int m = to.length();
      if (n == 0) {
         return m * 1;
      } else if (m == 0) {
         return n * 8;
      } else {
         if (levPrev.length <= m) {
            levPrev = new int[m + 1];
            levCur = new int[m + 1];
         }

         int[] prev = levPrev;
         int[] cur = levCur;

         for (int j = 0; j <= m; j++) {
            prev[j] = j * 1;
         }

         for (int i = 1; i <= n; i++) {
            cur[0] = i * 8;
            char typed = from.charAt(i - 1);

            for (int j = 1; j <= m; j++) {
               int substitute = prev[j - 1] + (typed == to.charAt(j - 1) ? 0 : 8);
               cur[j] = Math.min(Math.min(prev[j] + 8, cur[j - 1] + 1), substitute);
            }

            int[] swap = prev;
            prev = cur;
            cur = swap;
         }

         return prev[m];
      }
   }

   private static BlockListEditor.Fit fit(BlockListEditor.Fit fit, BlockListEditor.BlockInfo info, ImFont font, ImFont small, float avail) {
      if (fit.width == avail && fit.generation == namesGeneration) {
         return fit;
      } else {
         fit.width = avail;
         fit.generation = namesGeneration;
         fit.text = SpectreWidgets.ellipsize(font, info.name, avail);
         fit.textW = SpectreWidgets.textWidth(font, fit.text);
         float room = avail - fit.textW - SpectreWidgets.s(6.0F);
         fit.hint = info.hint != null && room >= SpectreWidgets.s(40.0F) ? SpectreWidgets.ellipsize(small, info.hint, room) : null;
         return fit;
      }
   }

   private static void countPill(ImDrawList dl, ImFont small, float x, float cy, String count) {
      float w = SpectreWidgets.textWidth(small, count) + SpectreWidgets.s(14.0F);
      float h = SpectreWidgets.lineHeight(small) + SpectreWidgets.s(6.0F);
      float y = cy - h * 0.5F;
      dl.addRectFilled(x, y, x + w, y + h, SpectreWidgets.c(-13226454), h * 0.5F);
      SpectreWidgets.text(dl, small, x + SpectreWidgets.s(7.0F), y + SpectreWidgets.s(3.0F), -4542808, count);
   }

   private static void shadow(ImDrawList dl, float x, float y, float w, float h, float rounding) {
      for (int i = 1; i <= 6; i++) {
         float e = SpectreWidgets.s(2.5F) * i;
         dl.addRectFilled(x - e, y - e + SpectreWidgets.s(4.0F), x + w + e, y + h + e + SpectreWidgets.s(4.0F), SpectreWidgets.c(335544320), rounding + e);
      }
   }

   private static boolean swatch(ImDrawList dl, String id, float[] rgba, float x, float y, float w, float h) {
      ImGui.setCursorScreenPos(x, y);
      boolean clicked = ImGui.invisibleButton(id, w, h);
      boolean hovered = ImGui.isItemHovered();
      float r = SpectreWidgets.s(5.0F);
      dl.addRectFilled(x, y, x + w, y + h, SpectreWidgets.c(-13226454), r);
      dl.addRectFilled(x, y, x + w, y + h, SpectreWidgets.c(abgr(rgba)), r);
      dl.addRect(x, y, x + w, y + h, SpectreWidgets.c(hovered ? 1728053247 : 654311423), r, 0, 1.0F);
      return clicked;
   }

   private static boolean checkbox(ImDrawList dl, String id, boolean on, float x, float y, float size) {
      ImGui.setCursorScreenPos(x, y);
      boolean clicked = ImGui.invisibleButton(id, size, size);
      int border = on ? -12276993 : (ImGui.isItemHovered() ? 1090519039 : 452984831);
      float r = SpectreWidgets.s(4.0F);
      dl.addRectFilled(x, y, x + size, y + size, SpectreWidgets.c(-13226454), r);
      dl.addRect(x + 0.5F, y + 0.5F, x + size - 0.5F, y + size - 0.5F, SpectreWidgets.c(border), r, 0, 1.0F);
      if (on) {
         SpectreWidgets.check(dl, x + size * 0.5F, y + size * 0.5F, size * 0.28F, -12276993);
      }

      return clicked;
   }

   private static int abgr(float[] rgba) {
      return unit(rgba[3]) << 24 | unit(rgba[2]) << 16 | unit(rgba[1]) << 8 | unit(rgba[0]);
   }

   private static int unit(float v) {
      return Math.round(Math.max(0.0F, Math.min(1.0F, v)) * 255.0F);
   }

   private static float frameDelta() {
      return ClickGuiRenderer.animationDelta(Math.max(ImGui.getIO().getDeltaTime(), 0.001F));
   }

   @Environment(EnvType.CLIENT)
   private static final class BlockInfo {
      final Block block;
      final int rawId;
      final String id;
      final String idLower;
      final String path;
      final boolean wallBanner;
      final String wallBannerId;
      String name;
      String nameLower;
      String hint;

      BlockInfo(Block block) {
         this.block = block;
         this.rawId = BuiltInRegistries.BLOCK.getId(block);
         Identifier key = BuiltInRegistries.BLOCK.getKey(block);
         this.id = key.toString();
         this.idLower = this.id.toLowerCase(Locale.ROOT);
         this.path = key.getPath();
         this.wallBanner = this.path.endsWith("_wall_banner");
         this.wallBannerId = !this.wallBanner && this.path.endsWith("_banner")
            ? key.getNamespace() + ":" + this.path.substring(0, this.path.length() - "banner".length()) + "wall_banner"
            : null;
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class Fit {
      String text;
      float textW;
      String hint;
      float width = -1.0F;
      int generation = -1;
   }

   @Environment(EnvType.CLIENT)
   private static final class ListState {
      final BlockListEditor.Row[] pickable;
      final Reference2ObjectOpenHashMap<BlockColorListSetting.BlockEntry, BlockListEditor.Row> byEntry = new Reference2ObjectOpenHashMap();
      BlockListEditor.Row[] chosen = new BlockListEditor.Row[0];
      int chosenCount;
      long chosenRevision = -1L;
      int labelsFor = -1;
      String countPill;
      final int[] results;
      final long[] sortKeys;
      int resultCount;
      String searchKey;
      int searchGeneration = -1;
      final BlockListEditor.Row[] available;
      int availableCount;
      BlockListEditor.Row[] shown = new BlockListEditor.Row[0];
      long[] shownKeys = new long[0];
      int shownCount;
      long columnsRevision = -1L;
      String columnsKey;
      int columnsGeneration = -1;
      int availableLabelFor = -1;
      int shownLabelFor = -1;
      String availableLabel;
      String shownLabel;

      ListState(BlockColorListSetting setting) {
         List<BlockColorListSetting.BlockEntry> entries = setting.entries();
         ArrayList<BlockListEditor.Row> rows = new ArrayList<>(entries.size());

         for (BlockColorListSetting.BlockEntry entry : entries) {
            Identifier key = Identifier.tryParse(entry.id());
            Block block = key == null ? null : BuiltInRegistries.BLOCK.getOptional(key).orElse(null);
            BlockListEditor.BlockInfo info = block == null ? null : (BlockListEditor.BlockInfo)BlockListEditor.INFOS.get(block);
            if (info != null) {
               BlockListEditor.Row row = new BlockListEditor.Row(entry, info);
               rows.add(row);
               this.byEntry.put(entry, row);
            }
         }

         ArrayList<BlockListEditor.Row> offered = new ArrayList<>(rows.size());

         for (BlockListEditor.Row row : rows) {
            if (row.info.wallBannerId != null) {
               BlockColorListSetting.BlockEntry wall = setting.entry(row.info.wallBannerId);
               row.follower = wall == null ? null : (BlockListEditor.Row)this.byEntry.get(wall);
               if (row.follower != null) {
                  row.follower.leader = row;
               }
            }

            if (!row.info.wallBanner) {
               offered.add(row);
            }
         }

         this.pickable = offered.toArray(new BlockListEditor.Row[0]);
         this.results = new int[this.pickable.length];
         this.sortKeys = new long[this.pickable.length];
         this.available = new BlockListEditor.Row[this.pickable.length];
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class Row {
      final BlockColorListSetting.BlockEntry entry;
      final BlockListEditor.BlockInfo info;
      BlockListEditor.Row follower;
      BlockListEditor.Row leader;
      final BlockListEditor.Fit inlineFit = new BlockListEditor.Fit();
      final BlockListEditor.Fit pickerFit = new BlockListEditor.Fit();

      Row(BlockColorListSetting.BlockEntry entry, BlockListEditor.BlockInfo info) {
         this.entry = entry;
         this.info = info;
      }
   }
}
