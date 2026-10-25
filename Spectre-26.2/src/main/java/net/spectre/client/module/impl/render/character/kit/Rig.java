package net.spectre.client.module.impl.render.character.kit;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class Rig {
   float scale = 1.0F;
   int outlineColor = -14017512;
   float outline = 0.2F;
   float occlusion = 0.55F;
   float flat;
   boolean firstPersonArms = true;
   final Part[] bones = new Part[Bone.values().length];
   final List<Part> extras = new ArrayList<>();

   public Rig scale(float scale) {
      if (scale > 0.05F && scale < 8.0F) {
         this.scale = scale;
         return this;
      } else {
         throw new IllegalArgumentException("scale must be between 0.05 and 8");
      }
   }

   public Rig outline(int argb, float thickness) {
      this.outlineColor = argb;
      this.outline = Math.max(0.0F, thickness);
      return this;
   }

   public Rig occlusion(float strength) {
      this.occlusion = Math.max(0.0F, Math.min(1.0F, strength));
      return this;
   }

   public Rig flatLight(float amount) {
      this.flat = Math.max(0.0F, Math.min(1.0F, amount));
      return this;
   }

   public Rig firstPersonArms(boolean show) {
      this.firstPersonArms = show;
      return this;
   }

   public Part part(Bone bone) {
      Part p = this.bones[bone.ordinal()];
      if (p == null) {
         p = new Part(bone.name(), bone, null, false);
         this.bones[bone.ordinal()] = p;
      }

      return p;
   }

   public Part extra(String name, Bone bone) {
      return this.extra(name, this.part(bone));
   }

   public Part extra(String name, Part parent) {
      if (name != null && !name.isEmpty()) {
         for (Part p : this.extras) {
            if (p.name.equals(name)) {
               throw new IllegalArgumentException("duplicate extra name: " + name);
            }
         }

         for (Bone b : Bone.values()) {
            if (b.name().equals(name)) {
               throw new IllegalArgumentException("extra name clashes with a bone: " + name);
            }
         }

         Part px = new Part(name, parent.bone, parent, true);
         this.extras.add(px);
         return px;
      } else {
         throw new IllegalArgumentException("extra needs a name");
      }
   }

   public float scale() {
      return this.scale;
   }
}
