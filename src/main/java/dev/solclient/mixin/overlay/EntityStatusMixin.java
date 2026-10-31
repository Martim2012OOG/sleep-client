package dev.solclient.mixin.overlay;

import dev.solclient.ext.KuboApi;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Passes entity statuses (totem pops...) to extensions. Only reads them, never changes anything. */
@Mixin(LivingEntity.class)
public abstract class EntityStatusMixin {

	@Inject(method = "handleStatus(B)V", at = @At("HEAD"), require = 0)
	private void kubo$status(byte status, CallbackInfo ci) {
		KuboApi.entityStatus((LivingEntity) (Object) this, status);
	}
}
