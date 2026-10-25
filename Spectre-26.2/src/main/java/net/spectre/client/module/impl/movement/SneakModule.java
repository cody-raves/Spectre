package net.spectre.client.module.impl.movement;

import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.ModeSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

@Environment(EnvType.CLIENT)
public final class SneakModule extends ClientModule {
   public static volatile SneakModule active;
   private final ModeSetting mode = this.addSetting("mode", new ModeSetting("Mode", "Vanilla", "Packet", "Vanilla"));

   public SneakModule() {
      super("sneak", "Sneak", "Sneaks for you.", ModuleCategory.MOVEMENT);
   }

   @Override
   protected void onEnable() {
      active = this;
   }

   @Override
   protected void onDisable() {
      active = null;
   }

   public boolean doPacket() {
      return this.isEnabled() && this.mode.is("Packet") && !isFlying();
   }

   public boolean doVanilla() {
      return this.isEnabled() && this.mode.is("Vanilla") && !isFlying();
   }

   private static boolean isFlying() {
      LocalPlayer player = Minecraft.getInstance().player;
      return player == null || player.getAbilities().flying;
   }
}
