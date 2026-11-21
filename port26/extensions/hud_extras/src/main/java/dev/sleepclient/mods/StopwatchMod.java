package dev.sleepclient.mods;

import dev.sleepclient.Keys;
import dev.sleepclient.option.Option;

/** A stopwatch on the HUD: one key starts/pauses, another resets. */
public class StopwatchMod extends TextHudMod {

	private final Option.Key startKey = key("Start / Pause Key", 80); // P
	private final Option.Key resetKey = key("Reset Key", -1);
	private final Option.Bool millis = bool("Show Milliseconds", true);
	private long startedAt, stored;
	private boolean running, sDown, rDown;

	public StopwatchMod() {
		super("Stopwatch", "stopwatch", "Start, pause and reset a timer with a key (P).", 0.01F, 0.69F);
	}

	@Override
	public void frame() {
		boolean ok = dev.sleepclient.compat.Mc.screen(mc) == null;
		boolean s = ok && Keys.down(startKey.code());
		if (s && !sDown) {
			if (running) {
				stored += System.currentTimeMillis() - startedAt;
				running = false;
			} else {
				startedAt = System.currentTimeMillis();
				running = true;
			}
		}
		sDown = s;
		boolean r = ok && Keys.down(resetKey.code());
		if (r && !rDown) {
			stored = 0;
			startedAt = System.currentTimeMillis();
		}
		rDown = r;
	}

	private long elapsed() {
		return stored + (running ? System.currentTimeMillis() - startedAt : 0);
	}

	@Override
	protected String text(boolean edit) {
		long t = edit && elapsed() == 0 ? 83_450 : elapsed();
		long m = t / 60000, s = (t / 1000) % 60, ms = t % 1000;
		String base = m >= 60 ? String.format("%d:%02d:%02d", m / 60, m % 60, s) : String.format("%02d:%02d", m, s);
		if (millis.on()) {
			base += String.format(".%02d", ms / 10);
		}
		return (running || edit ? "▶ " : "|| ") + base;
	}
}
