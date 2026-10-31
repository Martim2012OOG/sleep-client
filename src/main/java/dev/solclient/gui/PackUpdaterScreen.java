package dev.solclient.gui;

import dev.solclient.Gfx;
import dev.solclient.Lang;
import dev.solclient.pack.PackMigrator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

/**
 * Pick a resource pack from your resourcepacks folder and bring it up to the version of the game you are running
 * (textures that Minecraft moved or renamed are found again). "Check only" shows what would change without writing
 * anything; "Update" writes a new copy next to the original, which is never touched.
 */
public class PackUpdaterScreen extends Screen {

	private record Line(String text, int colour) {
	}

	private final Screen parent;
	private final List<Path> packs = new ArrayList<>();
	private int selected = -1;
	private float scroll, scrollTarget, maxScroll;

	private volatile boolean running;
	private volatile PackMigrator.Result done;
	private String runningName = "";
	private List<Line> report;
	private boolean showReport;

	public PackUpdaterScreen(Screen parent) {
		super(Text.literal("Update Resource Pack"));
		this.parent = parent;
		scan();
	}

	private void scan() {
		packs.clear();
		try (Stream<Path> s = Files.list(FabricLoader.getInstance().getGameDir().resolve("resourcepacks"))) {
			s.filter(PackMigrator::looksLikePack)
					.sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT))).forEach(packs::add);
		} catch (Exception ignored) {
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void close() {
		MinecraftClient.getInstance().setScreen(parent);
	}

	// ---- layout
	private int pw() {
		return Math.min(width - 16, 400);
	}

	private int px() {
		return (width - pw()) / 2;
	}

	private int py() {
		return Math.max(6, height / 2 - ph() / 2);
	}

	private int ph() {
		return Math.min(height - 12, 270);
	}

	private int listTop() {
		return py() + 52;
	}

	private int listBottom() {
		return py() + ph() - 54;
	}

	private int btnY() {
		return py() + ph() - 40;
	}

	private int[] btn(int i, int n) {
		int gap = 6, w = (pw() - 20 - gap * (n - 1)) / n;
		return new int[] {px() + 10 + i * (w + gap), btnY(), w, 26};
	}

	private static boolean in(int[] r, double x, double y) {
		return x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + r[3];
	}

	// ---- work
	private void start(boolean dryRun) {
		if (running || selected < 0 || selected >= packs.size()) {
			return;
		}
		Path p = packs.get(selected);
		runningName = p.getFileName().toString();
		running = true;
		done = null;
		Thread t = new Thread(() -> {
			PackMigrator.Result r;
			try {
				r = PackMigrator.run(p, dryRun);
			} catch (Throwable e) {
				r = new PackMigrator.Result();
				r.error = String.valueOf(e);
			}
			done = r;
		}, "kubo-pack-updater");
		t.setDaemon(true);
		t.start();
	}

	private void buildReport(PackMigrator.Result r) {
		List<Line> l = new ArrayList<>();
		if (r.error != null) {
			l.add(new Line(Lang.tr("Something went wrong: ", "Algo correu mal: ") + r.error, Theme.RED));
		} else {
			if (r.dryRun) {
				l.add(new Line(Lang.tr("Check only: nothing was changed.", "Só verificação: nada foi alterado."), Theme.DIM));
			} else if (r.output != null) {
				l.add(new Line(Lang.tr("Done! New pack: ", "Pronto! Novo pack: ") + r.output.getFileName(), Theme.GREEN));
				l.add(new Line(Lang.tr("Close and reopen the resource packs screen to see it.",
						"Fecha e volta a abrir o ecrã de resource packs para o veres."), Theme.DIM));
			}
			l.add(new Line(Lang.tr("Pack format: ", "Formato do pack: ") + (r.formatInfo.isEmpty() ? "-" : r.formatInfo),
					r.formatUpdated ? Theme.GREEN : Theme.DIM));
			l.add(new Line(Lang.tr("Files moved to their new place: ", "Ficheiros movidos para o sítio novo: ") + r.moved.size(),
					r.moved.isEmpty() ? Theme.DIM : Theme.GREEN));
			l.add(new Line(Lang.tr("Files already correct: ", "Ficheiros já corretos: ") + r.unchanged, Theme.DIM));
			if (r.jsonFixed > 0) {
				l.add(new Line(Lang.tr("Model / blockstate files fixed: ", "Ficheiros de modelo / blockstate corrigidos: ")
						+ r.jsonFixed, Theme.GREEN));
			}
			for (String n : r.notes) {
				l.add(new Line(n, Theme.accent()));
			}
			l.add(new Line(Lang.tr("Not in this version (kept as they are): ", "Não existem nesta versão (ficam como estão): ")
					+ r.unmatched.size() + ", " + Lang.tr("unsure: ", "incertos: ") + r.ambiguous.size(), Theme.DIM));
			if (r.report != null) {
				l.add(new Line(Lang.tr("Full list: ", "Lista completa: ") + "config/kubo-pack-updates/" + r.report.getFileName(),
						Theme.DIM));
			}
			l.add(new Line("", Theme.DIM));
			int cap = 150;
			for (int i = 0; i < r.moved.size() && i < cap; i++) {
				l.add(new Line(r.moved.get(i), Theme.GREEN));
			}
			if (r.moved.size() > cap) {
				l.add(new Line("... +" + (r.moved.size() - cap), Theme.DIM));
			}
			for (int i = 0; i < r.ambiguous.size() && i < 20; i++) {
				l.add(new Line("? " + r.ambiguous.get(i), 0xFFFFD866));
			}
		}
		report = l;
		showReport = true;
		scroll = 0;
		scrollTarget = 0;
	}

	// ---- drawing
	@Override
	public void render(DrawContext c, int mx, int my, float delta) {
		super.render(c, mx, my, delta);
		if (running && done != null) {
			running = false;
			buildReport(done);
		}
		scroll += (scrollTarget - scroll) * 0.35F;
		int x = px(), y = py(), w = pw(), h = ph();
		Gfx.rrect(c, x, y, w, h, 10, Theme.PANEL);
		Gfx.text(c, "Update Resource Pack", x + 14, y + 10, Theme.TEXT);
		Gfx.textClip(c, showReport ? Lang.tr("Result", "Resultado")
				: "Pick a pack. Your original is never changed: a new updated copy is made.", x + 14, y + 24, w - 28, Theme.DIM);
		if (!showReport) {
			Gfx.textClip(c, "Textures that Minecraft moved or renamed are found again.", x + 14, y + 36, w - 28, Theme.DIM);
		}
		int ly = listTop(), ly1 = listBottom();
		Gfx.rrect(c, x + 10, ly - 2, w - 20, ly1 - ly + 4, 7, Theme.FIELD);
		c.enableScissor(x + 10, ly, x + w - 10, ly1);
		if (running) {
			Gfx.textClip(c, Lang.tr("Working on ", "A trabalhar em ") + runningName + " ...", x + 20, ly + 8, w - 40, Theme.TEXT);
			maxScroll = 0;
		} else if (showReport && report != null) {
			int rh = 11;
			maxScroll = Math.max(0, report.size() * rh - (ly1 - ly) + 6);
			scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
			for (int i = 0; i < report.size(); i++) {
				int ry = ly + 4 + i * rh - Math.round(scroll);
				if (ry + rh < ly || ry > ly1) {
					continue;
				}
				Gfx.textClip(c, report.get(i).text(), x + 18, ry, w - 36, report.get(i).colour());
			}
		} else if (packs.isEmpty()) {
			Gfx.textClip(c, "No resource packs found in the resourcepacks folder.", x + 20, ly + 8, w - 40, Theme.DIM);
			maxScroll = 0;
		} else {
			int rowH = 24;
			maxScroll = Math.max(0, packs.size() * rowH - (ly1 - ly) + 4);
			scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
			for (int i = 0; i < packs.size(); i++) {
				int ry = ly + 2 + i * rowH - Math.round(scroll);
				if (ry + rowH < ly || ry > ly1) {
					continue;
				}
				boolean hov = mx >= x + 14 && mx < x + w - 14 && my >= ry && my < ry + 21 && my >= ly && my < ly1;
				boolean sel = i == selected;
				Gfx.rrect(c, x + 14, ry, w - 28, 21, 6, sel ? Theme.accent() : hov ? Theme.CARD : Theme.PANEL);
				Path p = packs.get(i);
				Gfx.textClip(c, p.getFileName().toString(), x + 22, ry + 6, w - 100, sel ? 0xFFFFFFFF : Theme.TEXT);
				String tag = Files.isDirectory(p) ? "folder" : "zip";
				Gfx.text(c, tag, x + w - 22 - Gfx.tw(tag), ry + 6, sel ? 0xFFFFFFFF : Theme.DIM);
			}
		}
		c.disableScissor();

		// buttons
		if (showReport) {
			drawBtn(c, btn(0, 2), "Back to the list", mx, my, true);
			drawBtn(c, btn(1, 2), "Done", mx, my, true);
		} else {
			boolean ok = selected >= 0 && !running;
			drawBtn(c, btn(0, 3), "Check only", mx, my, ok);
			drawBtn(c, btn(1, 3), "Update to current version", mx, my, ok);
			drawBtn(c, btn(2, 3), "Back", mx, my, true);
		}
	}

	private void drawBtn(DrawContext c, int[] r, String label, int mx, int my, boolean enabled) {
		Gfx.button(c, r[0], r[1], r[2], r[3], enabled && in(r, mx, my), false);
		Gfx.textFit(c, label, r[0] + r[2] / 2, r[2] - 8, r[1] + (r[3] - 9) / 2, enabled ? Theme.TEXT : Theme.DIM);
	}

	// ---- input
	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		double mx = click.x(), my = click.y();
		if (running) {
			return true;
		}
		if (showReport) {
			if (in(btn(0, 2), mx, my)) {
				showReport = false;
				scroll = 0;
				scrollTarget = 0;
				scan();
				selected = -1;
			} else if (in(btn(1, 2), mx, my)) {
				close();
			}
			return true;
		}
		if (in(btn(0, 3), mx, my)) {
			start(true);
			return true;
		}
		if (in(btn(1, 3), mx, my)) {
			start(false);
			return true;
		}
		if (in(btn(2, 3), mx, my)) {
			close();
			return true;
		}
		int x = px(), w = pw(), ly = listTop(), ly1 = listBottom();
		for (int i = 0; i < packs.size(); i++) {
			int ry = ly + 2 + i * 24 - Math.round(scroll);
			if (mx >= x + 14 && mx < x + w - 14 && my >= ry && my < ry + 21 && my >= ly && my < ly1) {
				selected = i;
				return true;
			}
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
		scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget - (float) vAmount * 30F));
		return true;
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (input.key() == 256) {
			close();
			return true;
		}
		return false;
	}
}
