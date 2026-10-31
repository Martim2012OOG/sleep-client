package dev.solclient.mods;

import dev.solclient.option.Option;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Wavy cape physics (like the "Wavey Capes" mod): the cape model is 16 one-pixel strips chained together, moved by a
 * real rope simulation (gravity, air drag from how you move, a bit of turbulence and wind, never going into the
 * back). It works for everyone's cape: speed comes from each player's position, not from their velocity.
 */
public class CapePhysicsMod extends Mod {

	public static CapePhysicsMod instance;
	public static final int SEGMENTS = 16;

	private final Option.Num wave = num("Wave Strength", 100, 0, 250, 5, "%");
	private final Option.Num gravity = num("Gravity", 70, 0, 100, 5, "%");
	private final Option.Num stiffness = num("Stiffness", 45, 10, 100, 5, "%");
	private final Option.Num wind = num("Wind", 30, 0, 100, 5, "%");
	private final Option.Bool sideSway = bool("Sway When Turning", true);
	private final Option.Num smooth = num("Smoothness", 70, 0, 100, 5, "%");
	private final Option.Num ripple = num("Side Ripple", 60, 0, 150, 5, "%");

	private static final class Sim {
		// rope points in the body's plane, in pixels: d = away from the back, h = down (point 0 = top of the cape)
		final float[] d = new float[SEGMENTS + 1], h = new float[SEGMENTS + 1], pd = new float[SEGMENTS + 1], ph = new float[SEGMENTS + 1];
		final float[] ang = new float[SEGMENTS], yaw = new float[SEGMENTS], yawVel = new float[SEGMENTS];
		float roll, rollVel;
		float lastYaw = Float.NaN;
		double lx = Double.NaN, ly, lz;
		float vf, vu, vs; // smoothed body-space velocity (blocks / s): forward, up, sideways
		long lastNs, seen;
		boolean init;
	}

	private final Map<Integer, Sim> sims = new HashMap<>();

	public CapePhysicsMod() {
		super("Cape Physics", "cape_physics", "Wavy capes: your cape (and everyone's) bends and waves with real physics.", Category.VISUAL);
		instance = this;
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}

	/** Cape model: the vanilla parts (empty) + a cape made of 16 chained strips. */
	public static TexturedModelData modelData() {
		// Start from the real player model (the cape model needs every player part: head, hat, sleeves, jacket, pants...)
		// with all boxes removed, exactly like vanilla does, then hang the strips on the body.
		ModelData md = net.minecraft.client.render.entity.model.PlayerEntityModel.getTexturedModelData(Dilation.NONE, false);
		ModelPartData root = md.getRoot().resetChildrenParts();
		ModelPartData body = root.getChild("body");
		// strip i uses texture row i of the 64x32 cape (texture scale 1 x 0.5 on a 64x64 model = 64x32)
		ModelPartData seg = body.addChild("cape",
				ModelPartBuilder.create().uv(0, 0).cuboid(-5F, 0F, -1F, 10F, 1F, 1F, Dilation.NONE, 1F, 0.5F),
				ModelTransform.of(0F, 0F, 2F, 0F, (float) Math.PI, 0F));
		for (int i = 1; i < SEGMENTS; i++) {
			seg = seg.addChild("seg" + i,
					ModelPartBuilder.create().uv(0, i).cuboid(-5F, 0F, -1F, 10F, 1F, 1F, new Dilation(0F, 0.07F, 0F), 1F, 0.5F),
					ModelTransform.origin(0F, 1F, 0F));
		}
		TexturedModelData t = TexturedModelData.of(md, 64, 64);
		// safety: build the cape model once here; if Minecraft can't use it, throw so the vanilla cape is kept
		new net.minecraft.client.render.entity.model.PlayerCapeModel(t.createModel());
		return t;
	}

	/** The chain of strips under the cape part (null if the model is not the segmented one). */
	public static ModelPart[] segments(ModelPart cape) {
		ModelPart[] out = new ModelPart[SEGMENTS];
		out[0] = cape;
		try {
			ModelPart p = cape;
			for (int i = 1; i < SEGMENTS; i++) {
				p = p.getChild("seg" + i);
				out[i] = p;
			}
			return out;
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** Bends the strips for the player with this entity id. Called from the cape model after the vanilla pose. */
	public void apply(int entityId, ModelPart[] segs) {
		long now = System.nanoTime();
		Sim s = sims.computeIfAbsent(entityId, k -> new Sim());
		float dt = s.lastNs == 0 ? 1F / 60F : Math.min(0.1F, (now - s.lastNs) / 1e9F);
		s.seen = now;
		if (dt > 0.002F || s.lastNs == 0) { // the same model can be drawn several times per frame
			s.lastNs = now;
			step(s, entityId, dt);
		}
		for (int i = 1; i < SEGMENTS; i++) {
			segs[i].pitch += s.ang[i];
			segs[i].yaw += s.yaw[i];
			segs[i].roll += s.roll / SEGMENTS;
		}
		if (sims.size() > 64) {
			sims.values().removeIf(x -> now - x.seen > 5_000_000_000L);
		}
	}

	/**
	 * A real rope: 16 points 1 px apart hanging from the top of the cape. Gravity pulls them down, the air you move
	 * through pushes them back (drag), and they can never go into your back. The speed is measured from the player's
	 * position every frame, so everyone's cape moves properly (other players' velocity is not known on your side).
	 */
	private void step(Sim s, int entityId, float dt) {
		Entity e = mc.world == null ? null : mc.world.getEntityById(entityId);
		double yawRate = 0;
		float bodyYaw = 0;
		if (e != null) {
			bodyYaw = e instanceof LivingEntity le ? le.bodyYaw : e.getYaw();
			if (!Float.isNaN(s.lastYaw)) {
				yawRate = MathHelper.wrapDegrees(bodyYaw - s.lastYaw) / Math.max(dt, 1e-3);
			}
			s.lastYaw = bodyYaw;
			double x = e.getX(), y = e.getY(), z = e.getZ();
			if (!Double.isNaN(s.lx)) {
				double vx = (x - s.lx) / dt, vy = (y - s.ly) / dt, vz = (z - s.lz) / dt;
				if (Math.abs(vx) + Math.abs(vz) > 60 || Math.abs(vy) > 80) { // teleport
					vx = vy = vz = 0;
				}
				double yr = Math.toRadians(bodyYaw);
				float vf = (float) (-vx * Math.sin(yr) + vz * Math.cos(yr));
				float vs = (float) (vx * Math.cos(yr) + vz * Math.sin(yr));
				float k = 1F - (float) Math.exp(-dt * 10F);
				s.vf += (vf - s.vf) * k;
				s.vs += (vs - s.vs) * k;
				s.vu += ((float) vy - s.vu) * k;
			}
			s.lx = x;
			s.ly = y;
			s.lz = z;
		}
		if (!s.init) {
			for (int i = 0; i <= SEGMENTS; i++) {
				s.d[i] = s.pd[i] = 0.2F * i;
				s.h[i] = s.ph[i] = i;
			}
			s.init = true;
		}
		double t = System.currentTimeMillis() / 1000.0;
		float g = 160F * (0.3F + gravity.f() / 100F); // px / s^2
		float drag = 1.6F * wave.f() / 100F;
		float wd = wind.f() / 100F;
		// air in the body's frame: running forward = air coming from the front, going back; jumping = air going down
		float airD = Math.min(140F, Math.max(0F, s.vf) * 16F), airH = MathHelper.clamp(s.vu * 16F, -60F, 40F);
		float speed = Math.abs(s.vf) + Math.abs(s.vs);
		int sub = Math.max(1, Math.min(8, Math.round(dt * 120F)));
		float h2 = dt / sub;
		for (int it = 0; it < sub; it++) {
			double tt = t + it * h2;
			for (int i = 1; i <= SEGMENTS; i++) {
				float f = i / (float) SEGMENTS;
				float vd = (s.d[i] - s.pd[i]) / h2, vh = (s.h[i] - s.ph[i]) / h2;
				// turbulence: the air is never perfectly steady (more when you move, plus a gentle wind)
				float turb = (float) (Math.sin(tt * (3.0 + speed * 1.6) - i * 0.6) * (6F + speed * 9F) * f + Math.sin(tt * 1.3 + i * 0.4) * 10F * wd * f);
				float ad = drag * (airD + turb - vd) + 2F * wd;
				float ah = g + drag * (airH - vh);
				float nd = s.d[i] + (s.d[i] - s.pd[i]) * 0.985F + ad * h2 * h2;
				float nh = s.h[i] + (s.h[i] - s.ph[i]) * 0.985F + ah * h2 * h2;
				s.pd[i] = s.d[i];
				s.ph[i] = s.h[i];
				s.d[i] = nd;
				s.h[i] = nh;
			}
			// keep every strip 1 px long, the top fixed, and nothing inside your back
			for (int pass = 0; pass < 6; pass++) {
				s.d[0] = 0F;
				s.h[0] = 0F;
				for (int i = 1; i <= SEGMENTS; i++) {
					float dx = s.d[i] - s.d[i - 1], dy = s.h[i] - s.h[i - 1];
					float len = (float) Math.sqrt(dx * dx + dy * dy);
					if (len < 1e-4F) {
						dy = 1F;
						len = 1F;
					}
					float diff = (len - 1F) / len;
					if (i == 1) {
						s.d[i] -= dx * diff;
						s.h[i] -= dy * diff;
					} else {
						s.d[i - 1] += dx * diff * 0.5F;
						s.h[i - 1] += dy * diff * 0.5F;
						s.d[i] -= dx * diff * 0.5F;
						s.h[i] -= dy * diff * 0.5F;
					}
				}
				for (int i = 1; i <= SEGMENTS; i++) {
					if (s.d[i] < 0.15F * i / SEGMENTS) {
						s.d[i] = 0.15F * i / SEGMENTS;
					}
				}
			}
		}
		// rope -> angles: lift of every strip (0 = straight down), then the bend between neighbours for the model
		float[] lift = new float[SEGMENTS + 1];
		for (int i = 1; i <= SEGMENTS; i++) {
			lift[i] = (float) Math.atan2(s.d[i] - s.d[i - 1], s.h[i] - s.h[i - 1]);
		}
		// how far the vanilla swing already lifts the top strip (it grows with speed), so only the extra bend is added
		float top = (float) Math.toRadians(6.0 + Math.min(110.0, Math.max(0F, s.vf) * 20.0));
		float sm = smooth.f() / 100F * 0.5F;
		for (int pass = 0; pass < 2; pass++) {
			for (int i = 2; i < SEGMENTS; i++) {
				lift[i] += ((lift[i - 1] + lift[i + 1]) * 0.5F - lift[i]) * sm;
			}
		}
		float stiff = 0.25F + stiffness.f() / 100F * 0.75F;
		float maxStep = 0.06F + (1F - stiffness.f() / 100F) * 0.2F;
		for (int i = 1; i < SEGMENTS; i++) {
			float prev = i == 1 ? top : lift[i];
			float bend = (lift[i + 1] - prev) * (i == 1 ? 0.5F : 1F);
			float target = MathHelper.clamp(bend, -maxStep, maxStep);
			s.ang[i] += (target - s.ang[i]) * Math.min(1F, stiff + 0.5F);
			// side ripple: a soft snake left / right that grows with speed
			float yt = (float) (Math.sin(t * (1.6 + speed * 0.6) - i * 0.5 + 1.3) * ripple.f() / 100F * (0.01 + speed * 0.012 + wd * 0.012) * i / SEGMENTS);
			float ya = 30F * (yt - s.yaw[i]) - 8F * s.yawVel[i];
			s.yawVel[i] += ya * dt;
			s.yaw[i] += s.yawVel[i] * dt;
			s.yaw[i] = MathHelper.clamp(s.yaw[i], -0.25F, 0.25F);
		}
		float rollTarget = sideSway.on() ? (float) MathHelper.clamp(-yawRate * 0.0025 - s.vs * 0.05, -0.35, 0.35) : 0F;
		float ra = 30F * (rollTarget - s.roll) - 8F * s.rollVel;
		s.rollVel += ra * dt;
		s.roll += s.rollVel * dt;
	}
}
