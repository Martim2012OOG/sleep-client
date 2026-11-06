package dev.solclient.verity;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Comparator;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import net.fabricmc.loader.api.FabricLoader;

/** Small download / unpack helpers for Verity's local brain (Ollama), voice (Piper) and ears. Nothing here touches the game thread. */
final class Net {

	static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).connectTimeout(Duration.ofSeconds(20)).build();

	private Net() {
	}

	/** .minecraft/kubo/verity: everything Verity downloads lives here. */
	static Path root() {
		return FabricLoader.getInstance().getGameDir().resolve("kubo").resolve("verity");
	}

	static boolean mac() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
	}

	static boolean windows() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
	}

	static boolean arm() {
		String a = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
		return a.contains("aarch64") || a.contains("arm64");
	}

	static JsonElement json(String url) throws IOException, InterruptedException {
		HttpRequest r = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "KuboClient").header("Accept", "application/json")
				.timeout(Duration.ofSeconds(30)).build();
		HttpResponse<String> res = HTTP.send(r, HttpResponse.BodyHandlers.ofString());
		if (res.statusCode() != 200) {
			throw new IOException("HTTP " + res.statusCode() + " (" + url + ")");
		}
		return JsonParser.parseString(res.body());
	}

	/** Downloads to 'to' (through a .part file), reporting "label 42%" while it goes. */
	static void download(String url, Path to, String label, Consumer<String> progress) throws IOException, InterruptedException {
		Files.createDirectories(to.getParent());
		Path part = to.resolveSibling(to.getFileName() + ".part");
		HttpResponse<InputStream> r = HTTP.send(HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "KuboClient").timeout(Duration.ofMinutes(30)).build(),
				HttpResponse.BodyHandlers.ofInputStream());
		if (r.statusCode() != 200) {
			r.body().close();
			throw new IOException("HTTP " + r.statusCode() + " (" + url + ")");
		}
		long total = r.headers().firstValueAsLong("content-length").orElse(-1);
		long read = 0;
		try (InputStream in = new BufferedInputStream(r.body(), 1 << 16); OutputStream o = Files.newOutputStream(part)) {
			byte[] b = new byte[1 << 16];
			int n;
			long lastTick = 0;
			while ((n = in.read(b)) > 0) {
				o.write(b, 0, n);
				read += n;
				long now = System.currentTimeMillis();
				if (now - lastTick > 250) {
					lastTick = now;
					progress.accept(total > 0 ? label + " " + Math.min(99, read * 100 / total) + "%" : label + " " + read / 1_000_000 + " MB");
				}
			}
		}
		if (total > 0 && read < total) {
			throw new IOException("download cut at " + read + " / " + total);
		}
		Files.move(part, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
	}

	static void deleteTree(Path p) throws IOException {
		if (!Files.exists(p)) {
			return;
		}
		try (var st = Files.walk(p)) {
			st.sorted(Comparator.reverseOrder()).forEach(x -> {
				try {
					Files.deleteIfExists(x);
				} catch (IOException ignored) {
					// best effort
				}
			});
		}
	}

	/** First regular file with this exact name under dir (3 levels deep), or null. */
	static Path find(Path dir, String... names) {
		if (!Files.isDirectory(dir)) {
			return null;
		}
		try (var st = Files.walk(dir, 4)) {
			return st.filter(Files::isRegularFile).filter(f -> {
				String n = f.getFileName().toString();
				for (String w : names) {
					if (n.equals(w)) {
						return true;
					}
				}
				return false;
			}).findFirst().orElse(null);
		} catch (IOException e) {
			return null;
		}
	}

	static void unpack(Path archive, Path dest) throws IOException {
		String n = archive.getFileName().toString().toLowerCase(Locale.ROOT);
		if (n.endsWith(".zip")) {
			unzip(archive, dest);
		} else if (n.endsWith(".tgz") || n.endsWith(".tar.gz")) {
			untarGz(archive, dest);
		} else {
			throw new IOException("unknown archive type: " + n);
		}
	}

	static void unzip(Path zip, Path dest) throws IOException {
		Path base = dest.toAbsolutePath().normalize();
		Files.createDirectories(base);
		try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zip), 1 << 16))) {
			ZipEntry e;
			byte[] b = new byte[1 << 16];
			while ((e = zin.getNextEntry()) != null) {
				Path out = base.resolve(e.getName()).normalize();
				if (!out.startsWith(base)) {
					continue; // never write outside the folder
				}
				if (e.isDirectory()) {
					Files.createDirectories(out);
					continue;
				}
				Files.createDirectories(out.getParent());
				try (OutputStream o = Files.newOutputStream(out)) {
					int r;
					while ((r = zin.read(b)) > 0) {
						o.write(b, 0, r);
					}
				}
				out.toFile().setExecutable(true, false);
			}
		}
	}

	/** Minimal tar.gz reader: files, folders, symlinks, long names. Keeps the executable bit. */
	static void untarGz(Path file, Path dest) throws IOException {
		Path base = dest.toAbsolutePath().normalize();
		Files.createDirectories(base);
		try (InputStream in = new BufferedInputStream(new GZIPInputStream(Files.newInputStream(file), 1 << 16), 1 << 16)) {
			byte[] h = new byte[512];
			String longName = null;
			while (readFully(in, h)) {
				boolean zero = true;
				for (byte x : h) {
					if (x != 0) {
						zero = false;
						break;
					}
				}
				if (zero) {
					break;
				}
				String name = cstr(h, 0, 100);
				long size = octal(h, 124, 12);
				int mode = (int) octal(h, 100, 8);
				char type = h[156] == 0 ? '0' : (char) h[156];
				String link = cstr(h, 157, 100);
				if ("ustar".equals(cstr(h, 257, 5)) && !cstr(h, 345, 155).isEmpty()) {
					name = cstr(h, 345, 155) + "/" + name;
				}
				long padded = (size + 511) / 512 * 512;
				if (type == 'L') {
					byte[] b = in.readNBytes((int) size);
					longName = cstr(b, 0, b.length);
					skip(in, padded - size);
					continue;
				}
				if (type == 'x' || type == 'g') {
					String s = new String(in.readNBytes((int) size), StandardCharsets.UTF_8);
					skip(in, padded - size);
					for (String line : s.split("\n")) {
						int sp = line.indexOf(' '), eq = line.indexOf('=');
						if (sp > 0 && eq > sp && line.substring(sp + 1, eq).equals("path")) {
							longName = line.substring(eq + 1);
						}
					}
					continue;
				}
				if (longName != null) {
					name = longName;
					longName = null;
				}
				Path out = base.resolve(name).normalize();
				if (!out.startsWith(base)) {
					skip(in, padded);
					continue;
				}
				if (type == '5' || name.endsWith("/")) {
					Files.createDirectories(out);
					skip(in, padded);
				} else if (type == '2') {
					Path target = out.getParent().resolve(link).normalize();
					if (target.startsWith(base)) {
						try {
							Files.createDirectories(out.getParent());
							Files.deleteIfExists(out);
							Files.createSymbolicLink(out, Paths.get(link));
						} catch (IOException | UnsupportedOperationException ignored) {
							// no symlinks here: the real file is usually there too
						}
					}
					skip(in, padded);
				} else if (type == '1') {
					Path src = base.resolve(link).normalize();
					if (src.startsWith(base) && Files.isRegularFile(src)) {
						Files.createDirectories(out.getParent());
						Files.copy(src, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
					}
					skip(in, padded);
				} else if (type == '0') {
					Files.createDirectories(out.getParent());
					Files.deleteIfExists(out);
					try (OutputStream o = Files.newOutputStream(out)) {
						long left = size;
						byte[] b = new byte[1 << 16];
						while (left > 0) {
							int r = in.read(b, 0, (int) Math.min(b.length, left));
							if (r < 0) {
								throw new IOException("tar cut short");
							}
							o.write(b, 0, r);
							left -= r;
						}
					}
					skip(in, padded - size);
					if ((mode & 0111) != 0) {
						out.toFile().setExecutable(true, false);
					}
				} else {
					skip(in, padded);
				}
			}
		}
	}

	private static boolean readFully(InputStream in, byte[] b) throws IOException {
		int off = 0;
		while (off < b.length) {
			int r = in.read(b, off, b.length - off);
			if (r < 0) {
				return false;
			}
			off += r;
		}
		return true;
	}

	private static void skip(InputStream in, long n) throws IOException {
		while (n > 0) {
			long s = in.skip(n);
			if (s <= 0) {
				if (in.read() < 0) {
					return;
				}
				s = 1;
			}
			n -= s;
		}
	}

	private static String cstr(byte[] b, int off, int len) {
		int end = off;
		while (end < off + len && end < b.length && b[end] != 0) {
			end++;
		}
		return new String(b, off, end - off, StandardCharsets.UTF_8);
	}

	private static long octal(byte[] b, int off, int len) {
		long v = 0;
		for (int i = off; i < off + len; i++) {
			int c = b[i];
			if (c >= '0' && c <= '7') {
				v = v * 8 + (c - '0');
			} else if (v > 0 || c == 0) {
				if (c == 0 || c == ' ') {
					break;
				}
			}
		}
		return v;
	}
}
