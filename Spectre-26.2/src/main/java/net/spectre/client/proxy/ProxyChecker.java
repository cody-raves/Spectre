package net.spectre.client.proxy;

import com.google.common.net.InetAddresses;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.proxy.ProxyHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class ProxyChecker {
   private static final InetSocketAddress TARGET = new InetSocketAddress(InetAddresses.forString("1.1.1.1"), 443);
   private static final int TIMEOUT_MS = 8000;
   private static final int IN_FLIGHT = 32;
   private static final ProxyChecker INSTANCE = new ProxyChecker();
   private final Set<Proxy> checking = ConcurrentHashMap.newKeySet();
   private final Deque<Proxy> queue = new ArrayDeque<>();
   private int running;
   private EventLoopGroup group;

   private ProxyChecker() {
   }

   public static ProxyChecker get() {
      return INSTANCE;
   }

   public synchronized void check(Collection<Proxy> proxies) {
      for (Proxy proxy : proxies) {
         if (this.checking.add(proxy)) {
            this.queue.add(proxy);
         }
      }

      this.pump();
   }

   public boolean isChecking(Proxy proxy) {
      return this.checking.contains(proxy);
   }

   public int pending() {
      return this.checking.size();
   }

   private synchronized void pump() {
      if (this.group == null) {
         this.group = new MultiThreadIoEventLoopGroup(2, new DefaultThreadFactory("Spectre proxy check", true), NioIoHandler.newFactory());
      }

      while (this.running < 32 && !this.queue.isEmpty()) {
         Proxy proxy = this.queue.poll();
         this.running++;
         this.group.execute(() -> this.start(proxy));
      }
   }

   private void start(Proxy proxy) {
      AtomicBoolean done = new AtomicBoolean();
      if (!proxy.isValid()) {
         this.finish(proxy, -1, done);
      } else {
         final ProxyHandler handler;
         try {
            handler = ProxyRouting.handler(proxy);
         } catch (RuntimeException var7) {
            this.finish(proxy, -1, done);
            return;
         }

         handler.setConnectTimeoutMillis(8000L);
         long started = System.nanoTime();
         ChannelFuture connect = ((Bootstrap)((Bootstrap)((Bootstrap)((Bootstrap)new Bootstrap().group(this.group)).channel(NioSocketChannel.class))
                  .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 8000))
               .handler(new ChannelInitializer<Channel>() {
                  protected void initChannel(Channel channel) {
                     channel.pipeline().addLast(new ChannelHandler[]{handler, new ChannelInboundHandlerAdapter() {
                        public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
                           context.close();
                        }
                     }});
                  }
               }))
            .connect(TARGET);
         connect.addListener(future -> {
            if (!future.isSuccess()) {
               this.finish(proxy, -1, done);
            }
         });
         handler.connectFuture().addListener(future -> {
            int ms = (int)((System.nanoTime() - started) / 1000000L);
            this.finish(proxy, future.isSuccess() ? ms : -1, done);
            connect.channel().close();
         });
      }
   }

   private void finish(Proxy proxy, int ping, AtomicBoolean done) {
      if (done.compareAndSet(false, true)) {
         proxy.ping = ping;
         this.checking.remove(proxy);
         boolean last;
         synchronized (this) {
            this.running--;
            last = this.running == 0 && this.queue.isEmpty();
            this.pump();
         }

         if (last) {
            Proxies.get().checked();
         }
      }
   }
}
