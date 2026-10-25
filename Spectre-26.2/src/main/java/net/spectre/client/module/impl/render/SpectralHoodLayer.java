package net.spectre.client.module.impl.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public final class SpectralHoodLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
   private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("spectre", "textures/cosmetic/headdress_base.png");
   private static final float HEAD_CENTRE = -0.25F;

   public SpectralHoodLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
      super(parent);
   }

   public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
      HeadCosmeticModule module = HeadCosmeticModule.worn;
      if (module != null && !state.isInvisible && module.wears(state)) {
         boolean helmet = !state.headEquipment.isEmpty() || !state.headItem.isEmpty() || state.wornHeadType != null;
         boolean chestplate = !state.chestEquipment.isEmpty() && !state.chestEquipment.has(DataComponents.GLIDER);
         CosmeticMesh head = module.headMesh(helmet);
         CosmeticMesh body = module.bodyMesh(chestplate);
         CosmeticMesh.Look look = module.look();
         float scale = module.scale();
         RenderType type = RenderTypes.entityCutout(TEXTURE);
         PlayerModel model = this.getParentModel();
         poseStack.pushPose();
         model.root().translateAndRotate(poseStack);
         poseStack.pushPose();
         model.getHead().translateAndRotate(poseStack);
         if (scale != 1.0F) {
            poseStack.translate(0.0F, -0.25F, 0.0F);
            poseStack.scale(scale, scale, scale);
            poseStack.translate(0.0F, 0.25F, 0.0F);
         }

         collector.submitCustomGeometry(poseStack, type, (pose, out) -> head.emit(pose, out, light, look));
         poseStack.popPose();
         if (!body.isEmpty()) {
            model.body.translateAndRotate(poseStack);
            collector.submitCustomGeometry(poseStack, type, (pose, out) -> body.emit(pose, out, light, look));
         }

         poseStack.popPose();
      }
   }
}
