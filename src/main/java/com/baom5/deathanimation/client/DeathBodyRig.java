package com.baom5.deathanimation.client;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class DeathBodyRig {
   private static final ThreadLocal<DeathBodyRig.Pose> ACTIVE = new ThreadLocal<>();
   private static final Map<ModelPart, DeathBodyRig.Geometry> GEOMETRY = new WeakHashMap<>();

   private DeathBodyRig() {
   }

   public static void clear() {
      DeathBodyRig.Pose pose = ACTIVE.get();
      if (pose != null) {
         pose.restoreLayers();
      }

      ACTIVE.remove();
      Kamehameha.clear();
   }

   public static DeathBodyRig.Pose activeFor(Object model) {
      DeathBodyRig.Pose pose = ACTIVE.get();
      return pose != null && pose.model == model ? pose : null;
   }

   public static void prepareForRender(Model model, Object renderState) {
      clear();
      if (model instanceof BipedEntityModel && renderState instanceof LivingEntityRenderState state) {
         PlayerActors.Binding actor = PlayerActors.forState(state);
         if (actor != null) {
            DeathBodyRig.Pose pose = setPose(model, PlayerActors.rig(actor), actor.scene());
            if ("nested-player".equals(actor.cue().actor())) {
               pose.renderKey = actor;
            }

            if (state instanceof PlayerEntityRenderState avatar) {
               pose.skinState = avatar;
            }

            Kamehameha.prepare(model, actor.scene(), actor);
         } else {
            DeathScene scene = DeathEffectController.sceneFor(state);
            if (scene != null) {
               DeathBodyRig.Pose pose = setPose(model, scene.rig((BipedEntityModel<?>)model), scene);
               if (state instanceof PlayerEntityRenderState avatar) {
                  pose.skinState = avatar;
               }

               Kamehameha.prepare(model, scene, null);
            }
         }
      }
   }

   public static void beginGoryExplosion(Model model, float progress) {
      DeathBodyRig.Motion[] parts = new DeathBodyRig.Motion[DeathBodyRig.Part.values().length];

      for (DeathBodyRig.Part part : DeathBodyRig.Part.values()) {
         parts[part.ordinal()] = new DeathBodyRig.Motion(false, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
      }

      goryPart(parts, DeathBodyRig.Part.HEAD, progress, 0.08F, 0.35F, -1.0F, 0.35F, -0.22F, 0.38F);
      goryPart(parts, DeathBodyRig.Part.TORSO, progress, 0.08F, -0.15F, 0.3F, 1.57F, 0.35F, -0.1F);
      goryPart(parts, DeathBodyRig.Part.LEFT_ARM, progress, 0.08F, -1.2F, 0.6F, -0.3F, -0.3F, -1.57F);
      goryPart(parts, DeathBodyRig.Part.RIGHT_ARM, progress, 0.08F, 1.2F, -0.55F, 0.3F, 0.25F, 1.57F);
      goryPart(parts, DeathBodyRig.Part.LEFT_LEG, progress, 0.08F, -0.7F, -0.95F, 1.57F, -0.42F, -0.15F);
      goryPart(parts, DeathBodyRig.Part.RIGHT_LEG, progress, 0.08F, 0.7F, 1.0F, -1.57F, 0.4F, 0.15F);
      setPose(model, parts);
   }

   private static void goryPart(
      DeathBodyRig.Motion[] parts, DeathBodyRig.Part part, float progress, float start, float x, float z, float xRot, float yRot, float zRot
   ) {
      float flight = MathHelper.clamp((progress - start) / 0.58F, 0.0F, 1.0F);
      if (!(flight <= 0.0F)) {
         float gravityArc = -3.5F * flight * (1.0F - flight);
         float spinFade = 1.0F - flight;
         parts[part.ordinal()] = new DeathBodyRig.Motion(
            true,
            x * flight,
            gravityArc,
            z * flight,
            xRot * flight + spinFade * 1.35F * (float)Math.sin(flight * 3.141592653589793 * 3.0),
            yRot * flight + spinFade * 1.15F * (float)Math.sin(flight * 3.141592653589793 * 2.0),
            zRot * flight + spinFade * 1.25F * (float)Math.sin(flight * 3.141592653589793 * 2.5),
            flight * flight
         );
      }
   }

   public static void beginBurn(Model model, float progress) {
      DeathBodyRig.Motion[] parts = new DeathBodyRig.Motion[DeathBodyRig.Part.values().length];

      for (DeathBodyRig.Part part : DeathBodyRig.Part.values()) {
         parts[part.ordinal()] = new DeathBodyRig.Motion(false, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
      }

      burnPart(parts, DeathBodyRig.Part.LEFT_ARM, progress, 0.3F, 0.8F, -0.25F, 0.1F, 0.1F, -1.57F);
      burnPart(parts, DeathBodyRig.Part.RIGHT_ARM, progress, 0.39F, -0.8F, 0.25F, -0.1F, -0.1F, 1.57F);
      burnPart(parts, DeathBodyRig.Part.LEFT_LEG, progress, 0.48F, 0.55F, 0.85F, 1.57F, 0.15F, -0.1F);
      burnPart(parts, DeathBodyRig.Part.RIGHT_LEG, progress, 0.57F, -0.55F, -0.85F, -1.57F, -0.15F, 0.1F);
      burnPart(parts, DeathBodyRig.Part.HEAD, progress, 0.66F, -0.15F, -1.3F, 0.2F, 0.2F, 0.1F);
      burnPart(parts, DeathBodyRig.Part.TORSO, progress, 0.75F, 0.0F, 0.08F, 1.57F, -0.15F, 0.05F);
      setPose(model, parts);
   }

   private static void burnPart(
      DeathBodyRig.Motion[] parts, DeathBodyRig.Part part, float progress, float start, float x, float z, float xRot, float yRot, float zRot
   ) {
      float fall = MathHelper.clamp((progress - start) / 0.16F, 0.0F, 1.0F);
      if (!(fall <= 0.0F)) {
         float eased = fall * fall * (3.0F - 2.0F * fall);
         parts[part.ordinal()] = new DeathBodyRig.Motion(true, x * eased, 0.0F, z * eased, xRot * eased, yRot * eased, zRot * eased, fall * fall);
      }
   }

   public static void setPose(Model model, DeathBodyRig.Motion[] parts) {
      setPose(model, parts, null);
   }

   public static DeathBodyRig.Pose setPose(Model model, DeathBodyRig.Motion[] parts, DeathScene scene) {
      if (parts.length != DeathBodyRig.Part.values().length) {
         throw new IllegalArgumentException("A death rig pose needs six body parts");
      }

      DeathBodyRig.Pose pose = new DeathBodyRig.Pose(model, (DeathBodyRig.Motion[])parts.clone(), scene);
      ACTIVE.set(pose);
      return pose;
   }

   public static ModelPart part(BipedEntityModel<?> model, DeathBodyRig.Part part) {
      return switch (part) {
         case HEAD -> model.head;
         case TORSO -> model.body;
         case LEFT_ARM -> model.leftArm;
         case RIGHT_ARM -> model.rightArm;
         case LEFT_LEG -> model.leftLeg;
         case RIGHT_LEG -> model.rightLeg;
      };
   }

   public static DeathBodyRig.Geometry geometry(ModelPart part) {
      return GEOMETRY.computeIfAbsent(part, key -> {
         IdentityHashMap<ModelPart, ModelTransform> saved = new IdentityHashMap<>();
         IdentityHashMap<ModelPart, Boolean> visibility = new IdentityHashMap<>();
         ArrayList<Vector3f> points = new ArrayList<>();

         try {
            key.traverse().forEach(bone -> {
               saved.put(bone, bone.getTransform());
               visibility.put(bone, bone.visible);
               bone.setTransform(bone.getDefaultTransform());
               bone.visible = true;
            });
            key.setOrigin(0.0F, 0.0F, 0.0F);
            key.setAngles(0.0F, 0.0F, 0.0F);
            key.collectVertices(new MatrixStack(), pointx -> points.add(new Vector3f(pointx)));
         } finally {
            saved.forEach(ModelPart::setTransform);
            visibility.forEach((bone, shown) -> bone.visible = shown);
         }

         Vector3f low = new Vector3f(1.0F / 0.0F);
         Vector3f high = new Vector3f(-1.0F / 0.0F);

         for (Vector3f point : points) {
            low.min(point);
            high.max(point);
         }

         Vector3f center = points.isEmpty() ? new Vector3f() : low.add(high).mul(0.5F);

         for (Vector3f point : points) {
            point.sub(center);
         }

         return new DeathBodyRig.Geometry(center, points.toArray(Vector3f[]::new));
      });
   }

   @Environment(EnvType.CLIENT)
   public record Geometry(Vector3f center, Vector3f[] vertices) {
      public Vector3f center() {
         return new Vector3f(this.center);
      }

      public float bottom(Quaternionf rotation) {
         return this.bottom(rotation, 1.0F, 1.0F, 1.0F);
      }

      public float bottom(Quaternionf rotation, float scaleX, float scaleY, float scaleZ) {
         float bottom = -1.0F / 0.0F;
         Vector3f rotated = new Vector3f();

         for (Vector3f vertex : this.vertices) {
            rotated.set(vertex.x * scaleX, vertex.y * scaleY, vertex.z * scaleZ);
            rotation.transform(rotated, rotated);
            bottom = Math.max(bottom, rotated.y);
         }

         return Float.isFinite(bottom) ? bottom : 0.0F;
      }
   }

   @Environment(EnvType.CLIENT)
   public record Motion(
      boolean detached, float x, float y, float z, float xRot, float yRot, float zRot, float groundBlend, float alpha, float scaleX, float scaleY, float scaleZ
   ) {
      public Motion(boolean detached, float x, float y, float z, float xRot, float yRot, float zRot, float groundBlend, float alpha) {
         this(detached, x, y, z, xRot, yRot, zRot, groundBlend, alpha, 1.0F, 1.0F, 1.0F);
      }

      public Motion(boolean detached, float x, float y, float z, float xRot, float yRot, float zRot, float groundBlend) {
         this(detached, x, y, z, xRot, yRot, zRot, groundBlend, 1.0F, 1.0F, 1.0F, 1.0F);
      }

      public Motion(boolean detached, float x, float y, float z, float xRot, float yRot, float zRot) {
         this(detached, x, y, z, xRot, yRot, zRot, -1.0F);
      }

      public Motion(float x, float y, float z, float xRot, float yRot, float zRot) {
         this(true, x, y, z, xRot, yRot, zRot);
      }
   }

   @Environment(EnvType.CLIENT)
   public enum Part {
      HEAD,
      TORSO,
      LEFT_ARM,
      RIGHT_ARM,
      LEFT_LEG,
      RIGHT_LEG;
   }

   @Environment(EnvType.CLIENT)
   public record PartState(float x, float y, float z, float xRot, float yRot, float zRot, boolean visible) {
   }

   @Environment(EnvType.CLIENT)
   public static final class Pose {
      private final Model model;
      private final DeathBodyRig.Motion[] parts;
      private final DeathScene scene;
      private Object renderKey;
      private PlayerEntityRenderState skinState;
      private PlayerSkinLayers.Snapshot skinLayers;
      private final DeathBodyRig.PartState[] originals = new DeathBodyRig.PartState[DeathBodyRig.Part.values().length];

      private Pose(Model model, DeathBodyRig.Motion[] parts, DeathScene scene) {
         this.model = model;
         this.parts = parts;
         this.scene = scene;
      }

      public DeathScene scene() {
         return this.scene;
      }

      public Object renderKey() {
         return this.renderKey;
      }

      public void prepareLayers(BipedEntityModel<?> model) {
         if (this.skinState != null && model instanceof PlayerEntityModel player) {
            this.skinLayers = PlayerSkinLayers.prepare(player, this.skinState);
         }

         if (this.scene != null && this.scene.laserGrid() != null) {
            this.renderKey = this.scene.laserGrid().prepare(model);
         }
      }

      public void restoreLayers() {
         if (this.skinLayers != null) {
            this.skinLayers.restore();
            this.skinLayers = null;
         }
      }

      public DeathBodyRig.Motion motion(DeathBodyRig.Part part) {
         return this.parts[part.ordinal()];
      }

      public DeathBodyRig.PartState original(DeathBodyRig.Part part) {
         return this.originals[part.ordinal()];
      }

      public void saveOriginal(DeathBodyRig.Part part, ModelPart modelPart) {
         this.originals[part.ordinal()] = new DeathBodyRig.PartState(
            modelPart.originX, modelPart.originY, modelPart.originZ, modelPart.pitch, modelPart.yaw, modelPart.roll, modelPart.visible
         );
      }
   }
}
