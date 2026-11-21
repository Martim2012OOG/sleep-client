package dev.sleepclient.gui;

/** True while another mod's settings screen is drawn inside the Sleep menu (its own background must not be drawn). */
public final class EmbedFlag {
	public static volatile boolean rendering;

	private EmbedFlag() {
	}
}
