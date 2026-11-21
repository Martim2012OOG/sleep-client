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
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class KirbyInhaleStarSpit {
   private static final float SWALLOW = 0.57F;
   private static final float SPIT = 0.7F;
   private static final float LAUNCH = 0.77F;

   private KirbyInhaleStarSpit() {
   }

   public static DeathScene.Root root(DeathScene scene) {
      float p = scene.progress();
      float floor = (float)(scene.floorY - scene.y);
      float pull = smooth(phase(p, 0.18F, 0.53F));
      float swallowed = smooth(phase(p, 0.52F, 0.57F));
      float launch = smooth(phase(p, 0.77F, 0.95F));
      float x = -0.72F * pull;
      float y = floor + 0.02F + 0.23F * smooth(phase(p, 0.25F, 0.42F)) - 0.03F * swallowed;
      float scale = 1.0F - 0.68F * smooth(phase(p, 0.31F, 0.52F)) - 0.22F * swallowed;
      if (p >= 0.57F) {
         x = -0.79F;
         y = floor + 0.22F;
         scale = 0.1F;
      }

      if (p >= 0.7F) {
         float t = smooth(phase(p, 0.57F, 0.77F));
         x = -0.79F + 0.5F * t;
         y = floor + 0.22F + 0.54F * t;
         scale = 0.1F + 0.1F * t;
      }

      if (p >= 0.77F) {
         float t = phase(p, 0.77F, 0.98F);
         x = -0.29F + 8.2F * t;
         y = floor + 0.76F + 0.08F * (float)Math.sin(t * 3.141592653589793 * 2.0);
         scale = 0.2F;
      }

      float struggle = (1.0F - pull) * phase(p, 0.12F, 0.22F) + pull * (1.0F - smooth(phase(p, 0.46F, 0.54F)));
      float wobble = (float)Math.sin(scene.age * 0.58F) * struggle * 7.0F;
      Vector3f position = world(scene, x, y, 0.0F);
      return new DeathScene.Root(
         position.x, position.y, position.z, 12.0F * struggle + wobble, scene.sceneFacing + 90.0F * pull, -8.0F * struggle, scale, scale, scale
      );
   }

   public static DeathBodyRig.Motion pose(DeathScene scene, int part) {
      float p = scene.progress();
      float suction = smooth(phase(p, 0.19F, 0.51F));
      float swallow = smooth(phase(p, 0.49F, 0.57F));
      float side = part != 2 && part != 4 ? 1.0F : -1.0F;
      float flap = (float)Math.sin(scene.age * 0.82F + part * 1.9F);
      float struggle = 1.0F - smooth(phase(p, 0.43F, 0.54F));
      float x = 0.0F;
      float y = 0.0F;
      float rx = 0.0F;
      float ry = 0.0F;
      float rz = 0.0F;
      if (part == 0) {
         rx = -0.22F * suction;
         ry = 0.2F * flap * struggle;
      }

      if (part == 1) {
         rx = 0.2F * suction;
      }

      if (part == 2 || part == 3) {
         rx = -0.35F * suction + 0.55F * flap * struggle;
         rz = side * (0.18F + 0.58F * suction) + side * 0.2F * flap * struggle;
         x = side * 0.07F * suction;
      }

      if (part == 4 || part == 5) {
         rx = 0.24F * suction - 0.44F * flap * struggle;
         rz = side * 0.12F * suction;
      }

      float hidden = swallow;
      x *= 1.0F - hidden;
      y -= 0.04F * suction;
      if (p >= 0.7F) {
         rx *= 0.35F;
         rz *= 0.35F;
      }

      return new DeathBodyRig.Motion(false, x, y, 0.0F, rx, ry, rz);
   }

   public static void spawn(DeathScene scene, Consumer<Entity> add) {
      if (scene.kirbyInhale == null) {
         KirbyInhaleStarSpit.State s = new KirbyInhaleStarSpit.State();
         scene.kirbyInhale = s;
         s.kirby = display(scene, add, "kirby_closed");
         s.star = display(scene, add, "kirby_star");
         animate(scene);
      }
   }

   private static ItemDisplayEntity display(DeathScene scene, Consumer<Entity> add, String model) {
      ItemDisplayEntity d = (ItemDisplayEntity)EntityType.ITEM_DISPLAY.create(scene.level, SpawnReason.COMMAND);
      if (d == null) {
         throw new IllegalStateException("Could not create Kirby display: " + model);
      }

      d.setItemDisplayContext(ItemDisplayContext.FIXED);
      ItemStack stack = new ItemStack(Items.PAPER);
      stack.set(DataComponentTypes.ITEM_MODEL, Identifier.of("deathanimation", "kirby/" + model));
      d.setItemStack(stack);
      d.setBrightness(new Brightness(15, 15));
      add.accept(d);
      return d;
   }

   public static void animate(DeathScene scene) {
      KirbyInhaleStarSpit.State s = scene.kirbyInhale;
      if (s != null) {
         float p = scene.progress();
         float floor = (float)(scene.floorY - scene.y);
         float inhale = smooth(phase(p, 0.16F, 0.32F)) * (1.0F - smooth(phase(p, 0.51F, 0.57F)));
         float puff = smooth(phase(p, 0.55F, 0.62F)) * (1.0F - smooth(phase(p, 0.67F, 0.71F)));
         float charge = smooth(phase(p, 0.62F, 0.7F));
         float recoil = smooth(phase(p, 0.7F, 0.78F)) * (1.0F - smooth(phase(p, 0.82F, 0.88F)));
         float wobble = (float)Math.sin(scene.age * 0.48F) * (puff * 0.035F + inhale * 0.018F);
         boolean open = p >= 0.18F && p < 0.57F || p >= 0.66F && p < 0.84F;
         if (open != s.open) {
            s.open = open;
            setModel(s.kirby, open ? "kirby_open" : "kirby_closed");
         }

         float kx = -1.18F + 0.02F * (float)Math.sin(scene.age * 0.12F) * inhale;
         float ky = floor + 0.66F + wobble;
         float kScale = 1.18F + inhale * 0.04F + puff * 0.17F + charge * 0.025F - recoil * 0.08F;
         set(
            scene,
            s.kirby,
            kx,
            ky,
            0.0F,
            0.0F,
            scene.sceneFacing + 90.0F + recoil * 7.0F,
            (float)Math.sin(scene.age * 0.4F) * inhale * 3.0F,
            kScale * (1.0F + inhale * 0.05F - puff * 0.04F),
            kScale * (1.0F - inhale * 0.035F + puff * 0.13F),
            kScale
         );
         float flight = phase(p, 0.77F, 0.96F);
         float starOn = smooth(phase(p, 0.67F, 0.71F)) * (1.0F - smooth(phase(p, 0.93F, 0.99F)));
         float sx = -0.2F + 8.2F * flight;
         float sy = floor + 1.12F + 0.08F * (float)Math.sin(flight * 3.141592653589793 * 2.0);
         set(
            scene,
            s.star,
            sx,
            sy,
            0.0F,
            0.0F,
            scene.sceneFacing + 90.0F,
            flight * 1080.0F,
            (0.06F + 0.95F * smooth(phase(p, 0.69F, 0.77F))) * starOn,
            (0.06F + 0.95F * smooth(phase(p, 0.69F, 0.77F))) * starOn,
            0.34F * starOn
         );
         if (DeathAnimationConfig.INSTANCE.visualEffects && !scene.gui) {
            int tick = (int)scene.age;
            if (tick != s.particleTick && tick % 2 == 0) {
               s.particleTick = tick;
               if (p > 0.18F && p < 0.57F) {
                  suctionParticles(scene, p);
               }

               if (p >= 0.77F && p < 0.96F) {
                  Vector3f pos = world(scene, sx, sy - floor, 0.0F);
                  EffectParticles.add(scene, ParticleTypes.END_ROD, scene.x + pos.x, scene.floorY + pos.y, scene.z + pos.z, 0.02, 0.01, 0.0);
                  if (tick % 4 == 0) {
                     EffectParticles.add(scene, ParticleTypes.FIREWORK, scene.x + pos.x, scene.floorY + pos.y, scene.z + pos.z, -0.06, 0.02, 0.0);
                  }
               }
            }
         }
      }
   }

   private static void suctionParticles(DeathScene scene, float p) {
      float t = phase(p, 0.18F, 0.57F);
      double x = 0.55 - 0.9 * t;
      double z = (Math.floorMod((int)scene.age * 23, 7) - 3) * 0.12;
      Vector3f pos = world(scene, (float)x, 1.05F, (float)z);
      Vector3f vel = world(scene, -0.1F, -0.01F, (float)(-z) * 0.08F);
      EffectParticles.add(scene, ParticleTypes.CLOUD, scene.x + pos.x, scene.floorY + pos.y, scene.z + pos.z, vel.x, vel.y, vel.z);
   }

   public static void effects(DeathScene scene) {
      KirbyInhaleStarSpit.State s = scene.kirbyInhale;
      if (s != null) {
         sound(scene, s, 1, 0.17F, "entity.breeze.wind_burst", 0.45F, 0.72F);
         sound(scene, s, 2, 0.53F, "entity.generic.eat", 0.8F, 1.05F);
         sound(scene, s, 4, 0.65F, "entity.player.burp", 0.55F, 1.2F);
         sound(scene, s, 8, 0.71F, "entity.firework_rocket.launch", 0.8F, 0.72F);
         sound(scene, s, 16, 0.95F, "entity.firework_rocket.blast", 0.55F, 1.15F);
      }
   }

   private static void sound(DeathScene scene, KirbyInhaleStarSpit.State s, int bit, float at, String id, float volume, float pitch) {
      if (!(scene.progress() < at) && (s.sounds & bit) == 0) {
         s.sounds |= bit;
         if (DeathAnimationConfig.INSTANCE.sounds) {
            SoundEvent event = (SoundEvent)Registries.SOUND_EVENT.get(Identifier.of(id));
            if (event != null) {
               scene.level
                  .playSoundClient(scene.x, scene.floorY + 1.0, scene.z, event, SoundCategory.PLAYERS, scene.gui ? volume * 0.25F : volume, pitch, false);
            }
         }
      }
   }

   private static void setModel(ItemDisplayEntity d, String model) {
      ItemStack stack = d.getItemStack().copy();
      stack.set(DataComponentTypes.ITEM_MODEL, Identifier.of("deathanimation", "kirby/" + model));
      d.setItemStack(stack);
      refresh(d);
   }

   private static void set(DeathScene scene, ItemDisplayEntity d, float x, float y, float z, float pitch, float yaw, float roll, float sx, float sy, float sz) {
      Vector3f pos = world(scene, x, y - (float)(scene.floorY - scene.y), z);
      Quaternionf rotation = new Quaternionf().rotationY((float)Math.toRadians(yaw)).rotateX((float)Math.toRadians(pitch)).rotateZ((float)Math.toRadians(roll));
      d.setTransformation(new AffineTransformation(new Vector3f(), rotation, new Vector3f(sx, sy, sz), new Quaternionf()));
      d.setPosition(scene.x + pos.x, scene.floorY + pos.y, scene.z + pos.z);
      refresh(d);
   }

   private static void refresh(DisplayEntity d) {
      d.lastX = d.lastRenderX = d.getX();
      d.lastY = d.lastRenderY = d.getY();
      d.lastZ = d.lastRenderZ = d.getZ();
      DisplayStateAccess access = (DisplayStateAccess)d;
      access.deathanimations$setRenderState(access.deathanimations$freshRenderState());
      access.deathanimations$refreshSubState(false, 1.0F);
      access.deathanimations$setDirty(false);
   }

   private static Vector3f world(DeathScene scene, float x, float y, float z) {
      return new Quaternionf().rotationY((float)Math.toRadians(scene.sceneFacing)).transform(new Vector3f(x, y, z));
   }

   private static float smooth(float p) {
      p = MathHelper.clamp(p, 0.0F, 1.0F);
      return p * p * (3.0F - 2.0F * p);
   }

   private static float phase(float p, float a, float b) {
      return MathHelper.clamp((p - a) / (b - a), 0.0F, 1.0F);
   }

   public static void discard(DeathScene scene) {
      scene.kirbyInhale = null;
   }

   @Environment(EnvType.CLIENT)
   static final class State {
      ItemDisplayEntity kirby;
      ItemDisplayEntity star;
      boolean open;
      int sounds;
      int particleTick = -1;
   }
}
