package com.baom5.deathanimation.client;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
final class DominoChainPhysics {
   private static final Map<Integer, DominoChainPhysics> CACHE = new ConcurrentHashMap<>();
   static final int COUNT = 9;
   static final int SUBSTEPS = 16;
   static final double PLAYER_HEIGHT = 1.8;
   static final double PLAYER_HALF_DEPTH = 0.3;
   static final double PIVOT_DISTANCE = 1.3;
   final double[] height = new double[9];
   final double[] width = new double[9];
   final double[] depth = new double[9];
   final double[] pivotZ = new double[9];
   final double[] contactTicks = new double[8];
   final double[][] frames;
   final double finalAngle = Math.atan(5.503355704697986);
   double impactTick = 0.0 / 0.0;

   static DominoChainPhysics forDuration(int ticks) {
      return CACHE.computeIfAbsent(ticks, DominoChainPhysics::new);
   }

   DominoChainPhysics(int ticks) {
      Arrays.fill(this.contactTicks, 0.0 / 0.0);

      for (int i = 0; i < 9; i++) {
         boolean last = i == 8;
         this.height[i] = last ? 3.2 : 2.1;
         this.width[i] = last ? 1.8 : 0.9;
         this.depth[i] = last ? 0.26 : 0.2;
         this.pivotZ[i] = -1.3 - (8 - i) * 0.78;
      }

      this.frames = new double[ticks * 16 + 1][9];
      double[] angles = new double[9];
      double[] omega = new double[9];
      double dt = 0.0625;
      boolean released = false;

      for (int step = 1; step < this.frames.length; step++) {
         double tick = step * dt;
         if (!released && tick >= ticks * 0.08) {
            released = true;
            omega[0] = 0.035;
         }

         for (int i = 0; i < 9; i++) {
            omega[i] += 0.0367875 / this.height[i] * Math.sin(angles[i]) * dt;
            angles[i] = Math.min(i == 8 ? this.finalAngle : 1.5707963267948966, angles[i] + omega[i] * dt);
         }

         for (int i = 7; i >= 0; i--) {
            if (this.overlaps(i, angles[i], i + 1, angles[i + 1]) && Double.isNaN(this.contactTicks[i])) {
               this.contactTicks[i] = tick;
               double ra = this.height[i] * Math.cos(angles[i] - angles[i + 1]);
               double rb = Math.max(0.1, this.height[i] * Math.cos(angles[i]) / Math.max(0.1, Math.cos(angles[i + 1])));
               double ia = this.width[i] * this.depth[i] * Math.pow(this.height[i], 3.0) / 3.0;
               double ib = this.width[i + 1] * this.depth[i + 1] * Math.pow(this.height[i + 1], 3.0) / 3.0;
               double impulse = Math.max(0.0, (ra * omega[i] - rb * omega[i + 1]) / (ra * ra / ia + rb * rb / ib));
               omega[i] = Math.max(0.0, omega[i] - impulse * ra / ia);
               omega[i + 1] = omega[i + 1] + impulse * rb / ib;
            }

            angles[i] = this.safeAngle(i, angles);
         }

         this.frames[step] = (double[])angles.clone();
         if (Double.isNaN(this.impactTick) && faceHeight(angles[8], 0.3) <= 1.805) {
            this.impactTick = tick;
         }
      }

      if (Double.isNaN(this.impactTick)) {
         throw new IllegalStateException("Domino chain never reaches the victim");
      }
   }

   double[] sample(double tick) {
      double frame = Math.max(0.0, Math.min(this.frames.length - 1, tick * 16.0));
      int index = (int)frame;
      int next = Math.min(index + 1, this.frames.length - 1);
      double[] a = new double[9];

      for (int i = 0; i < 9; i++) {
         a[i] = this.frames[index][i] + (this.frames[next][i] - this.frames[index][i]) * (frame - index);
      }

      for (int i = 7; i >= 0; i--) {
         a[i] = this.safeAngle(i, a);
      }

      return a;
   }

   private double safeAngle(int i, double[] a) {
      double high = a[i];
      if (!this.overlapsLater(i, high, a)) {
         return high;
      }

      double low = high;

      while (low > 0.0 && this.overlapsLater(i, low, a)) {
         low = Math.max(0.0, low - 0.01);
      }

      for (int n = 0; n < 14; n++) {
         double mid = (low + high) / 2.0;
         if (this.overlapsLater(i, mid, a)) {
            high = mid;
         } else {
            low = mid;
         }
      }

      return low;
   }

   private boolean overlapsLater(int i, double angle, double[] a) {
      for (int j = i + 1; j < 9; j++) {
         if (this.overlaps(i, angle, j, a[j])) {
            return true;
         }
      }

      return false;
   }

   double centerZ(int i, double a) {
      return this.pivotZ[i] + this.height[i] * 0.5 * Math.sin(a) - this.depth[i] * 0.5 * Math.cos(a);
   }

   double centerY(int i, double a) {
      return this.height[i] * 0.5 * Math.cos(a) + this.depth[i] * 0.5 * Math.sin(a);
   }

   static double faceHeight(double a, double halfDepth) {
      return a <= 0.0 ? 1.0 / 0.0 : (1.3 - halfDepth) / Math.tan(a);
   }

   static double bodyScale(double a) {
      if (faceHeight(a, 0.3) > 1.805) {
         return 1.0;
      }

      double cot = 1.0 / Math.tan(a);
      double expansion = 0.19565217391304346;
      return Math.max(0.08, Math.min(1.0, ((1.0 - expansion) * cot - 0.005) / (1.8 - expansion * cot)));
   }

   boolean overlaps(int a, double aa, int b, double ab) {
      double sa = Math.sin(aa);
      double ca = Math.cos(aa);
      double sb = Math.sin(ab);
      double cb = Math.cos(ab);
      double dz = this.centerZ(b, ab) - this.centerZ(a, aa);
      double dy = this.centerY(b, ab) - this.centerY(a, aa);
      return !this.separated(dz, dy, sa, ca, sa, ca, sb, cb, a, b)
         && !this.separated(dz, dy, ca, -sa, sa, ca, sb, cb, a, b)
         && !this.separated(dz, dy, sb, cb, sa, ca, sb, cb, a, b)
         && !this.separated(dz, dy, cb, -sb, sa, ca, sb, cb, a, b);
   }

   private boolean separated(double dz, double dy, double az, double ay, double sa, double ca, double sb, double cb, int a, int b) {
      double ra = this.height[a] * 0.5 * Math.abs(sa * az + ca * ay) + this.depth[a] * 0.5 * Math.abs(ca * az - sa * ay);
      double rb = this.height[b] * 0.5 * Math.abs(sb * az + cb * ay) + this.depth[b] * 0.5 * Math.abs(cb * az - sb * ay);
      return Math.abs(dz * az + dy * ay) >= ra + rb - 1.0E-7;
   }
}
