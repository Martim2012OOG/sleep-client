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
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.decoration.DisplayEntity.BillboardMode;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.entity.decoration.DisplayEntity.TextDisplayEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
final class BowlingSplit {
   private static final int PIN_COUNT = 10;
   private static final float BALL_START_Z = -4.1F;
   private static final float BALL_END_Z = 3.15F;
   private static final float BALL_TRAVEL_START = 0.095F;
   private static final float BALL_TRAVEL_END = 0.4F;
   private static final float PLAYER_CONTACT_Z = -0.5F;
   private static final float PLAYER_FALL_START = 0.245F;
   private static final float PLAYER_FALL_END = 0.49F;
   private static final float PIN_HEIGHT = 1.08F;
   private static final float PIN_ROW_SPACING = 0.43F;
   private static final float PIN_COLUMN_SPACING = 0.46F;

   private BowlingSplit() {
   }

   static DeathScene.Root root(DeathScene scene) {
      float fall = AnimationTimeline.smooth(AnimationTimeline.phase(scene.progress(), 0.245F, 0.49F));
      return new DeathScene.Root(0.0F, 0.0F, 0.0F, 90.0F * fall, scene.sceneFacing, 0.0F, 1.0F, 1.0F, 1.0F, 0.0F);
   }

   static DeathBodyRig.Motion pose(DeathScene scene, int part) {
      if (part >= 0 && part < 6) {
         return new DeathBodyRig.Motion(false, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
      } else {
         throw new IllegalArgumentException("Unknown player body part " + part);
      }
   }

   static void spawn(DeathScene scene, Consumer<Entity> add) {
      if (scene.bowlingSplit == null) {
         BowlingSplit.State state = new BowlingSplit.State();
         scene.bowlingSplit = state;
         float[][] rack = new float[][]{
            {0.0F, 1.18F},
            {-0.23F, 1.6099999F},
            {0.23F, 1.6099999F},
            {-0.46F, 2.04F},
            {0.0F, 2.04F},
            {0.46F, 2.04F},
            {-0.69F, 2.4699998F},
            {-0.23F, 2.4699998F},
            {0.23F, 2.4699998F},
            {0.69F, 2.4699998F}
         };

         for (int i = 0; i < 10; i++) {
            state.pins[i] = new BowlingSplit.Pin(item(scene, add, "bowling_pin"), rack[i][0], rack[i][1]);
         }

         state.ball = item(scene, add, "bowling_ball");
         state.finish = text(scene, add);
         animate(scene);
      }
   }

   static void animate(DeathScene scene) {
      BowlingSplit.State state = scene.bowlingSplit;
      if (state != null) {
         float p = scene.progress();
         int target = Math.max(0, (int)scene.age);
         if (target < state.simulatedTick) {
            reset(state);
         }

         while (state.simulatedTick < target) {
            state.simulatedTick++;
            step(scene, state, state.simulatedTick);
         }

         updateBall(scene, state, p);

         for (BowlingSplit.Pin pin : state.pins) {
            updatePin(scene, pin);
         }

         updateFinish(scene, state, p);
         if (state.pendingHits > 0 && scene.age - state.lastSoundAge >= 2.2F && !scene.gui && !scene.preview && DeathAnimationConfig.INSTANCE.sounds) {
            int hits = state.pendingHits;
            state.pendingHits = 0;
            state.lastSoundAge = scene.age;
            float volume = Math.min(0.75F, 0.22F + 0.11F * hits);
            scene.level
               .playSoundClient(
                  scene.x,
                  scene.floorY + 0.16,
                  scene.z + 1.0,
                  hits >= 3 ? SoundEvents.BLOCK_ANVIL_LAND : SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_ON,
                  SoundCategory.PLAYERS,
                  volume,
                  hits >= 3 ? 0.82F : 1.35F,
                  false
               );
         }

         if (state.ballHit && !state.ballImpactSounded) {
            state.ballImpactSounded = true;
            if (DeathAnimationConfig.INSTANCE.sounds && !scene.gui && !scene.preview) {
               scene.level
                  .playSoundClient(scene.x, scene.floorY + 0.42, scene.z + -0.5, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.72F, 0.88F, false);
            }
         }

         if (!scene.gui && DeathAnimationConfig.INSTANCE.visualEffects && state.ballHit && p < 0.425F && (int)scene.age % 3 == 0) {
            for (int i = 0; i < 2; i++) {
               EffectParticles.add(
                  scene,
                  ParticleTypes.CLOUD,
                  scene.x + (Math.random() - 0.5) * 0.75,
                  scene.floorY + 0.04 + Math.random() * 0.2,
                  scene.z + -0.5 + (Math.random() - 0.5) * 0.65,
                  (Math.random() - 0.5) * 0.06,
                  0.025 + Math.random() * 0.025,
                  (Math.random() - 0.5) * 0.06
               );
            }
         }
      }
   }

   private static void step(DeathScene scene, BowlingSplit.State state, int tick) {
      float ticks = AnimationCatalog.recipe(scene.animation).ticks();
      float ballZ = ballZ(tick, ticks);
      float previousZ = ballZ(Math.max(0, tick - 1), ticks);
      float ballSpeed = ballZ - previousZ;
      if (!state.playerHit && previousZ < -0.5F && ballZ >= -0.5F) {
         state.playerHit = true;
         state.ballHit = true;
      }

      for (BowlingSplit.Pin pin : state.pins) {
         if (!pin.hit && state.playerHit) {
            float d2 = pin.x * pin.x + (pin.z - ballZ) * (pin.z - ballZ);
            if (d2 < 0.15209998F) {
               float dx = pin.x;
               float dz = pin.z - ballZ;
               float len = (float)Math.sqrt(dx * dx + dz * dz);
               if (len < 0.001F) {
                  dx = 0.08F;
                  dz = 0.99F;
                  len = 1.0F;
               }

               impulse(pin, dx / len, dz / len, Math.max(0.12F, ballSpeed * 1.65F));
               state.pendingHits++;
               pin.hit = true;
            }
         }
      }

      for (int i = 0; i < 10; i++) {
         for (int j = i + 1; j < 10; j++) {
            BowlingSplit.Pin a = state.pins[i];
            BowlingSplit.Pin b = state.pins[j];
            float dx = b.x - a.x;
            float dz = b.z - a.z;
            float dist2 = dx * dx + dz * dz;
            float reach = (a.angle > 0.72F ? 0.37F : 0.21F) + (b.angle > 0.72F ? 0.37F : 0.21F);
            if (!(dist2 >= reach * reach) && !(dist2 < 1.0E-7F)) {
               float dist = (float)Math.sqrt(dist2);
               float nx = dx / dist;
               float nz = dz / dist;
               float relative = (b.vx - a.vx) * nx + (b.vz - a.vz) * nz;
               if (!(relative >= 0.0F)) {
                  float correction = (reach - dist) * 0.35F;
                  a.x -= nx * correction;
                  a.z -= nz * correction;
                  b.x += nx * correction;
                  b.z += nz * correction;
                  float impact = -1.22F * relative * 0.5F;
                  a.vx -= impact * nx;
                  a.vz -= impact * nz;
                  b.vx += impact * nx;
                  b.vz += impact * nz;
                  if (!a.hit) {
                     impulse(a, -nx, -nz, Math.max(0.13F, impact * 1.35F));
                     state.pendingHits++;
                  }

                  if (!b.hit) {
                     impulse(b, nx, nz, Math.max(0.13F, impact * 1.35F));
                     state.pendingHits++;
                  }
               }
            }
         }
      }

      for (BowlingSplit.Pin pin : state.pins) {
         if (pin.hit) {
            pin.omega = pin.omega + 0.027F * (float)Math.sin(Math.min(1.56F, pin.angle + 0.04F));
         }

         pin.omega = pin.omega * (pin.hit ? 0.955F : 0.88F);
         pin.angle = Math.min(1.58F, pin.angle + pin.omega);
         if (pin.angle > 1.16F) {
            pin.x = pin.x + pin.vx;
            pin.z = pin.z + pin.vz;
            pin.vx *= 0.91F;
            pin.vz *= 0.91F;
         } else {
            pin.vx *= 0.72F;
            pin.vz *= 0.72F;
         }

         if (Math.abs(pin.vx) + Math.abs(pin.vz) < 0.001F) {
            pin.vx = 0.0F;
            pin.vz = 0.0F;
         }

         if (pin.angle > 1.55F) {
            pin.omega *= 0.62F;
         }
      }
   }

   private static void impulse(BowlingSplit.Pin pin, float dx, float dz, float strength) {
      pin.vx += dx * strength * 0.2F;
      pin.vz += dz * strength * 0.2F;
      pin.tipX = dx;
      pin.tipZ = dz;
      pin.omega = Math.max(pin.omega, 0.1F + strength * 0.28F);
      pin.hit = true;
   }

   private static void reset(BowlingSplit.State state) {
      state.simulatedTick = -1;
      state.pendingHits = 0;
      state.ballHit = false;
      state.ballImpactSounded = false;
      state.playerHit = false;

      for (BowlingSplit.Pin pin : state.pins) {
         pin.x = pin.homeX;
         pin.z = pin.homeZ;
         pin.vx = pin.vz = pin.angle = pin.omega = 0.0F;
         pin.tipX = 0.0F;
         pin.tipZ = 1.0F;
         pin.hit = false;
      }
   }

   private static float ballZ(int tick, float ticks) {
      float p = tick / ticks;
      float travel = AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.095F, 0.4F));
      return MathHelper.lerp(travel, -4.1F, 3.15F);
   }

   private static void updateBall(DeathScene scene, BowlingSplit.State state, float p) {
      float travel = AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.095F, 0.4F));
      float z = MathHelper.lerp(travel, -4.1F, 3.15F);
      float roll = travel * 7.25F / 0.23F;
      Quaternionf facing = new Quaternionf().rotationY((float)Math.toRadians(scene.sceneFacing));
      Quaternionf rotation = new Quaternionf().rotationX(roll);
      rotation.premul(facing);
      float visible = p < 0.46F ? 1.0F : 1.0E-4F;
      state.ball.setTransformation(new AffineTransformation(new Vector3f(), rotation, new Vector3f(0.46F * visible), new Quaternionf()));
      Vector3f pos = facing.transform(new Vector3f(0.0F, (float)(scene.floorY - scene.y) + 0.31F, z));
      place(state.ball, scene.x + pos.x, scene.y + pos.y, scene.z + pos.z);
   }

   private static void updatePin(DeathScene scene, BowlingSplit.Pin pin) {
      Quaternionf facing = new Quaternionf().rotationY((float)Math.toRadians(scene.sceneFacing));
      float horizontal = (float)Math.sin(pin.angle) * 1.08F * 0.47F;
      Vector3f axis = new Vector3f(pin.tipZ, 0.0F, -pin.tipX);
      if (axis.lengthSquared() < 0.01F) {
         axis.set(1.0F, 0.0F, 0.0F);
      }

      axis.normalize();
      Quaternionf tip = new Quaternionf().rotationAxis(pin.angle, axis.x, axis.y, axis.z);
      Quaternionf rotation = new Quaternionf(facing).mul(tip);
      Vector3f center = new Vector3f(
         pin.x + pin.tipX * horizontal,
         (float)(scene.floorY - scene.y) + 0.08F + 0.54F * (float)Math.cos(pin.angle) + 0.25F * (float)Math.sin(pin.angle),
         pin.z + pin.tipZ * horizontal
      );
      facing.transform(center);
      pin.display.setTransformation(new AffineTransformation(new Vector3f(), rotation, new Vector3f(0.52F, 1.08F, 0.52F), new Quaternionf()));
      place(pin.display, scene.x + center.x, scene.y + center.y, scene.z + center.z);
   }

   private static ItemDisplayEntity item(DeathScene scene, Consumer<Entity> add, String model) {
      ItemDisplayEntity display = (ItemDisplayEntity)EntityType.ITEM_DISPLAY.create(scene.level, SpawnReason.COMMAND);
      if (display == null) {
         throw new IllegalStateException("Could not create bowling prop");
      }

      display.setItemDisplayContext(ItemDisplayContext.FIXED);
      ItemStack stack = new ItemStack(Items.IRON_INGOT);
      stack.set(DataComponentTypes.ITEM_MODEL, Identifier.of("deathanimation", "mass/" + model));
      display.setItemStack(stack);
      display.setBrightness(new Brightness(15, 15));
      add.accept(display);
      return display;
   }

   private static TextDisplayEntity text(DeathScene scene, Consumer<Entity> add) {
      TextDisplayEntity display = (TextDisplayEntity)EntityType.TEXT_DISPLAY.create(scene.level, SpawnReason.COMMAND);
      if (display == null) {
         throw new IllegalStateException("Could not create bowling result text");
      }

      display.setText(Text.literal("STRIKE!").styled(style -> style.withColor(16767050).withBold(true)));
      display.setTextOpacity((byte)0);
      display.setBackground(0);
      display.setDisplayFlags((byte)1);
      display.setBillboardMode(BillboardMode.CENTER);
      add.accept(display);
      return display;
   }

   private static void updateFinish(DeathScene scene, BowlingSplit.State state, float p) {
      boolean show = state.playerHit && p >= 0.76F && p < 0.97F;
      state.finish.setTextOpacity((byte)(show ? 255 : 0));
      Quaternionf facing = new Quaternionf().rotationY((float)Math.toRadians(scene.sceneFacing));
      Vector3f pos = facing.transform(new Vector3f(0.0F, (float)(scene.floorY - scene.y) + 2.45F, 1.45F));
      place(state.finish, scene.x + pos.x, scene.y + pos.y, scene.z + pos.z);
   }

   private static void place(Entity entity, double x, double y, double z) {
      entity.setPosition(x, y, z);
      entity.lastX = entity.lastRenderX = entity.getX();
      entity.lastY = entity.lastRenderY = entity.getY();
      entity.lastZ = entity.lastRenderZ = entity.getZ();
      if (entity instanceof DisplayEntity display) {
         DisplayStateAccess access = (DisplayStateAccess)display;
         access.deathanimations$setRenderState(access.deathanimations$freshRenderState());
         access.deathanimations$refreshSubState(false, 1.0F);
         access.deathanimations$setDirty(false);
      }
   }

   static void discard(DeathScene scene) {
      scene.bowlingSplit = null;
   }

   @Environment(EnvType.CLIENT)
   private static final class Pin {
      final ItemDisplayEntity display;
      final float homeX;
      final float homeZ;
      float x;
      float z;
      float vx;
      float vz;
      float angle;
      float omega;
      float tipX;
      float tipZ;
      boolean hit;

      Pin(ItemDisplayEntity display, float x, float z) {
         this.display = display;
         this.homeX = x;
         this.homeZ = z;
         this.x = x;
         this.z = z;
      }
   }

   @Environment(EnvType.CLIENT)
   static final class State {
      final BowlingSplit.Pin[] pins = new BowlingSplit.Pin[10];
      ItemDisplayEntity ball;
      TextDisplayEntity finish;
      int simulatedTick = -1;
      int pendingHits;
      float lastSoundAge = -100.0F;
      boolean ballHit;
      boolean ballImpactSounded;
      boolean playerHit;
   }
}
