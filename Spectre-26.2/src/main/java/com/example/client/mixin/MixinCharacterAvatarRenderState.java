package com.example.client.mixin;

import net.spectre.client.module.impl.render.character.CharacterHolder;
import net.spectre.client.module.impl.render.character.WornCharacter;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Environment(EnvType.CLIENT)
@Mixin(AvatarRenderState.class)
public abstract class MixinCharacterAvatarRenderState implements CharacterHolder {
   @Unique
   private WornCharacter spectre$character;
   @Unique
   private float spectre$seat;

   @Override
   public WornCharacter spectre$character() {
      return this.spectre$character;
   }

   @Override
   public void spectre$setCharacter(WornCharacter character) {
      this.spectre$character = character;
   }

   @Override
   public float spectre$seat() {
      return this.spectre$seat;
   }

   @Override
   public void spectre$setSeat(float knee) {
      this.spectre$seat = knee;
   }
}
