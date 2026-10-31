package dev.solclient.mods;

import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

/** What am I looking at: block or mob name, health of mobs. */
public class WailaMod extends TextHudMod {

	public WailaMod() {
		super("WAILA", "waila", "Shows the name of what you are looking at.", 0.5F, 0.02F);
	}

	@Override
	protected String text(boolean edit) {
		HitResult h = mc.crosshairTarget;
		if (mc.world == null || h == null || h.getType() == HitResult.Type.MISS) return edit ? "Oak Log" : null;
		if (h instanceof EntityHitResult eh) {
			var e = eh.getEntity();
			String s = e.getName().getString();
			if (e instanceof net.minecraft.entity.LivingEntity l) s += "  \u2764 " + String.format("%.0f/%.0f", l.getHealth(), l.getMaxHealth());
			return s;
		}
		if (h instanceof BlockHitResult bh) {
			return mc.world.getBlockState(bh.getBlockPos()).getBlock().getName().getString();
		}
		return null;
	}
}
