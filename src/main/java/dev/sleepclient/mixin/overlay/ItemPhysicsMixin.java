package dev.sleepclient.mixin.overlay;

import dev.sleepclient.fx.ItemPhysicsState;
import dev.sleepclient.mods.ItemPhysicsMod;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;
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
	private Random random;

	@Inject(method = "updateRenderState(Lnet/minecraft/entity/ItemEntity;Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;F)V",
			at = @At("TAIL"), require = 0)
	private void kubo$physicsState(ItemEntity item, ItemEntityRenderState state, float tickProgress, CallbackInfo ci) {
		if ((Object) state instanceof ItemPhysicsState p) {
			p.kubo$setPhysics(item.isOnGround(), item.isTouchingWater());
		}
	}

	@Inject(method = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void kubo$physicsRender(ItemEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera,
			CallbackInfo ci) {
		if (!ItemPhysicsMod.on() || state.itemRenderState == null || state.itemRenderState.isEmpty() || !((Object) state instanceof ItemPhysicsState p)) {
			return;
		}
		boolean pushed = false;
		try {
			Box box = state.itemRenderState.getModelBoundingBox();
			matrices.push();
			pushed = true;
			ItemPhysicsMod.transform(matrices, box, state.age, state.uniqueOffset, p.kubo$onGround(), p.kubo$inWater());
			ItemEntityRendererInvoker.kubo$renderStack(matrices, queue, state.light, state, random, box);
			ci.cancel();
		} catch (Throwable e) {
			ItemPhysicsMod.broken(e); // something changed in Minecraft: go back to the normal drawing
		} finally {
			if (pushed) {
				matrices.pop();
			}
		}
	}
}
