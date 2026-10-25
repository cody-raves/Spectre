package net.spectre.client.module.impl.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.ActionSetting;
import net.spectre.client.module.setting.BooleanSetting;
import net.spectre.client.module.setting.ModeSetting;
import net.spectre.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class HandsModule extends ClientModule {
   public static volatile HandsModule live;
   private static final String MAIN = "Main Hand";
   private static final String OFF = "Offhand";
   private static final float PIVOT_X = 0.56F;
   private static final float PIVOT_Y = -0.52F;
   private static final float PIVOT_Z = -0.72F;
   private static final float[] REST = new float[]{0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F};
   private static final int X = 0;
   private static final int Y = 1;
   private static final int Z = 2;
   private static final int PITCH = 3;
   private static final int YAW = 4;
   private static final int ROLL = 5;
   private static final int SCALE = 6;
   private static final float SETTLED = 0.001F;
   private static final float MAX_STEP = 0.1F;
   private static final float SLOWEST_RATE = 3.0F;
   private static final float FASTEST_RATE = 30.0F;
   private final BooleanSetting link = this.addSetting("link-hands", new BooleanSetting("Link Hands", false));
   private final ModeSetting editing = this.addSetting("editing", new ModeSetting("Editing", "Main Hand", "Main Hand", "Offhand"));
   private final NumberSetting[] main = this.axes("main", true);
   private final NumberSetting[] off = this.axes("off", false);
   private final NumberSetting glide = this.addSetting("glide", new NumberSetting("Glide", 0.4F, 0.0F, 1.0F).step(0.05F));
   private final HandsModule.HandState mainState = new HandsModule.HandState();
   private final HandsModule.HandState offState = new HandsModule.HandState();

   public HandsModule() {
      super("hand-pose", "Hand Pose", "Moves, rotates and resizes your first-person hands.", ModuleCategory.VISUALS);
      this.editing.visibleWhen(() -> !this.link.get());
      this.addSetting("reset-hand", new ActionSetting("Reset Hand", this::resetEdited));
   }

   private NumberSetting[] axes(String prefix, boolean mainHand) {
      NumberSetting[] axes = new NumberSetting[]{
         this.addSetting(prefix + "-x", new NumberSetting("Offset X", REST[0], -1.0F, 1.0F).step(0.01F)),
         this.addSetting(prefix + "-y", new NumberSetting("Offset Y", REST[1], -1.0F, 1.0F).step(0.01F)),
         this.addSetting(prefix + "-z", new NumberSetting("Offset Z", REST[2], -1.0F, 1.0F).step(0.01F)),
         this.addSetting(prefix + "-pitch", new NumberSetting("Pitch", REST[3], -180.0F, 180.0F).step(1.0F)),
         this.addSetting(prefix + "-yaw", new NumberSetting("Yaw", REST[4], -180.0F, 180.0F).step(1.0F)),
         this.addSetting(prefix + "-roll", new NumberSetting("Roll", REST[5], -180.0F, 180.0F).step(1.0F)),
         this.addSetting(prefix + "-scale", new NumberSetting("Scale", REST[6], 0.3F, 2.0F).step(0.05F))
      };

      for (NumberSetting axis : axes) {
         axis.visibleWhen(mainHand ? () -> this.link.get() || this.editing.is("Main Hand") : () -> !this.link.get() && this.editing.is("Offhand"));
      }

      return axes;
   }

   @Override
   protected void onEnable() {
      this.mainState.stamp = 0L;
      this.offState.stamp = 0L;
      live = this;
   }

   private void resetEdited() {
      NumberSetting[] axes = !this.link.get() && !this.editing.is("Main Hand") ? this.off : this.main;

      for (int i = 0; i < axes.length; i++) {
         axes[i].set(REST[i]);
      }
   }

   public static void shape(PoseStack pose, boolean mainHand, int side) {
      HandsModule module = live;
      if (module != null) {
         module.shapeHand(pose, mainHand, side);
      }
   }

   private void shapeHand(PoseStack pose, boolean mainHand, int side) {
      boolean on = this.isEnabled();
      HandsModule.HandState state = mainHand ? this.mainState : this.offState;
      state.step(!mainHand && !this.link.get() ? this.off : this.main, on, this.follow());
      if (!on && this.mainState.rested && this.offState.rested) {
         live = null;
      } else if (!state.rested) {
         float[] v = state.value;
         pose.translate(side * (0.56F + v[0]), -0.52F + v[1], -0.72F + v[2]);
         pose.mulPose(Axis.YP.rotationDegrees(side * v[4]));
         pose.mulPose(Axis.XP.rotationDegrees(v[3]));
         pose.mulPose(Axis.ZP.rotationDegrees(side * v[5]));
         pose.scale(v[6], v[6], v[6]);
         pose.translate(-side * 0.56F, 0.52F, 0.72F);
      }
   }

   private float follow() {
      float g = this.glide.get();
      return g <= 0.0F ? -1.0F : 30.0F + -27.0F * g;
   }

   @Environment(EnvType.CLIENT)
   private static final class HandState {
      final float[] value = (float[])HandsModule.REST.clone();
      long stamp;
      boolean rested = true;

      void step(NumberSetting[] target, boolean on, float rate) {
         long now = System.nanoTime();
         float dt = this.stamp == 0L ? 0.0F : Math.min(0.1F, (float)(now - this.stamp) / 1.0E9F);
         this.stamp = now;
         float k = rate < 0.0F ? 1.0F : 1.0F - (float)Math.exp(-rate * dt);
         boolean settled = true;

         for (int i = 0; i < this.value.length; i++) {
            float goal = on ? target[i].get() : HandsModule.REST[i];
            float next = this.value[i] + (goal - this.value[i]) * k;
            if (Math.abs(goal - next) < 0.001F) {
               next = goal;
            }

            this.value[i] = next;
            if (next != HandsModule.REST[i]) {
               settled = false;
            }
         }

         this.rested = settled;
      }
   }
}
