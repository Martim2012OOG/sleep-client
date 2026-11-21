package dev.sleepclient.mods;

import dev.sleepclient.Keys;
import dev.sleepclient.option.Option;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.MathHelper;

public class FreelookMod extends Mod {

	public static FreelookMod instance;
	public static boolean active;
	public static float yaw, pitch;

	private final Option.Key key = key("Key", 86);
	private final Option.Choice perspective = choice("Perspective", 1, "First Person", "Third Person Back",
			"Third Person Front");
	private final Option.Bool invertPitch = bool("Vertically Invert", false);
	private final Option.Bool invertYaw = bool("Horizontally Invert", false);
	private Perspective previous = Perspective.FIRST_PERSON;

	public FreelookMod() {
		super("Freelook", "freelook", "Unlock the camera rotation.", Category.UTILITY);
		instance = this;
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}

	private Perspective chosen() {
		switch (perspective.idx()) {
			case 0: return Perspective.FIRST_PERSON;
			case 2: return Perspective.THIRD_PERSON_FRONT;
			default: return Perspective.THIRD_PERSON_BACK;
		}
	}

	@Override
	protected void onDisable() {
		if (active) {
			stop();
		}
	}

	@Override
	public void tick() {
		boolean down = mc.currentScreen == null && mc.player != null && Keys.down(key.code());
		if (down && !active) {
			start();
		} else if (!down && active) {
			stop();
		}
	}

	private void start() {
		active = true;
		previous = mc.options.getPerspective();
		mc.options.setPerspective(chosen());
		yaw = mc.player.getYaw();
		pitch = mc.player.getPitch();
	}

	private void stop() {
		active = false;
		mc.options.setPerspective(previous);
	}

	/** Called from EntityMixin with the already sensitivity-scaled mouse deltas. */
	public static boolean rotate(double dx, double dy) {
		if (!active || instance == null) {
			return false;
		}
		float ny = (float) (instance.invertPitch.on() ? -dy : dy);
		float nx = (float) (instance.invertYaw.on() ? -dx : dx);
		yaw += nx * 0.15F;
		pitch = MathHelper.clamp(pitch + ny * 0.15F, -90F, 90F);
		return true;
	}
}
