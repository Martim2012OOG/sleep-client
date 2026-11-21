package dev.sleepclient.gui;

import dev.sleepclient.Gfx;
import dev.sleepclient.Lang;
import dev.sleepclient.mods.AchievementsMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

/** All Sleep achievements: unlocked ones shine (bronze / silver / gold), locked ones show how close you are. */
public class AchievementsScreen extends Screen {

	private static final int[] TIER = {0xFFCD7F32, 0xFFC0C8D8, 0xFFFFC94A};
	private final Screen parent;
	private float scroll;
	private int maxScroll;

	public AchievementsScreen(Screen parent) {
		super(Text.literal("Achievements"));
		this.parent = parent;
	}

	@Override
	public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void render(DrawContext c, int mx, int my, float delta) {
		// Keystrokes look: dim game behind, one small panel, flat translucent tiles
		c.fill(0, 0, width, height, 0x90000000);
		int pw = Math.min(330, width - 24), px = width / 2 - pw / 2;
		int top = 22;
		String title = Lang.t("Achievements");
		String sub = AchievementsMod.unlocked() + " / " + AchievementsMod.ALL.size();
		Gfx.text(c, title, px, top, 0xFFFFFFFF);
		Gfx.textRaw(c, sub, px + pw - Gfx.twRaw(sub), top, 0xA0FFFFFF);
		float total = AchievementsMod.ALL.isEmpty() ? 0 : AchievementsMod.unlocked() / (float) AchievementsMod.ALL.size();
		Gfx.rrectFree(c, px, top + 12, pw, 2, 1, 0x30FFFFFF);
		Gfx.rrectFree(c, px, top + 12, Math.max(2, pw * total), 2, 1, 0xFFFFFFFF);

		int cols = pw >= 300 ? 2 : 1, gap = 2, th = 26;
		int tw = (pw - (cols - 1) * gap) / cols;
		int y0 = top + 22;
		int rows = (AchievementsMod.ALL.size() + cols - 1) / cols;
		maxScroll = Math.max(0, rows * (th + gap) - (height - y0 - 10));
		scroll = Math.max(0, Math.min(maxScroll, scroll));
		c.enableScissor(0, y0, width, height - 6);
		for (int i = 0; i < AchievementsMod.ALL.size(); i++) {
			AchievementsMod.Ach a = AchievementsMod.ALL.get(i);
			int x = px + (i % cols) * (tw + gap), y = y0 + (i / cols) * (th + gap) - Math.round(scroll);
			if (y + th < y0 || y > height) {
				continue;
			}
			boolean done = a.done();
			int tc = TIER[a.tier()];
			Gfx.rrectFree(c, x, y, tw, th, 2, done ? 0x8F000000 : 0x5F000000);
			// medal: a small square in the tier colour (empty frame while locked)
			int sx = x + 6, sy = y + th / 2 - 6;
			if (done) {
				Gfx.rrectFree(c, sx, sy, 12, 12, 2, tc);
				Gfx.rrectFree(c, sx + 3, sy + 3, 6, 6, 1, Gfx.blend(tc, 0xFFFFFFFF, 0.45F));
			} else {
				Gfx.rrectFree(c, sx, sy, 12, 12, 2, 0x40FFFFFF);
				Gfx.rrectFree(c, sx + 1.5F, sy + 1.5F, 9, 9, 1.5F, 0xFF101114);
			}
			int tx = x + 24, maxW = tw - 24 - 30;
			String name = fitRaw(a.name(), maxW), desc = fitRaw(a.desc(), maxW);
			Gfx.textRaw(c, name, tx, y + 4, done ? 0xFFFFFFFF : 0xD0FFFFFF);
			Gfx.textRaw(c, desc, tx, y + 14, 0x80FFFFFF);
			double p = a.progress();
			String pc = done ? "✓" : Math.round(p * 100) + "%";
			Gfx.textRaw(c, pc, x + tw - 6 - Gfx.twRaw(pc), y + 4, done ? tc : 0x90FFFFFF);
			if (!done && p > 0) {
				Gfx.rrectFree(c, x + tw - 26, y + th - 6, 20, 1.5F, 0.75F, 0x30FFFFFF);
				Gfx.rrectFree(c, x + tw - 26, y + th - 6, (float) Math.max(1, 20 * p), 1.5F, 0.75F, 0xFFFFFFFF);
			}
		}
		c.disableScissor();
		Gfx.textRaw(c, "Esc", 8, 8, 0x60FFFFFF);
	}

	private static String fitRaw(String s, int maxW) {
		if (Gfx.twRaw(s) <= maxW) {
			return s;
		}
		while (s.length() > 1 && Gfx.twRaw(s + "..") > maxW) {
			s = s.substring(0, s.length() - 1);
		}
		return s + "..";
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double h, double v) {
		scroll -= (float) v * 24;
		return true;
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		return true;
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (input.key() == 256) {
			close();
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public void close() {
		MinecraftClient.getInstance().setScreen(parent);
	}
}
