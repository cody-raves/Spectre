package net.spectre.client.module.impl.movement.elytrafly;

import net.spectre.client.module.impl.movement.ElytraFlyModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.StatusOnly;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class PacketMode extends ElytraFlightMode {
   public PacketMode(ElytraFlyModule elytraFly) {
      super(elytraFly);
   }

   @Override
   public void onDeactivate() {
      LocalPlayer player = this.mc.player;
      if (player != null) {
         Abilities abilities = player.getAbilities();
         abilities.flying = false;
         if (!abilities.instabuild && !player.isSpectator()) {
            abilities.mayfly = false;
         }
      }
   }

   @Override
   public void onTick() {
      super.onTick();
      LocalPlayer player = this.mc.player;
      if (player != null) {
         if (player.getItemBySlot(EquipmentSlot.CHEST).getItem() == Items.ELYTRA && !(player.fallDistance <= 0.2) && !this.mc.options.keyShift.isDown()) {
            player.setDeltaMovement(Vec3.ZERO);
            player.connection.send(new ServerboundPlayerCommandPacket(player, Action.START_FALL_FLYING));
            player.connection.send(new StatusOnly(true, player.horizontalCollision));
         }
      }
   }

   @Override
   public void onPacketSend(Packet<?> packet) {
      if (packet instanceof ServerboundMovePlayerPacket) {
         LocalPlayer player = this.mc.player;
         player.connection.send(new ServerboundPlayerCommandPacket(player, Action.START_FALL_FLYING));
      }
   }

   @Override
   public void onPlayerMove() {
      Abilities abilities = this.mc.player.getAbilities();
      abilities.flying = true;
      abilities.setFlyingSpeed(this.elytraFly.horizontalSpeed.get() / 20.0F);
   }
}
