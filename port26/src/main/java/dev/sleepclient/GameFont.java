package dev.sleepclient;

import net.minecraft.resources.Identifier;

/**
 * Settings > Font > "Whole Game Font". Holds which Sleep font replaces the vanilla font everywhere in the game
 * (see FontManagerMixin). Index 0 = Off (normal Minecraft font).
 */
public final class GameFont {

	/** The font that replaces minecraft:default, or null for the normal one. Read on the render thread. */
	public static volatile Identifier target;

	/** "Off" followed by every Sleep font except "Minecraft" (that one is the vanilla font itself). */
	public static final String[] NAMES;
	private static final String[] IDS;

	static {
		int n = Gfx.FONT_NAMES.length;
		NAMES = new String[n];
		IDS = new String[n];
		NAMES[0] = "Off";
		int k = 1;
		for (int i = 0; i < n; i++) {
			if (Gfx.FONT_IDS[i].equals("minecraft:default")) {
				continue;
			}
			NAMES[k] = Gfx.FONT_NAMES[i];
			IDS[k] = Gfx.FONT_IDS[i];
			k++;
		}
	}

	private GameFont() {
	}

	/** Cheap, called every tick: follows the setting. "Custom" is only used while an imported font is installed. */
	public static void sync(int idx) {
		Identifier id = null;
		if (idx > 0 && idx < IDS.length && IDS[idx] != null) {
			String s = IDS[idx];
			if (!s.equals(CustomFont.FONT_ID) || CustomFont.installed()) {
				id = Identifier.tryParse(s);
			}
		}
		if (id != target) {
			target = id;
		}
	}
}
