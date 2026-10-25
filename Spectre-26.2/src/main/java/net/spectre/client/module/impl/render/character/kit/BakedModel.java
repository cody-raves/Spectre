package net.spectre.client.module.impl.render.character.kit;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.zip.InflaterInputStream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class BakedModel {
   public static final int MAGIC = 1095648072;
   public static final int VERSION = 3;
   private static final int SHIPPED_VERSION = 4;

   private BakedModel() {
   }

   static CharacterModel load(BakedDesign design, Detail detail) {
      long start = System.nanoTime();

      try {
         CharacterModel var45;
         try (InputStream raw = design.open()) {
            DataInputStream in = new DataInputStream(new BufferedInputStream(raw));
            int version = in.readInt() == 1095648072 ? in.readInt() : -1;
            if (version != 3 && version != 4) {
               throw new IllegalStateException(design.id() + ": not a version 3 baked character");
            }

            int width = 0;
            int height = 0;
            int[] atlas = null;
            if (version == 4) {
               width = in.readInt();
               height = in.readInt();
               byte[] filtered = new byte[width * height * 4];
               in.readFully(filtered);
               atlas = unfilter(filtered, width, height);
            }

            float minY = in.readFloat();
            float maxY = in.readFloat();
            float hipY = in.readFloat();
            float flat = in.readFloat();
            boolean firstPersonArms = in.readBoolean();
            boolean colored = in.readBoolean();
            int levels = in.readInt();
            int wanted = Math.min(detail.ordinal(), levels - 1);
            PartMesh[] parts = new PartMesh[in.readInt()];

            for (int i = 0; i < parts.length; i++) {
               Bone bone = Bone.values()[in.readByte()];
               float px = in.readFloat();
               float py = in.readFloat();
               float pz = in.readFloat();
               float gx = in.readFloat();
               float gy = in.readFloat();
               float gz = in.readFloat();
               float[] box = new float[6];

               for (int k = 0; k < 6; k++) {
                  box[k] = in.readFloat();
               }

               float swing = in.readFloat();
               float swingLimit = in.readFloat();
               boolean jointed = in.readBoolean();
               float jx = 0.0F;
               float jy = 0.0F;
               float jz = 0.0F;
               if (jointed) {
                  jx = in.readFloat() - px;
                  jy = in.readFloat() - py;
                  jz = in.readFloat() - pz;
               }

               Surface solid = null;
               Surface clear = null;

               for (int level = 0; level < levels; level++) {
                  Surface a = surface(in, box, px, py, pz, flat, colored, jointed, level == wanted);
                  Surface b = surface(in, box, px, py, pz, flat, colored, jointed, level == wanted);
                  if (level == wanted) {
                     solid = a;
                     clear = b;
                  }
               }

               parts[i] = new PartMesh(
                  bone.name(),
                  bone,
                  -1,
                  false,
                  px,
                  py,
                  pz,
                  gx,
                  gy,
                  gz,
                  null,
                  solid,
                  null,
                  clear,
                  -1,
                  swing,
                  swingLimit,
                  jointed,
                  jx,
                  jy,
                  jz,
                  false,
                  0.0F,
                  0.0F,
                  0.0F
               );
            }

            if (version == 3) {
               width = in.readInt();
               height = in.readInt();
               byte[] packed = new byte[in.readInt()];
               in.readFully(packed);
               atlas = pixels(packed, width, height);
            }

            long millis = (System.nanoTime() - start) / 1000000L;
            var45 = new CharacterModel(
               design.id(),
               design.name(),
               detail,
               1.0F,
               parts,
               0,
               width,
               height,
               Atlas.mips(atlas, width, height),
               0.0F,
               0.0F,
               minY,
               maxY,
               hipY,
               firstPersonArms,
               millis
            );
         }

         return var45;
      } catch (IOException var41) {
         throw new UncheckedIOException(design.name() + ": " + var41.getMessage(), var41);
      }
   }

   private static int[] pixels(byte[] packed, int width, int height) throws IOException {
      byte[] raw = new byte[width * height * 4];

      try (InflaterInputStream in = new InflaterInputStream(new ByteArrayInputStream(packed))) {
         int read = in.readNBytes(raw, 0, raw.length);
         if (read != raw.length) {
            throw new IOException("atlas is truncated");
         }
      }

      return unfilter(raw, width, height);
   }

   private static int[] unfilter(byte[] raw, int width, int height) {
      int[] px = new int[width * height];

      for (int i = 0; i < px.length; i++) {
         int left = i % width == 0 ? 0 : px[i - 1];
         int p = 0;

         for (int c = 0; c < 4; c++) {
            int shift = 24 - c * 8;
            p |= (raw[i * 4 + c] + (left >>> shift) & 0xFF) << shift;
         }

         px[i] = p;
      }

      return px;
   }

   private static Surface surface(DataInputStream in, float[] box, float px, float py, float pz, float flat, boolean colored, boolean jointed, boolean keep) throws IOException {
      int verts = in.readInt();
      if (!keep) {
         in.skipNBytes(verts * (13L + (colored ? 3L : 0L) + (jointed ? 1L : 0L)));
         in.skipNBytes(in.readInt() * (verts > 65535 ? 12L : 6L));
         return null;
      } else {
         float[] pos = new float[verts * 3];

         for (int i = 0; i < pos.length; i++) {
            int axis = i % 3;
            pos[i] = box[axis] + in.readChar() / 65535.0F * (box[axis + 3] - box[axis]);
         }

         byte[] nrm = new byte[verts * 3];
         in.readFully(nrm);
         float[] uv = new float[verts * 2];

         for (int i = 0; i < uv.length; i++) {
            uv[i] = in.readChar() / 65535.0F;
         }

         int[] color = new int[verts];

         for (int i = 0; i < verts; i++) {
            color[i] = colored ? 0xFF000000 | in.readUnsignedByte() << 16 | in.readUnsignedByte() << 8 | in.readUnsignedByte() : -1;
         }

         float[] joint = null;
         if (jointed) {
            joint = new float[verts];

            for (int i = 0; i < verts; i++) {
               joint[i] = in.readUnsignedByte() / 255.0F;
            }
         }

         int count = in.readInt();
         int[] tris = new int[count * 3];

         for (int i = 0; i < tris.length; i++) {
            tris[i] = verts > 65535 ? in.readInt() : in.readChar();
         }

         if (verts != 0 && count != 0) {
            Surface.Builder b = new Surface.Builder();

            for (int i = 0; i < verts; i++) {
               float nx = nrm[i * 3] / 127.0F;
               float ny = nrm[i * 3 + 1] / 127.0F;
               float nz = nrm[i * 3 + 2] / 127.0F;
               float l = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
               if (l > 1.0E-6F) {
                  nx /= l;
                  ny /= l;
                  nz /= l;
               }

               b.vertex(pos[i * 3] - px, pos[i * 3 + 1] - py, pos[i * 3 + 2] - pz, nx, ny, nz, uv[i * 2], uv[i * 2 + 1], color[i], false, flat);
            }

            CharacterBuilder.pair(tris, count, verts, b);
            Surface out = b.build();
            if (out != null && joint != null) {
               for (float w : joint) {
                  if (w > 0.0F) {
                     out.joint = joint;
                     break;
                  }
               }
            }

            return out;
         } else {
            return null;
         }
      }
   }
}
