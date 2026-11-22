package dev.sleepclient.gui;

import dev.sleepclient.Gfx;
import dev.sleepclient.Keys;
import dev.sleepclient.fightcam.Capture;
import dev.sleepclient.fightcam.FilmRender;
import dev.sleepclient.fightcam.ClipToast;
import dev.sleepclient.fightcam.Replay;
import dev.sleepclient.mods.FightCamMod;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * The replay viewer: a timeline at the bottom and a free spectator camera.
 * <ul>
 * <li>WASD fly, Space / Shift up / down, Ctrl = faster, drag the mouse to look, scroll = flying speed (or zoom when following).</li>
 * <li>P or the play button: play / pause. Left / Right: 5 s back / forward. , and . : one tick. 1-5: speed.</li>
 * <li>Camera: Free, Follow (orbits around a player) or POV (through their eyes). Tab picks the player.</li>
 * <li>Record: records the replay from the camera to an .mp4 (the timeline never shows up in the video). H hides the bar.</li>
 * </ul>
 */
public class ReplayScreen extends Screen {

	private static final float[] SPEEDS = {0.1F, 0.25F, 0.5F, 1F, 2F};
	private static final String[] MODES = {"Free", "Follow", "POV", "Path"};

	private final Minecraft mc = Minecraft.getInstance();
	private final boolean oldHud;
	private final CameraType oldPerspective;
	private boolean hideUi;
	private float flySpeed = 6F;
	private long lastNs;
	private boolean draggingTimeline, looking;
	private double lastMx, lastMy;
	private boolean recordingHere, startedCapture;
	private final boolean autoFilm;
	private boolean filmStarted, filmFinishing;
	private int autoFrames;
	private double filmStartT;
	private File filmFile;

	private record Btn(int x, int y, int w, int h, Runnable run) {
	}

	private final List<Btn> buttons = new ArrayList<>();
	private int barX, barY, barW;

	public ReplayScreen() {
		this(false);
	}

	/** autoFilm: the Fight Film plays the prepared camera and records it by itself, then closes. */
	public ReplayScreen(boolean autoFilm) {
		super(Component.literal("Replay"));
		this.autoFilm = autoFilm;
		oldHud = dev.sleepclient.compat.Mc.hideGui(mc);
		oldPerspective = mc.options.getCameraType();
	}

	@Override
	protected void init() {
		dev.sleepclient.compat.Mc.setHideGui(mc, true);
		mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); // no hand in front of the camera
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void removed() {
		FilmRender.abort(); // closing the screen cancels an unfinished film
		if (recordingHere) {
			stopRecording();
		}
		Replay.stop();
		dev.sleepclient.compat.Mc.setHideGui(mc, oldHud);
		mc.options.setCameraType(oldPerspective);
		Capture.ReplayForceWorldOnly.on = false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor c, int mouseX, int mouseY, float delta) {
		// no blur / darkening: you are watching the world
	}

	// ------------------------------------------------------------------ per frame
	@Override
	public void extractRenderState(GuiGraphicsExtractor c, int mouseX, int mouseY, float delta) {
		long now = System.nanoTime();
		float dt = lastNs == 0 ? 0 : Math.min(0.1F, (now - lastNs) / 1e9F);
		lastNs = now;
		if (!Replay.active()) {
			dev.sleepclient.compat.Mc.setScreen(mc, null);
			return;
		}
		fly(dt);
		Replay.advance();
		if (autoFilm) {
			hideUi = true;
			if (!filmStarted && ++autoFrames >= 4 && !beginFilm()) {
				return;
			}
		}
		if (filmStarted && !filmFinishing) {
			if (!FilmRender.alive() || FilmRender.error() != null || FilmRender.stalledMs() > 10_000) {
				String why = FilmRender.error() != null ? FilmRender.error() : FilmRender.stalledMs() > 10_000 ? "no picture arrived (" + FilmRender.debug() + ")" : FilmRender.log();
				FilmRender.abort();
				ClipToast.show("Film failed", why == null || why.isEmpty() ? "the encoder stopped" : why, null, true);
				dev.sleepclient.compat.Mc.setScreen(mc, null);
				return;
			}
			if (!Replay.playing && FilmRender.frameDone()) {
				filmFinishing = true;
				final File saved = filmFile;
				final long madeFrames = FilmRender.frames();
				final int madeFps = FilmRender.fps();
				FilmRender.finish(err -> {
					if (err != null) {
						ClipToast.show("Film failed", err, null, true);
						dev.sleepclient.compat.Mc.setScreen(mc, null);
					} else {
						dev.sleepclient.fightcam.FilmEdit.Info made = dev.sleepclient.fightcam.FilmEdit.last;
						if (made != null) {
							made.file = saved;
							made.fps = madeFps;
							made.seconds = madeFrames / (double) madeFps;
						}
						dev.sleepclient.compat.Mc.setScreen(mc, new FilmEditorScreen()); // sound effects + AI voice, then Save
					}
				});
			}
		}
		if (recordingHere && !Replay.playing) {
			stopRecording(); // reached the end
		}
		buttons.clear();
		if (!hideUi) {
			drawUi(c, mouseX, mouseY);
		} else {
			Gfx.text(c, "H: show controls", 6, 6, 0x80FFFFFF);
		}
		if (filmStarted) {
			boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
			Gfx.circle(c, width - 14, 12, 4, blink ? 0xFFE5484D : 0x60E5484D);
			Gfx.text(c, "REC", width - 40, 8, 0xFFFFFFFF);
		}
		if (filmStarted) {
			drawFilmProgress(c);
		}
		ClipToast.draw(c);
	}

	private void fly(float dt) {
		if (Replay.camMode != Replay.FREE || dev.sleepclient.compat.Mc.screen(mc) != this) {
			return;
		}
		double sp = flySpeed * dt * (Keys.down(GLFW.GLFW_KEY_LEFT_CONTROL) ? 3 : 1);
		double f = 0, s = 0, u = 0;
		if (Keys.down(GLFW.GLFW_KEY_W)) {
			f += sp;
		}
		if (Keys.down(GLFW.GLFW_KEY_S)) {
			f -= sp;
		}
		if (Keys.down(GLFW.GLFW_KEY_A)) {
			s -= sp;
		}
		if (Keys.down(GLFW.GLFW_KEY_D)) {
			s += sp;
		}
		if (Keys.down(GLFW.GLFW_KEY_SPACE)) {
			u += sp;
		}
		if (Keys.down(GLFW.GLFW_KEY_LEFT_SHIFT)) {
			u -= sp;
		}
		if (f != 0 || s != 0 || u != 0) {
			Replay.fly(f, s, u);
		}
	}

	private static String time(double frames) {
		int s = (int) Math.floor(frames / 20.0);
		return s / 60 + ":" + String.format("%02d", s % 60);
	}

	private void button(GuiGraphicsExtractor c, int mx, int my, int x, int y, int w, int h, String label, boolean on, Runnable run) {
		boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
		int bg = on ? Gfx.blend(Theme.accent(), 0xFF000000, 0.35F) : hov ? 0xE02A2E3A : 0xD01B1E27;
		Gfx.rrect(c, x, y, w, h, 5, bg);
		if (on) {
			Gfx.rrectBorder(c, x, y, w, h, 5, 0x00000000, Theme.accent());
		}
		Gfx.textFit(c, label, x + w / 2, w - 6, y + (h - 8) / 2, 0xFFFFFFFF);
		buttons.add(new Btn(x, y, w, h, run));
	}

	private void drawUi(GuiGraphicsExtractor c, int mx, int my) {
		int len = Replay.length();
		double t = Replay.time();
		// top: title + help
		Gfx.rrect(c, 6, 6, 150, 20, 6, 0xC0101218);
		Gfx.circle(c, 16, 16, 3.5F, Theme.accent());
		Gfx.text(c, "REPLAY  " + time(t) + " / " + time(len - 1), 24, 12, 0xFFFFFFFF);
		String help = Replay.camMode == Replay.PATH ? "Path: the camera flies through your points (K adds one where the camera is)" :
				Replay.camMode == Replay.FREE ? "WASD fly · Space/Shift up/down · drag to look · scroll speed · K = camera point" :
				Replay.camMode == Replay.FOLLOW ? "drag to orbit · scroll zoom · Tab player" : "Tab: next player";
		Gfx.text(c, help, 8, 32, 0xA0FFFFFF);
		if (!Replay.renderError().isEmpty()) {
			Gfx.text(c, "Player drawing error: " + Replay.renderError(), 8, 44, 0xFFFF7070);
		}

		// bottom panel
		int ph = 54;
		int py = height - ph - 6;
		Gfx.rrect(c, 6, py, width - 12, ph, 8, 0xD0101218);
		// timeline
		barX = 16;
		barW = width - 32;
		barY = py + 10;
		Gfx.rrect(c, barX, barY, barW, 4, 2, 0xFF2A2E3A);
		float k = len <= 1 ? 0 : (float) (t / (len - 1));
		Gfx.rrect(c, barX, barY, Math.max(4, Math.round(barW * k)), 4, 2, Theme.accent());
		Gfx.circle(c, barX + barW * k, barY + 2, 5, 0xFFFFFFFF);
		// camera points of the path
		for (double[] key : Replay.KEYS) {
			float kx = barX + (float) (barW * (key[0] / Math.max(1, len - 1)));
			Gfx.rrectF(c, kx - 2, barY - 4, 4, 12, 1.5F, 0xFFFFD84A);
		}
		// second ticks
		for (int s = 0; s * 20 < len; s += 5) {
			int x = barX + Math.round(barW * (s * 20F / Math.max(1, len - 1)));
			c.fill(x, barY + 7, x + 1, barY + (s % 15 == 0 ? 12 : 10), 0x50FFFFFF);
		}
		// buttons row
		int by = py + 28, bh = 18;
		int x = 14;
		button(c, mx, my, x, by, 26, bh, Replay.playing ? "II" : ">", false, () -> Replay.playing = !Replay.playing);
		x += 30;
		button(c, mx, my, x, by, 22, bh, "<<", false, () -> Replay.seek(Replay.time() - 100));
		x += 24;
		button(c, mx, my, x, by, 22, bh, ">>", false, () -> Replay.seek(Replay.time() + 100));
		x += 30;
		for (float s : SPEEDS) {
			String l = s < 1 ? (s == 0.25F ? ".25x" : s + "x").replace("0.", ".") : (int) s + "x";
			final float sp = s;
			button(c, mx, my, x, by, 30, bh, l, Replay.speed == s, () -> Replay.speed = sp);
			x += 32;
		}
		x += 8;
		for (int i = 0; i < MODES.length; i++) {
			final int m = i;
			button(c, mx, my, x, by, 44, bh, MODES[i], Replay.camMode == i, () -> setMode(m));
			x += 46;
		}
		button(c, mx, my, x, by, 34, bh, "K +", false, () -> {
			Replay.addKey();
			ClipToast.show("Camera point", Replay.KEYS.size() + " points - use Path to fly through them", null, false);
		});
		x += 36;
		if (!Replay.KEYS.isEmpty()) {
			button(c, mx, my, x, by, 40, bh, "Clear", false, Replay::clearKeys);
			x += 44;
		}
		if (Replay.camMode == Replay.FOLLOW || Replay.camMode == Replay.POV) {
			String name = Replay.nameOf(Replay.target);
			int w = Math.max(60, Gfx.tw(name) + 20);
			button(c, mx, my, x, by, w, bh, name, false, this::nextTarget);
			x += w + 4;
		}
		// right side: record / hide / exit
		int rx = width - 14;
		rx -= 40;
		button(c, mx, my, rx, by, 40, bh, "Exit", false, () -> dev.sleepclient.compat.Mc.setScreen(mc, null));
		rx -= 24;
		button(c, mx, my, rx, by, 22, bh, "H", hideUi, () -> hideUi = !hideUi);
		rx -= 88;
		button(c, mx, my, rx, by, 86, bh, filmStarted ? "Stop + save" : "● Render film", filmStarted, this::toggleRecording);
		rx -= 74;
		button(c, mx, my, rx, by, 72, bh, "Auto camera", Replay.camMode == Replay.PATH && !Replay.SLOWS.isEmpty(), this::autoCamera);
	}

	// ------------------------------------------------------------------ actions
	private void setMode(int m) {
		if (m == Replay.PATH && Replay.KEYS.size() < 2) {
			ClipToast.show("Path needs 2+ points", "Move the camera and press K at different moments", null, true);
			return;
		}
		Replay.camMode = m;
		if ((m == Replay.FOLLOW || m == Replay.POV) && (Replay.target == null || Replay.actors().stream().noneMatch(a -> a.id.equals(Replay.target)))) {
			nextTarget();
		}
	}

	private void nextTarget() {
		List<Replay.Actor> list = Replay.actors();
		if (list.isEmpty()) {
			return;
		}
		int i = 0;
		for (int k = 0; k < list.size(); k++) {
			if (list.get(k).id.equals(Replay.target)) {
				i = k + 1;
				break;
			}
		}
		UUID next = list.get(i % list.size()).id;
		Replay.target = next;
	}

	/** One click: a cinematic camera for the fight in this replay (you can still add your own points afterwards). */
	private void drawFilmProgress(GuiGraphicsExtractor c) {
		double span = Math.max(1, Replay.stopAt - filmStartT);
		double pct = filmFinishing ? 1 : Math.max(0, Math.min(1, (Replay.time() - filmStartT) / span));
		int w = Math.min(320, width - 40), x = (width - w) / 2, y = height - 74;
		Gfx.rrect(c, x, y, w, 52, 8, 0xE0101218);
		Gfx.text(c, filmFinishing ? "Saving the film..." : "Rendering the film  " + Math.round(pct * 100) + "%", x + 12, y + 9, 0xFFFFFFFF);
		Gfx.text(c, FilmRender.frames() < 3 ? FilmRender.debug() : "frame " + FilmRender.frames() + "  -  every frame is rendered, so it is smooth even if this looks slow", x + 12, y + 22, 0x90FFFFFF);
		Gfx.rrect(c, x + 12, y + 36, w - 24, 6, 3, 0xFF2A2E3A);
		Gfx.rrect(c, x + 12, y + 36, Math.max(6, (int) ((w - 24) * pct)), 6, 3, Theme.accent());
	}

	private void autoCamera() {
		dev.sleepclient.fightcam.FightFilm.Fight f = dev.sleepclient.fightcam.FightFilm.analyze(Replay.playFrames(), 0, false);
		if (f == null) {
			ClipToast.show("No fight found", "Nobody was hit in this replay", null, true);
			return;
		}
		dev.sleepclient.fightcam.FightFilm.build(f, Replay.playFrames(), true);
		dev.sleepclient.fightcam.FilmEdit.last = dev.sleepclient.fightcam.FightFilm.makeInfo(f);
		Replay.camMode = Replay.PATH;
		Replay.seek(f.start);
		Replay.stopAt = f.end;
		Replay.playing = true;
		ClipToast.show("Auto camera", "Playing the fight with a cinematic camera - press Record to film it", null, false);
	}

	/** Starts the frame-exact film render from the current moment (true when it started; false = it could not and the screen closes). */
	private boolean beginFilm() {
		FightCamMod fc = FightCamMod.instance;
		filmFile = new File(Capture.outputDir(), "Film_" + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date()) + ".mp4");
		String err = fc == null ? "FightCam is off" : fc.startFilm(filmFile);
		if (err != null) {
			ClipToast.show("Can't render the film", err + " - set up the recorder in FightCam > Recording", null, true);
			if (autoFilm) {
				dev.sleepclient.compat.Mc.setScreen(mc, null);
			}
			return false;
		}
		filmStarted = true;
		filmStartT = Replay.time();
		dev.sleepclient.fightcam.FilmEdit.Info known = dev.sleepclient.fightcam.FilmEdit.last;
		if (known == null || known.file != null) { // a render you started yourself: no fight events, but it can still get sound effects
			known = new dev.sleepclient.fightcam.FilmEdit.Info();
			known.startFrame = (int) filmStartT;
			dev.sleepclient.fightcam.FilmEdit.last = known;
		}
		if (Replay.stopAt < 0) {
			Replay.stopAt = Replay.length() - 1;
		}
		Replay.playing = true;
		return true;
	}

	/** The Record button: renders the replay from here to the end (or to the fight's end) frame by frame; press again to stop and save. */
	private void toggleRecording() {
		if (filmFinishing) {
			return;
		}
		if (filmStarted) {
			Replay.playing = false; // the picture of this moment is written, then the film is saved
			return;
		}
		beginFilm();
	}

	private void stopRecording() {
		recordingHere = false;
		File out = new File(Capture.outputDir(), "Replay_" + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date()) + ".mp4");
		boolean stopAfter = startedCapture;
		startedCapture = false;
		Capture.endRecording(out, err -> {
			Capture.ReplayForceWorldOnly.on = false;
			if (err == null) {
				ClipToast.show("Replay saved", out.getName(), Capture.thumbnail(ClipToast.thumbW(), ClipToast.thumbH()), false);
			} else {
				ClipToast.show("Replay not saved", err, null, true);
			}
			if (stopAfter && FightCamMod.instance != null) {
				FightCamMod.instance.captureMaybeStop();
			}
		});
	}

	// ------------------------------------------------------------------ input
	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		double mx = click.x(), my = click.y();
		if (!hideUi) {
			for (int i = buttons.size() - 1; i >= 0; i--) {
				Btn b = buttons.get(i);
				if (mx >= b.x() && mx < b.x() + b.w() && my >= b.y() && my < b.y() + b.h()) {
					b.run().run();
					return true;
				}
			}
			if (my >= barY - 6 && my <= barY + 12 && mx >= barX - 4 && mx <= barX + barW + 4) {
				draggingTimeline = true;
				scrub(mx);
				return true;
			}
		}
		looking = true;
		lastMx = mx;
		lastMy = my;
		return true;
	}

	private void scrub(double mx) {
		double k = Math.max(0, Math.min(1, (mx - barX) / Math.max(1, barW)));
		Replay.seek(k * (Replay.length() - 1));
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
		if (draggingTimeline) {
			scrub(click.x());
			return true;
		}
		if (looking) {
			double mx = click.x(), my = click.y();
			float ddx = (float) (mx - lastMx), ddy = (float) (my - lastMy);
			lastMx = mx;
			lastMy = my;
			if (Replay.camMode == Replay.FOLLOW) {
				Replay.orbitYaw += ddx * 0.6F;
				Replay.orbitPitch = Math.max(-80F, Math.min(85F, Replay.orbitPitch + ddy * 0.6F));
			} else if (Replay.camMode == Replay.FREE) {
				Replay.camYaw += ddx * 0.45F;
				Replay.camPitch = Math.max(-90F, Math.min(90F, Replay.camPitch + ddy * 0.45F));
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent click) {
		draggingTimeline = false;
		looking = false;
		return true;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double h, double v) {
		if (Replay.camMode == Replay.FOLLOW) {
			Replay.orbitDist = (float) Math.max(1.5, Math.min(20, Replay.orbitDist - v * 0.5));
		} else {
			flySpeed = (float) Math.max(0.5, Math.min(60, flySpeed * (v > 0 ? 1.2 : 1 / 1.2)));
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		int k = input.key();
		switch (k) {
			case GLFW.GLFW_KEY_P -> Replay.playing = !Replay.playing;
			case GLFW.GLFW_KEY_LEFT -> Replay.seek(Replay.time() - 100);
			case GLFW.GLFW_KEY_RIGHT -> Replay.seek(Replay.time() + 100);
			case GLFW.GLFW_KEY_COMMA -> {
				Replay.playing = false;
				Replay.seek(Math.floor(Replay.time()) - 1);
			}
			case GLFW.GLFW_KEY_PERIOD -> {
				Replay.playing = false;
				Replay.seek(Math.floor(Replay.time()) + 1);
			}
			case GLFW.GLFW_KEY_TAB -> nextTarget();
			case GLFW.GLFW_KEY_H -> hideUi = !hideUi;
			case GLFW.GLFW_KEY_R -> toggleRecording();
			case GLFW.GLFW_KEY_F -> setMode((Replay.camMode + 1) % 3);
			case GLFW.GLFW_KEY_K -> {
				Replay.addKey();
				ClipToast.show("Camera point", Replay.KEYS.size() + " points - use Path to fly through them", null, false);
			}
			case GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_3, GLFW.GLFW_KEY_4, GLFW.GLFW_KEY_5 -> Replay.speed = SPEEDS[k - GLFW.GLFW_KEY_1];
			default -> {
				return super.keyPressed(input); // Esc closes
			}
		}
		return true;
	}
}
