package dev.solclient.mods;

import dev.solclient.option.Option;

/** How name tags look (background darkness) and whether Sleep forces them to show when the server hides them. */
public class NameTagStyleMod extends Mod {

	public static NameTagStyleMod instance;

	private final Option.Num background = num("Background", 0, 0, 100, 5, "%");
	private final Option.Bool forceOthers = bool("Always Show Players' Names", false);
	private final Option.Bool forceOwn = bool("Always Show My Own Name", false);
	private Double saved;

	public NameTagStyleMod() {
		super("Name Tag Style", "name_tag_style", "Clean name tags: choose how dark the background is, and force names to show even when the server hides them.", Category.VISUAL);
		instance = this;
		forceOthers.tip("Shows the nickname above other players even if the server hides it (teams that hide name tags, bugged servers). Players who are invisible or in spectator stay hidden.");
		forceOwn.tip("Shows your own name above your head in third person even if the server hides it. Servers that draw their own custom name above you are left alone.");
	}

	/** Show other players' names even when the server hides them. */
	public static boolean forceOthers() {
		return instance != null && instance.enabled && instance.forceOthers.on();
	}

	/** Show your own name even when the server hides it (not on servers that draw their own plate). */
	public static boolean forceOwn() {
		return instance != null && instance.enabled && instance.forceOwn.on();
	}

	@Override
	protected void onEnable() {
		if (mc.options != null) {
			saved = mc.options.textBackgroundOpacity().get();
		}
	}

	@Override
	public void tick() {
		if (mc.options == null) {
			return;
		}
		double want = background.f() / 100.0;
		var o = mc.options.textBackgroundOpacity();
		if (Math.abs(o.get() - want) > 0.001) {
			o.set(want);
		}
	}

	@Override
	protected void onDisable() {
		if (mc.options != null) {
			mc.options.textBackgroundOpacity().set(saved == null ? 0.5 : saved);
		}
	}
}
