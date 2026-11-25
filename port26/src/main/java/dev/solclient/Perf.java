package dev.solclient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * How much time the client's own drawing costs per frame, by feature (pets, water, waves, sky lyrics, video screens...).
 * The mods' tick / HUD time is in {@link dev.solclient.mods.Mod#costUs}; this adds what is drawn in the 3D world and the
 * extensions' HUD, which were not measured before. Once a minute in a world it writes a short summary to the log, so a real
 * culprit can be found instead of guessing. Measuring is two System.nanoTime() calls per feature per frame.
 */
public final class Perf {

	private static final Map<String, AtomicLong> NANOS = new ConcurrentHashMap<>();
	private static final AtomicLong FRAMES = new AtomicLong();
	private static volatile List<Map.Entry<String, Float>> last = List.of();
	private static long windowStart = System.currentTimeMillis();

	private Perf() {
	}

	public static void add(String name, long nanos) {
		NANOS.computeIfAbsent(name, k -> new AtomicLong()).addAndGet(Math.max(0, nanos));
	}

	/** Once per drawn frame (from the HUD pass). */
	public static void frame() {
		FRAMES.incrementAndGet();
	}

	/** The last window's costs in ms per frame, biggest first. */
	public static List<Map.Entry<String, Float>> top() {
		return last;
	}

	/** "Pets 0.42 ms, Water 0.30 ms" for the settings / Lag Doctor (n biggest above 0.05 ms). */
	public static String topText(int n) {
		StringBuilder sb = new StringBuilder();
		for (var e : last) {
			if (n-- <= 0 || e.getValue() < 0.05F) {
				break;
			}
			if (sb.length() > 0) {
				sb.append(", ");
			}
			sb.append(e.getKey()).append(String.format(" %.2f ms", e.getValue()));
		}
		return sb.toString();
	}

	/** Called every client tick: closes a window every 10 s, and logs a summary once a minute while in a world. */
	public static void tick(net.minecraft.client.Minecraft mc, List<dev.solclient.mods.Mod> mods) {
		long now = System.currentTimeMillis();
		if (now - windowStart < 10_000L) {
			return;
		}
		long frames = Math.max(1, FRAMES.getAndSet(0));
		float secs = (now - windowStart) / 1000F;
		windowStart = now;
		List<Map.Entry<String, Float>> l = new ArrayList<>();
		for (var e : NANOS.entrySet()) {
			long ns = e.getValue().getAndSet(0);
			if (ns > 0) {
				l.add(Map.entry(e.getKey(), ns / 1_000_000F / frames));
			}
		}
		// the mods' own tick + HUD time (Mod.costUs is already an average per call, in microseconds)
		for (var m : mods) {
			if (m.enabled && m.costUs > 50F) {
				l.add(Map.entry(m.name, m.costUs / 1000F));
			}
		}
		l.sort((a, b) -> Float.compare(b.getValue(), a.getValue()));
		last = l;
		if (mc.level != null && mc.player != null && ++windows % 6 == 0) {
			float total = 0;
			for (var e : l) {
				total += e.getValue();
			}
			float fps = frames / secs;
			System.out.println(String.format("[Caos] Perf: %.0f fps (%.1f ms per frame), Caos %.2f ms: %s", fps, 1000F / Math.max(1F, fps), total,
					topText(8).isEmpty() ? "nothing above 0.05 ms" : topText(8)));
		}
	}

	private static int windows;
}
