package net.spectre.client.module.impl.misc.noinvleak;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.GDI32;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HBITMAP;
import com.sun.jna.platform.win32.WinDef.HDC;
import com.sun.jna.platform.win32.WinDef.HINSTANCE;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.POINT;
import com.sun.jna.platform.win32.WinGDI.BITMAPINFO;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.WinUser.BLENDFUNCTION;
import com.sun.jna.platform.win32.WinUser.SIZE;
import com.sun.jna.platform.win32.WinUser.WNDCLASSEX;
import com.sun.jna.platform.win32.WinUser.WindowProc;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.glfw.GLFWNativeWin32;

@Environment(EnvType.CLIENT)
final class OverlayWindow {
   private static final String CLASS_NAME = "SpectreNoInvLeakOverlay";
   private static final int WS_EX_TOOLWINDOW = 128;
   private static final int WS_EX_NOACTIVATE = 134217728;
   private static final int WDA_EXCLUDEFROMCAPTURE = 17;
   private static final WindowProc PROC = (hwnd, message, wParam, lParam) -> User32.INSTANCE.DefWindowProc(hwnd, message, wParam, lParam);
   private static boolean classRegistered;
   private final POINT destination = new POINT();
   private final POINT source = new POINT(0, 0);
   private final SIZE size = new SIZE();
   private final BLENDFUNCTION blend = new BLENDFUNCTION();
   private HWND hwnd;
   private HDC memory;
   private HBITMAP bitmap;
   private HANDLE previousBitmap;
   private Pointer bitsPointer;
   private IntBuffer bits;
   private int capacityWidth;
   private int capacityHeight;
   private boolean visible;

   OverlayWindow(long glfwWindow) {
      HINSTANCE instance = Kernel32.INSTANCE.GetModuleHandle(null);
      if (!classRegistered) {
         WNDCLASSEX cls = new WNDCLASSEX();
         cls.lpfnWndProc = PROC;
         cls.hInstance = instance;
         cls.lpszClassName = "SpectreNoInvLeakOverlay";
         if (User32.INSTANCE.RegisterClassEx(cls).intValue() == 0) {
            throw failure("RegisterClassEx");
         }

         classRegistered = true;
      }

      HWND owner = new HWND(new Pointer(GLFWNativeWin32.glfwGetWin32Window(glfwWindow)));
      this.hwnd = User32.INSTANCE.CreateWindowEx(134742176, "SpectreNoInvLeakOverlay", "", Integer.MIN_VALUE, 0, 0, 1, 1, owner, null, instance, null);
      if (this.hwnd == null) {
         throw failure("CreateWindowEx");
      } else if (!OverlayWindow.Affinity.INSTANCE.SetWindowDisplayAffinity(this.hwnd, 17)) {
         RuntimeException failure = failure("SetWindowDisplayAffinity");
         this.destroy();
         throw failure;
      } else {
         this.blend.BlendOp = 0;
         this.blend.SourceConstantAlpha = -1;
         this.blend.AlphaFormat = 1;
      }
   }

   IntBuffer pixels(int width, int height) {
      if (width > this.capacityWidth || height > this.capacityHeight) {
         int nextWidth = Math.max(width, this.capacityWidth);
         int nextHeight = Math.max(height, this.capacityHeight);
         this.releaseBitmap();
         BITMAPINFO info = new BITMAPINFO();
         info.bmiHeader.biWidth = nextWidth;
         info.bmiHeader.biHeight = -nextHeight;
         info.bmiHeader.biPlanes = 1;
         info.bmiHeader.biBitCount = 32;
         info.bmiHeader.biCompression = 0;
         PointerByReference bitsOut = new PointerByReference();
         this.bitmap = GDI32.INSTANCE.CreateDIBSection(null, info, 0, bitsOut, null, 0);
         if (this.bitmap == null) {
            throw failure("CreateDIBSection");
         }

         if (this.memory == null) {
            this.memory = GDI32.INSTANCE.CreateCompatibleDC(null);
            if (this.memory == null) {
               throw failure("CreateCompatibleDC");
            }
         }

         this.previousBitmap = GDI32.INSTANCE.SelectObject(this.memory, this.bitmap);
         this.capacityWidth = nextWidth;
         this.capacityHeight = nextHeight;
         this.bitsPointer = bitsOut.getValue();
         this.bits = this.bitsPointer.getByteBuffer(0L, (long)nextWidth * nextHeight * 4L).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
      }

      this.bitsPointer.setMemory(0L, (long)this.capacityWidth * height * 4L, (byte)0);
      return this.bits;
   }

   int stride() {
      return this.capacityWidth;
   }

   void present(int screenX, int screenY, int width, int height) {
      this.destination.x = screenX;
      this.destination.y = screenY;
      this.size.cx = width;
      this.size.cy = height;
      if (!User32.INSTANCE.UpdateLayeredWindow(this.hwnd, null, this.destination, this.size, this.memory, this.source, 0, this.blend, 2)) {
         throw failure("UpdateLayeredWindow");
      } else {
         if (!this.visible) {
            User32.INSTANCE.ShowWindow(this.hwnd, 4);
            this.visible = true;
         }
      }
   }

   void move(int screenX, int screenY) {
      User32.INSTANCE.SetWindowPos(this.hwnd, null, screenX, screenY, 0, 0, 21);
   }

   void hide() {
      if (this.visible) {
         User32.INSTANCE.ShowWindow(this.hwnd, 0);
         this.visible = false;
      }
   }

   void destroy() {
      this.releaseBitmap();
      if (this.memory != null) {
         GDI32.INSTANCE.DeleteDC(this.memory);
         this.memory = null;
      }

      if (this.hwnd != null) {
         User32.INSTANCE.DestroyWindow(this.hwnd);
         this.hwnd = null;
      }

      this.visible = false;
   }

   private void releaseBitmap() {
      if (this.bitmap != null) {
         if (this.previousBitmap != null) {
            GDI32.INSTANCE.SelectObject(this.memory, this.previousBitmap);
         }

         GDI32.INSTANCE.DeleteObject(this.bitmap);
         this.bitmap = null;
         this.previousBitmap = null;
         this.bitsPointer = null;
         this.bits = null;
         this.capacityWidth = 0;
         this.capacityHeight = 0;
      }
   }

   private static RuntimeException failure(String call) {
      return new IllegalStateException(call + " failed with error " + Kernel32.INSTANCE.GetLastError());
   }

   @Environment(EnvType.CLIENT)
   private interface Affinity extends StdCallLibrary {
      OverlayWindow.Affinity INSTANCE = (OverlayWindow.Affinity)Native.load("user32", OverlayWindow.Affinity.class, W32APIOptions.DEFAULT_OPTIONS);

      boolean SetWindowDisplayAffinity(HWND var1, int var2);
   }
}
