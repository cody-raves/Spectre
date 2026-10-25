package net.spectre.client.module.impl.render.character.kit;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class BakedDesign extends CharacterDesign {
   private final BakedDesign.Source source;

   public BakedDesign(String id, String name, BakedDesign.Source source) {
      super(id, name);
      this.source = source;
   }

   public static BakedDesign builtIn(String id, String name, String resource) {
      return new BakedDesign(id, name, () -> {
         InputStream in = BakedDesign.class.getResourceAsStream(resource);
         if (in == null) {
            throw new FileNotFoundException(resource);
         } else {
            return in;
         }
      });
   }

   InputStream open() throws IOException {
      return this.source.open();
   }

   @Override
   protected void define(Rig rig) {
      throw new UnsupportedOperationException(this.id() + " is baked and has no rig");
   }

   @Environment(EnvType.CLIENT)
   public interface Source {
      InputStream open() throws IOException;
   }
}
