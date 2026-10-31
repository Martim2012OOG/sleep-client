package dev.solclient.mods;

public class SessionTimeMod extends TextHudMod {

	private static final long START = System.currentTimeMillis();

	public SessionTimeMod() {
		super("Session Time", "session_time", "How long you have been playing (since the game opened).", 0.01F, 0.46F);
	}

	@Override
	protected String text(boolean edit) {
		long s = (System.currentTimeMillis() - START) / 1000L;
		return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60) : String.format("%d:%02d", s / 60, s % 60);
	}
}
