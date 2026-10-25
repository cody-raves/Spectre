package net.spectre.client.module.impl.combat;

import java.util.Comparator;
import java.util.Random;
import java.util.UUID;
import net.spectre.client.event.EventHandler;
import net.spectre.client.event.impl.PlayerAttackEntityEvent;
import net.spectre.client.event.impl.Render3DEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.ModeSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public final class AimAssistModule extends ClientModule {
   private final ModeSetting targetMode = this.addSetting("target-mode", new ModeSetting("Target Mode", "FOV", "FOV", "Closest"));
   private final NumberSetting targetRange = this.addSetting("range", new NumberSetting("Range", 3.2F, 0.0F, 10.0F).step(0.1F));
   private final NumberSetting fov = this.addSetting("fov", new NumberSetting("FOV", 90.0F, 0.0F, 360.0F).step(1.0F));
   private final NumberSetting speed = this.addSetting("speed", new NumberSetting("Speed", 0.18F, 0.01F, 1.0F).step(0.01F));
   private final ModeSetting part = this.addSetting("aim-part", new ModeSetting("Aim Part", "Torso", "Head", "Torso", "Legs"));
   private final BooleanSetting throughWalls = this.addSetting("through-walls", new BooleanSetting("Through Walls", false));
   private final BooleanSetting sword = this.addSetting("sword", new BooleanSetting("Sword", true));
   private final BooleanSetting axe = this.addSetting("axe", new BooleanSetting("Axe", true));
   private final BooleanSetting mace = this.addSetting("mace", new BooleanSetting("Mace", true));
   private final BooleanSetting trident = this.addSetting("spear-trident", new BooleanSetting("Spear/Trident", true));
   private final BooleanSetting everything = this.addSetting("everything", new BooleanSetting("Everything", false));
   private final Random random = new Random();
   private UUID targetUuid;
   private Player currentTarget;

   public AimAssistModule() {
      super("aim-assist", "Aim Assist", "Smooth combat aim assist with FOV, range, part, and weapon filters.", ModuleCategory.COMBAT);
   }

   @Override
   protected void onDisable() {
      this.reset();
   }

   @EventHandler
   public void onAttack(PlayerAttackEntityEvent event) {
      if (event.target instanceof Player player) {
         this.targetUuid = player.getUUID();
         this.currentTarget = player;
      }
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.level != null && mc.gui.screen() == null) {
         boolean rightClickHeld = GLFW.glfwGetMouseButton(mc.getWindow().handle(), 1) == 1;
         if (!mc.player.isUsingItem() && !rightClickHeld && this.isHoldingValidWeapon(mc)) {
            if (this.currentTarget != null
               && (
                  !this.currentTarget.isAlive()
                     || mc.player.distanceTo(this.currentTarget) > this.targetRange.get() + 1.0F
                     || this.get3DAngleDifference(mc, this.currentTarget) > this.fov.get() / 2.0F
               )) {
               this.reset();
            }

            if (this.currentTarget == null && this.targetUuid != null) {
               this.currentTarget = mc.level.players().stream().filter(player -> this.targetUuid.equals(player.getUUID())).findFirst().orElse(null);
            }

            if (this.currentTarget == null) {
               this.currentTarget = mc.level
                  .players()
                  .stream()
                  .filter(player -> player != mc.player && player.isAlive())
                  .filter(player -> mc.player.distanceTo(player) <= this.targetRange.get())
                  .filter(player -> this.get3DAngleDifference(mc, player) <= this.fov.get() / 2.0F)
                  .min(Comparator.comparingDouble(player -> this.targetMode.is("FOV") ? this.get3DAngleDifference(mc, player) : mc.player.distanceTo(player)))
                  .orElse(null);
               if (this.currentTarget != null) {
                  this.targetUuid = this.currentTarget.getUUID();
               }
            }

            if (this.currentTarget != null) {
               if (this.throughWalls.get() || mc.player.hasLineOfSight(this.currentTarget)) {
                  AimAssistModule.Rotation target = this.rotationTo(mc, this.currentTarget);
                  float targetYaw = target.yaw;
                  float targetPitch = target.pitch;
                  double time = System.currentTimeMillis() / 1000.0;
                  targetYaw += (float)(Math.sin(time * 1.4) * 0.7 + Math.cos(time * 2.1) * 0.3);
                  targetPitch += (float)(Math.cos(time * 1.1) * 0.5 + Math.sin(time * 2.5) * 0.2);
                  targetYaw += (this.random.nextFloat() - 0.5F) * 0.12F;
                  targetPitch += (this.random.nextFloat() - 0.5F) * 0.12F;
                  float diffYaw = Mth.wrapDegrees(targetYaw - mc.player.getYRot());
                  float diffPitch = Mth.wrapDegrees(targetPitch - mc.player.getXRot());
                  float distance = Mth.sqrt(diffYaw * diffYaw + diffPitch * diffPitch);
                  float baseSpeed = this.speed.get() * 0.18F;
                  float curve = (float)Math.pow(Math.min(distance / 12.0F, 1.0F), 2.2);
                  float randomSpeedScale = 0.8F + this.random.nextFloat() * 0.4F;
                  float speedFactor = distance < 0.45F ? 0.0F : baseSpeed * curve * randomSpeedScale;
                  float yawStep = this.quantizeToMouseGcd(mc, diffYaw * speedFactor);
                  float pitchStep = this.quantizeToMouseGcd(mc, diffPitch * speedFactor);
                  if (yawStep != 0.0F || pitchStep != 0.0F) {
                     mc.player.setYRot(mc.player.getYRot() + yawStep);
                     mc.player.setXRot(Mth.clamp(mc.player.getXRot() + pitchStep, -90.0F, 90.0F));
                  }
               }
            }
         } else {
            this.reset();
         }
      }
   }

   private void reset() {
      this.targetUuid = null;
      this.currentTarget = null;
   }

   private float get3DAngleDifference(Minecraft mc, Entity entity) {
      AimAssistModule.Rotation target = this.rotationTo(mc, entity);
      float diffYaw = Math.abs(Mth.wrapDegrees(target.yaw - mc.player.getYRot()));
      float diffPitch = Math.abs(Mth.wrapDegrees(target.pitch - mc.player.getXRot()));
      return Mth.sqrt(diffYaw * diffYaw + diffPitch * diffPitch);
   }

   private AimAssistModule.Rotation rotationTo(Minecraft mc, Entity entity) {
      double deltaX = entity.getX() - mc.player.getX();
      double deltaY = entity.getY() + this.targetOffset() - (mc.player.getY() + mc.player.getEyeHeight());
      double deltaZ = entity.getZ() - mc.player.getZ();
      double distanceXZ = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
      float yaw = (float)Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0F;
      float pitch = (float)(-Math.toDegrees(Math.atan2(deltaY, distanceXZ)));
      return new AimAssistModule.Rotation(yaw, pitch);
   }

   private double targetOffset() {
      String var1 = this.part.get();

      return switch (var1) {
         case "Head" -> 1.5;
         case "Legs" -> 0.4;
         default -> 1.0;
      };
   }

   private boolean isHoldingValidWeapon(Minecraft mc) {
      if (this.everything.get()) {
         return true;
      } else {
         ItemStack held = mc.player.getMainHandItem();
         if (held.isEmpty()) {
            return false;
         } else {
            String name = held.getItem().toString().toLowerCase();
            return this.sword.get() && name.contains("sword")
               || this.axe.get() && name.contains("axe")
               || this.mace.get() && name.contains("mace")
               || this.trident.get() && (name.contains("trident") || name.contains("spear"));
         }
      }
   }

   private float quantizeToMouseGcd(Minecraft mc, float value) {
      float sensitivity = mc.options.sensitivity().get().floatValue();
      float f = sensitivity * 0.6F + 0.2F;
      float gcd = f * f * f * 8.0F * 0.15F;
      return Math.round(value / gcd) * gcd;
   }

   @Environment(EnvType.CLIENT)
   private record Rotation(float yaw, float pitch) {
   }
}
