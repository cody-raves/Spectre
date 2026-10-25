package com.example.client.mixin;

import com.mojang.blaze3d.opengl.DirectStateAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Environment(EnvType.CLIENT)
@Mixin(DirectStateAccess.class)
public class MixinGlDevice {
   @Redirect(method = "create", at = @At(value = "FIELD", target = "Lcom/mojang/blaze3d/opengl/GlDevice;USE_GL_ARB_direct_state_access:Z", opcode = 178))
   private static boolean spectre$blockDsaForAmd() {
      String renderer = GL11.glGetString(7937);
      return renderer == null || !renderer.contains("AMD");
   }
}
