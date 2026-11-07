package dev.solclient.net;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Private Connection: sends the game's server connections through a SOCKS5 server in another country, so the server
 * sees that server's address instead of yours. Only the connection to Minecraft servers goes through it (not the
 * Microsoft login, not other programs). How it works: the game is pointed at a tiny relay on this computer
 * (127.0.0.1), which talks SOCKS5 to the chosen server and passes the bytes on.
 */
public final class PrivateConnection {

	/** One SOCKS5 server. */
	public static final class Server {
		public String name = "", country = "", host = "", user = "", pass = "";
		/** "socks5", "socks4" or "http" (CONNECT). Old saved servers have none: SOCKS5. */
		public String type = "socks5";
		public int port;
		public boolean mine;
		/** From a list that checks its servers (tried first). */
		public transient boolean verified;
		/** ms; -2 not tested, -1 failed. */
		public volatile int ping = -2;

		public String label() {
			return name.isEmpty() ? (mine || country.isEmpty() ? host + ":" + port : country + " " + host) : name;
		}
	}

	private static final Gson GSON = new Gson();
	private static final ExecutorService POOL = Executors.newCachedThreadPool(r -> {
		Thread t = new Thread(r, "Sleep-PrivateConnection");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY); // background work never takes CPU from the game
		return t;
	});
	/** Server lists downloaded / read at the same time (17 at once made the game stutter while they were parsed). */
	private static final java.util.concurrent.Semaphore LISTS_PAR = new java.util.concurrent.Semaphore(3);

	public static volatile boolean enabled;
	public static volatile Server selected;
	public static final List<Server> MINE = new CopyOnWriteArrayList<>();
	/** Country code -> free servers of that country. */
	public static volatile Map<String, List<Server>> free = Map.of();
	public static volatile String freeState = "";
	public static volatile String status = "";
	public static volatile String myIp = "";
	private static volatile boolean loaded;

	private PrivateConnection() {
	}

	// ------------------------------------------------------------------ saved settings
	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("kubo-private-connection.json");
	}

	/** Called every frame by the screen: once loaded it must not wait for the class lock (the background work holds others). */
	public static void load() {
		if (!loaded) {
			loadNow();
		}
	}

	private static synchronized void loadNow() {
		if (loaded) {
			return;
		}
		loaded = true;
		try {
			if (!Files.exists(file())) {
				return;
			}
			JsonObject o = GSON.fromJson(Files.readString(file()), JsonObject.class);
			if (o == null) {
				return;
			}
			enabled = o.has("enabled") && o.get("enabled").getAsBoolean();
			if (o.has("autoCountry") && o.get("autoCountry").isJsonPrimitive()) {
				autoCountry = o.get("autoCountry").getAsString(); // set by the launcher: connect by itself when the game opens
			}
			if (o.has("selected") && o.get("selected").isJsonObject()) {
				selected = GSON.fromJson(o.get("selected"), Server.class);
			}
			if (o.has("mine") && o.get("mine").isJsonArray()) {
				for (JsonElement e : o.getAsJsonArray("mine")) {
					Server s = GSON.fromJson(e, Server.class);
					if (s != null && !s.host.isEmpty()) {
						s.mine = true;
						MINE.add(s);
					}
				}
			}
		} catch (IOException | RuntimeException e) {
			System.err.println("[Sleep] private connection config: " + e);
		}
	}

	public static synchronized void save() {
		try {
			JsonObject o = new JsonObject();
			o.addProperty("enabled", enabled);
			if (selected != null) {
				o.add("selected", GSON.toJsonTree(selected));
			}
			o.add("mine", GSON.toJsonTree(MINE));
			Files.writeString(file(), GSON.toJson(o));
		} catch (IOException | RuntimeException e) {
			System.err.println("[Sleep] could not save private connection: " + e);
		}
	}

	private static volatile String autoCountry;
	private static boolean autoDone, autoAsked;

	/** The launcher asked for a connection when the game opens ("*" = fastest anywhere, or a country code): once the free lists are loaded, connect. */
	public static void autoTick() {
		if (autoDone) {
			return;
		}
		load();
		if (autoCountry == null || autoCountry.isBlank() || active()) {
			autoDone = autoCountry != null || loaded;
			return;
		}
		if (free.isEmpty()) {
			if (!autoAsked) {
				autoAsked = true;
				loadFree();
			}
			return;
		}
		autoDone = true;
		connectBest("*".equals(autoCountry) ? null : autoCountry);
	}

	public static boolean active() {
		load();
		return enabled && selected != null;
	}

	public static void setEnabled(boolean on) {
		load();
		enabled = on && selected != null;
		save();
		status = enabled ? "On" : "Off";
		refreshIp();
	}

	public static void select(Server s) {
		load();
		selected = s;
		enabled = s != null;
		connectedAt = System.currentTimeMillis();
		save();
		refreshIp();
	}

	// ------------------------------------------------------------------ the relay on this computer
	private static final Map<String, Integer> RELAYS = new HashMap<>();

	/** Called for every connection the game opens: returns the relay address when Private Connection is on. */
	public static InetSocketAddress redirect(InetSocketAddress original) {
		if (original == null || !active()) {
			return original;
		}
		try {
			var addr = original.getAddress();
			if (addr != null && (addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress() || addr.isAnyLocalAddress())) {
				return original; // your own computer / home network: never through a server abroad
			}
			String host = original.getHostString();
			if (host == null || host.equalsIgnoreCase("localhost") || host.endsWith(".local")) {
				return original;
			}
			return new InetSocketAddress("127.0.0.1", relayPort(host, original.getPort()));
		} catch (IOException | RuntimeException e) {
			System.err.println("[Sleep] private connection relay: " + e);
			return original;
		}
	}

	private static synchronized int relayPort(String host, int port) throws IOException {
		String key = host.toLowerCase(Locale.ROOT) + ":" + port;
		Integer p = RELAYS.get(key);
		if (p != null) {
			return p;
		}
		ServerSocket ss = new ServerSocket(0, 50, java.net.InetAddress.getLoopbackAddress());
		RELAYS.put(key, ss.getLocalPort());
		Thread t = new Thread(() -> {
			while (!ss.isClosed()) {
				try {
					Socket client = ss.accept();
					POOL.execute(() -> handle(client, host, port));
				} catch (IOException e) {
					return;
				}
			}
		}, "Sleep-Relay");
		t.setDaemon(true);
		t.start();
		return ss.getLocalPort();
	}

	private static void handle(Socket client, String host, int port) {
		Socket up = null;
		try {
			Server s = selected;
			if (s == null) {
				client.close();
				return;
			}
			client.setTcpNoDelay(true);
			up = connectWithFailover(s, host, port);
			up.setTcpNoDelay(true);
			final Socket u = up;
			POOL.execute(() -> pipe(u, client));
			pipe(client, up);
		} catch (IOException e) {
			status = dev.solclient.Lang.tr("Could not connect through ", "N\u00e3o consegui ligar por ") + (selected == null ? "?" : selected.label()) + ": " + e.getMessage();
			closeQuietly(client);
			closeQuietly(up);
		}
	}

	/**
	 * Opens the connection through the chosen server; when it is dead or refuses (free servers die all the time) a few others of the
	 * same country that answered before are tried at the same time, the first that works wins and becomes the chosen one.
	 */
	private static Socket connectWithFailover(Server first, String host, int port) throws IOException {
		List<Server> cands = new ArrayList<>();
		cands.add(first);
		if (!first.mine) {
			List<Server> pool = free.get(first.country);
			if (pool != null) {
				for (Server c : pool) {
					if (c != first && c.ping > 0 && cands.size() < 9) {
						cands.add(c);
					}
				}
			}
		}
		if (cands.size() == 1) {
			return openThrough(first, host, port, 8000);
		}
		java.util.concurrent.CompletionService<Object[]> done = new java.util.concurrent.ExecutorCompletionService<>(SCAN);
		java.util.concurrent.atomic.AtomicBoolean won = new java.util.concurrent.atomic.AtomicBoolean();
		for (int i = 0; i < cands.size(); i++) {
			final Server c = cands.get(i);
			final long delay = i == 0 ? 0 : 500L + 250L * i; // the chosen one gets a head start
			done.submit(() -> {
				if (delay > 0) {
					Thread.sleep(delay);
				}
				if (won.get()) {
					return null;
				}
				try {
					Socket sock = openThrough(c, host, port, 5000);
					if (won.compareAndSet(false, true)) {
						return new Object[] {sock, c};
					}
					closeQuietly(sock);
				} catch (IOException e) {
					c.ping = -1;
				}
				return null;
			});
		}
		long end = System.currentTimeMillis() + 14_000L;
		for (int i = 0; i < cands.size(); i++) {
			try {
				var f = done.poll(Math.max(1, end - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
				if (f == null) {
					break;
				}
				Object[] r = f.get();
				if (r != null) {
					Server w = (Server) r[1];
					if (w != first && selected == first) {
						selected = w; // the new one stays for the next connections
						save();
						status = dev.solclient.Lang.tr("Switched to a working server: ", "Mudei para um servidor que funciona: ") + w.label();
						refreshIp();
					}
					return (Socket) r[0];
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			} catch (java.util.concurrent.ExecutionException ignored) {
				// that attempt crashed: the others still count
			}
		}
		won.set(true);
		throw new IOException(dev.solclient.Lang.tr("no free server answered (try Refresh, or use My Server)", "nenhum servidor grátis respondeu (carrega em Atualizar ou usa o Meu servidor)"));
	}

	private static void pipe(Socket from, Socket to) {
		byte[] b = new byte[16384];
		try {
			InputStream in = from.getInputStream();
			OutputStream out = to.getOutputStream();
			int n;
			while ((n = in.read(b)) >= 0) {
				out.write(b, 0, n);
				out.flush();
			}
		} catch (IOException ignored) {
			// one side closed
		} finally {
			closeQuietly(from);
			closeQuietly(to);
		}
	}

	private static void closeQuietly(Socket s) {
		try {
			if (s != null) {
				s.close();
			}
		} catch (IOException ignored) {
			// closed
		}
	}

	// ------------------------------------------------------------------ SOCKS5 / SOCKS4 / HTTP
	/** Opens a connection to host:port through this server (its own kind of proxy). */
	public static Socket openThrough(Server s, String host, int port, int timeoutMs) throws IOException {
		String t = s.type == null ? "socks5" : s.type;
		return switch (t) {
			case "socks4" -> openSocks4(s, host, port, timeoutMs);
			case "http" -> openHttp(s, host, port, timeoutMs);
			default -> openSocks5(s, host, port, timeoutMs);
		};
	}

	/** SOCKS4a: the server looks the name up itself when it is not an IPv4 address. */
	private static Socket openSocks4(Server s, String host, int port, int timeoutMs) throws IOException {
		Socket sock = new Socket();
		try {
			sock.connect(new InetSocketAddress(s.host, s.port), timeoutMs);
			sock.setSoTimeout(timeoutMs);
			java.io.ByteArrayOutputStream req = new java.io.ByteArrayOutputStream();
			req.write(new byte[] {4, 1, (byte) (port >> 8), (byte) port});
			// most SOCKS4 servers do not know SOCKS4a (names): look the name up here and send the IPv4 address
			String target = host;
			if (!host.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
				java.net.InetAddress v4 = null;
				for (java.net.InetAddress a : java.net.InetAddress.getAllByName(host)) {
					if (a instanceof java.net.Inet4Address) {
						v4 = a;
						break;
					}
				}
				if (v4 == null) {
					throw new IOException("no IPv4 address for " + host);
				}
				target = v4.getHostAddress();
			}
			for (String part : target.split("\\.")) {
				req.write(Integer.parseInt(part));
			}
			req.write((s.user == null ? "" : s.user).getBytes(StandardCharsets.UTF_8));
			req.write(0);
			sock.getOutputStream().write(req.toByteArray());
			sock.getOutputStream().flush();
			byte[] r = sock.getInputStream().readNBytes(8);
			if (r.length < 8 || r[1] != 0x5A) {
				throw new IOException("the server refused (SOCKS4)");
			}
			sock.setSoTimeout(0);
			return sock;
		} catch (IOException | RuntimeException e) {
			closeQuietly(sock);
			throw e instanceof IOException io ? io : new IOException(e.getMessage());
		}
	}

	/** HTTP proxy with CONNECT (a tunnel: the bytes pass as they are). */
	private static Socket openHttp(Server s, String host, int port, int timeoutMs) throws IOException {
		Socket sock = new Socket();
		try {
			sock.connect(new InetSocketAddress(s.host, s.port), timeoutMs);
			sock.setSoTimeout(timeoutMs);
			StringBuilder req = new StringBuilder("CONNECT ").append(host).append(':').append(port).append(" HTTP/1.1\r\nHost: ")
					.append(host).append(':').append(port).append("\r\n");
			if (s.user != null && !s.user.isEmpty()) {
				String cred = s.user + ":" + (s.pass == null ? "" : s.pass);
				req.append("Proxy-Authorization: Basic ").append(java.util.Base64.getEncoder().encodeToString(cred.getBytes(StandardCharsets.UTF_8))).append("\r\n");
			}
			req.append("\r\n");
			sock.getOutputStream().write(req.toString().getBytes(StandardCharsets.US_ASCII));
			sock.getOutputStream().flush();
			// read the answer's head byte by byte, so nothing of the tunnel is eaten
			InputStream in = sock.getInputStream();
			StringBuilder head = new StringBuilder();
			while (head.length() < 8192 && !head.toString().endsWith("\r\n\r\n")) {
				int b = in.read();
				if (b < 0) {
					throw new IOException("the server closed the connection");
				}
				head.append((char) b);
			}
			String first = head.toString().split("\r\n", 2)[0];
			if (!first.matches("HTTP/1\\.[01] 200.*")) {
				throw new IOException("the server refused (" + first.trim() + ")");
			}
			sock.setSoTimeout(0);
			return sock;
		} catch (IOException | RuntimeException e) {
			closeQuietly(sock);
			throw e instanceof IOException io ? io : new IOException(e.getMessage());
		}
	}

	private static Socket openSocks5(Server s, String host, int port, int timeoutMs) throws IOException {
		Socket sock = new Socket();
		try {
			sock.connect(new InetSocketAddress(s.host, s.port), timeoutMs);
			sock.setSoTimeout(timeoutMs);
			OutputStream o = sock.getOutputStream();
			InputStream in = sock.getInputStream();
			boolean auth = s.user != null && !s.user.isEmpty();
			o.write(auth ? new byte[] {5, 2, 0, 2} : new byte[] {5, 1, 0});
			o.flush();
			int ver = in.read(), method = in.read();
			if (ver != 5 || method < 0 || method == 0xFF) {
				throw new IOException("the server does not speak SOCKS5 or wants a login");
			}
			if (method == 2) {
				byte[] u = s.user.getBytes(StandardCharsets.UTF_8), p = (s.pass == null ? "" : s.pass).getBytes(StandardCharsets.UTF_8);
				o.write(1);
				o.write(u.length);
				o.write(u);
				o.write(p.length);
				o.write(p);
				o.flush();
				if (in.read() != 1 || in.read() != 0) {
					throw new IOException("wrong user / password");
				}
			}
			byte[] h = host.getBytes(StandardCharsets.UTF_8);
			java.io.ByteArrayOutputStream req = new java.io.ByteArrayOutputStream();
			req.write(new byte[] {5, 1, 0});
			if (host.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
				req.write(1);
				for (String part : host.split("\\.")) {
					req.write(Integer.parseInt(part));
				}
			} else {
				req.write(3);
				req.write(h.length);
				req.write(h);
			}
			req.write(port >> 8);
			req.write(port & 255);
			o.write(req.toByteArray());
			o.flush();
			int v = in.read(), rep = in.read();
			in.read();
			int atyp = in.read();
			if (v != 5 || rep != 0) {
				throw new IOException("the server refused (code " + rep + ")");
			}
			int skip = atyp == 1 ? 4 : atyp == 4 ? 16 : atyp == 3 ? in.read() : 0;
			in.readNBytes(skip + 2);
			sock.setSoTimeout(0);
			return sock;
		} catch (IOException | RuntimeException e) {
			closeQuietly(sock);
			throw e instanceof IOException io ? io : new IOException(e.getMessage());
		}
	}

	/** Round trip through the server in ms, or -1. */
	public static int test(Server s) {
		long t0 = System.nanoTime();
		// HTTP proxies often only tunnel to port 443: test them on 80, so the ones that pass also let the game's port through
		boolean http = "http".equals(s.type);
		try (Socket x = openThrough(s, "1.1.1.1", http ? 80 : 443, TEST_MS)) {
			int ms = (int) ((System.nanoTime() - t0) / 1_000_000L);
			s.ping = Math.max(1, ms);
		} catch (IOException e) {
			s.ping = -1;
		}
		return s.ping;
	}

	// ------------------------------------------------------------------ your public address
	public static void refreshIp() {
		myIp = "...";
		Server s = active() ? selected : null;
		POOL.execute(() -> {
			try {
				Socket x = s == null ? connectDirect("api.ipify.org", 80) : openThrough(s, "api.ipify.org", 80, 6000);
				try (x) {
					x.setSoTimeout(6000);
					x.getOutputStream().write("GET / HTTP/1.1\r\nHost: api.ipify.org\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
					x.getOutputStream().flush();
					String all = new String(x.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
					String ip = all.substring(all.lastIndexOf('\n') + 1).trim();
					myIp = ip.matches("[0-9a-fA-F:.]{3,45}") ? ip : "?";
				}
			} catch (IOException | RuntimeException e) {
				myIp = "?";
			}
		});
	}

	private static Socket connectDirect(String host, int port) throws IOException {
		Socket x = new Socket();
		x.connect(new InetSocketAddress(host, port), 6000);
		return x;
	}

	// ------------------------------------------------------------------ the free servers (public lists)
	/** Per country: {best ping in ms (-1 none yet), servers that answered, servers tested}. */
	public static final Map<String, int[]> COUNTRY = new java.util.concurrent.ConcurrentHashMap<>();
	public static volatile int scanDone, scanTotal;
	private static volatile long scanId;
	/** Tests run on virtual threads (cheap while they wait for the network); this caps how many at the same time. */
	private static final ExecutorService SCAN = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("Sleep-PrivateConnection-Test", 0).factory());
	private static final java.util.concurrent.Semaphore PARALLEL = new java.util.concurrent.Semaphore(96); // fewer tests at once: 384 flooded the network (game lag)
	/** How many servers of each country the background check may try (most free servers are dead)... */
	private static final int SCAN_PER_COUNTRY = 40;
	/** ...it stops for a country once this many answered. */
	private static final int SCAN_ENOUGH = 3;
	/** At most this many servers kept per country (the huge lists are mostly the same few countries). */
	private static final int MAX_PER_COUNTRY = 4000;
	/** A test gives up after this long (connect + handshake). */
	private static final int TEST_MS = 2500;

	/** {kind, list on GitHub (via jsDelivr), format}: csv = proxifly (has the country), txt = host:port per line (country from the IP). */
	private static final String[][] LISTS = {
			{"socks5", "proxifly/free-proxy-list@main/proxies/protocols/socks5/data.csv", "csv"},
			{"socks4", "proxifly/free-proxy-list@main/proxies/protocols/socks4/data.csv", "csv"},
			{"http", "proxifly/free-proxy-list@main/proxies/protocols/http/data.csv", "csv"},
			{"socks5", "TheSpeedX/PROXY-List@master/socks5.txt", "txt"},
			{"socks4", "TheSpeedX/PROXY-List@master/socks4.txt", "txt"},
			{"http", "TheSpeedX/PROXY-List@master/http.txt", "txt"},
			{"socks5", "hookzof/socks5_list@master/proxy.txt", "txt"},
			{"socks5", "ErcinDedeoglu/proxies@main/proxies/socks5.txt", "txt"},
			{"socks5", "MuRongPIG/Proxy-Master@main/socks5.txt", "txt"},
			{"http", "MuRongPIG/Proxy-Master@main/http.txt", "txt"},
			{"socks5", "vakhov/fresh-proxy-list@master/socks5.txt", "txt"},
			{"http", "vakhov/fresh-proxy-list@master/http.txt", "txt"},
			{"socks4", "roosterkid/openproxylist@main/SOCKS4_RAW.txt", "txt"},
			{"socks5", "roosterkid/openproxylist@main/SOCKS5_RAW.txt", "txt"},
			{"socks5", "zloi-user/hideip.me@main/socks5.txt", "txt"},
			{"socks5", "sunny9577/proxy-scraper@master/generated/socks5_proxies.txt", "txt"}};

	private static final java.net.http.HttpClient HTTP = java.net.http.HttpClient.newBuilder()
			.followRedirects(java.net.http.HttpClient.Redirect.ALWAYS).connectTimeout(java.time.Duration.ofSeconds(6)).build();

	private static String fetch(int timeoutS, String... urls) throws Exception {
		Exception last = new IOException("no source");
		for (String u : urls) {
			try {
				var r = HTTP.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(u)).header("User-Agent", "Mozilla/5.0 SleepClient")
						.timeout(java.time.Duration.ofSeconds(timeoutS)).build(), java.net.http.HttpResponse.BodyHandlers.ofString());
				if (r.statusCode() == 200) {
					return r.body();
				}
				last = new IOException("HTTP " + r.statusCode());
			} catch (IOException e) {
				last = e;
			}
		}
		throw last;
	}

	private static String[] mirrors(String gh) {
		String repo = gh.substring(0, gh.indexOf('@')), rest = gh.substring(gh.indexOf('@') + 1);
		String branch = rest.substring(0, rest.indexOf('/')), file = rest.substring(rest.indexOf('/') + 1);
		return new String[] {"https://cdn.jsdelivr.net/gh/" + gh, "https://raw.githubusercontent.com/" + repo + "/" + branch + "/" + file,
				"https://fastly.jsdelivr.net/gh/" + gh};
	}

	// ---- IP -> country (a public range list, kept in the config folder and refreshed once a week)
	private static final String GEO_URL = "https://cdn.jsdelivr.net/npm/@ip-location-db/geo-whois-asn-country/geo-whois-asn-country-ipv4-num.csv";
	private static volatile long[] geoStart = new long[0], geoEnd = new long[0];
	private static volatile String[] geoCc = new String[0];
	/** The country of your own (real) address, for the map's starting point. Empty until known. */
	public static volatile String homeCountry = "";

	private static Path geoFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("kubo-ip-country.csv");
	}

	private static final Object GEO_LOCK = new Object(), LIST_LOCK = new Object();

	// the slow work (downloading the country list, sorting 100 000+ servers) has its own locks, so the game never waits for it
	private static void loadGeo() {
		synchronized (GEO_LOCK) {
			loadGeoLocked();
		}
	}

	private static void loadGeoLocked() {
		if (geoStart.length > 0) {
			return;
		}
		try {
			Path f = geoFile();
			boolean old = !Files.exists(f) || System.currentTimeMillis() - Files.getLastModifiedTime(f).toMillis() > 7L * 24 * 3600 * 1000;
			String body = null;
			if (old) {
				try {
					body = fetch(40, GEO_URL, "https://fastly.jsdelivr.net/npm/@ip-location-db/geo-whois-asn-country/geo-whois-asn-country-ipv4-num.csv");
					Files.writeString(f, body);
				} catch (Exception e) {
					System.err.println("[Sleep] ip-country list: " + e);
				}
			}
			if (body == null && Files.exists(f)) {
				body = Files.readString(f);
			}
			if (body == null) {
				return;
			}
			int n = 0;
			long[] s = new long[400_000], e = new long[400_000];
			String[] cc = new String[400_000];
			Map<String, String> intern = new HashMap<>();
			int i = 0, len = body.length();
			while (i < len && n < s.length) {
				int nl = body.indexOf('\n', i);
				if (nl < 0) {
					nl = len;
				}
				int c1 = body.indexOf(',', i), c2 = c1 < 0 ? -1 : body.indexOf(',', c1 + 1);
				if (c1 > i && c2 > c1 && c2 < nl) {
					try {
						s[n] = Long.parseLong(body, i, c1, 10);
						e[n] = Long.parseLong(body, c1 + 1, c2, 10);
						String code = body.substring(c2 + 1, nl).trim().toUpperCase(Locale.ROOT);
						cc[n] = intern.computeIfAbsent(code, k -> k);
						n++;
					} catch (NumberFormatException ignored) {
						// bad line
					}
				}
				i = nl + 1;
			}
			geoEnd = java.util.Arrays.copyOf(e, n);
			geoCc = java.util.Arrays.copyOf(cc, n);
			geoStart = java.util.Arrays.copyOf(s, n);
		} catch (IOException | RuntimeException e) {
			System.err.println("[Sleep] ip-country list: " + e);
		}
	}

	/** Country code of an IPv4 address, or null. */
	public static String countryOf(String ip) {
		long[] st = geoStart;
		if (st.length == 0) {
			return null;
		}
		long v = 0;
		int part = 0, parts = 0;
		for (int i = 0; i <= ip.length(); i++) {
			char ch = i < ip.length() ? ip.charAt(i) : '.';
			if (ch == '.') {
				if (part > 255) {
					return null;
				}
				v = (v << 8) | part;
				part = 0;
				parts++;
			} else if (ch >= '0' && ch <= '9') {
				part = part * 10 + (ch - '0');
			} else {
				return null;
			}
		}
		if (parts != 4) {
			return null;
		}
		int lo = 0, hi = st.length - 1, at = -1;
		while (lo <= hi) {
			int mid = (lo + hi) >>> 1;
			if (st[mid] <= v) {
				at = mid;
				lo = mid + 1;
			} else {
				hi = mid - 1;
			}
		}
		return at >= 0 && v <= geoEnd[at] ? geoCc[at] : null;
	}

	// ---- the list itself: every server once (kind + host + port), saved so the screen opens instantly next time
	private static final Map<String, Server> ALL = new java.util.concurrent.ConcurrentHashMap<>();
	private static volatile boolean loading;
	public static volatile int listsDone, listsTotal;

	private static Path cacheFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("kubo-private-connection-cache.txt");
	}

	/** Shows the saved list straight away (with the pings it had), then downloads every list again in the background. */
	public static void loadFree() {
		if (loading) {
			return;
		}
		loading = true;
		freeState = dev.solclient.Lang.tr("Loading the free servers...", "A carregar os servidores grátis...");
		POOL.execute(() -> {
			try {
				if (ALL.isEmpty()) {
					readCache();
					if (!ALL.isEmpty()) {
						publish();
						scan();
					}
				}
				POOL.execute(() -> {
					loadGeo();
					if (homeCountry.isEmpty()) {
						findHome();
					}
				});
				List<Future<?>> jobs = new ArrayList<>();
				String[] err = {""};
				listsDone = 0;
				listsTotal = LISTS.length + 1;
				Map<String, Server> fresh = new java.util.concurrent.ConcurrentHashMap<>();
				for (String[] l : LISTS) {
					jobs.add(POOL.submit(() -> {
						try {
							LISTS_PAR.acquire();
						} catch (InterruptedException ie) {
							return;
						}
						try {
							String body = fetch(20, mirrors(l[1]));
							if (l[2].equals("txt")) {
								loadGeo(); // these lists have no country: it comes from the address
							}
							for (String line : body.split("\\R")) {
								parseLine(fresh, l[0], line, l[2].equals("csv"));
							}
						} catch (Exception e) {
							err[0] = String.valueOf(e.getMessage());
						} finally {
							LISTS_PAR.release();
						}
						listsDone++;
						merge(fresh);
					}));
				}
				jobs.add(POOL.submit(() -> {
					// monosans: a smaller list where every server was checked a few minutes ago (with its country)
					try {
						JsonArray arr = GSON.fromJson(fetch(20, mirrors("monosans/proxy-list@main/proxies.json")), JsonArray.class);
						for (JsonElement e : arr) {
							JsonObject o = e.getAsJsonObject();
							String proto = o.get("protocol").getAsString();
							JsonObject geo = o.has("geolocation") && o.get("geolocation").isJsonObject() ? o.getAsJsonObject("geolocation") : null;
							JsonObject cty = geo != null && geo.has("country") && geo.get("country").isJsonObject() ? geo.getAsJsonObject("country") : null;
							if (cty != null && cty.has("iso_code") && (proto.equals("socks5") || proto.equals("socks4") || proto.equals("http"))) {
								add(fresh, proto, o.get("host").getAsString(), o.get("port").getAsString(), cty.get("iso_code").getAsString(), true);
							}
						}
					} catch (Exception e) {
						err[0] = String.valueOf(e.getMessage());
					}
					listsDone++;
					merge(fresh);
				}));
				for (Future<?> f : jobs) {
					try {
						f.get(60, TimeUnit.SECONDS);
					} catch (Exception ignored) {
						// that list is slow / down: the others still count
					}
				}
				if (free.isEmpty()) {
					freeState = dev.solclient.Lang.tr("Could not load the list: ", "Não consegui carregar a lista: ") + err[0];
					return;
				}
				// servers that are no longer on any list go away (keep the ones that answered)
				ALL.entrySet().removeIf(e -> !fresh.containsKey(e.getKey()) && e.getValue().ping <= 0);
				publish();
				writeCache();
				scan();
			} finally {
				loading = false;
			}
		});
	}

	/** One line of a list: "kind://host:port,CC,..." (csv) or "host:port[:anything]" (txt). */
	private static void parseLine(Map<String, Server> into, String type, String line, boolean csv) {
		String t = line.trim();
		if (t.isEmpty() || t.startsWith("#")) {
			return;
		}
		String cc = null;
		if (csv) {
			String[] f = t.split(",");
			if (f.length < 2 || !f[1].matches("[A-Za-z]{2}")) {
				return;
			}
			cc = f[1];
			t = f[0];
		}
		t = t.replaceFirst("^\\w+://", "");
		int c1 = t.indexOf(':');
		if (c1 <= 0) {
			return;
		}
		int c2 = t.indexOf(':', c1 + 1);
		String host = t.substring(0, c1), port = c2 < 0 ? t.substring(c1 + 1) : t.substring(c1 + 1, c2);
		if (cc == null) {
			cc = countryOf(host);
			if (cc == null) {
				return;
			}
		}
		add(into, type, host, port.trim(), cc, false);
	}

	private static void add(Map<String, Server> all, String type, String host, String port, String cc, boolean verified) {
		try {
			Server s = new Server();
			s.type = type;
			s.host = host;
			s.port = Integer.parseInt(port.trim());
			if (s.port <= 0 || s.port > 65535) {
				return;
			}
			s.country = cc.toUpperCase(Locale.ROOT);
			s.verified = verified;
			Server old = all.putIfAbsent(type + "://" + host + ":" + s.port, s);
			if (old != null && verified) {
				old.verified = true;
			}
		} catch (NumberFormatException ignored) {
			// bad line
		}
	}

	/** New servers join the list (old ones keep their ping), then the list on screen is rebuilt. */
	private static void merge(Map<String, Server> fresh) {
		synchronized (LIST_LOCK) {
			mergeLocked(fresh);
		}
	}

	private static void mergeLocked(Map<String, Server> fresh) {
		for (Map.Entry<String, Server> e : fresh.entrySet()) {
			Server old = ALL.putIfAbsent(e.getKey(), e.getValue());
			if (old != null && e.getValue().verified) {
				old.verified = true;
			}
		}
		publish();
	}

	/** Groups the servers by country (best first) and swaps them in for the screen. */
	private static volatile long lastPublish;
	private static final java.util.concurrent.atomic.AtomicBoolean PUBLISH_LATER = new java.util.concurrent.atomic.AtomicBoolean();

	/**
	 * Re-sorts the 100 000+ servers for the screen. Called a lot while lists arrive and servers answer, so it runs at most
	 * every 1.5 s (the calls in between are merged into one): sorting them on every answer made everything lag.
	 */
	private static void publish() {
		long now = System.currentTimeMillis();
		if (now - lastPublish < 1500) {
			if (PUBLISH_LATER.compareAndSet(false, true)) {
				POOL.execute(() -> {
					try {
						Thread.sleep(1500);
					} catch (InterruptedException ignored) {
						// publish now then
					}
					PUBLISH_LATER.set(false);
					publishNow();
				});
			}
			return;
		}
		publishNow();
	}

	private static void publishNow() {
		synchronized (LIST_LOCK) {
			lastPublish = System.currentTimeMillis();
			publishLocked();
		}
	}

	private static void publishLocked() {
		Map<String, List<Server>> by = new TreeMap<>();
		for (Server s : ALL.values()) {
			by.computeIfAbsent(s.country, k -> new ArrayList<>()).add(s);
		}
		int total = 0;
		for (Map.Entry<String, List<Server>> e : by.entrySet()) {
			List<Server> l = e.getValue();
			// answered (fastest first), then checked lists, then SOCKS5, SOCKS4, HTTP
			l.sort(java.util.Comparator.comparingInt((Server s) -> s.ping > 0 ? s.ping : Integer.MAX_VALUE).thenComparing(s -> !s.verified)
					.thenComparingInt(s -> "socks5".equals(s.type) ? 0 : "socks4".equals(s.type) ? 1 : 2));
			if (l.size() > MAX_PER_COUNTRY) {
				e.setValue(new ArrayList<>(l.subList(0, MAX_PER_COUNTRY)));
			}
			total += e.getValue().size();
		}
		if (by.isEmpty()) {
			return;
		}
		free = by;
		String part = listsTotal > 0 && listsDone < listsTotal ? "  ·  " + listsDone + "/" + listsTotal + dev.solclient.Lang.tr(" lists", " listas") : "";
		freeState = String.format(Locale.ROOT, "%,d", total).replace(',', ' ') + dev.solclient.Lang.tr(" free servers in ", " servidores grátis em ")
				+ by.size() + dev.solclient.Lang.tr(" countries", " países") + part;
	}

	private static void readCache() {
		try {
			Path f = cacheFile();
			if (!Files.exists(f)) {
				return;
			}
			for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
				String[] p = line.split(",");
				if (p.length < 6) {
					continue;
				}
				Server s = new Server();
				s.type = p[0];
				s.host = p[1];
				s.port = Integer.parseInt(p[2]);
				s.country = p[3];
				s.ping = Integer.parseInt(p[4]) > 0 ? Integer.parseInt(p[4]) : -2;
				s.verified = p[5].equals("1");
				ALL.putIfAbsent(s.type + "://" + s.host + ":" + s.port, s);
			}
		} catch (IOException | RuntimeException e) {
			System.err.println("[Sleep] private connection cache: " + e);
		}
	}

	private static void writeCache() {
		StringBuilder sb = new StringBuilder(ALL.size() * 40);
		for (List<Server> l : free.values()) {
			for (Server s : l) {
				sb.append(s.type).append(',').append(s.host).append(',').append(s.port).append(',').append(s.country).append(',')
						.append(Math.max(0, s.ping)).append(',').append(s.verified ? '1' : '0').append('\n');
			}
		}
		try {
			Files.writeString(cacheFile(), sb);
		} catch (IOException e) {
			System.err.println("[Sleep] private connection cache: " + e);
		}
	}

	/** Your real address's country (asked once, without the server). */
	private static void findHome() {
		try (Socket x = connectDirect("api.ipify.org", 80)) {
			x.setSoTimeout(6000);
			x.getOutputStream().write("GET / HTTP/1.1\r\nHost: api.ipify.org\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
			x.getOutputStream().flush();
			String all = new String(x.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
			String cc = countryOf(all.substring(all.lastIndexOf('\n') + 1).trim());
			if (cc != null) {
				homeCountry = cc;
			}
		} catch (IOException | RuntimeException ignored) {
			// the map keeps its default starting point
		}
	}

	/** Runs a test with the shared limit on how many run at the same time. */
	private static int limitedTest(Server s, long id, boolean scanJob) {
		try {
			PARALLEL.acquire();
		} catch (InterruptedException e) {
			return -1;
		}
		try {
			if (scanJob && id != scanId) {
				return -2;
			}
			int ms = test(s);
			record(s, ms);
			return ms;
		} finally {
			PARALLEL.release();
		}
	}

	/** Checks a few servers of every country in the background, so the list shows which countries work and how fast. */
	public static void scan() {
		long id = ++scanId;
		Map<String, List<Server>> by = free;
		List<Server> todo = new ArrayList<>();
		for (Map.Entry<String, List<Server>> e : by.entrySet()) {
			COUNTRY.putIfAbsent(e.getKey(), new int[] {-1, 0, 0});
			List<Server> l = e.getValue();
			// the ones that answered last time and checked ones first, then a random few
			int head = 0;
			while (head < l.size() && (l.get(head).ping > 0 || l.get(head).verified) && head < 4) {
				head++;
			}
			List<Server> rest = new ArrayList<>(l.subList(head, Math.min(l.size(), 1500)));
			Collections.shuffle(rest);
			todo.addAll(l.subList(0, head));
			todo.addAll(rest.subList(0, Math.min(rest.size(), SCAN_PER_COUNTRY - head)));
		}
		// every country moves forward together (one server of each, then the next...), not all of one country first
		Map<String, java.util.ArrayDeque<Server>> per = new java.util.LinkedHashMap<>();
		for (Server s : todo) {
			per.computeIfAbsent(s.country, k -> new java.util.ArrayDeque<>()).add(s);
		}
		List<Server> order = new ArrayList<>(todo.size());
		while (!per.isEmpty()) {
			per.values().removeIf(q -> {
				order.add(q.poll());
				return q.isEmpty();
			});
		}
		scanDone = 0;
		scanTotal = order.size();
		for (Server s : order) {
			SCAN.execute(() -> {
				if (id != scanId) {
					return; // a newer check started (Refresh)
				}
				int[] st = COUNTRY.get(s.country);
				if (st != null && st[1] >= SCAN_ENOUGH && s.ping <= 0) {
					scanDone++; // this country already has enough working servers
					return;
				}
				if (limitedTest(s, id, true) != -2) {
					scanDone++;
				}
				if (scanDone >= scanTotal) {
					publish(); // working servers to the top of each country
					writeCache();
				}
			});
		}
	}

	private static void record(Server s, int ms) {
		int[] st = COUNTRY.computeIfAbsent(s.country, k -> new int[] {-1, 0, 0});
		synchronized (st) {
			st[2]++;
			if (ms > 0) {
				st[1]++;
				if (st[0] < 0 || ms < st[0]) {
					st[0] = ms;
				}
			}
		}
	}

	public static boolean scanning() {
		return scanTotal > 0 && scanDone < scanTotal;
	}

	/** The country with the lowest ping found so far, or null. */
	public static String fastestCountry() {
		String best = null;
		int bp = Integer.MAX_VALUE;
		for (Map.Entry<String, int[]> e : COUNTRY.entrySet()) {
			int p = e.getValue()[0];
			if (p > 0 && p < bp) {
				bp = p;
				best = e.getKey();
			}
		}
		return best;
	}

	/**
	 * Connects to a fast server of a country (null = anywhere) as quickly as possible: many servers are tried at the same
	 * time (the ones that answered before first) and the first good answer wins, without waiting for the slow ones.
	 */
	public static void connectBest(String country) {
		status = dev.solclient.Lang.tr("Looking for a fast server...", "A procurar um servidor rápido...");
		connecting = country == null ? "*" : country;
		POOL.execute(() -> {
			try {
				List<Server> pool = new ArrayList<>();
				if (country == null) {
					for (List<Server> l : free.values()) {
						pool.addAll(l);
					}
				} else {
					pool.addAll(free.getOrDefault(country, List.of()));
				}
				if (pool.isEmpty()) {
					status = dev.solclient.Lang.tr("No servers loaded yet", "Ainda não há servidores carregados");
					return;
				}
				List<Server> good = new ArrayList<>(), fresh = new ArrayList<>(), failed = new ArrayList<>();
				for (Server s : pool) {
					(s.ping > 0 ? good : s.ping == -2 ? fresh : failed).add(s);
				}
				good.sort(java.util.Comparator.comparingInt(s -> s.ping));
				Collections.shuffle(fresh);
				fresh.sort(java.util.Comparator.comparing(s -> !s.verified));
				Collections.shuffle(failed);
				List<Server> order = new ArrayList<>(good.subList(0, Math.min(16, good.size())));
				order.addAll(fresh);
				order.addAll(failed); // they may work now
				int tried = 0;
				for (int wave = 0; wave < 4 && tried < order.size(); wave++) {
					List<Server> batch = order.subList(tried, Math.min(order.size(), tried + (wave == 0 ? 200 : 350)));
					tried += batch.size();
					Server best = race(batch);
					if (best != null) {
						select(best);
						connectedAt = System.currentTimeMillis();
						status = dev.solclient.Lang.tr("Connected via ", "Ligado via ") + best.country + " (" + best.ping + " ms)";
						publish();
						return;
					}
				}
				status = dev.solclient.Lang.tr("None of the " + tried + " servers tried answered - try again", "Nenhum dos " + tried + " servidores testados respondeu - tenta outra vez");
			} finally {
				connecting = null;
			}
		});
	}

	/** Tests the batch all at once; returns early with a fast server, or the best one ~0.6 s after the first answer. */
	private static Server race(List<Server> batch) {
		java.util.concurrent.LinkedBlockingQueue<Server> ok = new java.util.concurrent.LinkedBlockingQueue<>();
		java.util.concurrent.atomic.AtomicInteger left = new java.util.concurrent.atomic.AtomicInteger(batch.size());
		java.util.concurrent.atomic.AtomicBoolean done = new java.util.concurrent.atomic.AtomicBoolean();
		for (Server s : batch) {
			SCAN.execute(() -> {
				if (!done.get()) {
					int ms = limitedTest(s, 0, false);
					if (ms > 0) {
						ok.add(s);
					}
				}
				left.decrementAndGet();
			});
		}
		Server best = null;
		long firstAt = 0, deadline = System.currentTimeMillis() + TEST_MS * 2L + 500;
		try {
			while (System.currentTimeMillis() < deadline) {
				long wait = best == null ? 100 : Math.max(1, firstAt + 600 - System.currentTimeMillis());
				Server s = ok.poll(Math.min(wait, 100), TimeUnit.MILLISECONDS);
				if (s != null && (best == null || s.ping < best.ping)) {
					if (best == null) {
						firstAt = System.currentTimeMillis();
					}
					best = s;
				}
				if (best != null && (best.ping < 250 || System.currentTimeMillis() - firstAt >= 600)) {
					break;
				}
				if (best == null && left.get() == 0 && ok.isEmpty()) {
					break;
				}
			}
		} catch (InterruptedException ignored) {
			// give what we have
		} finally {
			done.set(true);
		}
		return best;
	}

	// ---- live latency of the server in use (the little graph on the screen)
	public static final int[] LATENCY = new int[40];
	public static volatile int latencyCount;
	private static volatile long lastLatencyAt;

	/** Re-tests the server in use every few seconds while the screen is open. */
	public static void pollLatency() {
		Server s = selected;
		long now = System.currentTimeMillis();
		if (!active() || s == null || now - lastLatencyAt < 5000) {
			return;
		}
		lastLatencyAt = now;
		SCAN.execute(() -> {
			int ms = test(s);
			synchronized (LATENCY) {
				System.arraycopy(LATENCY, 1, LATENCY, 0, LATENCY.length - 1);
				LATENCY[LATENCY.length - 1] = Math.max(0, ms);
				latencyCount = Math.min(LATENCY.length, latencyCount + 1);
			}
		});
	}

	/** Country being connected to right now ("*" = anywhere), or null. */
	public static volatile String connecting;
	/** When the current connection started (for the "Protected · 12 min" line). */
	public static volatile long connectedAt;

	// ------------------------------------------------------------------ your own servers
	/** Accepts host:port, host:port:user:pass or socks5://user:pass@host:port. */
	public static Server parse(String text) {
		if (text == null) {
			return null;
		}
		String low = text.trim().toLowerCase(Locale.ROOT);
		String t = text.trim().replaceFirst("^\\w+://", "");
		Server s = new Server();
		s.mine = true;
		s.type = low.startsWith("socks4") ? "socks4" : low.startsWith("http") ? "http" : "socks5";
		try {
			if (t.contains("@")) {
				String cred = t.substring(0, t.lastIndexOf('@')), hp = t.substring(t.lastIndexOf('@') + 1);
				int c = cred.indexOf(':');
				s.user = c < 0 ? cred : cred.substring(0, c);
				s.pass = c < 0 ? "" : cred.substring(c + 1);
				t = hp;
			}
			String[] p = t.split(":");
			s.host = p[0];
			s.port = Integer.parseInt(p[1].replaceAll("[^0-9]", ""));
			if (p.length >= 4) {
				s.user = p[2];
				s.pass = p[3];
			}
			if (s.host.isEmpty() || s.port <= 0 || s.port > 65535) {
				return null;
			}
			s.name = s.host;
			return s;
		} catch (RuntimeException e) {
			return null;
		}
	}

	public static void addMine(Server s) {
		load();
		MINE.add(s);
		save();
	}

	public static void removeMine(Server s) {
		MINE.remove(s);
		if (selected != null && selected.host.equals(s.host) && selected.port == s.port) {
			selected = null;
			enabled = false;
		}
		save();
	}
}
