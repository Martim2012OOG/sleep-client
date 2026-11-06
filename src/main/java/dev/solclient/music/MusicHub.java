package dev.solclient.music;

import dev.solclient.spotify.SpotifyClient;
import java.util.ArrayList;
import java.util.List;

/**
 * "Music": finds what is playing on this computer, whatever plays it (Spotify, YouTube Music in a browser, Apple Music,
 * SoundCloud...), and keeps the one song the HUD shows. A background thread asks the players once a second or so; the game
 * only reads the fields. If your Spotify account is connected (optional) it is one more source, with the best covers.
 */
public final class MusicHub {

	// ---- state read by the HUD
	public static volatile boolean hasTrack, playing;
	public static volatile String title = "", artist = "", artUrl = "", artKey = "", source = "";
	public static volatile long durationMs, progressMs, fetchedAt;
	/** One line for the settings: where the song comes from, or what to do when nothing is found. */
	public static volatile String status = "Open a song in any player";
	/** Why the player gave no cover for the song on screen ("" when it did); see {@link CoverFinder}. */
	public static volatile String playerArtError = "";

	/** The settings line: where the song comes from, plus what is wrong with its cover (if anything). */
	public static String statusLine() {
		String e = CoverFinder.error;
		return e == null || e.isEmpty() || !hasTrack ? status : status + "  |  Cover: " + e;
	}

	private static final Object LOCK = new Object();
	private static volatile boolean running;
	private static volatile int generation;
	private static volatile List<String> extraSites = List.of();
	private static volatile Track.Ctl shown;
	private static String lastId = "";

	private static final MacMusic MAC = new MacMusic();
	private static final WinMusic WIN = new WinMusic();
	private static final LinuxMusic LINUX = new LinuxMusic();
	private static final String OS = System.getProperty("os.name", "").toLowerCase();

	private MusicHub() {
	}

	public static synchronized void start() {
		if (running) {
			return;
		}
		running = true;
		int gen = ++generation;
		Thread t = new Thread(() -> loop(gen), "kubo-music");
		t.setDaemon(true);
		t.start();
	}

	public static synchronized void stop() {
		running = false;
		generation++;
		hasTrack = false;
		shown = null;
		WIN.stop();
		refreshNow();
	}

	/** Extra websites to look at in the browser (comma separated, like "bandcamp.com, radio.example.com"). */
	public static void setExtraSites(String csv) {
		List<String> l = new ArrayList<>();
		if (csv != null) {
			for (String s : csv.split(",")) {
				String d = s.trim().toLowerCase();
				if (!d.isEmpty()) {
					l.add(d);
				}
			}
		}
		if (!l.equals(extraSites)) {
			extraSites = l;
		}
	}

	/** Look again now instead of waiting for the next round. */
	public static void refreshNow() {
		synchronized (LOCK) {
			LOCK.notifyAll();
		}
	}

	private static void loop(int gen) {
		while (running && gen == generation) {
			try {
				round();
			} catch (Throwable t) {
				status = "Could not read the player";
			}
			long wait = OS.contains("mac") ? 1500 : 1000;
			synchronized (LOCK) {
				try {
					LOCK.wait(wait);
				} catch (InterruptedException e) {
					return;
				}
			}
		}
	}

	private static void round() {
		List<Track> all = new ArrayList<>();
		long now = System.currentTimeMillis();
		if (SpotifyClient.connected && SpotifyClient.hasTrack) {
			long pos = SpotifyClient.progressMs + (SpotifyClient.playing ? now - SpotifyClient.fetchedAt : 0);
			all.add(new Track("Spotify", "sp:" + SpotifyClient.artKey, SpotifyClient.title, SpotifyClient.artist, SpotifyClient.artUrl,
					SpotifyClient.durationMs, pos, SpotifyClient.playing, new Track.Ctl() {
						@Override
						public void playPause() {
							SpotifyClient.playPause();
						}

						@Override
						public void next() {
							SpotifyClient.next();
						}

						@Override
						public void previous() {
							SpotifyClient.previous();
						}
					}));
		}
		String hint = "";
		if (OS.contains("mac")) {
			all.addAll(MAC.poll(extraSites));
			hint = MAC.hint();
		} else if (OS.contains("win")) {
			all.addAll(WIN.poll());
			hint = WIN.hint();
		} else {
			all.addAll(LINUX.poll());
			hint = LINUX.hint();
		}
		if (!running) {
			return;
		}

		// what to show: a song that is playing (the same one as before if it still plays), else the one that was shown, else any
		Track pick = null;
		for (Track t : all) {
			if (t.playing() && (pick == null || t.id().equals(lastId) && !pick.id().equals(lastId))) {
				pick = t;
			}
		}
		if (pick == null) {
			for (Track t : all) {
				if (t.id().equals(lastId)) {
					pick = t;
					break;
				}
			}
		}
		if (pick == null && !all.isEmpty()) {
			pick = all.get(0);
		}
		if (pick == null) {
			hasTrack = false;
			shown = null;
			status = hint.isEmpty() ? "Nothing is playing: start a song in any player or browser" : hint;
			return;
		}
		String lastIdBefore = lastId;
		if (!pick.id().equals(lastId)) {
			CoverFinder.error = "";
		}
		playerArtError = pick.artError() == null ? "" : pick.artError();
		lastId = pick.id();
		shown = pick.ctl();
		title = pick.title();
		artist = pick.artist();
		artUrl = pick.artUrl() == null ? "" : pick.artUrl();
		artKey = pick.id() + "#" + artUrl.hashCode();
		durationMs = Math.max(0, pick.durationMs());
		// keep our own clock running smoothly: only jump to the player's position when it really differs (seek, pause,
		// another song); small differences are the player's report being late / rounded, and made the words stutter
		long predicted = progressMs + (playing ? now - fetchedAt : 0);
		long reported = Math.max(0, pick.positionMs());
		boolean sameSong = pick.id().equals(lastIdBefore) && playing == pick.playing();
		progressMs = sameSong && pick.playing() && Math.abs(reported - predicted) < 450 ? predicted + (reported - predicted) / 8 : reported;
		fetchedAt = now;
		playing = pick.playing();
		source = pick.source();
		hasTrack = true;
		status = (pick.playing() ? "Playing from " : "Paused in ") + pick.source() + (hint.isEmpty() ? "" : "  |  " + hint);
	}

	// ------------------------------------------------------------------ controls
	public static void playPause() {
		Track.Ctl c = shown;
		if (c != null) {
			// show the change at once; the next round corrects it if the player did something else
			long now = System.currentTimeMillis();
			progressMs = Math.min(durationMs <= 0 ? Long.MAX_VALUE : durationMs, progressMs + (playing ? now - fetchedAt : 0));
			fetchedAt = now;
			playing = !playing;
			c.playPause();
			later();
		} else if (SpotifyClient.connected) {
			SpotifyClient.playPause();
		}
	}

	public static void next() {
		Track.Ctl c = shown;
		if (c != null) {
			c.next();
			later();
		} else if (SpotifyClient.connected) {
			SpotifyClient.next();
		}
	}

	public static void previous() {
		Track.Ctl c = shown;
		if (c != null) {
			c.previous();
			later();
		} else if (SpotifyClient.connected) {
			SpotifyClient.previous();
		}
	}

	// ------------------------------------------------------------------ ducking (crate sounds)
	private static final java.util.Map<String, String> DUCKED = new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * Lowers (or gives back) the volume of the music player while a loud sound plays in the game.
	 * macOS: Spotify and the Music app, through their own volume. Linux: Spotify through playerctl.
	 * Windows has no per-app volume that can be set without extra programs, so there the player stays as it is.
	 */
	public static void duck(boolean on) {
		Sh.async(() -> {
			if (OS.contains("mac")) {
				for (String app : new String[]{"Spotify", "Music"}) {
					try {
						if (on) {
							Sh.Result r = Sh.run(4000, "/usr/bin/osascript", "-e",
									"if application \"" + app + "\" is running then tell application \"" + app + "\" to return sound volume");
							String v = r.out() == null ? "" : r.out().trim();
							if (!v.isEmpty() && v.matches("\\d+") && !DUCKED.containsKey(app)) {
								DUCKED.put(app, v);
								int low = Math.max(0, Integer.parseInt(v) / 8);
								Sh.run(4000, "/usr/bin/osascript", "-e", "tell application \"" + app + "\" to set sound volume to " + low);
							}
						} else {
							String v = DUCKED.remove(app);
							if (v != null) {
								Sh.run(4000, "/usr/bin/osascript", "-e", "if application \"" + app + "\" is running then tell application \"" + app + "\" to set sound volume to " + v);
							}
						}
					} catch (Exception ignored) {
						// the player is not there: nothing to lower
					}
				}
			} else if (OS.contains("linux")) {
				try {
					if (on) {
						Sh.Result r = Sh.run(3000, "playerctl", "-p", "spotify", "volume");
						String v = r.out() == null ? "" : r.out().trim();
						if (r.code() == 0 && !v.isEmpty() && !DUCKED.containsKey("spotify")) {
							DUCKED.put("spotify", v);
							Sh.run(3000, "playerctl", "-p", "spotify", "volume", String.valueOf(Math.max(0F, Float.parseFloat(v) / 8F)));
						}
					} else {
						String v = DUCKED.remove("spotify");
						if (v != null) {
							Sh.run(3000, "playerctl", "-p", "spotify", "volume", v);
						}
					}
				} catch (Exception ignored) {
					// no playerctl / no Spotify
				}
			}
		});
	}

	private static void later() {
		Sh.async(() -> {
			Thread.sleep(600);
			refreshNow();
		});
	}
}
