package dev.solclient.mixin.overlay;

import dev.solclient.mods.NoRenderMod;
import net.minecraft.client.render.Frustum;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No Render: chosen kinds of entities are never drawn. */
@Mixin(targets = "net.minecraft.client.render.entity.EntityRenderDispatcher")
public abstract class NoRenderMixin {

	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0)
	private void kubo$noRender(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
		if (NoRenderMod.hide(entity)) {
			cir.setReturnValue(false);
		}
	}
}
