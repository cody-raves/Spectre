package net.spectre.client.module.impl.render.character.kit;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class Part {
   final String name;
   final Bone bone;
   final Part parent;
   final boolean extra;
   Shape shape;
   float pivotX;
   float pivotY;
   float pivotZ;
   boolean pivotSet;
   float gripX;
   float gripY;
   float gripZ;
   boolean gripSet;
   int outlineColor;
   float outline = -1.0F;
   boolean outlineColorSet;
   final List<Decal> decals = new ArrayList<>();
   Motion motion;
   float detail = 1.0F;
   float resolution = 1.0F;
   float flat = -1.0F;
   float occlusion = -1.0F;
   float swing = 1.0F;
   float swingLimit;
   float jointX;
   float jointY;
   float jointZ;
   float jointDirX;
   float jointDirY = -1.0F;
   float jointDirZ;
   float jointBlend;
   boolean jointSet;
   boolean neck;
   float lookPitch;
   float lookYaw;
   float lookLimit;

   Part(String name, Bone bone, Part parent, boolean extra) {
      this.name = name;
      this.bone = bone;
      this.parent = parent;
      this.extra = extra;
      if (parent == null) {
         this.pivotX = bone.pivotX;
         this.pivotY = bone.pivotY;
         this.pivotZ = bone.pivotZ;
      } else {
         this.pivotX = parent.pivotX;
         this.pivotY = parent.pivotY;
         this.pivotZ = parent.pivotZ;
      }
   }

   public String name() {
      return this.name;
   }

   public Bone bone() {
      return this.bone;
   }

   public boolean extra() {
      return this.extra;
   }

   public Shape shape() {
      return this.shape;
   }

   public Part shape(Shape shape) {
      this.shape = shape;
      return this;
   }

   public Part pivot(float x, float y, float z) {
      this.pivotX = x;
      this.pivotY = y;
      this.pivotZ = z;
      this.pivotSet = true;
      return this;
   }

   public Vec3 pivot() {
      return new Vec3(this.pivotX, this.pivotY, this.pivotZ);
   }

   public Part grip(float x, float y, float z) {
      this.gripX = x;
      this.gripY = y;
      this.gripZ = z;
      this.gripSet = true;
      return this;
   }

   public Part outline(float thickness) {
      this.outline = Math.max(0.0F, thickness);
      return this;
   }

   public Part outline(int argb, float thickness) {
      this.outlineColor = argb;
      this.outlineColorSet = true;
      this.outline = Math.max(0.0F, thickness);
      return this;
   }

   public Part noOutline() {
      this.outline = 0.0F;
      return this;
   }

   public Part decal(Decal decal) {
      this.decals.add(decal);
      return this;
   }

   public Part motion(Motion motion) {
      this.motion = motion;
      return this;
   }

   public Part detail(float weight) {
      this.detail = Math.max(0.05F, weight);
      return this;
   }

   public Part resolution(float factor) {
      this.resolution = Math.max(0.35F, Math.min(3.0F, factor));
      return this;
   }

   public Part flatLight(float amount) {
      this.flat = Math.max(0.0F, Math.min(1.0F, amount));
      return this;
   }

   public Part occlusion(float strength) {
      this.occlusion = Math.max(0.0F, Math.min(1.0F, strength));
      return this;
   }

   public Part swing(float scale) {
      return this.swing(scale, 0.0F);
   }

   public Part swing(float scale, float limitDegrees) {
      if (!this.extra && (this.bone.arm() || this.bone.leg())) {
         this.swing = Math.max(0.0F, Math.min(2.0F, scale));
         this.swingLimit = Math.max(0.0F, Math.min(90.0F, limitDegrees));
         return this;
      } else {
         throw new IllegalStateException(this.name + ": swing() only applies to the arm and leg bone parts");
      }
   }

   public Part joint(float x, float y, float z) {
      return this.joint(x, y, z, 0.7F);
   }

   public Part joint(float x, float y, float z, float blend) {
      if (!this.extra && (this.bone.arm() || this.bone.leg())) {
         this.jointX = x;
         this.jointY = y;
         this.jointZ = z;
         this.jointDirX = 0.0F;
         this.jointDirY = -1.0F;
         this.jointDirZ = 0.0F;
         this.jointBlend = Math.max(0.1F, blend);
         this.jointSet = true;
         this.neck = false;
         return this;
      } else {
         throw new IllegalStateException(this.name + ": joint() only applies to the arm and leg bone parts");
      }
   }

   public Part neck(float x, float y, float z, float dx, float dy, float dz, float blend) {
      if (!this.bone.arm() && !this.bone.leg()) {
         float len = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
         if (!(len > 1.0E-4F)) {
            throw new IllegalArgumentException(this.name + ": neck() needs a direction");
         } else {
            this.jointX = x;
            this.jointY = y;
            this.jointZ = z;
            this.jointDirX = dx / len;
            this.jointDirY = dy / len;
            this.jointDirZ = dz / len;
            this.jointBlend = Math.max(0.1F, blend);
            this.jointSet = true;
            this.neck = true;
            return this;
         }
      } else {
         throw new IllegalStateException(this.name + ": limbs bend with joint(), not neck()");
      }
   }

   public Part look(float pitch, float yaw, float limitDegrees) {
      if (!this.neck) {
         throw new IllegalStateException(this.name + ": look() needs a neck()");
      } else {
         this.lookPitch = pitch;
         this.lookYaw = yaw;
         this.lookLimit = Math.max(0.0F, limitDegrees);
         return this;
      }
   }

   public Part mirrorOf(Part other) {
      this.shape = other.shape == null ? null : other.shape.mirrored();
      this.pivotX = -other.pivotX;
      this.pivotY = other.pivotY;
      this.pivotZ = other.pivotZ;
      this.pivotSet = true;
      if (other.gripSet) {
         this.gripX = -other.gripX;
         this.gripY = other.gripY;
         this.gripZ = other.gripZ;
         this.gripSet = true;
      }

      this.outline = other.outline;
      this.outlineColor = other.outlineColor;
      this.outlineColorSet = other.outlineColorSet;
      this.decals.clear();

      for (Decal d : other.decals) {
         this.decals.add(d.mirrored());
      }

      this.motion = other.motion == null ? null : other.motion.mirrored();
      this.detail = other.detail;
      this.resolution = other.resolution;
      this.flat = other.flat;
      this.occlusion = other.occlusion;
      this.swing = other.swing;
      this.swingLimit = other.swingLimit;
      this.jointX = -other.jointX;
      this.jointY = other.jointY;
      this.jointZ = other.jointZ;
      this.jointDirX = -other.jointDirX;
      this.jointDirY = other.jointDirY;
      this.jointDirZ = other.jointDirZ;
      this.jointBlend = other.jointBlend;
      this.jointSet = other.jointSet;
      this.neck = other.neck;
      this.lookPitch = other.lookPitch;
      this.lookYaw = other.lookYaw;
      this.lookLimit = other.lookLimit;
      return this;
   }

   float gripX() {
      return this.gripSet ? this.gripX : this.pivotX + this.bone.gripX;
   }

   float gripY() {
      return this.gripSet ? this.gripY : this.pivotY + this.bone.gripY;
   }

   float gripZ() {
      return this.gripSet ? this.gripZ : this.pivotZ + this.bone.gripZ;
   }
}
