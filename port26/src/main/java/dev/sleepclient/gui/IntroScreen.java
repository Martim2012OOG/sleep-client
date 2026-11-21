package dev.sleepclient.gui;

import dev.sleepclient.Gfx;
import dev.sleepclient.mods.CinematicIntroMod;
import dev.sleepclient.video.FfVideo;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Plays the intro video full screen once when the game opens, then shows the home screen. */
public class IntroScreen extends Screen {

	private final Screen next;
	private final Path file;
	private final boolean skippable;
	/** Own sound of a bundled seasonal intro (null = the normal audio.wav). */
	private final Path audio;
	/** The video already ends on the seasonal home screen, so no extra transition is drawn on top. */
	private final boolean baked;
	private FfVideo video;
	private long openedAt, firstFrameAt, transitionAt;
	private boolean done;
	/** Seasonal transition (Christmas / Halloween): the intro's last picture turns into the seasonal home screen. */
	private static final long TRANSITION_MS = 1900L;

	public IntroScreen(Screen next, Path file, boolean skippable) {
		this(next, file, skippable, null, false);
	}

	public IntroScreen(Screen next, Path file, boolean skippable, Path audio, boolean baked) {
		super(Component.literal("Intro"));
		this.next = next;
		this.file = file;
		this.skippable = skippable;
		this.audio = audio;
		this.baked = baked;
	}

	/** "natal" / "halloween" when a season is on (date or Settings > Menus > Seasonal Decorations), else null. */
	public static String seasonKey() {
		int s = Season.current();
		return s == Season.HALLOWEEN ? "halloween" : s == Season.CHRISTMAS ? "natal" : null;
	}

	@Override
	protected void init() {
		if (openedAt == 0) {
			openedAt = System.currentTimeMillis();
		}
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor c, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor c, int mx, int my, float delta) {
		try {
			renderSafe(c, mx, my, delta);
		} catch (Throwable t) {
			// e.g. the window was resized / maximised while the game was still loading: never crash the game for
			// the intro, just go on to the home screen
			System.err.println("[Sleep] intro: " + t);
			try {
				finish();
			} catch (Throwable ignored) {
			}
		}
	}

	private void renderSafe(GuiGraphicsExtractor c, int mx, int my, float delta) {
		c.fill(0, 0, width, height, 0xFF000000);
		long now = System.currentTimeMillis();
		if (video == null) {
			String ff = CinematicIntroMod.ffmpeg();
			if (ff == null) {
				// still looking for ffmpeg (a moment) or there is none: then just go to the home screen
				if (!CinematicIntroMod.ffmpegSearching() && now - openedAt > 1500L || now - openedAt > 6000L) {
					finish();
				}
				return;
			}
			video = new FfVideo(file, 1280, 720, false, Gfx.id("video/intro"));
			if (!video.start(ff)) {
				finish();
				return;
			}
		}
		if (transitionAt > 0) {
			transition(c, now);
			return;
		}
		if (video.upload()) {
			if (firstFrameAt == 0) {
				firstFrameAt = now;
				if (audio != null) {
					CinematicIntroMod.startMusic(audio);
				} else {
					CinematicIntroMod.startMusic(); // the music starts with the first picture
				}
			}
			CinematicIntroMod.cover(c, video.id(), 1280, 720, width, height);
		}
		if (video.finished() && firstFrameAt > 0 && !baked && Season.active()) {
			transitionAt = now; // keep the last picture and turn it into the seasonal home screen
			transition(c, now);
			return;
		}
		if (video.finished() || video.failed() || firstFrameAt == 0 && now - openedAt > 8000L) {
			finish();
		}
	}

	private static float smooth(float k) {
		k = Math.max(0, Math.min(1, k));
		return k * k * (3 - 2 * k);
	}

	/**
	 * Christmas: snow starts falling on the intro's cave, it frosts over and opens onto the snowy Christmas scene.
	 * Halloween: the cave goes dark purple, a swarm of bats flies across and a lightning flash reveals the pumpkins.
	 */
	private void transition(GuiGraphicsExtractor c, long now) {
		float k = (now - transitionAt) / (float) TRANSITION_MS;
		if (k >= 1F) {
			finish();
			return;
		}
		boolean xmas = Season.current() == Season.CHRISTMAS;
		float reveal = smooth((k - 0.35F) / 0.45F); // 0 = intro picture, 1 = seasonal scene
		Season.background(c, width, height);
		Season.overlay(c, width, height);
		CinematicIntroMod.cover(c, video.id(), 1280, 720, width, height, 1F + 0.12F * smooth(k), 1F - reveal);
		long seed = 31;
		if (xmas) {
			// frost + a thick snow shower that grows and then settles into the scene's own snow
			float frost = (float) Math.sin(Math.PI * Math.min(1, k * 1.1F));
			c.fill(0, 0, width, height, ((int) (frost * 120) << 24) | 0xEAF4FF);
			java.util.Random r = new java.util.Random(seed);
			int n = (int) (260 * Math.min(1F, k * 2.2F) * (1.15F - reveal * 0.7F));
			for (int i = 0; i < n; i++) {
				float sx = r.nextFloat() * width, sp = 0.5F + r.nextFloat(), size = 3 + r.nextFloat() * 7;
				float y = ((r.nextFloat() * height) + k * height * 1.4F * sp) % (height + 20) - 10;
				float x = sx + (float) Math.sin(k * 8 + i) * 12 + k * 60 * sp;
				Gfx.tex(c, Gfx.id("textures/gui/season/snowflake.png"), Math.round(x) % Math.max(1, width), Math.round(y), Math.round(size), Math.round(size), 16, 16,
						((int) (180 + 70 * r.nextFloat()) << 24) | 0xFFFFFF);
			}
		} else {
			// darkness + bats + lightning
			float dark = (float) Math.sin(Math.PI * Math.min(1, k * 1.15F));
			c.fill(0, 0, width, height, ((int) (dark * 170) << 24) | 0x1A0830);
			java.util.Random r = new java.util.Random(seed);
			for (int i = 0; i < 46; i++) {
				float start = r.nextFloat() * 0.35F, speed = 0.8F + r.nextFloat() * 0.8F;
				float t = (k - start) * speed;
				if (t < 0 || t > 1.2F) {
					continue;
				}
				float x = width * (1.1F - t * 1.3F), y = height * (0.1F + r.nextFloat() * 0.8F) + (float) Math.sin(t * 12 + i) * 18;
				int sz = 14 + r.nextInt(22);
				int frame = ((int) (now / 70 + i)) % 2;
				Gfx.texUv(c, Gfx.id("textures/gui/season/bat.png"), Math.round(x), Math.round(y), sz, sz, frame * 16, 0, 16, 16, 32, 16, 0xFFFFFFFF);
			}
			float f = k - 0.55F;
			if (f > 0 && f < 0.18F) {
				float a = f < 0.04F ? 1 : f < 0.08F ? 0.35F : f < 0.11F ? 0.85F : (0.18F - f) / 0.07F * 0.5F;
				c.fill(0, 0, width, height, ((int) (a * 200) << 24) | 0xE6EBFF);
			}
		}
	}

	private void finish() {
		if (done) {
			return;
		}
		done = true;
		if (video != null) {
			video.close();
			video = null;
		}
		dev.sleepclient.compat.Mc.setScreen(Minecraft.getInstance(), next);
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		if (skippable && (input.key() == 256 || input.key() == 32 || input.key() == 257)) {
			skip();
		}
		return true;
	}

	/** Skipping in a season still plays the short transition, so it never jumps straight from the cave to the pumpkins. */
	private void skip() {
		if (baked) {
			CinematicIntroMod.stopMusic();
			finish();
		} else if (transitionAt == 0 && video != null && video.hasPicture() && Season.active()) {
			transitionAt = System.currentTimeMillis() - TRANSITION_MS / 4;
		} else if (transitionAt == 0) {
			finish();
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		if (skippable) {
			skip();
		}
		return true;
	}

	@Override
	public void removed() {
		if (video != null) {
			video.close();
			video = null;
		}
	}
}
