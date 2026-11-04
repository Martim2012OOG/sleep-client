package dev.solclient.mods;

import dev.solclient.option.Option;

/**
 * Input Optimizer: lowers the delay between moving the mouse / pressing a key and seeing it on screen.
 * It does NOT change network ping (that is the round trip to the server and no client mod can shorten it).
 */
public class InputOptimizerMod extends Mod {

	private static InputOptimizerMod instance;
	private final Option.Bool raw = bool("Raw Mouse Input", true).tip("Reads the mouse straight from the hardware (no OS acceleration/smoothing). Less delay and more consistent aim.");
	private final Option.Bool sync = bool("Low Latency Frame Sync", true).tip("Waits for the GPU to finish each frame before reading input again, so the game never shows an old frame. Cuts input delay; can cost a little fps.");
	private final Option.Bool vsync = bool("Disable VSync", false).tip("VSync adds up to a whole frame of delay. Turning it off can show screen tearing.");
	private boolean prevRaw, prevVsync, applied;

	public InputOptimizerMod() {
		super("Input Optimizer", "input_optimizer", "Lower input latency: raw mouse, GPU frame sync and optional VSync off. Does not change network ping.", Category.UTILITY);
		instance = this;
	}

	/** Called at the end of every frame by the render mixin. */
	public static boolean syncFrames() {
		InputOptimizerMod m = instance;
		return m != null && m.isEnabled() && m.sync.on();
	}

	@Override
	public void tick() {
		if (!applied) {
			prevRaw = mc.options.getRawMouseInput().getValue();
			prevVsync = mc.options.getEnableVsync().getValue();
			applied = true;
		}
		if (raw.on() && !mc.options.getRawMouseInput().getValue()) mc.options.getRawMouseInput().setValue(true);
		if (vsync.on() && mc.options.getEnableVsync().getValue()) mc.options.getEnableVsync().setValue(false);
	}

	@Override
	protected void onDisable() {
		if (applied) {
			mc.options.getRawMouseInput().setValue(prevRaw);
			mc.options.getEnableVsync().setValue(prevVsync);
			applied = false;
		}
	}
}
