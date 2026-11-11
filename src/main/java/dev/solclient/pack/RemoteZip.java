package dev.solclient.pack;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Inflater;

/**
 * Reads single files out of a zip on a web server WITHOUT downloading the whole zip: first the end of the file (the zip's
 * table of contents), then only the bytes of the file that is asked for (HTTP "Range" requests). A resource pack of 80 MB
 * gives its 2 KB end-crystal texture after ~100 KB of download. Throws when the server does not support ranges; the caller
 * then downloads the whole file as before.
 */
final class RemoteZip implements TextureStyles.Src {

	private record Entry(int method, long comp, long size, long local) {
	}

	private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
			.connectTimeout(Duration.ofSeconds(10)).build();
	private static final Pattern TOTAL = Pattern.compile("/(\\d+)\\s*$");

	private final String url, ua;
	private final Map<String, Entry> entries = new LinkedHashMap<>();

	private RemoteZip(String url, String ua) {
		this.url = url;
		this.ua = ua;
	}

	/** Reads the table of contents; throws when the server sends the whole file instead of a part. */
	static RemoteZip open(String url, String ua) throws IOException, InterruptedException {
		RemoteZip z = new RemoteZip(url, ua);
		z.readDirectory();
		return z;
	}

	private record Part(byte[] data, long total) {
	}

	private Part range(String spec, int max) throws IOException, InterruptedException {
		HttpRequest rq = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(25)).header("User-Agent", ua)
				.header("Range", "bytes=" + spec).GET().build();
		HttpResponse<InputStream> r = HTTP.send(rq, HttpResponse.BodyHandlers.ofInputStream());
		try (InputStream in = r.body()) {
			if (r.statusCode() != 206) {
				throw new IOException("no range support (HTTP " + r.statusCode() + ")");
			}
			long total = -1;
			String cr = r.headers().firstValue("Content-Range").orElse("");
			Matcher m = TOTAL.matcher(cr);
			if (m.find()) {
				total = Long.parseLong(m.group(1));
			}
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] b = new byte[16384];
			int n;
			while ((n = in.read(b)) > 0) {
				out.write(b, 0, n);
				if (out.size() > max) {
					throw new IOException("part too big");
				}
			}
			return new Part(out.toByteArray(), total);
		}
	}

	private static int u16(byte[] b, int o) {
		return (b[o] & 0xFF) | (b[o + 1] & 0xFF) << 8;
	}

	private static long u32(byte[] b, int o) {
		return (u16(b, o) | (long) u16(b, o + 2) << 16) & 0xFFFFFFFFL;
	}

	private static long u64(byte[] b, int o) {
		return u32(b, o) | u32(b, o + 4) << 32;
	}

	private void readDirectory() throws IOException, InterruptedException {
		// Modrinth's file server refuses "the last N bytes" (bytes=-N, HTTP 501), which made every picture download its whole pack.
		// Ask for one byte first to learn the size, then for the end of the file with explicit positions.
		long fileSize = range("0-0", 4096).total();
		if (fileSize <= 0) {
			throw new IOException("unknown size");
		}
		Part tail = range(Math.max(0, fileSize - 66_000) + "-" + (fileSize - 1), 70_000);
		byte[] t = tail.data();
		if (tail.total() < 0) {
			throw new IOException("unknown size");
		}
		long tailStart = tail.total() - t.length;
		int e = -1;
		for (int i = t.length - 22; i >= 0; i--) {
			if (t[i] == 0x50 && t[i + 1] == 0x4b && t[i + 2] == 0x05 && t[i + 3] == 0x06) {
				e = i;
				break;
			}
		}
		if (e < 0) {
			throw new IOException("not a zip");
		}
		long cdSize = u32(t, e + 12), cdOff = u32(t, e + 16);
		if (cdOff == 0xFFFFFFFFL || cdSize == 0xFFFFFFFFL) {
			// zip64: the locator just before tells where the bigger record is
			int loc = e - 20;
			if (loc < 0 || u32(t, loc) != 0x07064b50L) {
				throw new IOException("zip64 without locator");
			}
			long rec = u64(t, loc + 8);
			byte[] z64 = rec >= tailStart ? java.util.Arrays.copyOfRange(t, (int) (rec - tailStart), (int) (rec - tailStart) + 56)
					: range(rec + "-" + (rec + 55), 4096).data();
			cdSize = u64(z64, 40);
			cdOff = u64(z64, 48);
		}
		if (cdSize > 24_000_000) {
			throw new IOException("directory too big");
		}
		byte[] cd;
		if (cdOff >= tailStart && cdOff + cdSize <= tail.total()) {
			int s = (int) (cdOff - tailStart);
			cd = java.util.Arrays.copyOfRange(t, s, s + (int) cdSize);
		} else {
			cd = range(cdOff + "-" + (cdOff + cdSize - 1), (int) cdSize + 1024).data();
		}
		int p = 0;
		while (p + 46 <= cd.length && u32(cd, p) == 0x02014b50L) {
			int method = u16(cd, p + 10);
			long comp = u32(cd, p + 20), size = u32(cd, p + 24);
			int nl = u16(cd, p + 28), xl = u16(cd, p + 30), cl = u16(cd, p + 32);
			long local = u32(cd, p + 42);
			String name = new String(cd, p + 46, nl, StandardCharsets.UTF_8);
			// zip64 sizes / offset live in the extra field
			int x = p + 46 + nl, xe = x + xl;
			while (x + 4 <= xe) {
				int id = u16(cd, x), len = u16(cd, x + 2);
				if (id == 1) {
					int q = x + 4;
					if (size == 0xFFFFFFFFL) {
						size = u64(cd, q);
						q += 8;
					}
					if (comp == 0xFFFFFFFFL) {
						comp = u64(cd, q);
						q += 8;
					}
					if (local == 0xFFFFFFFFL) {
						local = u64(cd, q);
					}
				}
				x += 4 + len;
			}
			if (!name.endsWith("/")) {
				entries.put(name, new Entry(method, comp, size, local));
			}
			p += 46 + nl + xl + cl;
		}
		if (entries.isEmpty()) {
			throw new IOException("empty zip");
		}
	}

	@Override
	public Iterable<String> names() {
		return entries.keySet();
	}

	@Override
	public long size(String name) {
		Entry e = entries.get(name);
		return e == null ? -1 : e.size();
	}

	@Override
	public byte[] read(String name) throws IOException {
		Entry e = entries.get(name);
		if (e == null || e.size() > 4_000_000 || e.comp() > 4_000_000) {
			return null;
		}
		try {
			int guess = (int) (30 + name.length() + 512 + e.comp());
			byte[] b = range(e.local() + "-" + (e.local() + guess - 1), guess + 1024).data();
			if (u32(b, 0) != 0x04034b50L) {
				throw new IOException("bad local header");
			}
			int start = 30 + u16(b, 26) + u16(b, 28);
			if (start + e.comp() > b.length) {
				b = range(e.local() + "-" + (e.local() + start + e.comp() - 1), (int) (start + e.comp()) + 1024).data();
			}
			if (e.method() == 0) {
				return java.util.Arrays.copyOfRange(b, start, start + (int) e.comp());
			}
			if (e.method() != 8) {
				return null;
			}
			Inflater inf = new Inflater(true);
			try {
				inf.setInput(b, start, (int) e.comp());
				byte[] out = new byte[(int) e.size()];
				int n = 0;
				while (n < out.length && !inf.finished()) {
					int k = inf.inflate(out, n, out.length - n);
					if (k == 0 && (inf.needsInput() || inf.needsDictionary())) {
						break;
					}
					n += k;
				}
				return n == out.length ? out : null;
			} finally {
				inf.end();
			}
		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
			throw new IOException(ie);
		} catch (java.util.zip.DataFormatException df) {
			throw new IOException(df);
		}
	}
}
