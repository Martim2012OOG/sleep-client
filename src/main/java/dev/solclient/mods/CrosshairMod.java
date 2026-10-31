package dev.solclient.mods;

import dev.solclient.Gfx;
import dev.solclient.option.Option;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.GameMode;

/**
 * Custom crosshair, Lunar-style:
 *  - Simple: shape, gap, thickness, length, centre dot, outline;
 *  - Presets: pick one of the ready crosshairs (it is also copied into Custom so you can edit it);
 *  - Custom: draw your own on a 15x15 grid (left click draws, right click erases).
 * Colour: Vanilla Blending (inverts like vanilla), Static (one colour) or Dynamic (other colour on players / mobs).
 * The crosshair is baked into a small texture, so blending works exactly like the vanilla one.
 */
public class CrosshairMod extends Mod {

	public static CrosshairMod instance;
	public static final int GRID = 15;
	private static final int TEX = 64;
	private static final Identifier TEX_ID = Gfx.id("plegumo_crosshair");

	public final Option.Choice mode;
	// simple
	public final Option.Choice shape;
	public final Option.Num gap;
	public final Option.Num thickness;
	public final Option.Num length;
	public final Option.Bool dot;
	public final Option.Bool outline;
	// presets / custom
	public final Option.Choice preset;
	public final Option.Grid pixels;
	// colour
	public final Option.Choice colourMode;
	public final Option.Col colour;
	public final Option.Col entityColour;
	public final Option.Col outlineColour;
	// general
	public final Option.Num scale;
	public final Option.Bool thirdPerson;
	public final Option.Bool spectator;
	/** Draws with plain rectangles instead of a texture: works on every graphics card / system (default on Windows). */
	public final Option.Bool compat;
	/** The texture path failed once (error): from then on only rectangles are used. */
	private boolean textureFailed;

	private NativeImageBackedTexture texture;
	private String bakedKey = "";
	private int bakedSize;

	public CrosshairMod() {
		super("Crosshair", "crosshair", "Replace the default Minecraft crosshair with a customizable crosshair.", Category.HUD);
		instance = this;
		header("General Options");
		mode = tabs("Mode", 0, "Simple", "Presets", "Custom");
		header("Shape").showIf(this::simple);
		shape = choice("Shape", 0, "Cross", "Circle", "Square", "X", "T", "Dot").showIf(this::simple);
		gap = num("Gap", 2, 0, 10, 1, "").showIf(this::simple);
		thickness = num("Thickness", 1, 1, 5, 1, "").showIf(this::simple);
		length = num("Width", 4, 1, 15, 1, "").showIf(this::simple);
		dot = bool("Centre Dot", false).showIf(this::simple);
		outline = bool("Outline", false).showIf(() -> colourModeIdx() != 0);
		preset = presetChoice().showIf(() -> mode.idx() == 1);
		pixels = grid("Custom Crosshair", GRID, PRESETS[0]).showIf(() -> mode.idx() == 2);
		header("Colour Options");
		colourMode = tabs("Colour Mode", 0, "Vanilla Blending", "Static", "Dynamic");
		colour = colour("Colour", 0xFFFFFFFF).showIf(() -> colourModeIdx() != 0);
		entityColour = colour("Colour On Players / Mobs", 0xFFFF4D4D).showIf(() -> colourModeIdx() == 2);
		outlineColour = colour("Outline Colour", 0xFF000000).showIf(() -> colourModeIdx() != 0 && outline.on());
		header("Display");
		scale = num("Scale", 100, 50, 300, 5, "%");
		thirdPerson = bool("Show In Third Person", false);
		spectator = bool("Show In Spectator", false);
		compat = bool("Compatibility Mode", System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win"));
	}

	private Option.Choice presetChoice() {
		String[] names = new String[PRESETS.length];
		for (int i = 0; i < names.length; i++) {
			names[i] = "Preset " + (i + 1);
		}
		Option.Choice c = choice("Preset", 0, names);
		return c;
	}

	private boolean simple() {
		return mode.idx() == 0;
	}

	private int colourModeIdx() {
		return colourMode == null ? 0 : colourMode.idx();
	}

	public static boolean replacesVanilla() {
		return instance != null && instance.enabled;
	}

	/** Called by the settings panel when a preset is picked: also loads it into the custom grid. */
	public void pickPreset(int i) {
		preset.set(i);
		String p = PRESETS[Math.max(0, Math.min(PRESETS.length - 1, i))];
		pixels.load(new com.google.gson.JsonPrimitive(p));
	}

	// ------------------------------------------------------------------ pixels
	/** The crosshair as a square pixel mask (odd size, centred). */
	public boolean[][] mask() {
		if (mode.idx() == 1) {
			return fromString(PRESETS[Math.max(0, Math.min(PRESETS.length - 1, preset.idx()))]);
		}
		if (mode.idx() == 2) {
			return fromString(pixels.get());
		}
		int g = Math.round(gap.f()), t = Math.round(thickness.f()), l = Math.round(length.f());
		int r = g + l + t;
		int n = Math.min(TEX - 4, 2 * r + 1);
		int c = n / 2;
		boolean[][] m = new boolean[n][n];
		int half = (t - 1) / 2;
		switch (shape.idx()) {
			case 0, 4 -> { // cross / T
				for (int i = g + 1; i <= g + l && c + i < n; i++) {
					for (int k = -half; k <= t - 1 - half; k++) {
						set(m, c + i, c + k);
						set(m, c - i, c + k);
						set(m, c + k, c + i);
						if (shape.idx() == 0) {
							set(m, c + k, c - i);
						}
					}
				}
			}
			case 1 -> { // circle
				double rad = g + l / 2.0;
				for (int y = 0; y < n; y++) {
					for (int x = 0; x < n; x++) {
						double d = Math.hypot(x - c, y - c);
						if (Math.abs(d - rad) <= t / 2.0 + 0.25) {
							m[y][x] = true;
						}
					}
				}
			}
			case 2 -> { // square
				int e = Math.min(c, g + l / 2 + 1);
				for (int i = -e; i <= e; i++) {
					for (int k = 0; k < t; k++) {
						set(m, c + i, c - e + k);
						set(m, c + i, c + e - k);
						set(m, c - e + k, c + i);
						set(m, c + e - k, c + i);
					}
				}
			}
			case 3 -> { // X
				for (int i = g + 1; i <= g + l; i++) {
					for (int k = -half; k <= t - 1 - half; k++) {
						set(m, c + i + k, c + i);
						set(m, c - i + k, c + i);
						set(m, c + i + k, c - i);
						set(m, c - i + k, c - i);
					}
				}
			}
			default -> {
			}
		}
		if (dot.on() || shape.idx() == 5) {
			for (int y = -half; y <= t - 1 - half; y++) {
				for (int x = -half; x <= t - 1 - half; x++) {
					set(m, c + x, c + y);
				}
			}
		}
		return m;
	}

	private static void set(boolean[][] m, int x, int y) {
		if (y >= 0 && y < m.length && x >= 0 && x < m.length) {
			m[y][x] = true;
		}
	}

	public static boolean[][] fromString(String s) {
		boolean[][] m = new boolean[GRID][GRID];
		for (int i = 0; i < GRID * GRID && i < s.length(); i++) {
			m[i / GRID][i % GRID] = s.charAt(i) == '1';
		}
		return m;
	}

	/** Bakes the mask (+ outline) into the texture when something changed. Render thread. */
	private boolean bake() {
		boolean[][] m = mask();
		boolean ol = outline.on() && colourModeIdx() != 0;
		StringBuilder key = new StringBuilder(m.length * m.length + 8);
		for (boolean[] row : m) {
			for (boolean b : row) {
				key.append(b ? '1' : '0');
			}
		}
		key.append(ol).append(outlineColour.c());
		String k = key.toString();
		if (texture != null && k.equals(bakedKey)) {
			return true;
		}
		try {
			if (texture == null) {
				texture = new NativeImageBackedTexture(() -> "plegumo_crosshair", TEX, TEX, false);
				mc.getTextureManager().registerTexture(TEX_ID, texture);
			}
			NativeImage img = texture.getImage();
			if (img == null) {
				return false;
			}
			for (int y = 0; y < TEX; y++) {
				for (int x = 0; x < TEX; x++) {
					img.setColorArgb(x, y, 0);
				}
			}
			int n = m.length, off = (TEX - n) / 2;
			if (ol) {
				int oc = outlineColour.c() | 0xFF000000;
				for (int y = 0; y < n; y++) {
					for (int x = 0; x < n; x++) {
						if (!m[y][x]) {
							continue;
						}
						for (int dy = -1; dy <= 1; dy++) {
							for (int dx = -1; dx <= 1; dx++) {
								int px = off + x + dx, py = off + y + dy;
								if (px >= 0 && py >= 0 && px < TEX && py < TEX) {
									img.setColorArgb(px, py, oc);
								}
							}
						}
					}
				}
			}
			for (int y = 0; y < n; y++) {
				for (int x = 0; x < n; x++) {
					if (m[y][x]) {
						img.setColorArgb(off + x, off + y, 0xFFFFFFFF);
					}
				}
			}
			texture.upload();
			bakedKey = k;
			bakedSize = n;
			return true;
		} catch (Throwable e) {
			System.err.println("[Sleep] crosshair: " + e);
			return false;
		}
	}

	/** Draws the crosshair centred on (cx, cy). Also used by the settings preview. */
	public void drawAt(DrawContext c, float cx, float cy, float s, boolean targeting, boolean blendAllowed) {
		int col;
		if (colourModeIdx() == 0) {
			col = 0xFFFFFFFF;
		} else if (colourModeIdx() == 2 && targeting) {
			col = entityColour.c();
		} else {
			col = colour.c();
		}
		if (!compat.on() && !textureFailed) {
			try {
				if (drawTextured(c, cx, cy, s, col, colourModeIdx() == 0 && blendAllowed)) {
					return;
				}
			} catch (Throwable t) {
				System.err.println("[Sleep] crosshair texture failed, drawing it with rectangles instead: " + t);
			}
			textureFailed = true;
		}
		drawRects(c, cx, cy, s, col);
	}

	private boolean drawTextured(DrawContext c, float cx, float cy, float s, int col, boolean blend) {
		if (!bake()) {
			return false;
		}
		int n = bakedSize + 2; // include the outline ring
		int off = (TEX - n) / 2;
		c.getMatrices().pushMatrix();
		c.getMatrices().translate(cx, cy);
		c.getMatrices().scale(s, s);
		c.drawTexture(blend ? RenderPipelines.CROSSHAIR : RenderPipelines.GUI_TEXTURED, TEX_ID, -n / 2, -n / 2,
				(float) off, (float) off, n, n, n, n, TEX, TEX, col);
		c.getMatrices().popMatrix();
		return true;
	}

	/** No texture at all: the mask is drawn with filled rectangles (rows of pixels), so it shows up everywhere. */
	private void drawRects(DrawContext c, float cx, float cy, float s, int col) {
		boolean[][] m = mask();
		int n = m.length;
		boolean ol = outline.on() && colourModeIdx() != 0;
		c.getMatrices().pushMatrix();
		c.getMatrices().translate(cx, cy);
		c.getMatrices().scale(s, s);
		int o = -n / 2;
		if (ol) {
			int oc = outlineColour.c() | 0xFF000000;
			for (int y = -1; y <= n; y++) {
				int runStart = -2;
				for (int x = -1; x <= n + 1; x++) {
					boolean on = x <= n && near(m, x, y);
					if (on && runStart == -2) {
						runStart = x;
					} else if (!on && runStart != -2) {
						c.fill(o + runStart, o + y, o + x, o + y + 1, oc);
						runStart = -2;
					}
				}
			}
		}
		int fc = (col >>> 24) == 0 ? col | 0xFF000000 : col;
		for (int y = 0; y < n; y++) {
			int runStart = -1;
			for (int x = 0; x <= n; x++) {
				boolean on = x < n && m[y][x];
				if (on && runStart < 0) {
					runStart = x;
				} else if (!on && runStart >= 0) {
					c.fill(o + runStart, o + y, o + x, o + y + 1, fc);
					runStart = -1;
				}
			}
		}
		c.getMatrices().popMatrix();
	}

	/** True when this pixel or one of its 8 neighbours is part of the crosshair (the outline ring). */
	private static boolean near(boolean[][] m, int x, int y) {
		int n = m.length;
		for (int dy = -1; dy <= 1; dy++) {
			for (int dx = -1; dx <= 1; dx++) {
				int px = x + dx, py = y + dy;
				if (px >= 0 && py >= 0 && px < n && py < n && m[py][px]) {
					return true;
				}
			}
		}
		return false;
	}

	public void draw(DrawContext c) {
		if (mc.player == null || mc.options.hudHidden) {
			return;
		}
		if (!mc.options.getPerspective().isFirstPerson() && !thirdPerson.on()) {
			return;
		}
		Entity target = mc.crosshairTarget instanceof EntityHitResult ehr ? ehr.getEntity() : null;
		if (!spectator.on() && mc.interactionManager != null
				&& mc.interactionManager.getCurrentGameMode() == GameMode.SPECTATOR && target == null) {
			return;
		}
		boolean targeting = target != null && !target.isInvisible();
		float cx = mc.getWindow().getScaledWidth() / 2F, cy = mc.getWindow().getScaledHeight() / 2F;
		drawAt(c, cx, cy, scale.f() / 100F, targeting, true);
	}

	// ------------------------------------------------------------------ presets (15x15)
	public static final String[] PRESETS = buildPresets();

	private static String[] buildPresets() {
		java.util.List<String> l = new java.util.ArrayList<>();
		l.add(pattern((x, y) -> (x == 7 && Math.abs(y - 7) >= 2 && Math.abs(y - 7) <= 5) || (y == 7 && Math.abs(x - 7) >= 2 && Math.abs(x - 7) <= 5)));
		l.add(pattern((x, y) -> (x == 7 && Math.abs(y - 7) <= 4) || (y == 7 && Math.abs(x - 7) <= 4)));
		l.add(pattern((x, y) -> (x == 7 && Math.abs(y - 7) >= 2 && Math.abs(y - 7) <= 5) || (y == 7 && Math.abs(x - 7) >= 2 && Math.abs(x - 7) <= 5) || (x == 7 && y == 7)));
		l.add(pattern((x, y) -> Math.abs(x - 7) <= 1 && Math.abs(y - 7) <= 1));
		l.add(pattern((x, y) -> x == 7 && y == 7));
		l.add(pattern((x, y) -> Math.abs(Math.hypot(x - 7, y - 7) - 4) < 0.6));
		l.add(pattern((x, y) -> Math.abs(Math.hypot(x - 7, y - 7) - 4) < 0.6 || (x == 7 && y == 7)));
		l.add(pattern((x, y) -> (Math.abs(x - 7) == 3 && Math.abs(y - 7) <= 3) || (Math.abs(y - 7) == 3 && Math.abs(x - 7) <= 3)));
		l.add(pattern((x, y) -> (Math.abs(x - 7) == 3 && Math.abs(y - 7) <= 3) || (Math.abs(y - 7) == 3 && Math.abs(x - 7) <= 3) || (x == 7 && y == 7)));
		l.add(pattern((x, y) -> Math.abs(x - 7) == Math.abs(y - 7) && Math.abs(x - 7) >= 2 && Math.abs(x - 7) <= 5));
		l.add(pattern((x, y) -> (Math.abs(x - 7) == Math.abs(y - 7) && Math.abs(x - 7) >= 2 && Math.abs(x - 7) <= 5) || (x == 7 && y == 7)));
		l.add(pattern((x, y) -> (x == 7 && y >= 9 && y <= 12) || (y == 7 && Math.abs(x - 7) >= 2 && Math.abs(x - 7) <= 5)));
		l.add(pattern((x, y) -> y >= 7 && y <= 10 && Math.abs(x - 7) == y - 7));
		l.add(pattern((x, y) -> (Math.abs(x - 7) + Math.abs(y - 7) == 4) || (x == 7 && y == 7)));
		l.add(pattern((x, y) -> ((x == 7 && Math.abs(y - 7) >= 3 && Math.abs(y - 7) <= 6) || (y == 7 && Math.abs(x - 7) >= 3 && Math.abs(x - 7) <= 6))
				|| ((x == 6 || x == 8) && Math.abs(y - 7) >= 3 && Math.abs(y - 7) <= 6) || ((y == 6 || y == 8) && Math.abs(x - 7) >= 3 && Math.abs(x - 7) <= 6)));
		return l.toArray(new String[0]);
	}

	private interface P {
		boolean on(int x, int y);
	}

	private static String pattern(P p) {
		StringBuilder sb = new StringBuilder(GRID * GRID);
		for (int y = 0; y < GRID; y++) {
			for (int x = 0; x < GRID; x++) {
				sb.append(p.on(x, y) ? '1' : '0');
			}
		}
		return sb.toString();
	}
}
