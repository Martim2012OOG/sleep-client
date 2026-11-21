package com.baom5.deathanimation.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class RigidStatuePhysics {
   private static final double MASS = 5000.0;
   private static final double DT = 0.125;
   private static final double GRAVITY = 0.024525;
   private static final Vec3d[] BASIS = new Vec3d[]{new Vec3d(1.0, 0.0, 0.0), new Vec3d(0.0, 1.0, 0.0), new Vec3d(0.0, 0.0, 1.0)};
   private final List<RigidStatuePhysics.Box> boxes;
   private final Vec3d inertia;
   private Vec3d position;
   private Vec3d previous;
   private Vec3d velocity;
   private Vec3d omega;
   private Quaternionf rotation = new Quaternionf();
   private Quaternionf previousRotation = new Quaternionf();
   private int quiet;
   private int supportMemory;
   private boolean sleeping;
   private double impact;

   public RigidStatuePhysics(List<RigidStatuePhysics.Box> boxes, Vec3d position, Vec3d velocity, Vec3d omega) {
      this.boxes = List.copyOf(boxes);
      this.position = this.previous = position;
      this.velocity = velocity;
      this.omega = omega;
      net.minecraft.util.math.Box b = this.bounds();
      double x = Math.max(0.2, b.getLengthX());
      double y = Math.max(0.2, b.getLengthY());
      double z = Math.max(0.2, b.getLengthZ());
      this.inertia = new Vec3d(5000.0 * (y * y + z * z) / 12.0, 5000.0 * (x * x + z * z) / 12.0, 5000.0 * (x * x + y * y) / 12.0);
   }

   private static Vec3d rotate(Quaternionf q, Vec3d p) {
      Vector3f v = q.transform(new Vector3f((float)p.x, (float)p.y, (float)p.z));
      return new Vec3d(v.x, v.y, v.z);
   }

   private Vec3d invInertia(Vec3d v) {
      Vec3d local = rotate(new Quaternionf(this.rotation).conjugate(), v);
      return rotate(this.rotation, new Vec3d(local.x / this.inertia.x, local.y / this.inertia.y, local.z / this.inertia.z));
   }

   public void impulse(Vec3d force, Vec3d point) {
      this.velocity = this.velocity.add(force.multiply(2.0E-4));
      this.omega = this.omega.add(this.invInertia(point.subtract(this.position).crossProduct(force)));
      this.sleeping = false;
      this.quiet = 0;
   }

   public Vec3d contactPoint(net.minecraft.util.math.Box target) {
      Vec3d nearest = null;
      double distance = 1.0 / 0.0;

      for (RigidStatuePhysics.Box box : this.boxes) {
         RigidStatuePhysics.Contact candidate = this.contact(box, target);
         if (candidate != null) {
            double next = candidate.point.squaredDistanceTo(target.getCenter());
            if (next < distance) {
               distance = next;
               nearest = candidate.point;
            }
         }
      }

      return nearest;
   }

   public Vec3d rayHit(Vec3d from, Vec3d to) {
      Vec3d delta = to.subtract(from);
      double nearest = 1.0 / 0.0;

      for (RigidStatuePhysics.Box box : this.boxes) {
         Quaternionf worldRotation = new Quaternionf(this.rotation).mul(box.rotation);
         Vec3d center = this.position.add(rotate(this.rotation, box.center));
         Quaternionf inverse = worldRotation.conjugate();
         Vec3d origin = rotate(inverse, from.subtract(center));
         Vec3d direction = rotate(inverse, delta);
         double low = 0.0;
         double high = 1.0;
         double[] o = new double[]{origin.x, origin.y, origin.z};
         double[] d = new double[]{direction.x, direction.y, direction.z};
         double[] half = new double[]{box.half.x, box.half.y, box.half.z};
         boolean hit = true;

         for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(d[axis]) < 1.0E-9) {
               if (o[axis] < -half[axis] || o[axis] > half[axis]) {
                  hit = false;
                  break;
               }
            } else {
               double a = (-half[axis] - o[axis]) / d[axis];
               double b = (half[axis] - o[axis]) / d[axis];
               if (a > b) {
                  double swap = a;
                  a = b;
                  b = swap;
               }

               low = Math.max(low, a);
               high = Math.min(high, b);
               if (low > high) {
                  hit = false;
                  break;
               }
            }
         }

         if (hit && low >= 0.0 && low <= 1.0) {
            nearest = Math.min(nearest, low);
         }
      }

      return Double.isFinite(nearest) ? from.add(delta.multiply(nearest)) : null;
   }

   private void turn(Vec3d angular, double dt) {
      double angle = angular.length() * dt;
      if (angle > 1.0E-10) {
         this.rotation
            .premul(
               new Quaternionf()
                  .rotationAxis(
                     (float)angle, (float)(angular.x / angular.length()), (float)(angular.y / angular.length()), (float)(angular.z / angular.length())
                  )
            )
            .normalize();
      }
   }

   public void tick(ClientWorld level, boolean stage, double floor) {
      this.tick(() -> {
         if (stage) {
            return List.of(new net.minecraft.util.math.Box(-3.0E7, floor - 8.0, -3.0E7, 3.0E7, floor, 3.0E7));
         }

         ArrayList<net.minecraft.util.math.Box> shapes = new ArrayList<>();

         for (VoxelShape shape : level.getBlockCollisions(null, this.bounds().expand(0.2))) {
            shapes.addAll(shape.getBoundingBoxes());
         }

         return shapes;
      });
   }

   public void tick(List<net.minecraft.util.math.Box> shapes) {
      this.tick(() -> shapes);
   }

   private void tick(Supplier<List<net.minecraft.util.math.Box>> terrain) {
      this.previous = this.position;
      this.previousRotation.set(this.rotation);
      this.impact = 0.0;
      if (!this.sleeping) {
         boolean supported = false;

         for (int sub = 0; sub < 8; sub++) {
            this.velocity = this.velocity.add(0.0, -0.003065625, 0.0).multiply(Math.pow(0.999, 0.125));
            this.omega = this.omega.multiply(Math.pow(0.996, 0.125));
            this.position = this.position.add(this.velocity.multiply(0.125));
            this.turn(this.omega, 0.125);
            List<net.minecraft.util.math.Box> shapes = terrain.get();

            for (int iteration = 0; iteration < 6; iteration++) {
               for (RigidStatuePhysics.Box box : this.boxes) {
                  for (net.minecraft.util.math.Box shape : shapes) {
                     RigidStatuePhysics.Contact c = this.contact(box, shape);
                     if (c != null) {
                        Vec3d r = c.point.subtract(this.position);
                        Vec3d angular = r.crossProduct(c.normal);
                        double inverse = 2.0E-4 + c.normal.dotProduct(this.invInertia(angular).crossProduct(r));
                        double correction = Math.max(0.0, c.depth - 3.0E-4) * 0.65 / Math.max(1.0E-9, inverse);
                        this.position = this.position.add(c.normal.multiply(correction / 5000.0));
                        this.turn(this.invInertia(angular).multiply(correction), 1.0);
                        Vec3d atPoint = this.velocity.add(this.omega.crossProduct(r));
                        double incoming = atPoint.dotProduct(c.normal);
                        if (c.normal.y > 0.3) {
                           supported = true;
                        }

                        if (incoming < 0.0) {
                           this.impact = Math.max(this.impact, -incoming);
                           double bounce = incoming < -0.12 ? 0.035 : 0.0;
                           double j = -(1.0 + bounce) * incoming / Math.max(1.0E-9, inverse);
                           Vec3d push = c.normal.multiply(j);
                           this.velocity = this.velocity.add(push.multiply(2.0E-4));
                           this.omega = this.omega.add(this.invInertia(r.crossProduct(push)));
                           Vec3d tangent = atPoint.subtract(c.normal.multiply(incoming));
                           double speed = tangent.length();
                           if (speed > 1.0E-7) {
                              Vec3d direction = tangent.multiply(1.0 / speed);
                              double invT = 2.0E-4 + direction.dotProduct(this.invInertia(r.crossProduct(direction)).crossProduct(r));
                              Vec3d friction = direction.multiply(-Math.min(speed / Math.max(1.0E-9, invT), j * 0.64));
                              this.velocity = this.velocity.add(friction.multiply(2.0E-4));
                              this.omega = this.omega.add(this.invInertia(r.crossProduct(friction)));
                           }
                        }
                     }
                  }
               }
            }
         }

         this.supportMemory = supported ? 3 : Math.max(0, this.supportMemory - 1);
         if (this.supportMemory > 0 && this.velocity.lengthSquared() < 2.5E-4 && this.omega.lengthSquared() < 4.0E-4) {
            this.velocity = this.velocity.multiply(0.8);
            this.omega = this.omega.multiply(0.8);
         }

         if (this.supportMemory > 0 && this.velocity.lengthSquared() < 6.0E-5 && this.omega.lengthSquared() < 8.0E-5) {
            this.quiet++;
         } else {
            this.quiet = 0;
         }

         if (this.quiet >= 16) {
            this.velocity = this.omega = Vec3d.ZERO;
            this.sleeping = true;
         }
      }
   }

   private RigidStatuePhysics.Contact contact(RigidStatuePhysics.Box box, net.minecraft.util.math.Box target) {
      Quaternionf q = new Quaternionf(this.rotation).mul(box.rotation);
      Vec3d center = this.position.add(rotate(this.rotation, box.center));
      Vec3d[] axes = new Vec3d[3];

      for (int i = 0; i < 3; i++) {
         axes[i] = rotate(q, BASIS[i]);
      }

      Vec3d targetCenter = target.getCenter();
      Vec3d delta = center.subtract(targetCenter);
      double[] half = new double[]{box.half.x, box.half.y, box.half.z};
      double[] other = new double[]{target.getLengthX() * 0.5, target.getLengthY() * 0.5, target.getLengthZ() * 0.5};
      double smallest = 1.0 / 0.0;
      Vec3d normal = null;
      ArrayList<Vec3d> candidates = new ArrayList<>(15);
      Collections.addAll(candidates, axes);
      Collections.addAll(candidates, BASIS);

      for (Vec3d a : axes) {
         for (Vec3d b : BASIS) {
            Vec3d cross = a.crossProduct(b);
            if (cross.lengthSquared() > 1.0E-8) {
               candidates.add(cross.normalize());
            }
         }
      }

      for (Vec3d axis : candidates) {
         double radius = 0.0;
         double otherRadius = 0.0;

         for (int i = 0; i < 3; i++) {
            radius += half[i] * Math.abs(axis.dotProduct(axes[i]));
            otherRadius += other[i] * Math.abs(axis.dotProduct(BASIS[i]));
         }

         double depth = radius + otherRadius - Math.abs(delta.dotProduct(axis));
         if (depth <= 0.0) {
            return null;
         }

         if (depth < smallest) {
            smallest = depth;
            normal = delta.dotProduct(axis) < 0.0 ? axis.multiply(-1.0) : axis;
         }
      }

      double low = 1.0 / 0.0;
      Vec3d point = Vec3d.ZERO;
      int count = 0;

      for (int corner = 0; corner < 8; corner++) {
         Vec3d v = center;

         for (int i = 0; i < 3; i++) {
            v = v.add(axes[i].multiply(half[i] * ((corner & 1 << i) == 0 ? -1 : 1)));
         }

         double dot = v.dotProduct(normal);
         if (dot < low - 2.0E-4) {
            low = dot;
            point = v;
            count = 1;
         } else if (Math.abs(dot - low) < 2.0E-4) {
            point = point.add(v);
            count++;
         }
      }

      return new RigidStatuePhysics.Contact(normal, point.multiply(1.0 / count), smallest);
   }

   public net.minecraft.util.math.Box bounds() {
      double x0 = 1.0 / 0.0;
      double y0 = x0;
      double z0 = x0;
      double x1 = -x0;
      double y1 = -x0;
      double z1 = -x0;

      for (RigidStatuePhysics.Box box : this.boxes) {
         Quaternionf q = new Quaternionf(this.rotation).mul(box.rotation);
         Vec3d center = this.position.add(rotate(this.rotation, box.center));

         for (int corner = 0; corner < 8; corner++) {
            Vec3d v = rotate(
                  q,
                  new Vec3d(
                     (corner & 1) == 0 ? -box.half.x : box.half.x, (corner & 2) == 0 ? -box.half.y : box.half.y, (corner & 4) == 0 ? -box.half.z : box.half.z
                  )
               )
               .add(center);
            x0 = Math.min(x0, v.x);
            y0 = Math.min(y0, v.y);
            z0 = Math.min(z0, v.z);
            x1 = Math.max(x1, v.x);
            y1 = Math.max(y1, v.y);
            z1 = Math.max(z1, v.z);
         }
      }

      return new net.minecraft.util.math.Box(x0, y0, z0, x1, y1, z1);
   }

   public Vec3d position(float partial) {
      return this.previous.lerp(this.position, partial);
   }

   public Quaternionf rotation(float partial) {
      return new Quaternionf(this.previousRotation).slerp(this.rotation, partial);
   }

   public double impact() {
      return this.impact;
   }

   public boolean sleeping() {
      return this.sleeping;
   }

   public List<RigidStatuePhysics.Box> boxes() {
      return this.boxes;
   }

   public Vec3d velocity() {
      return this.velocity;
   }

   public String diagnostics() {
      return "v=" + this.velocity + " angular=" + this.omega + " quiet=" + this.quiet + " contacts=" + this.supportMemory;
   }

   @Environment(EnvType.CLIENT)
   public record Box(Vec3d center, Vector3f half, Quaternionf rotation) {
      public Box {
         half = new Vector3f(half);
         rotation = new Quaternionf(rotation);
      }
   }

   @Environment(EnvType.CLIENT)
   private record Contact(Vec3d normal, Vec3d point, double depth) {
   }
}
