package net.spectre.client.module.impl.movement;

import net.spectre.client.event.Stage;
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
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

@Environment(EnvType.CLIENT)
public final class BoatNoclipModule extends ClientModule {
   public static volatile BoatNoclipModule active;
   private final NumberSetting speed = this.addSetting("speed", new NumberSetting("Speed", 10.0F, 0.0F, 50.0F).step(0.5F));
   private final NumberSetting speedInsideBlocks = this.addSetting(
      "speed-inside-blocks", new NumberSetting("Speed Inside Blocks", 5.0F, 0.0F, 50.0F).step(0.5F)
   );
   private final NumberSetting verticalSpeed = this.addSetting("vertical-speed", new NumberSetting("Vertical Speed", 6.0F, 0.0F, 20.0F).step(0.5F));
   private final NumberSetting fallSpeed = this.addSetting("fall-speed", new NumberSetting("Fall Speed", 0.0F, 0.0F, 10.0F).step(0.1F));
   private final BooleanSetting antiKick = this.addSetting("anti-fly-kick", new BooleanSetting("Anti Fly Kick", true));
   private final NumberSetting delay = this.addSetting("delay", new NumberSetting("Delay", 40.0F, 1.0F, 80.0F).step(1.0F));
   private final VehicleAntiKick antiKicker = new VehicleAntiKick();
   private AbstractBoat noclipBoat;

   public BoatNoclipModule() {
      super("boat-noclip", "Boat Noclip", "Fly your boat through blocks and entities.", ModuleCategory.MOVEMENT);
      this.delay.visibleWhen(this.antiKick::get);
      this.antiKick.onChange(this.antiKicker::forget);
   }

   @Override
   public boolean persistEnabled() {
      return false;
   }

   @Override
   protected void onEnable() {
      BoatFlyModule fly = ModuleRegistry.get(BoatFlyModule.class);
      if (fly != null) {
         fly.setEnabled(false);
      }

      this.antiKicker.start(this.delay.getInt());
      active = this;
      EntityControlVehicleHook.refresh();
      this.on(TickEvent.TYPE, Stage.PRE, this::onPreTick);
      this.on(PacketSendEvent.TYPE, this::onPacketSend);
   }

   @Override
   protected void onDisable() {
      active = null;
      EntityControlVehicleHook.refresh();
      this.antiKicker.stop();
      this.releaseBoat();
   }

   private void onPreTick(TickEvent event) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (this.noclipBoat != null && (player == null || player.getVehicle() != this.noclipBoat)) {
         this.releaseBoat();
      }

      this.antiKicker.tick();
   }

   private void onPacketSend(PacketSendEvent event) {
      if (this.antiKick.get() && event.packet instanceof ServerboundMoveVehiclePacket packet) {
         this.antiKicker.onPacketSend(packet, this.delay.getInt());
      }
   }

   public void onVehicleSetback() {
      this.antiKicker.forget();
   }

   private void releaseBoat() {
      if (this.noclipBoat != null) {
         this.noclipBoat.noPhysics = false;
      }

      this.noclipBoat = null;
   }

   public Vec3 boatMovement(AbstractBoat boat, LocalPlayer player) {
      if (this.noclipBoat != boat) {
         this.releaseBoat();
         this.noclipBoat = boat;
      }

      boat.noPhysics = true;
      double bps = isInsideBlock(boat, player) ? this.speedInsideBlocks.get() : this.speed.get();
      Vec3 horizontal = EntityControlVehicleHook.horizontalVelocity(player, bps);
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

      return new Vec3(horizontal.x, velY, horizontal.z);
   }

   private static boolean isInsideBlock(AbstractBoat boat, LocalPlayer player) {
      ClientLevel level = Minecraft.getInstance().level;
      if (level == null) {
         return false;
      } else {
         AABB boatBox = boat.getBoundingBox();
         AABB box = boatBox.contract(0.0, 0.05, 0.0).inflate(0.5, 0.0, 0.5);
         int maxX = Mth.floor(box.maxX);
         int maxY = Mth.floor(box.maxY);
         int maxZ = Mth.floor(box.maxZ);
         MutableBlockPos pos = new MutableBlockPos();

         for (int x = Mth.floor(box.minX); x <= maxX; x++) {
            for (int y = Mth.floor(box.minY); y <= maxY; y++) {
               for (int z = Mth.floor(box.minZ); z <= maxZ; z++) {
                  BlockState state = level.getBlockState(pos.set(x, y, z));
                  if (!state.isAir()) {
                     VoxelShape shape = state.getCollisionShape(level, pos, CollisionContext.empty());
                     if (!shape.isEmpty() && boatBox.intersects(shape.bounds().move(pos))) {
                        return true;
                     }
                  }
               }
            }
         }

         for (Entity other : level.getEntities(boat, box)) {
            if (other != player && !other.hasPassenger(boat) && !boat.hasPassenger(other) && other.isAlive() && other.getBoundingBox().intersects(box)) {
               return true;
            }
         }

         return false;
      }
   }
}
