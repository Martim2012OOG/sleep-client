package com.baom5.deathanimation.client;

import com.baom5.deathanimation.mixin.client.DisplayStateAccess;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.decoration.DisplayEntity.BillboardMode;
import net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.entity.decoration.DisplayEntity.TextDisplayEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.AffineTransformation;
import org.joml.Matrix3x2fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class CalendarAgeOut {
   private static final int PAGES = 9;
   private static final int HAIR = 4;
   private static final int BEARD = 35;
   private static final int WRINKLES = 3;
   private static final int CALENDAR_INDEX = 0;
   private static final int PAGE_START = 1;
   private static final int DAY_INDEX = 10;
   private static final int DETAIL_START = 11;
   private static final int JOKE_INDEX = 53;
   private static final Map<Object, DeathScene> SKELETON_STATES = new WeakHashMap<>();
   private static final float CALENDAR_Y = 2.43F;
   private static final float PAGE_Y = 2.35F;
   private static final float PAGE_Z = 0.04F;
   private static final String[] DAYS = new String[]{
      "DAY 1", "DAY 2", "DAY 5", "DAY 10", "DAY 100", "DAY 1,000", "DAY 10,000", "DAY 100,000", "DAY 1,000,000", "DAY 1,000,000,000", "DAY 999,999,999,999"
   };
   private static final float[] DAY_STAGES = new float[]{0.0F, 0.1F, 0.19F, 0.27F, 0.34F, 0.41F, 0.47F, 0.53F, 0.59F, 0.64F, 0.68F};

   private CalendarAgeOut() {
   }

   private static int dayIndex(float p) {
      int stage = 0;

      for (int i = 1; i < DAY_STAGES.length; i++) {
         if (p >= DAY_STAGES[i]) {
            stage = i;
         }
      }

      return stage;
   }

   private static float aging(float p) {
      int stage = dayIndex(p);
      float fraction = stage + 1 < DAY_STAGES.length ? AnimationTimeline.phase(p, DAY_STAGES[stage], DAY_STAGES[stage + 1]) : 0.0F;
      return AnimationTimeline.smooth(Math.clamp((stage + fraction - 2.0F) / (DAYS.length - 3.0F), 0.0F, 1.0F));
   }

   static DeathScene.Root root(DeathScene scene) {
      return new DeathScene.Root(0.0F, scene.gui ? 0.0F : (float)(scene.floorY - scene.y), 0.0F, 0.0F, scene.sceneFacing, 0.0F, 1.0F, 1.0F, 1.0F);
   }

   static DeathBodyRig.Motion pose(DeathScene scene, int part) {
      float p = scene.progress();
      float age = aging(p);
      float fade = 1.0F - AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.78F, 0.86F));
      float rx = 0.0F;
      float ry = 0.0F;
      float rz = 0.0F;
      switch (part) {
         case 0:
            rx = (float)Math.toRadians(3.0F + 13.0F * age);
            break;
         case 1:
            rx = (float)Math.toRadians(2.0F + 7.0F * age);
            break;
         case 2:
            rx = (float)Math.toRadians(5.0F + 18.0F * age);
            rz = (float)Math.toRadians(-4.0F - 8.0F * age);
            break;
         case 3:
            rx = (float)Math.toRadians(5.0F + 18.0F * age);
            rz = (float)Math.toRadians(4.0F + 8.0F * age);
            break;
         case 4:
         case 5:
            rx = (float)Math.toRadians(-3.0F - 7.0F * age);
      }

      return new DeathBodyRig.Motion(false, 0.0F, 0.0F, 0.0F, rx, ry, rz, -1.0F, fade);
   }

   static void spawn(DeathScene scene, Consumer<Entity> add) {
      ItemDisplayEntity calendar = itemDisplay(scene, "deathanimation:pilot/calendar");
      if (calendar != null) {
         add.accept(calendar);
      }

      for (int i = 0; i < 9; i++) {
         ItemDisplayEntity page = itemDisplay(scene, null);
         if (page != null) {
            add.accept(page);
         }
      }

      TextDisplayEntity day = (TextDisplayEntity)EntityType.TEXT_DISPLAY.create(scene.level, SpawnReason.COMMAND);
      if (day != null) {
         day.setText(Text.literal(DAYS[0]).styled(style -> style.withColor(2434341).withBold(true)));
         day.setTextOpacity((byte)-1);
         day.setLineWidth(400);
         day.setBackground(0);
         day.setDisplayFlags((byte)0);
         day.setBrightness(new Brightness(15, 15));
         day.setBillboardMode(BillboardMode.FIXED);
         day.setNoGravity(true);
         add.accept(day);
      }

      for (int i = 0; i < 4; i++) {
         addBlock(scene, add, Blocks.LIGHT_GRAY_WOOL);
      }

      for (int i = 0; i < 35; i++) {
         addBlock(scene, add, Blocks.WHITE_WOOL);
      }

      for (int i = 0; i < 3; i++) {
         addBlock(scene, add, Blocks.LIGHT_GRAY_CONCRETE);
      }

      TextDisplayEntity joke = (TextDisplayEntity)EntityType.TEXT_DISPLAY.create(scene.level, SpawnReason.COMMAND);
      if (joke != null) {
         joke.setText(Text.literal("drnames age\ninteger max").styled(style -> style.withBold(true).withColor(16777215)));
         joke.setLineWidth(300);
         joke.setBackground(0);
         joke.setBillboardMode(BillboardMode.CENTER);
         add.accept(joke);
      }

      scene.calendarDust = new CalendarBoneDust(scene);
      SkeletonEntity skeleton = (SkeletonEntity)EntityType.SKELETON.create(scene.level, SpawnReason.COMMAND);
      if (skeleton != null) {
         skeleton.setAiDisabled(true);
         skeleton.setNoGravity(true);
         skeleton.setSilent(true);
         skeleton.setInvulnerable(true);
         skeleton.setPosition(scene.x, scene.y, scene.z);
         skeleton.setInvisible(true);
         scene.props.add(skeleton);
      }

      animate(scene);
   }

   private static ItemDisplayEntity itemDisplay(DeathScene scene, String model) {
      ItemDisplayEntity display = (ItemDisplayEntity)EntityType.ITEM_DISPLAY.create(scene.level, SpawnReason.COMMAND);
      if (display == null) {
         return null;
      }

      ItemStack stack = new ItemStack((ItemConvertible)Registries.ITEM.get(Identifier.ofVanilla("paper")));
      if (model != null) {
         stack.set(DataComponentTypes.ITEM_MODEL, Identifier.of(model));
      }

      display.setItemStack(stack);
      display.setItemDisplayContext(ItemDisplayContext.FIXED);
      display.setNoGravity(true);
      return display;
   }

   private static void addBlock(DeathScene scene, Consumer<Entity> add, Block block) {
      BlockDisplayEntity display = (BlockDisplayEntity)EntityType.BLOCK_DISPLAY.create(scene.level, SpawnReason.COMMAND);
      if (display != null) {
         display.setBlockState(block.getDefaultState());
         display.setNoGravity(true);
         add.accept(display);
      }
   }

   static void animate(DeathScene scene) {
      float p = scene.progress();
      if (scene.props.size() > 10) {
         animateCalendar(scene, (ItemDisplayEntity)scene.props.get(0), p);

         for (int i = 0; i < 9; i++) {
            animatePage(scene, (ItemDisplayEntity)scene.props.get(1 + i), i, p);
         }

         updateDayCounter(scene, (TextDisplayEntity)scene.props.get(10), p);
         int index = 11;

         for (int i = 0; i < 4; i++) {
            animateHair(scene, (BlockDisplayEntity)scene.props.get(index++), i, p);
         }

         for (int i = 0; i < 35; i++) {
            animateBeard(scene, (BlockDisplayEntity)scene.props.get(index++), i, p);
         }

         for (int i = 0; i < 3; i++) {
            animateWrinkle(scene, (BlockDisplayEntity)scene.props.get(index++), i, p);
         }

         TextDisplayEntity joke = (TextDisplayEntity)scene.props.get(53);
         float jokeAlpha = AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.68F, 0.7F))
            * (1.0F - AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.755F, 0.78F)));
         joke.setTextOpacity((byte)Math.max(0, Math.round(255.0F * jokeAlpha)));
         float jokeSize = jokeAlpha > 0.01F ? 0.65F : 1.0E-4F;
         setDisplayTransform(scene, joke, 0.0F, 2.0F, -0.65F, jokeSize, jokeSize, jokeSize, new Quaternionf());
         SkeletonEntity skeleton = skeleton(scene);
         if (skeleton != null) {
            DeathScene.Root root = scene.root();
            skeleton.setPosition(scene.x + root.x(), scene.y + root.y(), scene.z + root.z());
            float yaw = 180.0F - scene.sceneFacing;
            skeleton.setYaw(yaw);
            skeleton.lastYaw = yaw;
            skeleton.setBodyYaw(yaw);
            skeleton.lastBodyYaw = yaw;
            skeleton.setHeadYaw(yaw);
            skeleton.lastHeadYaw = yaw;
            skeleton.setInvisible(p < 0.77F || skeletonScale(scene) < 0.01F);
            skeleton.lastX = skeleton.lastRenderX = skeleton.getX();
            skeleton.lastY = skeleton.lastRenderY = skeleton.getY();
            skeleton.lastZ = skeleton.lastRenderZ = skeleton.getZ();
         }
      }
   }

   private static void animateCalendar(DeathScene scene, ItemDisplayEntity display, float p) {
      setDisplayTransform(scene, display, 0.0F, 2.43F, 0.08F, 0.9F, 0.75F, 0.2F, new Quaternionf());
   }

   private static void animatePage(DeathScene scene, ItemDisplayEntity display, int index, float p) {
      int stage = dayIndex(p);
      float start = DAY_STAGES[stage];
      float end = stage + 1 < DAY_STAGES.length ? DAY_STAGES[stage + 1] : 0.77F;
      float turn = AnimationTimeline.smooth(AnimationTimeline.phase(p, start + (end - start) * 0.55F, end));
      float visibility = p < 0.77F && index == stage % 9 ? 1.0F : 0.0F;
      float angle = (float)Math.toRadians(178.0F * turn);
      float width = 0.62F;
      float height = 0.4F;
      Quaternionf rotation = new Quaternionf().rotationX(-angle);
      Vector3f hinge = new Vector3f(0.0F, 2.35F + height * 0.5F, 0.04F - index * 0.002F);
      Vector3f centerOffset = rotation.transform(new Vector3f(0.0F, -height * 0.5F, 0.0F));
      Vector3f center = hinge.add(centerOffset);
      float size = Math.max(0.001F, visibility);
      setDisplayTransform(scene, display, center.x, center.y, center.z, width * size, height * size, 0.012F * size, rotation);
   }

   private static void updateDayCounter(DeathScene scene, TextDisplayEntity display, float p) {
      int day = dayIndex(p);
      if (!display.getText().getString().equals(DAYS[day])) {
         display.setText(Text.literal(DAYS[day]).styled(style -> style.withColor(2434341).withBold(true)));
         refreshTextState(display);
      }

      float size = Math.min(0.58F, 0.64F / (Math.max(1, MinecraftClient.getInstance().textRenderer.getWidth(DAYS[day])) * 0.025F));
      setDisplayTransform(scene, display, 0.0F, 2.3400002F, -0.025F, size, size, size, new Quaternionf().rotationY(3.1415927F));
   }

   private static void refreshTextState(TextDisplayEntity display) {
      DisplayStateAccess access = (DisplayStateAccess)display;
      access.deathanimations$setRenderState(access.deathanimations$freshRenderState());
      access.deathanimations$refreshSubState(false, 1.0F);
      access.deathanimations$setDirty(false);
   }

   private static void animateHair(DeathScene scene, BlockDisplayEntity display, int i, float p) {
      float reveal = AnimationTimeline.smooth(AnimationTimeline.phase(aging(p), 0.04F + i * 0.025F, 0.3F + i * 0.025F));
      float stay = 1.0F - AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.78F, 0.86F));
      if (!scene.avatar.getEquippedStack(EquipmentSlot.HEAD).isEmpty()) {
         reveal = 0.0F;
      }

      BlockState hair = p < 0.43F + i * 0.035F ? Blocks.LIGHT_GRAY_WOOL.getDefaultState() : Blocks.WHITE_WOOL.getDefaultState();
      if (!display.getBlockState().equals(hair)) {
         display.setBlockState(hair);
      }

      float[][] pieces = new float[][]{
         {0.0F, 1.89F, 0.0F, 0.48F, 0.065F, 0.46F},
         {-0.245F, 1.73F, 0.03F, 0.045F, 0.24F, 0.42F},
         {0.245F, 1.73F, 0.03F, 0.045F, 0.24F, 0.42F},
         {0.0F, 1.84F, -0.24F, 0.32F, 0.09F, 0.06F}
      };
      float[] q = pieces[i];
      float visibility = reveal * stay;
      setBlockTransform(scene, display, q[0], q[1], q[2], q[3] * visibility, q[4] * visibility, q[5] * visibility, new Quaternionf());
   }

   private static void animateBeard(DeathScene scene, BlockDisplayEntity display, int i, float p) {
      int strand = i / 7;
      int row = i % 7;
      float growth = AnimationTimeline.smooth(AnimationTimeline.phase(aging(p), 0.1F, 0.96F));
      float stage = AnimationTimeline.smooth(AnimationTimeline.phase(aging(p), 0.08F + strand * 0.01F, 0.2F + strand * 0.01F));
      float stay = 1.0F - AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.78F, 0.86F));
      BlockState wool = aging(p) < 0.45F + strand * 0.035F ? Blocks.LIGHT_GRAY_WOOL.getDefaultState() : Blocks.WHITE_WOOL.getDefaultState();
      if (!display.getBlockState().equals(wool)) {
         display.setBlockState(wool);
      }

      float t = (row + 0.5F) / 7.0F;
      float length = 0.05F + (0.96F - Math.abs(strand - 2) * 0.12F) * growth;
      float visibility = stage * stay;
      float width = (0.042F + 0.022F * growth) * (1.0F - 0.58F * t * t);
      float height = length / 7.0F * 1.16F * visibility;
      float headPitch = -pose(scene, 0).xRot();
      Vector3f chin = new Quaternionf().rotationX(headPitch).transform(new Vector3f(0.0F, 0.06300008F, -0.285F)).add(0.0F, 1.407F, 0.0F);
      float angle = headPitch * (1.0F - 0.75F * growth * t);
      Vector3f hang = new Quaternionf()
         .rotationX(angle)
         .transform(new Vector3f((strand - 2) * 0.052F * (1.0F - 0.38F * t), -length * t, -0.035F * growth * t - strand % 2 * 0.014F));
      chin.add(hang);
      setDisplayTransform(
         scene, display, chin.x, chin.y, chin.z, width * visibility, height, (0.045F + 0.025F * growth) * visibility, new Quaternionf().rotationX(angle)
      );
   }

   private static void animateWrinkle(DeathScene scene, BlockDisplayEntity display, int i, float p) {
      float reveal = AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.47F + i * 0.055F, 0.58F + i * 0.055F));
      float stay = 1.0F - AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.78F, 0.86F));
      float[][] lines = new float[][]{{-0.115F, 1.61F, -0.263F, 0.095F}, {0.115F, 1.61F, -0.263F, 0.095F}, {0.0F, 1.535F, -0.264F, 0.13F}};
      float[] line = lines[i];
      float visibility = reveal * stay;
      setBlockTransform(scene, display, line[0], line[1], line[2], line[3] * visibility, 0.016F * visibility, 0.014F * visibility, new Quaternionf());
   }

   static SkeletonEntity skeleton(DeathScene scene) {
      for (Entity prop : scene.props) {
         if (prop instanceof SkeletonEntity skeleton) {
            return skeleton;
         }
      }

      return null;
   }

   static void drawPreviewCaption(DrawContext graphics, DeathScene scene, int x, int y, int width, int height) {
      float p = scene.progress();
      int day = 0;

      for (int i = 1; i < DAY_STAGES.length; i++) {
         if (p >= DAY_STAGES[i]) {
            day = i;
         }
      }

      TextRenderer font = MinecraftClient.getInstance().textRenderer;
      float scale = Math.min(1.4F, (width - 6.0F) / Math.max(1, font.getWidth(DAYS[day])));
      Matrix3x2fStack matrix = graphics.getMatrices();
      matrix.pushMatrix();
      matrix.translate(x + width * 0.5F, y + 3);
      matrix.scale(scale, scale);
      graphics.drawCenteredTextWithShadow(font, DAYS[day], 0, 0, -1);
      matrix.popMatrix();
      if (p >= 0.68F && p < 0.78F) {
         matrix.pushMatrix();
         matrix.translate(x + width * 0.5F, y + height * 0.45F);
         float jokeScale = Math.min(1.4F, (width - 6.0F) / Math.max(font.getWidth("drnames age"), font.getWidth("integer max")));
         matrix.scale(jokeScale, jokeScale);
         graphics.drawCenteredTextWithShadow(font, "drnames age", 0, 0, -1);
         graphics.drawCenteredTextWithShadow(font, "integer max", 0, 9 + 2, -1);
         matrix.popMatrix();
      }
   }

   static float skeletonScale(DeathScene scene) {
      return 1.0F;
   }

   static void trackSkeleton(Object state, DeathScene scene) {
      SKELETON_STATES.put(state, scene);
   }

   public static DeathScene skeletonScene(Object state) {
      return SKELETON_STATES.get(state);
   }

   public static int skeletonTint(DeathScene scene) {
      float p = scene.progress();
      float alpha = AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.78F, 0.86F))
         * (1.0F - AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.86F, 0.895F)));
      return Math.round(alpha * 255.0F) << 24 | 16777215;
   }

   static float skeletonFall(DeathScene scene) {
      return 88.0F * AnimationTimeline.smooth(AnimationTimeline.phase(scene.progress(), 0.86F, 0.96F));
   }

   static float skeletonBottom(DeathScene scene) {
      float angle = (float)Math.toRadians(skeletonFall(scene));
      return 0.98F - 0.98F * (float)Math.cos(angle) - 0.32F * (float)Math.sin(angle);
   }

   private static void setBlockTransform(
      DeathScene scene, BlockDisplayEntity display, float x, float y, float z, float sx, float sy, float sz, Quaternionf localRotation
   ) {
      float headPitch = -pose(scene, 0).xRot();
      Quaternionf head = new Quaternionf().rotationX(headPitch);
      Vector3f point = head.transform(new Vector3f(x, y - 1.407F, z)).add(0.0F, 1.407F, 0.0F);
      x = point.x;
      y = point.y;
      z = point.z;
      localRotation = new Quaternionf(head).mul(localRotation);
      setDisplayTransform(scene, display, x, y, z, sx, sy, sz, localRotation);
   }

   private static void setDisplayTransform(
      DeathScene scene, DisplayEntity display, float x, float y, float z, float sx, float sy, float sz, Quaternionf localRotation
   ) {
      DeathScene.Root root = scene.root();
      Quaternionf facing = new Quaternionf().rotationY((float)Math.toRadians(scene.sceneFacing));
      Vector3f local = new Vector3f(x, y, z).mul(root.sx(), root.sy(), root.sz());
      Quaternionf rootRotation = new Quaternionf().rotationX((float)Math.toRadians(root.pitch())).rotateZ((float)Math.toRadians(root.roll()));
      local.y = local.y - root.pivotY();
      rootRotation.transform(local);
      local.y = local.y + root.pivotY();
      facing.transform(local);
      Vector3f position = new Vector3f(local).add(root.x(), root.y(), root.z());
      Quaternionf rotation = new Quaternionf(facing).mul(rootRotation).mul(localRotation);
      if (display instanceof TextDisplayEntity && display != scene.props.get(10)) {
         rotation.identity();
      }

      Vector3f offset = display instanceof BlockDisplayEntity ? rotation.transform(new Vector3f(-sx * 0.5F, -sy * 0.5F, -sz * 0.5F)) : new Vector3f();
      display.setTransformation(new AffineTransformation(offset, rotation, new Vector3f(sx, sy, sz), new Quaternionf()));
      display.setPosition(scene.x + position.x, scene.y + position.y, scene.z + position.z);
      display.lastX = display.lastRenderX = display.getX();
      display.lastY = display.lastRenderY = display.getY();
      display.lastZ = display.lastRenderZ = display.getZ();
      DisplayStateAccess access = (DisplayStateAccess)display;
      access.deathanimations$setRenderState(access.deathanimations$freshRenderState());
      access.deathanimations$refreshSubState(false, 1.0F);
      access.deathanimations$setDirty(false);
   }
}
