package net.spectre.client.module.impl.movement;

import com.example.client.mixin.AccessorLocalPlayer;
import com.example.client.mixin.AccessorServerboundMovePlayerPacket;
import net.spectre.client.event.Stage;
import net.spectre.client.event.impl.PacketReceiveNettyEvent;
import net.spectre.client.event.impl.PacketSendEvent;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.ModuleRegistry;
import net.spectre.client.module.impl.misc.FreecamModule;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.ModeSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class FlyModule extends ClientModule {
   public static volatile FlyModule active;
   private static final String ABILITIES = "Abilities";
   private static final String VELOCITY = "Velocity";
   private static final String KICK_NORMAL = "Normal";
   private static final String KICK_PACKET = "Packet";
   private static final String KICK_NONE = "None";
   private static final double ANTI_KICK_DROP = 0.0313;
   private static final float VANILLA_FLYING_SPEED = 0.05F;
   private static final String DEFAULT_MODE = "Abilities";
   private static final float DEFAULT_SPEED = 0.1F;
   private final ModeSetting mode = this.addSetting("fly-mode", new ModeSetting("Mode", "Abilities", "Abilities", "Velocity"));
   private final NumberSetting speed = this.addSetting("fly-speed", new NumberSetting("Speed", 0.1F, 0.0F, 10.0F).step(0.05F));
   private final BooleanSetting verticalSpeedMatch = this.addSetting("vertical-speed-match", new BooleanSetting("Vertical Speed Match", false));
   private final BooleanSetting noSneak = this.addSetting("no-sneak", new BooleanSetting("No Sneak", false));
   private final ModeSetting antiKickMode = this.addSetting("anti-kick-mode", new ModeSetting("Anti Kick Mode", "Packet", "Normal", "Packet", "None"));
   private final NumberSetting delay = this.addSetting("delay", new NumberSetting("Anti Kick Delay", 20.0F, 1.0F, 200.0F).step(1.0F));
   private final NumberSetting offTime = this.addSetting("off-time", new NumberSetting("Anti Kick Off Time", 1.0F, 1.0F, 20.0F).step(1.0F));
   private int delayLeft = 20;
   private int offLeft = 1;
   private boolean flip;
   private float lastYaw;
   private double lastPacketY = Double.MAX_VALUE;
   private LocalPlayer trackedPlayer;

   public FlyModule() {
      super("fly", "Fly", "Lets you fly.", ModuleCategory.MOVEMENT);
      this.noSneak.visibleWhen(() -> this.mode.is("Velocity"));
      this.mode.onChange(() -> {
         if (this.isEnabled()) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
               abilitiesOff(player);
            }
         }
      });
   }

   @Override
   public boolean persistEnabled() {
      return false;
   }

   @Override
   protected void onEnable() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player == null) {
         this.requestDisable();
      } else {
         this.delayLeft = this.delay.getInt();
         this.offLeft = this.offTime.getInt();
         this.resetPlayerState(player);
         active = this;
         if (this.mode.is("Abilities") && !player.isSpectator()) {
            Abilities abilities = player.getAbilities();
            abilities.flying = true;
            if (!abilities.instabuild) {
               abilities.mayfly = true;
            }
         }

         this.on(TickEvent.TYPE, Stage.PRE, this::onPreTick);
         this.on(TickEvent.TYPE, Stage.POST, this::onPostTick);
         this.on(PacketSendEvent.TYPE, this::onPacketSend);
         this.on(PacketReceiveNettyEvent.TYPE, this::onPacketReceiveNetty);
      }
   }

   @Override
   protected void onDisable() {
      active = null;
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null && this.mode.is("Abilities")) {
         abilitiesOff(player);
      }

      this.trackedPlayer = null;
   }

   private void resetPlayerState(LocalPlayer player) {
      this.trackedPlayer = player;
      this.lastYaw = player.getYRot();
      this.lastPacketY = Double.MAX_VALUE;
   }

   private void onPreTick(TickEvent event) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player == null) {
         this.requestDisable();
      } else {
         if (player != this.trackedPlayer) {
            this.resetPlayerState(player);
         }

         float currentYaw = player.getYRot();
         if (player.fallDistance >= 3.0 && currentYaw == this.lastYaw && player.getDeltaMovement().length() < 0.003) {
            player.setYRot(currentYaw + (this.flip ? 1 : -1));
            this.flip = !this.flip;
         }

         this.lastYaw = currentYaw;
      }
   }

   private void onPostTick(TickEvent event) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         if (player != this.trackedPlayer) {
            this.resetPlayerState(player);
         }

         if (this.delayLeft > 0) {
            this.delayLeft--;
         }

         if (this.offLeft <= 0 && this.delayLeft <= 0) {
            this.delayLeft = this.delay.getInt();
            this.offLeft = this.offTime.getInt();
            if (this.antiKickMode.is("Packet")) {
               forcePositionPacket(player);
            }
         } else if (this.delayLeft <= 0) {
            boolean shouldReturn = false;
            if (this.antiKickMode.is("Normal")) {
               if (this.mode.is("Abilities")) {
                  abilitiesOff(player);
                  shouldReturn = true;
               }
            } else if (this.antiKickMode.is("Packet") && this.offLeft == this.offTime.getInt()) {
               forcePositionPacket(player);
            }

            this.offLeft--;
            if (shouldReturn) {
               return;
            }
         }

         if (player.getYRot() != this.lastYaw) {
            player.setYRot(this.lastYaw);
         }

         if (this.mode.is("Velocity")) {
            if (!player.isSpectator()) {
               player.getAbilities().flying = false;
            }

            player.setDeltaMovement(0.0, 0.0, 0.0);
            Vec3 velocity = player.getDeltaMovement();
            if (!isFreecamActive()) {
               Minecraft mc = Minecraft.getInstance();
               double vertical = (double)this.speed.get() * (this.verticalSpeedMatch.get() ? 10.0F : 5.0F);
               if (mc.options.keyJump.isDown()) {
                  velocity = velocity.add(0.0, vertical, 0.0);
               }

               if (mc.options.keyShift.isDown()) {
                  velocity = velocity.subtract(0.0, vertical, 0.0);
               }
            }

            player.setDeltaMovement(velocity);
            if (this.noSneak.get()) {
               player.setOnGround(false);
            }
         } else {
            if (player.isSpectator()) {
               return;
            }

            Abilities abilities = player.getAbilities();
            abilities.setFlyingSpeed(this.speed.get());
            abilities.flying = true;
            if (abilities.instabuild) {
               return;
            }

            abilities.mayfly = true;
         }
      }
   }

   private void onPacketSend(PacketSendEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.isSameThread()) {
         if (event.packet instanceof ServerboundAcceptTeleportationPacket) {
            this.lastPacketY = Double.MAX_VALUE;
         } else if (event.packet instanceof ServerboundMovePlayerPacket packet && this.antiKickMode.is("Packet")) {
            LocalPlayer player = mc.player;
            ClientPacketListener connection = mc.getConnection();
            if (player != null && connection != null) {
               double currentY = packet.getY(Double.MAX_VALUE);
               if (currentY != Double.MAX_VALUE) {
                  this.antiKickPacket(player, packet, currentY);
               } else {
                  ServerboundMovePlayerPacket fullPacket;
                  if (packet.hasRotation()) {
                     fullPacket = new PosRot(
                        player.getX(),
                        player.getY(),
                        player.getZ(),
                        packet.getYRot(0.0F),
                        packet.getXRot(0.0F),
                        packet.isOnGround(),
                        player.horizontalCollision
                     );
                  } else {
                     fullPacket = new Pos(player.getX(), player.getY(), player.getZ(), packet.isOnGround(), player.horizontalCollision);
                  }

                  event.setCancelled(true);
                  this.antiKickPacket(player, fullPacket, player.getY());
                  connection.send(fullPacket);
               }
            }
         }
      }
   }

   private void antiKickPacket(LocalPlayer player, ServerboundMovePlayerPacket packet, double currentY) {
      if (this.delayLeft <= 0 && this.lastPacketY != Double.MAX_VALUE && shouldFlyDown(currentY, this.lastPacketY) && isOnAir(player)) {
         ((AccessorServerboundMovePlayerPacket)packet).spectre$setY(this.lastPacketY - 0.0313);
      } else {
         this.lastPacketY = currentY;
      }
   }

   private void onPacketReceiveNetty(PacketReceiveNettyEvent event) {
      if (event.packet instanceof ClientboundPlayerAbilitiesPacket packet && this.mode.is("Abilities")) {
         event.setCancelled(true);
         Minecraft mc = Minecraft.getInstance();
         mc.execute(() -> {
            LocalPlayer player = mc.player;
            if (player != null) {
               Abilities abilities = player.getAbilities();
               abilities.invulnerable = packet.isInvulnerable();
               abilities.instabuild = packet.canInstabuild();
               abilities.setWalkingSpeed(packet.getWalkingSpeed());
            }
         });
      }
   }

   private static boolean shouldFlyDown(double currentY, double lastY) {
      return currentY >= lastY ? true : lastY - currentY < 0.0313;
   }

   private static void forcePositionPacket(LocalPlayer player) {
      ((AccessorLocalPlayer)player).spectre$setPositionReminder(20);
   }

   private static boolean isOnAir(LocalPlayer player) {
      AABB box = player.getBoundingBox().inflate(0.0625).expandTowards(0.0, -0.55, 0.0);
      return player.level().getBlockStates(box).allMatch(BlockStateBase::isAir);
   }

   private static boolean isFreecamActive() {
      FreecamModule freecam = ModuleRegistry.get(FreecamModule.class);
      return freecam != null && freecam.isEnabled();
   }

   private static void abilitiesOff(LocalPlayer player) {
      if (!player.isSpectator()) {
         Abilities abilities = player.getAbilities();
         abilities.flying = false;
         abilities.setFlyingSpeed(0.05F);
         if (!abilities.instabuild) {
            abilities.mayfly = false;
         }
      }
   }

   public float getOffGroundSpeed(LocalPlayer player) {
      return this.isEnabled() && this.mode.is("Velocity") ? this.speed.get() * (player.isSprinting() ? 15.0F : 10.0F) : -1.0F;
   }

   public boolean noSneak() {
      return this.isEnabled() && this.mode.is("Velocity") && this.noSneak.get();
   }

   public boolean suppressFlightVerticalInput() {
      return this.isEnabled() && isFreecamActive();
   }
}
