package net.spectre.client.module.impl.render.character.kit;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class CharacterModel {
   public final String id;
   public final String name;
   public final Detail detail;
   public final float scale;
   public final PartMesh[] parts;
   public final int motionSlots;
   public final int atlasWidth;
   public final int atlasHeight;
   public final int[][] atlasMips;
   public final float whiteU;
   public final float whiteV;
   public final float minY;
   public final float maxY;
   public final float hipY;
   public final boolean firstPersonArms;
   public final long buildMillis;

   CharacterModel(
      String id,
      String name,
      Detail detail,
      float scale,
      PartMesh[] parts,
      int motionSlots,
      int atlasWidth,
      int atlasHeight,
      int[][] atlasMips,
      float whiteU,
      float whiteV,
      float minY,
      float maxY,
      float hipY,
      boolean firstPersonArms,
      long buildMillis
   ) {
      this.id = id;
      this.name = name;
      this.detail = detail;
      this.scale = scale;
      this.parts = parts;
      this.motionSlots = motionSlots;
      this.atlasWidth = atlasWidth;
      this.atlasHeight = atlasHeight;
      this.atlasMips = atlasMips;
      this.whiteU = whiteU;
      this.whiteV = whiteV;
      this.minY = minY;
      this.maxY = maxY;
      this.hipY = hipY;
      this.firstPersonArms = firstPersonArms;
      this.buildMillis = buildMillis;
   }

   public int triangles() {
      int n = 0;

      for (PartMesh p : this.parts) {
         n += p.triangles();
      }

      return n;
   }

   public PartMesh part(Bone bone) {
      for (PartMesh p : this.parts) {
         if (!p.extra && p.bone == bone) {
            return p;
         }
      }

      return null;
   }
}
