package dev.solclient.mixin;

import dev.solclient.mods.OverlayMod;
import net.minecraft.client.render.entity.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 1.21.9+: EntityRenderDispatcher foi renomeado para EntityRenderManager e as sombras
// passaram a ser calculadas no EntityRenderer (updateShadow). Cancelar isto = sem sombra.
@Mixin(EntityRenderer.class)
public class EntityRenderDispatcherMixin {

	@Inject(method = "updateShadow(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/entity/state/EntityRenderState;)V",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void sol$noShadowA(CallbackInfo ci) {
		if (!OverlayMod.entityShadows()) {
			ci.cancel();
		}
	}

	@Inject(method = "updateShadow(Lnet/minecraft/client/render/entity/state/EntityRenderState;Lnet/minecraft/client/MinecraftClient;Lnet/minecraft/world/World;)V",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void sol$noShadowB(CallbackInfo ci) {
		if (!OverlayMod.entityShadows()) {
			ci.cancel();
		}
	}
}
