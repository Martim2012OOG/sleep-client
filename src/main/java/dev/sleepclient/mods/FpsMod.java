package dev.sleepclient.mods;

public class FpsMod extends CounterHudMod {
	public FpsMod() {
		super("FPS", "fps", "Display the FPS (frames per second).", 0.01F, 0.01F);
	}

	@Override
	public int getIntValue() {
		return mc.getCurrentFps();
	}

	@Override
	public String getSuffix() {
		return "FPS";
	}
}
