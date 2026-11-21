package dev.sleepclient.mods;

import java.text.DecimalFormat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public class ReachDisplayMod extends SimpleHudMod {

	private static final DecimalFormat FORMAT = new DecimalFormat("0.##");
	private double distance;
	private long hitTime = -1;

	public ReachDisplayMod() {
		super("Reach Display", "reach_display", "Display your reach when hitting entities.", 0.01F, 0.29F);
	}

	public void onAttack(Entity victim) {
		if (mc.player == null) {
			return;
		}
		HitResult hit = mc.hitResult;
		if (hit instanceof EntityHitResult ehr) {
			distance = ehr.getLocation().distanceTo(mc.player.getEyePosition());
			hitTime = System.currentTimeMillis();
		}
	}

	@Override
	public String getText(boolean edit) {
		if (System.currentTimeMillis() - hitTime > 5000) {
			distance = 0;
		}
		if (edit) {
			return "0 mts";
		}
		return FORMAT.format(distance) + " m" + (distance != 1.0 ? "ts" : "");
	}
}
