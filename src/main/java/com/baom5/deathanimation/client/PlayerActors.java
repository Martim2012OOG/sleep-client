package com.baom5.deathanimation.client;

import com.google.gson.Gson;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.AssetInfo.TextureAssetInfo;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class PlayerActors {
   private static final Map<Integer, PlayerActors.Binding> ACTIVE = new HashMap<>();
   private static final PlayerActors.Skins SKINS = loadSkins();

   private PlayerActors() {
   }

   public static String previewSkin() {
      return SKINS.defaultSkin();
   }

   public static SkinTextures skin(String texture) {
      Identifier location = Identifier.of(texture);
      return SkinTextures.create(new TextureAssetInfo(location, location), null, null, PlayerSkinType.SLIM);
   }

   private static PlayerActors.Skins loadSkins() {
      try (InputStream stream = PlayerActors.class.getResourceAsStream("/assets/deathanimation/animations/actor-skins.json")) {
         if (stream == null) {
            throw new IllegalStateException("Missing actor skin assignments");
         } else {
            return (PlayerActors.Skins)new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), PlayerActors.Skins.class);
         }
      } catch (Exception error) {
         throw new IllegalStateException("Could not load reference-actor skins", error);
      }
   }

   public static void spawn(DeathScene scene, AnimationCatalog.Cue cue, Consumer<Entity> add) {
      DeathAvatar avatar = new DeathAvatar(scene.level, scene.avatar, 0);
      if (!"pool-head".equals(cue.actor()) && !"photocopy".equals(cue.actor()) && !"shred-strip".equals(cue.actor()) && !"nested-player".equals(cue.actor())) {
         String texture = SKINS.skins().getOrDefault(cue.actor(), SKINS.defaultSkin());
         avatar.useReferenceSkin(texture == null ? avatar.getSkin() : skin(texture));
      } else {
         avatar.copyModelParts(scene.avatar);
         if ("pool-head".equals(cue.actor())) {
            avatar.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
         }
      }

      avatar.setYaw(180.0F);
      avatar.bodyYaw = avatar.lastBodyYaw = 180.0F;
      avatar.headYaw = avatar.lastHeadYaw = 180.0F;
      DeathScene.Root first = AnimationCatalog.sample(cue.keys(), scene.progress());
      update(
         scene,
         avatar,
         cue,
         new DeathScene.Root(
            first.x(),
            first.y(),
            first.z(),
            first.pitch(),
            first.yaw(),
            first.roll(),
            first.sx(),
            first.sy(),
            first.sz(),
            cue.actorPivotY() == null ? 0.0F : cue.actorPivotY()
         )
      );
      add.accept(avatar);
      ACTIVE.put(avatar.getId(), new PlayerActors.Binding(scene, cue, avatar));
   }

   static void spawnNestedPlayer(DeathScene scene, List<AnimationCatalog.Key> keys, float start, float end, Consumer<Entity> add) {
      AnimationCatalog.Cue cue = new AnimationCatalog.Cue(
         null,
         null,
         null,
         null,
         false,
         keys,
         start,
         end,
         null,
         null,
         null,
         null,
         null,
         false,
         false,
         false,
         null,
         null,
         null,
         null,
         false,
         "nested-player",
         null,
         1.0F,
         null,
         null,
         false,
         null,
         null
      );
      spawn(scene, cue, add);
   }

   public static PlayerActors.Binding forState(Object state) {
      return state instanceof PlayerEntityRenderState avatar ? ACTIVE.get(avatar.id) : null;
   }

   public static PlayerActors.Binding forEntity(Entity entity) {
      return ACTIVE.get(entity.getId());
   }

   public static PlayerActors.HandPose handPose(DeathScene scene) {
      PlayerActors.Binding binding = ACTIVE.values()
         .stream()
         .filter(candidate -> candidate.scene() == scene)
         .findFirst()
         .orElseThrow(() -> new IllegalStateException("Missing reference actor for attached effect"));
      if (Kamehameha.supports(scene)) {
         return Kamehameha.hands(binding);
      }

      AnimationCatalog.Cue cue = binding.cue();
      float p = scene.progress();
      DeathScene.Root root = AnimationCatalog.sample(cue.keys(), p);
      DeathBodyRig.Motion[] rig = rig(binding);
      Quaternionf rootRotation = new Quaternionf()
         .rotationY((float)Math.toRadians(root.yaw() + scene.sceneFacing))
         .rotateX((float)Math.toRadians(root.pitch()))
         .rotateZ((float)Math.toRadians(root.roll()));
      Vector3f[] palms = new Vector3f[2];
      Vector3f[] vectors = new Vector3f[2];
      DeathBodyRig.Part[] parts = new DeathBodyRig.Part[]{DeathBodyRig.Part.LEFT_ARM, DeathBodyRig.Part.RIGHT_ARM};

      for (int i = 0; i < 2; i++) {
         DeathBodyRig.Part part = parts[i];
         DeathBodyRig.Motion motion = rig[part.ordinal()];
         float shoulderX = part == DeathBodyRig.Part.LEFT_ARM ? 0.3125F : -0.3125F;
         Quaternionf armRotation = new Quaternionf().rotationZYX(motion.zRot(), motion.yRot(), motion.xRot());
         Vector3f palm = new Vector3f(0.0F, 0.75F, 0.0F);
         armRotation.transform(palm);
         Vector3f vector = new Vector3f(palm);
         rootRotation.transform(vector);
         vectors[i] = vector;
         palm.add(shoulderX + motion.x(), 0.125F + motion.y(), motion.z());
         palm.mul(root.sx(), root.sy(), root.sz());
         palm.y = palm.y - root.pivotY();
         rootRotation.transform(palm);
         palm.y = palm.y + root.pivotY();
         palm.add((float)binding.avatar().getX(), (float)binding.avatar().getY(), (float)binding.avatar().getZ());
         palm.y += 0.72F;
         palms[i] = palm;
      }

      Vector3f direction = vectors[0].add(vectors[1]).normalize();
      if (direction.lengthSquared() < 1.0E-8F) {
         direction.set(1.0F, 0.0F, 0.0F);
      }

      return new PlayerActors.HandPose(new Vec3d((palms[0].x + palms[1].x) * 0.5, (palms[0].y + palms[1].y) * 0.5, (palms[0].z + palms[1].z) * 0.5), direction);
   }

   public static PlayerActors.WeaponPose weaponPose(DeathScene scene) {
      return weaponPose(scene, true);
   }

   public static Vec3d actorTetherPoint(DeathScene scene, String actor, float side) {
      PlayerActors.Binding binding = ACTIVE.values()
         .stream()
         .filter(candidate -> candidate.scene() == scene && actor.equals(candidate.cue().actor()))
         .findFirst()
         .orElseThrow(() -> new IllegalStateException("Missing reference actor for tether: " + actor));
      AnimationCatalog.Cue cue = binding.cue();
      DeathScene.Root root = AnimationCatalog.sample(cue.keys(), scene.progress());
      float pivot = cue.actorPivotY() == null ? 0.0F : cue.actorPivotY();
      Vector3f point = new Vector3f(side * 0.28F, 0.94F, 0.0F).mul(root.sx(), root.sy(), root.sz());
      point.y -= pivot;
      Quaternionf rotation = new Quaternionf()
         .rotationY((float)Math.toRadians(root.yaw() + scene.sceneFacing))
         .rotateX((float)Math.toRadians(root.pitch()))
         .rotateZ((float)Math.toRadians(root.roll()));
      rotation.transform(point);
      point.y += pivot;
      point.add((float)binding.avatar().getX(), (float)binding.avatar().getY(), (float)binding.avatar().getZ());
      return new Vec3d(point.x, point.y, point.z);
   }

   public static PlayerActors.WeaponPose weaponPose(DeathScene scene, boolean rightHand) {
      PlayerActors.Binding binding = ACTIVE.values()
         .stream()
         .filter(candidate -> candidate.scene() == scene)
         .findFirst()
         .orElseThrow(() -> new IllegalStateException("Missing reference actor for held weapon"));
      AnimationCatalog.Cue cue = binding.cue();
      float p = scene.progress();
      DeathScene.Root root = AnimationCatalog.sample(cue.keys(), p);
      DeathBodyRig.Part part = rightHand ? DeathBodyRig.Part.RIGHT_ARM : DeathBodyRig.Part.LEFT_ARM;
      DeathBodyRig.Motion[] rig = rig(binding);
      DeathBodyRig.Motion motion = rig[part.ordinal()];
      Quaternionf rootRotation = new Quaternionf()
         .rotationY((float)Math.toRadians(root.yaw() + scene.sceneFacing))
         .rotateX((float)Math.toRadians(root.pitch()))
         .rotateZ((float)Math.toRadians(root.roll()));
      Quaternionf armRotation = new Quaternionf().rotationZYX(motion.zRot(), motion.yRot(), motion.xRot());
      Vector3f direction = armRotation.transform(new Vector3f(0.0F, -1.0F, 0.0F));
      rootRotation.transform(direction).normalize();
      float shoulderX = rightHand ? -0.3125F : 0.3125F;
      Vector3f palm = armRotation.transform(new Vector3f(0.0F, 0.75F, 0.0F));
      palm.add(shoulderX + motion.x(), 0.125F + motion.y(), motion.z());
      palm.mul(root.sx(), root.sy(), root.sz());
      palm.y = palm.y - root.pivotY();
      rootRotation.transform(palm);
      palm.y = palm.y + root.pivotY();
      palm.add((float)binding.avatar().getX(), (float)binding.avatar().getY() + 0.72F * root.sy(), (float)binding.avatar().getZ());
      Quaternionf itemRotation = new Quaternionf().rotationTo(new Vector3f(1.0F, 0.0F, 0.0F), direction);
      return new PlayerActors.WeaponPose(new Vec3d(palm.x, palm.y, palm.z), direction, itemRotation);
   }

   public static PlayerActors.WeaponPose mouthWeaponPose(DeathScene scene) {
      PlayerActors.Binding binding = ACTIVE.values()
         .stream()
         .filter(candidate -> candidate.scene() == scene)
         .findFirst()
         .orElseThrow(() -> new IllegalStateException("Missing reference actor for mouth-held weapon"));
      AnimationCatalog.Cue cue = binding.cue();
      float p = scene.progress();
      DeathScene.Root root = AnimationCatalog.sample(cue.keys(), p);
      DeathBodyRig.Motion[] rig = rig(binding);
      DeathBodyRig.Motion head = rig[DeathBodyRig.Part.HEAD.ordinal()];
      Quaternionf rootRotation = new Quaternionf()
         .rotationY((float)Math.toRadians(root.yaw() + scene.sceneFacing))
         .rotateX((float)Math.toRadians(root.pitch()))
         .rotateZ((float)Math.toRadians(root.roll()));
      Quaternionf headRotation = new Quaternionf().rotationZYX(head.zRot(), head.yRot(), head.xRot());
      Vector3f localMouth = new Vector3f(head.x(), 1.5F + head.y(), head.z());
      localMouth.add(headRotation.transform(new Vector3f(0.0F, 0.0F, -0.36F)));
      localMouth.mul(root.sx(), root.sy(), root.sz());
      localMouth.y = localMouth.y - root.pivotY();
      rootRotation.transform(localMouth);
      localMouth.y = localMouth.y + root.pivotY();
      localMouth.add((float)binding.avatar().getX(), (float)binding.avatar().getY(), (float)binding.avatar().getZ());
      Quaternionf itemRotation = new Quaternionf(rootRotation).mul(headRotation);
      Vector3f direction = itemRotation.transform(new Vector3f(1.0F, 0.0F, 0.0F)).normalize();
      return new PlayerActors.WeaponPose(new Vec3d(localMouth.x, localMouth.y, localMouth.z), direction, itemRotation);
   }

   public static void discard(DeathScene scene) {
      ACTIVE.values().removeIf(b -> b.scene() == scene);
   }

   public static void discardActor(Entity entity) {
      ACTIVE.remove(entity.getId());
   }

   public static boolean visible(PlayerActors.Binding b) {
      if ("nested-player".equals(b.cue().actor())
         && b.scene().nestedDoll != null
         && b.scene().nestedDoll.zoomTinyPreview()
         && !b.scene().nestedDoll.isFinalActor(b)) {
         return false;
      }

      float p = b.scene().progress();
      return p >= b.cue().start() && p < b.cue().end() && (!b.cue().visualEffect() || DeathAnimationConfig.INSTANCE.visualEffects);
   }

   public static int tint(PlayerActors.Binding b) {
      float opacity = b.cue().opacity() == null ? 1.0F : b.cue().opacity();
      return Math.round(Math.clamp(opacity, 0.0F, 1.0F) * 255.0F) << 24 | 16777215;
   }

   public static DeathScene.Root root(PlayerActors.Binding b) {
      DeathScene.Root k = AnimationCatalog.sample(b.cue().keys(), b.scene().progress());
      return new DeathScene.Root(
         0.0F,
         0.0F,
         0.0F,
         k.pitch(),
         k.yaw() + b.scene().sceneFacing,
         k.roll(),
         k.sx(),
         k.sy(),
         k.sz(),
         b.cue().actorPivotY() == null ? 0.0F : b.cue().actorPivotY()
      );
   }

   public static DeathBodyRig.Motion[] rig(PlayerActors.Binding b) {
      DeathBodyRig.Motion[] parts = new DeathBodyRig.Motion[6];

      for (int i = 0; i < 6; i++) {
         parts[i] = new DeathBodyRig.Motion(false, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
      }

      if (b.cue().actorLimbs() != null) {
         for (AnimationCatalog.PartTrack track : b.cue().actorLimbs()) {
            DeathScene.Root k = AnimationCatalog.sample(track.keys(), b.scene().progress());
            parts[track.part()] = new DeathBodyRig.Motion(
               track.detached(),
               k.x(),
               k.y(),
               k.z(),
               (float)Math.toRadians(k.pitch()),
               (float)Math.toRadians(k.yaw()),
               (float)Math.toRadians(k.roll()),
               -1.0F,
               track.visibleUntil() == 0.0F ? 0.0F : 1.0F,
               k.sx(),
               k.sy(),
               k.sz()
            );
         }
      }

      return parts;
   }

   public static void update(DeathScene scene, Entity entity, AnimationCatalog.Cue cue, DeathScene.Root k) {
      Vector3f offset = new Vector3f(k.x(), k.y(), k.z());
      if (AnimationCatalog.recipe(scene.animation).grounded()) {
         float blend = "nested-player".equals(cue.actor()) ? 1.0F : Math.clamp(scene.progress() / 0.16F, 0.0F, 1.0F);
         blend = blend * blend * (3.0F - 2.0F * blend);
         offset.y = offset.y + (float)(scene.floorY - scene.y) * blend;
      }

      new Quaternionf().rotationY((float)Math.toRadians(scene.sceneFacing)).transform(offset);
      entity.setPosition(scene.x + offset.x, scene.y + offset.y, scene.z + offset.z);
      ((DeathAvatar)entity).syncOldPosition();
   }

   public static void transform(PlayerActors.Binding b, MatrixStack poses) {
      DeathScene.Root root = root(b);
      poses.translate(0.0F, root.pivotY(), 0.0F);
      poses.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(root.yaw()));
      poses.multiply(RotationAxis.POSITIVE_X.rotationDegrees(root.pitch()));
      poses.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(root.roll()));
      poses.translate(0.0F, -root.pivotY(), 0.0F);
      poses.scale(root.sx(), root.sy(), root.sz());
   }

   public static void vertices(PlayerActors.Binding b, Consumer<Vector3f> include) {
      DeathScene.Root root = root(b);
      Quaternionf rotation = new Quaternionf()
         .rotationY((float)Math.toRadians(root.yaw()))
         .rotateX((float)Math.toRadians(root.pitch()))
         .rotateZ((float)Math.toRadians(root.roll()));

      for (int corner = 0; corner < 8; corner++) {
         Vector3f point = new Vector3f((corner & 1) == 0 ? -1.1F : 1.1F, (corner & 2) == 0 ? -0.1F : 2.25F, (corner & 4) == 0 ? -0.85F : 0.85F)
            .mul(root.sx(), root.sy(), root.sz());
         point.y = point.y - root.pivotY();
         rotation.transform(point);
         point.y = point.y + root.pivotY();
         point.add((float)b.avatar().getX(), (float)b.avatar().getY(), (float)b.avatar().getZ());
         include.accept(point);
      }
   }

   public static Box bounds(PlayerActors.Binding b) {
      Vector3f low = new Vector3f(1.0F / 0.0F);
      Vector3f high = new Vector3f(-1.0F / 0.0F);
      vertices(b, p -> {
         low.min(p);
         high.max(p);
      });
      return new Box(low.x, low.y, low.z, high.x, high.y, high.z);
   }

   @Environment(EnvType.CLIENT)
   public record Binding(DeathScene scene, AnimationCatalog.Cue cue, DeathAvatar avatar) {
   }

   @Environment(EnvType.CLIENT)
   public record HandPose(Vec3d midpoint, Vector3f direction) {
   }

   @Environment(EnvType.CLIENT)
   private record Skins(String defaultSkin, Map<String, String> skins) {
   }

   @Environment(EnvType.CLIENT)
   public record WeaponPose(Vec3d grip, Vector3f direction, Quaternionf rotation) {
   }
}
