package net.spectre.client.module.impl.render.character.kit;

import java.util.Arrays;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class Surface {
   public final float[] pos;
   public final float[] nrm;
   public final float[] uv;
   public final int[] color;
   public final byte[] glow;
   public final float[] flat;
   public final int[] quads;
   public final int triangles;
   public float[] weight;
   public float[] joint;

   Surface(float[] pos, float[] nrm, float[] uv, int[] color, byte[] glow, float[] flat, int[] quads, int triangles) {
      this.pos = pos;
      this.nrm = nrm;
      this.uv = uv;
      this.color = color;
      this.glow = glow;
      this.flat = flat;
      this.quads = quads;
      this.triangles = triangles;
   }

   public int vertexCount() {
      return this.pos.length / 3;
   }

   public int quadCount() {
      return this.quads.length / 4;
   }

   public boolean glows() {
      return this.glow != null;
   }

   public boolean flatLit() {
      return this.flat != null;
   }

   @Environment(EnvType.CLIENT)
   static final class Builder {
      float[] pos = new float[768];
      float[] nrm = new float[768];
      float[] uv = new float[512];
      int[] color = new int[256];
      byte[] glow = new byte[256];
      float[] flat = new float[256];
      boolean anyGlow;
      boolean anyFlat;
      int verts;
      int[] quads = new int[1024];
      int quadCount;
      int triangles;

      int vertex(float x, float y, float z, float nx, float ny, float nz, float u, float v, int argb, boolean glows) {
         return this.vertex(x, y, z, nx, ny, nz, u, v, argb, glows, 0.0F);
      }

      int vertex(float x, float y, float z, float nx, float ny, float nz, float u, float v, int argb, boolean glows, float flatLight) {
         if (this.verts == this.color.length) {
            int n = this.verts * 2;
            this.pos = Arrays.copyOf(this.pos, n * 3);
            this.nrm = Arrays.copyOf(this.nrm, n * 3);
            this.uv = Arrays.copyOf(this.uv, n * 2);
            this.color = Arrays.copyOf(this.color, n);
            this.glow = Arrays.copyOf(this.glow, n);
            this.flat = Arrays.copyOf(this.flat, n);
         }

         int i = this.verts++;
         this.pos[i * 3] = x;
         this.pos[i * 3 + 1] = y;
         this.pos[i * 3 + 2] = z;
         this.nrm[i * 3] = nx;
         this.nrm[i * 3 + 1] = ny;
         this.nrm[i * 3 + 2] = nz;
         this.uv[i * 2] = u;
         this.uv[i * 2 + 1] = v;
         this.color[i] = argb;
         this.glow[i] = (byte)(glows ? 1 : 0);
         this.flat[i] = flatLight;
         this.anyGlow |= glows;
         this.anyFlat |= flatLight > 0.0F;
         return i;
      }

      void quad(int a, int b, int c, int d, int tris) {
         if (this.quadCount * 4 + 4 > this.quads.length) {
            this.quads = Arrays.copyOf(this.quads, this.quads.length * 2);
         }

         this.quads[this.quadCount * 4] = a;
         this.quads[this.quadCount * 4 + 1] = b;
         this.quads[this.quadCount * 4 + 2] = c;
         this.quads[this.quadCount * 4 + 3] = d;
         this.quadCount++;
         this.triangles += tris;
      }

      Surface build() {
         return this.verts != 0 && this.quadCount != 0
            ? new Surface(
               Arrays.copyOf(this.pos, this.verts * 3),
               Arrays.copyOf(this.nrm, this.verts * 3),
               Arrays.copyOf(this.uv, this.verts * 2),
               Arrays.copyOf(this.color, this.verts),
               this.anyGlow ? Arrays.copyOf(this.glow, this.verts) : null,
               this.anyFlat ? Arrays.copyOf(this.flat, this.verts) : null,
               Arrays.copyOf(this.quads, this.quadCount * 4),
               this.triangles
            )
            : null;
      }
   }
}
