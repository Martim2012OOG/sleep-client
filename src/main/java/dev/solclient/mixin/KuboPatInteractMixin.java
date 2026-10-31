package dev.solclient.mixin;

import dev.solclient.mods.PatPatMod;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * PatPat: crouch + right-click a dog with an empty hand to pat it. The click stays on this computer (nothing is sent),
 * so the dog does not sit down / stand up.
 */
@Mixin(ClientPlayerInteractionManager.class)
public class KuboPatInteractMixin {

	@Inject(method = "interactEntityAtLocation", at = @At("HEAD"), cancellable = true, require = 0)
	private void kubo$patAt(PlayerEntity player, Entity entity, EntityHitResult hit, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
		if (PatPatMod.tryPat(player, entity, hand)) {
			cir.setReturnValue(ActionResult.SUCCESS);
		}
	}

	@Inject(method = "interactEntity", at = @At("HEAD"), cancellable = true, require = 0)
	private void kubo$pat(PlayerEntity player, Entity entity, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
		if (PatPatMod.patting(entity)) {
			cir.setReturnValue(ActionResult.SUCCESS); // the same click: never let it reach the server
		}
	}
}
