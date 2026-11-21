package dev.sleepclient.mixin.overlay;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.sleepclient.fx.ItemPhysicsState;
import dev.sleepclient.mods.ItemPhysicsMod;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Item Physics (Drop Physics): dropped items lie flat on the ground at a random angle instead of floating and
 * spinning, tumble while they fly, and blocks sit on the ground. Only the drawing changes, never the item itself.
 */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemPhysicsMixin {

	@Shadow
	@Final
	private RandomSource random;

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V",
			at = @At("TAIL"), require = 0)
	private void kubo$physicsState(ItemEntity item, ItemEntityRenderState state, float tickProgress, CallbackInfo ci) {
		if ((Object) state instanceof ItemPhysicsState p) {
			p.kubo$setPhysics(item.onGround(), item.isInWater());
		}
	}

	@Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void kubo$physicsRender(ItemEntityRenderState state, PoseStack matrices, SubmitNodeCollector queue, CameraRenderState camera,
			CallbackInfo ci) {
		if (!ItemPhysicsMod.on() || state.item == null || state.item.isEmpty() || !((Object) state instanceof ItemPhysicsState p)) {
			return;
		}
		boolean pushed = false;
		try {
			AABB box = state.item.getModelBoundingBox();
			matrices.pushPose();
			pushed = true;
			ItemPhysicsMod.transform(matrices, box, state.ageInTicks, state.bobOffset, p.kubo$onGround(), p.kubo$inWater());
			ItemEntityRendererInvoker.kubo$renderStack(matrices, queue, state.lightCoords, state, random, box);
			ci.cancel();
		} catch (Throwable e) {
			ItemPhysicsMod.broken(e); // something changed in Minecraft: go back to the normal drawing
		} finally {
			if (pushed) {
				matrices.popPose();
			}
		}
	}
}
