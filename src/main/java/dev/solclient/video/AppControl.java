package dev.solclient.video;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sends your clicks, scrolls and keys to an app shown on a panel (Open Apps). One small helper process stays open and
 * gets one line per event, so it is fast:
 *  - macOS: JavaScript for Automation posting CoreGraphics events straight to the app's process (CGEventPostToPid),
 *    so the app does not have to come in front of Minecraft. Needs Accessibility permission for the launcher
 *    (System Settings > Privacy & Security > Accessibility).
 *  - Windows: PowerShell + user32 PostMessage to the window (and the control under the mouse).
 *  - Linux (X11): xdotool.
 * Positions are fractions of the window (0..1), turned into screen points here.
 */
public final class AppControl {

	private static Process helper;
	private static BufferedWriter out;
	private static final Map<Long, double[]> BOUNDS = new ConcurrentHashMap<>();
	private static final Map<Long, Long> BOUNDS_AT = new ConcurrentHashMap<>();
	public static volatile String problem = "";

	private AppControl() {
	}

	// ------------------------------------------------------------------ API (render thread; never blocks)
	/** type: "down" / "up" / "move" / "drag"; button 0 left 1 right 2 middle. u, v: 0..1 inside the window. */
	public static void mouse(AppMirror.Source s, String type, int button, double u, double v, int clicks) {
		double[] p = point(s, u, v);
		if (p == null) {
			return;
		}
		if (AppMirror.MAC) {
			int t = switch (type) {
				case "down" -> button == 1 ? 3 : button == 2 ? 25 : 1;
				case "up" -> button == 1 ? 4 : button == 2 ? 26 : 2;
				case "drag" -> button == 1 ? 7 : button == 2 ? 27 : 6;
				default -> 5;
			};
			send("m\t" + s.pid() + "\t" + t + "\t" + fmt(p[0]) + "\t" + fmt(p[1]) + "\t" + button + "\t" + Math.max(1, clicks) + "\t" + s.window());
		} else if (AppMirror.WINDOWS) {
			send("m\t" + s.window() + "\t" + type + "\t" + button + "\t" + fmt(u) + "\t" + fmt(v));
		} else if (type.equals("down")) {
			linux(s, "mousemove", "--window", String.valueOf(s.window()), String.valueOf((int) (u * s.w())), String.valueOf((int) (v * s.h())), "click",
					String.valueOf(button == 1 ? 3 : button == 2 ? 2 : 1));
		}
	}

	public static void scroll(AppMirror.Source s, double u, double v, double lines) {
		double[] p = point(s, u, v);
		if (p == null) {
			return;
		}
		if (AppMirror.MAC) {
			send("s\t" + s.pid() + "\t" + fmt(p[0]) + "\t" + fmt(p[1]) + "\t" + (int) Math.round(lines * 3));
		} else if (AppMirror.WINDOWS) {
			send("w\t" + s.window() + "\t" + fmt(u) + "\t" + fmt(v) + "\t" + (int) Math.round(lines * 120));
		} else {
			linux(s, "click", "--window", String.valueOf(s.window()), lines > 0 ? "4" : "5");
		}
	}

	/** A key that is not a letter (arrows, Enter, Backspace...) or a shortcut (Cmd/Ctrl + key). glfw key code. */
	public static void key(AppMirror.Source s, int glfwKey, boolean down, boolean shift, boolean ctrl, boolean alt, boolean cmd) {
		if (AppMirror.MAC) {
			int code = macKey(glfwKey);
			if (code < 0) {
				return;
			}
			long flags = (shift ? 0x20000L : 0) | (ctrl ? 0x40000L : 0) | (alt ? 0x80000L : 0) | (cmd ? 0x100000L : 0);
			send("k\t" + s.pid() + "\t" + code + "\t" + (down ? 1 : 0) + "\t" + flags);
		} else if (AppMirror.WINDOWS) {
			int vk = winKey(glfwKey);
			if (vk > 0) {
				send("k\t" + s.window() + "\t" + vk + "\t" + (down ? 1 : 0));
			}
		} else if (down) {
			String k = linuxKey(glfwKey);
			if (k != null) {
				linux(s, "key", "--window", String.valueOf(s.window()), (ctrl ? "ctrl+" : "") + (alt ? "alt+" : "") + (shift ? "shift+" : "") + k);
			}
		}
	}

	/** Typed text (letters, accents, emoji...). */
	public static void text(AppMirror.Source s, String t) {
		if (t == null || t.isEmpty()) {
			return;
		}
		StringBuilder hex = new StringBuilder();
		for (char ch : t.toCharArray()) {
			hex.append(String.format("%04x", (int) ch));
		}
		if (AppMirror.MAC) {
			send("t\t" + s.pid() + "\t" + hex);
		} else if (AppMirror.WINDOWS) {
			send("t\t" + s.window() + "\t" + hex);
		} else {
			linux(s, "type", "--window", String.valueOf(s.window()), t);
		}
	}

	// ------------------------------------------------------------------ where
	/** Screen point (macOS points) for a place in the window, using where the window is now (checked every 2 s). */
	private static double[] point(AppMirror.Source s, double u, double v) {
		u = Math.max(0, Math.min(1, u));
		v = Math.max(0, Math.min(1, v));
		if (!AppMirror.MAC) {
			return new double[] {u, v};
		}
		long now = System.currentTimeMillis();
		double[] b = BOUNDS.get(s.window());
		Long at = BOUNDS_AT.get(s.window());
		if (at == null || now - at > 2000) {
			BOUNDS_AT.put(s.window(), now);
			Thread t = new Thread(() -> {
				double[] nb = AppMirror.bounds(s);
				if (nb != null) {
					BOUNDS.put(s.window(), nb);
				}
			}, "kubo-window-where");
			t.setDaemon(true);
			t.start();
		}
		if (b == null) {
			b = new double[] {s.x(), s.y(), s.w(), s.h()};
		}
		return new double[] {b[0] + u * b[2], b[1] + v * b[3]};
	}

	private static String fmt(double d) {
		return String.format(java.util.Locale.ROOT, "%.2f", d);
	}

	// ------------------------------------------------------------------ helper process
	private static synchronized void send(String line) {
		try {
			if (helper == null || !helper.isAlive()) {
				start();
			}
			if (out != null) {
				out.write(line);
				out.write('\n');
				out.flush();
			}
		} catch (Exception e) {
			problem = "Could not send input to the app: " + e.getMessage();
			helper = null;
		}
	}

	private static void start() throws Exception {
		ProcessBuilder pb;
		if (AppMirror.MAC) {
			Path js = Files.createTempFile("kubo-input", ".js");
			Files.writeString(js, MAC_JS, StandardCharsets.UTF_8);
			pb = new ProcessBuilder("osascript", "-l", "JavaScript", js.toString());
		} else {
			Path ps = Files.createTempFile("kubo-input", ".ps1");
			Files.writeString(ps, WIN_PS, StandardCharsets.UTF_8);
			pb = new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", ps.toString());
		}
		ProcessBuilder.Redirect sink = ProcessBuilder.Redirect.DISCARD;
		if (AppMirror.MAC) {
			Path logDir = Path.of(System.getProperty("user.home"), "Library", "Logs", "Caos");
			Files.createDirectories(logDir);
			sink = ProcessBuilder.Redirect.appendTo(logDir.resolve("input.log").toFile()); // ~/Library/Logs/Caos/input.log
		}
		helper = pb.redirectErrorStream(true).redirectOutput(sink).start();
		out = new BufferedWriter(new OutputStreamWriter(helper.getOutputStream(), StandardCharsets.UTF_8));
	}

	public static synchronized void stop() {
		if (helper != null) {
			helper.destroy();
			helper = null;
			out = null;
		}
	}

	private static void linux(AppMirror.Source s, String... args) {
		try {
			String[] cmd = new String[args.length + 1];
			cmd[0] = "xdotool";
			System.arraycopy(args, 0, cmd, 1, args.length);
			new ProcessBuilder(cmd).start();
		} catch (Exception e) {
			problem = "Install xdotool to use apps on panels";
		}
	}

	/** macOS: posts CoreGraphics events to the app's process. One command per line on stdin. */
	private static final String MAC_JS = String.join("\n",
			"ObjC.import('CoreGraphics'); ObjC.import('Foundation'); ObjC.import('ApplicationServices');",
			"console.log('kubo-input started. Accessibility allowed: ' + $.AXIsProcessTrusted());",
			"function post(pid, ev){ $.CGEventPostToPid(pid, ev); }",
			"function handle(line){",
			"  var f = line.split('\\t'); var c = f[0];",
			"  try {",
			"    if (c === 'm') {",
			"      var pid = +f[1], type = +f[2], p = $.CGPointMake(+f[3], +f[4]), btn = +f[5], clicks = +f[6];",
			"      var e = $.CGEventCreateMouseEvent(null, type, p, btn);",
			"      if (clicks > 1) { $.CGEventSetIntegerValueField(e, 1, clicks); }",
			"      if (f[7]) { var wn = +f[7]; $.CGEventSetIntegerValueField(e, 91, wn); $.CGEventSetIntegerValueField(e, 92, wn); }",
			"      post(pid, e);",
			"    } else if (c === 's') {",
			"      var e2 = $.CGEventCreateScrollWheelEvent2(null, 1, 1, +f[4], 0, 0);",
			"      $.CGEventSetLocation(e2, $.CGPointMake(+f[2], +f[3]));",
			"      post(+f[1], e2);",
			"    } else if (c === 'k') {",
			"      var e3 = $.CGEventCreateKeyboardEvent(null, +f[2], f[3] === '1');",
			"      $.CGEventSetFlags(e3, +f[4]);",
			"      post(+f[1], e3);",
			"    } else if (c === 't') {",
			"      var hex = f[2];",
			"      for (var i = 0; i + 4 <= hex.length; i += 4) {",
			"        var s = String.fromCharCode(parseInt(hex.substr(i, 4), 16));",
			"        var ns = $.NSString.stringWithString(s);",
			"        var down = $.CGEventCreateKeyboardEvent(null, 0, true), up = $.CGEventCreateKeyboardEvent(null, 0, false);",
			"        var chars = [ns.characterAtIndex(0)];",
			"        $.CGEventKeyboardSetUnicodeString(down, 1, chars); $.CGEventKeyboardSetUnicodeString(up, 1, chars);",
			"        post(+f[1], down); post(+f[1], up);",
			"      }",
			"    }",
			"  } catch (err) { console.log('kubo-input error: ' + err + ' | ' + line); }",
			"}",
			"var input = $.NSFileHandle.fileHandleWithStandardInput, rest = '';",
			"while (true) {",
			"  var d = input.availableData;",
			"  if (d.length === 0) { break; }",
			"  rest += $.NSString.alloc.initWithDataEncoding(d, $.NSUTF8StringEncoding).js;",
			"  var lines = rest.split('\\n'); rest = lines.pop();",
			"  for (var j = 0; j < lines.length; j++) { if (lines[j]) handle(lines[j]); }",
			"}");

	/** Windows: PostMessage to the window / the control under the mouse. */
	private static final String WIN_PS = String.join("\r\n",
			"Add-Type @\"",
			"using System; using System.Runtime.InteropServices;",
			"public class KI {",
			"  [StructLayout(LayoutKind.Sequential)] public struct P { public int X; public int Y; }",
			"  [StructLayout(LayoutKind.Sequential)] public struct R { public int L; public int T; public int Ri; public int B; }",
			"  [DllImport(\"user32.dll\")] public static extern bool PostMessage(IntPtr h, uint m, IntPtr w, IntPtr l);",
			"  [DllImport(\"user32.dll\")] public static extern bool GetWindowRect(IntPtr h, out R r);",
			"  [DllImport(\"user32.dll\")] public static extern IntPtr WindowFromPoint(P p);",
			"  [DllImport(\"user32.dll\")] public static extern bool ScreenToClient(IntPtr h, ref P p);",
			"  public static IntPtr L(int x, int y) { return (IntPtr)(((y & 0xFFFF) << 16) | (x & 0xFFFF)); }",
			"  public static void Mouse(long hw, string type, int btn, double u, double v) {",
			"    IntPtr h = (IntPtr)hw; R r; GetWindowRect(h, out r);",
			"    P p = new P(); p.X = r.L + (int)(u * (r.Ri - r.L)); p.Y = r.T + (int)(v * (r.B - r.T));",
			"    IntPtr t = WindowFromPoint(p); if (t == IntPtr.Zero) t = h; ScreenToClient(t, ref p);",
			"    uint m = 0x200; int wp = 0;",
			"    if (type == \"down\") { m = btn == 1 ? 0x204u : btn == 2 ? 0x207u : 0x201u; wp = btn == 1 ? 2 : btn == 2 ? 16 : 1; }",
			"    else if (type == \"up\") { m = btn == 1 ? 0x205u : btn == 2 ? 0x208u : 0x202u; }",
			"    else if (type == \"drag\") { wp = btn == 1 ? 2 : btn == 2 ? 16 : 1; }",
			"    PostMessage(t, m, (IntPtr)wp, L(p.X, p.Y));",
			"  }",
			"  public static void Wheel(long hw, double u, double v, int d) {",
			"    IntPtr h = (IntPtr)hw; R r; GetWindowRect(h, out r);",
			"    P p = new P(); p.X = r.L + (int)(u * (r.Ri - r.L)); p.Y = r.T + (int)(v * (r.B - r.T));",
			"    IntPtr t = WindowFromPoint(p); if (t == IntPtr.Zero) t = h;",
			"    PostMessage(t, 0x20A, (IntPtr)((d & 0xFFFF) << 16), L(p.X, p.Y));",
			"  }",
			"}",
			"\"@",
			"while (($l = [Console]::In.ReadLine()) -ne $null) {",
			"  $f = $l.Split(\"`t\")",
			"  try {",
			"    switch ($f[0]) {",
			"      'm' { [KI]::Mouse([long]$f[1], $f[2], [int]$f[3], [double]::Parse($f[4], [Globalization.CultureInfo]::InvariantCulture), [double]::Parse($f[5], [Globalization.CultureInfo]::InvariantCulture)) }",
			"      'w' { [KI]::Wheel([long]$f[1], [double]::Parse($f[2], [Globalization.CultureInfo]::InvariantCulture), [double]::Parse($f[3], [Globalization.CultureInfo]::InvariantCulture), [int]$f[4]) }",
			"      'k' { [KI]::PostMessage([IntPtr][long]$f[1], $(if ($f[3] -eq '1') { 0x100 } else { 0x101 }), [IntPtr][int]$f[2], [IntPtr]0) | Out-Null }",
			"      't' { for ($i = 0; $i + 4 -le $f[2].Length; $i += 4) { [KI]::PostMessage([IntPtr][long]$f[1], 0x102, [IntPtr][Convert]::ToInt32($f[2].Substring($i, 4), 16), [IntPtr]0) | Out-Null } }",
			"    }",
			"  } catch {}",
			"}");

	// ------------------------------------------------------------------ key codes
	/** GLFW -> macOS virtual key code (-1 = none). */
	static int macKey(int k) {
		if (k >= 65 && k <= 90) {
			int[] letters = {0, 11, 8, 2, 14, 3, 5, 4, 34, 38, 40, 37, 46, 45, 31, 35, 12, 15, 1, 17, 32, 9, 13, 7, 16, 6};
			return letters[k - 65];
		}
		if (k >= 48 && k <= 57) {
			int[] digits = {29, 18, 19, 20, 21, 23, 22, 26, 28, 25};
			return digits[k - 48];
		}
		return switch (k) {
			case 257, 335 -> 36; // enter
			case 258 -> 48; // tab
			case 32 -> 49; // space
			case 259 -> 51; // backspace
			case 256 -> 53; // esc
			case 261 -> 117; // delete
			case 262 -> 124;
			case 263 -> 123;
			case 264 -> 125;
			case 265 -> 126;
			case 268 -> 115; // home
			case 269 -> 119; // end
			case 266 -> 116; // page up
			case 267 -> 121; // page down
			case 290 -> 122; case 291 -> 120; case 292 -> 99; case 293 -> 118; case 294 -> 96; case 295 -> 97;
			case 296 -> 98; case 297 -> 100; case 298 -> 101; case 299 -> 109; case 300 -> 103; case 301 -> 111;
			case 45 -> 27; case 61 -> 24; case 91 -> 33; case 93 -> 30; case 92 -> 42; case 59 -> 41; case 39 -> 39;
			case 44 -> 43; case 46 -> 47; case 47 -> 44; case 96 -> 50;
			default -> -1;
		};
	}

	/** GLFW -> Windows virtual key (0 = none). */
	static int winKey(int k) {
		if (k >= 65 && k <= 90 || k >= 48 && k <= 57) {
			return k;
		}
		return switch (k) {
			case 257, 335 -> 0x0D; case 258 -> 0x09; case 32 -> 0x20; case 259 -> 0x08; case 256 -> 0x1B; case 261 -> 0x2E;
			case 262 -> 0x27; case 263 -> 0x25; case 264 -> 0x28; case 265 -> 0x26; case 268 -> 0x24; case 269 -> 0x23;
			case 266 -> 0x21; case 267 -> 0x22;
			default -> k >= 290 && k <= 301 ? 0x70 + (k - 290) : 0;
		};
	}

	static String linuxKey(int k) {
		if (k >= 65 && k <= 90) {
			return String.valueOf((char) (k + 32));
		}
		return switch (k) {
			case 257, 335 -> "Return"; case 258 -> "Tab"; case 259 -> "BackSpace"; case 256 -> "Escape"; case 261 -> "Delete";
			case 262 -> "Right"; case 263 -> "Left"; case 264 -> "Down"; case 265 -> "Up"; case 268 -> "Home"; case 269 -> "End";
			default -> null;
		};
	}
}
