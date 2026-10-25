package net.spectre.client.gui;

import com.example.client.mixin.AccessorHudCooldownInstance;
import com.example.client.mixin.AccessorHudItemCooldowns;
import imgui.ImDrawList;
import imgui.ImFont;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import net.spectre.client.config.Surface;
import net.spectre.client.imgui.ImGuiManager;
import net.spectre.client.module.impl.client.HudModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@Environment(EnvType.CLIENT)
final class CooldownTray {
   private static final float PAD = 6.0F;
   private static final float TILE_W = 32.0F;
   private static final float TILE_GAP = 5.0F;
   private static final float DIAL = 26.0F;
   private static final float DIAL_ICON = 16.0F;
   private static final float DIAL_STROKE = 2.0F;
   private static final float BAR_ICON = 18.0F;
   private static final float BAR_H = 3.0F;
   private static final float BAR_GAP = 3.0F;
   private static final float TEXT_GAP = 2.0F;
   private static final float APPEAR_TIME = 0.18F;
   private static final float READY_TIME = 0.32F;
   private static final float VANISH_TIME = 0.22F;
   private static final float LOOKUP_INTERVAL = 0.5F;
   private static final float TICKS_PER_SECOND = 20.0F;
   private static final float TAU = (float) (Math.PI * 2);
   private static final float TOP = (float) (-Math.PI / 2);
   private static final String[] TENTHS = new String[100];
   private static final String[] SECONDS = new String[100];
   private static final String[] MINUTES = new String[100];
   private final List<CooldownTray.Slot> slots = new ArrayList<>();
   private final List<CooldownTray.Slot> pool = new ArrayList<>();
   private final CooldownTray.Slot[] samples = new CooldownTray.Slot[]{new CooldownTray.Slot(), new CooldownTray.Slot()};
   private final List<CooldownTray.Slot> sampleView = List.of(this.samples);
   private LocalPlayer owner;
   private boolean sampling;
   private boolean sandbar;
   private float clock;
   private float tickRate = 20.0F;

   CooldownTray() {
      this.samples[0].item = Items.ENDER_PEARL;
      this.samples[0].total = 20.0F;
      this.samples[1].item = Items.WIND_CHARGE;
      this.samples[1].total = 10.0F;

      for (CooldownTray.Slot sample : this.samples) {
         sample.presence = 1.0F;
      }
   }

   void reset() {
      for (CooldownTray.Slot slot : this.slots) {
         slot.group = null;
         slot.item = null;
         this.pool.add(slot);
      }

      this.slots.clear();
      this.owner = null;
   }

   boolean visible() {
      return this.sampling || !this.slots.isEmpty();
   }

   void update(Minecraft mc, HudModule hud, boolean active, boolean editMode, float dt) {
      this.clock += dt;
      this.sandbar = hud.cooldownSandbar();
      float rate = mc.level == null ? 20.0F : mc.level.tickRateManager().tickrate();
      this.tickRate = rate > 0.0F ? rate : 20.0F;
      LocalPlayer player = mc.player;
      if (player != this.owner) {
         this.reset();
         this.owner = player;
      }

      for (CooldownTray.Slot slot : this.slots) {
         slot.live = false;
      }

      if (active && player != null) {
         this.collect(mc, player);
      }

      for (int i = this.slots.size() - 1; i >= 0; i--) {
         CooldownTray.Slot slot = this.slots.get(i);
         if (slot.live) {
            slot.presence = Math.min(1.0F, slot.presence + dt / 0.18F);
            if (slot.item == null && this.clock >= slot.lookupAt && player != null) {
               slot.item = resolve(player, slot.group);
               slot.lookupAt = this.clock + 0.5F;
            }
         } else {
            if (!slot.done) {
               slot.done = true;
               slot.remaining = 0.0F;
               slot.ready = active ? 1.0F : 0.0F;
            }

            if (slot.ready > 0.0F) {
               slot.ready = Math.max(0.0F, slot.ready - dt / 0.32F);
            } else {
               slot.presence -= dt / 0.22F;
               if (slot.presence <= 0.0F) {
                  this.slots.remove(i);
                  slot.group = null;
                  slot.item = null;
                  this.pool.add(slot);
               }
            }
         }
      }

      this.sampling = editMode && this.slots.isEmpty();
      if (this.sampling) {
         for (int ix = 0; ix < this.samples.length; ix++) {
            float phase = (this.clock * 0.45F + ix * 0.43F) % 1.0F;
            this.samples[ix].remaining = (1.0F - phase) * this.samples[ix].total;
         }
      }
   }

   private void collect(Minecraft mc, LocalPlayer player) {
      ItemCooldowns cooldowns = player.getCooldowns();
      AccessorHudItemCooldowns access = (AccessorHudItemCooldowns)cooldowns;
      int tick = access.spectre$hudTickCount();
      float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);

      for (Entry<Identifier, ?> entry : access.spectre$hudCooldowns().entrySet()) {
         AccessorHudCooldownInstance span = (AccessorHudCooldownInstance)entry.getValue();
         int start = span.spectre$hudStartTime();
         int end = span.spectre$hudEndTime();
         float left = end - (tick + partial);
         if (!(left <= 0.0F)) {
            CooldownTray.Slot slot = this.find(entry.getKey());
            if (slot == null) {
               slot = this.open(entry.getKey());
            }

            slot.live = true;
            slot.done = false;
            slot.ready = 0.0F;
            slot.remaining = left;
            slot.total = Math.max(1.0F, (float)(end - start));
         }
      }
   }

   private CooldownTray.Slot find(Identifier group) {
      for (CooldownTray.Slot slot : this.slots) {
         if (slot.group.equals(group)) {
            return slot;
         }
      }

      return null;
   }

   private CooldownTray.Slot open(Identifier group) {
      CooldownTray.Slot slot = this.pool.isEmpty() ? new CooldownTray.Slot() : this.pool.remove(this.pool.size() - 1);
      slot.group = group;
      slot.item = null;
      slot.lookupAt = 0.0F;
      slot.presence = 0.0F;
      slot.ready = 0.0F;
      slot.done = false;
      this.slots.add(slot);
      return slot;
   }

   private static Item resolve(LocalPlayer player, Identifier group) {
      Inventory inventory = player.getInventory();
      ItemCooldowns cooldowns = player.getCooldowns();

      for (int i = 0; i < inventory.getContainerSize(); i++) {
         ItemStack stack = inventory.getItem(i);
         if (!stack.isEmpty() && group.equals(cooldowns.getCooldownGroup(stack))) {
            return stack.getItem();
         }
      }

      return BuiltInRegistries.ITEM.containsKey(group) ? BuiltInRegistries.ITEM.getValue(group) : null;
   }

   float planWidth(float s) {
      if (!this.visible()) {
         return 1.0F;
      } else {
         float spread = 0.0F;

         for (CooldownTray.Slot slot : this.sampling ? this.sampleView : this.slots) {
            spread += HudGlyphs.easeOutQuint(slot.presence) * 37.0F * s;
         }

         return Math.max(1.0F, (float)Math.round(6.0F * s * 2.0F + Math.max(0.0F, spread - 5.0F * s)));
      }
   }

   float planHeight(float s) {
      float body = this.sandbar ? ItemIcons.pixels(18.0F * s) + 3.0F * s + barHeight(s) : Math.round(26.0F * s);
      return Math.round(6.0F * s * 2.0F + body + 2.0F * s + HudChrome.px(14.0F, s));
   }

   private static float barHeight(float s) {
      return Math.max(2.0F, (float)Math.round(3.0F * s));
   }

   void draw(ImDrawList dl, HudLayoutStore.HudElementState state, float x, float y, float alpha, boolean shadow, int accent) {
      if (!(alpha <= 0.004F) && this.visible()) {
         List<CooldownTray.Slot> shown = this.sampling ? this.sampleView : this.slots;
         float total = 0.0F;

         for (CooldownTray.Slot slot : shown) {
            total += slot.presence;
         }

         float cardAlpha = alpha * HudGlyphs.easeOutQuint(Math.min(1.0F, total));
         if (!(cardAlpha <= 0.004F)) {
            float s = HudRenderer.scaleOf(state);
            float w = state.width;
            float h = state.height;
            Surface surface = HudRenderer.hudSurface(state);
            boolean guard = HudChrome.needsTextGuard(surface);
            HudChrome.card(dl, surface, x, y, w, h, HudRenderer.hudRounding() * s, cardAlpha, shadow, s);
            ImFont label = ImGuiManager.getInstance().hudLabelFont();
            int labelPx = HudChrome.px(14.0F, s);
            float pad = Math.round(6.0F * s);
            float tile = 32.0F * s;
            float dial = this.sandbar ? ItemIcons.pixels(18.0F * s) + 3.0F * s + barHeight(s) : Math.round(26.0F * s);
            float textTop = Math.round(y + pad + dial + 2.0F * s);
            float bleed = 10.0F * s;
            dl.pushClipRect(x, y - bleed, x + w, y + h + bleed, true);
            float cx = x + pad;

            for (CooldownTray.Slot slot : shown) {
               float open = HudGlyphs.easeOutQuint(slot.presence);
               float a = alpha * open;
               if (a > 0.004F) {
                  if (this.sandbar) {
                     this.drawSandbar(dl, slot, Math.round(cx), y + pad, tile, s, a, accent);
                  } else {
                     this.drawHalo(dl, slot, cx + tile * 0.5F, y + pad + Math.round(26.0F * s) * 0.5F, s, a, accent);
                  }

                  this.drawCaption(dl, slot, cx + tile * 0.5F, textTop, label, labelPx, s, a, guard);
               }

               cx += open * (tile + 5.0F * s);
            }

            dl.popClipRect();
         }
      }
   }

   private void drawHalo(ImDrawList dl, CooldownTray.Slot slot, float cx, float cy, float s, float a, int accent) {
      float r = Math.round(26.0F * s) * 0.5F - 2.0F * s * 0.5F;
      float stroke = Math.max(1.5F, 2.0F * s);
      float left = fraction(slot);
      dl.addCircle(cx, cy, r, HudChrome.scaleAlpha(452984831, a), 0, stroke);
      if (left > 0.0F) {
         int tone = SpectreDraw.mix(accent, GuiPalette.abgr(-871878), 1.0F - left);
         HudGlyphs.arc(dl, cx, cy, r, (float) (-Math.PI / 2), (float) (Math.PI * 2) * left, stroke, HudChrome.scaleAlpha(tone, a));
      }

      int icon = (int)ItemIcons.pixels(16.0F * s);
      drawItem(dl, slot, cx - icon * 0.5F, cy - icon * 0.5F, icon, s, a * (0.62F + 0.38F * (1.0F - left)));
      if (slot.ready > 0.0F) {
         float grow = (1.0F - slot.ready) * 7.0F * s;
         dl.addCircle(cx, cy, r + grow, HudGlyphs.ink(-871878, a * slot.ready * 0.9F), 0, stroke);
      }
   }

   private void drawSandbar(ImDrawList dl, CooldownTray.Slot slot, float tx, float ty, float tile, float s, float a, int accent) {
      int icon = (int)ItemIcons.pixels(18.0F * s);
      float left = fraction(slot);
      float ix = Math.round(tx + (tile - icon) * 0.5F);
      drawItem(dl, slot, ix, ty, icon, s, a * (0.62F + 0.38F * (1.0F - left)));
      float by = ty + icon + 3.0F * s;
      float bh = barHeight(s);
      float inset = Math.round(3.0F * s);
      float bl = tx + inset;
      float br = tx + tile - inset;
      dl.addRectFilled(bl, by, br, by + bh, HudChrome.scaleAlpha(452984831, a), bh * 0.5F);
      if (left > 0.0F) {
         int tone = SpectreDraw.mix(accent, GuiPalette.abgr(-871878), 1.0F - left);
         float fill = (br - bl) * left;
         dl.addRectFilled(bl, by, bl + fill, by + bh, HudChrome.scaleAlpha(tone, a), Math.min(bh * 0.5F, fill * 0.5F));
      }

      if (slot.ready > 0.0F) {
         float glow = slot.ready;
         dl.addRectFilled(bl, by, br, by + bh, HudGlyphs.ink(-11654, a * glow), bh * 0.5F);
      }
   }

   private static void drawItem(ImDrawList dl, CooldownTray.Slot slot, float x, float y, int icon, float s, float a) {
      if (slot.item != null) {
         ItemIcons.draw(dl, slot.item, x, y, (float)icon, a);
      } else {
         HudGlyphs.hourglass(dl, x + icon * 0.5F, y + icon * 0.5F, icon * 0.8F, Math.max(1.0F, 1.3F * s), HudGlyphs.ink(-2513573, a));
      }
   }

   private void drawCaption(ImDrawList dl, CooldownTray.Slot slot, float centerX, float top, ImFont label, int labelPx, float s, float a, boolean guard) {
      if (slot.done) {
         float h = labelPx * 0.85F;
         HudGlyphs.ankh(dl, centerX, top + labelPx * 0.5F, h, Math.max(1.0F, 1.2F * s), HudGlyphs.ink(-871878, a * Math.max(slot.ready, 0.4F)));
      } else {
         String value = seconds(slot.remaining / this.tickRate);
         float vw = HudChrome.tabularWidth(label, labelPx, value);
         HudChrome.tabular(dl, label, labelPx, Math.round(centerX - vw * 0.5F), top, HudChrome.scaleAlpha(HudChrome.TEXT_LABEL, a), value, guard);
      }
   }

   private static float fraction(CooldownTray.Slot slot) {
      return slot.total <= 0.0F ? 0.0F : Math.max(0.0F, Math.min(1.0F, slot.remaining / slot.total));
   }

   private static String seconds(float seconds) {
      if (seconds < 9.95F) {
         int tenths = Math.max(1, Math.min(99, (int)Math.ceil(seconds * 10.0F)));
         String cached = TENTHS[tenths];
         if (cached == null) {
            cached = tenths / 10 + "." + tenths % 10 + "s";
            TENTHS[tenths] = cached;
         }

         return cached;
      } else {
         int whole = (int)Math.ceil(seconds);
         if (whole < 100) {
            String cached = SECONDS[whole];
            if (cached == null) {
               cached = whole + "s";
               SECONDS[whole] = cached;
            }

            return cached;
         } else {
            int minutes = Math.min(99, (int)Math.ceil(seconds / 60.0F));
            String cached = MINUTES[minutes];
            if (cached == null) {
               cached = minutes + "m";
               MINUTES[minutes] = cached;
            }

            return cached;
         }
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class Slot {
      Identifier group;
      Item item;
      float lookupAt;
      float remaining;
      float total;
      float presence;
      float ready;
      boolean live;
      boolean done;
   }
}
