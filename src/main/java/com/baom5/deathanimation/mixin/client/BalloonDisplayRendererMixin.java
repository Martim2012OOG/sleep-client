package com.baom5.deathanimation.mixin.client;

import com.baom5.deathanimation.client.BalloonFarewell;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.DisplayEntityRenderer.BlockDisplayEntityRenderer;
import net.minecraft.client.render.entity.state.BlockDisplayEntityRenderState;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(BlockDisplayEntityRenderer.class)
public abstract class BalloonDisplayRendererMixin {
   @Unique
   private final Map<BlockDisplayEntityRenderState, Float> deathanimations$balloonAlpha = new WeakHashMap<>();

   @Inject(method = "method_62407", at = @At("TAIL"))
   private void deathanimations$captureFade(BlockDisplayEntity prop, BlockDisplayEntityRenderState state, float partial, CallbackInfo ci) {
      Float alpha = BalloonFarewell.propAlpha(prop);
      if (alpha == null) {
         this.deathanimations$balloonAlpha.remove(state);
      } else {
         this.deathanimations$balloonAlpha.put(state, alpha);
      }
   }

   @Inject(method = "method_49054", at = @At("HEAD"), cancellable = true)
   private void deathanimations$fadeBalloon(
      BlockDisplayEntityRenderState state, MatrixStack poses, OrderedRenderCommandQueue collector, int light, float partial, CallbackInfo ci
   ) {
      Float alpha = this.deathanimations$balloonAlpha.get(state);
      if (alpha != null) {
         ci.cancel();
         if (!(alpha <= 0.0F) && state.data != null) {
            BlockStateModel model = MinecraftClient.getInstance().getBlockRenderManager().getModel(state.data.blockState());
            collector.submitCustom(
               poses,
               RenderLayers.entityTranslucent(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE),
               (pose, vertices) -> BlockModelRenderer.render(
                  pose, new BalloonFarewell.FadingVertices(vertices, alpha), model, 1.0F, 1.0F, 1.0F, light, OverlayTexture.DEFAULT_UV
               )
            );
         }
      }
   }
}
