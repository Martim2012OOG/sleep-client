package dev.solclient.mods;

import dev.solclient.fx.Rainbow;
import dev.solclient.fx.WorldDraw;
import dev.solclient.option.Option;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Tripwire Colour: the strings of tripwires are drawn as glowing coloured lines (one colour, rainbow, gradient or pulse),
 * so traps are easy to see (like the Lunar tripwire colour). Looks for tripwires around you a few times a second
 * (only in chunk sections that really have one), so it does not cost FPS.
 */
public class TripwireColourMod extends Mod {

	public static TripwireColourMod instance;

	private final Option.Choice mode = choice("Colour Mode", 1, "Colour", "Rainbow", "Gradient", "Pulse");
	private final Option.Col colour = colour("Colour", 0xFF2E7BFF);
	private final Option.Col colour2 = colour("Second Colour", 0xFFFF3BD4);
	private final Option.Num speed = num("Colour Speed", 40, 5, 200, 5, "%");
	private final Option.Num width = num("Line Width", 10, 2, 20, 1, " / 100 block");
	private final Option.Num height = num("Height", 12, 2, 40, 1, " / 100 block");
	private final Option.Num range = num("Range", 32, 8, 96, 8, " blocks");
	private final Option.Bool hooks = bool("Hooks Too", true).tip("Also draws the little string that goes into the hook.");
	private final Option.Bool triggered = bool("Flash When Triggered", true).tip("A tripwire that is being stepped on flashes white.");

	private final Option.Action info = action("Status", "Refresh", () -> this.t = 7, () -> this.segCount + " strings found");

	private static boolean hooked;
	private int t;
	/** Segments found around the player: ax, ay, az, bx, by, bz, flag (1 = triggered). */
	private float[] seg = new float[0];
	private int segCount;

	public TripwireColourMod() {
		super("Tripwire Colour", "tripwire_colour", "Colours the tripwire strings (one colour, rainbow, gradient or pulse) so traps are easy to see.",
				Category.VISUAL);
		instance = this;
		colour.showIf(() -> mode.idx() != 1);
		colour2.showIf(() -> mode.idx() == 2);
		speed.showIf(() -> mode.idx() != 0);
	}

	@Override
	public void init() {
		super.init();
		if (hooked) {
			return;
		}
		hooked = true;
		try {
			WorldRenderEvents.BEFORE_ENTITIES.register(ctx -> {
				if (instance != null && instance.enabled && instance.segCount > 0) {
					long t0 = System.nanoTime();
					try {
						instance.render(ctx.matrices(), ctx.commandQueue(), ctx.worldState().cameraRenderState.pos);
					} catch (RuntimeException ignored) {
					}
					instance.addCost(System.nanoTime() - t0);
				}
			});
		} catch (Throwable t) {
			System.err.println("[Sleep] Tripwire Colour unavailable: " + t);
		}
	}

	@Override
	protected void onDisable() {
		segCount = 0;
	}

	// ---------------------------------------------------------------- find the tripwires (a few times a second)
	@Override
	public void tick() {
		var w = mc.world;
		var p = mc.player;
		if (w == null || p == null) {
			segCount = 0;
			return;
		}
		if (++t % 8 != 0) {
			return;
		}
		long t0 = System.nanoTime();
		int r = Math.round(range.f());
		int cr = (r + 15) >> 4;
		int pcx = ((int) Math.floor(p.getX())) >> 4, pcz = ((int) Math.floor(p.getZ())) >> 4;
		float[] out = new float[7 * 256];
		int n = 0;
		BlockPos.Mutable m = new BlockPos.Mutable();
		BlockPos.Mutable nb = new BlockPos.Mutable();
		double y0 = height.f() / 100.0;
		outer:
		for (int cx = pcx - cr; cx <= pcx + cr; cx++) {
			for (int cz = pcz - cr; cz <= pcz + cr; cz++) {
				WorldChunk ch = w.getChunkManager().getWorldChunk(cx, cz);
				if (ch == null) {
					continue;
				}
				ChunkSection[] secs = ch.getSectionArray();
				int bottom = ch.getBottomSectionCoord();
				for (int si = 0; si < secs.length; si++) {
					ChunkSection sec = secs[si];
					if (sec == null || sec.isEmpty() || !sec.hasAny(s -> s.isOf(Blocks.TRIPWIRE))) {
						continue;
					}
					int by = (bottom + si) << 4;
					for (int ly = 0; ly < 16; ly++) {
						for (int lz = 0; lz < 16; lz++) {
							for (int lx = 0; lx < 16; lx++) {
								BlockState st = sec.getBlockState(lx, ly, lz);
								if (!st.isOf(Blocks.TRIPWIRE)) {
									continue;
								}
								int x = (cx << 4) + lx, y = by + ly, z = (cz << 4) + lz;
								double dx = x + 0.5 - p.getX(), dz = z + 0.5 - p.getZ();
								if (dx * dx + dz * dz > (double) r * r) {
									continue;
								}
								m.set(x, y, z);
								boolean on = triggered.on() && st.contains(Properties.POWERED) && st.get(Properties.POWERED);
								// four neighbours: a hook always gets its piece, wire-to-wire pieces only once (east / south)
								for (int d = 0; d < 4; d++) {
									int ox = d == 0 ? 1 : d == 1 ? -1 : 0, oz = d == 2 ? 1 : d == 3 ? -1 : 0;
									nb.set(x + ox, y, z + oz);
									BlockState ns = w.getBlockState(nb);
									Block nbk = ns.getBlock();
									boolean hook = nbk == Blocks.TRIPWIRE_HOOK;
									boolean wire = nbk == Blocks.TRIPWIRE;
									if (!hook && !wire) {
										continue;
									}
									if (wire && (d == 1 || d == 3)) {
										continue;
									}
									if (hook && !hooks.on()) {
										continue;
									}
									if (n + 7 > out.length) {
										if (out.length >= 7 * 4000) {
											break outer;
										}
										out = java.util.Arrays.copyOf(out, out.length * 2);
									}
									double ex = x + 0.5 + ox * (hook ? 0.7 : 1.0), ez = z + 0.5 + oz * (hook ? 0.7 : 1.0);
									boolean on2 = on || (wire && triggered.on() && ns.contains(Properties.POWERED) && ns.get(Properties.POWERED));
									out[n++] = x + 0.5F;
									out[n++] = (float) (y + y0);
									out[n++] = z + 0.5F;
									out[n++] = (float) ex;
									out[n++] = (float) (y + y0);
									out[n++] = (float) ez;
									out[n++] = on2 ? 1F : 0F;
								}
							}
						}
					}
				}
			}
		}
		seg = out;
		segCount = n / 7;
		addCost(System.nanoTime() - t0);
	}

	// ---------------------------------------------------------------- draw
	private int colourAt(double x, double z, float time) {
		return switch (mode.idx()) {
			case 1 -> Rainbow.hue(time * 0.5F + (float) (x + z) * 0.06F);
			case 2 -> {
				float k = 0.5F + 0.5F * (float) Math.sin(time * 3F + (x + z) * 0.35);
				yield Rainbow.mix(colour.c() | 0xFF000000, colour2.c() | 0xFF000000, k);
			}
			case 3 -> {
				float k = 0.3F + 0.7F * (0.5F + 0.5F * (float) Math.sin(time * 4F));
				yield Rainbow.mix(0xFF000000, colour.c() | 0xFF000000, k);
			}
			default -> colour.c() | 0xFF000000;
		};
	}

	private void render(MatrixStack m, OrderedRenderCommandQueue q, Vec3d cam) {
		float time = Rainbow.seconds() * (speed.f() / 100F);
		float w = width.f() / 100F;
		double r2 = (double) range.f() * range.f();
		WorldDraw.Lines lines = new WorldDraw.Lines(cam);
		float[] s = seg;
		int n = segCount;
		for (int i = 0; i < n; i++) {
			int o = i * 7;
			double mx = (s[o] + s[o + 3]) * 0.5 - cam.x, mz = (s[o + 2] + s[o + 5]) * 0.5 - cam.z;
			if (mx * mx + mz * mz > r2) {
				continue;
			}
			int ca, cb;
			if (s[o + 6] > 0.5F) {
				// a step on it: white flash
				int flash = (int) (System.currentTimeMillis() / 90 % 2) == 0 ? 0xFFFFFFFF : 0xFFFF4040;
				ca = flash;
				cb = flash;
			} else {
				ca = colourAt(s[o], s[o + 2], time);
				cb = colourAt(s[o + 3], s[o + 5], time);
			}
			lines.line(s[o], s[o + 1], s[o + 2], s[o + 3], s[o + 4], s[o + 5], w, ca, cb);
		}
		lines.draw(m, q);
	}
}
