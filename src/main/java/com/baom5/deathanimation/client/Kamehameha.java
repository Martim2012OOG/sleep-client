package com.baom5.deathanimation.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.ModelPart.Quad;
import net.minecraft.client.model.ModelPart.Vertex;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.entity.BeaconBlockEntityRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.MatrixStack.Entry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class Kamehameha {
   public static final float READY = 0.16F;
   public static final float CHARGE = 0.2F;
   public static final float CHARGED = 0.49F;
   public static final float THRUST = 0.53F;
   public static final float RELEASE = 0.61F;
   public static final float HIT = 0.657F;
   public static final float DISSOLVE = 0.68F;
   public static final float GONE = 0.83F;
   public static final float FADE = 0.9F;
   public static final float END = 0.95F;
   public static final float APPROACH_START_DISTANCE = 12.0F;
   public static final float APPROACH_DISTANCE = 1.5F;
   public static final float APPROACH_BLOCKS = 10.5F;
   public static final float BEYOND = 9.0F;
   public static final float PLAYER_SCALE = 0.9375F;
   private static final ThreadLocal<Kamehameha.Draw> DRAW = new ThreadLocal<>();
   private static final Map<ModelPart, Map<Long, List<Kamehameha.Face>>> FACES = new WeakHashMap<>();

   private Kamehameha() {
   }

   public static boolean supports(DeathScene scene) {
      return scene.animation == DeathAnimationConfig.Animation.BATCH01_KI_RECOIL;
   }

   public static void prepare(Object model, DeathScene scene, PlayerActors.Binding actor) {
      if (supports(scene)) {
         DRAW.set(new Kamehameha.Draw(model, scene, actor));
      }
   }

   public static void clear() {
      DRAW.remove();
   }

   public static boolean replaces(Object model) {
      Kamehameha.Draw draw = DRAW.get();
      return draw != null && draw.model == model && (draw.actor != null || draw.scene.progress() >= 0.657F);
   }

   private static float blend(float p, float start, float end) {
      return AnimationTimeline.smooth(AnimationTimeline.phase(p, start, end));
   }

   private static Vector3f mix(Vector3f a, Vector3f b, float t) {
      return new Vector3f(a).lerp(b, t);
   }

   private static Vector3f point(float x, float y, float z) {
      return new Vector3f(x, y, z);
   }

   private static Kamehameha.Limb solve(Vector3f start, Vector3f wanted, float halfLength, Vector3f hint) {
      Vector3f delta = new Vector3f(wanted).sub(start);
      float distance = Math.max(1.0E-4F, delta.length());
      Vector3f direction = new Vector3f(delta).div(distance);
      distance = Math.min(distance, halfLength * 1.995F);
      Vector3f end = new Vector3f(direction).mul(distance).add(start);
      Vector3f bend = new Vector3f(hint).sub(new Vector3f(direction).mul(hint.dot(direction)));
      if (bend.lengthSquared() < 1.0E-5F) {
         bend.set(0.0F, 0.0F, 1.0F);
      }

      bend.normalize().mul((float)Math.sqrt(Math.max(0.0F, halfLength * halfLength - distance * distance * 0.25F)));
      return new Kamehameha.Limb(new Vector3f(start), new Vector3f(start).add(end).mul(0.5F).add(bend), end);
   }

   public static Kamehameha.ActorPose actorPose(float p) {
      float ready = blend(p, 0.0F, 0.16F);
      float fire = blend(p, 0.53F, 0.6F);
      float recover = blend(p, 0.95F, 1.0F);
      float approach = 1.0F - ready;
      float runPhase = p / 0.16F * 3.1415927F * 6.0F;
      float stride = (float)Math.sin(runPhase);
      float crouch = 0.19F * ready * (1.0F - 0.7F * recover);
      Vector3f hip = point(0.0F, 0.75F + crouch - 0.035F * approach * (0.5F + 0.5F * (float)Math.cos(runPhase * 2.0F)), 0.0F);
      float yaw = -35.0F * ready * (1.0F - fire) * (1.0F - recover);
      float lean = (-10.0F * ready * (1.0F - fire) + 9.0F * fire) * (1.0F - recover) - 8.0F * approach;
      Quaternionf torso = new Quaternionf().rotationY((float)Math.toRadians(yaw)).rotateX((float)Math.toRadians(lean));
      Vector3f leftShoulder = torso.transform(point(0.375F, -0.72F, 0.0F)).add(hip);
      Vector3f rightShoulder = torso.transform(point(-0.375F, -0.72F, 0.0F)).add(hip);
      Vector3f leftRest = point(0.375F, 0.74F, 0.0F);
      Vector3f rightRest = point(-0.375F, 0.74F, 0.0F);
      Vector3f leftCharge = point(-0.14F, 0.51F + crouch, 0.32F);
      Vector3f rightCharge = point(-0.34F, 0.69F + crouch, 0.32F);
      Vector3f leftFire = point(0.13F, 0.2F + crouch, -0.78F);
      Vector3f rightFire = point(-0.13F, 0.2F + crouch, -0.78F);
      Vector3f leftHand = mix(mix(leftRest, leftCharge, ready), leftFire, fire).lerp(leftRest, recover);
      Vector3f rightHand = mix(mix(rightRest, rightCharge, ready), rightFire, fire).lerp(rightRest, recover);
      leftHand.add(0.0F, 0.08F * stride * approach, 0.16F * stride * approach);
      rightHand.add(0.0F, -0.08F * stride * approach, -0.16F * stride * approach);
      Kamehameha.Limb leftArm = solve(leftShoulder, leftHand, 0.4F, point(1.0F, 0.1F, 0.4F));
      Kamehameha.Limb rightArm = solve(rightShoulder, rightHand, 0.4F, point(-1.0F, 0.1F, 0.4F));
      Vector3f leftFoot = point(0.12F + 0.31F * ready, 1.501F - 0.12F * Math.max(0.0F, stride) * approach, -0.22F * ready + 0.24F * stride * approach);
      Vector3f rightFoot = point(-0.12F - 0.28F * ready, 1.501F - 0.12F * Math.max(0.0F, -stride) * approach, 0.24F * ready - 0.24F * stride * approach);
      Kamehameha.Limb leftLeg = solve(new Vector3f(hip).add(0.12F, 0.0F, 0.0F), leftFoot, 0.375F, point(0.2F, 0.0F, -1.0F));
      Kamehameha.Limb rightLeg = solve(new Vector3f(hip).add(-0.12F, 0.0F, 0.0F), rightFoot, 0.375F, point(-0.2F, 0.0F, -1.0F));
      Quaternionf head = new Quaternionf().rotationY((float)Math.toRadians(-yaw)).rotateX((float)Math.toRadians(8.0F * (1.0F - fire) * (1.0F - recover)));
      return new Kamehameha.ActorPose(hip, torso, head, leftArm, rightArm, leftLeg, rightLeg);
   }

   public static PlayerActors.HandPose hands(PlayerActors.Binding binding) {
      Kamehameha.ActorPose pose = actorPose(binding.scene().progress());
      Vec3d left = actorWorld(binding, pose.leftArm.end);
      Vec3d right = actorWorld(binding, pose.rightArm.end);
      Vec3d origin = left.add(right).multiply(0.5);
      Vec3d aim = new Vec3d(binding.scene().x, binding.scene().floorY + 1.0, binding.scene().z).subtract(origin).normalize();
      return new PlayerActors.HandPose(origin, new Vector3f((float)aim.x, (float)aim.y, (float)aim.z));
   }

   public static Vec3d actorWorld(PlayerActors.Binding binding, Vector3f model) {
      DeathScene.Root k = AnimationCatalog.sample(binding.cue().keys(), binding.scene().progress());
      Vector3f local = new Vector3f(-model.x, 1.501F - model.y, model.z).mul(0.9375F).mul(k.sx(), k.sy(), k.sz());
      Quaternionf rotation = new Quaternionf()
         .rotationY((float)Math.toRadians(k.yaw() + binding.scene().sceneFacing))
         .rotateX((float)Math.toRadians(k.pitch()))
         .rotateZ((float)Math.toRadians(k.roll()));
      rotation.transform(local);
      return new Vec3d(binding.avatar().getX() + local.x, binding.avatar().getY() + local.y, binding.avatar().getZ() + local.z);
   }

   public static Kamehameha.Beam beam(DeathScene scene) {
      Vec3d origin = PlayerActors.handPose(scene).midpoint();
      Vec3d target = new Vec3d(scene.x, scene.floorY + 1.0, scene.z);
      Vec3d toward = target.subtract(origin);
      double distance = toward.length();
      Vec3d direction = toward.normalize();
      float p = scene.progress();
      float strength = blend(p, 0.61F, 0.635F) * (1.0F - blend(p, 0.9F, 0.95F));
      float length = (float)(distance * blend(p, 0.61F, 0.657F) + 9.0F * blend(p, 0.657F, 0.7F));
      return new Kamehameha.Beam(origin, direction, length, strength);
   }

   public static void submitBeam(DeathScene scene, MatrixStack poses, OrderedRenderCommandQueue collector, Vec3d relativeTo) {
      if (supports(scene) && DeathAnimationConfig.INSTANCE.visualEffects) {
         if (!(scene.progress() < 0.2F) && !(scene.progress() >= 0.95F) && !scene.props.isEmpty() && PlayerActors.forEntity(scene.props.getFirst()) != null) {
            submitCharge(scene, poses, collector, relativeTo);
            if (!(scene.progress() < 0.61F)) {
               Kamehameha.Beam beam = beam(scene);
               if (!(beam.strength < 0.001F) && !(beam.length < 0.001F)) {
                  poses.push();
                  poses.translate(beam.origin.x - relativeTo.x, beam.origin.y - relativeTo.y, beam.origin.z - relativeTo.z);
                  poses.multiply(
                     new Quaternionf()
                        .rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), new Vector3f((float)beam.direction.x, (float)beam.direction.y, (float)beam.direction.z))
                  );
                  poses.scale(1.0F, beam.length, 1.0F);
                  poses.translate(-0.5, 0.0, -0.5);
                  float pulse = 1.0F + 0.025F * (float)Math.sin(scene.age * 0.75);
                  BeaconBlockEntityRenderer.renderBeam(
                     poses, collector, BeaconBlockEntityRenderer.BEAM_TEXTURE, 1.0F, scene.age, 0, 1, -12206081, 0.015F, 1.18F * beam.strength * pulse
                  );
                  BeaconBlockEntityRenderer.renderBeam(
                     poses,
                     collector,
                     BeaconBlockEntityRenderer.BEAM_TEXTURE,
                     1.0F,
                     scene.age,
                     0,
                     1,
                     -1770497,
                     0.7F * beam.strength,
                     1.02F * beam.strength * pulse
                  );
                  poses.pop();
               }
            }
         }
      }
   }

   private static void submitCharge(DeathScene scene, MatrixStack poses, OrderedRenderCommandQueue collector, Vec3d relativeTo) {
      float size = AnimationCatalog.sample(AnimationCatalog.recipe(scene.animation).props().get(1).keys(), scene.progress()).sx();
      if (!(size < 0.002F)) {
         Vec3d hands = PlayerActors.handPose(scene).midpoint();
         poses.push();
         poses.translate(hands.x - relativeTo.x, hands.y - relativeTo.y, hands.z - relativeTo.z);
         Identifier texture = Identifier.ofVanilla("textures/block/white_concrete.png");
         float radius = size * 0.43F;
         collector.submitCustom(poses, RenderLayers.beaconBeam(texture, false), (pose, vertices) -> sphere(pose, vertices, radius * 0.86F, -1442817));
         collector.submitCustom(poses, RenderLayers.beaconBeam(texture, true), (pose, vertices) -> sphere(pose, vertices, radius * 1.16F, 1162529791));
         poses.pop();
      }
   }

   private static void sphere(Entry pose, VertexConsumer vertices, float radius, int color) {
      for (int latitude = 0; latitude < 16; latitude++) {
         for (int longitude = 0; longitude < 24; longitude++) {
            for (int corner = 0; corner < 4; corner++) {
               float u = (longitude + (corner != 1 && corner != 2 ? 0 : 1)) / 24.0F;
               float v = (latitude + (corner >= 2 ? 1 : 0)) / 16.0F;
               float polar = v * 3.1415927F;
               float azimuth = u * 3.1415927F * 2.0F;
               float x = (float)(Math.sin(polar) * Math.cos(azimuth));
               float y = (float)Math.cos(polar);
               float z = (float)(Math.sin(polar) * Math.sin(azimuth));
               vertices.vertex(pose, x * radius, y * radius, z * radius).color(color).texture(u, v).overlay(655360).light(15728880).normal(pose, x, y, z);
            }
         }
      }
   }

   public static void render(
      BipedEntityModel<?> model, DeathBodyRig.Pose original, MatrixStack poses, VertexConsumer vertices, int light, int overlay, int color
   ) {
      Kamehameha.Draw draw = DRAW.get();
      if (draw != null) {
         if (draw.actor != null) {
            renderActor(model, original, poses, vertices, light, overlay, color, draw.scene.progress());
         } else {
            renderVictim(model, original, poses, vertices, overlay, color, draw.scene.progress());
         }
      }
   }

   private static void nativePart(ModelPart part, MatrixStack poses, VertexConsumer vertices, int light, int overlay, int color) {
      part.visible = true;
      part.setOrigin(0.0F, 0.0F, 0.0F);
      part.setAngles(0.0F, 0.0F, 0.0F);
      part.render(poses, vertices, light, overlay, color);
   }

   private static void renderActor(
      BipedEntityModel<?> model, DeathBodyRig.Pose original, MatrixStack poses, VertexConsumer vertices, int light, int overlay, int color, float p
   ) {
      Kamehameha.ActorPose pose = actorPose(p);
      poses.push();
      poses.translate(pose.hip.x, pose.hip.y, pose.hip.z);
      poses.multiply(pose.torso);
      poses.translate(0.0, -0.75, 0.0);
      if (original.original(DeathBodyRig.Part.TORSO).visible()) {
         nativePart(model.body, poses, vertices, light, overlay, color);
      }

      poses.push();
      poses.multiply(pose.head);
      if (original.original(DeathBodyRig.Part.HEAD).visible()) {
         nativePart(model.head, poses, vertices, light, overlay, color);
      }

      poses.pop();
      poses.pop();
      limb(model.leftArm, pose.leftArm, true, poses, vertices, light, overlay, color);
      limb(model.rightArm, pose.rightArm, true, poses, vertices, light, overlay, color);
      limb(model.leftLeg, pose.leftLeg, false, poses, vertices, light, overlay, color);
      limb(model.rightLeg, pose.rightLeg, false, poses, vertices, light, overlay, color);
   }

   private static void limb(ModelPart part, Kamehameha.Limb limb, boolean arm, MatrixStack poses, VertexConsumer vertices, int light, int overlay, int color) {
      List<Kamehameha.Face> mesh = faces(part);
      float minY = arm ? -0.125F : 0.0F;
      float midY = minY + 0.375F;
      float maxY = midY + 0.375F;
      float centerX = 0.0F;
      float low = 1.0F / 0.0F;
      float high = -1.0F / 0.0F;

      for (Kamehameha.Face f : mesh) {
         for (Kamehameha.V v : f.vertices) {
            low = Math.min(low, v.p.x);
            high = Math.max(high, v.p.x);
         }
      }

      if (Float.isFinite(low)) {
         centerX = (low + high) * 0.5F;
      }

      segment(mesh, limb.start, limb.joint, minY, midY, centerX, poses, vertices, light, overlay, color);
      segment(mesh, limb.joint, limb.end, midY, maxY, centerX, poses, vertices, light, overlay, color);
   }

   private static void segment(
      List<Kamehameha.Face> mesh,
      Vector3f start,
      Vector3f end,
      float from,
      float to,
      float centerX,
      MatrixStack poses,
      VertexConsumer vertices,
      int light,
      int overlay,
      int color
   ) {
      Vector3f direction = new Vector3f(end).sub(start);
      float length = direction.length();
      poses.push();
      poses.translate(start.x, start.y, start.z);
      poses.multiply(new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), direction.normalize()));
      poses.scale(1.0F, length / (to - from), 1.0F);
      poses.translate(-centerX, -from, 0.0F);

      for (Kamehameha.Face face : mesh) {
         List<Kamehameha.V> clipped = clip(clip(Arrays.asList(face.vertices), from, true), to, false);
         if (clipped.size() == 4) {
            emit(clipped.toArray(Kamehameha.V[]::new), face.normal, poses, vertices, light, overlay, color, new Vector3f(), 1.0F);
         }
      }

      poses.pop();
   }

   private static List<Kamehameha.V> clip(List<Kamehameha.V> input, float plane, boolean above) {
      ArrayList<Kamehameha.V> output = new ArrayList<>();
      if (input.isEmpty()) {
         return output;
      }

      Kamehameha.V a = input.getLast();
      boolean ai = above ? a.p.y >= plane : a.p.y <= plane;

      for (Kamehameha.V b : input) {
         boolean bi = above ? b.p.y >= plane : b.p.y <= plane;
         if (ai != bi) {
            float t = (plane - a.p.y) / (b.p.y - a.p.y);
            output.add(new Kamehameha.V(mix(a.p, b.p, t), a.u + (b.u - a.u) * t, a.v + (b.v - a.v) * t));
         }

         if (bi) {
            output.add(b);
         }

         a = b;
         ai = bi;
      }

      return output;
   }

   private static List<Kamehameha.Face> faces(ModelPart part) {
      return FACES.computeIfAbsent(part, ignored -> new HashMap<>())
         .computeIfAbsent(
            PlayerSkinLayers.visibilityKey(part),
            ignored -> {
               ModelPart key = part;
               ModelTransform saved = key.getTransform();
               ArrayList<Kamehameha.Face> output = new ArrayList<>();

               try {
                  key.setOrigin(0.0F, 0.0F, 0.0F);
                  key.setAngles(0.0F, 0.0F, 0.0F);
                  key.forEachCuboid(
                     new MatrixStack(),
                     (pose, path, index, cube) -> {
                        if (PlayerSkinLayers.visibleChildPath(key, path)) {
                           for (Quad polygon : cube.sides) {
                              Kamehameha.V[] points = new Kamehameha.V[4];
                              int i = 0;

                              for (Vertex v : polygon.vertices()) {
                                 points[i++] = new Kamehameha.V(
                                    pose.getPositionMatrix().transformPosition(new Vector3f(v.worldX(), v.worldY(), v.worldZ())), v.u(), v.v()
                                 );
                              }

                              Vector3f normal = pose.getNormalMatrix().transform(new Vector3f(polygon.direction()));
                              output.add(new Kamehameha.Face(points, normal));
                           }
                        }
                     }
                  );
               } finally {
                  key.setTransform(saved);
               }

               return output;
            }
         );
   }

   private static void emit(
      Kamehameha.V[] face, Vector3f normal, MatrixStack poses, VertexConsumer vertices, int light, int overlay, int color, Vector3f offset, float scale
   ) {
      Vector3f center = new Vector3f();

      for (Kamehameha.V v : face) {
         center.add(v.p);
      }

      center.mul(0.25F);

      for (Kamehameha.V v : face) {
         Vector3f p = new Vector3f(v.p).sub(center).mul(scale).add(center).add(offset);
         vertices.vertex(poses.peek(), p).color(color).texture(v.u, v.v).overlay(overlay).light(light).normal(poses.peek(), normal.x, normal.y, normal.z);
      }
   }

   private static Kamehameha.V bilerp(Kamehameha.V[] face, float u, float v) {
      Vector3f a = mix(face[0].p, face[1].p, u);
      Vector3f b = mix(face[3].p, face[2].p, u);
      float tu = (face[0].u + (face[1].u - face[0].u) * u) * (1.0F - v) + (face[3].u + (face[2].u - face[3].u) * u) * v;
      float tv = (face[0].v + (face[1].v - face[0].v) * u) * (1.0F - v) + (face[3].v + (face[2].v - face[3].v) * u) * v;
      return new Kamehameha.V(mix(a, b, v), tu, tv);
   }

   private static void renderVictim(
      BipedEntityModel<?> model, DeathBodyRig.Pose original, MatrixStack poses, VertexConsumer vertices, int overlay, int color, float p
   ) {
      float dissolve = AnimationTimeline.phase(p, 0.68F, 0.83F);

      for (DeathBodyRig.Part part : DeathBodyRig.Part.values()) {
         if (original.original(part).visible()) {
            ModelPart bone = DeathBodyRig.part(model, part);
            ModelTransform initial = bone.getDefaultTransform();
            DeathBodyRig.Motion motion = original.motion(part);
            poses.push();
            poses.translate(initial.x() / 16.0F + motion.x(), initial.y() / 16.0F + motion.y(), initial.z() / 16.0F + motion.z());
            poses.multiply(new Quaternionf().rotationZYX(motion.zRot(), motion.yRot(), motion.xRot()));
            int faceIndex = 0;

            for (Kamehameha.Face face : faces(bone)) {
               int nu = Math.max(1, Math.round(face.vertices[0].p.distance(face.vertices[1].p) * 16.0F));
               int nv = Math.max(1, Math.round(face.vertices[0].p.distance(face.vertices[3].p) * 16.0F));

               for (int u = 0; u < nu; u++) {
                  for (int v = 0; v < nv; v++) {
                     long h = part.ordinal() * 5323L + faceIndex * 1117L + u * 73L + v * 2377L ^ 2654435769L;
                     h = (h ^ h >>> 16) * 73244475L;
                     float threshold = (float)(h & 65535L) / 65535.0F;
                     threshold = 0.05F + 0.78F * threshold;
                     float elapsed = dissolve - threshold;
                     if (!(elapsed > 0.16F)) {
                        Kamehameha.V[] tile = new Kamehameha.V[]{
                           bilerp(face.vertices, (float)u / nu, (float)v / nv),
                           bilerp(face.vertices, (u + 1.0F) / nu, (float)v / nv),
                           bilerp(face.vertices, (u + 1.0F) / nu, (v + 1.0F) / nv),
                           bilerp(face.vertices, (float)u / nu, (v + 1.0F) / nv)
                        };
                        Vector3f offset = new Vector3f();
                        float scale = 1.0F;
                        int tint = color;
                        if (elapsed > 0.0F) {
                           float flight = elapsed / 0.16F;
                           offset.set((float)((h >>> 8 & 31L) - 16L) * 0.008F * flight, -0.12F * flight, flight * 3.5F);
                           scale = 1.0F - flight;
                           tint = Math.round(255.0F * (1.0F - flight)) << 24 | 12186623;
                        }

                        emit(tile, face.normal, poses, vertices, 15728880, overlay, tint, offset, scale);
                     }
                  }
               }

               faceIndex++;
            }

            poses.pop();
         }
      }
   }

   @Environment(EnvType.CLIENT)
   public record ActorPose(
      Vector3f hip, Quaternionf torso, Quaternionf head, Kamehameha.Limb leftArm, Kamehameha.Limb rightArm, Kamehameha.Limb leftLeg, Kamehameha.Limb rightLeg
   ) {
   }

   @Environment(EnvType.CLIENT)
   public record Beam(Vec3d origin, Vec3d direction, float length, float strength) {
      public Vec3d end() {
         return this.origin.add(this.direction.multiply(this.length));
      }
   }

   @Environment(EnvType.CLIENT)
   private record Draw(Object model, DeathScene scene, PlayerActors.Binding actor) {
   }

   @Environment(EnvType.CLIENT)
   private record Face(Kamehameha.V[] vertices, Vector3f normal) {
   }

   @Environment(EnvType.CLIENT)
   public record Limb(Vector3f start, Vector3f joint, Vector3f end) {
   }

   @Environment(EnvType.CLIENT)
   private record V(Vector3f p, float u, float v) {
   }
}
