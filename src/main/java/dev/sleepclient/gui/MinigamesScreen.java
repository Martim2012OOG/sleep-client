package dev.sleepclient.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sleepclient.Gfx;
import dev.sleepclient.Keys;
import dev.sleepclient.Lang;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

/**
 * Minigames (like LabyMod's): Snake, 2048, Sleep Bird, Breakout, Stack and Tetris, playable from the menus while you
 * wait in a queue. Best scores are kept in config/kubo-minigames.json.
 */
public class MinigamesScreen extends Screen {

	// ----------------------------------------------------------------- scores
	private static final Map<String, Integer> BEST = new HashMap<>();
	private static boolean loaded;

	private static Path file() {
		return MinecraftClient.getInstance().runDirectory.toPath().resolve("config").resolve("kubo-minigames.json");
	}

	private static void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		try {
			Path f = file();
			if (Files.exists(f)) {
				JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
				for (String k : o.keySet()) {
					BEST.put(k, o.get(k).getAsInt());
				}
			}
		} catch (Exception e) {
			System.err.println("[Sleep] minigames scores: " + e);
		}
	}

	private static void save() {
		try {
			JsonObject o = new JsonObject();
			BEST.forEach(o::addProperty);
			Path f = file();
			Files.createDirectories(f.getParent());
			Files.writeString(f, o.toString(), StandardCharsets.UTF_8);
		} catch (Exception e) {
			System.err.println("[Sleep] minigames scores: " + e);
		}
	}

	static int best(String id) {
		load();
		return BEST.getOrDefault(id, 0);
	}

	public static int bestScore(String id) {
		return best(id);
	}

	/** Returns true if it is a new best. */
	static boolean submit(String id, int score) {
		load();
		if (score > BEST.getOrDefault(id, 0)) {
			BEST.put(id, score);
			save();
			dev.sleepclient.ext.KuboApi.statsChanged();
			return true;
		}
		return false;
	}

	// ------------------------------------------------------------------ games
	abstract static class Game {
		final String id, name, about;
		final int accent;
		int score;
		boolean over, paused, newBest, started;
		final Random rnd = new Random();

		Game(String id, String name, String about, int accent) {
			this.id = id;
			this.name = name;
			this.about = about;
			this.accent = accent;
		}

		abstract int gw();

		abstract int gh();

		abstract void reset();

		/** dt in seconds (already clamped). */
		abstract void update(float dt);

		abstract void render(DrawContext c, int x, int y, float mx, float my);

		abstract String controls();

		void key(int k) {
		}

		void click(float mx, float my, int button) {
		}

		void end() {
			if (!over) {
				over = true;
				newBest = submit(id, score);
			}
		}

		/** Little picture for the card in the menu (w x h box). */
		abstract void icon(DrawContext c, int x, int y, int w, int h, float t);
	}

	static void cell(DrawContext c, float x, float y, float s, int col) {
		Gfx.rrect(c, Math.round(x), Math.round(y), Math.max(1, Math.round(s)), Math.max(1, Math.round(s)), Math.max(1, Math.round(s / 4)), col);
	}

	static int hsv(float h, float s, float v) {
		return 0xFF000000 | java.awt.Color.HSBtoRGB(h, s, v) & 0xFFFFFF;
	}

	// ================================================================== SNAKE
	static final class Snake extends Game {
		static final int W = 21, H = 15, S = 12;
		final List<int[]> body = new ArrayList<>();
		int dx = 1, dy = 0, ndx = 1, ndy = 0, ax, ay;
		float acc, step;
		final java.util.ArrayDeque<int[]> turns = new java.util.ArrayDeque<>();

		Snake() {
			super("snake", "Snake", "Eat apples, don't bite yourself", 0xFF4ADE80);
		}

		int gw() {
			return W * S;
		}

		int gh() {
			return H * S;
		}

		void reset() {
			body.clear();
			for (int i = 0; i < 4; i++) {
				body.add(new int[] {6 - i, H / 2});
			}
			dx = ndx = 1;
			dy = ndy = 0;
			turns.clear();
			score = 0;
			step = 0.13F;
			over = false;
			apple();
		}

		void apple() {
			while (true) {
				ax = rnd.nextInt(W);
				ay = rnd.nextInt(H);
				boolean ok = true;
				for (int[] b : body) {
					if (b[0] == ax && b[1] == ay) {
						ok = false;
						break;
					}
				}
				if (ok) {
					return;
				}
			}
		}

		void key(int k) {
			int[] d = k == 263 || k == 65 ? new int[] {-1, 0} : k == 262 || k == 68 ? new int[] {1, 0} : k == 265 || k == 87 ? new int[] {0, -1}
					: k == 264 || k == 83 ? new int[] {0, 1} : null;
			if (d != null && turns.size() < 3) {
				turns.add(d);
				started = true;
			}
		}

		void update(float dt) {
			if (!started) {
				return;
			}
			acc += dt;
			while (acc >= step && !over) {
				acc -= step;
				int[] t;
				while ((t = turns.poll()) != null) {
					if (t[0] != -dx || t[1] != -dy) {
						if (t[0] != dx || t[1] != dy) {
							dx = t[0];
							dy = t[1];
							break;
						}
					}
				}
				int[] h = body.get(0);
				int nx = h[0] + dx, ny = h[1] + dy;
				if (nx < 0 || ny < 0 || nx >= W || ny >= H) {
					end();
					return;
				}
				boolean eat = nx == ax && ny == ay;
				for (int i = 0; i < body.size() - (eat ? 0 : 1); i++) {
					if (body.get(i)[0] == nx && body.get(i)[1] == ny) {
						end();
						return;
					}
				}
				body.add(0, new int[] {nx, ny});
				if (eat) {
					score++;
					step = Math.max(0.06F, step - 0.003F);
					apple();
				} else {
					body.remove(body.size() - 1);
				}
			}
		}

		void render(DrawContext c, int x, int y, float mx, float my) {
			for (int gy = 0; gy < H; gy++) {
				for (int gx = 0; gx < W; gx++) {
					if (((gx + gy) & 1) == 0) {
						c.fill(x + gx * S, y + gy * S, x + gx * S + S, y + gy * S + S, 0x08FFFFFF);
					}
				}
			}
			float pulse = 1 + 0.12F * (float) Math.sin(System.currentTimeMillis() / 160.0);
			float as = (S - 2) * pulse;
			Gfx.circle(c, x + ax * S + S / 2F, y + ay * S + S / 2F, as / 2, 0xFFFF5C6C);
			c.fill(x + ax * S + S / 2, y + ay * S - 1, x + ax * S + S / 2 + 2, y + ay * S + 2, 0xFF4ADE80);
			for (int i = body.size() - 1; i >= 0; i--) {
				int[] b = body.get(i);
				float k = i / (float) Math.max(1, body.size());
				int col = Gfx.blend(0xFF4ADE80, 0xFF15803D, k);
				cell(c, x + b[0] * S + 1, y + b[1] * S + 1, S - 2, col);
				if (i == 0) {
					int ex = x + b[0] * S + S / 2 + dx * 2, ey = y + b[1] * S + S / 2 + dy * 2;
					c.fill(ex - 3 + dy * 0, ey - 3, ex - 1, ey - 1, 0xFF0B0C10);
					c.fill(ex + 1, ey + 1 - (dx != 0 ? 4 : 0), ex + 3, ey + 3 - (dx != 0 ? 4 : 0), 0xFF0B0C10);
				}
			}
			if (!started) {
				Gfx.textFit(c, "Press an arrow key to start", x + gw() / 2, gw() - 6, y + gh() / 2 + 20, 0xFFFFFFFF);
			}
		}

		String controls() {
			return "Arrows / WASD";
		}

		void icon(DrawContext c, int x, int y, int w, int h, float t) {
			int s = 8, n = 6;
			int ox = x + w / 2 - n * s / 2, oy = y + h / 2 - s / 2;
			for (int i = 0; i < n; i++) {
				int wob = (int) Math.round(Math.sin(t * 4 + i * 0.8) * 3);
				cell(c, ox + i * s, oy + wob, s - 1, Gfx.blend(0xFF15803D, 0xFF4ADE80, i / (float) n));
			}
			Gfx.circle(c, ox + n * s + 10, oy + 3, 4, 0xFFFF5C6C);
		}
	}

	// =================================================================== 2048
	static final class G2048 extends Game {
		final int[][] g = new int[4][4];
		final float[][] pop = new float[4][4];
		static final int S = 44, P = 6;

		G2048() {
			super("2048", "2048", "Slide and merge to reach 2048", 0xFFFCD34D);
		}

		int gw() {
			return 4 * S + 5 * P;
		}

		int gh() {
			return gw();
		}

		void reset() {
			for (int[] r : g) {
				java.util.Arrays.fill(r, 0);
			}
			score = 0;
			over = false;
			started = true;
			spawn();
			spawn();
		}

		void spawn() {
			List<int[]> free = new ArrayList<>();
			for (int y = 0; y < 4; y++) {
				for (int x = 0; x < 4; x++) {
					if (g[y][x] == 0) {
						free.add(new int[] {x, y});
					}
				}
			}
			if (!free.isEmpty()) {
				int[] p = free.get(rnd.nextInt(free.size()));
				g[p[1]][p[0]] = rnd.nextInt(10) == 0 ? 4 : 2;
				pop[p[1]][p[0]] = 0.01F;
			}
		}

		/** Slides one line towards index 0. */
		boolean slide(int[] line, int[] mergedAt) {
			int[] out = new int[4];
			int n = 0;
			boolean moved = false;
			int last = -1;
			for (int i = 0; i < 4; i++) {
				if (line[i] == 0) {
					continue;
				}
				if (last >= 0 && out[last] == line[i] && mergedAt[last] == 0) {
					out[last] *= 2;
					score += out[last];
					mergedAt[last] = 1;
				} else {
					out[n] = line[i];
					last = n++;
				}
			}
			for (int i = 0; i < 4; i++) {
				if (out[i] != line[i]) {
					moved = true;
				}
				line[i] = out[i];
			}
			return moved;
		}

		void key(int k) {
			int dir = k == 263 || k == 65 ? 0 : k == 262 || k == 68 ? 1 : k == 265 || k == 87 ? 2 : k == 264 || k == 83 ? 3 : -1;
			if (dir < 0 || over) {
				return;
			}
			boolean moved = false;
			for (int a = 0; a < 4; a++) {
				int[] line = new int[4];
				int[] merged = new int[4];
				for (int i = 0; i < 4; i++) {
					int[] p = pos(dir, a, i);
					line[i] = g[p[1]][p[0]];
				}
				if (slide(line, merged)) {
					moved = true;
				}
				for (int i = 0; i < 4; i++) {
					int[] p = pos(dir, a, i);
					g[p[1]][p[0]] = line[i];
					if (merged[i] == 1) {
						pop[p[1]][p[0]] = 0.01F;
					}
				}
			}
			if (moved) {
				spawn();
				if (!canMove()) {
					end();
				}
			}
		}

		/** Cell for direction (0 left, 1 right, 2 up, 3 down), line a, index i from the wall. */
		static int[] pos(int dir, int a, int i) {
			return switch (dir) {
				case 0 -> new int[] {i, a};
				case 1 -> new int[] {3 - i, a};
				case 2 -> new int[] {a, i};
				default -> new int[] {a, 3 - i};
			};
		}

		boolean canMove() {
			for (int y = 0; y < 4; y++) {
				for (int x = 0; x < 4; x++) {
					if (g[y][x] == 0 || x < 3 && g[y][x] == g[y][x + 1] || y < 3 && g[y][x] == g[y + 1][x]) {
						return true;
					}
				}
			}
			return false;
		}

		void update(float dt) {
			for (int y = 0; y < 4; y++) {
				for (int x = 0; x < 4; x++) {
					if (pop[y][x] > 0) {
						pop[y][x] += dt * 6;
						if (pop[y][x] >= 1) {
							pop[y][x] = 0;
						}
					}
				}
			}
		}

		static int colour(int v) {
			int lv = v <= 0 ? 0 : 31 - Integer.numberOfLeadingZeros(v); // 2 -> 1
			int[] cols = {0xFF2A2D38, 0xFF3B3F4E, 0xFF4B4F63, 0xFFF59E0B, 0xFFF97316, 0xFFEF4444, 0xFFDC2626, 0xFFFACC15, 0xFFEAB308, 0xFFA3E635,
					0xFF22C55E, 0xFF06B6D4, 0xFF8B5CF6};
			return cols[Math.min(cols.length - 1, lv)];
		}

		void render(DrawContext c, int x, int y, float mx, float my) {
			Gfx.rrect(c, x, y, gw(), gh(), 10, 0xFF1A1C24);
			var tr = MinecraftClient.getInstance().textRenderer;
			for (int gy = 0; gy < 4; gy++) {
				for (int gx = 0; gx < 4; gx++) {
					int tx = x + P + gx * (S + P), ty = y + P + gy * (S + P);
					Gfx.rrect(c, tx, ty, S, S, 7, 0xFF23262F);
					int v = g[gy][gx];
					if (v == 0) {
						continue;
					}
					float p = pop[gy][gx];
					float sc = p > 0 ? 1 + 0.15F * (float) Math.sin(p * Math.PI) : 1;
					int s = Math.round(S * sc), o = (S - s) / 2;
					Gfx.rrect(c, tx + o, ty + o, s, s, 7, colour(v));
					String str = Integer.toString(v);
					float ts = str.length() <= 2 ? 1.6F : str.length() == 3 ? 1.3F : 1.0F;
					var m = c.getMatrices();
					m.pushMatrix();
					m.translate(tx + S / 2F - Gfx.twRaw(str) * ts / 2F, ty + S / 2F - 4.5F * ts);
					m.scale(ts, ts);
					c.drawText(tr, Gfx.raw(str), 0, 0, v <= 4 ? 0xFFE6E8EE : 0xFFFFFFFF, v > 4);
					m.popMatrix();
				}
			}
		}

		String controls() {
			return "Arrows / WASD";
		}

		void icon(DrawContext c, int x, int y, int w, int h, float t) {
			int s = 14, ox = x + w / 2 - s - 2, oy = y + h / 2 - s - 2;
			int[] v = {2, 4, 8, 2048};
			for (int i = 0; i < 4; i++) {
				int cx = ox + (i % 2) * (s + 4), cy = oy + (i / 2) * (s + 4);
				Gfx.rrect(c, cx, cy, s, s, 3, colour(v[i]));
			}
		}
	}

	// ============================================================== KUBO BIRD
	static final class Bird extends Game {
		static final int W = 260, H = 200;
		float by, vy, t;
		final List<float[]> pipes = new ArrayList<>(); // x, gapY, passed
		float spawnIn;

		Bird() {
			super("bird", "Sleep Bird", "Flap through the pipes", 0xFF7DD3FC);
		}

		int gw() {
			return W;
		}

		int gh() {
			return H;
		}

		void reset() {
			by = H / 2F;
			vy = 0;
			pipes.clear();
			spawnIn = 0.6F;
			score = 0;
			over = false;
			started = false;
		}

		void flap() {
			if (over) {
				return;
			}
			started = true;
			vy = -165;
		}

		void key(int k) {
			if (k == 32 || k == 265 || k == 87) {
				flap();
			}
		}

		void click(float mx, float my, int b) {
			flap();
		}

		void update(float dt) {
			t += dt;
			if (!started || over) {
				return;
			}
			vy += 520 * dt;
			by += vy * dt;
			spawnIn -= dt;
			if (spawnIn <= 0) {
				spawnIn = 1.45F;
				pipes.add(new float[] {W + 20, 45 + rnd.nextFloat() * (H - 110), 0});
			}
			for (int i = pipes.size() - 1; i >= 0; i--) {
				float[] p = pipes.get(i);
				p[0] -= 85 * dt;
				if (p[0] < -40) {
					pipes.remove(i);
					continue;
				}
				if (p[2] == 0 && p[0] + 26 < 60) {
					p[2] = 1;
					score++;
				}
				// hit test: bird is a circle at (60, by) r 7
				if (60 + 7 > p[0] && 60 - 7 < p[0] + 26 && (by - 7 < p[1] - 31 || by + 7 > p[1] + 31)) {
					end();
				}
			}
			if (by > H - 14 || by < -20) {
				end();
			}
		}

		void render(DrawContext c, int x, int y, float mx, float my) {
			c.fillGradient(x, y, x + W, y + H, 0xFF1E3A5F, 0xFF3B6EA5);
			// clouds
			for (int i = 0; i < 4; i++) {
				float cx = ((i * 83 - t * 12) % (W + 60) + W + 60) % (W + 60) - 30;
				Gfx.rrect(c, x + Math.round(cx), y + 20 + i * 17, 34, 9, 4, 0x22FFFFFF);
			}
			for (float[] p : pipes) {
				int px = x + Math.round(p[0]);
				int top = y + Math.round(p[1] - 31), bot = y + Math.round(p[1] + 31);
				c.fill(px, y, px + 26, top, 0xFF22C55E);
				c.fill(px - 2, top - 8, px + 28, top, 0xFF16A34A);
				c.fill(px, bot, px + 26, y + H - 10, 0xFF22C55E);
				c.fill(px - 2, bot, px + 28, bot + 8, 0xFF16A34A);
				c.fill(px + 4, y, px + 7, top - 8, 0x33FFFFFF);
				c.fill(px + 4, bot + 8, px + 7, y + H - 10, 0x33FFFFFF);
			}
			c.fill(x, y + H - 10, x + W, y + H, 0xFFB45309);
			c.fill(x, y + H - 10, x + W, y + H - 8, 0xFF65A30D);
			// bird: a little Sleep cube that tilts with speed
			float ang = Math.max(-0.5F, Math.min(1.2F, vy / 300F));
			var m = c.getMatrices();
			m.pushMatrix();
			m.translate(x + 60, y + by + (started ? 0 : (float) Math.sin(t * 5) * 3));
			m.rotate(ang);
			Gfx.rrect(c, -8, -7, 16, 14, 4, 0xFFFCD34D);
			c.fill(1, -4, 6, 1, 0xFFFFFFFF);
			c.fill(3, -3, 5, 0, 0xFF0B0C10);
			c.fill(6, 0, 11, 3, 0xFFF97316);
			c.fill(-7, (int) (Math.sin(t * 20) * 2), -1, 3 + (int) (Math.sin(t * 20) * 2), 0xFFF59E0B);
			m.popMatrix();
			if (!started) {
				Gfx.textFit(c, "Space or click to flap", x + W / 2, W - 6, y + H / 2 + 26, 0xFFFFFFFF);
			}
			String sc = Integer.toString(score);
			var mm = c.getMatrices();
			mm.pushMatrix();
			mm.translate(x + W / 2F - Gfx.twRaw(sc), y + 12);
			mm.scale(2, 2);
			c.drawText(MinecraftClient.getInstance().textRenderer, Gfx.raw(sc), 0, 0, 0xFFFFFFFF, true);
			mm.popMatrix();
		}

		String controls() {
			return "Space / Click";
		}

		void icon(DrawContext c, int x, int y, int w, int h, float t) {
			c.fill(x + w - 26, y + 4, x + w - 16, y + h / 2 - 8, 0xFF22C55E);
			c.fill(x + w - 26, y + h / 2 + 10, x + w - 16, y + h - 4, 0xFF22C55E);
			int by = y + h / 2 + (int) Math.round(Math.sin(t * 5) * 4);
			Gfx.rrect(c, x + w / 2 - 14, by - 6, 14, 12, 3, 0xFFFCD34D);
			c.fill(x + w / 2 - 6, by - 3, x + w / 2 - 3, by, 0xFF0B0C10);
			c.fill(x + w / 2, by, x + w / 2 + 4, by + 3, 0xFFF97316);
		}
	}

	// =============================================================== BREAKOUT
	static final class Breakout extends Game {
		static final int W = 260, H = 200, COLS = 10, ROWS = 6;
		final boolean[][] bricks = new boolean[ROWS][COLS];
		float padX, bx, byy, vx, vy;
		int lives, level;
		boolean stuck;

		Breakout() {
			super("breakout", "Breakout", "Break every brick", 0xFFF472B6);
		}

		int gw() {
			return W;
		}

		int gh() {
			return H;
		}

		void reset() {
			score = 0;
			lives = 3;
			level = 1;
			over = false;
			fill();
			serve();
		}

		void fill() {
			for (boolean[] r : bricks) {
				java.util.Arrays.fill(r, true);
			}
		}

		void serve() {
			padX = W / 2F;
			stuck = true;
			bx = padX;
			byy = H - 22;
			vx = 0;
			vy = 0;
		}

		void launch() {
			if (stuck && !over) {
				stuck = false;
				started = true;
				float sp = 150 + level * 15;
				vx = sp * (rnd.nextBoolean() ? 0.55F : -0.55F);
				vy = -sp * 0.83F;
			}
		}

		void key(int k) {
			if (k == 32 || k == 265) {
				launch();
			}
		}

		void click(float mx, float my, int b) {
			launch();
		}

		float mouseX = -1, lastMouse = -1;

		void update(float dt) {
			if (over) {
				return;
			}
			if (Keys.down(263) || Keys.down(65)) {
				padX -= 240 * dt;
			}
			if (Keys.down(262) || Keys.down(68)) {
				padX += 240 * dt;
			}
			if (mouseX >= 0 && mouseX != lastMouse) {
				padX = mouseX;
				lastMouse = mouseX;
			}
			padX = Math.max(22, Math.min(W - 22, padX));
			if (stuck) {
				bx = padX;
				return;
			}
			// small sub-steps so the ball never tunnels through a brick
			int n = 4;
			for (int s = 0; s < n; s++) {
				float d = dt / n;
				bx += vx * d;
				byy += vy * d;
				if (bx < 4) {
					bx = 4;
					vx = Math.abs(vx);
				}
				if (bx > W - 4) {
					bx = W - 4;
					vx = -Math.abs(vx);
				}
				if (byy < 4) {
					byy = 4;
					vy = Math.abs(vy);
				}
				if (vy > 0 && byy > H - 18 && byy < H - 10 && Math.abs(bx - padX) < 24) {
					float off = (bx - padX) / 24F;
					float sp = (float) Math.hypot(vx, vy) * 1.01F;
					vx = sp * off * 0.85F;
					vy = -(float) Math.sqrt(Math.max(1, sp * sp - vx * vx));
				}
				int bw = W / COLS;
				int cx = (int) (bx / bw), cy = (int) ((byy - 24) / 10);
				if (byy >= 24 && cy >= 0 && cy < ROWS && cx >= 0 && cx < COLS && bricks[cy][cx]) {
					bricks[cy][cx] = false;
					score += (ROWS - cy) * 10;
					vy = -vy;
					if (allGone()) {
						level++;
						fill();
						serve();
						return;
					}
				}
			}
			if (byy > H + 6) {
				lives--;
				if (lives <= 0) {
					end();
				} else {
					serve();
				}
			}
		}

		boolean allGone() {
			for (boolean[] r : bricks) {
				for (boolean b : r) {
					if (b) {
						return false;
					}
				}
			}
			return true;
		}

		void render(DrawContext c, int x, int y, float mx, float my) {
			mouseX = mx >= 0 && mx <= W && my >= -20 && my <= H + 20 ? mx : -1;
			c.fill(x, y, x + W, y + H, 0xFF12131A);
			int bw = W / COLS;
			for (int r = 0; r < ROWS; r++) {
				int col = hsv(0.92F - r * 0.09F, 0.6F, 0.95F);
				for (int k = 0; k < COLS; k++) {
					if (bricks[r][k]) {
						Gfx.rrect(c, x + k * bw + 1, y + 24 + r * 10 + 1, bw - 2, 8, 2, col);
						c.fill(x + k * bw + 3, y + 24 + r * 10 + 2, x + k * bw + bw - 3, y + 24 + r * 10 + 3, 0x44FFFFFF);
					}
				}
			}
			Gfx.rrect(c, x + Math.round(padX) - 22, y + H - 14, 44, 6, 3, 0xFFE6E8EE);
			Gfx.circle(c, x + bx, y + byy, 3.5F, 0xFFFFFFFF);
			for (int i = 0; i < lives; i++) {
				Gfx.circle(c, x + 8 + i * 10, y + 9, 3, 0xFFFF5C8A);
			}
			Gfx.textRaw(c, "Lv " + level, x + W - 8 - Gfx.twRaw("Lv " + level), y + 5, 0xFF8C90A0);
			if (stuck && !over) {
				Gfx.textFit(c, "Space or click to launch", x + W / 2, W - 6, y + H / 2 + 20, 0xFFFFFFFF);
			}
		}

		String controls() {
			return "Mouse / Arrows, Space";
		}

		void icon(DrawContext c, int x, int y, int w, int h, float t) {
			for (int r = 0; r < 3; r++) {
				for (int k = 0; k < 5; k++) {
					if ((r + k) % 4 != 3) {
						c.fill(x + w / 2 - 30 + k * 12, y + 8 + r * 7, x + w / 2 - 30 + k * 12 + 11, y + 14 + r * 7, hsv(0.92F - r * 0.1F, 0.6F, 0.95F));
					}
				}
			}
			float bxx = x + w / 2F + (float) Math.sin(t * 2) * 18;
			Gfx.circle(c, bxx, y + h - 16 - Math.abs((float) Math.sin(t * 3)) * 10, 3, 0xFFFFFFFF);
			Gfx.rrect(c, Math.round(bxx) - 12, y + h - 9, 24, 4, 2, 0xFFE6E8EE);
		}
	}

	// ================================================================== STACK
	static final class Stack extends Game {
		static final int W = 220, H = 220, BH = 12;
		final List<float[]> blocks = new ArrayList<>(); // x, w, hue
		final List<float[]> falling = new ArrayList<>(); // x, y, w, vy, hue
		float cur, curW, dir, speed, camY, camTarget, perfectFlash;
		int combo;

		Stack() {
			super("stack", "Stack", "Drop blocks to build the tallest tower", 0xFFA78BFA);
		}

		int gw() {
			return W;
		}

		int gh() {
			return H;
		}

		void reset() {
			blocks.clear();
			falling.clear();
			blocks.add(new float[] {W / 2F - 50, 100, rnd.nextFloat()});
			curW = 100;
			cur = 0;
			dir = 1;
			speed = 90;
			score = 0;
			combo = 0;
			camY = camTarget = 0;
			over = false;
		}

		void drop() {
			if (over) {
				return;
			}
			started = true;
			float[] top = blocks.get(blocks.size() - 1);
			float l = Math.max(cur, top[0]), r = Math.min(cur + curW, top[0] + top[1]);
			float hue = (top[2] + 0.035F) % 1F;
			if (r - l <= 0) {
				falling.add(new float[] {cur, blocks.size(), curW, 0, hue});
				end();
				return;
			}
			if (Math.abs(cur - top[0]) < 3) {
				// perfect: snaps and grows back a bit after a streak
				l = top[0];
				r = top[0] + top[1];
				combo++;
				perfectFlash = 1;
				if (combo >= 3) {
					float grow = Math.min(6, 100 - (r - l));
					l -= grow / 2;
					r += grow / 2;
				}
			} else {
				combo = 0;
				if (cur < l) {
					falling.add(new float[] {cur, blocks.size(), l - cur, 0, hue});
				}
				if (cur + curW > r) {
					falling.add(new float[] {r, blocks.size(), cur + curW - r, 0, hue});
				}
			}
			blocks.add(new float[] {l, r - l, hue});
			score++;
			curW = r - l;
			dir = blocks.size() % 2 == 0 ? 1 : -1;
			cur = dir > 0 ? -curW : W;
			speed = Math.min(220, 90 + score * 4);
			camTarget = Math.max(0, (blocks.size() - 9) * BH);
		}

		void key(int k) {
			if (k == 32 || k == 264 || k == 257) {
				drop();
			}
		}

		void click(float mx, float my, int b) {
			drop();
		}

		void update(float dt) {
			camY += (camTarget - camY) * Math.min(1, dt * 6);
			perfectFlash = Math.max(0, perfectFlash - dt * 2);
			for (int i = falling.size() - 1; i >= 0; i--) {
				float[] f = falling.get(i);
				f[3] += 30 * dt;
				f[1] -= f[3] * dt;
				if (f[1] < -30) {
					falling.remove(i);
				}
			}
			if (over || !started && blocks.size() > 1) {
				return;
			}
			cur += dir * speed * dt;
			if (cur > W - curW / 3 && dir > 0 || cur < -curW * 2 / 3 && dir < 0) {
				dir = -dir;
			}
		}

		int yOf(float level) {
			return Math.round(H - 20 - (level + 1) * BH + camY);
		}

		void render(DrawContext c, int x, int y, float mx, float my) {
			float topHue = blocks.get(blocks.size() - 1)[2];
			c.fillGradient(x, y, x + W, y + H, hsv(topHue, 0.45F, 0.28F), hsv((topHue + 0.08F) % 1, 0.5F, 0.12F));
			c.enableScissor(x, y, x + W, y + H);
			for (int i = 0; i < blocks.size(); i++) {
				float[] b = blocks.get(i);
				int yy = y + yOf(i);
				if (yy > y + H || yy + BH < y) {
					continue;
				}
				c.fill(x + Math.round(b[0]), yy, x + Math.round(b[0] + b[1]), yy + BH, hsv(b[2], 0.55F, 0.92F));
				c.fill(x + Math.round(b[0]), yy, x + Math.round(b[0] + b[1]), yy + 2, hsv(b[2], 0.35F, 1F));
				c.fill(x + Math.round(b[0]), yy + BH - 2, x + Math.round(b[0] + b[1]), yy + BH, hsv(b[2], 0.6F, 0.7F));
			}
			for (float[] f : falling) {
				int yy = y + Math.round(H - 20 - (f[1] + 1) * BH + camY);
				c.fill(x + Math.round(f[0]), yy, x + Math.round(f[0] + f[2]), yy + BH, Gfx.withAlpha(hsv(f[4], 0.55F, 0.85F), 200));
			}
			if (!over) {
				float hue = (topHue + 0.035F) % 1F;
				int yy = y + yOf(blocks.size());
				c.fill(x + Math.round(cur), yy, x + Math.round(cur + curW), yy + BH, hsv(hue, 0.55F, 0.95F));
				c.fill(x + Math.round(cur), yy, x + Math.round(cur + curW), yy + 2, hsv(hue, 0.35F, 1F));
			}
			if (perfectFlash > 0) {
				int yy = y + yOf(blocks.size() - 1);
				float[] b = blocks.get(blocks.size() - 1);
				int a = Math.round(perfectFlash * 200);
				Gfx.rrectBorder(c, x + Math.round(b[0]) - 3, yy - 3, Math.round(b[1]) + 6, BH + 6, 3, 0x00000000, Gfx.withAlpha(0xFFFFFF, a));
				Gfx.textFit(c, combo >= 3 ? "PERFECT x" + combo : "PERFECT", x + W / 2, W - 6, yy - 16, Gfx.withAlpha(0xFFFFFF, a));
			}
			c.disableScissor();
			String sc = Integer.toString(score);
			var m = c.getMatrices();
			m.pushMatrix();
			m.translate(x + W / 2F - Gfx.twRaw(sc) * 1.5F, y + 10);
			m.scale(3, 3);
			c.drawText(MinecraftClient.getInstance().textRenderer, Gfx.raw(sc), 0, 0, 0xDDFFFFFF, true);
			m.popMatrix();
			if (!started) {
				Gfx.textFit(c, "Space or click to drop", x + W / 2, W - 6, y + 48, 0xFFFFFFFF);
			}
		}

		String controls() {
			return "Space / Click";
		}

		void icon(DrawContext c, int x, int y, int w, int h, float t) {
			for (int i = 0; i < 5; i++) {
				int bw = 44 - i * 4, bx = x + w / 2 - bw / 2 + (i == 4 ? (int) Math.round(Math.sin(t * 2.5) * 12) : (i % 2) * 2);
				int by = y + h - 8 - (i + 1) * 7;
				c.fill(bx, by, bx + bw, by + 6, hsv(0.7F + i * 0.04F, 0.55F, 0.92F));
			}
		}
	}

	// ================================================================= TETRIS
	static final class Tetris extends Game {
		static final int W = 10, H = 20, S = 10;
		static final int[][][] SHAPES = {
				{{0, 1}, {1, 1}, {2, 1}, {3, 1}}, // I
				{{0, 0}, {1, 0}, {0, 1}, {1, 1}}, // O
				{{1, 0}, {0, 1}, {1, 1}, {2, 1}}, // T
				{{1, 0}, {2, 0}, {0, 1}, {1, 1}}, // S
				{{0, 0}, {1, 0}, {1, 1}, {2, 1}}, // Z
				{{0, 0}, {0, 1}, {1, 1}, {2, 1}}, // J
				{{2, 0}, {0, 1}, {1, 1}, {2, 1}}}; // L
		static final int[] COLS = {0xFF22D3EE, 0xFFFACC15, 0xFFA855F7, 0xFF4ADE80, 0xFFF87171, 0xFF60A5FA, 0xFFFB923C};
		final int[][] board = new int[H][W];
		int[][] piece;
		int type, px, py, next, lines, level, hold = -1;
		boolean held;
		float fall, lockT;
		final List<Integer> bag = new ArrayList<>();
		final List<float[]> flashRows = new ArrayList<>();

		Tetris() {
			super("tetris", "Tetris", "Clear lines, don't reach the top", 0xFF60A5FA);
		}

		int gw() {
			return W * S + 70;
		}

		int gh() {
			return H * S;
		}

		void reset() {
			for (int[] r : board) {
				java.util.Arrays.fill(r, 0);
			}
			bag.clear();
			flashRows.clear();
			score = lines = 0;
			level = 1;
			hold = -1;
			over = false;
			started = true;
			next = draw();
			spawn();
		}

		int draw() {
			if (bag.isEmpty()) {
				for (int i = 0; i < 7; i++) {
					bag.add(i);
				}
				java.util.Collections.shuffle(bag, rnd);
			}
			return bag.remove(0);
		}

		void spawn() {
			spawn(next);
			next = draw();
		}

		void spawn(int t) {
			type = t;
			piece = new int[4][];
			for (int i = 0; i < 4; i++) {
				piece[i] = SHAPES[t].clone()[i].clone();
			}
			px = 3;
			py = 0;
			held = false;
			fall = 0;
			lockT = 0;
			if (!fits(piece, px, py)) {
				end();
			}
		}

		boolean fits(int[][] p, int ox, int oy) {
			for (int[] b : p) {
				int x = b[0] + ox, y = b[1] + oy;
				if (x < 0 || x >= W || y >= H || y >= 0 && board[y][x] != 0) {
					return false;
				}
			}
			return true;
		}

		void rotate(int dir) {
			if (type == 1) {
				return;
			}
			int size = type == 0 ? 4 : 3;
			int[][] r = new int[4][];
			for (int i = 0; i < 4; i++) {
				int x = piece[i][0], y = piece[i][1];
				r[i] = dir > 0 ? new int[] {size - 1 - y, x} : new int[] {y, size - 1 - x};
			}
			for (int kick : new int[] {0, -1, 1, -2, 2}) {
				if (fits(r, px + kick, py)) {
					piece = r;
					px += kick;
					lockT = 0;
					return;
				}
			}
		}

		void lock() {
			for (int[] b : piece) {
				int y = b[1] + py;
				if (y < 0) {
					end();
					return;
				}
				board[y][b[0] + px] = type + 1;
			}
			int cleared = 0;
			for (int y = H - 1; y >= 0; y--) {
				boolean full = true;
				for (int x = 0; x < W; x++) {
					if (board[y][x] == 0) {
						full = false;
						break;
					}
				}
				if (full) {
					flashRows.add(new float[] {y + cleared, 1});
					for (int yy = y; yy > 0; yy--) {
						board[yy] = board[yy - 1].clone();
					}
					board[0] = new int[W];
					cleared++;
					y++;
				}
			}
			if (cleared > 0) {
				lines += cleared;
				score += new int[] {0, 100, 300, 500, 800}[cleared] * level;
				level = 1 + lines / 10;
			}
			spawn();
		}

		void key(int k) {
			if (over) {
				return;
			}
			if (k == 263 || k == 65) {
				if (fits(piece, px - 1, py)) {
					px--;
					lockT = 0;
				}
			} else if (k == 262 || k == 68) {
				if (fits(piece, px + 1, py)) {
					px++;
					lockT = 0;
				}
			} else if (k == 265 || k == 87 || k == 88) {
				rotate(1);
			} else if (k == 90) {
				rotate(-1);
			} else if (k == 264 || k == 83) {
				if (fits(piece, px, py + 1)) {
					py++;
					score += 1;
				}
			} else if (k == 32) {
				int d = 0;
				while (fits(piece, px, py + 1)) {
					py++;
					d++;
				}
				score += d * 2;
				lock();
			} else if ((k == 67 || k == 340) && !held) {
				int cur = type;
				if (hold < 0) {
					hold = cur;
					spawn();
				} else {
					int h = hold;
					hold = cur;
					spawn(h);
				}
				held = true;
			}
		}

		void update(float dt) {
			for (int i = flashRows.size() - 1; i >= 0; i--) {
				flashRows.get(i)[1] -= dt * 3;
				if (flashRows.get(i)[1] <= 0) {
					flashRows.remove(i);
				}
			}
			if (over) {
				return;
			}
			float interval = Math.max(0.06F, 0.8F - (level - 1) * 0.07F);
			if (fits(piece, px, py + 1)) {
				fall += dt;
				lockT = 0;
				if (fall >= interval) {
					fall = 0;
					py++;
				}
			} else {
				lockT += dt;
				if (lockT > 0.45F) {
					lock();
				}
			}
		}

		void render(DrawContext c, int x, int y, float mx, float my) {
			Gfx.rrect(c, x - 3, y - 3, W * S + 6, H * S + 6, 4, 0xFF0D0E13);
			for (int gy = 0; gy < H; gy++) {
				for (int gx = 0; gx < W; gx++) {
					c.fill(x + gx * S, y + gy * S, x + gx * S + S, y + gy * S + S, ((gx + gy) & 1) == 0 ? 0xFF15161D : 0xFF13141A);
					int v = board[gy][gx];
					if (v != 0) {
						block(c, x + gx * S, y + gy * S, COLS[v - 1], 255);
					}
				}
			}
			for (float[] f : flashRows) {
				int yy = y + Math.round(f[0]) * S;
				c.fill(x, yy, x + W * S, yy + S, Gfx.withAlpha(0xFFFFFF, Math.round(f[1] * 180)));
			}
			if (!over) {
				int gy = py;
				while (fits(piece, px, gy + 1)) {
					gy++;
				}
				for (int[] b : piece) {
					if (b[1] + gy >= 0) {
						Gfx.rrectBorder(c, x + (b[0] + px) * S, y + (b[1] + gy) * S, S, S, 2, 0x00000000, Gfx.withAlpha(COLS[type], 120));
					}
				}
				for (int[] b : piece) {
					if (b[1] + py >= 0) {
						block(c, x + (b[0] + px) * S, y + (b[1] + py) * S, COLS[type], 255);
					}
				}
			}
			int sx = x + W * S + 12;
			Gfx.text(c, "NEXT", sx, y, 0xFF8C90A0);
			mini(c, next, sx, y + 12);
			Gfx.text(c, "HOLD", sx, y + 50, 0xFF8C90A0);
			if (hold >= 0) {
				mini(c, hold, sx, y + 62);
			}
			Gfx.text(c, "LINES", sx, y + 102, 0xFF8C90A0);
			Gfx.textRaw(c, Integer.toString(lines), sx, y + 113, 0xFFFFFFFF);
			Gfx.text(c, "LEVEL", sx, y + 130, 0xFF8C90A0);
			Gfx.textRaw(c, Integer.toString(level), sx, y + 141, 0xFFFFFFFF);
		}

		static void block(DrawContext c, int x, int y, int col, int a) {
			c.fill(x, y, x + S, y + S, Gfx.withAlpha(Gfx.blend(col, 0xFF000000, 0.25F), a));
			c.fill(x + 1, y + 1, x + S - 1, y + S - 1, Gfx.withAlpha(col, a));
			c.fill(x + 1, y + 1, x + S - 1, y + 2, Gfx.withAlpha(0xFFFFFF, a / 3));
		}

		void mini(DrawContext c, int t, int x, int y) {
			Gfx.rrect(c, x - 2, y - 2, 48, 30, 5, 0xFF1A1C24);
			for (int[] b : SHAPES[t]) {
				int s = 8;
				c.fill(x + 6 + b[0] * s, y + 6 + b[1] * s, x + 6 + b[0] * s + s - 1, y + 6 + b[1] * s + s - 1, COLS[t]);
			}
		}

		String controls() {
			return "←→ move  ↑ rotate  ↓ soft  Space drop  C hold";
		}

		void icon(DrawContext c, int x, int y, int w, int h, float t) {
			int s = 7, ox = x + w / 2 - 2 * s, oy = y + h - 6 - 4 * s;
			int[][] cells = {{0, 3, 0}, {1, 3, 0}, {2, 3, 2}, {3, 3, 2}, {0, 2, 0}, {3, 2, 2}, {1, 1, 6}, {1, 2, 6}, {2, 2, 6}, {1, 0, 6}};
			for (int[] b : cells) {
				c.fill(ox + b[0] * s, oy + b[1] * s, ox + b[0] * s + s - 1, oy + b[1] * s + s - 1, COLS[b[2]]);
			}
			int fy = oy - 10 + (int) ((t * 14) % 14);
			c.fill(ox + 2 * s, fy, ox + 3 * s - 1, fy + s - 1, COLS[3]);
			c.fill(ox + 3 * s, fy, ox + 4 * s - 1, fy + s - 1, COLS[3]);
		}
	}

	// ================================================================ screen
	private final Screen parent;
	private final List<Game> games = List.of(new Snake(), new G2048(), new Bird(), new Breakout(), new Stack(), new Tetris());
	private Game game;
	private long lastNs, openedAt;
	private float time;
	private int gx, gy, gs; // game box + scale
	private final List<int[]> cards = new ArrayList<>();
	private float backX, backY, againX, againY;

	public MinigamesScreen(Screen parent) {
		super(Text.literal("Minigames"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		openedAt = System.currentTimeMillis();
		load();
	}

	@Override
	public boolean shouldPause() {
		return true;
	}

	@Override
	public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void render(DrawContext c, int mx, int my, float delta) {
		long now = System.nanoTime();
		float dt = lastNs == 0 ? 0 : Math.min(0.05F, (now - lastNs) / 1e9F);
		lastNs = now;
		time += dt;
		c.fillGradient(0, 0, width, height, 0xF00B0C11, 0xF0141622);
		int accent = Theme.accent();
		if (game == null) {
			hub(c, mx, my, accent);
		} else {
			play(c, mx, my, dt, accent);
		}
	}

	private void hub(DrawContext c, int mx, int my, int accent) {
		float k = Math.min(1F, (System.currentTimeMillis() - openedAt) / 260F);
		k = 1 - (1 - k) * (1 - k);
		String title = "Minigames";
		var m = c.getMatrices();
		m.pushMatrix();
		m.translate(width / 2F - Gfx.tw(title), 22);
		m.scale(2, 2);
		Gfx.text(c, title, 0, 0, 0xFFFFFFFF);
		m.popMatrix();
		Gfx.textC(c, "Play something while you wait", width / 2, 44, Theme.DIM);
		backButton(c, mx, my, "Back");

		int cols = width >= 520 ? 3 : 2, cw = Math.min(150, (width - 40 - (cols - 1) * 10) / cols), ch = 112;
		int rows = (games.size() + cols - 1) / cols;
		int tx0 = width / 2 - (cols * cw + (cols - 1) * 10) / 2;
		int ty0 = Math.max(62, height / 2 - (rows * ch + (rows - 1) * 10) / 2 + 10);
		cards.clear();
		for (int i = 0; i < games.size(); i++) {
			Game g = games.get(i);
			int x = tx0 + (i % cols) * (cw + 10), y = ty0 + (i / cols) * (ch + 10) + Math.round((1 - k) * (12 + i * 4));
			boolean hov = mx >= x && mx < x + cw && my >= y && my < y + ch;
			Gfx.rrectBorder(c, x, y, cw, ch, 12, hov ? 0xFF1D1F28 : 0xFF16171E, hov ? g.accent : 0xFF262833);
			// preview area
			Gfx.rrect(c, x + 6, y + 6, cw - 12, 60, 8, Gfx.blend(g.accent, 0xFF0D0E13, 0.86F));
			c.enableScissor(x + 6, y + 6, x + cw - 6, y + 66);
			g.icon(c, x + 6, y + 6, cw - 12, 60, time * (hov ? 1.6F : 1F));
			c.disableScissor();
			Gfx.text(c, g.name, x + 10, y + 72, 0xFFFFFFFF);
			Gfx.text(c, g.about, x + 10, y + 84, 0xFF7D8190);
			int b = best(g.id);
			String bs = Lang.tr("Best ", "Recorde ") + b;
			Gfx.rrect(c, x + 8, y + ch - 17, Gfx.twRaw(bs) + 10, 12, 6, b > 0 ? Gfx.withAlpha(g.accent, 0x44) : 0xFF22242E);
			Gfx.textRaw(c, bs, x + 13, y + ch - 15, b > 0 ? g.accent : 0xFF7D8190);
			if (hov) {
				String p = Lang.tr("Play", "Jogar");
				int pw = Gfx.twRaw(p) + 14;
				Gfx.rrect(c, x + cw - pw - 8, y + ch - 18, pw, 14, 7, g.accent);
				Gfx.textRaw(c, p, x + cw - pw - 1, y + ch - 15, 0xFF0B0C10);
			}
			cards.add(new int[] {x, y, cw, ch, i});
		}
	}

	private void backButton(DrawContext c, int mx, int my, String label) {
		int bw = Gfx.tw(label) + 22, bx = 12, by = 12;
		boolean h = mx >= bx && mx < bx + bw && my >= by && my < by + 20;
		Gfx.rrectBorder(c, bx, by, bw, 20, 7, h ? 0xFF22242E : 0xFF17181F, 0xFF2A2D38);
		Gfx.text(c, "‹", bx + 7, by + 6, Theme.TEXT);
		Gfx.text(c, label, bx + 15, by + 6, Theme.TEXT);
		backX = bx + bw;
		backY = by + 20;
	}

	private void play(DrawContext c, int mx, int my, float dt, int accent) {
		Game g = game;
		if (!g.paused) {
			g.update(dt);
		}
		backButton(c, mx, my, "Games");
		// header: name, score, best
		Gfx.textC(c, g.name, width / 2, 14, 0xFFFFFFFF);
		String sc = Lang.tr("Score ", "Pontos ") + g.score + "    " + Lang.tr("Best ", "Recorde ") + Math.max(best(g.id), g.score);
		Gfx.textRaw(c, sc, width / 2 - Gfx.twRaw(sc) / 2, 26, g.accent);
		// game box, scaled up to fill the screen nicely
		int availW = width - 40, availH = height - 80;
		float s = Math.max(1F, Math.min(3F, Math.min(availW / (float) g.gw(), availH / (float) g.gh())));
		s = (float) Math.floor(s * 2) / 2F;
		gs = Math.round(s * 100);
		gx = Math.round(width / 2F - g.gw() * s / 2F);
		gy = Math.round(44 + (availH - g.gh() * s) / 2F);
		Gfx.rrectBorder(c, gx - 4, gy - 4, Math.round(g.gw() * s) + 8, Math.round(g.gh() * s) + 8, 10, 0xFF101117, Gfx.withAlpha(g.accent, 0x66));
		var m = c.getMatrices();
		m.pushMatrix();
		m.translate(gx, gy);
		m.scale(s, s);
		float lmx = (mx - gx) / s, lmy = (my - gy) / s;
		g.render(c, 0, 0, lmx, lmy);
		if (g.over) {
			c.fill(0, 0, g.gw(), g.gh(), 0xB0000000);
			String t = g.newBest ? Lang.tr("NEW BEST!", "NOVO RECORDE!") : Lang.tr("GAME OVER", "FIM DO JOGO");
			m.pushMatrix();
			m.translate(g.gw() / 2F - Gfx.twRaw(t), g.gh() / 2F - 30);
			m.scale(2, 2);
			Gfx.textRaw(c, t, 0, 0, g.newBest ? 0xFFFCD34D : 0xFFFFFFFF);
			m.popMatrix();
			String sv = Lang.tr("Score: ", "Pontos: ") + g.score;
			Gfx.textRaw(c, sv, g.gw() / 2 - Gfx.twRaw(sv) / 2, g.gh() / 2 - 6, 0xFFE6E8EE);
			String again = Lang.tr("Play again", "Jogar outra vez");
			int aw = Gfx.twRaw(again) + 20, ax = g.gw() / 2 - aw / 2, ay = g.gh() / 2 + 8;
			boolean ha = lmx >= ax && lmx < ax + aw && lmy >= ay && lmy < ay + 16;
			Gfx.rrect(c, ax, ay, aw, 16, 8, ha ? Gfx.blend(g.accent, 0xFFFFFFFF, 0.2F) : g.accent);
			Gfx.textRaw(c, again, ax + 10, ay + 4, 0xFF0B0C10);
			againX = ax;
			againY = ay;
			Gfx.textRaw(c, Lang.tr("or press R", "ou carrega no R"), g.gw() / 2 - Gfx.twRaw(Lang.tr("or press R", "ou carrega no R")) / 2, ay + 22, 0xFF8C90A0);
		} else if (g.paused) {
			c.fill(0, 0, g.gw(), g.gh(), 0x99000000);
			String p = Lang.tr("Paused - press P", "Em pausa - carrega no P");
			Gfx.textRaw(c, p, g.gw() / 2 - Gfx.twRaw(p) / 2, g.gh() / 2 - 4, 0xFFFFFFFF);
		}
		m.popMatrix();
		String hint = g.controls() + "   ·   P " + Lang.tr("pause", "pausa") + "   ·   R " + Lang.tr("restart", "recomeçar") + "   ·   Esc";
		Gfx.textRaw(c, hint, width / 2 - Gfx.twRaw(hint) / 2, height - 16, 0xFF6B7080);
	}

	private void start(Game g) {
		game = g;
		g.started = false;
		g.paused = false;
		g.newBest = false;
		g.reset();
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		int k = input.key();
		if (k == 256) {
			if (game != null) {
				game = null;
			} else {
				close();
			}
			return true;
		}
		if (game != null) {
			if (k == 82) {
				start(game);
				return true;
			}
			if (k == 80 && !game.over) {
				game.paused = !game.paused;
				return true;
			}
			if (game.over && (k == 32 || k == 257)) {
				start(game);
				return true;
			}
			if (!game.paused && !game.over) {
				game.key(k);
			}
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		double mx = click.x(), my = click.y();
		if (mx >= 12 && mx < backX && my >= 12 && my < backY) {
			if (game != null) {
				game = null;
			} else {
				close();
			}
			return true;
		}
		if (game == null) {
			for (int[] cd : cards) {
				if (mx >= cd[0] && mx < cd[0] + cd[2] && my >= cd[1] && my < cd[1] + cd[3]) {
					start(games.get(cd[4]));
					return true;
				}
			}
			return true;
		}
		float s = gs / 100F;
		float lx = (float) ((mx - gx) / s), ly = (float) ((my - gy) / s);
		if (game.over) {
			if (lx >= againX && lx < againX + 120 && ly >= againY && ly < againY + 16) {
				start(game);
			}
			return true;
		}
		if (!game.paused && lx >= 0 && ly >= 0 && lx <= game.gw() && ly <= game.gh()) {
			game.click(lx, ly, click.button());
		}
		return true;
	}

	@Override
	public void close() {
		MinecraftClient.getInstance().setScreen(parent);
	}
}
