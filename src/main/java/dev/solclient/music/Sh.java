package dev.solclient.music;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Small helpers shared by the player readers: run a program with a timeout, run a job in the background, a GET. */
final class Sh {

	record Result(String out, String err, int code) {
	}

	private static final ExecutorService BG = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "kubo-music-ctl");
		t.setDaemon(true);
		return t;
	});

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	private Sh() {
	}

	/** Controls (play / pause / next) run here so the game never waits for a program. */
	interface Job {
		void run() throws Exception;
	}

	static void async(Job r) {
		BG.execute(() -> {
			try {
				r.run();
			} catch (Throwable ignored) {
			}
		});
	}

	static Result run(long timeoutMs, String... cmd) throws IOException {
		Process p = new ProcessBuilder(cmd).start();
		try {
			p.getOutputStream().close();
		} catch (IOException ignored) {
		}
		byte[][] buf = new byte[2][];
		Thread to = reader(p.getInputStream(), buf, 0);
		Thread te = reader(p.getErrorStream(), buf, 1);
		boolean done;
		try {
			done = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
			if (!done) {
				p.destroyForcibly();
			}
			to.join(800);
			te.join(800);
		} catch (InterruptedException e) {
			p.destroyForcibly();
			Thread.currentThread().interrupt();
			return new Result("", "", -1);
		}
		String out = buf[0] == null ? "" : new String(buf[0], StandardCharsets.UTF_8);
		String err = buf[1] == null ? "" : new String(buf[1], StandardCharsets.UTF_8);
		return new Result(out, err, done ? p.exitValue() : -1);
	}

	private static Thread reader(InputStream in, byte[][] buf, int i) {
		Thread t = new Thread(() -> {
			try {
				buf[i] = in.readAllBytes();
			} catch (IOException ignored) {
			}
		}, "kubo-music-read");
		t.setDaemon(true);
		t.start();
		return t;
	}

	static String get(String url) {
		try {
			HttpRequest r = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(6))
					.header("User-Agent", "Sleep-Client/1.0").GET().build();
			HttpResponse<String> res = HTTP.send(r, HttpResponse.BodyHandlers.ofString());
			return res.statusCode() / 100 == 2 ? res.body() : null;
		} catch (Exception e) {
			return null;
		}
	}

	static long num(String s) {
		try {
			return Math.round(Double.parseDouble(s.trim().replace(',', '.')));
		} catch (Exception e) {
			return 0;
		}
	}
}
