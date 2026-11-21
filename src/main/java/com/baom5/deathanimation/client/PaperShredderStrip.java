package com.baom5.deathanimation.client;

import com.baom5.deathanimation.mixin.client.DisplayStateAccess;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.AffineTransformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class PaperShredderStrip {
   static final float FEED_TOP = 1.65F;
   static final float OUTLET = 1.24F;
   static final float FEED_START = 0.33F;
   static final float FEED_END = 0.72F;
   static final float TRANSIT = 0.08415789F;

   private PaperShredderStrip() {
   }

   public static void spawn(DeathScene scene, Consumer<Entity> add) {
      if (scene.paperShredder == null) {
         PaperShredderStrip.State state = new PaperShredderStrip.State(scene);
         scene.paperShredder = state;
         ItemDisplayEntity machine = (ItemDisplayEntity)EntityType.ITEM_DISPLAY.create(scene.level, SpawnReason.COMMAND);
         if (machine == null) {
            throw new IllegalStateException("Could not create paper shredder");
         }

         state.machine = machine;
         machine.setItemDisplayContext(ItemDisplayContext.FIXED);
         ItemStack stack = new ItemStack(Items.IRON_INGOT);
         stack.set(DataComponentTypes.ITEM_MODEL, Identifier.of("deathanimation:mass/paper_shredder_office"));
         machine.setItemStack(stack);
         machine.setBrightness(new Brightness(15, 15));
         add.accept(machine);
         animate(scene);
      }
   }

   static float consumed(float p) {
      return 1.9F * AnimationTimeline.phase(p, 0.33F, 0.72F);
   }

   static float outputLength(float p) {
      return 1.9F * AnimationTimeline.phase(p, 0.4141579F, 0.8041579F);
   }

   static float paperAlpha(float p) {
      return AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.065F, 0.15F));
   }

   static float turn(float p) {
      return AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.2F, 0.28F));
   }

   static float pageCenterY(float p) {
      return 2.6F + 0.15F * (1.0F - AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.285F, 0.33F))) - consumed(p);
   }

   public static DeathScene.Root root(DeathScene scene) {
      float p = scene.progress();
      float flatten = AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.015F, 0.13F));
      float floor = scene.gui ? 0.0F : (float)(scene.floorY - scene.y);
      return new DeathScene.Root(0.0F, floor + 1.8F * flatten, 0.0F, 0.0F, scene.sceneFacing, 0.0F, 1.0F - 0.15F * flatten, 1.0F, 1.0F - 0.99F * flatten);
   }

   public static DeathBodyRig.Motion pose(DeathScene scene, int part) {
      return new DeathBodyRig.Motion(false, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
   }

   public static void animate(DeathScene scene) {
      PaperShredderStrip.State state = scene.paperShredder;
      if (state != null) {
         Quaternionf rotation = new Quaternionf().rotationY((float)Math.toRadians(scene.sceneFacing));
         state.machine.setTransformation(new AffineTransformation(new Vector3f(), rotation, new Vector3f(1.7F, 1.65F, 0.84F), new Quaternionf()));
         state.machine.setPosition(scene.x, (scene.gui ? scene.y : scene.floorY) + 0.824999988079071, scene.z);
         state.machine.lastX = state.machine.lastRenderX = state.machine.getX();
         state.machine.lastY = state.machine.lastRenderY = state.machine.getY();
         state.machine.lastZ = state.machine.lastRenderZ = state.machine.getZ();
         DisplayStateAccess access = (DisplayStateAccess)state.machine;
         access.deathanimations$setRenderState(access.deathanimations$freshRenderState());
         access.deathanimations$refreshSubState(false, 1.0F);
         access.deathanimations$setDirty(false);
         state.mesh.advance(scene);
      }
   }

   public static void effects(DeathScene scene) {
      PaperShredderStrip.State state = scene.paperShredder;
      if (state != null) {
         sound(scene, state, 1, 0.16F, SoundEvents.ITEM_BOOK_PAGE_TURN, 0.35F, 0.9F);
         sound(scene, state, 2, 0.33F, SoundEvents.BLOCK_PISTON_EXTEND, 0.34F, 0.78F);
         sound(scene, state, 4, 0.4141579F, SoundEvents.ITEM_SHEARS_SNIP, 0.4F, 1.15F);
         sound(scene, state, 8, 0.8191579F, SoundEvents.BLOCK_PISTON_CONTRACT, 0.32F, 0.68F);
         float p = scene.progress();
         int batch = (int)((p - 0.33F) * AnimationTimeline.ticks(scene.animation) / 7.0F);
         if (p >= 0.33F && p <= 0.8041579F && batch > state.lastSoundBatch) {
            state.lastSoundBatch = batch;
            if (DeathAnimationConfig.INSTANCE.sounds) {
               scene.level
                  .playSoundClient(
                     scene.x,
                     scene.floorY + 1.4,
                     scene.z,
                     SoundEvents.ITEM_SHEARS_SNIP,
                     SoundCategory.PLAYERS,
                     scene.gui ? 0.065F : 0.16F,
                     0.86F + batch % 3 * 0.09F,
                     false
                  );
            }
         }
      }
   }

   private static void sound(DeathScene scene, PaperShredderStrip.State state, int bit, float at, SoundEvent sound, float volume, float pitch) {
      if (!(scene.progress() < at) && (state.soundMask & bit) == 0) {
         state.soundMask |= bit;
         if (DeathAnimationConfig.INSTANCE.sounds) {
            scene.level.playSoundClient(scene.x, scene.floorY + 1.4, scene.z, sound, SoundCategory.PLAYERS, volume * (scene.gui ? 0.25F : 1.0F), pitch, false);
         }
      }
   }

   public static void discard(DeathScene scene) {
      scene.paperShredder = null;
   }

   @Environment(EnvType.CLIENT)
   static final class State {
      final PaperShredderMesh mesh;
      ItemDisplayEntity machine;
      int soundMask;
      int lastSoundBatch = -1;

      State(DeathScene scene) {
         this.mesh = new PaperShredderMesh(scene);
      }
   }
}
