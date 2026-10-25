package com.example.client.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Environment(EnvType.CLIENT)
@Mixin(GameRenderer.class)
public interface AccessorGameRenderer {
   
   @Accessor("fogRenderer")
   FogRenderer spectre$getFogRenderer();

   @Invoker("shouldRenderBlockOutline")
   boolean spectre$shouldRenderBlockOutline();
}

