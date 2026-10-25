package net.spectre.client.module.impl.render.character;

import com.mojang.blaze3d.platform.Lighting.Entry;
import net.spectre.client.module.impl.render.character.kit.Lights;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.CardinalLighting;
import org.joml.Matrix4f;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class CharacterLighting {
   private static final Vector3f LIGHT_0 = new Vector3f(0.2F, 1.0F, -0.7F).normalize();
   private static final Vector3f LIGHT_1 = new Vector3f(-0.2F, 1.0F, 0.7F).normalize();
   private static final Lights NETHER = new Lights(0.2F, 1.0F, -0.7F, -0.2F, -1.0F, 0.7F);
   private static final Lights INVENTORY = new Lights(0.2F, -1.0F, 1.0F, -0.2F, -1.0F, 0.0F);
   private static final Lights ITEMS_FLAT = transformed(new Matrix4f().rotationY((float) (-Math.PI / 8)).rotateX((float) (Math.PI * 3.0 / 4.0)));
   private static final Lights ITEMS_3D = transformed(
      new Matrix4f().scaling(1.0F, -1.0F, 1.0F).rotateYXZ(1.0821041F, 3.2375858F, 0.0F).rotateYXZ((float) (-Math.PI / 8), (float) (Math.PI * 3.0 / 4.0), 0.0F)
   );
   private static Entry entry = Entry.LEVEL;

   private CharacterLighting() {
   }

   public static void setup(Entry current) {
      entry = current;
   }

   static Lights current() {
      return switch (entry) {
         case LEVEL -> nether() ? NETHER : Lights.WORLD;
         case ITEMS_FLAT -> ITEMS_FLAT;
         case ITEMS_3D -> ITEMS_3D;
         case ENTITY_IN_UI, PLAYER_SKIN -> INVENTORY;
      };
   }

   private static boolean nether() {
      ClientLevel level = Minecraft.getInstance().level;
      return level != null && level.dimensionType().cardinalLightType() == CardinalLighting.Type.NETHER;
   }

   private static Lights transformed(Matrix4f matrix) {
      Vector3f a = matrix.transformDirection(LIGHT_0, new Vector3f());
      Vector3f b = matrix.transformDirection(LIGHT_1, new Vector3f());
      return new Lights(a.x, a.y, a.z, b.x, b.y, b.z);
   }
}
