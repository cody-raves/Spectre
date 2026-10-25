package net.spectre.client.module.impl.render.character;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.spectre.client.module.impl.render.character.kit.Bone;
import net.spectre.client.module.impl.render.character.kit.CharacterModel;
import net.spectre.client.module.impl.render.character.kit.Motion;
import net.spectre.client.module.impl.render.character.kit.PartMesh;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;

@Environment(EnvType.CLIENT)
final class CharacterMotion {
   private static final long FORGET_NANOS = 20000000000L;
   private static final Int2ObjectOpenHashMap<CharacterMotion.State> STATES = new Int2ObjectOpenHashMap();
   private static final Motion.Input INPUT = new Motion.Input();
   private static final float[] TARGET = new float[3];
   private static long prunedAt;

   private CharacterMotion() {
   }

   static float[] update(AvatarRenderState state, CharacterModel model, PlayerModel pm, float[] pitch) {
      if (model.motionSlots == 0) {
         return null;
      } else {
         long now = System.nanoTime();
         prune(now);
         CharacterMotion.State st = (CharacterMotion.State)STATES.get(state.id);
         if (st == null || st.model != model) {
            st = new CharacterMotion.State(model);
            st.bodyRot = state.bodyRot;
            STATES.put(state.id, st);
         }

         float dt = st.nanos == 0L ? 0.0F : (float)(now - st.nanos) / 1.0E9F;
         if (st.nanos != 0L && dt < 5.0E-4F) {
            return st.angles;
         } else {
            st.nanos = now;
            float turn = Mth.wrapDegrees(state.bodyRot - st.bodyRot);
            st.bodyRot = state.bodyRot;
            if (dt > 0.0F && dt < 0.25F) {
               st.turnRate = st.turnRate + (turn / dt - st.turnRate) * Math.min(1.0F, dt * 12.0F);
            } else {
               st.turnRate = 0.0F;
            }

            Motion.Input in = INPUT;
            in.time = state.ageInTicks / 20.0F;
            in.walkPos = state.walkAnimationPos;
            in.walkSpeed = state.walkAnimationSpeed;
            in.turnRate = st.turnRate;
            bone(in, Bone.HEAD, pm.head, pitch);
            bone(in, Bone.BODY, pm.body, pitch);
            bone(in, Bone.RIGHT_ARM, pm.rightArm, pitch);
            bone(in, Bone.LEFT_ARM, pm.leftArm, pitch);
            bone(in, Bone.RIGHT_LEG, pm.rightLeg, pitch);
            bone(in, Bone.LEFT_LEG, pm.leftLeg, pitch);

            for (PartMesh part : model.parts) {
               if (part.motionSlot >= 0 && part.motion != null) {
                  part.motion.target(in, part.bone, TARGET);
                  part.motion.step(st.spring, part.motionSlot * 6, TARGET, dt);
                  System.arraycopy(st.spring, part.motionSlot * 6, st.angles, part.motionSlot * 3, 3);
               }
            }

            return st.angles;
         }
      }
   }

   private static void bone(Motion.Input in, Bone bone, ModelPart part, float[] pitch) {
      int b = bone.ordinal();
      in.pitch[b] = pitch[b];
      in.yaw[b] = -part.yRot;
      in.roll[b] = -part.zRot;
   }

   private static void prune(long now) {
      if (now - prunedAt >= 5000000000L) {
         prunedAt = now;
         STATES.values().removeIf(st -> now - st.nanos > 20000000000L);
      }
   }

   static void clear() {
      STATES.clear();
   }

   @Environment(EnvType.CLIENT)
   private static final class State {
      final CharacterModel model;
      final float[] spring;
      final float[] angles;
      float bodyRot;
      float turnRate;
      long nanos;

      State(CharacterModel model) {
         this.model = model;
         this.spring = new float[model.motionSlots * 6];
         this.angles = new float[model.motionSlots * 3];

         for (int i = 0; i < model.motionSlots; i++) {
            this.spring[i * 6] = Float.NaN;
         }
      }
   }
}
