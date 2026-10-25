package net.spectre.client.module.impl.render.character;

import net.spectre.client.module.impl.render.character.kit.Bone;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.HumanoidModel.ArmPose;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.SwingAnimationType;

@Environment(EnvType.CLIENT)
final class CharacterJoints {
   static final float CROUCH_HIP = 0.5F;
   static final float SADDLE_KNEE = 1.35F;
   static final float SEAT_KNEE = 0.3F;
   private static final float CROUCH_KNEE = 0.62F;
   private static final float CROUCH_ELBOW = 0.2F;
   private static final float MAX_KNEE = 1.7F;
   private static final float MAX_ELBOW = 1.6F;

   private CharacterJoints() {
   }

   static float angle(AvatarRenderState state, Bone bone, float swing) {
      float fade = !state.isFallFlying && !state.isAutoSpinAttack ? 1.0F - state.swimAmount * 0.8F : 0.0F;
      if (fade <= 0.0F) {
         return 0.0F;
      } else if (bone.leg()) {
         return knee(state, bone) * fade;
      } else {
         return bone.arm() ? -elbow(state, bone, swing) * fade : 0.0F;
      }
   }

   private static float knee(AvatarRenderState state, Bone bone) {
      if (state.isPassenger) {
         return CharacterModule.seat(state);
      } else {
         float speed = Math.min(1.0F, state.walkAnimationSpeed / state.speedValue);
         float phase = state.walkAnimationPos * 0.6662F + (bone == Bone.LEFT_LEG ? (float) Math.PI : 0.0F);
         float flex = speed * (0.1F + 0.85F * Math.max(0.0F, Mth.sin(phase)));
         if (state.isCrouching) {
            flex += 0.62F;
         }

         return Math.min(1.7F, flex);
      }
   }

   private static float elbow(AvatarRenderState state, Bone bone, float swing) {
      boolean right = bone == Bone.RIGHT_ARM;
      ArmPose pose = right ? state.rightArmPose : state.leftArmPose;
      ArmPose other = right ? state.leftArmPose : state.rightArmPose;
      if (other.isTwoHanded()) {
         return 0.0F;
      } else {
         float speed = Math.min(1.0F, state.walkAnimationSpeed / state.speedValue);

         float flex = switch (pose) {
            case EMPTY -> 0.16F + 0.45F * Math.max(0.0F, -swing) + 0.3F * speed;
            case ITEM -> 0.3F + 0.2F * speed;
            case BLOCK -> 0.55F;
            case BRUSH -> 0.4F;
            default -> 0.0F;
         };
         if (flex <= 0.0F) {
            return 0.0F;
         } else {
            if (state.isCrouching) {
               flex += 0.2F;
            }

            HumanoidArm arm = right ? HumanoidArm.RIGHT : HumanoidArm.LEFT;
            if (state.attackTime > 0.0F && state.attackArm == arm && state.swingAnimationType == SwingAnimationType.WHACK) {
               flex += 0.6F * Mth.sin(state.attackTime * (float) Math.PI);
            }

            return Math.min(1.6F, flex);
         }
      }
   }
}
