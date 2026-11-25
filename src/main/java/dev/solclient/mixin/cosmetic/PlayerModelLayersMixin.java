package dev.solclient.mixin.cosmetic;

import dev.solclient.cosmetic.LayerMask;
import dev.solclient.cosmetic.SkinLayers3D;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 3D Skin Layers: the flat copy of a layer is hidden exactly where the 3D one is drawn (never both). */
@Mixin(PlayerEntityModel.class)
public abstract class PlayerModelLayersMixin {

	@Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V", at = @At("TAIL"), require = 0)
	private void caos$hideFlat(PlayerEntityRenderState state, CallbackInfo ci) {
		int mask = ((LayerMask) state).caos$getMask();
		if (mask == 0) {
			return;
		}
		PlayerEntityModel m = (PlayerEntityModel) (Object) this;
		if ((mask & SkinLayers3D.HAT) != 0) {
			m.hat.visible = false;
		}
		if ((mask & SkinLayers3D.JACKET) != 0) {
			m.jacket.visible = false;
		}
		if ((mask & SkinLayers3D.LSLEEVE) != 0) {
			m.leftSleeve.visible = false;
		}
		if ((mask & SkinLayers3D.RSLEEVE) != 0) {
			m.rightSleeve.visible = false;
		}
		if ((mask & SkinLayers3D.LPANTS) != 0) {
			m.leftPants.visible = false;
		}
		if ((mask & SkinLayers3D.RPANTS) != 0) {
			m.rightPants.visible = false;
		}
	}
}
