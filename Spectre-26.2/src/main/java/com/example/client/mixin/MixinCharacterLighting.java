package com.example.client.mixin;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.Lighting.Entry;
import net.spectre.client.module.impl.render.character.CharacterLighting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Lighting.class)
public abstract class MixinCharacterLighting {
   @Inject(method = "setupFor", at = @At("HEAD"))
   private void spectre$trackCharacterLighting(Entry entry, CallbackInfo ci) {
      CharacterLighting.setup(entry);
   }
}
