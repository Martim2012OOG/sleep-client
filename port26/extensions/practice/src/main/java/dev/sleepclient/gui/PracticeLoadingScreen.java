package dev.sleepclient.gui;

import dev.sleepclient.Gfx;
import dev.sleepclient.addons.pvp.PracticeWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Custom loading screen for the PvP Practice and Bridge Practice worlds. It replaces the vanilla loading screens
 * from the moment the world starts opening until the add-on has built its arena / course and put you in it,
 * and plays an animated GIF (stored as a frame atlas PNG in assets/sleep_client/textures/gui/loading).
 *
 * Nothing here is needed for the add-ons to work: if this screen never shows, the add-ons behave as before.
 */
public class PracticeLoadingScreen extends Screen {

	/** One animated clip: a PNG with all frames in a grid, left to right, top to bottom. */
	private record Clip(Identifier tex, int fw, int fh, int cols, int rows, int frames, int delayMs) {
		int total() {
			return frames * delayMs;
		}
	}

	private static final Clip PVP = new Clip(Identifier.fromNamespaceAndPath("kubo_ext_practice", "loading/pvp"),
			533, 300, 7, 8, 51, 100);
	private static final Clip BRIDGE = new Clip(Identifier.fromNamespaceAndPath("kubo_ext_practice", "loading/bridge"),
			480, 270, 8, 9, 66, 40);

	/** The screen stays at least this long, so a fast load does not just flash it. */
	private static final long MIN_MS = 2200L;
	/** Safety net: never keep the player here longer than this. */
	private static final long MAX_MS = 120_000L;

	private static final String[] PVP_TIPS = {
			"Tip: block with your shield, then counter-attack.",
			"Tip: crit by hitting while you fall.",
			"Tip: start with Easy bots, then move up.",
			"Tip: strafe left and right so bots miss you."
	};
	private static final String[] BRIDGE_TIPS = {
			"Tip: sneak at the edge to bridge safely.",
			"Tip: keep your crosshair low and your speed steady.",
			"Tip: beat your best time on every mode.",
			"Tip: diagonal and stairs are harder, take it slow."
	};

	private final boolean pvp;
	private final long shownAt = System.currentTimeMillis();

	// the clips live inside this extension's jar (not in the resource packs, so it also works when it was installed
	// without a restart): read in the background, uploaded on the render thread
	private static final java.util.Map<Identifier, Object> CLIPS = new java.util.concurrent.ConcurrentHashMap<>();

	private static boolean ready(Clip clip) {
		Object st = CLIPS.get(clip.tex);
		if (st == null) {
			CLIPS.put(clip.tex, Boolean.FALSE);
			String file = "/kubo_practice/" + clip.tex.getPath().substring("loading/".length()) + ".png";
			Thread t = new Thread(() -> {
				try (java.io.InputStream in = PracticeLoadingScreen.class.getResourceAsStream(file)) {
					if (in != null) {
						CLIPS.put(clip.tex, com.mojang.blaze3d.platform.NativeImage.read(in));
					}
				} catch (Exception ignored) {
				}
			}, "kubo-practice-clip");
			t.setDaemon(true);
			t.start();
			return false;
		}
		if (st instanceof com.mojang.blaze3d.platform.NativeImage img) {
			net.minecraft.client.renderer.texture.DynamicTexture tex = new net.minecraft.client.renderer.texture.DynamicTexture(() -> "kubo_practice_clip", img);
			tex.upload();
			Minecraft.getInstance().getTextureManager().register(clip.tex, tex);
			CLIPS.put(clip.tex, Boolean.TRUE);
			return true;
		}
		return st == Boolean.TRUE;
	}

	public PracticeLoadingScreen(boolean pvp) {
		super(Component.literal(pvp ? "PvP Practice" : "Bridge Practice"));
		this.pvp = pvp;
	}

	// ------------------------------------------------------------------ hook (called from SetScreenMixin / tick)
	/**
	 * Returns the screen to open instead of {@code s}. While a practice world is loading, the vanilla loading
	 * screens (and the "no screen" that comes when you join) are swapped for this one.
	 */
	public static Screen intercept(Screen s) {
		if (!PracticeWorld.loading()) {
			return s;
		}
		Minecraft mc = Minecraft.getInstance();
		if (s instanceof PracticeLoadingScreen || s instanceof CreateWorldScreen) {
			return s;
		}
		if (s == null) {
			// joined the world: keep the loading screen until the arena is ready
			return mc.level != null ? make(mc) : s;
		}
		return isVanillaLoading(s) ? make(mc) : s;
	}

	private static Screen make(Minecraft mc) {
		if (dev.sleepclient.compat.Mc.screen(mc) instanceof PracticeLoadingScreen cur) {
			return cur;
		}
		return new PracticeLoadingScreen(PracticeWorld.loadingIsPvp());
	}

	/** By class name on purpose: these screens were renamed between versions, so no hard dependency on them. */
	private static boolean isVanillaLoading(Screen s) {
		String n = s.getClass().getSimpleName();
		return n.equals("ProgressScreen") || n.equals("MessageScreen") || n.equals("GenericMessageScreen")
				|| n.equals("DownloadingTerrainScreen") || n.equals("GenericWaitingScreen") || n.endsWith("LoadingScreen");
	}

	// ------------------------------------------------------------------ screen
	@Override
	public boolean isPauseScreen() {
		return false; // the integrated server has to keep ticking, or the arena never gets built
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public void tick() {
		long age = System.currentTimeMillis() - shownAt;
		Minecraft mc = Minecraft.getInstance();
		if (!PracticeWorld.loading()) {
			// cancelled or left the world: just get out of the way
			if (dev.sleepclient.compat.Mc.screen(mc) == this) {
				dev.sleepclient.compat.Mc.setScreen(mc, mc.level != null ? null : new net.minecraft.client.gui.screens.TitleScreen());
			}
			return;
		}
		if (age >= MAX_MS) {
			PracticeWorld.finishLoading();
		}
		if (PracticeWorld.ready() && age >= MIN_MS) {
			PracticeWorld.finishLoading();
			dev.sleepclient.compat.Mc.setScreen(mc, null);
		}
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		if (input.key() == 256) { // Esc: give up and go back to the menu
			PracticeWorld.abort();
			return true;
		}
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor c, int mx, int my, float delta) {
		// opaque dark background (hides the world behind)
		Gfx.fill(c, 0, 0, width, height, 0xFF0B0D14);
		Gfx.gradientV(c, 0, 0, width, height, 0xFF141827, 0xFF07080D);

		Clip clip = pvp ? PVP : BRIDGE;
		long now = System.currentTimeMillis();

		// the GIF, as big as fits while keeping 16:9, with a border
		int availW = Math.min(width - 32, 460);
		int availH = height - 120;
		float ratio = clip.fw / (float) clip.fh;
		int gw = availW, gh = Math.round(gw / ratio);
		if (gh > availH) {
			gh = Math.max(40, availH);
			gw = Math.round(gh * ratio);
		}
		int gx = (width - gw) / 2, gy = Math.max(30, (height - gh) / 2 - 22);

		Gfx.rrectBorder(c, gx - 4, gy - 4, gw + 8, gh + 8, 8, Theme.PANEL, Theme.accent());
		int f = (int) ((now / clip.delayMs) % clip.frames);
		int u = (f % clip.cols) * clip.fw, v = (f / clip.cols) * clip.fh;
		if (ready(clip)) Gfx.texUv(c, clip.tex, gx, gy, gw, gh, u, v, clip.fw, clip.fh, clip.cols * clip.fw, clip.rows * clip.fh, 0xFFFFFFFF);

		// title + status
		int ty = gy + gh + 14;
		Gfx.textC(c, pvp ? "PvP Practice" : "Bridge Practice", width / 2, ty, Theme.TEXT);
		String status = PracticeWorld.loadingStatus();
		Gfx.textC(c, status, width / 2, ty + 14, Theme.DIM);

		// moving bar (we cannot know the real progress, so it just slides)
		int bw = Math.min(width - 60, 220), bh = 4, bx = (width - bw) / 2, by = ty + 30;
		Gfx.rrect(c, bx, by, bw, bh, 2, Theme.OFF_BG);
		int seg = bw / 3;
		float p = (now % 1400L) / 1400F;
		int sx = bx + Math.round((bw + seg) * p) - seg;
		int x1 = Math.max(bx, sx), x2 = Math.min(bx + bw, sx + seg);
		if (x2 > x1) {
			Gfx.rrect(c, x1, by, x2 - x1, bh, 2, Theme.accent());
		}

		// tip, changes every 4 seconds
		String[] tips = pvp ? PVP_TIPS : BRIDGE_TIPS;
		Gfx.textC(c, tips[(int) ((now / 4000L) % tips.length)], width / 2, by + 16, Theme.DIM);
		Gfx.textC(c, "Esc to cancel", width / 2, height - 14, Gfx.withAlpha(Theme.DIM, 150));
	}
}
