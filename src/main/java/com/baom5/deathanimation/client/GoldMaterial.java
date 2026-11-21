package com.baom5.deathanimation.client;

import com.baom5.deathanimation.mixin.client.GoldPipelineAccess;
import com.baom5.deathanimation.mixin.client.GoldRenderSetupAccess;
import com.baom5.deathanimation.mixin.client.GoldRenderTypeAccess;
import com.baom5.deathanimation.mixin.client.GoldTextureBindingAccess;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Builder;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.command.RenderCommandQueue;
import net.minecraft.client.render.command.OrderedRenderCommandQueue.Custom;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.item.ItemRenderState.Glint;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.MatrixStack.Entry;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class GoldMaterial {
   private static RenderPipeline pipeline;
   private static final Map<RenderLayer, RenderLayer> TYPES = new IdentityHashMap<>();

   private GoldMaterial() {
   }

   public static void init() {
      RenderPipeline original = RenderPipelines.ENTITY_TRANSLUCENT;
      Builder builder = RenderPipeline.builder(new Snippet[0])
         .withLocation(Identifier.of("deathanimation", "pipeline/gold"))
         .withVertexShader(Identifier.of("deathanimation", "core/gold"))
         .withFragmentShader(Identifier.of("deathanimation", "core/gold"))
         .withCull(false)
         .withDepthTestFunction(original.getDepthTestFunction())
         .withDepthWrite(true)
         .withVertexFormat(original.getVertexFormat(), original.getVertexFormatMode());
      original.getBlendFunction().ifPresent(builder::withBlend);
      original.getSamplers().forEach(builder::withSampler);
      original.getUniforms().forEach(u -> builder.withUniform(u.name(), u.type()));
      pipeline = GoldPipelineAccess.deathanimations$register(builder.build());
   }

   private static RenderLayer type(RenderLayer source) {
      return TYPES.computeIfAbsent(
         source,
         key -> {
            net.minecraft.client.render.RenderSetup.Builder setup = RenderSetup.builder(pipeline)
               .useLightmap()
               .useOverlay()
               .translucent()
               .expectedBufferSize(key.getExpectedBufferSize());
            GoldRenderSetupAccess access = (GoldRenderSetupAccess)(Object)((GoldRenderTypeAccess)(Object)key).deathanimations$setup();
            access.deathanimations$textures().forEach((name, value) -> setup.texture(name, ((GoldTextureBindingAccess)value).deathanimations$texture()));
            if (!access.deathanimations$textures().containsKey("Sampler0")) {
               setup.texture("Sampler0", Identifier.of("deathanimation:textures/entity/white_skin.png"));
            }

            return GoldRenderTypeAccess.deathanimations$create("deathanimation_gold_" + TYPES.size(), setup.build());
         }
      );
   }

   public static OrderedRenderCommandQueue collector(DeathScene scene, OrderedRenderCommandQueue delegate) {
      return collector(scene, delegate, null);
   }

   public static OrderedRenderCommandQueue collector(DeathScene scene, OrderedRenderCommandQueue delegate, Boolean mainHand) {
      return scene != null && scene.animation == DeathAnimationConfig.Animation.GOLD
         ? (OrderedRenderCommandQueue)proxy(scene, unwrap(delegate), mainHand, true)
         : delegate;
   }

   private static Object unwrap(Object delegate) {
      return Proxy.isProxyClass(delegate.getClass()) && Proxy.getInvocationHandler(delegate) instanceof GoldMaterial.GoldCall call ? call.delegate : delegate;
   }

   private static Object proxy(DeathScene scene, Object delegate, Boolean main, boolean ordered) {
      return Proxy.newProxyInstance(
         GoldMaterial.class.getClassLoader(),
         new Class[]{ordered ? OrderedRenderCommandQueue.class : RenderCommandQueue.class},
         new GoldMaterial.GoldCall(scene, delegate, main)
      );
   }

   private static GoldMaterial.GoldConsumer consumer(VertexConsumer target, Entry pose, DeathScene scene, float age, Boolean hand) {
      return new GoldMaterial.GoldConsumer(target, pose, scene, age, hand);
   }

   @Environment(EnvType.CLIENT)
   private record GoldCall(DeathScene scene, Object delegate, Boolean main) implements InvocationHandler {
      @Override
      public Object invoke(Object self, Method method, Object[] args) throws Throwable {
         if (args != null && args.length == 1 && args[0] instanceof Integer && RenderCommandQueue.class.isAssignableFrom(method.getReturnType())) {
            return GoldMaterial.proxy(this.scene, method.invoke(this.delegate, args), this.main, false);
         }

         float age = this.scene.gold().time();
         if (age <= 6.0F) {
            return this.forward(method, args);
         }

         if (args != null && args.length >= 9 && args[0] instanceof Model && args[2] instanceof MatrixStack && args[3] instanceof RenderLayer) {
            Model model = (Model)args[0];
            Object state = args[1];
            MatrixStack poses = (MatrixStack)args[2];
            RenderLayer original = (RenderLayer)args[3];
            if (original.toString().toLowerCase(Locale.ROOT).contains("glint")) {
               return null;
            }

            int light = (Integer)args[4];
            int overlay = (Integer)args[5];
            int color = (Integer)args[6];
            Sprite sprite = args.length == 10 ? (Sprite)args[7] : null;
            ((RenderCommandQueue)this.delegate).submitCustom(poses, GoldMaterial.type(original), (pose, vertices) -> {
               MatrixStack draw = new MatrixStack();
               draw.peek().copy(pose);
               GoldMaterial.GoldConsumer coated = GoldMaterial.consumer(vertices, pose, this.scene, age, model instanceof BipedEntityModel ? null : this.main);
               model.setAngles(state);
               if (model instanceof BipedEntityModel) {
                  DeathBodyRig.setPose(model, this.scene.gold().pose());
               }

               try {
                  model.render(draw, sprite == null ? coated : sprite.getTextureSpecificVertexConsumer(coated), light, overlay, color);
               } finally {
                  coated.finish();
                  DeathBodyRig.clear();
               }
            });
            return null;
         } else if (args != null && args.length >= 5 && args[0] instanceof ModelPart && args[1] instanceof MatrixStack && args[2] instanceof RenderLayer) {
            ModelPart part = (ModelPart)args[0];
            MatrixStack poses = (MatrixStack)args[1];
            RenderLayer original = (RenderLayer)args[2];
            int light = (Integer)args[3];
            int overlay = (Integer)args[4];
            Sprite sprite = args.length > 5 ? (Sprite)args[5] : null;
            int color = args.length == 11 ? (Integer)args[8] : -1;
            ((RenderCommandQueue)this.delegate).submitCustom(poses, GoldMaterial.type(original), (pose, vertices) -> {
               MatrixStack draw = new MatrixStack();
               draw.peek().copy(pose);
               GoldMaterial.GoldConsumer coated = GoldMaterial.consumer(vertices, pose, this.scene, age, this.main == null ? true : this.main);

               try {
                  part.render(draw, sprite == null ? coated : sprite.getTextureSpecificVertexConsumer(coated), light, overlay, color);
               } finally {
                  coated.finish();
               }
            });
            return null;
         } else if (args != null && args.length == 9 && args[0] instanceof MatrixStack && args[1] instanceof ItemDisplayContext) {
            MatrixStack poses = (MatrixStack)args[0];
            ItemDisplayContext context = (ItemDisplayContext)args[1];
            int light = (Integer)args[2];
            int overlay = (Integer)args[3];
            int[] tints = (int[])((int[])args[5]).clone();
            List quads = (List)args[6];
            RenderLayer original = (RenderLayer)args[7];
            ((RenderCommandQueue)this.delegate).submitCustom(poses, GoldMaterial.type(original), (pose, vertices) -> {
               MatrixStack draw = new MatrixStack();
               draw.peek().copy(pose);
               GoldMaterial.GoldConsumer coated = GoldMaterial.consumer(vertices, pose, this.scene, age, this.main == null ? true : this.main);

               try {
                  ItemRenderer.renderItem(context, draw, requested -> coated, light, overlay, tints, quads, GoldMaterial.type(original), Glint.NONE);
               } finally {
                  coated.finish();
               }
            });
            return null;
         } else if (args != null && args.length == 3 && args[0] instanceof MatrixStack && args[1] instanceof RenderLayer && args[2] instanceof Custom) {
            MatrixStack poses = (MatrixStack)args[0];
            RenderLayer original = (RenderLayer)args[1];
            Custom geometry = (Custom)args[2];
            ((RenderCommandQueue)this.delegate).submitCustom(poses, GoldMaterial.type(original), (pose, vertices) -> {
               GoldMaterial.GoldConsumer coated = GoldMaterial.consumer(vertices, pose, this.scene, age, this.main == null ? true : this.main);

               try {
                  geometry.render(pose, coated);
               } finally {
                  coated.finish();
               }
            });
            return null;
         } else {
            return this.forward(method, args);
         }
      }

      private Object forward(Method method, Object[] args) throws Throwable {
         try {
            return method.invoke(this.delegate, args);
         } catch (InvocationTargetException error) {
            throw error.getCause();
         }
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class GoldConsumer implements VertexConsumer {
      final VertexConsumer target;
      final Matrix4f inverse;
      final DeathScene scene;
      final float age;
      final Boolean hand;
      boolean pending;
      float x;
      float y;
      float z;
      float u;
      float v;
      float nx;
      float ny;
      float nz;
      float amount;
      int r;
      int g;
      int b;
      int ou;
      int ov;
      int lu;
      int lv;

      GoldConsumer(VertexConsumer target, Entry pose, DeathScene scene, float age, Boolean hand) {
         this.target = target;
         this.inverse = new Matrix4f(pose.getPositionMatrix()).invert();
         this.scene = scene;
         this.age = age;
         this.hand = hand;
      }

      void finish() {
         if (this.pending) {
            this.target
               .vertex(this.x, this.y, this.z)
               .color(
                  Math.round(this.r + (255 - this.r) * this.amount),
                  Math.round(this.g + (255 - this.g) * this.amount),
                  Math.round(this.b + (255 - this.b) * this.amount),
                  Math.round(this.amount * 255.0F)
               )
               .texture(this.u, this.v)
               .overlay(this.ou, this.ov)
               .light(this.lu, this.lv)
               .normal(this.nx, this.ny, this.nz);
            this.pending = false;
         }
      }

      public VertexConsumer vertex(float x, float y, float z) {
         this.finish();
         this.x = x;
         this.y = y;
         this.z = z;
         this.pending = true;
         this.r = this.g = this.b = 255;
         this.u = this.v = 0.5F;
         this.ou = this.ov = 0;
         this.lu = this.lv = 240;
         this.nx = this.nz = 0.0F;
         this.ny = 1.0F;
         Vector3f local = this.inverse.transformPosition(new Vector3f(x, y, z));
         this.amount = this.hand == null ? this.scene.gold().coverage(local, this.age) : this.scene.gold().itemCoverage(this.age, this.hand);
         return this;
      }

      public VertexConsumer color(int red, int green, int blue, int alpha) {
         this.r = red;
         this.g = green;
         this.b = blue;
         return this;
      }

      public VertexConsumer color(int argb) {
         return this.color(argb >>> 16 & 0xFF, argb >>> 8 & 0xFF, argb & 0xFF, argb >>> 24);
      }

      public VertexConsumer texture(float u, float v) {
         this.u = u;
         this.v = v;
         return this;
      }

      public VertexConsumer overlay(int u, int v) {
         this.ou = u;
         this.ov = v;
         return this;
      }

      public VertexConsumer light(int u, int v) {
         this.lu = u;
         this.lv = v;
         return this;
      }

      public VertexConsumer normal(float x, float y, float z) {
         this.nx = x;
         this.ny = y;
         this.nz = z;
         return this;
      }

      public VertexConsumer lineWidth(float width) {
         return this;
      }
   }
}
