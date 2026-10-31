package dev.solclient.mods;

import net.minecraft.entity.LivingEntity;

/** Name and health of the player or mob you are fighting. */
public class TargetHudMod extends TextHudMod {

	public static TargetHudMod instance;
	private LivingEntity target;
	private long until;

	public TargetHudMod() {
		super("Target HUD", "target_hud", "Name and health of who you are fighting.", 0.55F, 0.55F);
		instance = this;
	}

	public void onAttack(net.minecraft.entity.Entity e) {
		if (e instanceof LivingEntity l) {
			target = l;
			until = System.currentTimeMillis() + 6000;
		}
	}

	@Override
	protected String text(boolean edit) {
		LivingEntity t = target;
		if (t == null || System.currentTimeMillis() > until || t.isRemoved()) {
			return edit ? "Steve  \u2764 18.0/20" : null;
		}
		return t.getName().getString() + "  \u2764 " + String.format("%.1f/%.0f", t.getHealth(), t.getMaxHealth());
	}
}
