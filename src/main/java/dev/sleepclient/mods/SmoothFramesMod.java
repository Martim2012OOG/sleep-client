package dev.sleepclient.mods;

import dev.sleepclient.option.Option;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.particle.ParticlesMode;

/**
 * Smooth Frames: when the game starts to lag, the screen is lightened by itself (fewer particles, no entity shadows, no clouds, entities
 * drawn closer) so what you see stays as clean and steady as if you were playing on the lightest settings. When the game is smooth again
 * everything goes back exactly as you had it. Only settings that change instantly are touched (never the render distance: that would
 * rebuild every chunk and make the lag worse).
 */
public class SmoothFramesMod extends Mod {

	private final Option.Num below = num("Lighten Below", 40, 15, 120, 5, " FPS")
			.tip("When your FPS stays under this for a moment the screen is lightened. It goes back when the game is smooth again.");
	private final Option.Bool particles = bool("Fewer Particles", true);
	private final Option.Bool shadows = bool("No Entity Shadows", true);
	private final Option.Bool clouds = bool("Hide Clouds", true);
	private final Option.Bool closer = bool("Draw Entities Closer", true).tip("Players and mobs far away are not drawn while the game lags.");

	/** True while the lighter look is on, and the values to give back. */
	private boolean lite;
	private int lowTicks, goodTicks;
	private ParticlesMode savedParticles;
	private boolean savedShadows;
	private CloudRenderMode savedClouds;
	private double savedEntityScale;
	private boolean hooked;

	public SmoothFramesMod() {
		super("Smooth Frames", "smooth_frames", "Keeps the screen clean and steady when the game lags, as if you played on the lightest settings.", Category.UTILITY);
	}

	@Override
	protected void onEnable() {
		if (mc.options == null) {
			throw new IllegalStateException("options not ready"); // Mod.init tries again once the game is up
		}
		if (!hooked) {
			hooked = true;
			// never leave the lighter values in options.txt when the game closes
			ClientLifecycleEvents.CLIENT_STOPPING.register(c -> restore());
		}
	}

	@Override
	protected void onDisable() {
		restore();
		lowTicks = goodTicks = 0;
	}

	@Override
	public void tick() {
		if (mc.world == null || mc.options == null) {
			restore();
			lowTicks = goodTicks = 0;
			return;
		}
		int fps = mc.getCurrentFps();
		if (fps <= 0) {
			return; // not measured yet
		}
		float limit = below.f();
		if (fps < limit) {
			goodTicks = 0;
			if (!lite && ++lowTicks >= 30) { // about 1.5 s under the limit
				apply();
			}
		} else {
			lowTicks = 0;
			if (lite && fps > limit + 15F && ++goodTicks >= 80) { // about 4 s clearly above it
				restore();
			}
		}
	}

	private void apply() {
		var o = mc.options;
		savedParticles = o.getParticles().getValue();
		savedShadows = o.getEntityShadows().getValue();
		savedClouds = o.getCloudRenderMode().getValue();
		savedEntityScale = o.getEntityDistanceScaling().getValue();
		lite = true;
		if (particles.on()) {
			o.getParticles().setValue(ParticlesMode.MINIMAL);
		}
		if (shadows.on()) {
			o.getEntityShadows().setValue(false);
		}
		if (clouds.on()) {
			o.getCloudRenderMode().setValue(CloudRenderMode.OFF);
		}
		if (closer.on()) {
			o.getEntityDistanceScaling().setValue(Math.min(savedEntityScale, 0.6));
		}
	}

	private void restore() {
		if (!lite || mc.options == null) {
			lite = false;
			return;
		}
		lite = false;
		var o = mc.options;
		if (savedParticles != null) {
			o.getParticles().setValue(savedParticles);
		}
		o.getEntityShadows().setValue(savedShadows);
		if (savedClouds != null) {
			o.getCloudRenderMode().setValue(savedClouds);
		}
		o.getEntityDistanceScaling().setValue(savedEntityScale);
	}
}
