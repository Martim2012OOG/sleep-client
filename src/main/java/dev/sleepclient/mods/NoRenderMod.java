package dev.sleepclient.mods;

import dev.sleepclient.option.Option;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;

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
				|| e instanceof ExperienceOrbEntity && m.xp.on()
				|| e instanceof ArmorStandEntity && m.armorStands.on()
				|| e instanceof ItemFrameEntity && m.frames.on()
				|| e instanceof FallingBlockEntity && m.falling.on()
				|| e instanceof PersistentProjectileEntity && m.arrows.on()
				|| e instanceof LightningEntity && m.lightning.on();
	}
}
