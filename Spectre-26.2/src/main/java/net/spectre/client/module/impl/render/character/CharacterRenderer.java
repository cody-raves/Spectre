package net.spectre.client.module.impl.render.character;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import net.spectre.client.module.impl.render.character.kit.Bone;
import net.spectre.client.module.impl.render.character.kit.CharacterModel;
import net.spectre.client.module.impl.render.character.kit.Lights;
import net.spectre.client.module.impl.render.character.kit.PartMesh;
import net.spectre.client.module.impl.render.character.kit.Surface;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.HumanoidModel.ArmPose;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

@Environment(EnvType.CLIENT)
final class CharacterRenderer {
   private static final int GHOST_ALPHA = 38;
   private static final Bone[] BONES = Bone.values();
   private static final Quaternionf ROTATION = new Quaternionf();
   private static final float[] LEVELS = new float[153];
   private static final float[] PITCH = new float[BONES.length];
   private static final float[] JOINT = new float[BONES.length];
   private static final float[] JOINT_LEVELS = new float[153];
   private static float[] scratch = new float[24576];

   private CharacterRenderer() {
   }

   static void submit(WornCharacter worn, PlayerModel pm, PoseStack stack, SubmitNodeCollector collector, int light, AvatarRenderState state) {
      CharacterModel model = worn.model;
      PartMesh[] parts = model.parts;
      boolean ghost = state.isInvisible;
      int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
      float[] pitch = pitches(state, model, pm);
      float[] joint = joints(state, model);
      float lookPitch = pm.head.xRot;
      float lookYaw = -pm.head.yRot;
      float[] angles = CharacterMotion.update(state, model, pm, pitch);
      stack.pushPose();
      pm.root().translateAndRotate(stack);
      lift(stack, state, model);

      for (int i = 0; i < parts.length; i++) {
         PartMesh part = parts[i];
         if (part.base != null || part.outline != null || part.decals != null) {
            boolean bends = part.bends() && angles != null;
            float bendPitch = bends ? angles[part.motionSlot * 3] : 0.0F;
            float yaw = bends ? angles[part.motionSlot * 3 + 1] : 0.0F;
            float roll = bends ? angles[part.motionSlot * 3 + 2] : 0.0F;
            float bent = 0.0F;
            float turn = 0.0F;
            if (part.neck) {
               bent = look(part.lookPitch * lookPitch, part.lookLimit);
               turn = look(part.lookYaw * lookYaw, part.lookLimit);
            } else if (part.jointed) {
               bent = joint[part.bone.ordinal()];
            }

            stack.pushPose();
            chain(stack, pm, model, i, angles, !bends, null, pitch);
            submitPart(worn, part, stack, collector, light, overlay, ghost, bends, bendPitch, yaw, roll, bent, turn);
            stack.popPose();
         }
      }

      stack.popPose();
   }

   static boolean firstPersonHand(PlayerModel pm, PoseStack stack, SubmitNodeCollector collector, int light, ModelPart arm) {
      WornCharacter worn = CharacterModule.firstPerson();
      if (worn == null) {
         return false;
      } else {
         Bone bone = arm == pm.rightArm ? Bone.RIGHT_ARM : Bone.LEFT_ARM;
         CharacterModel model = worn.model;
         PartMesh main = model.part(bone);
         if (main != null && main.base != null) {
            arm.resetPose();
            arm.zRot = bone == Bone.RIGHT_ARM ? 0.1F : -0.1F;
            PartMesh[] parts = model.parts;

            for (int i = 0; i < parts.length; i++) {
               PartMesh part = parts[i];
               if (part.bone == bone && (part.base != null || part.outline != null || part.decals != null)) {
                  stack.pushPose();
                  chain(stack, pm, model, i, null, true, arm, null);
                  submitPart(worn, part, stack, collector, light, OverlayTexture.NO_OVERLAY, false, false, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
                  stack.popPose();
               }
            }

            return true;
         } else {
            return false;
         }
      }
   }

   private static void submitPart(
      WornCharacter worn,
      PartMesh part,
      PoseStack stack,
      SubmitNodeCollector collector,
      int light,
      int overlay,
      boolean ghost,
      boolean bends,
      float pitch,
      float yaw,
      float roll,
      float bent,
      float turn
   ) {
      if (ghost) {
         if (part.base != null) {
            collector.order(1)
               .submitCustomGeometry(
                  stack, worn.ghost, (pose, out) -> emit(pose, out, part.base, light, overlay, 38, bends, pitch, yaw, roll, part, bent, turn)
               );
         }
      } else {
         if (part.base != null || part.outline != null) {
            collector.submitCustomGeometry(stack, worn.solid, (pose, out) -> {
               if (part.base != null) {
                  emit(pose, out, part.base, light, overlay, 255, bends, pitch, yaw, roll, part, bent, turn);
               }

               if (part.outline != null) {
                  emit(pose, out, part.outline, light, overlay, 255, bends, pitch, yaw, roll, part, bent, turn);
               }
            });
         }

         if (part.decals != null) {
            collector.order(1)
               .submitCustomGeometry(
                  stack, worn.decal, (pose, out) -> emit(pose, out, part.decals, light, overlay, 255, bends, pitch, yaw, roll, part, bent, turn)
               );
         }
      }
   }

   static boolean translateToHand(PlayerModel pm, AvatarRenderState state, HumanoidArm arm, PoseStack stack) {
      WornCharacter worn = CharacterModule.worn(state);
      if (worn == null) {
         return false;
      } else {
         CharacterModel model = worn.model;
         Bone bone = arm == HumanoidArm.RIGHT ? Bone.RIGHT_ARM : Bone.LEFT_ARM;
         PartMesh part = model.part(bone);
         if (part == null) {
            return false;
         } else {
            float s = model.scale;
            ModelPart vp = vanilla(pm, bone);
            pm.root().translateAndRotate(stack);
            lift(stack, state, model);
            pivot(stack, vp, part, s, pitch(state, part, vp.xRot));
            float bent = part.jointed ? CharacterJoints.angle(state, bone, walk(state, bone) * weight(state, bone)) : 0.0F;
            if (bent != 0.0F) {
               float jy = part.jointY * s / 16.0F;
               float jz = part.jointZ * s / 16.0F;
               stack.translate(0.0F, -jy, -jz);
               stack.mulPose(ROTATION.rotationX(bent));
               stack.translate(0.0F, jy, jz);
            }

            stack.translate((part.gripX - part.pivotX) * s / 16.0F, -(part.gripY - part.pivotY) * s / 16.0F, -(part.gripZ - part.pivotZ) * s / 16.0F);
            stack.scale(s, s, s);
            stack.translate((arm == HumanoidArm.RIGHT ? 1.0F : -1.0F) / 16.0F, -0.625F, 0.125F);
            return true;
         }
      }
   }

   private static float[] pitches(AvatarRenderState state, CharacterModel model, PlayerModel pm) {
      float[] out = PITCH;

      for (Bone bone : BONES) {
         float xRot = vanilla(pm, bone).xRot;
         PartMesh part = !bone.arm() && !bone.leg() ? null : model.part(bone);
         out[bone.ordinal()] = part == null ? xRot : pitch(state, part, xRot);
      }

      return out;
   }

   private static float pitch(AvatarRenderState state, PartMesh part, float xRot) {
      float out = part.swingPitch(xRot, walk(state, part.bone), weight(state, part.bone));
      if (part.jointed && part.bone.leg() && state.isCrouching && !state.isPassenger) {
         out -= 0.5F * (1.0F - state.swimAmount);
      }

      return out;
   }

   private static float[] joints(AvatarRenderState state, CharacterModel model) {
      float[] out = JOINT;

      for (Bone bone : BONES) {
         PartMesh part = !bone.arm() && !bone.leg() ? null : model.part(bone);
         out[bone.ordinal()] = part != null && part.jointed ? CharacterJoints.angle(state, bone, walk(state, bone) * weight(state, bone)) : 0.0F;
      }

      return out;
   }

   private static float look(float angle, float limitDegrees) {
      float max = limitDegrees * (float) (Math.PI / 180.0);
      return Mth.clamp(angle, -max, max);
   }

   private static float walk(AvatarRenderState state, Bone bone) {
      float phase = state.walkAnimationPos * 0.6662F;
      float speed = state.walkAnimationSpeed / state.speedValue;

      return switch (bone) {
         case RIGHT_LEG -> Mth.cos(phase) * 1.4F * speed;
         case LEFT_LEG -> Mth.cos(phase + (float) Math.PI) * 1.4F * speed;
         case RIGHT_ARM -> Mth.cos(phase + (float) Math.PI) * speed;
         case LEFT_ARM -> Mth.cos(phase) * speed;
         default -> 0.0F;
      };
   }

   private static float weight(AvatarRenderState state, Bone bone) {
      if (bone.leg()) {
         return state.isPassenger ? 0.0F : 1.0F - state.swimAmount;
      } else if (!bone.arm()) {
         return 0.0F;
      } else {
         ArmPose pose = bone == Bone.RIGHT_ARM ? state.rightArmPose : state.leftArmPose;
         ArmPose other = bone == Bone.RIGHT_ARM ? state.leftArmPose : state.rightArmPose;
         if (other.isTwoHanded()) {
            return 0.0F;
         } else {
            float share = switch (pose) {
               case EMPTY -> 1.0F;
               case ITEM, BLOCK, BRUSH, THROW_TRIDENT -> 0.5F;
               default -> 0.0F;
            };
            return state.isUsingItem ? share : share * (1.0F - state.swimAmount);
         }
      }
   }

   private static void lift(PoseStack stack, AvatarRenderState state, CharacterModel model) {
      if (state.isPassenger) {
         float rise = 12.0F - model.scale * model.hipY;
         if (rise != 0.0F) {
            stack.translate(0.0F, -rise / 16.0F, 0.0F);
         }
      }
   }

   private static void chain(PoseStack stack, PlayerModel pm, CharacterModel model, int index, float[] angles, boolean rotate, ModelPart hand, float[] pitch) {
      PartMesh part = model.parts[index];
      float s = model.scale;
      if (!part.extra) {
         if (hand == null) {
            ModelPart vp = vanilla(pm, part.bone);
            pivot(stack, vp, part, s, pitch == null ? vp.xRot : pitch[part.bone.ordinal()]);
         } else {
            stack.translate(hand.x / 16.0F, hand.y / 16.0F, hand.z / 16.0F);
            stack.mulPose(ROTATION.rotationZYX(hand.zRot, hand.yRot, hand.xRot));
            float side = part.bone == Bone.RIGHT_ARM ? -1.0F : 1.0F;
            stack.translate(
               (side - (part.gripX - part.pivotX) * s) / 16.0F,
               (10.0F + (part.gripY - part.pivotY) * s) / 16.0F,
               (-2.0F + (part.gripZ - part.pivotZ) * s) / 16.0F
            );
         }

         float k = s / 16.0F;
         stack.scale(k, -k, -k);
      } else {
         chain(stack, pm, model, part.parent, angles, true, hand, pitch);
         PartMesh parent = model.parts[part.parent];
         stack.translate(part.pivotX - parent.pivotX, part.pivotY - parent.pivotY, part.pivotZ - parent.pivotZ);
         if (rotate && angles != null && part.motionSlot >= 0) {
            int o = part.motionSlot * 3;
            if (angles[o] != 0.0F || angles[o + 1] != 0.0F || angles[o + 2] != 0.0F) {
               stack.mulPose(ROTATION.rotationZYX(angles[o + 2], angles[o + 1], angles[o]));
            }
         }
      }
   }

   private static void pivot(PoseStack stack, ModelPart vp, PartMesh part, float s, float xRot) {
      PartPose rest = vp.getInitialPose();
      stack.translate(
         (s * part.pivotX + (vp.x - rest.x()) * s) / 16.0F,
         (24.0F - s * part.pivotY + (vp.y - rest.y()) * s) / 16.0F,
         (-s * part.pivotZ + (vp.z - rest.z()) * s) / 16.0F
      );
      if (xRot != 0.0F || vp.yRot != 0.0F || vp.zRot != 0.0F) {
         stack.mulPose(ROTATION.rotationZYX(vp.zRot, vp.yRot, xRot));
      }

      if (vp.xScale != 1.0F || vp.yScale != 1.0F || vp.zScale != 1.0F) {
         stack.scale(vp.xScale, vp.yScale, vp.zScale);
      }
   }

   private static ModelPart vanilla(PlayerModel pm, Bone bone) {
      return switch (bone) {
         case RIGHT_LEG -> pm.rightLeg;
         case LEFT_LEG -> pm.leftLeg;
         case RIGHT_ARM -> pm.rightArm;
         case LEFT_ARM -> pm.leftArm;
         case HEAD -> pm.head;
         case BODY -> pm.body;
      };
   }

   private static void emit(
      Pose pose,
      VertexConsumer out,
      Surface s,
      int light,
      int overlay,
      int alpha,
      boolean bends,
      float pitch,
      float yaw,
      float roll,
      PartMesh part,
      float bent,
      float turn
   ) {
      int n = s.vertexCount();
      if (scratch.length < n * 6) {
         scratch = new float[Math.max(n * 6, scratch.length * 2)];
      }

      float[] buf = scratch;
      Matrix4f m = pose.pose();
      Matrix3f nm = pose.normal();
      float m00 = m.m00();
      float m01 = m.m10();
      float m02 = m.m20();
      float m03 = m.m30();
      float m10 = m.m01();
      float m11 = m.m11();
      float m12 = m.m21();
      float m13 = m.m31();
      float m20 = m.m02();
      float m21 = m.m12();
      float m22 = m.m22();
      float m23 = m.m32();
      float n00 = nm.m00();
      float n01 = nm.m10();
      float n02 = nm.m20();
      float n10 = nm.m01();
      float n11 = nm.m11();
      float n12 = nm.m21();
      float n20 = nm.m02();
      float n21 = nm.m12();
      float n22 = nm.m22();
      float[] pos = s.pos;
      float[] nrm = s.nrm;
      float[] weight = bends ? s.weight : null;
      float[] flat = s.flat;
      Lights lights = flat != null ? CharacterLighting.current() : null;
      if (weight != null) {
         levels(LEVELS, pitch, yaw, roll);
      }

      float[] joint = bent == 0.0F && turn == 0.0F ? null : s.joint;
      float jx = part.jointX;
      float jy = part.jointY;
      float jz = part.jointZ;
      if (joint != null) {
         levels(JOINT_LEVELS, bent, turn, 0.0F);
      }

      for (int v = 0; v < n; v++) {
         float x = pos[v * 3];
         float y = pos[v * 3 + 1];
         float z = pos[v * 3 + 2];
         float nx = nrm[v * 3];
         float ny = nrm[v * 3 + 1];
         float nz = nrm[v * 3 + 2];
         if (joint != null) {
            int k = Math.round(joint[v] * 16.0F);
            if (k > 0) {
               float[] r = JOINT_LEVELS;
               int o = k * 9;
               float dx = x - jx;
               float dy = y - jy;
               float dz = z - jz;
               x = jx + r[o] * dx + r[o + 1] * dy + r[o + 2] * dz;
               y = jy + r[o + 3] * dx + r[o + 4] * dy + r[o + 5] * dz;
               z = jz + r[o + 6] * dx + r[o + 7] * dy + r[o + 8] * dz;
               float rx = r[o] * nx + r[o + 1] * ny + r[o + 2] * nz;
               float ry = r[o + 3] * nx + r[o + 4] * ny + r[o + 5] * nz;
               nz = r[o + 6] * nx + r[o + 7] * ny + r[o + 8] * nz;
               nx = rx;
               ny = ry;
            }
         }

         if (weight != null) {
            int k = Math.round(weight[v] * 16.0F) * 9;
            float[] r = LEVELS;
            float bx = r[k] * x + r[k + 1] * y + r[k + 2] * z;
            float by = r[k + 3] * x + r[k + 4] * y + r[k + 5] * z;
            float bz = r[k + 6] * x + r[k + 7] * y + r[k + 8] * z;
            float cx = r[k] * nx + r[k + 1] * ny + r[k + 2] * nz;
            float cy = r[k + 3] * nx + r[k + 4] * ny + r[k + 5] * nz;
            float cz = r[k + 6] * nx + r[k + 7] * ny + r[k + 8] * nz;
            x = bx;
            y = by;
            z = bz;
            nx = cx;
            ny = cy;
            nz = cz;
         }

         int o = v * 6;
         buf[o] = m00 * x + m01 * y + m02 * z + m03;
         buf[o + 1] = m10 * x + m11 * y + m12 * z + m13;
         buf[o + 2] = m20 * x + m21 * y + m22 * z + m23;
         float tx = n00 * nx + n01 * ny + n02 * nz;
         float ty = n10 * nx + n11 * ny + n12 * nz;
         float tz = n20 * nx + n21 * ny + n22 * nz;
         float l = tx * tx + ty * ty + tz * tz;
         if (l > 1.0E-12F && Math.abs(l - 1.0F) > 0.001F) {
            l = 1.0F / (float)Math.sqrt(l);
            tx *= l;
            ty *= l;
            tz *= l;
         }

         buf[o + 3] = tx;
         buf[o + 4] = ty;
         buf[o + 5] = tz;
         if (lights != null && flat[v] > 0.0F) {
            lights.flatten(buf, o + 3, flat[v]);
         }
      }

      int[] quads = s.quads;
      int[] color = s.color;
      float[] uv = s.uv;
      byte[] glow = s.glow;

      for (int q = 0; q < quads.length; q++) {
         int v = quads[q];
         int ox = v * 6;
         int c = alpha == 255 ? color[v] : color[v] & 16777215 | alpha << 24;
         int lx = glow != null && glow[v] != 0 ? 15728880 : light;
         out.addVertex(buf[ox], buf[ox + 1], buf[ox + 2], c, uv[v * 2], uv[v * 2 + 1], overlay, lx, buf[ox + 3], buf[ox + 4], buf[ox + 5]);
      }
   }

   private static void levels(float[] r, float pitch, float yaw, float roll) {
      for (int k = 0; k <= 16; k++) {
         float t = k / 16.0F;
         float cx = (float)Math.cos(pitch * t);
         float sx = (float)Math.sin(pitch * t);
         float cy = (float)Math.cos(yaw * t);
         float sy = (float)Math.sin(yaw * t);
         float cz = (float)Math.cos(roll * t);
         float sz = (float)Math.sin(roll * t);
         int o = k * 9;
         r[o] = cz * cy;
         r[o + 1] = cz * sy * sx - sz * cx;
         r[o + 2] = cz * sy * cx + sz * sx;
         r[o + 3] = sz * cy;
         r[o + 4] = sz * sy * sx + cz * cx;
         r[o + 5] = sz * sy * cx - cz * sx;
         r[o + 6] = -sy;
         r[o + 7] = cy * sx;
         r[o + 8] = cy * cx;
      }
   }
}
