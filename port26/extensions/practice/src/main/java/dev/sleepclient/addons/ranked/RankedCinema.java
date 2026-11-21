package dev.sleepclient.addons.ranked;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The duel intro's camera, shot for shot like the intro page (duelo-intro-best.html), but in the real arena around the
 * real players: crane that reveals the arena, a whip-in orbit on each player, the VS close-ups, then a crane that ends
 * in your own eyes looking at your opponent. Also the victory camera around the winner.
 *
 * Coordinates: the page's scene has team A at z = +7.5 and team B at z = -7.5 (player faces: A = PI, B = 0), floor at
 * y = 0. Here that frame is put on the arena: its z axis runs from team B's captain to team A's, the centre is between
 * them. Shots near a player use blocks as they are; shots of the whole arena are scaled to its size.
 */
final class RankedCinema {

	// the page's timeline (seconds)
	static final float A1 = 2, A2 = 4, B1 = 5, B2 = 7, VS = 8, WIDE = 10, POV = 12, C3 = 12, C2 = 13, C1 = 14, GO = 15, END = 16.6F;

	/** One player of the intro: team, index in the team, feet position, face angle in the page's frame. */
	record Ref(String team, int idx, String name, Vec3 pos, double face) {
	}

	/** Result of a shot: camera position, look-at point, vertical FOV. */
	record Shot(Vec3 p, Vec3 l, double fov) {
	}

	private final List<Ref> refs = new ArrayList<>();
	private final String local, other;
	private final boolean two;
	private Vec3 centre, zA, xA;
	/** blocks per page unit along the duel line (z) and across / up (x, y: capped so wide shots stay near the arena). */
	private double sz, sxy;

	private RankedCinema(String local, boolean two) {
		this.local = local;
		this.other = "A".equals(local) ? "B" : "A";
		this.two = two;
	}

	/** Builds the frame from where the players are (or their arena spawns when not loaded yet). Null when impossible. */
	static RankedCinema of(Ranked.Match m, String localTeam) {
		return of(m, localTeam, null);
	}

	/** Same, with fixed positions by player name (the /ranked intro test). */
	static RankedCinema of(Ranked.Match m, String localTeam, java.util.Map<String, Vec3> fixed) {
		RankedCinema c = new RankedCinema(localTeam, "2v2".equals(m.mode()));
		Minecraft mc = Minecraft.getInstance();
		RankedWorld.Arena ar = RankedWorld.arena(m.arena());
		for (String t : new String[] {"A", "B"}) {
			List<Ranked.Player> ps = "A".equals(t) ? m.teamA() : m.teamB();
			for (int i = 0; i < ps.size(); i++) {
				String n = ps.get(i).mcUsername();
				Vec3 pos = fixed == null ? null : fixed.get(n.toLowerCase(java.util.Locale.ROOT));
				if (pos == null && mc.level != null) {
					for (Player e : mc.level.players()) {
						if (e.getName().getString().equalsIgnoreCase(n)) {
							pos = new Vec3(e.getX(), e.getY(), e.getZ());
						}
					}
				}
				if (pos == null && ar != null) {
					RankedWorld.Spot sp = "A".equals(t) ? (i == 0 ? ar.a() : ar.a2()) : (i == 0 ? ar.b() : ar.b2());
					pos = new Vec3(sp.x(), sp.y(), sp.z());
				}
				if (pos != null) {
					c.refs.add(new Ref(t, i, n, pos, "A".equals(t) ? Math.PI : 0));
				}
			}
		}
		Ref a = c.captain("A"), b = c.captain("B");
		if (a == null || b == null) {
			return null;
		}
		Vec3 d = new Vec3(a.pos.x - b.pos.x, 0, a.pos.z - b.pos.z);
		double len = d.length();
		if (len < 1) {
			return null;
		}
		c.zA = d.scale(1 / len);
		c.xA = new Vec3(c.zA.z, 0, -c.zA.x); // up x z, like the page's right-handed frame
		c.centre = new Vec3((a.pos.x + b.pos.x) / 2, (a.pos.y + b.pos.y) / 2, (a.pos.z + b.pos.z) / 2);
		c.sz = (len / 2) / 7.5;
		c.sxy = Math.min(c.sz, 1.3);
		return c;
	}

	Ref captain(String team) {
		for (Ref r : refs) {
			if (r.team.equals(team) && r.idx == 0) {
				return r;
			}
		}
		return null;
	}

	private Ref second(String team) {
		for (Ref r : refs) {
			if (r.team.equals(team) && r.idx == 1) {
				return r;
			}
		}
		return captain(team);
	}

	/** A point given in the page's arena units. */
	private Vec3 H(double x, double y, double z) {
		return centre.add(xA.scale(x * sxy)).add(0, y * sxy, 0).add(zA.scale(z * sz));
	}

	// ------------------------------------------------------------------ easing (same as the page)
	static double seg(double t, double a, double b) {
		return Math.max(0, Math.min(1, (t - a) / (b - a)));
	}

	static double lerp(double a, double b, double t) {
		return a + (b - a) * t;
	}

	static double out2(double x) {
		return 1 - (1 - x) * (1 - x);
	}

	static double out3(double x) {
		return 1 - Math.pow(1 - x, 3);
	}

	static double out5(double x) {
		return 1 - Math.pow(1 - x, 5);
	}

	static double in2(double x) {
		return x * x;
	}

	static double in3(double x) {
		return x * x * x;
	}

	static double inOut(double x) {
		return x < .5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
	}

	static double smooth(double x) {
		return x * x * (3 - 2 * x);
	}

	static double outBack(double x) {
		double c1 = 1.9, c3 = c1 + 1;
		return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2);
	}

	/** Fast whip, then a slow glide. */
	static double whip(double x) {
		return (x >= 1 ? 1 : 1 - Math.pow(2, -11 * x)) * .78 + .22 * x;
	}

	private static Vec3 lerpV(Vec3 a, Vec3 b, double t) {
		return new Vec3(lerp(a.x, b.x, t), lerp(a.y, b.y, t), lerp(a.z, b.z, t));
	}

	// ------------------------------------------------------------------ shots
	private Shot orbit(Ref P, double yawDeg, double r, double h, double lh, double shift, double fov) {
		double a = P.face + Math.toRadians(yawDeg);
		Vec3 dir = xA.scale(Math.sin(a)).add(zA.scale(Math.cos(a)));
		Vec3 p = new Vec3(P.pos.x + dir.x * r, P.pos.y + h, P.pos.z + dir.z * r);
		Vec3 l = new Vec3(P.pos.x, P.pos.y + lh, P.pos.z);
		Vec3 f = l.subtract(p);
		double d = f.length();
		f = f.normalize();
		Vec3 rt = new Vec3(-f.z, 0, f.x).normalize();
		return new Shot(p, l.add(rt.scale(shift * d)), fov);
	}

	private interface Ease {
		double f(double x);
	}

	private Shot shotOrbit(Ref P, double a0, double a1, double r0, double r1, double h0, double h1, double lh0, double lh1, double f0, double f1,
			double sh, Ease ease, double u) {
		double e = ease.f(u);
		return orbit(P, lerp(a0, a1, e), lerp(r0, r1, e), lerp(h0, h1, e), lerp(lh0, lh1, e), sh, lerp(f0, f1, e));
	}

	/** The camera at time t of the intro (null from the POV moment on: you're in your own eyes). */
	Shot at(double t) {
		Ref cA = captain(local), cB = captain(other);
		if (t < A1) { // opening: a crane that reveals the arena
			double e = out3(seg(t, 0, A1));
			return new Shot(lerpV(H(-46, 17, 9), H(-27, 12.5, 5), e), lerpV(H(0, 2, 0), H(0, 0, .4), e), lerp(52, 44, e));
		}
		if (t < A2) {
			return shotOrbit(cA, 78, 24, 3.6, 3.05, .5, .85, 1.3, 1.25, 64, 44, .15, RankedCinema::whip, seg(t, A1, A2));
		}
		if (t < B1) {
			double u = seg(t, A2, B1);
			if (two) {
				return shotOrbit(second(local), -55, -24, 3.3, 2.8, .6, .9, 1.25, 1.2, 60, 44, .15, RankedCinema::whip, u);
			}
			return shotOrbit(cA, 4, 14, 2.8, 2.45, 1.2, 1.25, 1.5, 1.5, 42, 38, .12, RankedCinema::whip, u);
		}
		if (t < B2) {
			return shotOrbit(cB, -78, -24, 3.6, 3.05, .5, .85, 1.3, 1.25, 64, 44, -.15, RankedCinema::whip, seg(t, B1, B2));
		}
		if (t < VS) {
			double u = seg(t, B2, VS);
			if (two) {
				return shotOrbit(second(other), 55, 24, 3.3, 2.8, .6, .9, 1.25, 1.2, 60, 44, -.15, RankedCinema::whip, u);
			}
			return shotOrbit(cB, -4, -14, 2.8, 2.45, 1.2, 1.25, 1.5, 1.5, 42, 38, -.12, RankedCinema::whip, u);
		}
		if (t < WIDE) {
			// the page splits the screen in two close-ups; here: the opponent's close-up, then yours
			double u = seg(t, VS, WIDE), r = two ? 3.4 : 2.25;
			if (t < VS + 1) {
				return shotOrbit(cB, 28, 22, r, r - .35, 1.55, 1.58, 1.5, 1.52, 34, 31, .2, RankedCinema::out2, u);
			}
			return shotOrbit(cA, -28, -22, r, r - .35, 1.55, 1.58, 1.5, 1.52, 34, 31, -.2, RankedCinema::out2, u);
		}
		if (t < POV) { // crane over the arena down into your eyes
			double sg = "A".equals(local) ? 1 : -1;
			double e = inOut(seg(t, WIDE, POV)), q = 1 - e;
			Vec3 eye = cA.pos.add(0, 1.62, 0), opH = cB.pos.add(0, 1.5, 0);
			double meX = cA.pos.subtract(centre).dot(xA) / sxy;
			Vec3 p0 = H(16 * sg, 9, -1 * sg), p1 = H(meX + 4 * sg, 5.5, 16 * sg);
			Vec3 p = p0.scale(q * q).add(p1.scale(2 * q * e)).add(eye.scale(e * e));
			Vec3 l = lerpV(H(0, .6, 0), opH, smooth(seg(seg(t, WIDE, POV), .15, 1)));
			return new Shot(p, l, lerp(46, 72, in2(seg(t, WIDE, POV))));
		}
		return null;
	}

	/** Where your eyes look at the POV moment (your opponent's head). */
	Vec3 opponentHead() {
		Ref o = captain(other);
		return o == null ? null : o.pos.add(0, 1.5, 0);
	}

	/** Victory camera: a slow crane in front of the winner (pos / bodyYaw of the winner, vt = seconds since the end). */
	static Shot victory(Vec3 w, float bodyYaw, double vt) {
		double e = out3(seg(vt, 0, 1.6));
		double r = lerp(8.5, 4.6, e) - seg(vt - 1.6, 0, 12) * .55;
		double sway = Math.sin(vt * .3) * .16 * smooth(seg(vt, 1.2, 3));
		double yaw = Math.toRadians(bodyYaw) + sway;
		Vec3 front = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
		Vec3 p = new Vec3(w.x + front.x * r, w.y + lerp(5, 2.2, e), w.z + front.z * r);
		return new Shot(p, new Vec3(w.x, w.y + lerp(.8, 1.45, e), w.z), lerp(46, 35, e));
	}

	/** The page's cut effects: {white flash, black fade, camera shake} at time t. */
	static double[] fx(double t) {
		double[][] cuts = {{A1, 1, .55}, {A2, .45, .12}, {B1, .9, .4}, {B2, .45, .12}, {VS, .8, .35}, {WIDE, .7, .25}, {GO, 1.2, .9}};
		double[][] shakes = {{A1 + .02, .10}, {B1 + .02, .09}, {VS + .25, .16}, {WIDE, .05}, {C3, .04}, {C2, .05}, {C1, .06}, {GO, .34}};
		double flash = 0, shake = 0;
		for (double[] c : cuts) {
			double d = t - c[0];
			if (d >= 0) {
				flash += c[2] * Math.exp(-d * 10);
			}
		}
		for (double[] s : shakes) {
			double d = t - s[0];
			if (d >= 0) {
				shake += s[1] * Math.exp(-d * 6);
			}
		}
		return new double[] {Math.min(flash, .95), 1 - out2(seg(t, 0, .7)), shake};
	}

	/** {x, y, z, yaw, pitch, fov} for the game's camera, with the shake of the cuts. */
	static double[] pose(Shot s, double shake, double t) {
		Vec3 d = s.l.subtract(s.p);
		double yaw = Math.toDegrees(Math.atan2(-d.x, d.z));
		double pitch = -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
		yaw += Math.sin(t * 91) * shake * 7;
		pitch += Math.cos(t * 77) * shake * 5;
		return new double[] {s.p.x, s.p.y, s.p.z, yaw, pitch, s.fov};
	}
}
