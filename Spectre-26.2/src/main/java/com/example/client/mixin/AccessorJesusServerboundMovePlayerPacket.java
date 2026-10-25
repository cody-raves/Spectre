package com.example.client.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(ServerboundMovePlayerPacket.class)
public interface AccessorJesusServerboundMovePlayerPacket {
   @Mutable
   @Accessor("onGround")
   void spectre$jesusSetOnGround(boolean var1);
}
