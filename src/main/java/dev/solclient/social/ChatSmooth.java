package dev.solclient.social;

/** When the newest chat message arrived (used by the smooth chat slide). */
public final class ChatSmooth {

	public static volatile long lastMessageAt;

	private ChatSmooth() {
	}
}
