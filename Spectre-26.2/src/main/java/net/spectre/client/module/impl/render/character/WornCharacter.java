package net.spectre.client.module.impl.render.character;

import net.spectre.client.module.impl.render.character.kit.CharacterModel;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public final class WornCharacter {
   final CharacterModel model;
   final RenderType solid;
   final RenderType decal;
   final RenderType ghost;

   WornCharacter(CharacterModel model, Identifier texture) {
      this.model = model;
      this.solid = RenderTypes.entitySolid(texture);
      this.decal = RenderTypes.entityTranslucent(texture);
      this.ghost = RenderTypes.entityTranslucentCullItemTarget(texture);
   }
}
