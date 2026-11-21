package dev.sleepclient.mixin.emote;

import dev.sleepclient.emote.EmoteFlag;
import net.minecraft.client.render.entity.state.EntityRenderState;
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
	private dev.sleepclient.emote.EmoteManager.Remote sol$remote;

	@Override
	public dev.sleepclient.emote.EmoteManager.Remote sol$remoteEmote() {
		return sol$remote;
	}

	@Override
	public void sol$setRemoteEmote(dev.sleepclient.emote.EmoteManager.Remote remote) {
		this.sol$remote = remote;
	}
}
