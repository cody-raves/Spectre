package net.spectre.client.util.spotify;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.DWORD;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.WORD;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.WinUser.INPUT;
import com.sun.jna.platform.win32.WinUser.WNDENUMPROC;
import com.sun.jna.ptr.IntByReference;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class SpotifyWindow {
   private static final String EXECUTABLE = "spotify.exe";
   private static final String MAIN_CLASS = "Chrome_WidgetWin_0";
   private static final String CLASS_FAMILY = "Chrome_WidgetWin";
   private static final long PID_CACHE_MS = 30000L;
   private static final int PID_CACHE_MAX = 512;
   private static final long RECHECK_MS = 10000L;
   private static final String TRACK_SEPARATOR = " - ";
   private static final int KEYEVENTF_EXTENDEDKEY = 1;
   private static final int KEYEVENTF_KEYUP = 2;
   private final char[] textBuffer = new char[512];
   private final char[] classBuffer = new char[64];
   private final char[] pathBuffer = new char[1024];
   private final IntByReference pidOut = new IntByReference();
   private final IntByReference sizeInOut = new IntByReference();
   private final Map<Integer, Boolean> spotifyPids = new HashMap<>();
   private long pidCacheSince;
   private HWND window;
   private long foundAt;
   private HWND best;
   private int bestScore;
   private final WNDENUMPROC finder = this::consider;

   public String readTitle() {
      long now = System.currentTimeMillis();
      if (this.window != null && User32.INSTANCE.IsWindow(this.window) && this.isSpotify(this.processOf(this.window))) {
         String title = this.text(this.window);
         if (title.contains(" - ") || !title.isEmpty() && now - this.foundAt < 10000L) {
            return title;
         }
      }

      this.window = this.find();
      this.foundAt = now;
      return this.window == null ? null : this.text(this.window);
   }

   public static void tap(SpotifyWindow.MediaKey key) {
      send(key.virtualKey, 1);
      send(key.virtualKey, 3);
   }

   private HWND find() {
      long now = System.currentTimeMillis();
      if (now - this.pidCacheSince > 30000L || this.spotifyPids.size() > 512) {
         this.spotifyPids.clear();
         this.pidCacheSince = now;
      }

      this.best = null;
      this.bestScore = 0;
      User32.INSTANCE.EnumWindows(this.finder, Pointer.NULL);
      HWND found = this.best;
      this.best = null;
      return found;
   }

   private boolean consider(HWND hwnd, Pointer data) {
      if (!this.isSpotify(this.processOf(hwnd))) {
         return true;
      } else {
         String title = this.text(hwnd);
         if (!title.isEmpty() && !title.equals("Default IME") && !title.equals("MSCTFIME UI") && !title.startsWith("GDI+ Window")) {
            String windowClass = this.className(hwnd);
            int score = 1;
            if (windowClass.equals("Chrome_WidgetWin_0")) {
               score += 4;
            } else if (windowClass.startsWith("Chrome_WidgetWin")) {
               score += 2;
            }

            if (title.contains(" - ")) {
               score += 3;
            } else if (title.startsWith("Spotify")) {
               score++;
            }

            if (User32.INSTANCE.IsWindowVisible(hwnd)) {
               score++;
            }

            if (score > this.bestScore) {
               this.bestScore = score;
               this.best = hwnd;
            }

            return true;
         } else {
            return true;
         }
      }
   }

   private int processOf(HWND hwnd) {
      User32.INSTANCE.GetWindowThreadProcessId(hwnd, this.pidOut);
      return this.pidOut.getValue();
   }

   private boolean isSpotify(int pid) {
      if (pid <= 0) {
         return false;
      } else {
         Boolean cached = this.spotifyPids.get(pid);
         if (cached != null) {
            return cached;
         } else {
            boolean spotify = "spotify.exe".equals(this.imageName(pid));
            this.spotifyPids.put(pid, spotify);
            return spotify;
         }
      }
   }

   private String imageName(int pid) {
      HANDLE process = Kernel32.INSTANCE.OpenProcess(4096, false, pid);
      if (process == null) {
         return "";
      } else {
         String path;
         try {
            this.sizeInOut.setValue(this.pathBuffer.length);
            if (Kernel32.INSTANCE.QueryFullProcessImageName(process, 0, this.pathBuffer, this.sizeInOut)) {
               path = new String(this.pathBuffer, 0, Math.min(this.sizeInOut.getValue(), this.pathBuffer.length));
               int slash = path.lastIndexOf(92);
               return path.substring(slash + 1).toLowerCase(Locale.ROOT);
            }

            path = "";
         } finally {
            Kernel32.INSTANCE.CloseHandle(process);
         }

         return path;
      }
   }

   private String text(HWND hwnd) {
      int length = User32.INSTANCE.GetWindowText(hwnd, this.textBuffer, this.textBuffer.length);
      return length <= 0 ? "" : new String(this.textBuffer, 0, Math.min(length, this.textBuffer.length));
   }

   private String className(HWND hwnd) {
      int length = User32.INSTANCE.GetClassName(hwnd, this.classBuffer, this.classBuffer.length);
      return length <= 0 ? "" : new String(this.classBuffer, 0, Math.min(length, this.classBuffer.length));
   }

   private static void send(int virtualKey, int flags) {
      INPUT input = new INPUT();
      input.type = new DWORD(1L);
      input.input.setType("ki");
      input.input.ki.wVk = new WORD(virtualKey);
      input.input.ki.wScan = new WORD(0L);
      input.input.ki.dwFlags = new DWORD(flags);
      input.input.ki.time = new DWORD(0L);
      User32.INSTANCE.SendInput(new DWORD(1L), new INPUT[]{input}, input.size());
   }

   @Environment(EnvType.CLIENT)
   public static enum MediaKey {
      PLAY_PAUSE(179),
      NEXT(176),
      PREVIOUS(177);

      final int virtualKey;

      private MediaKey(int virtualKey) {
         this.virtualKey = virtualKey;
      }
   }
}
