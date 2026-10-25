package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.netty.bootstrap.AbstractBootstrap;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelHandler;
import java.net.InetSocketAddress;
import net.spectre.client.proxy.ProxyRouting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(Connection.class)
public abstract class MixinProxyConnection {
   @WrapOperation(
      method = "connect",
      at = @At(value = "INVOKE", target = "Lio/netty/bootstrap/Bootstrap;handler(Lio/netty/channel/ChannelHandler;)Lio/netty/bootstrap/AbstractBootstrap;")
   )
   private static AbstractBootstrap<?, ?> spectre$routeThroughProxy(
      Bootstrap bootstrap, ChannelHandler initializer, Operation<AbstractBootstrap<?, ?>> original, @Local(argsOnly = true) InetSocketAddress target
   ) {
      return (AbstractBootstrap<?, ?>)original.call(new Object[]{bootstrap, ProxyRouting.route(target, initializer)});
   }
}
