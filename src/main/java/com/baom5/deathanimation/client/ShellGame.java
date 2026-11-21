package com.baom5.deathanimation.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.decoration.DisplayEntity.BillboardMode;
import net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.entity.decoration.DisplayEntity.TextDisplayEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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
import org.joml.Matrix3x2fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

@Environment(EnvType.CLIENT)
final class ShellGame {
   private static final float SHUFFLE_START = 40.0F;
   private static final float OPENING_REVEAL_END = 36.0F;
   private static final int WAIT_TIMEOUT = 100;
   private static final int RESULT_DURATION = 90;
   private static final double PICK_REACH = 6.0;
   private static final float[] STAGE_SWAP_LENGTHS = new float[]{14.0F, 8.0F, 4.0F, 2.4F, 1.4F};
   private static final int[] STAGE_SWAP_COUNTS = new int[]{3, 4, 6, 10, 16};
   private static final String[] STAGE_NAMES = new String[]{"SLOW", "MEDIUM", "FAST", "REALLY FAST", "MOTION BLUR"};
   private static final float[] SLOT_X = new float[]{-0.91F, 0.0F, 0.91F};
   private static final float CUP_SCALE = 0.72F;
   private static final float TABLE_TOP = 0.3F;
   private static final float CUP_Y = 0.615F;
   private final DeathScene scene;
   private final int[] cupAtSlot = new int[]{0, 1, 2};
   private final int[] initialCupAtSlot = new int[]{0, 1, 2};
   private final int[] cupSlot = new int[]{0, 1, 2};
   private final List<ShellGame.Swap> swaps = new ArrayList<>();
   private final ItemDisplayEntity[] cups = new ItemDisplayEntity[3];
   private final ItemDisplayEntity[][] blurCups = new ItemDisplayEntity[2][2];
   private final float[] stageEnds = new float[STAGE_NAMES.length];
   private TextDisplayEntity message;
   private final int ballCup;
   private float elapsed;
   private float waitTicks;
   private float resultTicks;
   private int selectedSlot = -1;
   private int selectedCup = -1;
   private int resultStep;
   private boolean won;
   private boolean waiting;
   private boolean finished;
   private boolean preview;

   static void register() {
      HudElementRegistry.addLast(Identifier.of("deathanimation", "shell_game_timer"), (graphics, delta) -> {
         MinecraftClient client = MinecraftClient.getInstance();
         if (client.world != null && client.player != null && client.currentScreen == null) {
            Vec3d camera = client.gameRenderer.getCamera().getCameraPos();
            ShellGame nearest = null;
            double distance = 64.0;

            for (DeathScene active : DeathEffectController.scenes()) {
               ShellGame game = active.shellGame();
               if (game != null && !active.gui && active.level == client.world && !game.finished && (game.waiting || game.resultStep != 0)) {
                  double next = camera.squaredDistanceTo(active.x, game.baseY() + 0.6150000095367432, active.z);
                  if (next < distance) {
                     nearest = game;
                     distance = next;
                  }
               }
            }

            if (nearest != null) {
               nearest.drawHud(graphics);
            }
         }
      });
   }

   private void drawHud(DrawContext graphics) {
      MinecraftClient client = MinecraftClient.getInstance();
      String text = this.waiting
         ? Integer.toString(this.secondsRemaining())
         : (
            this.resultStep == 2 && this.resultTicks >= 10.0F
               ? "WINNER!"
               : (this.resultStep != 3 && (this.resultStep != 1 || !(this.resultTicks >= 32.0F)) ? "" : "LOSER")
         );
      if (!text.isEmpty()) {
         int color = this.waiting ? -5981 : (this.won ? -10223741 : -43691);
         Matrix3x2fStack pose = graphics.getMatrices();
         pose.pushMatrix();
         pose.translate(client.getWindow().getScaledWidth() * 0.5F, client.getWindow().getScaledHeight() * 0.18F);
         pose.scale(2.5F, 2.5F);
         graphics.drawCenteredTextWithShadow(client.textRenderer, text, 0, 0, color);
         pose.popMatrix();
         String prompt = this.waiting ? "LEFT-CLICK A CUP" : (this.resultStep == 3 && this.resultTicks < 20.0F ? "0" : "");
         if (!prompt.isEmpty()) {
            graphics.drawCenteredTextWithShadow(
               client.textRenderer, prompt, client.getWindow().getScaledWidth() / 2, Math.round(client.getWindow().getScaledHeight() * 0.18F) + 28, color
            );
         }
      }
   }

   private int secondsRemaining() {
      return Math.max(0, (int)Math.ceil((100.0F - this.waitTicks) / 20.0F));
   }

   private void tickGuess() {
      this.waitTicks++;
      if (this.waitTicks >= 100.0F) {
         this.beginChoice(-1);
      } else if ((int)this.waitTicks % 20 == 0) {
         this.updateGuessMessage();
      }
   }

   private void startGuessing() {
      this.elapsed = this.shuffleEnd();
      this.scene.age = AnimationCatalog.recipe(this.scene.animation).ticks();
      this.applySwapsUntil(this.elapsed);
      this.waitTicks = 0.0F;
      this.waiting = true;
      this.updateGuessMessage();
   }

   private void updateGuessMessage() {
      this.setMessage("LEFT-CLICK A CUP - " + this.secondsRemaining(), -5981);
   }

   ShellGame(DeathScene scene) {
      this.scene = scene;
      Random random = new Random(scene.seed ^ System.nanoTime());
      this.ballCup = random.nextInt(3);
      this.swapSlots(1, this.ballCup);
      System.arraycopy(this.cupAtSlot, 0, this.initialCupAtSlot, 0, 3);
      int previous = -1;
      float tick = 40.0F;

      for (int stage = 0; stage < STAGE_SWAP_COUNTS.length; stage++) {
         for (int count = 0; count < STAGE_SWAP_COUNTS[stage]; count++) {
            float length = STAGE_SWAP_LENGTHS[stage];
            int pair = random.nextInt(2);
            if (pair == previous && random.nextBoolean()) {
               pair = 1 - pair;
            }

            previous = pair;
            int a = pair;
            int b = pair + 1;
            this.swaps.add(new ShellGame.Swap(tick, length, a, b));
            tick += length;
         }

         this.stageEnds[stage] = tick;
      }
   }

   static void spawn(DeathScene scene, Consumer<Entity> add) {
      ShellGame game = scene.shellGame();
      game.preview = scene.gui;
      game.block(add, Blocks.DARK_OAK_PLANKS, 0.0F, 0.12F, 0.0F, 2.72F, 0.24F, 1.84F);
      game.block(add, Blocks.STRIPPED_DARK_OAK_WOOD, 0.0F, 0.255F, 0.0F, 2.62F, 0.045F, 1.74F);
      game.block(add, Blocks.GREEN_WOOL, 0.0F, 0.287F, 0.0F, 2.48F, 0.018F, 1.64F);

      for (float x : new float[]{-1.13F, 1.13F}) {
         for (float z : new float[]{-0.7F, 0.7F}) {
            game.block(add, Blocks.DARK_OAK_LOG, x, -0.19F, z, 0.19F, 0.58F, 0.19F);
         }
      }

      for (int slot = 0; slot < 3; slot++) {
         game.cups[slot] = game.createCup(add, "shell_cup");
      }

      for (int cup = 0; cup < 2; cup++) {
         for (int trail = 0; trail < 2; trail++) {
            game.blurCups[cup][trail] = game.createCup(add, "shell_cup_blur");
         }
      }

      TextDisplayEntity label = (TextDisplayEntity)EntityType.TEXT_DISPLAY.create(scene.level, SpawnReason.COMMAND);
      if (label != null) {
         label.setText(Text.literal("WATCH THE PLAYER").styled(s -> s.withColor(16773314).withBold(true)));
         label.setTextOpacity((byte)-1);
         label.setBackground(-2012408045);
         label.setDisplayFlags((byte)3);
         label.setBillboardMode(BillboardMode.CENTER);
         label.setNoGravity(true);
         label.setTransformation(new AffineTransformation(new Vector3f(), new Quaternionf(), new Vector3f(0.72F), new Quaternionf()));
         game.message = label;
         add.accept(label);
      }

      animate(scene);
   }

   private ItemDisplayEntity createCup(Consumer<Entity> add, String model) {
      ItemDisplayEntity display = (ItemDisplayEntity)EntityType.ITEM_DISPLAY.create(this.scene.level, SpawnReason.COMMAND);
      if (display == null) {
         return null;
      }

      ItemStack stack = new ItemStack(Items.PAPER);
      stack.set(DataComponentTypes.ITEM_MODEL, Identifier.of("deathanimation", "pilot/" + model));
      display.setItemStack(stack);
      display.setItemDisplayContext(ItemDisplayContext.FIXED);
      display.setNoGravity(true);
      add.accept(display);
      return display;
   }

   void tick() {
      if (!this.finished) {
         if (this.waiting) {
            this.tickGuess();
         } else if (this.resultStep <= 0) {
            this.elapsed = this.elapsed + Math.max(0.05F, DeathAnimationConfig.INSTANCE.animationSpeed);
            this.scene.age = Math.min(this.elapsed, AnimationCatalog.recipe(this.scene.animation).ticks());
            if (this.elapsed >= 40.0F && this.elapsed < this.shuffleEnd()) {
               this.setStageMessage(this.elapsed);
            }

            if (this.elapsed >= this.shuffleEnd()) {
               this.startGuessing();
            }
         } else {
            this.resultTicks++;
            if (this.resultStep == 1 && this.resultTicks == 32.0F) {
               this.play(SoundEvents.ENTITY_VILLAGER_NO, 0.8F, 0.75F);
               this.setMessage("LOSER", -43691);
            } else if (this.resultStep == 2 && this.resultTicks == 10.0F) {
               this.play(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8F, 1.1F);
               this.setMessage("WINNER!", -10223741);
               EffectParticles.create(ParticleTypes.HAPPY_VILLAGER, this.scene.x, this.baseY() + 0.75, this.scene.z, 0.0, 0.04, 0.0);
            }

            if (this.resultTicks >= 90.0F) {
               this.finished = true;
            }
         }
      }
   }

   void tickPreview(float speed) {
      if (!this.finished) {
         if (this.waiting) {
            this.tickGuess();
         } else if (this.resultStep <= 0) {
            this.elapsed = this.elapsed + Math.max(0.05F, speed);
            this.scene.age = Math.min(this.elapsed, AnimationCatalog.recipe(this.scene.animation).ticks());
            if (this.elapsed >= 40.0F && this.elapsed < this.shuffleEnd()) {
               this.setStageMessage(this.elapsed);
            }

            if (this.elapsed >= this.shuffleEnd()) {
               this.startGuessing();
            }
         } else {
            this.resultTicks++;
            if (this.resultStep == 1 && this.resultTicks == 32.0F) {
               this.play(SoundEvents.ENTITY_VILLAGER_NO, 0.8F, 0.75F);
               this.setMessage("LOSER", -43691);
            } else if (this.resultStep == 2 && this.resultTicks == 10.0F) {
               this.play(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8F, 1.1F);
               this.setMessage("WINNER!", -10223741);
               EffectParticles.create(ParticleTypes.HAPPY_VILLAGER, this.scene.x, this.baseY() + 0.75, this.scene.z, 0.0, 0.04, 0.0);
            }

            if (this.resultTicks >= 90.0F) {
               this.finished = true;
            }
         }
      }
   }

   String caption() {
      return this.message == null ? "" : this.message.getText().getString();
   }

   boolean finished() {
      return this.finished;
   }

   boolean awaitingGuess() {
      return this.waiting && !this.finished;
   }

   int previewWatchdog(float speed) {
      return (int)Math.ceil(this.shuffleEnd() / Math.max(0.05F, speed)) + 100 + 90 + 20;
   }

   boolean playerVisible() {
      if (this.elapsed < 36.0F) {
         return true;
      } else {
         return this.resultStep == 1 ? this.resultTicks >= 32.0F : this.resultStep >= 2 && this.resultTicks >= 10.0F;
      }
   }

   static DeathScene.Root root(DeathScene scene) {
      ShellGame game = scene.shellGame();
      if (game == null) {
         return DeathScene.Root.STILL;
      } else if (game.elapsed < 36.0F) {
         float shrink = AnimationTimeline.smooth(AnimationTimeline.phase(game.elapsed, 0.0F, 14.0F));
         float scale = MathHelper.lerp(shrink, 1.0F, 0.22F);
         return new DeathScene.Root(0.0F, (float)(game.baseY() - scene.y) + 0.3F, 0.0F, 0.0F, scene.sceneFacing, 0.0F, scale, scale, scale);
      } else {
         float localX = SLOT_X[game.cupSlot[game.ballCup]];
         Vector3f offset = new Quaternionf().rotationY((float)Math.toRadians(scene.sceneFacing)).transform(new Vector3f(localX, 0.0F, 0.0F));
         return new DeathScene.Root(offset.x, (float)(game.baseY() - scene.y) + 0.3F, offset.z, 0.0F, scene.sceneFacing, 0.0F, 0.22F, 0.22F, 0.22F);
      }
   }

   ShellGame.CupHit pick(AbstractClientPlayerEntity player) {
      if (this.awaitingGuess() && !this.scene.gui && this.scene.level == player.getEntityWorld()) {
         MinecraftClient client = MinecraftClient.getInstance();
         Vec3d start = player.getEyePos();
         Vec3d direction = player.getRotationVector();
         Camera camera = client.gameRenderer.getCamera();
         if (player == client.player && camera.isReady()) {
            start = camera.getCameraPos();
            Vector3fc forward = camera.getHorizontalPlane();
            direction = new Vec3d(forward.x(), forward.y(), forward.z());
         }

         double cameraOffset = start.distanceTo(player.getEyePos());
         Vec3d end = start.add(direction.normalize().multiply(6.0 + cameraOffset));
         Quaternionf inverse = new Quaternionf().rotationY((float)Math.toRadians(-this.scene.sceneFacing));
         Vector3f localStart = inverse.transform(
            new Vector3f((float)(start.x - this.scene.x), (float)(start.y - this.baseY() - 0.6150000095367432), (float)(start.z - this.scene.z))
         );
         Vector3f localEnd = inverse.transform(
            new Vector3f((float)(end.x - this.scene.x), (float)(end.y - this.baseY() - 0.6150000095367432), (float)(end.z - this.scene.z))
         );
         Vec3d from = new Vec3d(localStart);
         Vec3d to = new Vec3d(localEnd);
         int target = -1;
         double nearest = 1.0 / 0.0;

         for (int slot = 0; slot < 3; slot++) {
            ItemDisplayEntity display = this.cups[this.cupAtSlot[slot]];
            if (display != null) {
               NativePropBounds.Bounds mesh = NativePropBounds.of(display);
               if (mesh != null) {
                  Vector3f low = mesh.low();
                  Vector3f high = mesh.high();
                  Box bounds = new Box(
                        SLOT_X[slot] + low.x * 0.72F, low.y * 0.72F, low.z * 0.72F, SLOT_X[slot] + high.x * 0.72F, high.y * 0.72F, high.z * 0.72F
                     )
                     .expand(0.025);
                  Optional<Vec3d> hit = bounds.contains(from) ? Optional.of(from) : bounds.raycast(from, to);
                  if (hit.isPresent()) {
                     double distance = from.squaredDistanceTo(hit.get());
                     if (distance < nearest) {
                        nearest = distance;
                        target = slot;
                     }
                  }
               }
            }
         }

         if (target < 0) {
            return null;
         }

         Vec3d point = start.add(direction.normalize().multiply(Math.sqrt(nearest)));
         if (player.getEyePos().squaredDistanceTo(point) > 36.0) {
            return null;
         }

         BlockHitResult obstruction = this.scene.level.raycast(new RaycastContext(start, point, ShapeType.COLLIDER, FluidHandling.NONE, player));
         return obstruction.getType() == Type.BLOCK && obstruction.getPos().squaredDistanceTo(start) < nearest - 1.0E-4
            ? null
            : new ShellGame.CupHit(this, target, nearest);
      } else {
         return null;
      }
   }

   boolean interact(AbstractClientPlayerEntity player) {
      ShellGame.CupHit hit = this.pick(player);
      return hit != null && this.choose(hit.slot());
   }

   boolean choose(int slot) {
      if (this.awaitingGuess() && slot >= 0 && slot <= 2 && !(this.waitTicks >= 100.0F)) {
         this.beginChoice(slot);
         return true;
      } else {
         return false;
      }
   }

   boolean choosePreview(int slot) {
      return this.scene.gui && this.choose(slot);
   }

   private void beginChoice(int slot) {
      if (this.waiting) {
         this.applySwapsUntil(this.shuffleEnd());
         this.waiting = false;
         this.selectedSlot = slot;
         this.selectedCup = this.selectedSlot < 0 ? this.ballCup : this.cupAtSlot[this.selectedSlot];
         this.won = slot >= 0 && this.selectedCup == this.ballCup;
         this.resultTicks = 0.0F;
         this.resultStep = slot < 0 ? 3 : (this.won ? 2 : 1);
         if (slot < 0) {
            this.play(SoundEvents.ENTITY_VILLAGER_NO, 0.8F, 0.75F);
            this.setMessage("LOSER", -43691);
         } else if (this.won) {
            this.setMessage("", -10223741);
         } else {
            this.play(SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_ON, 0.75F, 0.8F);
            this.setMessage("", -43691);
         }
      }
   }

   static void animate(DeathScene scene) {
      ShellGame game = scene.shellGame();
      if (game != null) {
         game.renderState();
      }
   }

   private void renderState() {
      float now = !this.waiting && this.resultStep <= 0
         ? this.elapsed + MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(false) * DeathAnimationConfig.INSTANCE.animationSpeed
         : this.shuffleEnd();
      now = Math.min(now, this.shuffleEnd());
      this.applySwapsUntil(now);
      int activeSwap = -1;

      for (int i = 0; i < this.swaps.size(); i++) {
         ShellGame.Swap swap = this.swaps.get(i);
         if (now >= swap.start && now < swap.start + swap.duration) {
            activeSwap = i;
            break;
         }
      }

      int movingA = -1;
      int movingB = -1;
      float swapT = 0.0F;
      if (activeSwap >= 0) {
         ShellGame.Swap s = this.swaps.get(activeSwap);
         movingA = this.cupAtSlot[s.from];
         movingB = this.cupAtSlot[s.to];
         swapT = MathHelper.clamp((now - s.start) / s.duration, 0.0F, 1.0F);
         swapT = swapT * swapT * (3.0F - 2.0F * swapT);
      }

      for (int slot = 0; slot < 3; slot++) {
         int cup = this.cupAtSlot[slot];
         ItemDisplayEntity display = this.cups[cup];
         if (display != null) {
            float x = SLOT_X[slot];
            float z = 0.0F;
            float y = 0.615F;
            if (activeSwap >= 0 && (cup == movingA || cup == movingB)) {
               Vector3f position = swapPosition(this.swaps.get(activeSwap), cup == movingA, swapT);
               x = position.x;
               z = position.z;
            }

            float lift = this.openingLift(cup, now);
            if (this.resultStep > 0) {
               boolean selected = cup == this.selectedCup;
               if (this.resultStep == 1) {
                  if (selected) {
                     if (this.resultTicks < 8.0F) {
                        lift = MathHelper.sin(this.resultTicks / 8.0F * 3.1415927F * 0.5F) * 0.58F;
                     } else if (this.resultTicks < 16.0F) {
                        lift = 0.58F;
                     } else if (this.resultTicks < 22.0F) {
                        lift = 0.58F * (1.0F - MathHelper.sin((this.resultTicks - 16.0F) / 6.0F * 3.1415927F * 0.5F));
                     }
                  } else if (cup == this.ballCup && this.resultTicks >= 22.0F) {
                     lift = MathHelper.sin(MathHelper.clamp((this.resultTicks - 22.0F) / 10.0F, 0.0F, 1.0F) * 3.1415927F * 0.5F) * 0.58F;
                  }
               } else if (selected) {
                  lift = MathHelper.sin(MathHelper.clamp(this.resultTicks / 10.0F, 0.0F, 1.0F) * 3.1415927F * 0.5F) * 0.58F;
               }
            }

            this.setCup(display, x, y + lift, z, 0.0F, 0.72F);
         }
      }

      this.updateMotionBlur(now, activeSwap, movingA, movingB, swapT);
      if (this.message != null) {
         Vec3d pos = this.worldPoint(0.0F, this.baseY() + 1.649999976158142, 0.0F);
         this.message.setPosition(pos.x, pos.y, pos.z);
      }
   }

   private float openingLift(int cup, float now) {
      if (cup != this.ballCup) {
         return 0.0F;
      } else if (now < 18.0F) {
         return 0.65F;
      } else {
         return now < 36.0F ? 0.65F * (1.0F - AnimationTimeline.smooth(AnimationTimeline.phase(now, 18.0F, 36.0F))) : 0.0F;
      }
   }

   private void updateMotionBlur(float now, int activeSwap, int movingA, int movingB, float swapT) {
      if (activeSwap >= 0 && this.stageFor(now) == 4) {
         ShellGame.Swap swap = this.swaps.get(activeSwap);

         for (int mover = 0; mover < 2; mover++) {
            int cup = mover == 0 ? movingA : movingB;

            for (int trail = 0; trail < 2; trail++) {
               ItemDisplayEntity display = this.blurCups[mover][trail];
               if (display != null) {
                  float past = swapT - (trail + 1) * 0.18F;
                  if (past <= 0.0F) {
                     this.hideCup(display);
                  } else {
                     Vector3f position = swapPosition(swap, cup == movingA, past);
                     float size = trail == 0 ? 0.96F : 0.84F;
                     this.setCup(display, position.x, 0.615F, position.z, 0.0F, 0.72F * size);
                  }
               }
            }
         }
      } else {
         this.hideMotionBlur();
      }
   }

   private void hideMotionBlur() {
      for (ItemDisplayEntity[] pair : this.blurCups) {
         for (ItemDisplayEntity display : pair) {
            if (display != null) {
               this.hideCup(display);
            }
         }
      }
   }

   private void hideCup(ItemDisplayEntity display) {
      display.setTransformation(new AffineTransformation(new Vector3f(), new Quaternionf(), new Vector3f(0.001F), new Quaternionf()));
   }

   private static Vector3f swapPosition(ShellGame.Swap swap, boolean first, float easedProgress) {
      float middle = (SLOT_X[swap.from] + SLOT_X[swap.to]) * 0.5F;
      float radius = (SLOT_X[swap.to] - SLOT_X[swap.from]) * 0.5F;
      float angle = 3.1415927F * easedProgress;
      float sign = first ? 1.0F : -1.0F;
      return new Vector3f(middle - sign * radius * (float)Math.cos(angle), 0.615F, sign * radius * (float)Math.sin(angle));
   }

   private int stageFor(float now) {
      for (int stage = 0; stage < this.stageEnds.length; stage++) {
         if (now < this.stageEnds[stage]) {
            return stage;
         }
      }

      return this.stageEnds.length - 1;
   }

   private void setStageMessage(float now) {
      int stage = this.stageFor(now);
      this.setMessage(STAGE_NAMES[stage] + " - TRACK THE PLAYER", -5981);
   }

   private void applySwapsUntil(float now) {
      System.arraycopy(this.initialCupAtSlot, 0, this.cupAtSlot, 0, 3);

      for (ShellGame.Swap swap : this.swaps) {
         if (now >= swap.start + swap.duration) {
            this.swapSlots(swap.from, swap.to);
         }
      }

      int slot = 0;

      while (slot < 3) {
         this.cupSlot[this.cupAtSlot[slot]] = slot++;
      }
   }

   private float shuffleEnd() {
      ShellGame.Swap last = this.swaps.getLast();
      return last.start + last.duration;
   }

   private void swapSlots(int a, int b) {
      int cup = this.cupAtSlot[a];
      this.cupAtSlot[a] = this.cupAtSlot[b];
      this.cupAtSlot[b] = cup;
      int slot = 0;

      while (slot < 3) {
         this.cupSlot[this.cupAtSlot[slot]] = slot++;
      }
   }

   private void setCup(ItemDisplayEntity display, float x, float y, float z, float yaw, float scale) {
      Vec3d pos = this.worldPoint(x, this.baseY() + y, z);
      Quaternionf facing = new Quaternionf().rotationY((float)Math.toRadians(this.scene.sceneFacing + yaw));
      display.setTransformation(new AffineTransformation(new Vector3f(), facing, new Vector3f(scale), new Quaternionf()));
      display.setPosition(pos.x, pos.y, pos.z);
   }

   private void block(Consumer<Entity> add, Block block, float x, float y, float z, float sx, float sy, float sz) {
      BlockDisplayEntity display = (BlockDisplayEntity)EntityType.BLOCK_DISPLAY.create(this.scene.level, SpawnReason.COMMAND);
      if (display != null) {
         display.setBlockState(block.getDefaultState());
         display.setNoGravity(true);
         Quaternionf rotation = new Quaternionf().rotationY((float)Math.toRadians(this.scene.sceneFacing));
         Vector3f size = new Vector3f(sx, sy, sz);
         Vector3f offset = rotation.transform(new Vector3f(size).mul(-0.5F));
         display.setTransformation(new AffineTransformation(offset, rotation, size, new Quaternionf()));
         Vec3d pos = this.worldPoint(x, this.baseY() + y, z);
         display.setPosition(pos.x, pos.y, pos.z);
         add.accept(display);
      }
   }

   private double baseY() {
      return this.scene.gui ? this.scene.y : this.scene.floorY;
   }

   private Vec3d worldPoint(float localX, double worldY, float localZ) {
      Vector3f offset = new Quaternionf().rotationY((float)Math.toRadians(this.scene.sceneFacing)).transform(new Vector3f(localX, 0.0F, localZ));
      return new Vec3d(this.scene.x + offset.x, worldY, this.scene.z + offset.z);
   }

   private void setMessage(String value, int color) {
      if (this.message != null) {
         this.message.setText(Text.literal(value).styled(s -> s.withColor(color).withBold(true)));
         this.message.setTextOpacity((byte)-1);
      }
   }

   private void play(SoundEvent sound, float volume, float pitch) {
      if (DeathAnimationConfig.INSTANCE.sounds) {
         this.scene.level.playSoundClient(this.scene.x, this.baseY() + 0.5, this.scene.z, sound, SoundCategory.PLAYERS, volume, pitch, false);
      }
   }

   @Environment(EnvType.CLIENT)
   record CupHit(ShellGame game, int slot, double distanceSquared) {
   }

   @Environment(EnvType.CLIENT)
   private record Swap(float start, float duration, int from, int to) {
   }
}
