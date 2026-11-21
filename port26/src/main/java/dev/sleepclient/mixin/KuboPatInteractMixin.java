package dev.sleepclient.mixin;

import dev.sleepclient.mods.PatPatMod;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * PatPat: crouch + right-click a dog with an empty hand to pat it. The click stays on this computer (nothing is sent),
 * so the dog does not sit down / stand up.
 */
@Mixin(MultiPlayerGameMode.class)
public class KuboPatInteractMixin {

	@Inject(method = "interact", at = @At("HEAD"), cancellable = true, require = 0)
	private void kubo$patAt(Player player, Entity entity, EntityHitResult hit, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		if (PatPatMod.tryPat(player, entity, hand)) {
			cir.setReturnValue(InteractionResult.SUCCESS);
		}
	}

	@Inject(method = "interact", at = @At("HEAD"), cancellable = true, require = 0)
	private void kubo$pat(Player player, Entity entity, EntityHitResult hit, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		if (PatPatMod.patting(entity)) {
			cir.setReturnValue(InteractionResult.SUCCESS); // the same click: never let it reach the server
		}
	}
}
