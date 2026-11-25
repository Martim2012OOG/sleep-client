package dev.solclient.video;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

/**
 * Talks to the optional "MCEF Modern" mod (Chromium inside Minecraft) by reflection, so Caos still works when it is
 * not installed. MCEF downloads its browser files (~100 MB) the first time it is initialised.
 */
public final class Mcef {

	private static Boolean present;
	private static Object init;
	private static Class<?> apiCls, initCls, browserCls, cefCls;

	private Mcef() {
	}

	public static boolean installed() {
		if (present == null) {
			try {
				apiCls = Class.forName("net.dimaskama.mcef.api.MCEFApi");
				initCls = Class.forName("net.dimaskama.mcef.api.MCEFApi$Initialization");
				browserCls = Class.forName("net.dimaskama.mcef.api.MCEFBrowser");
				cefCls = Class.forName("org.cef.browser.CefBrowser");
				present = true;
			} catch (Throwable t) {
				present = false;
			}
		}
		return present;
	}

	/** Starts the (asynchronous) MCEF initialisation; safe to call many times. */
	public static void start() {
		if (!installed() || init != null) {
			return;
		}
		try {
			init = apiCls.getMethod("initialize").invoke(null);
		} catch (Throwable t) {
			System.err.println("[Caos] MCEF init failed: " + t);
			present = false;
		}
	}

	public static boolean ready() {
		if (init == null) {
			return false;
		}
		try {
			return "DONE".equals(String.valueOf(initCls.getMethod("getStage").invoke(init)));
		} catch (Throwable t) {
			return false;
		}
	}

	/** Short status for the settings ("Ready", "Downloading 40%"...). */
	public static String status() {
		if (!installed()) {
			return "Video engine missing";
		}
		if (init == null) {
			return "Not started";
		}
		if (failed()) {
			return "Failed: " + error();
		}
		try {
			String stage = String.valueOf(initCls.getMethod("getStage").invoke(init));
			float p = (float) initCls.getMethod("getPercentage").invoke(init);
			if ("DONE".equals(stage)) {
				return "Ready";
			}
			String s = stage.charAt(0) + stage.substring(1).toLowerCase().replace('_', ' ');
			return p >= 0 ? s + " " + Math.round(p) + "%" : s + "...";
		} catch (Throwable t) {
			return "Error";
		}
	}


	// ---- download / start-up progress (for the progress bars)
	private static long changedAt;
	private static String lastStage = "";
	private static float lastPct = -2;

	/** True once the start-up was asked for (so a download may be running). */
	public static boolean started() {
		return init != null;
	}

	private static java.util.concurrent.CompletableFuture<?> future() {
		try {
			return (java.util.concurrent.CompletableFuture<?>) initCls.getMethod("getFuture").invoke(init);
		} catch (Throwable t) {
			return null;
		}
	}

	/** The start-up crashed (no internet, no disk space, blocked download...). */
	public static boolean failed() {
		if (init == null) {
			return false;
		}
		var f = future();
		return f != null && f.isCompletedExceptionally();
	}

	public static String error() {
		var f = future();
		if (f == null || !f.isCompletedExceptionally()) {
			return "";
		}
		try {
			f.join();
		} catch (Throwable t) {
			Throwable c = t.getCause() != null ? t.getCause() : t;
			String m = c.getMessage();
			return m == null || m.isBlank() ? c.getClass().getSimpleName() : m;
		}
		return "";
	}

	/** Tries the download / start-up again after a failure. */
	public static void retry() {
		if (failed()) {
			init = null;
			present = null;
			lastStage = "";
			lastPct = -2;
			start();
		}
	}

	/** What the engine is doing, in words for the player. */
	public static String stageLabel() {
		if (init == null) {
			return "Waiting to start";
		}
		try {
			String st = String.valueOf(initCls.getMethod("getStage").invoke(init));
			return switch (st) {
				case "DOWNLOADING" -> "Downloading the browser engine";
				case "EXTRACTING" -> "Unpacking the files";
				case "INSTALL" -> "Installing";
				case "INITIALIZING" -> "Starting the browser";
				case "DONE" -> "Ready";
				default -> st.charAt(0) + st.substring(1).toLowerCase().replace('_', ' ');
			};
		} catch (Throwable t) {
			return "Working";
		}
	}

	/** 0..100, or -1 when the engine does not say (the bar then shows it is busy instead). */
	public static float progress() {
		if (init == null) {
			return -1;
		}
		try {
			float p = (float) initCls.getMethod("getPercentage").invoke(init);
			String st = String.valueOf(initCls.getMethod("getStage").invoke(init));
			if (!st.equals(lastStage) || Math.abs(p - lastPct) > 0.01F) {
				lastStage = st;
				lastPct = p;
				changedAt = System.currentTimeMillis();
			}
			return "DONE".equals(st) ? 100 : p >= 0 ? Math.min(100, p) : -1;
		} catch (Throwable t) {
			return -1;
		}
	}

	/** Nothing has moved for a long time: probably stuck (slow or blocked connection). */
	public static boolean stalled() {
		return init != null && !ready() && changedAt != 0 && System.currentTimeMillis() - changedAt > 45_000;
	}

	/** A browser showing 'url' (null if MCEF is not ready). */
	public static Browser create(String url, int w, int h) {
		if (!ready()) {
			return null;
		}
		try {
			@SuppressWarnings("unchecked")
			CompletableFuture<Object> f = (CompletableFuture<Object>) initCls.getMethod("getFuture").invoke(init);
			Object api = f.getNow(null);
			if (api == null) {
				return null;
			}
			Object b = apiCls.getMethod("createBrowser", String.class, boolean.class).invoke(api, url, false);
			Browser br = new Browser(b);
			br.resize(w, h);
			br.focus();
			br.frameRate(30);
			// the new page took the keyboard focus: give it back (next frame) to the panel you are typing in
			PanelInput.refocus();
			return br;
		} catch (Throwable t) {
			System.err.println("[Caos] could not create a browser: " + t);
			return null;
		}
	}

	/** One embedded Chromium page. */
	public static final class Browser {
		private final Object b;
		private Object cef;
		private static Method resize, view, close, getCef, exec, focus, fps, getUrl;

		Browser(Object b) throws ReflectiveOperationException {
			this.b = b;
			if (resize == null) {
				resize = browserCls.getMethod("resize", int.class, int.class);
				view = browserCls.getMethod("getTextureView");
				close = browserCls.getMethod("close");
				getCef = browserCls.getMethod("getCefBrowser");
				exec = cefCls.getMethod("executeJavaScript", String.class, String.class, int.class);
				try {
					focus = cefCls.getMethod("setFocus", boolean.class);
				} catch (ReflectiveOperationException ignored) {
				}
				try {
					fps = cefCls.getMethod("setWindowlessFrameRate", int.class);
				} catch (ReflectiveOperationException ignored) {
				}
				try {
					getUrl = cefCls.getMethod("getURL");
				} catch (ReflectiveOperationException ignored) {
				}
			}
		}

		/** Asks Chromium for 'rate' painted frames per second (off-screen browsers can be left at a very low rate). */
		public void frameRate(int rate) {
			try {
				if (cef == null) {
					cef = getCef.invoke(b);
				}
				if (fps != null) {
					fps.invoke(cef, rate);
				}
			} catch (Throwable ignored) {
			}
		}

		/** Tells Chromium this page is focused/visible so it keeps painting frames. */
		public void focus() {
			try {
				if (cef == null) {
					cef = getCef.invoke(b);
				}
				if (!callApi("setFocus", true) && focus != null) {
					focus.invoke(cef, true);
				}
			} catch (Throwable ignored) {
			}
		}

		public void resize(int w, int h) {
			try {
				resize.invoke(b, w, h);
			} catch (Throwable ignored) {
			}
		}

		public com.mojang.blaze3d.textures.GpuTextureView texture() {
			try {
				return (com.mojang.blaze3d.textures.GpuTextureView) view.invoke(b);
			} catch (Throwable t) {
				return null;
			}
		}

		/** Current address of the page (the page writes the video length into its #hash), or null. */
		public String url() {
			try {
				if (cef == null) {
					cef = getCef.invoke(b);
				}
				return getUrl == null ? null : (String) getUrl.invoke(cef);
			} catch (Throwable t) {
				return null;
			}
		}

		public void js(String code) {
			try {
				if (cef == null) {
					cef = getCef.invoke(b);
				}
				exec.invoke(cef, code, "", 0);
			} catch (Throwable ignored) {
			}
		}

		public void close() {
			try {
				close.invoke(b);
			} catch (Throwable ignored) {
			}
		}

		// ---- input and navigation (the standard CefBrowser methods, fed with AWT events)
		private static final java.awt.Component SRC = new java.awt.Component() {
		};
		private static final java.util.Map<String, Method> CEF = new java.util.HashMap<>();

		/** Input methods that JCEF only exposes as protected final on CefBrowser_N (they are not on the CefBrowser interface). */
		private static final java.util.Set<String> HIDDEN = java.util.Set.of("sendMouseEvent", "sendMouseWheelEvent", "sendKeyEvent");
		private static final java.util.Set<String> LOGGED = new java.util.HashSet<>();

		private static Method hidden(Class<?> start, String name, Class<?> type) throws NoSuchMethodException {
			for (Class<?> c = start; c != null; c = c.getSuperclass()) {
				try {
					Method m = c.getDeclaredMethod(name, type);
					m.setAccessible(true);
					return m;
				} catch (NoSuchMethodException ignored) {
				}
			}
			throw new NoSuchMethodException(name);
		}

		private Object call(String name, Class<?> type, Object arg) {
			String key = name + ":" + (type == null ? "" : type.getName());
			try {
				if (cef == null) {
					cef = getCef.invoke(b);
				}
				Method m = CEF.get(key);
				if (m == null) {
					if (type == null) {
						m = cefCls.getMethod(name);
					} else if (HIDDEN.contains(name)) {
						m = hidden(cef.getClass(), name, type);
					} else {
						m = cefCls.getMethod(name, type);
					}
					CEF.put(key, m);
				}
				return type == null ? m.invoke(cef) : m.invoke(cef, arg);
			} catch (Throwable t) {
				if (LOGGED.add(key)) {
					System.err.println("[Caos] browser call " + name + " failed: " + t);
				}
				return null;
			}
		}

		// ---- MCEF Modern's own input methods (they build the Chromium events the way MCEF needs them, also on macOS)
		private static final java.util.Map<String, Method> API = new java.util.HashMap<>();
		private static final java.util.Set<String> NO_API = new java.util.HashSet<>();

		private static Method api(String name, int params) {
			String key = name + "/" + params;
			if (NO_API.contains(key)) {
				return null;
			}
			Method m = API.get(key);
			if (m == null) {
				for (Method c : browserCls.getMethods()) {
					if (c.getName().equals(name) && c.getParameterCount() == params) {
						m = c;
						break;
					}
				}
				if (m == null) {
					NO_API.add(key);
					return null;
				}
				API.put(key, m);
			}
			return m;
		}

		private boolean callApi(String name, Object... args) {
			Method m = api(name, args.length);
			if (m == null) {
				return false;
			}
			try {
				m.invoke(b, args);
				return true;
			} catch (Throwable t) {
				if (LOGGED.add("api:" + name)) {
					System.err.println("[Caos] browser " + name + " failed: " + t);
				}
				return false;
			}
		}

		/**
		 * Edit commands of the page itself (Chromium's own copy / paste / cut / selectAll / undo / redo / delete on the
		 * focused frame): they use the real system clipboard. False = not available.
		 */
		public boolean edit(String command) {
			try {
				if (cef == null) {
					cef = getCef.invoke(b);
				}
				Object frame = cefCls.getMethod("getFocusedFrame").invoke(cef);
				if (frame == null) {
					frame = cefCls.getMethod("getMainFrame").invoke(cef);
				}
				if (frame == null) {
					return false;
				}
				Method m = frame.getClass().getMethod(command);
				m.setAccessible(true);
				m.invoke(frame);
				return true;
			} catch (Throwable t) {
				if (LOGGED.add("edit:" + command)) {
					System.err.println("[Caos] browser " + command + " failed: " + t);
				}
				return false;
			}
		}

		/** Minecraft's key event straight to MCEF. False = not possible (the old way is used). */
		public boolean keyMc(Object keyInput, boolean press) {
			return keyInput != null && callApi(press ? "onKeyPressed" : "onKeyReleased", keyInput);
		}

		public boolean charMc(Object charInput) {
			return charInput != null && callApi("onCharTyped", charInput);
		}

		/** Builds Minecraft's Click(x, y, MouseInput(button, modifiers)) for MCEF's onMouseClicked / onMouseReleased. */
		private static Object click(Method m, int x, int y, int glfwButton) throws ReflectiveOperationException {
			Class<?> clickCls = m.getParameterTypes()[0];
			java.lang.reflect.RecordComponent[] rc = clickCls.getRecordComponents();
			if (rc == null || rc.length != 3) {
				return null;
			}
			Class<?> inputCls = rc[2].getType();
			java.lang.reflect.RecordComponent[] ic = inputCls.getRecordComponents();
			if (ic == null || ic.length != 2) {
				return null;
			}
			Object input = inputCls.getDeclaredConstructor(ic[0].getType(), ic[1].getType()).newInstance(glfwButton, 0);
			return clickCls.getDeclaredConstructor(rc[0].getType(), rc[1].getType(), rc[2].getType()).newInstance((double) x, (double) y, input);
		}

		private boolean mouseMc(int id, int x, int y, int awtButton, int clicks) {
			try {
				if (id == 503) {
					return callApi("onMouseMoved", x, y);
				}
				if (id == 506) {
					return false; // dragging: the AWT event below carries "left button held", so text gets selected
				}
				int glfw = awtButton == 1 ? 0 : awtButton == 3 ? 1 : 2;
				if (id == 501) {
					Method m = api("onMouseClicked", 2);
					Object c = m == null ? null : click(m, x, y, glfw);
					return c != null && callApi("onMouseClicked", c, clicks >= 2);
				}
				if (id == 502) {
					Method m = api("onMouseReleased", 1);
					Object c = m == null ? null : click(m, x, y, glfw);
					return c != null && callApi("onMouseReleased", c);
				}
			} catch (Throwable t) {
				if (LOGGED.add("api:mouse")) {
					System.err.println("[Caos] browser mouse: " + t);
				}
			}
			return false;
		}

		/** id: 501 pressed, 502 released, 503 moved, 506 dragged. button 1 left, 2 middle, 3 right (0 for moves). Coordinates in page pixels. */
		public void mouse(int id, int x, int y, int button, int clicks) {
			if (mouseMc(id, x, y, button, clicks)) {
				return;
			}
			int mods = button == 1 ? 1024 : button == 2 ? 2048 : button == 3 ? 4096 : 0;
			if (id == 502 || id == 503) {
				mods = id == 502 ? 0 : mods;
			}
			call("sendMouseEvent", java.awt.event.MouseEvent.class,
					new java.awt.event.MouseEvent(SRC, id, System.currentTimeMillis(), mods, x, y, clicks, button == 3 && id == 501, button));
		}

		/** direction > 0 scrolls up, < 0 down (JCEF passes amount * rotation straight to Chromium as the pixel delta). */
		public void wheel(int x, int y, int direction) {
			if (callApi("onMouseScrolled", x, y, (double) Integer.signum(direction))) {
				return;
			}
			int rot = Integer.signum(direction);
			call("sendMouseWheelEvent", java.awt.event.MouseWheelEvent.class,
					new java.awt.event.MouseWheelEvent(SRC, 507, System.currentTimeMillis(), 0, x, y, 0, false,
							java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL, 100, rot));
		}

		/** id: 401 pressed, 402 released, 400 typed (then awtCode must be 0 and ch the character). */
		public void key(int id, int awtCode, char ch, int mods) {
			try {
				call("sendKeyEvent", java.awt.event.KeyEvent.class,
						new java.awt.event.KeyEvent(SRC, id, System.currentTimeMillis(), mods, awtCode, ch == 0 ? java.awt.event.KeyEvent.CHAR_UNDEFINED : ch,
								java.awt.event.KeyEvent.KEY_LOCATION_STANDARD));
			} catch (IllegalArgumentException ignored) {
				// an impossible key / char combination: nothing to send
			}
		}

		public void load(String url) {
			call("loadURL", String.class, url);
		}

		public void back() {
			call("goBack", null, null);
		}

		public void forward() {
			call("goForward", null, null);
		}

		public void reload() {
			call("reload", null, null);
		}

		public boolean canBack() {
			return Boolean.TRUE.equals(call("canGoBack", null, null));
		}

		public boolean canForward() {
			return Boolean.TRUE.equals(call("canGoForward", null, null));
		}
	}
}
