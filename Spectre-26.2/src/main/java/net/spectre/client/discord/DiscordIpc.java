package net.spectre.client.discord;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.sun.jna.Platform;
import java.io.Closeable;
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.SocketTimeoutException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class DiscordIpc implements Closeable {
   private static final int OP_HANDSHAKE = 0;
   private static final int OP_FRAME = 1;
   private static final int OP_CLOSE = 2;
   private static final int OP_PING = 3;
   private static final int OP_PONG = 4;
   private static final int RPC_VERSION = 1;
   private static final int SOCKET_SLOTS = 10;
   private static final String[] UNIX_TEMP_VARIABLES = new String[]{"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"};
   private static final boolean WINDOWS = Platform.isWindows();
   private static final long REPLY_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(5L);
   private static final long READ_POLL_NANOS = TimeUnit.MILLISECONDS.toNanos(20L);
   private static final int MAX_FRAME_BYTES = 1048576;
   private static final long PID = ProcessHandle.current().pid();
   private static final Gson GSON = new GsonBuilder().serializeNulls().create();
   private final DiscordIpc.Pipe pipe;
   private final ByteBuffer header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);

   private DiscordIpc(DiscordIpc.Pipe pipe) {
      this.pipe = pipe;
   }

   public static DiscordIpc connect(String applicationId) throws IOException {
      DiscordIpc.Pipe pipe = WINDOWS ? openWindowsPipe() : openUnixSocket();
      if (pipe == null) {
         return null;
      } else {
         DiscordIpc ipc = new DiscordIpc(pipe);

         try {
            ipc.handshake(applicationId);
            return ipc;
         } catch (RuntimeException | IOException var4) {
            ipc.close();
            throw var4;
         }
      }
   }

   public void setActivity(JsonObject activity) throws IOException, DiscordIpc.RefusedException {
      String nonce = UUID.randomUUID().toString();
      JsonObject args = new JsonObject();
      args.addProperty("pid", PID);
      args.add("activity", (JsonElement)(activity != null ? activity : JsonNull.INSTANCE));
      JsonObject command = new JsonObject();
      command.addProperty("cmd", "SET_ACTIVITY");
      command.add("args", args);
      command.addProperty("nonce", nonce);
      this.send(1, command);
      long deadline = System.nanoTime() + REPLY_TIMEOUT_NANOS;

      JsonObject reply;
      do {
         reply = this.answer(this.readFrame(deadline, true));
      } while (reply == null || !nonce.equals(string(reply, "nonce")));

      if ("ERROR".equals(string(reply, "evt"))) {
         throw new DiscordIpc.RefusedException(errorMessage(reply));
      }
   }

   public void poll() throws IOException {
      DiscordIpc.Frame frame;
      while ((frame = this.readFrame(System.nanoTime() + REPLY_TIMEOUT_NANOS, false)) != null) {
         this.answer(frame);
      }
   }

   @Override
   public void close() {
      try {
         this.pipe.close();
      } catch (IOException var2) {
      }
   }

   private void handshake(String applicationId) throws IOException {
      JsonObject hello = new JsonObject();
      hello.addProperty("v", 1);
      hello.addProperty("client_id", applicationId);
      this.send(0, hello);
      long deadline = System.nanoTime() + REPLY_TIMEOUT_NANOS;

      while (true) {
         JsonObject reply = this.answer(this.readFrame(deadline, true));
         if (reply != null && "DISPATCH".equals(string(reply, "cmd"))) {
            String event = string(reply, "evt");
            if ("READY".equals(event)) {
               return;
            }

            if ("ERROR".equals(event)) {
               throw new IOException("Discord refused the handshake: " + errorMessage(reply));
            }
         }
      }
   }

   private JsonObject answer(DiscordIpc.Frame frame) throws IOException {
      return switch (frame.opcode()) {
         case 1 -> frame.json();
         case 2 -> throw new IOException("Discord closed the connection: " + errorMessage(frame.json()));
         case 3 -> {
            this.send(4, frame.body());
            yield null;
         }
         default -> null;
      };
   }

   private DiscordIpc.Frame readFrame(long deadline, boolean wait) throws IOException {
      this.header.clear();
      if (!wait && this.pipe.readWaiting(this.header) == 0) {
         return null;
      } else {
         this.readFully(this.header, deadline);
         this.header.flip();
         int opcode = this.header.getInt();
         int length = this.header.getInt();
         if (length >= 0 && length <= 1048576) {
            ByteBuffer body = ByteBuffer.allocate(length);
            this.readFully(body, deadline);
            return new DiscordIpc.Frame(opcode, body.array());
         } else {
            throw new IOException("Discord sent a frame of " + length + " bytes");
         }
      }
   }

   private void readFully(ByteBuffer buffer, long deadline) throws IOException {
      while (buffer.hasRemaining()) {
         if (this.pipe.readWaiting(buffer) <= 0) {
            if (System.nanoTime() - deadline >= 0L) {
               throw new SocketTimeoutException("Discord did not answer in time");
            }

            LockSupport.parkNanos(READ_POLL_NANOS);
         }
      }
   }

   private void send(int opcode, JsonObject body) throws IOException {
      this.send(opcode, GSON.toJson(body).getBytes(StandardCharsets.UTF_8));
   }

   private void send(int opcode, byte[] body) throws IOException {
      ByteBuffer frame = ByteBuffer.allocate(8 + body.length).order(ByteOrder.LITTLE_ENDIAN);
      frame.putInt(opcode).putInt(body.length).put(body).flip();
      this.pipe.write(frame, System.nanoTime() + REPLY_TIMEOUT_NANOS);
   }

   private static String errorMessage(JsonObject body) {
      JsonElement data = body.get("data");
      String message = string(data != null && data.isJsonObject() ? data.getAsJsonObject() : body, "message");
      return message != null ? message : body.toString();
   }

   private static String string(JsonObject object, String key) {
      JsonElement value = object.get(key);
      return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
   }

   private static DiscordIpc.Pipe openWindowsPipe() {
      for (int slot = 0; slot < 10; slot++) {
         try {
            return new DiscordIpc.WindowsPipe(new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + slot, "rw"));
         } catch (FileNotFoundException var2) {
         }
      }

      return null;
   }

   private static DiscordIpc.Pipe openUnixSocket() {
      for (Path folder : unixFolders()) {
         for (int slot = 0; slot < 10; slot++) {
            Path socket = folder.resolve("discord-ipc-" + slot);
            if (Files.exists(socket)) {
               try {
                  SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);

                  try {
                     channel.connect(UnixDomainSocketAddress.of(socket));
                     channel.configureBlocking(false);
                     return new DiscordIpc.UnixPipe(channel);
                  } catch (IOException var6) {
                     channel.close();
                     throw var6;
                  }
               } catch (IOException var7) {
               }
            }
         }
      }

      return null;
   }

   private static Set<Path> unixFolders() {
      Set<Path> roots = new LinkedHashSet<>();

      for (String variable : UNIX_TEMP_VARIABLES) {
         String value = System.getenv(variable);
         if (value != null && !value.isBlank()) {
            try {
               roots.add(Path.of(value));
            } catch (InvalidPathException var7) {
            }
         }
      }

      roots.add(Path.of("/tmp"));
      Set<Path> folders = new LinkedHashSet<>();

      for (Path root : roots) {
         folders.add(root);
         addSubfolders(folders, root.resolve("app"), "*");
         addSubfolders(folders, root, "snap.*");
      }

      return folders;
   }

   private static void addSubfolders(Set<Path> folders, Path parent, String glob) {
      if (Files.isDirectory(parent)) {
         try (DirectoryStream<Path> children = Files.newDirectoryStream(parent, glob)) {
            for (Path child : children) {
               if (Files.isDirectory(child)) {
                  folders.add(child);
               }
            }
         } catch (SecurityException | IOException var8) {
         }
      }
   }

   @Environment(EnvType.CLIENT)
   private record Frame(int opcode, byte[] body) {
      JsonObject json() throws IOException {
         try {
            JsonElement parsed = JsonParser.parseString(new String(this.body, StandardCharsets.UTF_8));
            if (parsed.isJsonObject()) {
               return parsed.getAsJsonObject();
            }
         } catch (JsonParseException var2) {
         }

         throw new IOException("Discord sent a frame that is not a JSON object");
      }
   }

   @Environment(EnvType.CLIENT)
   private interface Pipe extends Closeable {
      int readWaiting(ByteBuffer var1) throws IOException;

      void write(ByteBuffer var1, long var2) throws IOException;
   }

   @Environment(EnvType.CLIENT)
   public static final class RefusedException extends Exception {
      RefusedException(String message) {
         super(message);
      }
   }

   @Environment(EnvType.CLIENT)
   private record UnixPipe(SocketChannel channel) implements DiscordIpc.Pipe {
      @Override
      public int readWaiting(ByteBuffer into) throws IOException {
         int read = this.channel.read(into);
         if (read < 0) {
            throw new EOFException("Discord hung up");
         } else {
            return read;
         }
      }

      @Override
      public void write(ByteBuffer from, long deadline) throws IOException {
         while (from.hasRemaining()) {
            if (this.channel.write(from) <= 0) {
               if (System.nanoTime() - deadline >= 0L) {
                  throw new SocketTimeoutException("Discord stopped reading");
               }

               LockSupport.parkNanos(DiscordIpc.READ_POLL_NANOS);
            }
         }
      }

      @Override
      public void close() throws IOException {
         this.channel.close();
      }
   }

   @Environment(EnvType.CLIENT)
   private record WindowsPipe(RandomAccessFile file) implements DiscordIpc.Pipe {
      @Override
      public int readWaiting(ByteBuffer into) throws IOException {
         long waiting = this.file.length();
         if (waiting <= 0L) {
            return 0;
         } else {
            int read = this.file.read(into.array(), into.arrayOffset() + into.position(), (int)Math.min(waiting, (long)into.remaining()));
            if (read < 0) {
               throw new EOFException("Discord hung up");
            } else {
               into.position(into.position() + read);
               return read;
            }
         }
      }

      @Override
      public void write(ByteBuffer from, long deadline) throws IOException {
         this.file.write(from.array(), from.arrayOffset() + from.position(), from.remaining());
         from.position(from.limit());
      }

      @Override
      public void close() throws IOException {
         this.file.close();
      }
   }
}
