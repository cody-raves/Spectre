package net.spectre.client.account;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class Accounts {
   private static final Logger LOGGER = LoggerFactory.getLogger("Spectre");
   private static final Accounts INSTANCE = new Accounts();
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final int CONFIG_VERSION = 1;
   private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
   private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
      Thread thread = new Thread(runnable, "Spectre Accounts");
      thread.setDaemon(true);
      return thread;
   });
   private final GameSession session = new GameSession();
   private final List<Account> accounts = new ArrayList<>();
   private volatile List<Account> snapshot = List.of();
   private volatile String activeId;
   private volatile boolean busy;
   private volatile String status = "";
   private volatile Thread microsoftSignIn;
   private volatile boolean loaded;
   private boolean readOnly;

   private Accounts() {
   }

   public static Accounts get() {
      return INSTANCE;
   }

   public List<Account> list() {
      this.ensureLoaded();
      return this.snapshot;
   }

   public Account active() {
      String sessionUuid = Minecraft.getInstance().getUser().getProfileId().toString();
      List<Account> all = this.list();
      String lastSwitched = this.activeId;

      for (Account account : all) {
         if (account.id().equals(lastSwitched) && account.uuid().equals(sessionUuid)) {
            return account;
         }
      }

      for (Account accountx : all) {
         if (accountx.uuid().equals(sessionUuid)) {
            return accountx;
         }
      }

      return null;
   }

   public CompletableFuture<Account> addCracked(String username) {
      String name = username == null ? "" : username.trim();
      return !VALID_NAME.matcher(name).matches()
         ? this.failed("A cracked name is 1-16 letters, digits or underscores")
         : this.signIn(AccountType.CRACKED, null, "Logging in as " + name + "...", () -> offline(name));
   }

   public CompletableFuture<Account> addSession(String token) {
      String accessToken = token == null ? "" : token.trim();
      return accessToken.isEmpty()
         ? this.failed("Paste an access token first")
         : this.signIn(AccountType.SESSION, null, "Checking the session token...", () -> SessionLogin.signIn(accessToken));
   }

   public CompletableFuture<Account> addMicrosoft() {
      return this.signIn(AccountType.MICROSOFT, null, "Opening Microsoft sign-in...", this::microsoftBrowserSignIn);
   }

   public void cancelMicrosoft() {
      Thread signIn = this.microsoftSignIn;
      if (signIn != null) {
         signIn.interrupt();
      }
   }

   public CompletableFuture<Void> login(Account account) {
      Account saved = this.find(account);
      if (saved == null) {
         return this.failed("That account is no longer saved");
      } else {
         String who = saved.name().isEmpty() ? saved.type().label() + " account" : saved.name();
         return this.signIn(saved.type(), saved.id(), "Logging in as " + who + "...", () -> {
            return switch (saved.type()) {
               case CRACKED -> offline(saved.name());
               case SESSION -> SessionLogin.signIn(saved.secret());
               case MICROSOFT -> MicrosoftLogin.minecraft(MicrosoftLogin.refresh(saved.secret()));
            };
         }).thenAccept(signedIn -> {});
      }
   }

   public synchronized void remove(Account account) {
      int index = this.indexOf(account);
      if (index >= 0) {
         this.accounts.remove(index);
         this.publish();
         this.save();
      }
   }

   public boolean busy() {
      return this.busy;
   }

   public String status() {
      return this.status;
   }

   private static Credentials offline(String name) {
      return new Credentials(name, UUIDUtil.createOfflinePlayerUUID(name), "", "");
   }

   private Credentials microsoftBrowserSignIn() throws InterruptedException {
      this.microsoftSignIn = Thread.currentThread();

      Credentials var3;
      try {
         MicrosoftLogin.DeviceCode code = MicrosoftLogin.requestDeviceCode();
         this.status = "Waiting for Microsoft sign-in in your browser... (code "
            + code.userCode()
            + " at "
            + code.verificationUri().replaceFirst("^https?://", "")
            + ")";
         Util.getPlatform().openUri(code.directUri());
         MicrosoftLogin.MsaToken token = MicrosoftLogin.awaitApproval(code);
         this.status = "Signing in to Xbox Live...";
         var3 = MicrosoftLogin.minecraft(token);
      } finally {
         this.microsoftSignIn = null;
      }

      return var3;
   }

   private CompletableFuture<Account> signIn(AccountType type, String savedId, String progress, Accounts.SignIn blocking) {
      synchronized (this) {
         if (this.busy) {
            return CompletableFuture.failedFuture(new AccountException("Another account action is still running"));
         }

         if (inGame()) {
            return this.failed("Leave the world before switching accounts");
         }

         this.busy = true;
      }

      this.ensureLoaded();
      this.status = progress;
      CompletableFuture<Account> result = new CompletableFuture<>();
      this.worker.execute(() -> {
         Thread.interrupted();

         try {
            Credentials credentials = blocking.run();
            GameSession.Prepared prepared = this.session.prepare(type, credentials);
            Minecraft.getInstance().execute(() -> this.finish(type, savedId, credentials, prepared, result));
         } catch (InterruptedException var7) {
            this.settleLater(result, null, new AccountException(type == AccountType.MICROSOFT ? "Microsoft sign-in cancelled" : "Login cancelled"));
         } catch (AccountException var8) {
            LOGGER.warn("[Spectre] {} login failed: {}", type.label(), var8.getMessage());
            this.settleLater(result, null, var8);
         } catch (RuntimeException var9) {
            LOGGER.warn("[Spectre] {} login failed", type.label(), var9);
            this.settleLater(result, null, new AccountException("Login failed: " + var9.getMessage(), var9));
         }
      });
      return result;
   }

   private void finish(AccountType type, String savedId, Credentials credentials, GameSession.Prepared prepared, CompletableFuture<Account> result) {
      Account account;
      synchronized (this) {
         account = this.remember(type, savedId, credentials);
      }

      if (inGame()) {
         this.settle(result, null, new AccountException("Joined a world before the switch finished - leave it and log in again"));
      } else {
         try {
            this.session.apply(prepared);
         } catch (RuntimeException var9) {
            LOGGER.error("[Spectre] Switching the session to {} failed", account, var9);
            this.settle(result, null, new AccountException("Couldn't switch the game to " + credentials.name() + ": " + var9.getMessage(), var9));
            return;
         }

         this.activeId = account.id();
         LOGGER.info("[Spectre] Switched session to {}", account);
         this.status = "Logged in as " + credentials.name();
         this.settle(result, account, null);
      }
   }

   private void settleLater(CompletableFuture<Account> result, Account account, AccountException error) {
      Minecraft.getInstance().execute(() -> this.settle(result, account, error));
   }

   private void settle(CompletableFuture<Account> result, Account account, AccountException error) {
      if (error != null) {
         this.status = error.getMessage();
      }

      this.busy = false;
      if (error != null) {
         result.completeExceptionally(error);
      } else {
         result.complete(account);
      }
   }

   private Account remember(AccountType type, String savedId, Credentials credentials) {
      String uuid = credentials.uuid().toString();
      int index = -1;

      for (int i = 0; i < this.accounts.size(); i++) {
         Account existing = this.accounts.get(i);
         boolean same = savedId != null
            ? existing.id().equals(savedId)
            : existing.type() == type && (type == AccountType.CRACKED ? existing.name().equalsIgnoreCase(credentials.name()) : existing.uuid().equals(uuid));
         if (same) {
            index = i;
            break;
         }
      }

      if (index < 0 && savedId != null) {
         return new Account(savedId, type, credentials.name(), uuid, credentials.secret());
      } else {
         Account account = index >= 0
            ? this.accounts.get(index).withLogin(credentials.name(), uuid, credentials.secret())
            : new Account(UUID.randomUUID().toString(), type, credentials.name(), uuid, credentials.secret());
         if (index >= 0) {
            this.accounts.set(index, account);
         } else {
            this.accounts.add(account);
         }

         this.publish();
         this.save();
         return account;
      }
   }

   private static boolean inGame() {
      Minecraft mc = Minecraft.getInstance();
      return mc.level != null || mc.getConnection() != null;
   }

   private <T> CompletableFuture<T> failed(String message) {
      this.status = message;
      return CompletableFuture.failedFuture(new AccountException(message));
   }

   private synchronized Account find(Account account) {
      int index = this.indexOf(account);
      return index < 0 ? null : this.accounts.get(index);
   }

   private int indexOf(Account account) {
      this.ensureLoaded();
      if (account == null) {
         return -1;
      } else {
         for (int i = 0; i < this.accounts.size(); i++) {
            if (this.accounts.get(i).id().equals(account.id())) {
               return i;
            }
         }

         return -1;
      }
   }

   private void publish() {
      this.snapshot = List.copyOf(this.accounts);
   }

   private void ensureLoaded() {
      if (!this.loaded) {
         this.load();
      }
   }

   private synchronized void load() {
      if (!this.loaded) {
         this.loaded = true;
         Path path = configPath();
         if (Files.isRegularFile(path)) {
            try {
               label101: {
                  try (Reader reader = Files.newBufferedReader(path)) {
                     JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                     int version = root.has("version") ? root.get("version").getAsInt() : 0;
                     if (version > 1) {
                        this.readOnly = true;
                        LOGGER.warn("[Spectre] Account list version {} is newer than supported {}; skipping load", version, 1);
                        return;
                     }

                     if (root.has("accounts") && root.get("accounts").isJsonArray()) {
                        Iterator var5 = root.getAsJsonArray("accounts").iterator();

                        while (true) {
                           if (!var5.hasNext()) {
                              break label101;
                           }

                           JsonElement element = (JsonElement)var5.next();
                           if (element.isJsonObject()) {
                              Account account = read(element.getAsJsonObject());
                              if (account != null) {
                                 this.accounts.add(account);
                              }
                           }
                        }
                     }
                  }

                  return;
               }
            } catch (RuntimeException | IOException var10) {
               LOGGER.warn("[Spectre] Failed to load the account list; preserving the file", var10);
               this.accounts.clear();
               this.readOnly = true;
            }

            this.publish();
         }
      }
   }

   private static Account read(JsonObject json) {
      String typeName = AuthHttp.text(json, "type");
      AccountType type = null;

      for (AccountType candidate : AccountType.values()) {
         if (candidate.name().equalsIgnoreCase(typeName)) {
            type = candidate;
         }
      }

      if (type == null) {
         return null;
      } else {
         String id = AuthHttp.text(json, "id");
         String name = AuthHttp.text(json, "name");
         String uuid = AuthHttp.text(json, "uuid");
         String token = AuthHttp.text(json, "token");
         if (name == null) {
            name = "";
         }

         if (type == AccountType.CRACKED ? VALID_NAME.matcher(name).matches() : token != null && !token.isEmpty()) {
            if (type == AccountType.CRACKED) {
               uuid = UUIDUtil.createOfflinePlayerUUID(name).toString();
            }

            return new Account(
               id != null && !id.isEmpty() ? id : UUID.randomUUID().toString(), type, name, uuid == null ? "" : uuid, token == null ? "" : token
            );
         } else {
            return null;
         }
      }
   }

   private void save() {
      if (!this.readOnly) {
         JsonObject root = new JsonObject();
         root.addProperty("version", 1);
         JsonArray list = new JsonArray();

         for (Account account : this.accounts) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", account.id());
            entry.addProperty("type", account.type().name());
            entry.addProperty("name", account.name());
            entry.addProperty("uuid", account.uuid());
            if (!account.secret().isEmpty()) {
               entry.addProperty("token", account.secret());
            }

            list.add(entry);
         }

         root.add("accounts", list);
         Path path = configPath();

         try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");

            try (Writer writer = Files.newBufferedWriter(tmp)) {
               GSON.toJson(root, writer);
            }

            try {
               Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException var9) {
               Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
         } catch (IOException var11) {
            LOGGER.warn("[Spectre] Failed to save the account list", var11);
         }
      }
   }

   private static Path configPath() {
      return FabricLoader.getInstance().getConfigDir().resolve("spectre").resolve("accounts.json");
   }

   @Environment(EnvType.CLIENT)
   private interface SignIn {
      Credentials run() throws InterruptedException;
   }
}
