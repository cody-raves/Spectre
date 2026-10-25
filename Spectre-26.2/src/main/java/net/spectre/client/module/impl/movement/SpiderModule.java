package net.spectre.client.module.impl.movement;

import net.spectre.client.event.Stage;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class SpiderModule extends ClientModule {
   private static final double CLIMB_VELOCITY_THRESHOLD = 0.2;
   private final NumberSetting climbSpeed = this.addSetting("climb-speed", new NumberSetting("Climb Speed", 0.2F, 0.0F, 10.0F).step(0.1F));

   public SpiderModule() {
      super("spider", "Spider", "Allows you to climb walls like a spider.", ModuleCategory.MOVEMENT);
   }

   @Override
   protected void onEnable() {
      this.on(TickEvent.TYPE, Stage.POST, this::onPostTick);
   }

   private void onPostTick(TickEvent event) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null && player.horizontalCollision) {
         Vec3 velocity = player.getDeltaMovement();
         if (!(velocity.y >= 0.2)) {
            player.setDeltaMovement(velocity.x, this.climbSpeed.get(), velocity.z);
         }
      }
   }
}
