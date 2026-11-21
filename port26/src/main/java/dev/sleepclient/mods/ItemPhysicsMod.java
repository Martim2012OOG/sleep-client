package dev.sleepclient.mods;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.sleepclient.option.Option;
import net.minecraft.world.phys.AABB;

/**
 * Item Physics (Drop Physics, like Lunar): dropped items fall flat on the ground at a random angle, tumble while they
 * fly and turn slowly in water. Blocks sit on the floor instead of floating. Purely visual.
 */
public class ItemPhysicsMod extends Mod {

	public static ItemPhysicsMod instance;

	private final Option.Bool tumble = bool("Tumble While Falling", true);
	private final Option.Num speed = num("Tumble Speed", 100, 20, 300, 10, "%");
	private final Option.Bool blocksFlat = bool("Blocks Lie On Their Side", false);
	private final Option.Bool water = bool("Float In Water", true);
	private static boolean broken;

	public ItemPhysicsMod() {
		super("Item Physics", "item_physics", "Dropped items lie flat on the ground and tumble when they fall (Drop Physics).", Category.VISUAL);
		instance = this;
	}

	public static boolean on() {
		return instance != null && instance.enabled && !broken;
	}

	public static void broken(Throwable t) {
		if (!broken) {
			broken = true;
			System.err.println("[Sleep] Item Physics turned off: " + t);
		}
	}

	/** Turns the item: called with the matrices already pushed, right before vanilla draws the stack. */
	public static void transform(PoseStack m, AABB box, float age, float offset, boolean onGround, boolean inWater) {
		ItemPhysicsMod mod = instance;
		boolean flat = box.maxZ - box.minZ < 0.2; // a flat item (sword, apple...) and not a block model
		boolean lie = flat || mod.blocksFlat.on();
		float spin = age * 0.35F * mod.speed.f() / 100F; // radians
		if (onGround) {
			if (lie) {
				// lying on its face, at its own random angle (the copies of a stack pile up)
				m.translate(0F, (float) (-box.minZ) + 0.005F, 0F);
				m.mulPose(Axis.YP.rotation(offset));
				m.mulPose(Axis.XN.rotationDegrees(90F));
			} else {
				m.translate(0F, (float) (-box.minY) + 0.001F, 0F);
				m.mulPose(Axis.YP.rotation(offset));
			}
			return;
		}
		float centre = (float) ((box.minY + box.maxY) / 2);
		if (inWater && mod.water.on()) {
			// floating: gently rocking and turning
			m.translate(0F, 0.2F - centre + (float) Math.sin(age / 12F + offset) * 0.04F, 0F);
			m.mulPose(Axis.YP.rotation(offset + age * 0.02F));
			if (lie) {
				m.mulPose(Axis.XN.rotationDegrees(80F + (float) Math.sin(age / 9F + offset) * 8F));
			}
			return;
		}
		m.translate(0F, 0.15F - centre, 0F);
		m.mulPose(Axis.YP.rotation(offset + (mod.tumble.on() ? spin * 0.4F : 0F)));
		if (mod.tumble.on()) {
			m.mulPose(Axis.XP.rotation(spin));
		}
	}
}
