package dev.solclient.mixin;

import dev.solclient.fx.ScaleFlag;
import net.minecraft.client.render.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public class KuboRenderStateMixin implements ScaleFlag {

	@Unique
	private float kubo$scale = 1F;

	@Override
	public float kubo$scale() {
		return kubo$scale;
	}

	@Override
	public void kubo$setScale(float scale) {
		this.kubo$scale = scale;
	}
}
