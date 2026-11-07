package dev.solclient.fx.water;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.attribute.EnvironmentAttributes;
import org.joml.Vector3f;

/**
 * Sleep's own liquid renderer (no Iris, no shader pack): glossy water with real reflections of the sky, moving waves and
 * sun glints, foam on shores and currents, molten churning lava, and thousands of spray droplets / foam patches / embers.
 * Everything is drawn right after the entities and before Minecraft's own water, so things under the water still show
 * through and the vanilla surface is hidden under ours. Works the same with or without Sodium.
 */
public final class KuboWater {

	private static final RenderPipeline WATER = pipeline("kubo_water", "kubo_water", true);
	private static final RenderPipeline LAVA = pipeline("kubo_lava", "kubo_lava", true);
	private static final RenderPipeline SPRAY = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.TRANSFORMS_PROJECTION_FOG_SNIPPET)
			.withLocation(Identifier.of("sol_client", "pipeline/kubo_spray"))
			.withVertexShader(Identifier.of("sol_client", "core/kubo_spray"))
			.withFragmentShader(Identifier.of("sol_client", "core/kubo_spray"))
			.withSampler("Sampler2")
			.withBlend(BlendFunction.TRANSLUCENT)
			.withCull(false)
			.withDepthWrite(false)
			.withVertexFormat(VertexFormats.POSITION_COLOR_TEXTURE_LIGHT, VertexFormat.DrawMode.QUADS)
			.build());

	private static RenderPipeline pipeline(String name, String frag, boolean depthWrite) {
		return RenderPipelines.register(RenderPipeline.builder(RenderPipelines.TRANSFORMS_PROJECTION_FOG_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
				.withLocation(Identifier.of("sol_client", "pipeline/" + name))
				.withVertexShader(Identifier.of("sol_client", "core/kubo_water"))
				.withFragmentShader(Identifier.of("sol_client", "core/" + frag))
				.withSampler("Sampler2")
				.withBlend(BlendFunction.TRANSLUCENT)
				.withCull(false)
				.withDepthWrite(depthWrite)
				.withVertexFormat(VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS)
				.build());
	}

	private static final RenderLayer WATER_LAYER = RenderLayer.of("kubo_water", RenderSetup.builder(WATER).useLightmap().translucent().expectedBufferSize(1 << 18).build());
	private static final RenderLayer LAVA_LAYER = RenderLayer.of("kubo_lava", RenderSetup.builder(LAVA).useLightmap().translucent().expectedBufferSize(1 << 16).build());
	private static final RenderLayer SPRAY_LAYER = RenderLayer.of("kubo_spray", RenderSetup.builder(SPRAY).useLightmap().translucent().expectedBufferSize(1 << 18).build());

	/** What the Realistic Liquids mod wants this frame. */
	public static final class Settings {
		public boolean on, surface = true, spray = true, lavaFx = true;
		public int radius = 40;
		/** 0..1: how many particles (lowered by itself when the game gets slow). */
		public float amount = 1F;
	}

	public static final Settings SETTINGS = new Settings();

	private static final LiquidScan SCAN = new LiquidScan();
	private static final Spray PARTICLES = new Spray();
	private static final Map<Integer, Boolean> WAS_WET = new HashMap<>();
	private static boolean hooked;
	private static long lastFrame;
	private static final long START = System.nanoTime();
	/** For the settings page: what the renderer is doing. */
	public static volatile String status = "";

	private KuboWater() {
	}

	/** Called once at start-up (the pipelines above are registered when this class loads, before the shaders are compiled). */
	public static void init() {
		if (hooked) {
			return;
		}
		hooked = true;
		WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
			long kuboPerfT0 = System.nanoTime();
			try {
			try {
				render(ctx.consumers());
			} catch (Throwable t) {
				status = "Error: " + t;
				SETTINGS.on = false; // never break the game: switch ourselves off
				System.err.println("[Sleep] own liquid renderer stopped: " + t);
				t.printStackTrace();
			}
					} finally {
				dev.solclient.Perf.add("Water renderer", System.nanoTime() - kuboPerfT0);
			}
		});
	}

	// ------------------------------------------------------------------ every tick: scan + spawn
	public static void tick(MinecraftClient mc) {
		ClientWorld w = mc.world;
		if (!SETTINGS.on || w == null || mc.player == null) {
			if (PARTICLES.count() > 0) {
				PARTICLES.clear();
			}
			return;
		}
		Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
		SCAN.tick(w, cam, SETTINGS.radius, 6);
		if (!SETTINGS.spray || SETTINGS.amount <= 0F) {
			return;
		}
		ThreadLocalRandom r = ThreadLocalRandom.current();
		float amt = SETTINGS.amount;
		double r2 = (double) SETTINGS.radius * SETTINGS.radius;
		BlockPos.Mutable bp = new BlockPos.Mutable();

		for (LiquidScan.Section s : SCAN.sections.values()) {
			// waterfalls: a constant cloud of spray and foam where they land
			for (int i = 0; i < s.fallCount; i++) {
				float fx = s.falls[i * 3], fy = s.falls[i * 3 + 1], fz = s.falls[i * 3 + 2];
				if (sq(fx - cam.x, fz - cam.z) > r2) {
					continue;
				}
				int light = WorldRenderer.getLightmapCoordinates(w, bp.set(fx, fy + 0.5, fz));
				if (r.nextFloat() < 0.9F * amt) {
					PARTICLES.splash(fx, fy + 0.05, fz, 0, 0, 0.55F, 2 + r.nextInt(3), light);
				}
				if (r.nextFloat() < 0.5F * amt) {
					double a = r.nextDouble() * Math.PI * 2, d = 0.3 + r.nextDouble() * 1.2;
					PARTICLES.foam(fx + Math.cos(a) * d, fy + 0.012, fz + Math.sin(a) * d, Math.cos(a) * 0.15, Math.sin(a) * 0.15, 0.25F + r.nextFloat() * 0.35F, light);
				}
			}
			// currents and shores: foam that drifts; lava: embers and jumping blobs
			for (int i = 0, tries = Math.min(s.count, 6); i < tries; i++) {
				int k = r.nextInt(s.count) * LiquidScan.STRIDE;
				float[] q = s.quads;
				double x = q[k] + r.nextDouble(), z = q[k + 2] + r.nextDouble();
				if (sq(x - cam.x, z - cam.z) > r2) {
					continue;
				}
				double y = q[k + 1] + (q[k + 3] + q[k + 4] + q[k + 5] + q[k + 6]) / 4F;
				int light = Float.floatToRawIntBits(q[k + 10]);
				float fl = (float) Math.sqrt(q[k + 7] * q[k + 7] + q[k + 8] * q[k + 8]);
				if (q[k + 11] > 0) {
					if (SETTINGS.lavaFx && r.nextFloat() < 0.08F * amt) {
						PARTICLES.add(Spray.EMBER, x, y + 0.05, z, (r.nextDouble() - 0.5) * 0.3, 0.4 + r.nextDouble() * 0.8, (r.nextDouble() - 0.5) * 0.3,
								0.03F + r.nextFloat() * 0.04F, 1.2F + r.nextFloat() * 2F, light);
					}
					if (SETTINGS.lavaFx && r.nextFloat() < 0.012F * amt) {
						PARTICLES.add(Spray.BLOB, x, y, z, (r.nextDouble() - 0.5) * 0.8, 2.5 + r.nextDouble() * 2.5, (r.nextDouble() - 0.5) * 0.8,
								0.06F + r.nextFloat() * 0.06F, 3F, light);
					}
				} else if (fl > 0.05F && r.nextFloat() < 0.35F * amt) {
					PARTICLES.foam(x, y + 0.012, z, q[k + 7] * 0.8, q[k + 8] * 0.8, 0.15F + r.nextFloat() * 0.25F, light);
				} else if (q[k + 9] > 0.2F && r.nextFloat() < 0.08F * amt) {
					PARTICLES.foam(x, y + 0.012, z, 0, 0, 0.12F + r.nextFloat() * 0.2F, light);
				}
			}
		}
		playerFx(mc, w, r, amt, bp);
		// new water (a bucket, a broken wall): a splash where it appeared
		for (double[] a : SCAN.appeared) {
			if (sq(a[0] - cam.x, a[2] - cam.z) < r2) {
				PARTICLES.splash(a[0], a[1], a[2], 0, 0, 0.8F, (int) (18 * amt), WorldRenderer.getLightmapCoordinates(w, bp.set(a[0], a[1] + 0.5, a[2])));
			}
		}
		SCAN.appeared.clear();
		// things that fall in: a splash as big as the hit; swimming at the surface leaves a wake
		for (Entity e : w.getEntities()) {
			if (e == null || e.squaredDistanceTo(cam) > r2) {
				continue;
			}
			boolean wet = e.isTouchingWater();
			Boolean before = WAS_WET.put(e.getId(), wet);
			if (before == null) {
				continue;
			}
			Vec3d v = e.getVelocity();
			double speed = v.length();
			if (wet && !before && speed > 0.08) {
				double sy = surfaceY(w, e, bp);
				float power = (float) Math.min(1.6, 0.35 + speed * 1.3) * Math.max(0.6F, Math.min(1.6F, e.getWidth()));
				PARTICLES.splash(e.getX(), sy, e.getZ(), v.x, v.z, power, (int) Math.min(160, (20 + speed * 90) * amt * e.getWidth() * 1.4),
						WorldRenderer.getLightmapCoordinates(w, e.getBlockPos()));
			} else if (wet && speed > 0.06 && !e.isSubmergedInWater() && r.nextFloat() < 0.7F * amt) {
				double sy = surfaceY(w, e, bp);
				int light = WorldRenderer.getLightmapCoordinates(w, e.getBlockPos());
				PARTICLES.splash(e.getX() - v.x * 2, sy, e.getZ() - v.z * 2, -v.x * 0.5, -v.z * 0.5, 0.25F, 2, light);
				PARTICLES.foam(e.getX() - v.x * 3, sy + 0.012, e.getZ() - v.z * 3, 0, 0, 0.2F + r.nextFloat() * 0.25F, light);
			}
		}
		if (WAS_WET.size() > 800) {
			WAS_WET.clear();
		}
	}

	/**
	 * You in the water: a bow wave and spray around you when you move at the surface (more the faster you go), drops falling
	 * off when you climb out, bubbles behind you under water, and light shafts coming down from the surface when you dive.
	 */
	private static void playerFx(MinecraftClient mc, ClientWorld w, ThreadLocalRandom r, float amt, BlockPos.Mutable bp) {
		var p = mc.player;
		Vec3d v = p.getVelocity();
		double hs = Math.sqrt(v.x * v.x + v.z * v.z);
		int light = WorldRenderer.getLightmapCoordinates(w, p.getBlockPos().up());
		if (p.isTouchingWater() && !p.isSubmergedInWater() && hs > 0.03) {
			double sy = surfaceY(w, p, bp);
			double fx = v.x / hs, fz = v.z / hs;
			int n = (int) Math.min(14, (2 + hs * 30) * amt);
			// bow wave in front, spray off both sides
			PARTICLES.splash(p.getX() + fx * 0.45, sy, p.getZ() + fz * 0.45, v.x * 0.8, v.z * 0.8, (float) Math.min(0.9, 0.25 + hs * 1.6), n, light);
			for (int side = -1; side <= 1; side += 2) {
				PARTICLES.foam(p.getX() - fz * 0.45 * side - fx * 0.3, sy + 0.012, p.getZ() + fx * 0.45 * side - fz * 0.3, -fz * side * 0.4, fx * side * 0.4,
						0.2F + r.nextFloat() * 0.25F, light);
			}
		}
		// climbing out / standing up in the water: drops falling off you
		if (!p.isTouchingWater() && p.age - lastWetAge < 30 && r.nextFloat() < 0.6F * amt) {
			PARTICLES.add(Spray.SPRAY, p.getX() + (r.nextDouble() - 0.5) * 0.6, p.getY() + r.nextDouble() * 1.6, p.getZ() + (r.nextDouble() - 0.5) * 0.6, 0,
					-0.5, 0, 0.03F, 0.8F, light);
		}
		if (p.isTouchingWater()) {
			lastWetAge = p.age;
		}
		if (p.isSubmergedInWater()) {
			double sp = v.length();
			for (int i = 0, n = (int) ((1 + sp * 12) * amt); i < n; i++) {
				PARTICLES.add(Spray.BUBBLE, p.getX() + (r.nextDouble() - 0.5) * 0.6, p.getEyeY() - 0.2 + (r.nextDouble() - 0.5) * 0.6,
						p.getZ() + (r.nextDouble() - 0.5) * 0.6, 0, 0.3, 0, 0.025F + r.nextFloat() * 0.035F, 4F, light);
			}
			// light shafts: from the surface above you, when there is daylight
			if (PARTICLES.count(Spray.SHAFT) < (int) (16 * amt) && w.isDay()) {
				double ax = p.getX() + (r.nextDouble() - 0.5) * 18, az = p.getZ() + (r.nextDouble() - 0.5) * 18;
				bp.set(ax, p.getEyeY(), az);
				for (int i = 0; i < 40 && w.getFluidState(bp).isIn(FluidTags.WATER); i++) {
					bp.move(0, 1, 0);
				}
				if (w.getFluidState(bp).isEmpty() && w.isSkyVisible(bp)) {
					double top = bp.getY() - 0.1;
					PARTICLES.add(Spray.SHAFT, ax, top, az, (r.nextDouble() - 0.5) * 0.15, 6 + r.nextDouble() * 8, (r.nextDouble() - 0.5) * 0.15,
							0.3F + r.nextFloat() * 0.7F, 6F + r.nextFloat() * 6F, 0xF000F0);
				}
			}
		}
	}

	private static int lastWetAge = -1000;

	private static double sq(double a, double b) {
		return a * a + b * b;
	}

	private static double surfaceY(ClientWorld w, Entity e, BlockPos.Mutable bp) {
		bp.set(e.getBlockPos());
		for (int i = 0; i < 4 && !w.getFluidState(bp.up()).isEmpty(); i++) {
			bp.move(0, 1, 0);
		}
		FluidState fs = w.getFluidState(bp);
		return bp.getY() + (fs.isEmpty() ? 0.9 : fs.getHeight(w, bp));
	}

	// ------------------------------------------------------------------ every frame: draw
	private static void render(VertexConsumerProvider consumers) {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientWorld w = mc.world;
		long nowNs = System.nanoTime();
		float dt = lastFrame == 0 ? 0F : Math.min(0.1F, (nowNs - lastFrame) / 1e9F);
		lastFrame = nowNs;
		if (!SETTINGS.on || w == null || !(consumers instanceof VertexConsumerProvider.Immediate imm)) {
			return;
		}
		Camera camera = mc.gameRenderer.getCamera();
		Vec3d cam = camera.getCameraPos();
		float time = ((nowNs - START) / 1e9F) % 3600F;

		boolean under = camera.getSubmersionType() == CameraSubmersionType.WATER;
		if (SETTINGS.surface && (camera.getSubmersionType() == CameraSubmersionType.NONE || under)) {
			int sky;
			float sunAngle;
			try {
				sky = w.getEnvironmentAttributes().getAttributeValue(EnvironmentAttributes.SKY_COLOR_VISUAL, cam);
				sunAngle = w.getEnvironmentAttributes().getAttributeValue(EnvironmentAttributes.SUN_ANGLE_VISUAL, cam);
			} catch (Throwable t) {
				sky = 0x78A7FF;
				sunAngle = 0F;
			}
			double a = Math.toRadians(sunAngle);
			float sx = (float) -Math.sin(a), sy = (float) Math.cos(a);
			float rain = w.getRainGradient(1F);
			float sunI = Math.max(0F, Math.min(1F, sy * 4F)) * (1F - rain * 0.9F);
			if (sy < 0) { // night: the moon, dimmer, on the other side
				sx = -sx;
				sy = -sy;
				sunI = Math.min(1F, sy * 4F) * 0.25F * (1F - rain);
			}
			Vector3f sun = new Vector3f(sx, sy, 0).normalize().mul(Math.max(0.0F, sunI));
			int skyRgb = sky & 0xFFFFFF;
			VertexConsumer water = imm.getBuffer(WATER_LAYER);
			emitSurfaces(water, cam, time, sun, skyRgb, false, under);
			imm.draw(WATER_LAYER);
			VertexConsumer lava = imm.getBuffer(LAVA_LAYER);
			emitSurfaces(lava, cam, time, sun, skyRgb, true, under);
			imm.draw(LAVA_LAYER);
		}
		if (SETTINGS.spray) {
			if (dt > 0) {
				PARTICLES.update(w, dt);
			}
			if (PARTICLES.count() > 0) {
				var rot = camera.getRotation();
				Vector3f right = rot.transform(new Vector3f(1, 0, 0)), up = rot.transform(new Vector3f(0, 1, 0));
				VertexConsumer vc = imm.getBuffer(SPRAY_LAYER);
				PARTICLES.emit(vc, cam, right, up, dt);
				imm.draw(SPRAY_LAYER);
			}
		}
		status = SCAN.sections.size() + " sections, " + PARTICLES.count() + " particles";
	}

	private static void emitSurfaces(VertexConsumer vc, Vec3d cam, float time, Vector3f sun, int skyRgb, boolean lava, boolean under) {
		float radius = SETTINGS.radius;
		float r2 = (radius + 2) * (radius + 2);
		for (LiquidScan.Section s : SCAN.sections.values()) {
			float[] q = s.quads;
			for (int i = 0; i < s.count; i++) {
				int k = i * LiquidScan.STRIDE;
				if ((q[k + 11] > 0) != lava) {
					continue;
				}
				float x = (float) (q[k] - cam.x), y = (float) (q[k + 1] - cam.y), z = (float) (q[k + 2] - cam.z);
				float dx = x + 0.5F, dz = z + 0.5F;
				if (dx * dx + dz * dz > r2) {
					continue;
				}
				if (!under && !lava && y + q[k + 6] > 0.02F && y < 0) {
					continue; // the camera is inside this very block, just under its top: skip (seen from below)
				}
				int col = ((Math.round(q[k + 9] * 255) & 255) << 24) | skyRgb;
				int fx = Math.round(q[k + 7] * 1000), fz = Math.round(q[k + 8] * 1000);
				int light = Float.floatToRawIntBits(q[k + 10]);
				// from below, ours sits just under Minecraft's surface so it is the one you see
				float lift = under ? -0.004F : 0.0015F;
				v(vc, x, y + q[k + 3] + lift, z, col, time, radius, fx, fz, light, sun);
				v(vc, x, y + q[k + 5] + lift, z + 1, col, time, radius, fx, fz, light, sun);
				v(vc, x + 1, y + q[k + 6] + lift, z + 1, col, time, radius, fx, fz, light, sun);
				v(vc, x + 1, y + q[k + 4] + lift, z, col, time, radius, fx, fz, light, sun);
			}
		}
	}

	private static void v(VertexConsumer vc, float x, float y, float z, int col, float time, float radius, int fx, int fz, int light, Vector3f sun) {
		vc.vertex(x, y, z).color(col).texture(time, radius).overlay(fx, fz).light(light).normal(sun.x, sun.y, sun.z);
	}
}
