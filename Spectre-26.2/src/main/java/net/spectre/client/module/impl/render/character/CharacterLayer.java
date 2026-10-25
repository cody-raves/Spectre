package net.spectre.client.module.impl.render.character;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.HumanoidArm;

@Environment(EnvType.CLIENT)
public final class CharacterLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
   public CharacterLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
      super(parent);
   }

   public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
      WornCharacter worn = CharacterModule.worn(state);
      if (worn != null && (!state.isInvisible || !state.isInvisibleToPlayer)) {
         CharacterRenderer.submit(worn, this.getParentModel(), poseStack, collector, light, state);
      }
   }

   public static boolean firstPersonHand(PlayerModel model, PoseStack poseStack, SubmitNodeCollector collector, int light, ModelPart arm) {
      return CharacterRenderer.firstPersonHand(model, poseStack, collector, light, arm);
   }

   public static boolean translateToHand(PlayerModel model, AvatarRenderState state, HumanoidArm arm, PoseStack poseStack) {
      return CharacterRenderer.translateToHand(model, state, arm, poseStack);
   }
}
