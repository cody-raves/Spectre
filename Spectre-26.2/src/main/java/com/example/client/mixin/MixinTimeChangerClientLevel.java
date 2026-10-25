package com.example.client.mixin;
import net.minecraft.client.ClientClockManager;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import net.spectre.client.module.impl.render.TimeChangerModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Override the displayed clock without altering server synchronization or tick rate. */
@Mixin(ClientClockManager.class)
public abstract class MixinTimeChangerClientLevel {
    @Inject(method="getTotalTicks",at=@At("RETURN"),cancellable=true)
    private void spectre$displayTime(Holder<WorldClock> clock,CallbackInfoReturnable<Long> cir){
        var module=TimeChangerModule.active;
        if(module!=null&&clock.is(WorldClocks.OVERWORLD))cir.setReturnValue(module.forcedDayTime());
    }
}
