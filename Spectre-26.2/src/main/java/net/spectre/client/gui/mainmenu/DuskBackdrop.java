package net.spectre.client.gui.mainmenu;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public final class DuskBackdrop {
   public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("spectre", "textures/gui/spectre_backdrop.png");

   private DuskBackdrop() {
   }

   public static void draw(GuiGraphicsExtractor graphics, int width, int height) {
      if (width > 0 && height > 0) {
         float aspect = 16f / 9f, screen = (float)width / height;
         float u = screen < aspect ? (1-screen/aspect)*.5f : 0;
         float v = screen > aspect ? (1-aspect/screen)*.5f : 0;
         graphics.blit(TEXTURE,0,0,width,height,u,1-u,v,1-v);
      }
   }
}
