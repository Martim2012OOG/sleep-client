package dev.solclient.gui;

import dev.solclient.Gfx;
import dev.solclient.Lang;
import dev.solclient.mods.TextureStylesMod;
import dev.solclient.option.Option;
import dev.solclient.pack.TextureStyles;
import dev.solclient.pack.TextureStyles.Cat;
import dev.solclient.pack.TextureStyles.Hit;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

/**
 * Texture Styles, drawn INSIDE the mod's settings page (no extra window). Pick an item, then pick its texture from pictures
 * of that item only (no pack names, no pack icons). A picture is made the first time it is on screen: the style is downloaded
 * once (cached) and only the item's texture is read from it. Click a picture to use it; the first tile goes back to vanilla.
 */
public final class TextureStylesPanel {

	public static final TextureStylesPanel INSTANCE = new TextureStylesPanel();

	private static final int TILE = 56, GAP = 6, PAGE = 40, PIC = 128;

	/** Smooth (linear) filtering: the 3D pictures are drawn smaller than they are, so no jagged edges. */
	private static final class Smooth extends NativeImageBackedTexture {
		Smooth(String id, NativeImage img) {
			super(() -> "kubo_style_" + id, img);
			this.sampler = com.mojang.blaze3d.systems.RenderSystem.getSamplerCache().get(com.mojang.blaze3d.textures.FilterMode.LINEAR);
		}
	}

	private static final class Tile {
		volatile int state; // 0 not started, 1 loading, 2 ready (picture bytes), 3 no texture / failed
		volatile dev.solclient.pack.StylePreview3D.Pic pic;
		volatile TextureStyles.Choice choice;
		Identifier id;
		int w, h;
	}

	private int cat;
	private final List<Hit> hits = new ArrayList<>();
	private final Map<String, Tile> tiles = new ConcurrentHashMap<>();
	private volatile boolean loading, started;
	private volatile String error = "";
	private int loadId, offset;
	private volatile String note = "";
	/** Pack id -> its file, looked up for the whole page with one request (so a picture needs no lookup of its own). */
	private final Map<String, TextureStyles.Choice> known = new ConcurrentHashMap<>();
	private final ExecutorService pool = Executors.newFixedThreadPool(12, r -> {
		Thread t = new Thread(r, "kubo-styles-preview");
		t.setDaemon(true);
		return t;
	});

	private TextureStylesPanel() {
	}

	public Option.Custom.Drawer drawer() {
		return this::draw;
	}

	// ------------------------------------------------------------------ data
	private void startCat() {
		final int id = ++loadId;
		final Cat c = TextureStyles.CATS.get(cat);
		final int off = offset;
		loading = true;
		error = "";
		Thread t = new Thread(() -> {
			try {
				List<Hit> r = TextureStyles.search(c, "", off, false);
				known.putAll(TextureStyles.resolveAll(r));
				MinecraftClient.getInstance().execute(() -> {
					if (id == loadId) {
						hits.addAll(r);
						loading = false;
					}
				});
			} catch (Throwable e) {
				MinecraftClient.getInstance().execute(() -> {
					if (id == loadId) {
						error = Lang.tr("Could not reach Modrinth", "Não foi possível ligar ao Modrinth");
						loading = false;
					}
				});
			}
		}, "kubo-styles-search");
		t.setDaemon(true);
		t.start();
	}

	private void freeTextures() {
		MinecraftClient mc = MinecraftClient.getInstance();
		for (Tile t : tiles.values()) {
			if (t.id != null) {
				mc.getTextureManager().destroyTexture(t.id);
			}
		}
		tiles.clear();
	}

	private void selectCat(int i) {
		if (i == cat) {
			return;
		}
		cat = i;
		freeTextures();
		hits.clear();
		offset = 0;
		note = "";
		startCat();
	}

	private void prepare(Hit h, Cat c, Tile t) {
		t.state = 1;
		pool.submit(() -> {
			try {
				// 3D picture; only the few bytes it needs are downloaded, and it is kept on disk for next time
				TextureStyles.Preview pv = TextureStyles.preview(h, c, PIC, known.get(h.id()));
				if (pv.pic() == null) {
					t.state = 3;
					return;
				}
				t.choice = pv.choice();
				t.pic = pv.pic();
				t.state = 2;
			} catch (Throwable e) {
				t.state = 3;
			}
		});
	}

	/** Makes the GPU picture on the render thread. */
	private void upload(Hit h, Tile t) {
		if (t.id != null || t.pic == null) {
			return;
		}
		var pic = t.pic;
		t.pic = null;
		try {
			NativeImage img = new NativeImage(pic.w(), pic.h(), false);
			for (int y = 0; y < pic.h(); y++) {
				for (int x = 0; x < pic.w(); x++) {
					img.setColorArgb(x, y, pic.px()[y * pic.w() + x]);
				}
			}
			t.w = pic.w();
			t.h = pic.h();
			NativeImageBackedTexture tex = new Smooth(h.id(), img);
			tex.upload();
			Identifier id = Gfx.id("style_" + Integer.toHexString(h.id().hashCode()) + "_" + cat);
			MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
			t.id = id;
		} catch (Throwable e) {
			t.state = 3;
		}
	}

	private void use(Hit h, Tile t) {
		Cat c = TextureStyles.CATS.get(cat);
		if (t.choice == null) {
			return;
		}
		TextureStyles.CHOICES.put(c.id(), t.choice);
		TextureStyles.save();
		TextureStylesMod.apply();
		note = "";
	}

	private void vanilla() {
		Cat c = TextureStyles.CATS.get(cat);
		if (TextureStyles.CHOICES.remove(c.id()) != null) {
			TextureStyles.save();
			TextureStylesMod.apply();
			note = "";
		}
	}

	private static boolean in(int mx, int my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	// ------------------------------------------------------------------ drawing
	private int draw(DrawContext c, int mx, int my, int x, int y, int w, Option.Custom.Reg reg) {
		if (!started) {
			started = true;
			TextureStyles.load();
			startCat();
		}
		TextureStylesMod.flushIfNoWorld();
		int top = y;
		// item chips (wrapping)
		int cx = x, cy = y;
		for (int i = 0; i < TextureStyles.CATS.size(); i++) {
			Cat k = TextureStyles.CATS.get(i);
			String name = Lang.tr(k.en(), k.pt());
			int cw = 22 + Gfx.tw(name) + 8;
			if (cx + cw > x + w) {
				cx = x;
				cy += 24;
			}
			boolean on = i == cat, hov = in(mx, my, cx, cy, cw, 20);
			Gfx.rrect(c, cx, cy, cw, 20, 6, on ? Gfx.blend(Theme.FIELD, Theme.accent() | 0xFF000000, 0.35F) : hov ? Theme.CARD_HOVER : Theme.FIELD);
			ItemStack st = k.icon();
			if (!st.isEmpty()) {
				c.getMatrices().pushMatrix();
				c.getMatrices().translate(cx + 3, cy + 2);
				c.getMatrices().scale(0.9F, 0.9F);
				c.drawItem(st, 0, 0);
				c.getMatrices().popMatrix();
			}
			Gfx.text(c, name, cx + 21, cy + 6, on ? Theme.TEXT : Theme.DIM);
			if (TextureStyles.CHOICES.containsKey(k.id())) {
				Gfx.circle(c, cx + cw - 5, cy + 5, 2.5F, Theme.GREEN);
			}
			final int idx = i;
			reg.region(cx, cy, cw, 20, () -> selectCat(idx));
			cx += cw + 4;
		}
		int gy = cy + 30;

		Cat k = TextureStyles.CATS.get(cat);
		TextureStyles.Choice cur = TextureStyles.CHOICES.get(k.id());
		int cols = Math.max(1, (w + GAP) / (TILE + GAP));
		int vh = MinecraftClient.getInstance().getWindow().getScaledHeight();
		int n = 0;

		// tile 0: vanilla
		{
			int tx = x, ty = gy;
			boolean on = cur == null, hov = in(mx, my, tx, ty, TILE, TILE);
			Gfx.rrectBorder(c, tx, ty, TILE, TILE, 8, on ? Theme.GREEN_BG : hov ? Theme.CARD_HOVER : Theme.CARD, on ? Theme.GREEN : Theme.CARD_BORDER);
			ItemStack st = k.icon();
			if (!st.isEmpty()) {
				c.getMatrices().pushMatrix();
				c.getMatrices().translate(tx + TILE / 2F - 16, ty + 8);
				c.getMatrices().scale(2F, 2F);
				c.drawItem(st, 0, 0);
				c.getMatrices().popMatrix();
			}
			Gfx.textC(c, "Vanilla", tx + TILE / 2, ty + TILE - 12, on ? Theme.TEXT : Theme.DIM);
			reg.region(tx, ty, TILE, TILE, this::vanilla);
			n++;
		}
		int shown = 0;
		for (Hit h : new ArrayList<>(hits)) {
			Tile t = tiles.computeIfAbsent(h.id(), kk -> new Tile());
			if (t.state == 3) {
				continue;
			}
			int tx = x + (n % cols) * (TILE + GAP), ty = gy + (n / cols) * (TILE + GAP);
			boolean visible = ty < vh && ty + TILE > 0;
			if (t.state == 0) {
				prepare(h, k, t); // the whole page at once (12 at a time), not only what is on screen
			}
			if (t.state == 2) {
				upload(h, t);
			}
			boolean active = cur != null && cur.project().equals(h.id());
			boolean hov = in(mx, my, tx, ty, TILE, TILE);
			Gfx.rrectBorder(c, tx, ty, TILE, TILE, 8, active ? Theme.GREEN_BG : hov ? Theme.CARD_HOVER : Theme.CARD, active ? Theme.GREEN : Theme.CARD_BORDER);
			if (t.id != null) {
				int s = hov ? TILE - 4 : TILE - 8; // grows a little under the mouse
				Gfx.texUv(c, t.id, tx + (TILE - s) / 2, ty + (TILE - s) / 2, s, s, 0, 0, t.w, t.h, t.w, t.h, 0xFFFFFFFF);
				final Hit hh = h;
				final Tile tt = t;
				reg.region(tx, ty, TILE, TILE, () -> use(hh, tt));
			} else {
				Gfx.textC(c, "...", tx + TILE / 2, ty + TILE / 2 - 4, Theme.DIM);
			}
			n++;
			shown++;
		}
		int rows = (n + cols - 1) / cols;
		int by = gy + rows * (TILE + GAP);

		// status + credit + more
		String st;
		if (!error.isEmpty()) {
			st = error;
		} else if (loading && hits.isEmpty()) {
			st = Lang.tr("Loading styles...", "A carregar estilos...");
		} else {
			st = note.isEmpty() ? TextureStylesMod.statusText() : note;
		}
		if (!st.isEmpty()) {
			Gfx.textClip(c, st, x, by + 2, w, error.isEmpty() ? Theme.DIM : Theme.RED);
			by += 12;
		}
		if (cur != null) {
			Gfx.textClip(c, Lang.tr("Credit: ", "Crédito: ") + cur.author() + " (" + cur.title() + ")", x, by + 2, w, Theme.DIM);
			by += 12;
		}
		if (!loading && error.isEmpty() && !hits.isEmpty() && hits.size() % PAGE == 0) {
			int bw = Gfx.tw(Lang.tr("Show more", "Mostrar mais")) + 24;
			boolean hov = in(mx, my, x, by + 2, bw, 20);
			Gfx.button(c, x, by + 2, bw, 20, hov, false);
			Gfx.textFit(c, Lang.tr("Show more", "Mostrar mais"), x + bw / 2, bw - 6, by + 8, Theme.TEXT);
			reg.region(x, by + 2, bw, 20, () -> {
				offset = hits.size();
				startCat();
			});
			by += 26;
		}
		return by - top;
	}
}
