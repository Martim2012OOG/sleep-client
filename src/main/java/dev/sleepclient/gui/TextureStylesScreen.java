package dev.sleepclient.gui;

import dev.sleepclient.Gfx;
import dev.sleepclient.Lang;
import dev.sleepclient.mods.TextureStylesMod;
import dev.sleepclient.pack.TextureStyles;
import dev.sleepclient.pack.TextureStyles.Cat;
import dev.sleepclient.pack.TextureStyles.Hit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * Texture Styles window (collections on the left, styles in the middle, inspector on the right), drawn like the
 * community texture managers: pick an item category, pick a style, it becomes the ACTIVE one. Styles are Modrinth packs.
 */
public class TextureStylesScreen extends Screen {

	private final Screen parent;
	private int cat;
	private float catScroll, gridScroll;
	private List<Hit> hits = new ArrayList<>();
	private volatile boolean loading;
	private volatile String error = "";
	private int loadId;
	private boolean newest;
	private final TextBox search = new TextBox(40);
	private boolean searchFocus;
	private Hit selected;
	private final Set<String> busy = ConcurrentHashMap.newKeySet();
	private boolean dirty;
	private String note = "";

	private static final int LEFT_W = 132, RIGHT_W = 138, CARD_H = 44, ROW_H = 22;

	public TextureStylesScreen(Screen parent) {
		super(Text.literal("Texture Styles"));
		this.parent = parent;
		TextureStyles.load();
		loadCat();
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void close() {
		if (dirty) {
			TextureStylesMod.apply();
		}
		dev.sleepclient.social.KuboPacks.releaseImages(); // frees the icon textures
		MinecraftClient.getInstance().setScreen(parent);
	}

	// ---- layout
	private int pw() {
		return Math.min(width - 12, 620);
	}

	private int ph() {
		return Math.min(height - 12, 340);
	}

	private int px() {
		return (width - pw()) / 2;
	}

	private int py() {
		return (height - ph()) / 2;
	}

	private int bodyTop() {
		return py() + 40;
	}

	private int bodyBottom() {
		return py() + ph() - 20;
	}

	private int midX() {
		return px() + LEFT_W + 8;
	}

	private int midW() {
		return pw() - LEFT_W - RIGHT_W - 16;
	}

	private int cols() {
		return Math.max(1, midW() / 100);
	}

	private int cardW() {
		return (midW() - (cols() - 1) * 4) / cols();
	}

	private static boolean in(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	// ---- data
	private void loadCat() {
		final int id = ++loadId;
		final Cat c = TextureStyles.CATS.get(cat);
		final String q = search.text();
		final boolean nw = newest;
		loading = true;
		error = "";
		hits = new ArrayList<>();
		gridScroll = 0;
		Thread t = new Thread(() -> {
			try {
				List<Hit> r = TextureStyles.search(c, q, 0, nw);
				MinecraftClient.getInstance().execute(() -> {
					if (id == loadId) {
						hits = r;
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

	private void pick(Hit h) {
		final Cat c = TextureStyles.CATS.get(cat);
		if (!busy.add(h.id())) {
			return;
		}
		note = Lang.tr("Downloading ", "A descarregar ") + h.title() + " ...";
		Thread t = new Thread(() -> {
			try {
				TextureStyles.Choice ch = TextureStyles.resolve(h);
				TextureStyles.download(ch);
				TextureStyles.CHOICES.put(c.id(), ch);
				TextureStyles.save();
				dirty = true;
				note = h.title() + Lang.tr(" is ready. Close this window to apply.", " está pronto. Fecha esta janela para aplicar.");
			} catch (Throwable e) {
				note = Lang.tr("Failed: ", "Falhou: ") + e.getMessage();
			} finally {
				busy.remove(h.id());
			}
		}, "kubo-styles-pick");
		t.setDaemon(true);
		t.start();
	}

	private void reset() {
		Cat c = TextureStyles.CATS.get(cat);
		if (TextureStyles.CHOICES.remove(c.id()) != null) {
			TextureStyles.save();
			dirty = true;
			note = Lang.tr("Back to vanilla. Close this window to apply.", "Voltou ao vanilla. Fecha esta janela para aplicar.");
		}
	}

	private static String compact(long n) {
		return n >= 1_000_000 ? (n / 100_000) / 10.0 + "M" : n >= 1000 ? (n / 100) / 10.0 + "k" : String.valueOf(n);
	}

	// ---- drawing
	@Override
	public void render(DrawContext c, int mx, int my, float delta) {
		super.render(c, mx, my, delta);
		int x = px(), y = py(), w = pw(), h = ph();
		Gfx.rrect(c, x, y, w, h, 10, Theme.PANEL);
		Gfx.text(c, "Texture Styles", x + 14, y + 10, Theme.TEXT);
		Gfx.textClip(c, Lang.tr("Pick a style for each item. The original packs are never changed.", "Escolhe um estilo para cada item. Os packs originais nunca são alterados."),
				x + 14, y + 24, w - 200, Theme.DIM);
		// search + sort + close
		int sx = x + w - 230, sy = y + 8, sw = 118;
		Gfx.rrect(c, sx, sy, sw, 20, 6, Theme.FIELD);
		search.draw(c, sx, sy, sw, 20, sx + 7, sw - 12, searchFocus, Theme.accent(), Theme.TEXT);
		if (search.isEmpty() && !searchFocus) {
			Gfx.text(c, Lang.tr("Search styles...", "Procurar estilos..."), sx + 7, sy + 6, Theme.DIM);
		}
		Gfx.button(c, sx + sw + 6, sy, 52, 20, in(mx, my, sx + sw + 6, sy, 52, 20), false);
		Gfx.textFit(c, newest ? "Newest" : "Top", sx + sw + 6 + 26, 46, sy + 6, Theme.TEXT);
		Gfx.button(c, x + w - 62, sy, 50, 20, in(mx, my, x + w - 62, sy, 50, 20), false);
		Gfx.textFit(c, Lang.tr("Done", "Feito"), x + w - 37, 44, sy + 6, Theme.TEXT);

		int top = bodyTop(), bot = bodyBottom();
		drawLeft(c, mx, my, x + 8, top, LEFT_W, bot - top);
		drawMid(c, mx, my, midX(), top, midW(), bot - top);
		drawRight(c, mx, my, x + w - RIGHT_W - 8, top, RIGHT_W, bot - top);
		Gfx.textClip(c, note, x + 14, bot + 6, w - 28, Theme.DIM);
	}

	private void drawLeft(DrawContext c, int mx, int my, int x, int y, int w, int h) {
		Gfx.rrect(c, x, y, w, h, 7, Theme.FIELD);
		int n = TextureStyles.CATS.size();
		float max = Math.max(0, n * ROW_H - h + 6);
		catScroll = Math.max(0, Math.min(max, catScroll));
		c.enableScissor(x, y, x + w, y + h);
		for (int i = 0; i < n; i++) {
			int ry = y + 3 + i * ROW_H - Math.round(catScroll);
			if (ry + ROW_H < y || ry > y + h) {
				continue;
			}
			Cat k = TextureStyles.CATS.get(i);
			boolean on = i == cat, hov = in(mx, my, x + 3, ry, w - 6, ROW_H - 1) && in(mx, my, x, y, w, h);
			Gfx.rrect(c, x + 3, ry, w - 6, ROW_H - 1, 5, on ? Gfx.blend(Theme.FIELD, Theme.accent() | 0xFF000000, 0.3F) : hov ? Theme.CARD : Theme.FIELD);
			ItemStack st = k.icon();
			if (!st.isEmpty()) {
				c.getMatrices().pushMatrix();
				c.getMatrices().translate(x + 6, ry + 3);
				c.getMatrices().scale(0.9F, 0.9F);
				c.drawItem(st, 0, 0);
				c.getMatrices().popMatrix();
			}
			String t = TextureStyles.chosenTitle(k.id());
			Gfx.textClip(c, Lang.tr(k.en(), k.pt()), x + 24, ry + (t == null ? 6 : 2), w - 30, Theme.TEXT);
			if (t != null) {
				Gfx.textClip(c, t, x + 24, ry + 11, w - 30, Theme.GREEN);
			}
		}
		c.disableScissor();
	}

	private void drawMid(DrawContext c, int mx, int my, int x, int y, int w, int h) {
		Cat k = TextureStyles.CATS.get(cat);
		Gfx.text(c, Lang.tr(k.en(), k.pt()), x + 2, y - 11, Theme.TEXT);
		Gfx.rrect(c, x - 2, y, w + 4, h, 7, Theme.FIELD);
		if (loading) {
			Gfx.textClip(c, Lang.tr("Loading styles...", "A carregar estilos..."), x + 8, y + 8, w - 16, Theme.DIM);
			return;
		}
		if (!error.isEmpty()) {
			Gfx.textClip(c, error, x + 8, y + 8, w - 16, Theme.RED);
			return;
		}
		if (hits.isEmpty()) {
			Gfx.textClip(c, Lang.tr("No styles found. Try another search.", "Nenhum estilo encontrado. Tenta outra pesquisa."), x + 8, y + 8, w - 16, Theme.DIM);
			return;
		}
		int cols = cols(), cw = cardW();
		int rows = (hits.size() + cols - 1) / cols;
		float max = Math.max(0, rows * (CARD_H + 4) - h + 6);
		gridScroll = Math.max(0, Math.min(max, gridScroll));
		TextureStyles.Choice cur = TextureStyles.CHOICES.get(k.id());
		c.enableScissor(x - 2, y, x + w + 2, y + h);
		for (int i = 0; i < hits.size(); i++) {
			int cx = x + (i % cols) * (cw + 4), cy = y + 3 + (i / cols) * (CARD_H + 4) - Math.round(gridScroll);
			if (cy + CARD_H < y || cy > y + h) {
				continue;
			}
			Hit hit = hits.get(i);
			boolean hov = in(mx, my, cx, cy, cw, CARD_H) && in(mx, my, x, y, w, h);
			boolean active = cur != null && cur.project().equals(hit.id());
			boolean sel = selected != null && selected.id().equals(hit.id());
			Gfx.rrect(c, cx, cy, cw, CARD_H, 6, active ? Theme.GREEN_BG : hov || sel ? Theme.CARD_HOVER : Theme.CARD);
			picture(c, hit, cx + 5, cy + 6, 32);
			Gfx.textClip(c, hit.title(), cx + 42, cy + 9, cw - 47, Theme.TEXT);
			Gfx.textClip(c, hit.author() + "  " + compact(hit.downloads()), cx + 42, cy + 23, cw - 47, Theme.DIM);
			if (busy.contains(hit.id())) {
				Gfx.text(c, "...", cx + cw - 16, cy + 5, Theme.accent());
			}
		}
		c.disableScissor();
	}

	/** The style's picture (its Modrinth icon, loaded in the background); a letter tile until it arrives. */
	private void picture(DrawContext c, Hit h, int x, int y, int size) {
		dev.sleepclient.social.KuboPacks.Img t = h.icon().isEmpty() ? null : dev.sleepclient.social.KuboPacks.image(h.icon(), 128);
		if (t != null) {
			Gfx.tex(c, t.id(), x, y, size, size, t.width(), t.height(), 0xFFFFFFFF);
		} else {
			Gfx.rrect(c, x, y, size, size, 5, 0xFF2C2F38);
			String ini = h.title().isEmpty() ? "?" : h.title().substring(0, 1).toUpperCase();
			Gfx.textRaw(c, ini, x + size / 2 - Gfx.twRaw(ini) / 2, y + size / 2 - 4, Theme.DIM);
		}
	}

	private void drawRight(DrawContext c, int mx, int my, int x, int y, int w, int h) {
		Gfx.rrect(c, x, y, w, h, 7, Theme.FIELD);
		Gfx.text(c, "INSPECTOR", x + 8, y + 7, Theme.DIM);
		Cat k = TextureStyles.CATS.get(cat);
		ItemStack st = k.icon();
		if (selected != null) {
			picture(c, selected, x + w / 2 - 24, y + 22, 48);
		} else if (!st.isEmpty()) {
			c.getMatrices().pushMatrix();
			c.getMatrices().translate(x + w / 2F - 24, y + 22);
			c.getMatrices().scale(3F, 3F);
			c.drawItem(st, 0, 0);
			c.getMatrices().popMatrix();
		}
		TextureStyles.Choice cur = TextureStyles.CHOICES.get(k.id());
		int ty = y + 78;
		Gfx.textClip(c, Lang.tr("Now:", "Agora:") + " " + (cur == null ? "Vanilla" : cur.title()), x + 8, ty, w - 16, Theme.TEXT);
		if (cur != null) {
			Gfx.textClip(c, cur.author(), x + 8, ty + 12, w - 16, Theme.DIM);
		}
		if (selected != null) {
			Gfx.textClip(c, selected.title(), x + 8, ty + 30, w - 16, Theme.accent());
			String d = selected.desc();
			Gfx.textClip(c, d, x + 8, ty + 42, w - 16, Theme.DIM);
			Gfx.textClip(c, d.length() > 28 ? d.substring(Math.min(28, d.length())) : "", x + 8, ty + 53, w - 16, Theme.DIM);
		}
		int by = y + h - 56;
		boolean canUse = selected != null && !busy.contains(selected.id());
		Gfx.button(c, x + 8, by, w - 16, 20, canUse && in(mx, my, x + 8, by, w - 16, 20), false);
		Gfx.textFit(c, Lang.tr("Use this style", "Usar este estilo"), x + w / 2, w - 24, by + 6, canUse ? Theme.TEXT : Theme.DIM);
		boolean canReset = cur != null;
		Gfx.button(c, x + 8, by + 26, w - 16, 20, canReset && in(mx, my, x + 8, by + 26, w - 16, 20), false);
		Gfx.textFit(c, Lang.tr("Back to vanilla", "Voltar ao vanilla"), x + w / 2, w - 24, by + 32, canReset ? Theme.TEXT : Theme.DIM);
	}

	// ---- input
	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		double mx = click.x(), my = click.y();
		int x = px(), y = py(), w = pw();
		int sx = x + w - 230, sy = y + 8, sw = 118;
		searchFocus = false;
		if (in(mx, my, sx, sy, sw, 20)) {
			searchFocus = true;
			search.click(mx);
			return true;
		}
		if (in(mx, my, sx + sw + 6, sy, 52, 20)) {
			newest = !newest;
			loadCat();
			return true;
		}
		if (in(mx, my, x + w - 62, sy, 50, 20)) {
			close();
			return true;
		}
		int top = bodyTop(), bot = bodyBottom();
		// categories
		if (in(mx, my, x + 8, top, LEFT_W, bot - top)) {
			int i = (int) ((my - top - 3 + catScroll) / ROW_H);
			if (i >= 0 && i < TextureStyles.CATS.size() && i != cat) {
				cat = i;
				selected = null;
				loadCat();
			}
			return true;
		}
		// styles
		if (in(mx, my, midX(), top, midW(), bot - top) && !loading && error.isEmpty()) {
			int cols = cols(), cw = cardW();
			for (int i = 0; i < hits.size(); i++) {
				int cx = midX() + (i % cols) * (cw + 4), cy = top + 3 + (i / cols) * (CARD_H + 4) - Math.round(gridScroll);
				if (in(mx, my, cx, cy, cw, CARD_H)) {
					selected = hits.get(i);
					if (doubled) {
						pick(selected);
					}
					return true;
				}
			}
			return true;
		}
		// inspector buttons
		int rx = x + w - RIGHT_W - 8, by = bot - 56 + 0;
		by = (top + (bot - top)) - 56;
		if (in(mx, my, rx + 8, by, RIGHT_W - 16, 20) && selected != null) {
			pick(selected);
			return true;
		}
		if (in(mx, my, rx + 8, by + 26, RIGHT_W - 16, 20)) {
			reset();
			return true;
		}
		return true;
	}

	@Override
	public boolean mouseReleased(Click click) {
		search.release();
		return super.mouseReleased(click);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
		if (mx < px() + LEFT_W + 8) {
			catScroll -= (float) vAmount * 24F;
		} else {
			gridScroll -= (float) vAmount * 30F;
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		int k = input.key();
		if (searchFocus) {
			if (k == 256) {
				searchFocus = false;
				return true;
			}
			if (k == 257 || k == 335) { // Enter
				loadCat();
				return true;
			}
			search.key(k);
			return true;
		}
		if (k == 256) {
			close();
			return true;
		}
		return false;
	}

	@Override
	public boolean charTyped(CharInput input) {
		if (searchFocus) {
			int cp = input.codepoint();
			if (cp >= 32 && cp != 127) {
				search.typed(cp);
			}
			return true;
		}
		return false;
	}
}
