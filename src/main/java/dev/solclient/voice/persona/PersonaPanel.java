package dev.solclient.voice.persona;

import dev.solclient.Gfx;
import dev.solclient.Lang;
import dev.solclient.gui.TextBox;
import dev.solclient.gui.Theme;
import dev.solclient.mods.PersonasMod;
import dev.solclient.option.Option;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/**
 * The voice changer, drawn INSIDE the mod's settings page (no extra window), like Texture Styles. A banner with the chosen
 * voice, the on / off switches, a search box and category chips, then a grid of round photos. Every photo can be swapped for
 * any picture from your computer (pencil on a voice, or drag a photo onto the window = the chosen voice); it is cut into a
 * smooth circle, so real photos look right. Your pictures live in kubo/personas/pictures/&lt;voice&gt;.png|jpg.
 */
public final class PersonaPanel {

	public static final PersonaPanel INSTANCE = new PersonaPanel();

	/** Size of the round pictures on the GPU (drawn smaller, smooth filtering). */
	private static final int PIC = 160;
	private static final int CELL = 74, ROW_H = 88;

	/** Last time the panel was on screen (the "hear myself" playback stops when it is gone). */
	public static volatile long lastDrawn;

	private static final class Smooth extends NativeImageBackedTexture {
		Smooth(NativeImage img) {
			super(() -> "kubo_persona_pic", img);
			this.sampler = com.mojang.blaze3d.systems.RenderSystem.getSamplerCache().get(com.mojang.blaze3d.textures.FilterMode.LINEAR);
		}
	}

	private static final class Pic {
		volatile int state; // 0 not started, 1 loading, 2 pixels ready, 3 on the GPU, 4 failed
		volatile int[] px;
		Identifier id;
	}

	private final Map<String, Pic> pics = new ConcurrentHashMap<>();
	private final ExecutorService pool = Executors.newFixedThreadPool(3, r -> {
		Thread t = new Thread(r, "kubo-persona-pics");
		t.setDaemon(true);
		return t;
	});
	private final TextBox search = new TextBox(32);
	/** -1 = favourites, otherwise an index of Persona.CATS (0 = all). */
	private int cat;
	private long shownAt;
	private volatile String note = "";

	private PersonaPanel() {
	}

	public Option.Custom.Drawer drawer() {
		return this::draw;
	}

	// ------------------------------------------------------------------ pictures
	private static Path picturesDir() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("kubo").resolve("personas").resolve("pictures");
	}

	/** Your own picture for a voice, if you set one. */
	private static Path override(String id) {
		for (String ext : new String[] {".png", ".jpg", ".jpeg"}) {
			Path f = picturesDir().resolve(id + ext);
			if (Files.isRegularFile(f)) {
				return f;
			}
		}
		return null;
	}

	private static String key(Persona p) {
		Path f = override(p.id);
		if (f == null) {
			f = p.imageFile;
		}
		try {
			return f == null ? p.id : p.id + "@" + Files.getLastModifiedTime(f).toMillis();
		} catch (java.io.IOException e) {
			return p.id;
		}
	}

	/** The round picture of a voice, or null while it is loading / when there is none. */
	private Identifier picture(Persona p) {
		Pic pic = pics.computeIfAbsent(key(p), k -> new Pic());
		if (pic.state == 0) {
			pic.state = 1;
			Path file = override(p.id);
			pool.submit(() -> {
				try {
					BufferedImage img = file != null ? readFile(file) : p.imageFile != null ? readFile(p.imageFile) : p.custom ? null : readResource(p.image);
					if (img == null) {
						pic.state = 4;
						return;
					}
					pic.px = round(img);
					pic.state = 2;
				} catch (Throwable e) {
					System.err.println("[Sleep] persona picture " + p.id + ": " + e);
					pic.state = 4;
				}
			});
		}
		if (pic.state == 2) {
			upload(p, pic);
		}
		return pic.state == 3 ? pic.id : null;
	}

	private static BufferedImage readFile(Path f) throws Exception {
		try (InputStream in = Files.newInputStream(f)) {
			return decode(in.readAllBytes());
		}
	}

	private static BufferedImage readResource(Identifier id) throws Exception {
		var r = MinecraftClient.getInstance().getResourceManager().getResource(id);
		if (r.isEmpty()) {
			return null;
		}
		try (InputStream in = r.get().getInputStream()) {
			return decode(in.readAllBytes());
		}
	}

	/** PNG / JPG / BMP / GIF through ImageIO; PNG through Minecraft's reader if ImageIO is not there. */
	private static BufferedImage decode(byte[] bytes) throws Exception {
		try {
			BufferedImage b = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
			if (b != null) {
				return b;
			}
		} catch (Throwable ignored) {
			// try below
		}
		try (NativeImage n = NativeImage.read(new java.io.ByteArrayInputStream(bytes))) {
			BufferedImage b = new BufferedImage(n.getWidth(), n.getHeight(), BufferedImage.TYPE_INT_ARGB);
			for (int y = 0; y < n.getHeight(); y++) {
				for (int x = 0; x < n.getWidth(); x++) {
					b.setRGB(x, y, n.getColorArgb(x, y));
				}
			}
			return b;
		}
	}

	/** Centre square, scaled smoothly to PIC x PIC, cut into a circle with a soft (anti-aliased) edge. */
	private static int[] round(BufferedImage src) {
		int s = Math.min(src.getWidth(), src.getHeight());
		BufferedImage sq = src.getSubimage((src.getWidth() - s) / 2, (src.getHeight() - s) / 2, s, s);
		// halve while it is much bigger: smoother than one big bicubic step
		while (s / 2 >= PIC * 2) {
			s /= 2;
			sq = scale(sq, s);
		}
		BufferedImage out = scale(sq, PIC);
		int[] px = new int[PIC * PIC];
		float r = PIC / 2F, c = (PIC - 1) / 2F;
		for (int y = 0; y < PIC; y++) {
			for (int x = 0; x < PIC; x++) {
				float d = (float) Math.hypot(x - c, y - c);
				float a = Math.max(0, Math.min(1, r - d));
				int argb = out.getRGB(x, y);
				int alpha = Math.round(((argb >>> 24) & 0xFF) * a);
				px[y * PIC + x] = (alpha << 24) | (argb & 0xFFFFFF);
			}
		}
		return px;
	}

	private static BufferedImage scale(BufferedImage in, int size) {
		BufferedImage o = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		var g = o.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.drawImage(in, 0, 0, size, size, null);
		g.dispose();
		return o;
	}

	private void upload(Persona p, Pic pic) {
		int[] px = pic.px;
		pic.px = null;
		try {
			NativeImage img = new NativeImage(PIC, PIC, false);
			for (int y = 0; y < PIC; y++) {
				for (int x = 0; x < PIC; x++) {
					img.setColorArgb(x, y, px[y * PIC + x]);
				}
			}
			Smooth tex = new Smooth(img);
			tex.upload();
			Identifier id = Gfx.id("generated/persona_round_" + p.id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_") + "_"
					+ Integer.toHexString(key(p).hashCode()));
			MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
			pic.id = id;
			pic.state = 3;
		} catch (Throwable e) {
			pic.state = 4;
		}
	}

	/** Uses a picture from your computer for a voice (copied into kubo/personas/pictures). */
	public void setPicture(Persona p, Path file) {
		String fn = file.getFileName().toString().toLowerCase(Locale.ROOT);
		String ext = fn.endsWith(".png") ? ".png" : fn.endsWith(".jpg") || fn.endsWith(".jpeg") ? ".jpg" : null;
		try {
			Files.createDirectories(picturesDir());
			BufferedImage check = readFile(file);
			if (check == null) {
				note = Lang.tr("That file is not a picture (use PNG or JPG).", "Esse ficheiro não é uma imagem (usa PNG ou JPG).");
				return;
			}
			resetPicture(p);
			if (ext == null) { // GIF / BMP / ...: keep it as PNG
				javax.imageio.ImageIO.write(check, "png", picturesDir().resolve(p.id + ".png").toFile());
			} else {
				Files.copy(file, picturesDir().resolve(p.id + ext), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			}
			note = Lang.tr("New picture for ", "Nova imagem para ") + Lang.t(p.name) + ".";
		} catch (Throwable e) {
			note = Lang.tr("Could not use that picture: ", "Não deu para usar essa imagem: ") + e.getMessage();
		}
	}

	private void resetPicture(Persona p) {
		for (String ext : new String[] {".png", ".jpg", ".jpeg"}) {
			try {
				Files.deleteIfExists(picturesDir().resolve(p.id + ext));
			} catch (java.io.IOException ignored) {
				// still there; the next one wins anyway
			}
		}
	}

	private void choosePicture(Persona p) {
		note = Lang.tr("Choose a picture...", "Escolhe uma imagem...");
		Thread t = new Thread(() -> {
			String path;
			try (MemoryStack stack = MemoryStack.stackPush()) {
				PointerBuffer f = stack.mallocPointer(5);
				f.put(stack.UTF8("*.png")).put(stack.UTF8("*.jpg")).put(stack.UTF8("*.jpeg")).put(stack.UTF8("*.bmp")).put(stack.UTF8("*.gif"));
				f.flip();
				path = TinyFileDialogs.tinyfd_openFileDialog(Lang.tr("Picture for ", "Imagem para ") + Lang.t(p.name), "", f, "Images", false);
			} catch (Throwable e) {
				note = Lang.tr("No file picker here: drag a photo onto the window.", "Sem janela de ficheiros: arrasta uma foto para a janela.");
				return;
			}
			if (path == null) {
				note = "";
				return;
			}
			MinecraftClient.getInstance().execute(() -> setPicture(p, Path.of(path)));
		}, "kubo-persona-pick");
		t.setDaemon(true);
		t.start();
	}

	// ------------------------------------------------------------------ typing (forwarded by the menu)
	public boolean typed(int codepoint) {
		if (System.currentTimeMillis() - shownAt < 250 || codepoint < 32 || codepoint == 127) {
			return true; // the key that opened the page
		}
		search.typed(codepoint);
		return true;
	}

	/** Backspace & co. go to the search; Escape clears it first. */
	public boolean key(int key) {
		if (search.isEmpty()) {
			return false;
		}
		if (key == 256) {
			search.clear();
			return true;
		}
		return search.key(key);
	}

	// ------------------------------------------------------------------ list
	private List<Persona> shown(PersonasMod m) {
		String q = search.text().trim().toLowerCase(Locale.ROOT);
		List<Persona> out = new ArrayList<>();
		for (Persona p : Persona.all()) {
			if (cat == -1 && !m.isFav(p.id)) {
				continue;
			}
			if (cat > 0 && !p.category.equals(Persona.CATS[cat])) {
				continue;
			}
			if (!q.isEmpty() && !p.name.toLowerCase(Locale.ROOT).contains(q) && !Lang.t(p.name).toLowerCase(Locale.ROOT).contains(q)
					&& !p.tagline.toLowerCase(Locale.ROOT).contains(q) && !Lang.t(p.tagline).toLowerCase(Locale.ROOT).contains(q)) {
				continue;
			}
			out.add(p);
		}
		return out;
	}

	private static int count(PersonasMod m, int c) {
		int n = 0;
		for (Persona p : Persona.all()) {
			if (c == -1 ? m.isFav(p.id) : c == 0 || p.category.equals(Persona.CATS[c])) {
				n++;
			}
		}
		return n;
	}

	/** Short tags for the banner: what the voice does. */
	private static List<String> tags(Persona p) {
		List<String> t = new ArrayList<>();
		if (p.pitch != 0) {
			t.add(p.pitch > 0 ? "Higher" : "Deeper");
		}
		if (p.ring > 0.2F) {
			t.add("Robot");
		}
		if (p.drive > 0.25F) {
			t.add("Growl");
		}
		if (p.echo > 0.15F || p.reverb > 0.3F) {
			t.add("Echo");
		}
		if (p.vibrato > 0.3F) {
			t.add("Wobble");
		}
		if (p.crush > 0.4F) {
			t.add("8-bit");
		}
		if (p.bandHigh > 0 && p.bandHigh < 4000) {
			t.add("Radio");
		}
		if (p.chorus > 0.4F) {
			t.add("Crowd");
		}
		if (p.whisper > 0.4F) {
			t.add("Whisper");
		}
		return t;
	}

	private static boolean in(int mx, int my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	// ------------------------------------------------------------------ drawing
	/** A round photo on a dark backing, with a coloured ring behind it when ring != 0; the first letter while the picture loads. */
	private void portrait(DrawContext c, Persona p, int cx, int cy, int size, int ring) {
		Gfx.circle(c, cx, cy + 2, size / 2F + (ring != 0 ? 3 : 1), 0x55000000); // soft shadow
		if (ring != 0) {
			Gfx.circle(c, cx, cy, size / 2F + 2.5F, ring);
		}
		Gfx.circle(c, cx, cy, size / 2F, 0xFF22242C);
		Identifier id = picture(p);
		if (id != null) {
			Gfx.tex(c, id, cx - size / 2, cy - size / 2, size, size, size, size, 0xFFFFFFFF);
		} else {
			Gfx.textC(c, p.name.substring(0, 1).toUpperCase(Locale.ROOT), cx, cy - 4, Theme.DIM);
		}
	}

	private int draw(DrawContext c, int mx, int my, int x, int y, int w, Option.Custom.Reg reg) {
		PersonasMod m = PersonasMod.instance;
		if (m == null) {
			return 0;
		}
		long now = System.currentTimeMillis();
		if (now - lastDrawn > 500) {
			shownAt = now;
		}
		lastDrawn = now;
		int accent = Theme.accent() | 0xFF000000;
		int top = y;

		// ---------------------------------------------------------- banner: the chosen voice
		Persona cur = m.current();
		int bh = 92;
		Gfx.rrect(c, x, y, w, bh, 12, Gfx.blend(Theme.CARD, accent, 0.10F));
		Gfx.gradientH(c, x + w / 3, y, w - w / 3, bh, 0x00000000, Gfx.withAlpha(accent, 0x30));
		int pcx = x + 14 + 34, pcy = y + bh / 2;
		float pulse = m.enabled ? 0.5F + 0.5F * (float) Math.sin(now / 450.0) : 0;
		if (m.enabled) {
			Gfx.circle(c, pcx, pcy, 37 + 2 * pulse, Gfx.withAlpha(accent, Math.round(40 + 40 * pulse)));
		}
		portrait(c, cur, pcx, pcy, 66, m.enabled ? accent : 0xFF4A4D59);
		int tx = x + 14 + 68 + 14;
		var mat = c.getMatrices();
		mat.pushMatrix();
		mat.translate(tx, y + 16);
		mat.scale(1.6F, 1.6F);
		Gfx.text(c, cur.name, 0, 0, Theme.TEXT);
		mat.popMatrix();
		Gfx.textClip(c, cur.tagline, tx, y + 34, w - (tx - x) - 12, Theme.DIM);
		int chx = tx;
		for (String t : tags(cur)) {
			int tw = Gfx.tw(Lang.t(t)) + 12;
			if (chx + tw > x + w - 10) {
				break;
			}
			Gfx.rrect(c, chx, y + 48, tw, 14, 7, 0x22FFFFFF);
			Gfx.text(c, t, chx + 6, y + 51, Theme.TEXT);
			chx += tw + 4;
		}
		// switches along the bottom of the banner
		int sy = y + bh - 20;
		int sx = toggle(c, mx, my, tx, sy, "Voice changer", m.enabled, accent, () -> m.setEnabled(!m.enabled), reg);
		toggle(c, mx, my, sx + 14, sy, "Hear myself", PersonaMonitor.on(), accent, PersonaMonitor::toggle, reg);
		// live state, top right
		boolean live = m.enabled && (PersonaHook.hookAlive() || SystemVoice.on());
		String state = !m.enabled ? "Voice changer is off" : SystemVoice.on() ? "Live on the whole PC" : PersonaHook.hookAlive() ? "Live in your voice chat" : "Ready";
		int stw = Gfx.tw(Lang.t(state)) + 20;
		int stx = x + w - stw - 10;
		Gfx.rrect(c, stx, y + 10, stw, 16, 8, !m.enabled ? 0x40E5484D : live ? 0x4022C55E : 0x22FFFFFF);
		Gfx.circle(c, stx + 8, y + 18, 3, !m.enabled ? 0xFFF87171 : live ? 0xFF4ADE80 : 0xFFB8BCCB);
		Gfx.text(c, state, stx + 14, y + 14, Theme.TEXT);
		y += bh + 8;

		// ---------------------------------------------------------- where: Minecraft / PC / both
		String[] wh = {"Only Minecraft", "Whole PC", "Both"};
		int segW = Math.min(w, 330), segX = x + w - segW, segH = 22;
		// search on the left of the same row
		int fw = w - segW - 8;
		if (fw < 120) { // narrow: search gets its own row
			fw = w;
		}
		boolean focused = !search.isEmpty();
		Gfx.rrectBorder(c, x, y, fw, segH, 11, Theme.FIELD, focused ? Gfx.withAlpha(accent, 0xC0) : Theme.CARD_BORDER);
		Gfx.tex(c, Theme.iconPublic("search"), x + 8, y + 6, 10, 10, 64, 64, Theme.DIM);
		if (search.isEmpty()) {
			Gfx.textClip(c, Lang.tr("Search voices (just type)", "Procurar vozes (escreve)"), x + 23, y + 7, fw - 30, 0xFF6C7182);
		}
		search.draw(c, x, y, fw, segH, x + 23, fw - 34, focused, accent, Theme.TEXT);
		if (fw == w) {
			y += segH + 6;
			segX = x;
			segW = w;
		}
		Gfx.rrect(c, segX, y, segW, segH, 11, Theme.FIELD);
		int sw3 = (segW - 4) / 3;
		for (int i = 0; i < 3; i++) {
			int bx = segX + 2 + i * sw3;
			boolean on = m.where.idx() == i, hov = in(mx, my, bx, y + 2, sw3, segH - 4);
			if (on || hov) {
				Gfx.rrect(c, bx, y + 2, sw3, segH - 4, 9, on ? accent : Theme.CARD_HOVER);
			}
			Gfx.textFit(c, wh[i], bx + sw3 / 2, sw3 - 8, y + 7, on ? 0xFFFFFFFF : Theme.DIM);
			final int wi = i;
			reg.region(bx, y + 2, sw3, segH - 4, () -> m.where.set(wi));
		}
		y += segH + 8;

		// ---------------------------------------------------------- category chips
		int cx = x;
		int[] order = new int[Persona.CATS.length + 1];
		order[0] = 0;
		order[1] = -1;
		for (int i = 1; i < Persona.CATS.length; i++) {
			order[i + 1] = i;
		}
		for (int id : order) {
			String name = id == -1 ? "Favourites" : id == 0 ? "All voices" : Persona.CAT_NAMES[id];
			int n = count(m, id);
			if (id == Persona.CATS.length - 1 && n == 0) {
				continue; // "Mine" only when you made one
			}
			String num = String.valueOf(n);
			int cw = Gfx.tw(Lang.t(name)) + Gfx.tw(num) + 26 + (id == -1 ? 12 : 0);
			if (cx + cw > x + w) {
				cx = x;
				y += 24;
			}
			boolean on = cat == id, hov = in(mx, my, cx, y, cw, 20);
			Gfx.rrect(c, cx, y, cw, 20, 10, on ? accent : hov ? Theme.CARD_HOVER : Theme.CARD);
			int lx = cx + 10;
			if (id == -1) {
				Gfx.tex(c, Theme.iconPublic("star_filled"), lx, y + 5, 10, 10, 64, 64, on ? 0xFFFFFFFF : 0xFFFFC94A);
				lx += 12;
			}
			Gfx.text(c, name, lx, y + 6, on ? 0xFFFFFFFF : Theme.TEXT);
			Gfx.text(c, num, cx + cw - 10 - Gfx.tw(num), y + 6, on ? 0xCCFFFFFF : 0xFF6C7182);
			final int ci = id;
			reg.region(cx, y, cw, 20, () -> cat = ci);
			cx += cw + 5;
		}
		y += 30;

		// ---------------------------------------------------------- grid of round photos
		List<Persona> list = shown(m);
		int cols = Math.max(3, w / CELL), cwid = w / cols;
		int vh = MinecraftClient.getInstance().getWindow().getScaledHeight();
		for (int i = 0; i < list.size(); i++) {
			Persona p = list.get(i);
			int gx = x + (i % cols) * cwid, gy = y + (i / cols) * ROW_H;
			if (gy + ROW_H < 0 || gy > vh) {
				continue; // off screen: no drawing, no picture loading
			}
			boolean sel = p.id.equals(m.character.get()), hov = in(mx, my, gx, gy, cwid, ROW_H);
			int pcxx = gx + cwid / 2, pcyy = gy + 31;
			int size = hov ? 58 : 54;
			if (hov && !sel) {
				Gfx.rrect(c, gx + 2, gy, cwid - 4, ROW_H - 4, 10, Theme.CARD_HOVER);
			}
			if (sel) {
				Gfx.rrect(c, gx + 2, gy, cwid - 4, ROW_H - 4, 10, Gfx.blend(Theme.CARD, accent, 0.18F));
				Gfx.circle(c, pcxx, pcyy, size / 2F + 5, Gfx.withAlpha(accent, 0x50));
			}
			portrait(c, p, pcxx, pcyy, size, sel ? accent : hov ? 0x66FFFFFF : 0);
			Gfx.textFit(c, p.name, pcxx, cwid - 8, gy + 64, sel ? 0xFFFFFFFF : Theme.TEXT);
			String cn = Persona.CAT_NAMES[Math.max(0, java.util.Arrays.asList(Persona.CATS).indexOf(p.category))];
			Gfx.textFit(c, cn, pcxx, cwid - 8, gy + 74, 0xFF6C7182);
			// the whole tile picks the voice; the small buttons are registered after it so they win
			reg.region(gx, gy, cwid, ROW_H - 4, () -> {
				m.pick(p);
				if (!m.enabled) {
					m.setEnabled(true);
				}
			});
			boolean fav = m.isFav(p.id);
			if (fav || hov) {
				int fx = pcxx + size / 2 - 4, fy = pcyy - size / 2 + 3;
				boolean hf = in(mx, my, fx - 7, fy - 7, 14, 14);
				Gfx.circle(c, fx, fy, 7, 0xE6101116);
				Gfx.tex(c, Theme.iconPublic(fav ? "star_filled" : "star_outline"), fx - 5, fy - 5, 10, 10, 64, 64, fav ? 0xFFFFC94A : hf ? 0xFFFFFFFF : 0xFFB8BCCB);
				reg.region(fx - 7, fy - 7, 14, 14, () -> m.toggleFav(p.id));
			}
			if (hov) {
				// change the picture (and put the original back when you already changed it)
				int ex = pcxx - size / 2 + 4, ey = pcyy - size / 2 + 3;
				boolean he = in(mx, my, ex - 7, ey - 7, 14, 14);
				Gfx.circle(c, ex, ey, 7, 0xE6101116);
				Gfx.tex(c, Theme.iconPublic("edit"), ex - 4, ey - 4, 8, 8, 64, 64, he ? 0xFFFFFFFF : 0xFFB8BCCB);
				reg.region(ex - 7, ey - 7, 14, 14, () -> choosePicture(p));
				if (override(p.id) != null) {
					int rx = pcxx - size / 2 + 4, ry = pcyy + size / 2 - 5;
					boolean hr = in(mx, my, rx - 7, ry - 7, 14, 14);
					Gfx.circle(c, rx, ry, 7, 0xE6101116);
					Gfx.tex(c, Theme.iconPublic("close"), rx - 4, ry - 4, 8, 8, 64, 64, hr ? 0xFFF87171 : 0xFFB8BCCB);
					reg.region(rx - 7, ry - 7, 14, 14, () -> {
						resetPicture(p);
						note = Lang.tr("Original picture back.", "Imagem original de volta.");
					});
				}
			}
		}
		int rows = (list.size() + cols - 1) / cols;
		if (list.isEmpty()) {
			Gfx.textC(c, cat == -1 ? "Click the star on a voice to add it here" : "No voice found", x + w / 2, y + 20, Theme.DIM);
			rows = 1;
		}
		y += rows * ROW_H + 4;

		// ---------------------------------------------------------- notes + pictures folder
		String status = m.where.idx() != 0 && !SystemVoice.status.isEmpty() ? SystemVoice.status : PersonaMonitor.status;
		if (!status.isEmpty()) {
			Gfx.textClip(c, status, x, y, w, SystemVoice.on() || PersonaMonitor.on() ? 0xFF4ADE80 : 0xFFF87171);
			y += 12;
		}
		if (!note.isEmpty()) {
			Gfx.textClip(c, note, x, y, w, Theme.GREEN);
			y += 12;
		}
		Gfx.textClip(c, Lang.tr("Pencil on a voice = your own photo. Or drag a photo onto the window to use it for the chosen voice.",
				"Lápis numa voz = a tua própria foto. Ou arrasta uma foto para a janela para a voz escolhida."), x, y, w - 120, Theme.DIM);
		String fl = Lang.tr("Pictures folder", "Pasta das imagens");
		int fbw = Gfx.tw(fl) + 30;
		boolean fh = in(mx, my, x + w - fbw, y - 5, fbw, 18);
		Gfx.rrect(c, x + w - fbw, y - 5, fbw, 18, 9, fh ? Theme.CARD_HOVER : Theme.CARD);
		Gfx.tex(c, Theme.iconPublic("folder"), x + w - fbw + 8, y - 1, 10, 10, 64, 64, Theme.DIM);
		Gfx.text(c, fl, x + w - fbw + 22, y, Theme.TEXT);
		reg.region(x + w - fbw, y - 5, fbw, 18, () -> {
			try {
				Files.createDirectories(picturesDir());
				net.minecraft.util.Util.getOperatingSystem().open(picturesDir().getParent().toFile());
			} catch (java.io.IOException e) {
				System.err.println("[Sleep] personas folder: " + e);
			}
		});
		y += 18;
		return y - top;
	}

	/** A switch with its label. Returns the x after it. */
	private int toggle(DrawContext c, int mx, int my, int x, int y, String label, boolean on, int accent, Runnable click, Option.Custom.Reg reg) {
		int w = 26, h = 14, cy = y + 7;
		Gfx.rrect(c, x, y, w, h, 7, on ? accent : 0xFF3A3D49);
		Gfx.circle(c, on ? x + w - 7 : x + 7, cy, 5, 0xFFFFFFFF);
		Gfx.text(c, label, x + w + 6, cy - 4, on ? Theme.TEXT : Theme.DIM);
		int tw = w + 6 + Gfx.tw(Lang.t(label));
		reg.region(x, y - 2, tw, h + 4, click);
		return x + tw;
	}
}
