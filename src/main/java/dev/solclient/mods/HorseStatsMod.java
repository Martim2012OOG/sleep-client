package dev.solclient.mods;

import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.AbstractHorseEntity;

/** Speed, jump height and health of the horse (donkey, mule...) you are riding. */
public class HorseStatsMod extends TextHudMod {

	public HorseStatsMod() {
		super("Horse Stats", "horse_stats", "Speed, jump height and health of the horse you ride.", 0.02F, 0.7F);
	}

	@Override
	protected String text(boolean edit) {
		if (mc.player == null || !(mc.player.getVehicle() instanceof AbstractHorseEntity h)) {
			return edit ? "Speed 9.7 b/s  Jump 4.1 blocks  HP 25" : null;
		}
		double speed = h.getAttributeValue(EntityAttributes.MOVEMENT_SPEED) * 43.17;
		double j = h.getAttributeValue(EntityAttributes.JUMP_STRENGTH);
		double jump = -0.1817584952 * j * j * j + 3.689713992 * j * j + 2.128599134 * j - 0.343930367;
		return String.format("Speed %.1f b/s  Jump %.1f blocks  HP %d", speed, jump, Math.round(h.getHealth()));
	}
}
