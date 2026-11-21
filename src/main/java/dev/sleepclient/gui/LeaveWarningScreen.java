package dev.sleepclient.gui;

import dev.sleepclient.Gfx;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

/** "Cuidado: you will be removed from the current server" confirmation shown before an add-on takes you elsewhere. */
public class LeaveWarningScreen extends Screen {

	private final Screen back;
	private final String target;
	private final Runnable confirm;

	private record Hit(int x, int y, int w, int h, Runnable run) {
	}

	private final List<Hit> hits = new ArrayList<>();

	public LeaveWarningScreen(Screen back, String target, Runnable confirm) {
		super(Text.literal("Aviso"));
		this.back = back;
		this.target = target;
		this.confirm = confirm;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void render(DrawContext c, int mx, int my, float delta) {
		super.render(c, mx, my, delta);
		hits.clear();
		MinecraftClient mc = MinecraftClient.getInstance();
		int w = Math.min(width - 16, 340), h = 150;
		int x = (width - w) / 2, y = (height - h) / 2;
		Gfx.rrectBorder(c, x, y, w, h, 10, Theme.PANEL, Theme.RED);

		Gfx.textFit(c, "Cuidado!", x + w / 2, w - 6, y + 14, Theme.RED);
		Gfx.textFit(c, "Se executares esta ação irás ser removido", x + w / 2, w - 6, y + 36, Theme.TEXT);
		Gfx.textFit(c, "do servidor atual.", x + w / 2, w - 6, y + 48, Theme.TEXT);

		String here;
		var entry = mc.getCurrentServerEntry();
		if (entry != null) {
			here = "Servidor atual: " + entry.address;
		} else if (mc.isInSingleplayer()) {
			here = "Mundo atual: singleplayer (é guardado)";
		} else {
			here = "Ligação atual";
		}
		Gfx.textFit(c, here, x + w / 2, w - 6, y + 70, Theme.DIM);
		Gfx.textFit(c, "Destino: " + target, x + w / 2, w - 6, y + 82, Theme.DIM);

		int bw = (w - 14 * 2 - 8) / 2, bh = 24, by = y + h - bh - 14;
		button(c, mx, my, x + 14, by, bw, bh, "Cancelar", false, () -> MinecraftClient.getInstance().setScreen(back));
		button(c, mx, my, x + 14 + bw + 8, by, bw, bh, "Continuar", true, confirm);
	}

	private void button(DrawContext c, int mx, int my, int x, int y, int w, int h, String label, boolean primary, Runnable run) {
		boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
		int bg = primary ? (hov ? Theme.accentHover() : Theme.accent()) : (hov ? Theme.OFF_BG_HOVER : Theme.OFF_BG);
		Gfx.rrect(c, x, y, w, h, 8, bg);
		Gfx.textFit(c, label, x + w / 2, w - 6, y + (h - 8) / 2, primary ? 0xFFFFFFFF : Theme.TEXT);
		hits.add(new Hit(x, y, w, h, run));
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		for (Hit hit : new ArrayList<>(hits)) {
			if (click.x() >= hit.x && click.x() < hit.x + hit.w && click.y() >= hit.y && click.y() < hit.y + hit.h) {
				hit.run.run();
				return true;
			}
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (input.key() == 256) {
			MinecraftClient.getInstance().setScreen(back);
			return true;
		}
		return false;
	}
}
