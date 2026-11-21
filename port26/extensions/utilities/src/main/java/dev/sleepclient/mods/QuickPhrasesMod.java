package dev.sleepclient.mods;

import dev.sleepclient.option.Option;

/** Send a saved message or command with one key. */
public class QuickPhrasesMod extends Mod {

	private static final String[] PHRASES = {"gg", "gl hf", "/spawn", "/home", "brb"};
	private final Option.Key[] keys = new Option.Key[PHRASES.length];
	private final Option.Choice[] which = new Option.Choice[PHRASES.length];
	private final boolean[] down = new boolean[PHRASES.length];

	public QuickPhrasesMod() {
		super("Quick Phrases", "quick_phrases", "Send gg, /spawn, /home and more with one key.", Category.UTILITY);
		for (int i = 0; i < PHRASES.length; i++) {
			keys[i] = key("Phrase " + (i + 1) + " Key", -1);
			which[i] = choice("Phrase " + (i + 1), i, PHRASES);
		}
	}

	@Override
	public void tick() {
		if (mc.player == null) return;
		for (int i = 0; i < keys.length; i++) {
			boolean d = dev.sleepclient.compat.Mc.screen(mc) == null && keys[i].code() > 0 && dev.sleepclient.Keys.down(keys[i].code());
			if (d && !down[i]) {
				String msg = PHRASES[which[i].idx()];
				if (msg.startsWith("/")) mc.player.connection.sendCommand(msg.substring(1));
				else mc.player.connection.sendChat(msg);
			}
			down[i] = d;
		}
	}
}
