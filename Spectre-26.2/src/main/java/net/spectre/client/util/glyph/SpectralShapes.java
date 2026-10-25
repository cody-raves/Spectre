package net.spectre.client.util.glyph;

import java.util.Arrays;
import net.spectre.client.util.glyph.GlyphShapes;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class SpectralShapes {
   public static final float[] GHOST=ghost(),STAR=star(),MOON=arc(),WISP=wisp(),CRYSTAL=crystal(),ORBIT=orbit();
   private SpectralShapes(){}
   private static float[] ghost(){Sketch s=new Sketch();s.stroke(.14F,-.7F,-.9F,-.7F,.2F,-.5F,.72F,0,1,.5F,.72F,.7F,.2F,.7F,-.9F,.35F,-.6F,0,-.9F,-.35F,-.6F,-.7F,-.9F);s.quad(-.35F,.05F,-.15F,.05F,-.15F,.4F,-.35F,.4F);s.quad(.15F,.05F,.35F,.05F,.35F,.4F,.15F,.4F);return s.build();}
   private static float[] star(){Sketch s=new Sketch();s.quad(0,1,.18F,.18F,0,0,-.18F,.18F);s.quad(1,0,.18F,-.18F,0,0,.18F,.18F);s.quad(0,-1,-.18F,-.18F,0,0,.18F,-.18F);s.quad(-1,0,-.18F,.18F,0,0,-.18F,-.18F);return s.build();}
   private static float[] arc(){Sketch s=new Sketch();float[] p=new float[34];for(int i=0;i<17;i++){double a=Math.PI*.3+i*Math.PI*1.4/16;p[i*2]=(float)Math.cos(a)*.85F;p[i*2+1]=(float)Math.sin(a)*.95F;}s.stroke(.2F,p);return s.build();}
   private static float[] wisp(){Sketch s=new Sketch();s.stroke(.16F,0,-1,-.6F,-.4F,-.5F,.1F,-.1F,.4F,.15F,1,.35F,.4F,.65F,.05F,.5F,-.5F,0,-1);return s.build();}
   private static float[] crystal(){Sketch s=new Sketch();s.stroke(.12F,0,1,.65F,0,0,-1,-.65F,0,0,1);s.stroke(.08F,-.65F,0,.65F,0);s.stroke(.08F,0,1,0,-1);return s.build();}
   private static float[] orbit(){Sketch s=new Sketch();float[] p=new float[50];for(int i=0;i<25;i++){double a=i*Math.PI*2/24;p[i*2]=(float)Math.cos(a)*.95F;p[i*2+1]=(float)Math.sin(a)*.5F;}s.stroke(.12F,p);s.quad(-.2F,-.2F,.2F,-.2F,.2F,.2F,-.2F,.2F);return s.build();}

   @Environment(EnvType.CLIENT)
   private static final class Sketch {
      private float[] data = new float[64];
      private int size;

      void quad(float ax, float ay, float bx, float by, float cx, float cy, float dx, float dy) {
         if (this.size + 8 > this.data.length) {
            this.data = Arrays.copyOf(this.data, this.data.length * 2);
         }

         this.data[this.size++] = ax;
         this.data[this.size++] = ay;
         this.data[this.size++] = bx;
         this.data[this.size++] = by;
         this.data[this.size++] = cx;
         this.data[this.size++] = cy;
         this.data[this.size++] = dx;
         this.data[this.size++] = dy;
      }

      void stroke(float width, float... points) {
         float[] low = new float[points.length / 2];
         float[] high = new float[points.length / 2];
         Arrays.fill(low, -width * 0.5F);
         Arrays.fill(high, width * 0.5F);
         this.band(points, low, high);
      }

      void band(float[] points, float[] low, float[] high) {
         int n = points.length / 2;
         float[] nx = new float[n];
         float[] ny = new float[n];

         for (int i = 0; i < n; i++) {
            int a = Math.max(0, i - 1);
            int b = Math.min(n - 1, i + 1);
            float dx = points[b * 2] - points[a * 2];
            float dy = points[b * 2 + 1] - points[a * 2 + 1];
            float length = (float)Math.sqrt(dx * dx + dy * dy);
            nx[i] = -dy / length;
            ny[i] = dx / length;
         }

         int i = 0;

         for (int j = 1; j < n; j++) {
            float ax = points[i * 2];
            float ay = points[i * 2 + 1];
            float bx = points[j * 2];
            float by = points[j * 2 + 1];
            this.quad(
               ax + nx[i] * low[i],
               ay + ny[i] * low[i],
               bx + nx[j] * low[j],
               by + ny[j] * low[j],
               bx + nx[j] * high[j],
               by + ny[j] * high[j],
               ax + nx[i] * high[i],
               ay + ny[i] * high[i]
            );
            i++;
         }
      }

      float[] build() {
         return Arrays.copyOf(this.data, this.size);
      }
   }
}
