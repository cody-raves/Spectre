package net.spectre.client.module.impl.render;

import com.mojang.blaze3d.platform.InputConstants;
import net.spectre.client.event.Stage;
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
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

@Environment(EnvType.CLIENT)
public final class FreelookModule extends ClientModule {
   private static final String PLAYER = "Player";
   private static final String CAMERA = "Camera";
   private static final float MIN_SENSITIVITY = 0.1F;
   private final ModeSetting mode = this.addSetting("mode", new ModeSetting("Mode", "Player", "Player", "Camera"));
   private final BooleanSetting togglePerspective = this.addSetting("toggle-perspective", new BooleanSetting("Toggle Perspective", true));
   private final NumberSetting sensitivity = this.addSetting("camera-sensitivity", new NumberSetting("Camera Sensitivity", 8.0F, 0.0F, 10.0F).step(0.1F));
   private final BooleanSetting arrows = this.addSetting("arrows-control-opposite", new BooleanSetting("Arrows Control Opposite", true));
   private final NumberSetting arrowSpeed = this.addSetting("arrow-speed", new NumberSetting("Arrow Speed", 4.0F, 0.0F, 10.0F).step(0.5F));
   private float cameraYaw;
   private float cameraPitch;
   private CameraType previousView;

   public FreelookModule() {
      super("freelook", "Freelook", "Turns the camera and the player separately.", ModuleCategory.RENDER);
      this.sensitivity.visibleWhen(() -> this.mode.is("Camera"));
      this.arrowSpeed.visibleWhen(this.arrows::get);
   }

   @Override
   public boolean persistEnabled() {
      return false;
   }

   @Override
   protected void onEnable() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) {
         this.requestDisable();
      } else {
         this.cameraYaw = mc.player.getYRot();
         this.cameraPitch = mc.player.getXRot();
         this.previousView = mc.options.getCameraType();
         if (this.previousView != CameraType.THIRD_PERSON_BACK && this.togglePerspective.get()) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
         }

         this.on(TickEvent.TYPE, Stage.POST, this::onTick);
      }
   }

   @Override
   protected void onDisable() {
      Minecraft mc = Minecraft.getInstance();
      if (this.previousView != null && mc.options.getCameraType() != this.previousView && this.togglePerspective.get()) {
         mc.options.setCameraType(this.previousView);
      }

      this.previousView = null;
   }

   private void onTick(TickEvent e) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player != null) {
         if (this.arrows.get() && mc.gui.screen() == null) {
            float turn = this.arrowSpeed.get();
            float yawTurn = (held(mc, 262) ? turn : 0.0F) - (held(mc, 263) ? turn : 0.0F);
            float pitchTurn = (held(mc, 264) ? turn : 0.0F) - (held(mc, 265) ? turn : 0.0F);
            if (this.mode.is("Player")) {
               this.cameraYaw += yawTurn;
               this.cameraPitch += pitchTurn;
            } else if (yawTurn != 0.0F || pitchTurn != 0.0F) {
               player.setYRot(player.getYRot() + yawTurn);
               player.setXRot(player.getXRot() + pitchTurn);
            }
         }

         this.cameraPitch = Mth.clamp(this.cameraPitch, -90.0F, 90.0F);
      }
   }

   private static boolean held(Minecraft mc, int key) {
      return InputConstants.isKeyDown(mc.getWindow(), key);
   }

   public boolean cameraMode() {
      return this.isEnabled() && this.mode.is("Camera");
   }

   public static boolean throughBlocks() {
      FreelookModule freelook = ModuleRegistry.get(FreelookModule.class);
      return freelook != null && freelook.isEnabled() && FreecamModule.active == null;
   }

   public void turnCamera(double yRot, double xRot) {
      float divisor = Math.max(this.sensitivity.get(), 0.1F);
      this.cameraYaw += (float)(yRot / divisor);
      this.cameraPitch += (float)(xRot / divisor);
      if (Math.abs(this.cameraPitch) > 90.0F) {
         this.cameraPitch = this.cameraPitch > 0.0F ? 90.0F : -90.0F;
      }
   }

   public float cameraYaw() {
      return this.cameraYaw;
   }

   public float cameraPitch() {
      return this.cameraPitch;
   }
}
