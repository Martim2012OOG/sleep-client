package com.baom5.deathanimation.client;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class DeathEffectController {
   private static final Map<Integer, DeathScene> SCENES = new LinkedHashMap<>();
   private static final Map<Integer, UUID> HIDDEN_ORIGINALS = new HashMap<>();
   private static final Map<Integer, DeathScene> NO_MOTION = new HashMap<>();
   private static final AtomicInteger IDS = new AtomicInteger(-1000000);
   private static ClientWorld observedLevel;
   private static final Random RANDOM = new Random();
   private static List<DeathAnimationConfig.Animation> previewSequence = List.of();
   private static int previewTick = -1;
   private static int previewStep;
   private static int tourSceneElapsedTicks;
   private static DeathScene tourScene;
   private static DeathScene hoverScene;
   private static DeathAnimationConfig.Animation hoverPreview;
   private static int hoverPreviewTicks;

   private DeathEffectController() {
   }

   public static void register() {
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> tick(client.world));
   }

   private static void tick(ClientWorld level) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (level != observedLevel) {
         clearWorld();
         HIDDEN_ORIGINALS.clear();
         NO_MOTION.clear();
         MotionHistory.clear();
         AttackerTracker.changeLevel(level);
         hoverScene = null;
         hoverPreview = null;
         previewTick = -1;
         observedLevel = level;
      }

      if (level == null) {
         PlayerDeathDetection.tick(null);
      } else if (!client.isPaused()) {
         DeathAnimationConfig config = DeathAnimationConfig.INSTANCE;
         if (hoverScene != null) {
            hoverPreviewTicks++;
            if (hoverScene.shellGame() != null) {
               hoverScene.shellGame().tickPreview(config.animationSpeed);
            } else {
               int loop = AnimationTimeline.ticks(hoverPreview) + 20;
               float next = hoverPreviewTicks * config.animationSpeed % loop;
               if (next < hoverScene.age) {
                  hoverScene.emitted = 0;
                  hoverScene.sounded = 0;
               }

               hoverScene.age = next;
            }

            animateProps(hoverScene);
            if (hoverPreviewTicks >= 8) {
               effects(hoverScene);
            }

            hoverScene.props.removeIf(Entity::isRemoved);

            for (Entity prop : hoverScene.props) {
               prop.tick();
            }
         }

         if (!config.enabled) {
            for (DeathScene scene : List.copyOf(SCENES.values())) {
               if (!scene.preview) {
                  scene.discard();
                  SCENES.remove(scene.avatar.getId());
               }
            }

            HIDDEN_ORIGINALS.clear();
            NO_MOTION.clear();
         }

         PlayerDeathDetection.tick(level);
         HIDDEN_ORIGINALS.entrySet()
            .removeIf(
               entry -> level.getEntityById(entry.getKey()) == null
                  || !entry.getValue().equals(level.getEntityById(entry.getKey()).getUuid())
                  || level.getEntityById(entry.getKey()) instanceof LivingEntity living && !living.isDead()
            );
         NO_MOTION.entrySet()
            .removeIf(
               entry -> level.getEntityById(entry.getKey()) == null
                  || !entry.getValue().sourceUuid.equals(level.getEntityById(entry.getKey()).getUuid())
                  || level.getEntityById(entry.getKey()) instanceof LivingEntity living && !living.isDead()
            );

         for (DeathScene scene : List.copyOf(SCENES.values())) {
            scene.tick();
            if (scene.expired()) {
               scene.discard();
               SCENES.remove(scene.avatar.getId());
            } else {
               animateProps(scene);
               effects(scene);
            }
         }

         List<DeathScene> active = List.copyOf(SCENES.values());

         for (int i = 0; i < active.size(); i++) {
            for (int j = i + 1; j < active.size(); j++) {
               for (BodyFragment a : active.get(i).pieces()) {
                  for (BodyFragment b : active.get(j).pieces()) {
                     if (a.position.squaredDistanceTo(b.position) < 4.0) {
                        BodyFragment.collidePair(a, b, level, false, Math.min(active.get(i).floorY, active.get(j).floorY));
                     }
                  }
               }
            }
         }

         tickTour(level);
      }
   }

   static boolean playDetectedDeath(ClientWorld level, AbstractClientPlayerEntity source, Vec3d position) {
      DeathAnimationConfig config = DeathAnimationConfig.INSTANCE;
      List<DeathAnimationConfig.Animation> enabled = ReleaseProfile.animations().stream().filter(config::isEnabled).toList();
      if (config.enabled && !enabled.isEmpty()) {
         DeathAnimationConfig.Animation style = enabled.get(config.randomMode ? RANDOM.nextInt(enabled.size()) : 0);
         if (config.deathDetectionDebug || Boolean.getBoolean("deathanimation.deathDebug")) {
            LoggerFactory.getLogger("deathanimation/death-detection")
               .info(
                  "animationSelected uuid={} style={} noCustomMovement={}", new Object[]{source.getUuid(), style, style == DeathAnimationConfig.Animation.NONE}
               );
         }

         if (config.removePrevious) {
            clearWorld();
         }

         DeathScene scene = create(level, source, style, false, false, position);
         if (style == DeathAnimationConfig.Animation.NONE) {
            NO_MOTION.put(source.getId(), scene);
         } else {
            HIDDEN_ORIGINALS.put(source.getId(), source.getUuid());
         }

         return true;
      } else {
         return false;
      }
   }

   private static DeathScene create(
      ClientWorld level, AbstractClientPlayerEntity source, DeathAnimationConfig.Animation animation, boolean preview, boolean gui, Vec3d origin
   ) {
      DeathScene scene = new DeathScene(level, source, animation, IDS.getAndDecrement(), preview, gui, origin);
      if (gui) {
         spawnProps(scene);
         return scene;
      }

      int limit = Math.clamp(DeathAnimationConfig.INSTANCE.maxPersistentEffects, 1, 24);

      while (SCENES.size() >= limit) {
         Iterator<Entry<Integer, DeathScene>> first = SCENES.entrySet().iterator();
         Entry<Integer, DeathScene> old = first.next();
         old.getValue().discard();
         first.remove();
      }

      SCENES.put(scene.avatar.getId(), scene);
      if (animation != DeathAnimationConfig.Animation.NONE || preview) {
         level.addEntity(scene.avatar);
      }

      spawnProps(scene);
      return scene;
   }

   public static DeathScene sceneFor(LivingEntityRenderState state) {
      if (state instanceof PlayerEntityRenderState avatar) {
         if (hoverScene != null && hoverScene.avatar.getId() == avatar.id) {
            return hoverScene;
         } else {
            DeathScene scene = SCENES.get(avatar.id);
            if (scene != null && scene.preview) {
               return scene;
            } else if (!DeathAnimationConfig.INSTANCE.enabled) {
               return null;
            } else {
               return scene != null ? scene : NO_MOTION.get(avatar.id);
            }
         }
      } else {
         return null;
      }
   }

   public static DeathScene sceneForAvatar(DeathAvatar avatar) {
      return hoverScene != null && hoverScene.avatar == avatar ? hoverScene : SCENES.get(avatar.getId());
   }

   public static boolean hidesOriginal(LivingEntityRenderState state) {
      return DeathAnimationConfig.INSTANCE.enabled && state instanceof PlayerEntityRenderState avatar && HIDDEN_ORIGINALS.containsKey(avatar.id);
   }

   public static DeathAvatar previewAvatar() {
      return hoverScene == null ? null : hoverScene.avatar;
   }

   public static DeathScene hoverScene() {
      return hoverScene;
   }

   public static Collection<DeathScene> scenes() {
      return List.copyOf(SCENES.values());
   }

   public static boolean interactSand(AbstractClientPlayerEntity player) {
      for (DeathScene scene : SCENES.values()) {
         if (scene.dustfallSand != null && scene.dustfallSand.interact(scene, player)) {
            return true;
         }
      }

      return false;
   }

   public static boolean claimClawPrize(AbstractClientPlayerEntity player) {
      for (DeathScene scene : SCENES.values()) {
         if (ClawMachinePrizeDrop.claim(scene, player)) {
            return true;
         }
      }

      return false;
   }

   public static boolean interactGold(AbstractClientPlayerEntity player) {
      for (DeathScene scene : SCENES.values()) {
         if (scene.gold() != null && scene.gold().interact(player)) {
            return true;
         }
      }

      return false;
   }

   public static boolean interactAcrobatics(AbstractClientPlayerEntity player) {
      for (DeathScene scene : List.copyOf(SCENES.values())) {
         if (scene.interactAcrobatics(player)) {
            return true;
         }
      }

      return false;
   }

   public static boolean interactShellGame(AbstractClientPlayerEntity player) {
      ShellGame.CupHit hit = shellGameHit(player);
      return hit != null && hit.game().choose(hit.slot());
   }

   private static ShellGame.CupHit shellGameHit(AbstractClientPlayerEntity player) {
      ShellGame.CupHit nearest = null;

      for (DeathScene scene : SCENES.values()) {
         if (scene.shellGame() != null) {
            ShellGame.CupHit hit = scene.shellGame().pick(player);
            if (hit != null && (nearest == null || hit.distanceSquared() < nearest.distanceSquared())) {
               nearest = hit;
            }
         }
      }

      return nearest;
   }

   public static boolean handleShellGameInput(MinecraftClient client) {
      if (client.player != null && client.world != null && client.currentScreen == null && client.mouse.isCursorLocked()) {
         ShellGame.CupHit hit = shellGameHit(client.player);
         if (hit != null && client.options.attackKey.wasPressed()) {
            while (client.options.attackKey.wasPressed()) {
            }

            hit.game().choose(hit.slot());
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean chooseShellGamePreview(int slot) {
      return hoverScene != null && hoverScene.shellGame() != null && hoverScene.shellGame().choosePreview(slot);
   }

   public static void setHoverPreview(DeathAnimationConfig.Animation animation) {
      setHoverPreview(animation, MinecraftClient.getInstance().player);
   }

   static void setHoverPreview(DeathAnimationConfig.Animation animation, AbstractClientPlayerEntity source) {
      if (animation != null && !ReleaseProfile.allows(animation)) {
         animation = null;
      }

      MinecraftClient client = MinecraftClient.getInstance();
      if (animation != hoverPreview || animation != null && hoverScene == null) {
         if (hoverScene != null) {
            hoverScene.discard();
         }

         hoverPreview = animation;
         hoverScene = null;
         hoverPreviewTicks = 0;
         if (animation != null && source != null && client.world != null) {
            hoverScene = create(client.world, source, animation, true, true, source.getEntityPos());
         }
      }
   }

   public static float previewProgress() {
      return hoverScene != null ? hoverScene.progress() : (tourScene != null ? tourScene.progress() : 0.0F);
   }

   public static void playPreview(DeathAnimationConfig.Animation style) {
      if (ReleaseProfile.allows(style)) {
         discardTourScene();
         previewSequence = List.of(style);
         previewStep = 0;
         previewTick = 0;
         tourScene = null;
         tourSceneElapsedTicks = 0;
         setHoverPreview(null);
      }
   }

   public static void startPreview() {
      discardTourScene();
      previewSequence = ReleaseProfile.animations().stream().filter(animation -> animation != DeathAnimationConfig.Animation.NONE).toList();
      if (!previewSequence.isEmpty()) {
         previewStep = 0;
         previewTick = 0;
         tourScene = null;
         tourSceneElapsedTicks = 0;
         setHoverPreview(null);
      }
   }

   private static void tickTour(ClientWorld level) {
      if (previewTick >= 0 && MinecraftClient.getInstance().player != null) {
         boolean timedOut = tourScene != null && tourSceneElapsedTicks >= previewWatchdog(tourScene);
         boolean animationFinished = tourScene != null
            && (
               (
                     tourScene.clawMachine != null || tourScene.dustfallSand != null && tourScene.dustfallSand.sandScene()
                        ? tourScene.expired()
                        : tourScene.age >= AnimationTimeline.ticks(tourScene.animation) + 20
                  )
                  || tourScene.shellGame() != null && tourScene.shellGame().finished()
            );
         if (tourScene == null || animationFinished || timedOut) {
            if (tourScene != null) {
               tourScene.discard();
               SCENES.remove(tourScene.avatar.getId());
               tourScene = null;
               previewStep++;
               tourSceneElapsedTicks = 0;
            }

            if (previewStep >= previewSequence.size()) {
               previewTick = -1;
               return;
            }

            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            Vec3d direction = new Vec3d(player.getRotationVector().x, 0.0, player.getRotationVector().z).normalize();
            Vec3d ahead = player.getEntityPos().add(direction.multiply(3.5));
            double ground = BodyFragment.support(level, ahead, 4.0, player.getY());
            DeathAnimationConfig.Animation animation = previewSequence.get(previewStep);

            try {
               tourScene = create(level, player, animation, true, false, new Vec3d(ahead.x, ground, ahead.z));
            } catch (RuntimeException exception) {
               SCENES.values().stream().filter(scene -> scene.preview && scene.animation == animation).toList().forEach(scene -> {
                  scene.discard();
                  SCENES.remove(scene.avatar.getId());
               });
               player.sendMessage(Text.literal("Preview failed; skipping: " + animation.label), true);
               previewStep++;
               previewTick++;
               return;
            }

            tourSceneElapsedTicks = 0;
            player.sendMessage(Text.literal("Preview " + (previewStep + 1) + "/" + previewSequence.size() + ": " + tourScene.animation.label), true);
         }

         if (tourScene != null) {
            tourSceneElapsedTicks++;
         }

         previewTick++;
      }
   }

   private static int previewWatchdog(DeathScene scene) {
      DeathAnimationConfig.Animation animation = scene.animation;
      float speed = Math.max(0.05F, DeathAnimationConfig.INSTANCE.animationSpeed);
      if (scene.shellGame() != null) {
         return scene.shellGame().previewWatchdog(speed);
      }

      int hold = scene.dustfallSand != null && scene.dustfallSand.sandScene()
         ? Math.max(600, DeathAnimationConfig.INSTANCE.durationSeconds * 20)
         : (scene.clawMachine != null ? Math.max(300, DeathAnimationConfig.INSTANCE.durationSeconds * 20) : 20);
      double duration = Math.ceil((AnimationTimeline.ticks(animation) + hold) / speed) + (hold > 20 ? hold : 0) + 100.0;
      return (int)Math.min(7200.0, Math.max(200.0, duration));
   }

   private static void discardTourScene() {
      if (tourScene != null) {
         tourScene.discard();
         SCENES.remove(tourScene.avatar.getId());
         tourScene = null;
      }
   }

   public static void stopPreview() {
      previewTick = -1;
      previewSequence = List.of();
      tourSceneElapsedTicks = 0;
      setHoverPreview(null);

      for (DeathScene scene : List.copyOf(SCENES.values())) {
         if (scene.preview) {
            scene.discard();
            SCENES.remove(scene.avatar.getId());
         }
      }

      tourScene = null;
   }

   private static void clearWorld() {
      PlayerPrizeActivation.clear();
      EffectParticles.clear();

      for (DeathScene scene : SCENES.values()) {
         scene.discard();
      }

      SCENES.clear();
      tourScene = null;
      previewTick = -1;
      tourSceneElapsedTicks = 0;
   }

   private static void spawnProps(DeathScene scene) {
      if (scene.dustfallSand != null && scene.dustfallSand.sandScene()) {
         scene.dustfallSand.spawn(scene, entity -> local(scene, entity));
      } else if (AnimationCatalog.contains(scene.animation)) {
         AnimationCatalog.spawn(scene, entity -> local(scene, entity));
      } else {
         switch (scene.animation) {
            case GRAVE:
               ItemStack marker = new ItemStack(Items.PAPER);
               marker.set(DataComponentTypes.ITEM_MODEL, Identifier.of("deathanimation", "grave_marker"));
               item(scene, marker, scene.x, scene.floorY - 1.35, scene.z, 1.0F);
               if (!scene.props.isEmpty() && scene.props.getLast() instanceof ItemDisplayEntity display) {
                  display.setItemDisplayContext(ItemDisplayContext.FIXED);
                  display.setTransformation(
                     new AffineTransformation(new Vector3f(), new Quaternionf().rotationY(scene.gui ? 3.1415927F : 0.0F), new Vector3f(1.0F), new Quaternionf())
                  );
               }
               break;
            case ANVIL:
               block(scene, Blocks.ANVIL.getDefaultState(), scene.x - 0.5, scene.y + 3.5, scene.z - 0.5, 1.0F, 1.0F, 1.0F);
               break;
            case FREEZE:
               block(scene, Blocks.ICE.getDefaultState(), scene.x - 0.42, scene.floorY, scene.z - 0.42, 0.1F, 0.01F, 0.84F);
               block(scene, Blocks.ICE.getDefaultState(), scene.x + 0.32, scene.floorY, scene.z - 0.42, 0.1F, 0.01F, 0.84F);
               block(scene, Blocks.ICE.getDefaultState(), scene.x - 0.42, scene.floorY, scene.z - 0.42, 0.84F, 0.01F, 0.1F);
               block(scene, Blocks.ICE.getDefaultState(), scene.x - 0.42, scene.floorY, scene.z + 0.32, 0.84F, 0.01F, 0.1F);
               block(scene, Blocks.ICE.getDefaultState(), scene.x - 0.42, scene.floorY, scene.z - 0.42, 0.84F, 0.1F, 0.84F);
               break;
            case TOTEM:
               item(scene, new ItemStack(Items.TOTEM_OF_UNDYING), scene.x, scene.y + 1.0, scene.z, 0.001F);
         }
      }
   }

   private static void animateProps(DeathScene scene) {
      if (scene.dustfallSand != null && scene.dustfallSand.sandScene()) {
         scene.dustfallSand.animate(scene);
      } else {
         float p = scene.progress();
         if (AnimationCatalog.contains(scene.animation)) {
            AnimationCatalog.animate(scene);
         } else {
            switch (scene.animation) {
               case GRAVE:
                  if (!scene.props.isEmpty()) {
                     scene.props
                        .getFirst()
                        .setPosition(scene.x, scene.floorY - 1.35 * (1.0F - AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.08F, 0.58F))), scene.z);
                  }
                  break;
               case ANVIL:
                  if (!scene.props.isEmpty()) {
                     scene.props
                        .getFirst()
                        .setPosition(
                           scene.x - 0.5,
                           scene.floorY + AnimationTimeline.track(p, 0.0F, 3.5F, 0.12F, 3.5F, 0.34F, 1.78F, 0.44F, 0.13F, 0.52F, 0.25F, 0.68F, 0.15F),
                           scene.z - 0.5
                        );
                  }
                  break;
               case FREEZE:
                  float height = 1.95F * AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.08F, 0.7F));

                  for (int i = 0; i < scene.props.size(); i++) {
                     BlockDisplayEntity prop = (BlockDisplayEntity)scene.props.get(i);
                     if (i < 4) {
                        prop.setTransformation(transform(i < 2 ? 0.1F : 0.84F, Math.max(0.01F, height), i < 2 ? 0.84F : 0.1F));
                     } else {
                        prop.setPosition(scene.x - 0.42, scene.floorY + height, scene.z - 0.42);
                     }
                  }
                  break;
               case TOTEM:
                  if (!scene.props.isEmpty() && scene.props.getFirst() instanceof ItemDisplayEntity prop) {
                     float size = 0.65F * AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.5F, 0.85F));
                     prop.setTransformation(transform(Math.max(0.001F, size), Math.max(0.001F, size), Math.max(0.001F, size)));
                     prop.setPosition(scene.x, scene.y + 0.8 + 0.25 * Math.sin(scene.age * 0.1), scene.z);
                     prop.setYaw(scene.age * 3.0F);
                  }
            }
         }
      }
   }

   private static void effects(DeathScene scene) {
      if (scene.dustfallSand != null && scene.dustfallSand.sandScene()) {
         scene.dustfallSand.effects(scene);
      } else if (scene.animation == DeathAnimationConfig.Animation.BATCH01_BANK_SHOT) {
         BankShot.effects(scene);
      } else if (scene.animation == DeathAnimationConfig.Animation.FULL_0118_INHALE_AND_STAR_SPIT) {
         KirbyInhaleStarSpit.effects(scene);
      } else if (scene.animation == DeathAnimationConfig.Animation.FULL_1322_PHOTOCOPIER_TONER_TRANSFER) {
         PhotocopierMassProduction.effects(scene);
      } else if (scene.animation == DeathAnimationConfig.Animation.FULL_1331_PAPER_SHREDDER_STRIP) {
         PaperShredderStrip.effects(scene);
      } else if (scene.animation == DeathAnimationConfig.Animation.FULL_1323_WASHING_MACHINE_SPIN_CYCLE) {
         WashingMachineCycle.effects(scene);
      } else if (scene.animation == DeathAnimationConfig.Animation.FULL_0899_DOMINO_BRIDGE_COLLAPSE) {
         DominoBridgeCollapse.effects(scene);
      } else if (scene.animation == DeathAnimationConfig.Animation.FULL_0905_CLAW_MACHINE_PRIZE_DROP) {
         ClawMachinePrizeDrop.effects(scene);
      } else if (scene.animation == DeathAnimationConfig.Animation.FULL_1321_PAPER_AIRPLANE_FOLD) {
         PaperAirplaneFold.effects(scene);
      } else if (scene.animation == DeathAnimationConfig.Animation.FULL_1338_PLAYER_DEFLATE) {
         PlayerDeflate.effects(scene);
      } else {
         if (scene.animation == DeathAnimationConfig.Animation.BATCH01_KI_RECOIL) {
            kamehamehaEffects(scene);
         }

         if (scene.animation == DeathAnimationConfig.Animation.BATCH01_BULLET_BILL) {
            bulletEffects(scene);
         } else if (HangingRagdoll.supports(scene.animation)) {
            HangingRagdoll physics = scene.hanging();
            HangingRagdoll.SoundImpact hit = physics == null ? null : physics.pollImpact();
            if (hit != null && DeathAnimationConfig.INSTANCE.sounds) {
               SoundEvent sound = (SoundEvent)Registries.SOUND_EVENT.get(Identifier.ofVanilla("block.note_block.bell"));
               float volume = scene.gui ? 0.16F : (float)Math.clamp(0.25 + hit.speed() * 1.8, 0.25, 0.85);
               Vec3d point = hit.point();
               scene.level.playSoundClient(point.x, point.y, point.z, sound, SoundCategory.PLAYERS, volume, 1.0F, false);
            }
         } else if (scene.animation != DeathAnimationConfig.Animation.RAGDOLL
            && scene.animation != DeathAnimationConfig.Animation.NONE
            && (
               !scene.isJointed()
                  || scene.animation == DeathAnimationConfig.Animation.LIGHTNING
                  || scene.animation == DeathAnimationConfig.Animation.OPPONENT
                  || scene.animation == DeathAnimationConfig.Animation.BATCH01_BANK_SHOT
                  || scene.animation == DeathAnimationConfig.Animation.BATCH01_FREDDY_SHUTDOWN
            )) {
            float p = scene.progress();
            if (scene.animation != DeathAnimationConfig.Animation.TORNADO && scene.animation != DeathAnimationConfig.Animation.WHIRLPOOL) {
               if (scene.animation == DeathAnimationConfig.Animation.BATCH01_DRONE_ELIMINATION
                  && !scene.gui
                  && DeathAnimationConfig.INSTANCE.visualEffects
                  && p > 0.28F
                  && p < 0.78F
                  && (int)scene.age % 2 == 0) {
                  for (int i = 0; i < 8; i++) {
                     double angle = scene.age * 0.8 + i * 3.141592653589793 * 0.25;
                     Vector3f origin = FortniteElimination.dissolvingPart(scene, i);
                     Vector3f velocity = FortniteElimination.intake(scene).sub(origin);
                     if (velocity.lengthSquared() < 1.0E-6F) {
                        velocity.set(0.0F, 0.12F, 0.0F);
                     } else {
                        velocity.normalize().mul(0.12F);
                     }

                     Particle mote = EffectParticles.create(
                        new DustParticleEffect(4575231, 0.8F),
                        scene.x + origin.x + Math.cos(angle) * 0.2,
                        scene.y + origin.y,
                        scene.z + origin.z + Math.sin(angle) * 0.2,
                        velocity.x,
                        velocity.y,
                        velocity.z
                     );
                     if (mote != null) {
                        mote.setMaxAge(13);
                     }

                     Particle spark = EffectParticles.create(
                        ParticleTypes.END_ROD,
                        scene.x + origin.x + Math.cos(angle) * 0.16,
                        scene.y + origin.y,
                        scene.z + origin.z + Math.sin(angle) * 0.16,
                        velocity.x * 0.6,
                        velocity.y * 0.6,
                        velocity.z * 0.6
                     );
                     if (spark != null) {
                        spark.setMaxAge(8);
                        spark.scale(0.35F);
                        if (spark instanceof BillboardParticle quad) {
                           quad.setColor(0.2F, 0.8F, 1.0F);
                        }
                     }
                  }
               }

               if (scene.animation == DeathAnimationConfig.Animation.EXPLOSION
                  && !scene.gui
                  && DeathAnimationConfig.INSTANCE.visualEffects
                  && p > 0.2F
                  && p < 0.72F
                  && (int)scene.age % 3 == 0) {
                  for (int i = 0; i < 3; i++) {
                     double angle = scene.age * 0.65 + i * 3.141592653589793 * 2.0 / 3.0;
                     double radius = 0.9 * (1.0F - AnimationTimeline.phase(p, 0.2F, 0.72F)) + 0.12;
                     EffectParticles.add(
                        scene,
                        ParticleTypes.END_ROD,
                        scene.x + Math.cos(angle) * radius,
                        scene.y + 0.65 + i * 0.25,
                        scene.z + Math.sin(angle) * radius,
                        -Math.cos(angle) * 0.03,
                        0.015,
                        -Math.sin(angle) * 0.03
                     );
                  }
               }

               if (AnimationCatalog.contains(scene.animation)) {
                  AnimationCatalog.Recipe recipe = AnimationCatalog.recipe(scene.animation);
                  if (recipe.events() == null) {
                     SoundEvent sound = switch (recipe.sound()) {
                        case "none" -> null;
                        case "glass" -> SoundEvents.BLOCK_GLASS_BREAK;
                        case "slime" -> SoundEvents.ENTITY_SLIME_JUMP;
                        case "metal" -> SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE;
                        case "magic" -> SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME;
                        default -> SoundEvents.BLOCK_WOOL_PLACE;
                     };

                     ParticleEffect particle = (ParticleEffect)(switch (recipe.particle()) {
                        case "snow" -> ParticleTypes.SNOWFLAKE;
                        case "flame" -> ParticleTypes.FLAME;
                        case "bubble" -> ParticleTypes.BUBBLE_POP;
                        case "portal" -> ParticleTypes.REVERSE_PORTAL;
                        case "glow" -> ParticleTypes.END_ROD;
                        case "note" -> ParticleTypes.NOTE;
                        case "sand" -> new BlockStateParticleEffect(ParticleTypes.FALLING_DUST, Blocks.SAND.getDefaultState());
                        default -> null;
                     });
                     event(scene, 0, 0.38F, sound, particle, 2);
                     if (particle != null && p > 0.2F && p < 0.85F && (int)scene.age % 16 == 0) {
                        particles(scene, particle, DeathAnimationConfig.INSTANCE.cinematicEffects ? 2 : 1, 0.4, 0.035);
                     }
                  } else {
                     for (int i = 0; i < Math.min(24, recipe.events().size()); i++) {
                        AnimationCatalog.EffectCue cue = recipe.events().get(i);
                        SoundEvent cueSound = cue.sound() != null && !cue.sound().equals("none")
                           ? (SoundEvent)Registries.SOUND_EVENT.get(Identifier.ofVanilla(cue.sound()))
                           : null;

                        ParticleEffect cueParticle = switch (cue.particle() == null ? "none" : cue.particle()) {
                           case "glow" -> ParticleTypes.END_ROD;
                           case "portal" -> ParticleTypes.REVERSE_PORTAL;
                           case "smoke" -> ParticleTypes.SMOKE;
                           case "electric" -> ParticleTypes.ELECTRIC_SPARK;
                           case "poof" -> ParticleTypes.POOF;
                           case "bubble" -> ParticleTypes.BUBBLE_POP;
                           case "note" -> ParticleTypes.NOTE;
                           default -> null;
                        };
                        event(scene, i, cue.p(), cueSound, cueParticle, Math.clamp(cue.count(), 0, 32), cue.pitch(), cue.volume());
                     }
                  }
               } else {
                  switch (scene.animation) {
                     case GRAVE:
                        event(scene, 0, 0.1F, SoundEvents.BLOCK_GRAVEL_BREAK, ParticleTypes.POOF, 8);
                        break;
                     case ANVIL:
                        event(scene, 0, 0.34F, SoundEvents.BLOCK_ANVIL_LAND, ParticleTypes.POOF, 8);
                        break;
                     case FREEZE:
                        event(scene, 0, 0.08F, SoundEvents.BLOCK_GLASS_PLACE, ParticleTypes.SNOWFLAKE, 10);
                        event(scene, 1, 0.72F, SoundEvents.BLOCK_GLASS_BREAK, ParticleTypes.SNOWFLAKE, 14);
                        break;
                     case TOTEM:
                        event(scene, 0, 0.2F, SoundEvents.ITEM_TOTEM_USE, ParticleTypes.TOTEM_OF_UNDYING, 12);
                        aura(scene, ParticleTypes.TOTEM_OF_UNDYING, 5);
                        break;
                     case GHOST:
                        event(scene, 0, 0.02F, SoundEvents.ENTITY_ALLAY_AMBIENT_WITH_ITEM, null, 0);
                        aura(scene, ParticleTypes.SOUL, 8);
                        break;
                     case BURN:
                        if (p < 0.29F && (int)scene.age % 4 == 0) {
                           particles(scene, ParticleTypes.FLAME, 5, 0.55, 0.04);
                        }

                        event(scene, 0, 0.3F, SoundEvents.BLOCK_FIRE_EXTINGUISH, ParticleTypes.SMOKE, 5);

                        for (int i = 1; i <= 6; i++) {
                           event(scene, i, 0.21F + i * 0.09F, SoundEvents.ENTITY_SKELETON_STEP, null, 0);
                        }
                        break;
                     case GORY_EXPLOSION:
                        event(scene, 0, 0.08F, (SoundEvent)SoundEvents.ENTITY_GENERIC_EXPLODE.value(), ParticleTypes.POOF, 10);
                        break;
                     case EXPLOSION:
                        event(scene, 0, 0.02F, SoundEvents.ENTITY_TNT_PRIMED, null, 0);
                        event(scene, 1, 0.72F, (SoundEvent)SoundEvents.ENTITY_GENERIC_EXPLODE.value(), ParticleTypes.EXPLOSION_EMITTER, 1);
                        break;
                     case LIGHTNING:
                        if (event(scene, 0, 0.24F, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, ParticleTypes.ELECTRIC_SPARK, 12)) {
                           VisualLightning bolt = new VisualLightning(scene.level);
                           bolt.setPosition(scene.x, scene.floorY, scene.z);
                           local(scene, bolt);
                        }
                        break;
                     case OPPONENT:
                        event(scene, 0, 0.5F, SoundEvents.ENTITY_ENDERMAN_TELEPORT, ParticleTypes.PORTAL, 10);
                        break;
                     case GOLD:
                        event(scene, 0, 0.042857144F, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, null, 0);
                        if (scene.age >= 6.0F && scene.age < 54.0F && (int)scene.age % 4 == 0 && DeathAnimationConfig.INSTANCE.visualEffects) {
                           particle(scene, new DustParticleEffect(15317567, 0.5F), scene.gold().sparkOrigin(), new Vec3d(0.0, 0.018, 0.0));
                        }

                        double impact = scene.gold().consumeImpact();
                        if (impact > 0.1) {
                           Vec3d point = scene.gold().bounds().getCenter();
                           if (DeathAnimationConfig.INSTANCE.sounds) {
                              scene.level
                                 .playSoundClient(
                                    point.x,
                                    point.y,
                                    point.z,
                                    SoundEvents.BLOCK_ANVIL_LAND,
                                    SoundCategory.PLAYERS,
                                    (float)Math.min(0.8, 0.35 + impact),
                                    0.65F,
                                    false
                                 );
                           }

                           if (DeathAnimationConfig.INSTANCE.visualEffects) {
                              for (int i = 0; i < 4; i++) {
                                 particle(scene, new DustParticleEffect(11110994, 0.45F), point.add(0.0, -0.15, 0.0), new Vec3d((i - 1.5) * 0.012, 0.012, 0.0));
                              }
                           }
                        }
                        break;
                     case HEAVEN:
                        event(scene, 0, 0.08F, SoundEvents.BLOCK_BEACON_ACTIVATE, ParticleTypes.END_ROD, 12);
                        aura(scene, ParticleTypes.END_ROD, 4);
                        break;
                     case AIR:
                        event(scene, 0, 0.12F, SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, ParticleTypes.CLOUD, 8);
                        aura(scene, ParticleTypes.CLOUD, 6);
                        break;
                     case VOID:
                        event(scene, 0, 0.1F, SoundEvents.ENTITY_ENDERMAN_TELEPORT, ParticleTypes.REVERSE_PORTAL, 12);
                        aura(scene, ParticleTypes.REVERSE_PORTAL, 6);
                        break;
                     case ENDER:
                        event(scene, 0, 0.18F, SoundEvents.ENTITY_ENDERMAN_TELEPORT, ParticleTypes.PORTAL, 12);
                        event(scene, 1, 0.86F, SoundEvents.ENTITY_ENDERMAN_TELEPORT, ParticleTypes.REVERSE_PORTAL, 8);
                        break;
                     case DUST:
                        event(
                           scene, 0, 0.16F, SoundEvents.BLOCK_SAND_BREAK, new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.SAND.getDefaultState()), 10
                        );
                        if (p > 0.2F && p < 0.9F && (int)scene.age % 4 == 0) {
                           particles(scene, new BlockStateParticleEffect(ParticleTypes.FALLING_DUST, Blocks.SAND.getDefaultState()), 4, 0.5, -0.03);
                        }
                  }
               }
            } else {
               if (scene.animation == DeathAnimationConfig.Animation.WHIRLPOOL) {
                  whirlpoolSound(scene, p);
                  waterVortex(scene);
               } else {
                  weatherParticles(scene, true);
               }
            }
         }
      }
   }

   private static void kamehamehaEffects(DeathScene scene) {
      if (!scene.gui && DeathAnimationConfig.INSTANCE.visualEffects && scene.age % 2.0F == 0.0F) {
         float p = scene.progress();
         PlayerActors.HandPose hands = PlayerActors.handPose(scene);
         Vec3d origin = hands.midpoint();
         Vec3d target = new Vec3d(scene.x, scene.floorY + 1.0, scene.z);
         Vec3d direction = target.subtract(origin);
         if (!(direction.lengthSquared() < 1.0E-6)) {
            direction = direction.normalize();
            Random random = new Random(scene.seed + (long)scene.age * 7919L);
            if (p >= 0.2F && p < 0.61F) {
               double radius = 0.08 + 0.34 * AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.2F, 0.49F));

               for (int i = 0; i < 5; i++) {
                  double angle = scene.age * 0.47 + i * 3.141592653589793 * 2.0 / 3.0;
                  Vec3d point = origin.add(Math.cos(angle) * radius, 0.55 * radius * Math.sin(angle * 1.7), Math.sin(angle) * radius);
                  Vec3d pull = origin.subtract(point).normalize().multiply(0.06);
                  particle(scene, (ParticleEffect)(i % 2 == 0 ? ParticleTypes.ELECTRIC_SPARK : new DustParticleEffect(7921663, 0.55F)), point, pull);
               }
            }

            if (p >= 0.61F && p < 0.95F) {
               double length = Kamehameha.beam(scene).length();

               for (int i = 0; i < 9; i++) {
                  double along = (i + random.nextDouble()) / 9.0;
                  Vec3d point = origin.add(direction.multiply(length * along));
                  point = point.add((random.nextDouble() - 0.5) * 1.3, (random.nextDouble() - 0.5) * 1.3, (random.nextDouble() - 0.5) * 1.3);
                  particle(
                     scene,
                     (ParticleEffect)(i % 3 == 0 ? ParticleTypes.END_ROD : new DustParticleEffect(5750783, 0.65F)),
                     point,
                     direction.multiply(0.3 + random.nextDouble() * 0.3)
                  );
               }
            }

            if (p >= 0.657F && p < 0.85499996F) {
               float erosion = AnimationTimeline.phase(p, 0.657F, 0.83F);

               for (int i = 0; i < 8 + Math.round(erosion * 12.0F); i++) {
                  Vec3d point = new Vec3d(
                     scene.x + (random.nextDouble() - 0.5) * 0.7, scene.floorY + 0.08 + random.nextDouble() * 1.7, scene.z + (random.nextDouble() - 0.5) * 0.7
                  );
                  Vec3d velocity = direction.multiply(0.25 + random.nextDouble() * 0.55).add(0.0, (random.nextDouble() - 0.5) * 0.07, 0.0);
                  particle(scene, (ParticleEffect)(i % 4 == 0 ? ParticleTypes.END_ROD : new DustParticleEffect(11728127, 0.45F)), point, velocity);
               }
            }
         }
      }
   }

   private static void particle(DeathScene scene, ParticleEffect type, Vec3d point, Vec3d velocity) {
      EffectParticles.add(scene, type, point.x, point.y, point.z, velocity.x, velocity.y, velocity.z);
   }

   private static void weatherParticles(DeathScene scene, boolean wind) {
      if (!scene.gui && DeathAnimationConfig.INSTANCE.visualEffects && (int)scene.age % (wind ? 3 : 2) == 0) {
         float p = scene.progress();
         if (!(p < 0.08F) && !(p > (wind ? 0.7F : 0.86F))) {
            int count = wind ? 3 : 8;

            for (int i = 0; i < count; i++) {
               double turn = scene.age * (wind ? 0.38 : -0.38) + i * 3.141592653589793 * 2.0 / count;
               double height = wind ? 0.12 + (scene.age * 0.045 + i * 0.31) % 1.0 * 2.8 : 0.04 + (scene.age * 0.035 + i * 0.27) % 1.0 * 0.3;
               double radius = wind ? 0.18 + height * 0.3 : (0.35 + i % 3 * 0.38) * (1.0 - p * 0.55);
               double dx = Math.cos(turn);
               double dz = Math.sin(turn);
               Particle particle = EffectParticles.create(
                  wind ? ParticleTypes.CLOUD : ParticleTypes.BUBBLE_POP,
                  scene.x + dx * radius,
                  scene.floorY + height,
                  scene.z + dz * radius,
                  -dz * (wind ? 0.025 : -0.025),
                  wind ? 0.035 : 0.005,
                  dx * (wind ? 0.025 : -0.025)
               );
               if (particle != null) {
                  particle.setMaxAge(wind ? 14 : 22);
                  particle.scale(wind ? 0.4F : 2.0F);
               }

               if (!wind && i % 2 == 0) {
                  Particle spray = EffectParticles.create(
                     ParticleTypes.SPLASH, scene.x + dx * radius, scene.floorY + 0.08, scene.z + dz * radius, -dz * 0.08, 0.06, dx * 0.08
                  );
                  if (spray != null) {
                     spray.scale(1.25F);
                     spray.setMaxAge(18);
                  }

                  Particle foam = EffectParticles.create(
                     new DustParticleEffect(13695743, 1.1F), scene.x + dx * radius, scene.floorY + 0.04, scene.z + dz * radius, -dz * 0.04, 0.0, dx * 0.04
                  );
                  if (foam != null) {
                     foam.setMaxAge(12);
                  }
               }
            }
         }
      }
   }

   private static void waterVortex(DeathScene scene) {
      if (!scene.gui && DeathAnimationConfig.INSTANCE.visualEffects && (int)scene.age % 3 == 0) {
         WhirlpoolMotion.Flow flow = WhirlpoolMotion.flow(scene, scene.progress());
         float formation = 0.22F + 0.78F * AnimationTimeline.smooth(AnimationTimeline.phase(flow.progress(), 0.015F, 0.22F));
         float fade = WhirlpoolMotion.funnelFade(scene) * formation;
         if (!(fade <= 0.015F)) {
            int segments = DeathAnimationConfig.INSTANCE.cinematicEffects ? 8 : 6;
            float centerX = WhirlpoolMotion.centerX(scene);
            float centerZ = WhirlpoolMotion.centerZ(scene);
            double angular = Math.min(0.28F, flow.angularStep());
            float funnelWidth = WhirlpoolMotion.outerRadius() + 0.2F;
            float funnelHeight = 0.18F + 0.62F * (1.0F - 0.25F * flow.tightening());

            for (int layer = 0; layer < 5; layer++) {
               float height = layer / 4.0F;
               float radius = 0.1F + funnelWidth * (float)Math.pow(height, 0.8799999952316284);
               double y = scene.floorY + 0.035 + funnelHeight * height;

               for (int i = 0; i < segments; i++) {
                  double angle = flow.angle() + i * 3.141592653589793 * 2.0 / segments + height * 3.5F;
                  double sin = Math.sin(angle);
                  double cos = Math.cos(angle);
                  float radial = flow.tighteningStep() * 0.64F * height + flow.radialStep() * 0.16F * (1.0F - height);
                  float localX = (float)(sin * radius);
                  float localZ = (float)(cos * radius);
                  float localVelocityX = (float)(-radial * sin + radius * cos * angular);
                  float localVelocityZ = (float)(-radial * cos - radius * sin * angular);
                  double vx = WhirlpoolMotion.offsetX(scene, localVelocityX, localVelocityZ);
                  double vz = WhirlpoolMotion.offsetZ(scene, localVelocityX, localVelocityZ);
                  double px = scene.x + centerX + WhirlpoolMotion.offsetX(scene, localX, localZ);
                  double pz = scene.z + centerZ + WhirlpoolMotion.offsetZ(scene, localX, localZ);
                  SimpleParticleType type = (i + layer) % 4 == 0 ? ParticleTypes.SPLASH : ParticleTypes.BUBBLE_POP;
                  double vy = -0.003F - flow.sinkStep() * (0.18F + 0.72F * height);
                  Particle particle = EffectParticles.create(type, px, y, pz, vx * 0.72 * fade, vy * fade, vz * 0.72 * fade);
                  if (particle != null) {
                     particle.setMaxAge(Math.max(3, Math.round(15.0F * fade)));
                     particle.scale((0.62F + 0.36F * flow.tightening()) * fade);
                  }

                  if ((i + layer) % 7 == 0) {
                     Particle foam = EffectParticles.create(
                        new DustParticleEffect(12447743, 0.62F), px, y + 0.015, pz, vx * 0.45 * fade, 0.002 * fade, vz * 0.45 * fade
                     );
                     if (foam != null) {
                        foam.setMaxAge(Math.max(3, Math.round(10.0F * fade)));
                        foam.scale(0.62F * fade);
                     }
                  }
               }
            }
         }
      }
   }

   private static void whirlpoolSound(DeathScene scene, float progress) {
      if (!(progress < 0.025F) && (scene.emitted & 1) == 0) {
         scene.emitted |= 1;
         if (DeathAnimationConfig.INSTANCE.sounds) {
            scene.level
               .playSoundClient(
                  scene.x + WhirlpoolMotion.centerX(scene),
                  scene.floorY,
                  scene.z + WhirlpoolMotion.centerZ(scene),
                  SoundEvents.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_AMBIENT,
                  SoundCategory.PLAYERS,
                  scene.gui ? 0.14F : 0.55F,
                  1.0F,
                  false
               );
            scene.sounded |= 1;
         }
      }
   }

   private static void bulletEffects(DeathScene scene) {
      float hit = BulletImpactRagdoll.impactPhase(scene);
      float p = scene.progress();
      boolean struck = event(scene, 0, hit, SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, null, 0);
      Quaternionf facing = new Quaternionf().rotationY((float)Math.toRadians(scene.sceneFacing));
      Vector3f impact = facing.transform(new Vector3f(0.0F, 1.1F, 0.1F));
      if (struck && !scene.gui && DeathAnimationConfig.INSTANCE.visualEffects) {
         for (int i = 0; i < 16; i++) {
            double angle = i * 3.141592653589793 / 8.0;
            Vector3f velocity = facing.transform(new Vector3f((float)Math.cos(angle) * 0.12F, (float)Math.sin(angle) * 0.1F, -0.08F));
            Particle puff = EffectParticles.create(
               ParticleTypes.POOF, scene.x + impact.x, scene.y + impact.y, scene.z + impact.z, velocity.x, velocity.y, velocity.z
            );
            if (puff != null) {
               puff.setMaxAge(9);
               puff.scale(0.55F);
            }

            if (i % 3 == 0) {
               Particle spark = EffectParticles.create(
                  ParticleTypes.END_ROD, scene.x + impact.x, scene.y + impact.y, scene.z + impact.z, velocity.x * 1.4, velocity.y * 1.4, velocity.z * 1.4
               );
               if (spark != null) {
                  spark.setMaxAge(6);
                  spark.scale(0.3F);
               }
            }
         }
      }

      BulletImpactRagdoll physics = scene.laserGrid() == null ? scene.bullet() : null;
      if (physics != null
         && physics.landingAge() >= 0.0F
         && scene.age >= physics.landingAge()
         && event(scene, 1, physics.landingAge() / AnimationTimeline.ticks(scene.animation), SoundEvents.ENTITY_PLAYER_BIG_FALL, null, 0)
         && !scene.gui
         && DeathAnimationConfig.INSTANCE.visualEffects) {
         Vec3d point = physics.landingPoint();

         for (int i = 0; i < 10; i++) {
            double angle = i * 3.141592653589793 / 5.0;
            Particle dust = EffectParticles.create(
               ParticleTypes.POOF,
               point.x + Math.cos(angle) * 0.18,
               point.y + 0.06,
               point.z + Math.sin(angle) * 0.18,
               Math.cos(angle) * 0.055,
               0.03,
               Math.sin(angle) * 0.055
            );
            if (dust != null) {
               dust.setMaxAge(10);
               dust.scale(0.4F);
            }
         }
      }

      if (!scene.gui && DeathAnimationConfig.INSTANCE.visualEffects && p > 0.06F && p < 0.54F && (int)scene.age % 2 == 0 && !scene.props.isEmpty()) {
         Entity projectile = scene.props.getFirst();
         Vector3f rear = facing.transform(new Vector3f(0.0F, 0.0F, 0.68F));
         Particle smoke = EffectParticles.create(
            ParticleTypes.CLOUD, projectile.getX() + rear.x, projectile.getY(), projectile.getZ() + rear.z, 0.0, 0.005, 0.0
         );
         if (smoke != null) {
            smoke.setMaxAge(8);
            smoke.scale(0.3F);
         }
      }
   }

   private static boolean event(DeathScene scene, int index, float phase, SoundEvent sound, ParticleEffect particle, int count) {
      return event(scene, index, phase, sound, particle, count, null, null);
   }

   private static boolean event(DeathScene scene, int index, float phase, SoundEvent sound, ParticleEffect particle, int count, Float pitch, Float volume) {
      int mask = 1 << index;
      if ((scene.emitted & mask) == 0 && !(scene.progress() < phase)) {
         scene.emitted |= mask;
         if (sound != null && DeathAnimationConfig.INSTANCE.sounds) {
            float loudness = volume == null
               ? (scene.gui ? 0.18F : (sound == SoundEvents.ITEM_TOTEM_USE ? 0.35F : 0.65F))
               : Math.clamp(volume, 0.0F, 1.0F) * (scene.gui ? 0.28F : 1.0F);
            Vec3d soundPoint = scene.animation == DeathAnimationConfig.Animation.BAN_HAMMER && phase == 0.64F
               ? BanHammer.contactPoint(scene)
               : new Vec3d(scene.x, scene.y, scene.z);
            scene.level
               .playSoundClient(
                  soundPoint.x, soundPoint.y, soundPoint.z, sound, SoundCategory.PLAYERS, loudness, pitch == null ? 1.0F : Math.clamp(pitch, 0.5F, 2.0F), false
               );
            scene.sounded |= mask;
         }

         if (particle != null) {
            particles(scene, particle, count, 0.7, 0.04);
         }

         return true;
      } else {
         return false;
      }
   }

   private static void aura(DeathScene scene, ParticleEffect particle, int interval) {
      if (scene.age < AnimationTimeline.ticks(scene.animation) && (int)scene.age % interval == 0) {
         particles(scene, particle, DeathAnimationConfig.INSTANCE.cinematicEffects ? 4 : 2, 0.45, 0.06);
      }
   }

   private static void particles(DeathScene scene, ParticleEffect particle, int count, double radius, double up) {
      if (DeathAnimationConfig.INSTANCE.visualEffects && !scene.gui) {
         for (int i = 0; i < Math.min(32, count); i++) {
            double angle = RANDOM.nextDouble() * 3.141592653589793 * 2.0;
            double dx = Math.cos(angle) * radius * RANDOM.nextDouble();
            double dz = Math.sin(angle) * radius * RANDOM.nextDouble();
            DeathScene.Root root = scene.root();
            double y = scene.y + root.y() + 0.2 + RANDOM.nextDouble() * 1.5;
            if (scene.animation == DeathAnimationConfig.Animation.BAN_HAMMER) {
               y = BanHammer.contactPoint(scene).y + (RANDOM.nextDouble() - 0.5) * 0.16;
            }

            Particle created = EffectParticles.create(particle, scene.x + root.x() + dx, y, scene.z + root.z() + dz, dx * 0.08, up, dz * 0.08);
            if (created != null && scene.animation == DeathAnimationConfig.Animation.BURN && particle == ParticleTypes.FLAME) {
               created.setMaxAge(
                  Math.max(1, (int)((AnimationTimeline.ticks(scene.animation) * 0.3F - scene.age) / DeathAnimationConfig.INSTANCE.animationSpeed))
               );
            }
         }
      }
   }

   private static void block(DeathScene scene, BlockState state, double x, double y, double z, float sx, float sy, float sz) {
      BlockDisplayEntity prop = (BlockDisplayEntity)EntityType.BLOCK_DISPLAY.create(scene.level, SpawnReason.COMMAND);
      if (prop != null) {
         prop.setBlockState(state);
         prop.setTransformation(transform(sx, sy, sz));
         prop.setPosition(x, y, z);
         local(scene, prop);
      }
   }

   private static void item(DeathScene scene, ItemStack item, double x, double y, double z, float size) {
      ItemDisplayEntity prop = (ItemDisplayEntity)EntityType.ITEM_DISPLAY.create(scene.level, SpawnReason.COMMAND);
      if (prop != null) {
         prop.setItemStack(item);
         prop.setTransformation(transform(size, size, size));
         prop.setPosition(x, y, z);
         local(scene, prop);
      }
   }

   private static AffineTransformation transform(float x, float y, float z) {
      return new AffineTransformation(new Vector3f(), new Quaternionf(), new Vector3f(x, y, z), new Quaternionf());
   }

   private static void local(DeathScene scene, Entity entity) {
      entity.setId(IDS.getAndDecrement());
      entity.setUuid(UUID.randomUUID());
      if (!scene.gui && !HangingRagdoll.supports(scene.animation)) {
         scene.level.addEntity(entity);
      }

      scene.props.add(entity);
   }
}
