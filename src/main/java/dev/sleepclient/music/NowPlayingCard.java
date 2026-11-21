package dev.sleepclient.music;

import dev.sleepclient.Gfx;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.joml.Quaternionf;

/**
 * The "Now playing" card: when a song starts it first says WHAT is playing and by WHOM (label, title, artist, the player it comes from),
 * floating in the real sky (3D, like the lyrics) or flat on the screen, and then it fades away so the lyrics can take over.
 * It reads {@link MusicHub}, so it works for any player: Spotify, YouTube / YouTube Music or SoundCloud in a browser, Apple Music...
 * When a player gives no artist (a plain browser video) we try "Artist - Title" in the title, then fall back to the player's name.
 */
public final class NowPlayingCard {

	/** Everything the player can change; filled by the Music mod every frame. */
	public static final class Cfg {
		public boolean enabled = true, sky = true, onlyPlaying = true, hideLyrics, showLabel = true, showArtist = true, showSource = true,
				lines = true, upper, follow, behind;
		public String label = "NOW PLAYING";
		/** index in {@link LyricFonts} */
		public int font;
		/** 0 centre, 1 left, 2 right, 3 random for each song */
		public int position;
		/** 0 fade, 1 typewriter, 2 rise, 3 zoom */
		public int anim;
		/** 0 solid, 1 shimmer (title breathes between its colour and the accent), 2 gradient across the title letters */
		public int colourMode;
		/** preset of {@link Gradients} (the last one = custom: title colour to accent colour) and how fast it slides (0 = still) */
		public int gradient;
		public float gradFlow;
		public float size = 1F, artistSize = 1F, labelSize = 1F, height = 14F, spread = 24F, depth = 0.7F, opacity = 1F, glow = 0.5F;
		public float seconds = 6F, repeatSec;
		public int titleCol = 0xFFFFFFFF, artistCol = 0xFFC4B5FD, labelCol = 0xFFFFFFFF, accent = 0xFF8B5CF6;
	}

	public static volatile Cfg cfg;
	public static volatile long lastSet;
	public static volatile String error;

	private static final float DIST = 72F;
	private static boolean hooked;

	// the song being announced
	private static String key = "", vTitle = "", vArtist = "", vSource = "";
	private static long shownAt = -1L, lastTrigger;
	private static float yaw0, pitch0, tiltYaw, tiltPitch, tiltRoll;
	private static int side;

	private NowPlayingCard() {
	}

	public static void register() {
		if (hooked) {
			return;
		}
		hooked = true;
		try {
			WorldRenderEvents.BEFORE_ENTITIES.register(ctx -> {
			long kuboPerfT0 = System.nanoTime();
			try {
				Cfg c = cfg;
				if (c == null || !c.enabled || !c.sky || System.currentTimeMillis() - lastSet > 5000L || !visible(System.currentTimeMillis())) {
					return;
				}
				try {
					renderWorld(ctx.matrices(), ctx.commandQueue(), c);
				} catch (RuntimeException e) {
					if (error == null) {
						System.err.println("[Sleep] now playing card: " + e);
						e.printStackTrace();
					}
					error = String.valueOf(e);
				}
						} finally {
				dev.sleepclient.Perf.add("Music card (world)", System.nanoTime() - kuboPerfT0);
			}
		});
		} catch (Throwable t) {
			System.err.println("[Sleep] now playing card unavailable: " + t);
		}
	}

	// ------------------------------------------------------------------ when to show

	/** Called every frame by the Music mod: sets the settings and starts the card for a new song (or every N seconds). */
	public static void update(Cfg c) {
		cfg = c;
		lastSet = System.currentTimeMillis();
		MinecraftClient mc = MinecraftClient.getInstance();
		if (!c.enabled || mc.player == null || mc.world == null || !MusicHub.hasTrack || (c.onlyPlaying && !MusicHub.playing)) {
			return;
		}
		long now = System.currentTimeMillis();
		String k = MusicHub.title + "|" + MusicHub.artist;
		boolean fresh = !k.equals(key);
		boolean again = c.repeatSec >= 5F && shownAt > 0 && now - lastTrigger > (long) (c.repeatSec * 1000F) + (long) (c.seconds * 1000F);
		if (fresh || again) {
			key = k;
			start(mc, c, now, MusicHub.title, MusicHub.artist, MusicHub.source, k.hashCode());
		}
	}

	/** The "Preview now" button: shows the card with the current song (or a sample when nothing plays). */
	public static void preview() {
		MinecraftClient mc = MinecraftClient.getInstance();
		Cfg c = cfg;
		if (c == null || mc.player == null) {
			return;
		}
		if (MusicHub.hasTrack && !MusicHub.title.isBlank()) {
			start(mc, c, System.currentTimeMillis(), MusicHub.title, MusicHub.artist, MusicHub.source, (int) System.nanoTime());
		} else {
			start(mc, c, System.currentTimeMillis(), "Hate Me", "Ellie Goulding", "Spotify", (int) System.nanoTime());
		}
	}

	private static void start(MinecraftClient mc, Cfg c, long now, String title, String artist, String source, int seed) {
		String t = title == null ? "" : title.trim(), a = artist == null ? "" : artist.trim(), s = source == null ? "" : source.trim();
		if (a.isEmpty()) {
			int i = t.indexOf(" - ");
			if (i > 0 && i < t.length() - 3) {
				a = t.substring(0, i).trim();
				t = t.substring(i + 3).trim();
			}
		}
		if (a.isEmpty() && !s.isEmpty()) {
			a = "via " + s; // browser video with no artist: at least say where it plays
			s = "";
		}
		vTitle = t;
		vArtist = a;
		vSource = s;
		shownAt = now;
		lastTrigger = now;
		java.util.Random r = new java.util.Random(seed * 2654435761L + 17L);
		side = switch (c.position) {
			case 1 -> -1;
			case 2 -> 1;
			case 3 -> r.nextInt(3) - 1;
			default -> 0;
		};
		float d = Math.max(0F, Math.min(1F, c.depth));
		yaw0 = mc.player.getYaw() + side * c.spread + (r.nextFloat() - 0.5F) * 4F;
		pitch0 = -(c.height + (r.nextFloat() - 0.5F) * 3F);
		tiltYaw = d * (side != 0 ? side * (8F + r.nextFloat() * 10F) : (r.nextBoolean() ? 1 : -1) * (4F + r.nextFloat() * 8F));
		tiltPitch = d * (-2F + r.nextFloat() * 8F);
		tiltRoll = d * (r.nextFloat() * 2F - 1F) * 4F;
	}

	private static boolean visible(long now) {
		Cfg c = cfg;
		return c != null && shownAt > 0 && !vTitle.isEmpty() && now - shownAt < (long) (c.seconds * 1000F);
	}

	/** True while the card is on screen and the player wants the lyrics to wait for it. */
	public static boolean blocksLyrics() {
		Cfg c = cfg;
		return c != null && c.enabled && c.hideLyrics && visible(System.currentTimeMillis()) && System.currentTimeMillis() - lastSet < 2000L;
	}

	// ------------------------------------------------------------------ animation

	private static float ease(float x) {
		x = Math.max(0F, Math.min(1F, x));
		return x * x * (3F - 2F * x);
	}

	/** age in seconds; returns {alpha, rise (units), zoom, typed fraction} */
	private static float[] anim(Cfg c, long now) {
		float age = (now - shownAt) / 1000F;
		float in = ease(age / 0.3F), out = ease((c.seconds - age) / 0.9F);
		float rise = c.anim == 2 ? (1F - in) * 10F : 0F;
		rise += (1F - out) * 4F;
		float zoom = c.anim == 3 ? 0.7F + 0.3F * in : 1F;
		float typed = c.anim == 1 ? Math.max(0F, Math.min(1F, age / 1.4F)) : 1F;
		float alpha = (c.anim == 1 ? Math.min(1F, age / 0.2F) : in) * out * c.opacity;
		return new float[] {alpha, rise, zoom, typed};
	}

	private static String typed(String s, float frac) {
		if (frac >= 1F) {
			return s;
		}
		return s.substring(0, Math.max(0, Math.min(s.length(), (int) Math.ceil(s.length() * frac))));
	}

	private static int titleColour(Cfg c, double time) {
		if (c.colourMode == 1) {
			return Gfx.blend(c.titleCol, c.accent, 0.5F + 0.5F * (float) Math.sin(time * 1.8));
		}
		return c.titleCol;
	}

	private static Text styled(String s, Cfg c) {
		LyricsView.Cfg f = new LyricsView.Cfg();
		LyricFonts.apply(f, c.font);
		f.upper = c.upper;
		return LyricsView.styled(s, f);
	}

	private static int[] gradStops(Cfg c) {
		return Gradients.stops(c.gradient, new int[] {c.titleCol, c.accent}, 2);
	}

	// ------------------------------------------------------------------ in the sky

	private static void renderWorld(MatrixStack m, OrderedRenderCommandQueue queue, Cfg c) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.world == null) {
			return;
		}
		long now = System.currentTimeMillis();
		float[] an = anim(c, now);
		if (an[0] <= 0.01F) {
			return;
		}
		TextRenderer tr = mc.textRenderer;
		double time = now / 1000.0;
		Text title = styled(vTitle, c);
		Text artist = styled(vArtist, c), source = styled("via " + vSource, c), label = styled(c.label, c);
		float titleW = tr.getWidth(title);
		float titleScale = titleW > 190F ? 190F / titleW : 1F;
		float artistSc = 0.65F * c.artistSize, labelSc = 0.55F * c.labelSize, srcSc = 0.45F;
		float refW = Math.max(80F, Math.max(titleW * titleScale, Math.max(tr.getWidth(artist) * artistSc, tr.getWidth(label) * labelSc)));
		float widthDeg = (c.font == 0 ? 26F : 22F) * c.size;
		float s = (float) (2 * DIST * Math.tan(Math.toRadians(widthDeg / 2))) / refW;

		float yawD = (c.follow ? mc.player.getYaw() + side * c.spread : yaw0) + (float) Math.sin(time * 0.6) * 0.5F;
		float pitchD = (c.follow ? -c.height : pitch0) + (float) Math.sin(time * 0.45 + 1.0) * 0.35F;
		double yr = Math.toRadians(yawD), pr = Math.toRadians(pitchD);
		double dx = -Math.sin(yr) * Math.cos(pr), dy = -Math.sin(pr), dz = Math.cos(yr) * Math.cos(pr);
		m.push();
		m.translate(dx * DIST, dy * DIST, dz * DIST);
		m.multiply(new Quaternionf().rotateY((float) (Math.PI - yr)).rotateX((float) -pr));
		m.multiply(new Quaternionf().rotateY((float) Math.toRadians(tiltYaw)).rotateX((float) Math.toRadians(tiltPitch)).rotateZ((float) Math.toRadians(tiltRoll)));
		m.scale(s * an[2], -s * an[2], s * an[2]);

		var layer = c.behind ? TextRenderer.TextLayerType.POLYGON_OFFSET : TextRenderer.TextLayerType.SEE_THROUGH;
		int layers = Math.min(2, Math.round(3F * Math.max(0F, Math.min(1F, c.depth))));
		float a = an[0], rise = an[1];
		int tc = titleColour(c, time);

		if (c.showLabel && !c.label.isBlank()) {
			sky(m, queue, tr, styled(typed(c.label, an[3]), c), tr.getWidth(label), -17F - rise, labelSc, c.labelCol, a * 0.9F, c, 0, 0, layer, false);
		}
		if (c.lines) {
			float lw = Math.max(60F, Math.min(190F, titleW * titleScale)) * 0.9F * ease(((now - shownAt) / 1000F) / 0.45F);
			sky(m, queue, tr, Text.literal("—").setStyle(net.minecraft.text.Style.EMPTY), tr.getWidth("—"), -9F - rise, 1F, c.accent, a * 0.85F, c, 0, lw, layer, true);
			sky(m, queue, tr, Text.literal("—").setStyle(net.minecraft.text.Style.EMPTY), tr.getWidth("—"), 9F - rise, 1F, c.accent, a * 0.85F, c, 0, lw, layer, true);
		}
		if (c.colourMode == 2) {
			// gradient: every letter is its own piece with its own colour
			int[] st = gradStops(c);
			String shown = typed(vTitle, an[3]);
			int nShown = shown.length(), nAll = Math.max(1, vTitle.length());
			float left = -titleW * titleScale / 2F;
			for (int i = 0; i < nShown; i++) {
				String ch = vTitle.substring(i, i + 1);
				if (ch.isBlank()) {
					continue;
				}
				float before = tr.getWidth(styled(vTitle.substring(0, i), c)), cw = tr.getWidth(styled(ch, c));
				float cxu = left + (before + cw / 2F) * titleScale;
				int lc = Gradients.at(st, nAll <= 1 ? 0.5F : i / (float) (nAll - 1), c.gradFlow, time);
				sky(m, queue, tr, styled(ch, c), cw, cxu, -rise, titleScale, lc, a, c, layers, 0, layer, false, 0.8F);
			}
		} else {
			sky(m, queue, tr, styled(typed(vTitle, an[3]), c), titleW, -rise, titleScale, tc, a, c, layers, 0, layer, false);
		}
		if (c.showArtist && !vArtist.isEmpty()) {
			sky(m, queue, tr, styled(typed(vArtist, an[3]), c), tr.getWidth(artist), 17F - rise, artistSc, c.artistCol, a * 0.95F, c, Math.min(1, layers), 0, layer, false);
		}
		if (c.showSource && !vSource.isEmpty()) {
			sky(m, queue, tr, source, tr.getWidth(source), 27F - rise, srcSc, c.artistCol, a * 0.6F, c, 0, 0, layer, false);
		}
		m.pop();
	}

	/**
	 * One piece of text on the sheet: v is how far below the centre (sheet units), sc its size, glow copies around it (accent colour)
	 * and "layers" dark copies behind it for thickness. stretch &gt; 0 draws it as a bar of that width (used for the lines).
	 */
	private static void sky(MatrixStack m, OrderedRenderCommandQueue queue, TextRenderer tr, Text t, float raw, float v, float sc, int col, float alpha,
			Cfg c, int layers, float stretch, TextRenderer.TextLayerType layer, boolean bar) {
		sky(m, queue, tr, t, raw, 0F, v, sc, col, alpha, c, layers, stretch, layer, bar, 1F);
	}

	private static void sky(MatrixStack m, OrderedRenderCommandQueue queue, TextRenderer tr, Text t, float raw, float xOff, float v, float sc, int col, float alpha,
			Cfg c, int layers, float stretch, TextRenderer.TextLayerType layer, boolean bar, float glowMul) {
		int light = 0xF000F0;
		m.push();
		m.translate(xOff, v, 0);
		if (bar) {
			m.scale(raw <= 0F ? 1F : stretch / raw, 0.7F, 1F);
		} else {
			m.scale(sc, sc, 1F);
		}
		m.translate(-raw / 2F, -4F, 0);
		if (layers > 0) {
			int dark = Gfx.blend(col, 0xFF000000, 0.72F);
			for (int l = layers; l >= 1; l--) {
				int a8 = LyricsView.alphaOf(dark, alpha * (0.75F - 0.08F * l));
				if (((a8 >>> 24) & 255) < 6) {
					continue;
				}
				m.push();
				m.translate(0, 0, -0.55F * l);
				queue.submitText(m, 0, 0, t.asOrderedText(), false, layer, light, a8, 0, 0);
				m.pop();
			}
		}
		if (c.glow > 0.02F && !bar) {
			int gl = LyricsView.alphaOf(c.colourMode == 2 ? col : c.accent, 0.38F * c.glow * glowMul * alpha);
			for (int[] o : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
				m.push();
				m.translate(o[0] * 0.9F, o[1] * 0.9F, -0.2F);
				queue.submitText(m, 0, 0, t.asOrderedText(), false, layer, light, gl, 0, 0);
				m.pop();
			}
		}
		int a8 = LyricsView.alphaOf(col, alpha);
		if (((a8 >>> 24) & 255) >= 8) {
			queue.submitText(m, 0, 0, t.asOrderedText(), c.font != 0 && !bar, layer, light, a8, 0, 0);
		}
		m.pop();
	}

	// ------------------------------------------------------------------ flat on the screen

	/** The same card flat on the screen (when "Screen" is chosen instead of the sky). */
	public static void drawFlat(DrawContext dc) {
		Cfg c = cfg;
		long now = System.currentTimeMillis();
		if (c == null || !c.enabled || c.sky || !visible(now)) {
			return;
		}
		float[] an = anim(c, now);
		if (an[0] <= 0.01F) {
			return;
		}
		int w = dc.getScaledWindowWidth(), h = dc.getScaledWindowHeight();
		float sc = 1.7F * c.size * an[2];
		int cx = Math.round(w / 2F + side * w * c.spread / 100F);
		int cy = Math.round(h * (0.5F - c.height / 100F)) - Math.round(an[1]);
		double time = now / 1000.0;
		float a = an[0];
		String vt = c.upper ? vTitle.toUpperCase(java.util.Locale.ROOT) : vTitle;
		String title = typed(vt, an[3]);
		int tw = Gfx.tw(vt);
		float lineW = Math.max(60F, Math.min(200F, tw * 1F)) * ease(((now - shownAt) / 1000F) / 0.45F);

		if (c.showLabel && !c.label.isBlank()) {
			flat(dc, typed(c.label, an[3]), Gfx.tw(c.label), cx, cy - Math.round(18 * sc), sc * 0.62F * c.labelSize, c.labelCol, a * 0.9F);
		}
		if (c.lines) {
			int lw = Math.round(lineW * sc * 0.9F);
			int th = Math.max(1, Math.round(sc * 0.7F));
			Gfx.fill(dc, cx - lw / 2, cy - Math.round(10 * sc), cx + lw / 2, cy - Math.round(10 * sc) + th, Gfx.withAlpha(c.accent & 0xFFFFFF, Math.round(210 * a)));
			Gfx.fill(dc, cx - lw / 2, cy + Math.round(11 * sc), cx + lw / 2, cy + Math.round(11 * sc) + th, Gfx.withAlpha(c.accent & 0xFFFFFF, Math.round(210 * a)));
		}
		if (c.glow > 0.02F) {
			int gcol = c.accent;
			for (int[] o : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
				flat(dc, title, tw, cx + o[0], cy + o[1] - Math.round(4 * sc), sc, gcol, 0.30F * c.glow * a);
			}
		}
		if (c.colourMode == 2) {
			int[] st = gradStops(c);
			int nAll = Math.max(1, vt.length());
			float left = cx - tw * sc / 2F;
			for (int i = 0; i < title.length(); i++) {
				String ch = title.substring(i, i + 1);
				if (ch.isBlank()) {
					continue;
				}
				int lx = Math.round(left + Gfx.tw(vt.substring(0, i)) * sc);
				int lc = Gradients.at(st, nAll <= 1 ? 0.5F : i / (float) (nAll - 1), c.gradFlow, time);
				flat(dc, ch, Gfx.tw(ch), lx + Math.round(Gfx.tw(ch) * sc / 2F), cy - Math.round(4 * sc), sc, lc, a);
			}
		} else {
			flat(dc, title, tw, cx, cy - Math.round(4 * sc), sc, titleColour(c, time), a);
		}
		if (c.showArtist && !vArtist.isEmpty()) {
			flat(dc, typed(vArtist, an[3]), Gfx.tw(vArtist), cx, cy + Math.round(15 * sc), sc * 0.7F * c.artistSize, c.artistCol, a * 0.95F);
		}
		if (c.showSource && !vSource.isEmpty()) {
			String s = "via " + vSource;
			flat(dc, s, Gfx.tw(s), cx, cy + Math.round(15 * sc + 12 * sc * c.artistSize * 0.7F), sc * 0.5F, c.artistCol, a * 0.6F);
		}
	}

	private static void flat(DrawContext dc, String s, int fullW, int cx, int y, float sc, int argb, float alpha) {
		int al = Math.round(255 * Math.max(0F, Math.min(1F, alpha)));
		if (al < 5 || s.isEmpty()) {
			return;
		}
		dc.getMatrices().pushMatrix();
		dc.getMatrices().translate(cx - fullW * sc / 2F, y);
		dc.getMatrices().scale(sc, sc);
		Gfx.textRaw(dc, s, 0, 0, Gfx.withAlpha(argb & 0xFFFFFF, al));
		dc.getMatrices().popMatrix();
	}
}
