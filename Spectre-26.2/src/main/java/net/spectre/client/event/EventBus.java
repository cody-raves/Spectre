package net.spectre.client.event;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class EventBus {
   public static final EventBus INSTANCE = new EventBus();
   private final Map<Object, List<EventBus.RegistrationEntry<?>>> subscriptions = new IdentityHashMap<>();
   private final ArrayBlockingQueue<Runnable> asyncQueue = new ArrayBlockingQueue<>(4096);
   private volatile long failedTasks;
   private static final MethodType FACTORY_TYPE = MethodType.methodType(Listener.class, Object.class);
   private static final ClassValue<List<EventBus.HandlerSpec>> HANDLER_SPECS = new ClassValue<List<EventBus.HandlerSpec>>() {
      protected List<EventBus.HandlerSpec> computeValue(Class<?> clazz) {
         return EventBus.scan(clazz);
      }
   };
   private static final ClassValue<EventType<?>> TYPE_CACHE = new ClassValue<EventType<?>>() {
      protected EventType<?> computeValue(Class<?> eventClass) {
         try {
            Field f = eventClass.getField("TYPE");
            if (!EventType.class.isAssignableFrom(f.getType())) {
               throw new IllegalArgumentException(eventClass.getName() + ".TYPE must be an EventType instance.");
            } else {
               return (EventType<?>)f.get(null);
            }
         } catch (NoSuchFieldException var3) {
            throw new IllegalArgumentException("Event class " + eventClass.getName() + " must declare 'public static final EventType<...> TYPE'.", var3);
         } catch (IllegalAccessException var4) {
            throw new IllegalArgumentException("Cannot access " + eventClass.getName() + ".TYPE", var4);
         }
      }
   };

   public boolean postAsync(Runnable task) {
      return this.asyncQueue.offer(Objects.requireNonNull(task));
   }

   public int drainAsync() {
      return this.drainAsync(512, 2000000L);
   }

   public int drainAsync(int maxTasks, long maxNanos) {
      if (maxTasks > 0 && maxNanos > 0L) {
         long started = System.nanoTime();
         int processed = 0;

         while (processed < maxTasks && System.nanoTime() - started < maxNanos) {
            Runnable task = this.asyncQueue.poll();
            if (task == null) {
               break;
            }

            processed++;

            try {
               task.run();
            } catch (RuntimeException var11) {
               long count = ++this.failedTasks;
               if (count <= 4L || count % 256L == 0L) {
                  System.err.println("[SpectreEventBus] Async task failed (#" + count + ")");
                  var11.printStackTrace();
               }
            }
         }

         return processed;
      } else {
         throw new IllegalArgumentException("Drain limits must be positive");
      }
   }

   public <E extends Event> void off(ListenerHandle<E> handle) {
      handle.unregister();
   }

   public int register(Object subscriber) {
      List<EventBus.HandlerSpec> specs = HANDLER_SPECS.get(subscriber.getClass());
      if (specs.isEmpty()) {
         return 0;
      } else {
         synchronized (this) {
            return this.register(subscriber, specs);
         }
      }
   }

   private int register(Object subscriber, List<EventBus.HandlerSpec> specs) {
      if (this.subscriptions.containsKey(subscriber)) {
         throw new IllegalStateException("Subscriber already registered: " + subscriber.getClass().getName());
      } else {
         List<EventBus.RegistrationEntry<?>> entries = buildEntries(subscriber, specs);

         try {
            for (EventBus.RegistrationEntry<?> entry : entries) {
               entry.register();
            }
         } catch (Error | RuntimeException var9) {
            Throwable failure = var9;

            for (EventBus.RegistrationEntry<?> entry : entries) {
               try {
                  entry.unregister();
               } catch (Error | RuntimeException var8) {
                  if (failure != var8) {
                     failure.addSuppressed(var8);
                  }
               }
            }

            throw var9;
         }

         this.subscriptions.put(subscriber, entries);
         return entries.size();
      }
   }

   public synchronized void unregister(Object subscriber) {
      List<EventBus.RegistrationEntry<?>> entries = this.subscriptions.remove(subscriber);
      if (entries != null) {
         for (EventBus.RegistrationEntry<?> entry : entries) {
            entry.unregister();
         }
      }
   }

   private static List<EventBus.RegistrationEntry<?>> buildEntries(Object subscriber, List<EventBus.HandlerSpec> specs) {
      List<EventBus.RegistrationEntry<?>> entries = new ArrayList<>(specs.size());

      for (EventBus.HandlerSpec spec : specs) {
         Listener<Event> listener = spec.staticListener();
         if (listener == null) {
            try {
               listener = castListener((Listener)spec.listenerFactory().invokeExact((Object)subscriber));
            } catch (Throwable var7) {
               throw new RuntimeException("Failed to bind listener for " + subscriber.getClass().getName(), var7);
            }
         }

         ListenerHandle<Event> handle = new ListenerHandle<>(spec.type(), spec.stage(), spec.priority(), subscriber, listener::invoke);
         entries.add(new EventBus.RegistrationEntry<>(spec.type(), handle));
      }

      return entries;
   }

   private static Listener<Event> castListener(Listener<?> listener) {
      return (Listener<Event>)listener;
   }

   private static List<EventBus.HandlerSpec> scan(Class<?> clazz) {
      List<EventBus.HandlerSpec> specs = new ArrayList<>();

      for (Class<?> parent = clazz; parent != null; parent = parent.getSuperclass()) {
         for (Method method : parent.getDeclaredMethods()) {
            if (isHandler(method) && !Modifier.isPublic(method.getModifiers())) {
               throw new IllegalArgumentException("Event handler must be public: " + method);
            }
         }
      }

      for (Method methodx : clazz.getMethods()) {
         Stage stage = Stage.PRE;
         EventPriority priority = EventPriority.NORMAL;
         boolean matched = false;
         EventHandler newAnnotation = methodx.getAnnotation(EventHandler.class);
         if (newAnnotation != null) {
            stage = newAnnotation.stage();
            priority = newAnnotation.priority();
            matched = true;
         } else {
            EventListener legacyAnnotation = methodx.getAnnotation(EventListener.class);
            if (legacyAnnotation != null) {
               stage = Stage.PRE;
               priority = EventPriority.fromInt(legacyAnnotation.priority());
               matched = true;
            }
         }

         if (matched) {
            Class<?>[] params = methodx.getParameterTypes();
            if (params.length != 1 || !Event.class.isAssignableFrom(params[0]) || methodx.getReturnType() != void.class) {
               throw new IllegalArgumentException("Event handler must return void and accept one Event: " + methodx);
            }

            Class<Event> eventClass = (Class<Event>)params[0];
            EventType<Event> type = resolveTypeFor(eventClass);
            if (Modifier.isStatic(methodx.getModifiers())) {
               specs.add(new EventBus.HandlerSpec(type, stage, priority, null, LambdaListenerFactory.createStatic(methodx)));
            } else {
               specs.add(new EventBus.HandlerSpec(type, stage, priority, LambdaListenerFactory.createFactory(methodx).asType(FACTORY_TYPE), null));
            }
         }
      }

      return specs.isEmpty() ? List.of() : List.copyOf(specs);
   }

   private static boolean isHandler(Method method) {
      return method.isAnnotationPresent(EventHandler.class) || method.isAnnotationPresent(EventListener.class);
   }

   static <E extends Event> EventType<E> resolveTypeFor(Class<E> eventClass) {
      return (EventType<E>)TYPE_CACHE.get(eventClass);
   }

   @Environment(EnvType.CLIENT)
   private record HandlerSpec(EventType<Event> type, Stage stage, EventPriority priority, MethodHandle listenerFactory, Listener<Event> staticListener) {
   }

   @Environment(EnvType.CLIENT)
   private record RegistrationEntry<E extends Event>(EventType<E> type, ListenerHandle<E> handle) {
      void register() {
         this.type.register(this.handle);
      }

      void unregister() {
         this.type.unregister(this.handle);
      }
   }
}
