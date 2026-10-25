package net.spectre.client.module.impl.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
final class VoxelModel {
   static final int X_NEG = 0;
   static final int X_POS = 1;
   static final int TOP = 2;
   static final int BOTTOM = 3;
   static final int FRONT = 4;
   static final int BACK = 5;
   private static final int SPAN = 40;
   private static final int OFFSET = 20;
   private static final float EDGE_SHADOW = 0.11F;
   private static final float CORNER_SHADOW = 0.05F;
   private static final int[] STEP_X = new int[]{-1, 1, 0, 0, 0, 0};
   private static final int[] STEP_Y = new int[]{0, 0, -1, 1, 0, 0};
   private static final int[] STEP_Z = new int[]{0, 0, 0, 0, -1, 1};
   private final byte[] cells = new byte[64000];
   private final byte[] paint = new byte[384000];

   void fill(int x0, int y0, int z0, int x1, int y1, int z1, int ink) {
      for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
         for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
               this.cells[index(x, y, z)] = (byte)(ink + 1);
            }
         }
      }
   }

   void paint(int x0, int y0, int z0, int x1, int y1, int z1, int face, int ink) {
      for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
         for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
               this.paint[index(x, y, z) * 6 + face] = (byte)(ink + 1);
            }
         }
      }
   }

   void paint(int x, int y, int z, int face, int ink) {
      this.paint[index(x, y, z) * 6 + face] = (byte)(ink + 1);
   }

   boolean solid(int x, int y, int z) {
      return x >= -20 && y >= -20 && z >= -20 && x < 20 && y < 20 && z < 20 ? this.cells[index(x, y, z)] != 0 : false;
   }

   CosmeticMesh mesh(VoxelModel.Ink[] inks, float ox, float oy, float oz, float sx, float sy, float sz) {
      CosmeticMesh.Builder b = new CosmeticMesh.Builder();
      this.emit(b, inks, ox, oy, oz, sx, sy, sz);
      return new CosmeticMesh(b);
   }

   void emit(CosmeticMesh.Builder b, VoxelModel.Ink[] inks, float ox, float oy, float oz, float sx, float sy, float sz) {
      float[] lo = new float[3];
      float[] hi = new float[3];

      for (int x = -20; x < 20; x++) {
         for (int y = -20; y < 20; y++) {
            for (int z = -20; z < 20; z++) {
               int cell = index(x, y, z);
               if (this.cells[cell] != 0) {
                  lo[0] = ox + x * sx;
                  lo[1] = oy + y * sy;
                  lo[2] = oz + z * sz;
                  hi[0] = lo[0] + sx;
                  hi[1] = lo[1] + sy;
                  hi[2] = lo[2] + sz;
                  float grain = grain(x, y, z);

                  for (int face = 0; face < 6; face++) {
                     if (!this.solid(x + STEP_X[face], y + STEP_Y[face], z + STEP_Z[face])) {
                        int painted = this.paint[cell * 6 + face];
                        VoxelModel.Ink ink = inks[(painted != 0 ? painted : this.cells[cell]) - 1];
                        b.shade(ink.tone() * (1.0F - ink.grain() * grain) * this.occlusion(x, y, z, face));
                        face(b, face, lo, hi, ink.material());
                     }
                  }
               }
            }
         }
      }

      b.shade(1.0F);
   }

   private float occlusion(int x, int y, int z, int face) {
      int fx = x + STEP_X[face];
      int fy = y + STEP_Y[face];
      int fz = z + STEP_Z[face];
      int edges = 0;
      int corners = 0;

      for (int du = -1; du <= 1; du++) {
         for (int dv = -1; dv <= 1; dv++) {
            if (du != 0 || dv != 0) {
               boolean hit = switch (face >> 1) {
                  case 0 -> this.solid(fx, fy + du, fz + dv);
                  case 1 -> this.solid(fx + du, fy, fz + dv);
                  default -> this.solid(fx + du, fy + dv, fz);
               };
               if (hit) {
                  if (du != 0 && dv != 0) {
                     corners++;
                  } else {
                     edges++;
                  }
               }
            }
         }
      }

      return Math.max(0.55F, 1.0F - 0.11F * edges - 0.05F * corners);
   }

   private static void face(CosmeticMesh.Builder b, int face, float[] lo, float[] hi, int material) {
      float cx = (lo[0] + hi[0]) * 0.5F;
      float cy = (lo[1] + hi[1]) * 0.5F;
      float cz = (lo[2] + hi[2]) * 0.5F;
      float x0 = lo[0];
      float y0 = lo[1];
      float z0 = lo[2];
      float x1 = hi[0];
      float y1 = hi[1];
      float z1 = hi[2];
      switch (face) {
         case 0:
            b.quad(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, cx, cy, cz, material);
            break;
         case 1:
            b.quad(x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, cx, cy, cz, material);
            break;
         case 2:
            b.quad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, cx, cy, cz, material);
            break;
         case 3:
            b.quad(x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, cx, cy, cz, material);
            break;
         case 4:
            b.quad(x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, cx, cy, cz, material);
            break;
         default:
            b.quad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, cx, cy, cz, material);
      }
   }

   private static float grain(int x, int y, int z) {
      int h = x * 522133279 ^ y * 1540483477 ^ z * 668265263;
      h ^= h >>> 13;
      h *= -2048144789;
      h ^= h >>> 16;
      return (h & 0xFF) / 255.0F;
   }

   private static int index(int x, int y, int z) {
      return ((x + 20) * 40 + y + 20) * 40 + z + 20;
   }

   @Environment(EnvType.CLIENT)
   record Ink(int material, float tone, float grain) {
   }
}
