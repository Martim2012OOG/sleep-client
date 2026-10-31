package dev.solclient.mixin;

import dev.solclient.fx.PatSquish;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntityRenderState.class)
public class KuboPatStateMixin implements PatSquish {

	@Unique
	private float kubo$pat;

	@Override
	public float kubo$pat() {
		return kubo$pat;
	}

	@Override
	public void kubo$setPat(float pat) {
		this.kubo$pat = pat;
	}
}
