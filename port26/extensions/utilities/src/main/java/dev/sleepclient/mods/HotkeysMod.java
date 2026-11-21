package dev.sleepclient.mods;

import dev.sleepclient.option.Option;
import net.minecraft.client.CameraType;

/** Extra shortcuts: hide the HUD, third person, hide chat. */
public class HotkeysMod extends Mod {

	private final Option.Key hud = key("Hide HUD", -1);
	private final Option.Key third = key("Third Person", -1);
	private final Option.Key front = key("Front View", -1);
	private final Option.Key chat = key("Hide Chat", -1);
	private final boolean[] down = new boolean[4];

	public HotkeysMod() {
		super("Hotkeys", "hotkeys", "Shortcuts: hide HUD, third person, hide chat.", Category.UTILITY);
	}

	private boolean pressed(int i, Option.Key k) {
		boolean d = dev.sleepclient.compat.Mc.screen(mc) == null && k.code() > 0 && dev.sleepclient.Keys.down(k.code());
		boolean edge = d && !down[i];
		down[i] = d;
		return edge;
	}

	@Override
	public void tick() {
		if (mc.player == null) return;
		if (pressed(0, hud)) dev.sleepclient.compat.Mc.setHideGui(mc, !dev.sleepclient.compat.Mc.hideGui(mc));
		if (pressed(1, third)) mc.options.setCameraType(mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK ? CameraType.FIRST_PERSON : CameraType.THIRD_PERSON_BACK);
		if (pressed(2, front)) mc.options.setCameraType(mc.options.getCameraType() == CameraType.THIRD_PERSON_FRONT ? CameraType.FIRST_PERSON : CameraType.THIRD_PERSON_FRONT);
		if (pressed(3, chat)) {
			var v = mc.options.chatVisibility();
			v.set(v.get() == net.minecraft.world.entity.player.ChatVisiblity.HIDDEN ? net.minecraft.world.entity.player.ChatVisiblity.FULL : net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
		}
	}
}
