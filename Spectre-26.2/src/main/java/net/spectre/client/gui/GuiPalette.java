package net.spectre.client.gui;

import net.spectre.client.config.Surface;
import net.spectre.client.config.ThemeStore;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class GuiPalette {
   private static int accent = -624588;
   private static int accentHover = -486564;
   private static int panelBg = -349691361;
   private static int cardBg = -149022441;
   private static int tabBarBg = -149022441;
   private static float rounding = 10.0F;
   private static float animationSpeed = 1.0F;

   private GuiPalette() {
   }

   public static void refresh() {
      ThemeStore theme = ThemeStore.get();
      accent = abgr(theme.accent.argbWithAlpha(255));
      accentHover = SpectreTheme.lerpColor(accent, -1, 0.2F);
      panelBg = fill(theme.panels);
      cardBg = fill(theme.cards);
      tabBarBg = fill(theme.tabBar);
      rounding = theme.rounding.get();
      animationSpeed = theme.animationSpeed.get();
   }

   public static int accent() {
      return accent;
   }

   public static int accentHover() {
      return accentHover;
   }

   public static int panelBg() {
      return panelBg;
   }

   public static int cardBg() {
      return cardBg;
   }

   public static int tabBarBg() {
      return tabBarBg;
   }

   public static int fill(Surface surface) {
      return abgr(surface.color.argbWithAlpha(Math.round(surface.opacity.get() * 255.0F)));
   }

   public static float rounding() {
      return rounding;
   }

   public static float animationSpeed() {
      return animationSpeed;
   }

   public static int abgr(int argb) {
      return argb & -16711936 | argb >> 16 & 0xFF | (argb & 0xFF) << 16;
   }
}
