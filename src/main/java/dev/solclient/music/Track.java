package dev.solclient.music;

/**
 * One song that some player on this computer (app or browser tab) is playing or has paused. {@code artError} says why the
 * player could not give a cover (empty when there is no problem): it is shown in the Music settings instead of failing silently.
 */
public record Track(String source, String id, String title, String artist, String artUrl, long durationMs, long positionMs,
		boolean playing, Ctl ctl, String artError) {

	public Track(String source, String id, String title, String artist, String artUrl, long durationMs, long positionMs,
			boolean playing, Ctl ctl) {
		this(source, id, title, artist, artUrl, durationMs, positionMs, playing, ctl, "");
	}

	/** What can be done to the player this song came from. */
	public interface Ctl {
		void playPause();

		void next();

		void previous();
	}
}
