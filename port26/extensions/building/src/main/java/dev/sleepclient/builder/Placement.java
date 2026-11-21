package dev.sleepclient.builder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Where a schematic is in the world: origin (the box's lowest corner), rotation and mirror. Rotating keeps the box
 * in the same place (it turns "in place" around its corner), so you never lose it.
 */
public final class Placement {

	public Schematic schem;
	public String file = "";
	public BlockPos origin = BlockPos.ZERO;
	public int rotation; // 0..3 = 0, 90, 180, 270 degrees clockwise
	public boolean mirrorX, mirrorZ;
	public String dimension = "";
	private BlockState[] transformed;
	private int tKey = -1;

	public Placement(Schematic s) {
		this.schem = s;
	}

	public Rotation blockRotation() {
		return switch (rotation & 3) {
			case 1 -> Rotation.CLOCKWISE_90;
			case 2 -> Rotation.CLOCKWISE_180;
			case 3 -> Rotation.COUNTERCLOCKWISE_90;
			default -> Rotation.NONE;
		};
	}

	/** Size of the box in the world (x and z swap at 90 / 270 degrees). */
	public int wx() {
		return (rotation & 1) == 0 ? schem.sx : schem.sz;
	}

	public int wz() {
		return (rotation & 1) == 0 ? schem.sz : schem.sx;
	}

	/** Palette with rotation and mirror applied to every block (stairs face the right way...). */
	public BlockState[] palette() {
		int key = (rotation & 3) | (mirrorX ? 4 : 0) | (mirrorZ ? 8 : 0);
		if (transformed == null || key != tKey) {
			BlockState[] p = schem.palette;
			BlockState[] out = new BlockState[p.length];
			for (int i = 0; i < p.length; i++) {
				BlockState s = p[i];
				if (mirrorX) {
					s = s.mirror(Mirror.FRONT_BACK);
				}
				if (mirrorZ) {
					s = s.mirror(Mirror.LEFT_RIGHT);
				}
				out[i] = s.rotate(blockRotation());
			}
			transformed = out;
			tKey = key;
		}
		return transformed;
	}

	/** World position -> schematic index, or -1 when outside the box. */
	public int indexAt(int x, int y, int z) {
		int lx = x - origin.getX(), ly = y - origin.getY(), lz = z - origin.getZ();
		if (ly < 0 || ly >= schem.sy || lx < 0 || lz < 0 || lx >= wx() || lz >= wz()) {
			return -1;
		}
		// undo the rotation (box anchored at its corner), then the mirror
		int sx, sz;
		switch (rotation & 3) {
			case 1 -> {
				sx = lz;
				sz = schem.sz - 1 - lx;
			}
			case 2 -> {
				sx = schem.sx - 1 - lx;
				sz = schem.sz - 1 - lz;
			}
			case 3 -> {
				sx = schem.sx - 1 - lz;
				sz = lx;
			}
			default -> {
				sx = lx;
				sz = lz;
			}
		}
		if (mirrorX) {
			sx = schem.sx - 1 - sx;
		}
		if (mirrorZ) {
			sz = schem.sz - 1 - sz;
		}
		return sx + sz * schem.sx + ly * schem.sx * schem.sz;
	}

	/** The block the schematic wants at a world position (null outside the box). */
	public BlockState wanted(int x, int y, int z) {
		int i = indexAt(x, y, z);
		return i < 0 ? null : palette()[schem.data[i]];
	}
}
