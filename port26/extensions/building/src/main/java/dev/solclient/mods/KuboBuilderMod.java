package dev.solclient.mods;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.solclient.Gfx;
import dev.solclient.Keys;
import dev.solclient.Lang;
import dev.solclient.builder.Placement;
import dev.solclient.builder.Schematic;
import dev.solclient.option.Option;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Sleep Builder: our own Litematica, made simple and light. Load a schematic, it appears where you stand as see-through
 * "ghost" blocks; move it with the arrows / Page Up / Page Down, turn it, mirror it, show one layer at a time, see the
 * materials you need and what is still missing or wrong. Only blocks near you are drawn, all in one batch, and the
 * world is checked a little every tick, so it stays smooth even with huge builds.
 */
public class KuboBuilderMod extends HudMod {

	public static KuboBuilderMod instance;

	public final Option.Key menuKey = key("Builder Menu Key", Keys.withMods(66, true, false, false)); // Ctrl+B
	private final Option.Bool arrows = bool("Move With Arrows / Page Up / Page Down", true);
	public final Option.Key rotateKey = key("Rotate Key", Keys.withMods(82, true, false, false)); // Ctrl+R
	public final Option.Key mirrorKey = key("Mirror Key", Keys.withMods(77, true, false, false)); // Ctrl+M
	public final Option.Key layerUpKey = key("Layer Up Key", 268); // Home
	public final Option.Key layerDownKey = key("Layer Down Key", 269); // End
	public final Option.Key hideKey = key("Show / Hide Key", Keys.withMods(72, true, false, false)); // Ctrl+H
	public final Option.Num range = num("Draw Range", 40, 16, 96, 4, " blocks");
	public final Option.Num opacity = num("Ghost Opacity", 45, 15, 90, 5, "%");
	public final Option.Bool showWrong = bool("Show Wrong Blocks", true);
	public final Option.Bool smartPick = bool("Smart Pick (select the block you need)", true);

	public Placement placement;
	public boolean visible = true;
	public int layerMode; // 0 all, 1 only this layer, 2 this layer and below
	public int layer;
	public String message = "";
	public long messageAt;

	// what was found near you (refreshed a few times a second): x, y, z, palette index, status (0 missing, 1 wrong)
	private int[] near = new int[0];
	private int nearCount;
	private long lastScan;
	private boolean dirty = true;
	// progress of the whole build (scanned a slice per tick)
	private int scanIdx;
	private int correctAcc, wrongAcc, missingAcc;
	public int correct, wrong, missing;
	private final Map<Block, Integer> placedAcc = new HashMap<>();
	public Map<Block, Integer> placedCount = new HashMap<>();
	private final boolean[] down = new boolean[8];
	private static boolean hooked;

	public KuboBuilderMod() {
		super("Sleep Builder", "kubo_builder", "Our own Litematica: load schematics, place them, move them easily and build without lag.", 0.01F, 0.6F);
		instance = this;
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}

	public static Path schematicsDir() {
		return FabricLoader.getInstance().getGameDir().resolve("schematics");
	}

	private static Path stateFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("kubo-builder.json");
	}

	public void say(String s) {
		message = s;
		messageAt = System.currentTimeMillis();
	}

	// ------------------------------------------------------------------ load / place
	public boolean load(Path file) {
		try {
			Schematic s = Schematic.load(file);
			Placement p = new Placement(s);
			p.file = file.toAbsolutePath().toString();
			if (mc.player != null) {
				p.origin = mc.player.blockPosition();
			}
			p.dimension = dim();
			placement = p;
			layer = p.origin.getY();
			visible = true;
			changed();
			say(Lang.tr("Loaded ", "Carregado ") + s.name + " (" + s.sx + "x" + s.sy + "x" + s.sz + ", " + s.nonAir + Lang.tr(" blocks)", " blocos)"));
			saveState();
			return true;
		} catch (Throwable t) {
			say(Lang.tr("Could not load: ", "Não deu para carregar: ") + t.getMessage());
			return false;
		}
	}

	public void unload() {
		placement = null;
		near = new int[0];
		nearCount = 0;
		saveState();
	}

	public void changed() {
		dirty = true;
		scanIdx = 0;
		correctAcc = wrongAcc = missingAcc = 0;
		placedAcc.clear();
		saveState();
	}

	public void move(int dx, int dy, int dz) {
		if (placement == null) {
			return;
		}
		placement.origin = placement.origin.offset(dx, dy, dz);
		if (layerMode != 0) {
			layer += dy;
		}
		changed();
	}

	/** Moves the schematic relative to where you look (forward = away from you). */
	public void moveRelative(int forward, int right, int up) {
		if (mc.player == null) {
			return;
		}
		Direction f = mc.player.getDirection();
		Direction r = f.getClockWise();
		move(f.getStepX() * forward + r.getStepX() * right, up, f.getStepZ() * forward + r.getStepZ() * right);
	}

	public void rotate() {
		if (placement != null) {
			placement.rotation = (placement.rotation + 1) & 3;
			changed();
			say(Lang.tr("Rotated ", "Rodado ") + placement.rotation * 90 + "°");
		}
	}

	public void mirror() {
		if (placement != null) {
			if (!placement.mirrorX && !placement.mirrorZ) {
				placement.mirrorX = true;
			} else if (placement.mirrorX && !placement.mirrorZ) {
				placement.mirrorX = false;
				placement.mirrorZ = true;
			} else {
				placement.mirrorZ = false;
			}
			changed();
			say(placement.mirrorX ? Lang.tr("Mirror: X", "Espelho: X") : placement.mirrorZ ? Lang.tr("Mirror: Z", "Espelho: Z") : Lang.tr("Mirror: off", "Espelho: desligado"));
		}
	}

	public void hereAtMyFeet() {
		if (placement != null && mc.player != null) {
			placement.origin = mc.player.blockPosition();
			layer = placement.origin.getY();
			changed();
		}
	}

	private String dim() {
		return mc.level == null ? "" : mc.level.dimension().identifier().toString();
	}

	private void saveState() {
		try {
			JsonObject o = new JsonObject();
			if (placement != null) {
				o.addProperty("file", placement.file);
				o.addProperty("x", placement.origin.getX());
				o.addProperty("y", placement.origin.getY());
				o.addProperty("z", placement.origin.getZ());
				o.addProperty("rot", placement.rotation);
				o.addProperty("mx", placement.mirrorX);
				o.addProperty("mz", placement.mirrorZ);
				o.addProperty("dim", placement.dimension);
			}
			Files.writeString(stateFile(), o.toString(), StandardCharsets.UTF_8);
		} catch (Exception ignored) {
		}
	}

	private boolean restored;

	private void restoreState() {
		if (restored || mc.level == null) {
			return;
		}
		restored = true;
		try {
			if (!Files.exists(stateFile())) {
				return;
			}
			JsonObject o = JsonParser.parseString(Files.readString(stateFile(), StandardCharsets.UTF_8)).getAsJsonObject();
			if (!o.has("file")) {
				return;
			}
			Path f = Path.of(o.get("file").getAsString());
			if (!Files.exists(f)) {
				return;
			}
			Schematic s = Schematic.load(f);
			Placement p = new Placement(s);
			p.file = f.toString();
			p.origin = new BlockPos(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt());
			p.rotation = o.get("rot").getAsInt();
			p.mirrorX = o.get("mx").getAsBoolean();
			p.mirrorZ = o.get("mz").getAsBoolean();
			p.dimension = o.has("dim") ? o.get("dim").getAsString() : "";
			placement = p;
			layer = p.origin.getY();
			changed();
		} catch (Throwable ignored) {
		}
	}

	// ------------------------------------------------------------------ tick: keys + scanning
	private boolean edge(int i, int code) {
		boolean d = dev.solclient.compat.Mc.screen(mc) == null && Keys.down(code);
		boolean e = d && !down[i];
		down[i] = d;
		return e;
	}

	@Override
	public void init() {
		super.init();
		if (hooked) {
			return;
		}
		hooked = true;
		try {
			LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> {
				if (instance != null && instance.enabled && instance.visible && instance.placement != null && instance.nearCount > 0) {
					try {
						instance.render(ctx.poseStack(), ctx.submitNodeCollector(), ctx.levelState().cameraRenderState.pos);
					} catch (RuntimeException ignored) {
					}
				}
			});
		} catch (Throwable t) {
			System.err.println("[Sleep] Builder render unavailable: " + t);
		}
	}

	@Override
	public void tick() {
		restoreState();
		if (edge(0, menuKey.code())) {
			dev.solclient.compat.Mc.setScreen(mc, new dev.solclient.gui.BuilderScreen(null));
			return;
		}
		if (placement == null) {
			return;
		}
		if (edge(1, rotateKey.code())) {
			rotate();
		}
		if (edge(2, mirrorKey.code())) {
			mirror();
		}
		if (edge(3, hideKey.code())) {
			visible = !visible;
		}
		if (edge(4, layerUpKey.code())) {
			if (layerMode == 0) {
				layerMode = 1;
			}
			layer++;
			dirty = true;
		}
		if (edge(5, layerDownKey.code())) {
			if (layerMode == 0) {
				layerMode = 1;
			}
			layer--;
			dirty = true;
		}
		if (arrows.on() && dev.solclient.compat.Mc.screen(mc) == null) {
			// arrows move it relative to where you look; Page Up / Down move it up and down
			if (edgeRaw(263)) {
				moveRelative(0, -1, 0);
			}
			if (edgeRaw(262)) {
				moveRelative(0, 1, 0);
			}
			if (edgeRaw(265)) {
				moveRelative(1, 0, 0);
			}
			if (edgeRaw(264)) {
				moveRelative(-1, 0, 0);
			}
			if (edgeRaw(266)) {
				move(0, 1, 0);
			}
			if (edgeRaw(267)) {
				move(0, -1, 0);
			}
		}
		if (!dim().equals(placement.dimension)) {
			nearCount = 0;
			return;
		}
		long now = System.currentTimeMillis();
		if (dirty || now - lastScan > 400) {
			scanNear();
			lastScan = now;
			dirty = false;
		}
		scanSlice();
		if (smartPick.on()) {
			smartPick();
		}
	}

	private final Map<Integer, Boolean> rawPrev = new HashMap<>();
	private final Map<Integer, Long> rawRepeat = new HashMap<>();

	/** Arrow keys: a press moves 1 block, holding repeats after a moment. */
	private boolean edgeRaw(int code) {
		boolean d = Keys.rawDown(code) && !Keys.ctrlHeld();
		boolean prev = rawPrev.getOrDefault(code, false);
		rawPrev.put(code, d);
		long now = System.currentTimeMillis();
		if (d && !prev) {
			rawRepeat.put(code, now + 350);
			return true;
		}
		if (d && now >= rawRepeat.getOrDefault(code, Long.MAX_VALUE)) {
			rawRepeat.put(code, now + 80);
			return true;
		}
		return false;
	}

	private boolean layerOk(int y) {
		return layerMode == 0 || layerMode == 1 && y == layer || layerMode == 2 && y <= layer;
	}

	private static boolean same(BlockState a, BlockState b) {
		return a == b;
	}

	/** Finds the missing / wrong blocks around you (only inside the draw range). */
	private void scanNear() {
		Placement p = placement;
		if (p == null || mc.player == null || mc.level == null) {
			return;
		}
		int r = range.i();
		BlockPos me = mc.player.blockPosition();
		BlockPos o = p.origin;
		int x0 = Math.max(o.getX(), me.getX() - r), x1 = Math.min(o.getX() + p.wx() - 1, me.getX() + r);
		int y0 = Math.max(o.getY(), me.getY() - r), y1 = Math.min(o.getY() + p.schem.sy - 1, me.getY() + r);
		int z0 = Math.max(o.getZ(), me.getZ() - r), z1 = Math.min(o.getZ() + p.wz() - 1, me.getZ() + r);
		BlockState[] pal = p.palette();
		int[] buf = near.length >= 5 * 4096 ? near : new int[5 * 4096];
		int n = 0;
		int max = 25000;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int y = y0; y <= y1 && n < max; y++) {
			if (!layerOk(y)) {
				continue;
			}
			for (int z = z0; z <= z1 && n < max; z++) {
				for (int x = x0; x <= x1 && n < max; x++) {
					int idx = p.indexAt(x, y, z);
					if (idx < 0) {
						continue;
					}
					int pi = p.schem.data[idx];
					BlockState want = pal[pi];
					if (want.isAir()) {
						continue;
					}
					if (enclosed(p, pal, x, y, z)) {
						continue; // hidden inside the build: no need to draw it
					}
					BlockState have = mc.level.getBlockState(m.set(x, y, z));
					int status;
					if (same(have, want)) {
						continue;
					} else if (have.isAir() || have.canBeReplaced()) {
						status = 0;
					} else if (showWrong.on()) {
						status = 1;
					} else {
						continue;
					}
					if ((n + 1) * 5 > buf.length) {
						int[] nb = new int[buf.length * 2];
						System.arraycopy(buf, 0, nb, 0, buf.length);
						buf = nb;
					}
					int b = n * 5;
					buf[b] = x;
					buf[b + 1] = y;
					buf[b + 2] = z;
					buf[b + 3] = pi;
					buf[b + 4] = status;
					n++;
				}
			}
		}
		near = buf;
		nearCount = n;
	}

	/** True when the block is surrounded on all 6 sides by other blocks of the schematic (shown layer only). */
	private boolean enclosed(Placement p, BlockState[] pal, int x, int y, int z) {
		return solid(p, pal, x + 1, y, z) && solid(p, pal, x - 1, y, z) && solid(p, pal, x, y, z + 1) && solid(p, pal, x, y, z - 1)
				&& solid(p, pal, x, y + 1, z) && solid(p, pal, x, y - 1, z);
	}

	private boolean solid(Placement p, BlockState[] pal, int x, int y, int z) {
		if (!layerOk(y)) {
			return false;
		}
		int i = p.indexAt(x, y, z);
		return i >= 0 && pal[p.schem.data[i]].isSolidRender();
	}

	/** Checks a slice of the whole build every tick for the progress bar and the material list. */
	private void scanSlice() {
		Placement p = placement;
		if (p == null || mc.level == null) {
			return;
		}
		Schematic s = p.schem;
		int total = s.data.length;
		BlockState[] pal = p.palette();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		int budget = 30000;
		while (budget-- > 0) {
			if (scanIdx >= total) {
				correct = correctAcc;
				wrong = wrongAcc;
				missing = missingAcc;
				placedCount = new HashMap<>(placedAcc);
				scanIdx = 0;
				correctAcc = wrongAcc = missingAcc = 0;
				placedAcc.clear();
				return;
			}
			int i = scanIdx++;
			BlockState want = pal[s.data[i]];
			if (want.isAir()) {
				continue;
			}
			int sx = i % s.sx, sz = i / s.sx % s.sz, sy = i / (s.sx * s.sz);
			// schematic -> world (inverse of Placement.indexAt)
			int mx = p.mirrorX ? s.sx - 1 - sx : sx, mz = p.mirrorZ ? s.sz - 1 - sz : sz;
			int lx, lz;
			switch (p.rotation & 3) {
				case 1 -> {
					lx = s.sz - 1 - mz;
					lz = mx;
				}
				case 2 -> {
					lx = s.sx - 1 - mx;
					lz = s.sz - 1 - mz;
				}
				case 3 -> {
					lx = mz;
					lz = s.sx - 1 - mx;
				}
				default -> {
					lx = mx;
					lz = mz;
				}
			}
			m.set(p.origin.getX() + lx, p.origin.getY() + sy, p.origin.getZ() + lz);
			if (!mc.level.hasChunk(m.getX() >> 4, m.getZ() >> 4)) {
				missingAcc++;
				continue;
			}
			BlockState have = mc.level.getBlockState(m);
			if (have == want) {
				correctAcc++;
				placedAcc.merge(want.getBlock(), 1, Integer::sum);
			} else if (have.isAir() || have.canBeReplaced()) {
				missingAcc++;
			} else {
				wrongAcc++;
			}
		}
	}

	/** Looking at a missing block: select the block it needs in your hotbar (only changes which slot is selected). */
	private BlockState lookingWant;
	private BlockPos lookingPos;

	private void smartPick() {
		lookingWant = null;
		lookingPos = null;
		if (mc.player == null || placement == null || dev.solclient.compat.Mc.screen(mc) != null) {
			return;
		}
		Vec3 eye = mc.player.getEyePosition(), dir = mc.player.getViewVector(1F);
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (double d = 0.5; d < 6; d += 0.25) {
			Vec3 at = eye.add(dir.scale(d));
			m.set(Math.floor(at.x), Math.floor(at.y), Math.floor(at.z));
			BlockState want = placement.wanted(m.getX(), m.getY(), m.getZ());
			if (want != null && !want.isAir() && layerOk(m.getY())) {
				BlockState have = mc.level.getBlockState(m);
				if (have != want && (have.isAir() || have.canBeReplaced())) {
					lookingWant = want;
					lookingPos = m.immutable();
					Item it = want.getBlock().asItem();
					var inv = mc.player.getInventory();
					if (!inv.getSelectedItem().is(it)) {
						for (int i = 0; i < 9; i++) {
							if (inv.getItem(i).is(it)) {
								inv.setSelectedSlot(i);
								break;
							}
						}
					}
					return;
				}
				if (!have.isAir()) {
					return;
				}
			} else if (!mc.level.getBlockState(m).isAir()) {
				return;
			}
		}
	}

	// ------------------------------------------------------------------ rendering (one batch)
	private final Map<BlockState, float[]> uvCache = new HashMap<>();
	private final Map<BlockState, float[]> shapeCache = new HashMap<>();

	private float[] uv(BlockState s) {
		return uvCache.computeIfAbsent(s, k -> {
			try {
				TextureAtlasSprite sp = mc.getModelManager().getBlockStateModelSet().getParticleMaterial(k).sprite();
				return new float[] {sp.getU0(), sp.getU1(), sp.getV0(), sp.getV1()};
			} catch (RuntimeException e) {
				return new float[] {0, 1, 0, 1};
			}
		});
	}

	/** The block's outline (so slabs are half height, carpets thin...), from 0 to 1. */
	private float[] shape(BlockState s) {
		return shapeCache.computeIfAbsent(s, k -> {
			try {
				var sh = k.getShape(mc.level, BlockPos.ZERO);
				if (sh.isEmpty()) {
					return new float[] {0.25F, 0, 0.25F, 0.75F, 0.5F, 0.75F};
				}
				AABB b = sh.bounds();
				return new float[] {(float) b.minX, (float) b.minY, (float) b.minZ, (float) b.maxX, (float) b.maxY, (float) b.maxZ};
			} catch (RuntimeException e) {
				return new float[] {0, 0, 0, 1, 1, 1};
			}
		});
	}

	private void render(PoseStack ms, SubmitNodeCollector q, Vec3 cam) {
		final int[] buf = near;
		final int n = nearCount;
		final BlockState[] pal = placement.palette();
		final int alpha = Math.round(opacity.f() / 100F * 255);
		final double cx = cam.x, cy = cam.y, cz = cam.z;
		q.submitCustomGeometry(ms, RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS), (entry, vc) -> {
			Matrix4f mat = entry.pose();
			for (int i = 0; i < n; i++) {
				int b = i * 5;
				BlockState st = pal[buf[b + 3]];
				boolean wrongBlock = buf[b + 4] == 1;
				float[] uv = uv(st);
				float[] sh = wrongBlock ? new float[] {0, 0, 0, 1, 1, 1} : shape(st);
				float e = wrongBlock ? -0.003F : 0.02F;
				float x0 = (float) (buf[b] - cx) + sh[0] + e, y0 = (float) (buf[b + 1] - cy) + sh[1] + e, z0 = (float) (buf[b + 2] - cz) + sh[2] + e;
				float x1 = (float) (buf[b] - cx) + sh[3] - e, y1 = (float) (buf[b + 1] - cy) + sh[4] - e, z1 = (float) (buf[b + 2] - cz) + sh[5] - e;
				int col = wrongBlock ? (Math.min(255, alpha + 40) << 24) | 0xFF3030 : (alpha << 24) | 0xC8E6FF;
				box(vc, mat, entry, x0, y0, z0, x1, y1, z1, uv, col);
			}
		});
		// a thin frame around the whole schematic so you always see where it is
		dev.solclient.fx.WorldDraw.Lines l = new dev.solclient.fx.WorldDraw.Lines(cam);
		BlockPos o = placement.origin;
		l.box(o.getX(), o.getY(), o.getZ(), o.getX() + placement.wx(), o.getY() + placement.schem.sy, o.getZ() + placement.wz(), 0.04F, 0xCC7DD3FC);
		if (layerMode != 0) {
			l.box(o.getX(), layer, o.getZ(), o.getX() + placement.wx(), layer + 1, o.getZ() + placement.wz(), 0.03F, 0xCCFFD166);
		}
		if (lookingPos != null) {
			l.box(lookingPos.getX() - 0.005, lookingPos.getY() - 0.005, lookingPos.getZ() - 0.005, lookingPos.getX() + 1.005, lookingPos.getY() + 1.005,
					lookingPos.getZ() + 1.005, 0.03F, 0xFF4ADE80);
		}
		l.draw(ms, q);
	}

	private static void v(com.mojang.blaze3d.vertex.VertexConsumer vc, Matrix4f m, PoseStack.Pose e, float x, float y, float z, float u, float vv, int col,
			float nx, float ny, float nz) {
		vc.addVertex(m, x, y, z).setColor(col).setUv(u, vv).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(e, nx, ny, nz);
	}

	private static void box(com.mojang.blaze3d.vertex.VertexConsumer vc, Matrix4f m, PoseStack.Pose e, float x0, float y0, float z0, float x1, float y1, float z1,
			float[] uv, int c) {
		float u0 = uv[0], u1 = uv[1], v0 = uv[2], v1 = uv[3];
		// up
		v(vc, m, e, x0, y1, z0, u0, v0, c, 0, 1, 0);
		v(vc, m, e, x0, y1, z1, u0, v1, c, 0, 1, 0);
		v(vc, m, e, x1, y1, z1, u1, v1, c, 0, 1, 0);
		v(vc, m, e, x1, y1, z0, u1, v0, c, 0, 1, 0);
		// down
		v(vc, m, e, x0, y0, z0, u0, v0, c, 0, -1, 0);
		v(vc, m, e, x1, y0, z0, u1, v0, c, 0, -1, 0);
		v(vc, m, e, x1, y0, z1, u1, v1, c, 0, -1, 0);
		v(vc, m, e, x0, y0, z1, u0, v1, c, 0, -1, 0);
		// north (-z)
		v(vc, m, e, x0, y0, z0, u1, v1, c, 0, 0, -1);
		v(vc, m, e, x0, y1, z0, u1, v0, c, 0, 0, -1);
		v(vc, m, e, x1, y1, z0, u0, v0, c, 0, 0, -1);
		v(vc, m, e, x1, y0, z0, u0, v1, c, 0, 0, -1);
		// south (+z)
		v(vc, m, e, x0, y0, z1, u0, v1, c, 0, 0, 1);
		v(vc, m, e, x1, y0, z1, u1, v1, c, 0, 0, 1);
		v(vc, m, e, x1, y1, z1, u1, v0, c, 0, 0, 1);
		v(vc, m, e, x0, y1, z1, u0, v0, c, 0, 0, 1);
		// west (-x)
		v(vc, m, e, x0, y0, z0, u0, v1, c, -1, 0, 0);
		v(vc, m, e, x0, y0, z1, u1, v1, c, -1, 0, 0);
		v(vc, m, e, x0, y1, z1, u1, v0, c, -1, 0, 0);
		v(vc, m, e, x0, y1, z0, u0, v0, c, -1, 0, 0);
		// east (+x)
		v(vc, m, e, x1, y0, z0, u1, v1, c, 1, 0, 0);
		v(vc, m, e, x1, y1, z0, u1, v0, c, 1, 0, 0);
		v(vc, m, e, x1, y1, z1, u0, v0, c, 1, 0, 0);
		v(vc, m, e, x1, y0, z1, u0, v1, c, 1, 0, 0);
	}

	// ------------------------------------------------------------------ HUD: progress + what you are looking at
	@Override
	public boolean visible() {
		return placement != null;
	}

	@Override
	public int width() {
		return 170;
	}

	@Override
	public int height() {
		return lookingWant != null ? 52 : 38;
	}

	@Override
	protected void renderHud(GuiGraphicsExtractor c, boolean edit) {
		int w = width(), h = height();
		Gfx.rrectFree(c, 0, 0, w, h, 6, 0xC00D0F14);
		Placement p = placement;
		String name = p == null ? "My build" : p.schem.name;
		Gfx.textRaw(c, cut(name, w - 50), 7, 5, 0xFFFFFFFF);
		int total = p == null ? 100 : Math.max(1, p.schem.nonAir);
		int done = p == null ? 42 : correct;
		String pct = Math.round(done * 100F / total) + "%";
		Gfx.textRaw(c, pct, w - 7 - Gfx.twRaw(pct), 5, 0xFF4ADE80);
		Gfx.rrectFree(c, 7, 17, w - 14, 4, 2, 0x44FFFFFF);
		Gfx.rrectFree(c, 7, 17, Math.max(2, (w - 14) * done / (float) total), 4, 2, 0xFF4ADE80);
		String line = (p == null ? "" : missing + Lang.tr(" missing", " em falta") + "  ·  " + wrong + Lang.tr(" wrong", " errados"))
				+ (layerMode != 0 ? "  ·  y " + layer : "");
		Gfx.textRaw(c, line, 7, 25, 0xFFB9BDC9);
		if (lookingWant != null) {
			ItemStack st = new ItemStack(lookingWant.getBlock().asItem());
			c.item(st, 6, 34);
			Gfx.textRaw(c, cut(lookingWant.getBlock().getName().getString(), w - 34), 26, 39, 0xFFFFD166);
		}
		if (System.currentTimeMillis() - messageAt < 4000 && !message.isEmpty()) {
			Gfx.textRaw(c, cut(message, w), 0, h + 3, 0xFFE6E8EE);
		}
	}

	private static String cut(String s, int w) {
		if (Gfx.twRaw(s) <= w) {
			return s;
		}
		while (s.length() > 1 && Gfx.twRaw(s + "..") > w) {
			s = s.substring(0, s.length() - 1);
		}
		return s + "..";
	}

	/** Materials: block -> {needed, placed, in your inventory}. */
	public List<Object[]> materials() {
		List<Object[]> out = new ArrayList<>();
		if (placement == null) {
			return out;
		}
		Map<Item, Integer> inv = new HashMap<>();
		if (mc.player != null) {
			var pi = mc.player.getInventory();
			for (int i = 0; i < pi.getContainerSize(); i++) {
				ItemStack s = pi.getItem(i);
				if (!s.isEmpty()) {
					inv.merge(s.getItem(), s.getCount(), Integer::sum);
				}
			}
		}
		for (Map.Entry<Block, Integer> e : placement.schem.materials().entrySet()) {
			Item it = e.getKey().asItem();
			int placed = placedCount.getOrDefault(e.getKey(), 0);
			out.add(new Object[] {e.getKey(), e.getValue(), placed, inv.getOrDefault(it, 0)});
		}
		out.sort((a, b) -> Integer.compare(((Integer) b[1]) - ((Integer) b[2]), ((Integer) a[1]) - ((Integer) a[2])));
		return out;
	}
}
