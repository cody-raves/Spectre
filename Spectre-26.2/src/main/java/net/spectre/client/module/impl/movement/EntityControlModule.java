package net.spectre.client.module.impl.movement;

import net.spectre.client.event.impl.PacketReceiveNettyEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.impl.movement.entitycontrol.EntityControlVehicleHook;
import net.spectre.client.module.setting.BooleanSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;

@Environment(EnvType.CLIENT)
public final class EntityControlModule extends ClientModule {
   public static volatile EntityControlModule active;
   private final BooleanSetting boats = this.addSetting("boats", new BooleanSetting("Boats", true));
   private final BooleanSetting horses = this.addSetting("horses", new BooleanSetting("Horses", true));
   private final BooleanSetting camels = this.addSetting("camels", new BooleanSetting("Camels", true));
   private final BooleanSetting pigs = this.addSetting("pigs", new BooleanSetting("Pigs", true));
   private final BooleanSetting striders = this.addSetting("striders", new BooleanSetting("Striders", true));
   private final BooleanSetting nautiluses = this.addSetting("nautiluses", new BooleanSetting("Nautiluses", true));
   private final BooleanSetting happyGhasts = this.addSetting("happy-ghasts", new BooleanSetting("Happy Ghasts", true));
   private final BooleanSetting spoofSaddle = this.addSetting("spoof-saddle", new BooleanSetting("Spoof Saddle", false));
   private final BooleanSetting maxJump = this.addSetting("max-jump", new BooleanSetting("Max Jump", true));
   private final BooleanSetting lockYaw = this.addSetting("lock-yaw", new BooleanSetting("Lock Yaw", true));
   private final BooleanSetting cancelServerPackets = this.addSetting("cancel-server-packets", new BooleanSetting("Cancel Server Packets", false));

   public EntityControlModule() {
      super("entity-control", "Entity Control", "Lets you control rideable entities without a saddle.", ModuleCategory.MOVEMENT);
   }

   @Override
   protected void onEnable() {
      active = this;
      EntityControlVehicleHook.refresh();
      this.on(PacketReceiveNettyEvent.TYPE, this::onPacketReceive);
   }

   @Override
   protected void onDisable() {
      active = null;
      EntityControlVehicleHook.refresh();
   }

   private void onPacketReceive(PacketReceiveNettyEvent event) {
      if (this.cancelServerPackets.get() && event.packet instanceof ClientboundMoveVehiclePacket) {
         event.setCancelled(true);
      }
   }

   public static boolean spoofSaddle() {
      EntityControlModule module = active;
      return module != null && module.spoofSaddle.get();
   }

   public static boolean maxJump() {
      EntityControlModule module = active;
      return module != null && module.maxJump.get();
   }

   public boolean locksYaw(Entity vehicle) {
      return this.lockYaw.get() && this.isTarget(vehicle);
   }

   private boolean isTarget(Entity entity) {
      if (entity instanceof AbstractBoat) {
         return this.boats.get();
      } else {
         EntityType<?> type = entity.getType();
         if (type == net.minecraft.world.entity.EntityTypes.HORSE
            || type == net.minecraft.world.entity.EntityTypes.DONKEY
            || type == net.minecraft.world.entity.EntityTypes.MULE
            || type == net.minecraft.world.entity.EntityTypes.SKELETON_HORSE
            || type == net.minecraft.world.entity.EntityTypes.ZOMBIE_HORSE) {
            return this.horses.get();
         } else if (type == net.minecraft.world.entity.EntityTypes.CAMEL || type == net.minecraft.world.entity.EntityTypes.CAMEL_HUSK) {
            return this.camels.get();
         } else if (type == net.minecraft.world.entity.EntityTypes.PIG) {
            return this.pigs.get();
         } else if (type == net.minecraft.world.entity.EntityTypes.STRIDER) {
            return this.striders.get();
         } else if (type == net.minecraft.world.entity.EntityTypes.NAUTILUS || type == net.minecraft.world.entity.EntityTypes.ZOMBIE_NAUTILUS) {
            return this.nautiluses.get();
         } else {
            return type == net.minecraft.world.entity.EntityTypes.HAPPY_GHAST ? this.happyGhasts.get() : false;
         }
      }
   }
}
