package dev.solclient.music;

import com.google.gson.JsonObject;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

/**
 * Music Share, "stream my sound": the sound of THIS computer (macOS, through the audio helper) goes to the sync Worker in real time
 * and the Worker hands it to the friends who listen along. Everything runs on its own threads (capture, send, receive, play): the game's
 * render thread never waits for any of it. Format on the wire: 24 kHz mono, mu-law, 40 ms per message (960 bytes, ~24 KB/s).
 */
public final class AudioStream {

	private static final String OS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
	private static final boolean MAC = OS.contains("mac"), WIN = OS.contains("win");
	private static final int RATE = 24000;
	private static final int FRAME_SAMPLES = 960; // 40 ms
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	/** What the settings / chat can say about it (permission missing, ...). */
	public static volatile String status = "";

	private AudioStream() {
	}

	private static volatile String[] linuxCmd;

	/** The command that records what this Linux plays (PulseAudio / PipeWire, from the "monitor" of the output), or null. */
	private static String[] linuxCommand() {
		String[] c = linuxCmd;
		if (c != null) {
			return c.length == 0 ? null : c;
		}
		String[][] tries = {
				{"parec", "--format=s16le", "--rate=24000", "--channels=1", "--latency-msec=40", "-d", "@DEFAULT_MONITOR@"},
				{"pw-record", "--rate", "24000", "--channels", "1", "--format", "s16", "-P", "stream.capture.sink=true", "-"},
		};
		for (String[] t : tries) {
			try {
				Process p = new ProcessBuilder("sh", "-c", "command -v " + t[0]).redirectErrorStream(true).start();
				if (p.waitFor(3, TimeUnit.SECONDS) && p.exitValue() == 0) {
					linuxCmd = t;
					return t;
				}
			} catch (Exception ignored) {
				// next
			}
		}
		linuxCmd = new String[0];
		return null;
	}

	/** Can this computer send its sound? macOS 13+ (helper), Windows (PowerShell, nothing to install) or Linux with PulseAudio / PipeWire. */
	public static boolean capable() {
		if (MAC) {
			return AudioStream.class.getResource("/kubo/audio-helper") != null;
		}
		if (WIN) {
			return AudioStream.class.getResource("/kubo/audio.ps1") != null;
		}
		return OS.contains("linux") && linuxCommand() != null;
	}

	// ------------------------------------------------------------------ mu-law
	private static final byte[] ENC = new byte[65536];
	private static final short[] DEC = new short[256];

	static {
		for (int i = 0; i < 65536; i++) {
			ENC[i] = encode((short) i);
		}
		for (int i = 0; i < 256; i++) {
			DEC[i] = decode((byte) i);
		}
	}

	private static byte encode(short pcm) {
		int s = pcm, sign = (s >> 8) & 0x80;
		if (sign != 0) {
			s = -s;
		}
		if (s > 32635) {
			s = 32635;
		}
		s += 0x84;
		int exp = 7;
		for (int m = 0x4000; (s & m) == 0 && exp > 0; m >>= 1) {
			exp--;
		}
		int mant = (s >> (exp + 3)) & 0x0F;
		return (byte) ~(sign | (exp << 4) | mant);
	}

	private static short decode(byte u) {
		int v = ~u & 0xFF, sign = v & 0x80, exp = (v >> 4) & 7, mant = v & 0x0F;
		int s = ((mant << 3) + 0x84) << exp;
		s -= 0x84;
		return (short) (sign != 0 ? -s : s);
	}

	private static String url(String path) {
		return dev.solclient.social.SyncLink.URL + path;
	}

	private static String hello(MinecraftClient mc) {
		JsonObject h = new JsonObject();
		h.addProperty("t", "hello");
		h.addProperty("token", dev.solclient.social.SolarApi.tokenValue());
		h.addProperty("uuid", mc.getSession().getUuidOrNull().toString().replace("-", ""));
		h.addProperty("nick", mc.getSession().getUsername());
		return h.toString();
	}

	// ------------------------------------------------------------------ host: capture + send
	private static volatile Process proc;
	private static volatile WebSocket hostWs;
	private static volatile boolean hostReady, hostStarting;
	private static volatile Thread reader;

	public static boolean hosting() {
		Process p = proc;
		return p != null && p.isAlive();
	}

	/** Called every tick: starts the capture when somebody listens, stops it when nobody does. Cheap: no work while nothing changes. */
	public static void hostWant(boolean want, String source) {
		String src = source == null ? "" : source;
		if (hosting() && want && !hostWarned && System.currentTimeMillis() - hostStartedAt > 9000L && bytesCaptured == 0) {
			hostWarned = true;
			status = "audio: the capture is running but your computer gives it no sound. macOS: System Settings > Privacy & Security > Screen & System Audio Recording, turn it ON for Sleep / Java, restart the game. Also check that the music is playing";
		}
		if (hosting() && want && startedFor != null && !startedFor.equals(src)) {
			hostStop(); // the song now comes from another player: capture that one instead
		}
		if (want && !hosting() && !hostStarting) {
			hostStarting = true;
			startedFor = src;
			Thread t = new Thread(AudioStream::hostStart, "Sleep audio start");
			t.setDaemon(true);
			t.start();
		} else if (!want && (hosting() || hostWs != null)) {
			hostStop();
		}
	}

	private static volatile String startedFor;

	/** Linux: the PulseAudio / PipeWire "sink input" of the music player (by name), so only its sound is sent. Null = not found. */
	private static String linuxSinkInput(String source) {
		if (source == null || source.isEmpty()) {
			return null;
		}
		String key = source.toLowerCase(Locale.ROOT).replace(" music", "");
		try {
			Process p = new ProcessBuilder("pactl", "list", "sink-inputs").redirectErrorStream(true).start();
			String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
			p.waitFor(3, TimeUnit.SECONDS);
			for (String block : out.split("Sink Input #")) {
				if (block.toLowerCase(Locale.ROOT).contains(key)) {
					int nl = block.indexOf('\n');
					String id = (nl > 0 ? block.substring(0, nl) : block).trim();
					if (id.matches("\\d+")) {
						return id;
					}
				}
			}
		} catch (Exception ignored) {
			// no pactl
		}
		return null;
	}

	private static void hostStart() {
		try {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (mc.getSession().getUuidOrNull() == null) {
				return;
			}
			Path dir = FabricLoader.getInstance().getGameDir().resolve("kubo").resolve("tools");
			Files.createDirectories(dir);
			Process p;
			if (MAC) {
				// O macOS liga a permissao (gravar o som do Mac) ao ficheiro. Por isso o helper fica SEMPRE no mesmo sitio (partilhado por todas as
				// instancias) e so e escrito uma vez por versao: se o reescrevessemos a cada arranque, o macOS tratava-o como um programa novo e voltava a pedir.
				Path exe;
				try (InputStream in = AudioStream.class.getResourceAsStream("/kubo/audio-helper")) {
					if (in == null) {
						status = "this Sleep has no audio helper";
						return;
					}
					byte[] bin = in.readAllBytes();
					StringBuilder hash = new StringBuilder();
					for (byte b : java.security.MessageDigest.getInstance("SHA-1").digest(bin)) {
						hash.append(String.format("%02x", b));
					}
					Path hdir = Path.of(System.getProperty("user.home"), "Library", "Application Support", "Sleep", "helper");
					Files.createDirectories(hdir);
					exe = hdir.resolve("audio-" + hash.substring(0, 10));
					if (!Files.isRegularFile(exe) || Files.size(exe) != bin.length) {
						Path tmp = hdir.resolve(exe.getFileName() + ".part");
						Files.write(tmp, bin);
						Files.move(tmp, exe, StandardCopyOption.REPLACE_EXISTING);
					}
				}
				exe.toFile().setExecutable(true, false);
				String src = startedFor == null ? "" : startedFor;
				java.util.List<String> cmd = new java.util.ArrayList<>(java.util.List.of(exe.toString()));
				if (src.equals("Spotify")) {
					cmd.add("--only");
					cmd.add("com.spotify.client");
				} else if (src.equals("Apple Music")) {
					cmd.add("--only");
					cmd.add("com.apple.Music");
				}
				p = new ProcessBuilder(cmd).redirectErrorStream(false).start();
			} else if (WIN) {
				Path ps = dir.resolve("audio.ps1");
				try (InputStream in = AudioStream.class.getResourceAsStream("/kubo/audio.ps1")) {
					if (in == null) {
						status = "this Sleep has no audio helper";
						return;
					}
					Files.copy(in, ps, StandardCopyOption.REPLACE_EXISTING);
				}
				p = new ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden", "-File", ps.toString()).start();
			} else {
				String[] cmd = linuxCommand();
				if (cmd != null && cmd[0].equals("parec")) {
					String idx = linuxSinkInput(startedFor);
					if (idx != null) { // only the music player's own stream, not the whole output
						cmd = new String[] {"parec", "--format=s16le", "--rate=24000", "--channels=1", "--latency-msec=40", "--monitor-stream=" + idx};
					}
				}
				if (cmd == null) {
					status = "install pulseaudio-utils (parec) or pipewire to send your sound";
					return;
				}
				p = new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start();
			}
			proc = p;
			status = "starting";
			Thread err = new Thread(() -> {
				try (var r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getErrorStream()))) {
					String l;
					while ((l = r.readLine()) != null) {
						if (l.startsWith("capturing")) {
							status = "sending your sound";
						} else if (l.startsWith("error")) {
							status = l.toLowerCase(Locale.ROOT).contains("3801") || l.toLowerCase(Locale.ROOT).contains("declin") || l.toLowerCase(Locale.ROOT).contains("permission")
									? "macOS has no permission yet: System Settings > Privacy & Security > Screen & System Audio Recording, turn it on for Java / Sleep, then try again"
									: l;
						}
					}
				} catch (Exception ignored) {
					// ended
				}
			}, "Sleep audio helper log");
			err.setDaemon(true);
			err.start();
			connectHost(mc);
			Thread t = new Thread(() -> pump(p), "Sleep audio send");
			t.setDaemon(true);
			reader = t;
			t.start();
		} catch (Exception e) {
			status = "audio: " + e.getMessage();
		} finally {
			hostStarting = false;
		}
	}

	private static void connectHost(MinecraftClient mc) {
		String me = mc.getSession().getUuidOrNull().toString().replace("-", "");
		String hello = hello(mc);
		HTTP.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10)).buildAsync(URI.create(url("/audio/" + me)), new WebSocket.Listener() {
			@Override
			public void onOpen(WebSocket ws) {
				hostWs = ws;
				ws.sendText(hello, true);
				ws.request(1);
			}

			@Override
			public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
				if (data.toString().contains("\"ready\"")) {
					hostReady = true;
					if (data.toString().contains("\"host\":false")) {
						status = "audio: the Worker did not take you as the owner of this sound room (account mismatch)";
					} else if (status == null || status.isEmpty() || status.startsWith("starting") || status.startsWith("sending")) {
						status = "your sound is going out to your listeners";
					}
				}
				ws.request(1);
				return null;
			}

			@Override
			public CompletionStage<?> onClose(WebSocket ws, int code, String reason) {
				hostReady = false;
				hostWs = null;
				return null;
			}

			@Override
			public void onError(WebSocket ws, Throwable error) {
				hostReady = false;
				hostWs = null;
			}
		});
	}

	/** Reads the captured PCM, compresses it and sends 40 ms frames. A frame is dropped (never queued) when the network is behind: no lag builds up. */
	private static void pump(Process p) {
		byte[] raw = new byte[FRAME_SAMPLES * 2];
		bytesCaptured = 0;
		hostStartedAt = System.currentTimeMillis();
		hostWarned = false;
		long lastTry = System.currentTimeMillis();
		CompletableFuture<?> last = null;
		try (InputStream in = p.getInputStream()) {
			while (p.isAlive() && proc == p) {
				int got = 0;
				while (got < raw.length) {
					int n = in.read(raw, got, raw.length - got);
					if (n < 0) {
						return;
					}
					got += n;
				}
				bytesCaptured += raw.length;
				WebSocket ws = hostWs;
				if (ws == null || !hostReady) {
					long now = System.currentTimeMillis();
					if (ws == null && now - lastTry > 4000L) {
						lastTry = now;
						connectHost(MinecraftClient.getInstance());
					}
					continue;
				}
				if (last != null && !last.isDone()) {
					continue; // behind: skip this frame
				}
				byte[] enc = new byte[FRAME_SAMPLES];
				for (int i = 0; i < FRAME_SAMPLES; i++) {
					int s = (raw[2 * i] & 0xFF) | (raw[2 * i + 1] << 8);
					enc[i] = ENC[s & 0xFFFF];
				}
				last = ws.sendBinary(ByteBuffer.wrap(enc), true);
				framesSent++;
			}
		} catch (Exception ignored) {
			// stopped
		}
	}

	public static void hostStop() {
		Process p = proc;
		proc = null;
		hostReady = false;
		WebSocket w = hostWs;
		hostWs = null;
		if (p != null) {
			p.destroy();
		}
		if (w != null) {
			try {
				w.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
			} catch (RuntimeException ignored) {
				// closed
			}
		}
		status = "";
	}

	// ------------------------------------------------------------------ listener: receive + play
	private static volatile String listenHost;
	private static volatile WebSocket lisWs;
	private static volatile boolean lisBusy;
	private static volatile long lisRetryAt;
	private static final BlockingQueue<byte[]> PLAY = new ArrayBlockingQueue<>(80);
	private static volatile Thread player;
	private static volatile boolean playing;
	/** 0..1 */
	public static volatile float volume = 1F;
	private static volatile long framesSent, framesGot, framesPlayed, bytesCaptured, hostStartedAt, hostOnAt;
	private static volatile boolean lisReady, hostOn, lisWarned, hostWarned;

	/** One line for the sync status in the settings: what the sound is doing right now. */
	public static String diag() {
		return "Audio: send " + (hosting() ? (hostReady ? "ON" : "connecting") : "off") + " (" + framesSent + " sent), listen " + (listenHost == null ? "off" : (lisWs == null ? "connecting" : "ON")) + " (" + framesGot + " got, " + framesPlayed + " played)" + (status == null || status.isEmpty() ? "" : " | " + status);
	}

	/** Called every tick with the host to listen to (uuid, no dashes), or null to stop. */
	public static void listenTo(String hostUuid) {
		String cur = listenHost;
		if (hostUuid == null ? cur != null : !hostUuid.equals(cur)) {
			listenStop();
			listenHost = hostUuid;
			lisRetryAt = 0;
		}
		if (hostUuid != null && lisReady && hostOn && !lisWarned && framesGot == 0 && System.currentTimeMillis() - hostOnAt > 7000L) {
			lisWarned = true;
			status = "audio: the host is connected but no sound arrives: on the host computer the capture is silent (permission or nothing playing)";
		}
		if (hostUuid != null && lisWs == null && !lisBusy && System.currentTimeMillis() >= lisRetryAt) {
			lisBusy = true;
			lisRetryAt = System.currentTimeMillis() + 5000L;
			connectListener(hostUuid);
		}
	}

	private static void connectListener(String host) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.getSession().getUuidOrNull() == null) {
			lisBusy = false;
			return;
		}
		String hello = hello(mc);
		HTTP.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10)).buildAsync(URI.create(url("/audio/" + host)), new WebSocket.Listener() {
			private final java.io.ByteArrayOutputStream part = new java.io.ByteArrayOutputStream();

			@Override
			public void onOpen(WebSocket ws) {
				lisWs = ws;
				lisBusy = false;
				framesGot = 0;
				framesPlayed = 0;
				lisReady = false;
				hostOn = false;
				lisWarned = false;
				status = "connecting to the sound room...";
				ws.sendText(hello, true);
				startPlayer();
				ws.request(1);
			}

			@Override
			public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
				String t = data.toString();
				if (t.contains("\"ready\"")) {
					lisReady = true;
					hostOn = t.contains("\"hostOn\":true");
					hostOnAt = System.currentTimeMillis();
					status = hostOn ? "connected, the host's sound room is open: waiting for the sound" : "connected, but the host is not sending sound yet";
				} else if (t.contains("\"host\"")) {
					hostOn = t.contains("\"on\":true");
					hostOnAt = System.currentTimeMillis();
					status = hostOn ? "the host started sending sound" : "the host stopped sending sound";
				}
				ws.request(1);
				return null;
			}

			@Override
			public CompletionStage<?> onBinary(WebSocket ws, ByteBuffer data, boolean last) {
				byte[] b = new byte[data.remaining()];
				data.get(b);
				part.write(b, 0, b.length);
				if (last) {
					byte[] all = part.toByteArray();
					part.reset();
					byte[] pcm = new byte[all.length * 2];
					for (int i = 0; i < all.length; i++) {
						short s = DEC[all[i] & 0xFF];
						pcm[2 * i] = (byte) s;
						pcm[2 * i + 1] = (byte) (s >> 8);
					}
					if (framesGot++ == 0) {
						status = "receiving the sound";
					}
					while (!PLAY.offer(pcm)) {
						PLAY.poll(); // too far behind: drop the oldest, the delay stays short
					}
				}
				ws.request(1);
				return null;
			}

			@Override
			public CompletionStage<?> onClose(WebSocket ws, int code, String reason) {
				lisWs = null;
				if (code == 4003) {
					status = "audio: the sound Worker did not accept your login";
				}
				return null;
			}

			@Override
			public void onError(WebSocket ws, Throwable error) {
				lisWs = null;
				lisBusy = false;
			}
		}).whenComplete((ws, err) -> {
			if (err != null) {
				lisBusy = false;
			}
		});
	}

	private static void startPlayer() {
		if (playing) {
			return;
		}
		playing = true;
		Thread t = new Thread(() -> {
			SourceDataLine line = null;
			try {
				// 24 kHz mono is not accepted by every sound card (it used to fail without a word): the line is 48 kHz stereo, which all of them take,
				// and the sound is doubled up to it here
				AudioFormat fmt = new AudioFormat(48000, 16, 2, true, false);
				line = AudioSystem.getSourceDataLine(fmt);
				line.open(fmt, 48000 * 4 / 2); // 0.5 s of buffer
				line.start();
				boolean primed = false;
				short prev = 0;
				while (playing && listenHost != null) {
					if (!primed) {
						if (PLAY.size() < 8) { // ~320 ms before the first sound: smooths the network
							Thread.sleep(20);
							continue;
						}
						primed = true;
					}
					byte[] f = PLAY.poll(1200, TimeUnit.MILLISECONDS);
					if (f == null) {
						primed = false; // the stream stopped: fill up again
						continue;
					}
					float v = volume;
					int n = f.length / 2;
					byte[] out = new byte[n * 8]; // each mono sample -> 2 stereo frames of 4 bytes
					for (int i = 0; i < n; i++) {
						short cur = (short) ((f[2 * i] & 0xFF) | (f[2 * i + 1] << 8));
						short mid = (short) ((prev + cur) / 2);
						prev = cur;
						short a1 = (short) Math.round(mid * v), a2 = (short) Math.round(cur * v);
						int o = i * 8;
						out[o] = (byte) a1;
						out[o + 1] = (byte) (a1 >> 8);
						out[o + 2] = (byte) a1;
						out[o + 3] = (byte) (a1 >> 8);
						out[o + 4] = (byte) a2;
						out[o + 5] = (byte) (a2 >> 8);
						out[o + 6] = (byte) a2;
						out[o + 7] = (byte) (a2 >> 8);
					}
					line.write(out, 0, out.length);
					framesPlayed++;
				}
			} catch (Throwable e) {
				status = "audio: could not play the sound (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")";
			} finally {
				if (line != null) {
					line.stop();
					line.close();
				}
				playing = false;
			}
		}, "Sleep audio play");
		t.setDaemon(true);
		player = t;
		t.start();
	}

	private static void listenStop() {
		listenHost = null;
		playing = false;
		PLAY.clear();
		WebSocket w = lisWs;
		lisWs = null;
		lisBusy = false;
		if (w != null) {
			try {
				w.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
			} catch (RuntimeException ignored) {
				// closed
			}
		}
	}

	public static boolean listening() {
		return listenHost != null && lisWs != null && playing;
	}
}
