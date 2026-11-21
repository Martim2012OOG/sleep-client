package dev.sleepclient.mods;

import dev.sleepclient.option.Option;
import net.minecraft.client.option.Perspective;

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
		boolean d = mc.currentScreen == null && k.code() > 0 && dev.sleepclient.Keys.down(k.code());
		boolean edge = d && !down[i];
		down[i] = d;
		return edge;
	}

	@Override
	public void tick() {
		if (mc.player == null) return;
		if (pressed(0, hud)) mc.options.hudHidden = !mc.options.hudHidden;
		if (pressed(1, third)) mc.options.setPerspective(mc.options.getPerspective() == Perspective.THIRD_PERSON_BACK ? Perspective.FIRST_PERSON : Perspective.THIRD_PERSON_BACK);
		if (pressed(2, front)) mc.options.setPerspective(mc.options.getPerspective() == Perspective.THIRD_PERSON_FRONT ? Perspective.FIRST_PERSON : Perspective.THIRD_PERSON_FRONT);
		if (pressed(3, chat)) {
			var v = mc.options.getChatVisibility();
			v.setValue(v.getValue() == net.minecraft.network.message.ChatVisibility.HIDDEN ? net.minecraft.network.message.ChatVisibility.FULL : net.minecraft.network.message.ChatVisibility.HIDDEN);
		}
	}
}
