package net.spectre.client.module.impl.combat;

import net.spectre.client.event.EventHandler;
import net.spectre.client.event.Stage;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.CombatMacroLock;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.impl.combat.melee.MeleeAttacks;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.spectre.client.util.pvp.Rotation;
import net.spectre.client.util.pvp.RotationUtils;
import net.spectre.client.util.pvp.ServerRotation;
import net.spectre.client.util.pvp.TargetingUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class KillAuraModule extends ClientModule {
   private static final float FOOT_REACH = 3.0F;
   private static final int CRIT_HOLD_TICKS = 8;
   private final NumberSetting vehicleRange = this.addSetting("vehicle-range", new NumberSetting("Vehicle Range", 6.0F, 3.0F, 8.0F));
   private final BooleanSetting players = this.addSetting("players", new BooleanSetting("Players", true));
   private final BooleanSetting hostiles = this.addSetting("hostiles", new BooleanSetting("Hostiles", false));
   private final BooleanSetting skipFriends = this.addSetting("skip-friends", new BooleanSetting("Skip Friends", true));
   private final BooleanSetting waitCharge = this.addSetting("wait-charge", new BooleanSetting("Wait Charge", true));
   private int critWait;
   private boolean droppedSprintKey;

   public KillAuraModule() {
      super("kill-aura", "Kill Aura", "Attacks nearby targets without moving your camera.", ModuleCategory.COMBAT);
   }

   @Override
   protected void onEnable() {
      this.on(TickEvent.TYPE, Stage.POST, this::restoreSprintKey);
   }

   @Override
   protected void onDisable() {
      this.restoreSprintKey(null);
      this.critWait = 0;
   }

   @EventHandler
   public void onTick(TickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (MeleeAttacks.canAct(mc) && !player.isDeadOrDying() && !player.isUsingItem() && !player.isHandsBusy()) {
         if (!CombatMacroLock.heldByOther(this) && !MeleeAttacks.attackedThisTick(player)) {
            if (!rowing(player)) {
               boolean riding = player.getVehicle() != null;
               float reach = riding ? this.vehicleRange.get() : 3.0F;
               LivingEntity target = this.nearest(mc, player, reach);
               if (target == null) {
                  this.critWait = 0;
               } else {
                  Vec3 eye = player.getEyePosition();
                  AABB raw = target.getBoundingBox();
                  double inset = Math.min(0.03, raw.getXsize() * 0.15);
                  AABB box = raw.inflate(-inset, 0.0, -inset);
                  if (!riding && ServerRotation.yawFree(player)) {
                     ServerRotation.request(RotationUtils.fromTo(eye, TargetingUtils.closestPoint(eye, box)));
                  }

                  if (this.waitCharge.get() && MeleeAttacks.charge(player) <= 0.9F) {
                     this.critWait = 0;
                  } else {
                     Rotation look = riding ? RotationUtils.entityRotation(player) : ServerRotation.sent();
                     if (look != null) {
                        Vec3 direction = Vec3.directionFromRotation(look.pitch(), look.yaw());
                        Vec3 at = box.clip(eye, eye.add(direction.scale(reach))).orElse(box.contains(eye) ? eye : null);
                        if (at != null && !(eye.distanceTo(at) > reach) && MeleeAttacks.lineIsClear(player, eye, at)) {
                           if (riding || !this.waitCharge.get() || !holdForCrit(player) || this.critWait++ >= 8) {
                              if (!riding && falling(player)) {
                                 this.releaseSprint(mc, player);
                              }

                              if (riding) {
                                 mc.gameMode.attack(player, target);
                                 player.swing(InteractionHand.MAIN_HAND);
                              } else {
                                 MeleeAttacks.attack(mc, new EntityHitResult(target, at));
                              }

                              this.critWait = 0;
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static boolean holdForCrit(LocalPlayer player) {
      if (player.getAbilities().flying
         || player.isFallFlying()
         || player.isInWater()
         || player.onClimbable()
         || player.isPassenger()
         || player.isMobilityRestricted()) {
         return false;
      } else {
         return !player.onGround() && player.fallDistance <= 0.0 ? true : player.onGround() && player.input.keyPresses.jump();
      }
   }

   private static boolean falling(LocalPlayer player) {
      return player.fallDistance > 0.0
         && !player.onGround()
         && !player.onClimbable()
         && !player.isInWater()
         && !player.isMobilityRestricted()
         && !player.isPassenger()
         && !player.getAbilities().flying
         && !player.isFallFlying();
   }

   private void releaseSprint(Minecraft mc, LocalPlayer player) {
      if (player.isSprinting()) {
         ClientPacketListener connection = mc.getConnection();
         if (connection != null) {
            connection.send(new ServerboundPlayerCommandPacket(player, Action.STOP_SPRINTING));
            player.setSprinting(false);
            if (mc.options.keySprint.isDown()) {
               mc.options.keySprint.setDown(false);
               this.droppedSprintKey = true;
            }
         }
      }
   }

   private void restoreSprintKey(TickEvent event) {
      if (this.droppedSprintKey) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.options != null) {
            mc.options.keySprint.setDown(true);
         }

         this.droppedSprintKey = false;
      }
   }

   private LivingEntity nearest(Minecraft mc, LocalPlayer player, float reach) {
      LivingEntity best = null;
      double bestDistance = Double.MAX_VALUE;

      for (Entity entity : mc.level.entitiesForRendering()) {
         if (entity instanceof LivingEntity living && this.wanted(living)) {
            double distance = player.getEyePosition().distanceTo(TargetingUtils.closestPoint(player.getEyePosition(), living.getBoundingBox()));
            if (!(distance > reach) && !(distance >= bestDistance)) {
               best = living;
               bestDistance = distance;
            }
         }
      }

      return best;
   }

   private boolean wanted(LivingEntity living) {
      if (!TargetingUtils.isValidTarget(Minecraft.getInstance().player, living, false)) {
         return false;
      } else {
         return living instanceof Player
            ? this.players.get() && (!this.skipFriends.get() || !MeleeAttacks.isFriend(living))
            : this.hostiles.get() && living instanceof Enemy;
      }
   }

   private static boolean rowing(LocalPlayer player) {
      return player.getVehicle() instanceof AbstractBoat boat && (boat.getPaddleState(0) || boat.getPaddleState(1));
   }
}
