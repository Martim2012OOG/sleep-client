package dev.sleepclient.mods;

import dev.sleepclient.option.Option;

/** Hides the HUD when you stand still and shows it again when you move. */
public class AutoHideHudMod extends Mod {

	private final Option.Num seconds = num("Hide After", 8, 2, 60, 1, " s");
	private long lastActive = System.currentTimeMillis();
	private boolean hiddenByUs;
	private double lx, ly, lz;
	private float lyaw, lpitch;

	public AutoHideHudMod() {
		super("Auto-hide HUD", "auto_hide_hud", "Hide the HUD when you stand still.", Category.VISUAL);
	}

	@Override
	public void tick() {
		var p = mc.player;
		if (p == null) return;
		boolean moved = p.getX() != lx || p.getY() != ly || p.getZ() != lz || p.getYaw() != lyaw || p.getPitch() != lpitch || mc.currentScreen != null;
		lx = p.getX(); ly = p.getY(); lz = p.getZ(); lyaw = p.getYaw(); lpitch = p.getPitch();
		long now = System.currentTimeMillis();
		if (moved) {
			lastActive = now;
			if (hiddenByUs) { mc.options.hudHidden = false; hiddenByUs = false; }
		} else if (!hiddenByUs && !mc.options.hudHidden && now - lastActive > seconds.f() * 1000) {
			mc.options.hudHidden = true;
			hiddenByUs = true;
		}
	}

	@Override
	protected void onDisable() {
		if (hiddenByUs) { mc.options.hudHidden = false; hiddenByUs = false; }
	}
}
