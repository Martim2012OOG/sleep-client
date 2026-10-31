package dev.solclient.voice.persona;

import dev.solclient.Gfx;
import dev.solclient.gui.Theme;
import dev.solclient.gui.UiScreen;
import dev.solclient.mods.PersonasMod;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/** Character picker: a grid of characters with their pictures, then fine pitch / strength / loudness. */
public class PersonaScreen extends UiScreen {

	private int scroll;
	private static final java.util.Set<Identifier> LOADED = new java.util.HashSet<>();

	public PersonaScreen() {
		super("Characters");
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	/** A custom character's picture from kubo/personas, registered the first time it is needed. */
	private static boolean ready(Persona p) {
		if (p.imageFile == null) {
			return !p.custom; // bundled pictures are always there; custom without a picture gets a letter
		}
		if (!LOADED.contains(p.image)) {
			try (java.io.InputStream in = java.nio.file.Files.newInputStream(p.imageFile)) {
				var img = net.minecraft.client.texture.NativeImage.read(in);
				var tex = new net.minecraft.client.texture.NativeImageBackedTexture(() -> "kubo_persona", img);
				tex.upload();
				mc().getTextureManager().registerTexture(p.image, tex);
			} catch (Exception e) {
				System.err.println("[Sleep] persona picture " + p.imageFile + ": " + e);
			}
			LOADED.add(p.image);
		}
		return true;
	}

	private static net.minecraft.client.MinecraftClient mc() {
		return net.minecraft.client.MinecraftClient.getInstance();
	}

	@Override
	protected void draw(DrawContext c, float delta) {
		PersonasMod m = PersonasMod.instance;
		if (m == null) {
			return;
		}
		int pw = Math.min(520, width - 24), ph = Math.min(330, height - 24);
		int px = width / 2 - pw / 2, py = height / 2 - ph / 2;
		Gfx.rrectBorder(c, px, py, pw, ph, 10, Theme.PANEL_UI, 0xFF23252D);
		Gfx.text(c, "Characters", px + 16, py + 14, Theme.TEXT);
		String sub = m.enabled ? "Pick who you want to be. Everything you say in any voice chat sounds like them."
				: "Turn on the Personas mod to use a character.";
		Gfx.text(c, sub, px + 16, py + 28, Theme.DIM);

		List<Persona> all = Persona.all();
		int gx = px + 16, gy = py + 46, gw = pw - 32, gh = ph - 46 - 96;
		int cols = Math.max(2, gw / 96), cw = (gw - (cols - 1) * 8) / cols, chh = 96;
		int rows = (all.size() + cols - 1) / cols;
		int maxScroll = Math.max(0, rows * (chh + 8) - 8 - gh);
		scroll = Math.max(0, Math.min(maxScroll, scroll));
		c.enableScissor(gx - 2, gy, gx + gw + 2, gy + gh);
		for (int i = 0; i < all.size(); i++) {
			Persona p = all.get(i);
			int x = gx + (i % cols) * (cw + 8), y = gy + (i / cols) * (chh + 8) - scroll;
			if (y + chh < gy || y > gy + gh) {
				continue;
			}
			boolean sel = p.id.equals(m.character.get()), hov = hovered(x, y, cw, chh) && mouseY >= gy && mouseY < gy + gh;
			float a = anim("pc" + p.id, hov);
			Gfx.rrectBorder(c, x, y, cw, chh, 6, Gfx.blend(TILE, TILE_HOVER, a), sel ? 0xFF8B5CF6 : Gfx.blend(Theme.CARD_BORDER, Theme.CARD_BORDER_HOVER, a));
			int is = Math.min(cw - 16, 56);
			if (ready(p) && (p.imageFile != null || !p.custom)) {
				Gfx.tex(c, p.image, x + (cw - is) / 2, y + 8, is, is, 64, 64, 0xFFFFFFFF);
			} else {
				Gfx.circle(c, x + cw / 2F, y + 8 + is / 2F, is / 2.4F, 0xFF3A3D49);
				Gfx.textC(c, p.name.substring(0, 1).toUpperCase(), x + cw / 2, y + 8 + is / 2 - 4, 0xFFFFFFFF);
			}
			Gfx.textFit(c, p.name, x + cw / 2, cw - 8, y + 8 + is + 6, sel ? 0xFFFFFFFF : Theme.TEXT);
			if (sel) {
				Gfx.textFit(c, "Selected", x + cw / 2, cw - 8, y + 8 + is + 18, 0xFFA78BFA);
			}
			region(x, y, cw, chh, () -> {
				m.pick(p);
				if (!m.enabled) {
					m.setEnabled(true);
				}
			});
		}
		c.disableScissor();

		// ---- tuning
		int ty = py + ph - 88;
		Persona cur = m.current();
		Gfx.text(c, cur.name + " - " + cur.tagline, px + 16, ty, Theme.TEXT);
		int sw = (pw - 32 - 16) / 3;
		slider(c, "pp", px + 16, ty + 18, sw, "Fine Pitch  " + fmt(m.pitch.f()) + " st", (m.pitch.f() + 12F) / 24F, v -> m.pitch.set(Math.round((v * 24F - 12F) * 2F) / 2F));
		slider(c, "pa", px + 24 + sw, ty + 18, sw, "Effect Strength  " + Math.round(m.amount.f()) + "%", m.amount.f() / 150F, v -> m.amount.set((float) Math.round(v * 30F) * 5F));
		slider(c, "pv", px + 32 + 2 * sw, ty + 18, sw, "Loudness  " + fmt(m.volume.f()) + " dB", (m.volume.f() + 12F) / 24F, v -> m.volume.set((float) Math.round(v * 24F - 12F)));
		String st = !m.enabled ? "Personas is off" : PersonaHook.hookAlive() ? "Active on " + PersonaHook.streams + " microphone stream" + (PersonaHook.streams == 1 ? "" : "s")
				: "Ready: starts when a voice chat records your mic";
		Gfx.text(c, st, px + 16, ty + 48, PersonaHook.hookAlive() ? 0xFF4ADE80 : Theme.DIM);
		Gfx.text(c, "Your own: put name.json + name.png in the folder below (any picture you like)", px + 16, ty + 60, Theme.DIM);
		button(c, "pclose", px + pw - 76, py + ph - 30, 60, 20, "Close", this::close);
		button(c, "pdir", px + 16, py + ph - 30, 150, 20, "My characters folder", () -> {
			try {
				java.nio.file.Path dir = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("kubo").resolve("personas");
				java.nio.file.Files.createDirectories(dir);
				net.minecraft.util.Util.getOperatingSystem().open(dir.toFile());
			} catch (java.io.IOException e) {
				System.err.println("[Sleep] personas folder: " + e);
			}
		});
		// try it without anybody: your microphone through the character, played back to you
		boolean mon = PersonaMonitor.on();
		button(c, "pmon", px + pw - 76 - 150, py + ph - 30, 142, 20, mon ? "Stop hearing myself" : "Hear myself (test)", PersonaMonitor::toggle);
		if (mon || !PersonaMonitor.status.isEmpty()) {
			Gfx.text(c, PersonaMonitor.status, px + pw - 76 - 150, py + ph - 44, mon ? 0xFF4ADE80 : 0xFFF87171);
		}
	}

	private static String fmt(float v) {
		return (v > 0 ? "+" : "") + (v == Math.rint(v) ? String.valueOf((int) v) : String.valueOf(v));
	}

	@Override
	public void removed() {
		PersonaMonitor.stop(); // never leave the microphone playing back when the window is gone
		super.removed();
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double dx, double dy) {
		scroll -= (int) (dy * 24);
		return true;
	}
}
