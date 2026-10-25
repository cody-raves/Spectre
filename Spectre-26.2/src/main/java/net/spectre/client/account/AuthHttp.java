package net.spectre.client.account;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
final class AuthHttp {
   private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8L)).followRedirects(Redirect.NEVER).build();
   private static final Duration TIMEOUT = Duration.ofSeconds(30L);
   private static final String USER_AGENT = "SpectreClient";

   private AuthHttp() {
   }

   static AuthHttp.Reply postForm(String url, Map<String, String> form) throws InterruptedException {
      String body = form.entrySet()
         .stream()
         .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
         .collect(Collectors.joining("&"));
      return send(url, HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/x-www-form-urlencoded").POST(BodyPublishers.ofString(body)));
   }

   static AuthHttp.Reply postJson(String url, String body, Map<String, String> headers) throws InterruptedException {
      Builder request = HttpRequest.newBuilder(URI.create(url))
         .header("Content-Type", "application/json")
         .POST(BodyPublishers.ofString(body, StandardCharsets.UTF_8));
      headers.forEach(request::header);
      return send(url, request);
   }

   static AuthHttp.Reply get(String url, String bearerToken) throws InterruptedException {
      return send(url, HttpRequest.newBuilder(URI.create(url)).header("Authorization", "Bearer " + bearerToken).GET());
   }

   static String text(JsonObject object, String key) {
      JsonElement value = object == null ? null : object.get(key);
      return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
   }

   static JsonObject object(JsonObject object, String key) {
      JsonElement value = object == null ? null : object.get(key);
      return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
   }

   private static AuthHttp.Reply send(String url, Builder request) throws InterruptedException {
      try {
         HttpResponse<String> response = CLIENT.send(
            request.timeout(TIMEOUT).header("Accept", "application/json").header("User-Agent", "SpectreClient").build(),
            BodyHandlers.ofString(StandardCharsets.UTF_8)
         );
         return new AuthHttp.Reply(response.statusCode(), parse(response.body()), response.headers());
      } catch (IOException var3) {
         throw new AccountException("Couldn't reach " + URI.create(url).getHost() + " - check your connection", var3);
      }
   }

   private static JsonObject parse(String body) {
      try {
         JsonElement root = JsonParser.parseString(body);
         return root.isJsonObject() ? root.getAsJsonObject() : new JsonObject();
      } catch (RuntimeException var2) {
         return new JsonObject();
      }
   }

   @Environment(EnvType.CLIENT)
   record Reply(int status, JsonObject json, HttpHeaders headers) {
      boolean ok() {
         return this.status >= 200 && this.status < 300;
      }

      String text(String key) {
         return AuthHttp.text(this.json, key);
      }
   }
}
