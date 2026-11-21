package dev.sleepclient.builder;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A loaded schematic: a box of blocks (palette + one index per block). Reads Litematica (.litematic), Sponge /
 * WorldEdit (.schem, v1-v3) and vanilla structure (.nbt) files. Blocks are stored as shorts (palette index), so even
 * big builds stay small in memory.
 */
public final class Schematic {

	public final String name;
	public final int sx, sy, sz;
	public final BlockState[] palette;
	public final short[] data; // index = x + z * sx + y * sx * sz
	public int nonAir;

	private Schematic(String name, int sx, int sy, int sz, BlockState[] palette, short[] data) {
		this.name = name;
		this.sx = sx;
		this.sy = sy;
		this.sz = sz;
		this.palette = palette;
		this.data = data;
		int n = 0;
		for (short d : data) {
			if (!palette[d].isAir()) {
				n++;
			}
		}
		nonAir = n;
	}

	public BlockState get(int x, int y, int z) {
		if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) {
			return Blocks.AIR.defaultBlockState();
		}
		return palette[data[x + z * sx + y * sx * sz]];
	}

	/** How many of each block the build needs. */
	public Map<Block, Integer> materials() {
		int[] per = new int[palette.length];
		for (short d : data) {
			per[d]++;
		}
		Map<Block, Integer> out = new HashMap<>();
		for (int i = 0; i < palette.length; i++) {
			if (per[i] > 0 && !palette[i].isAir()) {
				out.merge(palette[i].getBlock(), per[i], Integer::sum);
			}
		}
		return out;
	}

	// ------------------------------------------------------------------ loading
	public static Schematic load(Path file) throws Exception {
		CompoundTag root;
		try (InputStream in = Files.newInputStream(file)) {
			root = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
		}
		String n = file.getFileName().toString();
		String base = n.contains(".") ? n.substring(0, n.lastIndexOf('.')) : n;
		HolderGetter<Block> blocks = lookup();
		if (root.contains("Regions")) {
			return litematic(base, root, blocks);
		}
		if (root.contains("Schematic") && root.getCompoundOrEmpty("Schematic").contains("Blocks")) {
			return spongeV3(base, root.getCompoundOrEmpty("Schematic"), blocks);
		}
		if (root.contains("Blocks") && root.getCompoundOrEmpty("Blocks").contains("Palette")) {
			return spongeV3(base, root, blocks);
		}
		if (root.contains("Palette") && root.contains("BlockData")) {
			return spongeV2(base, root, blocks);
		}
		if (root.contains("palette") || root.contains("palettes")) {
			return structure(base, root, blocks);
		}
		if (root.contains("Blocks")) {
			throw new IllegalArgumentException("Old MCEdit .schematic (number IDs): convert it to .schem or .litematic first");
		}
		throw new IllegalArgumentException("Unknown schematic format");
	}

	private static HolderGetter<Block> lookup() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			throw new IllegalStateException("Join a world to load schematics");
		}
		return mc.level.holderLookup(Registries.BLOCK);
	}

	private static BlockState state(HolderGetter<Block> blocks, CompoundTag c) {
		try {
			return NbtUtils.readBlockState(blocks, c);
		} catch (RuntimeException e) {
			return Blocks.AIR.defaultBlockState();
		}
	}

	/** "minecraft:oak_stairs[facing=east,half=bottom]" -> block state. */
	static BlockState parse(HolderGetter<Block> blocks, String s) {
		CompoundTag c = new CompoundTag();
		String name = s, props = null;
		int b = s.indexOf('[');
		if (b >= 0) {
			name = s.substring(0, b);
			props = s.substring(b + 1, s.endsWith("]") ? s.length() - 1 : s.length());
		}
		if (!name.contains(":")) {
			name = "minecraft:" + name;
		}
		c.putString("Name", name.toLowerCase(Locale.ROOT));
		if (props != null && !props.isEmpty()) {
			CompoundTag p = new CompoundTag();
			for (String kv : props.split(",")) {
				int eq = kv.indexOf('=');
				if (eq > 0) {
					p.putString(kv.substring(0, eq).trim(), kv.substring(eq + 1).trim());
				}
			}
			c.put("Properties", p);
		}
		return state(blocks, c);
	}

	private static Schematic litematic(String name, CompoundTag root, HolderGetter<Block> blocks) {
		CompoundTag regions = root.getCompoundOrEmpty("Regions");
		record R(int x0, int y0, int z0, int w, int h, int l, BlockState[] pal, long[] bits) {
		}
		List<R> rs = new ArrayList<>();
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for (String key : regions.keySet()) {
			CompoundTag r = regions.getCompoundOrEmpty(key);
			CompoundTag pos = r.getCompoundOrEmpty("Position"), size = r.getCompoundOrEmpty("Size");
			int px = pos.getIntOr("x", 0), py = pos.getIntOr("y", 0), pz = pos.getIntOr("z", 0);
			int w = size.getIntOr("x", 0), h = size.getIntOr("y", 0), l = size.getIntOr("z", 0);
			int x0 = px + (w < 0 ? w + 1 : 0), y0 = py + (h < 0 ? h + 1 : 0), z0 = pz + (l < 0 ? l + 1 : 0);
			w = Math.abs(w);
			h = Math.abs(h);
			l = Math.abs(l);
			ListTag pl = r.getListOrEmpty("BlockStatePalette");
			BlockState[] pal = new BlockState[Math.max(1, pl.size())];
			pal[0] = Blocks.AIR.defaultBlockState();
			for (int i = 0; i < pl.size(); i++) {
				pal[i] = state(blocks, pl.getCompoundOrEmpty(i));
			}
			long[] bits = r.getLongArray("BlockStates").orElse(new long[0]);
			rs.add(new R(x0, y0, z0, w, h, l, pal, bits));
			minX = Math.min(minX, x0);
			minY = Math.min(minY, y0);
			minZ = Math.min(minZ, z0);
			maxX = Math.max(maxX, x0 + w - 1);
			maxY = Math.max(maxY, y0 + h - 1);
			maxZ = Math.max(maxZ, z0 + l - 1);
		}
		if (rs.isEmpty()) {
			throw new IllegalArgumentException("Empty litematic");
		}
		int sx = maxX - minX + 1, sy = maxY - minY + 1, sz = maxZ - minZ + 1;
		Map<BlockState, Short> ids = new HashMap<>();
		List<BlockState> pal = new ArrayList<>();
		pal.add(Blocks.AIR.defaultBlockState());
		ids.put(Blocks.AIR.defaultBlockState(), (short) 0);
		short[] data = new short[sx * sy * sz];
		for (R r : rs) {
			int bitsPer = Math.max(2, 32 - Integer.numberOfLeadingZeros(r.pal().length - 1));
			long mask = (1L << bitsPer) - 1;
			short[] map = new short[r.pal().length];
			for (int i = 0; i < r.pal().length; i++) {
				BlockState st = r.pal()[i];
				Short id = ids.get(st);
				if (id == null) {
					id = (short) pal.size();
					pal.add(st);
					ids.put(st, id);
				}
				map[i] = id;
			}
			long total = (long) r.w() * r.h() * r.l();
			for (long i = 0; i < total; i++) {
				long bit = i * bitsPer;
				int start = (int) (bit >> 6), end = (int) ((bit + bitsPer - 1) >> 6);
				int off = (int) (bit & 63);
				if (end >= r.bits().length) {
					break;
				}
				long v = start == end ? r.bits()[start] >>> off & mask : (r.bits()[start] >>> off | r.bits()[end] << (64 - off)) & mask;
				if (v <= 0 || v >= map.length) {
					continue;
				}
				int x = (int) (i % r.w()), z = (int) (i / r.w() % r.l()), y = (int) (i / ((long) r.w() * r.l()));
				int gx = r.x0() - minX + x, gy = r.y0() - minY + y, gz = r.z0() - minZ + z;
				data[gx + gz * sx + gy * sx * sz] = map[(int) v];
			}
		}
		return new Schematic(name, sx, sy, sz, pal.toArray(new BlockState[0]), data);
	}

	private static Schematic sponge(String name, int w, int h, int l, CompoundTag palette, byte[] blockData, HolderGetter<Block> blocks) {
		int max = 0;
		for (String k : palette.keySet()) {
			max = Math.max(max, palette.getIntOr(k, 0));
		}
		BlockState[] byId = new BlockState[max + 1];
		for (String k : palette.keySet()) {
			byId[palette.getIntOr(k, 0)] = parse(blocks, k);
		}
		Map<BlockState, Short> ids = new HashMap<>();
		List<BlockState> pal = new ArrayList<>();
		pal.add(Blocks.AIR.defaultBlockState());
		ids.put(Blocks.AIR.defaultBlockState(), (short) 0);
		short[] map = new short[byId.length];
		for (int i = 0; i < byId.length; i++) {
			BlockState st = byId[i] == null ? Blocks.AIR.defaultBlockState() : byId[i];
			Short id = ids.get(st);
			if (id == null) {
				id = (short) pal.size();
				pal.add(st);
				ids.put(st, id);
			}
			map[i] = id;
		}
		short[] data = new short[w * h * l];
		int i = 0, p = 0;
		while (p < blockData.length && i < data.length) {
			int v = 0, shift = 0;
			byte b;
			do { // varint
				b = blockData[p++];
				v |= (b & 0x7F) << shift;
				shift += 7;
			} while ((b & 0x80) != 0 && p < blockData.length);
			data[i++] = v >= 0 && v < map.length ? map[v] : 0;
		}
		return new Schematic(name, w, h, l, pal.toArray(new BlockState[0]), data);
	}

	private static Schematic spongeV2(String name, CompoundTag root, HolderGetter<Block> blocks) {
		int w = root.getShortOr("Width", (short) 0) & 0xFFFF, h = root.getShortOr("Height", (short) 0) & 0xFFFF, l = root.getShortOr("Length", (short) 0) & 0xFFFF;
		return sponge(name, w, h, l, root.getCompoundOrEmpty("Palette"), root.getByteArray("BlockData").orElse(new byte[0]), blocks);
	}

	private static Schematic spongeV3(String name, CompoundTag s, HolderGetter<Block> blocks) {
		int w = s.getShortOr("Width", (short) 0) & 0xFFFF, h = s.getShortOr("Height", (short) 0) & 0xFFFF, l = s.getShortOr("Length", (short) 0) & 0xFFFF;
		CompoundTag b = s.getCompoundOrEmpty("Blocks");
		return sponge(name, w, h, l, b.getCompoundOrEmpty("Palette"), b.getByteArray("Data").orElse(new byte[0]), blocks);
	}

	private static Schematic structure(String name, CompoundTag root, HolderGetter<Block> blocks) {
		ListTag size = root.getListOrEmpty("size");
		int w = size.getIntOr(0, 0), h = size.getIntOr(1, 0), l = size.getIntOr(2, 0);
		ListTag pl = root.contains("palette") ? root.getListOrEmpty("palette") : root.getListOrEmpty("palettes").getListOrEmpty(0);
		BlockState[] pal = new BlockState[pl.size() + 1];
		pal[0] = Blocks.AIR.defaultBlockState();
		for (int i = 0; i < pl.size(); i++) {
			pal[i + 1] = state(blocks, pl.getCompoundOrEmpty(i));
		}
		short[] data = new short[w * h * l];
		ListTag bl = root.getListOrEmpty("blocks");
		for (int i = 0; i < bl.size(); i++) {
			CompoundTag b = bl.getCompoundOrEmpty(i);
			ListTag pos = b.getListOrEmpty("pos");
			int x = pos.getIntOr(0, 0), y = pos.getIntOr(1, 0), z = pos.getIntOr(2, 0);
			int st = b.getIntOr("state", -1);
			if (x >= 0 && y >= 0 && z >= 0 && x < w && y < h && z < l && st >= 0 && st + 1 < pal.length) {
				data[x + z * w + y * w * l] = (short) (st + 1);
			}
		}
		return new Schematic(name, w, h, l, pal, data);
	}
}
