package dev.solclient.mixin.social;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Shows your own nametag (with the Plegumo logo) in third person, like Lunar / Essential. */
@Mixin(LivingEntityRenderer.class)
public abstract class OwnNametagMixin {

	@Inject(method = "hasLabel(Lnet/minecraft/entity/LivingEntity;D)Z", at = @At("RETURN"), cancellable = true, require = 0)
	private void plegumo$ownName(LivingEntity entity, double distance, CallbackInfoReturnable<Boolean> cir) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (!cir.getReturnValueZ() && entity == mc.player && dev.solclient.mods.OwnNametagMod.on() && !mc.options.hudHidden && !entity.isInvisible() && !dev.solclient.mods.OwnNametagMod.serverHandlesName(mc.player)
				&& !(mc.currentScreen != null && entity == mc.getCameraEntity() && mc.options.getPerspective().isFirstPerson())) {
			cir.setReturnValue(true);
		}
	}
}
