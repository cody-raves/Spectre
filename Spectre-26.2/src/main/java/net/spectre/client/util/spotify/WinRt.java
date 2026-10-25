package net.spectre.client.util.spotify;

import com.sun.jna.Function;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.Guid.GUID;
import com.sun.jna.ptr.ByteByReference;
import com.sun.jna.ptr.DoubleByReference;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import java.util.ArrayDeque;
import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
final class WinRt {
   static final int FIRST_SLOT = 6;
   static final int RO_INIT_MULTITHREADED = 1;
   static final int RPC_E_CHANGED_MODE = -2147417850;
   static final int REGDB_E_CLASSNOTREG = -2147221164;
   static final int E_NOINTERFACE = -2147467262;
   static final int E_ABORT = -2147467260;
   static final int E_TIMEOUT = -2147023436;
   private static final int SLOT_QUERY_INTERFACE = 0;
   private static final int SLOT_RELEASE = 2;
   private static final int ASYNC_OPERATION_GET_RESULTS = 8;
   private static final GUID IID_ASYNC_INFO = iid("00000036-0000-0000-C000-000000000046");
   private static final int ASYNC_INFO_GET_STATUS = 7;
   private static final int ASYNC_INFO_GET_ERROR_CODE = 8;
   private static final int ASYNC_INFO_CANCEL = 9;
   private static final int ASYNC_STARTED = 0;
   private static final int ASYNC_COMPLETED = 1;
   private static final int ASYNC_CANCELED = 2;
   private static final long MAX_WAIT_STEP_MS = 5L;

   private WinRt() {
   }

   static GUID iid(String text) {
      GUID guid = new GUID(text);
      guid.write();
      return guid;
   }

   static int initializeMultithreaded() {
      return WinRt.Combase.INSTANCE.RoInitialize(1);
   }

   static void uninitialize() {
      WinRt.Combase.INSTANCE.RoUninitialize();
   }

   static Pointer activationFactory(String runtimeClass, GUID iid) {
      Pointer name = createString(runtimeClass);

      Pointer var4;
      try {
         PointerByReference factory = new PointerByReference();
         check(WinRt.Combase.INSTANCE.RoGetActivationFactory(name, iid.getPointer(), factory), "RoGetActivationFactory(" + runtimeClass + ")");
         var4 = owned(factory.getValue(), "RoGetActivationFactory(" + runtimeClass + ")");
      } finally {
         WinRt.Combase.INSTANCE.WindowsDeleteString(name);
      }

      return var4;
   }

   static int invoke(Pointer self, int slot, Object... args) {
      Pointer vtable = self.getPointer(0L);
      Function method = Function.getFunction(vtable.getPointer((long)slot * Native.POINTER_SIZE), 63);
      Object[] full = new Object[args.length + 1];
      full[0] = self;
      System.arraycopy(args, 0, full, 1, args.length);
      return method.invokeInt(full);
   }

   static void check(int hresult, String step) {
      if (hresult < 0) {
         throw new WinRt.WinRtException(step, hresult);
      }
   }

   static void release(Pointer pointer) {
      if (pointer != null) {
         invoke(pointer, 2);
      }
   }

   static Pointer queryInterface(Pointer self, GUID iid, String step) {
      PointerByReference out = new PointerByReference();
      check(invoke(self, 0, iid.getPointer(), out), step);
      return owned(out.getValue(), step);
   }

   static Pointer tryQueryInterface(Pointer self, GUID iid) {
      PointerByReference out = new PointerByReference();
      return invoke(self, 0, iid.getPointer(), out) < 0 ? null : out.getValue();
   }

   static Pointer callForObject(Pointer self, int slot, String step, Object... inArgs) {
      PointerByReference out = new PointerByReference();
      check(invoke(self, slot, append(inArgs, out)), step);
      return out.getValue();
   }

   static String getString(Pointer self, int slot, String step) {
      PointerByReference out = new PointerByReference();
      check(invoke(self, slot, out), step);
      Pointer hstring = out.getValue();
      if (hstring == null) {
         return "";
      } else {
         String var5;
         try {
            var5 = readString(hstring);
         } finally {
            WinRt.Combase.INSTANCE.WindowsDeleteString(hstring);
         }

         return var5;
      }
   }

   static int getInt32(Pointer self, int slot, String step) {
      IntByReference out = new IntByReference();
      check(invoke(self, slot, out), step);
      return out.getValue();
   }

   static long getInt64(Pointer self, int slot, String step) {
      LongByReference out = new LongByReference();
      check(invoke(self, slot, out), step);
      return out.getValue();
   }

   static double getDouble(Pointer self, int slot, String step) {
      DoubleByReference out = new DoubleByReference();
      check(invoke(self, slot, out), step);
      return out.getValue();
   }

   static Pointer awaitObject(Pointer operation, long timeoutMs, String step) {
      await(operation, timeoutMs, step);
      PointerByReference out = new PointerByReference();
      check(invoke(operation, 8, out), step + " results");
      return out.getValue();
   }

   static boolean awaitBoolean(Pointer operation, long timeoutMs, String step) {
      await(operation, timeoutMs, step);
      ByteByReference out = new ByteByReference();
      check(invoke(operation, 8, out), step + " results");
      return out.getValue() != 0;
   }

   static int awaitUInt32(Pointer operation, long timeoutMs, String step) {
      await(operation, timeoutMs, step);
      IntByReference out = new IntByReference();
      check(invoke(operation, 8, out), step + " results");
      return out.getValue();
   }

   private static void await(Pointer operation, long timeoutMs, String step) {
      if (operation == null) {
         throw new WinRt.WinRtException(step + " returned no operation", -2147467260);
      } else {
         Pointer info = queryInterface(operation, IID_ASYNC_INFO, step + " IAsyncInfo");

         try {
            long deadline = System.nanoTime() + timeoutMs * 1000000L;
            long pause = 1L;

            while (true) {
               int status = getInt32(info, 7, step + " status");
               if (status == 1) {
                  return;
               }

               if (status == 2) {
                  throw new WinRt.WinRtException(step + " canceled", -2147467260);
               }

               if (status != 0) {
                  IntByReference code = new IntByReference();
                  int hresult = invoke(info, 8, code);
                  throw new WinRt.WinRtException(step, hresult < 0 ? hresult : code.getValue());
               }

               if (System.nanoTime() - deadline >= 0L) {
                  invoke(info, 9);
                  throw new WinRt.WinRtException(step + " timed out", -2147023436);
               }

               try {
                  Thread.sleep(pause);
               } catch (InterruptedException var15) {
                  invoke(info, 9);
                  Thread.currentThread().interrupt();
                  throw new WinRt.WinRtException(step + " interrupted", -2147467260);
               }

               pause = Math.min(pause + 1L, 5L);
            }
         } finally {
            release(info);
         }
      }
   }

   private static Pointer createString(String text) {
      PointerByReference out = new PointerByReference();
      check(WinRt.Combase.INSTANCE.WindowsCreateString(new WString(text), text.length(), out), "WindowsCreateString");
      return out.getValue();
   }

   private static String readString(Pointer hstring) {
      IntByReference length = new IntByReference();
      Pointer buffer = WinRt.Combase.INSTANCE.WindowsGetStringRawBuffer(hstring, length);
      int count = length.getValue();
      return buffer != null && count > 0 ? new String(buffer.getCharArray(0L, count)) : "";
   }

   private static Pointer owned(Pointer pointer, String step) {
      if (pointer == null) {
         throw new WinRt.WinRtException(step + " returned null", -2147467262);
      } else {
         return pointer;
      }
   }

   private static Object[] append(Object[] args, Object last) {
      Object[] all = new Object[args.length + 1];
      System.arraycopy(args, 0, all, 0, args.length);
      all[args.length] = last;
      return all;
   }

   @Environment(EnvType.CLIENT)
   interface Combase extends StdCallLibrary {
      WinRt.Combase INSTANCE = (WinRt.Combase)Native.load("combase", WinRt.Combase.class);

      int RoInitialize(int var1);

      void RoUninitialize();

      int RoGetActivationFactory(Pointer var1, Pointer var2, PointerByReference var3);

      int WindowsCreateString(WString var1, int var2, PointerByReference var3);

      int WindowsDeleteString(Pointer var1);

      Pointer WindowsGetStringRawBuffer(Pointer var1, IntByReference var2);
   }

   @Environment(EnvType.CLIENT)
   static final class Refs implements AutoCloseable {
      private final ArrayDeque<Pointer> held = new ArrayDeque<>();

      Pointer add(Pointer pointer) {
         if (pointer != null) {
            this.held.push(pointer);
         }

         return pointer;
      }

      @Override
      public void close() {
         while (!this.held.isEmpty()) {
            WinRt.release(this.held.pop());
         }
      }
   }

   @Environment(EnvType.CLIENT)
   static final class WinRtException extends RuntimeException {
      private static final long serialVersionUID = 1L;
      final int hresult;

      WinRtException(String step, int hresult) {
         super(step + " failed (0x" + Integer.toHexString(hresult).toUpperCase(Locale.ROOT) + ")", null, false, false);
         this.hresult = hresult;
      }
   }
}
