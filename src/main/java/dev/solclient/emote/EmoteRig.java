package dev.solclient.emote;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelTransform;

/**
 * Applies a sampled {@link Animation} pose to the vanilla player model, the same way PlayerAnimator / Emotecraft do.
 *
 * Our emote files have no "version", so (like PlayerAnimator's AnimationJson, version &lt; 3) their "torso" track is
 * really the "body" track: a transform of the WHOLE player (head, torso, arms AND legs), rotated around the hips
 * (0.7 blocks above the feet = model y 12) in the order roll, yaw, pitch, and moved in blocks. The torso model part
 * itself stays at its default pose. Head, arms and legs are set to their own (absolute) values inside that transform.
 *
 * PlayerAnimator applies "body" in world space before Minecraft flips the model (scale -1, -1, 1); in model space
 * that is Rz(roll) * Ry(-yaw) * Rx(-pitch) and a translation of (-x, -y, z) blocks.
 *
 * Bends (elbows / knees / upper body) are approximated because the vanilla model has no joints:
 *  - limb bent by b in the middle -> aim the straight limb at (pitch + b/2) and shorten it to cos(b/2);
 *  - body bend -> head and arms rotate by b around the middle of the torso (model y 6), the torso by b/2 around the hips.
 */
public final class EmoteRig {

	private static final double HIP_Y = 12.0, MID_Y = 6.0;
	/** Blocks -> model pixels (the player model is drawn at 15/16 scale). */
	private static final double PX = 16.0 / 0.9375;

	private EmoteRig() {
	}

	private static double[] rot(double pitch, double yaw, double roll) {
		double ca = Math.cos(pitch), sa = Math.sin(pitch);
		double cb = Math.cos(yaw), sb = Math.sin(yaw);
		double cc = Math.cos(roll), sc = Math.sin(roll);
		return new double[]{
				cc * cb, cc * sb * sa - sc * ca, cc * sb * ca + sc * sa,
				sc * cb, sc * sb * sa + cc * ca, sc * sb * ca - cc * sa,
				-sb, cb * sa, cb * ca};
	}

	private static double[] mul(double[] a, double[] b) {
		double[] r = new double[9];
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) {
				r[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j];
			}
		}
		return r;
	}

	private static double[] mulV(double[] m, double x, double y, double z) {
		return new double[]{
				m[0] * x + m[1] * y + m[2] * z,
				m[3] * x + m[4] * y + m[5] * z,
				m[6] * x + m[7] * y + m[8] * z};
	}

	private static float[] euler(double[] m) {
		double yaw = Math.asin(Math.max(-1.0, Math.min(1.0, -m[6])));
		double pitch, roll;
		if (Math.abs(m[6]) < 0.99999) {
			pitch = Math.atan2(m[7], m[8]);
			roll = Math.atan2(m[3], m[0]);
		} else {
			pitch = Math.atan2(-m[5], m[4]);
			roll = 0;
		}
		return new float[]{(float) pitch, (float) yaw, (float) roll};
	}

	/** A rigid transform: rotate by r around pivot p, then move by t. */
	private record Rigid(double[] r, double[] p, double[] t) {
		double[] point(double x, double y, double z) {
			double[] v = mulV(r, x - p[0], y - p[1], z - p[2]);
			return new double[]{p[0] + v[0] + t[0], p[1] + v[1] + t[1], p[2] + v[2] + t[2]};
		}

		Rigid then(Rigid inner) {
			// this ∘ inner
			double[] r2 = mul(r, inner.r);
			double[] o = point(inner.point(0, 0, 0)[0], inner.point(0, 0, 0)[1], inner.point(0, 0, 0)[2]);
			double[] rv = mulV(r2, 0, 0, 0);
			return new Rigid(r2, new double[]{0, 0, 0}, new double[]{o[0] - rv[0], o[1] - rv[1], o[2] - rv[2]});
		}
	}

	private static final Rigid IDENTITY = new Rigid(rot(0, 0, 0), new double[]{0, 0, 0}, new double[]{0, 0, 0});

	/** Puts a part at (local pivot, local angles) seen through transform g. */
	private static void place(ModelPart part, Rigid g, double x, double y, double z, double pitch, double yaw, double roll) {
		double[] pos = g.point(x, y, z);
		float[] ang = euler(mul(g.r(), rot(pitch, yaw, roll)));
		part.setTransform(ModelTransform.of((float) pos[0], (float) pos[1], (float) pos[2], ang[0], ang[1], ang[2]));
	}

	private static float lerp(float a, float b, float w) {
		return a + (b - a) * w;
	}

	/** Split limbs (see LimbSplit): the lower half is a child called "lower"; sleeves / pants have their own "lower" too. */
	private static boolean split(ModelPart part) {
		return part.hasChild("lower");
	}

	/** Bends a split limb: elbows go forward, knees go back. 'cover' = name of the sleeve / pants layer on it. */
	private static void bendLower(ModelPart part, String cover, boolean arm, float bend) {
		// the files use one sign for both: elbows are mostly negative, knees mostly positive, and in both cases the forearm / shin
		// swings by exactly that angle (arms: negative = forward, legs: positive = backward)
		float pitch = bend;
		part.getChild("lower").pitch = pitch;
		if (cover != null && part.hasChild(cover) && part.getChild(cover).hasChild("lower")) {
			part.getChild(cover).getChild("lower").pitch = pitch;
		}
	}

	private static final String[] COVER = {null, null, "right_sleeve", "left_sleeve", "right_pants", "left_pants"};

	/** Back to straight: the preview model is shared and its joints are not reset by resetTransform on the limb itself. */
	public static void resetBends(ModelPart... limbs) {
		String[] covers = {"right_sleeve", "left_sleeve", "right_pants", "left_pants"};
		for (ModelPart l : limbs) {
			if (l != null && l.hasChild("lower")) {
				l.getChild("lower").resetTransform();
				for (String c : covers) {
					if (l.hasChild(c) && l.getChild(c).hasChild("lower")) {
						l.getChild(c).getChild("lower").resetTransform();
					}
				}
			}
		}
	}

	/** After the game moved the hand to the arm: follow the elbow, so a held item stays in the (bent) hand. */
	public static void afterArm(ModelPart arm, net.minecraft.client.util.math.MatrixStack ms) {
		if (arm == null || !arm.hasChild("lower")) {
			return;
		}
		ModelPart lo = arm.getChild("lower");
		if (lo.pitch == 0F) {
			return;
		}
		lo.applyTransform(ms);
		ms.translate(0F, -lo.originY / 16F, 0F); // the hand offset the game adds next counts from the shoulder
	}

	/** Moves a held prop the way the emote asks (rightItem / leftItem track): o = x, y, z, pitch, yaw, roll. */
	public static void applyItem(net.minecraft.client.util.math.MatrixStack ms, float[] o) {
		ms.translate(-o[0] / 16F, -o[1] / 16F, o[2] / 16F); // the files give pixels, like the limbs
		ms.multiply(new org.joml.Quaternionf().rotateZ(o[5]).rotateY(-o[4]).rotateX(-o[3]));
	}

	private static void shorten(ModelPart part, float bend, float w) {
		if (bend == 0F) {
			return;
		}
		float k = Math.max(0.55F, (float) Math.cos(bend / 2F));
		part.yScale = 1F + (k - 1F) * w;
	}

	/** Limb (arm / leg): own values, blended with the vanilla pose by w, bend approximated. */
	private static void limb(ModelPart part, Animation anim, float[] p, int idx, Rigid g, float w) {
		float[] def = Animation.DEFAULTS[idx];
		float b = p[Animation.BEND] * w;
		float x = lerp(part.originX, p[0], w), y = lerp(part.originY, p[1], w), z = lerp(part.originZ, p[2], w);
		boolean real = split(part);
		// no real elbows / knees (R6): the limb is aimed half way into the bend and made a little shorter, so a sit, a kneel or a
		// waving forearm still reads right instead of staying straight (the bend of the file was thrown away before)
		float pitch = lerp(part.pitch, anim.has(idx, 3) ? p[3] : def[3], w) + (real ? 0F : b * 0.5F);
		float yaw = lerp(part.yaw, anim.has(idx, 4) ? p[4] : def[4], w);
		float roll = lerp(part.roll, anim.has(idx, 5) ? p[5] : def[5], w);
		place(part, g, x, y, z, pitch, yaw, roll);
		if (real) {
			bendLower(part, COVER[idx], idx == Animation.RIGHT_ARM || idx == Animation.LEFT_ARM, b);
		} else {
			shorten(part, p[Animation.BEND], w);
		}
	}

	private static final float[][] SAMPLE = new float[Animation.PARTS.length][Animation.PROPS.length];

	public static void apply(Animation anim, float t, float w, ModelPart head, ModelPart body, ModelPart rightArm,
			ModelPart leftArm, ModelPart rightLeg, ModelPart leftLeg) {
		float[][] s = SAMPLE; // render thread only: no new arrays every frame for every model layer (player, armour...)
		anim.sample(t, s);

		// whole-body transform (the file's "torso" track)
		float[] bt = s[Animation.TORSO];
		double bp = bt[3] * w, by = bt[4] * w, br = bt[5] * w;
		Rigid g = new Rigid(rot(-bp, -by, br), new double[]{0, HIP_Y, 0},
				new double[]{-bt[0] * PX * w, -bt[1] * PX * w, bt[2] * PX * w});

		// upper-body bend: head and arms turn around the middle of the torso
		double bodyBend = bt[Animation.BEND] * w;
		Rigid upper = bodyBend == 0 ? g : g.then(new Rigid(rot(bodyBend, 0, 0), new double[]{0, MID_Y, 0}, new double[]{0, 0, 0}));
		Rigid torso = bodyBend == 0 ? g : g.then(new Rigid(rot(bodyBend * 0.5, 0, 0), new double[]{0, HIP_Y, 0}, new double[]{0, 0, 0}));

		// torso part: default pose inside the body transform
		place(body, torso, 0, 0, 0, 0, 0, 0);

		// head: animated values, or the vanilla look where the emote has no head rotation
		float[] h = s[Animation.HEAD];
		float hx = lerp(head.originX, h[0], w), hy = lerp(head.originY, h[1], w), hz = lerp(head.originZ, h[2], w);
		float hp = anim.has(Animation.HEAD, 3) ? lerp(head.pitch, h[3], w) : head.pitch;
		float hyw = anim.has(Animation.HEAD, 4) ? lerp(head.yaw, h[4], w) : head.yaw;
		float hr = anim.has(Animation.HEAD, 5) ? lerp(head.roll, h[5], w) : head.roll;
		place(head, upper, hx, hy, hz, hp, hyw, hr);

		limb(rightArm, anim, s[Animation.RIGHT_ARM], Animation.RIGHT_ARM, upper, w);
		limb(leftArm, anim, s[Animation.LEFT_ARM], Animation.LEFT_ARM, upper, w);
		limb(rightLeg, anim, s[Animation.RIGHT_LEG], Animation.RIGHT_LEG, g, w);
		limb(leftLeg, anim, s[Animation.LEFT_LEG], Animation.LEFT_LEG, g, w);
	}

	static Rigid identity() {
		return IDENTITY;
	}
}
