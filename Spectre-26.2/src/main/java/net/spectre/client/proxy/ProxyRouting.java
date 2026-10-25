package net.spectre.client.proxy;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.proxy.ProxyHandler;
import io.netty.handler.proxy.Socks4ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class ProxyRouting {
   private static final String HANDLER_NAME = "spectre_proxy";

   private ProxyRouting() {
   }

   public static ChannelHandler route(InetSocketAddress target, ChannelHandler initializer) {
      if (target != null && !isLocal(target)) {
         Proxy proxy = Proxies.get().enabledCopy();
         if (proxy != null && proxy.isValid()) {
            final ProxyHandler handler = handler(proxy);
            return new ChannelInitializer<Channel>() {
               protected void initChannel(Channel channel) {
                  channel.pipeline().addFirst("spectre_proxy", handler);
                  channel.pipeline().addLast(new ChannelHandler[]{initializer});
               }
            };
         } else {
            return initializer;
         }
      } else {
         return initializer;
      }
   }

   static ProxyHandler handler(Proxy proxy) {
      InetSocketAddress server = new InetSocketAddress(proxy.address.trim(), proxy.port);
      String username = proxy.username != null && !proxy.username.isEmpty() ? proxy.username : null;
      String password = proxy.password != null && !proxy.password.isEmpty() ? proxy.password : null;

      return (ProxyHandler)(switch (proxy.type) {
         case SOCKS4 -> new Socks4ProxyHandler(server, username);
         case SOCKS5 -> new Socks5ProxyHandler(server, username, password);
      });
   }

   private static boolean isLocal(InetSocketAddress target) {
      InetAddress ip = target.getAddress();
      if (ip == null) {
         return "localhost".equalsIgnoreCase(target.getHostString());
      } else if (!ip.isLoopbackAddress() && !ip.isAnyLocalAddress() && !ip.isLinkLocalAddress() && !ip.isSiteLocalAddress()) {
         byte[] raw = ip.getAddress();
         return raw.length == 16 && (raw[0] & 254) == 252;
      } else {
         return true;
      }
   }
}
