package net.spectre.client.module.impl.movement;

import net.spectre.client.event.Stage;
import net.spectre.client.event.impl.PacketReceiveNettyEvent;
import net.spectre.client.event.impl.PacketSendEvent;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.ModuleRegistry;
import net.spectre.client.module.impl.movement.entitycontrol.EntityControlVehicleHook;
import net.spectre.client.module.impl.movement.entitycontrol.VehicleAntiKick;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.world.entity.PlayerRideableJumping;

@Environment(EnvType.CLIENT)
public final class BoatFlyModule extends ClientModule {
   public static volatile BoatFlyModule active;
   private final NumberSetting speed = this.addSetting("speed", new NumberSetting("Speed", 10.0F, 0.0F, 50.0F).step(0.5F));
   private final NumberSetting verticalSpeed = this.addSetting("vertical-speed", new NumberSetting("Vertical Speed", 6.0F, 0.0F, 20.0F).step(0.5F));
   private final NumberSetting fallSpeed = this.addSetting("fall-speed", new NumberSetting("Fall Speed", 0.0F, 0.0F, 10.0F).step(0.1F));
   private final BooleanSetting antiKick = this.addSetting("anti-fly-kick", new BooleanSetting("Anti Fly Kick", true));
   private final NumberSetting delay = this.addSetting("delay", new NumberSetting("Delay", 40.0F, 1.0F, 80.0F).step(1.0F));
   private final BooleanSetting cancelServerPackets = this.addSetting("cancel-server-packets", new BooleanSetting("Cancel Server Packets", false));
   private final VehicleAntiKick antiKicker = new VehicleAntiKick();

   public BoatFlyModule() {
      super("boat-fly", "Boat Fly", "Transforms your boat (or any ridden vehicle) into a plane.", ModuleCategory.MOVEMENT);
      this.delay.visibleWhen(this.antiKick::get);
      this.antiKick.onChange(this.antiKicker::forget);
   }

   @Override
   public boolean persistEnabled() {
      return false;
   }

   @Override
   protected void onEnable() {
      BoatNoclipModule noclip = ModuleRegistry.get(BoatNoclipModule.class);
      if (noclip != null) {
         noclip.setEnabled(false);
      }

      this.antiKicker.start(this.delay.getInt());
      active = this;
      EntityControlVehicleHook.refresh();
      this.on(TickEvent.TYPE, Stage.PRE, event -> this.antiKicker.tick());
      this.on(PacketSendEvent.TYPE, this::onPacketSend);
      this.on(PacketReceiveNettyEvent.TYPE, this::onPacketReceive);
   }

   @Override
   protected void onDisable() {
      active = null;
      EntityControlVehicleHook.refresh();
      this.antiKicker.stop();
   }

   private void onPacketSend(PacketSendEvent event) {
      if (this.antiKick.get() && event.packet instanceof ServerboundMoveVehiclePacket packet) {
         this.antiKicker.onPacketSend(packet, this.delay.getInt());
      }
   }

   private void onPacketReceive(PacketReceiveNettyEvent event) {
      if (this.cancelServerPackets.get() && event.packet instanceof ClientboundMoveVehiclePacket) {
         event.setCancelled(true);
      }
   }

   public void onVehicleSetback() {
      this.antiKicker.forget();
   }

   public double horizontalSpeed() {
      return this.speed.get();
   }

   public double verticalVelocity() {
      Minecraft mc = Minecraft.getInstance();
      double velY = 0.0;
      if (EntityControlVehicleHook.isPhysicallyDown(mc.options.keyJump)) {
         velY += this.verticalSpeed.get() / 20.0;
      }

      if (EntityControlVehicleHook.isPhysicallyDown(mc.options.keySprint)) {
         velY -= this.verticalSpeed.get() / 20.0;
      } else {
         velY -= this.fallSpeed.get() / 20.0;
      }

      return velY;
   }

   public static boolean cancelsMountJump(LocalPlayer player) {
      return active != null && player.getVehicle() instanceof PlayerRideableJumping;
   }
}
