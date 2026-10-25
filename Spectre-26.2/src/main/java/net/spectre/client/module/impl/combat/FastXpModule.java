package net.spectre.client.module.impl.combat;

import net.spectre.client.event.EventHandler;
import net.spectre.client.event.impl.Render3DEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public final class FastXpModule extends ClientModule {
   private long lastThrowTime;

   public FastXpModule() {
      super("fastxp", "FastXP", "Throws experience bottles every 100 ms while physical right-click is held.", ModuleCategory.COMBAT);
   }

   @Override
   protected void onEnable() {
      this.lastThrowTime = 0L;
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.gameMode != null && mc.gui.screen() == null) {
         if (mc.player.getMainHandItem().is(Items.EXPERIENCE_BOTTLE)) {
            if (GLFW.glfwGetMouseButton(mc.getWindow().handle(), 1) == 1) {
               long now = System.currentTimeMillis();
               if (now - this.lastThrowTime >= 100L) {
                  mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                  this.lastThrowTime = now;
               }
            }
         }
      }
   }
}
