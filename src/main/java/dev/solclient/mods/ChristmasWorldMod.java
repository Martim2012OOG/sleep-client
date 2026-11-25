package dev.solclient.mods;

import dev.solclient.option.Option;
import java.util.Random;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;

/**
 * Christmas World: a winter world on your screen only. Real Minecraft snow falls in every biome under a grey winter
 * sky, grass and leaves turn frosty white, and extra flakes drift around you. Nothing changes in the world itself.
 */
public class ChristmasWorldMod extends Mod {

	public static ChristmasWorldMod instance;

	private final Option.Bool snow, frostOn, underRoofs, music, textures;
	private final Option.Num frostAmount, flakes, musicVol, snowAmount;
	private final Option.Choice flakeStyle;
	/** Whether the Christmas texture packs were last turned on (null = not applied yet in this session). */
	private Boolean packsApplied;
	private final Random rnd = new Random();
	/** The frost state the chunks were last built with (they are rebuilt when it changes). */
	private int builtFrost = -1;

	public ChristmasWorldMod() {
		super("Christmas World", "christmas_world", "Snow everywhere, a grey winter sky and frosty grass (only on your screen).", Category.VISUAL);
		instance = this;
		header("Snow");
		snow = bool("Snowfall", true);
		snow.tip("Real Minecraft snow in every biome, with the grey winter sky.");
		snowAmount = num("Snowfall amount", 60, 10, 100, 5, "%");
		snowAmount.tip("Less = fewer snowflakes falling (and a lighter sky).");
		snowAmount.showIf(snow::on);
		frostOn = bool("Frosty grass and leaves", true);
		frostAmount = num("Frost", 75, 20, 100, 5, "%");
		frostAmount.showIf(frostOn::on);
		flakeStyle = choice("Extra flakes", 1, "Off", "Small", "Big");
		flakeStyle.tip("Flakes floating around you, on top of the snowfall.");
		flakes = num("Flake amount", 1F, 0.1F, 20F, 0.1F, "");
		flakes.showIf(() -> flakeStyle.idx() != 0);
		underRoofs = bool("Flakes under roofs too", false);
		underRoofs.showIf(() -> flakeStyle.idx() != 0);

		header("Music");
		music = bool("Christmas music", true);
		music.tip("Christmas songs instead of Minecraft's music while you play.");
		musicVol = num("Music volume", 100, 0, 200, 5, "%");
		musicVol.tip("On top of Minecraft's Music volume.");
		musicVol.showIf(music::on);
		action("Songs", "Choose", () -> mc.setScreen(new dev.solclient.gui.XmasPickerScreen(mc.currentScreen, false)),
				() -> dev.solclient.gui.XmasChoices.songs().stream().filter(dev.solclient.gui.XmasChoices::songOn).count() + " / "
						+ dev.solclient.gui.XmasChoices.songs().size()).showIf(music::on);

		header("Texture packs");
		textures = bool("Christmas texture packs", true);
		textures.tip("Your Christmas resource packs turn on with the mod (and off again without it).");
		action("Packs", "Choose", () -> mc.setScreen(new dev.solclient.gui.XmasPickerScreen(mc.currentScreen, true)),
				() -> dev.solclient.gui.XmasChoices.packs().stream().filter(dev.solclient.gui.XmasChoices::packOn).count() + " / "
						+ dev.solclient.gui.XmasChoices.packs().size()).showIf(textures::on);
		action("Santa hat", "How?", () -> dev.solclient.gui.Notify.show(dev.solclient.gui.Notify.Kind.INFO,
				dev.solclient.Lang.tr("Santa hat", "Gorro de Natal"),
				dev.solclient.Lang.tr("Anvil: rename a carved pumpkin to \"santa hat\", then wear it", "Bigorna: muda o nome de uma ab\u00f3bora esculpida para \"santa hat\" e p\u00f5e-na na cabe\u00e7a")),
				() -> dev.solclient.Lang.tr("anvil: carved pumpkin -> \"santa hat\"", "bigorna: ab\u00f3bora -> \"santa hat\"")).showIf(textures::on);
	}

	public static float musicVolume() {
		return instance == null ? 1F : instance.musicVol.f() / 100F;
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}

	public static boolean music() {
		return instance != null && instance.enabled && instance.music.on();
	}

	/** How much it snows (0.1..1): Minecraft draws fewer flakes when the "rain" is weaker. */
	public static float snowAmount() {
		return instance == null ? 1F : instance.snowAmount.f() / 100F;
	}

	public static boolean snowing() {
		return instance != null && instance.enabled && instance.snow.on();
	}

	/** The tint of grass / leaves with the frost mixed in. */
	public static int frost(int rgb) {
		ChristmasWorldMod m = instance;
		if (m == null || !m.enabled || !m.frostOn.on()) {
			return rgb;
		}
		float k = m.frostAmount.f() / 100F;
		int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
		int fr = 0xE6, fg = 0xEE, fb = 0xF2; // icy white with a hint of blue
		return (rgb & 0xFF000000) | Math.round(r + (fr - r) * k) << 16 | Math.round(g + (fg - g) * k) << 8 | Math.round(b + (fb - b) * k);
	}

	private int frostKey() {
		return enabled && frostOn.on() ? Math.round(frostAmount.f()) : 0;
	}

	@Override
	protected void onDisable() {
		rebuildIfNeeded();
		packsApplied = null;
		dev.solclient.gui.XmasChoices.apply(false); // the Christmas packs Caos turned on go off again
	}

	private void rebuildIfNeeded() {
		int key = frostKey();
		if (key != builtFrost) {
			boolean first = builtFrost == -1;
			builtFrost = key;
			if (!first && mc.worldRenderer != null && mc.world != null) {
				mc.worldRenderer.reload(); // the chunks keep their colours until they are built again
			}
		}
	}

	@Override
	public void tick() {
		rebuildIfNeeded();
		boolean wantPacks = textures.on();
		if ((packsApplied == null || packsApplied != wantPacks) && mc.getOverlay() == null) {
			packsApplied = wantPacks;
			dev.solclient.gui.XmasChoices.apply(wantPacks);
		}
		var w = mc.world;
		var p = mc.player;
		if (w == null || p == null || flakeStyle.idx() == 0) {
			return;
		}
		// fractions too: 0.3 = one flake in about three ticks
		float want = flakes.f();
		int n = (int) want + (rnd.nextFloat() < want - (int) want ? 1 : 0);
		var type = flakeStyle.idx() == 1 ? ParticleTypes.WHITE_ASH : ParticleTypes.SNOWFLAKE;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int i = 0; i < n; i++) {
			// around your eyes, so they are seen (higher up they melt away before they come into view)
			double x = p.getX() + (rnd.nextDouble() * 2 - 1) * 10, z = p.getZ() + (rnd.nextDouble() * 2 - 1) * 10;
			double y = p.getEyeY() - 1 + rnd.nextDouble() * 6;
			pos.set(x, y, z);
			if (!underRoofs.on() && !w.isSkyVisible(pos)) {
				continue;
			}
			w.addParticleClient(type, x, y, z, (rnd.nextDouble() - 0.5) * 0.03, -0.02 - rnd.nextDouble() * 0.03,
					(rnd.nextDouble() - 0.5) * 0.03);
		}
	}
}
