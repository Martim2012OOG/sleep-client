package com.baom5.deathanimation.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
final class JointedRagdoll {
   private static final float DT = 0.16666667F;
   private static final Vec3d UP = new Vec3d(0.0, 1.0, 0.0);
   private static final Vec3d[] AXES = new Vec3d[]{new Vec3d(1.0, 0.0, 0.0), UP, new Vec3d(0.0, 0.0, 1.0)};
   private final BodyFragment[] pieces;
   private final JointedRagdoll.Rigid[] bodies = new JointedRagdoll.Rigid[6];
   private final List<JointedRagdoll.Joint> joints = new ArrayList<>();
   private int quiet;
   private boolean sleeping;
   private double lastMotion;
   private double filteredMotion;
   private double impact;
   private final HangingRagdoll suspension;

   JointedRagdoll(BodyFragment[] pieces, Vector3f[] centers, Vector3f[] pivots) {
      this(pieces, centers, pivots, null);
   }

   JointedRagdoll(BodyFragment[] pieces, Vector3f[] centers, Vector3f[] pivots, HangingRagdoll suspension) {
      this(pieces, centers, pivots, null, 1.0F, suspension, null);
   }

   JointedRagdoll(BodyFragment[] pieces, Vector3f[][] localAnchors, float massScale) {
      this(pieces, null, null, localAnchors, massScale, null, null);
   }

   JointedRagdoll(BodyFragment[] pieces, Vector3f[][] localAnchors, float massScale, Vec3d[] angularVelocity) {
      this(pieces, null, null, localAnchors, massScale, null, angularVelocity);
   }

   private JointedRagdoll(
      BodyFragment[] pieces,
      Vector3f[] centers,
      Vector3f[] pivots,
      Vector3f[][] localAnchors,
      float massScale,
      HangingRagdoll suspension,
      Vec3d[] angularVelocity
   ) {
      this.pieces = pieces;
      this.suspension = suspension;
      float[] masses = new float[]{5.0F, 30.0F, 4.0F, 4.0F, 9.0F, 9.0F};

      for (int i = 0; i < 6; i++) {
         pieces[i].jointed = true;
         this.bodies[i] = new JointedRagdoll.Rigid(pieces[i], masses[i] * massScale);
         if (angularVelocity != null) {
            this.bodies[i].omega = angularVelocity[i];
         }
      }

      for (int child : new int[]{0, 2, 3, 4, 5}) {
         Vector3f parentAnchor;
         Vector3f childAnchor;
         if (localAnchors != null) {
            parentAnchor = localAnchors[child][0];
            childAnchor = localAnchors[child][1];
         } else {
            parentAnchor = new Vector3f(pivots[child]).sub(centers[1]).mul(0.9375F);
            childAnchor = new Vector3f(pivots[child]).sub(centers[child]).mul(0.9375F);
         }

         this.joints.add(new JointedRagdoll.Joint(child, parentAnchor, childAnchor));
      }
   }

   void tick(ClientWorld level, boolean stage, double floor) {
      this.tick(level, stage, floor, null);
   }

   void tick(List<Box> collisionShapes) {
      this.tick(null, false, 0.0, collisionShapes);
   }

   private void tick(ClientWorld level, boolean stage, double floor, List<Box> collisionShapes) {
      double incomingDown = 0.0;

      for (JointedRagdoll.Rigid body : this.bodies) {
         incomingDown = Math.max(incomingDown, Math.max(0.0, -body.velocity.y));
      }

      this.impact = 0.0;
      boolean disturbed = false;

      for (int i = 0; i < 6; i++) {
         if (!stage && level != null) {
            this.pieces[i].collidePlayers(level);
         }

         if (this.bodies[i].position.squaredDistanceTo(this.pieces[i].position) > 1.0E-10
            || this.bodies[i].velocity.squaredDistanceTo(this.pieces[i].velocity) > 1.0E-10) {
            this.bodies[i].position = this.pieces[i].position;
            this.bodies[i].velocity = this.pieces[i].velocity;
            disturbed = true;
         }
      }

      if (!this.sleeping || disturbed) {
         this.sleeping = false;

         for (BodyFragment piece : this.pieces) {
            piece.wake();
            piece.previous = piece.position;
            piece.previousRotation.set(piece.rotation);
         }

         for (int substep = 0; substep < 6; substep++) {
            if (this.suspension != null) {
               this.suspension.predict(0.16666667F, this.bodies);
            }

            for (JointedRagdoll.Rigid body : this.bodies) {
               body.predict();
            }

            List<List<Box>> terrain = new ArrayList<>(6);

            for (JointedRagdoll.Rigid body : this.bodies) {
               List<Box> boxes = new ArrayList<>();
               if (!stage) {
                  Box search = body.bounds().expand(0.12);
                  if (collisionShapes != null) {
                     for (Box box : collisionShapes) {
                        if (search.intersects(box)) {
                           boxes.add(box);
                        }
                     }
                  } else {
                     for (VoxelShape shape : level.getBlockCollisions(null, search)) {
                        boxes.addAll(shape.getBoundingBoxes());
                     }
                  }
               }

               terrain.add(boxes);
            }

            for (int iteration = 0; iteration < 10; iteration++) {
               for (int j = 0; j < this.joints.size(); j++) {
                  this.solveJoint(this.joints.get(iteration % 2 == 0 ? j : this.joints.size() - 1 - j));
               }

               for (int i = 0; i < 6; i++) {
                  for (int j = i + 1; j < 6; j++) {
                     if (i != 1 && j != 1) {
                        contact(this.bodies[i], this.bodies[j], null, true);
                     }
                  }
               }

               for (int i = 0; i < 6; i++) {
                  if (stage) {
                     floorContact(this.bodies[i], floor, true);
                  } else {
                     for (Box box : terrain.get(i)) {
                        contact(this.bodies[i], null, box, true);
                     }
                  }
               }

               if (this.suspension != null) {
                  this.suspension.project(this.bodies);
               }
            }

            if (this.suspension != null) {
               this.suspension.closeAttachment(this.bodies[0]);
            }

            for (JointedRagdoll.Rigid body : this.bodies) {
               body.reconstruct();
            }

            if (this.suspension != null) {
               this.suspension.reconstruct();
            }

            for (int iteration = 0; iteration < 6; iteration++) {
               for (JointedRagdoll.Joint joint : this.joints) {
                  this.solveJointVelocity(joint);
               }

               for (int i = 0; i < 6; i++) {
                  if (stage) {
                     floorContact(this.bodies[i], floor, false);
                  } else {
                     for (Box box : terrain.get(i)) {
                        contact(this.bodies[i], null, box, false);
                     }
                  }
               }

               for (int i = 0; i < 6; i++) {
                  for (int j = i + 1; j < 6; j++) {
                     if (i != 1 && j != 1) {
                        contact(this.bodies[i], this.bodies[j], null, false);
                     }
                  }
               }

               if (this.suspension != null) {
                  this.suspension.solveVelocity(this.bodies);
               }
            }
         }

         double motion = 0.0;
         int supported = 0;

         for (int i = 0; i < 6; i++) {
            JointedRagdoll.Rigid body = this.bodies[i];
            Vec3d angular = rotationVector(new Quaternionf(body.orientation).mul(this.pieces[i].worldRotation().conjugate()));
            motion = Math.max(motion, body.position.squaredDistanceTo(this.pieces[i].previous) + angular.lengthSquared() * 0.1);
            if (body.supported) {
               supported++;
            }

            this.pieces[i].position = body.position;
            this.pieces[i].velocity = body.velocity;
            this.pieces[i].worldRotation(body.orientation);
         }

         this.lastMotion = motion;
         this.filteredMotion = this.filteredMotion * 0.85 + motion * 0.15;
         boolean resting = this.suspension == null ? supported >= 2 : this.suspension.canRest(this.bodies);
         double motionLimit = this.suspension == null ? 3.0E-4 : 3.0E-7;
         if (resting && this.filteredMotion < motionLimit && this.maxJointError() < (this.suspension == null ? 0.045 : 0.02)) {
            this.quiet++;
         } else {
            this.quiet = 0;
         }

         if (this.quiet >= (this.suspension == null ? 24 : 32)) {
            this.sleeping = true;

            for (int i = 0; i < 6; i++) {
               this.bodies[i].velocity = Vec3d.ZERO;
               this.bodies[i].omega = Vec3d.ZERO;
               this.pieces[i].sleep();
            }
         }

         if (Arrays.stream(this.bodies).anyMatch(body -> body.groundSupported)) {
            this.impact = incomingDown;
         }
      }
   }

   double maxJointError() {
      double worst = 0.0;

      for (JointedRagdoll.Joint joint : this.joints) {
         worst = Math.max(worst, this.bodies[1].point(joint.parentAnchor).distanceTo(this.bodies[joint.child].point(joint.childAnchor)));
      }

      return worst;
   }

   void impulse(int part, Vec3d impulse, Vec3d offset) {
      this.bodies[part].impulse(impulse, offset);
      this.sleeping = false;
      this.quiet = 0;
      this.filteredMotion = Math.max(this.filteredMotion, 0.001);
      this.pieces[part].wake();
      this.pieces[part].velocity = this.bodies[part].velocity;
   }

   void detach(int child) {
      this.joints.removeIf(joint -> joint.child() == child);
   }

   void placePart(int part, Vec3d position) {
      JointedRagdoll.Rigid body = this.bodies[part];
      body.position = position;
      body.before = position;
      body.velocity = Vec3d.ZERO;
      body.omega = Vec3d.ZERO;
      this.pieces[part].position = position;
      this.pieces[part].previous = position;
      this.pieces[part].velocity = Vec3d.ZERO;
   }

   void guideVelocity(int part, Vec3d target, float strength) {
      JointedRagdoll.Rigid body = this.bodies[part];
      float blend = Math.max(0.0F, Math.min(1.0F, strength));
      Vec3d velocity = body.velocity.add(target.subtract(body.velocity).multiply(blend));
      body.velocity = velocity;
      this.pieces[part].velocity = velocity;
      this.pieces[part].wake();
      this.sleeping = false;
      this.quiet = 0;
      this.filteredMotion = Math.max(this.filteredMotion, 0.001);
   }

   int upperGroundContact() {
      return this.bodies[1].groundSupported ? 1 : (this.bodies[0].groundSupported ? 0 : -1);
   }

   boolean sleeping() {
      return this.sleeping;
   }

   double consumeImpact() {
      double value = this.impact;
      this.impact = 0.0;
      return value;
   }

   String diagnostics() {
      return "quiet="
         + this.quiet
         + " gap="
         + this.maxJointError()
         + " frameMotion="
         + this.lastMotion
         + " filtered="
         + this.filteredMotion
         + " supported="
         + Arrays.stream(this.bodies).filter(b -> b.supported).count()
         + " motion="
         + Arrays.toString(Arrays.stream(this.bodies).mapToDouble(b -> b.velocity.lengthSquared() + b.omega.lengthSquared() * 0.1).toArray());
   }

   private void solveJoint(JointedRagdoll.Joint joint) {
      JointedRagdoll.Rigid a = this.bodies[1];
      JointedRagdoll.Rigid b = this.bodies[joint.child];

      for (Vec3d axis : AXES) {
         Vec3d pa = a.point(joint.parentAnchor);
         Vec3d pb = b.point(joint.childAnchor);
         Vec3d ra = pa.subtract(a.position);
         Vec3d rb = pb.subtract(b.position);
         double error = pb.subtract(pa).dotProduct(axis);
         double weight = a.weight(ra, axis) + b.weight(rb, axis);
         Vec3d impulse = axis.multiply(error * 0.85 / weight);
         a.project(impulse, ra);
         b.project(impulse.multiply(-1.0), rb);
      }

      Quaternionf relative = new Quaternionf(a.orientation).conjugate().mul(b.orientation).normalize();
      if (relative.w < 0.0F) {
         relative.set(-relative.x, -relative.y, -relative.z, -relative.w);
      }

      Quaternionf twist = new Quaternionf(0.0F, relative.y, 0.0F, relative.w);
      if (twist.lengthSquared() < 1.0E-8) {
         twist.identity();
      } else {
         twist.normalize();
      }

      Quaternionf swing = new Quaternionf(relative).mul(new Quaternionf(twist).conjugate()).normalize();
      Vec3d rotation = rotationVector(swing);
      double forward = joint.child == 0 ? 0.8 : (joint.child < 4 ? 2.55 : 1.55);
      double sideways = joint.child == 0 ? 0.65 : (joint.child < 4 ? 2.45 : 0.65);
      double ellipse = Math.sqrt(rotation.x * rotation.x / (forward * forward) + rotation.z * rotation.z / (sideways * sideways));
      if (ellipse > 1.0) {
         rotation = rotation.multiply(1.0 / ellipse);
      }

      double twistLimit = joint.child == 0 ? 1.05 : (joint.child < 4 ? 1.15 : 0.5);
      double twistAngle = Math.max(-twistLimit, Math.min(twistLimit, 2.0 * Math.atan2(twist.y, twist.w)));
      Quaternionf limited = rotationQuaternion(rotation).mul(new Quaternionf().rotationY((float)twistAngle));
      Vec3d correction = rotationVector(limited.mul(new Quaternionf(relative).conjugate()));
      if (correction.lengthSquared() > 1.0E-10) {
         correction = rotate(a.orientation, correction).multiply(0.45);
         Vec3d axis = correction.multiply(1.0 / correction.length());
         double wa = axis.dotProduct(a.inertia(axis));
         double wb = axis.dotProduct(b.inertia(axis));
         a.rotate(correction.multiply(-wa / (wa + wb)));
         b.rotate(correction.multiply(wb / (wa + wb)));
      }
   }

   private void solveJointVelocity(JointedRagdoll.Joint joint) {
      JointedRagdoll.Rigid a = this.bodies[1];
      JointedRagdoll.Rigid b = this.bodies[joint.child];
      Vec3d ra = a.point(joint.parentAnchor).subtract(a.position);
      Vec3d rb = b.point(joint.childAnchor).subtract(b.position);

      for (Vec3d axis : AXES) {
         double closing = b.at(rb).subtract(a.at(ra)).dotProduct(axis);
         Vec3d impulse = axis.multiply(closing / (a.weight(ra, axis) + b.weight(rb, axis)));
         a.impulse(impulse, ra);
         b.impulse(impulse.multiply(-1.0), rb);
      }
   }

   private static void floorContact(JointedRagdoll.Rigid body, double floor, boolean project) {
      surfaceContact(body, null, UP, floor, project);
   }

   static void contact(JointedRagdoll.Rigid a, JointedRagdoll.Rigid b, Box box, boolean project) {
      contact(a, b, box, project, false);
   }

   static void contact(JointedRagdoll.Rigid a, JointedRagdoll.Rigid b, Box box, boolean project, boolean fullShape) {
      Box other = b == null ? box : b.bounds();
      if (a.bounds().expand(0.002).intersects(other.expand(0.002))) {
         double margin = project ? 0.0 : 0.002;
         Vec3d penetration = BodyFragment.contact(
            b == null ? box.getCenter() : b.position,
            b == null ? AXES : b.axes(),
            b == null
               ? new Vector3f((float)box.getLengthX() * 0.5F, (float)box.getLengthY() * 0.5F, (float)box.getLengthZ() * 0.5F)
               : (fullShape ? b.half : b.pairHalf),
            a.position,
            a.axes(),
            b != null && !fullShape ? a.pairHalf : a.half,
            margin
         );
         if (penetration != null) {
            Vec3d normal = penetration.multiply(1.0 / penetration.length());
            if (b == null && (Math.abs(normal.x) > 0.9999 || Math.abs(normal.y) > 0.9999 || Math.abs(normal.z) > 0.9999)) {
               double plane = box.getCenter().dotProduct(normal)
                  + Math.abs(normal.x) * box.getLengthX() * 0.5
                  + Math.abs(normal.y) * box.getLengthY() * 0.5
                  + Math.abs(normal.z) * box.getLengthZ() * 0.5;
               if (surfaceContact(a, box, normal, plane, project)) {
                  return;
               }
            }

            Vec3d point = a.support(normal.multiply(-1.0));
            if (b != null) {
               point = point.add(b.support(normal)).multiply(0.5);
            } else {
               point = intersectionCenter(a, box);
            }

            resolve(a, b, normal, Math.max(0.0, penetration.length() - margin), point, project);
         }
      }
   }

   private static boolean surfaceContact(JointedRagdoll.Rigid body, Box box, Vec3d normal, double plane, boolean project) {
      boolean hit = false;

      for (int i = 0; i < 8; i++) {
         Vec3d point = body.corner(i);
         double depth = plane - point.dotProduct(normal);
         if (!(depth < (project ? 0.0 : -0.002))
            && (
               box == null
                  || (!(Math.abs(normal.x) < 0.5) || !(point.x < box.minX - 0.002) && !(point.x > box.maxX + 0.002))
                     && (!(Math.abs(normal.y) < 0.5) || !(point.y < box.minY - 0.002) && !(point.y > box.maxY + 0.002))
                     && (!(Math.abs(normal.z) < 0.5) || !(point.z < box.minZ - 0.002) && !(point.z > box.maxZ + 0.002))
            )) {
            resolve(body, null, normal, Math.max(0.0, depth), point.add(normal.multiply(depth)), project);
            hit = true;
         }
      }

      return hit;
   }

   private static Vec3d intersectionCenter(JointedRagdoll.Rigid body, Box box) {
      Vec3d total = Vec3d.ZERO;
      int count = 0;
      Quaternionf inverse = new Quaternionf(body.orientation).conjugate();
      Box local = new Box(-body.half.x, -body.half.y, -body.half.z, body.half.x, body.half.y, body.half.z);

      for (int i = 0; i < 8; i++) {
         for (int axis = 0; axis < 3; axis++) {
            if ((i & 1 << axis) == 0) {
               Vec3d from = body.corner(i);
               Vec3d to = body.corner(i | 1 << axis);
               double[] interval = clipSegment(from, to, box);
               if (interval != null) {
                  total = total.add(from.lerp(to, interval[0])).add(from.lerp(to, interval[1]));
                  count += 2;
               }

               from = new Vec3d((i & 1) == 0 ? box.minX : box.maxX, (i & 2) == 0 ? box.minY : box.maxY, (i & 4) == 0 ? box.minZ : box.maxZ);
               int j = i | 1 << axis;
               to = new Vec3d((j & 1) == 0 ? box.minX : box.maxX, (j & 2) == 0 ? box.minY : box.maxY, (j & 4) == 0 ? box.minZ : box.maxZ);
               interval = clipSegment(rotate(inverse, from.subtract(body.position)), rotate(inverse, to.subtract(body.position)), local);
               if (interval != null) {
                  total = total.add(from.lerp(to, interval[0])).add(from.lerp(to, interval[1]));
                  count += 2;
               }
            }
         }
      }

      return count == 0 ? body.support(box.getCenter().subtract(body.position)) : total.multiply(1.0 / count);
   }

   private static double[] clipSegment(Vec3d from, Vec3d to, Box box) {
      double lo = 0.0;
      double hi = 1.0;
      double[] start = new double[]{from.x, from.y, from.z};
      double[] end = new double[]{to.x, to.y, to.z};
      double[] min = new double[]{box.minX, box.minY, box.minZ};
      double[] max = new double[]{box.maxX, box.maxY, box.maxZ};

      for (int i = 0; i < 3; i++) {
         double delta = end[i] - start[i];
         if (Math.abs(delta) < 1.0E-10) {
            if (start[i] < min[i] || start[i] > max[i]) {
               return null;
            }
         } else {
            double a = (min[i] - start[i]) / delta;
            double b = (max[i] - start[i]) / delta;
            lo = Math.max(lo, Math.min(a, b));
            hi = Math.min(hi, Math.max(a, b));
            if (lo > hi) {
               return null;
            }
         }
      }

      return new double[]{lo, hi};
   }

   private static void resolve(JointedRagdoll.Rigid a, JointedRagdoll.Rigid b, Vec3d normal, double depth, Vec3d point, boolean project) {
      Vec3d ra = point.subtract(a.position);
      Vec3d rb = b == null ? Vec3d.ZERO : point.subtract(b.position);
      double weight = a.weight(ra, normal) + (b == null ? 0.0 : b.weight(rb, normal));
      if (project) {
         Vec3d impulse = normal.multiply(Math.min(0.15, Math.max(0.0, depth)) * 0.9 / weight);
         a.project(impulse, ra);
         if (b != null) {
            b.project(impulse.multiply(-1.0), rb);
         }

         if (b == null) {
            a.normalLoad = a.normalLoad + impulse.length() / 0.1666666716337204;
            Vec3d local = rotate(new Quaternionf(a.orientation).conjugate(), point.subtract(a.position));
            Vec3d oldPoint = a.before.add(rotate(a.old, local));
            Vec3d slip = point.subtract(oldPoint);
            slip = slip.subtract(normal.multiply(slip.dotProduct(normal)));
            double distance = slip.length();
            if (distance > 1.0E-8) {
               Vec3d tangent = slip.multiply(1.0 / distance);
               double stop = distance / a.weight(ra, tangent);
               double budget = Math.max(0.0, a.normalLoad * 0.1666666716337204 * 0.6 - a.frictionLoad);
               double applied = Math.min(stop, budget);
               a.project(tangent.multiply(-applied), ra);
               a.frictionLoad += applied;
            }
         }

         if (normal.y > 0.6) {
            a.supported = true;
            if (b == null) {
               a.groundSupported = true;
            }
         }

         if (b != null && normal.y < -0.6) {
            b.supported = true;
         }
      } else {
         Vec3d velocity = a.at(ra).subtract(b == null ? Vec3d.ZERO : b.at(rb));
         double closing = velocity.dotProduct(normal);
         double normalImpulse = Math.max(0.0, -closing / weight) + Math.max(0.0, depth) / (0.1666666716337204 * weight);
         if (b == null) {
            normalImpulse = Math.max(normalImpulse, a.normalLoad);
         }

         if (closing < 0.0) {
            Vec3d impulse = normal.multiply(-closing / weight);
            a.impulse(impulse, ra);
            if (b != null) {
               b.impulse(impulse.multiply(-1.0), rb);
            }
         }

         velocity = a.at(ra).subtract(b == null ? Vec3d.ZERO : b.at(rb));
         Vec3d tangent = velocity.subtract(normal.multiply(velocity.dotProduct(normal)));
         double speed = tangent.length();
         if (!(speed < 1.0E-8)) {
            tangent = tangent.multiply(1.0 / speed);
            double tangentWeight = a.weight(ra, tangent) + (b == null ? 0.0 : b.weight(rb, tangent));
            double stop = speed / tangentWeight;
            double friction = stop <= normalImpulse * 0.7 ? stop : Math.min(stop, normalImpulse * 0.5);
            Vec3d impulse = tangent.multiply(-friction);
            a.impulse(impulse, ra);
            if (b != null) {
               b.impulse(impulse.multiply(-1.0), rb);
            }
         }
      }
   }

   private static Quaternionf rotationQuaternion(Vec3d vector) {
      double length = vector.length();
      return length < 1.0E-10
         ? new Quaternionf()
         : new Quaternionf().rotationAxis((float)length, (float)(vector.x / length), (float)(vector.y / length), (float)(vector.z / length));
   }

   private static Vec3d rotationVector(Quaternionf q) {
      if (q.w < 0.0F) {
         q.set(-q.x, -q.y, -q.z, -q.w);
      }

      double sin = Math.sqrt(q.x * q.x + q.y * q.y + q.z * q.z);
      return sin < 1.0E-10 ? Vec3d.ZERO : new Vec3d(q.x, q.y, q.z).multiply(2.0 * Math.atan2(sin, q.w) / sin);
   }

   static Vec3d rotate(Quaternionf q, Vec3d value) {
      Vector3f v = q.transform(new Vector3f((float)value.x, (float)value.y, (float)value.z));
      return new Vec3d(v.x, v.y, v.z);
   }

   @Environment(EnvType.CLIENT)
   private record Joint(int child, Vector3f parentAnchor, Vector3f childAnchor) {
   }

   @Environment(EnvType.CLIENT)
   static final class Rigid {
      Vec3d position;
      Vec3d velocity;
      Vec3d omega = Vec3d.ZERO;
      Vec3d before;
      Quaternionf orientation;
      Quaternionf old;
      final Vector3f half;
      final Vector3f pairHalf;
      final Vec3d inverseInertia;
      final double inverseMass;
      boolean supported;
      boolean groundSupported;
      double normalLoad;
      double frictionLoad;

      Rigid(BodyFragment piece, float mass) {
         this(piece.position, piece.halfExtents(), mass);
         this.velocity = piece.velocity;
         this.orientation = piece.worldRotation();
      }

      Rigid(Vec3d position, Vector3f half, float mass) {
         this.position = position;
         this.velocity = Vec3d.ZERO;
         this.orientation = new Quaternionf();
         this.half = new Vector3f(half);
         this.inverseMass = mass > 0.0F ? 1.0 / mass : 0.0;
         this.pairHalf = new Vector3f(half).sub(0.025F, 0.025F, 0.025F).max(new Vector3f(0.02F));
         this.inverseInertia = new Vec3d(
            3.0 * this.inverseMass / (half.y * half.y + half.z * half.z),
            3.0 * this.inverseMass / (half.x * half.x + half.z * half.z),
            3.0 * this.inverseMass / (half.x * half.x + half.y * half.y)
         );
      }

      void predict() {
         this.before = this.position;
         this.old = new Quaternionf(this.orientation);
         this.supported = false;
         this.groundSupported = false;
         this.normalLoad = 0.0;
         this.frictionLoad = 0.0;
         this.velocity = this.velocity.add(0.0, -0.009166666939854622, 0.0).multiply(Math.pow(0.997, 0.1666666716337204));
         this.omega = this.omega.multiply(Math.pow(0.985, 0.1666666716337204));
         this.position = this.position.add(this.velocity.multiply(0.1666666716337204));
         this.rotate(this.omega.multiply(0.1666666716337204));
      }

      void reconstruct() {
         this.velocity = this.position.subtract(this.before).multiply(6.0);
         this.omega = JointedRagdoll.rotationVector(new Quaternionf(this.orientation).mul(new Quaternionf(this.old).conjugate())).multiply(6.0);
         if (this.velocity.lengthSquared() > 2.25) {
            this.velocity = this.velocity.normalize().multiply(1.5);
         }

         if (this.omega.lengthSquared() > 1.44) {
            this.omega = this.omega.normalize().multiply(1.2);
         }
      }

      Vec3d inertia(Vec3d world) {
         Vec3d local = JointedRagdoll.rotate(new Quaternionf(this.orientation).conjugate(), world).multiply(this.inverseInertia);
         return JointedRagdoll.rotate(this.orientation, local);
      }

      double weight(Vec3d r, Vec3d axis) {
         Vec3d cross = r.crossProduct(axis);
         return this.inverseMass + cross.dotProduct(this.inertia(cross));
      }

      Vec3d at(Vec3d r) {
         return this.velocity.add(this.omega.crossProduct(r));
      }

      void impulse(Vec3d impulse, Vec3d r) {
         this.velocity = this.velocity.add(impulse.multiply(this.inverseMass));
         this.omega = this.omega.add(this.inertia(r.crossProduct(impulse)));
      }

      void project(Vec3d impulse, Vec3d r) {
         this.position = this.position.add(impulse.multiply(this.inverseMass));
         this.rotate(this.inertia(r.crossProduct(impulse)));
      }

      void rotate(Vec3d rotation) {
         if (rotation.lengthSquared() > 0.09) {
            rotation = rotation.normalize().multiply(0.3);
         }

         this.orientation = JointedRagdoll.rotationQuaternion(rotation).mul(this.orientation).normalize();
      }

      Vec3d point(Vector3f local) {
         Vector3f v = this.orientation.transform(new Vector3f(local));
         return this.position.add(v.x, v.y, v.z);
      }

      Vec3d corner(int i) {
         return this.point(
            new Vector3f((i & 1) == 0 ? -this.half.x : this.half.x, (i & 2) == 0 ? -this.half.y : this.half.y, (i & 4) == 0 ? -this.half.z : this.half.z)
         );
      }

      Vec3d[] axes() {
         return new Vec3d[]{
            JointedRagdoll.rotate(this.orientation, JointedRagdoll.AXES[0]),
            JointedRagdoll.rotate(this.orientation, JointedRagdoll.AXES[1]),
            JointedRagdoll.rotate(this.orientation, JointedRagdoll.AXES[2])
         };
      }

      Box bounds() {
         Vec3d[] axes = this.axes();
         double x = Math.abs(axes[0].x) * this.half.x + Math.abs(axes[1].x) * this.half.y + Math.abs(axes[2].x) * this.half.z;
         double y = Math.abs(axes[0].y) * this.half.x + Math.abs(axes[1].y) * this.half.y + Math.abs(axes[2].y) * this.half.z;
         double z = Math.abs(axes[0].z) * this.half.x + Math.abs(axes[1].z) * this.half.y + Math.abs(axes[2].z) * this.half.z;
         return new Box(this.position.x - x, this.position.y - y, this.position.z - z, this.position.x + x, this.position.y + y, this.position.z + z);
      }

      Vec3d support(Vec3d direction) {
         Vec3d local = JointedRagdoll.rotate(new Quaternionf(this.orientation).conjugate(), direction);
         return this.point(
            new Vector3f(
               Math.abs(local.x) < 0.015 ? 0.0F : Math.copySign(this.half.x, (float)local.x),
               Math.abs(local.y) < 0.015 ? 0.0F : Math.copySign(this.half.y, (float)local.y),
               Math.abs(local.z) < 0.015 ? 0.0F : Math.copySign(this.half.z, (float)local.z)
            )
         );
      }
   }
}
