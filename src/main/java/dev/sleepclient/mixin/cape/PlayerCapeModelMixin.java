package dev.sleepclient.mixin.cape;

import dev.sleepclient.mods.CapePhysicsMod;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.PlayerCapeModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
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

	@Inject(method = "getTexturedModelData", at = @At("HEAD"), cancellable = true, require = 0)
	private static void plegumo$segmentedCape(CallbackInfoReturnable<TexturedModelData> cir) {
		try {
			cir.setReturnValue(CapePhysicsMod.modelData());
		} catch (Throwable t) {
			System.err.println("[Sleep] cape physics model failed, using the vanilla cape: " + t);
		}
	}

	@Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V", at = @At("TAIL"), require = 0)
	private void plegumo$physics(PlayerEntityRenderState state, CallbackInfo ci) {
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
