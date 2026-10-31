package dev.solclient.social;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

/**
 * Asks a Minecraft server for its card (the same "server list ping" the multiplayer menu does): icon, MOTD,
 * players and ping. Used by the server invites in the friend chat. Hosted worlds (host.endlock.net/name) are asked
 * through the EndLock page instead. Results are cached for a minute.
 */
public final class ServerPing {

	public static final class Info {
		public volatile boolean done, online;
		public volatile String motd = "", version = "";
		public volatile int players = -1, max = -1;
		public volatile long ping = -1;
		public volatile Identifier icon;
		volatile byte[] iconPng;
		final long at = System.currentTimeMillis();
	}

	private static final Map<String, Info> CACHE = new ConcurrentHashMap<>();

	private ServerPing() {
	}

	/** Never blocks: starts the ping the first time and returns what is known so far. */
	public static Info get(String address) {
		String key = address.trim().toLowerCase(java.util.Locale.ROOT);
		Info i = CACHE.get(key);
		if (i != null && System.currentTimeMillis() - i.at < 60_000L) {
			uploadIcon(key, i);
			return i;
		}
		Info fresh = new Info();
		if (i != null) { // keep showing the old card while it refreshes
			fresh.motd = i.motd;
			fresh.players = i.players;
			fresh.max = i.max;
			fresh.icon = i.icon;
			fresh.online = i.online;
		}
		CACHE.put(key, fresh);
		Thread t = new Thread(() -> run(address, fresh), "kubo-server-ping");
		t.setDaemon(true);
		t.start();
		return fresh;
	}

	private static void uploadIcon(String key, Info i) {
		byte[] png = i.iconPng;
		if (png == null || i.icon != null) {
			return;
		}
		i.iconPng = null;
		try {
			NativeImage img = NativeImage.read(png);
			String h = Integer.toHexString(key.hashCode());
			NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "kubo_server_icon_" + h, img);
			tex.upload();
			Identifier id = dev.solclient.Gfx.id("server_icon_" + h);
			MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
			i.icon = id;
		} catch (Exception ignored) {
		}
	}

	private static void run(String address, Info info) {
		try {
			if (KuboRelay.isRelayAddress(address)) {
				hosted(address, info);
			} else {
				ping(address, info);
			}
		} catch (Exception e) {
			info.online = false;
		} finally {
			info.done = true;
		}
	}

	/** host.endlock.net/<name>?json -> {hosting, world, players, slots} */
	private static void hosted(String address, Info info) throws Exception {
		String a = address.replaceFirst("(?i)^(kubo:|wss?://|https?://)", "");
		long t0 = System.currentTimeMillis();
		var res = java.net.http.HttpClient.newHttpClient().send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("https://" + a + "?json"))
				.timeout(java.time.Duration.ofSeconds(8)).build(), java.net.http.HttpResponse.BodyHandlers.ofString());
		JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
		info.ping = Math.max(1, System.currentTimeMillis() - t0);
		info.online = o.has("hosting") && o.get("hosting").getAsBoolean();
		if (info.online) {
			info.motd = o.has("world") ? o.get("world").getAsString() : "";
			info.players = o.has("players") ? o.get("players").getAsInt() : -1;
			info.max = o.has("slots") ? o.get("slots").getAsInt() : -1;
		} else {
			info.motd = "Not hosting right now";
		}
	}

	private static void ping(String address, Info info) throws IOException {
		String host = address.trim();
		int port = 25565;
		int colon = host.lastIndexOf(':');
		if (colon > 0 && host.indexOf(':') == colon) {
			try {
				port = Integer.parseInt(host.substring(colon + 1));
				host = host.substring(0, colon);
			} catch (NumberFormatException ignored) {
			}
		} else {
			String[] srv = srv(host);
			if (srv != null) {
				host = srv[0];
				port = Integer.parseInt(srv[1]);
			}
		}
		try (Socket s = new Socket()) {
			s.connect(new InetSocketAddress(host, port), 5000);
			s.setSoTimeout(5000);
			OutputStream out = s.getOutputStream();
			DataInputStream in = new DataInputStream(s.getInputStream());
			// handshake: id 0, protocol -1, host, port, next state 1 (status)
			ByteArrayOutputStream hs = new ByteArrayOutputStream();
			varInt(hs, 0);
			varInt(hs, -1);
			byte[] hb = host.getBytes(StandardCharsets.UTF_8);
			varInt(hs, hb.length);
			hs.write(hb);
			hs.write((port >> 8) & 0xFF);
			hs.write(port & 0xFF);
			varInt(hs, 1);
			packet(out, hs.toByteArray());
			packet(out, new byte[] {0}); // status request
			readVarInt(in); // length
			readVarInt(in); // id
			int len = readVarInt(in);
			byte[] json = new byte[len];
			in.readFully(json);
			// ping
			long t0 = System.currentTimeMillis();
			ByteArrayOutputStream pp = new ByteArrayOutputStream();
			varInt(pp, 1);
			for (int i = 0; i < 8; i++) {
				pp.write(0);
			}
			packet(out, pp.toByteArray());
			try {
				readVarInt(in);
				info.ping = System.currentTimeMillis() - t0;
			} catch (IOException e) {
				info.ping = System.currentTimeMillis() - t0;
			}
			JsonObject o = JsonParser.parseString(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
			info.online = true;
			info.motd = plain(o.get("description")).replaceAll("§.", "").replaceAll("\\s+\\n", "\n").strip();
			if (o.has("players")) {
				JsonObject p = o.getAsJsonObject("players");
				info.players = p.has("online") ? p.get("online").getAsInt() : -1;
				info.max = p.has("max") ? p.get("max").getAsInt() : -1;
			}
			if (o.has("version") && o.getAsJsonObject("version").has("name")) {
				info.version = o.getAsJsonObject("version").get("name").getAsString();
			}
			if (o.has("favicon")) {
				String f = o.get("favicon").getAsString();
				int comma = f.indexOf(',');
				info.iconPng = Base64.getDecoder().decode(f.substring(comma + 1).replace("\n", ""));
			}
		}
	}

	private static String plain(JsonElement e) {
		if (e == null || e.isJsonNull()) {
			return "";
		}
		if (e.isJsonPrimitive()) {
			return e.getAsString();
		}
		StringBuilder b = new StringBuilder();
		if (e.isJsonArray()) {
			for (JsonElement x : e.getAsJsonArray()) {
				b.append(plain(x));
			}
			return b.toString();
		}
		JsonObject o = e.getAsJsonObject();
		if (o.has("text")) {
			b.append(o.get("text").getAsString());
		}
		if (o.has("extra")) {
			b.append(plain(o.get("extra")));
		}
		return b.toString();
	}

	private static String[] srv(String host) {
		try {
			var env = new java.util.Hashtable<String, String>();
			env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
			var ctx = new javax.naming.directory.InitialDirContext(env);
			var attrs = ctx.getAttributes("_minecraft._tcp." + host, new String[] {"SRV"});
			var a = attrs.get("SRV");
			if (a == null) {
				return null;
			}
			String[] parts = a.get().toString().split(" ");
			String target = parts[3].endsWith(".") ? parts[3].substring(0, parts[3].length() - 1) : parts[3];
			return new String[] {target, parts[2]};
		} catch (Exception e) {
			return null;
		}
	}

	private static void packet(OutputStream out, byte[] body) throws IOException {
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		varInt(b, body.length);
		b.write(body);
		out.write(b.toByteArray());
		out.flush();
	}

	private static void varInt(ByteArrayOutputStream o, int v) {
		while (true) {
			if ((v & ~0x7F) == 0) {
				o.write(v);
				return;
			}
			o.write((v & 0x7F) | 0x80);
			v >>>= 7;
		}
	}

	private static int readVarInt(InputStream in) throws IOException {
		int v = 0, shift = 0;
		while (true) {
			int b = in.read();
			if (b < 0) {
				throw new IOException("closed");
			}
			v |= (b & 0x7F) << shift;
			if ((b & 0x80) == 0) {
				return v;
			}
			shift += 7;
			if (shift > 35) {
				throw new IOException("varint");
			}
		}
	}
}
