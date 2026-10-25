package net.spectre.client.module.impl.movement;

import net.spectre.client.event.Stage;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.ModeSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class AutoJumpModule extends ClientModule {
   private final ModeSetting mode = this.addSetting("mode", new ModeSetting("Mode", "Jump", "Jump", "LowHop"));
   private final ModeSetting jumpIf = this.addSetting("jump-if", new ModeSetting("Jump If", "Always", "Sprinting", "Walking", "Always"));
   private final NumberSetting velocityHeight = this.addSetting("velocity-height", new NumberSetting("Velocity Height", 0.25F, 0.0F, 2.0F).step(0.05F));

   public AutoJumpModule() {
      super("auto-jump", "Auto Jump", "Automatically jumps.", ModuleCategory.MOVEMENT);
   }

   @Override
   protected void onEnable() {
      this.on(TickEvent.TYPE, Stage.PRE, this::onPreTick);
   }

   private void onPreTick(TickEvent event) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         if (player.onGround() && !player.isShiftKeyDown() && this.shouldJump(player)) {
            if (this.mode.is("Jump")) {
               player.jumpFromGround();
            } else {
               Vec3 velocity = player.getDeltaMovement();
               player.setDeltaMovement(velocity.x, this.velocityHeight.get(), velocity.z);
            }
         }
      }
   }

   private boolean shouldJump(LocalPlayer player) {
      boolean moving = player.zza != 0.0F || player.xxa != 0.0F;
      String var3 = this.jumpIf.get();

      return switch (var3) {
         case "Sprinting" -> player.isSprinting() && moving;
         case "Walking" -> moving;
         default -> true;
      };
   }
}
