package dev.sleepclient.gui;

import dev.sleepclient.Gfx;
import dev.sleepclient.Lang;
import dev.sleepclient.option.Option;
import dev.sleepclient.tiktok.TikGifts;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Pick TikTok gifts from a searchable grid with their pictures and prices, instead of typing their names. */
public class GiftPickerScreen extends Screen {

	private static final int TILE_W = 76, TILE_H = 78, GAP = 6;

	private final Screen parent;
	private final String label;
	private final Option.Text target;
	private final TextBox search = new TextBox(40);
	private final Map<String, String> picked = new LinkedHashMap<>();
	// the "custom gift" form
	private boolean form;
	private final TextBox formName = new TextBox(40), formCoins = new TextBox(6, Character::isDigit);
	private int formFocus;
	private java.nio.file.Path formPicture;
	private String listStatus = "";
	private List<TikGifts.Gift> all = new ArrayList<>();
	private float scroll;

	private record Hit(int x, int y, int w, int h, Runnable run, Runnable right) {
		Hit(int x, int y, int w, int h, Runnable run) {
			this(x, y, w, h, run, null);
		}
	}

	private final List<Hit> hits = new ArrayList<>();

	public GiftPickerScreen(Screen parent, String label, Option.Text target) {
		super(Component.literal(label));
		this.parent = parent;
		this.label = label;
		this.target = target;
		for (String n : target.get().split("[,;]")) {
			if (!n.isBlank()) {
				picked.put(TikGifts.key(n), n.trim());
			}
		}
		all = TikGifts.all();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		dev.sleepclient.compat.Mc.setScreen(Minecraft.getInstance(), parent);
	}

	private void save() {
		target.set(String.join(", ", picked.values()));
		dev.sleepclient.Config.save();
		onClose();
	}

	private List<TikGifts.Gift> shown() {
		String q = TikGifts.key(search.text());
		List<TikGifts.Gift> out = new ArrayList<>();
		for (TikGifts.Gift g : all) {
			if (q.isEmpty() || TikGifts.key(g.name()).contains(q)) {
				out.add(g);
			}
		}
		return out;
	}

	private static boolean in(int mx, int my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private void toggle(String name) {
		String k = TikGifts.key(name);
		if (picked.remove(k) == null) {
			picked.put(k, name);
		}
	}

	private void button(GuiGraphicsExtractor c, int mx, int my, int x, int y, int w, int h, String t, boolean primary, Runnable run) {
		boolean hov = in(mx, my, x, y, w, h);
		if (primary) {
			KuboButton.primary(c, x, y, w, h, t, hov);
		} else {
			KuboButton.neutral(c, x, y, w, h, t, hov);
		}
		hits.add(new Hit(x, y, w, h, run));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor c, int mx, int my, float delta) {
		super.extractRenderState(c, mx, my, delta);
		hits.clear();
		int w = Math.min(width - 16, 620), h = Math.min(height - 16, 370), x = (width - w) / 2, y = (height - h) / 2;
		Gfx.rrect(c, x, y, w, h, 10, Theme.PANEL);
		int ix = x + 14, iw = w - 28;
		Gfx.text(c, label, ix, y + 11, Theme.TEXT);
		List<TikGifts.Gift> list = shown();
		String count = (listStatus.isEmpty() ? "" : listStatus + "  ·  ") + list.size() + " " + Lang.tr("gifts", "presentes");
		Gfx.text(c, count, ix + iw - Gfx.tw(count), y + 11, Theme.DIM);
		int sy = y + 26;
		search.draw(c, ix, sy, iw, 20, ix + 8, iw - 16, true, Theme.accent(), Theme.TEXT);
		if (search.isEmpty()) {
			Gfx.text(c, Lang.tr("Search for a gift, or @someone who is live to load all gifts", "Pesquisa um presente, ou @alguém em live para carregar todos"), ix + 8, sy + 6, Theme.DIM);
		}
		int top = sy + 26, bottom = y + h - 40;
		int cols = Math.max(1, (iw + GAP) / (TILE_W + GAP));
		int tw = (iw - GAP * (cols - 1)) / cols;
		int rows = (list.size() + cols - 1) / cols;
		float max = Math.max(0, rows * (TILE_H + GAP) - GAP - (bottom - top));
		scroll = Math.max(0, Math.min(max, scroll));
		c.enableScissor(ix, top, ix + iw, bottom);
		for (int i = 0; i < list.size(); i++) {
			int tx = ix + (i % cols) * (tw + GAP), ty = top + (i / cols) * (TILE_H + GAP) - Math.round(scroll);
			if (ty + TILE_H < top || ty > bottom) {
				continue;
			}
			TikGifts.Gift g = list.get(i);
			boolean sel = picked.containsKey(TikGifts.key(g.name()));
			boolean hov = in(mx, my, tx, ty, tw, TILE_H) && my >= top && my < bottom;
			Gfx.rrectBorder(c, tx, ty, tw, TILE_H, 8, sel ? Gfx.blend(Theme.TILE, Theme.accent() | 0xFF000000, 0.3F) : hov ? Theme.TILE_HOVER : Theme.TILE,
					sel ? Theme.accent() : hov ? Theme.CARD_BORDER_HOVER : Theme.CARD_BORDER);
			Identifier icon = TikGifts.icon(g.name());
			int is = 36, icx = tx + (tw - is) / 2;
			if (icon != null) {
				Gfx.tex(c, icon, icx, ty + 6, is, is, 100, 100, 0xFFFFFFFF);
			} else {
				Gfx.textC(c, g.name().substring(0, 1).toUpperCase(Locale.ROOT), tx + tw / 2, ty + 20, Theme.DIM);
			}
			Gfx.textFit(c, g.name(), tx + tw / 2, tw - 8, ty + 46, sel ? 0xFFFFFFFF : Theme.TEXT);
			Gfx.textC(c, g.cost() > 0 ? String.valueOf(g.cost()) : "-", tx + tw / 2, ty + 60, 0xFFFFD34D);
			if (my >= top && my < bottom) {
				hits.add(new Hit(tx, ty, tw, TILE_H, () -> toggle(g.name()), g.custom() ? () -> {
					TikGifts.removeCustom(g.name());
					picked.remove(TikGifts.key(g.name()));
					all = TikGifts.all();
				} : null));
				if (hov && g.custom()) {
					Gfx.text(c, Lang.tr("Right click: delete", "Botão direito: apagar"), ix + 2, bottom - 10, Theme.DIM);
				}
			}
		}
		c.disableScissor();
		// bottom bar
		int by = y + h - 32;
		String q = search.text().trim();
		boolean exact = false;
		for (TikGifts.Gift g : list) {
			exact |= TikGifts.key(g.name()).equals(TikGifts.key(q));
		}
		int bx = ix;
		if (q.startsWith("@") && q.length() > 2) {
			String load = Lang.tr("Load all from ", "Carregar de ") + q;
			int bw = Math.min(190, Gfx.tw(load) + 18);
			button(c, mx, my, bx, by, bw, 22, load, true, () -> {
				TikGifts.loadFromLive(q.substring(1), s -> listStatus = s);
				search.clear();
			});
			bx += bw + 6;
		}
		String cu = Lang.tr("+ Custom", "+ Personalizado");
		int cw = Gfx.tw(cu) + 18;
		button(c, mx, my, bx, by, cw, 22, cu, false, () -> {
			form = true;
			formName.set(q.startsWith("@") ? "" : q);
			formCoins.clear();
			formPicture = null;
			formFocus = 0;
		});
		bx += cw + 6;
		String sel = picked.isEmpty() ? Lang.tr("Nothing picked", "Nada escolhido") : picked.size() + Lang.tr(" picked: ", " escolhidos: ") + String.join(", ", picked.values());
		int right = ix + iw - 76 - 6 - 62 - 6;
		Gfx.textClip(c, sel, bx, by + 7, Math.max(10, right - bx), Theme.DIM);
		button(c, mx, my, ix + iw - 76 - 6 - 62, by, 62, 22, Lang.tr("Clear", "Limpar"), false, picked::clear);
		button(c, mx, my, ix + iw - 76, by, 76, 22, Lang.tr("Done", "Feito"), true, this::save);
		if (form) {
			hits.clear();
			drawForm(c, mx, my);
		}
		if (TikGifts.all().size() != all.size()) {
			all = TikGifts.all(); // the full list arrived while this is open
		}
	}

	private void drawForm(GuiGraphicsExtractor c, int mx, int my) {
		c.fill(0, 0, width, height, 0x90000000);
		int w = 300, h = 168, x = (width - w) / 2, y = (height - h) / 2, ix = x + 14, iw = w - 28;
		Gfx.rrect(c, x, y, w, h, 10, Theme.PANEL);
		Gfx.text(c, Lang.tr("Custom gift", "Presente personalizado"), ix, y + 11, Theme.TEXT);
		Gfx.text(c, Lang.tr("Name (exactly as TikTok sends it)", "Nome (igual ao que o TikTok envia)"), ix, y + 28, Theme.DIM);
		formName.draw(c, ix, y + 40, iw, 20, ix + 8, iw - 16, formFocus == 0, Theme.accent(), Theme.TEXT);
		hits.add(new Hit(ix, y + 40, iw, 20, () -> formFocus = 0));
		Gfx.text(c, Lang.tr("Coins (optional)", "Moedas (opcional)"), ix, y + 66, Theme.DIM);
		formCoins.draw(c, ix, y + 78, 90, 20, ix + 8, 74, formFocus == 1, Theme.accent(), Theme.TEXT);
		hits.add(new Hit(ix, y + 78, 90, 20, () -> formFocus = 1));
		String pic = formPicture == null ? Lang.tr("Picture...", "Imagem...") : formPicture.getFileName().toString();
		button(c, mx, my, ix + 100, y + 78, iw - 100, 20, pic, false, this::pickPicture);
		boolean ok = !TikGifts.key(formName.text()).isEmpty();
		button(c, mx, my, ix + iw - 76 - 6 - 70, y + h - 32, 70, 22, Lang.tr("Cancel", "Cancelar"), false, () -> form = false);
		button(c, mx, my, ix + iw - 76, y + h - 32, 76, 22, Lang.tr("Add", "Adicionar"), ok, () -> {
			if (!ok) {
				return;
			}
			int coins = formCoins.isEmpty() ? 0 : Integer.parseInt(formCoins.text());
			TikGifts.addCustom(formName.text().trim(), coins, formPicture);
			picked.put(TikGifts.key(formName.text()), formName.text().trim());
			all = TikGifts.all();
			form = false;
			search.clear();
		});
	}

	private void pickPicture() {
		Thread t = new Thread(() -> {
			String path;
			try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
				org.lwjgl.PointerBuffer filters = stack.mallocPointer(1);
				filters.put(stack.UTF8("*.png"));
				filters.flip();
				path = org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog("Picture (PNG)", "", filters, "PNG", false);
			} catch (Throwable e) {
				return;
			}
			if (path != null && !path.isBlank()) {
				formPicture = java.nio.file.Path.of(path);
			}
		}, "kubo-gift-picture");
		t.setDaemon(true);
		t.start();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			if (click.x() >= h.x && click.x() < h.x + h.w && click.y() >= h.y && click.y() < h.y + h.h) {
				if (click.button() == 1) {
					if (h.right != null) {
						h.right.run();
					}
					return true;
				}
				Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1F));
				h.run.run();
				return true;
			}
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double h, double v) {
		scroll -= (float) v * 24F;
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		if (form) {
			if (input.key() == 256) {
				form = false;
			} else if (input.key() == 258) {
				formFocus = 1 - formFocus;
			} else {
				(formFocus == 0 ? formName : formCoins).key(input.key());
			}
			return true;
		}
		if (input.key() == 256) {
			onClose();
			return true;
		}
		search.key(input.key());
		scroll = 0;
		return true;
	}

	@Override
	public boolean charTyped(CharacterEvent input) {
		if (form) {
			(formFocus == 0 ? formName : formCoins).typed(input.codepoint());
			return true;
		}
		search.typed(input.codepoint());
		scroll = 0;
		return true;
	}
}
