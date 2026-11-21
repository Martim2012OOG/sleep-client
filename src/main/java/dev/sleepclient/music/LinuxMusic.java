package dev.sleepclient.music;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Linux: every player that speaks MPRIS (Spotify, browsers, VLC, Rhythmbox...) through playerctl. */
final class LinuxMusic {

	private static final String SEP = "~|~";
	private volatile String hint = "";

	String hint() {
		return hint;
	}

	List<Track> poll() {
		List<Track> out = new ArrayList<>();
		try {
			Sh.Result r = Sh.run(3000, "playerctl", "-a", "metadata", "--format",
					"{{playerName}}" + SEP + "{{status}}" + SEP + "{{title}}" + SEP + "{{artist}}" + SEP + "{{mpris:length}}" + SEP
							+ "{{position}}" + SEP + "{{mpris:artUrl}}");
			hint = "";
			for (String line : r.out().split("\n")) {
				String[] f = line.split(Pattern.quote(SEP), -1);
				if (f.length < 7 || f[2].isEmpty()) {
					continue;
				}
				out.add(new Track(f[0], f[2] + "\u0001" + f[3], f[2], f[3], f[6], Sh.num(f[4]) / 1000, Sh.num(f[5]) / 1000,
						f[1].equals("Playing"), ctl(f[0])));
			}
		} catch (Exception e) {
			hint = "Install playerctl to read what is playing";
		}
		return out;
	}

	private static Track.Ctl ctl(String player) {
		return new Track.Ctl() {
			@Override
			public void playPause() {
				Sh.async(() -> Sh.run(2000, "playerctl", "-p", player, "play-pause"));
			}

			@Override
			public void next() {
				Sh.async(() -> Sh.run(2000, "playerctl", "-p", player, "next"));
			}

			@Override
			public void previous() {
				Sh.async(() -> Sh.run(2000, "playerctl", "-p", player, "previous"));
			}
		};
	}
}
