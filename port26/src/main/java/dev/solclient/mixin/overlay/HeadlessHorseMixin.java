package dev.solclient.mixin.overlay;

import dev.solclient.mods.OverlayMod;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.model.animal.equine.AbstractEquineModel")
public abstract class HeadlessHorseMixin {

	/** 1.21.11: the head group is the "head" field. */
	@Shadow
	@Final
	protected ModelPart headParts;

	@Inject(method = "setupAnim", at = @At("TAIL"), require = 0)
	private void sol$headless(@Coerce Object state, CallbackInfo ci) {
		this.headParts.visible = !OverlayMod.headlessHorse();
	}
}
