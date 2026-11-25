package dev.solclient.gui;

import dev.solclient.Gfx;
import dev.solclient.Keys;
import dev.solclient.Lang;
import dev.solclient.builder.Placement;
import dev.solclient.mods.KuboBuilderMod;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * Caos Builder menu: Schematics (your files, one click to load), Place (big buttons to move, turn, mirror, layers),
 * Materials (what you need / have / placed) and Find (search schematic websites; what you download there is
 * imported here by itself).
 */
public class BuilderScreen extends Screen {

	private static final String[] TABS = {"Schematics", "Place", "Materials", "Find"};
	private static final String[][] SITES = {
			{"Bloxelizer", "https://bloxelizer.com/schematics?q=%s"},
			{"Minecraft-Schematics", "https://www.minecraft-schematics.com/search/?q=%s"},
			{"Planet Minecraft", "https://www.planetminecraft.com/projects/?keywords=%s&share=schematic"},
			{"Abfielder", "https://abfielder.com/?s=%s"},
			{"Reddit r/litematica", "https://www.reddit.com/r/litematica/search/?q=%s"}};

	private final Screen parent;
	private static int tab;
	private final TextBox filter = new TextBox(40), query = new TextBox(60);
	private boolean queryFocus;
	private float scroll;
	private final List<int[]> hits = new ArrayList<>();
	private final List<Runnable> acts = new ArrayList<>();
	private long openedAt;
	private List<Path> files = new ArrayList<>();
	private List<Path> downloads = new ArrayList<>();
	private long filesAt;

	public BuilderScreen(Screen parent) {
		super(Text.literal("Caos Builder"));
		this.parent = parent;
	}

	private static KuboBuilderMod b() {
		return KuboBuilderMod.instance;
	}

	@Override
	protected void init() {
		openedAt = System.currentTimeMillis();
		refreshFiles();
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	private void refreshFiles() {
		filesAt = System.currentTimeMillis();
		List<Path> l = new ArrayList<>();
		try {
			Files.createDirectories(KuboBuilderMod.schematicsDir());
			try (var st = Files.walk(KuboBuilderMod.schematicsDir(), 3)) {
				st.filter(p -> isSchem(p.getFileName().toString())).forEach(l::add);
			}
		} catch (Exception ignored) {
		}
		l.sort((a, c) -> a.getFileName().toString().compareToIgnoreCase(c.getFileName().toString()));
		files = l;
		// new schematics in your Downloads (from the Find tab websites)
		List<Path> d = new ArrayList<>();
		for (Path dir : new Path[] {Path.of(System.getProperty("user.home"), "Downloads"), Path.of(System.getProperty("user.home"), "Desktop")}) {
			if (!Files.isDirectory(dir)) {
				continue;
			}
			try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
				for (Path p : ds) {
					if (isSchem(p.getFileName().toString()) && !Files.exists(KuboBuilderMod.schematicsDir().resolve(p.getFileName()))) {
						d.add(p);
					}
				}
			} catch (Exception ignored) {
			}
		}
		d.sort((a, c) -> {
			try {
				return Files.getLastModifiedTime(c).compareTo(Files.getLastModifiedTime(a));
			} catch (Exception e) {
				return 0;
			}
		});
		downloads = d;
	}

	private static boolean isSchem(String n) {
		String l = n.toLowerCase(Locale.ROOT);
		return l.endsWith(".litematic") || l.endsWith(".schem") || l.endsWith(".schematic") || l.endsWith(".nbt");
	}

	// ------------------------------------------------------------------ helpers
	private boolean in(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private void button(DrawContext c, int mx, int my, int x, int y, int w, int h, String label, int col, Runnable r) {
		boolean hov = in(mx, my, x, y, w, h);
		Gfx.rrect(c, x, y, w, h, 6, hov ? Gfx.blend(col, 0xFFFFFFFF, 0.15F) : col);
		Gfx.textC(c, label, x + w / 2, y + h / 2 - 4, 0xFFFFFFFF);
		hits.add(new int[] {x, y, w, h});
		acts.add(r);
	}

	private static String cut(String s, int w) {
		if (Gfx.twRaw(s) <= w) {
			return s;
		}
		while (s.length() > 1 && Gfx.twRaw(s + "..") > w) {
			s = s.substring(0, s.length() - 1);
		}
		return s + "..";
	}

	// ------------------------------------------------------------------ render
	@Override
	public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void render(DrawContext c, int mx, int my, float delta) {
		hits.clear();
		acts.clear();
		if (System.currentTimeMillis() - filesAt > 3000) {
			refreshFiles();
		}
		int pw = Math.min(460, width - 24), ph = Math.min(330, height - 24);
		int px = width / 2 - pw / 2, py = height / 2 - ph / 2;
		c.fill(0, 0, width, height, 0x88000000);
		Gfx.rrectBorder(c, px, py, pw, ph, 12, 0xF4121318, Theme.PANEL_BORDER);
		Gfx.tex(c, Theme.icon2("kubo_builder"), px + 10, py + 8, 16, 16, 64, 64, Theme.accent());
		Gfx.text(c, "Caos Builder", px + 31, py + 12, 0xFFFFFFFF);
		// tabs
		int tx = px + pw - 10;
		for (int i = TABS.length - 1; i >= 0; i--) {
			int w = Gfx.tw(TABS[i]) + 16;
			tx -= w + 4;
			final int idx = i;
			boolean sel = tab == i;
			Gfx.rrect(c, tx, py + 7, w, 18, 6, sel ? Theme.accent() : in(mx, my, tx, py + 7, w, 18) ? Theme.OFF_BG_HOVER : Theme.OFF_BG);
			Gfx.text(c, TABS[i], tx + 8, py + 12, 0xFFFFFFFF);
			hits.add(new int[] {tx, py + 7, w, 18});
			acts.add(() -> {
				tab = idx;
				scroll = 0;
			});
		}
		int cx = px + 10, cy = py + 32, cw = pw - 20, ch = ph - 42;
		KuboBuilderMod m = b();
		if (m == null) {
			return;
		}
		switch (tab) {
			case 1 -> place(c, mx, my, cx, cy, cw, ch, m);
			case 2 -> materials(c, mx, my, cx, cy, cw, ch, m);
			case 3 -> find(c, mx, my, cx, cy, cw, ch);
			default -> schematics(c, mx, my, cx, cy, cw, ch, m);
		}
		if (System.currentTimeMillis() - m.messageAt < 5000 && !m.message.isEmpty()) {
			Gfx.textRaw(c, cut(m.message, pw - 20), px + 10, py + ph + 4, 0xFFE6E8EE);
		}
	}

	private void schematics(DrawContext c, int mx, int my, int x, int y, int w, int h, KuboBuilderMod m) {
		filter.draw(c, x, y, w - 120, 18, x + 6, w - 132, true, Theme.accent(), Theme.TEXT);
		if (filter.text().isEmpty()) {
			Gfx.text(c, "Search your schematics...", x + 6, y + 5, 0xFF5E6272);
		}
		button(c, mx, my, x + w - 114, y, 114, 18, "Open folder", Theme.OFF_BG, () -> {
			try {
				Files.createDirectories(KuboBuilderMod.schematicsDir());
			} catch (Exception ignored) {
			}
			net.minecraft.util.Util.getOperatingSystem().open(KuboBuilderMod.schematicsDir().toFile());
		});
		int ly = y + 24, lh = h - 24 - (m.placement != null ? 26 : 0);
		String q = filter.text().toLowerCase(Locale.ROOT);
		List<Path> shown = new ArrayList<>();
		for (Path p : files) {
			if (q.isEmpty() || p.getFileName().toString().toLowerCase(Locale.ROOT).contains(q)) {
				shown.add(p);
			}
		}
		if (shown.isEmpty()) {
			Gfx.textC(c, files.isEmpty() ? "No schematics yet: put .litematic / .schem / .nbt files in the folder" : "Nothing found", x + w / 2, ly + 20, Theme.DIM);
			Gfx.textC(c, "or find some in the Find tab", x + w / 2, ly + 34, Theme.DIM);
		}
		int max = Math.max(0, shown.size() * 24 - lh);
		scroll = Math.max(0, Math.min(max, scroll));
		c.enableScissor(x, ly, x + w, ly + lh);
		int ry = ly - Math.round(scroll);
		for (Path p : shown) {
			if (ry + 24 > ly && ry < ly + lh) {
				boolean current = m.placement != null && m.placement.file.equals(p.toAbsolutePath().toString());
				boolean hov = in(mx, my, x, ry, w, 22) && my >= ly && my < ly + lh;
				Gfx.rrect(c, x, ry, w, 22, 6, current ? Gfx.blend(Theme.accent(), 0xFF121318, 0.7F) : hov ? Theme.OFF_BG_HOVER : 0xFF17181F);
				String n = p.getFileName().toString();
				String ext = n.substring(n.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT);
				Gfx.rrect(c, x + 5, ry + 5, Gfx.twRaw(ext) + 8, 12, 4, 0xFF2A2D38);
				Gfx.textRaw(c, ext, x + 9, ry + 7, 0xFFB9BDC9);
				Gfx.textRaw(c, cut(n.substring(0, n.lastIndexOf('.')), w - 140), x + Gfx.twRaw(ext) + 20, ry + 7, 0xFFFFFFFF);
				String act = current ? Lang.tr("Loaded", "Carregado") : Lang.tr("Load", "Carregar");
				int bw = Gfx.twRaw(act) + 16;
				Gfx.rrect(c, x + w - bw - 5, ry + 3, bw, 16, 5, current ? 0xFF2A2D38 : Theme.accent());
				Gfx.textRaw(c, act, x + w - bw + 3, ry + 7, 0xFFFFFFFF);
				if (hov && !current) {
					hits.add(new int[] {x, Math.max(ry, ly), w, 22});
					acts.add(() -> {
						if (m.load(p)) {
							tab = 1;
						}
					});
				}
			}
			ry += 24;
		}
		c.disableScissor();
		if (m.placement != null) {
			int by = y + h - 20;
			button(c, mx, my, x, by, 120, 20, "Unload", 0xFF7F1D1D, m::unload);
			Gfx.textRaw(c, cut(m.placement.schem.name, w - 140), x + 130, by + 6, Theme.DIM);
		}
	}

	private void place(DrawContext c, int mx, int my, int x, int y, int w, int h, KuboBuilderMod m) {
		Placement p = m.placement;
		if (p == null) {
			Gfx.textC(c, "Load a schematic first (Schematics tab)", x + w / 2, y + h / 2 - 4, Theme.DIM);
			return;
		}
		Gfx.textRaw(c, p.schem.name + "  ·  " + p.wx() + " x " + p.schem.sy + " x " + p.wz(), x, y, 0xFFFFFFFF);
		Gfx.textRaw(c, "X " + p.origin.getX() + "   Y " + p.origin.getY() + "   Z " + p.origin.getZ() + "   ·  " + p.rotation * 90 + "°"
				+ (p.mirrorX ? "  mirror X" : p.mirrorZ ? "  mirror Z" : ""), x, y + 12, Theme.DIM);
		// move pad (relative to where you look)
		int s = 30, gx = x + 20, gy = y + 34;
		button(c, mx, my, gx + s, gy, s - 2, s - 2, "↑", Theme.OFF_BG, () -> m.moveRelative(1, 0, 0));
		button(c, mx, my, gx, gy + s, s - 2, s - 2, "←", Theme.OFF_BG, () -> m.moveRelative(0, -1, 0));
		button(c, mx, my, gx + 2 * s, gy + s, s - 2, s - 2, "→", Theme.OFF_BG, () -> m.moveRelative(0, 1, 0));
		button(c, mx, my, gx + s, gy + 2 * s, s - 2, s - 2, "↓", Theme.OFF_BG, () -> m.moveRelative(-1, 0, 0));
		button(c, mx, my, gx + s, gy + s, s - 2, s - 2, "•", Theme.accent(), m::hereAtMyFeet);
		button(c, mx, my, gx + 3 * s + 10, gy, 60, s - 2, Lang.tr("Up", "Subir"), Theme.OFF_BG, () -> m.move(0, 1, 0));
		button(c, mx, my, gx + 3 * s + 10, gy + 2 * s, 60, s - 2, Lang.tr("Down", "Descer"), Theme.OFF_BG, () -> m.move(0, -1, 0));
		Gfx.textC(c, "Arrows / PgUp / PgDn in game too", gx + 2 * s, gy + 3 * s + 4, 0xFF6B7080);
		// right column: rotate, mirror, layers, show/hide
		int rx = x + w / 2 + 10, rw = w / 2 - 10, ry = y + 34;
		button(c, mx, my, rx, ry, rw / 2 - 3, 24, Lang.tr("Rotate", "Rodar") + " ↻", Theme.OFF_BG, m::rotate);
		button(c, mx, my, rx + rw / 2 + 3, ry, rw / 2 - 3, 24, Lang.tr("Mirror", "Espelhar"), Theme.OFF_BG, m::mirror);
		ry += 30;
		String[] lm = {Lang.tr("All layers", "Todas"), Lang.tr("One layer", "Uma camada"), Lang.tr("Up to layer", "Até à camada")};
		for (int i = 0; i < 3; i++) {
			final int idx = i;
			int bw = (rw - 6) / 3;
			button(c, mx, my, rx + i * (bw + 3), ry, bw, 20, lm[i], m.layerMode == i ? Theme.accent() : Theme.OFF_BG, () -> {
				m.layerMode = idx;
				m.changed();
			});
		}
		ry += 26;
		if (m.layerMode != 0) {
			button(c, mx, my, rx, ry, 40, 20, "-", Theme.OFF_BG, () -> {
				m.layer--;
				m.changed();
			});
			Gfx.textC(c, "Y " + m.layer, rx + rw / 2, ry + 6, 0xFFFFFFFF);
			button(c, mx, my, rx + rw - 40, ry, 40, 20, "+", Theme.OFF_BG, () -> {
				m.layer++;
				m.changed();
			});
			ry += 26;
		}
		button(c, mx, my, rx, ry, rw, 20, m.visible ? Lang.tr("Hide", "Esconder") : Lang.tr("Show", "Mostrar"), Theme.OFF_BG, () -> m.visible = !m.visible);
		ry += 26;
		// progress
		int total = Math.max(1, p.schem.nonAir);
		Gfx.textRaw(c, Lang.tr("Built ", "Construído ") + m.correct + " / " + total + "  (" + Math.round(m.correct * 100F / total) + "%)", rx, ry, 0xFF4ADE80);
		Gfx.rrectFree(c, rx, ry + 12, rw, 5, 2.5F, 0x33FFFFFF);
		Gfx.rrectFree(c, rx, ry + 12, Math.max(3, rw * m.correct / (float) total), 5, 2.5F, 0xFF4ADE80);
		Gfx.textRaw(c, m.missing + Lang.tr(" missing  ·  ", " em falta  ·  ") + m.wrong + Lang.tr(" wrong", " errados"), rx, ry + 22, Theme.DIM);
		// key hints
		KuboBuilderMod b = m;
		String hint = Keys.name(b.rotateKey.code()) + " " + Lang.tr("rotate", "rodar") + "  ·  " + Keys.name(b.mirrorKey.code()) + " " + Lang.tr("mirror", "espelhar")
				+ "  ·  " + Keys.name(b.layerUpKey.code()) + "/" + Keys.name(b.layerDownKey.code()) + " " + Lang.tr("layer", "camada") + "  ·  "
				+ Keys.name(b.menuKey.code()) + " menu";
		Gfx.textRaw(c, cut(hint, w), x, y + h - 10, 0xFF6B7080);
	}

	private void materials(DrawContext c, int mx, int my, int x, int y, int w, int h, KuboBuilderMod m) {
		if (m.placement == null) {
			Gfx.textC(c, "Load a schematic first (Schematics tab)", x + w / 2, y + h / 2 - 4, Theme.DIM);
			return;
		}
		List<Object[]> l = m.materials();
		Gfx.text(c, "Block", x + 24, y, Theme.DIM);
		Gfx.text(c, "Needed", x + w - 200, y, Theme.DIM);
		Gfx.text(c, "Placed", x + w - 130, y, Theme.DIM);
		Gfx.text(c, "You have", x + w - 64, y, Theme.DIM);
		int ly = y + 14, lh = h - 34;
		int max = Math.max(0, l.size() * 20 - lh);
		scroll = Math.max(0, Math.min(max, scroll));
		c.enableScissor(x, ly, x + w, ly + lh);
		int ry = ly - Math.round(scroll);
		for (Object[] r : l) {
			if (ry + 20 > ly && ry < ly + lh) {
				Block b = (Block) r[0];
				int need = (Integer) r[1], placed = (Integer) r[2], have = (Integer) r[3];
				int left = need - placed;
				Gfx.rrect(c, x, ry, w, 18, 5, left <= 0 ? 0xFF13261B : 0xFF17181F);
				c.drawItem(new ItemStack(b.asItem()), x + 2, ry + 1);
				Gfx.textRaw(c, cut(b.getName().getString(), w - 240), x + 24, ry + 5, 0xFFFFFFFF);
				Gfx.textRaw(c, need + (need >= 64 ? "  (" + need / 64 + "st)" : ""), x + w - 200, ry + 5, 0xFFE6E8EE);
				Gfx.textRaw(c, Integer.toString(placed), x + w - 130, ry + 5, left <= 0 ? 0xFF4ADE80 : 0xFFE6E8EE);
				Gfx.textRaw(c, Integer.toString(have), x + w - 64, ry + 5, have >= left ? 0xFF4ADE80 : 0xFFFF8A8A);
			}
			ry += 20;
		}
		c.disableScissor();
		button(c, mx, my, x, y + h - 18, 160, 18, "Copy list", Theme.OFF_BG, () -> {
			StringBuilder sb = new StringBuilder(m.placement.schem.name + "\n");
			for (Object[] r : l) {
				int need = (Integer) r[1] - (Integer) r[2];
				if (need > 0) {
					sb.append(((Block) r[0]).getName().getString()).append(": ").append(need).append('\n');
				}
			}
			MinecraftClient.getInstance().keyboard.setClipboard(sb.toString());
			m.say(Lang.tr("Missing blocks copied", "Blocos em falta copiados"));
		});
	}

	private void find(DrawContext c, int mx, int my, int x, int y, int w, int h) {
		Gfx.text(c, "Search schematics on the internet", x, y, 0xFFFFFFFF);
		query.draw(c, x, y + 14, w, 20, x + 6, w - 12, queryFocus, Theme.accent(), Theme.TEXT);
		if (query.text().isEmpty()) {
			Gfx.text(c, "castle, japanese house, statue...", x + 6, y + 20, 0xFF5E6272);
		}
		hits.add(new int[] {x, y + 14, w, 20});
		acts.add(() -> queryFocus = true);
		int bx = x, by = y + 40;
		for (String[] s : SITES) {
			int bw = Gfx.twRaw(s[0]) + 18;
			if (bx + bw > x + w) {
				bx = x;
				by += 24;
			}
			button(c, mx, my, bx, by, bw, 20, s[0], Theme.accent(), () -> {
				String q = URLEncoder.encode(query.text().trim(), StandardCharsets.UTF_8);
				net.minecraft.util.Util.getOperatingSystem().open(URI.create(String.format(s[1], q)));
			});
			bx += bw + 6;
		}
		by += 30;
		Gfx.textRaw(c, cut(Lang.tr("Download a .litematic / .schem there: it shows up below, click to add it.",
				"Transfere um .litematic / .schem lá: aparece aqui em baixo, clica para o adicionar."), w), x, by, Theme.DIM);
		by += 16;
		Gfx.text(c, "New in your Downloads", x, by, 0xFFFFFFFF);
		by += 14;
		if (downloads.isEmpty()) {
			Gfx.text(c, "Nothing yet", x, by + 2, 0xFF6B7080);
		}
		for (int i = 0; i < Math.min(6, downloads.size()); i++) {
			Path p = downloads.get(i);
			boolean hov = in(mx, my, x, by, w, 20);
			Gfx.rrect(c, x, by, w, 20, 5, hov ? Theme.OFF_BG_HOVER : 0xFF17181F);
			Gfx.textRaw(c, cut(p.getFileName().toString(), w - 120), x + 6, by + 6, 0xFFFFFFFF);
			Gfx.rrect(c, x + w - 104, by + 2, 100, 16, 5, Theme.accent());
			Gfx.text(c, "Add & load", x + w - 96, by + 6, 0xFFFFFFFF);
			hits.add(new int[] {x, by, w, 20});
			acts.add(() -> {
				try {
					Path dest = KuboBuilderMod.schematicsDir().resolve(p.getFileName());
					Files.createDirectories(KuboBuilderMod.schematicsDir());
					Files.copy(p, dest, StandardCopyOption.REPLACE_EXISTING);
					refreshFiles();
					if (b().load(dest)) {
						tab = 1;
					}
				} catch (Exception e) {
					b().say("Could not copy: " + e.getMessage());
				}
			});
			by += 22;
		}
	}

	// ------------------------------------------------------------------ input
	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		double mx = click.x(), my = click.y();
		boolean wasFocus = queryFocus;
		queryFocus = false;
		for (int i = hits.size() - 1; i >= 0; i--) {
			int[] r = hits.get(i);
			if (in(mx, my, r[0], r[1], r[2], r[3])) {
				acts.get(i).run();
				return true;
			}
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double h, double v) {
		scroll -= (float) v * 24;
		return true;
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		int k = input.key();
		if (k == 256) {
			close();
			return true;
		}
		if (tab == 3) {
			if (k == 257 || k == 335) {
				String q = URLEncoder.encode(query.text().trim(), StandardCharsets.UTF_8);
				net.minecraft.util.Util.getOperatingSystem().open(URI.create(String.format(SITES[0][1], q)));
				return true;
			}
			query.key(k);
			queryFocus = true;
		} else if (tab == 0) {
			filter.key(k);
			scroll = 0;
		} else if (tab == 1 && b() != null) {
			KuboBuilderMod m = b();
			switch (k) {
				case 263 -> m.moveRelative(0, -1, 0);
				case 262 -> m.moveRelative(0, 1, 0);
				case 265 -> m.moveRelative(1, 0, 0);
				case 264 -> m.moveRelative(-1, 0, 0);
				case 266 -> m.move(0, 1, 0);
				case 267 -> m.move(0, -1, 0);
				default -> {
				}
			}
		}
		return true;
	}

	@Override
	public boolean charTyped(CharInput input) {
		int cp = input.codepoint();
		if (cp < 32 || cp == 127) {
			return true;
		}
		if (tab == 3) {
			query.typed(cp);
		} else if (tab == 0) {
			filter.typed(cp);
			scroll = 0;
		}
		return true;
	}

	@Override
	public void close() {
		MinecraftClient.getInstance().setScreen(parent);
	}
}
