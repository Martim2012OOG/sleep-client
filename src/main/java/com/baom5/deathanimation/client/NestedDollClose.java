package com.baom5.deathanimation.client;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
final class NestedDollClose {
   private static final int GIANT_COUNT = 16;
   static final int MINIATURE_STEPS = 15;
   private static final float GIANT_START = 0.025F;
   private static final float GIANT_STEP = 0.058F;
   static final float MINIATURE_START = 0.02F;
   static final float MINIATURE_STEP = 0.06F;
   private final DeathScene scene;
   private final boolean miniatureScene = ThreadLocalRandom.current().nextBoolean();
   private final List<NestedDollClose.Shell> shells = new ArrayList<>();
   private boolean spawned;
   private int spawnedLayers;
   private int soundMask;

   List<NestedDollClose.Shell> visibleShells() {
      if (this.shells.isEmpty()) {
         return List.of();
      }

      int current = this.spawnedLayers - 1;
      int first = this.closed(current) ? current : Math.max(0, current - 1);
      return this.shells.subList(first * 2, this.shells.size());
   }

   private boolean closed(int level) {
      return this.scene.progress() >= 0.025F + level * 0.058F + 0.051039997F;
   }

   float shellScale(NestedDollClose.Shell shell) {
      return 2.65F + shell.level * 0.82F;
   }

   float shellX(NestedDollClose.Shell shell) {
      float start = 0.025F + shell.level * 0.058F;
      float close = smooth(AnimationTimeline.phase(this.scene.progress(), start, start + 0.051039997F));
      return shell.side * (1.8F + this.shellScale(shell) * 0.7F) * (1.0F - close);
   }

   double shellBaseY() {
      return this.baseY() + 0.004;
   }

   Box shellBounds() {
      if (this.shells.isEmpty()) {
         return null;
      }

      Box bounds = null;

      for (NestedDollClose.Shell shell : this.visibleShells()) {
         float scale = this.shellScale(shell);
         float x = this.shellX(shell);

         for (int corner = 0; corner < 8; corner++) {
            float lowX = shell.side < 0 ? -0.34F : 0.0F;
            float highX = shell.side < 0 ? 0.0F : 0.34F;
            Vector3f point = world(
               this.scene, x + scale * ((corner & 1) == 0 ? lowX : highX), scale * ((corner & 2) == 0 ? 0 : 1), scale * ((corner & 4) == 0 ? -0.28F : 0.28F)
            );
            double px = this.scene.x + point.x;
            double py = this.shellBaseY() + point.y;
            double pz = this.scene.z + point.z;
            Box vertex = new Box(px, py, pz, px, py, pz);
            bounds = bounds == null ? vertex : bounds.union(vertex);
         }
      }

      return bounds;
   }

   NestedDollClose(DeathScene scene) {
      this.scene = scene;
   }

   boolean bodyVisible() {
      return !this.miniatureScene;
   }

   boolean zoomTinyPreview() {
      float finalStart = 0.89479995F;
      return this.miniatureScene && this.scene.gui && this.scene.progress() >= finalStart && this.scene.progress() < 0.985F;
   }

   boolean isFinalActor(PlayerActors.Binding binding) {
      return "nested-player".equals(binding.cue().actor()) && binding.cue().keys().getFirst().sx() == Math.scalb(1.0F, -15);
   }

   float previewScale(float requested) {
      return this.zoomTinyPreview() ? requested * Math.scalb(0.5F, 15) : requested;
   }

   static DeathScene.Root root(DeathScene scene) {
      NestedDollClose state = scene.nestedDoll;
      if (state == null) {
         return DeathScene.Root.STILL;
      }

      float ground = (float)(state.baseY() - scene.y);
      if (!state.miniatureScene) {
         return new DeathScene.Root(0.0F, ground, 0.0F, 0.0F, scene.sceneFacing, 0.0F);
      }

      boolean originalVisible = state.bodyVisible();
      float inflate = scene.progress() < 0.08F ? 1.025F : 1.0F;
      float scale = originalVisible ? inflate : 1.0E-4F;
      return new DeathScene.Root(0.0F, ground, 0.0F, 0.0F, scene.sceneFacing, 0.0F, scale, scale, scale);
   }

   static void spawn(DeathScene scene, Consumer<Entity> add) {
      NestedDollClose state = scene.nestedDoll;
      if (state != null) {
         state.spawned = true;
         if (state.miniatureScene) {
            state.spawnMiniatures(add);
         }

         state.animateProps(scene);
      }
   }

   private void spawnMiniatures(Consumer<Entity> add) {
      PlayerActors.spawnNestedPlayer(this.scene, List.of(key(0.0F, 0.0F, 0.0F, 0.0F, 1.0F), key(1.0F, 0.0F, 0.0F, 0.0F, 1.0F)), 0.0F, 1.01F, add);

      for (int level = 1; level <= 15; level++) {
         int parent = level - 1;
         float stage = 0.02F + parent * 0.06F;
         float start = stage + 0.024F;
         float end = level == 15 ? 0.985F : 1.01F;
         float exactScale = (float)Math.scalb(1.0, -level);
         float parentX = miniatureX(parent);
         float childX = miniatureX(level);
         List<AnimationCatalog.Key> keys = List.of(
            key(start, parentX, 0.0F, 0.0F, exactScale),
            key(start + 0.006F, parentX, 0.76F * (float)Math.scalb(1.0, -parent), 0.0F, exactScale),
            key(start + 0.0144F, childX, 0.76F * (float)Math.scalb(1.0, -parent), 0.0F, exactScale),
            key(start + 0.019199999F, childX, 0.0F, 0.0F, exactScale),
            key(end, childX, 0.0F, 0.0F, exactScale)
         );
         PlayerActors.spawnNestedPlayer(this.scene, keys, start, end, add);
      }
   }

   private static AnimationCatalog.Key key(float p, float x, float y, float z, float scale) {
      return new AnimationCatalog.Key(p, x, y, z, 0.0F, 0.0F, 0.0F, scale, scale, scale, "smooth");
   }

   static void animate(DeathScene scene) {
      NestedDollClose state = scene.nestedDoll;
      if (state != null) {
         state.animateProps(scene);
      }
   }

   private void animateProps(DeathScene scene) {
      float p = scene.progress();
      if (!this.miniatureScene && this.spawned) {
         int target = p < 0.025F ? 0 : Math.min(16, 1 + (int)((p - 0.025F) / 0.058F));

         while (this.spawnedLayers < target) {
            int level = this.spawnedLayers++;
            this.shells.add(new NestedDollClose.Shell(level, -1));
            this.shells.add(new NestedDollClose.Shell(level, 1));
         }
      }

      for (NestedDollClose.Shell shell : this.shells) {
         this.sound(shell.level, this.closed(shell.level));
      }

      for (Entity entity : scene.props) {
         PlayerActors.Binding binding = PlayerActors.forEntity(entity);
         if (binding != null && binding.scene() == scene) {
            DeathScene.Root key = AnimationCatalog.sample(binding.cue().keys(), p);
            PlayerActors.update(
               scene, entity, binding.cue(), new DeathScene.Root(key.x(), key.y(), key.z(), key.pitch(), key.yaw(), key.roll(), key.sx(), key.sy(), key.sz())
            );
         }
      }
   }

   private static float miniatureX(int level) {
      return 3.08F * (1.0F - (float)Math.scalb(1.0, -level));
   }

   private double baseY() {
      return this.scene.gui ? this.scene.y : this.scene.floorY;
   }

   private void sound(int index, boolean ready) {
      int bit = 1 << index;
      if (ready && (this.soundMask & bit) == 0) {
         this.soundMask |= bit;
         this.play(SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_ON, 0.55F, 0.72F + index % 4 * 0.12F);
      }
   }

   private void play(SoundEvent sound, float volume, float pitch) {
      if (DeathAnimationConfig.INSTANCE.sounds) {
         this.scene
            .level
            .playSoundClient(
               this.scene.x, this.baseY() + 0.65, this.scene.z, sound, SoundCategory.PLAYERS, this.scene.gui ? volume * 0.18F : volume, pitch, false
            );
      }
   }

   private static Vector3f world(DeathScene scene, float x, float y, float z) {
      Vector3f horizontal = new Vector3f(x, 0.0F, z);
      new Quaternionf().rotationY((float)Math.toRadians(scene.sceneFacing)).transform(horizontal);
      return new Vector3f(horizontal.x, y, horizontal.z);
   }

   private static float clamp(float value) {
      return Math.max(0.0F, Math.min(1.0F, value));
   }

   private static float smooth(float value) {
      float t = clamp(value);
      return t * t * (3.0F - 2.0F * t);
   }

   @Environment(EnvType.CLIENT)
   record Shell(int level, int side) {
   }
}
