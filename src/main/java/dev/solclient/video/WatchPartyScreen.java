package dev.solclient.video;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Control panel of your Watch Party screen: change video, play/pause, skip, redraw the corners, remove. */
public class WatchPartyScreen extends Screen {

	private TextFieldWidget link;
	private TextFieldWidget time;
	private int barY;
	private boolean dragging;
	private double dragFrac;
	private ButtonWidget playPause;

	public WatchPartyScreen() {
		super(Text.literal(WatchPartyText.t("Watch Party")));
	}

	@Override
	protected void init() {
		int cx = width / 2, y = height / 2 - 60;
		link = new TextFieldWidget(textRenderer, cx - 150, y, 170, 20, Text.literal(WatchPartyText.t("YouTube link")));
		link.setMaxLength(300);
		link.setPlaceholder(Text.literal(WatchPartyText.t("Paste a YouTube link...")));
		addDrawableChild(link);
		setInitialFocus(link);
		addDrawableChild(ButtonWidget.builder(Text.literal(WatchPartyText.t("Play")), b -> {
			if (!link.getText().isBlank()) {
				VideoScreens.play(link.getText());
				link.setText("");
			}
		}).dimensions(cx + 24, y, 62, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Live"), b -> {
			if (!link.getText().isBlank()) {
				VideoScreens.playLive(link.getText());
				link.setText("");
			}
		}).dimensions(cx + 90, y, 60, 20).build());

		barY = y + 36; // seek bar (drawn in render)
		y += 56;
		addDrawableChild(ButtonWidget.builder(Text.literal("-1m"), b -> VideoScreens.skip(-60)).dimensions(cx - 150, y, 45, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("-10s"), b -> VideoScreens.skip(-10)).dimensions(cx - 102, y, 45, 20).build());
		playPause = addDrawableChild(ButtonWidget.builder(Text.literal(WatchPartyText.t(VideoScreens.playing() ? "Pause" : "Resume")), b -> {
			VideoScreens.pause(VideoScreens.playing());
			b.setMessage(Text.literal(WatchPartyText.t(VideoScreens.playing() ? "Pause" : "Resume")));
		}).dimensions(cx - 54, y, 108, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("+10s"), b -> VideoScreens.skip(10)).dimensions(cx + 57, y, 45, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("+1m"), b -> VideoScreens.skip(60)).dimensions(cx + 105, y, 45, 20).build());

		y += 24;
		time = new TextFieldWidget(textRenderer, cx - 150, y, 236, 20, Text.literal(WatchPartyText.t("Go to time")));
		time.setMaxLength(12);
		time.setPlaceholder(Text.literal(WatchPartyText.t("Go to time (e.g. 1:30 or 90)")));
		addDrawableChild(time);
		addDrawableChild(ButtonWidget.builder(Text.literal(WatchPartyText.t("Go")), b -> goTo()).dimensions(cx + 90, y, 60, 20).build());

		y += 24;
		addDrawableChild(ButtonWidget.builder(Text.literal(WatchPartyText.t("Restart")), b -> VideoScreens.seek("0")).dimensions(cx - 150, y, 98, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal(WatchPartyText.t("Redraw Corners")), b -> {
			close();
			VideoScreens.startPlacing();
		}).dimensions(cx - 49, y, 98, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal(WatchPartyText.t("Remove Screen")), b -> {
			VideoScreens.remove();
			close();
		}).dimensions(cx + 52, y, 98, 20).build());

		y += 34;
		addDrawableChild(ButtonWidget.builder(Text.literal(WatchPartyText.t("Done")), b -> close()).dimensions(cx - 50, y, 100, 20).build());
	}

	private void goTo() {
		if (!time.getText().isBlank()) {
			VideoScreens.seek(time.getText());
			time.setText("");
		}
	}

	private boolean onBar(double mx, double my) {
		int cx = width / 2;
		return mx >= cx - 154 && mx <= cx + 154 && my >= barY - 6 && my <= barY + 14;
	}

	private void setFrac(double mx) {
		int cx = width / 2;
		dragFrac = Math.max(0, Math.min(1, (mx - (cx - 150)) / 300.0));
	}

	@Override
	public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
		if (click.button() == 0 && VideoScreens.duration() > 0 && onBar(click.x(), click.y())) {
			dragging = true;
			setFrac(click.x());
			return true;
		}
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseDragged(net.minecraft.client.gui.Click click, double dx, double dy) {
		if (dragging) {
			setFrac(click.x());
			return true;
		}
		return super.mouseDragged(click, dx, dy);
	}

	@Override
	public boolean mouseReleased(net.minecraft.client.gui.Click click) {
		if (dragging) {
			dragging = false;
			setFrac(click.x());
			VideoScreens.seekTo(dragFrac * VideoScreens.duration());
			return true;
		}
		return super.mouseReleased(click);
	}

	private static String fmt(double sec) {
		int t = (int) Math.max(0, sec);
		return t >= 3600 ? (t / 3600) + ":" + String.format("%02d:%02d", t / 60 % 60, t % 60) : (t / 60) + ":" + String.format("%02d", t % 60);
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
		if (time != null && time.isFocused() && (input.key() == 257 || input.key() == 335)) {
			goTo();
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public void render(DrawContext c, int mouseX, int mouseY, float delta) {
		super.render(c, mouseX, mouseY, delta);
		int cx = width / 2, y = height / 2 - 60;
		c.drawCenteredTextWithShadow(textRenderer, getTitle(), cx, y - 32, 0xFFFFFFFF);
		double dur = VideoScreens.duration();
		int bx = cx - 150;
		c.fill(bx, barY, bx + 300, barY + 4, 0xFF3A3F4B);
		if (dur > 0) {
			double frac = dragging ? dragFrac : Math.max(0, Math.min(1, VideoScreens.position() / dur));
			int kx = bx + (int) Math.round(frac * 300);
			c.fill(bx, barY, kx, barY + 4, 0xFF3FC8FF);
			c.fill(kx - 3, barY - 4, kx + 3, barY + 8, 0xFFFFFFFF);
			c.drawTextWithShadow(textRenderer, fmt(dragging ? dragFrac * dur : VideoScreens.position()), bx, barY + 12, 0xFFFFFFFF);
			String ds = fmt(dur);
			c.drawTextWithShadow(textRenderer, ds, bx + 300 - textRenderer.getWidth(ds), barY + 12, 0xFF9AA3B5);
		} else {
			c.drawCenteredTextWithShadow(textRenderer, WatchPartyText.t("Loading video length..."), cx, barY + 12, 0xFF9AA3B5);
		}
		String now = VideoScreens.nowPlaying();
		c.drawCenteredTextWithShadow(textRenderer, now == null ? WatchPartyText.t("No video yet") : WatchPartyText.t("Now: " + now), cx, y - 18, 0xFF9AA3B5);
		if (!Mcef.installed()) {
			c.drawCenteredTextWithShadow(textRenderer, WatchPartyText.t("Video engine missing (rebuild Sleep with MCEF included)"), cx, y + 166, 0xFFFF8A8A);
		} else if (!Mcef.ready()) {
			c.drawCenteredTextWithShadow(textRenderer, WatchPartyText.t("Video engine: " + Mcef.status()), cx, y + 166, 0xFFFFD27A);
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
