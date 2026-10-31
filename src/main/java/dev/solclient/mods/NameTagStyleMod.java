package dev.solclient.mods;

import dev.solclient.option.Option;

/** How name tags look: background darkness (also applies to text backgrounds). */
public class NameTagStyleMod extends Mod {

	private final Option.Num background = num("Background", 0, 0, 100, 5, "%");
	private Double saved;

	public NameTagStyleMod() {
		super("Name Tag Style", "name_tag_style", "Clean name tags: choose how dark the background is.", Category.VISUAL);
	}

	@Override
	protected void onEnable() {
		saved = mc.options.getTextBackgroundOpacity().getValue();
	}

	@Override
	public void tick() {
		double want = background.f() / 100.0;
		var o = mc.options.getTextBackgroundOpacity();
		if (Math.abs(o.getValue() - want) > 0.001) o.setValue(want);
	}

	@Override
	protected void onDisable() {
		mc.options.getTextBackgroundOpacity().setValue(saved == null ? 0.5 : saved);
	}
}
