package net.spectre.client.webhook;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import net.spectre.client.SpectreClientMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class WebhookService {
   private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8L)).build();

   public boolean send(WebhookConfig config, WebhookPayloadBuilder payloadBuilder) {
      JsonObject payload;
      try {
         payload = payloadBuilder.build(config);
      } catch (RuntimeException var7) {
         SpectreClientMod.LOGGER.warn("[Spectre] Failed to build webhook payload", var7);
         return false;
      }

      HttpRequest request = HttpRequest.newBuilder(URI.create(config.url()))
         .timeout(Duration.ofSeconds(10L))
         .header("Content-Type", "application/json")
         .POST(BodyPublishers.ofString(payload.toString()))
         .build();

      try {
         HttpResponse<Void> response = this.httpClient.send(request, BodyHandlers.discarding());
         int status = response.statusCode();
         if (status >= 200 && status < 300) {
            return true;
         }

         SpectreClientMod.LOGGER.warn("[Spectre] Webhook request failed with status {}", status);
      } catch (InterruptedException | IOException var8) {
         if (var8 instanceof InterruptedException) {
            Thread.currentThread().interrupt();
         }

         SpectreClientMod.LOGGER.warn("[Spectre] Webhook request failed", var8);
      }

      return false;
   }
}
