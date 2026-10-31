package dev.solclient.music;

/** One song that some player on this computer (app or browser tab) is playing or has paused. */
public record Track(String source, String id, String title, String artist, String artUrl, long durationMs, long positionMs,
		boolean playing, Ctl ctl) {

	/** What can be done to the player this song came from. */
	public interface Ctl {
		void playPause();

		void next();

		void previous();
	}
}
