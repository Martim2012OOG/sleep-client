package com.baom5.deathanimation.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.equipment.trim.ArmorTrim;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class GoldStatue {
   public static final int START = 6;
   public static final int FULL = 54;
   public static final int TIP = 64;
   public static final int TICKS = 140;
   private static final double WALK_PUSH = 420.0;
   private static final double CLICK_PUSH = 144.0;
   private final DeathScene scene;
   private final DeathBodyRig.Motion[] frozen = new DeathBodyRig.Motion[6];
   private final EnumMap<EquipmentSlot, ItemStack> originals = new EnumMap<>(EquipmentSlot.class);
   private final ItemRenderState[] handModels = new ItemRenderState[2];
   private final ItemRenderState[] originalHandModels = new ItemRenderState[2];
   private final List<RigidStatuePhysics.Box> shape = new ArrayList<>();
   private final Vec3d center;
   private final Vector3f seed = new Vector3f();
   private float radius = 0.5F;
   private RigidStatuePhysics body;
   private int simulated;
   private double impact;
   private int lastImpact = -100;
   private boolean goldEquipment;

   GoldStatue(DeathScene scene, AbstractClientPlayerEntity source) {
      this.scene = scene;

      for (EquipmentSlot slot : EquipmentSlot.values()) {
         this.originals.put(slot, scene.avatar.getEquippedStack(slot).copy());
      }

      LivingEntityRenderer renderer = (LivingEntityRenderer)MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(source);
      BipedEntityModel model = (BipedEntityModel)renderer.getModel();
      IdentityHashMap<ModelPart, ModelTransform> saved = new IdentityHashMap<>();
      IdentityHashMap<ModelPart, Boolean> visibility = new IdentityHashMap<>();
      model.getRootPart().traverse().forEach(bonex -> {
         saved.put(bonex, bonex.getTransform());
         visibility.put(bonex, bonex.visible);
      });
      EntityRenderState state = renderer.getAndUpdateRenderState(source, 1.0F);

      for (Arm arm : Arm.values()) {
         int index = arm == Arm.RIGHT ? 0 : 1;
         EquipmentSlot slot = arm == source.getMainArm() ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
         ItemRenderState held = new ItemRenderState();
         MinecraftClient.getInstance()
            .getItemModelManager()
            .updateForLivingEntity(
               held,
               this.originals.get(slot),
               arm == Arm.RIGHT ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND : ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
               source
            );
         this.originalHandModels[index] = this.handModels[index] = held;
      }

      model.setAngles(state);
      Vec3d weighted = Vec3d.ZERO;
      float totalMass = 0.0F;
      float[] masses = new float[]{5.0F, 30.0F, 4.0F, 4.0F, 9.0F, 9.0F};
      Quaternionf worldFacing = new Quaternionf().rotationY((float)Math.toRadians(180.0F - scene.avatar.bodyYaw));
      ArrayList<RigidStatuePhysics.Box> uncentered = new ArrayList<>();
      ArrayList<Vector3f> modelVertices = new ArrayList<>();

      try {
         for (DeathBodyRig.Part part : DeathBodyRig.Part.values()) {
            int i = part.ordinal();
            ModelPart bone = DeathBodyRig.part(model, part);
            ModelTransform initial = bone.getDefaultTransform();
            this.frozen[i] = new DeathBodyRig.Motion(
               false,
               (bone.originX - initial.x()) / 16.0F,
               (bone.originY - initial.y()) / 16.0F,
               (bone.originZ - initial.z()) / 16.0F,
               bone.pitch - initial.pitch(),
               bone.yaw - initial.yaw(),
               bone.roll - initial.roll()
            );
            DeathBodyRig.Geometry geometry = DeathBodyRig.geometry(bone);
            Quaternionf boneRotation = new Quaternionf().rotationZYX(bone.roll, bone.yaw, bone.pitch);
            Vector3f local = boneRotation.transform(geometry.center()).add(bone.originX / 16.0F, bone.originY / 16.0F, bone.originZ / 16.0F);
            Vec3d wc = this.worldPoint(local, worldFacing);
            Vector3f half = new Vector3f();

            for (Vector3f v : geometry.vertices()) {
               half.max(new Vector3f(Math.abs(v.x), Math.abs(v.y), Math.abs(v.z)).mul(0.9375F));
            }

            EquipmentSlot slot = i == 0 ? EquipmentSlot.HEAD : (i < 4 ? EquipmentSlot.CHEST : EquipmentSlot.LEGS);
            if (!this.originals.get(slot).isEmpty()) {
               half.add(0.035F, 0.035F, 0.035F);
            }

            if (i >= 4 && !this.originals.get(EquipmentSlot.FEET).isEmpty()) {
               half.add(0.025F, 0.025F, 0.025F);
            }

            uncentered.add(new RigidStatuePhysics.Box(wc, half, new Quaternionf(worldFacing).rotateZ(3.1415927F).mul(boneRotation)));
            weighted = weighted.add(wc.multiply(masses[i]));
            totalMass += masses[i];

            for (Vector3f v : geometry.vertices()) {
               modelVertices.add(
                  boneRotation.transform(new Vector3f(v).add(geometry.center())).add(bone.originX / 16.0F, bone.originY / 16.0F, bone.originZ / 16.0F)
               );
            }
         }

         int arm = source.getMainArm() == Arm.RIGHT ? 3 : 2;
         ModelPart bone = DeathBodyRig.part(model, DeathBodyRig.Part.values()[arm]);
         new Quaternionf().rotationZYX(bone.roll, bone.yaw, bone.pitch).transform(new Vector3f(0.0F, 0.65F, 0.0F), this.seed);
         this.seed.add(bone.originX / 16.0F, bone.originY / 16.0F, bone.originZ / 16.0F);

         for (Vector3f v : modelVertices) {
            this.radius = Math.max(this.radius, v.distance(this.seed) + 0.18F);
         }

         this.addItemShape(model, Arm.RIGHT, worldFacing, uncentered);
         this.addItemShape(model, Arm.LEFT, worldFacing, uncentered);
      } finally {
         saved.forEach(ModelPart::setTransform);
         visibility.forEach((bonex, visible) -> bonex.visible = visible);
      }

      this.center = weighted.multiply(1.0F / totalMass);

      for (RigidStatuePhysics.Box box : uncentered) {
         this.shape.add(new RigidStatuePhysics.Box(box.center().subtract(this.center), box.half(), box.rotation()));
      }

      scene.avatar.copyModelParts(source);
   }

   private Vec3d worldPoint(Vector3f model, Quaternionf facing) {
      Vector3f p = facing.transform(new Vector3f(-model.x, 1.501F - model.y, model.z).mul(0.9375F));
      return new Vec3d(p.x, p.y, p.z);
   }

   private void addItemShape(BipedEntityModel<?> model, Arm arm, Quaternionf facing, List<RigidStatuePhysics.Box> boxes) {
      boolean main = arm == this.scene.avatar.getMainArm();
      ItemStack stack = this.originals.get(main ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
      if (!stack.isEmpty()) {
         ItemRenderState item = this.handModels[arm == Arm.RIGHT ? 0 : 1];
         ModelPart bone = DeathBodyRig.part(model, arm == Arm.RIGHT ? DeathBodyRig.Part.RIGHT_ARM : DeathBodyRig.Part.LEFT_ARM);
         MatrixStack poses = new MatrixStack();
         bone.applyTransform(poses);
         poses.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90.0F));
         poses.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F));
         poses.translate((arm == Arm.LEFT ? -1 : 1) / 16.0F, 0.125F, -0.625F);
         Vector3f low = new Vector3f(1.0F / 0.0F);
         Vector3f high = new Vector3f(-1.0F / 0.0F);
         item.load(v -> {
            Vector3f point = poses.peek().getPositionMatrix().transformPosition(new Vector3f(v));
            low.min(point);
            high.max(point);
         });
         if (Float.isFinite(low.x)) {
            Vector3f midpoint = new Vector3f(low).add(high).mul(0.5F);
            Vector3f half = new Vector3f(high).sub(low).mul(0.46875F);
            half.max(new Vector3f(0.015F));
            boxes.add(new RigidStatuePhysics.Box(this.worldPoint(midpoint, facing), half, new Quaternionf(facing).rotateZ(3.1415927F)));
         }
      }
   }

   public DeathBodyRig.Motion[] pose() {
      return (DeathBodyRig.Motion[])this.frozen.clone();
   }

   public float time() {
      return this.scene.age + MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(false) * DeathAnimationConfig.INSTANCE.animationSpeed;
   }

   public float coverage(Vector3f point, float age) {
      if (age <= 6.0F) {
         return 0.0F;
      }

      if (age >= 54.0F) {
         return 1.0F;
      }

      float reach = this.radius * AnimationTimeline.smooth(AnimationTimeline.phase(age, 6.0F, 54.0F));
      float d = point.distance(this.seed);
      return AnimationTimeline.smooth(AnimationTimeline.phase(reach - d, -0.1F, 0.1F));
   }

   public float itemCoverage(float age, boolean main) {
      return AnimationTimeline.smooth(AnimationTimeline.phase(age, main ? 28.0F : 34.0F, 54.0F));
   }

   public void tick() {
      this.prepareEquipment();
      this.ensure(this.scene.age);
      if (this.body != null && this.scene.age >= 64.0F && !this.scene.gui) {
         for (AbstractClientPlayerEntity player : this.scene.level.getPlayers()) {
            if (player.isAlive() && !(player instanceof DeathAvatar) && player.getBoundingBox().intersects(this.body.bounds())) {
               Vec3d movement = player.getEntityPos().subtract(new Vec3d(player.lastRenderX, player.lastRenderY, player.lastRenderZ));
               movement = new Vec3d(movement.x, 0.0, movement.z);
               if (movement.lengthSquared() > 0.1225) {
                  movement = movement.normalize().multiply(0.35);
               }

               if (!(movement.lengthSquared() < 1.0E-4)) {
                  Vec3d contact = this.body.contactPoint(player.getBoundingBox());
                  if (contact != null) {
                     this.body.impulse(movement.multiply(420.0), contact);
                  }
               }
            }
         }
      }
   }

   public boolean interact(AbstractClientPlayerEntity player) {
      if (!this.scene.gui && player.isAlive() && !(this.time() < 64.0F)) {
         this.ensure(this.time());
         if (this.body == null) {
            return false;
         }

         Vec3d eye = player.getEyePos();
         Vec3d direction = player.getRotationVector().normalize();
         Vec3d contact = this.body.rayHit(eye, eye.add(direction.multiply(4.5)));
         if (contact == null) {
            return false;
         }

         BlockHitResult obstruction = this.scene.level.raycast(new RaycastContext(eye, contact, ShapeType.OUTLINE, FluidHandling.NONE, player));
         if (obstruction.getType() == Type.BLOCK && obstruction.getPos().squaredDistanceTo(eye) < contact.squaredDistanceTo(eye) - 1.0E-4) {
            return false;
         }

         this.body.impulse(direction.multiply(144.0), contact);
         return true;
      } else {
         return false;
      }
   }

   public void prepareEquipment() {
      if (this.time() >= 54.0F && !this.goldEquipment) {
         for (EquipmentSlot slot : EquipmentSlot.values()) {
            this.scene.avatar.equipStack(slot, golden(this.originals.get(slot), slot));
         }

         for (Arm arm : Arm.values()) {
            EquipmentSlot slot = arm == this.scene.avatar.getMainArm() ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
            if (this.scene.avatar.getEquippedStack(slot).getItem() != this.originals.get(slot).getItem()) {
               ItemRenderState item = new ItemRenderState();
               MinecraftClient.getInstance()
                  .getItemModelManager()
                  .updateForLivingEntity(
                     item,
                     this.scene.avatar.getEquippedStack(slot),
                     arm == Arm.RIGHT ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND : ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
                     this.scene.avatar
                  );
               this.handModels[arm == Arm.RIGHT ? 0 : 1] = item;
            }
         }

         this.goldEquipment = true;
      } else if (this.time() < 54.0F && this.goldEquipment) {
         this.originals.forEach((slotx, stack) -> this.scene.avatar.equipStack(slotx, stack.copy()));
         this.goldEquipment = false;
         System.arraycopy(this.originalHandModels, 0, this.handModels, 0, 2);
      }
   }

   public Vec3d sparkOrigin() {
      return this.worldPoint(this.seed, new Quaternionf().rotationY((float)Math.toRadians(180.0F - this.scene.avatar.bodyYaw)))
         .add(this.scene.x, this.scene.y, this.scene.z);
   }

   public ItemRenderState handModel(Arm arm) {
      return this.handModels[arm == Arm.RIGHT ? 0 : 1];
   }

   private void ensure(float age) {
      int target = Math.max(0, (int)Math.ceil(age - 64.0F));
      if (this.body != null && target < this.simulated - 1) {
         this.body = null;
         this.simulated = 0;
      }

      if (!(age < 64.0F)) {
         if (this.body == null) {
            Vector3f direction = new Quaternionf()
               .rotationY((float)Math.toRadians(180.0F - this.scene.avatar.bodyYaw))
               .transform(new Vector3f(0.14F, 0.0F, 0.018F));
            Vec3d inherited = this.scene.gui ? Vec3d.ZERO : this.scene.inheritedVelocity;
            if (inherited.lengthSquared() > 0.36) {
               inherited = inherited.normalize().multiply(0.6);
            }

            this.body = new RigidStatuePhysics(
               this.shape,
               new Vec3d(this.scene.x, this.scene.y, this.scene.z).add(this.center),
               inherited.multiply(0.3),
               new Vec3d(direction.x, direction.y, direction.z)
            );
         }

         while (this.simulated < target) {
            this.body.tick(this.scene.level, this.scene.gui, this.scene.gui ? this.scene.y : this.scene.floorY);
            this.simulated++;
            if (this.body.impact() > 0.1 && this.simulated - this.lastImpact > 5) {
               this.impact = Math.max(this.impact, this.body.impact());
               this.lastImpact = this.simulated;
            }
         }
      }
   }

   public DeathScene.Root root() {
      float age = this.time();
      this.ensure(age);
      if (!(age < 64.0F) && this.body != null) {
         float blend = this.simulated == 0 ? 1.0F : Math.clamp(age - 64.0F - (this.simulated - 1), 0.0F, 1.0F);
         Quaternionf rotation = this.body.rotation(blend);
         Vector3f pivot = rotation.transform(new Vector3f((float)this.center.x, (float)this.center.y, (float)this.center.z));
         Vec3d p = this.body.position(blend).subtract(this.scene.x + pivot.x, this.scene.y + pivot.y, this.scene.z + pivot.z);
         Vector3f angle = rotation.getEulerAnglesYXZ(new Vector3f());
         float deg = 57.295776F;
         return new DeathScene.Root((float)p.x, (float)p.y, (float)p.z, angle.x * deg, angle.y * deg, angle.z * deg);
      } else {
         return DeathScene.Root.STILL;
      }
   }

   public Box bounds() {
      this.ensure(this.time());
      return this.body == null
         ? new Box(this.scene.x - 2.0, this.scene.y - 0.2, this.scene.z - 2.0, this.scene.x + 2.0, this.scene.y + 3.0, this.scene.z + 2.0)
         : this.body.bounds().expand(0.2);
   }

   public boolean sleeping() {
      return this.body != null && this.body.sleeping();
   }

   public String diagnostics() {
      return this.body == null ? "not released" : this.body.diagnostics();
   }

   public double consumeImpact() {
      double value = this.impact;
      this.impact = 0.0;
      return value;
   }

   public int boxCount() {
      return this.shape.size();
   }

   public Map<EquipmentSlot, ItemStack> originalEquipment() {
      return Collections.unmodifiableMap(this.originals);
   }

   public static ItemStack golden(ItemStack original, EquipmentSlot slot) {
      if (original.isEmpty()) {
         return ItemStack.EMPTY;
      }

      Identifier id = Registries.ITEM.getId(original.getItem());
      String path = id.getPath();
      String target = null;
      Identifier model = (Identifier)original.get(DataComponentTypes.ITEM_MODEL);
      EquippableComponent equippable = (EquippableComponent)original.get(DataComponentTypes.EQUIPPABLE);
      boolean custom = !id.getNamespace().equals("minecraft")
         || model != null && !model.equals(id)
         || equippable != null && equippable.assetId().isPresent() && !((RegistryKey)equippable.assetId().get()).getValue().getNamespace().equals("minecraft");
      if (!custom) {
         for (String tool : List.of("sword", "axe", "pickaxe", "shovel", "hoe", "spear")) {
            if (path.matches("(wooden|stone|copper|iron|diamond|netherite|golden)_" + tool)) {
               target = "golden_" + tool;
            }
         }

         for (String armor : List.of("helmet", "chestplate", "leggings", "boots")) {
            if (path.matches("(leather|chainmail|copper|iron|diamond|netherite|golden|turtle)_" + armor)) {
               target = "golden_" + armor;
            }
         }

         if (List.of("iron_ingot", "copper_ingot", "netherite_ingot").contains(path)) {
            target = "gold_ingot";
         }

         if (path.equals("iron_nugget")) {
            target = "gold_nugget";
         }

         if (List.of("coal", "charcoal", "raw_iron", "raw_copper").contains(path)) {
            target = "raw_gold";
         }

         if (List.of("cobblestone", "stone", "dirt", "iron_ore", "copper_ore").contains(path)) {
            target = "gold_ore";
         }

         if (List.of("deepslate", "cobbled_deepslate", "deepslate_iron_ore", "deepslate_copper_ore").contains(path)) {
            target = "deepslate_gold_ore";
         }

         if (List.of("iron_block", "copper_block", "diamond_block", "netherite_block").contains(path)) {
            target = "gold_block";
         }

         if (path.equals("apple")) {
            target = "golden_apple";
         }

         if (path.equals("carrot")) {
            target = "golden_carrot";
         }
      }

      ItemStack result = original.copy();
      if (target != null) {
         Identifier key = Identifier.ofVanilla(target);
         if (Registries.ITEM.containsId(key)) {
            result = new ItemStack((ItemConvertible)Registries.ITEM.get(key), original.getCount());
         }
      }

      if (original.contains(DataComponentTypes.TRIM)) {
         result.set(DataComponentTypes.TRIM, (ArmorTrim)original.get(DataComponentTypes.TRIM));
      }

      result.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, false);
      return result;
   }
}
