package dev.sleepclient.lod;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Pure Java (no Minecraft classes): the "memory" of the far terrain. Every chunk you have seen (or that was generated
 * for the LOD) is kept as 4x4 samples of 4x4 blocks: surface height + colour (+ a water flag).
 */
public final class LodStore {

	private static final int MAGIC = 0x4B4C4F31;

	/** One chunk: 16 samples (4x4), each covering 4x4 blocks. */
	public static final class Chunk {
		public final short[] h = new short[16];
		public final int[] rgb = new int[16];
		/** bit i set = sample i is water. */
		public int water;

		public Chunk() {
			Arrays.fill(h, Short.MIN_VALUE);
		}
	}

	private final ConcurrentHashMap<Long, Chunk> map = new ConcurrentHashMap<>();
	public volatile int version;
	public volatile boolean dirty;

	public static long key(int cx, int cz) {
		return (cx & 0xFFFFFFFFL) | ((long) cz << 32);
	}

	public static int kx(long k) {
		return (int) k;
	}

	public static int kz(long k) {
		return (int) (k >>> 32);
	}

	public void put(long key, Chunk c) {
		map.put(key, c);
		version++;
		dirty = true;
	}

	public Chunk get(long key) {
		return map.get(key);
	}

	public boolean has(long key) {
		return map.containsKey(key);
	}

	public int size() {
		return map.size();
	}

	public void clear() {
		map.clear();
		version++;
		dirty = true;
	}

	public void save(Path file) {
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(tmp)), 1 << 16))) {
				out.writeInt(MAGIC);
				out.writeInt(map.size());
				for (Map.Entry<Long, Chunk> e : map.entrySet()) {
					Chunk c = e.getValue();
					out.writeLong(e.getKey());
					out.writeInt(c.water);
					for (int i = 0; i < 16; i++) {
						out.writeShort(c.h[i]);
						out.writeInt(c.rgb[i]);
					}
				}
			}
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			dirty = false;
		} catch (IOException | RuntimeException ignored) {
		}
	}

	/** Adds what is in the file without overwriting chunks that were already captured this session. */
	public void load(Path file) {
		if (!Files.isRegularFile(file)) {
			return;
		}
		try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(file)), 1 << 16))) {
			if (in.readInt() != MAGIC) {
				return;
			}
			int n = in.readInt();
			for (int i = 0; i < n; i++) {
				long k = in.readLong();
				Chunk c = new Chunk();
				c.water = in.readInt();
				for (int j = 0; j < 16; j++) {
					c.h[j] = in.readShort();
					c.rgb[j] = in.readInt();
				}
				map.putIfAbsent(k, c);
			}
			version++;
		} catch (IOException | RuntimeException ignored) {
		}
	}
}
