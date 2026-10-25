package net.spectre.client.event;

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaConversionException;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Method;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class LambdaListenerFactory {
   private static final Lookup LOOKUP = MethodHandles.lookup();
   private static final MethodType ERASED_INVOKE_TYPE = MethodType.methodType(void.class, Event.class);

   private LambdaListenerFactory() {
   }

   public static <E extends Event> Listener<E> create(Object instance, Method method) {
      if (instance == null) {
         return createStatic(method);
      } else {
         try {
            return (Listener)createFactory(method).invoke((Object)instance);
         } catch (RuntimeException var3) {
            throw var3;
         } catch (Throwable var4) {
            throw new RuntimeException("Unexpected error creating listener for method " + method, var4);
         }
      }
   }

   public static <E extends Event> Listener<E> createStatic(Method method) {
      validateMethod(method);

      try {
         Lookup targetLookup = MethodHandles.privateLookupIn(method.getDeclaringClass(), LOOKUP);
         MethodHandle directHandle = targetLookup.unreflect(method);
         MethodType instantiatedType = MethodType.methodType(void.class, method.getParameterTypes()[0]);
         CallSite site = LambdaMetafactory.metafactory(
            targetLookup, "invoke", MethodType.methodType(Listener.class), ERASED_INVOKE_TYPE, directHandle, instantiatedType
         );
         return (Listener)site.getTarget().invoke();
      } catch (Throwable var5) {
         throw wrap(method, var5);
      }
   }

   public static MethodHandle createFactory(Method method) {
      validateMethod(method);

      try {
         Lookup targetLookup = MethodHandles.privateLookupIn(method.getDeclaringClass(), LOOKUP);
         MethodHandle directHandle = targetLookup.unreflect(method);
         MethodType instantiatedType = MethodType.methodType(void.class, method.getParameterTypes()[0]);
         MethodType factoryType = MethodType.methodType(Listener.class, method.getDeclaringClass());
         CallSite site = LambdaMetafactory.metafactory(targetLookup, "invoke", factoryType, ERASED_INVOKE_TYPE, directHandle, instantiatedType);
         return site.getTarget();
      } catch (Throwable var6) {
         throw wrap(method, var6);
      }
   }

   private static RuntimeException wrap(Method method, Throwable t) {
      if (t instanceof LambdaConversionException) {
         return new RuntimeException(
            "LambdaMetafactory failed for method " + method + ". Ensure the listener method is public and its parameter type is a concrete Event subclass.", t
         );
      } else {
         return t instanceof IllegalAccessException
            ? new RuntimeException("Cannot access method " + method + ". Listener methods must be public and their declaring class must be accessible.", t)
            : new RuntimeException("Unexpected error creating listener for method " + method, t);
      }
   }

   private static void validateMethod(Method method) {
      if (method.getReturnType() != void.class) {
         throw new IllegalArgumentException("@EventListener method must return void: " + method);
      } else {
         Class<?>[] params = method.getParameterTypes();
         if (params.length != 1) {
            throw new IllegalArgumentException("@EventListener method must have exactly one parameter: " + method);
         } else if (!Event.class.isAssignableFrom(params[0])) {
            throw new IllegalArgumentException("@EventListener parameter must be a subclass of Event: " + method);
         }
      }
   }
}
