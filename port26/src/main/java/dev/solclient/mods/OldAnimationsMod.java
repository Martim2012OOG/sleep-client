package dev.solclient.mods;

/** Items held like in 1.7: a bit lower, closer and bigger. */
public class OldAnimationsMod extends Mod {

	public static OldAnimationsMod instance;

	public OldAnimationsMod() {
		super("1.7 Animations", "1_7_visuals", "Hold items like in Minecraft 1.7.", Category.VISUAL);
		instance = this;
	}

	public static boolean on() {
		return instance != null && instance.enabled;
	}
}
