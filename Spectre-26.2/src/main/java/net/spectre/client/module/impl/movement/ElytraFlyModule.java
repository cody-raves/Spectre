package net.spectre.client.module.impl.movement;

import com.example.client.mixin.AccessorElytraFlyBlockBehaviour;
import net.spectre.client.event.EventPriority;
import net.spectre.client.event.ListenerHandle;
import net.spectre.client.event.Stage;
import net.spectre.client.event.impl.PacketReceiveNettyEvent;
import net.spectre.client.event.impl.PacketSendEvent;
import net.spectre.client.event.impl.PlayerMoveEvent;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.ModuleRegistry;
import net.spectre.client.module.impl.misc.FreecamModule;
import net.spectre.client.module.impl.movement.elytrafly.BounceMode;
import net.spectre.client.module.impl.movement.elytrafly.ElytraFlightMode;
import net.spectre.client.module.impl.movement.elytrafly.ElytraFlightModes;
import net.spectre.client.module.impl.movement.elytrafly.ElytraFlyInventory;
import net.spectre.client.module.impl.movement.elytrafly.PacketMode;
import net.spectre.client.module.impl.movement.elytrafly.Pitch40Mode;
import net.spectre.client.module.impl.movement.elytrafly.VanillaMode;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.ModeSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.StatusOnly;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

@Environment(EnvType.CLIENT)
public final class ElytraFlyModule extends ClientModule {
   public static volatile ElytraFlyModule active;
   private static final float ELYTRA_MAX_REPLACE_DURABILITY = 431.0F;
   public final ModeSetting flightMode = this.addSetting("mode", new ModeSetting("Mode", "Vanilla", "Vanilla", "Packet", "Pitch40", "Bounce"));
   public final BooleanSetting autoTakeOff = this.addSetting("auto-take-off", new BooleanSetting("Auto Take Off", false));
   public final NumberSetting fallMultiplier = this.addSetting("fall-multiplier", new NumberSetting("Fall Multiplier", 0.01F, 0.0F, 2.0F).step(0.01F));
   public final NumberSetting horizontalSpeed = this.addSetting("horizontal-speed", new NumberSetting("Horizontal Speed", 1.0F, 0.0F, 10.0F).step(0.1F));
   public final NumberSetting verticalSpeed = this.addSetting("vertical-speed", new NumberSetting("Vertical Speed", 1.0F, 0.0F, 10.0F).step(0.1F));
   public final BooleanSetting acceleration = this.addSetting("acceleration", new BooleanSetting("Acceleration", false));
   public final NumberSetting accelerationStep = this.addSetting("acceleration-step", new NumberSetting("Acceleration Step", 1.0F, 0.1F, 5.0F).step(0.1F));
   public final NumberSetting accelerationMin = this.addSetting("acceleration-start", new NumberSetting("Acceleration Start", 0.0F, 0.0F, 10.0F).step(0.1F));
   public final BooleanSetting stopInWater = this.addSetting("stop-in-water", new BooleanSetting("Stop In Water", true));
   public final BooleanSetting dontGoIntoUnloadedChunks = this.addSetting("no-unloaded-chunks", new BooleanSetting("No Unloaded Chunks", true));
   public final BooleanSetting autoHover = this.addSetting("auto-hover", new BooleanSetting("Auto Hover", false));
   public final BooleanSetting noCrash = this.addSetting("no-crash", new BooleanSetting("No Crash", false));
   public final NumberSetting crashLookAhead = this.addSetting("crash-look-ahead", new NumberSetting("Crash Look Ahead", 5.0F, 1.0F, 15.0F).step(1.0F));
   private final BooleanSetting instaDrop = this.addSetting("insta-drop", new BooleanSetting("Insta Drop", false));
   public final NumberSetting pitch40lowerBounds = this.addSetting(
      "pitch40-lower-bounds", new NumberSetting("Pitch40 Lower Bounds", 180.0F, -128.0F, 360.0F).step(1.0F)
   );
   public final NumberSetting pitch40upperBounds = this.addSetting(
      "pitch40-upper-bounds", new NumberSetting("Pitch40 Upper Bounds", 220.0F, -128.0F, 360.0F).step(1.0F)
   );
   public final NumberSetting pitch40rotationSpeedUp = this.addSetting(
      "pitch40-rotate-speed-up", new NumberSetting("Pitch40 Rotate Speed Up", 5.45F, 1.0F, 20.0F).step(0.05F)
   );
   public final NumberSetting pitch40rotationSpeedDown = this.addSetting(
      "pitch40-rotate-speed-down", new NumberSetting("Pitch40 Rotate Speed Down", 0.9F, 0.5F, 2.0F).step(0.05F)
   );
   public final BooleanSetting autoJump = this.addSetting("auto-jump", new BooleanSetting("Auto Jump", true));
   public final ModeSetting yawLockMode = this.addSetting("yaw-lock", new ModeSetting("Yaw Lock", "Smart", "Smart", "Simple", "None"));
   public final NumberSetting yaw = this.addSetting("yaw", new NumberSetting("Yaw", 0.0F, 0.0F, 360.0F).step(1.0F));
   public final BooleanSetting lockPitch = this.addSetting("pitch-lock", new BooleanSetting("Pitch Lock", true));
   public final NumberSetting pitch = this.addSetting("pitch", new NumberSetting("Pitch", 85.0F, 0.0F, 90.0F).step(1.0F));
   public final BooleanSetting restart = this.addSetting("restart", new BooleanSetting("Restart", true));
   public final NumberSetting restartDelay = this.addSetting("restart-delay", new NumberSetting("Restart Delay", 7.0F, 0.0F, 20.0F).step(1.0F));
   public final BooleanSetting sprint = this.addSetting("sprint-constantly", new BooleanSetting("Sprint Constantly", true));
   public final BooleanSetting manualTakeoff = this.addSetting("manual-takeoff", new BooleanSetting("Manual Takeoff", false));
   public final BooleanSetting replace = this.addSetting("elytra-replace", new BooleanSetting("Elytra Replace", false));
   public final NumberSetting replaceDurability = this.addSetting("replace-durability", new NumberSetting("Replace Durability", 2.0F, 1.0F, 431.0F).step(1.0F));
   public final ModeSetting chestSwap = this.addSetting("chest-swap", new ModeSetting("Chest Swap", "Never", "Always", "Never", "WaitForGround"));
   public final BooleanSetting autoReplenish = this.addSetting("replenish-fireworks", new BooleanSetting("Replenish Fireworks", false));
   public final NumberSetting replenishSlot = this.addSetting("replenish-slot", new NumberSetting("Replenish Slot", 9.0F, 1.0F, 9.0F).step(1.0F));
   public final BooleanSetting autoPilot = this.addSetting("auto-pilot", new BooleanSetting("Auto Pilot", false));
   public final BooleanSetting useFireworks = this.addSetting("use-fireworks", new BooleanSetting("Use Fireworks", false));
   public final NumberSetting autoPilotFireworkDelay = this.addSetting("firework-delay", new NumberSetting("Firework Delay", 8.0F, 1.0F, 20.0F).step(0.5F));
   public final NumberSetting autoPilotMinimumHeight = this.addSetting(
      "minimum-height", new NumberSetting("Minimum Height", 120.0F, -128.0F, 260.0F).step(1.0F)
   );
   private ElytraFlightModes modeType = ElytraFlightModes.Vanilla;
   private ElytraFlightMode currentMode;
   private boolean modeRequestedDisable;
   private volatile boolean positionPacketReceived;
   private boolean moveIsVelocity;
   private LocalPlayer recastPlayer;
   private boolean previousGliding;
   private ListenerHandle<PlayerMoveEvent> groundListener;
   private ListenerHandle<TickEvent> instaDropListener;

   public ElytraFlyModule() {
      super("elytra-fly", "Elytra Fly", "Gives you more control over your elytra.", ModuleCategory.MOVEMENT);
      this.usesNotifications();
      this.flightMode.onChange(this::onModeChanged);
      this.autoTakeOff.visibleWhen(this::steerableMode);
      this.fallMultiplier.visibleWhen(this::steerableMode);
      this.horizontalSpeed.visibleWhen(this::steerableMode);
      this.verticalSpeed.visibleWhen(this::steerableMode);
      this.acceleration.visibleWhen(this::steerableMode);
      this.accelerationStep.visibleWhen(() -> this.steerableMode() && this.acceleration.get());
      this.accelerationMin.visibleWhen(() -> this.steerableMode() && this.acceleration.get());
      this.stopInWater.visibleWhen(() -> !this.flightMode.is("Bounce"));
      this.autoHover.visibleWhen(() -> !this.flightMode.is("Bounce"));
      this.noCrash.visibleWhen(() -> !this.flightMode.is("Bounce"));
      this.crashLookAhead.visibleWhen(() -> this.noCrash.get() && !this.flightMode.is("Bounce"));
      this.instaDrop.visibleWhen(() -> !this.flightMode.is("Bounce"));
      this.pitch40lowerBounds.visibleWhen(() -> this.flightMode.is("Pitch40"));
      this.pitch40upperBounds.visibleWhen(() -> this.flightMode.is("Pitch40"));
      this.pitch40rotationSpeedUp.visibleWhen(() -> this.flightMode.is("Pitch40"));
      this.pitch40rotationSpeedDown.visibleWhen(() -> this.flightMode.is("Pitch40"));
      this.autoJump.visibleWhen(() -> this.flightMode.is("Bounce"));
      this.yawLockMode.visibleWhen(() -> this.flightMode.is("Bounce"));
      this.yaw.visibleWhen(() -> this.flightMode.is("Bounce") && this.yawLockMode.is("Simple"));
      this.lockPitch.visibleWhen(() -> this.flightMode.is("Bounce"));
      this.pitch.visibleWhen(() -> this.flightMode.is("Bounce") && this.lockPitch.get());
      this.restart.visibleWhen(() -> this.flightMode.is("Bounce"));
      this.restartDelay.visibleWhen(() -> this.flightMode.is("Bounce") && this.restart.get());
      this.sprint.visibleWhen(() -> this.flightMode.is("Bounce"));
      this.manualTakeoff.visibleWhen(() -> this.flightMode.is("Bounce"));
      this.replaceDurability.visibleWhen(this.replace::get);
      this.replenishSlot.visibleWhen(this.autoReplenish::get);
      this.autoPilot.visibleWhen(this::steerableMode);
      this.useFireworks.visibleWhen(() -> this.autoPilot.get() && this.steerableMode());
      this.autoPilotFireworkDelay.visibleWhen(() -> this.useFireworks.get() && this.steerableMode());
      this.autoPilotMinimumHeight.visibleWhen(() -> this.autoPilot.get() && this.steerableMode());
   }

   @Override
   public boolean persistEnabled() {
      return false;
   }

   private boolean steerableMode() {
      return !this.flightMode.is("Pitch40") && !this.flightMode.is("Bounce");
   }

   public ElytraFlightModes flightModeType() {
      return this.modeType;
   }

   @Override
   protected void onEnable() {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player == null) {
         this.requestDisable();
      } else {
         this.cancelGroundListener();
         this.cancelInstaDropListener();
         this.modeRequestedDisable = false;
         this.positionPacketReceived = false;
         this.moveIsVelocity = false;
         this.recastPlayer = player;
         this.previousGliding = player.isFallFlying();
         this.modeType = ElytraFlightModes.valueOf(this.flightMode.get());
         this.currentMode = this.createMode(this.modeType);
         this.currentMode.onActivate();
         if (!this.modeRequestedDisable) {
            if ((this.chestSwap.is("Always") || this.chestSwap.is("WaitForGround")) && player.getItemBySlot(EquipmentSlot.CHEST).getItem() != Items.ELYTRA) {
               ElytraFlyInventory.chestSwap();
            }

            active = this;
            this.on(PlayerMoveEvent.TYPE, Stage.PRE, EventPriority.HIGHEST, this::captureMoveAliasing);
            this.on(PlayerMoveEvent.TYPE, this::onPlayerMove);
            this.on(TickEvent.TYPE, Stage.PRE, this::onPreTick);
            this.on(TickEvent.TYPE, Stage.POST, this::onPostTick);
            this.on(PacketSendEvent.TYPE, this::onPacketSend);
            this.on(PacketReceiveNettyEvent.TYPE, this::onPacketReceive);
         }
      }
   }

   @Override
   protected void onDisable() {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      ElytraFlightMode mode = this.currentMode;
      this.currentMode = null;

      try {
         if (player != null && mode != null) {
            if (this.autoPilot.get()) {
               mc.options.keyUp.setDown(false);
            }

            if (this.chestSwap.is("Always") && player.getItemBySlot(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
               ElytraFlyInventory.chestSwap();
            } else if (this.chestSwap.is("WaitForGround")) {
               this.enableGroundListener();
            }

            if (player.isFallFlying() && this.instaDrop.get()) {
               this.enableInstaDropListener();
            }
         }

         if (mode != null) {
            mode.onDeactivate();
         }
      } finally {
         active = null;
         this.recastPlayer = null;
      }
   }

   private void onModeChanged() {
      this.modeType = ElytraFlightModes.valueOf(this.flightMode.get());
      if (this.isEnabled() && this.currentMode != null) {
         this.currentMode = this.createMode(this.modeType);
      }

      if (this.modeType == ElytraFlightModes.Pitch40) {
         this.autoPilot.set(false);
      }
   }

   private ElytraFlightMode createMode(ElytraFlightModes type) {
      return (ElytraFlightMode)(switch (type) {
         case Vanilla -> new VanillaMode(this);
         case Packet -> new PacketMode(this);
         case Pitch40 -> new Pitch40Mode(this);
         case Bounce -> new BounceMode(this);
      });
   }

   public void disableFromMode() {
      this.modeRequestedDisable = true;
      this.requestDisable();
   }

   private void onPreTick(TickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.level != null) {
         ElytraFlightMode mode = this.currentMode;
         if (mode != null) {
            if (this.positionPacketReceived) {
               this.positionPacketReceived = false;
               mode.zeroAcceleration();
               mode.onPositionPacket();
            }

            mode.onPreTick();
         }
      } else {
         this.requestDisable();
      }
   }

   private void onPostTick(TickEvent event) {
      ElytraFlightMode mode = this.currentMode;
      if (Minecraft.getInstance().player != null && mode != null) {
         mode.onTick();
      }
   }

   private void onPacketSend(PacketSendEvent event) {
      Minecraft mc = Minecraft.getInstance();
      ElytraFlightMode mode = this.currentMode;
      if (mc.isSameThread() && mc.player != null && mode != null) {
         mode.onPacketSend(event.packet);
      }
   }

   private void onPacketReceive(PacketReceiveNettyEvent event) {
      if (event.packet instanceof ClientboundPlayerPositionPacket) {
         this.positionPacketReceived = true;
      }
   }

   private void captureMoveAliasing(PlayerMoveEvent event) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player == null) {
         this.moveIsVelocity = false;
      } else {
         Vec3 velocity = player.getDeltaMovement();
         this.moveIsVelocity = velocity.x == event.x && velocity.y == event.y && velocity.z == event.z;
      }
   }

   private void onPlayerMove(PlayerMoveEvent event) {
      boolean aliased = this.moveIsVelocity;
      this.moveIsVelocity = false;
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      ElytraFlightMode mode = this.currentMode;
      if (player != null && mc.level != null && mode != null) {
         if (player.getItemBySlot(EquipmentSlot.CHEST).has(DataComponents.GLIDER)) {
            mode.autoTakeoff();
            boolean bounce = this.modeType == ElytraFlightModes.Bounce;
            if (player.isFallFlying()) {
               if (!bounce) {
                  mode.velX = 0.0;
                  mode.velY = event.y;
                  mode.velZ = 0.0;
                  mode.forward = Vec3.directionFromRotation(0.0F, player.getYRot()).scale(0.1);
                  mode.right = Vec3.directionFromRotation(0.0F, player.getYRot() + 90.0F).scale(0.1);
                  if (player.isInWater() && this.stopInWater.get()) {
                     player.connection.send(new ServerboundPlayerCommandPacket(player, Action.START_FALL_FLYING));
                     return;
                  }

                  mode.handleFallMultiplier();
                  mode.handleAutopilot();
                  mode.handleAcceleration();
                  mode.handleHorizontalSpeed(event);
                  mode.handleVerticalSpeed(event);
               }

               int chunkX = (int)((player.getX() + mode.velX) / 16.0);
               int chunkZ = (int)((player.getZ() + mode.velZ) / 16.0);
               if (this.dontGoIntoUnloadedChunks.get()) {
                  if (mc.level.getChunkSource().hasChunk(chunkX, chunkZ)) {
                     if (!bounce) {
                        setMovement(player, aliased, event, mode.velX, mode.velY, mode.velZ);
                     }
                  } else {
                     mode.zeroAcceleration();
                     setMovement(player, aliased, event, 0.0, mode.velY, 0.0);
                  }
               } else if (!bounce) {
                  setMovement(player, aliased, event, mode.velX, mode.velY, mode.velZ);
               }

               if (!bounce) {
                  mode.onPlayerMove();
               }
            } else if (mode.lastForwardPressed && !bounce) {
               mc.options.keyUp.setDown(false);
               mode.lastForwardPressed = false;
            }

            if (this.noCrash.get() && player.isFallFlying() && !bounce) {
               Vec3 position = player.position();
               Vec3 lookAheadPos = position.add(player.getDeltaMovement().normalize().scale(this.crashLookAhead.getInt()));
               ClipContext context = new ClipContext(position, new Vec3(lookAheadPos.x, player.getY(), lookAheadPos.z), Block.COLLIDER, Fluid.NONE, player);
               BlockHitResult hitResult = mc.level.clip(context);
               if (hitResult != null && hitResult.getType() == Type.BLOCK) {
                  setMovement(player, aliased, event, 0.0, mode.velY, 0.0);
               }
            }

            if (this.autoHover.get() && player.input.keyPresses.shift() && !isFreecamActive() && player.isFallFlying() && !bounce) {
               BlockPos below = player.blockPosition().below();
               BlockState underState = mc.level.getBlockState(below);
               BlockState under2State = mc.level.getBlockState(below.below());
               boolean underCollidable = ((AccessorElytraFlyBlockBehaviour)underState.getBlock()).spectre$elytraFlyHasCollision()
                  || !underState.getFluidState().isEmpty();
               boolean under2Collidable = ((AccessorElytraFlyBlockBehaviour)under2State.getBlock()).spectre$elytraFlyHasCollision()
                  || !under2State.getFluidState().isEmpty();
               if (!underCollidable && under2Collidable) {
                  setMovement(player, aliased, event, event.x, -0.1F, event.z);
                  player.setXRot(Mth.clamp(player.getXRot(0.0F), -50.0F, 20.0F));
               }

               if (underCollidable) {
                  setMovement(player, aliased, event, event.x, -0.03F, event.z);
                  player.setXRot(Mth.clamp(player.getXRot(0.0F), -50.0F, 20.0F));
                  if (player.position().y <= below.getY() + 1.34F) {
                     setMovement(player, aliased, event, event.x, 0.0, event.z);
                     player.setShiftKeyDown(false);
                  }
               }
            }
         }
      }
   }

   private static void setMovement(LocalPlayer player, boolean aliased, PlayerMoveEvent event, double x, double y, double z) {
      event.x = x;
      event.y = y;
      event.z = z;
      if (aliased) {
         player.setDeltaMovement(x, y, z);
      }
   }

   public boolean canPacketEfly(LocalPlayer player) {
      return this.modeType == ElytraFlightModes.Packet && player.getItemBySlot(EquipmentSlot.CHEST).has(DataComponents.GLIDER) && !player.onGround();
   }

   public boolean modifyFallFlying(LocalPlayer player, boolean original) {
      boolean gliding = original || this.canPacketEfly(player);
      if (player != this.recastPlayer) {
         this.recastPlayer = player;
         this.previousGliding = false;
      }

      boolean wasGliding = this.previousGliding;
      this.previousGliding = gliding;
      return wasGliding && !gliding && this.modeType == ElytraFlightModes.Bounce ? BounceMode.recastElytra(player) : gliding;
   }

   private void enableGroundListener() {
      if (this.groundListener == null) {
         this.groundListener = PlayerMoveEvent.TYPE.listen(this, Stage.PRE, EventPriority.NORMAL, event -> {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null && player.onGround() && player.getItemBySlot(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
               ElytraFlyInventory.chestSwap();
               this.cancelGroundListener();
            }
         });
      }
   }

   private void cancelGroundListener() {
      if (this.groundListener != null) {
         this.groundListener.unregister();
         this.groundListener = null;
      }
   }

   private void enableInstaDropListener() {
      if (this.instaDropListener == null) {
         this.instaDropListener = TickEvent.TYPE.listen(this, Stage.POST, EventPriority.NORMAL, event -> {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null && player.isFallFlying()) {
               player.setDeltaMovement(0.0, 0.0, 0.0);
               player.connection.send(new StatusOnly(true, player.horizontalCollision));
            } else {
               this.cancelInstaDropListener();
            }
         });
      }
   }

   private void cancelInstaDropListener() {
      if (this.instaDropListener != null) {
         this.instaDropListener.unregister();
         this.instaDropListener = null;
      }
   }

   private static boolean isFreecamActive() {
      FreecamModule freecam = ModuleRegistry.get(FreecamModule.class);
      return freecam != null && freecam.isEnabled();
   }
}
