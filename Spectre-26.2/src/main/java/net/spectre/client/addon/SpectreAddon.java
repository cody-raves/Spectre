package net.spectre.client.addon;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;

@Environment(EnvType.CLIENT)
public abstract class SpectreAddon {
   private String name = this.getClass().getSimpleName();
   private String version = "";
   private List<String> authors = List.of();
   String packageName;

   public abstract void onInitialize();

   public void onRegisterCategories() {
   }

   public void onConfigLoaded() {
   }

   public abstract String getPackage();

   public String getWebsite() {
      return null;
   }

   public SpectreAddon.GithubRepo getRepo() {
      return null;
   }

   public final String name() {
      return this.name;
   }

   public final String version() {
      return this.version;
   }

   public final List<String> authors() {
      return this.authors;
   }

   final void bind(ModMetadata metadata) {
      this.name = metadata.getName();
      this.version = metadata.getVersion().getFriendlyString();
      List<String> names = new ArrayList<>(metadata.getAuthors().size());

      for (Person author : metadata.getAuthors()) {
         names.add(author.getName());
      }

      this.authors = List.copyOf(names);
   }

   @Environment(EnvType.CLIENT)
   public record GithubRepo(String owner, String name) {
      public String url() {
         return "https://github.com/" + this.owner + "/" + this.name;
      }
   }
}
