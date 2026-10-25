package net.spectre.client.module.impl.client;

import imgui.ImDrawList;
import imgui.ImFont;
import java.util.Locale;
import net.spectre.client.esp.ChunkScanner;
import net.spectre.client.event.impl.Render3DEvent;
import net.spectre.client.gui.SpectreWidgets;
import net.spectre.client.imgui.ImGuiManager;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.HudOverlayProvider;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.ModuleRegistry;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class PerformanceModule extends ClientModule implements HudOverlayProvider {
   private final BooleanSetting frustumCulling = this.addSetting("frustum-culling", new BooleanSetting("Frustum Culling", true));
   private final NumberSetting scanBudget = this.addSetting("scan-budget-ms", new NumberSetting("Scan Budget ms", 2.0F, 0.5F, 8.0F).step(0.5F));
   private long smoothedNanos;

   public PerformanceModule() {
      super("performance", "Performance", "Render performance settings.", ModuleCategory.CLIENT);
   }

   public static boolean frustumCulling() {
      PerformanceModule module = ModuleRegistry.get(PerformanceModule.class);
      return module == null || module.frustumCulling.get();
   }

   public static long scanBudgetNanos() {
      PerformanceModule module = ModuleRegistry.get(PerformanceModule.class);
      return module == null ? 2000000L : (long)(module.scanBudget.get() * 1000000.0F);
   }

   @Override
   public boolean isHudOverlayEnabled() {
      return this.isEnabled();
   }

   @Override
   public void renderHudOverlay(ImDrawList dl, float screenW, float screenH) {
      this.smoothedNanos = this.smoothedNanos + (Render3DEvent.lastDurationNanos() - this.smoothedNanos) / 8L;
      String text = String.format(Locale.ROOT, "3D listeners %.2f ms  ·  scan queue %d", this.smoothedNanos / 1000000.0, ChunkScanner.pending());
      ImFont font = ImGuiManager.getInstance().hudLabelFont();
      float x = screenW - SpectreWidgets.textWidth(font, text) - 8.0F;
      float y = screenH - SpectreWidgets.lineHeight(font) - 6.0F;
      SpectreWidgets.text(dl, font, x + 1.0F, y + 1.0F, -1610612736, text);
      SpectreWidgets.text(dl, font, x, y, -3618616, text);
   }
}
