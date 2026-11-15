package dev.solclient.mods;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public class ComboCounterMod extends SimpleHudMod {

	private long hitTime = -1;
	private int combo;
	private int possibleTarget = -1;
	private int targetHurtPrev;
	private int selfHurtPrev;

	public ComboCounterMod() {
		super("Combo Counter", "combo_counter", "Display the number of subsequent hits.", 0.01F, 0.36F);
	}

	public void onAttack(Entity victim) {
		possibleTarget = victim.getId();
		targetHurtPrev = victim instanceof LivingEntity le ? le.hurtTime : 0;
	}

	@Override
	public void tick() {
		if (mc.level == null || mc.player == null) {
			return;
		}
		if (possibleTarget != -1) {
			Entity e = mc.level.getEntity(possibleTarget);
			if (e instanceof LivingEntity le) {
				if (le.hurtTime > targetHurtPrev && le.hurtTime > 0) {
					combo++;
					possibleTarget = -1;
					hitTime = System.currentTimeMillis();
				}
				targetHurtPrev = le.hurtTime;
			}
		}
		if (mc.player.hurtTime > selfHurtPrev && mc.player.hurtTime > 0) {
			combo = 0;
		}
		selfHurtPrev = mc.player.hurtTime;
	}

	@Override
	public void renderHud(GuiGraphicsExtractor c, boolean edit) {
		super.renderHud(c, edit);
		if (System.currentTimeMillis() - hitTime > 2000) {
			combo = 0;
		}
	}

	@Override
	public String getText(boolean edit) {
		if (edit || combo == 0) {
			return "No combo";
		}
		return combo + " hit" + (combo > 1 ? "s" : "");
	}
}
