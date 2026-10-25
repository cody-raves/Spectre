package net.spectre.client.gui.mainmenu;

import com.mojang.realmsclient.RealmsMainScreen;
import imgui.ImDrawList;
import java.util.function.Supplier;
import net.spectre.client.gui.ImGuiScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.util.Util;

@Environment(EnvType.CLIENT)
public final class MenuContext extends MenuFrame {
   private static final float LOOK_EASE = 2.5F;
   private final Screen screen;
   private final Runnable openAccounts;
   private final float[] hover = new float[MenuAction.values().length];
   private float lookX;
   private float lookY;

   public MenuContext(Screen screen, Runnable openAccounts) {
      this.screen = screen;
      this.openAccounts = openAccounts;
   }

   @Override
   public void begin(ImDrawList bg, ImDrawList fg, float width, float height, float scale, float time, float dt, float mouseX, float mouseY, boolean clicked) {
      super.begin(bg, fg, width, height, scale, time, dt, mouseX, mouseY, clicked);
      boolean onScreen = mouseX >= 0.0F && mouseY >= 0.0F && mouseX <= width && mouseY <= height;
      float targetX = onScreen ? mouseX / width * 2.0F - 1.0F : 0.0F;
      float targetY = onScreen ? mouseY / height * 2.0F - 1.0F : 0.0F;
      float k = Math.min(1.0F, dt * 2.5F);
      this.lookX = this.lookX + (targetX - this.lookX) * k;
      this.lookY = this.lookY + (targetY - this.lookY) * k;
   }

   public float lookX() {
      return this.lookX;
   }

   public float lookY() {
      return this.lookY;
   }

   public boolean enabled(MenuAction action) {
      return action != MenuAction.MULTIPLAYER && action != MenuAction.REALMS || Minecraft.getInstance().allowsMultiplayer();
   }

   public float control(MenuAction action, float x, float y, float w, float h) {
      boolean over = this.inside(x, y, w, h);
      if (!this.enabled(action)) {
         if (over) {
            this.tooltip("Multiplayer is disabled", this.mouseX(), y + h + this.px(6.0F));
         }

         return this.hover(action, false);
      } else {
         if (over && this.clicked()) {
            this.activate(action);
         }

         return this.hover(action, over);
      }
   }

   public float hover(MenuAction action, boolean target) {
      float k = this.hover[action.ordinal()];
      k += ((target ? 1.0F : 0.0F) - k) * Math.min(1.0F, this.dt() * 12.0F);
      this.hover[action.ordinal()] = k;
      return k;
   }

   public void activate(MenuAction action) {
      if (this.enabled(action)) {
         Minecraft mc = Minecraft.getInstance();

         Supplier<Screen> next = switch (action) {
            case SINGLEPLAYER -> () -> new SelectWorldScreen(this.screen);
            case MULTIPLAYER -> () -> (Screen)(mc.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(this.screen) : new SafetyScreen(this.screen));
            case REALMS -> () -> new RealmsMainScreen(this.screen);
            case OPTIONS -> () -> new OptionsScreen(this.screen, mc.options, false);
            case LANGUAGE -> () -> new LanguageSelectScreen(this.screen, mc.options, mc.getLanguageManager());
            case ACCESSIBILITY -> () -> new AccessibilityOptionsScreen(this.screen, mc.options);
            case SPECTRE -> ImGuiScreen::new;
            case QUIT, MODS, ACCOUNTS -> null;
         };
         if (action == MenuAction.QUIT) {
            mc.execute(mc::stop);
         } else if (action == MenuAction.ACCOUNTS) {
            this.openAccounts.run();
         } else if (action == MenuAction.MODS) {
            mc.execute(() -> Util.getPlatform().openPath(FabricLoader.getInstance().getGameDir().resolve("mods")));
         } else if (next != null) {
            mc.schedule(() -> mc.gui.setScreen(next.get()));
         }
      }
   }
}
