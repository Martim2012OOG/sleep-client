package dev.sleepclient.fightcam;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Fight Film: the replay-mod workflow done for you. It reads the replay buffer, finds the last fight (1v1, 2v2, ...: every
 * hit between players), and writes a cinematic camera for it: orbit, chase behind the attacker, low angle, wide shot and a
 * slow-motion orbit around the kill. The result is an ordinary camera path ({@link Replay#KEYS}) plus slow-motion ranges,
 * so the normal replay viewer plays it and records it to an .mp4.
 */
public final class FightFilm {

	/** One fight found in the buffer (frame numbers are indexes in the replay, 20 per second). */
	public static final class Fight {
		public int start, end, kill = -1, hits;
		public UUID killer, victim;
		public final List<UUID> fighters = new ArrayList<>();
		public final List<Hit> events = new ArrayList<>();
		/** Absolute game tick of the first hit (to know whether this fight was already offered). */
		public long tick;

		public double seconds() {
			return (end - start) / 20.0;
		}
	}

	public record Hit(int frame, UUID victim, UUID attacker) {
	}

	private static final int LEAD = 40, TAIL_KILL = 70, TAIL_NO_KILL = 50, JOIN_GAP = 200, MIN_LEN = 100;

	private FightFilm() {
	}

	private static Replay.Snap find(Replay.Frame f, UUID id) {
		for (Replay.Snap s : f.snaps) {
			if (s.id.equals(id)) {
				return s;
			}
		}
		return null;
	}

	/** Who was swinging at 'victim' around frame i: the closest other player within reach that swung in the last 6 frames. */
	private static UUID attacker(Replay.Frame[] fr, int i, Replay.Snap victim) {
		UUID best = null;
		double bd = 6.5 * 6.5;
		for (Replay.Snap s : fr[i].snaps) {
			if (s.id.equals(victim.id)) {
				continue;
			}
			double dx = s.x - victim.x, dy = s.y - victim.y, dz = s.z - victim.z, d = dx * dx + dy * dy + dz * dz;
			if (d > bd) {
				continue;
			}
			boolean swung = false;
			for (int k = Math.max(0, i - 6); k <= i && !swung; k++) {
				Replay.Snap o = find(fr[k], s.id);
				swung = o != null && o.swing > 0.02F;
			}
			if (swung) {
				bd = d;
				best = s.id;
			}
		}
		if (best == null) {
			// no swing seen (explosion, arrow, lag): the nearest other player within 16 blocks is the opponent
			double nd = 16.0 * 16.0;
			for (Replay.Snap s : fr[i].snaps) {
				if (s.id.equals(victim.id)) {
					continue;
				}
				double dx = s.x - victim.x, dy = s.y - victim.y, dz = s.z - victim.z, d = dx * dx + dy * dy + dz * dz;
				if (d < nd) {
					nd = d;
					best = s.id;
				}
			}
		}
		return best;
	}

	public static Fight analyze(Replay.Frame[] fr, int from) {
		return analyze(fr, from, true);
	}

	/**
	 * The last fight in frames[from..] (null when there is none). strict = the automatic "fight over" offer (needs 2 hits or a kill);
	 * not strict = you asked for a film: one hit is enough, and when nobody was hit at all, the last time players stayed close together
	 * counts (creeper / TNT / bow fights where the game never shows a hit).
	 */
	public static Fight analyze(Replay.Frame[] fr, int from, boolean strict) {
		if (fr == null || fr.length < 30) {
			return null;
		}
		List<Hit> hits = new ArrayList<>();
		for (int i = Math.max(1, from); i < fr.length; i++) {
			for (Replay.Snap s : fr[i].snaps) {
				Replay.Snap p = find(fr[i - 1], s.id);
				if (p != null && s.hurt > 0 && s.hurt > p.hurt) {
					UUID att = attacker(fr, i, s);
					if (att != null) {
						hits.add(new Hit(i, s.id, att));
					}
				}
			}
		}
		if (hits.isEmpty()) {
			return strict ? null : closeEncounter(fr, from);
		}
		// the last group of hits that follow each other (less than 10 s apart)
		int gs = hits.size() - 1;
		while (gs > 0 && hits.get(gs).frame() - hits.get(gs - 1).frame() <= JOIN_GAP) {
			gs--;
		}
		List<Hit> g = hits.subList(gs, hits.size());
		Fight f = new Fight();
		Set<UUID> ids = new HashSet<>();
		for (Hit h : g) {
			if (ids.add(h.victim())) {
				f.fighters.add(h.victim());
			}
			if (ids.add(h.attacker())) {
				f.fighters.add(h.attacker());
			}
		}
		f.hits = g.size();
		f.events.addAll(g);
		int first = g.get(0).frame(), last = g.get(g.size() - 1).frame();
		// a death: a fighter whose death animation starts inside the fight
		for (int i = Math.max(1, first); i < fr.length && f.kill < 0; i++) {
			for (UUID id : f.fighters) {
				Replay.Snap s = find(fr[i], id), p = find(fr[i - 1], id);
				if (s != null && p != null && s.death > 0 && p.death <= 0 && i <= last + 100) {
					f.kill = i;
					f.victim = id;
					for (int k = g.size() - 1; k >= 0; k--) {
						if (g.get(k).victim().equals(id)) {
							f.killer = g.get(k).attacker();
							break;
						}
					}
					break;
				}
			}
		}
		if (strict && f.hits < 2 && f.kill < 0) {
			return null; // one stray hit is not a fight
		}
		f.tick = fr[first].tick;
		f.start = Math.max(0, first - LEAD);
		f.end = Math.min(fr.length - 1, Math.max(last, f.kill) + (f.kill >= 0 ? TAIL_KILL : TAIL_NO_KILL));
		if (f.end - f.start < MIN_LEN) {
			f.start = Math.max(0, f.end - MIN_LEN);
		}
		return f;
	}

	/** A fight you marked yourself (start / stop key): the players close to you in that stretch, start and end as you pressed. */
	public static Fight manual(Replay.Frame[] fr, int s, int e, UUID me, double radius) {
		Fight f = new Fight();
		Replay.Frame mid = fr[Math.max(0, Math.min(fr.length - 1, (s + e) / 2))];
		Replay.Snap mine = me == null ? null : find(mid, me);
		for (Replay.Snap sn : mid.snaps) {
			if (mine == null || Math.sqrt(Math.pow(sn.x - mine.x, 2) + Math.pow(sn.y - mine.y, 2) + Math.pow(sn.z - mine.z, 2)) <= radius) {
				f.fighters.add(sn.id);
			}
		}
		if (f.fighters.isEmpty()) {
			return null;
		}
		for (int i = Math.max(1, s); i <= Math.min(fr.length - 1, e) && f.kill < 0; i++) {
			for (UUID id : f.fighters) {
				Replay.Snap a = find(fr[i], id), b = find(fr[i - 1], id);
				if (a != null && b != null && a.death > 0 && b.death <= 0) {
					f.kill = i;
					f.victim = id;
				}
			}
		}
		f.tick = fr[Math.max(0, s)].tick;
		f.start = Math.max(0, s);
		f.end = Math.min(fr.length - 1, Math.max(e, s + 40));
		return f;
	}

	/** No hits seen: the last stretch (3 s or more) where two or more players were within 12 blocks of each other. */
	private static Fight closeEncounter(Replay.Frame[] fr, int from) {
		int runStart = -1, runEnd = -1, bestStart = -1, bestEnd = -1, gap = 0;
		for (int i = Math.max(0, from); i < fr.length; i++) {
			boolean close = false;
			Replay.Snap[] sn = fr[i].snaps;
			for (int a = 0; a < sn.length && !close; a++) {
				for (int b = a + 1; b < sn.length && !close; b++) {
					double dx = sn[a].x - sn[b].x, dy = sn[a].y - sn[b].y, dz = sn[a].z - sn[b].z;
					close = dx * dx + dy * dy + dz * dz < 12.0 * 12.0;
				}
			}
			if (close) {
				if (runStart < 0) {
					runStart = i;
				}
				runEnd = i;
				gap = 0;
			} else if (runStart >= 0 && ++gap > 40) {
				if (runEnd - runStart >= 60) {
					bestStart = runStart;
					bestEnd = runEnd;
				}
				runStart = -1;
			}
		}
		if (runStart >= 0 && runEnd - runStart >= 60) {
			bestStart = runStart;
			bestEnd = runEnd;
		}
		if (bestStart < 0) {
			return null;
		}
		Fight f = new Fight();
		int mid = (bestStart + bestEnd) / 2;
		Replay.Snap[] sn = fr[mid].snaps;
		for (int a = 0; a < sn.length; a++) {
			for (int b = a + 1; b < sn.length; b++) {
				double dx = sn[a].x - sn[b].x, dy = sn[a].y - sn[b].y, dz = sn[a].z - sn[b].z;
				if (dx * dx + dy * dy + dz * dz < 12.0 * 12.0) {
					if (!f.fighters.contains(sn[a].id)) {
						f.fighters.add(sn[a].id);
					}
					if (!f.fighters.contains(sn[b].id)) {
						f.fighters.add(sn[b].id);
					}
				}
			}
		}
		if (f.fighters.isEmpty()) {
			return null;
		}
		for (int i = Math.max(1, bestStart); i <= Math.min(fr.length - 1, bestEnd + 60) && f.kill < 0; i++) {
			for (UUID id : f.fighters) {
				Replay.Snap s = find(fr[i], id), p = find(fr[i - 1], id);
				if (s != null && p != null && s.death > 0 && p.death <= 0) {
					f.kill = i;
					f.victim = id;
				}
			}
		}
		f.tick = fr[bestStart].tick;
		f.start = Math.max(0, bestStart - 20);
		f.end = Math.min(fr.length - 1, Math.max(bestEnd, f.kill) + 40);
		if (f.end - f.start < MIN_LEN) {
			f.start = Math.max(0, f.end - MIN_LEN);
		}
		return f;
	}

	/** True when the fight is over: nobody hit anybody for 5 seconds (or the kill was at least 2 seconds ago). */
	public static boolean finished(Fight f, int frames, int quietTicks) {
		int lastActivity = f.kill >= 0 ? f.kill + 60 : f.end - TAIL_NO_KILL + quietTicks; // no hit for the time you chose
		return frames - 1 >= lastActivity;
	}

	/** What happened in the film (for the sound effects and the AI voice): hits, the kill and the slow motion, in video seconds. Call after build(). */
	public static FilmEdit.Info makeInfo(Fight f) {
		FilmEdit.Info in = new FilmEdit.Info();
		in.startFrame = f.start;
		for (double[] r : Replay.SLOWS) {
			in.slows.add(r.clone());
		}
		in.seconds = FilmEdit.secOf(in, f.end);
		for (Hit h : f.events) {
			if (h.frame() >= f.start && h.frame() <= f.end) {
				in.events.add(new FilmEdit.Ev(FilmEdit.secOf(in, h.frame()), "hit", Replay.nameOf(h.attacker()), Replay.nameOf(h.victim())));
			}
		}
		if (f.kill >= 0) {
			in.events.add(new FilmEdit.Ev(FilmEdit.secOf(in, f.kill), "kill", Replay.nameOf(f.killer), Replay.nameOf(f.victim)));
		}
		for (double[] r : Replay.SLOWS) {
			in.events.add(new FilmEdit.Ev(FilmEdit.secOf(in, r[0]), "slow", Replay.nameOf(f.victim), ""));
		}
		in.events.sort(java.util.Comparator.comparingDouble(FilmEdit.Ev::sec));
		return in;
	}

	// ------------------------------------------------------------------ the director
	private static final int ORBIT = 0, CHASE = 1, LOW = 2, WIDE = 3, KILL = 4;

	private static double[] focus(Replay.Frame[] fr, Fight f, int frame) {
		double x = 0, y = 0, z = 0;
		int n = 0;
		for (int k = frame - 4; k <= frame + 4; k++) {
			Replay.Frame fk = fr[Math.max(0, Math.min(fr.length - 1, k))];
			for (UUID id : f.fighters) {
				Replay.Snap s = find(fk, id);
				if (s != null && s.death <= 0) {
					x += s.x;
					y += s.y;
					z += s.z;
					n++;
				}
			}
		}
		if (n == 0) { // everybody dead / gone: stay on the last known spot
			Replay.Snap s = find(fr[Math.max(0, Math.min(fr.length - 1, frame))], f.fighters.get(0));
			return s == null ? new double[] {0, 0, 0, 2} : new double[] {s.x, s.y + 1, s.z, 2};
		}
		x /= n;
		y /= n;
		z /= n;
		// how far apart the fighters are (to frame everybody)
		double spread = 2;
		Replay.Frame fc = fr[Math.max(0, Math.min(fr.length - 1, frame))];
		for (UUID a : f.fighters) {
			for (UUID b : f.fighters) {
				Replay.Snap sa = find(fc, a), sb = find(fc, b);
				if (sa != null && sb != null) {
					spread = Math.max(spread, Math.hypot(sa.x - sb.x, sa.z - sb.z));
				}
			}
		}
		return new double[] {x, y + 1.0, z, spread};
	}

	private static Replay.Snap who(Replay.Frame[] fr, int frame, UUID id) {
		return id == null ? null : find(fr[Math.max(0, Math.min(fr.length - 1, frame))], id);
	}

	/** Pulls the camera towards the subject when a wall is in the way. */
	private static Vec3d clear(Vec3d from, Vec3d cam) {
		try {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (mc.world == null || mc.player == null) {
				return cam;
			}
			var r = mc.world.raycast(new RaycastContext(from, cam, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
			if (r.getType() == HitResult.Type.BLOCK) {
				Vec3d d = cam.subtract(from);
				double len = d.length();
				double hit = r.getPos().subtract(from).length();
				double keep = Math.max(1.4, hit - 0.35);
				if (len > 1e-6 && keep < len) {
					return from.add(d.multiply(keep / len));
				}
			}
		} catch (RuntimeException ignored) {
			// no world: keep the camera where it is
		}
		return cam;
	}

	private static void key(List<double[]> out, int frame, Vec3d cam, Vec3d look) {
		double dx = look.x - cam.x, dy = look.y - cam.y, dz = look.z - cam.z;
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		out.add(new double[] {frame, cam.x, cam.y, cam.z, yaw, pitch});
	}

	/** Writes the camera path and the slow motion for the fight into the replay. */
	public static void build(Fight f, Replay.Frame[] fr, boolean slowKill) {
		Replay.KEYS.clear();
		Replay.SLOWS.clear();
		Random rnd = new Random(f.start * 31L + f.fighters.size() * 7L + f.hits);
		int killShot = f.kill >= 0 ? Math.max(f.start + 30, f.kill - 25) : f.end + 1;
		int[] order = {ORBIT, CHASE, LOW, WIDE, CHASE, ORBIT, LOW};
		List<double[]> keys = new ArrayList<>();
		int cur = f.start, shot = rnd.nextInt(2), lastType = -1;
		while (cur < Math.min(killShot, f.end + 1)) {
			int len = 60 + rnd.nextInt(30);
			int e = cur + len;
			if (killShot - e < 40) {
				e = Math.min(killShot, f.end + 1) - 1; // do not leave a tiny shot before the kill
			}
			e = Math.min(e, f.end);
			int type = order[shot++ % order.length];
			if (type == lastType) {
				type = order[shot++ % order.length];
			}
			lastType = type;
			shotKeys(keys, fr, f, type, cur, e, rnd);
			cur = e + 1;
		}
		if (f.kill >= 0 && killShot <= f.end) {
			shotKeys(keys, fr, f, KILL, Math.max(cur, killShot), f.end, rnd);
		}
		Replay.KEYS.addAll(keys);
		if (slowKill && f.kill >= 0) {
			Replay.SLOWS.add(new double[] {f.kill - 8, f.kill + 45, 0.3});
		}
	}

	/** A smooth chase camera behind one player for frames from..to (the "behind me" view of a whole recording session). */
	public static void buildChase(Replay.Frame[] fr, UUID me, int from, int to) {
		Replay.KEYS.clear();
		Replay.SLOWS.clear();
		double sm = Double.NaN;
		for (int f = from; ; f += 3) {
			int fm = Math.min(to, f);
			Replay.Snap s = who(fr, fm, me);
			if (s != null) {
				double yaw = s.headYaw;
				sm = Double.isNaN(sm) ? yaw : sm + net.minecraft.util.math.MathHelper.wrapDegrees(yaw - sm) * 0.2;
				double r = Math.toRadians(sm), dx = -Math.sin(r), dz = Math.cos(r);
				Vec3d head = new Vec3d(s.x, s.y + 1.6, s.z);
				Vec3d cam = clear(head, new Vec3d(head.x - dx * 3.4, head.y + 0.9, head.z - dz * 3.4));
				key(Replay.KEYS, fm, cam, new Vec3d(head.x + dx * 4, head.y - 0.2, head.z + dz * 4));
			}
			if (fm >= to) {
				break;
			}
		}
	}

	private static void shotKeys(List<double[]> out, Replay.Frame[] fr, Fight f, int type, int s, int e, Random rnd) {
		if (e <= s) {
			e = s + 1;
		}
		double dir = rnd.nextBoolean() ? 1 : -1, base = rnd.nextDouble() * Math.PI * 2;
		UUID att = null;
		for (int step = 0;; step += 6) {
			int fm = Math.min(e, s + step);
			double p = (fm - s) / (double) Math.max(1, e - s);
			double[] fc = focus(fr, f, fm);
			Vec3d focus = new Vec3d(fc[0], fc[1], fc[2]);
			double d = Math.max(4.2, Math.min(12, 3.4 + fc[3] * 0.85));
			Vec3d cam, look = focus;
			switch (type) {
				case CHASE -> {
					UUID a = f.killer != null && f.kill >= 0 && fm > f.kill - 60 ? f.killer : att;
					if (a == null) {
						a = f.fighters.get(rnd.nextInt(f.fighters.size()));
					}
					att = a;
					Replay.Snap sa = who(fr, fm, a);
					if (sa == null) {
						cam = focus.add(Math.sin(base) * d, d * 0.3, Math.cos(base) * d);
					} else {
						double dx = focus.x - sa.x, dz = focus.z - sa.z, l = Math.max(0.5, Math.hypot(dx, dz));
						dx /= l;
						dz /= l;
						cam = new Vec3d(sa.x - dx * 2.9 - dz * 0.9, sa.y + 1.9, sa.z - dz * 2.9 + dx * 0.9);
					}
				}
				case LOW -> {
					double a = base + dir * p * 0.5, dd = d * 0.8 * (1 - 0.15 * p);
					double fy = focus.y - 1.0;
					cam = new Vec3d(focus.x + Math.sin(a) * dd, fy + 0.35, focus.z + Math.cos(a) * dd);
					look = focus.add(0, 0.3, 0);
				}
				case WIDE -> {
					double a = base + dir * p * 0.45;
					cam = new Vec3d(focus.x + Math.sin(a) * d * 1.5, focus.y + d * 0.9 + 1.5, focus.z + Math.cos(a) * d * 1.5);
				}
				case KILL -> {
					Replay.Snap v = who(fr, fm, f.victim);
					Vec3d vp = v == null ? focus : new Vec3d(v.x, v.y + 0.9, v.z);
					double a = base + dir * p * 1.9, dd = 3.4 - 0.9 * p;
					cam = new Vec3d(vp.x + Math.sin(a) * dd, vp.y + 0.5 + 0.5 * p, vp.z + Math.cos(a) * dd);
					look = vp;
				}
				default -> { // ORBIT
					double a = base + dir * p * 1.2;
					cam = new Vec3d(focus.x + Math.sin(a) * d, focus.y + d * 0.28 + 0.6, focus.z + Math.cos(a) * d);
				}
			}
			cam = clear(look, cam);
			key(out, fm, cam, look);
			if (fm >= e) {
				break;
			}
		}
	}
}
