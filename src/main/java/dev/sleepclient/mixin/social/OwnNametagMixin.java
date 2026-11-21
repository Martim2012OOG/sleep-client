package dev.sleepclient.mixin.social;

import dev.sleepclient.mods.NameTagStyleMod;
import dev.sleepclient.mods.OwnNametagMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Your own nametag (with the Plegumo logo) in third person, like Lunar / Essential, and the "Always show names" options of
 * Name Tag Style: other players' names (and yours) show even when the server hides them.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class OwnNametagMixin {

	@Inject(method = "hasLabel(Lnet/minecraft/entity/LivingEntity;D)Z", at = @At("RETURN"), cancellable = true, require = 0)
	private void plegumo$ownName(LivingEntity entity, double distance, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (entity == mc.player) {
			if (mc.player == null || mc.options.hudHidden || entity.isInvisible()
					|| mc.currentScreen != null && entity == mc.getCameraEntity() && mc.options.getPerspective().isFirstPerson()) {
				return;
			}
			// only a real plate of the server (text display / armor stand over you) keeps your name away: a team that merely HIDES names does not, hiding is what this overrides
			boolean normal = OwnNametagMod.on() && !OwnNametagMod.customPlate(mc.player);
			// forced: the server hides the name (a team rule, say) but draws no plate of its own
			boolean forced = NameTagStyleMod.forceOwn() && !OwnNametagMod.customPlate(mc.player);
			if (normal || forced) {
				cir.setReturnValue(true);
			}
		} else if (entity instanceof PlayerEntity p && NameTagStyleMod.forceOthers() && !p.isInvisible() && !p.isSpectator() && distance < 4096.0) {
			cir.setReturnValue(true);
		}
	}
}
