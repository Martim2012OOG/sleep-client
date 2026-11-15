package dev.solclient.music;

import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

/**
 * The fonts you can pick for the lyrics and the now playing card. The order matters (saved by position): 0 is the default
 * rounded font, 1 the game font, 2 the font you imported, the rest are bundled ({@code assets/sol_client/font/lf_*.json}).
 * Most of them are the project's own UI fonts made bolder so they read well floating in the sky.
 */
public final class LyricFonts {

	/** id = font json (sol_client:lf_&lt;id&gt;), script = handwriting-like (wider word gaps, no hard shadow). */
	private record F(String id, String name, boolean script) {
	}

	private static final F[] LIST = {
			new F("rounded", "Rounded (default)", false),
			new F(null, "Minecraft", false),
			new F(null, "My font", false),
			new F("lyrics", "Handwriting", true),
			new F("fredoka", "Fredoka", false),
			new F("nunito", "Nunito", false),
			new F("comfortaa", "Comfortaa", false),
			new F("baloo", "Baloo", false),
			new F("outfit", "Outfit", false),
			new F("rubik", "Rubik", false),
			new F("poppins", "Poppins", false),
			new F("lexend", "Lexend", false),
			new F("inter", "Inter Black", false),
			new F("carlito", "Carlito", false),
			new F("adventor", "Adventor", false),
			new F("bonum", "Bonum Serif", false),
			new F("lora", "Lora Serif", false),
			new F("chorus", "Chorus Script", true),
			new F("jetbrains", "JetBrains Mono", false),
			new F("pixel", "Press Start", false),
			new F("silk", "Silkscreen", false),
	};

	private LyricFonts() {
	}

	public static String[] names() {
		String[] n = new String[LIST.length];
		for (int i = 0; i < n.length; i++) {
			n[i] = LIST[i].name();
		}
		return n;
	}

	/** Sets the font of a lyrics config. */
	public static void apply(LyricsView.Cfg k, int idx) {
		int i = Math.max(0, Math.min(LIST.length - 1, idx));
		F f = LIST[i];
		k.script = f.script();
		if (i == 1) {
			k.fontOverride = null; // the game font, in italics (as before)
		} else if (i == 2) {
			k.fontOverride = dev.solclient.CustomFont.installed() ? new FontDescription.Resource(Identifier.fromNamespaceAndPath("sol_client", "ui_custom")) : null;
		} else {
			k.fontOverride = new FontDescription.Resource(Identifier.fromNamespaceAndPath("sol_client", f.id().equals("lyrics") ? "lyrics" : "lf_" + f.id()));
		}
	}
}
