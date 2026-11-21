package com.baom5.deathanimation.client;

import com.baom5.deathanimation.mixin.client.DisplayStateAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.MatrixStack.Entry;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class PhotocopierMassProduction {
   private static final int TOTAL_COPIES = 220;
   private static final float SCANNER_Y = 1.79F;
   private static final float SCAN_START = 0.23F;
   private static final float SCAN_END = 0.42F;
   private static final float PRINT_START = 0.46F;
   private static final float PRINT_END = 0.94F;
   private static final float OUTPUT_Y = 1.05F;
   private static final float OUTPUT_Z = -1.135F;
   private static final float PAGE_W = 0.49F;
   private static final float PAGE_H = 0.69F;
   private static final float FEED_TIME = 0.035F;
   private static final Identifier WHITE = Identifier.of("deathanimation:textures/entity/white_skin.png");

   private PhotocopierMassProduction() {
   }

   public static void start(DeathScene scene, Consumer<Entity> add) {
      if (scene.photocopier == null) {
         PhotocopierMassProduction.State state = new PhotocopierMassProduction.State(scene);
         scene.photocopier = state;
         state.cabinet = (ItemDisplayEntity)EntityType.ITEM_DISPLAY.create(scene.level, SpawnReason.COMMAND);
         state.scanner = (BlockDisplayEntity)EntityType.BLOCK_DISPLAY.create(scene.level, SpawnReason.COMMAND);
         if (state.cabinet != null && state.scanner != null) {
            ItemStack stack = new ItemStack(Items.PAPER);
            stack.set(DataComponentTypes.ITEM_MODEL, Identifier.of("deathanimation:mass/refined_prop_photocopier_lime"));
            state.cabinet.setItemStack(stack);
            state.cabinet.setItemDisplayContext(ItemDisplayContext.FIXED);
            state.cabinet.setBrightness(new Brightness(15, 15));
            state.scanner.setBlockState(Blocks.SEA_LANTERN.getDefaultState());
            state.scanner.setBrightness(new Brightness(15, 15));
            add.accept(state.cabinet);
            add.accept(state.scanner);
            animate(scene);
         } else {
            throw new IllegalStateException("Could not create office copier");
         }
      }
   }

   public static DeathScene.Root root(DeathScene scene) {
      float p = scene.progress();
      float flight = AnimationTimeline.phase(p, 0.025F, 0.2F);
      float landed = AnimationTimeline.smooth(flight);
      float absorb = AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.955F, 1.0F));
      DeathScene.Root flat = new DeathScene.Root(0.0F, 0.0F, 0.0F, -90.0F, scene.sceneFacing, 0.0F, 0.92F, 0.92F, 0.92F, 0.9F);
      float contact = 1.8019999F - Acrobatics.minimumY(scene, flat);
      float y = MathHelper.lerp(landed, 0.0F, contact) + 0.68F * (float)Math.sin(3.141592653589793 * flight) - 0.35F * absorb;
      Vector3f offset = world(scene, -0.195F * landed, MathHelper.lerp(landed, -1.85F, 0.0F));
      float scale = MathHelper.lerp(landed, 1.0F, 0.92F) * (1.0F - 0.98F * absorb);
      return new DeathScene.Root(
         offset.x,
         (scene.gui ? 0.0F : (float)(scene.floorY - scene.y)) + y,
         offset.z,
         -90.0F * landed,
         scene.sceneFacing,
         -8.0F * (float)Math.sin(3.141592653589793 * flight),
         scale,
         scale,
         scale,
         0.9F
      );
   }

   public static DeathBodyRig.Motion pose(DeathScene scene, int part) {
      float airborne = (float)Math.sin(3.141592653589793 * AnimationTimeline.phase(scene.progress(), 0.025F, 0.2F));

      float pitch = switch (part) {
         case 0 -> -10.0F * airborne;
         default -> 0.0F;
         case 2, 3 -> -50.0F * airborne;
         case 4 -> 15.0F * airborne;
         case 5 -> -15.0F * airborne;
      };
      float roll = part == 2 ? 18.0F * airborne : (part == 3 ? -18.0F * airborne : 0.0F);
      return new DeathBodyRig.Motion(
         false,
         0.0F,
         0.0F,
         0.0F,
         (float)Math.toRadians(pitch),
         0.0F,
         (float)Math.toRadians(roll),
         -1.0F,
         1.0F - AnimationTimeline.smooth(AnimationTimeline.phase(scene.progress(), 0.955F, 1.0F))
      );
   }

   public static void animate(DeathScene scene) {
      PhotocopierMassProduction.State state = scene.photocopier;
      if (state != null) {
         float p = scene.progress();
         if (p < state.lastProgress) {
            state.sheets.clear();
            state.nextCopy = 0;
            state.soundMask = 0;
            state.lastPrintSoundBatch = 0;
         }

         state.lastProgress = p;

         while (state.nextCopy < 220 && p >= printPhase(state.nextCopy)) {
            int index = state.nextCopy++;
            Random random = new Random(scene.seed ^ index * -7046029254386353131L);
            float targetX = (random.nextFloat() - 0.5F) * 1.85F;
            float targetZ = -1.85F - random.nextFloat() * 1.65F;
            float groundY = 0.004F;

            for (PhotocopierMassProduction.Sheet previous : state.sheets) {
               if (Math.abs(previous.targetX - targetX) < 0.5635F && Math.abs(previous.targetZ - targetZ) < 0.7935F) {
                  groundY = Math.max(groundY, previous.groundY + 0.002F);
               }
            }

            state.sheets.add(new PhotocopierMassProduction.Sheet(printPhase(index), targetX, targetZ, (random.nextFloat() - 0.5F) * 0.7F, index, groundY));
         }

         Quaternionf facing = new Quaternionf().rotationY((float)Math.toRadians(scene.sceneFacing));
         set(scene, state.cabinet, 0.0F, 0.0F, 0.0F, new Vector3f(2.0F), facing, false);
         boolean scanning = p >= 0.23F && p < 0.42F;
         float scanZ = MathHelper.lerp(AnimationTimeline.phase(p, 0.23F, 0.42F), -1.01F, 1.01F);
         set(scene, state.scanner, -0.195F, 1.796F, scanZ, scanning ? new Vector3f(1.47F, 0.009F, 0.045F) : new Vector3f(1.0E-5F), facing, true);
      }
   }

   private static float printPhase(int index) {
      return 0.46F + 0.48F * (float)Math.pow(index / 219.0, 0.48);
   }

   private static float clock(DeathScene scene) {
      float partial = MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(false);
      return (scene.age + partial * DeathAnimationConfig.INSTANCE.animationSpeed) / AnimationTimeline.ticks(scene.animation);
   }

   private static PhotocopierMassProduction.SheetPose sheetPose(PhotocopierMassProduction.Sheet sheet, float time, int ticks) {
      float feedTime = sheet.index == 219 ? 0.055F : Math.min(0.035F, 0.85F * (printPhase(sheet.index + 1) - sheet.start));
      float feed = AnimationTimeline.phase(time, sheet.start, sheet.start + feedTime);
      if (feed < 1.0F) {
         return new PhotocopierMassProduction.SheetPose(0.0F, 1.05F, -0.78999996F - 0.69F * feed, -0.345F + 0.69F * feed, new Quaternionf());
      }

      float seconds = Math.max(0.0F, (time - sheet.start - feedTime) * ticks / 20.0F);
      float layer = sheet.groundY;
      float fallTime = (float)Math.sqrt(2.0F * (1.05F - layer) / 9.8);
      float t = Math.min(1.0F, seconds / fallTime);
      float startZ = -1.48F;
      float x = sheet.targetX * AnimationTimeline.smooth(t);
      float z = MathHelper.lerp(t, startZ, sheet.targetZ);
      float y = Math.max(layer, 1.05F - 4.9F * seconds * seconds);
      float flutter = (float)Math.sin(seconds * 22.0F + sheet.index * 0.8F) * 0.12F * (float)Math.sin(3.141592653589793 * t);
      Quaternionf rotation = new Quaternionf().rotationY(sheet.yaw * AnimationTimeline.smooth(t)).rotateX(flutter).rotateZ(-flutter * 0.55F);
      float extent = Math.abs(rotation.transform(new Vector3f(0.245F, 0.0F, 0.0F)).y) + Math.abs(rotation.transform(new Vector3f(0.0F, 0.0F, 0.345F)).y);
      y = Math.max(y, layer + extent);
      return new PhotocopierMassProduction.SheetPose(x, y, z, 0.345F, rotation);
   }

   public static void submitPrintedPages(DeathScene scene, MatrixStack poses, OrderedRenderCommandQueue collector, Vec3d relativeTo) {
      PhotocopierMassProduction.State state = scene.photocopier;
      if (state != null && !state.sheets.isEmpty()) {
         state.print.refresh(scene);
         Identifier skin = scene.avatar.getSkin().body().texturePath();
         float time = clock(scene);
         float fit = Math.min(0.32666665F, 0.3268421F);

         for (PhotocopierMassProduction.Sheet sheet : state.sheets) {
            PhotocopierMassProduction.SheetPose page = sheetPose(sheet, time, AnimationTimeline.ticks(scene.animation));
            if (!(page.visibleTop <= -0.34499F)) {
               Vector3f offset = world(scene, page.x, page.z);
               poses.push();
               poses.translate(
                  scene.x + offset.x - relativeTo.x, (scene.gui ? scene.y : scene.floorY) + page.y - relativeTo.y, scene.z + offset.z - relativeTo.z
               );
               poses.multiply(new Quaternionf().rotationY((float)Math.toRadians(scene.sceneFacing)).mul(page.rotation));
               collector.submitCustom(poses, RenderLayers.entityCutout(WHITE), (pose, vertices) -> {
                  quad(vertices, pose, -0.245F, -0.345F, 0.245F, page.visibleTop, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, false);
                  quad(vertices, pose, -0.245F, -0.345F, 0.245F, page.visibleTop, -0.001F, 0.0F, 0.0F, 1.0F, 1.0F, true);
               });
               collector.submitCustom(
                  poses,
                  RenderLayers.entityCutout(skin),
                  (pose, vertices) -> {
                     for (PrintedPlayerPage.Patch patch : state.print.patches) {
                        if (patch.material() != 0) {
                           float bottom = (patch.bottom() - 0.95F) * fit;
                           float top = Math.min((patch.top() - 0.95F) * fit, page.visibleTop);
                           if (!(top <= bottom)) {
                              float fullHeight = (patch.top() - patch.bottom()) * fit;
                              float topV = MathHelper.lerp((top - bottom) / fullHeight, patch.v1(), patch.v0());
                              quad(
                                 vertices,
                                 pose,
                                 patch.left() * fit,
                                 bottom,
                                 patch.right() * fit,
                                 top,
                                 patch.material() == 2 ? 7.0E-4F : 4.0E-4F,
                                 patch.u0(),
                                 topV,
                                 patch.u1(),
                                 patch.v1(),
                                 false
                              );
                           }
                        }
                     }
                  }
               );
               poses.pop();
            }
         }
      }
   }

   private static void quad(
      VertexConsumer out, Entry pose, float left, float bottom, float right, float top, float y, float u0, float v0, float u1, float v1, boolean back
   ) {
      vertex(out, pose, left, y, bottom, u0, v1, back);
      if (back) {
         vertex(out, pose, right, y, bottom, u1, v1, true);
         vertex(out, pose, right, y, top, u1, v0, true);
         vertex(out, pose, left, y, top, u0, v0, true);
      } else {
         vertex(out, pose, left, y, top, u0, v0, false);
         vertex(out, pose, right, y, top, u1, v0, false);
         vertex(out, pose, right, y, bottom, u1, v1, false);
      }
   }

   private static void vertex(VertexConsumer out, Entry pose, float x, float y, float z, float u, float v, boolean back) {
      out.vertex(pose, new Vector3f(x, y, z))
         .color(-1)
         .texture(u, v)
         .overlay(655360)
         .light(15728880)
         .normal(pose, new Vector3f(0.0F, back ? -1.0F : 1.0F, 0.0F));
   }

   private static void set(DeathScene scene, DisplayEntity display, float x, float y, float z, Vector3f scale, Quaternionf rotation, boolean centeredBlock) {
      Vector3f center = world(scene, x, z);
      Vector3f shift = centeredBlock ? rotation.transform(new Vector3f(scale).mul(-0.5F)) : new Vector3f();
      display.setTransformation(new AffineTransformation(shift, rotation, scale, new Quaternionf()));
      display.setPosition(scene.x + center.x, (scene.gui ? scene.y : scene.floorY) + y, scene.z + center.z);
      display.lastX = display.lastRenderX = display.getX();
      display.lastY = display.lastRenderY = display.getY();
      display.lastZ = display.lastRenderZ = display.getZ();
      DisplayStateAccess access = (DisplayStateAccess)display;
      access.deathanimations$setRenderState(access.deathanimations$freshRenderState());
      access.deathanimations$refreshSubState(false, 1.0F);
      access.deathanimations$setDirty(false);
   }

   private static Vector3f world(DeathScene scene, float x, float z) {
      return new Quaternionf().rotationY((float)Math.toRadians(scene.sceneFacing)).transform(new Vector3f(x, 0.0F, z));
   }

   public static void effects(DeathScene scene) {
      PhotocopierMassProduction.State state = scene.photocopier;
      if (state != null) {
         sound(scene, state, 1, 0.025F, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 0.35F, 0.8F);
         sound(scene, state, 2, 0.2F, SoundEvents.BLOCK_WOOL_FALL, 0.4F, 0.85F);
         sound(scene, state, 4, 0.23F, SoundEvents.BLOCK_BEACON_ACTIVATE, 0.18F, 1.7F);
         sound(scene, state, 8, 0.46F, SoundEvents.BLOCK_PISTON_EXTEND, 0.35F, 1.1F);
         sound(scene, state, 16, 0.94F, SoundEvents.BLOCK_PISTON_CONTRACT, 0.5F, 0.65F);
         int batch = state.nextCopy / 8;
         if (batch > state.lastPrintSoundBatch) {
            state.lastPrintSoundBatch = batch;
            if (DeathAnimationConfig.INSTANCE.sounds) {
               scene.level
                  .playSoundClient(
                     scene.x,
                     scene.floorY + 1.0499999523162842,
                     scene.z,
                     SoundEvents.ITEM_BOOK_PAGE_TURN,
                     SoundCategory.PLAYERS,
                     scene.gui ? 0.08F : 0.22F,
                     1.1F + scene.progress() * 0.4F,
                     false
                  );
            }
         }
      }
   }

   private static void sound(DeathScene scene, PhotocopierMassProduction.State state, int bit, float at, SoundEvent event, float volume, float pitch) {
      if (!(scene.progress() < at) && (state.soundMask & bit) == 0) {
         state.soundMask |= bit;
         if (DeathAnimationConfig.INSTANCE.sounds) {
            scene.level.playSoundClient(scene.x, scene.floorY + 1.1, scene.z, event, SoundCategory.PLAYERS, volume * (scene.gui ? 0.25F : 1.0F), pitch, false);
         }
      }
   }

   public static void discard(DeathScene scene) {
      scene.photocopier = null;
   }

   @Environment(EnvType.CLIENT)
   private record Sheet(float start, float targetX, float targetZ, float yaw, int index, float groundY) {
   }

   @Environment(EnvType.CLIENT)
   private record SheetPose(float x, float y, float z, float visibleTop, Quaternionf rotation) {
   }

   @Environment(EnvType.CLIENT)
   static final class State {
      final PrintedPlayerPage print;
      final List<PhotocopierMassProduction.Sheet> sheets = new ArrayList<>(220);
      ItemDisplayEntity cabinet;
      BlockDisplayEntity scanner;
      int nextCopy;
      int soundMask;
      int lastPrintSoundBatch;
      float lastProgress;

      State(DeathScene scene) {
         this.print = new PrintedPlayerPage(scene);
      }
   }
}
