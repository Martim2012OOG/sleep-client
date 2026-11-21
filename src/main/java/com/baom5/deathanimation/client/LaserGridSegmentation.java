package com.baom5.deathanimation.client;

import java.util.ArrayList;
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
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.MatrixStack.Entry;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class LaserGridSegmentation {
   private static final float MODEL_SCALE = 0.9375F;
   private static final float LASER_TOLERANCE = 0.008F;
   private static final float CUT_VOXEL_DEPTH = 0.055F;
   private static final int MAX_FRAGMENTS_PER_SCENE = 16384;
   private static final int MAX_ALCHEMY_FRAGMENTS = 4096;
   private static final boolean METRICS = Boolean.getBoolean("deathanimation.fragmentMetrics");
   private final DeathScene scene;
   private final Map<BipedEntityModel<?>, Map<LaserGridSegmentation.LayerKey, LaserGridSegmentation.FragmentLayer>> layers = new WeakHashMap<>();
   private int fragmentCount;
   private long renderNanos;
   private long renderedFragments;
   private int renderCalls;

   public LaserGridSegmentation(DeathScene scene) {
      this.scene = scene;
   }

   public static boolean supports(DeathScene scene) {
      return scene.animation == DeathAnimationConfig.Animation.CURATED_RESIDENT_EVIL_LASER_HALLWAY
         || scene.animation == DeathAnimationConfig.Animation.BATCH01_BULLET_BILL
         || scene.animation == DeathAnimationConfig.Animation.CURATED_ALCHEMY_DISINTEGRATION;
   }

   public Object prepare(BipedEntityModel<?> model) {
      LaserGridSegmentation.LayerKey key = key(model);
      LaserGridSegmentation.FragmentLayer layer = this.layers
         .computeIfAbsent(model, ignored -> new HashMap<>())
         .computeIfAbsent(key, ignored -> new LaserGridSegmentation.FragmentLayer());
      if (!layer.initialized) {
         this.initialize(model, layer);
      }

      this.advanceToAge(layer, this.scene.age);
      return key;
   }

   public void tick(BipedEntityModel<?> model) {
      Map<LaserGridSegmentation.LayerKey, LaserGridSegmentation.FragmentLayer> modelLayers = this.layers.get(model);
      if (modelLayers != null) {
         for (LaserGridSegmentation.FragmentLayer layer : modelLayers.values()) {
            this.advanceToAge(layer, this.scene.age);
         }
      }
   }

   private static LaserGridSegmentation.LayerKey key(BipedEntityModel<?> model) {
      long visible = 0L;
      long skip = 0L;
      int index = 0;

      for (ModelPart part : model.getParts()) {
         if (index < 64) {
            if (part.visible) {
               visible |= 1L << index;
            }

            if (part.hidden) {
               skip |= 1L << index;
            }
         }

         index++;
      }

      return new LaserGridSegmentation.LayerKey(visible, skip);
   }

   private void advanceToAge(LaserGridSegmentation.FragmentLayer layer, float requestedAge) {
      float duration = AnimationTimeline.ticks(this.scene.animation);
      float age = Math.min(duration, Math.max(0.0F, requestedAge));
      if (age + 1.0E-4F < layer.lastSimulatedAge) {
         this.reset(layer);
      }

      int targetAge = (int)Math.floor(age + 1.0E-4F);

      while (layer.lastSimulatedAge < targetAge) {
         layer.lastSimulatedAge++;
         this.simulateTick(layer, Math.min(1.0F, layer.lastSimulatedAge / duration));
      }

      if (!this.scene.gui && age >= duration && !layer.voxels.isEmpty()) {
         int before = layer.voxels.size();
         layer.voxels.removeIf(voxel -> voxel.released && voxel.alpha <= 0.002F);
         this.fragmentCount = this.fragmentCount - (before - layer.voxels.size());
         if (layer.voxels.isEmpty()) {
            layer.beams.clear();
         }
      }
   }

   private void simulateTick(LaserGridSegmentation.FragmentLayer layer, float p) {
      if (this.scene.animation == DeathAnimationConfig.Animation.CURATED_ALCHEMY_DISINTEGRATION) {
         this.simulateAlchemyDissolve(layer, p);
      } else if (this.scene.animation == DeathAnimationConfig.Animation.BATCH01_BULLET_BILL) {
         this.simulateBillShred(layer, p);
      } else {
         this.beams(p, layer.beams);
         DeathScene.Root root = this.scene.root();
         layer.facing.rotationY((float)Math.toRadians(180.0F - this.scene.avatar.bodyYaw));
         layer.body.rotationY((float)Math.toRadians(root.yaw())).rotateX((float)Math.toRadians(root.pitch())).rotateZ((float)Math.toRadians(root.roll()));
         float inheritedX = (float)this.scene.inheritedVelocity.x * 0.22F;
         float inheritedY = (float)this.scene.inheritedVelocity.y * 0.22F;
         float inheritedZ = (float)this.scene.inheritedVelocity.z * 0.22F;

         for (LaserGridSegmentation.Voxel voxel : layer.voxels) {
            if (!this.stepReleased(voxel, p)) {
               worldPoint(voxel.modelCenter, root, layer.facing, layer.body, layer.center);
               boolean cut = false;

               for (LaserGridSegmentation.BeamBox beam : layer.beams) {
                  if (beam.contains(layer.center, voxel.radius, layer.scratch)) {
                     layer.direction.set(layer.center).sub(beam.center);
                     release(voxel, layer.center, layer.direction, p, inheritedX, inheritedY, inheritedZ, 0.018F);
                     cut = true;
                     break;
                  }
               }

               if (!cut && p >= 0.8F) {
                  float collapseAt = 0.8F + (1.0F - MathHelper.clamp(layer.center.y / 2.2F, 0.0F, 1.0F)) * 0.1F;
                  if (p >= collapseAt) {
                     layer.direction.set(layer.center.x, 0.0F, layer.center.z);
                     if (layer.direction.lengthSquared() < 1.0E-5F) {
                        layer.direction.set(voxel.worldNormal);
                     }

                     release(voxel, layer.center, layer.direction, p, inheritedX, inheritedY, inheritedZ, 0.006F);
                  }
               }
            }
         }
      }
   }

   private void simulateAlchemyDissolve(LaserGridSegmentation.FragmentLayer layer, float p) {
      DeathScene.Root root = this.scene.root();
      layer.facing.rotationY((float)Math.toRadians(180.0F - this.scene.avatar.bodyYaw));
      layer.body.rotationY((float)Math.toRadians(root.yaw())).rotateX((float)Math.toRadians(root.pitch())).rotateZ((float)Math.toRadians(root.roll()));

      for (LaserGridSegmentation.Voxel voxel : layer.voxels) {
         if (!this.stepReleased(voxel, p)) {
            worldPoint(voxel.modelCenter, root, layer.facing, layer.body, layer.center);
            float localY = layer.center.y - root.y();

            float start = switch (voxel.part) {
               case LEFT_LEG, RIGHT_LEG -> 0.34F + MathHelper.clamp(localY, 0.0F, 0.9F) * 0.075F;
               case LEFT_ARM, RIGHT_ARM -> 0.4F + MathHelper.clamp(localY - 0.72F, 0.0F, 0.7F) * 0.075F;
               case TORSO -> 0.55F + MathHelper.clamp(1.35F - localY, 0.0F, 0.58F) * 0.075F;
               case HEAD -> 0.72F + MathHelper.clamp((Math.abs(layer.center.x - root.x()) + Math.abs(layer.center.z - root.z())) * 0.04F, 0.0F, 0.035F);
            };
            if (!(p < start)) {
               layer.direction.set(root.x() - layer.center.x, root.y() + 1.02F - layer.center.y, root.z() - layer.center.z);
               release(voxel, layer.center, layer.direction, p, 0.0F, 0.0F, 0.0F, 0.007F);
            }
         }
      }
   }

   private void simulateBillShred(LaserGridSegmentation.FragmentLayer layer, float p) {
      float hit = BulletImpactRagdoll.impactPhase(this.scene);
      AnimationCatalog.Cue cue = AnimationCatalog.recipe(this.scene.animation).props().getFirst();
      DeathScene.Root bill = AnimationCatalog.sample(cue.keys(), p);
      layer.billCenter.set(bill.x(), bill.y(), bill.z());
      layer.sceneFacing.rotationY((float)Math.toRadians(this.scene.sceneFacing));
      layer.sceneFacing.transform(layer.billCenter);
      layer.travel.set(0.0F, 0.0F, -1.0F);
      layer.sceneFacing.transform(layer.travel).normalize();
      DeathScene.Root root = this.scene.root();
      layer.facing.rotationY((float)Math.toRadians(180.0F - this.scene.avatar.bodyYaw));
      layer.body.rotationY((float)Math.toRadians(root.yaw())).rotateX((float)Math.toRadians(root.pitch())).rotateZ((float)Math.toRadians(root.roll()));
      float inheritedX = (float)this.scene.inheritedVelocity.x * 0.12F;
      float inheritedY = (float)this.scene.inheritedVelocity.y * 0.12F;
      float inheritedZ = (float)this.scene.inheritedVelocity.z * 0.12F;

      for (LaserGridSegmentation.Voxel voxel : layer.voxels) {
         if (!this.stepReleased(voxel, p)) {
            worldPoint(voxel.modelCenter, root, layer.facing, layer.body, layer.center);
            layer.fromBill.set(layer.center).sub(layer.billCenter);
            float along = layer.fromBill.dot(layer.travel);
            layer.lateral.set(layer.fromBill).fma(-along, layer.travel);
            boolean swept = p >= hit && p < 0.34F && Math.abs(along) < 0.82F && layer.lateral.lengthSquared() < 0.5776F;
            float radial = Math.abs(layer.center.y - 1.1F) * 0.12F + Math.abs(layer.center.x) * 0.04F + Math.abs(layer.center.z) * 0.045F;
            float ripWave = hit + 0.045F + radial;
            if (swept || p >= ripWave && p < 0.46F) {
               layer.direction.set(layer.travel).mul(0.8F).add(layer.center.x * 0.55F, layer.center.y - 1.1F, layer.center.z * 0.4F);
               release(voxel, layer.center, layer.direction, p, inheritedX, inheritedY, inheritedZ, swept ? 0.052F : 0.039F);
            }
         }
      }
   }

   private boolean stepReleased(LaserGridSegmentation.Voxel voxel, float p) {
      if (!voxel.released) {
         return false;
      }

      if (voxel.alpha <= 0.002F) {
         return true;
      }

      if (this.scene.animation == DeathAnimationConfig.Animation.CURATED_ALCHEMY_DISINTEGRATION) {
         voxel.dx = voxel.dx + voxel.vx;
         voxel.dy = voxel.dy + voxel.vy;
         voxel.dz = voxel.dz + voxel.vz;
         voxel.vx *= 0.965F;
         voxel.vy *= 0.965F;
         voxel.vz *= 0.965F;
         float fadeStart = voxel.releasedAt + 0.045F;
         voxel.alpha = 1.0F - AnimationTimeline.smooth(AnimationTimeline.phase(p, fadeStart, fadeStart + 0.12F));
         return true;
      }

      voxel.vy -= 0.025F;
      voxel.dx = voxel.dx + voxel.vx;
      voxel.dy = voxel.dy + voxel.vy;
      voxel.dz = voxel.dz + voxel.vz;
      voxel.rx = voxel.rx + voxel.sx;
      voxel.ry = voxel.ry + voxel.sy;
      voxel.rz = voxel.rz + voxel.sz;
      float floor = (float)(this.scene.floorY - this.scene.y);
      if (voxel.base.y + voxel.dy < floor + 0.018F) {
         voxel.dy = floor + 0.018F - voxel.base.y;
         if (voxel.vy < -0.012F) {
            voxel.vy = -voxel.vy * 0.14F;
         } else {
            voxel.vy = 0.0F;
         }

         voxel.vx *= 0.82F;
         voxel.vz *= 0.82F;
         voxel.sx *= 0.86F;
         voxel.sz *= 0.86F;
      }

      float fadeStart = this.scene.animation == DeathAnimationConfig.Animation.BATCH01_BULLET_BILL
         ? Math.max(0.82F, voxel.releasedAt + 0.22F)
         : Math.max(0.82F, voxel.releasedAt + 0.025F);
      voxel.alpha = 1.0F - AnimationTimeline.smooth(AnimationTimeline.phase(p, fadeStart, 1.0F));
      return true;
   }

   private void reset(LaserGridSegmentation.FragmentLayer layer) {
      layer.lastSimulatedAge = 0;

      for (LaserGridSegmentation.Voxel voxel : layer.voxels) {
         voxel.released = false;
         voxel.releasedAt = 0.0F;
         voxel.base.zero();
         voxel.dx = voxel.dy = voxel.dz = voxel.vx = voxel.vy = voxel.vz = 0.0F;
         voxel.rx = voxel.ry = voxel.rz = voxel.sx = voxel.sy = voxel.sz = 0.0F;
         voxel.alpha = 1.0F;
      }
   }

   private static void release(
      LaserGridSegmentation.Voxel voxel, Vector3f center, Vector3f direction, float p, float inheritedX, float inheritedY, float inheritedZ, float spread
   ) {
      voxel.released = true;
      voxel.base.set(center);
      voxel.releasedAt = p;
      if (direction.lengthSquared() < 1.0E-5F) {
         direction.set(voxel.worldNormal);
      }

      if (direction.lengthSquared() < 1.0E-5F) {
         direction.set(0.0F, 0.0F, 1.0F);
      }

      direction.normalize();
      long h = voxel.hash;
      float jitter = ((float)(h & 255L) / 255.0F - 0.5F) * spread * 0.7F;
      voxel.vx = inheritedX + direction.x * spread + jitter;
      voxel.vy = inheritedY + direction.y * spread + spread * 0.65F;
      voxel.vz = inheritedZ + direction.z * spread - jitter;
      voxel.sx = ((float)(h >>> 8 & 255L) / 255.0F - 0.5F) * 0.035F;
      voxel.sy = ((float)(h >>> 16 & 255L) / 255.0F - 0.5F) * 0.035F;
      voxel.sz = ((float)(h >>> 24 & 255L) / 255.0F - 0.5F) * 0.035F;
   }

   private void initialize(BipedEntityModel<?> model, LaserGridSegmentation.FragmentLayer layer) {
      layer.initialized = true;

      for (DeathBodyRig.Part bodyPart : DeathBodyRig.Part.values()) {
         if (this.fragmentCount >= this.fragmentLimit()) {
            break;
         }

         ModelPart part = DeathBodyRig.part(model, bodyPart);
         if (part.visible) {
            ModelTransform saved = part.getTransform();

            try {
               part.setOrigin(0.0F, 0.0F, 0.0F);
               part.setAngles(0.0F, 0.0F, 0.0F);
               part.forEachCuboid(
                  new MatrixStack(),
                  (pose, path, cubeIndex, cube) -> {
                     if (this.fragmentCount < this.fragmentLimit() && visiblePath(part, path)) {
                        for (Quad polygon : cube.sides) {
                           if (polygon.vertices().length == 4) {
                              LaserGridSegmentation.V[] face = new LaserGridSegmentation.V[4];
                              int i = 0;

                              for (Vertex vertex : polygon.vertices()) {
                                 face[i++] = new LaserGridSegmentation.V(
                                    pose.getPositionMatrix().transformPosition(new Vector3f(vertex.worldX(), vertex.worldY(), vertex.worldZ())),
                                    vertex.u(),
                                    vertex.v()
                                 );
                              }

                              Vector3f normal = pose.getNormalMatrix().transform(new Vector3f(polygon.direction())).normalize();
                              float width = face[0].point.distance(face[1].point);
                              float height = face[0].point.distance(face[3].point);
                              int across = MathHelper.clamp(Math.round(width * 16.0F), 1, 16);
                              int down = MathHelper.clamp(Math.round(height * 16.0F), 1, 16);

                              for (int u = 0; u < across && this.fragmentCount < this.fragmentLimit(); u++) {
                                 for (int v = 0; v < down && this.fragmentCount < this.fragmentLimit(); v++) {
                                    LaserGridSegmentation.V[] tile = new LaserGridSegmentation.V[]{
                                       bilerp(face, (float)u / across, (float)v / down),
                                       bilerp(face, (u + 1.0F) / across, (float)v / down),
                                       bilerp(face, (u + 1.0F) / across, (v + 1.0F) / down),
                                       bilerp(face, (float)u / across, (v + 1.0F) / down)
                                    };
                                    Vector3f center = new Vector3f();

                                    for (LaserGridSegmentation.V vertex : tile) {
                                       center.add(vertex.point);
                                    }

                                    center.mul(0.25F);
                                    Vector3f initial = new Vector3f(saved.x() / 16.0F, saved.y() / 16.0F, saved.z() / 16.0F);
                                    Vector3f modelCenter = new Vector3f(center).add(initial);
                                    long hash = bodyPart.ordinal() * -7046029254386353131L
                                       ^ cubeIndex * 7146057691288625177L
                                       ^ (long)across * u * 2232777461L
                                       ^ (long)down * v * 668265261L;
                                    layer.voxels.add(new LaserGridSegmentation.Voxel(bodyPart, tile, normal, initial, modelCenter, hash));
                                    this.fragmentCount++;
                                 }
                              }
                           }
                        }
                     }
                  }
               );
            } finally {
               part.setTransform(saved);
            }
         }
      }
   }

   private int fragmentLimit() {
      return this.scene.animation == DeathAnimationConfig.Animation.CURATED_ALCHEMY_DISINTEGRATION ? 4096 : 16384;
   }

   private static boolean visiblePath(ModelPart root, String path) {
      if (!root.visible) {
         return false;
      }

      ModelPart current = root;
      if (!path.isEmpty()) {
         for (String childName : path.split("/")) {
            if (!childName.isEmpty()) {
               if (!current.hasChild(childName)) {
                  return false;
               }

               current = current.getChild(childName);
               if (!current.visible) {
                  return false;
               }
            }
         }
      }

      return !current.hidden;
   }

   private void beams(float p, List<LaserGridSegmentation.BeamBox> result) {
      result.clear();
      List<AnimationCatalog.Cue> cues = AnimationCatalog.recipe(this.scene.animation).props();
      Quaternionf aligned = new Quaternionf().rotationY((float)Math.toRadians(this.scene.sceneFacing));

      for (int i = 8; i < cues.size(); i++) {
         AnimationCatalog.Cue cue = AnimationCatalog.recipe(this.scene.animation).props().get(i);
         if (!(p < cue.start()) && !(p >= cue.end())) {
            DeathScene.Root key = AnimationCatalog.sample(cue.keys(), p);
            Vector3f center = aligned.transform(new Vector3f(key.x(), key.y(), key.z()));
            Quaternionf rotation = new Quaternionf()
               .rotationZYX((float)Math.toRadians(key.roll()), (float)Math.toRadians(key.yaw()), (float)Math.toRadians(key.pitch()));
            rotation.premul(aligned);
            Vector3f half = new Vector3f(key.sx(), key.sy(), key.sz()).mul(0.5F);
            result.add(new LaserGridSegmentation.BeamBox(center, rotation.conjugate(new Quaternionf()), half));
         }
      }
   }

   private static void worldPoint(Vector3f model, DeathScene.Root root, Quaternionf facing, Quaternionf body, Vector3f out) {
      out.set(-model.x, 1.501F - model.y, model.z).mul(0.9375F);
      facing.transform(out);
      out.mul(root.sx(), root.sy(), root.sz());
      out.y = out.y - root.pivotY();
      body.transform(out);
      out.y = out.y + root.pivotY();
      out.add(root.x(), root.y(), root.z());
   }

   private static void renderOffset(
      LaserGridSegmentation.Voxel voxel, DeathScene.Root root, Quaternionf inverseBody, Quaternionf inverseFacing, Vector3f local, Vector3f out
   ) {
      local.set(voxel.dx, voxel.dy, voxel.dz);
      inverseBody.transform(local);
      local.div(root.sx(), root.sy(), root.sz());
      inverseFacing.transform(local);
      out.set(-local.x / 0.9375F, -local.y / 0.9375F, local.z / 0.9375F);
   }

   public void render(BipedEntityModel<?> model, MatrixStack poses, VertexConsumer vertices, int light, int overlay, int color, Object cacheKey) {
      if (this.scene.gui || EffectParticles.near(this.scene.x, this.scene.y + 1.0, this.scene.z)) {
         long renderStarted = METRICS ? System.nanoTime() : 0L;
         LaserGridSegmentation.LayerKey key = cacheKey instanceof LaserGridSegmentation.LayerKey layerKey ? layerKey : key(model);
         LaserGridSegmentation.FragmentLayer layer = this.layers
            .computeIfAbsent(model, ignored -> new HashMap<>())
            .computeIfAbsent(key, ignored -> new LaserGridSegmentation.FragmentLayer());
         if (!layer.initialized) {
            this.initialize(model, layer);
         }

         if (this.scene.gui) {
            this.advanceToAge(layer, this.scene.age);
         }

         float p = this.scene.progress();
         DeathScene.Root root = this.scene.root();
         layer.inverseBody
            .rotationY((float)Math.toRadians(root.yaw()))
            .rotateX((float)Math.toRadians(root.pitch()))
            .rotateZ((float)Math.toRadians(root.roll()))
            .conjugate();
         layer.inverseFacing.rotationY((float)Math.toRadians(180.0F - this.scene.avatar.bodyYaw)).conjugate();

         for (LaserGridSegmentation.Voxel voxel : layer.voxels) {
            if (!voxel.released || !(voxel.alpha <= 0.002F)) {
               if (!voxel.released) {
                  emit(voxel.renderFace, voxel.normal, poses, vertices, light, overlay, color);
               } else {
                  renderOffset(voxel, root, layer.inverseBody, layer.inverseFacing, layer.offsetScratch, layer.offset);
                  layer.center.set(voxel.modelCenter).add(layer.offset);
                  layer.spin.identity().rotationZYX(voxel.rz, voxel.ry, voxel.rx);
                  float shrink = this.scene.animation == DeathAnimationConfig.Animation.CURATED_ALCHEMY_DISINTEGRATION
                     ? 1.0F - 0.58F * AnimationTimeline.smooth(AnimationTimeline.phase(p, voxel.releasedAt, voxel.releasedAt + 0.16F))
                     : 1.0F;

                  for (int i = 0; i < 4; i++) {
                     LaserGridSegmentation.V moved = voxel.moved[i];
                     moved.point.set(voxel.face[i].point).add(voxel.partOrigin);
                     if (voxel.released) {
                        moved.point.sub(voxel.modelCenter).mul(shrink);
                     }

                     layer.spin.transform(moved.point).add(layer.center);
                  }

                  emitCube(voxel, layer, poses, vertices, light, overlay, color, voxel.alpha);
               }
            }
         }

         if (METRICS) {
            this.renderNanos = this.renderNanos + (System.nanoTime() - renderStarted);
            this.renderCalls++;
            this.renderedFragments = this.renderedFragments + layer.voxels.size();
         }
      }
   }

   private static void emitCube(
      LaserGridSegmentation.Voxel voxel,
      LaserGridSegmentation.FragmentLayer layer,
      MatrixStack poses,
      VertexConsumer vertices,
      int light,
      int overlay,
      int color,
      float alpha
   ) {
      layer.backOffset.set(voxel.normal).mul(-0.055F);

      for (int i = 0; i < 4; i++) {
         voxel.back[i].point.set(voxel.moved[i].point).add(layer.backOffset);
      }

      int tinted = Math.round((color >>> 24) * alpha) << 24 | color & 16777215;
      emit(voxel.moved, voxel.normal, poses, vertices, light, overlay, tinted);
      emit(voxel.backFace, layer.inverseNormal.set(voxel.normal).negate(), poses, vertices, light, overlay, tinted);

      for (int i = 0; i < 4; i++) {
         int next = i + 1 & 3;
         LaserGridSegmentation.V a = voxel.moved[i];
         LaserGridSegmentation.V b = voxel.moved[next];
         LaserGridSegmentation.V c = voxel.back[next];
         LaserGridSegmentation.V d = voxel.back[i];
         layer.edgeA.set(b.point).sub(a.point);
         layer.edgeB.set(d.point).sub(a.point);
         voxel.sideNormals[i].set(layer.edgeA).cross(layer.edgeB).normalize();
         emit(voxel.sideFaces[i], voxel.sideNormals[i], poses, vertices, light, overlay, tinted);
      }
   }

   private static void emit(LaserGridSegmentation.V[] face, Vector3f normal, MatrixStack poses, VertexConsumer vertices, int light, int overlay, int color) {
      Entry pose = poses.peek();

      for (LaserGridSegmentation.V vertex : face) {
         vertices.vertex(pose, vertex.point).color(color).texture(vertex.u, vertex.v).overlay(overlay).light(light).normal(pose, normal.x, normal.y, normal.z);
      }
   }

   private static LaserGridSegmentation.V bilerp(LaserGridSegmentation.V[] face, float u, float v) {
      Vector3f a = new Vector3f(face[0].point).lerp(face[1].point, u);
      Vector3f b = new Vector3f(face[3].point).lerp(face[2].point, u);
      float tu = (face[0].u + (face[1].u - face[0].u) * u) * (1.0F - v) + (face[3].u + (face[2].u - face[3].u) * u) * v;
      float tv = (face[0].v + (face[1].v - face[0].v) * u) * (1.0F - v) + (face[3].v + (face[2].v - face[3].v) * u) * v;
      return new LaserGridSegmentation.V(a.lerp(b, v), tu, tv);
   }

   public int fragmentCount() {
      return this.fragmentCount;
   }

   public long renderNanos() {
      return this.renderNanos;
   }

   public long renderedFragments() {
      return this.renderedFragments;
   }

   public int renderCalls() {
      return this.renderCalls;
   }

   public int renderPasses() {
      return this.layers.values().stream().mapToInt(Map::size).sum();
   }

   public void discard() {
      this.layers.clear();
      this.fragmentCount = 0;
   }

   @Environment(EnvType.CLIENT)
   private record BeamBox(Vector3f center, Quaternionf inverseRotation, Vector3f halfSize) {
      boolean contains(Vector3f point, float radius, Vector3f scratch) {
         this.inverseRotation.transform(scratch.set(point).sub(this.center));
         return Math.abs(scratch.x) <= this.halfSize.x + radius
            && Math.abs(scratch.y) <= this.halfSize.y + radius
            && Math.abs(scratch.z) <= this.halfSize.z + radius;
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class FragmentLayer {
      final List<LaserGridSegmentation.Voxel> voxels = new ArrayList<>(512);
      final List<LaserGridSegmentation.BeamBox> beams = new ArrayList<>(16);
      final Vector3f center = new Vector3f();
      final Vector3f scratch = new Vector3f();
      final Vector3f direction = new Vector3f();
      final Vector3f billCenter = new Vector3f();
      final Vector3f travel = new Vector3f();
      final Vector3f fromBill = new Vector3f();
      final Vector3f lateral = new Vector3f();
      final Vector3f offset = new Vector3f();
      final Vector3f offsetScratch = new Vector3f();
      final Vector3f backOffset = new Vector3f();
      final Vector3f inverseNormal = new Vector3f();
      final Vector3f edgeA = new Vector3f();
      final Vector3f edgeB = new Vector3f();
      final Quaternionf facing = new Quaternionf();
      final Quaternionf body = new Quaternionf();
      final Quaternionf sceneFacing = new Quaternionf();
      final Quaternionf inverseBody = new Quaternionf();
      final Quaternionf inverseFacing = new Quaternionf();
      final Quaternionf spin = new Quaternionf();
      boolean initialized;
      int lastSimulatedAge;
   }

   @Environment(EnvType.CLIENT)
   private record LayerKey(long visibleParts, long skippedParts) {
   }

   @Environment(EnvType.CLIENT)
   private static final class V {
      final Vector3f point;
      final float u;
      final float v;

      V(Vector3f point, float u, float v) {
         this.point = point;
         this.u = u;
         this.v = v;
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class Voxel {
      final DeathBodyRig.Part part;
      final LaserGridSegmentation.V[] face;
      final LaserGridSegmentation.V[] renderFace;
      final LaserGridSegmentation.V[] moved;
      final LaserGridSegmentation.V[] back;
      final LaserGridSegmentation.V[] backFace;
      final LaserGridSegmentation.V[][] sideFaces;
      final Vector3f normal;
      final Vector3f partOrigin;
      final Vector3f modelCenter;
      final Vector3f worldNormal;
      final Vector3f base;
      final Vector3f[] sideNormals;
      final long hash;
      final float radius;
      boolean released;
      float releasedAt;
      float dx;
      float dy;
      float dz;
      float vx;
      float vy;
      float vz;
      float rx;
      float ry;
      float rz;
      float sx;
      float sy;
      float sz;
      float alpha = 1.0F;

      Voxel(DeathBodyRig.Part part, LaserGridSegmentation.V[] face, Vector3f normal, Vector3f origin, Vector3f center, long hash) {
         this.part = part;
         this.face = face;
         this.normal = new Vector3f(normal);
         this.partOrigin = origin;
         this.modelCenter = center;
         this.hash = hash;
         this.renderFace = new LaserGridSegmentation.V[4];
         this.moved = new LaserGridSegmentation.V[4];
         this.back = new LaserGridSegmentation.V[4];
         this.backFace = new LaserGridSegmentation.V[4];
         this.sideFaces = new LaserGridSegmentation.V[4][4];
         this.sideNormals = new Vector3f[4];

         for (int i = 0; i < 4; i++) {
            this.renderFace[i] = new LaserGridSegmentation.V(new Vector3f(face[i].point).add(origin), face[i].u, face[i].v);
            this.moved[i] = new LaserGridSegmentation.V(new Vector3f(), face[i].u, face[i].v);
            this.back[i] = new LaserGridSegmentation.V(new Vector3f(), face[i].u, face[i].v);
            this.sideNormals[i] = new Vector3f();
         }

         for (int i = 0; i < 4; i++) {
            this.backFace[i] = this.back[3 - i];
            int next = i + 1 & 3;
            this.sideFaces[i][0] = this.moved[i];
            this.sideFaces[i][1] = this.moved[next];
            this.sideFaces[i][2] = this.back[next];
            this.sideFaces[i][3] = this.back[i];
         }

         this.radius = Math.max(0.018F, face[0].point.distance(face[2].point) * 0.5F);
         this.worldNormal = new Vector3f(-normal.x, -normal.y, normal.z).mul(0.9375F);
         this.base = new Vector3f();
      }
   }
}
