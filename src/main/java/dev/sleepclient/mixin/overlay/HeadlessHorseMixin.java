package dev.sleepclient.mixin.overlay;

import dev.sleepclient.mods.OverlayMod;
import net.minecraft.client.model.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.render.entity.model.AbstractHorseEntityModel")
public abstract class HeadlessHorseMixin {

	/** 1.21.11: the head group is the "head" field. */
	@Shadow
	@Final
	protected ModelPart head;

	@Inject(method = "setAngles", at = @At("TAIL"), require = 0)
	private void sol$headless(@Coerce Object state, CallbackInfo ci) {
		this.head.visible = !OverlayMod.headlessHorse();
	}
}
