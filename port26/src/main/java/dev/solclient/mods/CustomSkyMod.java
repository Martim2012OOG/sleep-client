package dev.solclient.mods;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.solclient.Gfx;
import dev.solclient.option.Option;
import dev.solclient.sky.SkyBake;
import dev.solclient.sky.SkyFx;
import dev.solclient.sky.SkyEffects;
import dev.solclient.sky.SkyGen;
import dev.solclient.sky.SkyLook;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/**
 * Custom Sky: your own sky + your own atmosphere.
 * <ul>
 * <li>Sky: a photo, or a generated gradient (top / horizon / ground colours + a sun you can place), or the vanilla one.</li>
 * <li>Up to 3 layers of particles (petals, cherry flowers, snow, leaves, hearts, glow, sparks, stars or YOUR texture)
 * that float IN THE WORLD all around you (real 3D: they stay in place when you turn, near ones are big, far ones small,
 * flat ones tumble in the air). Each layer has its own amount, size, speed, sway, spin, opacity, wind and tint.</li>
 * <li>Ambient tint + vignette over the whole view.</li>
 * <li>Presets just fill all those options in; you can change anything after.</li>
 * </ul>
 * The sky itself is drawn by {@link dev.solclient.mixin.overlay.SkyRenderingMixin} (vanilla end-sky cube with our texture).
 * The particles are drawn in the world from a {@link LevelRenderEvents} hook ({@link #renderWorld}); tint + vignette on the HUD.
 * Settings are split in 3 tabs (Sky / Particles / Ambient) and the particle layers in 3 tabs, so only a few options show at once.
 */
public class CustomSkyMod extends Mod {

	public static CustomSkyMod instance;

	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "plegumo-sky");
		t.setDaemon(true);
		return t;
	});

	public static final String[] GRADES = {"None", "Warm", "Cold", "Pastel", "Night", "Cinematic", "Dreamy", "Vintage"};

	public static final String[] PRESETS = {"Custom (nothing applied)", "Sakura", "Sunflower", "Snowdrop", "Ember dusk", "Autumn", "Firefly night",
			"Love", "Starfall", "Party", "Spring garden", "Dreamy bubbles"};

	// existing options (keep their names so saved configs still load)
	private final Option.Num brightness = num("Brightness", 100, 10, 100, 1, "%");
	private final Option.Choice mapping = choice("Mapping", 0, "Panorama 360", "Photo on each side");
	private final Option.Bool hideCelestials = bool("Hide Sun, Moon & Stars", true);
	private final Option.Bool hideGlow = bool("Hide Sunrise Glow", true);

	// new options (created in the constructor, after the list was cleared, so the panel order is ours)
	private Option.Choice preset, section, sourceMode, layerTab;
	private Option.Col gTop, gHorizon, gGround, sunColour, tint;
	private Option.Bool sunOn, outdoors, glowing;
	private Option.Num sunHeight, sunDir, sunSize, sunGlow, tintStrength, vignette, area;
	private final Lay[] lays = new Lay[3];
	// time of day + night sky
	private Option.Bool timeOfDay, moonOn, shootingOn;
	private Option.Col sTop, sHorizon, nTop, nHorizon, moonColour, nebula;
	private Option.Num horizonGlow, stars, milky, moonSize, moonGlow, shootingRate, skyDensity;
	private Option.Choice celestialStyle;
	// effects
	private Option.Bool cloudsOn, hideVanillaClouds, auroraOn, auroraAlways, hazeOn, hazeAuto;
	private Option.Num cloudCount, cloudHeight, cloudSize, cloudOpacity, cloudSpeed, cloudWind;
	private Option.Col cloudColour, auroraA, auroraB, hazeColour;
	private Option.Num auroraStrength, auroraHeight, auroraSpeed, auroraDir, hazeStrength;
	private Option.Bool rainbowOn, rainbowAuto, raysOn;
	private Option.Num rainbowStrength, rainbowDir, raysStrength;
	// particles extra
	private Option.Bool landing, react;
	private Option.Num stay, gusts;
	// ambient extra
	private Option.Choice grading;
	private Option.Num gradingStrength;
	private Option.Bool letterbox;
	// more
	private Option.Bool autoSeason, menuBackground, autoPerf;
	private Option.Num targetFps;
	private volatile String shareStatus = "";
	private int lastSeason = -1;
	private float quality = 1F;
	private long perfCheckNs;
	private Object prevCloudMode;
	private final SkyEffects.Settings fx = new SkyEffects.Settings();
	private volatile SkyLook.Params pendingLook;
	private int bakedFace = SkyBake.FACE, bufferFace = -1;
	private int timeKey = Integer.MIN_VALUE;

	// worker side
	private volatile SkyBake.Source source;
	private volatile String bakedKey = "";
	private volatile int bakedMode = -1;
	private volatile boolean busy;
	private volatile String status = "";
	private int lastPreset = Integer.MIN_VALUE;

	// render-thread side
	private DynamicTexture texture;
	private GpuBuffer buffer;
	private int bufferBrightness = -1;
	private long lastNs;
	private int frameNo;
	private float smoothDt;
	private static boolean hooked;
	private static volatile String renderError = "";

	/** One layer of particles. */
	private final class Lay {
		final int idx;
		Option.Choice style;
		Option.Num amount, size, speed, sway, spin, opacity, wind, windDir, variation;
		Option.Bool advanced, randomColours;
		Option.Choice where;
		Option.Col colour;
		final SkyFx.Layer sim = new SkyFx.Layer();
		final SkyFx.Params params = new SkyFx.Params();
		volatile int[] custom;
		volatile int customVer;
		volatile String status = "";
		int seenVer = -1, uploadedStyle = -1;
		DynamicTexture tex;
		Identifier id;
		// sorting buffer (back to front)
		int[] order = new int[0];
		float[] dist = new float[0];
		// settings preview (2D, only inside the menu)
		final float[] px = new float[120], py = new float[120], ps = new float[120], pr = new float[120];
		boolean previewInit;

		Lay(int idx) {
			this.idx = idx;
		}

		void build() {
			String n = "Layer " + (idx + 1) + " ";
			BooleanSupplier here = () -> section.idx() == 3 && layerTab.idx() == idx;
			BooleanSupplier on = () -> here.getAsBoolean() && style.idx() != SkyGen.OFF;
			BooleanSupplier adv = () -> on.getAsBoolean() && advanced.on();
			style = choice(n + "style", 0, SkyGen.STYLES);
			style.visibleIf = here;
			action(n + "texture", "Choose image...", () -> pickLayer(this), () -> status)
					.showIf(() -> here.getAsBoolean() && style.idx() == SkyGen.CUSTOM);
			amount = num(n + "amount", 0, 0, 400, 1, "");
			size = num(n + "size", 30, 4, 150, 1, "");
			speed = num(n + "fall speed", 25, -100, 100, 1, "");
			opacity = num(n + "opacity", 90, 5, 100, 1, "%");
			colour = colour(n + "tint", 0xFFFFFFFF);
			for (Option<?> o : new Option<?>[] {amount, size, speed, opacity, colour}) {
				o.visibleIf = on;
			}
			where = choice(n + "where", 0, "Everywhere", "Near the ground", "High up");
			randomColours = bool(n + "random colours", false);
			where.visibleIf = on;
			randomColours.visibleIf = on;
			advanced = bool(n + "more motion options", false);
			advanced.visibleIf = on;
			sway = num(n + "sway", 40, 0, 100, 1, "%");
			spin = num(n + "spin", 40, 0, 100, 1, "%");
			wind = num(n + "wind", 0, -100, 100, 1, "");
			windDir = num(n + "wind direction", 0, 0, 359, 1, "°");
			variation = num(n + "size variation", 60, 0, 100, 1, "%");
			for (Option<?> o : new Option<?>[] {sway, spin, wind, windDir, variation}) {
				o.visibleIf = adv;
			}
		}
	}

	public CustomSkyMod() {
		super("Custom Sky", "custom_sky", "Your own sky, petals / snow / anything floating in the world around you, and ambient tint.",
				Category.VISUAL);
		instance = this;
		// declared after the fields above so they show up in our order: re-order the list
		options.clear();

		header("Quick start");
		preset = choice("Preset", 0, PRESETS);
		section = tabs("Section", 0, "Sky", "Night", "Effects", "Particles", "Ambient", "More");

		// ---------------------------------------------------------------- Sky tab
		BooleanSupplier sky = () -> section.idx() == 0;
		sourceMode = tabs("Sky source", 0, "Photo", "Gradient", "Vanilla");
		sourceMode.visibleIf = sky;
		options.add(brightness);
		options.add(hideCelestials);
		options.add(hideGlow);
		brightness.visibleIf = () -> sky.getAsBoolean() && sourceMode.idx() != 2;
		hideCelestials.visibleIf = sky;
		hideGlow.visibleIf = sky;

		BooleanSupplier photo = () -> sky.getAsBoolean() && sourceMode.idx() == 0;
		header("Photo").showIf(photo);
		action("Sky photo", "Choose photo...", this::pick, () -> status).showIf(photo);
		action("Remove photo", "Remove", this::remove, () -> "").showIf(photo);
		options.add(mapping);
		mapping.visibleIf = photo;

		BooleanSupplier grad = () -> sky.getAsBoolean() && sourceMode.idx() == 1;
		header("Colours").showIf(grad);
		gTop = colour("Sky top", 0xFF3B2A5C);
		gHorizon = colour("Sky horizon", 0xFFF0A890);
		gGround = colour("Sky ground", 0xFF4A3550);
		header("Sun").showIf(grad);
		sunOn = bool("Sun", true);
		sunColour = colour("Sun colour", 0xFFFFE2B8);
		sunHeight = num("Sun height", 6, -20, 80, 1, "°");
		sunDir = num("Sun direction", 0, 0, 359, 1, "°");
		sunSize = num("Sun size", 6, 1, 30, 1, "°");
		sunGlow = num("Sun glow", 70, 0, 100, 1, "%");
		for (Option<?> o : new Option<?>[] {gTop, gHorizon, gGround, sunOn}) {
			o.visibleIf = grad;
		}
		for (Option<?> o : new Option<?>[] {sunColour, sunSize, sunGlow}) {
			o.visibleIf = () -> grad.getAsBoolean() && sunOn.on();
		}
		header("Time of day").showIf(grad);
		timeOfDay = bool("Follow time of day", true);
		timeOfDay.visibleIf = grad;
		sunHeight.visibleIf = () -> grad.getAsBoolean() && sunOn.on() && !timeOfDay.on();
		sunDir.visibleIf = () -> grad.getAsBoolean() && sunOn.on() && !timeOfDay.on();
		BooleanSupplier tod = () -> grad.getAsBoolean() && timeOfDay.on();
		sTop = colour("Sunset top", 0xFF4A3778);
		sHorizon = colour("Sunset horizon", 0xFFF39A7C);
		nTop = colour("Night top", 0xFF070B1E);
		nHorizon = colour("Night horizon", 0xFF22305A);
		for (Option<?> o : new Option<?>[] {sTop, sHorizon, nTop, nHorizon}) {
			o.visibleIf = tod;
		}
		horizonGlow = num("Horizon glow", 35, 0, 100, 1, "%");
		horizonGlow.visibleIf = grad;
		skyDensity = num("Sky density", 50, 0, 100, 1, "%");
		skyDensity.visibleIf = grad;
		celestialStyle = choice("Sun & moon look", 0, "Blocky (Minecraft)", "Round");
		celestialStyle.visibleIf = () -> grad.getAsBoolean() || section.idx() == 1 && sourceMode.idx() == 1;

		// ---------------------------------------------------------------- Night tab
		BooleanSupplier night = () -> section.idx() == 1;
		header("Stars").showIf(night);
		stars = num("Stars", 55, 0, 100, 1, "%");
		milky = num("Milky way", 60, 0, 100, 1, "%");
		nebula = colour("Nebula colour", 0xFF8A5CFF);
		header("Moon").showIf(night);
		moonOn = bool("Moon", true);
		moonColour = colour("Moon colour", 0xFFE8ECF5);
		moonSize = num("Moon size", 5, 1, 20, 1, "\u00b0");
		moonGlow = num("Moon glow", 40, 0, 100, 1, "%");
		header("Shooting stars").showIf(night);
		shootingOn = bool("Shooting stars", true);
		shootingRate = num("Shooting star rate", 40, 5, 100, 1, "%");
		for (Option<?> o : new Option<?>[] {stars, milky, nebula, moonOn, shootingOn}) {
			o.visibleIf = night;
		}
		for (Option<?> o : new Option<?>[] {moonColour, moonSize, moonGlow}) {
			o.visibleIf = () -> night.getAsBoolean() && moonOn.on();
		}
		shootingRate.visibleIf = () -> night.getAsBoolean() && shootingOn.on();
		action("Night sky", "", () -> {
		}, () -> "Stars, milky way and moon are part of the Gradient sky").showIf(() -> night.getAsBoolean() && sourceMode.idx() != 1);

		// -------------------------------------------------------------- Effects tab
		BooleanSupplier eff = () -> section.idx() == 2;
		header("Clouds").showIf(eff);
		cloudsOn = bool("Soft clouds", true);
		hideVanillaClouds = bool("Hide vanilla clouds", true);
		cloudCount = num("Cloud amount", 50, 5, 200, 1, "");
		cloudHeight = num("Cloud height", 170, 80, 320, 1, " Y");
		cloudSize = num("Cloud size", 100, 30, 300, 5, "%");
		cloudOpacity = num("Cloud opacity", 85, 10, 100, 1, "%");
		cloudSpeed = num("Cloud speed", 30, 0, 100, 1, "%");
		cloudWind = num("Cloud direction", 60, 0, 359, 1, "\u00b0");
		cloudColour = colour("Cloud colour", 0xFFFFFFFF);
		cloudsOn.visibleIf = eff;
		for (Option<?> o : new Option<?>[] {hideVanillaClouds, cloudCount, cloudHeight, cloudSize, cloudOpacity, cloudSpeed, cloudWind, cloudColour}) {
			o.visibleIf = () -> eff.getAsBoolean() && cloudsOn.on();
		}
		header("Aurora").showIf(eff);
		auroraOn = bool("Aurora", false);
		auroraAlways = bool("Aurora in daytime too", false);
		auroraA = colour("Aurora colour 1", 0xFF3CFFB4);
		auroraB = colour("Aurora colour 2", 0xFF8A5CFF);
		auroraStrength = num("Aurora brightness", 70, 10, 100, 1, "%");
		auroraHeight = num("Aurora height", 45, 15, 120, 1, " blocks");
		auroraSpeed = num("Aurora speed", 40, 0, 100, 1, "%");
		auroraDir = num("Aurora direction", 0, 0, 359, 1, "\u00b0");
		auroraOn.visibleIf = eff;
		for (Option<?> o : new Option<?>[] {auroraAlways, auroraA, auroraB, auroraStrength, auroraHeight, auroraSpeed, auroraDir}) {
			o.visibleIf = () -> eff.getAsBoolean() && auroraOn.on();
		}
		header("Horizon haze").showIf(eff);
		hazeOn = bool("Horizon haze", true);
		hazeAuto = bool("Haze takes the sky colour", true);
		hazeColour = colour("Haze colour", 0xFFF0A890);
		hazeStrength = num("Haze strength", 40, 5, 100, 1, "%");
		hazeOn.visibleIf = eff;
		hazeAuto.visibleIf = () -> eff.getAsBoolean() && hazeOn.on();
		hazeStrength.visibleIf = () -> eff.getAsBoolean() && hazeOn.on();
		hazeColour.visibleIf = () -> eff.getAsBoolean() && hazeOn.on() && !hazeAuto.on();
		header("Rainbow & sun rays").showIf(eff);
		rainbowOn = bool("Rainbow", false);
		rainbowAuto = bool("Rainbow opposite the sun", true);
		rainbowStrength = num("Rainbow brightness", 55, 10, 100, 1, "%");
		rainbowDir = num("Rainbow direction", 180, 0, 359, 1, "\u00b0");
		raysOn = bool("Sun rays", false);
		raysStrength = num("Sun rays strength", 40, 5, 100, 1, "%");
		rainbowOn.visibleIf = eff;
		raysOn.visibleIf = eff;
		rainbowAuto.visibleIf = () -> eff.getAsBoolean() && rainbowOn.on();
		rainbowStrength.visibleIf = () -> eff.getAsBoolean() && rainbowOn.on();
		rainbowDir.visibleIf = () -> eff.getAsBoolean() && rainbowOn.on() && !rainbowAuto.on();
		raysStrength.visibleIf = () -> eff.getAsBoolean() && raysOn.on();

		// ---------------------------------------------------------- Particles tab
		BooleanSupplier parts = () -> section.idx() == 3;
		area = num("Area around you", 24, 8, 64, 1, " blocks");
		outdoors = bool("Only outdoors", true);
		glowing = bool("Glow in the dark", true);
		landing = bool("Land on the ground", true);
		react = bool("React to you", true);
		stay = num("Stay on the ground", 4, 1, 20, 1, " s");
		gusts = num("Wind gusts", 40, 0, 100, 1, "%");
		for (Option<?> o : new Option<?>[] {area, outdoors, glowing, landing, react, gusts}) {
			o.visibleIf = parts;
		}
		stay.visibleIf = () -> parts.getAsBoolean() && landing.on();
		layerTab = tabs("Layer", 0, "Layer 1", "Layer 2", "Layer 3");
		layerTab.visibleIf = parts;
		for (int i = 0; i < lays.length; i++) {
			lays[i] = new Lay(i);
			lays[i].build();
		}

		// ------------------------------------------------------------ Ambient tab
		BooleanSupplier amb = () -> section.idx() == 4;
		header("Colour grading").showIf(amb);
		grading = choice("Look", 0, GRADES);
		gradingStrength = num("Look strength", 60, 0, 100, 1, "%");
		letterbox = bool("Cinematic bars", false);
		grading.visibleIf = amb;
		gradingStrength.visibleIf = () -> amb.getAsBoolean() && grading.idx() != 0;
		letterbox.visibleIf = amb;
		header("Tint").showIf(amb);
		tint = colour("Ambient tint", 0xFFFF9EC8);
		tintStrength = num("Tint strength", 0, 0, 60, 1, "%");
		vignette = num("Vignette", 0, 0, 100, 1, "%");
		tint.visibleIf = amb;
		tintStrength.visibleIf = amb;
		vignette.visibleIf = amb;

		// --------------------------------------------------------------- More tab
		BooleanSupplier more = () -> section.idx() == 5;
		header("Automatic").showIf(more);
		autoSeason = bool("Preset follows the season", false);
		menuBackground = bool("Your sky on the main menu", true);
		autoPerf = bool("Auto performance", true);
		targetFps = num("Target FPS", 60, 30, 240, 5, "");
		for (Option<?> o : new Option<?>[] {autoSeason, menuBackground, autoPerf}) {
			o.visibleIf = more;
		}
		targetFps.visibleIf = () -> more.getAsBoolean() && autoPerf.on();
		action("Effects quality", "Reset", () -> quality = 1F, () -> Math.round(quality * 100) + "%" + (quality < 0.99F ? " (lowered to keep your FPS)" : ""))
				.showIf(() -> more.getAsBoolean() && autoPerf.on());
		header("Share").showIf(more);
		action("Copy sky code", "Copy", this::copyCode, () -> shareStatus).showIf(more);
		action("Paste sky code", "Paste", this::pasteCode, () -> "from the clipboard").showIf(more);
	}

	@Override
	public void init() {
		super.init();
		if (hooked) {
			return;
		}
		hooked = true;
		try {
			LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> {
			long kuboPerfT0 = System.nanoTime();
			try {
				CustomSkyMod m = instance;
				if (m == null || !m.enabled) {
					return;
				}
				long t0 = System.nanoTime();
				try {
					m.renderWorld(ctx.poseStack(), ctx.submitNodeCollector(), ctx.levelState().cameraRenderState.pos,
							ctx.levelState().cameraRenderState.orientation);
					m.addCost(System.nanoTime() - t0);
				} catch (RuntimeException e) {
					if (renderError.isEmpty()) {
						System.err.println("[Sleep] Custom Sky particles: " + e);
						e.printStackTrace();
					}
					renderError = String.valueOf(e);
				}
						} finally {
				dev.solclient.Perf.add("Custom sky", System.nanoTime() - kuboPerfT0);
			}
		});
		} catch (Throwable t) {
			System.err.println("[Sleep] Custom Sky: world particles unavailable: " + t);
		}
	}

	// ------------------------------------------------------------------ paths
	private static Path dir() {
		return FabricLoader.getInstance().getGameDir().resolve("plegumo").resolve("sky");
	}

	private static Path saved() {
		return dir().resolve("sky.png");
	}

	private static Path layerFile(int idx) {
		return dir().resolve("layer" + (idx + 1) + ".png");
	}

	// -------------------------------------------------------------- lifecycle
	@Override
	protected void onEnable() {
		if (source == null && !busy && Files.exists(saved())) {
			load(saved(), false);
		} else if (source == null && !busy) {
			status = "No photo yet";
		}
		for (Lay l : lays) {
			if (l.custom == null && Files.exists(layerFile(l.idx))) {
				loadLayer(l, layerFile(l.idx), false);
			}
		}
	}

	@Override
	public void tick() {
		// a preset was picked in the settings: fill every option from it (the first tick just remembers the saved value)
		int p = preset.idx();
		if (lastPreset == Integer.MIN_VALUE) {
			lastPreset = p;
		} else if (p != lastPreset) {
			lastPreset = p;
			applyPreset(p);
		}
		// preset follows the real season (checked once a minute)
		if (autoSeason.on() && mc.level != null && mc.level.getGameTime() % 1200 == 0) {
			int season = seasonPreset();
			if (season != lastSeason) {
				lastSeason = season;
				preset.set(season);
				lastPreset = season;
				applyPreset(season);
			}
		}
		// vanilla clouds off while our soft clouds are on (and back as they were after)
		updateVanillaClouds(enabled && cloudsOn.on() && hideVanillaClouds.on());
		// the generated sky (time of day moves the sun, the colours and the stars): rebuilt when it changed
		if (sourceMode.idx() == 1) {
			lastLook = buildLook();
		}
		if (!busy && !currentKey().equals(bakedKey)) {
			bake();
		}
	}

	@Override
	protected void onDisable() {
		updateVanillaClouds(false);
	}

	private void updateVanillaClouds(boolean hide) {
		try {
			var opt = mc.options.cloudStatus();
			if (hide && prevCloudMode == null) {
				prevCloudMode = opt.get();
				opt.set(net.minecraft.client.CloudStatus.OFF);
			} else if (!hide && prevCloudMode != null) {
				opt.set((net.minecraft.client.CloudStatus) prevCloudMode);
				prevCloudMode = null;
			}
		} catch (RuntimeException ignored) {
		}
	}

	/** Spring = Sakura, summer = Sunflower, autumn = Autumn, winter = Snowdrop. */
	private static int seasonPreset() {
		int m = java.time.LocalDate.now().getMonthValue();
		return m >= 3 && m <= 5 ? 1 : m >= 6 && m <= 8 ? 2 : m >= 9 && m <= 11 ? 5 : 3;
	}

	// ------------------------------------------------------------------ time of day
	private volatile SkyLook.Params lastLook;
	private float dayNight, daySunset;

	/** night (0..1) and sunset (0..1) right now, from the world time. */
	private float[] dayState() {
		if (mc.level == null) {
			return new float[] {0F, 0F};
		}
		long t = Math.floorMod(mc.level.getOverworldClockTime(), 24000L);
		double ey = Math.sin(2 * Math.PI * t / 24000.0);
		float night = (float) SkyFx.smoothstep(0.08, -0.22, ey);
		float sunset = (float) Math.max(0, 1 - Math.abs(ey) / 0.32);
		return new float[] {night, sunset};
	}

	private static int darker(int c) {
		return SkyLook.mix(c, 0xFF000000, 0.45);
	}

	/** The generated sky as it should look now. */
	private SkyLook.Params buildLook() {
		SkyLook.Params p = new SkyLook.Params();
		double[] sd;
		float night, sunset;
		if (timeOfDay.on() && mc.level != null) {
			long t = Math.floorMod(mc.level.getOverworldClockTime(), 24000L) / 100 * 100; // re-painted every 5 seconds of game time
			double th = 2 * Math.PI * t / 24000.0;
			double ex = Math.cos(th), ey = Math.sin(th), ez = 0.22;
			double l = Math.sqrt(ex * ex + ey * ey + ez * ez);
			sd = new double[] {ex / l, ey / l, ez / l};
			night = (float) SkyFx.smoothstep(0.08, -0.22, ey);
			sunset = (float) Math.max(0, 1 - Math.abs(ey) / 0.32);
			p.top = SkyLook.mix(SkyLook.mix(gTop.c(), sTop.c(), sunset), nTop.c(), night);
			p.horizon = SkyLook.mix(SkyLook.mix(gHorizon.c(), sHorizon.c(), sunset), nHorizon.c(), night);
			p.ground = SkyLook.mix(SkyLook.mix(gGround.c(), darker(sHorizon.c()), sunset), darker(nHorizon.c()), night);
			p.mx = -sd[0];
			p.my = -sd[1];
			p.mz = -sd[2];
			p.night = night;
		} else {
			sd = SkyLook.dir(sunDir.f(), sunHeight.f());
			sunset = 0F;
			p.top = gTop.c();
			p.horizon = gHorizon.c();
			p.ground = gGround.c();
			double[] md = SkyLook.dir(sunDir.f() + 180, 35);
			p.mx = md[0];
			p.my = md[1];
			p.mz = md[2];
			p.night = 1F; // stars only show where the sky is dark anyway
		}
		p.sx = sd[0];
		p.sy = sd[1];
		p.sz = sd[2];
		p.sun = sunOn.on();
		p.sunColour = SkyLook.mix(sunColour.c(), 0xFFFF8A4A, sunset * 0.6);
		p.sunSize = sunSize.f();
		p.sunGlow = sunGlow.f();
		p.moon = moonOn.on();
		p.moonColour = moonColour.c();
		p.moonSize = moonSize.f();
		p.moonGlow = moonGlow.f();
		p.stars = stars.f();
		p.milky = milky.f();
		p.nebula = nebula.c();
		p.haze = horizonGlow.f();
		p.density = skyDensity.f();
		p.blocky = celestialStyle.idx() == 0;
		return p;
	}

	private static String keyOf(SkyLook.Params p) {
		return p.top + "," + p.horizon + "," + p.ground + "," + p.sun + "," + p.sunColour + "," + (float) p.sx + "," + (float) p.sy + "," + (float) p.sz + ","
				+ p.sunSize + "," + p.sunGlow + "," + p.moon + "," + p.moonColour + "," + (float) p.mx + "," + (float) p.my + "," + p.moonSize + ","
				+ p.moonGlow + "," + p.stars + "," + p.milky + "," + p.nebula + "," + p.night + "," + p.haze + "," + p.density + "," + p.blocky;
	}

	// ------------------------------------------------------------ user actions
	/** Opens the file picker on a worker thread. Returns null when cancelled or when there is no picker. */
	private static String dialog(String title) throws Throwable {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			PointerBuffer filters = stack.mallocPointer(5);
			filters.put(stack.UTF8("*.png")).put(stack.UTF8("*.jpg")).put(stack.UTF8("*.jpeg")).put(stack.UTF8("*.bmp")).put(stack.UTF8("*.gif"));
			filters.flip();
			return TinyFileDialogs.tinyfd_openFileDialog(title, "", filters, "Images", false);
		}
	}

	private void pick() {
		if (busy) {
			return;
		}
		status = "Choosing...";
		Thread t = new Thread(() -> {
			String path;
			try {
				path = dialog("Choose a sky photo");
			} catch (Throwable e) {
				status = "No file picker: drag a photo onto the window";
				return;
			}
			if (path == null || path.isBlank()) {
				status = Files.exists(saved()) ? "Active" : "No photo yet";
				return;
			}
			choose(Path.of(path));
		}, "plegumo-sky-picker");
		t.setDaemon(true);
		t.start();
	}

	private void pickLayer(Lay l) {
		l.status = "Choosing...";
		Thread t = new Thread(() -> {
			String path;
			try {
				path = dialog("Choose a texture for layer " + (l.idx + 1) + " (PNG with transparency works best)");
			} catch (Throwable e) {
				l.status = "No file picker";
				return;
			}
			if (path == null || path.isBlank()) {
				l.status = l.custom != null ? "Custom texture" : "";
				return;
			}
			String n = path.toLowerCase(Locale.ROOT);
			if (!(n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".bmp") || n.endsWith(".gif"))) {
				l.status = "Not an image";
				return;
			}
			loadLayer(l, Path.of(path), true);
		}, "plegumo-sky-layer-picker");
		t.setDaemon(true);
		t.start();
	}

	/** Called with the file picked in the dialog or dropped onto the window. Any thread. */
	public void choose(Path file) {
		if (busy) {
			return;
		}
		String n = file.getFileName().toString().toLowerCase(Locale.ROOT);
		if (!(n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".bmp") || n.endsWith(".gif"))) {
			status = "Not an image (png/jpg)";
			return;
		}
		sourceMode.set(0);
		load(file, true);
		if (!enabled) {
			setEnabled(true);
		}
	}

	private void remove() {
		if (busy) {
			return;
		}
		source = null;
		bakedKey = "";
		bakedMode = -1;
		try {
			Files.deleteIfExists(saved());
		} catch (Exception ignored) {
		}
		status = "No photo yet";
		mc.execute(this::freeGpu);
	}

	// ----------------------------------------------------------- worker side
	private void load(Path file, boolean saveCopy) {
		busy = true;
		status = "Loading...";
		WORKER.execute(() -> {
			try {
				BufferedImage img = ImageIO.read(file.toFile());
				if (img == null) {
					status = "Unsupported image";
					return;
				}
				if (saveCopy) {
					Files.createDirectories(dir());
					Path tmp = dir().resolve("sky.tmp");
					ImageIO.write(img, "png", tmp.toFile());
					Files.move(tmp, saved(), StandardCopyOption.REPLACE_EXISTING);
				}
				source = SkyBake.source(img);
				bakeNow();
			} catch (Throwable e) {
				status = "Error: " + e.getClass().getSimpleName();
				System.err.println("[Sleep] Custom Sky: could not load " + file + ": " + e);
			} finally {
				busy = false;
			}
		});
	}

	private void loadLayer(Lay l, Path file, boolean userPicked) {
		l.status = "Loading...";
		WORKER.execute(() -> {
			try {
				BufferedImage img = ImageIO.read(file.toFile());
				if (img == null) {
					l.status = "Unsupported image";
					return;
				}
				if (userPicked) {
					Files.createDirectories(dir());
					ImageIO.write(img, "png", layerFile(l.idx).toFile());
				}
				l.custom = SkyGen.fit(img);
				l.customVer++;
				l.status = "Custom texture";
				if (userPicked) {
					l.style.set(SkyGen.CUSTOM);
					if (l.amount.i() == 0) {
						l.amount.set(40F);
					}
					if (!enabled) {
						setEnabled(true);
					}
				}
			} catch (Throwable e) {
				l.status = "Error: " + e.getClass().getSimpleName();
				System.err.println("[Sleep] Custom Sky: could not load layer texture " + file + ": " + e);
			}
		});
	}

	private void bake() {
		busy = true;
		status = "Processing...";
		WORKER.execute(() -> {
			try {
				bakeNow();
			} catch (Throwable e) {
				status = "Error: " + e.getClass().getSimpleName();
			} finally {
				busy = false;
			}
		});
	}

	/** Everything that changes the baked sky texture. */
	private String currentKey() {
		return switch (sourceMode.idx()) {
			case 0 -> "p" + System.identityHashCode(source) + "m" + mapping.idx();
			case 1 -> lastLook == null ? "g?" : "g" + keyOf(lastLook);
			default -> "v";
		};
	}

	private SkyGen.Gradient gradient() {
		return new SkyGen.Gradient(gTop.c(), gHorizon.c(), gGround.c(), sunOn.on(), sunColour.c(), sunDir.f(), sunHeight.f(), sunSize.f(), sunGlow.f());
	}

	private void bakeNow() {
		int mode = sourceMode.idx();
		bakedKey = currentKey();
		final int[] atlas;
		if (mode == 0) {
			SkyBake.Source s = source;
			if (s == null) {
				bakedMode = -1;
				return;
			}
			atlas = SkyBake.bake(s, mapping.idx());
		} else if (mode == 1) {
			SkyLook.Params lp = lastLook;
			if (lp == null) {
				bakedKey = "";
				return;
			}
			// painted straight onto the cube faces at 512 px: sharp enough for a smooth sky, and fast to re-paint
			final int face = timeOfDay.on() ? 384 : 512;
			atlas = SkyLook.bake(lp, face);
			final int[] a2 = atlas;
			mc.execute(() -> upload(a2, mode, face));
			return;
		} else {
			bakedMode = mode;
			return;
		}
		mc.execute(() -> upload(atlas, mode, SkyBake.FACE));
	}

	// ----------------------------------------------------- render thread side
	private void upload(int[] atlas, int mode, int face) {
		try {
			int aw = face * 3, ah = face * 2;
			if (texture != null && bakedFace == face && texture.getPixels() != null) {
				// same size: re-use the texture (no new GPU allocation every few seconds in time-of-day mode)
				NativeImage img = texture.getPixels();
				for (int y = 0; y < ah; y++) {
					int row = y * aw;
					for (int x = 0; x < aw; x++) {
						img.setPixel(x, y, atlas[row + x]);
					}
				}
				texture.upload();
				bakedMode = mode;
				status = "Active";
				return;
			}
			SkyTexture t = new SkyTexture("plegumo_custom_sky", aw, ah, false);
			NativeImage img = t.getPixels();
			if (img == null) {
				t.close();
				status = "Error: no image memory";
				return;
			}
			for (int y = 0; y < ah; y++) {
				int row = y * aw;
				for (int x = 0; x < aw; x++) {
					img.setPixel(x, y, atlas[row + x]);
				}
			}
			t.useBilinear();
			t.upload();
			if (texture != null) {
				texture.close();
			}
			texture = t;
			bakedFace = face;
			bakedMode = mode;
			status = "Active";
		} catch (Throwable e) {
			status = "Error: " + e.getClass().getSimpleName();
			System.err.println("[Sleep] Custom Sky: upload failed: " + e);
		}
	}

	/** 1.21.11 removed setFilter(); the filter now lives in the texture's sampler. */
	private static final class SkyTexture extends DynamicTexture {
		SkyTexture(String name, int width, int height, boolean useStb) {
			super(name, width, height, useStb);
		}

		void useBilinear() {
			this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		}
	}

	private void freeGpu() {
		if (texture != null) {
			texture.close();
			texture = null;
		}
		if (buffer != null) {
			buffer.close();
			buffer = null;
		}
		bufferBrightness = -1;
	}

	private void rebuildBuffer(int percent) {
		if (buffer != null) {
			buffer.close();
			buffer = null;
		}
		int g = Math.max(0, Math.min(255, Math.round(percent * 2.55F)));
		int rgba = 0xFF000000 | g << 16 | g << 8 | g;
		// POSITION_TEXTURE_COLOR: 3 floats + 2 floats + 4 bytes = 24 bytes per vertex, 4 vertices per face
		ByteBuffer bb = ByteBuffer.allocateDirect(6 * 4 * 24).order(ByteOrder.nativeOrder());
		int fc = bakedFace, aw = fc * 3, ah = fc * 2;
		for (int f = 0; f < 6; f++) {
			int col = f % 3, row = f / 3;
			float u0 = (col * fc + 0.5F) / aw, u1 = ((col + 1) * fc - 0.5F) / aw;
			float v0 = (row * fc + 0.5F) / ah, v1 = ((row + 1) * fc - 0.5F) / ah;
			float[][] uv = {{u0, v0}, {u0, v1}, {u1, v1}, {u1, v0}};
			for (int k = 0; k < 4; k++) {
				float[] p = SkyBake.FACES[f][k];
				bb.putFloat(p[0]).putFloat(p[1]).putFloat(p[2]);
				bb.putFloat(uv[k][0]).putFloat(uv[k][1]);
				bb.putInt(rgba);
			}
		}
		bb.flip();
		// usage 32 = GpuBuffer.USAGE_VERTEX
		buffer = RenderSystem.getDevice().createBuffer(() -> "Sleep custom sky", 32, bb);
		bufferBrightness = percent;
		bufferFace = fc;
	}

	// ------------------------------------------------------- particle textures
	/** Uploads the sprite of a layer when its style (or its custom texture) changed. Render thread. */
	private boolean ensureTexture(Lay l, int style) {
		boolean custom = style == SkyGen.CUSTOM;
		int ver = l.customVer;
		if (l.tex != null && l.uploadedStyle == style && (!custom || ver == l.seenVer)) {
			return true;
		}
		try {
			int[] px = custom ? (l.custom != null ? l.custom : SkyGen.sprite(SkyGen.GLOW)) : SkyGen.sprite(style);
			if (l.tex == null) {
				l.tex = new SkyTexture("plegumo_sky_layer_" + l.idx, SkyGen.SPRITE, SkyGen.SPRITE, false);
				l.id = Gfx.id("sky_layer_" + l.idx);
				mc.getTextureManager().register(l.id, l.tex);
				((SkyTexture) l.tex).useBilinear();
			}
			NativeImage img = l.tex.getPixels();
			if (img == null) {
				return false;
			}
			int n = SkyGen.SPRITE;
			for (int y = 0; y < n; y++) {
				for (int x = 0; x < n; x++) {
					img.setPixel(x, y, px[y * n + x]);
				}
			}
			l.tex.upload();
			l.uploadedStyle = style;
			l.seenVer = ver;
			return true;
		} catch (Throwable e) {
			l.status = "Error: " + e.getClass().getSimpleName();
			l.uploadedStyle = style;
			l.seenVer = ver;
			return false;
		}
	}

	private static float clamp01(float v) {
		return Math.max(0F, Math.min(1F, v));
	}

	private void fillParams(Lay l, int st) {
		SkyFx.Params q = l.params;
		q.style = st;
		q.amount = l.amount.i();
		q.size = l.size.f();
		q.speed = l.speed.f();
		q.sway = l.sway.f();
		q.spin = l.spin.f();
		q.opacity = l.opacity.f();
		q.wind = l.wind.f();
		q.windDir = l.windDir.f();
		q.sizeVariation = l.variation.f();
		q.radius = area.f();
		q.amount = Math.round(q.amount * quality);
		q.landing = landing.on() && SkyGen.landable(st);
		q.stay = stay.f();
		q.react = react.on();
		q.band = l.where.idx();
	}

	/** Light of the particles: full bright when "Glow in the dark" is on, otherwise follows day / night. */
	private int light() {
		if (glowing.on() || mc.level == null) {
			return 0xF000F0;
		}
		long t = Math.floorMod(mc.level.getOverworldClockTime(), 24000L);
		float f = t < 12000 ? 1F : t < 13800 ? 1F - (t - 12000) / 1800F * 0.75F : t < 22200 ? 0.25F : 0.25F + (t - 22200) / 1800F * 0.75F;
		int b = Math.max(3, Math.round(f * 15F));
		return b << 20 | b << 4;
	}

	// ------------------------------------------------------- particles in the world (3D)
	/** Called every frame from the world renderer. Positions are emitted relative to the camera (like the browser panels). */
	private void renderWorld(PoseStack m, SubmitNodeCollector queue, Vec3 cam, Quaternionf camRot) {
		var world = mc.level;
		if (mc.player == null || world == null || cam == null) {
			return;
		}
		long now = System.nanoTime();
		float raw = lastNs == 0L ? 0F : (now - lastNs) / 1.0e9F;
		lastNs = now;
		// a hitch (lag spike, alt-tab, loading) must not make everything jump: cap the step and smooth it out
		raw = Math.min(raw, 0.05F);
		smoothDt = smoothDt <= 0F ? raw : smoothDt + (raw - smoothDt) * 0.2F;
		float dt = Math.min(raw, smoothDt * 1.5F + 0.002F);
		frameNo++;
		float t = (float) ((now / 1.0e9) % 6000.0);
		boolean paused = mc.isPaused();
		if (paused) {
			dt = 0F;
		}

		// camera right / up (billboards face you)
		Quaternionf q = camRot != null ? camRot : new Quaternionf();
		Vector3f right = q.transform(new Vector3f(1, 0, 0));
		Vector3f up = q.transform(new Vector3f(0, 1, 0));
		int lt = light();
		boolean onlyOut = outdoors.on();
		autoPerformance(now);
		float[] day = dayState();
		dayNight = day[0];
		daySunset = day[1];
		// gusts: every now and then the wind blows harder for a few seconds
		float gust = (float) Math.max(0, Math.sin(t * 0.13) + 0.7 * Math.sin(t * 0.31 + 1.0) - 0.6) * gusts.f() / 100F * 1.5F;
		var pl = mc.player;
		var vel = pl.getDeltaMovement();

		for (Lay l : lays) {
			int st = l.style.idx();
			if (st == SkyGen.OFF || l.amount.i() <= 0) {
				continue;
			}
			fillParams(l, st);
			SkyFx.Params pr = l.params;
			pr.gust = gust;
			pr.px = pl.getX();
			pr.py = pl.getY();
			pr.pz = pl.getZ();
			pr.pvx = vel.x * dt * 20;
			pr.pvz = vel.z * dt * 20;
			SkyFx.Layer sim = l.sim;
			sim.step(pr, dt, t, cam.x, cam.y, cam.z);
			if (onlyOut || pr.landing) {
				// re-check a slice of the particles each frame: the ground below it / is there a roof above it?
				for (int i = frameNo % 6; i < sim.n; i += 6) {
					int bx = (int) Math.floor(sim.x[i]), bz = (int) Math.floor(sim.z[i]);
					int top = world.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
					sim.ground[i] = top;
					sim.covered[i] = onlyOut && sim.y[i] < top - 0.5 && sim.land[i] < 0F;
				}
			} else {
				java.util.Arrays.fill(sim.covered, false);
			}
			if (!ensureTexture(l, st)) {
				continue;
			}
			emitLayer(m, queue, l, st, cam, right, up, t, lt);
		}
		fillEffects();
		SkyEffects.render(m, queue, cam, right, up, t, dt, fx);
	}

	private void fillEffects() {
		SkyEffects.Settings f = fx;
		f.clouds = cloudsOn.on();
		f.cloudCount = cloudCount.i();
		f.cloudColour = cloudColour.c();
		f.cloudHeight = cloudHeight.f();
		f.cloudSize = cloudSize.f();
		f.cloudOpacity = cloudOpacity.f();
		f.cloudSpeed = cloudSpeed.f();
		f.windDir = cloudWind.f();
		f.aurora = auroraOn.on();
		f.auroraAlways = auroraAlways.on();
		f.auroraA = auroraA.c();
		f.auroraB = auroraB.c();
		f.auroraStrength = auroraStrength.f();
		f.auroraHeight = auroraHeight.f();
		f.auroraSpeed = auroraSpeed.f();
		f.auroraDir = auroraDir.f();
		f.shooting = shootingOn.on();
		f.shootingRate = shootingRate.f();
		f.haze = hazeOn.on();
		SkyLook.Params lk = lastLook;
		int skyHorizon = sourceMode.idx() == 1 && lk != null ? lk.horizon
				: SkyLook.mix(SkyLook.mix(gHorizon.c(), sHorizon.c(), daySunset), nHorizon.c(), dayNight);
		f.hazeColour = hazeAuto.on() ? skyHorizon : hazeColour.c();
		f.hazeStrength = hazeStrength.f();
		f.night = dayNight;
		f.sunset = daySunset;
		f.sunsetColour = sHorizon.c();
		f.quality = quality;
		double[] sd = sunDirNow();
		f.sx = sd[0];
		f.sy = sd[1];
		f.sz = sd[2];
		f.sunColour = lk != null && sourceMode.idx() == 1 ? lk.sunColour : sunColour.c();
		f.sunRays = raysOn.on();
		f.sunRayStrength = raysStrength.f();
		f.rainbow = rainbowOn.on();
		f.rainbowStrength = rainbowStrength.f();
		f.rainbowDir = rainbowAuto.on() ? (float) Math.toDegrees(Math.atan2(-sd[0], sd[2])) : rainbowDir.f();
	}

	/** Where the sun is now (unit vector): the generated sky's sun, or the real one from the world time. */
	private double[] sunDirNow() {
		SkyLook.Params lk = lastLook;
		if (sourceMode.idx() == 1 && lk != null) {
			return new double[] {lk.sx, lk.sy, lk.sz};
		}
		long t = mc.level == null ? 6000 : Math.floorMod(mc.level.getOverworldClockTime(), 24000L);
		double th = 2 * Math.PI * t / 24000.0;
		double ex = Math.cos(th), ey = Math.sin(th), ez = 0.22;
		double l = Math.sqrt(ex * ex + ey * ey + ez * ez);
		return new double[] {ex / l, ey / l, ez / l};
	}

	/** Lowers the amount of particles / clouds when the FPS drops under the target, raises it back when there is room. */
	private void autoPerformance(long now) {
		if (!autoPerf.on()) {
			quality = 1F;
			return;
		}
		if (now - perfCheckNs < 500_000_000L) {
			return;
		}
		perfCheckNs = now;
		int fps = mc.getFps();
		float target = targetFps.f();
		if (fps < target * 0.9F) {
			quality = Math.max(0.25F, quality - 0.1F);
		} else if (fps > target * 1.1F + 5) {
			quality = Math.min(1F, quality + 0.05F);
		}
	}

	/** A hit throws the particles around the target away. */
	public void onAttack(net.minecraft.world.entity.Entity e) {
		if (!enabled || !react.on() || e == null) {
			return;
		}
		for (Lay l : lays) {
			l.sim.burst(e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 3.5, 6.0);
		}
	}

	private final Quaternionf tmpQ = new Quaternionf();
	private final Vector3f tmpU = new Vector3f(), tmpV = new Vector3f();

	private void emitLayer(PoseStack m, SubmitNodeCollector queue, Lay l, int st, Vec3 cam, Vector3f right, Vector3f up, float t, int lt) {
		SkyFx.Params p = l.params;
		SkyFx.Layer sim = l.sim;
		int n = sim.n;
		if (l.order.length < n) {
			l.order = new int[n];
			l.dist = new float[n];
		}
		// visible particles, sorted far -> near so the see-through edges blend correctly
		int count = 0;
		for (int i = 0; i < n; i++) {
			double dx = sim.x[i] - cam.x, dy = sim.y[i] - cam.y, dz = sim.z[i] - cam.z;
			float f = SkyFx.Layer.fade(dx, dy, dz, p) * sim.vis[i];
			if (f < 0.02F) {
				continue;
			}
			l.order[count] = i;
			l.dist[count] = (float) (dx * dx + dy * dy + dz * dz);
			count++;
		}
		if (count == 0) {
			return;
		}
		sortFarFirst(l.order, l.dist, count);

		final int[] order = l.order;
		final int cnt = count;
		final boolean tumble = SkyGen.tumbles(st);
		final boolean twinkle = SkyGen.twinkles(st);
		final boolean randomCol = l.randomColours.on();
		final boolean flap = st == SkyGen.BUTTERFLY;
		final int rgb = l.colour.c() & 0xFFFFFF;
		final float op = p.opacity / 100F;
		final float base = p.size / 100F * 0.8F; // half size in blocks (size 30 = about half a block across)
		m.pushPose();
		queue.submitCustomGeometry(m, RenderTypes.entityTranslucent(l.id), (e, vc) -> {
			for (int k = 0; k < cnt; k++) {
				int i = order[k];
				float cx = (float) (sim.x[i] - cam.x), cy = (float) (sim.y[i] - cam.y), cz = (float) (sim.z[i] - cam.z);
				float a = op * SkyFx.Layer.fade(cx, cy, cz, p) * sim.vis[i];
				if (twinkle) {
					a *= sim.twinkle(i, t);
				}
				boolean landed = sim.land[i] >= 0F;
				if (landed) {
					a *= clamp01(p.stay - sim.land[i]); // fades out during its last second on the ground
				}
				int al = Math.round(clamp01(a) * 255F);
				if (al < 4) {
					continue;
				}
				float h = base * Math.max(0.5F, sim.scale[i]);
				// a close particle must not balloon over the screen: cap its size by its distance from the camera
				float dist = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
				h = Math.min(h, 0.05F + dist * 0.18F);
				Vector3f u = tmpU, v = tmpV;
				if (landed) {
					// lying flat on the ground
					float c = (float) Math.cos(sim.rot[i]) * h, s = (float) Math.sin(sim.rot[i]) * h;
					u.set(c, 0, s);
					v.set(-s, 0, c);
				} else if (tumble) {
					// flat thing that flutters: mostly faces you, spins, and tilts back and forth (but never goes edge-on,
					// so it never looks like a thin line)
					float c = (float) Math.cos(sim.rot[i]) * h, s = (float) Math.sin(sim.rot[i]) * h;
					u.set(right.x * c + up.x * s, right.y * c + up.y * s, right.z * c + up.z * s);
					v.set(up.x * c - right.x * s, up.y * c - right.y * s, up.z * c - right.z * s);
					float tilt = (float) Math.sin(t * (1.4 + 0.6 * sim.speedMul[i]) + sim.phase[i]) * 0.9F; // up to ~50 degrees
					float ct = (float) Math.cos(tilt), stl = (float) Math.sin(tilt);
					// forward = direction to the particle (from the camera)
					float fl = (float) Math.sqrt(cx * cx + cy * cy + cz * cz) + 1e-4F;
					float fx = cx / fl, fy = cy / fl, fz = cz / fl;
					float vl = v.length();
					v.set(v.x * ct + fx * vl * stl, v.y * ct + fy * vl * stl, v.z * ct + fz * vl * stl);
				} else {
					// faces the camera, spins in the screen plane
					float c = (float) Math.cos(sim.rot[i]) * h, s = (float) Math.sin(sim.rot[i]) * h;
					u.set(right.x * c + up.x * s, right.y * c + up.y * s, right.z * c + up.z * s);
					v.set(up.x * c - right.x * s, up.y * c - right.y * s, up.z * c - right.z * s);
				}
				if (flap && !landed) {
					// wings beat: squeeze the width
					float beat = 0.25F + 0.75F * Math.abs((float) Math.sin(t * 13 + sim.phase[i] * 5));
					u.mul(beat);
				}
				// normal u x v; turn the quad so its front faces the camera (works with or without back-face culling)
				float nx = u.y * v.z - u.z * v.y, ny = u.z * v.x - u.x * v.z, nz = u.x * v.y - u.y * v.x;
				if (nx * cx + ny * cy + nz * cz > 0) {
					u.negate();
					nx = -nx;
					ny = -ny;
					nz = -nz;
				}
				float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
				if (len < 1e-6F) {
					continue;
				}
				nx /= len;
				ny /= len;
				nz /= len;
				int col = al << 24 | (randomCol ? java.awt.Color.HSBtoRGB(sim.hue[i], 0.62F, 1F) & 0xFFFFFF : rgb);
				quadVertex(vc, e, cx - u.x - v.x, cy - u.y - v.y, cz - u.z - v.z, 0F, 1F, col, lt, nx, ny, nz);
				quadVertex(vc, e, cx + u.x - v.x, cy + u.y - v.y, cz + u.z - v.z, 1F, 1F, col, lt, nx, ny, nz);
				quadVertex(vc, e, cx + u.x + v.x, cy + u.y + v.y, cz + u.z + v.z, 1F, 0F, col, lt, nx, ny, nz);
				quadVertex(vc, e, cx - u.x + v.x, cy - u.y + v.y, cz - u.z + v.z, 0F, 0F, col, lt, nx, ny, nz);
			}
		});
		m.popPose();
	}

	private static void quadVertex(VertexConsumer vc, PoseStack.Pose e, float x, float y, float z, float u, float v, int col, int lt, float nx,
			float ny, float nz) {
		vc.addVertex(e, x, y, z).setColor(col).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lt).setNormal(e, nx, ny, nz);
	}

	/** Insertion sort is plenty for a few hundred mostly-already-sorted particles. */
	private static void sortFarFirst(int[] idx, float[] d, int n) {
		for (int i = 1; i < n; i++) {
			int vi = idx[i];
			float vd = d[i];
			int j = i - 1;
			while (j >= 0 && d[j] < vd) {
				idx[j + 1] = idx[j];
				d[j + 1] = d[j];
				j--;
			}
			idx[j + 1] = vi;
			d[j + 1] = vd;
		}
	}

	// ------------------------------------------------------- tint + vignette (HUD)
	/** Called every frame from the HUD while the mod is enabled. */
	public void draw(GuiGraphicsExtractor c) {
		if (mc.player == null || mc.level == null) {
			return;
		}
		int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
		// colour grading: an overlay "look" (tint + glows + vignette) at the chosen strength
		int g = grading.idx();
		float gs = gradingStrength.f() / 100F;
		float extraVig = 0F;
		if (g != 0 && gs > 0F) {
			int tintC, topC = 0, botC = 0;
			float ta, tpa = 0, ba = 0;
			switch (g) {
				case 1 -> { tintC = 0xFF9A40; ta = 0.10F; botC = 0xFF7A20; ba = 0.12F; }
				case 2 -> { tintC = 0x4A8CFF; ta = 0.10F; topC = 0x2050C0; tpa = 0.12F; }
				case 3 -> { tintC = 0xFFD6F0; ta = 0.14F; topC = 0xFFFFFF; tpa = 0.08F; }
				case 4 -> { tintC = 0x102040; ta = 0.30F; extraVig = 0.45F; }
				case 5 -> { tintC = 0x000000; ta = 0.0F; topC = 0x1E6A7A; tpa = 0.16F; botC = 0xFF8A3A; ba = 0.14F; extraVig = 0.35F; }
				case 6 -> { tintC = 0xFFB8E0; ta = 0.12F; topC = 0xFFFFFF; tpa = 0.16F; extraVig = 0.15F; }
				default -> { tintC = 0xC9A46A; ta = 0.16F; extraVig = 0.4F; }
			}
			if (ta > 0) {
				c.fill(0, 0, w, h, Gfx.withAlpha(tintC, Math.round(clamp01(ta * gs) * 255)));
			}
			if (tpa > 0) {
				c.fillGradient(0, 0, w, h / 2, Gfx.withAlpha(topC, Math.round(clamp01(tpa * gs) * 255)), Gfx.withAlpha(topC, 0));
			}
			if (ba > 0) {
				c.fillGradient(0, h / 2, w, h, Gfx.withAlpha(botC, 0), Gfx.withAlpha(botC, Math.round(clamp01(ba * gs) * 255)));
			}
			extraVig *= gs;
		}
		float ts = tintStrength.f();
		if (ts > 0F) {
			c.fill(0, 0, w, h, Gfx.withAlpha(tint.c(), Math.round(clamp01(ts / 100F) * 255F)));
		}
		float vg = Math.min(1F, vignette.f() / 100F + extraVig);
		if (vg > 0F) {
			int a = Math.round(clamp01(vg) * 200F);
			int col = Gfx.withAlpha(0x000000, a), clear = Gfx.withAlpha(0x000000, 0);
			int e = Math.max(24, Math.min(w, h) / 3);
			c.fillGradient(0, 0, w, e, col, clear);
			c.fillGradient(0, h - e, w, h, clear, col);
			Gfx.gradientH(c, 0, 0, e, h, col, clear);
			Gfx.gradientH(c, w - e, 0, e, h, clear, col);
		}
		if (letterbox.on() || g == 5 && gs > 0.5F) {
			int bar = Math.round(h * 0.09F);
			c.fill(0, 0, w, bar, 0xFF000000);
			c.fill(0, h - bar, w, h, 0xFF000000);
		}
	}

	// ------------------------------------------------------- share codes
	private static final String CODE_PREFIX = "SLEEPSKY1:";
	/** Sky codes made before the rename. */
	private static final String OLD_PREFIX = "KUBOSKY1:";

	/** Copies all the sky settings (not the photo) as one line of text to the clipboard. */
	private void copyCode() {
		try {
			com.google.gson.JsonObject o = new com.google.gson.JsonObject();
			for (Option<?> op : options) {
				if (op instanceof Option.Header || op instanceof Option.Action || op == preset || op == section || op == layerTab) {
					continue;
				}
				o.add(op.key, op.save());
			}
			byte[] raw = o.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
			java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
			try (java.util.zip.GZIPOutputStream gz = new java.util.zip.GZIPOutputStream(bos)) {
				gz.write(raw);
			}
			String code = CODE_PREFIX + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bos.toByteArray());
			mc.keyboardHandler.setClipboard(code);
			shareStatus = "Copied (" + code.length() + " characters)";
		} catch (Exception e) {
			shareStatus = "Could not copy: " + e.getClass().getSimpleName();
		}
	}

	/** Loads a sky code from the clipboard. */
	private void pasteCode() {
		try {
			String code = mc.keyboardHandler.getClipboard();
			code = code == null ? "" : code.trim();
			if (code.startsWith(OLD_PREFIX)) {
				code = CODE_PREFIX + code.substring(OLD_PREFIX.length());
			}
			if (!code.startsWith(CODE_PREFIX)) {
				shareStatus = "The clipboard has no sky code";
				return;
			}
			byte[] gz = java.util.Base64.getUrlDecoder().decode(code.substring(CODE_PREFIX.length()));
			byte[] raw;
			try (java.util.zip.GZIPInputStream in = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(gz))) {
				raw = in.readNBytes(1 << 20);
			}
			com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(new String(raw, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
			int n = 0;
			for (Option<?> op : options) {
				if (op instanceof Option.Header || op instanceof Option.Action || !o.has(op.key)) {
					continue;
				}
				try {
					op.load(o.get(op.key));
					n++;
				} catch (RuntimeException ignored) {
				}
			}
			preset.set(0);
			lastPreset = 0;
			shareStatus = "Loaded " + n + " settings";
		} catch (Exception e) {
			shareStatus = "Not a valid sky code";
		}
	}

	// ------------------------------------------------------- live preview in the settings
	private final Random previewRnd = new Random(7);
	private long previewNs;

	/** Your sky colours + your particles (2D) in a box. Used by the settings preview and the main menu background. */
	public void drawScene(GuiGraphicsExtractor c, int x, int py, int w, int h, boolean hills) {
		dev.solclient.gui.Backdrops.Anim bd = backdrop();
		if (bd != null) {
			// a real picture behind (clear sky, cherry grove, autumn, night) - no drawn terrain
			dev.solclient.gui.Backdrops.draw(c, bd, x, py, w, h);
		} else {
			// your own gradient: sky only (top -> horizon), Minecraft-style square sun, a few stars when dark
			int top, hor;
			SkyLook.Params lk = lastLook;
			if (lk != null) {
				top = lk.top;
				hor = lk.horizon;
			} else {
				top = gTop.c();
				hor = gHorizon.c();
			}
			c.fillGradient(x, py, x + w, py + h, top | 0xFF000000, hor | 0xFF000000);
			if (stars.f() > 0) {
				int lum = ((top >> 16 & 255) * 3 + (top >> 8 & 255) * 6 + (top & 255)) / 10;
				if (lum < 70) {
					java.util.Random sr = new java.util.Random(99);
					int n = Math.round(stars.f() * w / 400F);
					for (int i = 0; i < n; i++) {
						int sx2 = x + sr.nextInt(Math.max(1, w)), sy2 = py + sr.nextInt(Math.max(1, h * 3 / 4));
						c.fill(sx2, sy2, sx2 + 1, sy2 + 1, (120 + sr.nextInt(135)) << 24 | 0xFFFFFF);
					}
				}
			}
			if (sunOn.on()) {
				int sx = x + Math.round(w * (0.5F + (float) Math.sin(Math.toRadians(sunDir.f())) * 0.35F));
				int sy = py + h - 8 - Math.round((h * 0.7F) * clamp01((sunHeight.f() + 20F) / 100F));
				int sz = Math.max(6, Math.round(4 + sunSize.f() * 0.9F));
				blockySun(c, sx, sy, sz, sunColour.c() | 0xFF000000, sunGlow.f() / 100F);
			}
		}
		long now = System.nanoTime();
		float dt = previewNs == 0 ? 0 : Math.min(0.1F, (now - previewNs) / 1e9F);
		previewNs = now;
		float t = (float) ((now / 1.0e9) % 6000.0);
		c.enableScissor(x, py, x + w, py + h);
		for (Lay l : lays) {
			int st = l.style.idx();
			if (st == SkyGen.OFF || l.amount.i() <= 0 || !ensureTexture(l, st)) {
				continue;
			}
			int cnt = Math.min(l.px.length, Math.max(3, l.amount.i() / 8 * (h > 200 ? 3 : 1)));
			if (!l.previewInit) {
				for (int i = 0; i < l.px.length; i++) {
					l.px[i] = previewRnd.nextFloat();
					l.py[i] = previewRnd.nextFloat();
					l.ps[i] = 0.4F + previewRnd.nextFloat() * previewRnd.nextFloat() * 1.6F;
					l.pr[i] = previewRnd.nextFloat() * 6.28F;
				}
				l.previewInit = true;
			}
			float fall = l.speed.f() / 100F * 0.35F;
			double wr = Math.toRadians(l.windDir.f());
			float wx = (float) Math.sin(wr) * l.wind.f() / 100F * 0.3F;
			int rgb = l.colour.c() & 0xFFFFFF;
			boolean tw = SkyGen.twinkles(st), tumble = SkyGen.tumbles(st);
			var mat = c.pose();
			for (int i = 0; i < cnt; i++) {
				l.py[i] += fall * l.ps[i] * dt;
				l.px[i] += (wx + (float) Math.sin(t + i) * l.sway.f() / 100F * 0.06F) * dt;
				l.pr[i] += l.spin.f() / 100F * 2F * dt * (i % 2 == 0 ? 1 : -1);
				if (l.py[i] > 1.1F) {
					l.py[i] -= 1.2F;
					l.px[i] = previewRnd.nextFloat();
				} else if (l.py[i] < -0.1F) {
					l.py[i] += 1.2F;
					l.px[i] = previewRnd.nextFloat();
				}
				l.px[i] = l.px[i] - (float) Math.floor(l.px[i]);
				float a = l.opacity.f() / 100F * (tw ? sim2Twinkle(t, i) : 1F);
				int al = Math.round(clamp01(a) * 255F);
				int s = Math.max(2, Math.round(l.size.f() / 100F * 22F * Math.max(1F, h / 160F) * l.ps[i]));
				mat.pushMatrix();
				mat.translate(x + l.px[i] * w, py + l.py[i] * h);
				mat.rotate(l.pr[i]);
				if (tumble) {
					mat.scale(1F, 0.35F + 0.65F * Math.abs((float) Math.cos(t * 1.6 + i)));
				}
				Gfx.tex(c, l.id, -s / 2, -s / 2, s, s, SkyGen.SPRITE, SkyGen.SPRITE, al << 24 | rgb);
				mat.popMatrix();
			}
		}
		c.disableScissor();
		float ts = tintStrength.f();
		if (ts > 0F) {
			c.fill(x, py, x + w, py + h, Gfx.withAlpha(tint.c(), Math.round(clamp01(ts / 100F) * 255F)));
		}
	}

	/** Picture for the preview: the night picture on the Night tab, else one that matches the preset (null = your gradient). */
	private dev.solclient.gui.Backdrops.Anim backdrop() {
		if (section.idx() == 1) {
			return dev.solclient.gui.Backdrops.NIGHT;
		}
		return switch (preset.idx()) {
			case 1 -> dev.solclient.gui.Backdrops.SAKURA;
			case 4, 5 -> dev.solclient.gui.Backdrops.AUTUMN;
			case 6, 8, 9 -> dev.solclient.gui.Backdrops.NIGHT;
			case 0 -> sourceMode.idx() == 1 ? null : dev.solclient.gui.Backdrops.CLEAR;
			default -> dev.solclient.gui.Backdrops.CLEAR;
		};
	}

	/** Square pixel sun (like Minecraft's): a soft square glow, a darker rim and a lighter centre. */
	private static void blockySun(GuiGraphicsExtractor c, int cx, int cy, int sz, int col, float glow) {
		for (int i = 3; i >= 1; i--) {
			int g = Math.round(sz * (0.25F + 0.3F * i) * (0.4F + glow));
			c.fill(cx - sz / 2 - g, cy - sz / 2 - g, cx + sz / 2 + g, cy + sz / 2 + g, Gfx.withAlpha(col, Math.round(18 * glow / i + 6)));
		}
		int x0 = cx - sz / 2, y0 = cy - sz / 2;
		int rim = Gfx.blend(col, 0xFFE0A030, 0.45F), inner = Gfx.blend(col, 0xFFFFFFFF, 0.55F);
		c.fill(x0, y0, x0 + sz, y0 + sz, rim);
		int b = Math.max(1, sz / 8);
		c.fill(x0 + b, y0 + b, x0 + sz - b, y0 + sz - b, col);
		c.fill(x0 + b * 2, y0 + b * 2, x0 + sz - b * 2, y0 + sz - b * 2, inner);
	}

	/** Main menu background: your sky behind the menu (true when it was drawn). */
	public static boolean menuBackdrop(GuiGraphicsExtractor c, int w, int h) {
		CustomSkyMod m = instance;
		if (m == null || !m.enabled || !m.menuBackground.on()) {
			return false;
		}
		try {
			m.drawScene(c, 0, 0, w, h, false);
			return true;
		} catch (RuntimeException e) {
			return false;
		}
	}

	/** Small animated picture at the top of the settings: your sky colours + your particles. Returns its height. */
	public int preview(GuiGraphicsExtractor c, int x, int y, int w) {
		int h = 112;
		Gfx.text(c, "PREVIEW", x + 4, y + 2, 0xFF8A8F9C);
		int py = y + 14;
		drawScene(c, x, py, w, h, true);
		Gfx.outline(c, x, py, w, h, 0x40FFFFFF);
		if (!renderError.isEmpty()) {
			Gfx.text(c, "3D error: " + renderError, x + 4, py + h - 10, 0xFFFF6060);
		}
		return 14 + h;
	}

	private static float sim2Twinkle(float t, int i) {
		return 0.5F + 0.5F * (0.5F + 0.5F * (float) Math.sin(t * 2.4 + i * 1.7));
	}

	// ------------------------------------------------------------------ presets
	private static void setN(Option.Num o, float v) {
		o.set(v);
	}

	private void gradientPreset(int top, int horizon, int ground, boolean sun, int sunCol, float dir, float height, float size, float glow) {
		sourceMode.set(1);
		gTop.set(top);
		gHorizon.set(horizon);
		gGround.set(ground);
		sunOn.set(sun);
		sunColour.set(sunCol);
		setN(sunDir, dir);
		setN(sunHeight, height);
		setN(sunSize, size);
		setN(sunGlow, glow);
		hideCelestials.set(true);
		hideGlow.set(true);
		setN(brightness, 100F);
		timeOfDay.set(false); // a preset is one fixed look; turn "Follow time of day" on to animate it
	}

	/** clouds on / colour, aurora on, stars, milky way, haze strength (0 = off), shooting stars. */
	private void fxPreset(boolean clouds, int cloudCol, boolean aurora, float st, float mw, float haze, boolean shoot) {
		rainbowOn.set(false);
		raysOn.set(false);
		cloudsOn.set(clouds);
		cloudColour.set(cloudCol);
		auroraOn.set(aurora);
		auroraAlways.set(aurora);
		setN(stars, st);
		setN(milky, mw);
		hazeOn.set(haze > 0);
		hazeAuto.set(true);
		if (haze > 0) {
			setN(hazeStrength, haze);
		}
		shootingOn.set(shoot);
	}

	private void ambientPreset(int col, float strength, float vig) {
		tint.set(col);
		setN(tintStrength, strength);
		setN(vignette, vig);
	}

	/** amount, size (1/100 block), speed, sway, spin, opacity, wind, tint */
	private void layerPreset(int idx, int style, float amount, float size, float speed, float sway, float spin, float opacity, float wind, int col) {
		Lay l = lays[idx];
		l.style.set(style);
		setN(l.amount, amount);
		setN(l.size, size);
		setN(l.speed, speed);
		setN(l.sway, sway);
		setN(l.spin, spin);
		setN(l.opacity, opacity);
		setN(l.wind, wind);
		setN(l.windDir, 60F);
		setN(l.variation, 60F);
		l.colour.set(col);
		l.where.set(0);
		l.randomColours.set(false);
	}

	private void off(int idx) {
		layerPreset(idx, SkyGen.OFF, 0F, 30F, 25F, 40F, 40F, 90F, 0F, 0xFFFFFFFF);
	}

	private void applyPreset(int p) {
		if (p > 0) {
			setN(area, 24F);
		}
		switch (p) {
			case 1 -> { // Sakura: purple dusk, cherry flowers + petals everywhere
				gradientPreset(0xFF4A3778, 0xFFF39A7C, 0xFF5A3E66, true, 0xFFFFD2A8, 0F, 2F, 8F, 80F);
				ambientPreset(0xFFFF9EC8, 8F, 22F);
				fxPreset(true, 0xFFFFE0EC, false, 35F, 25F, 50F, true);
				layerPreset(0, SkyGen.BLOSSOM, 140F, 34F, 22F, 45F, 35F, 95F, 12F, 0xFFFFFFFF);
				layerPreset(1, SkyGen.PETAL, 220F, 20F, 28F, 60F, 60F, 90F, 16F, 0xFFFFFFFF);
				layerPreset(2, SkyGen.PETAL, 260F, 9F, 20F, 50F, 50F, 70F, 10F, 0xFFFFC4D8);
				glowing.set(true);
			}
			case 2 -> { // Sunflower: red-orange sunset, rising embers
				gradientPreset(0xFF7A1F10, 0xFFFFB347, 0xFF8A3A1A, true, 0xFFFFF2B0, 0F, 5F, 7F, 90F);
				ambientPreset(0xFFFF8A3D, 12F, 30F);
				fxPreset(true, 0xFFFFD2A0, false, 10F, 0F, 55F, false);
				layerPreset(0, SkyGen.GLOW, 120F, 18F, -10F, 40F, 0F, 85F, 4F, 0xFFFFFFFF);
				layerPreset(1, SkyGen.SPARK, 70F, 14F, -18F, 35F, 20F, 80F, 6F, 0xFFFFFFFF);
				off(2);
				glowing.set(true);
			}
			case 3 -> { // Snowdrop: white haze and snow
				gradientPreset(0xFFB5C3D6, 0xFFE8EEF4, 0xFFDDE6EE, false, 0xFFFFFFFF, 0F, 20F, 6F, 40F);
				ambientPreset(0xFFDCEBFF, 18F, 20F);
				fxPreset(true, 0xFFFFFFFF, false, 0F, 0F, 60F, false);
				layerPreset(0, SkyGen.SNOW, 320F, 12F, 22F, 35F, 20F, 90F, 15F, 0xFFFFFFFF);
				layerPreset(1, SkyGen.SNOW, 120F, 24F, 30F, 40F, 25F, 80F, 20F, 0xFFFFFFFF);
				off(2);
				glowing.set(true);
			}
			case 4 -> { // Ember dusk: dark red sky, sparks rising
				gradientPreset(0xFF1A0F24, 0xFFB04A2A, 0xFF2A1410, true, 0xFFFF7A3A, 90F, 2F, 10F, 60F);
				ambientPreset(0xFFFF5A1F, 10F, 45F);
				fxPreset(false, 0xFFFFFFFF, false, 45F, 30F, 45F, true);
				layerPreset(0, SkyGen.SPARK, 160F, 12F, -22F, 30F, 30F, 90F, 8F, 0xFFFF7A2A);
				layerPreset(1, SkyGen.GLOW, 80F, 16F, -10F, 40F, 0F, 70F, 5F, 0xFFFF9A55);
				off(2);
				glowing.set(true);
			}
			case 5 -> { // Autumn: warm afternoon, maple leaves
				gradientPreset(0xFF4F78B8, 0xFFF5C88A, 0xFF6B5240, true, 0xFFFFE6B0, 220F, 18F, 6F, 60F);
				ambientPreset(0xFFFFB060, 8F, 20F);
				fxPreset(true, 0xFFFFF0E0, false, 0F, 0F, 40F, false);
				layerPreset(0, SkyGen.AUTUMN, 110F, 36F, 26F, 55F, 55F, 95F, 30F, 0xFFFFFFFF);
				layerPreset(1, SkyGen.LEAF, 90F, 26F, 30F, 55F, 60F, 90F, 30F, 0xFFFFB45A);
				off(2);
				glowing.set(false);
			}
			case 6 -> { // Firefly night: deep blue night, slow golden lights
				gradientPreset(0xFF070B1E, 0xFF22305A, 0xFF0A0F1A, true, 0xFFDDE6FF, 140F, 35F, 4F, 30F);
				ambientPreset(0xFF203060, 10F, 40F);
				fxPreset(true, 0xFF8090C0, true, 85F, 80F, 30F, true);
				layerPreset(0, SkyGen.GLOW, 160F, 14F, -2F, 70F, 0F, 95F, 0F, 0xFFFFE27A);
				layerPreset(1, SkyGen.STAR, 60F, 10F, 0F, 30F, 20F, 80F, 0F, 0xFFFFFFFF);
				off(2);
				glowing.set(true);
			}
			case 7 -> { // Love: pink sky, hearts and petals
				gradientPreset(0xFFB04A8A, 0xFFFFB3C8, 0xFF8A3A6A, true, 0xFFFFF0F4, 0F, 10F, 7F, 70F);
				ambientPreset(0xFFFF7AAE, 10F, 25F);
				fxPreset(true, 0xFFFFD0E4, false, 30F, 20F, 50F, true);
				layerPreset(0, SkyGen.HEART, 90F, 30F, -12F, 45F, 25F, 95F, 6F, 0xFFFFFFFF);
				layerPreset(1, SkyGen.PETAL, 160F, 16F, 24F, 55F, 55F, 85F, 12F, 0xFFFFFFFF);
				off(2);
				glowing.set(true);
			}
			case 8 -> { // Starfall: night sky, falling stars
				gradientPreset(0xFF0B0820, 0xFF3A2A6A, 0xFF120E24, false, 0xFFFFFFFF, 0F, 20F, 6F, 40F);
				ambientPreset(0xFF6A5AFF, 8F, 35F);
				fxPreset(false, 0xFFFFFFFF, true, 95F, 90F, 25F, true);
				layerPreset(0, SkyGen.STAR, 140F, 18F, 30F, 20F, 40F, 95F, 10F, 0xFFFFF4C8);
				layerPreset(1, SkyGen.GLOW, 120F, 8F, 8F, 30F, 0F, 80F, 0F, 0xFFB8C8FF);
				off(2);
				glowing.set(true);
			}
			case 9 -> { // Party: night, confetti in every colour, aurora
				gradientPreset(0xFF140C2E, 0xFF5A2A8A, 0xFF1A1030, false, 0xFFFFFFFF, 0F, 20F, 6F, 40F);
				ambientPreset(0xFFB050FF, 6F, 30F);
				fxPreset(false, 0xFFFFFFFF, true, 70F, 50F, 30F, true);
				layerPreset(0, SkyGen.CONFETTI, 220F, 16F, 26F, 50F, 80F, 95F, 6F, 0xFFFFFFFF);
				lays[0].randomColours.set(true);
				layerPreset(1, SkyGen.SPARK, 60F, 12F, -10F, 30F, 20F, 85F, 0F, 0xFFFFFFFF);
				lays[1].randomColours.set(true);
				off(2);
				glowing.set(true);
			}
			case 10 -> { // Spring garden: blue morning, butterflies near the ground, petals, rainbow
				gradientPreset(0xFF4A86E0, 0xFFCDE6FF, 0xFF7FA0C0, true, 0xFFFFF4D0, 120F, 30F, 6F, 60F);
				ambientPreset(0xFFFFF0C0, 4F, 12F);
				fxPreset(true, 0xFFFFFFFF, false, 0F, 0F, 35F, false);
				rainbowOn.set(true);
				raysOn.set(true);
				layerPreset(0, SkyGen.BUTTERFLY, 40F, 26F, 0F, 90F, 10F, 100F, 0F, 0xFFFFFFFF);
				lays[0].where.set(1);
				lays[0].randomColours.set(true);
				layerPreset(1, SkyGen.BLOSSOM, 70F, 26F, 18F, 50F, 40F, 95F, 10F, 0xFFFFFFFF);
				layerPreset(2, SkyGen.PETAL, 120F, 14F, 22F, 55F, 55F, 85F, 12F, 0xFFFFFFFF);
				glowing.set(false);
			}
			case 11 -> { // Dreamy bubbles: pastel sky, bubbles floating up
				gradientPreset(0xFF7FB8FF, 0xFFFFD6F0, 0xFFB09CD8, true, 0xFFFFFFFF, 40F, 25F, 5F, 50F);
				ambientPreset(0xFFFFD6F0, 8F, 15F);
				fxPreset(true, 0xFFFFF0FA, false, 20F, 10F, 45F, false);
				layerPreset(0, SkyGen.BUBBLE, 120F, 30F, -14F, 60F, 0F, 90F, 4F, 0xFFFFFFFF);
				layerPreset(1, SkyGen.GLOW, 60F, 10F, -6F, 50F, 0F, 70F, 0F, 0xFFFFE0F4);
				off(2);
				glowing.set(true);
			}
			default -> {
			}
		}
	}

	// ----------------------------------------------------------- mixin hooks
	private boolean useSky() {
		int mode = sourceMode.idx();
		return enabled && texture != null && mode != 2 && bakedMode == mode;
	}

	/** True when the photo / gradient should replace the vanilla sky. */
	public static boolean active() {
		return instance != null && instance.useSky();
	}

	/** Render thread: makes sure the vertex buffer matches the brightness option. */
	public static boolean prepare() {
		CustomSkyMod m = instance;
		if (m == null || !m.useSky()) {
			return false;
		}
		int b = m.brightness.i();
		if (m.buffer == null || b != m.bufferBrightness || m.bufferFace != m.bakedFace) {
			try {
				m.rebuildBuffer(b);
			} catch (Throwable e) {
				m.status = "Error: " + e.getClass().getSimpleName();
				return false;
			}
		}
		return m.buffer != null;
	}

	public static DynamicTexture texture() {
		return instance.texture;
	}

	public static GpuBuffer buffer() {
		return instance.buffer;
	}

	public static boolean hideCelestials() {
		return active() && instance.hideCelestials.on();
	}

	public static boolean hideGlow() {
		return active() && instance.hideGlow.on();
	}
}
