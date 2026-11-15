package dev.solclient.mixin.emote;

import dev.solclient.emote.EmoteFlag;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public class EntityRenderStateMixin implements EmoteFlag {

	@Unique
	private boolean sol$emoting;

	@Override
	public boolean sol$isEmoting() {
		return sol$emoting;
	}

	@Override
	public void sol$setEmoting(boolean emoting) {
		this.sol$emoting = emoting;
	}

	@Unique
	private dev.solclient.emote.EmoteManager.Remote sol$remote;

	@Override
	public dev.solclient.emote.EmoteManager.Remote sol$remoteEmote() {
		return sol$remote;
	}

	@Override
	public void sol$setRemoteEmote(dev.solclient.emote.EmoteManager.Remote remote) {
		this.sol$remote = remote;
	}
}
