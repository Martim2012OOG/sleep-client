package dev.sleepclient.music;

import dev.sleepclient.Gfx;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.joml.Quaternionf;

/**
 * The lyrics written in the REAL sky: every line is a sheet of letters hanging in the world at a fixed direction above the horizon
 * (it does not follow your screen: turn around and it stays where it is, turn back and it is still there). It leans and turns in 3D,
 * the letters have thickness and glow, and they pop in one after the other as the words are sung. The next line appears in another
 * part of the sky, in front of you, and the old one fades away.
 */
public final class SkyLyrics {

	/** Set by the Spotify mod every frame. */
	public static volatile boolean active;
	public static volatile LyricsView.Cfg cfg;
	public static volatile boolean behindTerrain;
	public static volatile long lastSet;

	private static final float DIST = 72F;
	private static boolean hooked;

	/** Where a line hangs: a direction (yaw / pitch in game degrees) and its own lean. */
	private static final class Anchor {
		int line = -2, version;
		long leftAt;
		float yaw, pitch, tiltYaw, tiltPitch, tiltRoll;
		int rows;
		/** "in front of me": the sheet stays at this place in the WORLD (you walk past it), at this distance from where you stood */
		boolean world;
		double wx, wy, wz;
		float dist = DIST;
		long bornAt;
		/** "in front of me": still glued in front of you (until its last word has been sung), and its angle from where you look */
		boolean follow;
		float yawOff;
		/** blocks this piece was moved up / down so it does not cover another one */
		double lift;
		/** half size in blocks (measured when placed), to keep pieces from overlapping */
		float hw, hh;
	}

	// ------------------------------------------------------------------ pieces: the line cut into a few words at a time
	private static List<Lyrics.Line> piecesSrc;
	private static List<Lyrics.Line> pieces = List.of();
	private static int piecesN = -1, piecesVersion = -1;

	/** The lines cut into pieces of n words (n <= 0: the lines as they are). Cached until the lyrics or n change. */
	static List<Lyrics.Line> pieces(List<Lyrics.Line> lines, int n) {
		if (n <= 0) {
			return lines;
		}
		if (lines == piecesSrc && n == piecesN && piecesVersion == Lyrics.version) {
			return pieces;
		}
		List<Lyrics.Line> out = new java.util.ArrayList<>();
		for (Lyrics.Line l : lines) {
			List<Lyrics.Chunk> ch = l.chunks();
			if (l.blank() || ch.size() <= n) {
				out.add(l);
				continue;
			}
			for (int i = 0; i < ch.size(); i += n) {
				List<Lyrics.Chunk> part = ch.subList(i, Math.min(ch.size(), i + n));
				StringBuilder t = new StringBuilder();
				for (Lyrics.Chunk k : part) {
					t.append(t.length() == 0 ? "" : " ").append(k.text());
				}
				long end = i + n < ch.size() ? ch.get(i + n).start() : l.end();
				out.add(new Lyrics.Line(part.get(0).start(), end, t.toString(), List.copyOf(part)));
			}
		}
		piecesSrc = lines;
		piecesN = n;
		piecesVersion = Lyrics.version;
		pieces = out;
		return out;
	}

	/** Half width / half height in blocks of a line drawn at this distance (same layout as draw()). */
	private static float[] halfSize(TextRenderer tr, LyricsView.Cfg c, Lyrics.Line line, float dist) {
		List<Lyrics.Chunk> ch = line.chunks();
		int n = ch.size();
		float space = tr.getWidth(LyricsView.styled(" ", c)) * (c.script ? 1.6F : 1F);
		float tot = 0F;
		for (Lyrics.Chunk k : ch) {
			tot += tr.getWidth(LyricsView.styled(k.text(), c)) + space;
		}
		tot = Math.max(1F, tot - space);
		int rows = n <= 2 ? 1 : (n <= 6 ? 2 : 3);
		float widest = Math.max(1F, tot / rows * 1.08F);
		float widthDeg = (c.script ? 34F : 28F) * c.size;
		float s = (float) (2 * dist * Math.tan(Math.toRadians(widthDeg / 2))) / widest;
		float rowH = 11F * (c.script ? 1.35F : 1.25F);
		return new float[] {widest * s / 2F, rows * rowH * s / 2F};
	}

	private static final java.util.ArrayList<Anchor> trail = new java.util.ArrayList<>();
	private static int frontLine = -2, frontVersion;
	private static double camX, camY, camZ;

	private static Anchor cur = new Anchor(), old = new Anchor();

	private SkyLyrics() {
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
				if (!active || cfg == null || System.currentTimeMillis() - lastSet > 5000L) {
					return;
				}
				try {
					render(ctx.matrices(), ctx.commandQueue());
				} catch (RuntimeException e) {
					if (error == null) {
						System.err.println("[Sleep] sky lyrics: " + e);
						e.printStackTrace();
					}
					error = String.valueOf(e);
				}
						} finally {
				dev.sleepclient.Perf.add("Sky lyrics", System.nanoTime() - kuboPerfT0);
			}
		});
		} catch (Throwable t) {
			System.err.println("[Sleep] sky lyrics unavailable: " + t);
		}
	}

	public static volatile String error;

	private static void render(MatrixStack m, OrderedRenderCommandQueue queue) {
		MinecraftClient mc = MinecraftClient.getInstance();
		LyricsView.Cfg c = cfg;
		if (mc.player == null || mc.world == null || !MusicHub.hasTrack || (c.onlyPlaying && !MusicHub.playing) || NowPlayingCard.blocksLyrics()) {
			return;
		}
		List<Lyrics.Line> lines = pieces(Lyrics.lines, c.wordsPerPiece);
		if (lines.isEmpty()) {
			return;
		}
		long now = System.currentTimeMillis();
		long pos = MusicHub.progressMs + (MusicHub.playing ? now - MusicHub.fetchedAt + MusicHub.LYRICS_LEAD_MS : 0) + Math.round(c.offsetMs);
		int idx = Lyrics.indexAt(lines, pos);
		net.minecraft.util.math.Vec3d cam = mc.player.getCameraPosVec(mc.getRenderTickCounter().getTickProgress(true));
		camX = cam.x;
		camY = cam.y;
		camZ = cam.z;
		if (c.front) {
			renderFront(m, queue, mc, c, lines, pos, now, idx);
			return;
		}
		trail.clear();
		if (idx >= 0) {
			Lyrics.Line line = lines.get(idx);
			boolean over = !line.blank() && pos > line.chunks().get(line.chunks().size() - 1).end() + 1800;
			if (line.blank() || over) {
				if (cur.line != -2) {
					leave(now);
				}
			} else {
				if (cur.line != idx || cur.version != Lyrics.version) {
					if (cur.line != -2 && cur.version == Lyrics.version) {
						leave(now);
					}
					cur = place(idx, mc, c);
				}
				draw(m, queue, mc, c, line, cur, pos, now, 1F);
			}
		}
		// the previous line drifts up and fades
		if (old.line >= 0 && old.leftAt != 0 && old.version == Lyrics.version && old.line < lines.size()) {
			float k = 1F - (now - old.leftAt) / 900F;
			if (k > 0F) {
				draw(m, queue, mc, c, lines.get(old.line), old, pos, now, k * k);
			} else {
				old.line = -2;
			}
		}
	}

	/**
	 * "In front of me": every line appears a few blocks ahead of you and STAYS there in the world. You keep walking, the line stays
	 * behind you and fades out with the distance (and with time); the next line appears ahead again, so the words trail behind you.
	 */
	private static void renderFront(MatrixStack m, OrderedRenderCommandQueue queue, MinecraftClient mc, LyricsView.Cfg c, List<Lyrics.Line> lines, long pos, long now, int idx) {
		boolean live = false;
		if (idx >= 0) {
			Lyrics.Line line = lines.get(idx);
			boolean over = !line.blank() && pos > line.chunks().get(line.chunks().size() - 1).end() + 1800;
			live = !line.blank() && !over;
			if (live && (frontLine != idx || frontVersion != Lyrics.version)) {
				frontLine = idx;
				frontVersion = Lyrics.version;
				Anchor a = place(idx, mc, c);
				float py = mc.player.getYaw();
				a.yaw = py + (a.yaw - py) * 0.45F; // not as wide as in the sky
				a.pitch = -(float) (java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 7.0 - 1.5);
				a.dist = Math.max(2F, Math.min(40F, c.frontDist));
				double yr = Math.toRadians(a.yaw), pr = Math.toRadians(a.pitch);
				a.world = true;
				a.wx = camX - Math.sin(yr) * Math.cos(pr) * a.dist;
				a.wy = camY - Math.sin(pr) * a.dist - 0.2 + c.frontLift;
				a.wz = camZ + Math.cos(yr) * Math.cos(pr) * a.dist;
				float[] hs = halfSize(mc.textRenderer, c, lines.get(idx), a.dist);
				a.hw = hs[0];
				a.hh = hs[1];
				a.lift = c.frontLift + clearSpot(a);
				a.wy += a.lift - c.frontLift;
				a.bornAt = now;
				a.follow = c.frontFollow;
				a.yawOff = a.yaw - py;
				trail.add(a);
				while (trail.size() > Math.max(1, Math.min(8, c.frontKeep)) + 1) {
					trail.remove(0);
				}
			}
		}
		float maxD = Math.max(8F, c.frontFade);
		for (int i = trail.size() - 1; i >= 0; i--) {
			Anchor a = trail.get(i);
			if (a.version != Lyrics.version || a.line >= lines.size()) {
				trail.remove(i);
				continue;
			}
			boolean current = live && a.line == frontLine && a.version == frontVersion;
			if (a.follow) {
				// glued in front of you while the line is being sung; when its last word has been sung it stays where it is
				Lyrics.Line ln = lines.get(a.line);
				long lastEnd = ln.blank() ? 0 : ln.chunks().get(ln.chunks().size() - 1).end();
				if (!current || pos > lastEnd) {
					a.follow = false;
				} else {
					float target = mc.player.getYaw() + a.yawOff;
					float dy = net.minecraft.util.math.MathHelper.wrapDegrees(target - a.yaw);
					a.yaw += dy * 0.12F; // turns with you smoothly, not stuck to the screen
					double yr = Math.toRadians(a.yaw), pr = Math.toRadians(a.pitch);
					double nx = camX - Math.sin(yr) * Math.cos(pr) * a.dist, ny = camY - Math.sin(pr) * a.dist - 0.2 + a.lift,
							nz = camZ + Math.cos(yr) * Math.cos(pr) * a.dist;
					a.wx += (nx - a.wx) * 0.25;
					a.wy += (ny - a.wy) * 0.25;
					a.wz += (nz - a.wz) * 0.25;
				}
			}
			if (current) {
				a.leftAt = 0;
			} else if (a.leftAt == 0) {
				a.leftAt = now;
			}
			float age = current ? 1F : 1F - (now - a.leftAt - 7000L) / 3000F; // stays 7 s, then fades for 3 s
			double dx = a.wx - camX, dz = a.wz - camZ, d = Math.sqrt(dx * dx + dz * dz);
			float far = (float) ((maxD - d) / (maxD * 0.45));
			float k = Math.max(0F, Math.min(1F, Math.min(age, far)));
			if (age <= 0F) {
				trail.remove(i);
				continue;
			}
			if (k > 0.01F) {
				draw(m, queue, mc, c, lines.get(a.line), a, pos, now, k * k);
			}
		}
	}

	/**
	 * How far up (or down) a new piece must move so it covers none of the pieces still shown: tries the spot itself, then a
	 * little higher, a little lower, higher again... Pieces are compared as boxes facing you.
	 */
	private static double clearSpot(Anchor a) {
		double[] tries = {0, 1, -1, 2, -2, 3, 4};
		for (double t : tries) {
			double y = a.wy + t * (a.hh * 2 + 0.25);
			boolean free = true;
			for (Anchor o : trail) {
				if (o == a || o.version != a.version) {
					continue;
				}
				// side-by-side distance seen from the camera, and height
				double ox = o.wx - a.wx, oz = o.wz - a.wz;
				double yr = Math.toRadians(a.yaw);
				double across = Math.abs(ox * Math.cos(yr) + oz * Math.sin(yr));
				double along = Math.abs(-ox * Math.sin(yr) + oz * Math.cos(yr));
				if (across < a.hw + o.hw + 0.2 && Math.abs(o.wy - y) < a.hh + o.hh + 0.15 && along < 4.0) {
					free = false;
					break;
				}
			}
			if (free) {
				return t * (a.hh * 2 + 0.25);
			}
		}
		return 4 * (a.hh * 2 + 0.25);
	}

	private static void leave(long now) {
		cur.leftAt = now;
		old = cur;
		cur = new Anchor();
	}

	/** A new line gets its own part of the sky, in front of where you are looking, and its own lean (same line = same place, seeded). */
	private static Anchor place(int idx, MinecraftClient mc, LyricsView.Cfg c) {
		Anchor a = new Anchor();
		a.line = idx;
		a.version = Lyrics.version;
		java.util.Random r = new java.util.Random(idx * 7919L + a.version * 104729L + 31L);
		float side = switch (c.position) {
			case 1 -> -1F;
			case 2 -> 1F;
			case 3 -> idx % 2 == 0 ? -1F : 1F;
			case 4 -> r.nextInt(3) - 1F;
			default -> 0F;
		};
		float spread = Math.max(0.05F, Math.min(0.40F, c.spread)) * 110F; // degrees to each side
		a.yaw = mc.player.getYaw() + side * spread + (r.nextFloat() - 0.5F) * 10F;
		a.pitch = -(Math.max(4F, c.height) + (r.nextFloat() - 0.4F) * 9F); // negative = up in the game
		float d = Math.max(0F, Math.min(1F, c.depth));
		a.tiltYaw = d * (side != 0 ? side * (12F + r.nextFloat() * 14F) : (r.nextBoolean() ? 1 : -1) * (10F + r.nextFloat() * 12F));
		a.tiltPitch = d * (-4F + r.nextFloat() * 16F);
		a.tiltRoll = d * (r.nextFloat() * 2F - 1F) * 8F;
		return a;
	}

	private static void draw(MatrixStack m, OrderedRenderCommandQueue queue, MinecraftClient mc, LyricsView.Cfg c, Lyrics.Line line, Anchor a, long pos, long now, float fade) {
		if (a.line < 0 || line.blank()) {
			return;
		}
		TextRenderer tr = mc.textRenderer;
		List<Lyrics.Chunk> ch = line.chunks();
		int n = ch.size(), current = LyricsView.currentChunk(ch, pos);
		double time = now / 1000.0;

		// widths and rows (same breaking as the screen version)
		float space = tr.getWidth(LyricsView.styled(" ", c)) * (c.script ? 1.6F : 1F);
		float[] cw = new float[n];
		float tot = 0F;
		for (int i = 0; i < n; i++) {
			cw[i] = tr.getWidth(LyricsView.styled(ch.get(i).text(), c));
			tot += cw[i] + space;
		}
		tot -= space;
		int wantRows = n <= 2 ? 1 : (n <= 6 ? 2 : 3);
		int[] rowOf = new int[n];
		float cum = 0F;
		int rows = 1;
		for (int i = 0; i < n; i++) {
			int r = Math.min(wantRows - 1, (int) ((cum + cw[i] / 2F) / (tot / wantRows + 0.001F)));
			rowOf[i] = Math.max(i == 0 ? 0 : rowOf[i - 1], r);
			rows = Math.max(rows, rowOf[i] + 1);
			cum += cw[i] + space;
		}
		float[] rowW = new float[rows], x = new float[n];
		for (int i = 0; i < n; i++) {
			x[i] = rowW[rowOf[i]];
			rowW[rowOf[i]] += cw[i] + space;
		}
		float widest = 1F;
		for (int r = 0; r < rows; r++) {
			widest = Math.max(widest, rowW[r] - space);
		}
		// size: the widest row spans this many degrees of the sky
		float widthDeg = (c.script ? 34F : 28F) * c.size;
		float dist = a.world ? a.dist : DIST;
		float s = (float) (2 * dist * Math.tan(Math.toRadians(widthDeg / 2))) / widest;
		float rowH = 11F * (c.script ? 1.35F : 1.25F);

		// the sheet: hangs at its direction, breathes a little, faces you, then leans by its own tilt
		float breathe = (float) Math.sin(time * 0.6 + a.line) * 0.6F;
		float yawD = a.yaw + breathe, pitchD = a.pitch + (float) Math.sin(time * 0.45 + 1.0 + a.line) * 0.4F - (1F - fade) * 3F;
		double yr = Math.toRadians(yawD), pr = Math.toRadians(pitchD);
		double dx = -Math.sin(yr) * Math.cos(pr), dy = -Math.sin(pr), dz = Math.cos(yr) * Math.cos(pr);
		m.push();
		if (a.world) {
			m.translate(a.wx - camX, a.wy - camY - (1F - fade) * 0.3F, a.wz - camZ); // stays in the world; rises a little as it fades
		} else {
			m.translate(dx * DIST, dy * DIST, dz * DIST);
		}
		m.multiply(new Quaternionf().rotateY((float) (Math.PI - yr)).rotateX((float) -pr));
		m.multiply(new Quaternionf().rotateY((float) Math.toRadians(a.tiltYaw)).rotateX((float) Math.toRadians(a.tiltPitch))
				.rotateZ((float) Math.toRadians(a.tiltRoll)));
		m.scale(s, -s, s);

		var layer = behindTerrain ? TextRenderer.TextLayerType.POLYGON_OFFSET : TextRenderer.TextLayerType.SEE_THROUGH;
		int light = 0xF000F0;
		int layers = LyricsView.backLayers(c, Math.min(2, Math.round(3F * Math.max(0F, Math.min(1F, c.depth))))); // few copies: the old 5 + glow made 10 draws per letter and lagged
		for (int i = 0; i < n; i++) {
			long since = pos - ch.get(i).start();
			if (since < 0) {
				continue; // not sung yet: it appears when it is sung
			}
			int r = rowOf[i];
			boolean sung = i == current;
			float rw = rowW[r] - space;
			float u0 = -rw / 2F + x[i];
			float vRow = (r - (rows - 1) / 2F) * rowH - rowH * 0.5F;
			String text = ch.get(i).text();
			int base0 = LyricsView.wordColour(c, i, n, time);
			int col = Gfx.blend(base0, 0xFFFFFFFF, sung ? (c.colourMode == 0 ? 0.4F : 0.25F) : 0F);
			if (since > (c.lAnim == 0 ? text.length() * 22L : 0L) + 320L && !text.isBlank()) {
				// the word is fully in: draw it as ONE text (not letter by letter), a fraction of the draw calls
				Text wt = LyricsView.styled(text, c);
				float wraw = tr.getWidth(wt);
				float wcu = u0 + wraw / 2F;
				float wur = rw <= 1F ? 0F : (wcu + rw / 2F) / (rw / 2F) - 1F;
				float wcv = vRow + wur * wur * 3F + (float) Math.sin(time * 1.5 + (i * 3) * 0.5) * 0.9F;
				float wal = fade * (sung ? 1F : 0.85F) * c.opacity;
				m.push();
				m.translate(wcu, wcv, 0);
				float wg = sung ? 1.1F : 1F;
				m.scale(wg, wg, 1F);
				m.translate(-wraw / 2F, -4F, 0);
				if (layers > 0) {
					int dark = LyricsView.backColour(c, base0);
					for (int l = layers; l >= 1; l--) {
						int a8 = LyricsView.alphaOf(dark, wal * (0.75F - 0.08F * l) * c.backingAlpha / 0.6F);
						if (((a8 >>> 24) & 255) < 6) {
							continue;
						}
						m.push();
						m.translate(0, 0, -0.55F * l);
						queue.submitText(m, 0, 0, wt.asOrderedText(), false, layer, light, a8, 0, 0);
						m.pop();
					}
				}
				if (sung) {
					int gl = LyricsView.alphaOf(c.colourMode == 0 ? c.highlight : base0, (c.script ? 0.30F : 0.22F) * c.glow * wal);
					for (int[] o : new int[][] {{-1, 0}, {1, 0}}) {
						m.push();
						m.translate(o[0] * 0.9F, o[1] * 0.9F, -0.2F);
						queue.submitText(m, 0, 0, wt.asOrderedText(), false, layer, light, gl, 0, 0);
						m.pop();
					}
				}
				int wa8 = LyricsView.alphaOf(col, wal);
				if (((wa8 >>> 24) & 255) >= 8) {
					queue.submitText(m, 0, 0, wt.asOrderedText(), LyricsView.backShadow(c), layer, light, wa8, 0, 0);
				}
				m.pop();
				continue;
			}
			for (int g = 0; g < text.length(); g++) {
				char chr = text.charAt(g);
				if (chr == ' ') {
					continue;
				}
				float inK = Math.max(0F, Math.min(1F, (since - (c.lAnim == 0 ? g * 22F : 0F)) / 300F));
				if (inK <= 0F) {
					continue;
				}
				inK = 1F - (1F - inK) * (1F - inK) * (1F - inK);
				Text t = LyricsView.styled(String.valueOf(chr), c);
				float raw = tr.getWidth(t);
				float off = tr.getWidth(LyricsView.styled(text.substring(0, g), c));
				float cu = u0 + off + raw / 2F;
				float ur = rw <= 1F ? 0F : (cu + rw / 2F) / (rw / 2F) - 1F;
				float cv = vRow + ur * ur * 3F + (1F - inK) * (c.lAnim == 0 ? 7F : c.lAnim == 2 ? 14F : 0F) + (float) Math.sin(time * 1.5 + (i * 3 + g) * 0.5) * 0.9F;
				float alpha = inK * fade * (sung ? 1F : 0.85F) * c.opacity;
				float grow = (sung ? 1.1F : 1F) * (c.lAnim == 3 ? 0.25F + 0.75F * inK : c.lAnim == 0 ? 0.6F + 0.4F * inK : 1F);
				m.push();
				m.translate(cu, cv, 0);
				m.scale(grow, grow, 1F);
				m.translate(-raw / 2F, -4F, 0);
				// thickness: dark copies behind the letter, so it reads as a solid slab when you walk around
				if (layers > 0) {
					int dark = LyricsView.backColour(c, base0);
					for (int l = layers; l >= 1; l--) {
						int a8 = LyricsView.alphaOf(dark, alpha * (0.75F - 0.08F * l) * c.backingAlpha / 0.6F);
						if (((a8 >>> 24) & 255) < 6) {
							continue;
						}
						m.push();
						m.translate(0, 0, -0.55F * l);
						queue.submitText(m, 0, 0, t.asOrderedText(), false, layer, light, a8, 0, 0);
						m.pop();
					}
				}
				if (sung) {
					// glow: soft coloured copies around the letter
					int gl = LyricsView.alphaOf(c.colourMode == 0 ? c.highlight : base0, (c.script ? 0.30F : 0.22F) * c.glow * alpha);
					for (int[] o : new int[][] {{-1, 0}, {1, 0}}) {
						m.push();
						m.translate(o[0] * 0.9F, o[1] * 0.9F, -0.2F);
						queue.submitText(m, 0, 0, t.asOrderedText(), false, layer, light, gl, 0, 0);
						m.pop();
					}
				}
				int a8 = LyricsView.alphaOf(col, alpha);
				if (((a8 >>> 24) & 255) >= 8) {
					queue.submitText(m, 0, 0, t.asOrderedText(), LyricsView.backShadow(c), layer, light, a8, 0, 0);
				}
				m.pop();
			}
		}
		m.pop();
	}
}
