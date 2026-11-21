package dev.sleepclient.video;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Open Apps: every app of this computer on a floating panel, live, and you can use it (click, scroll, type).
 *
 * macOS (v102): the windows come from CoreGraphics (every app with a window, no Accessibility permission needed just
 * to list them; the old System Events list only ever showed Java). Each window is captured on its own with
 * {@code screencapture -l <window>}, which also works when Minecraft is in front of it (the old whole-screen capture
 * just showed whatever was on top). Clicks / keys go to that app's process ({@link AppControl}).
 * Apps that are installed but not open are listed too: picking one opens it first.
 *
 * Windows: gdigrab by window title + input with PostMessage. Linux (X11): x11grab + xdotool.
 */
public final class AppMirror {

	/**
	 * One thing that can be shown. input/filter: ffmpeg capture (Windows / Linux / whole screen); pid + window: the
	 * window (macOS capture, input); x y w h: where it is on the screen (points); app: what to open when it is not
	 * running yet (installed = not open now).
	 */
	public record Source(String label, List<String> input, String filter, long pid, long window, double x, double y, double w, double h,
			String app, boolean installed) {

		public Source(String label, List<String> input, String filter) {
			this(label, input, filter, 0, 0, 0, 0, 0, 0, null, false);
		}

		/** Can be clicked / typed into. */
		public boolean controllable() {
			return pid > 0 || window > 0;
		}
	}

	private static final String OS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
	public static final boolean MAC = OS.contains("mac"), WINDOWS = OS.contains("win");
	public static volatile String problem = "";

	private AppMirror() {
	}

	/** Open windows first, then the installed apps that are not open. Slow: call from a worker thread. */
	public static List<Source> list() {
		problem = "";
		List<Source> out = new ArrayList<>();
		try {
			if (MAC) {
				Source screen = macScreen();
				if (screen != null) {
					out.add(screen);
				}
				out.addAll(macWindows());
				Set<String> open = new HashSet<>();
				for (Source s : out) {
					open.add(s.app() == null ? "" : s.app().toLowerCase(Locale.ROOT));
				}
				for (Source s : macInstalled()) {
					if (!open.contains(s.app().toLowerCase(Locale.ROOT))) {
						out.add(s);
					}
				}
				if (out.isEmpty()) {
					problem = "No apps found. Allow the launcher in System Settings > Privacy > Screen Recording";
				}
			} else if (WINDOWS) {
				out.add(new Source("Whole screen", List.of("-f", "gdigrab", "-framerate", "30", "-i", "desktop"), null));
				for (String t : run(List.of("powershell", "-NoProfile", "-Command",
						"Get-Process | Where-Object { $_.MainWindowTitle } | ForEach-Object { \"$($_.Id)|$($_.MainWindowHandle)|$($_.ProcessName)|$($_.MainWindowTitle)\" }"))) {
					String[] f = t.split("\\|", 4);
					if (f.length < 4 || f[3].isBlank()) {
						continue;
					}
					try {
						long pid = Long.parseLong(f[0].trim()), hwnd = Long.parseLong(f[1].trim());
						String title = f[3].trim();
						out.add(new Source(title, List.of("-f", "gdigrab", "-framerate", "30", "-i", "title=" + title), null, pid, hwnd, 0, 0, 0, 0,
								f[2].trim(), false));
					} catch (NumberFormatException ignored) {
					}
				}
				out.addAll(windowsInstalled());
			} else {
				String display = System.getenv().getOrDefault("DISPLAY", ":0.0");
				out.add(new Source("Whole screen", List.of("-f", "x11grab", "-framerate", "30", "-i", display), null));
				for (String line : run(List.of("wmctrl", "-lpG"))) {
					// id desktop pid x y w h host title...
					String[] f = line.trim().split("\\s+", 9);
					if (f.length < 9) {
						continue;
					}
					try {
						long win = Long.decode(f[0]), pid = Long.parseLong(f[2]);
						int x = Integer.parseInt(f[3]), y = Integer.parseInt(f[4]), w = Integer.parseInt(f[5]) & ~1, h = Integer.parseInt(f[6]) & ~1;
						if (w < 40 || h < 40) {
							continue;
						}
						out.add(new Source(f[8], List.of("-f", "x11grab", "-framerate", "30", "-video_size", w + "x" + h, "-i",
								display + "+" + Math.max(0, x) + "," + Math.max(0, y)), null, pid, win, x, y, w, h, null, false));
					} catch (NumberFormatException ignored) {
					}
				}
				if (out.size() == 1) {
					problem = "Install wmctrl and xdotool to pick and use single windows (X11 only)";
				}
			}
		} catch (Exception e) {
			problem = "Could not list the apps: " + e.getMessage();
		}
		return out;
	}

	// ------------------------------------------------------------------ macOS
	/** Every normal window on screen (layer 0, a real size), via CoreGraphics from JavaScript for Automation. */
	static List<Source> macWindows() {
		String js = "ObjC.import('CoreGraphics');ObjC.import('Foundation');"
				+ "var l=ObjC.deepUnwrap(ObjC.castRefToObject($.CGWindowListCopyWindowInfo(17,0)));"
				+ "var o=[];(l||[]).forEach(function(w){var b=w.kCGWindowBounds||{};if(w.kCGWindowLayer===0&&b.Width>80&&b.Height>60){"
				+ "o.push([w.kCGWindowOwnerPID,w.kCGWindowNumber,b.X,b.Y,b.Width,b.Height,(w.kCGWindowOwnerName||''),(w.kCGWindowName||'')].join('\\t'));}});o.join('\\n');";
		List<Source> out = new ArrayList<>();
		for (String line : run(List.of("osascript", "-l", "JavaScript", "-e", js))) {
			String[] f = line.split("\t", 8);
			if (f.length < 7) {
				continue;
			}
			try {
				long pid = (long) Double.parseDouble(f[0]), win = (long) Double.parseDouble(f[1]);
				double x = Double.parseDouble(f[2]), y = Double.parseDouble(f[3]), w = Double.parseDouble(f[4]), h = Double.parseDouble(f[5]);
				String owner = f[6].trim(), title = f.length > 7 ? f[7].trim() : "";
				if (owner.equalsIgnoreCase("java") || owner.toLowerCase(Locale.ROOT).contains("minecraft") || pid == ProcessHandle.current().pid()) {
					continue; // not Minecraft itself
				}
				String label = title.isEmpty() || title.equalsIgnoreCase(owner) ? owner : owner + " - " + title;
				out.add(new Source(label, List.of(), null, pid, win, x, y, w, h, owner, false));
			} catch (NumberFormatException ignored) {
			}
		}
		return out;
	}

	/** Apps in /Applications, /System/Applications (and their Utilities) and ~/Applications. */
	static List<Source> macInstalled() {
		List<Source> out = new ArrayList<>();
		String home = System.getProperty("user.home", "");
		for (String dir : new String[] {"/Applications", "/Applications/Utilities", "/System/Applications", "/System/Applications/Utilities", home + "/Applications"}) {
			File[] apps = new File(dir).listFiles((d, n) -> n.endsWith(".app"));
			if (apps == null) {
				continue;
			}
			for (File a : apps) {
				String name = a.getName().substring(0, a.getName().length() - 4);
				out.add(new Source(name, List.of(), null, 0, 0, 0, 0, 0, 0, a.getAbsolutePath(), true));
			}
		}
		out.sort((a, b) -> a.label().compareToIgnoreCase(b.label()));
		return out;
	}

	/** Opens an app that is not running and waits (up to 12 s) for its first window. Worker thread. */
	public static Source launch(Source s) {
		try {
			if (MAC) {
				new ProcessBuilder("open", "-g", s.app()).start(); // -g: open it without taking Minecraft's place
				String name = new File(s.app()).getName().replace(".app", "");
				for (int i = 0; i < 48; i++) {
					Thread.sleep(250);
					for (Source w : macWindows()) {
						if (w.app() != null && w.app().equalsIgnoreCase(name)) {
							return w;
						}
					}
				}
			} else if (WINDOWS) {
				new ProcessBuilder("cmd", "/c", "start", "", s.app()).start();
				for (int i = 0; i < 48; i++) {
					Thread.sleep(250);
					for (Source w : list()) {
						if (!w.installed() && w.label().toLowerCase(Locale.ROOT).contains(s.label().toLowerCase(Locale.ROOT))) {
							return w;
						}
					}
				}
			}
		} catch (Exception e) {
			problem = "Could not open " + s.label() + ": " + e.getMessage();
		}
		return null;
	}

	/** Where a running window is now (it may have moved): {x, y, w, h} in points, or null. */
	public static double[] bounds(Source s) {
		if (!MAC) {
			return s.w() > 0 ? new double[] {s.x(), s.y(), s.w(), s.h()} : null;
		}
		for (Source w : macWindows()) {
			if (w.window() == s.window()) {
				return new double[] {w.x(), w.y(), w.w(), w.h()};
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ Windows
	static List<Source> windowsInstalled() {
		List<Source> out = new ArrayList<>();
		String pd = System.getenv().getOrDefault("ProgramData", "C:\\ProgramData"), ad = System.getenv().getOrDefault("APPDATA", "");
		for (String dir : new String[] {pd + "\\Microsoft\\Windows\\Start Menu\\Programs", ad + "\\Microsoft\\Windows\\Start Menu\\Programs"}) {
			collectLinks(new File(dir), out, 0);
		}
		out.sort((a, b) -> a.label().compareToIgnoreCase(b.label()));
		return out;
	}

	private static void collectLinks(File dir, List<Source> out, int depth) {
		File[] fs = dir.listFiles();
		if (fs == null || depth > 2) {
			return;
		}
		for (File f : fs) {
			if (f.isDirectory()) {
				collectLinks(f, out, depth + 1);
			} else if (f.getName().toLowerCase(Locale.ROOT).endsWith(".lnk") && !f.getName().toLowerCase(Locale.ROOT).contains("uninstall")) {
				out.add(new Source(f.getName().substring(0, f.getName().length() - 4), List.of(), null, 0, 0, 0, 0, 0, 0, f.getAbsolutePath(), true));
			}
		}
	}

	// ------------------------------------------------------------------ whole screen (macOS, ffmpeg)
	/** The whole screen on a Mac (ffmpeg avfoundation): only used for "Whole screen". */
	static Source macScreen() {
		String ff = dev.sleepclient.fightcam.Ffmpeg.findPath();
		if (ff == null) {
			return null;
		}
		Pattern p = Pattern.compile("\\[(\\d+)] Capture screen 0");
		for (String l : run(List.of(ff, "-hide_banner", "-f", "avfoundation", "-list_devices", "true", "-i", ""))) {
			Matcher m = p.matcher(l);
			if (m.find()) {
				return new Source("Whole screen", List.of("-f", "avfoundation", "-capture_cursor", "1", "-framerate", "30", "-i", m.group(1) + ":none"), null);
			}
		}
		return null;
	}

	static List<String> run(List<String> cmd) {
		List<String> lines = new ArrayList<>();
		try {
			Process p = new ProcessBuilder(cmd).redirectErrorStream(false).start();
			try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
				String l;
				while ((l = r.readLine()) != null) {
					lines.add(l);
				}
			}
			p.waitFor(10, TimeUnit.SECONDS);
		} catch (Exception ignored) {
		}
		return lines;
	}
}
