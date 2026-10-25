package net.spectre.client.module.impl.render.character.kit;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
final class Mesh {
   final float[] pos;
   final int vertexCount;
   final int[] tris;
   final int triCount;

   Mesh(float[] pos, int vertexCount, int[] tris, int triCount) {
      this.pos = pos;
      this.vertexCount = vertexCount;
      this.tris = tris;
      this.triCount = triCount;
   }
}
