package net.spectre.client.webhook;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public record WebhookConfig(String url, String username, String avatarUrl) {
   public static final String DEFAULT_USERNAME = "Spectre";
   public static final String DEFAULT_AVATAR_URL = "https://i.imgur.com/W3cTqvc.png";

   public WebhookConfig(String url, String username, String avatarUrl) {
      url = normalizeRequired(url, "url");
      username = normalizeOptional(username, "Spectre");
      avatarUrl = normalizeOptional(avatarUrl, "https://i.imgur.com/W3cTqvc.png");
      this.url = url;
      this.username = username;
      this.avatarUrl = avatarUrl;
   }

   public WebhookConfig(String url) {
      this(url, "Spectre", "https://i.imgur.com/W3cTqvc.png");
   }

   private static String normalizeRequired(String value, String fieldName) {
      String normalized = normalizeOptional(value, null);
      if (normalized == null) {
         throw new IllegalArgumentException(fieldName + " cannot be blank");
      } else {
         return normalized;
      }
   }

   private static String normalizeOptional(String value, String fallback) {
      if (value == null) {
         return fallback;
      } else {
         String normalized = value.trim();
         return normalized.isEmpty() ? fallback : normalized;
      }
   }
}
