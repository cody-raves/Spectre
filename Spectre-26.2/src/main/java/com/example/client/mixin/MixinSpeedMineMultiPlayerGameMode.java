package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.spectre.client.module.impl.misc.SpeedMineModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(MultiPlayerGameMode.class)
public abstract class MixinSpeedMineMultiPlayerGameMode {
   @Shadow
   @Final
   private Minecraft minecraft;
   @Shadow
   private int destroyDelay;

   @Inject(method = "continueDestroyBlock", at = @At("HEAD"))
   private void spectre$speedMineDelay(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
      LocalPlayer player = this.minecraft.player;
      if (this.destroyDelay > 0 && player != null && this.minecraft.level != null && !player.getAbilities().instabuild) {
         BlockState state = this.minecraft.level.getBlockState(pos);
         if (SpeedMineModule.dropsDelay(state, state.getDestroyProgress(player, this.minecraft.level, pos))) {
            this.destroyDelay = 0;
         }
      }
   }

   @WrapOperation(
      method = "continueDestroyBlock",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/level/block/state/BlockState;getDestroyProgress(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"
      )
   )
   private float spectre$speedMineProgress(BlockState state, Player player, BlockGetter level, BlockPos pos, Operation<Float> original) {
      return SpeedMineModule.scaleProgress(state, (Float)original.call(new Object[]{state, player, level, pos}));
   }
}
