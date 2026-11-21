package dev.sleepclient;

import java.util.ArrayList;
import java.util.List;

public class CpsMonitor {

	public static final CpsMonitor LMB = new CpsMonitor(0);
	public static final CpsMonitor RMB = new CpsMonitor(1);

	private final int button;
	private boolean prev;
	private final List<Long> presses = new ArrayList<>();

	public CpsMonitor(int button) {
		this.button = button;
	}

	/** Called once per frame. */
	public void poll() {
		long now = System.currentTimeMillis();
		boolean down = dev.sleepclient.compat.Mc.screen(Gfx.mc()) == null && Keys.mouseDown(button);
		if (down && !prev) {
			if (button == 0) {
				dev.sleepclient.ext.KuboApi.leftClick(); // e.g. the swing counter of the Practice extension
			}
			presses.add(now);
		}
		prev = down;
		presses.removeIf(t -> now - t > 1000);
	}

	public int getCps() {
		long now = System.currentTimeMillis();
		presses.removeIf(t -> now - t > 1000);
		return presses.size();
	}
}
