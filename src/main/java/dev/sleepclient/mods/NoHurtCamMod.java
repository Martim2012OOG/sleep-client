package dev.sleepclient.mods;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;

/**
 * Removes the screen shake when you take damage.
 * Layer 1 / 2: the tilt method of the game renderer is skipped (HurtCamWeatherMixin, under both of its known names).
 * Layer 3 (does not depend on any method name): while the 3D world is being set up, the camera entity's hurtTime is 0,
 * so there is nothing to tilt; it is given back right after (HurtCamRenderMixin), so the Damage Tint and the combo counter still work.
 */
public class NoHurtCamMod extends Mod {

	public static NoHurtCamMod instance;
	private static LivingEntity held;
	private static int heldHurt;

	public NoHurtCamMod() {
		super("No Hurt Cam", "no_hurt_cam", "Removes the camera shake when you get hit.", Category.VISUAL);
		instance = this;
	}

	public static boolean active() {
		return instance != null && instance.enabled;
	}

	/** Called when the world render starts: the camera entity looks "not hurt" while the view is set up. */
	public static void begin() {
		end(); // never hold two
		if (!active()) {
			return;
		}
		if (MinecraftClient.getInstance().getCameraEntity() instanceof LivingEntity l && l.hurtTime > 0) {
			held = l;
			heldHurt = l.hurtTime;
			l.hurtTime = 0;
		}
	}

	/** Called when the world render ends: gives the real hurtTime back. */
	public static void end() {
		if (held != null) {
			held.hurtTime = heldHurt;
			held = null;
		}
	}
}
