package net.spectre.client.webhook;

import com.google.gson.JsonObject;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public record WebhookEmbed(String title, String description, Integer color, String thumbnailUrl, String imageUrl, String footerText, String footerIconUrl) {
   public JsonObject toJson() {
      JsonObject json = new JsonObject();
      add(json, "title", this.title);
      add(json, "description", this.description);
      if (this.color != null) {
         json.addProperty("color", this.color);
      }

      addNestedUrl(json, "thumbnail", this.thumbnailUrl);
      addNestedUrl(json, "image", this.imageUrl);
      if (this.footerText != null || this.footerIconUrl != null) {
         JsonObject footer = new JsonObject();
         add(footer, "text", this.footerText);
         add(footer, "icon_url", this.footerIconUrl);
         json.add("footer", footer);
      }

      return json;
   }

   private static void addNestedUrl(JsonObject root, String name, String url) {
      if (url != null && !url.isBlank()) {
         JsonObject object = new JsonObject();
         object.addProperty("url", url);
         root.add(name, object);
      }
   }

   private static void add(JsonObject root, String name, String value) {
      if (value != null && !value.isBlank()) {
         root.addProperty(name, value);
      }
   }
}
