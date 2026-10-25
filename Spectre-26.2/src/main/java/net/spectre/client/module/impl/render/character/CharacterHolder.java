package net.spectre.client.module.impl.render.character;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public interface CharacterHolder {
   WornCharacter spectre$character();

   void spectre$setCharacter(WornCharacter var1);

   float spectre$seat();

   void spectre$setSeat(float var1);
}
