package dev.sleepclient.gui;

import dev.sleepclient.Gfx;
import dev.sleepclient.fightcam.Capture;
import dev.sleepclient.mods.FightCamMod;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

/**
 * The Film Studio: one screen with big clickable buttons to start / stop a recording, make the fight film, and a list of every
 * video you made (recordings, clips, films) with Watch (opens it in your video player), Folder and Delete.
 */
public final class FilmStudioScreen extends Screen {

	private final MinecraftClient mc = MinecraftClient.getInstance();
	private final File focus;
	private final List<File> files = new ArrayList<>();
	private record Btn(int x, int y, int w, int h, Runnable run) {
	}

	private final List<Btn> buttons = new ArrayList<>();
	private int scroll;
	private File confirmDelete;
	private long confirmAt;

	/** @param focus a video to highlight (the film that was just made), or null */
	public FilmStudioScreen(File focus) {
		super(Text.literal("Film Studio"));
		this.focus = focus;
	}

	@Override
	protected void init() {
		reload();
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	private void reload() {
		files.clear();
		File root = Capture.outputDir();
		scan(root);
		scan(new File(root, "clips"));
		files.sort(Comparator.comparingLong(File::lastModified).reversed());
	}

	private void scan(File d) {
		File[] fs = d.listFiles((f, n) -> n.toLowerCase(java.util.Locale.ROOT).endsWith(".mp4") && !n.contains(".look."));
		if (fs != null) {
			for (File f : fs) {
				files.add(f);
			}
		}
	}

	private static String size(long b) {
		return b >= 1_000_000_000L ? String.format("%.1f GB", b / 1e9) : String.format("%.0f MB", Math.max(1, b / 1e6));
	}

	private void button(DrawContext c, int mx, int my, int x, int y, int w, int h, String label, int color, Runnable run) {
		boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
		int bg = hov ? Gfx.blend(color, 0xFFFFFFFF, 0.18F) : color;
		Gfx.rrect(c, x, y, w, h, 6, bg);
		Gfx.textFit(c, label, x + w / 2, w - 8, y + (h - 8) / 2, 0xFFFFFFFF);
		buttons.add(new Btn(x, y, w, h, run));
	}

	@Override
	public void render(DrawContext c, int mx, int my, float delta) {
		c.fill(0, 0, width, height, 0xB0070910);
		buttons.clear();
		int pw = Math.min(600, width - 24), ph = Math.min(height - 24, 380);
		int px = (width - pw) / 2, py = (height - ph) / 2;
		Gfx.rrect(c, px, py, pw, ph, 10, 0xF0121520);
		Gfx.text(c, "Film Studio", px + 16, py + 14, 0xFFFFFFFF);
		Gfx.text(c, "Esc to close", px + pw - 16 - Gfx.tw("Esc to close"), py + 14, 0x80FFFFFF);

		FightCamMod fc = FightCamMod.instance;
		boolean live = fc != null && fc.isRecording();
		boolean session = fc != null && fc.sessionActive();
		int by = py + 36, bh = 30, gap = 8, bw = (pw - 32 - gap * 3) / 4;
		String sessionLabel = session ? "Stop + render " + (int) fc.sessionSeconds() + "s" : "Start (no lag)";
		button(c, mx, my, px + 16, by, bw, bh, sessionLabel, session ? 0xFFC0392B : 0xFF2E8B57, () -> {
			if (fc == null) {
				return;
			}
			if (session) {
				mc.setScreen(null);
				fc.stopSession();
			} else {
				fc.startSession();
			}
		});
		button(c, mx, my, px + 16 + bw + gap, by, bw, bh, "Make fight film", 0xFF3B6FD4, () -> {
			mc.setScreen(null);
			if (fc != null) {
				fc.openFilm();
			}
		});
		button(c, mx, my, px + 16 + (bw + gap) * 2, by, bw, bh, live ? "Stop live record" : "Live record", live ? 0xFFC0392B : 0xFF3A3F52, () -> {
			if (fc != null) {
				fc.toggleRecording();
			}
		});
		button(c, mx, my, px + 16 + (bw + gap) * 3, by, bw, bh, "Open folder", 0xFF3A3F52, () -> net.minecraft.util.Util.getOperatingSystem().open(Capture.outputDir()));
		Gfx.text(c, session ? "Recording with no lag: just play. Stop + render makes the .mp4 frame by frame."
				: live ? "Live recording..." : "Start (no lag): nothing heavy runs while you play, the video is rendered after. Live record: classic recorder.",
				px + 16, by + bh + 8, session || live ? 0xFFFF8A80 : 0x90FFFFFF);

		int ly = by + bh + 26, rowH = 30, listH = py + ph - 14 - ly;
		Gfx.text(c, files.isEmpty() ? "No videos yet. Make a recording or a fight film." : "Your videos (" + files.size() + ")", px + 16, ly - 12, 0xB0FFFFFF);
		int rows = Math.max(1, listH / rowH);
		scroll = Math.max(0, Math.min(Math.max(0, files.size() - rows), scroll));
		SimpleDateFormat df = new SimpleDateFormat("dd MMM HH:mm");
		for (int i = 0; i < rows && scroll + i < files.size(); i++) {
			File f = files.get(scroll + i);
			int y = ly + i * rowH;
			boolean isFocus = focus != null && f.getAbsolutePath().equals(focus.getAbsolutePath());
			Gfx.rrect(c, px + 12, y, pw - 24, rowH - 4, 6, isFocus ? 0xFF1F3B5E : 0xFF1A1D2A);
			Gfx.textClip(c, f.getName().replace(".mp4", ""), px + 22, y + 5, pw - 300, 0xFFFFFFFF);
			Gfx.text(c, df.format(new Date(f.lastModified())) + "  -  " + size(f.length()), px + 22, y + 15, 0x90FFFFFF);
			boolean sure = f.equals(confirmDelete) && System.currentTimeMillis() - confirmAt < 4000;
			button(c, mx, my, px + pw - 22 - 78, y + 3, 78, rowH - 10, sure ? "Sure?" : "Delete", sure ? 0xFFC0392B : 0xFF3A3F52, () -> {
				if (sure) {
					f.delete();
					confirmDelete = null;
					reload();
				} else {
					confirmDelete = f;
					confirmAt = System.currentTimeMillis();
				}
			});
			button(c, mx, my, px + pw - 22 - 78 - 84, y + 3, 78, rowH - 10, "Watch", 0xFF2E8B57, () -> net.minecraft.util.Util.getOperatingSystem().open(f));
		}
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		if (click.button() == 0) {
			double mx = click.x(), my = click.y();
			for (int i = buttons.size() - 1; i >= 0; i--) {
				Btn b = buttons.get(i);
				if (mx >= b.x() && mx < b.x() + b.w() && my >= b.y() && my < b.y() + b.h()) {
					b.run().run();
					return true;
				}
			}
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double h, double v) {
		scroll -= (int) Math.signum(v);
		return true;
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (input.key() == 256) {
			mc.setScreen(null);
			return true;
		}
		return super.keyPressed(input);
	}
}
