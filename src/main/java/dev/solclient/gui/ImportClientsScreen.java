package dev.solclient.gui;

import dev.solclient.Gfx;
import dev.solclient.Lang;
import dev.solclient.imp.ClientImport;
import dev.solclient.imp.ClientImport.Found;
import dev.solclient.imp.ClientImport.Profile;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

/** "Import Client Settings": pick the client (Lunar, Badlion, Feather / Dawn), then the profile, and what can be read comes over. */
public class ImportClientsScreen extends Screen {

	private final Screen parent;
	private List<Found> clients = new ArrayList<>();
	private Found picked;
	private String result;
	private final List<int[]> hits = new ArrayList<>();
	private final List<Runnable> acts = new ArrayList<>();
	private float k = 1F;

	public ImportClientsScreen(Screen parent) {
		super(Text.literal("Import Client Settings"));
		this.parent = parent;
		clients = ClientImport.detect();
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void renderBackground(DrawContext c, int mx, int my, float delta) {
		c.fill(0, 0, width, height, 0x99000000);
	}

	private static boolean in(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private void hit(int x, int y, int w, int h, Runnable r) {
		hits.add(new int[] {x, y, w, h});
		acts.add(r);
	}

	@Override
	public void render(DrawContext c, int mouseX, int mouseY, float delta) {
		hits.clear();
		acts.clear();
		renderBackground(c, mouseX, mouseY, delta);
		k = Math.max(0.5F, Math.min(1F, Math.min((width - 12) / 560F, (height - 12) / 330F)));
		int vw = Math.round(width / k), vh = Math.round(height / k);
		int mx = Math.round(mouseX / k), my = Math.round(mouseY / k);
		c.getMatrices().pushMatrix();
		c.getMatrices().scale(k, k);
		try {
			int pw = Math.min(560, vw - 12), ph = Math.min(330, vh - 12);
			int x = (vw - pw) / 2, y = (vh - ph) / 2;
			Gfx.rrectBorder(c, x, y, pw, ph, 6, Theme.PANEL_UI, Theme.PANEL_BORDER);
			Gfx.text(c, Lang.tr("Import Client Settings", "Importar definições de outro client"), x + 18, y + 16, Theme.TEXT);
			close(c, mx, my, x + pw - 38, y + 10);
			if (result != null) {
				drawResult(c, mx, my, x, y, pw, ph);
			} else if (picked != null) {
				drawProfiles(c, mx, my, x, y, pw, ph);
			} else {
				drawClients(c, mx, my, x, y, pw, ph);
			}
		} finally {
			c.getMatrices().popMatrix();
		}
	}

	private void close(DrawContext c, int mx, int my, int x, int y) {
		boolean h = in(mx, my, x, y, 22, 22);
		Gfx.rrect(c, x, y, 22, 22, 5, h ? Theme.OFF_BG_HOVER : Theme.OFF_BG);
		Gfx.textC(c, "x", x + 11, y + 7, Theme.TEXT);
		hit(x, y, 22, 22, this::close);
	}

	private void drawClients(DrawContext c, int mx, int my, int x, int y, int pw, int ph) {
		Gfx.textC(c, Lang.tr("Pick the client you want to import from.", "Escolhe o client de onde queres importar."), x + pw / 2, y + 42, Theme.DIM);
		int gap = 12, cw = (pw - 36 - gap * 2) / 3, cy = y + 66, ch = ph - 66 - 54;
		for (int i = 0; i < clients.size(); i++) {
			Found f = clients.get(i);
			int cx = x + 18 + i * (cw + gap);
			boolean hov = in(mx, my, cx, cy, cw, ch);
			Gfx.rrectBorder(c, cx, cy, cw, ch, 6, hov ? Theme.TILE_HOVER : Theme.TILE, hov ? Theme.CARD_BORDER_HOVER : Theme.CARD_BORDER);
			int is = Math.min(48, ch / 3);
			Gfx.tex(c, Theme.icon2(f.icon()), cx + (cw - is) / 2, cy + ch / 2 - is - 6, is, is, 64, 64, 0xFFFFFFFF);
			Gfx.textFit(c, Lang.tr("Import from ", "Importar do ") + f.label(), cx + cw / 2, cw - 10, cy + ch / 2 + 6, Theme.TEXT);
			int n = f.profiles().size();
			String sub = n > 0 ? n + (n == 1 ? Lang.tr(" profile found", " perfil encontrado") : Lang.tr(" profiles found", " perfis encontrados"))
					: f.note().isEmpty() ? Lang.tr("Not found on this computer", "Não encontrado neste computador") : f.note();
			Gfx.textFit(c, sub, cx + cw / 2, cw - 10, cy + ch / 2 + 22, n > 0 ? 0xFF8FE3A8 : Theme.DIM);
			hit(cx, cy, cw, ch, () -> choose(f));
		}
		Gfx.textFit(c, Lang.tr("It brings over which mods were on (Feather / Dawn), your key bindings, mouse sensitivity and FOV.",
				"Traz os mods que estavam ligados (Feather / Dawn), as teclas, a sensibilidade do rato e o FOV."), x + pw / 2, pw - 30, y + ph - 40, Theme.DIM);
	}

	private void choose(Found f) {
		if (f.profiles().isEmpty()) {
			result = Lang.tr("Nothing to import: ", "Nada para importar: ") + f.label() + Lang.tr(" was not found on this computer, or it has no settings Sleep can read yet.",
					" não foi encontrado neste computador, ou não tem definições que o Sleep consiga ler ainda.");
			return;
		}
		if (f.profiles().size() == 1) {
			result = ClientImport.run(f, f.profiles().get(0));
			return;
		}
		picked = f;
	}

	private void drawProfiles(DrawContext c, int mx, int my, int x, int y, int pw, int ph) {
		Gfx.textC(c, Lang.tr("Which profile of ", "Que perfil do ") + picked.label() + "?", x + pw / 2, y + 42, Theme.DIM);
		int ry = y + 66, rh = 28;
		for (Profile p : picked.profiles()) {
			if (ry + rh > y + ph - 50) {
				break;
			}
			boolean hov = in(mx, my, x + 24, ry, pw - 48, rh - 4);
			Gfx.rrectBorder(c, x + 24, ry, pw - 48, rh - 4, 5, hov ? Theme.TILE_HOVER : Theme.TILE, Theme.CARD_BORDER);
			Gfx.textClip(c, p.name(), x + 36, ry + 8, pw - 90, Theme.TEXT);
			final Found f = picked;
			hit(x + 24, ry, pw - 48, rh - 4, () -> result = ClientImport.run(f, p));
			ry += rh;
		}
		button(c, mx, my, x + 24, y + ph - 40, 90, 24, Lang.tr("Back", "Voltar"), () -> picked = null);
	}

	private void drawResult(DrawContext c, int mx, int my, int x, int y, int pw, int ph) {
		int ty = y + 60;
		for (String line : result.split("\n")) {
			Gfx.textFit(c, line, x + pw / 2, pw - 40, ty, Theme.TEXT);
			ty += 14;
		}
		button(c, mx, my, x + pw / 2 - 45, y + ph - 44, 90, 26, "OK", this::close);
	}

	private void button(DrawContext c, int mx, int my, int x, int y, int w, int h, String label, Runnable r) {
		boolean hov = in(mx, my, x, y, w, h);
		Gfx.rrect(c, x, y, w, h, 5, hov ? Theme.accentHover() : Theme.accent());
		Gfx.textFit(c, label, x + w / 2, w - 8, y + (h - 8) / 2, 0xFFFFFFFF);
		hit(x, y, w, h, r);
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		double mx = click.x() / k, my = click.y() / k;
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
