package dev.solclient.mixin.overlay;

import dev.solclient.fx.ItemPhysicsState;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Keeps "on the ground" / "in water" for each dropped item, so Item Physics can lay it flat. */
@Mixin(ItemEntityRenderState.class)
public class ItemPhysicsStateMixin implements ItemPhysicsState {

	@Unique
	private boolean kubo$ground, kubo$water;

	@Override
	public boolean kubo$onGround() {
		return kubo$ground;
	}

	@Override
	public boolean kubo$inWater() {
		return kubo$water;
	}

	@Override
	public void kubo$setPhysics(boolean onGround, boolean inWater) {
		kubo$ground = onGround;
		kubo$water = inWater;
	}
}
