package dev.solclient.gui;

import dev.solclient.Gfx;
import dev.solclient.Lang;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import javax.imageio.ImageIO;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Skin Editor: paint your skin pixel by pixel (pencil, eraser, fill, colour picker, mirror, undo), see it on a
 * character from the front, back and sides, then save the PNG or wear it right away (uploaded to your Sleep account).
 */
public class SkinEditorScreen extends Screen {

	private static final int[] PALETTE = {0xFF000000, 0xFF3B3B3B, 0xFF7A7A7A, 0xFFBDBDBD, 0xFFFFFFFF, 0xFF5C3A21, 0xFF8B5A2B, 0xFFC68642, 0xFFF1C27D,
			0xFFFFDBAC, 0xFF7F0000, 0xFFE53935, 0xFFFF7043, 0xFFFFA726, 0xFFFFEE58, 0xFF9CCC65, 0xFF43A047, 0xFF1B5E20, 0xFF26C6DA, 0xFF1E88E5,
			0xFF283593, 0xFF7E57C2, 0xFFAB47BC, 0xFFEC407A, 0xFFF8BBD0, 0xFF4E342E, 0xFF37474F, 0xFF90A4AE};
	private static final String[] TOOLS = {"Pencil", "Eraser", "Fill", "Picker"};

	private final Screen parent;
	private final int[] px = new int[64 * 64];
	private final ArrayDeque<int[]> undo = new ArrayDeque<>();
	private NativeImageBackedTexture tex;
	private final Identifier id = Gfx.id("skin_editor/canvas");
	private boolean dirty = true, painting, mirror, grid = true, slim;
	private int tool, colour = 0xFF1E88E5, view;
	private String msg = "";
	private long msgAt;
	private int cx, cy, cs; // canvas position and pixel size
	private volatile boolean loading;

	public SkinEditorScreen(Screen parent) {
		super(Text.literal("Skin Editor"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		if (tex == null) {
			tex = new NativeImageBackedTexture("kubo_skin_editor", 64, 64, false);
			MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
			loadSteve();
			loadMine();
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	// ------------------------------------------------------------------ loading / saving
	private void loadSteve() {
		try {
			var res = MinecraftClient.getInstance().getResourceManager().getResource(Identifier.ofVanilla("textures/entity/player/wide/steve.png"));
			if (res.isPresent()) {
				try (InputStream in = res.get().getInputStream(); NativeImage img = NativeImage.read(in)) {
					for (int y = 0; y < 64; y++) {
						for (int x = 0; x < 64; x++) {
							px[y * 64 + x] = y < img.getHeight() ? img.getColorArgb(x, y) : 0;
						}
					}
				}
				dirty = true;
			}
		} catch (Exception e) {
			System.err.println("[Sleep] skin editor: " + e);
		}
	}

	/** Your current skin (by nick), so you start from it. */
	private void loadMine() {
		String nick = MinecraftClient.getInstance().getSession().getUsername();
		loading = true;
		dev.solclient.social.Social.IO.execute(() -> {
			try {
				HttpRequest req = HttpRequest.newBuilder(URI.create("https://mc-heads.net/skin/" + nick)).timeout(Duration.ofSeconds(8)).GET().build();
				HttpResponse<byte[]> r = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofByteArray());
				if (r.statusCode() == 200) {
					BufferedImage img = ImageIO.read(new ByteArrayInputStream(r.body()));
					if (img != null && img.getWidth() == 64) {
						int[] got = new int[64 * 64];
						for (int y = 0; y < 64; y++) {
							for (int x = 0; x < 64; x++) {
								// old 64x32 skins: copy the right arm / leg to the left ones
								int sy = y, sx = x;
								if (img.getHeight() == 32 && y >= 32) {
									if (y >= 48 && x >= 16 && x < 32) {
										sx = x - 16 + 0;
										sy = y - 32;
									} else if (y >= 48 && x >= 32 && x < 48) {
										sx = x + 8;
										sy = y - 32;
									} else {
										got[y * 64 + x] = 0;
										continue;
									}
								}
								got[y * 64 + x] = img.getRGB(sx, sy);
							}
						}
						MinecraftClient.getInstance().execute(() -> {
							System.arraycopy(got, 0, px, 0, px.length);
							dirty = true;
						});
					}
				}
			} catch (Exception ignored) {
			} finally {
				loading = false;
			}
		});
	}

	private byte[] png() throws Exception {
		BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 64; y++) {
			for (int x = 0; x < 64; x++) {
				img.setRGB(x, y, px[y * 64 + x]);
			}
		}
		ByteArrayOutputStream bo = new ByteArrayOutputStream();
		ImageIO.write(img, "png", bo);
		return bo.toByteArray();
	}

	private Path savePng() throws Exception {
		Path dir = FabricLoader.getInstance().getGameDir().resolve("kubo-skins");
		Files.createDirectories(dir);
		Path out = dir.resolve("skin_" + new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new java.util.Date()) + ".png");
		Files.write(out, png());
		return out;
	}

	private void say(String s) {
		msg = s;
		msgAt = System.currentTimeMillis();
	}

	// ------------------------------------------------------------------ editing
	private void snapshot() {
		undo.push(px.clone());
		while (undo.size() > 40) {
			undo.removeLast();
		}
	}

	/** The overlay (hat, jacket...) can be see-through; the base layer can't. */
	private static boolean overlay(int x, int y) {
		return y < 16 && x >= 32 || y >= 32 && y < 48 || y >= 48 && (x < 16 || x >= 48);
	}

	/** Mirror partner of a pixel (left arm <-> right arm, left leg <-> right leg, head / body sides). */
	private static int mirrorX(int x, int y) {
		// simple: mirror inside the same 8 px face for heads and bodies
		int face = x / 8 * 8;
		return face + 7 - (x - face);
	}

	private void paint(int x, int y) {
		if (x < 0 || y < 0 || x >= 64 || y >= 64) {
			return;
		}
		switch (tool) {
			case 1 -> set(x, y, overlay(x, y) ? 0 : 0xFFFFFFFF);
			case 2 -> fill(x, y);
			case 3 -> {
				int c = px[y * 64 + x];
				if ((c >>> 24) != 0) {
					colour = c | 0xFF000000;
					tool = 0;
				}
			}
			default -> set(x, y, colour);
		}
	}

	private void set(int x, int y, int c) {
		px[y * 64 + x] = c;
		if (mirror) {
			int mx = mirrorX(x, y);
			if (mx >= 0 && mx < 64) {
				px[y * 64 + mx] = c;
			}
		}
		dirty = true;
	}

	private void fill(int x, int y) {
		int target = px[y * 64 + x];
		if (target == colour) {
			return;
		}
		ArrayDeque<int[]> q = new ArrayDeque<>();
		q.add(new int[] {x, y});
		int x0 = x / 8 * 8, y0 = y / 8 * 8; // stays inside one 8x8 face so it never floods the whole sheet
		int count = 0;
		while (!q.isEmpty() && count < 4096) {
			int[] p = q.poll();
			int ax = p[0], ay = p[1];
			if (ax < x0 - 8 || ax >= x0 + 16 || ay < y0 - 8 || ay >= y0 + 16 || ax < 0 || ay < 0 || ax >= 64 || ay >= 64 || px[ay * 64 + ax] != target) {
				continue;
			}
			px[ay * 64 + ax] = colour;
			count++;
			q.add(new int[] {ax + 1, ay});
			q.add(new int[] {ax - 1, ay});
			q.add(new int[] {ax, ay + 1});
			q.add(new int[] {ax, ay - 1});
		}
		dirty = true;
	}

	private void upload() {
		if (!dirty || tex == null || tex.getImage() == null) {
			return;
		}
		NativeImage img = tex.getImage();
		for (int y = 0; y < 64; y++) {
			for (int x = 0; x < 64; x++) {
				img.setColorArgb(x, y, px[y * 64 + x]);
			}
		}
		tex.upload();
		dirty = false;
	}

	// ------------------------------------------------------------------ drawing
	@Override
	public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) {
	}

	private boolean in(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	@Override
	public void render(DrawContext c, int mx, int my, float delta) {
		upload();
		c.fillGradient(0, 0, width, height, 0xF00B0C11, 0xF0141622);
		Gfx.text(c, "Skin Editor", 12, 10, 0xFFFFFFFF);
		if (loading) {
			Gfx.text(c, "Loading your skin...", 12 + Gfx.tw("Skin Editor") + 10, 10, Theme.DIM);
		}
		// canvas
		cs = Math.max(2, Math.min((height - 70) / 64, (width - 300) / 64));
		cx = 12;
		cy = 28;
		int sz = cs * 64;
		// checkerboard behind see-through pixels
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				c.fill(cx + x * sz / 16, cy + y * sz / 16, cx + (x + 1) * sz / 16, cy + (y + 1) * sz / 16, ((x + y) & 1) == 0 ? 0xFF1C1E26 : 0xFF23262F);
			}
		}
		Gfx.tex(c, id, cx, cy, sz, sz, 64, 64, 0xFFFFFFFF);
		if (grid && cs >= 5) {
			for (int i = 0; i <= 64; i += 8) {
				c.fill(cx + i * cs, cy, cx + i * cs + 1, cy + sz, 0x33FFFFFF);
				c.fill(cx, cy + i * cs, cx + sz, cy + i * cs + 1, 0x33FFFFFF);
			}
		}
		Gfx.outline(c, cx - 1, cy - 1, sz + 2, sz + 2, 0xFF3A3D49);
		// hover pixel
		if (in(mx, my, cx, cy, sz, sz)) {
			int hx = (mx - cx) / cs, hy = (my - cy) / cs;
			Gfx.outline(c, cx + hx * cs, cy + hy * cs, cs, cs, 0xFFFFFFFF);
			Gfx.textRaw(c, hx + ", " + hy + (overlay(hx, hy) ? "  (overlay)" : ""), cx, cy + sz + 4, Theme.DIM);
		}

		// right panel: tools, colours, preview, buttons
		int rx = cx + sz + 14, ry = 28, rw = width - rx - 12;
		for (int i = 0; i < TOOLS.length; i++) {
			int bw = (rw - 9) / 4, bx = rx + i * (bw + 3);
			boolean s = tool == i, h = in(mx, my, bx, ry, bw, 18);
			Gfx.rrect(c, bx, ry, bw, 18, 6, s ? Theme.accent() : h ? Theme.OFF_BG_HOVER : Theme.OFF_BG);
			Gfx.textFit(c, TOOLS[i], bx + bw / 2, bw - 6, ry + 5, 0xFFFFFFFF);
		}
		ry += 24;
		int sw = 14, per = Math.max(1, (rw + 3) / (sw + 3));
		for (int i = 0; i < PALETTE.length; i++) {
			int x = rx + (i % per) * (sw + 3), y = ry + (i / per) * (sw + 3);
			Gfx.rrect(c, x, y, sw, sw, 3, PALETTE[i]);
			if (PALETTE[i] == colour) {
				Gfx.outline(c, x - 1, y - 1, sw + 2, sw + 2, 0xFFFFFFFF);
			}
		}
		ry += ((PALETTE.length + per - 1) / per) * (sw + 3) + 4;
		Gfx.rrect(c, rx, ry, 30, 16, 4, colour);
		Gfx.textRaw(c, String.format("#%06X", colour & 0xFFFFFF), rx + 36, ry + 4, Theme.TEXT);
		String[] adj = {"-", "+"};
		for (int i = 0; i < 2; i++) {
			int bx = rx + rw - 40 + i * 21;
			Gfx.rrect(c, bx, ry, 18, 16, 4, in(mx, my, bx, ry, 18, 16) ? Theme.OFF_BG_HOVER : Theme.OFF_BG);
			Gfx.textC(c, adj[i], bx + 9, ry + 4, 0xFFFFFFFF);
		}
		ry += 22;
		// toggles
		String[] tg = {Lang.tr("Mirror", "Espelho") + (mirror ? " ✓" : ""), "Grid" + (grid ? " ✓" : ""), (slim ? "Slim" : "Classic"),
				Lang.tr("Undo", "Desfazer")};
		for (int i = 0; i < tg.length; i++) {
			int bw = (rw - 9) / 4, bx = rx + i * (bw + 3);
			Gfx.rrect(c, bx, ry, bw, 16, 5, in(mx, my, bx, ry, bw, 16) ? Theme.OFF_BG_HOVER : Theme.OFF_BG);
			Gfx.textRaw(c, tg[i], bx + bw / 2 - Gfx.twRaw(tg[i]) / 2, ry + 4, 0xFFE6E8EE);
		}
		ry += 24;
		// preview of the character
		int ph = Math.max(60, height - ry - 60);
		Gfx.rrect(c, rx, ry, rw, ph, 8, 0xFF15161C);
		drawCharacter(c, rx + rw / 2, ry + 8, Math.max(2, Math.min(ph / 36, rw / 20)));
		String[] views = {"Front", "Back", "Left", "Right"};
		Gfx.textFit(c, views[view] + "  (click to turn)", rx + rw / 2, rw - 6, ry + ph - 12, Theme.DIM);
		ry += ph + 6;
		// actions
		String[] acts = {Lang.tr("Save PNG", "Guardar PNG"), Lang.tr("Wear it", "Vestir"), Lang.tr("Reset", "Repor")};
		for (int i = 0; i < acts.length; i++) {
			int bw = (rw - 6) / 3, bx = rx + i * (bw + 3);
			boolean primary = i == 1;
			Gfx.rrect(c, bx, ry, bw, 20, 6, primary ? (in(mx, my, bx, ry, bw, 20) ? Theme.accentHover() : Theme.accent()) : in(mx, my, bx, ry, bw, 20) ? Theme.OFF_BG_HOVER : Theme.OFF_BG);
			Gfx.textRaw(c, acts[i], bx + bw / 2 - Gfx.twRaw(acts[i]) / 2, ry + 6, 0xFFFFFFFF);
		}
		if (System.currentTimeMillis() - msgAt < 5000L) {
			Gfx.textRaw(c, msg, rx, ry + 26, Theme.TEXT);
		}
		Gfx.textRaw(c, "Esc", width - 26, 10, 0xFF6B7080);
	}

	/** One box face from the skin, drawn flat. */
	private void face(DrawContext c, int x, int y, int u, int v, int w, int h, int s) {
		Gfx.texUv(c, id, x, y, w * s, h * s, u, v, w, h, 64, 64, 0xFFFFFFFF);
	}

	/** Front / back / side of the character: base layer and then the overlay on top. */
	private void drawCharacter(DrawContext c, int midX, int top, int s) {
		int arm = slim ? 3 : 4;
		switch (view) {
			case 1 -> { // back
				int x = midX - 8 * s;
				part(c, x, top, s, 24, 8, 8, 8, 56, 8); // head back
				part(c, x, top + 8 * s, s, 32, 20, 8, 12, 32, 36);
				part(c, x - arm * s, top + 8 * s, s, 52, 20, arm, 12, 52, 36); // right arm back (shows on the left)
				part(c, x + 8 * s, top + 8 * s, s, 44 + 8, 52, arm, 12, 60, 52);
				part(c, x, top + 20 * s, s, 12, 20, 4, 12, 12, 36);
				part(c, x + 4 * s, top + 20 * s, s, 28, 52, 4, 12, 12, 52);
			}
			case 2, 3 -> { // sides
				boolean left = view == 2;
				int x = midX - 4 * s;
				part(c, x, top, s, left ? 16 : 0, 8, 8, 8, left ? 48 : 32, 8);
				part(c, x + 2 * s, top + 8 * s, s, left ? 28 : 16, 20, 4, 12, left ? 28 : 16, 36);
				part(c, x + 2 * s, top + 20 * s, s, left ? 24 : 0, left ? 52 : 20, 4, 12, left ? 8 : 0, left ? 52 : 36);
				part(c, x + 2 * s, top + 8 * s, s, left ? 40 : 40, left ? 52 : 20, 4, 12, left ? 56 : 40, left ? 52 : 36);
			}
			default -> { // front
				int x = midX - 4 * s;
				part(c, x, top, s, 8, 8, 8, 8, 40, 8);
				part(c, x, top + 8 * s, s, 20, 20, 8, 12, 20, 36);
				part(c, x - arm * s, top + 8 * s, s, 44, 20, arm, 12, 44, 36);
				part(c, x + 8 * s, top + 8 * s, s, 36, 52, arm, 12, 52, 52);
				part(c, x, top + 20 * s, s, 4, 20, 4, 12, 4, 36);
				part(c, x + 4 * s, top + 20 * s, s, 20, 52, 4, 12, 4, 52);
			}
		}
	}

	private void part(DrawContext c, int x, int y, int s, int u, int v, int w, int h, int ou, int ov) {
		face(c, x, y, u, v, w, h, s);
		face(c, x, y, ou, ov, w, h, s);
	}

	// ------------------------------------------------------------------ input
	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		double mx = click.x(), my = click.y();
		int sz = cs * 64;
		if (in(mx, my, cx, cy, sz, sz)) {
			snapshot();
			painting = true;
			int oldTool = tool;
			if (click.button() == 1) {
				tool = 1;
			}
			paint((int) ((mx - cx) / cs), (int) ((my - cy) / cs));
			if (click.button() == 1) {
				tool = oldTool;
			}
			return true;
		}
		int rx = cx + sz + 14, ry = 28, rw = width - rx - 12;
		for (int i = 0; i < TOOLS.length; i++) {
			int bw = (rw - 9) / 4, bx = rx + i * (bw + 3);
			if (in(mx, my, bx, ry, bw, 18)) {
				tool = i;
				return true;
			}
		}
		ry += 24;
		int sw = 14, per = Math.max(1, (rw + 3) / (sw + 3));
		for (int i = 0; i < PALETTE.length; i++) {
			int x = rx + (i % per) * (sw + 3), y = ry + (i / per) * (sw + 3);
			if (in(mx, my, x, y, sw, sw)) {
				colour = PALETTE[i];
				if (tool == 1 || tool == 3) {
					tool = 0;
				}
				return true;
			}
		}
		ry += ((PALETTE.length + per - 1) / per) * (sw + 3) + 4;
		for (int i = 0; i < 2; i++) {
			int bx = rx + rw - 40 + i * 21;
			if (in(mx, my, bx, ry, 18, 16)) {
				colour = Gfx.blend(colour, i == 0 ? 0xFF000000 : 0xFFFFFFFF, 0.15F) | 0xFF000000;
				return true;
			}
		}
		ry += 22;
		for (int i = 0; i < 4; i++) {
			int bw = (rw - 9) / 4, bx = rx + i * (bw + 3);
			if (in(mx, my, bx, ry, bw, 16)) {
				switch (i) {
					case 0 -> mirror = !mirror;
					case 1 -> grid = !grid;
					case 2 -> slim = !slim;
					default -> undo();
				}
				return true;
			}
		}
		ry += 24;
		int ph = Math.max(60, height - ry - 60);
		if (in(mx, my, rx, ry, rw, ph)) {
			view = (view + 1) % 4;
			return true;
		}
		ry += ph + 6;
		for (int i = 0; i < 3; i++) {
			int bw = (rw - 6) / 3, bx = rx + i * (bw + 3);
			if (in(mx, my, bx, ry, bw, 20)) {
				action(i);
				return true;
			}
		}
		return true;
	}

	private void action(int i) {
		try {
			switch (i) {
				case 0 -> {
					Path p = savePng();
					say(Lang.tr("Saved ", "Guardado ") + p.getFileName());
					net.minecraft.util.Util.getOperatingSystem().open(p.getParent().toFile());
				}
				case 1 -> {
					byte[] data = png();
					savePng();
					say(Lang.tr("Uploading...", "A enviar..."));
					dev.solclient.social.Social.IO.execute(() -> {
						String err = dev.solclient.social.Social.uploadSkinAndUse(data, "Sleep Editor");
						MinecraftClient.getInstance().execute(() -> say(err == null ? Lang.tr("You are wearing it!", "Já a tens vestida!") : err));
					});
				}
				default -> {
					snapshot();
					loadSteve();
				}
			}
		} catch (Exception e) {
			say("Error: " + e.getMessage());
		}
	}

	private void undo() {
		int[] u = undo.poll();
		if (u != null) {
			System.arraycopy(u, 0, px, 0, px.length);
			dirty = true;
		}
	}

	@Override
	public boolean mouseDragged(Click click, double dx, double dy) {
		if (painting && (tool == 0 || tool == 1)) {
			int oldTool = tool;
			if (click.button() == 1) {
				tool = 1;
			}
			paint((int) ((click.x() - cx) / cs), (int) ((click.y() - cy) / cs));
			tool = oldTool;
		}
		return true;
	}

	@Override
	public boolean mouseReleased(Click click) {
		painting = false;
		return true;
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		int k = input.key();
		if (k == 256) {
			close();
			return true;
		}
		if (k == 90 && input.hasCtrlOrCmd()) {
			undo();
			return true;
		}
		if (k == 66) {
			tool = 0; // B = brush
		} else if (k == 69) {
			tool = 1; // E = eraser
		} else if (k == 70) {
			tool = 2; // F = fill
		} else if (k == 73) {
			tool = 3; // I = picker
		} else if (k == 77) {
			mirror = !mirror;
		}
		return true;
	}

	@Override
	public void close() {
		MinecraftClient.getInstance().setScreen(parent);
	}

	@Override
	public void removed() {
		if (tex != null && !(MinecraftClient.getInstance().currentScreen instanceof SkinEditorScreen)) {
			MinecraftClient.getInstance().getTextureManager().destroyTexture(id);
			tex = null;
		}
	}
}
