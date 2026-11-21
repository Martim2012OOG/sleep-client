package dev.sleepclient.mods;

import dev.sleepclient.option.Option;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;

/** Stops drawing things you don't want to see (less lag, less clutter). */
public class NoRenderMod extends Mod {

	public static NoRenderMod instance;
	private final Option.Bool items = bool("Dropped Items", false);
	private final Option.Bool xp = bool("XP Orbs", true);
	private final Option.Bool armorStands = bool("Armor Stands", false);
	private final Option.Bool frames = bool("Item Frames", false);
	private final Option.Bool falling = bool("Falling Blocks", false);
	private final Option.Bool arrows = bool("Arrows", false);
	private final Option.Bool lightning = bool("Lightning", true);

	public NoRenderMod() {
		super("No Render", "no_render", "Hide dropped items, XP orbs, armor stands, lightning and more.", Category.VISUAL);
		instance = this;
	}

	public static boolean hide(Entity e) {
		NoRenderMod m = instance;
		if (m == null || !m.enabled || e == null) {
			return false;
		}
		return e instanceof ItemEntity && m.items.on()
				|| e instanceof ExperienceOrb && m.xp.on()
				|| e instanceof ArmorStand && m.armorStands.on()
				|| e instanceof ItemFrame && m.frames.on()
				|| e instanceof FallingBlockEntity && m.falling.on()
				|| e instanceof AbstractArrow && m.arrows.on()
				|| e instanceof LightningBolt && m.lightning.on();
	}
}
