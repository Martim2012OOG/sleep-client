package dev.solclient.emote;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/** An Emotecraft keyframe animation. Only the six body parts of the vanilla player model are used. */
public final class Animation {

	public static final int HEAD = 0, TORSO = 1, RIGHT_ARM = 2, LEFT_ARM = 3, RIGHT_LEG = 4, LEFT_LEG = 5;
	/** The prop in each hand also has a track (position / rotation relative to the hand), like Emotecraft's rightItem / leftItem. */
	public static final int RIGHT_ITEM = 6, LEFT_ITEM = 7;
	public static final String[] PARTS = {"head", "torso", "rightArm", "leftArm", "rightLeg", "leftLeg", "rightItem", "leftItem"};
	/** "bend" = Emotecraft's elbow / knee (and upper-torso) bend, in radians. */
	public static final String[] PROPS = {"x", "y", "z", "pitch", "yaw", "roll", "bend"};
	public static final int BEND = 6;

	public static final float[][] DEFAULTS = {
			{0F, 0F, 0F, 0F, 0F, 0F, 0F},
			{0F, 0F, 0F, 0F, 0F, 0F, 0F},
			{-5F, 2F, 0F, 0F, 0F, 0F, 0F},
			{5F, 2F, 0F, 0F, 0F, 0F, 0F},
			{-1.9F, 12F, 0F, 0F, 0F, 0F, 0F},
			{1.9F, 12F, 0F, 0F, 0F, 0F, 0F},
			{0F, 0F, 0F, 0F, 0F, 0F, 0F},
			{0F, 0F, 0F, 0F, 0F, 0F, 0F}};

	public final int endTick, stopTick, returnTick;
	public final boolean loop;
	private final Track[][] tracks = new Track[PARTS.length][PROPS.length];

	private static final class Track {
		final int[] ticks;
		final float[] values;
		final byte[] easing;

		Track(List<Integer> t, List<Float> v, List<Byte> e) {
			int n = t.size();
			ticks = new int[n];
			values = new float[n];
			easing = new byte[n];
			for (int i = 0; i < n; i++) {
				ticks[i] = t.get(i);
				values[i] = v.get(i);
				easing[i] = e.get(i);
			}
		}

		float sample(float t, float def) {
			int n = ticks.length;
			if (t <= ticks[0]) {
				if (ticks[0] <= 0) {
					return values[0];
				}
				return mix(def, values[0], ease(easing[0], Math.max(0F, t) / ticks[0]));
			}
			if (t >= ticks[n - 1]) {
				return values[n - 1];
			}
			int lo = 0, hi = n - 1;
			while (hi - lo > 1) {
				int mid = (lo + hi) >>> 1;
				if (ticks[mid] <= t) {
					lo = mid;
				} else {
					hi = mid;
				}
			}
			float u = (t - ticks[lo]) / (float) (ticks[hi] - ticks[lo]);
			return mix(values[lo], values[hi], ease(easing[hi], u));
		}
	}

	private static float mix(float a, float b, float u) {
		return a + (b - a) * u;
	}

	private static float ease(int type, float u) {
		return Easing.apply(type, u);
	}

	private Animation(int endTick, int stopTick, int returnTick, boolean loop) {
		this.endTick = endTick;
		this.stopTick = stopTick;
		this.returnTick = returnTick;
		this.loop = loop;
	}

	public static Animation parse(JsonObject root) {
		JsonObject e = root.has("emote") ? root.getAsJsonObject("emote") : root;
		int end = intOr(e, "endTick", 0);
		int stop = intOr(e, "stopTick", end);
		int ret = intOr(e, "returnTick", 0);
		boolean loop = e.has("isLoop") && e.get("isLoop").getAsBoolean();
		boolean degrees = e.has("degrees") && e.get("degrees").getAsBoolean();
		Animation a = new Animation(end, stop, ret, loop);

		List<List<List<Integer>>> ts = new ArrayList<>();
		List<List<List<Float>>> vs = new ArrayList<>();
		List<List<List<Byte>>> es = new ArrayList<>();
		for (int p = 0; p < PARTS.length; p++) {
			List<List<Integer>> a1 = new ArrayList<>();
			List<List<Float>> a2 = new ArrayList<>();
			List<List<Byte>> a3 = new ArrayList<>();
			for (int q = 0; q < PROPS.length; q++) {
				a1.add(new ArrayList<>());
				a2.add(new ArrayList<>());
				a3.add(new ArrayList<>());
			}
			ts.add(a1);
			vs.add(a2);
			es.add(a3);
		}
		JsonArray moves = e.getAsJsonArray("moves");
		for (JsonElement el : moves) {
			JsonObject m = el.getAsJsonObject();
			int tick = m.get("tick").getAsInt();
			byte easing = parseEasing(m.has("easing") ? m.get("easing").getAsString() : "LINEAR");
			for (int p = 0; p < PARTS.length; p++) {
				if (!m.has(PARTS[p]) || !m.get(PARTS[p]).isJsonObject()) {
					continue;
				}
				JsonObject part = m.getAsJsonObject(PARTS[p]);
				for (int q = 0; q < PROPS.length; q++) {
					if (!part.has(PROPS[q])) {
						continue;
					}
					float v = part.get(PROPS[q]).getAsFloat();
					if (degrees && q >= 3) { // pitch, yaw, roll, bend
						v = (float) Math.toRadians(v);
					}
					List<Integer> tl = ts.get(p).get(q);
					int n = tl.size();
					if (n > 0 && tl.get(n - 1) > tick) {
						continue;
					}
					tl.add(tick);
					vs.get(p).get(q).add(v);
					es.get(p).get(q).add(easing);
				}
			}
		}
		for (int p = 0; p < PARTS.length; p++) {
			for (int q = 0; q < PROPS.length; q++) {
				if (!ts.get(p).get(q).isEmpty()) {
					a.tracks[p][q] = new Track(ts.get(p).get(q), vs.get(p).get(q), es.get(p).get(q));
				}
			}
		}
		return a;
	}

	private static int intOr(JsonObject o, String k, int def) {
		return o.has(k) ? o.get(k).getAsInt() : def;
	}

	private static byte parseEasing(String s) {
		return (byte) Easing.parse(s);
	}

	public float animTime(float ticks) {
		if (loop && endTick > returnTick && ticks > endTick) {
			float span = endTick - returnTick;
			return returnTick + ((ticks - returnTick) % span);
		}
		return ticks;
	}

	public int lengthTicks() {
		return Math.max(endTick, stopTick);
	}

	/** True if the emote moves the prop of this hand (RIGHT_ITEM / LEFT_ITEM). */
	public boolean hasItem(int part) {
		for (int q = 0; q < 6; q++) {
			if (tracks[part][q] != null) {
				return true;
			}
		}
		return false;
	}

	/** True if the emote animates this property of this part (otherwise the vanilla value is kept, like PlayerAnimator). */
	public boolean has(int part, int prop) {
		return tracks[part][prop] != null;
	}

	public void sample(float t, float[][] out) {
		for (int p = 0; p < PARTS.length; p++) {
			for (int q = 0; q < PROPS.length; q++) {
				Track tr = tracks[p][q];
				out[p][q] = tr == null ? DEFAULTS[p][q] : tr.sample(t, DEFAULTS[p][q]);
			}
		}
	}
}
