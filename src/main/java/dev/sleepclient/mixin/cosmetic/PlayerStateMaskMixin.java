package dev.sleepclient.mixin.cosmetic;

import dev.sleepclient.cosmetic.LayerMask;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PlayerEntityRenderState.class)
public abstract class PlayerStateMaskMixin implements LayerMask {

	@Unique
	private int sleep$mask;

	@Override
	public int sleep$getMask() {
		return sleep$mask;
	}

	@Override
	public void sleep$setMask(int mask) {
		sleep$mask = mask;
	}
}
