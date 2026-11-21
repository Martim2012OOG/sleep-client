package com.baom5.deathanimation.client;

import com.baom5.deathanimation.mixin.client.DisplayStateAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class DominoBridgeCollapse {
   private static final int COUNT = 9;

   private DominoBridgeCollapse() {
   }

   private static double[] angles(DeathScene scene) {
      return scene.dominoBridge.physics.sample(scene.progress() * AnimationCatalog.recipe(scene.animation).ticks());
   }

   public static DeathScene.Root root(DeathScene scene) {
      float sy = scene.dominoBridge == null ? 1.0F : (float)DominoChainPhysics.bodyScale(angles(scene)[8]);
      float crush = (1.0F - sy) / 0.92F;
      return new DeathScene.Root(
         0.0F, (float)(scene.floorY - scene.y) + 0.005F, 0.0F, 0.0F, scene.sceneFacing, 0.0F, 1.0F + 0.8F * crush, sy, 1.0F + 0.6F * crush
      );
   }

   public static DeathBodyRig.Motion pose(DeathScene scene, int part) {
      float sy = scene.dominoBridge == null ? 1.0F : (float)DominoChainPhysics.bodyScale(angles(scene)[8]);
      float crush = (1.0F - sy) / 0.92F;
      float side = part != 2 && part != 4 ? 1.0F : -1.0F;
      float pitch = part == 0 ? 18.0F : (part == 2 || part == 3 ? -75.0F : (part != 4 && part != 5 ? 0.0F : -side * 10.0F));
      float yaw = part == 0 ? 7.0F : 0.0F;
      float roll = part != 2 && part != 3 ? (part != 4 && part != 5 ? 0.0F : side * 10.0F) : side * 40.0F;
      float radians = 0.017453292F;
      return new DeathBodyRig.Motion(false, 0.0F, 0.0F, 0.0F, pitch * radians * crush, yaw * radians * crush, roll * radians * crush);
   }

   public static void spawn(DeathScene scene, Consumer<Entity> add) {
      if (scene.dominoBridge == null) {
         DominoBridgeCollapse.State state = new DominoBridgeCollapse.State();
         scene.dominoBridge = state;
         int ticks = AnimationCatalog.recipe(scene.animation).ticks();
         state.physics = DominoChainPhysics.forDuration(ticks);
         state.impactProgress = (float)(state.physics.impactTick / ticks);

         for (int i = 0; i < 9; i++) {
            state.tiles.add(new DominoBridgeCollapse.Tile(makeDisplay(scene, add, "domino_tile_" + i % 6), 0.0F, (float)state.physics.pivotZ[i]));
         }

         animate(scene);
      }
   }

   private static ItemDisplayEntity makeDisplay(DeathScene scene, Consumer<Entity> add, String model) {
      ItemDisplayEntity display = (ItemDisplayEntity)EntityType.ITEM_DISPLAY.create(scene.level, SpawnReason.COMMAND);
      if (display == null) {
         throw new IllegalStateException("Could not create domino display");
      }

      display.setItemDisplayContext(ItemDisplayContext.FIXED);
      ItemStack stack = new ItemStack(Items.IRON_INGOT);
      stack.set(DataComponentTypes.ITEM_MODEL, Identifier.of("deathanimation", "mass/" + model));
      display.setItemStack(stack);
      display.setBrightness(new Brightness(15, 15));
      add.accept(display);
      return display;
   }

   public static void animate(DeathScene scene) {
      DominoBridgeCollapse.State state = scene.dominoBridge;
      if (state != null) {
         double[] a = angles(scene);

         for (int i = 0; i < 9; i++) {
            DominoBridgeCollapse.Tile tile = state.tiles.get(i);
            DominoChainPhysics physics = state.physics;
            Vector3f pos = world(scene, 0.0F, (float)physics.centerZ(i, a[i]));
            Quaternionf rotation = new Quaternionf().rotationY((float)Math.toRadians(scene.sceneFacing)).rotateX((float)a[i]);
            tile.display
               .setTransformation(
                  new AffineTransformation(
                     new Vector3f(),
                     rotation,
                     new Vector3f((float)physics.width[i] / 0.875F, (float)physics.height[i], (float)physics.depth[i] / 0.25F),
                     new Quaternionf()
                  )
               );
            tile.display.setPosition(scene.x + pos.x, scene.floorY + physics.centerY(i, a[i]), scene.z + pos.z);
            tile.display.lastX = tile.display.lastRenderX = tile.display.getX();
            tile.display.lastY = tile.display.lastRenderY = tile.display.getY();
            tile.display.lastZ = tile.display.lastRenderZ = tile.display.getZ();
            DisplayStateAccess access = (DisplayStateAccess)tile.display;
            access.deathanimations$setRenderState(access.deathanimations$freshRenderState());
            access.deathanimations$refreshSubState(false, 1.0F);
            access.deathanimations$setDirty(false);
         }
      }
   }

   private static float impactBlend(DeathScene scene) {
      return scene.dominoBridge == null ? 1.0F : scene.dominoBridge.impactProgress;
   }

   public static Vec3d cameraShake(DeathScene scene) {
      if (!scene.gui && !scene.preview && scene.dominoBridge != null) {
         float ticks = AnimationCatalog.recipe(scene.animation).ticks();
         float elapsed = scene.age - impactBlend(scene) * ticks;
         if (!(elapsed < 0.0F) && !(elapsed > 11.0F)) {
            float strength = 0.028F * (1.0F - elapsed / 11.0F);
            return new Vec3d(
               Math.sin(scene.age * 4.7F) * strength, Math.sin(scene.age * 7.1F) * strength * 0.4000000059604645, Math.cos(scene.age * 4.1F) * strength
            );
         } else {
            return Vec3d.ZERO;
         }
      } else {
         return Vec3d.ZERO;
      }
   }

   public static void effects(DeathScene scene) {
      DominoBridgeCollapse.State state = scene.dominoBridge;
      if (state != null && !state.tiles.isEmpty()) {
         float now = scene.age;

         for (int i = 0; i < state.tiles.size() - 1; i++) {
            DominoBridgeCollapse.Tile tile = state.tiles.get(i);
            float hitTick = (float)state.physics.contactTicks[i];
            if (now >= hitTick && !state.contactHeard[i]) {
               state.contactHeard[i] = true;
               if (DeathAnimationConfig.INSTANCE.sounds) {
                  Vector3f pos = world(scene, tile.x, tile.z);
                  scene.level
                     .playSoundClient(
                        scene.x + pos.x,
                        scene.floorY + 1.0,
                        scene.z + pos.z,
                        SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_ON,
                        SoundCategory.PLAYERS,
                        scene.gui ? 0.12F : 0.24F,
                        0.7F + i % 3 * 0.08F,
                        false
                     );
               }
            }
         }

         float impact = impactBlend(scene);
         if ((state.soundMask & 1) == 0 && scene.progress() >= impact) {
            state.soundMask |= 1;
            Vector3f target = world(scene, state.playerX, state.playerZ);
            if (DeathAnimationConfig.INSTANCE.sounds) {
               scene.level
                  .playSoundClient(
                     scene.x + target.x,
                     scene.floorY + 0.45,
                     scene.z + target.z,
                     SoundEvents.BLOCK_ANVIL_LAND,
                     SoundCategory.PLAYERS,
                     scene.gui ? 0.24F : 1.0F,
                     0.46F,
                     false
                  );
            }

            if (DeathAnimationConfig.INSTANCE.sounds) {
               scene.level
                  .playSoundClient(
                     scene.x + target.x,
                     scene.floorY + 0.35,
                     scene.z + target.z,
                     (SoundEvent)SoundEvents.ENTITY_GENERIC_EXPLODE.value(),
                     SoundCategory.PLAYERS,
                     scene.gui ? 0.1F : 0.32F,
                     0.68F,
                     false
                  );
            }
         }

         if ((state.soundMask & 2) == 0 && scene.progress() >= impact) {
            state.soundMask |= 2;
            if (DeathAnimationConfig.INSTANCE.visualEffects && !scene.gui) {
               Vector3f target = world(scene, state.playerX, state.playerZ);

               for (int i = 0; i < 22; i++) {
                  double angle = i * 3.141592653589793 * 2.0 / 22.0;
                  double rad = 0.28 + i % 4 * 0.1;
                  EffectParticles.add(
                     scene,
                     new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.COBBLESTONE.getDefaultState()),
                     scene.x + target.x + Math.cos(angle) * rad,
                     scene.floorY + 0.06 + i % 5 * 0.09,
                     scene.z + target.z + Math.sin(angle) * rad,
                     Math.cos(angle) * 0.08,
                     0.04 + i % 3 * 0.035,
                     Math.sin(angle) * 0.08
                  );
               }

               for (int i = 0; i < 12; i++) {
                  EffectParticles.add(
                     scene,
                     ParticleTypes.CLOUD,
                     scene.x + target.x + (Math.random() - 0.5) * 1.2,
                     scene.floorY + 0.1 + Math.random() * 0.45,
                     scene.z + target.z + (Math.random() - 0.5) * 1.0,
                     (Math.random() - 0.5) * 0.07,
                     0.025 + Math.random() * 0.055,
                     (Math.random() - 0.5) * 0.07
                  );
               }
            }
         }
      }
   }

   private static Vector3f world(DeathScene scene, float x, float z) {
      return new Quaternionf().rotationY((float)Math.toRadians(scene.sceneFacing)).transform(new Vector3f(x, 0.0F, z));
   }

   public static void discard(DeathScene scene) {
      scene.dominoBridge = null;
   }

   @Environment(EnvType.CLIENT)
   static final class State {
      final List<DominoBridgeCollapse.Tile> tiles = new ArrayList<>();
      DominoChainPhysics physics;
      int soundMask;
      final boolean[] contactHeard = new boolean[9];
      float impactProgress;
      float playerX = 0.0F;
      float playerZ = 0.0F;
   }

   @Environment(EnvType.CLIENT)
   private record Tile(ItemDisplayEntity display, float x, float z) {
   }
}
