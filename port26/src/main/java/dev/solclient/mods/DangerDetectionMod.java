package dev.solclient.mods;

import dev.solclient.Gfx;
import dev.solclient.Lang;
import dev.solclient.option.Option;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;

/**
 * Danger Detection: a small panel with a warning sign that shows when a creeper is near you (and in red when it is about to explode),
 * with the direction and the distance. It only shows when there is one; move it in the HUD editor.
 */
public class DangerDetectionMod extends HudMod {

	private final Option.Num radius = num("Radius", 12, 4, 32, 1, " blocks");
	private final Option.Bool arrows = bool("Direction Arrows", true);
	private final Option.Bool sound = bool("Warning Sound", true);
	private final Option.Bool flash = bool("Flashing Border", true);

	private record Alert(int level, String text, double dist, double dx, double dz, boolean hasDir, String key) {
	}

	private volatile List<Alert> alerts = List.of();
	private long lastBeep;
	private final java.util.Set<String> known = new java.util.HashSet<>();

	public DangerDetectionMod() {
		super("Danger Detection", "danger_detection", "Warns you when a creeper is near you, with its direction and distance.", 0.5F, 0.16F);
	}

	@Override
	public boolean enabledByDefault() {
		return false;
	}

	@Override
	public boolean visible() {
		return !alerts.isEmpty();
	}

	@Override
	public int width() {
		return 150;
	}

	@Override
	public int height() {
		return 14 + Math.max(1, Math.min(5, alerts.isEmpty() ? 3 : alerts.size())) * 12;
	}

	private static String metres(double d) {
		return Math.round(d) + "m";
	}

	@Override
	public void tick() {
		if (mc.player == null || mc.level == null) {
			alerts = List.of();
			return;
		}
		if (mc.player.tickCount % 4 != 0) {
			return; // a scan a few times a second is plenty
		}
		var p = mc.player;
		double r = radius.f();
		List<Alert> out = new ArrayList<>();
		double r2 = r * r;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e == p || e.isRemoved()) {
				continue;
			}
			double dx = e.getX() - p.getX(), dz = e.getZ() - p.getZ(), dy = e.getY() - p.getY();
			double d2 = dx * dx + dy * dy + dz * dz;
			if (d2 > r2) {
				continue;
			}
			double d = Math.sqrt(d2);
			if (e instanceof Creeper cr) {
				boolean lit = cr.isIgnited();
				if (lit || d < 9) {
					out.add(new Alert(lit ? 2 : 1, lit ? Lang.tr("Creeper about to explode!", "Creeper vai explodir!") : "Creeper", d, dx, dz, true, "cr" + e.getId()));
				}
			}
		}
		out.sort(Comparator.<Alert>comparingInt(a -> -a.level()).thenComparingDouble(Alert::dist));
		if (out.size() > 5) {
			out = new ArrayList<>(out.subList(0, 5));
		}
		// a beep when something new shows up (at most every 1.2 s)
		long now = System.currentTimeMillis();
		java.util.Set<String> keys = new java.util.HashSet<>();
		boolean fresh = false;
		for (Alert a : out) {
			keys.add(a.key());
			if (!known.contains(a.key()) && a.level() >= 1) {
				fresh = true;
			}
		}
		known.clear();
		known.addAll(keys);
		if (fresh && sound.on() && now - lastBeep > 1200L) {
			lastBeep = now;
			try {
				p.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 0.7F, 0.6F);
			} catch (RuntimeException ignored) {
				// no sound
			}
		}
		alerts = out;
	}

	@Override
	protected void renderHud(GuiGraphicsExtractor c, boolean edit) {
		List<Alert> list = alerts;
		if (list.isEmpty() && edit) {
			list = List.of(new Alert(2, Lang.tr("Creeper about to explode!", "Creeper vai explodir!"), 4, 3, 2, true, "e1"));
		}
		int rows = Math.max(1, Math.min(5, list.size()));
		int w = width(), h = 14 + rows * 12;
		int top = 0;
		for (Alert a : list) {
			top = Math.max(top, a.level());
		}
		int border = top >= 2 ? 0xFFE5453A : top == 1 ? 0xFFF2A33A : 0xFFE6C84A;
		if (flash.on() && top >= 2 && (System.currentTimeMillis() / 300) % 2 == 0) {
			border = 0xFFFFFFFF;
		}
		Gfx.rrect(c, 0, 0, w, h, 5, border);
		Gfx.rrect(c, 1, 1, w - 2, h - 2, 4, 0xE61A1214);
		// the warning sign + the title
		Gfx.tex(c, dev.solclient.gui.Theme.iconPublic("danger_detection"), 4, 1, 12, 12, 64, 64, 0xFFFFFFFF);
		c.text(mc.font, Lang.tr("DANGER", "PERIGO"), 19, 3, border, true);
		for (int i = 0; i < rows; i++) {
			Alert a = list.get(i);
			int y = 15 + i * 12;
			int col = a.level() >= 2 ? 0xFFFF6B5E : a.level() == 1 ? 0xFFFFC266 : 0xFFE9DC8A;
			if (arrows.on() && a.hasDir() && mc.player != null) {
				float target = (float) (Mth.atan2(-a.dx(), a.dz()) * 57.29578);
				float rel = Mth.wrapDegrees(target - mc.player.getYRot());
				arrow(c, 11, y + 4, (float) Math.toRadians(rel), col);
			} else {
				c.fill(9, y + 3, 13, y + 7, col);
			}
			String t = mc.font.plainSubstrByWidth(a.text(), w - 52);
			c.text(mc.font, t, 20, y, col, false);
			String d = metres(a.dist());
			c.text(mc.font, d, w - 4 - mc.font.width(d), y, 0xFFB9B2B4, false);
		}
	}

	/** A small arrow pointing where the danger is, relative to where you look (up = ahead). */
	private static void arrow(GuiGraphicsExtractor c, int cx, int cy, float angle, int col) {
		var m = c.pose();
		m.pushMatrix();
		m.translate(cx, cy);
		m.rotate(angle);
		for (int r = 0; r < 5; r++) {
			c.fill(-r / 2 - (r > 0 ? 1 : 0), r - 3, r / 2 + (r > 0 ? 2 : 1), r - 2, col);
		}
		c.fill(0, 2, 1, 4, col);
		m.popMatrix();
	}
}
