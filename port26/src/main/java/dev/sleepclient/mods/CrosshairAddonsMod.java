package dev.sleepclient.mods;

import dev.sleepclient.Gfx;
import dev.sleepclient.option.Option;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Crosshair Addons: extras around any crosshair - a hit marker (an X that flashes when you hit), a red one when the
 * hit kills, and the distance to the player / mob you are looking at.
 */
public class CrosshairAddonsMod extends Mod {

	public static CrosshairAddonsMod instance;

	private final Option.Bool hitMarker = bool("Hit Marker", true);
	private final Option.Bool killMarker = bool("Red Marker On Kills", true);
	private final Option.Bool distance = bool("Distance To Target", true);
	private final Option.Col colour = colour("Marker Colour", 0xFFFFFFFF);
	private long hitAt;
	private boolean kill;
	private LivingEntity lastHit;

	public CrosshairAddonsMod() {
		super("Crosshair Addons", "crosshair_addons", "Hit marker, kill marker and distance to what you aim at.", Category.VISUAL);
		instance = this;
	}

	public void onAttack(Entity e) {
		if (e instanceof LivingEntity le) {
			hitAt = System.currentTimeMillis();
			kill = false;
			lastHit = le;
		}
	}

	@Override
	public void tick() {
		if (lastHit != null && System.currentTimeMillis() - hitAt < 600 && (lastHit.isDeadOrDying() || lastHit.getHealth() <= 0)) {
			kill = true;
			hitAt = System.currentTimeMillis();
			lastHit = null;
		}
	}

	public void draw(GuiGraphicsExtractor c) {
		if (mc.player == null || dev.sleepclient.compat.Mc.hideGui(mc) || dev.sleepclient.compat.Mc.screen(mc) != null) {
			return;
		}
		int cx = mc.getWindow().getGuiScaledWidth() / 2, cy = mc.getWindow().getGuiScaledHeight() / 2;
		long age = System.currentTimeMillis() - hitAt;
		if (hitMarker.on() && age < 350) {
			float a = 1F - age / 350F;
			int col = Gfx.withAlpha(kill && killMarker.on() ? 0xFFFF3B3B : colour.c(), Math.round(255 * a));
			float g = 4F + (1F - a) * 2F, l = 4F;
			for (int sx = -1; sx <= 1; sx += 2) {
				for (int sy = -1; sy <= 1; sy += 2) {
					for (int i = 0; i < 4; i++) {
						float t = g + i * l / 4F;
						Gfx.rrectFree(c, cx + sx * t - 0.75F, cy + sy * t - 0.75F, 1.5F, 1.5F, 0.4F, col);
					}
				}
			}
		}
		if (distance.on() && mc.hitResult instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity) {
			double d = Math.sqrt(mc.player.distanceToSqr(eh.getEntity()));
			String t = String.format(java.util.Locale.ROOT, "%.1f", d);
			var m = c.pose();
			m.pushMatrix();
			m.translate(cx + 7, cy + 5);
			m.scale(0.75F, 0.75F);
			Gfx.textRaw(c, t, 0, 0, d <= 3.0 ? 0xFF4ADE80 : 0xFFE8E8F0);
			m.popMatrix();
		}
	}
}
