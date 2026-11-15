package dev.solclient.gui;

import dev.solclient.Config;
import dev.solclient.SolClient;
import dev.solclient.mods.*;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ParticleStatus;

/**
 * The choices of the first-time setup, so the same thing happens when they are changed later in Sleep Settings
 * (Setup: Performance Preset, HUD Preset; Theme & Colours: Theme, UI Transparency).
 */
public final class Presets {

	public static final String[] PERF = {"Performance", "Balanced", "Quality"};
	public static final String[] HUD = {"Minimal", "PvP", "Everything"};
	public static final String[] THEMES = {"Sleep (Original)", "Eclipse", "Graphite", "AMOLED"};

	/** accent, background, card, border, field, text, dim text, toggle on, toggle on highlight, toggle off, danger, button */
	private static final int[][] THEME_COLOURS = {
			null, // the original look: every colour back to its default
			{0xFF6B7385, 0xFF0F0F11, 0xFF1C1C20, 0xFF2C2C31, 0xFF0B0B0D, 0xFFF4F4F6, 0xFF8E8E96, 0xFF2F6F4A, 0xFF86E7AE, 0xFF26262B, 0xFFE5484D, 0xFF2A2A2F},
			{0xFFF0B44C, 0xFF17181B, 0xFF202125, 0xFF33353B, 0xFF111215, 0xFFF2F2F0, 0xFF94949A, 0xFF2D6A3E, 0xFF9BF0AE, 0xFF26272C, 0xFFE5534B, 0xFF34363D},
			{0xFF4DD0E1, 0xFF000000, 0xFF0A0A0C, 0xFF1C1C20, 0xFF000000, 0xFFFFFFFF, 0xFF8A8A94, 0xFF0F5A3A, 0xFF7CFFC4, 0xFF141416, 0xFFFF4D4D, 0xFF1A1A1E},
	};
	/** background / card transparency (%) that comes with each theme: Eclipse is the see-through one */
	private static final int[][] THEME_GLASS = {{0, 0}, {20, 30}, {0, 0}, {0, 0}};

	private Presets() {
	}

	/** The 3 colours shown next to a theme's name in the setup: background, card, accent. */
	public static int[] swatch(int theme) {
		if (THEME_COLOURS[theme] == null) {
			return new int[] {Theme.presetColour(0, 1), Theme.presetColour(0, 2), Theme.presetColour(0, 0)};
		}
		int[] t = THEME_COLOURS[theme];
		return new int[] {t[1], t[2], t[0]};
	}

	public static void theme(int i) {
		SolClientMod s = SolClient.settings;
		if (s == null || i < 0 || i >= THEMES.length) {
			return;
		}
		dev.solclient.option.Option.Col[] cols = {s.uiColour, s.bgColour, s.cardColour, s.borderColour, s.fieldColour, s.textColour, s.dimColour,
				s.onColour, s.onGlowColour, s.offColour, s.dangerColour, s.buttonColour};
		int[] t = THEME_COLOURS[i];
		for (int k = 0; k < cols.length; k++) {
			if (t == null) {
				cols[k].reset();
			} else {
				cols[k].set(t[k]);
			}
		}
		s.bgTransparency.set((float) THEME_GLASS[i][0]);
		s.cardTransparency.set((float) THEME_GLASS[i][1]);
		Theme.refresh();
		Config.save();
	}

	private static void set(Class<? extends Mod> cls, boolean on) {
		for (Mod m : SolClient.MODS) {
			if (cls.isInstance(m) && !m.locked && m.enabled != on) {
				m.setEnabled(on);
			}
		}
	}

	/** 0 Performance (most FPS), 1 Balanced, 2 Quality. */
	public static void performance(int perf) {
		SolClientMod s = SolClient.settings;
		boolean fast = perf == 0, quality = perf == 2;
		for (Mod m : SolClient.MODS) {
			if (m instanceof PerformanceMod p) {
				if (!p.enabled) {
					p.setEnabled(true);
				}
				p.limitUnfocused.set(true);
				p.limitAfk.set(fast);
				p.cosmeticDistance.set(fast ? 16F : quality ? 64F : 32F);
			}
		}
		set(CapePhysicsMod.class, !fast);
		set(ItemPhysicsMod.class, !fast);
		set(MenuBlurMod.class, quality);
		if (fast) {
			set(CustomSkyMod.class, false);
		}
		if (s != null) {
			s.buttonAnimations.set(!fast);
			s.homeShowcase.set(!fast);
		}
		if (fast) {
			try {
				var o = Minecraft.getInstance().options;
				o.renderDistance().set(Math.min(o.renderDistance().get(), 8));
				o.simulationDistance().set(Math.min(o.simulationDistance().get(), 8));
				o.entityShadows().set(false);
				o.particles().set(ParticleStatus.DECREASED);
				// max FPS: uncapped, no vsync, no clouds, no biome blending, no mipmaps, nearer entities
				o.framerateLimit().set(260);
				o.enableVsync().set(false);
				o.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);
				o.biomeBlendRadius().set(0);
				o.mipmapLevels().set(0);
				o.entityDistanceScaling().set(0.75D);
				o.save();
			} catch (Throwable t) {
				System.err.println("[Sleep] performance preset (video settings): " + t);
			}
		}
		Config.save();
	}

	/** 0 Minimal, 1 PvP, 2 Everything. */
	public static void hud(int hud) {
		List<Class<? extends Mod>> all = List.of(FpsMod.class, CpsMod.class, PingMod.class, CoordinatesMod.class, KeystrokesMod.class, EquipmentMod.class,
				PotionEffectsMod.class, ComboCounterMod.class, ReachDisplayMod.class, ClockMod.class, BiomeMod.class);
		List<Class<? extends Mod>> want = switch (hud) {
			case 0 -> List.of(FpsMod.class, EquipmentMod.class);
			case 2 -> all;
			default -> List.of(FpsMod.class, CpsMod.class, PingMod.class, KeystrokesMod.class, EquipmentMod.class, PotionEffectsMod.class,
					ComboCounterMod.class, ReachDisplayMod.class);
		};
		for (Class<? extends Mod> cls : all) {
			set(cls, want.contains(cls));
		}
		Config.save();
	}
}
