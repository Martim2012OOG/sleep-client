package dev.sleepclient.emote;

/** Duck interface mixed into EntityRenderState: "this render pass must show an emote". */
public interface EmoteFlag {

	/** The local player's emote. */
	boolean sol$isEmoting();

	void sol$setEmoting(boolean emoting);

	/** Another player's emote (null = none). */
	EmoteManager.Remote sol$remoteEmote();

	void sol$setRemoteEmote(EmoteManager.Remote remote);
}
