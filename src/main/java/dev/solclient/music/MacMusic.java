package dev.solclient.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * macOS: reads what is playing without any extra install.
 * <ul>
 * <li>Spotify app and Music (Apple Music) app: asked directly with AppleScript.</li>
 * <li>Browser tabs (Chrome, Brave, Edge, Arc, Vivaldi, Opera, Safari): on music sites (YouTube Music, Spotify Web, Apple
 * Music, SoundCloud...) the page's own media info ({@code navigator.mediaSession}) is read, so title, artist, cover and
 * paused / playing are the real ones. Needs one switch in the browser: Allow JavaScript from Apple Events.</li>
 * </ul>
 * Apps that are not running are never touched (AppleScript would open them).
 */
final class MacMusic {

	private static final String SEP = "~|~";
	private static final String[] CHROMIUM = {"Google Chrome", "Brave Browser", "Microsoft Edge", "Arc", "Vivaldi", "Opera",
			"Chromium", "Google Chrome Canary"};
	static final String[] SITES = {"music.youtube.com", "youtube.com", "open.spotify.com", "music.apple.com", "soundcloud.com",
			"deezer.com", "tidal.com", "bandcamp.com", "pandora.com", "music.amazon.", "audiomack.com", "mixcloud.com",
			"jiosaavn.com", "music.yandex.", "qobuz.com", "napster.com", "anghami.com"};

	/** Runs in the page: title / artist / cover / state / position / length of whatever the site tells the system it plays. */
	private static final String JS_INFO = "(function(){try{var ms=navigator.mediaSession,m=ms&&ms.metadata;"
			+ "if(!m||!m.title)return '';var e=document.querySelector('video,audio');var a='';"
			+ "if(m.artwork&&m.artwork.length){a=m.artwork[m.artwork.length-1].src||''}"
			+ "var st=ms.playbackState;if(st!=='playing'&&st!=='paused'){st=e?(e.paused?'paused':'playing'):'paused'}"
			+ "var d=0;if(e&&isFinite(e.duration))d=Math.round(e.duration*1000);"
			+ "return [st,m.title,m.artist||m.album||'',a,e?Math.round(e.currentTime*1000):0,d].join('" + SEP + "')}catch(x){return ''}})()";

	/** Clicks the site's own button (so the page stays in sync); falls back to the media element. %IDX%: 0 play/pause, 1 next, 2 previous. */
	private static final String JS_CTL = "(function(){var i=%IDX%;var h=location.hostname.replace(/^www\\./,'');"
			+ "var s={'music.youtube.com':['#play-pause-button','.next-button','.previous-button'],"
			+ "'youtube.com':['.ytp-play-button','.ytp-next-button',''],"
			+ "'open.spotify.com':['[data-testid=\"control-button-playpause\"]','[data-testid=\"control-button-skip-forward\"]','[data-testid=\"control-button-skip-back\"]'],"
			+ "'soundcloud.com':['.playControl','.skipControl__next','.skipControl__previous']}[h];"
			+ "var q=s&&s[i];var b=q&&document.querySelector(q);if(b){b.click();return 'ok'}"
			+ "var e=document.querySelector('video,audio');if(e&&i===0){e.paused?e.play():e.pause()}return 'ok'})()";

	private final Map<String, String> artCache = new HashMap<>();
	private volatile String hint = "";

	/** A problem the player can fix (a permission, a browser switch), shown in the settings. Empty when all is fine. */
	String hint() {
		return hint;
	}

	List<Track> poll(List<String> extraSites) {
		List<Track> out = new ArrayList<>();
		Set<String> run = running();
		if (run.isEmpty()) {
			return out;
		}
		String err = "";
		if (run.contains("Spotify")) {
			spotify(out);
		}
		if (run.contains("Music")) {
			music(out);
		}
		List<String> sites = new ArrayList<>(List.of(SITES));
		sites.addAll(extraSites);
		for (String b : CHROMIUM) {
			if (run.contains(b)) {
				err = browser(b, false, sites, out, err);
			}
		}
		if (run.contains("Safari")) {
			err = browser("Safari", true, sites, out, err);
		}
		hint = err;
		return out;
	}

	private Set<String> running() {
		Set<String> set = new HashSet<>();
		try {
			Sh.Result r = Sh.run(3000, "/bin/ps", "-axo", "comm=");
			for (String line : r.out().split("\n")) {
				int i = line.lastIndexOf("/MacOS/");
				if (i >= 0) {
					set.add(line.substring(i + 7).trim());
				}
			}
		} catch (Exception ignored) {
		}
		return set;
	}

	private static Sh.Result osa(String script) {
		try {
			return Sh.run(6000, "/usr/bin/osascript", "-e", script);
		} catch (Exception e) {
			return new Sh.Result("", String.valueOf(e), -1);
		}
	}

	// ------------------------------------------------------------------ native apps
	private void spotify(List<Track> out) {
		String s = "tell application \"Spotify\"\ntry\nif player state is stopped then return \"\"\nset t to current track\n"
				+ "set p to \"0\"\nif player state is playing then set p to \"1\"\n"
				+ "return p & \"" + SEP + "\" & (name of t) & \"" + SEP + "\" & (artist of t) & \"" + SEP + "\" & (duration of t) & \""
				+ SEP + "\" & (player position) & \"" + SEP + "\" & (artwork url of t)\non error\nreturn \"\"\nend try\nend tell";
		String[] f = osa(s).out().trim().split(java.util.regex.Pattern.quote(SEP), -1);
		if (f.length < 6 || f[1].isEmpty()) {
			return;
		}
		out.add(new Track("Spotify", f[1] + "\u0001" + f[2], f[1], f[2], f[5], Sh.num(f[3]), Sh.ms(f[4]), f[0].equals("1"),
				appCtl("Spotify")));
	}

	private void music(List<Track> out) {
		String s = "tell application \"Music\"\ntry\nif player state is stopped then return \"\"\nset t to current track\n"
				+ "set p to \"0\"\nif player state is playing then set p to \"1\"\n"
				+ "return p & \"" + SEP + "\" & (name of t) & \"" + SEP + "\" & (artist of t) & \"" + SEP + "\" & (duration of t) & \""
				+ SEP + "\" & (player position)\non error\nreturn \"\"\nend try\nend tell";
		String[] f = osa(s).out().trim().split(java.util.regex.Pattern.quote(SEP), -1);
		if (f.length < 5 || f[1].isEmpty()) {
			return;
		}
		out.add(new Track("Apple Music", f[1] + "\u0001" + f[2], f[1], f[2], art(f[1], f[2]), Sh.num(f[3]) * 1000L,
				Sh.ms(f[4]), f[0].equals("1"), appCtl("Music")));
	}

	private static Track.Ctl appCtl(String app) {
		return new Track.Ctl() {
			@Override
			public void playPause() {
				Sh.async(() -> osa("tell application \"" + app + "\" to playpause"));
			}

			@Override
			public void next() {
				Sh.async(() -> osa("tell application \"" + app + "\" to next track"));
			}

			@Override
			public void previous() {
				Sh.async(() -> osa("tell application \"" + app + "\" to previous track"));
			}
		};
	}

	/** The Music app gives no cover link, so it is looked up once per song in Apple's public search. */
	private String art(String title, String artist) {
		String key = title + "\u0001" + artist;
		String cached = artCache.get(key);
		if (cached != null) {
			return cached;
		}
		String url = "";
		String body = Sh.get("https://itunes.apple.com/search?media=music&entity=song&limit=1&term="
				+ URLEncoder.encode(title + " " + artist, StandardCharsets.UTF_8));
		try {
			if (body != null) {
				JsonArray results = JsonParser.parseString(body).getAsJsonObject().getAsJsonArray("results");
				if (results != null && results.size() > 0) {
					JsonObject r = results.get(0).getAsJsonObject();
					if (r.has("artworkUrl100")) {
						url = r.get("artworkUrl100").getAsString().replace("100x100", "300x300");
					}
				}
			}
		} catch (Exception ignored) {
		}
		if (artCache.size() > 200) {
			artCache.clear();
		}
		artCache.put(key, url);
		return url;
	}

	// ------------------------------------------------------------------ browsers
	private String browser(String app, boolean safari, List<String> sites, List<Track> out, String err) {
		StringBuilder cond = new StringBuilder();
		for (String s : sites) {
			String d = s.trim().toLowerCase();
			if (d.isEmpty() || d.contains("\"") || d.contains("\\")) {
				continue;
			}
			if (cond.length() > 0) {
				cond.append(" or ");
			}
			cond.append("u contains \"").append(d).append("\"");
		}
		if (cond.length() == 0) {
			return err;
		}
		StringBuilder sb = new StringBuilder();
		sb.append("set res to \"\"\ntell application \"").append(app).append("\"\nrepeat with w in windows\ntry\n");
		sb.append("set urls to URL of tabs of w\nset wid to id of w\n");
		sb.append("repeat with i from 1 to count of urls\ntry\nset u to item i of urls\nif ").append(cond).append(" then\n");
		sb.append("set tb to tab i of w\n");
		if (safari) {
			sb.append("set r to do JavaScript \"").append(asq(JS_INFO)).append("\" in tb\n");
			sb.append("if r is not \"\" then set res to res & wid & \":\" & i & \"").append(SEP).append("\" & r & linefeed\n");
		} else {
			sb.append("set r to execute tb javascript \"").append(asq(JS_INFO)).append("\"\n");
			sb.append("if r is not \"\" then set res to res & wid & \":\" & (id of tb) & \"").append(SEP).append("\" & r & linefeed\n");
		}
		sb.append("end if\non error m\nset res to res & \"ERR").append(SEP).append("\" & m & linefeed\nend try\nend repeat\n");
		sb.append("end try\nend repeat\nend tell\nreturn res");
		Sh.Result r = osa(sb.toString());
		String all = r.out() + "\n" + r.err();
		if (all.contains("-1743") || all.contains("not authorized") || all.contains("not allowed assistive")) {
			return "Allow Sleep / Java to control " + app + " (System Settings > Privacy & Security > Automation)";
		}
		for (String line : r.out().split("\n")) {
			line = line.trim();
			if (line.isEmpty()) {
				continue;
			}
			if (line.startsWith("ERR")) {
				if (line.contains("JavaScript") && err.isEmpty()) {
					err = safari ? "Safari: Develop menu > Allow JavaScript from Apple Events"
							: app + ": View > Developer > Allow JavaScript from Apple Events";
				}
				continue;
			}
			String[] f = line.split(java.util.regex.Pattern.quote(SEP), -1);
			if (f.length < 7) {
				continue;
			}
			String ids = f[0];
			String state = f[1], title = f[2], artist = f[3], art = f[4];
			if (!art.startsWith("http")) {
				art = "";
			}
			out.add(new Track(app, title + "\u0001" + artist, title, artist, art, Sh.num(f[6]), Sh.num(f[5]), state.equals("playing"),
					tabCtl(app, safari, ids)));
		}
		return err;
	}

	/** Text for inside an AppleScript string: backslashes and double quotes escaped. */
	private static String asq(String js) {
		return js.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private static Track.Ctl tabCtl(String app, boolean safari, String ids) {
		String[] p = ids.split(":");
		if (p.length < 2) {
			return null;
		}
		return new Track.Ctl() {
			private void send(int idx) {
				String js = asq(JS_CTL.replace("%IDX%", String.valueOf(idx)));
				String target = safari ? "tab " + p[1] + " of window id " + p[0] : "tab id " + p[1] + " of window id " + p[0];
				String s = safari ? "tell application \"" + app + "\" to do JavaScript \"" + js + "\" in " + target
						: "tell application \"" + app + "\" to execute (" + target + ") javascript \"" + js + "\"";
				Sh.async(() -> osa(s));
			}

			@Override
			public void playPause() {
				send(0);
			}

			@Override
			public void next() {
				send(1);
			}

			@Override
			public void previous() {
				send(2);
			}
		};
	}
}
