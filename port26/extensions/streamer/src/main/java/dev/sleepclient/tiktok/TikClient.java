package dev.sleepclient.tiktok;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WebSocket connection to whatever delivers the live events (TikFinity on this PC, Tik.Tools, or your own server).
 *
 * Everything here runs OFF the game thread (JDK async WebSocket + one tiny scheduler thread): the game only ever does
 * {@link #poll()} on a lock-free queue, so the live can never make Minecraft stutter. If the game is slower than the
 * live, chat/other events are dropped first and gifts are kept.
 */
public final class TikClient {

	private static final int QUEUE_MAX = 300;
	private static final Pattern AT_USER = Pattern.compile("@([A-Za-z0-9_.]{1,40})");

	private final ConcurrentLinkedQueue<TikEvent> queue = new ConcurrentLinkedQueue<>();
	private final AtomicInteger size = new AtomicInteger();
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
	private final ScheduledExecutorService ex = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "kubo-tiktok");
		t.setDaemon(true);
		return t;
	});

	private volatile String status = "Disconnected";
	private volatile boolean want;
	private volatile WebSocket ws;
	private volatile String url = "";
	private volatile int generation;
	private volatile boolean debug;
	private volatile int debugLeft;
	private volatile long received;
	private int attempt;
	/** How many messages of each raw event name arrived (chat, gift, follow, like...), to diagnose "nothing happens". */
	private final java.util.concurrent.ConcurrentHashMap<String, Integer> names = new java.util.concurrent.ConcurrentHashMap<>();
	private final java.util.concurrent.atomic.AtomicLong total = new java.util.concurrent.atomic.AtomicLong();
	/** Generation that already scheduled a retry (onError + onClose both fire for one drop). */
	private volatile int lostGen = -1;

	public String status() {
		return status;
	}

	public boolean wanted() {
		return want;
	}

	public boolean connected() {
		return (ws != null || directOn) && status.startsWith("Connected");
	}

	// ------------------------------------------------------------------ automatic (straight to TikTok, only the @user)
	private Object direct; // a TikDirect, made on first use (so a missing library can never break the other sources)
	private volatile boolean directOn;

	/** Connects straight to TikTok with only the @user; waits by itself until the live starts. */
	public synchronized void connectDirect(String user) {
		disconnect();
		want = true;
		directOn = true;
		try {
			if (direct == null) {
				direct = new TikDirect();
			}
			((TikDirect) direct).start(user, this);
		} catch (Throwable t) {
			// the bundled library is missing / broken: say so instead of crashing the game
			directOn = false;
			status = "Automatic connection unavailable: " + t.getClass().getSimpleName();
		}
	}

	void directStatus(String s) {
		if (directOn) {
			status = s;
		}
	}

	/** A message in the gateway JSON shape, from the automatic connection (or anything else inside the mod). */
	void inject(String json) {
		if (directOn) {
			handle(json);
		}
	}

	public long received() {
		return received;
	}

	public void setDebug(boolean on) {
		if (on && !debug) {
			debugLeft = 25; // log the first 25 raw messages so a different field name is easy to spot
		}
		debug = on;
	}

	/** Next event from the live, or null. Called from the game thread. */
	public TikEvent poll() {
		TikEvent e = queue.poll();
		if (e != null) {
			size.decrementAndGet();
		}
		return e;
	}

	private void push(TikEvent e) {
		// when we are behind, drop chat first (gifts are what the streamer cares about)
		if (size.get() >= QUEUE_MAX && e.type() == TikEvent.Type.CHAT) {
			return;
		}
		if (size.get() >= QUEUE_MAX * 2) {
			return;
		}
		queue.add(e);
		size.incrementAndGet();
	}

	// ------------------------------------------------------------------ public API
	public synchronized void connect(String target) {
		disconnect();
		if (target == null || target.isBlank()) {
			status = "Missing address";
			return;
		}
		URI uri;
		try {
			uri = URI.create(target.trim());
			String sc = uri.getScheme();
			if (sc == null || !(sc.equalsIgnoreCase("ws") || sc.equalsIgnoreCase("wss"))) {
				status = "Address must start with ws:// or wss://";
				return;
			}
		} catch (RuntimeException e) {
			status = "Invalid address";
			return;
		}
		url = target.trim();
		want = true;
		attempt = 0;
		int gen = ++generation;
		status = "Connecting...";
		ex.execute(() -> open(uri, gen));
	}

	/** Stops everything and shows a reason in the status (missing username, API key...). */
	public synchronized void fail(String why) {
		disconnect();
		status = why;
	}

	public synchronized void disconnect() {
		want = false;
		if (directOn) {
			directOn = false;
			try {
				if (direct != null) {
					((TikDirect) direct).stop();
				}
			} catch (Throwable ignored) {
				// library missing: nothing to stop
			}
		}
		generation++;
		WebSocket w = ws;
		ws = null;
		if (w != null) {
			try {
				w.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
			} catch (RuntimeException ignored) {
				// already closed
			}
			try {
				w.abort();
			} catch (RuntimeException ignored) {
				// already closed
			}
		}
		queue.clear();
		size.set(0);
		names.clear();
		total.set(0);
		status = "Disconnected";
	}

	public void shutdown() {
		disconnect();
		ex.shutdownNow();
	}

	// ------------------------------------------------------------------ connection
	private void open(URI uri, int gen) {
		if (gen != generation || !want) {
			return;
		}
		lostGen = -1;
		try {
			StringBuilder partial = new StringBuilder();
			WebSocket.Listener l = new WebSocket.Listener() {
				@Override
				public void onOpen(WebSocket w) {
					if (gen != generation) {
						w.abort();
						return;
					}
					ws = w;
					attempt = 0;
					status = "Connected - waiting for the live";
					w.request(1);
					ex.schedule(() -> ping(w, gen), 20, TimeUnit.SECONDS);
				}

				@Override
				public CompletionStage<?> onText(WebSocket w, CharSequence data, boolean last) {
					if (gen == generation) {
						partial.append(data);
						if (partial.length() > 262144) {
							partial.setLength(0); // absurdly big message: skip it
						} else if (last) {
							String msg = partial.toString();
							partial.setLength(0);
							handle(msg);
						}
					}
					w.request(1);
					return null;
				}

				@Override
				public CompletionStage<?> onClose(WebSocket w, int code, String reason) {
					lost(gen, "Closed" + (reason == null || reason.isBlank() ? "" : ": " + reason));
					return null;
				}

				@Override
				public void onError(WebSocket w, Throwable error) {
					lost(gen, "Error: " + shortMsg(error));
				}
			};
			http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(8)).buildAsync(uri, l).whenComplete((w, err) -> {
				if (err != null) {
					lost(gen, shortMsg(err));
				}
			});
		} catch (RuntimeException e) {
			lost(gen, shortMsg(e));
		}
	}

	private void handle(String msg) {
		if (debug && debugLeft > 0) {
			debugLeft--;
			System.out.println("[Sleep TikTok] raw: " + (msg.length() > 600 ? msg.substring(0, 600) + "..." : msg));
		}
		total.incrementAndGet();
		String nm = eventName(msg);
		if (names.size() < 60 || names.containsKey(nm)) {
			names.merge(nm, 1, Integer::sum);
		}
		String low = nm.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
		if (TikStats.wants(low)) {
			TikStats.read(low, msg); // viewers / likes for the viewer counter
			if (!status.startsWith("Connected")) {
				status = "Connected - live events arriving";
			}
			return;
		}
		TikEvent e = TikParser.parse(msg);
		if (e == null) {
			return;
		}
		received++;
		status = "Connected - live events arriving";
		push(e);
	}

	/** Raw event name of a message (looked up in its first characters only: cheap even with hundreds of likes per second). */
	private static String eventName(String msg) {
		int i = msg.indexOf("\"event\"");
		if (i < 0 || i > 200) {
			i = msg.indexOf("\"type\"");
		}
		if (i < 0 || i > 200) {
			return "?";
		}
		int colon = msg.indexOf(':', i);
		int q1 = colon < 0 ? -1 : msg.indexOf('"', colon + 1);
		int q2 = q1 < 0 ? -1 : msg.indexOf('"', q1 + 1);
		return q2 < 0 || q2 - q1 > 40 ? "?" : msg.substring(q1 + 1, q2);
	}

	public long total() {
		return total.get();
	}

	/** "chat 12, like 340, gift 2" (most frequent first), in lines of at most 6 names. */
	public java.util.List<String> summary() {
		java.util.List<java.util.Map.Entry<String, Integer>> l = new java.util.ArrayList<>(names.entrySet());
		l.sort((a, b) -> b.getValue() - a.getValue());
		java.util.List<String> out = new java.util.ArrayList<>();
		StringBuilder b = new StringBuilder();
		int n = 0;
		for (var en : l) {
			if (n > 0) {
				b.append(", ");
			}
			b.append(en.getKey()).append(' ').append(en.getValue());
			if (++n == 6) {
				out.add(b.toString());
				b.setLength(0);
				n = 0;
			}
		}
		if (b.length() > 0) {
			out.add(b.toString());
		}
		return out;
	}

	private void ping(WebSocket w, int gen) {
		if (gen != generation || !want || ws != w) {
			return;
		}
		try {
			w.sendPing(ByteBuffer.allocate(0));
		} catch (RuntimeException ignored) {
			return;
		}
		ex.schedule(() -> ping(w, gen), 20, TimeUnit.SECONDS);
	}

	private void lost(int gen, String why) {
		if (gen != generation || !want || lostGen == gen) {
			return;
		}
		lostGen = gen;
		ws = null;
		// 2, 4, 8, 16, 30, 30... seconds
		int wait = (int) Math.min(30, 2L << Math.min(attempt, 4));
		attempt++;
		status = "Lost (" + why + "). Retrying in " + wait + "s";
		URI uri = URI.create(url);
		ex.schedule(() -> open(uri, gen), wait, TimeUnit.SECONDS);
	}

	private static String shortMsg(Throwable t) {
		Throwable c = t;
		while (c.getCause() != null && c.getCause() != c) {
			c = c.getCause();
		}
		if (c instanceof java.net.ConnectException || c instanceof java.nio.channels.ClosedChannelException
				|| c instanceof java.net.UnknownHostException) {
			return "can't reach it - is the app running / address right?";
		}
		String m = c.getMessage();
		if (m == null || m.isBlank()) {
			m = c.getClass().getSimpleName();
		}
		return m.length() > 60 ? m.substring(0, 60) : m;
	}

	// ------------------------------------------------------------------ helpers for the settings
	/** "@name", "name", or any tiktok.com/@name/live link -> "name" (null if nothing usable). */
	public static String username(String input) {
		if (input == null) {
			return null;
		}
		String s = input.trim();
		if (s.isEmpty()) {
			return null;
		}
		Matcher m = AT_USER.matcher(s);
		if (m.find()) {
			return m.group(1);
		}
		if (s.contains("/") || s.contains(" ")) {
			return null; // a link without @name (e.g. a vm.tiktok.com short link): we cannot resolve it
		}
		String n = s.replaceAll("[^A-Za-z0-9_.]", "");
		return n.isEmpty() ? null : n;
	}

	/** Fills {user} and {key} in an address template (both URL-encoded). */
	public static String fill(String template, String user, String key) {
		return template.replace("{user}", enc(user)).replace("{key}", enc(key));
	}

	private static String enc(String s) {
		return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
	}
}
