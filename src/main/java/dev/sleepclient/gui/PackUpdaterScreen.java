package dev.sleepclient.gui;

import dev.sleepclient.Gfx;
import dev.sleepclient.Lang;
import dev.sleepclient.pack.PackMigrator;
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
 * Pick resource packs from your resourcepacks folder (one, several or all) and bring them up to the version of the game
 * you are running (textures that Minecraft moved or renamed are found again). "Check only" shows what would change without
 * writing anything; "Update" changes the pack itself (no copy next to it); its original is kept in config/kubo-pack-backups.
 */
public class PackUpdaterScreen extends Screen {

	private record Line(String text, int colour) {
	}

	private final Screen parent;
	private final List<Path> packs = new ArrayList<>();
	private final java.util.Set<Integer> sel = new java.util.LinkedHashSet<>();
	private float scroll, scrollTarget, maxScroll;

	private volatile boolean running;
	private volatile List<PackMigrator.Result> done;
	private volatile List<String> doneNames;
	private volatile int progress, total;
	private volatile String runningName = "";
	private volatile List<String> revertDone;
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
		return Math.min(width - 16, 300);
	}

	private int px() {
		return (width - pw()) / 2;
	}

	private int py() {
		return Math.max(6, height / 2 - ph() / 2);
	}

	private int ph() {
		return Math.min(height - 12, 215);
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
	private void start(boolean dryRun, boolean all) {
		if (running) {
			return;
		}
		List<Path> targets = new ArrayList<>();
		if (all) {
			targets.addAll(packs);
		} else {
			for (int i : sel) {
				if (i >= 0 && i < packs.size()) {
					targets.add(packs.get(i));
				}
			}
		}
		if (targets.isEmpty()) {
			return;
		}
		total = targets.size();
		progress = 0;
		runningName = targets.get(0).getFileName().toString();
		running = true;
		done = null;
		Thread t = new Thread(() -> {
			List<PackMigrator.Result> out = new ArrayList<>();
			List<String> names = new ArrayList<>();
			for (Path p : targets) {
				runningName = p.getFileName().toString();
				PackMigrator.Result r;
				try {
					r = PackMigrator.run(p, dryRun);
				} catch (Throwable e) {
					r = new PackMigrator.Result();
					r.error = String.valueOf(e);
				}
				out.add(r);
				names.add(p.getFileName().toString());
				progress++;
			}
			if (!dryRun) {
				PackMigrator.writeJournal(out);
			}
			doneNames = names;
			done = out;
		}, "kubo-pack-updater");
		t.setDaemon(true);
		t.start();
	}

	/** Undoes the very last update (one pack, or all the packs of the last Update ALL). */
	private void revert() {
		if (running || !PackMigrator.hasJournal()) {
			return;
		}
		runningName = Lang.tr("restoring the originals", "a repor os originais");
		total = 1;
		progress = 0;
		running = true;
		done = null;
		Thread t = new Thread(() -> revertDone = PackMigrator.revertLast(), "kubo-pack-revert");
		t.setDaemon(true);
		t.start();
	}

	private void buildRevertReport(List<String> lines) {
		List<Line> l = new ArrayList<>();
		int bad = 0;
		for (String s : lines) {
			if (s.startsWith("ERR")) {
				bad++;
			}
		}
		l.add(new Line(bad == 0 ? Lang.tr("Reverted: the packs are back as they were before the last update.", "Revertido: os packs voltaram a ficar como estavam antes da última atualização.")
				: Lang.tr("Some packs could not be restored (close the game's resource pack or restart and try again).", "Alguns packs não puderam ser repostos (fecha o resource pack no jogo ou reinicia e tenta outra vez)."),
				bad == 0 ? Theme.GREEN : Theme.RED));
		l.add(new Line(Lang.tr("Press F3 + T in the game to reload the textures.", "Carrega F3 + T no jogo para recarregar as texturas."), Theme.DIM));
		l.add(new Line("", Theme.DIM));
		for (String s : lines) {
			l.add(new Line(s, s.startsWith("ERR") ? Theme.RED : Theme.GREEN));
		}
		report = l;
		showReport = true;
		scroll = 0;
		scrollTarget = 0;
	}

	private void buildReport(List<PackMigrator.Result> results, List<String> names) {
		List<Line> l = new ArrayList<>();
		boolean many = results.size() > 1;
		int ok = 0, changed = 0, failed = 0;
		for (PackMigrator.Result r : results) {
			if (r.error != null) {
				failed++;
			} else {
				ok++;
				if (!r.moved.isEmpty() || r.formatUpdated || r.jsonFixed > 0) {
					changed++;
				}
			}
		}
		if (many) {
			l.add(new Line(Lang.tr("Packs checked: ", "Packs verificados: ") + results.size() + "   "
					+ Lang.tr("with changes: ", "com alterações: ") + changed + "   "
					+ Lang.tr("failed: ", "falharam: ") + failed, failed > 0 ? Theme.RED : Theme.GREEN));
			if (!results.get(0).dryRun) {
				l.add(new Line(Lang.tr("The packs were updated in place. Originals are in config/kubo-pack-backups.",
						"Os packs foram atualizados no próprio sítio. Os originais estão em config/kubo-pack-backups."), Theme.DIM));
				l.add(new Line(Lang.tr("Press F3 + T in the game to reload the textures.", "Carrega F3 + T no jogo para recarregar as texturas."), Theme.DIM));
			}
			l.add(new Line("", Theme.DIM));
		}
		for (int n = 0; n < results.size(); n++) {
			PackMigrator.Result r = results.get(n);
			if (many) {
				l.add(new Line("== " + names.get(n) + " ==", Theme.TEXT));
			}
			if (r.error != null) {
				l.add(new Line(Lang.tr("Something went wrong: ", "Algo correu mal: ") + r.error, Theme.RED));
				continue;
			}
			if (r.dryRun) {
				if (!many) {
					l.add(new Line(Lang.tr("Check only: nothing was changed.", "Só verificação: nada foi alterado."), Theme.DIM));
				}
			} else if (r.output != null && !many) {
				l.add(new Line(Lang.tr("Done! Pack updated: ", "Pronto! Pack atualizado: ") + r.output.getFileName(), Theme.GREEN));
				l.add(new Line(Lang.tr("Your original is saved in config/kubo-pack-backups. Press F3 + T in the game to reload.",
						"O teu original está guardado em config/kubo-pack-backups. Carrega F3 + T no jogo para recarregar."), Theme.DIM));
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
			for (String note : r.notes) {
				l.add(new Line(note, Theme.accent()));
			}
			l.add(new Line(Lang.tr("Not in this version (kept as they are): ", "Não existem nesta versão (ficam como estão): ")
					+ r.unmatched.size() + ", " + Lang.tr("unsure: ", "incertos: ") + r.ambiguous.size(), Theme.DIM));
			if (r.report != null && !many) {
				l.add(new Line(Lang.tr("Full list: ", "Lista completa: ") + "config/kubo-pack-updates/" + r.report.getFileName(),
						Theme.DIM));
			}
			int cap = many ? 12 : 150;
			if (!many) {
				l.add(new Line("", Theme.DIM));
			}
			for (int i = 0; i < r.moved.size() && i < cap; i++) {
				l.add(new Line(r.moved.get(i), Theme.GREEN));
			}
			if (r.moved.size() > cap) {
				l.add(new Line("... +" + (r.moved.size() - cap), Theme.DIM));
			}
			for (int i = 0; i < r.ambiguous.size() && i < (many ? 5 : 20); i++) {
				l.add(new Line("? " + r.ambiguous.get(i), 0xFFFFD866));
			}
			if (many) {
				l.add(new Line("", Theme.DIM));
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
			buildReport(done, doneNames);
		}
		if (running && revertDone != null) {
			running = false;
			List<String> rd = revertDone;
			revertDone = null;
			buildRevertReport(rd);
		}
		scroll += (scrollTarget - scroll) * 0.35F;
		int x = px(), y = py(), w = pw(), h = ph();
		Gfx.rrect(c, x, y, w, h, 10, Theme.PANEL);
		Gfx.text(c, "Update Resource Pack", x + 14, y + 10, Theme.TEXT);
		Gfx.textClip(c, showReport ? Lang.tr("Result", "Resultado")
				: "Tick the packs to update (or use Update ALL). They are changed in place; your original is backed up.", x + 14, y + 24, w - 28, Theme.DIM);
		if (!showReport) {
			Gfx.textClip(c, "Textures that Minecraft moved or renamed are found again.", x + 14, y + 36, w - 28, Theme.DIM);
		}
		int ly = listTop(), ly1 = listBottom();
		Gfx.rrect(c, x + 10, ly - 2, w - 20, ly1 - ly + 4, 7, Theme.FIELD);
		c.enableScissor(x + 10, ly, x + w - 10, ly1);
		if (running) {
			Gfx.textClip(c, Lang.tr("Working on ", "A trabalhar em ") + runningName + " ... (" + Math.min(total, progress + 1) + "/" + total + ")", x + 20, ly + 8, w - 40, Theme.TEXT);
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
				boolean on = sel.contains(i);
				Gfx.rrect(c, x + 14, ry, w - 28, 21, 6, on ? Gfx.blend(Theme.PANEL, Theme.accent() | 0xFF000000, 0.22F) : hov ? Theme.CARD : Theme.PANEL);
				CheckBox.draw(c, "pu" + i, x + 20, ry + 5.5F, 10, on, hov);
				Path p = packs.get(i);
				Gfx.textClip(c, p.getFileName().toString(), x + 38, ry + 6, w - 116, Theme.TEXT);
				String tag = Files.isDirectory(p) ? "folder" : "zip";
				Gfx.text(c, tag, x + w - 22 - Gfx.tw(tag), ry + 6, Theme.DIM);
			}
		}
		c.disableScissor();

		// buttons
		if (showReport) {
			drawBtn(c, btn(0, 2), "Back to the list", mx, my, true);
			drawBtn(c, btn(1, 2), "Done", mx, my, true);
		} else {
			boolean ok = !sel.isEmpty() && !running, any = !packs.isEmpty() && !running;
			drawBtn(c, btn(0, 5), "Check only", mx, my, ok);
			drawBtn(c, btn(1, 5), "Update selected", mx, my, ok);
			drawBtn(c, btn(2, 5), "Update ALL", mx, my, any);
			drawBtn(c, btn(3, 5), "Revert last", mx, my, !running && PackMigrator.hasJournal());
			drawBtn(c, btn(4, 5), "Back", mx, my, true);
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
				sel.clear();
			} else if (in(btn(1, 2), mx, my)) {
				close();
			}
			return true;
		}
		if (in(btn(0, 5), mx, my)) {
			start(true, false);
			return true;
		}
		if (in(btn(1, 5), mx, my)) {
			start(false, false);
			return true;
		}
		if (in(btn(2, 5), mx, my)) {
			start(false, true);
			return true;
		}
		if (in(btn(3, 5), mx, my)) {
			revert();
			return true;
		}
		if (in(btn(4, 5), mx, my)) {
			close();
			return true;
		}
		int x = px(), w = pw(), ly = listTop(), ly1 = listBottom();
		for (int i = 0; i < packs.size(); i++) {
			int ry = ly + 2 + i * 24 - Math.round(scroll);
			if (mx >= x + 14 && mx < x + w - 14 && my >= ry && my < ry + 21 && my >= ly && my < ly1) {
				if (!sel.remove(i)) {
					sel.add(i);
				}
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
