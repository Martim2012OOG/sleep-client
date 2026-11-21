package dev.sleepclient.gui;

import dev.sleepclient.Gfx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Overlapping heads (who is in a voice group, who is in a world...), after the user's PresenceAvatars component:
 * each head is a small framed tile, the next one slides in under the previous one, people keep the place they
 * arrived in, extra people become a "+N" chip, and heads spring in / out when someone joins or leaves.
 * A green frame = talking right now.
 */
public final class AvatarStack {

	public record Person(String id, String name, boolean talking) {
	}

	private static final class Tile {
		float x, vx, scale = 0.86F, alpha;
		boolean leaving;
		long goneAt;
		String name;
		int order;
	}

	private static final class Stack {
		final LinkedHashMap<String, Tile> tiles = new LinkedHashMap<>();
		final Map<String, Integer> seen = new HashMap<>();
		int next;
		float chipX = -1, chipA;
		long last;
	}

	private static final Map<String, Stack> STACKS = new HashMap<>();

	private AvatarStack() {
	}

	/**
	 * @param key     one per place on screen
	 * @param size    tile size (28 in the original)
	 * @param overlap how much each tile hides of the previous (9 for 28)
	 * @return the width used (for laying out what comes after)
	 */
	public static int draw(GuiGraphicsExtractor c, String key, int x, int y, int size, int overlap, List<Person> people, int max) {
		Stack st = STACKS.computeIfAbsent(key, k -> new Stack());
		long now = System.nanoTime();
		float dt = st.last == 0 ? 0F : Math.min(0.05F, (now - st.last) / 1e9F);
		st.last = now;
		// first come, first placed (stays in its slot)
		for (Person p : people) {
			st.seen.computeIfAbsent(p.id(), k -> st.next++);
		}
		List<Person> ordered = new ArrayList<>(people);
		ordered.sort((a, b) -> Integer.compare(st.seen.get(a.id()), st.seen.get(b.id())));
		int slots = Math.max(1, max);
		List<Person> visible = ordered.subList(0, Math.min(slots, ordered.size()));
		int overflow = ordered.size() - visible.size();
		int step = size - overlap;

		Map<String, Person> want = new LinkedHashMap<>();
		for (Person p : visible) {
			want.put(p.id(), p);
		}
		for (var e : st.tiles.entrySet()) {
			if (!want.containsKey(e.getKey()) && !e.getValue().leaving) {
				e.getValue().leaving = true;
				e.getValue().goneAt = System.currentTimeMillis();
			}
		}
		int i = 0;
		for (Person p : visible) {
			Tile t = st.tiles.get(p.id());
			if (t == null || t.leaving) {
				t = new Tile();
				t.x = i * step;
				st.tiles.put(p.id(), t);
			}
			t.name = p.name();
			t.order = i;
			spring(t, i * step, dt);
			t.scale += (1F - t.scale) * Math.min(1F, dt * 18F);
			t.alpha += (1F - t.alpha) * Math.min(1F, dt * 14F);
			i++;
		}
		st.tiles.values().removeIf(t -> t.leaving && System.currentTimeMillis() - t.goneAt > 240);

		// draw from the last to the first: the first head is on top
		List<Map.Entry<String, Tile>> list = new ArrayList<>(st.tiles.entrySet());
		list.sort((a, b) -> Integer.compare(b.getValue().order, a.getValue().order));
		int pad = Math.max(1, Math.round(size * 3F / 28F));
		for (Map.Entry<String, Tile> e : list) {
			Tile t = e.getValue();
			float a = t.alpha, sc = t.scale;
			if (t.leaving) {
				float k = Math.min(1F, (System.currentTimeMillis() - t.goneAt) / 240F);
				a = 1F - k;
				sc = 1F - 0.14F * k;
			}
			int alpha = Math.round(255 * Math.max(0F, Math.min(1F, a)));
			if (alpha < 6) {
				continue;
			}
			float s = size * sc, ox = x + t.x + (size - s) / 2F, oy = y + (size - s) / 2F;
			Person p = want.get(e.getKey());
			boolean talking = p != null && p.talking();
			int frame = talking ? 0x4ADE80 : 0x44403C;
			Gfx.rrectFree(c, ox, oy, s, s, Gfx.corner(s * 10F / 28F) + 1F, Gfx.withAlpha(frame, alpha));
			float ws = s - pad * 2;
			Gfx.rrectFree(c, ox + pad, oy + pad, ws, ws, Gfx.corner(s * 7F / 28F), Gfx.withAlpha(0x292524, alpha));
			int fs = Math.round(ws - 2);
			Faces.draw(c, t.name, Math.round(ox + pad + 1), Math.round(oy + pad + 1), fs, alpha);
		}
		// "+N" chip after the heads
		int chipW = size + 8;
		float targetChip = visible.size() * step;
		if (overflow > 0) {
			st.chipX = st.chipX < 0 ? targetChip : st.chipX + (targetChip - st.chipX) * Math.min(1F, dt * 16F);
			st.chipA += (1F - st.chipA) * Math.min(1F, dt * 14F);
		} else {
			st.chipA += (0F - st.chipA) * Math.min(1F, dt * 14F);
		}
		if (st.chipA > 0.03F && st.chipX >= 0) {
			int ca = Math.round(255 * st.chipA);
			int cx = Math.round(x + st.chipX);
			Gfx.rrectBorder(c, cx, y, chipW, size, 3, Gfx.withAlpha(0x1D1D1A, ca), Gfx.withAlpha(0x3A3A36, ca));
			String n = "+" + Math.min(Math.max(overflow, 1), 99);
			Gfx.textRaw(c, n, cx + chipW / 2 - Gfx.twRaw(n) / 2, y + size / 2 - 4, Gfx.withAlpha(0xA8A29E, ca));
		}
		if (visible.isEmpty()) {
			return 0;
		}
		return overflow > 0 ? visible.size() * step + chipW : (visible.size() - 1) * step + size;
	}

	/** stiffness 520, damping 34, mass 0.45 (the component's spring), on the x position. */
	private static void spring(Tile t, float target, float dt) {
		if (dt <= 0F) {
			return;
		}
		float k = 520F, d = 34F, m = 0.45F;
		int steps = Math.max(1, (int) Math.ceil(dt / 0.004F));
		float h = dt / steps;
		for (int i = 0; i < steps; i++) {
			float f = -k * (t.x - target) - d * t.vx;
			t.vx += f / m * h;
			t.x += t.vx * h;
		}
		if (Math.abs(t.x - target) < 0.05F && Math.abs(t.vx) < 0.5F) {
			t.x = target;
			t.vx = 0F;
		}
	}
}
