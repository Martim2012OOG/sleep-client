package dev.solclient.mixin.cosmetic;

import dev.solclient.cosmetic.LayerMask;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
public abstract class PlayerStateMaskMixin implements LayerMask {

	@Unique
	private int caos$mask;

	@Override
	public int caos$getMask() {
		return caos$mask;
	}

	@Override
	public void caos$setMask(int mask) {
		caos$mask = mask;
	}
}
