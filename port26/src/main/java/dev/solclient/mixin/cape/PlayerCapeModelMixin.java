package dev.solclient.mixin.cape;

import dev.solclient.mods.CapePhysicsMod;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerCapeModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Segmented cape model + per-strip physics (Cape Physics mod). Own mixin config, required=false. */
@Mixin(PlayerCapeModel.class)
public abstract class PlayerCapeModelMixin {

	@Shadow @Final private ModelPart cape;

	@Unique
	private ModelPart[] plegumo$segs;
	@Unique
	private boolean plegumo$looked;

	@Inject(method = "createCapeLayer", at = @At("HEAD"), cancellable = true, require = 0)
	private static void plegumo$segmentedCape(CallbackInfoReturnable<LayerDefinition> cir) {
		try {
			cir.setReturnValue(CapePhysicsMod.modelData());
		} catch (Throwable t) {
			System.err.println("[Sleep] cape physics model failed, using the vanilla cape: " + t);
		}
	}

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"), require = 0)
	private void plegumo$physics(AvatarRenderState state, CallbackInfo ci) {
		CapePhysicsMod mod = CapePhysicsMod.instance;
		if (mod == null || !mod.enabled) {
			return;
		}
		if (!plegumo$looked) {
			plegumo$looked = true;
			plegumo$segs = CapePhysicsMod.segments(cape);
		}
		if (plegumo$segs != null) {
			mod.apply(state.id, plegumo$segs);
		}
	}
}
