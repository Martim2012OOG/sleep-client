package com.baom5.deathanimation.client;

import com.baom5.deathanimation.mixin.client.DisplayStateAccess;
import java.util.Optional;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.decoration.DisplayEntity.BillboardMode;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.entity.decoration.DisplayEntity.TextDisplayEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class ClawMachinePrizeDrop {
   private static final float CABINET_HEIGHT = 3.9F;
   private static final float CLAW_TOP = 3.32F;
   private static final float PICKUP_X = -0.45F;
   private static final float PICKUP_Z = 0.24F;
   private static final float CHUTE_X = -0.64F;
   private static final float CHUTE_Z = 0.24F;
   private static final float PLAYER_SCALE = 0.62F;
   private static final float SEATED_Y = 0.78F;
   private static final float CARRY_Y = 1.84F;
   private static final float HUB_OFFSET = 1.27F;
   private static final float GRAB = 0.38F;
   private static final float RELEASE = 0.79F;
   private static final float DROP = 0.82F;
   private static final float LAND = 0.895F;
   private static final float WIN_REVEAL = 0.94F;
   private static final float WIN_END = 0.995F;

   private ClawMachinePrizeDrop() {
   }

   private static ClawMachinePrizeDrop.Path path(float p) {
      float approachX = smooth(phase(p, 0.04F, 0.115F));
      float approachZ = smooth(phase(p, 0.115F, 0.18F));
      float lower = smooth(phase(p, 0.2F, 0.34F));
      float lift = smooth(phase(p, 0.415F, 0.55F));
      float carry = smooth(phase(p, 0.575F, 0.74F));
      float x = MathHelper.lerp(approachX, -0.65F, -0.45F) + -0.19F * carry;
      float z = MathHelper.lerp(approachZ, -0.25F, 0.24F) + 0.0F * carry;
      float hubY = 3.1100001F - 1.0600001F * lower + 1.0600001F * lift;
      float open = 1.0F - smooth(phase(p, 0.355F, 0.405F));
      open = Math.max(open, smooth(phase(p, 0.79F, 0.82F)));
      float hanging = smooth(phase(p, 0.405F, 0.46F)) * (1.0F - phase(p, 0.79F, 0.82F));
      float travel = phase(p, 0.575F, 0.765F);
      float sway = (float)(Math.sin(travel * 3.141592653589793 * 3.0) * Math.pow(Math.sin(travel * 3.141592653589793), 2.0));
      return new ClawMachinePrizeDrop.Path(x, z, hubY, open, hanging, sway);
   }

   public static DeathScene.Root root(DeathScene scene) {
      float p = scene.progress();
      ClawMachinePrizeDrop.Path path = path(p);
      float x = p < 0.38F ? -0.45F : path.x;
      float z = p < 0.38F ? 0.24F : path.z;
      float y = p < 0.38F ? 0.78F : path.hubY - 1.27F;
      float falling = phase(p, 0.82F, 0.895F);
      y -= 1.6800001F * falling * falling;
      float crouch = smooth(phase(p, 0.895F, 0.925F));
      y -= 0.26F * crouch;
      float peek = smooth(phase(p, 0.935F, 0.955F)) * (1.0F - smooth(phase(p, 0.965F, 0.995F)));
      y += 0.16F * peek;
      Vector3f pos = world(scene, x, z);
      float floor = scene.gui ? 0.0F : (float)(scene.floorY - scene.y);
      return new DeathScene.Root(pos.x, floor + y, pos.z, 2.5F * path.sway, scene.sceneFacing, 3.0F * path.sway, 0.62F, 0.62F, 0.62F, 0.93F * path.hanging);
   }

   public static DeathBodyRig.Motion pose(DeathScene scene, int part) {
      float p = scene.progress();
      ClawMachinePrizeDrop.Path path = path(p);
      float seated = 1.0F - smooth(phase(p, 0.405F, 0.47F));
      float crouch = smooth(phase(p, 0.895F, 0.925F));
      float struggle = (float)Math.sin(phase(p, 0.405F, 0.51F) * 3.1415927F * 6.0F) * phase(p, 0.405F, 0.43F) * (1.0F - phase(p, 0.46F, 0.51F));
      float side = part != 2 && part != 4 ? 1.0F : -1.0F;
      float rx = 0.0F;
      float rz = 0.0F;
      switch (part) {
         case 0:
            rx = 0.1F * path.hanging + 0.1F * crouch;
            break;
         case 1:
            rx = 0.04F * crouch;
            break;
         case 2:
         case 3:
            rx = 0.22F * seated + 0.12F * path.hanging + 0.16F * struggle;
            rz = side * (0.04F + 0.06F * path.hanging + 0.08F * crouch);
            break;
         case 4:
         case 5:
            rx = -1.25F * seated + 0.12F * path.hanging + 0.16F * struggle - 1.35F * crouch;
            rz = side * 0.025F * path.hanging;
            break;
         default:
            throw new IllegalArgumentException("Unknown player body part " + part);
      }

      return new DeathBodyRig.Motion(false, 0.0F, 0.0F, 0.0F, rx, 0.0F, rz);
   }

   public static void spawn(DeathScene scene, Consumer<Entity> add) {
      if (scene.clawMachine == null) {
         ClawMachinePrizeDrop.State state = new ClawMachinePrizeDrop.State();
         scene.clawMachine = state;
         state.cabinet = display(scene, add, "claw_machine_cabinet");
         state.prizes = display(scene, add, "claw_machine_prize_fill");
         state.cable = display(scene, add, "claw_machine_cable");
         state.hub = display(scene, add, "claw_machine_hub");
         state.trolley = display(scene, add, "claw_machine_trolley");
         state.crossbar = display(scene, add, "claw_machine_rail");

         for (int i = 0; i < state.fingers.length; i++) {
            state.fingers[i] = display(scene, add, "claw_machine_finger");
         }

         state.prompt = (TextDisplayEntity)EntityType.TEXT_DISPLAY.create(scene.level, SpawnReason.COMMAND);
         if (state.prompt != null) {
            state.prompt.setText(Text.literal("PICK ME").styled(style -> style.withBold(true).withColor(16770698)));
            state.prompt.setBillboardMode(BillboardMode.CENTER);
            state.prompt.setBackground(1611274015);
            state.prompt.setLineWidth(160);
            state.prompt.setNoGravity(true);
            add.accept(state.prompt);
         }

         animate(scene);
      }
   }

   private static ItemDisplayEntity display(DeathScene scene, Consumer<Entity> add, String model) {
      ItemDisplayEntity entity = (ItemDisplayEntity)EntityType.ITEM_DISPLAY.create(scene.level, SpawnReason.COMMAND);
      if (entity == null) {
         throw new IllegalStateException("Could not create claw-machine display: " + model);
      }

      entity.setItemDisplayContext(ItemDisplayContext.FIXED);
      ItemStack stack = new ItemStack(Items.IRON_INGOT);
      stack.set(DataComponentTypes.ITEM_MODEL, Identifier.of("deathanimation", "mass/" + model));
      entity.setItemStack(stack);
      entity.setBrightness(new Brightness(15, 15));
      add.accept(entity);
      return entity;
   }

   public static void animate(DeathScene scene) {
      ClawMachinePrizeDrop.State state = scene.clawMachine;
      if (state != null) {
         ClawMachinePrizeDrop.Path path = path(scene.progress());
         set(scene, state.cabinet, 0.0F, 1.95F, 0.0F, 0.0F, 0.0F, 0.0F, 2.6F, 3.9F, 1.9F, 0.0F);
         set(scene, state.prizes, 0.0F, 1.95F, 0.0F, 0.0F, 0.0F, 0.0F, 2.6F, 3.9F, 1.9F, 0.0F);
         set(scene, state.crossbar, 0.0F, 3.335F, path.z, 0.0F, 0.0F, 0.0F, 2.16F, 0.05F, 0.05F, 0.0F);
         set(scene, state.trolley, path.x, 3.345F, path.z, 0.0F, 0.0F, 0.0F, 0.24F, 0.16F, 0.24F, 0.0F);
         float length = 3.32F - path.hubY;
         set(scene, state.cable, path.x, (3.32F + path.hubY) * 0.5F, path.z, 0.0F, 0.0F, 0.0F, 0.075F, length, 0.075F, 0.0F);
         set(scene, state.hub, path.x, path.hubY, path.z, 0.0F, 0.0F, 0.0F, 0.3F, 0.17F, 0.3F, 0.0F);

         for (int i = 0; i < state.fingers.length; i++) {
            float angle = (float)(i * 3.141592653589793 * 2.0 / 3.0);
            float x = path.x + 0.1F * (float)Math.cos(angle);
            float z = path.z + 0.1F * (float)Math.sin(angle);
            set(scene, state.fingers[i], x, path.hubY - 0.025F, z, 0.0F, -((float)Math.toDegrees(angle)), 25.0F * path.open, 0.6F, 0.6F, 0.6F, 0.3F);
         }

         updatePrize(scene, state);
      }
   }

   private static void updatePrize(DeathScene scene, ClawMachinePrizeDrop.State state) {
      DeathScene.Root root = root(scene);
      float visible = scene.progress() >= 0.94F && state.claimedAge < 0.0F ? 0.65F : 1.0E-4F;
      if (state.prompt != null) {
         state.prompt.setTransformation(new AffineTransformation(new Vector3f(), new Quaternionf(), new Vector3f(visible), new Quaternionf()));
         state.prompt.setPosition(scene.x + root.x(), scene.y + root.y() + 1.3, scene.z + root.z());
         refreshPrize(state.prompt);
      }
   }

   private static void refreshPrize(DisplayEntity display) {
      display.lastX = display.lastRenderX = display.getX();
      display.lastY = display.lastRenderY = display.getY();
      display.lastZ = display.lastRenderZ = display.getZ();
      DisplayStateAccess access = (DisplayStateAccess)display;
      access.deathanimations$setRenderState(access.deathanimations$freshRenderState());
      access.deathanimations$refreshSubState(false, 1.0F);
      access.deathanimations$setDirty(false);
   }

   static boolean claim(DeathScene scene, AbstractClientPlayerEntity player) {
      ClawMachinePrizeDrop.State state = scene.clawMachine;
      MinecraftClient client = MinecraftClient.getInstance();
      if (player != client.player) {
         return false;
      }

      if (state != null && !scene.gui && !(state.claimedAge >= 0.0F) && !(scene.progress() < 0.94F) && player.isAlive() && !player.isSpectator()) {
         DeathScene.Root root = root(scene);
         double x = scene.x + root.x();
         double y = scene.y + root.y();
         double z = scene.z + root.z();
         Box target = new Box(x - 0.32, y + 0.08, z - 0.32, x + 0.32, y + 1.12, z + 0.32);
         Vec3d eye = player.getEyePos();
         Vec3d end = eye.add(player.getRotationVector().multiply(4.5));
         Optional<Vec3d> hit = target.raycast(eye, end);
         if (hit.isEmpty()) {
            return false;
         }

         BlockHitResult obstruction = scene.level.raycast(new RaycastContext(eye, hit.get(), ShapeType.COLLIDER, FluidHandling.NONE, player));
         if (obstruction.getType() == Type.BLOCK && eye.squaredDistanceTo(obstruction.getPos()) < eye.squaredDistanceTo(hit.get()) - 1.0E-4) {
            return false;
         }

         state.claimedAge = scene.age;
         updatePrize(scene, state);
         PlayerPrizeActivation.start(scene);
         if (DeathAnimationConfig.INSTANCE.sounds) {
            scene.level.playSoundClient(player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 1.0F, 1.0F, false);
         }

         if (DeathAnimationConfig.INSTANCE.visualEffects) {
            client.particleManager.addEmitter(player, ParticleTypes.TOTEM_OF_UNDYING, 30);
         }

         return true;
      } else {
         return false;
      }
   }

   public static void effects(DeathScene scene) {
      ClawMachinePrizeDrop.State state = scene.clawMachine;
      if (state != null) {
         sound(scene, state, 1, 0.04F, (SoundEvent)SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 0.35F, 1.25F);
         sound(scene, state, 2, 0.2F, SoundEvents.BLOCK_PISTON_EXTEND, 0.38F, 0.78F);
         sound(scene, state, 4, 0.38F, SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE, 0.52F, 0.86F);
         sound(scene, state, 8, 0.415F, SoundEvents.BLOCK_CHAIN_STEP, 0.38F, 0.92F);
         sound(scene, state, 16, 0.79F, SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN, 0.45F, 1.1F);
         sound(scene, state, 32, 0.82F, SoundEvents.BLOCK_BARREL_OPEN, 0.38F, 0.85F);
         sound(scene, state, 64, 0.895F, SoundEvents.BLOCK_WOOL_FALL, 0.52F, 0.82F);
         sound(scene, state, 128, 0.94F, (SoundEvent)SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 0.38F, 1.55F);
         sound(scene, state, 512, 0.94F, SoundEvents.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.25F, 1.4F);
         winnerGlow(scene, state);
         if ((state.soundMask & 256) == 0 && scene.progress() >= 0.895F) {
            state.soundMask |= 256;
            if (DeathAnimationConfig.INSTANCE.visualEffects && !scene.gui) {
               for (int i = 0; i < 8; i++) {
                  Vector3f pos = world(scene, -0.64F + (float)(Math.random() - 0.5) * 0.38F, 0.24F + (float)(Math.random() - 0.5) * 0.38F);
                  EffectParticles.add(
                     scene,
                     ParticleTypes.CLOUD,
                     scene.x + pos.x,
                     scene.floorY + 0.18,
                     scene.z + pos.z,
                     (Math.random() - 0.5) * 0.025,
                     0.025,
                     (Math.random() - 0.5) * 0.025
                  );
               }
            }
         }
      }
   }

   private static void winnerGlow(DeathScene scene, ClawMachinePrizeDrop.State state) {
      float p = scene.progress();
      if (!scene.gui && !(state.claimedAge >= 0.0F) && DeathAnimationConfig.INSTANCE.visualEffects && !(p < 0.94F)) {
         int tick = (int)scene.age;
         if (tick != state.lastGlowTick) {
            boolean pop = state.lastGlowTick < 0;
            if (pop || tick - state.lastGlowTick >= 2) {
               state.lastGlowTick = tick;
               float fade = p < 0.995F ? 1.0F - smooth(phase(p, 0.97F, 0.995F)) : 0.65F;
               DeathScene.Root root = root(scene);
               double centerX = scene.x + root.x();
               double centerY = scene.y + root.y() + 0.6;
               double centerZ = scene.z + root.z();
               int count = pop ? 12 : 5;

               for (int i = 0; i < count; i++) {
                  double angle = i * 3.141592653589793 * 2.0 / count + tick * 0.17;
                  double radius = 0.26 + i % 3 * 0.045;
                  double dx = Math.cos(angle);
                  double dz = Math.sin(angle);
                  Particle glow = EffectParticles.create(
                     new DustParticleEffect(16773294, 1.15F * fade),
                     centerX + dx * radius,
                     centerY - 0.22 + i % 4 * 0.15,
                     centerZ + dz * radius,
                     dx * 0.006,
                     0.009,
                     dz * 0.006
                  );
                  if (glow != null) {
                     glow.setMaxAge(12);
                  }

                  if (pop || i % 2 == 0) {
                     Particle sparkle = EffectParticles.create(
                        ParticleTypes.END_ROD,
                        centerX + dx * radius,
                        centerY - 0.15 + i % 3 * 0.19,
                        centerZ + dz * radius,
                        dx * (pop ? 0.025 : 0.008),
                        0.025,
                        dz * (pop ? 0.025 : 0.008)
                     );
                     if (sparkle != null) {
                        sparkle.setMaxAge(pop ? 14 : 10);
                        sparkle.scale(0.4F * fade);
                        if (sparkle instanceof BillboardParticle quad) {
                           quad.setColor(1.0F, 0.92F, 0.64F);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static void sound(DeathScene scene, ClawMachinePrizeDrop.State state, int bit, float at, SoundEvent event, float volume, float pitch) {
      if (!(scene.progress() < at) && (state.soundMask & bit) == 0) {
         state.soundMask |= bit;
         if (DeathAnimationConfig.INSTANCE.sounds) {
            scene.level.playSoundClient(scene.x, scene.floorY + 1.1, scene.z, event, SoundCategory.PLAYERS, volume * (scene.gui ? 0.24F : 1.0F), pitch, false);
         }
      }
   }

   private static void set(
      DeathScene scene, ItemDisplayEntity display, float x, float y, float z, float pitch, float yaw, float roll, float sx, float sy, float sz, float anchorY
   ) {
      Vector3f pos = world(scene, x, z);
      Quaternionf rotation = new Quaternionf()
         .rotationY((float)Math.toRadians(scene.sceneFacing + yaw))
         .rotateX((float)Math.toRadians(pitch))
         .rotateZ((float)Math.toRadians(roll));
      Vector3f offset = rotation.transform(new Vector3f(0.0F, -anchorY, 0.0F));
      display.setTransformation(new AffineTransformation(offset, rotation, new Vector3f(sx, sy, sz), new Quaternionf()));
      display.setPosition(scene.x + pos.x, (scene.gui ? scene.y : scene.floorY) + y, scene.z + pos.z);
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

   private static float smooth(float p) {
      p = MathHelper.clamp(p, 0.0F, 1.0F);
      return p * p * (3.0F - 2.0F * p);
   }

   private static float phase(float p, float a, float b) {
      return MathHelper.clamp((p - a) / (b - a), 0.0F, 1.0F);
   }

   public static void discard(DeathScene scene) {
      scene.clawMachine = null;
   }

   @Environment(EnvType.CLIENT)
   private record Path(float x, float z, float hubY, float open, float hanging, float sway) {
   }

   @Environment(EnvType.CLIENT)
   static final class State {
      ItemDisplayEntity cabinet;
      ItemDisplayEntity prizes;
      ItemDisplayEntity cable;
      ItemDisplayEntity hub;
      ItemDisplayEntity trolley;
      ItemDisplayEntity crossbar;
      final ItemDisplayEntity[] fingers = new ItemDisplayEntity[3];
      int soundMask;
      int lastGlowTick = -1;
      TextDisplayEntity prompt;
      float claimedAge = -1.0F;
   }
}
