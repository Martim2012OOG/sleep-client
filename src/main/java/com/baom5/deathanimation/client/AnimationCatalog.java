package com.baom5.deathanimation.client;

import com.baom5.deathanimation.mixin.client.DisplayStateAccess;
import com.google.gson.Gson;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.decoration.DisplayEntity.BillboardMode;
import net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.entity.decoration.DisplayEntity.TextDisplayEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class AnimationCatalog {
   private static final Map<DeathAnimationConfig.Animation, AnimationCatalog.Recipe> RECIPES = load();

   private AnimationCatalog() {
   }

   private static Map<DeathAnimationConfig.Animation, AnimationCatalog.Recipe> load() {
      EnumMap<DeathAnimationConfig.Animation, AnimationCatalog.Recipe> recipes = new EnumMap<>(DeathAnimationConfig.Animation.class);

      try (InputStream stream = AnimationCatalog.class.getResourceAsStream("/assets/deathanimation/animations/catalog.json")) {
         if (stream == null) {
            throw new IllegalStateException("Missing animation catalog");
         }

         for (AnimationCatalog.Recipe recipe : (AnimationCatalog.Recipe[])new Gson()
            .fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), AnimationCatalog.Recipe[].class)) {
            DeathAnimationConfig.Animation animation = DeathAnimationConfig.Animation.valueOf(recipe.id);
            if (!ReleaseProfile.allows(animation)) {
               throw new IllegalStateException("Excluded choreography packaged: " + recipe.id);
            }

            if (recipe.keys == null || recipe.keys.size() < 2 || recipe.ticks <= 0 || recipes.put(animation, recipe) != null) {
               throw new IllegalStateException("Invalid or duplicate choreography: " + recipe.id);
            }
         }

         return recipes;
      } catch (Exception exception) {
         throw new IllegalStateException("Could not read death animation choreography", exception);
      }
   }

   public static boolean contains(DeathAnimationConfig.Animation animation) {
      return RECIPES.containsKey(animation);
   }

   public static AnimationCatalog.Recipe recipe(DeathAnimationConfig.Animation animation) {
      return RECIPES.get(animation);
   }

   public static Item icon(DeathAnimationConfig.Animation animation) {
      return (Item)Registries.ITEM.get(Identifier.ofVanilla(recipe(animation).icon));
   }

   public static DeathScene.Root root(DeathScene scene) {
      if (scene.animation == DeathAnimationConfig.Animation.PILOT_CARPET_ROLL) {
         return CarpetPlayerWrap.root(scene);
      }

      if (scene.animation == DeathAnimationConfig.Animation.BATCH01_AIRLOCK_VERDICT) {
         return AmongUsEjected.root(scene);
      }

      if (scene.animation == DeathAnimationConfig.Animation.BATCH01_IMPOSTOR_KILL && scene.progress() >= 0.49F) {
         return AmongUsImpostorKill.root(scene);
      }

      if (scene.animation == DeathAnimationConfig.Animation.PILOT_NESTED_DOLLS) {
         return NestedDollClose.root(scene);
      }

      if (scene.animation == DeathAnimationConfig.Animation.BAN_HAMMER) {
         return new DeathScene.Root(0.0F, (float)(scene.floorY - scene.y), 0.0F, 0.0F, scene.sceneFacing, 0.0F);
      }

      if (scene.animation == DeathAnimationConfig.Animation.FULL_0118_INHALE_AND_STAR_SPIT) {
         return KirbyInhaleStarSpit.root(scene);
      }

      if (scene.animation == DeathAnimationConfig.Animation.FULL_1322_PHOTOCOPIER_TONER_TRANSFER) {
         return PhotocopierMassProduction.root(scene);
      }

      if (scene.animation == DeathAnimationConfig.Animation.FULL_1331_PAPER_SHREDDER_STRIP) {
         return PaperShredderStrip.root(scene);
      }

      if (scene.animation == DeathAnimationConfig.Animation.FULL_1323_WASHING_MACHINE_SPIN_CYCLE) {
         return WashingMachineCycle.root(scene);
      }

      if (scene.animation == DeathAnimationConfig.Animation.FULL_0899_DOMINO_BRIDGE_COLLAPSE) {
         return DominoBridgeCollapse.root(scene);
      }

      if (scene.animation == DeathAnimationConfig.Animation.BATCH01_BOWLING_SPLIT) {
         return Acrobatics.adjust(scene, BowlingSplit.root(scene));
      }

      if (scene.animation == DeathAnimationConfig.Animation.FULL_0905_CLAW_MACHINE_PRIZE_DROP) {
         return ClawMachinePrizeDrop.root(scene);
      }

      if (scene.animation == DeathAnimationConfig.Animation.FULL_1321_PAPER_AIRPLANE_FOLD) {
         return PaperAirplaneFold.root(scene);
      }

      if (scene.animation == DeathAnimationConfig.Animation.BATCH01_BANK_SHOT) {
         return BankShot.root(scene);
      }

      if (scene.animation == DeathAnimationConfig.Animation.PILOT_SHELL_GAME) {
         return ShellGame.root(scene);
      }

      if (scene.animation == DeathAnimationConfig.Animation.FULL_1338_PLAYER_DEFLATE) {
         return PlayerDeflate.root(scene);
      }

      if (HangingRagdoll.supports(scene.animation) || scene.animation == DeathAnimationConfig.Animation.BATCH01_BULLET_BILL) {
         return DeathScene.Root.STILL;
      }

      if (scene.animation == DeathAnimationConfig.Animation.PILOT_CALENDAR) {
         return CalendarAgeOut.root(scene);
      }

      DeathScene.Root root = scene.animation == DeathAnimationConfig.Animation.WHIRLPOOL
         ? WhirlpoolMotion.root(scene)
         : sample(recipe(scene.animation).keys, scene.progress());
      if (recipe(scene.animation).grounded) {
         float ground = (float)(scene.floorY - scene.y) * AnimationTimeline.smooth(AnimationTimeline.phase(scene.progress(), 0.0F, 0.16F));
         root = new DeathScene.Root(root.x(), root.y() + ground, root.z(), root.pitch(), root.yaw(), root.roll(), root.sx(), root.sy(), root.sz());
      }

      if (scene.animation == DeathAnimationConfig.Animation.BATCH01_DRONE_ELIMINATION) {
         float ground = (float)(scene.floorY - scene.y) * AnimationTimeline.smooth(AnimationTimeline.phase(scene.progress(), 0.0F, 0.18F));
         root = new DeathScene.Root(root.x(), root.y() + ground, root.z(), root.pitch(), root.yaw(), root.roll(), root.sx(), root.sy(), root.sz());
      }

      if (recipe(scene.animation).sceneAligned) {
         Vector3f position = new Quaternionf().rotationY((float)Math.toRadians(scene.sceneFacing)).transform(new Vector3f(root.x(), root.y(), root.z()));
         root = new DeathScene.Root(
            position.x, position.y, position.z, root.pitch(), root.yaw() + scene.sceneFacing, root.roll(), root.sx(), root.sy(), root.sz()
         );
      }

      return Acrobatics.adjust(scene, root);
   }

   public static DeathScene.Root sample(List<AnimationCatalog.Key> keys, float p) {
      int next = 0;

      while (next < keys.size() - 1 && keys.get(next).p < p) {
         next++;
      }

      int current = Math.max(0, next - 1);
      AnimationCatalog.Key left = keys.get(current);
      AnimationCatalog.Key right = keys.get(next);
      if ("flow".equals(right.curve) && next != current) {
         AnimationCatalog.Key previous = keys.get(Math.max(0, current - 1));
         AnimationCatalog.Key following = keys.get(Math.min(keys.size() - 1, next + 1));
         float t = AnimationTimeline.phase(p, left.p, right.p);
         return new DeathScene.Root(
            flow(previous, left, right, following, t, 0),
            flow(previous, left, right, following, t, 1),
            flow(previous, left, right, following, t, 2),
            flow(previous, left, right, following, t, 3),
            flow(previous, left, right, following, t, 4),
            flow(previous, left, right, following, t, 5),
            flow(previous, left, right, following, t, 6),
            flow(previous, left, right, following, t, 7),
            flow(previous, left, right, following, t, 8)
         );
      }

      AnimationCatalog.Key a = keys.getFirst();
      AnimationCatalog.Key b = a;

      for (AnimationCatalog.Key key : keys) {
         b = key;
         if (key.p >= p) {
            break;
         }

         a = key;
      }

      float t = a == b ? 0.0F : AnimationTimeline.phase(p, a.p, b.p);

      t = switch (b.curve == null ? "smooth" : b.curve) {
         case "linear" -> t;
         case "hold" -> p >= b.p ? 1.0F : 0.0F;
         case "in" -> t * t;
         case "out" -> 1.0F - (1.0F - t) * (1.0F - t);
         default -> AnimationTimeline.smooth(t);
      };
      return new DeathScene.Root(
         lerp(a.x, b.x, t),
         lerp(a.y, b.y, t),
         lerp(a.z, b.z, t),
         lerp(a.pitch, b.pitch, t),
         lerp(a.yaw, b.yaw, t),
         lerp(a.roll, b.roll, t),
         lerp(a.sx, b.sx, t),
         lerp(a.sy, b.sy, t),
         lerp(a.sz, b.sz, t)
      );
   }

   private static float lerp(float a, float b, float p) {
      return a + (b - a) * p;
   }

   private static float channel(AnimationCatalog.Key k, int channel) {
      return switch (channel) {
         case 0 -> k.x;
         case 1 -> k.y;
         case 2 -> k.z;
         case 3 -> k.pitch;
         case 4 -> k.yaw;
         case 5 -> k.roll;
         case 6 -> k.sx;
         case 7 -> k.sy;
         default -> k.sz;
      };
   }

   private static float tangent(float before, float after, float h0, float h1) {
      if (!(before * after <= 0.0F) && !(h0 <= 0.0F) && !(h1 <= 0.0F)) {
         float w0 = 2.0F * h1 + h0;
         float w1 = h1 + 2.0F * h0;
         return (w0 + w1) / (w0 / before + w1 / after);
      } else {
         return 0.0F;
      }
   }

   private static float flow(AnimationCatalog.Key previous, AnimationCatalog.Key a, AnimationCatalog.Key b, AnimationCatalog.Key following, float t, int c) {
      float x = channel(a, c);
      float y = channel(b, c);
      float h = b.p - a.p;
      float d = (y - x) / h;
      if (x == y) {
         return x;
      }

      float h0 = a.p - previous.p;
      float h1 = following.p - b.p;
      float m0 = h0 > 0.0F && !"hold".equals(a.curve) ? tangent((x - channel(previous, c)) / h0, d, h0, h) : 0.0F;
      float m1 = h1 > 0.0F && !"hold".equals(following.curve) ? tangent(d, (channel(following, c) - y) / h1, h, h1) : 0.0F;
      float t2 = t * t;
      float t3 = t2 * t;
      return (2.0F * t3 - 3.0F * t2 + 1.0F) * x + (t3 - 2.0F * t2 + t) * h * m0 + (-2.0F * t3 + 3.0F * t2) * y + (t3 - t2) * h * m1;
   }

   public static void animateFrame(DeathScene scene) {
      if (contains(scene.animation) && recipe(scene.animation).frameSampled) {
         animate(scene);

         for (Entity entity : scene.props) {
            if (entity instanceof DisplayEntity display) {
               DisplayStateAccess access = (DisplayStateAccess)display;
               access.deathanimations$setRenderState(access.deathanimations$freshRenderState());
               access.deathanimations$refreshSubState(false, 1.0F);
               access.deathanimations$setDirty(false);
               entity.lastX = entity.lastRenderX = entity.getX();
               entity.lastY = entity.lastRenderY = entity.getY();
               entity.lastZ = entity.lastRenderZ = entity.getZ();
            }
         }
      }
   }

   public static DeathBodyRig.Motion pose(DeathScene scene, int part) {
      if (scene.animation == DeathAnimationConfig.Animation.BAN_HAMMER) {
         return new DeathBodyRig.Motion(false, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
      }

      if (scene.animation == DeathAnimationConfig.Animation.FULL_0118_INHALE_AND_STAR_SPIT) {
         return KirbyInhaleStarSpit.pose(scene, part);
      }

      if (scene.animation == DeathAnimationConfig.Animation.FULL_1322_PHOTOCOPIER_TONER_TRANSFER) {
         return PhotocopierMassProduction.pose(scene, part);
      }

      if (scene.animation == DeathAnimationConfig.Animation.FULL_1331_PAPER_SHREDDER_STRIP) {
         return PaperShredderStrip.pose(scene, part);
      }

      if (scene.animation == DeathAnimationConfig.Animation.FULL_1323_WASHING_MACHINE_SPIN_CYCLE) {
         return WashingMachineCycle.pose(scene, part);
      }

      if (scene.animation == DeathAnimationConfig.Animation.FULL_0899_DOMINO_BRIDGE_COLLAPSE) {
         return DominoBridgeCollapse.pose(scene, part);
      }

      if (scene.animation == DeathAnimationConfig.Animation.BATCH01_BOWLING_SPLIT) {
         return BowlingSplit.pose(scene, part);
      }

      if (scene.animation == DeathAnimationConfig.Animation.BATCH01_IMPOSTOR_KILL) {
         return AmongUsImpostorKill.pose(scene, part);
      }

      if (scene.animation == DeathAnimationConfig.Animation.FULL_0905_CLAW_MACHINE_PRIZE_DROP) {
         return ClawMachinePrizeDrop.pose(scene, part);
      }

      if (scene.animation == DeathAnimationConfig.Animation.FULL_1321_PAPER_AIRPLANE_FOLD) {
         return PaperAirplaneFold.pose(scene, part);
      }

      if (scene.animation == DeathAnimationConfig.Animation.FULL_1338_PLAYER_DEFLATE) {
         return PlayerDeflate.pose(scene, part);
      }

      if (scene.animation == DeathAnimationConfig.Animation.PILOT_CALENDAR) {
         return CalendarAgeOut.pose(scene, part);
      }

      if (scene.animation == DeathAnimationConfig.Animation.WHIRLPOOL) {
         return WhirlpoolMotion.pose(scene, part);
      }

      AnimationCatalog.Recipe recipe = recipe(scene.animation);
      if (scene.animation == DeathAnimationConfig.Animation.PILOT_CARPET_ROLL) {
         return CarpetRoll.pose(scene, part);
      }

      if (scene.animation == DeathAnimationConfig.Animation.BATCH01_BANK_SHOT) {
         return BankShot.pose(scene, part);
      }

      if (recipe.limbs != null) {
         for (AnimationCatalog.PartTrack track : recipe.limbs) {
            if (track.part == part) {
               DeathScene.Root key = sample(track.keys, scene.progress());
               float alpha = track.visibleUntil > 0.0F
                  ? 1.0F - AnimationTimeline.smooth(AnimationTimeline.phase(scene.progress(), Math.max(0.0F, track.visibleUntil - 0.08F), track.visibleUntil))
                  : 1.0F;
               return new DeathBodyRig.Motion(
                  track.detached,
                  key.x(),
                  key.y(),
                  key.z(),
                  (float)Math.toRadians(key.pitch()),
                  (float)Math.toRadians(key.yaw()),
                  (float)Math.toRadians(key.roll()),
                  track.landEnd > track.landStart ? AnimationTimeline.smooth(AnimationTimeline.phase(scene.progress(), track.landStart, track.landEnd)) : -1.0F,
                  alpha
               );
            }
         }
      }

      String pose = recipe(scene.animation).pose;
      float p = scene.progress();
      float blend = AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.05F, 0.55F));
      float side = part != 2 && part != 4 ? 1.0F : -1.0F;
      boolean arm = part == 2 || part == 3;
      boolean leg = part >= 4;
      float x = 0.0F;
      float y = 0.0F;
      float z = 0.0F;
      float rx = 0.0F;
      float ry = 0.0F;
      float rz = 0.0F;
      switch (pose) {
         case "spread":
            if (arm) {
               rz = side * 1.25F;
            }

            if (leg) {
               rz = side * 0.15F;
            }
            break;
         case "tuck":
            if (arm) {
               rx = -1.6F;
            }

            if (leg) {
               rx = -1.1F;
            }

            if (part == 0) {
               rx = 0.35F;
            }
            break;
         case "kneel":
            if (leg) {
               rx = 1.5F;
            }

            if (arm) {
               rx = -0.5F;
            }
            break;
         case "cross":
            if (leg) {
               rx = -1.25F;
               rz = side * 0.8F;
            }

            if (arm) {
               rx = -0.9F;
               rz = side * 0.25F;
            }
            break;
         case "bow":
            if (part == 0) {
               rx = 0.65F;
            }

            if (arm) {
               rx = -0.5F;
               rz = side * 0.15F;
            }
            break;
         case "balance":
            if (arm) {
               rx = 3.0F;
            }

            if (leg) {
               rz = side * 0.4F;
            }
            break;
         case "puppet":
            if (arm) {
               rz = side * 0.9F;
               rx = -0.4F;
            }

            if (part == 0) {
               rz = 0.3F;
            }
            break;
         case "fold":
            if (arm) {
               rx = -1.5F;
               rz = -side * 0.5F;
            }

            if (leg) {
               rx = -0.8F;
            }

            if (part == 0) {
               rx = 0.45F;
            }
            break;
         case "fright":
            if (arm) {
               rx = -2.0F;
               rz = side * 0.55F;
            }

            if (part == 0) {
               rx = -0.25F;
            }

            if (leg) {
               rz = side * 0.2F;
            }
            break;
         case "hang":
            if (arm) {
               rx = -3.0F;
            }

            if (leg) {
               rx = side * 0.12F;
            }
            break;
         case "pray":
            if (arm) {
               rx = -1.4F;
               rz = -side * 0.5F;
            }

            if (leg) {
               rx = 1.2F;
            }

            if (part == 0) {
               rx = 0.4F;
            }
            break;
         case "melt":
            y = part * 0.06F * p;
            if (arm) {
               rz = side * 0.25F;
            }
            break;
         case "stumble":
            if (arm) {
               rx = -1.3F;
               rz = side * 0.4F;
            }

            if (leg) {
               rx = side * 0.45F;
            }
            break;
         case "swim":
            if (arm) {
               rx = -1.2F;
               rz = side * 0.5F;
            }

            if (leg) {
               rx = side * 0.3F;
            }
            break;
         case "dance":
         case "walk":
         case "flap":
         case "glitch":
            float wave = (float)Math.sin(p * 3.141592653589793 * (pose.equals("glitch") ? 18 : (pose.equals("flap") ? 10 : 6)))
               * (1.0F - AnimationTimeline.phase(p, 0.75F, 1.0F));
            if (arm) {
               rx = side * wave * (pose.equals("walk") ? 0.7F : 1.5F);
            }

            if (leg) {
               rx = -side * wave * 0.6F;
            }

            if (pose.equals("flap") && arm) {
               rz = side * (1.1F + wave * 0.5F);
            }

            if (pose.equals("glitch")) {
               x = wave * 0.05F * side;
               rz = wave * 0.2F;
            }
      }

      return new DeathBodyRig.Motion(false, x * blend, y * blend, z, rx * blend, ry * blend, rz * blend);
   }

   public static float alpha(DeathScene scene) {
      if (scene.shellGame() != null) {
         return scene.shellGame().playerVisible() ? 1.0F : 0.0F;
      } else {
         float until = recipe(scene.animation).visibleUntil;
         Float start = recipe(scene.animation).fadeStart;
         if (until > 1.0F) {
            return 1.0F;
         } else if (start != null && start >= until) {
            return scene.progress() < until ? 1.0F : 0.0F;
         } else {
            return 1.0F - AnimationTimeline.smooth(AnimationTimeline.phase(scene.progress(), start == null ? Math.max(0.1F, until - 0.16F) : start, until));
         }
      }
   }

   public static void spawn(DeathScene scene, Consumer<Entity> add) {
      if (scene.animation == DeathAnimationConfig.Animation.FULL_1322_PHOTOCOPIER_TONER_TRANSFER) {
         PhotocopierMassProduction.start(scene, add);
      } else if (scene.animation == DeathAnimationConfig.Animation.PILOT_NESTED_DOLLS) {
         NestedDollClose.spawn(scene, add);
      } else if (scene.animation == DeathAnimationConfig.Animation.FULL_0118_INHALE_AND_STAR_SPIT) {
         KirbyInhaleStarSpit.spawn(scene, add);
      } else if (scene.animation == DeathAnimationConfig.Animation.PILOT_SHELL_GAME) {
         ShellGame.spawn(scene, add);
      } else if (scene.animation == DeathAnimationConfig.Animation.FULL_1331_PAPER_SHREDDER_STRIP) {
         PaperShredderStrip.spawn(scene, add);
      } else if (scene.animation == DeathAnimationConfig.Animation.FULL_1323_WASHING_MACHINE_SPIN_CYCLE) {
         WashingMachineCycle.spawn(scene, add);
      } else if (scene.animation == DeathAnimationConfig.Animation.FULL_0899_DOMINO_BRIDGE_COLLAPSE) {
         DominoBridgeCollapse.spawn(scene, add);
      } else if (scene.animation == DeathAnimationConfig.Animation.BATCH01_BOWLING_SPLIT) {
         BowlingSplit.spawn(scene, add);
      } else if (scene.animation == DeathAnimationConfig.Animation.FULL_0905_CLAW_MACHINE_PRIZE_DROP) {
         ClawMachinePrizeDrop.spawn(scene, add);
      } else if (scene.animation == DeathAnimationConfig.Animation.FULL_1321_PAPER_AIRPLANE_FOLD) {
         PaperAirplaneFold.spawn(scene, add);
      } else if (scene.animation == DeathAnimationConfig.Animation.PILOT_CALENDAR) {
         CalendarAgeOut.spawn(scene, add);
      } else if (scene.animation == DeathAnimationConfig.Animation.PILOT_CARPET_ROLL) {
         CarpetRoll.spawn(scene, add);
      } else if (scene.animation == DeathAnimationConfig.Animation.BATCH01_BANK_SHOT) {
         BankShot.spawn(scene, add);
      } else if (scene.animation == DeathAnimationConfig.Animation.BATCH01_FREDDY_SHUTDOWN) {
         FredbearBite.spawn(scene, add);
      } else if (scene.animation == DeathAnimationConfig.Animation.BATCH01_IMPOSTOR_KILL) {
         AmongUsImpostorKill.spawn(scene, add);
      } else if (scene.animation != DeathAnimationConfig.Animation.BATCH01_AIRLOCK_VERDICT) {
         for (AnimationCatalog.Cue cue : recipe(scene.animation).props) {
            if (cue.actor != null) {
               PlayerActors.spawn(scene, cue, add);
            } else {
               Entity entity;
               if (cue.text != null) {
                  TextDisplayEntity display = (TextDisplayEntity)EntityType.TEXT_DISPLAY.create(scene.level, SpawnReason.COMMAND);
                  if (display == null) {
                     continue;
                  }

                  display.setText(
                     Text.literal(cue.text.replace("{player}", scene.sourceName))
                        .styled(style -> style.withColor(cue.textColor == null ? 16777215 : cue.textColor).withBold(cue.bold))
                  );
                  display.setTextOpacity((byte)-1);
                  display.setBackground(0);
                  display.setDisplayFlags((byte)3);
                  display.setBillboardMode(scene.gui ? BillboardMode.FIXED : BillboardMode.CENTER);
                  entity = display;
               } else if (cue.item != null) {
                  ItemDisplayEntity display = (ItemDisplayEntity)EntityType.ITEM_DISPLAY.create(scene.level, SpawnReason.COMMAND);
                  if (display == null) {
                     continue;
                  }

                  ItemStack stack = new ItemStack((ItemConvertible)Registries.ITEM.get(Identifier.ofVanilla(cue.item)));
                  if (cue.model != null) {
                     stack.set(DataComponentTypes.ITEM_MODEL, Identifier.of(cue.model));
                     display.setItemDisplayContext(ItemDisplayContext.FIXED);
                  }

                  display.setItemStack(stack);
                  entity = display;
               } else {
                  BlockDisplayEntity display = (BlockDisplayEntity)EntityType.BLOCK_DISPLAY.create(scene.level, SpawnReason.COMMAND);
                  if (display == null) {
                     continue;
                  }

                  BlockState state = ((Block)Registries.BLOCK.get(Identifier.ofVanilla(cue.block))).getDefaultState();
                  if (cue.sampleGround) {
                     AnimationCatalog.Key first = cue.keys.getFirst();
                     int x = MathHelper.floor(scene.x + first.x);
                     int z = MathHelper.floor(scene.z + first.z);
                     BlockPos ground = BlockPos.ofFloored(x, scene.floorY - 0.02, z);

                     for (int depth = 0; depth < 16; depth++) {
                        BlockState found = scene.level.getBlockState(ground.down(depth));
                        if (!found.isAir() && !found.getCollisionShape(scene.level, ground.down(depth)).isEmpty()) {
                           state = found;
                           break;
                        }
                     }
                  }

                  display.setBlockState(state);
                  entity = display;
               }

               if (cue.fullBright) {
                  ((DisplayEntity)entity).setBrightness(new Brightness(15, 15));
               }

               entity.setPosition(scene.x, scene.y, scene.z);
               add.accept(entity);
            }
         }

         animate(scene);
      }
   }

   public static void animate(DeathScene scene) {
      if (scene.animation == DeathAnimationConfig.Animation.PILOT_NESTED_DOLLS) {
         NestedDollClose.animate(scene);
      } else if (scene.animation == DeathAnimationConfig.Animation.FULL_0118_INHALE_AND_STAR_SPIT) {
         KirbyInhaleStarSpit.animate(scene);
      } else if (scene.animation == DeathAnimationConfig.Animation.PILOT_SHELL_GAME) {
         ShellGame.animate(scene);
      } else if (scene.animation == DeathAnimationConfig.Animation.FULL_1331_PAPER_SHREDDER_STRIP) {
         PaperShredderStrip.animate(scene);
      } else if (scene.animation == DeathAnimationConfig.Animation.FULL_1323_WASHING_MACHINE_SPIN_CYCLE) {
         WashingMachineCycle.animate(scene);
      } else if (scene.animation == DeathAnimationConfig.Animation.FULL_0899_DOMINO_BRIDGE_COLLAPSE) {
         DominoBridgeCollapse.animate(scene);
      } else if (scene.animation == DeathAnimationConfig.Animation.BATCH01_BOWLING_SPLIT) {
         BowlingSplit.animate(scene);
      } else if (scene.animation == DeathAnimationConfig.Animation.FULL_0905_CLAW_MACHINE_PRIZE_DROP) {
         ClawMachinePrizeDrop.animate(scene);
      } else if (scene.animation == DeathAnimationConfig.Animation.FULL_1321_PAPER_AIRPLANE_FOLD) {
         PaperAirplaneFold.animate(scene);
      } else if (scene.animation == DeathAnimationConfig.Animation.PILOT_CALENDAR) {
         CalendarAgeOut.animate(scene);
      } else if (scene.animation == DeathAnimationConfig.Animation.PILOT_CARPET_ROLL) {
         CarpetRoll.animate(scene);
      } else if (scene.animation == DeathAnimationConfig.Animation.BATCH01_BANK_SHOT) {
         BankShot.animate(scene);
      } else if (scene.animation == DeathAnimationConfig.Animation.BATCH01_FREDDY_SHUTDOWN) {
         FredbearBite.animate(scene);
      } else if (scene.animation == DeathAnimationConfig.Animation.BATCH01_IMPOSTOR_KILL) {
         AmongUsImpostorKill.animate(scene);
      } else {
         float p = scene.progress();
         List<AnimationCatalog.Cue> cues = recipe(scene.animation).props;

         for (int i = 0; i < Math.min(cues.size(), scene.props.size()); i++) {
            AnimationCatalog.Cue cue = cues.get(i);
            Entity entity = scene.props.get(i);
            DeathScene.Root key = sample(cue.keys, p);
            if (scene.animation == DeathAnimationConfig.Animation.BAN_HAMMER && cue.model != null && cue.model.endsWith("weapons/ban_hammer")) {
               key = BanHammer.swingPose(scene, p);
            }

            if (cue.actor != null) {
               PlayerActors.update(scene, entity, cue, key);
            } else {
               float visible = !(p >= cue.start)
                     || !(p < cue.end)
                     || cue.guiOnly && !scene.gui
                     || cue.visualEffect && !DeathAnimationConfig.INSTANCE.visualEffects
                  ? 1.0E-4F
                  : 1.0F;
               if (cue.actorAnchor == null && !cue.actorBeam) {
                  if (cue.modelFrames != null && entity instanceof ItemDisplayEntity display) {
                     String model = cue.model;

                     for (AnimationCatalog.ModelFrame frame : cue.modelFrames) {
                        if (frame.p > p) {
                           break;
                        }

                        model = frame.model;
                     }

                     Identifier wanted = Identifier.of(model);
                     ItemStack current = display.getItemStack();
                     if (!wanted.equals(current.get(DataComponentTypes.ITEM_MODEL))) {
                        ItemStack replacement = current.copy();
                        replacement.set(DataComponentTypes.ITEM_MODEL, wanted);
                        display.setItemStack(replacement);
                     }
                  }

                  if (HangingRagdoll.supports(scene.animation)) {
                     HangingRagdoll physics = scene.hanging();
                     if (physics != null && physics.animateProp(cue, entity, key, visible)) {
                        continue;
                     }
                  }

                  Vector3f scale = new Vector3f(key.sx() * visible, key.sy() * visible, key.sz() * visible);
                  Quaternionf rotation = new Quaternionf()
                     .rotationZYX((float)Math.toRadians(key.roll()), (float)Math.toRadians(key.yaw()), (float)Math.toRadians(key.pitch()));
                  Vector3f offset = cue.block != null ? rotation.transform(new Vector3f(scale).mul(-0.5F)) : new Vector3f();
                  Vector3f position = new Vector3f(key.x(), key.y(), key.z());
                  if (recipe(scene.animation).grounded) {
                     position.y = position.y
                        + (float)(scene.floorY - scene.y)
                           * (
                              scene.animation == DeathAnimationConfig.Animation.BAN_HAMMER
                                 ? 1.0F
                                 : AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.0F, 0.16F))
                           );
                  }

                  if (cue.sampleGround) {
                     position.x = (float)(Math.floor(scene.x + key.x()) + 0.5 - scene.x);
                     position.z = (float)(Math.floor(scene.z + key.z()) + 0.5 - scene.z);
                  }

                  if (recipe(scene.animation).sceneAligned && !recipe(scene.animation).attach) {
                     Quaternionf facing = new Quaternionf().rotationY((float)Math.toRadians(scene.sceneFacing));
                     facing.transform(position);
                     rotation.premul(facing);
                     offset = cue.block != null ? rotation.transform(new Vector3f(scale).mul(-0.5F)) : new Vector3f();
                  }

                  if (recipe(scene.animation).attach) {
                     DeathScene.Root body = scene.root();
                     Quaternionf bodyRotation = new Quaternionf()
                        .rotationY((float)Math.toRadians(body.yaw()))
                        .rotateX((float)Math.toRadians(body.pitch()))
                        .rotateZ((float)Math.toRadians(body.roll()))
                        .rotateY((float)Math.toRadians(180.0F - scene.avatar.bodyYaw));
                     bodyRotation.transform(position);
                     position.add(body.x(), body.y(), body.z());
                     rotation.premul(bodyRotation);
                     offset = cue.block != null ? rotation.transform(new Vector3f(scale).mul(-0.5F)) : new Vector3f();
                  }

                  if (scene.animation == DeathAnimationConfig.Animation.BAN_HAMMER && cue.model != null && cue.model.endsWith("weapons/ban_hammer")) {
                     rotation.mul(new Quaternionf().rotationY((float)Math.toRadians(180.0)));
                  }

                  if (scene.animation == DeathAnimationConfig.Animation.BATCH01_DRONE_ELIMINATION) {
                     if (cue.dissolvePart != null) {
                        float travel = AnimationTimeline.smooth(AnimationTimeline.phase(p, cue.start, cue.end));
                        Vector3f origin = Acrobatics.partCenter(scene, cue.dissolvePart).add(position);
                        position.set(origin).lerp(FortniteElimination.intake(scene), travel);
                     } else {
                        position.y = position.y + (float)(scene.floorY - scene.y);
                     }
                  }

                  if (cue.text != null) {
                     rotation.identity();
                  }

                  ((DisplayEntity)entity).setTransformation(new AffineTransformation(offset, rotation, scale, new Quaternionf()));
                  entity.setPosition(scene.x + position.x, scene.y + position.y, scene.z + position.z);
                  if (cue.tetherActor() != null && entity instanceof ItemDisplayEntity) {
                     Vec3d endpoint = PlayerActors.actorTetherPoint(scene, cue.tetherActor(), cue.tetherSide() == null ? 0.0F : cue.tetherSide());
                     Vector3f anchor = new Vector3f(
                        cue.anchorX() == null ? 0.0F : cue.anchorX(),
                        cue.anchorY() == null ? 0.0F : cue.anchorY(),
                        cue.anchorZ() == null ? 0.0F : cue.anchorZ()
                     );
                     new Quaternionf().rotationY((float)Math.toRadians(scene.sceneFacing)).transform(anchor);
                     float ground = (float)(scene.floorY - scene.y) * AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.0F, 0.16F));
                     anchor.add((float)scene.x, (float)(scene.y + ground), (float)scene.z);
                     Vector3f from = new Vector3f(anchor.x, anchor.y, anchor.z);
                     Vector3f to = new Vector3f((float)endpoint.x, (float)endpoint.y, (float)endpoint.z);
                     Vector3f direction = new Vector3f(to).sub(from);
                     float length = direction.length();
                     if (length < 1.0E-4F) {
                        direction.set(1.0F, 0.0F, 0.0F);
                        length = 1.0E-4F;
                     } else {
                        direction.div(length);
                     }

                     Quaternionf ropeRotation = new Quaternionf().rotationTo(new Vector3f(1.0F, 0.0F, 0.0F), direction);
                     Vector3f ropeScale = new Vector3f(length * key.sx() * visible, key.sy() * visible, key.sz() * visible);
                     Vector3f midpoint = new Vector3f(from).add(to).mul(0.5F);
                     ((DisplayEntity)entity).setTransformation(new AffineTransformation(new Vector3f(), ropeRotation, ropeScale, new Quaternionf()));
                     entity.setPosition(midpoint.x, midpoint.y, midpoint.z);
                     entity.lastX = entity.lastRenderX = midpoint.x;
                     entity.lastY = entity.lastRenderY = midpoint.y;
                     entity.lastZ = entity.lastRenderZ = midpoint.z;
                  } else if (cue.tetherPart != null) {
                     Vector3f endpoint = Acrobatics.partCenter(scene, cue.tetherPart);
                     Vector3f anchor = new Vector3f(
                        cue.anchorX == null ? 0.0F : cue.anchorX, cue.anchorY == null ? 0.0F : cue.anchorY, cue.anchorZ == null ? 0.0F : cue.anchorZ
                     );
                     new Quaternionf().rotationY((float)Math.toRadians(scene.sceneFacing)).transform(anchor);
                     Vector3f direction = new Vector3f(endpoint).sub(anchor);
                     float distance = direction.length();
                     float length = Math.max(0.001F, distance);
                     if (distance < 1.0E-6F) {
                        direction.set(0.0F, 1.0F, 0.0F);
                     } else {
                        direction.div(distance);
                     }

                     Quaternionf ropeRotation = new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), direction);
                     Vector3f ropeScale = new Vector3f(0.025F * visible, length * visible, 0.025F * visible);
                     Vector3f ropeOffset = ropeRotation.transform(new Vector3f(ropeScale).mul(-0.5F));
                     ((DisplayEntity)entity).setTransformation(new AffineTransformation(ropeOffset, ropeRotation, ropeScale, new Quaternionf()));
                     Vector3f midpoint = anchor.add(endpoint).mul(0.5F);
                     entity.setPosition(scene.x + midpoint.x, scene.y + midpoint.y, scene.z + midpoint.z);
                  }
               } else {
                  boolean rightHandWeapon = "right_hand_weapon".equals(cue.actorAnchor);
                  boolean leftHandWeapon = "left_hand_weapon".equals(cue.actorAnchor);
                  boolean mouthWeapon = "mouth_weapon".equals(cue.actorAnchor);
                  boolean rightHandEffect = "right_hand_effect".equals(cue.actorAnchor);
                  boolean leftHandEffect = "left_hand_effect".equals(cue.actorAnchor);
                  PlayerActors.WeaponPose handPose = mouthWeapon
                     ? PlayerActors.mouthWeaponPose(scene)
                     : (
                        !rightHandWeapon && !leftHandWeapon && !rightHandEffect && !leftHandEffect
                           ? null
                           : PlayerActors.weaponPose(scene, !leftHandWeapon && !leftHandEffect)
                     );
                  Vec3d midpoint = handPose == null ? PlayerActors.handPose(scene).midpoint() : handPose.grip();
                  Quaternionf rotation = handPose == null ? new Quaternionf() : new Quaternionf(handPose.rotation());
                  Vector3f scale = new Vector3f(key.sx() * visible, key.sy() * visible, key.sz() * visible);
                  Vector3f position = new Vector3f((float)midpoint.x, (float)midpoint.y, (float)midpoint.z);
                  if (cue.actorBeam) {
                     Vector3f towardTarget = new Vector3f((float)(scene.x - midpoint.x), (float)(scene.y + 1.05 - midpoint.y), (float)(scene.z - midpoint.z));
                     Vector3f direction = new Vector3f(towardTarget);
                     if (direction.lengthSquared() < 1.0E-8F) {
                        direction.set(handPose.direction());
                     }

                     float length = Math.max(0.01F, direction.length());
                     direction.normalize();
                     rotation.rotationTo(new Vector3f(0.0F, 0.0F, 1.0F), direction);
                     position.add(new Vector3f(direction).mul(length * 0.5F));
                     scale.set(key.sy() * visible, key.sz() * visible, length * key.sx() * visible);
                  } else if (rightHandWeapon || leftHandWeapon || mouthWeapon) {
                     float weaponScale = Math.max(key.sx(), Math.max(key.sy(), key.sz()));
                     position.add(new Vector3f(handPose.direction()).mul(0.25F * weaponScale));
                     rotation.rotateY((float)Math.toRadians(key.yaw())).rotateX((float)Math.toRadians(key.pitch())).rotateZ((float)Math.toRadians(key.roll()));
                     Vector3f handOffset = new Vector3f(key.x(), key.y(), key.z());
                     rotation.transform(handOffset);
                     position.add(handOffset);
                     scale.mul(visible);
                  } else if (!rightHandEffect && !leftHandEffect) {
                     scale.mul(visible);
                  } else {
                     Vector3f localOffset = new Vector3f(key.x(), key.y(), key.z());
                     rotation.transform(localOffset);
                     position.add(localOffset);
                     rotation.rotateY((float)Math.toRadians(key.yaw())).rotateX((float)Math.toRadians(key.pitch())).rotateZ((float)Math.toRadians(key.roll()));
                     scale.mul(visible);
                  }

                  if (scene.animation == DeathAnimationConfig.Animation.BAN_HAMMER && cue.model != null && cue.model.endsWith("weapons/ban_hammer")) {
                     rotation.mul(new Quaternionf().rotationY((float)Math.toRadians(180.0)));
                  }

                  ((DisplayEntity)entity).setTransformation(new AffineTransformation(new Vector3f(), rotation, scale, new Quaternionf()));
                  entity.setPosition(position.x, position.y, position.z);
                  entity.lastX = entity.lastRenderX = position.x;
                  entity.lastY = entity.lastRenderY = position.y;
                  entity.lastZ = entity.lastRenderZ = position.z;
               }
            }
         }

         if (scene.animation == DeathAnimationConfig.Animation.FULL_1322_PHOTOCOPIER_TONER_TRANSFER) {
            PhotocopierMassProduction.animate(scene);
         }
      }
   }

   @Environment(EnvType.CLIENT)
   public record Cue(
      String block,
      String item,
      String text,
      Integer textColor,
      boolean bold,
      List<AnimationCatalog.Key> keys,
      float start,
      float end,
      String model,
      Integer tetherPart,
      Float anchorX,
      Float anchorY,
      Float anchorZ,
      boolean sampleGround,
      boolean fullBright,
      boolean guiOnly,
      Integer dissolvePart,
      String hangingRole,
      Integer hangingIndex,
      List<AnimationCatalog.ModelFrame> modelFrames,
      boolean visualEffect,
      String actor,
      List<AnimationCatalog.PartTrack> actorLimbs,
      Float opacity,
      Float actorPivotY,
      String actorAnchor,
      boolean actorBeam,
      String tetherActor,
      Float tetherSide
   ) {
   }

   @Environment(EnvType.CLIENT)
   public record EffectCue(float p, String sound, String particle, int count, Float pitch, Float volume) {
   }

   @Environment(EnvType.CLIENT)
   public record Key(float p, float x, float y, float z, float pitch, float yaw, float roll, float sx, float sy, float sz, String curve) {
   }

   @Environment(EnvType.CLIENT)
   public record ModelFrame(float p, String model) {
   }

   @Environment(EnvType.CLIENT)
   public record PartTrack(int part, boolean detached, List<AnimationCatalog.Key> keys, float visibleUntil, float landStart, float landEnd) {
   }

   @Environment(EnvType.CLIENT)
   public record Recipe(
      String id,
      String title,
      String description,
      int ticks,
      String icon,
      int accent,
      String pose,
      float visibleUntil,
      List<AnimationCatalog.Key> keys,
      List<AnimationCatalog.Cue> props,
      String particle,
      String sound,
      boolean attach,
      String reference,
      String collection,
      List<AnimationCatalog.PartTrack> limbs,
      List<AnimationCatalog.Stunt> stunts,
      boolean sceneAligned,
      List<AnimationCatalog.EffectCue> events,
      Float fadeStart,
      boolean grounded,
      String previewSkin,
      boolean frameSampled,
      List<Float> previewBounds
   ) {
   }

   @Environment(EnvType.CLIENT)
   public record Stunt(float start, float end, boolean land, Float pivotY) {
   }
}
