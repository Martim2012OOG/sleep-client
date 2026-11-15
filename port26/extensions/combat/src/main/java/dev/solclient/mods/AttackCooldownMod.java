package dev.solclient.mods;

/** How charged your next hit is. */
public class AttackCooldownMod extends TextHudMod {

	public AttackCooldownMod() {
		super("Attack Cooldown", "attack_cooldown", "Shows how charged your next hit is.", 0.5F, 0.6F);
	}

	@Override
	protected String text(boolean edit) {
		if (mc.player == null) return edit ? "Attack: 100%" : null;
		float f = mc.player.getAttackStrengthScale(0F);
		int bars = Math.round(f * 10);
		return "Attack " + "|".repeat(bars) + ".".repeat(10 - bars) + " " + Math.round(f * 100) + "%";
	}
}
