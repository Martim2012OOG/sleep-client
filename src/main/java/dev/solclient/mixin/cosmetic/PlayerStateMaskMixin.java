package dev.solclient.mixin.cosmetic;

import dev.solclient.cosmetic.LayerMask;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PlayerEntityRenderState.class)
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
