package com.baom5.deathanimation.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class DeathScene {
   static final float LIGHTNING_IMPACT = 0.24F;
   private static final float OPPONENT_COLLAPSE = 0.62F;
   public final DeathAnimationConfig.Animation animation;
   public final DeathAvatar avatar;
   public final int sourceId;
   public final String sourceName;
   public final boolean preview;
   public final boolean gui;
   public final ClientWorld level;
   public final long seed;
   final boolean ejectedImpostor;
   public final List<Entity> props = new ArrayList<>();
   public final float heading;
   public final float sceneFacing;
   public SkinTextures opponentSkin;
   public final UUID sourceUuid;
   public final double x;
   public final double y;
   public final double z;
   public float age;
   public boolean playbackRenderObserved;
   private int restingTicks;
   public int sounded;
   public int emitted;
   public double floorY;
   float banHammerCrown = 0.0F / 0.0F;
   private BodyFragment[] fragments;
   private DeathBodyRig.Geometry[] geometry;
   private Vector3f[] centers;
   private int simulated;
   private JointedRagdoll ragdoll;
   private HangingRagdoll hanging;
   private BulletImpactRagdoll bullet;
   private GoldStatue gold;
   private AcrobaticRagdoll acrobatics;
   private LaserGridSegmentation laserGrid;
   private ShellGame shellGame;
   DustfallSand dustfallSand;
   NestedDollClose nestedDoll;
   CalendarBoneDust calendarDust;
   PhotocopierMassProduction.State photocopier;
   PaperShredderStrip.State paperShredder;
   WashingMachineCycle.State washer;
   DominoBridgeCollapse.State dominoBridge;
   BowlingSplit.State bowlingSplit;
   ClawMachinePrizeDrop.State clawMachine;
   PaperAirplaneFold.State paperAirplane;
   KirbyInhaleStarSpit.State kirbyInhale;
   public final Vec3d inheritedVelocity;
   private final float variant;

   public DeathScene(
      ClientWorld level, AbstractClientPlayerEntity source, DeathAnimationConfig.Animation animation, int entityId, boolean preview, boolean gui, Vec3d origin
   ) {
      if (!ReleaseProfile.allows(animation)) {
         throw new IllegalArgumentException("Animation excluded from this build: " + animation);
      }

      this.level = level;
      this.animation = animation;
      this.preview = preview;
      this.gui = gui;
      this.sourceId = source.getId();
      this.sourceName = source.getName().getString();
      this.sourceUuid = source.getUuid();
      this.seed = source.getUuid().getLeastSignificantBits() ^ entityId * 31L;
      Random random = new Random(this.seed);
      this.ejectedImpostor = animation == DeathAnimationConfig.Animation.BATCH01_AIRLOCK_VERDICT && random.nextInt(10) == 0;
      this.heading = random.nextFloat() * 360.0F;
      this.variant = random.nextFloat() * 2.0F - 1.0F;
      this.x = origin.x;
      this.y = origin.y;
      this.z = origin.z;
      this.floorY = BodyFragment.support(level, origin, 2.0, this.y);
      this.avatar = new DeathAvatar(level, source, entityId);
      this.avatar.copyModelParts(source);
      boolean aligned = AnimationCatalog.contains(animation) && AnimationCatalog.recipe(animation).sceneAligned();
      this.sceneFacing = !gui && aligned ? 180.0F - source.bodyYaw : 0.0F;
      if (gui || aligned) {
         this.avatar.setYaw(180.0F);
         this.avatar.bodyYaw = this.avatar.lastBodyYaw = 180.0F;
         this.avatar.headYaw = this.avatar.lastHeadYaw = 180.0F;
      }

      this.avatar.setPosition(this.x, this.y, this.z);
      this.avatar.syncOldPosition();
      Vec3d motion = source.getVelocity();
      Vec3d observed = source.getEntityPos().subtract(new Vec3d(source.lastRenderX, source.lastRenderY, source.lastRenderZ));
      this.inheritedVelocity = MotionHistory.atDeath(source, motion.lengthSquared() > observed.lengthSquared() ? motion : observed);
      this.opponentSkin = AttackerTracker.skinFor(source);
      if (animation == DeathAnimationConfig.Animation.DUST) {
         this.dustfallSand = new DustfallSand();
      }

      if (animation == DeathAnimationConfig.Animation.PILOT_NESTED_DOLLS) {
         this.nestedDoll = new NestedDollClose(this);
      }

      if (animation == DeathAnimationConfig.Animation.GOLD) {
         this.gold = new GoldStatue(this, source);
      }

      if (animation == DeathAnimationConfig.Animation.PILOT_SHELL_GAME) {
         this.shellGame = new ShellGame(this);
      }

      if (AcrobaticRagdoll.supports(animation)) {
         this.acrobatics = new AcrobaticRagdoll(this);
      }

      if (LaserGridSegmentation.supports(this)) {
         this.laserGrid = new LaserGridSegmentation(this);
      }
   }

   public float progress() {
      float partial = MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(false);
      return MathHelper.clamp((this.age + partial * DeathAnimationConfig.INSTANCE.animationSpeed) / AnimationTimeline.ticks(this.animation), 0.0F, 1.0F);
   }

   public void tick() {
      if (this.shellGame != null) {
         this.shellGame.tick();
      } else {
         this.age = this.age + DeathAnimationConfig.INSTANCE.animationSpeed;
         if (this.animation == DeathAnimationConfig.Animation.OPPONENT && this.progress() >= 0.5F) {
            this.opponentSkin = AttackerTracker.skinFor(this.sourceUuid, this.opponentSkin);
            AttackerTracker.applySkin(this.sourceUuid, this.avatar, this.opponentSkin);
         }

         if (this.gold != null) {
            this.gold.tick();
         }

         if (this.acrobatics != null
            && MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(this.avatar) instanceof LivingEntityRenderer<?, ?, ?> living
            && living.getModel() instanceof BipedEntityModel<?> model) {
            this.acrobatics.tick(model);
         }

         if (this.laserGrid != null
            && MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(this.avatar) instanceof LivingEntityRenderer<?, ?, ?> living
            && living.getModel() instanceof BipedEntityModel<?> model) {
            this.laserGrid.tick(model);
         }

         if (this.animation == DeathAnimationConfig.Animation.BATCH01_BULLET_BILL && this.laserGrid == null) {
            BulletImpactRagdoll physics = this.bullet();
            if (physics != null) {
               physics.tick();
            }
         }

         if (HangingRagdoll.supports(this.animation)) {
            HangingRagdoll physics = this.hanging();
            if (physics != null) {
               physics.tick();
            }
         }

         if (this.usesFragments()
            && MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(this.avatar) instanceof LivingEntityRenderer<?, ?, ?> living
            && living.getModel() instanceof BipedEntityModel<?> model) {
            this.simulate(model);
         }

         if (this.age >= AnimationTimeline.ticks(this.animation)) {
            this.restingTicks++;
         }
      }
   }

   public boolean expired() {
      if (this.dustfallSand != null && this.dustfallSand.finished()) {
         return true;
      }

      if (this.shellGame != null) {
         return this.shellGame.finished();
      }

      int rest = DeathAnimationConfig.INSTANCE.persistEffects ? DeathAnimationConfig.INSTANCE.durationSeconds * 20 : 20;
      if (this.dustfallSand != null && this.dustfallSand.sandScene()) {
         rest = Math.max(rest, 600);
      }

      if (this.clawMachine != null) {
         if (this.clawMachine.claimedAge >= 0.0F) {
            return this.age - this.clawMachine.claimedAge >= 60.0F;
         }

         rest = Math.max(rest, 300);
      }

      if (this.animation == DeathAnimationConfig.Animation.FULL_1323_WASHING_MACHINE_SPIN_CYCLE) {
         rest = Math.max(rest, 60);
      }

      if (this.animation == DeathAnimationConfig.Animation.FULL_1338_PLAYER_DEFLATE) {
         rest = Math.max(rest, 80);
      }

      if (this.animation == DeathAnimationConfig.Animation.WHIRLPOOL) {
         float speed = Math.max(0.05F, DeathAnimationConfig.INSTANCE.animationSpeed);
         rest = Math.max(rest, (int)Math.ceil(18.0F / speed) + 2);
      }

      return this.restingTicks >= rest;
   }

   public void discard() {
      if (this.dustfallSand != null) {
         this.dustfallSand.discard();
      }

      PlayerActors.discard(this);
      PhotocopierMassProduction.discard(this);
      PaperShredderStrip.discard(this);
      WashingMachineCycle.discard(this);
      DominoBridgeCollapse.discard(this);
      BowlingSplit.discard(this);
      ClawMachinePrizeDrop.discard(this);
      PaperAirplaneFold.discard(this);
      KirbyInhaleStarSpit.discard(this);
      if (this.laserGrid != null) {
         this.laserGrid.discard();
      }

      this.avatar.discard();

      for (Entity prop : this.props) {
         prop.discard();
      }

      this.props.clear();
   }

   public DeathScene.Root root() {
      if (this.gold != null) {
         return this.gold.root();
      }

      if (this.dustfallSand != null && this.dustfallSand.sandScene()) {
         return this.dustfallSand.root(this);
      }

      if (this.nestedDoll != null) {
         return NestedDollClose.root(this);
      }

      if (this.acrobatics != null && this.acrobatics.active()) {
         return DeathScene.Root.STILL;
      }

      if (!HangingRagdoll.supports(this.animation) && this.animation != DeathAnimationConfig.Animation.BATCH01_BULLET_BILL) {
         float p = this.progress();
         float fall = AnimationTimeline.easeOut(AnimationTimeline.phase(p, 0.0F, 0.48F));
         if (AnimationCatalog.contains(this.animation)) {
            return AnimationCatalog.root(this);
         }

         return switch (this.animation) {
            case RAGDOLL -> new DeathScene.Root(0.0F, 0.13F * fall, 0.0F, 90.0F * fall, this.heading, 0.0F);
            case GHOST -> new DeathScene.Root(0.0F, AnimationTimeline.track(p, 0.0F, 0.0F, 0.15F, 0.1F, 1.0F, 2.5F), 0.0F, -5.0F * p, 0.0F, 0.0F);
            case ANVIL -> new DeathScene.Root(
               0.0F,
               0.005F,
               0.0F,
               0.0F,
               0.0F,
               0.0F,
               AnimationTimeline.track(p, 0.0F, 1.0F, 0.33F, 1.0F, 0.44F, 1.85F, 0.52F, 1.65F, 0.68F, 1.8F),
               AnimationTimeline.track(p, 0.0F, 1.0F, 0.33F, 1.0F, 0.44F, 0.06F, 0.52F, 0.13F, 0.68F, 0.08F),
               AnimationTimeline.track(p, 0.0F, 1.0F, 0.33F, 1.0F, 0.44F, 1.65F, 0.52F, 1.45F, 0.68F, 1.6F)
            );
            case EXPLOSION -> {
               float charge = AnimationTimeline.phase(p, 0.12F, 0.72F);
               float pulse = (float)Math.pow(Math.max(0.0, Math.sin(charge * charge * 44.0F)), 4.0);
               float swell = 1.0F + 0.04F * charge + 0.07F * pulse * charge;
               yield new DeathScene.Root(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, swell, 1.0F + 0.04F * charge, swell);
            }
            case HEAVEN -> new DeathScene.Root(0.0F, 7.0F * AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.32F, 1.0F)), 0.0F, 0.0F, 0.0F, 0.0F);
            case AIR -> {
               float launch = AnimationTimeline.phase(p, 0.12F, 0.82F);
               yield new DeathScene.Root(
                  0.0F, 108.0F * launch * launch, 0.0F, AnimationTimeline.track(p, 0.0F, -12.0F, 0.12F, 10.0F, 0.6F, 100.0F, 1.0F, 130.0F), 0.0F, 0.0F
               );
            }
            case VOID -> new DeathScene.Root(0.0F, -2.3F * AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.12F, 0.9F)), 0.0F, 0.0F, 0.0F, 0.0F);
            case TOTEM -> new DeathScene.Root(0.0F, 0.4F * (float)Math.sin(p * 3.141592653589793), 0.0F, 0.0F, 0.0F, 0.0F);
            default -> DeathScene.Root.STILL;
         };
      } else {
         return DeathScene.Root.STILL;
      }
   }

   public boolean bodyVisible() {
      if (this.clawMachine != null && this.progress() >= 0.94F) {
         return this.clawMachine.claimedAge < 0.0F;
      }

      if (this.animation == DeathAnimationConfig.Animation.PILOT_CARPET_ROLL) {
         return this.progress() < 0.84F;
      }

      if (this.animation == DeathAnimationConfig.Animation.BATCH01_IMPOSTOR_KILL) {
         return true;
      }

      if (this.shellGame != null) {
         return this.shellGame.playerVisible();
      }

      if (this.nestedDoll != null) {
         return this.nestedDoll.bodyVisible();
      }

      if (this.dustfallSand != null && this.dustfallSand.sandScene()) {
         return this.dustfallSand.originalVisible(this.progress());
      }

      if (this.animation == DeathAnimationConfig.Animation.FULL_1321_PAPER_AIRPLANE_FOLD) {
         return this.progress() < 0.14F;
      }

      if (this.animation == DeathAnimationConfig.Animation.WHIRLPOOL) {
         return this.progress() < 0.99F;
      }

      float p = this.progress();
      if (AnimationCatalog.contains(this.animation)) {
         return p < AnimationCatalog.recipe(this.animation).visibleUntil();
      }

      return switch (this.animation) {
         case GHOST, HEAVEN -> p < 0.995F;
         default -> true;
         case EXPLOSION -> p < 0.72F;
         case AIR -> p < 1.0F;
         case VOID -> p < 0.88F;
         case TOTEM -> p < 0.72F;
         case GRAVE -> false;
         case FREEZE -> p < 0.72F;
         case ENDER, DUST -> p < 0.94F;
      };
   }

   public Box renderBounds() {
      if (this.gold != null) {
         return this.gold.bounds();
      }

      if (this.nestedDoll != null && this.nestedDoll.shellBounds() != null) {
         return this.nestedDoll.shellBounds();
      }

      if (this.dustfallSand != null && this.dustfallSand.sandScene()) {
         return this.dustfallSand.bounds(this);
      }

      if (this.acrobatics != null && this.acrobatics.active()) {
         return this.acrobatics.bounds();
      }

      if (this.laserGrid != null && this.animation == DeathAnimationConfig.Animation.BATCH01_BULLET_BILL) {
         return new Box(this.x - 6.0, this.floorY - 2.0, this.z - 6.0, this.x + 6.0, this.floorY + 4.0, this.z + 6.0);
      }

      if (HangingRagdoll.supports(this.animation)) {
         return new Box(this.x - 4.0, this.floorY - 0.5, this.z - 4.0, this.x + 4.0, this.floorY + 5.0, this.z + 4.0);
      }

      DeathScene.Root pose = this.root();
      Box bounds = new Box(
         this.x + pose.x - 2.0, this.y + pose.y - 2.0, this.z + pose.z - 2.0, this.x + pose.x + 2.0, this.y + pose.y + 2.0, this.z + pose.z + 2.0
      );
      if (this.fragments != null) {
         for (BodyFragment fragment : this.fragments) {
            if (fragment != null) {
               bounds = bounds.union(fragment.bounds());
            }
         }
      }

      if (this.bullet != null) {
         for (BodyFragment piece : this.bullet.pieces()) {
            bounds = bounds.union(piece.bounds());
         }
      }

      return bounds;
   }

   public int tint() {
      if (this.clawMachine != null && this.progress() >= 0.94F) {
         return -1;
      }

      if (this.animation == DeathAnimationConfig.Animation.PILOT_CARPET_ROLL) {
         return -1;
      }

      if (this.animation == DeathAnimationConfig.Animation.WHIRLPOOL && this.progress() >= 0.99F) {
         return 16777215;
      }

      if (this.gold != null) {
         return -1;
      }

      float p = this.progress();
      int alpha = AnimationCatalog.contains(this.animation) ? Math.round(255.0F * AnimationCatalog.alpha(this)) : 255;
      int rgb = 16777215;
      if (this.animation == DeathAnimationConfig.Animation.EXPLOSION && p >= 0.12F) {
         return -1;
      }

      if (this.animation == DeathAnimationConfig.Animation.BURN && p >= 0.3F) {
         return -16777216;
      }

      if (this.animation == DeathAnimationConfig.Animation.LIGHTNING && p >= 0.24F) {
         return -16777216;
      }

      if (this.animation == DeathAnimationConfig.Animation.BATCH01_FREDDY_SHUTDOWN && p >= 0.565F && p < 0.59F) {
         return -3368;
      }

      if (this.animation == DeathAnimationConfig.Animation.BATCH01_IMPOSTOR_KILL && p >= 0.49F) {
         return -1;
      }

      if (this.animation == DeathAnimationConfig.Animation.BATCH01_DRONE_ELIMINATION && p >= 0.25F) {
         rgb = 5623551;
      }

      if (this.animation == DeathAnimationConfig.Animation.BATCH01_BULLET_BILL && DeathAnimationConfig.INSTANCE.visualEffects) {
         float hit = BulletImpactRagdoll.impactPhase(this);
         if (p >= hit && p < hit + 0.035F) {
            rgb = 16773580;
         }
      }

      if (this.animation == DeathAnimationConfig.Animation.STATIC && p > 0.08F && p < 0.9F) {
         int glitch = (int)(p * 110.0F) % 9;
         alpha = glitch != 0 && glitch != 4 ? 255 : 20;
         rgb = glitch == 1 ? 6944255 : (glitch == 5 ? 16740555 : 16777215);
      }

      switch (this.animation) {
         case GHOST:
            alpha = Math.round(255.0F * (1.0F - AnimationTimeline.smooth(p)));
            rgb = 11730933;
         case ANVIL:
         case EXPLOSION:
         case GRAVE:
         default:
            break;
         case HEAVEN:
            alpha = Math.round(255.0F * (1.0F - AnimationTimeline.phase(p, 0.72F, 1.0F)));
            rgb = 16774350;
            break;
         case AIR:
            alpha = Math.round(255.0F * (1.0F - AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.74F, 1.0F))));
            break;
         case VOID:
            alpha = Math.round(255.0F * (1.0F - AnimationTimeline.phase(p, 0.55F, 0.9F)));
            break;
         case TOTEM:
            rgb = 16770682;
            break;
         case FREEZE:
            rgb = 8052735;
            break;
         case ENDER:
            rgb = 13675263;
            break;
         case DUST:
            rgb = 14533527;
            break;
         case OPPONENT:
            if (p > 0.35F && p < 0.55F) {
               rgb = 7851263;
            }
      }

      if (DeathAnimationConfig.INSTANCE.redDeathTint) {
         rgb = rgb & 0xFF0000 | Math.round((rgb >>> 8 & 0xFF) * 0.47F) << 8 | Math.round((rgb & 0xFF) * 0.47F);
      }

      return MathHelper.clamp(alpha, 0, 255) << 24 | rgb;
   }

   public DeathBodyRig.Motion[] rig(BipedEntityModel<?> model) {
      if (this.laserGrid != null) {
         DeathBodyRig.Motion[] still = new DeathBodyRig.Motion[DeathBodyRig.Part.values().length];

         for (int i = 0; i < still.length; i++) {
            still[i] = new DeathBodyRig.Motion(false, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
         }

         return still;
      } else {
         if (this.gold != null) {
            return this.gold.pose();
         }

         if (this.dustfallSand != null && this.dustfallSand.sandScene()) {
            return this.dustfallSand.originalPose();
         }

         if (this.acrobatics != null) {
            return this.acrobatics.pose(model);
         }

         if (this.animation == DeathAnimationConfig.Animation.BATCH01_BULLET_BILL) {
            if (this.bullet == null) {
               this.bullet = new BulletImpactRagdoll(this, model);
            }

            return this.bullet.pose();
         } else if (HangingRagdoll.supports(this.animation)) {
            if (this.hanging == null) {
               this.hanging = new HangingRagdoll(this, model);
            }

            return this.hanging.pose();
         } else {
            DeathBodyRig.Part[] parts = DeathBodyRig.Part.values();
            DeathBodyRig.Motion[] result = new DeathBodyRig.Motion[parts.length];
            float p = this.progress();
            if (this.usesFragments()) {
               this.simulate(model);

               for (DeathBodyRig.Part part : parts) {
                  BodyFragment fragment = this.fragments[part.ordinal()];
                  if (fragment == null) {
                     result[part.ordinal()] = this.attached(0.0F, 0.0F, 0.0F);
                  } else {
                     float partial = MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(false);
                     Vec3d world = fragment.previous.lerp(fragment.position, partial);
                     ModelPart renderedPart = DeathBodyRig.part(model, part);
                     ModelTransform renderedInitial = renderedPart.getDefaultTransform();
                     Vector3f renderedCenter = DeathBodyRig.geometry(renderedPart)
                        .center()
                        .add(renderedInitial.x() / 16.0F, renderedInitial.y() / 16.0F, renderedInitial.z() / 16.0F);
                     Vector3f local = this.toModel(world).sub(renderedCenter);
                     Quaternionf bodyRotation = new Quaternionf()
                        .rotationZYX(fragment.previousRotation.z, fragment.previousRotation.y, fragment.previousRotation.x)
                        .slerp(new Quaternionf().rotationZYX(fragment.rotation.z, fragment.rotation.y, fragment.rotation.x), partial);
                     if (this.animation == DeathAnimationConfig.Animation.BATCH01_BANK_SHOT
                        || this.animation == DeathAnimationConfig.Animation.FULL_1323_WASHING_MACHINE_SPIN_CYCLE) {
                        DeathScene.Root anchor = this.root();
                        Quaternionf rootRotation = new Quaternionf()
                           .rotationY((float)Math.toRadians(anchor.yaw()))
                           .rotateX((float)Math.toRadians(anchor.pitch()))
                           .rotateZ((float)Math.toRadians(anchor.roll()));
                        rootRotation.conjugate().mul(bodyRotation);
                     }

                     Vector3f rotation = bodyRotation.getEulerAnglesZYX(new Vector3f());
                     result[part.ordinal()] = new DeathBodyRig.Motion(true, local.x, local.y, local.z, rotation.x, rotation.y, rotation.z);
                     if (this.animation == DeathAnimationConfig.Animation.BATCH01_BANK_SHOT && part == DeathBodyRig.Part.HEAD) {
                        result[part.ordinal()] = new DeathBodyRig.Motion(false, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -1.0F, 0.0F);
                     }
                  }
               }

               return result;
            } else {
               for (DeathBodyRig.Part part : parts) {
                  int index = part.ordinal();
                  float arm = part == DeathBodyRig.Part.LEFT_ARM ? -1.0F : 1.0F;
                  float side = index != 2 && index != 4 ? 1.0F : -1.0F;
                  boolean hand = index == 2 || index == 3;
                  boolean leg = index >= 4;
                  if (AnimationCatalog.contains(this.animation)) {
                     result[index] = AnimationCatalog.pose(this, index);
                  } else {
                     result[index] = switch (this.animation) {
                        case RAGDOLL -> this.attached(
                           part == DeathBodyRig.Part.HEAD ? -0.1F : 0.15F * this.variant, 0.0F, index != 2 && index != 3 ? 0.0F : arm * 0.28F
                        );
                        case GHOST -> this.degrees(
                           index == 0
                              ? -12.0F * AnimationTimeline.smooth(p)
                              : (hand ? 12.0F * (float)Math.sin(p * 5.0F + side) : (leg ? side * 7.0F * (float)Math.sin(p * 4.0F) : 0.0F)),
                           index == 0 ? 8.0F * (float)Math.sin(p * 3.0F) : 0.0F,
                           hand ? side * (12.0F + 15.0F * AnimationTimeline.smooth(p)) : 0.0F
                        );
                        case ANVIL -> {
                           float flinch = AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.18F, 0.33F));
                           float impact = AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.33F, 0.45F));
                           yield this.degrees(
                              index == 0 ? -24.0F * flinch + 42.0F * impact : (hand ? -48.0F * flinch - 27.0F * impact : (leg ? -side * 10.0F * impact : 0.0F)),
                              index == 0 ? 7.0F * impact : 0.0F,
                              hand ? side * (18.0F * flinch + 22.0F * impact) : (leg ? side * 10.0F * impact : 0.0F)
                           );
                        }
                        case EXPLOSION -> {
                           float tension = AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.06F, 0.35F));
                           yield this.degrees(
                              index == 0 ? 18.0F * tension : (hand ? -58.0F * tension : (leg ? -side * 7.0F * tension : 0.0F)),
                              hand ? -side * 12.0F * tension : 0.0F,
                              hand ? side * 18.0F * tension : (leg ? side * 6.0F * tension : 0.0F)
                           );
                        }
                        case HEAVEN -> this.degrees(
                           index == 0
                              ? -16.0F * AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.15F, 0.5F))
                              : (
                                 hand
                                    ? -35.0F * AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.1F, 0.45F))
                                    : (leg ? side * 8.0F * (float)Math.sin(p * 4.0F) * AnimationTimeline.smooth(p) : 0.0F)
                              ),
                           index == 0 ? 5.0F * (float)Math.sin(p * 3.0F) : 0.0F,
                           hand ? side * (25.0F * AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.08F, 0.4F))) : 0.0F
                        );
                        case AIR -> this.degrees(
                           index == 0
                              ? -18.0F * AnimationTimeline.smooth(p)
                              : (
                                 hand
                                    ? (35.0F + side * 10.0F) * AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.08F, 0.25F))
                                    : (
                                       leg
                                          ? side * 20.0F * (float)Math.sin(p * 5.0F) * AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.12F, 0.3F))
                                          : 0.0F
                                    )
                              ),
                           index == 0 ? side * 12.0F * AnimationTimeline.smooth(p) : 0.0F,
                           hand ? side * (22.0F + 14.0F * (float)Math.sin(p * 6.0F)) : (leg ? side * 10.0F : 0.0F)
                        );
                        default -> this.attached(0.0F, 0.0F, 0.0F);
                        case TOTEM -> this.recall(model, part, p);
                        case FREEZE -> {
                           float shiver = (float)Math.sin(p * 65.0F)
                              * (1.0F - AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.4F, 0.7F)))
                              * AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.0F, 0.15F));
                           yield this.degrees(
                              index == 0
                                 ? 8.0F * AnimationTimeline.smooth(p) + shiver * 3.0F
                                 : (hand ? -32.0F * AnimationTimeline.smooth(p) + side * shiver * 4.0F : (leg ? side * shiver * 3.0F : 0.0F)),
                              index == 0 ? shiver * 5.0F : 0.0F,
                              hand ? side * 8.0F * AnimationTimeline.smooth(p) : 0.0F
                           );
                        }
                        case ENDER -> this.split(index, p);
                        case DUST -> this.dust(index, p);
                        case OPPONENT -> this.degrees(
                           index == 0
                              ? 12.0F * (float)Math.sin(p * 5.0F) * (1.0F - AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.6F, 1.0F)))
                              : (
                                 hand
                                    ? -25.0F
                                       * AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.62F, 0.8F))
                                       * (1.0F - 0.6F * AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.8F, 1.0F)))
                                    : 0.0F
                              ),
                           index == 0 ? 18.0F * (float)Math.sin(p * 4.0F) * (1.0F - AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.7F, 1.0F))) : 0.0F,
                           hand
                              ? side * 20.0F * AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.65F, 0.9F))
                              : (leg ? side * 6.0F * AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.7F, 1.0F)) : 0.0F)
                        );
                     };
                  }
               }

               return result;
            }
         }
      }
   }

   private DeathBodyRig.Motion attached(float rx, float ry, float rz) {
      return new DeathBodyRig.Motion(false, 0.0F, 0.0F, 0.0F, rx, ry, rz);
   }

   private DeathBodyRig.Motion degrees(float rx, float ry, float rz) {
      return this.attached((float)Math.toRadians(rx), (float)Math.toRadians(ry), (float)Math.toRadians(rz));
   }

   private DeathBodyRig.Motion split(int index, float p) {
      float start = 0.14F + index * 0.045F;
      float move = AnimationTimeline.smooth(AnimationTimeline.phase(p, start, start + 0.4F));
      return new DeathBodyRig.Motion(
         true,
         (index % 2 == 0 ? -1 : 1) * 1.5F * move,
         -0.25F * move,
         (index % 3 - 1) * 0.3F * move,
         0.0F,
         (index % 2 == 0 ? -1 : 1) * 0.25F * move,
         0.0F,
         -1.0F,
         1.0F - AnimationTimeline.smooth(AnimationTimeline.phase(p, start + 0.22F, start + 0.48F))
      );
   }

   private DeathBodyRig.Motion dust(int index, float p) {
      float start = switch (index) {
         case 0 -> 0.12F;
         case 1 -> 0.39F;
         case 2, 3 -> 0.27F;
         default -> 0.55F;
      };
      float fade = AnimationTimeline.smooth(AnimationTimeline.phase(p, start, start + 0.3F));
      return new DeathBodyRig.Motion(true, (index % 2 == 0 ? -1 : 1) * 0.15F * fade, 0.25F * fade, 0.0F, 0.1F * fade, 0.0F, 0.0F, -1.0F, 1.0F - fade);
   }

   private DeathBodyRig.Motion recall(BipedEntityModel<?> model, DeathBodyRig.Part part, float p) {
      ModelPart modelPart = DeathBodyRig.part(model, part);
      ModelTransform initial = modelPart.getDefaultTransform();
      Vector3f center = DeathBodyRig.geometry(modelPart).center();
      center.add(initial.x() / 16.0F, initial.y() / 16.0F, initial.z() / 16.0F);
      float move = AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.22F, 0.68F));
      return new DeathBodyRig.Motion(
         true,
         -center.x * move,
         (0.48F - center.y) * move,
         -center.z * move,
         0.0F,
         0.0F,
         0.0F,
         -1.0F,
         1.0F - AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.28F, 0.68F))
      );
   }

   private void simulate(BipedEntityModel<?> model) {
      float simulationStart = this.fragmentStart() * AnimationTimeline.ticks(this.animation);
      int target = Math.max(0, (int)(this.age - simulationStart));
      if (this.fragments == null || this.simulated > target + 1) {
         this.ragdoll = null;
         this.fragments = new BodyFragment[6];
         this.geometry = new DeathBodyRig.Geometry[6];
         this.centers = new Vector3f[6];
         this.simulated = 0;

         for (DeathBodyRig.Part part : DeathBodyRig.Part.values()) {
            ModelPart modelPart = DeathBodyRig.part(model, part);
            this.geometry[part.ordinal()] = DeathBodyRig.geometry(modelPart);
            ModelTransform initial = modelPart.getDefaultTransform();
            this.centers[part.ordinal()] = new Vector3f(initial.x(), initial.y(), initial.z()).div(16.0F).add(this.geometry[part.ordinal()].center());
         }
      }

      if (this.isJointed() && this.ragdoll == null) {
         Vector3f[] pivots = new Vector3f[6];
         Vec3d inherited = this.animation == DeathAnimationConfig.Animation.MOMENTUM_RAGDOLL ? this.inheritedVelocity : Vec3d.ZERO;
         if (inherited.lengthSquared() > 1.44) {
            inherited = inherited.normalize().multiply(1.2);
         }

         for (DeathBodyRig.Part part : DeathBodyRig.Part.values()) {
            int i = part.ordinal();
            ModelTransform initial = DeathBodyRig.part(model, part).getDefaultTransform();
            pivots[i] = new Vector3f(initial.x(), initial.y(), initial.z()).div(16.0F);
            double tilt = i == 0 ? 0.17 : (i == 1 ? 0.11 : (i < 4 ? 0.08 : 0.015));
            Vec3d impulse = new Vec3d(Math.cos(Math.toRadians(this.heading)) * tilt, 0.0, Math.sin(Math.toRadians(this.heading)) * tilt);
            float initialYaw = this.animation != DeathAnimationConfig.Animation.BATCH01_BANK_SHOT
                  && this.animation != DeathAnimationConfig.Animation.FULL_1323_WASHING_MACHINE_SPIN_CYCLE
               ? 180.0F - this.avatar.bodyYaw
               : this.root().yaw();
            this.fragments[i] = new BodyFragment(
               this.toWorld(this.centers[i]),
               inherited.add(impulse),
               new Vector3f(),
               new Vector3f(0.0F, (float)Math.toRadians(initialYaw), 0.0F),
               this.geometry[i],
               (float)Math.toRadians(180.0F - this.avatar.bodyYaw),
               0.9375F
            );
         }

         this.ragdoll = new JointedRagdoll(this.fragments, this.centers, pivots);
         if (this.animation == DeathAnimationConfig.Animation.LIGHTNING) {
            Vec3d jolt = this.rotateToWorld(new Vector3f(0.04F, 0.02F, -0.1F)).multiply(30.0);
            this.ragdoll.impulse(1, jolt, new Vec3d(0.0, 0.18, 0.0));
         }

         if (this.animation == DeathAnimationConfig.Animation.BATCH01_FREDDY_SHUTDOWN) {
            this.ragdoll.detach(DeathBodyRig.Part.HEAD.ordinal());
            Vec3d bite = this.rotateToWorld(new Vector3f(0.0F, -0.1F, 0.8F)).multiply(9.0);
            this.ragdoll.impulse(DeathBodyRig.Part.TORSO.ordinal(), bite, new Vec3d(0.0, 0.18, 0.0));
         }

         if (this.animation == DeathAnimationConfig.Animation.FULL_1323_WASHING_MACHINE_SPIN_CYCLE) {
            this.ragdoll.impulse(1, WashingMachineCycle.initialImpulse(this), new Vec3d(0.0, 0.08, 0.0));
         }
      }

      for (; this.simulated <= target; this.simulated++) {
         if (this.isJointed()) {
            if (this.animation == DeathAnimationConfig.Animation.FULL_1323_WASHING_MACHINE_SPIN_CYCLE) {
               WashingMachineCycle.applyDrumMotion(this, this.ragdoll, this.fragments);
               this.ragdoll.tick(WashingMachineCycle.colliders(this));
               WashingMachineCycle.contain(this, this.ragdoll, this.fragments);
            } else {
               this.ragdoll.tick(this.level, this.gui || !DeathAnimationConfig.INSTANCE.physicsDebris, this.y);
            }

            if (this.animation == DeathAnimationConfig.Animation.BATCH01_FREDDY_SHUTDOWN
               && (simulationStart + this.simulated) / AnimationTimeline.ticks(this.animation) <= 0.72F) {
               this.ragdoll.placePart(DeathBodyRig.Part.HEAD.ordinal(), FredbearBite.heldHeadPosition(this));
            }
         } else {
            for (DeathBodyRig.Part part : DeathBodyRig.Part.values()) {
               int i = part.ordinal();
               float var10000;
               if (this.animation == DeathAnimationConfig.Animation.GORY_EXPLOSION) {
                  var10000 = 0.08F;
               } else {
                  switch (part) {
                     case LEFT_ARM:
                        var10000 = 0.3F;
                        break;
                     case RIGHT_ARM:
                        var10000 = 0.39F;
                        break;
                     case LEFT_LEG:
                        var10000 = 0.48F;
                        break;
                     case RIGHT_LEG:
                        var10000 = 0.57F;
                        break;
                     case HEAD:
                        var10000 = 0.66F;
                        break;
                     case TORSO:
                        var10000 = 0.75F;
                        break;
                     default:
                        throw new MatchException(null, null);
                  }
               }

               float start = var10000;
               if (!(this.simulated < start * AnimationTimeline.ticks(this.animation))) {
                  if (this.fragments[i] == null) {
                     Random random = new Random(this.seed + i * 179L);
                     Vector3f outward = new Vector3f(
                        this.centers[i].x >= 0.0F ? 1.0F : -1.0F, 0.0F, i < 2 ? (i == 0 ? -1.0F : 0.2F) : (i >= 4 ? 0.65F : (i == 2 ? -0.4F : 0.4F))
                     );
                     float force = this.animation == DeathAnimationConfig.Animation.GORY_EXPLOSION ? 0.13F : 0.1F;
                     Vec3d velocity = this.rotateToWorld(outward.mul(force));
                     velocity = velocity.add(0.0, this.animation == DeathAnimationConfig.Animation.GORY_EXPLOSION ? 0.17 + random.nextFloat() * 0.09 : 0.0, 0.0);
                     Vector3f spin = new Vector3f((random.nextFloat() - 0.5F) * 0.22F, (random.nextFloat() - 0.5F) * 0.15F, (random.nextFloat() - 0.5F) * 0.18F);
                     Vector3f rest = new Vector3f(part == DeathBodyRig.Part.HEAD ? 0.0F : 1.5707964F, (random.nextFloat() - 0.5F) * 1.2F, 0.0F);
                     this.fragments[i] = new BodyFragment(
                        this.toWorld(this.centers[i]), velocity, spin, rest, this.geometry[i], (float)Math.toRadians(180.0F - this.avatar.bodyYaw), 0.9375F
                     );
                  } else {
                     this.fragments[i].tick(this.level, this.gui || !DeathAnimationConfig.INSTANCE.physicsDebris, this.y, 1.0F);
                  }
               }
            }

            if (!this.isJointed()) {
               for (int i = 0; i < 6; i++) {
                  for (int j = i + 1; j < 6; j++) {
                     if (this.fragments[i] != null && this.fragments[j] != null) {
                        BodyFragment.collidePair(
                           this.fragments[i], this.fragments[j], this.level, this.gui || !DeathAnimationConfig.INSTANCE.physicsDebris, this.y
                        );
                     }
                  }
               }
            }
         }
      }

      this.simulated = target + 1;
      if (this.animation == DeathAnimationConfig.Animation.BATCH01_FREDDY_SHUTDOWN && this.progress() <= 0.72F) {
         this.ragdoll.placePart(DeathBodyRig.Part.HEAD.ordinal(), FredbearBite.heldHeadPosition(this));
      }
   }

   public HangingRagdoll hanging() {
      if (!HangingRagdoll.supports(this.animation)) {
         return null;
      }

      if (this.hanging == null
         && MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(this.avatar) instanceof LivingEntityRenderer<?, ?, ?> living
         && living.getModel() instanceof BipedEntityModel<?> model) {
         this.hanging = new HangingRagdoll(this, model);
      }

      return this.hanging;
   }

   public BulletImpactRagdoll bullet() {
      if (this.animation != DeathAnimationConfig.Animation.BATCH01_BULLET_BILL) {
         return null;
      }

      if (this.bullet == null
         && MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(this.avatar) instanceof LivingEntityRenderer<?, ?, ?> living
         && living.getModel() instanceof BipedEntityModel<?> model) {
         this.bullet = new BulletImpactRagdoll(this, model);
      }

      return this.bullet;
   }

   public GoldStatue gold() {
      return this.gold;
   }

   ShellGame shellGame() {
      return this.shellGame;
   }

   public boolean interactShellGame(AbstractClientPlayerEntity player) {
      return this.shellGame != null && player.getEntityPos().squaredDistanceTo(this.x, this.floorY, this.z) < 64.0 && this.shellGame.interact(player);
   }

   public LaserGridSegmentation laserGrid() {
      return this.laserGrid;
   }

   public boolean interruptAcrobatics(Vec3d impulse, Vec3d point) {
      if (this.acrobatics == null) {
         return false;
      }

      this.acrobatics.applyImpulse(impulse, point);
      return true;
   }

   public boolean interactAcrobatics(AbstractClientPlayerEntity player) {
      return this.acrobatics != null && this.acrobatics.interact(player);
   }

   boolean acrobaticsActive() {
      return this.acrobatics != null && this.acrobatics.active();
   }

   float acrobaticsInterruptedAtAge() {
      return this.acrobatics == null ? -1.0F : this.acrobatics.interruptedAtAge();
   }

   public boolean isJointed() {
      return this.animation == DeathAnimationConfig.Animation.PHYSICS_RAGDOLL
         || this.animation == DeathAnimationConfig.Animation.MOMENTUM_RAGDOLL
         || this.animation == DeathAnimationConfig.Animation.LIGHTNING
         || this.animation == DeathAnimationConfig.Animation.OPPONENT
         || this.animation == DeathAnimationConfig.Animation.BATCH01_FREDDY_SHUTDOWN
         || this.animation == DeathAnimationConfig.Animation.FULL_1323_WASHING_MACHINE_SPIN_CYCLE;
   }

   private float fragmentStart() {
      return this.animation == DeathAnimationConfig.Animation.LIGHTNING
         ? 0.24F
         : (
            this.animation == DeathAnimationConfig.Animation.OPPONENT
               ? 0.62F
               : (
                  this.animation == DeathAnimationConfig.Animation.BATCH01_FREDDY_SHUTDOWN
                     ? 0.565F
                     : (this.animation == DeathAnimationConfig.Animation.FULL_1323_WASHING_MACHINE_SPIN_CYCLE ? 0.43F : 0.0F)
               )
         );
   }

   private boolean usesFragments() {
      return this.progress() < this.fragmentStart()
         ? false
         : this.isJointed() || this.animation == DeathAnimationConfig.Animation.BURN || this.animation == DeathAnimationConfig.Animation.GORY_EXPLOSION;
   }

   public List<BodyFragment> pieces() {
      if (this.progress() < this.fragmentStart()) {
         return List.of();
      } else if (this.acrobatics != null && this.acrobatics.active()) {
         return this.acrobatics.pieces();
      } else if (this.bullet != null) {
         return this.bullet.pieces();
      } else {
         return this.fragments == null ? List.of() : Arrays.stream(this.fragments).filter(Objects::nonNull).toList();
      }
   }

   private Vec3d rotateToWorld(Vector3f model) {
      model.x = -model.x;
      new Quaternionf().rotationY((float)Math.toRadians(180.0F - this.avatar.bodyYaw)).transform(model);
      return new Vec3d(model.x, model.y, model.z);
   }

   private Vec3d toWorld(Vector3f center) {
      Vector3f value = new Vector3f(center.x * -0.9375F, (center.y - 1.501F) * -0.9375F, center.z * 0.9375F);
      new Quaternionf().rotationY((float)Math.toRadians(180.0F - this.avatar.bodyYaw)).transform(value);
      if (this.animation == DeathAnimationConfig.Animation.BATCH01_BANK_SHOT
         || this.animation == DeathAnimationConfig.Animation.FULL_1323_WASHING_MACHINE_SPIN_CYCLE) {
         DeathScene.Root anchor = this.root();
         Quaternionf rotation = new Quaternionf()
            .rotationY((float)Math.toRadians(anchor.yaw()))
            .rotateX((float)Math.toRadians(anchor.pitch()))
            .rotateZ((float)Math.toRadians(anchor.roll()));
         if (this.animation == DeathAnimationConfig.Animation.FULL_1323_WASHING_MACHINE_SPIN_CYCLE) {
            value.mul(anchor.sx(), anchor.sy(), anchor.sz());
            value.y = value.y - anchor.pivotY();
            rotation.transform(value);
            value.y = value.y + anchor.pivotY();
         } else {
            rotation.transform(value);
         }

         value.add(anchor.x(), anchor.y(), anchor.z());
      }

      return new Vec3d(this.x + value.x, this.y + value.y, this.z + value.z);
   }

   private Vector3f toModel(Vec3d world) {
      Vector3f value = new Vector3f((float)(world.x - this.x), (float)(world.y - this.y), (float)(world.z - this.z));
      if (this.animation == DeathAnimationConfig.Animation.BATCH01_BANK_SHOT
         || this.animation == DeathAnimationConfig.Animation.FULL_1323_WASHING_MACHINE_SPIN_CYCLE) {
         DeathScene.Root anchor = this.root();
         value.sub(anchor.x(), anchor.y(), anchor.z());
         Quaternionf rotation = new Quaternionf()
            .rotationY((float)Math.toRadians(anchor.yaw()))
            .rotateX((float)Math.toRadians(anchor.pitch()))
            .rotateZ((float)Math.toRadians(anchor.roll()));
         if (this.animation == DeathAnimationConfig.Animation.FULL_1323_WASHING_MACHINE_SPIN_CYCLE) {
            value.y = value.y - anchor.pivotY();
         }

         rotation.conjugate().transform(value);
         if (this.animation == DeathAnimationConfig.Animation.FULL_1323_WASHING_MACHINE_SPIN_CYCLE) {
            value.y = value.y + anchor.pivotY();
            value.div(anchor.sx(), anchor.sy(), anchor.sz());
         }
      }

      new Quaternionf().rotationY((float)Math.toRadians(this.avatar.bodyYaw - 180.0F)).transform(value);
      return value.div(-0.9375F, -0.9375F, 0.9375F).add(0.0F, 1.501F, 0.0F);
   }

   @Environment(EnvType.CLIENT)
   public record Root(float x, float y, float z, float pitch, float yaw, float roll, float sx, float sy, float sz, float pivotY) {
      public static final DeathScene.Root STILL = new DeathScene.Root(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);

      public Root(float x, float y, float z, float pitch, float yaw, float roll, float sx, float sy, float sz) {
         this(x, y, z, pitch, yaw, roll, sx, sy, sz, 0.0F);
      }

      public Root(float x, float y, float z, float pitch, float yaw, float roll) {
         this(x, y, z, pitch, yaw, roll, 1.0F, 1.0F, 1.0F);
      }
   }
}
