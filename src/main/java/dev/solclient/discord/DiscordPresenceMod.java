package dev.solclient.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.solclient.mods.Mod;
import dev.solclient.option.Option;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.network.ServerInfo;

/**
 * Discord Rich Presence: your Discord profile shows "Playing Caos Client" with the Plegumo logo, what you are doing
 * (menus / singleplayer / the server) and for how long. Talks to the Discord app on your computer through its local
 * IPC socket (no internet, no token). The name and logo come from a Discord application: see DISCORD-SETUP.md.
 */
public class DiscordPresenceMod extends Mod {

	/** Application ID of the "Caos Client" app on discord.com/developers (can be changed in config/plegumo-discord.json). */
	public static final String DEFAULT_CLIENT_ID = "1560746654989160523";

	public final Option.Bool showServer = bool("Show Server Address", true);
	public final Option.Bool showTime = bool("Show Elapsed Time", true);

	private volatile Thread worker;
	private volatile boolean running;
	private final long startedAt = System.currentTimeMillis() / 1000L;
	public static volatile String status = "Not connected";

	public DiscordPresenceMod() {
		super("Discord Status", "discord", "Shows \"Playing Caos Client\" with the logo on your Discord profile.", Category.UTILITY);
		action("Discord", "Reconnect", this::restart, () -> status);
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}

	@Override
	protected void onEnable() {
		start();
	}

	@Override
	protected void onDisable() {
		stop();
	}

	@Override
	public void tick() {
		if (worker == null) {
			start();
		}
	}

	public void startBackground() {
		start();
	}

	private void restart() {
		stop();
		start();
	}

	private synchronized void start() {
		if (worker != null) {
			return;
		}
		running = true;
		Thread t = new Thread(this::loop, "Caos Discord presence");
		t.setDaemon(true);
		worker = t;
		t.start();
	}

	private synchronized void stop() {
		running = false;
		Thread t = worker;
		worker = null;
		if (t != null) {
			t.interrupt();
		}
		status = "Off";
	}

	// ---------------------------------------------------------------- config
	private static String clientId() {
		try {
			Path p = FabricLoader.getInstance().getConfigDir().resolve("plegumo-discord.json");
			if (Files.exists(p)) {
				JsonObject o = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
				if (o.has("clientId") && !o.get("clientId").getAsString().isBlank()) {
					return o.get("clientId").getAsString().trim();
				}
			} else {
				Files.writeString(p, "{\n  \"clientId\": \"" + DEFAULT_CLIENT_ID + "\",\n  \"largeImage\": \"logo\",\n  \"buttonLabel\": \"\",\n  \"buttonUrl\": \"\"\n}\n");
			}
		} catch (Exception ignored) {
		}
		return DEFAULT_CLIENT_ID;
	}

	private static String conf(String key, String def) {
		try {
			Path p = FabricLoader.getInstance().getConfigDir().resolve("plegumo-discord.json");
			JsonObject o = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
			return o.has(key) ? o.get(key).getAsString() : def;
		} catch (Exception e) {
			return def;
		}
	}

	// ---------------------------------------------------------------- IPC
	private interface Pipe extends AutoCloseable {
		InputStream in();

		OutputStream out();

		@Override
		void close();
	}

	private static Pipe open() {
		boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
		for (int i = 0; i < 10; i++) {
			if (windows) {
				try {
					RandomAccessFile f = new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw");
					return new Pipe() {
						final InputStream in = new InputStream() {
							@Override
							public int read() throws java.io.IOException {
								return f.read();
							}

							@Override
							public int read(byte[] b, int off, int len) throws java.io.IOException {
								return f.read(b, off, len);
							}
						};
						final OutputStream out = new OutputStream() {
							@Override
							public void write(int b) throws java.io.IOException {
								f.write(b);
							}

							@Override
							public void write(byte[] b, int off, int len) throws java.io.IOException {
								f.write(b, off, len);
							}
						};

						public InputStream in() {
							return in;
						}

						public OutputStream out() {
							return out;
						}

						public void close() {
							try {
								f.close();
							} catch (Exception ignored) {
							}
						}
					};
				} catch (Exception ignored) {
				}
			} else {
				for (String dir : new String[]{System.getenv("XDG_RUNTIME_DIR"), System.getenv("TMPDIR"), System.getenv("TMP"), System.getenv("TEMP"), "/tmp"}) {
					if (dir == null || dir.isBlank()) {
						continue;
					}
					Path p = Path.of(dir, "discord-ipc-" + i);
					if (!Files.exists(p)) {
						continue;
					}
					try {
						SocketChannel ch = SocketChannel.open(UnixDomainSocketAddress.of(p));
						InputStream in = Channels.newInputStream(ch);
						OutputStream out = Channels.newOutputStream(ch);
						return new Pipe() {
							public InputStream in() {
								return in;
							}

							public OutputStream out() {
								return out;
							}

							public void close() {
								try {
									ch.close();
								} catch (Exception ignored) {
								}
							}
						};
					} catch (Exception ignored) {
					}
				}
			}
		}
		return null;
	}

	private static void write(OutputStream out, int op, String json) throws java.io.IOException {
		byte[] data = json.getBytes(StandardCharsets.UTF_8);
		ByteBuffer b = ByteBuffer.allocate(8 + data.length).order(ByteOrder.LITTLE_ENDIAN);
		b.putInt(op).putInt(data.length).put(data);
		out.write(b.array());
		out.flush();
	}

	/** Reads one frame (and ignores it). Returns the JSON or null when the pipe closed. */
	private static String read(InputStream in) throws java.io.IOException {
		byte[] head = in.readNBytes(8);
		if (head.length < 8) {
			return null;
		}
		ByteBuffer h = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
		h.getInt();
		int len = h.getInt();
		if (len < 0 || len > 1 << 20) {
			return null;
		}
		return new String(in.readNBytes(len), StandardCharsets.UTF_8);
	}

	// ---------------------------------------------------------------- loop
	private void loop() {
		while (running) {
			String id = clientId();
			if (id.isBlank()) {
				status = "Set your Discord Application ID (see DISCORD-SETUP.md)";
				caos(10_000);
				continue;
			}
			Pipe pipe = open();
			if (pipe == null) {
				status = "Discord is not open";
				caos(15_000);
				continue;
			}
			try (pipe) {
				write(pipe.out(), 0, "{\"v\":1,\"client_id\":\"" + id + "\"}");
				String ready = read(pipe.in());
				if (ready == null || ready.contains("\"evt\":\"ERROR\"")) {
					status = "Discord refused the Application ID";
					caos(30_000);
					continue;
				}
				status = "Connected";
				String last = null;
				while (running) {
					String act = activity();
					if (!act.equals(last)) {
						write(pipe.out(), 1, act);
						read(pipe.in());
						last = act;
					}
					caos(5_000);
				}
				// clear the activity when turned off
				write(pipe.out(), 1, "{\"cmd\":\"SET_ACTIVITY\",\"args\":{\"pid\":" + ProcessHandle.current().pid() + "},\"nonce\":\"" + UUID.randomUUID() + "\"}");
			} catch (Exception e) {
				status = "Lost Discord, retrying...";
				caos(10_000);
			}
		}
	}

	private static void caos(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/** SET_ACTIVITY payload for what you are doing now (only changes are sent). */
	private String activity() {
		String details;
		String state = null;
		int[] party = null;
		String[] slogans = {"Playing above the clouds", "Caos tight. Play fast."};
		String slogan = slogans[(int) ((System.currentTimeMillis() / 60_000L) % slogans.length)];
		if (mc.world == null) {
			details = "In the menus";
			state = slogan;
		} else if (mc.isInSingleplayer()) {
			details = slogan;
			state = "Playing Singleplayer";
		} else {
			details = slogan;
			ServerInfo s = mc.getCurrentServerEntry();
			int online = 0, max = 0;
			try {
				if (mc.getNetworkHandler() != null) {
					online = mc.getNetworkHandler().getPlayerList().size();
				}
				if (s != null && s.players != null) {
					max = s.players.max();
					online = Math.max(online, Math.min(s.players.online(), 1_000_000));
				}
			} catch (RuntimeException ignored) {
			}
			if (s != null && showServer.on() && s.address != null && online >= 50) {
				// big public server: show its address (like Essential)
				state = "Playing on " + s.address.toLowerCase(java.util.Locale.ROOT).replaceAll(":25565$", "");
			} else {
				// small / private server: keep the address private, show how many are playing
				state = "Playing on a Minecraft server";
				if (online > 0) {
					party = new int[] {online, Math.max(online, max)};
				}
			}
		}
		JsonObject a = new JsonObject();
		a.addProperty("details", details);
		if (state != null) {
			a.addProperty("state", state);
		}
		if (party != null) { // shows "(3 of 4)" next to the state
			JsonObject pt = new JsonObject();
			pt.addProperty("id", "kubo-" + Integer.toHexString(String.valueOf(mc.getCurrentServerEntry() == null ? "" : mc.getCurrentServerEntry().address).hashCode()));
			JsonArray size = new JsonArray();
			size.add(party[0]);
			size.add(party[1]);
			pt.add("size", size);
			a.add("party", pt);
		}
		if (showTime.on()) {
			JsonObject ts = new JsonObject();
			ts.addProperty("start", startedAt);
			a.add("timestamps", ts);
		}
		JsonObject assets = new JsonObject();
		assets.addProperty("large_image", conf("largeImage", "logo"));
		assets.addProperty("large_text", "Caos Client");
		a.add("assets", assets);
		String label = conf("buttonLabel", ""), url = conf("buttonUrl", "");
		if (!label.isBlank() && url.startsWith("https://")) {
			JsonArray buttons = new JsonArray();
			JsonObject b = new JsonObject();
			b.addProperty("label", label.length() > 32 ? label.substring(0, 32) : label);
			b.addProperty("url", url);
			buttons.add(b);
			a.add("buttons", buttons);
		}
		JsonObject args = new JsonObject();
		args.addProperty("pid", ProcessHandle.current().pid());
		args.add("activity", a);
		JsonObject o = new JsonObject();
		o.addProperty("cmd", "SET_ACTIVITY");
		o.add("args", args);
		o.addProperty("nonce", "kubo-" + details.hashCode() + "-" + (state == null ? 0 : state.hashCode()) + "-" + (party == null ? 0 : party[0] * 31 + party[1]));
		return o.toString();
	}
}
