package com.example.client.mixin;

import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.player.PlayerModelType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(EntityRenderDispatcher.class)
public interface AccessorSkinChangeEntityRenderDispatcher {
   @Accessor("playerRenderers")
   Map<PlayerModelType, AvatarRenderer<AbstractClientPlayer>> spectre$skinChangePlayerRenderers();
}
