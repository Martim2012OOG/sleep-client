package dev.solclient.mods;

import dev.solclient.lod.LodMesh;
import dev.solclient.lod.LodSampler;
import dev.solclient.lod.LodStore;
import dev.solclient.option.Option;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;

/**
 * Far Horizons: a Distant-Horizons style level of detail. Terrain you have seen (and, in singleplayer, terrain the game
 * generates quietly in the background) is remembered as a very light height/colour map and painted as simplified columns
 * far beyond your real render distance. Near the player the cells are small, further away they get bigger, so the number
 * of quads stays low and the game does not lag even with a huge distance. Vanilla chunks are never touched.
 */
public class FarHorizonsMod extends Mod {

	public static FarHorizonsMod instance;

	private static final Identifier WHITE = Identifier.of("minecraft", "textures/misc/white.png");
	/** Light of the LOD: full bright by day, darker at night (computed per frame from the world time). */
	private static int light(MinecraftClient mc) {
		long t = Math.floorMod(mc.world.getTimeOfDay(), 24000L); // hora do dia (getTime() e a idade do mundo e dessincroniza ao dormir / mudar a hora)
		float f = t < 12000 ? 1F : t < 13800 ? 1F - (t - 12000) / 1800F * 0.8F : t < 22200 ? 0.2F : 0.2F + (t - 22200) / 1800F * 0.8F;
		int b = Math.max(2, Math.round(f * 15F));
		return b << 20 | b << 4;
	}
	private static boolean hooked;
	/** Diagnostics: how many times each hook ran (to see what Sodium / Iris break). */
	private static volatile int fogCalls, farCalls, renderCalls, lastTiles, lastDrawn;
	private static volatile String lastError = "";

	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "kubo-lod");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY + 1);
		return t;
	});

	// ---- options (declaration order = order in the settings panel)
	private final Option.Num distance;
	private final Option.Choice quality;
	private final Option.Num brightness;
	private final Option.Col tint;
	private final Option.Bool fogExtend;
	private final Option.Num fogReach;
	private final Option.Choice generate;

	// ---- state
	private LodStore store = new LodStore();
	private Object lastWorld;
	private String storeFile = "";
	private final Set<Long> loaded = ConcurrentHashMap.newKeySet();
	private final ConcurrentLinkedQueue<Long> pending = new ConcurrentLinkedQueue<>();
	private final Set<Long> requested = new HashSet<>();
	private final AtomicInteger inflight = new AtomicInteger();
	private volatile LodMesh mesh;
	private volatile boolean building;
	private long lastBuildMs;
	private int builtHash, builtVersion = -1, loadedChanges, builtLoaded = -1;
	private int ticks;
	private long[] spiral = new long[0];
	private int spiralRadius = -1, cursor, genCx = Integer.MIN_VALUE, genCz;

	public FarHorizonsMod() {
		super("Far Horizons", "far_horizons", "See terrain far beyond your render distance without lag (like Distant Horizons).", Category.VISUAL);
		instance = this;
		header("Distance");
		distance = num("LOD Distance", 96, 16, 512, 16, " chunks");
		quality = tabs("LOD Quality", 1, "Low", "Medium", "High");
		header("Look");
		brightness = num("LOD Brightness", 100, 40, 160, 5, "%");
		tint = colour("LOD Tint", 0xFFFFFFFF);
		header("Fog");
		fogExtend = bool("Extend Fog To LOD", true);
		fogReach = num("Fog Reach", 100, 30, 100, 5, "%");
		header("Singleplayer");
		generate = choice("Generate Terrain", 2, "Off", "Slow", "Normal", "Fast");
		header("Data");
		action("LOD Cache", "Clear", this::clearCache, () -> store.size() + " chunks remembered");
		header("Diagnostics");
		action("Status", "Copy", this::copyDiag, this::diag);
	}

	@Override
	public boolean enabledByDefault() {
		return false;
	}

	@Override
	public void init() {
		super.init();
		if (hooked) {
			return;
		}
		hooked = true;
		System.out.println("[Sleep] Far Horizons: Sodium=" + net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium") + " Iris=" + net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("iris"));
		try {
			WorldRenderEvents.BEFORE_ENTITIES.register(ctx -> {
				long t0 = System.nanoTime();
				try {
					render(ctx.matrices(), ctx.commandQueue(), ctx.worldState().cameraRenderState.pos);
					if (instance != null && instance.enabled) {
						instance.addCost(System.nanoTime() - t0);
					}
				} catch (RuntimeException ex) {
					lastError = String.valueOf(ex);
				}
			});
		} catch (Throwable t) {
			System.err.println("[Sleep] Far Horizons rendering unavailable: " + t);
		}
		try {
			ClientLifecycleEvents.CLIENT_STOPPING.register(c -> saveNow());
		} catch (Throwable ignored) {
		}
	}

	@Override
	protected void onDisable() {
		mesh = null;
		saveNow();
	}

	// ------------------------------------------------------------------ hooks used by the mixins
	private static boolean live() {
		FarHorizonsMod m = instance;
		return m != null && m.enabled && m.mesh != null;
	}

	/** Called with the vanilla fog distance (chunks or blocks); returns the bigger one so the LOD is not hidden by fog. */
	public static int fogDistance(int original) {
		fogCalls++;
		if (!live() || !instance.fogExtend.on()) {
			return original;
		}
		int chunks = Math.round(instance.distance.f() * instance.fogReach.f() / 100F);
		return original <= 32 ? Math.max(original, chunks) : Math.max(original, chunks * 16);
	}

	public static float farPlane(float original) {
		farCalls++;
		if (!live()) {
			return original;
		}
		return Math.max(original, instance.distance.f() * 16F * 1.2F + 64F);
	}

	// ------------------------------------------------------------------ tick
	@Override
	public void tick() {
		var w = mc.world;
		var p = mc.player;
		if (w == null || p == null) {
			return;
		}
		ticks++;
		if (w != lastWorld) {
			switchWorld(w);
		}
		if (w.getDimension().hasCeiling()) {
			mesh = null;
			return;
		}
		int rd = mc.options.getViewDistance().getValue();
		int pcx = p.getBlockX() >> 4, pcz = p.getBlockZ() >> 4;

		if (ticks % 20 == 1) {
			scanLoaded(w, pcx, pcz, rd + 2, ticks % 200 == 1);
		}
		for (int i = 0; i < 6; i++) {
			Long k = pending.poll();
			if (k == null) {
				break;
			}
			Chunk c = w.getChunk(LodStore.kx(k), LodStore.kz(k), ChunkStatus.FULL, false);
			if (c != null) {
				LodStore.Chunk s = LodSampler.sample(w, c, false);
				if (s != null) {
					store.put(k, s);
				}
			}
		}
		generate(w, pcx, pcz, rd);
		maybeBuild(p.getBlockX(), p.getBlockZ());
		if (ticks % 3600 == 0 && store.dirty) {
			saveAsync();
		}
	}

	private void scanLoaded(net.minecraft.client.world.ClientWorld w, int pcx, int pcz, int radius, boolean refresh) {
		Set<Long> now = new HashSet<>();
		for (int dz = -radius; dz <= radius; dz++) {
			for (int dx = -radius; dx <= radius; dx++) {
				Chunk c = w.getChunk(pcx + dx, pcz + dz, ChunkStatus.FULL, false);
				if (c != null) {
					long k = LodStore.key(pcx + dx, pcz + dz);
					now.add(k);
					if (refresh || !loaded.contains(k)) {
						pending.add(k);
					}
				}
			}
		}
		if (!now.equals(loaded)) {
			loaded.retainAll(now);
			loaded.addAll(now);
			loadedChanges++;
		}
	}

	// ------------------------------------------------------------------ singleplayer generation
	private void generate(net.minecraft.client.world.ClientWorld w, int pcx, int pcz, int rd) {
		int mode = generate.idx();
		var server = mc.getServer();
		if (mode == 0 || server == null || !mc.isInSingleplayer()) {
			return;
		}
		int every = mode == 1 ? 6 : mode == 2 ? 2 : 1;
		if (ticks % every != 0 || inflight.get() >= 3) {
			return;
		}
		ServerWorld sw = server.getWorld(w.getRegistryKey());
		if (sw == null) {
			return;
		}
		int radius = Math.min(Math.round(distance.f()), 192);
		if (radius != spiralRadius) {
			buildSpiral(radius);
		}
		if (Math.abs(pcx - genCx) >= 4 || Math.abs(pcz - genCz) >= 4) {
			genCx = pcx;
			genCz = pcz;
			cursor = 0;
		}
		for (int step = 0; step < 2048 && cursor < spiral.length; step++) {
			long v = spiral[cursor++];
			int dx = (int) ((v >> 10) & 1023) - 512, dz = (int) (v & 1023) - 512;
			if (Math.max(Math.abs(dx), Math.abs(dz)) <= rd + 1) {
				continue;
			}
			int cx = pcx + dx, cz = pcz + dz;
			long k = LodStore.key(cx, cz);
			if (store.has(k) || requested.contains(k)) {
				continue;
			}
			requested.add(k);
			inflight.incrementAndGet();
			final LodStore st = store;
			server.execute(() -> {
				try {
					Chunk c = sw.getChunk(cx, cz, ChunkStatus.SURFACE, true);
					LodStore.Chunk s = c == null ? null : LodSampler.sample(sw, c, true);
					if (s != null) {
						st.put(k, s);
					}
				} catch (Throwable ignored) {
				} finally {
					inflight.decrementAndGet();
				}
			});
			return;
		}
	}

	private void buildSpiral(int radius) {
		int n = 0;
		long[] tmp = new long[(2 * radius + 1) * (2 * radius + 1)];
		for (int dz = -radius; dz <= radius; dz++) {
			for (int dx = -radius; dx <= radius; dx++) {
				long d2 = (long) dx * dx + (long) dz * dz;
				if (d2 > (long) radius * radius) {
					continue;
				}
				tmp[n++] = d2 << 20 | (long) (dx + 512) << 10 | (dz + 512);
			}
		}
		spiral = Arrays.copyOf(tmp, n);
		Arrays.sort(spiral);
		spiralRadius = radius;
		cursor = 0;
	}

	// ------------------------------------------------------------------ mesh
	private void maybeBuild(int px, int pz) {
		if (building) {
			return;
		}
		long now = System.currentTimeMillis();
		int hash = Arrays.hashCode(new int[] { distance.i(), quality.idx(), Math.round(brightness.f()), tint.c() });
		LodMesh m = mesh;
		boolean moved = m == null || Math.abs(px - m.ox) > 24 || Math.abs(pz - m.oz) > 24;
		boolean settings = hash != builtHash;
		boolean data = store.version != builtVersion || loadedChanges != builtLoaded;
		if (!((moved || settings) && now - lastBuildMs > 400) && !(data && now - lastBuildMs > 2500)) {
			return;
		}
		building = true;
		lastBuildMs = now;
		builtHash = hash;
		builtVersion = store.version;
		builtLoaded = loadedChanges;
		LodMesh.Params prm = new LodMesh.Params();
		prm.ox = px;
		prm.oz = pz;
		prm.maxDist = distance.i() * 16;
		prm.base = quality.idx() == 0 ? 16 : quality.idx() == 1 ? 8 : 4;
		prm.brightness = brightness.f() / 100F;
		prm.tint = tint.c();
		prm.loaded = new HashSet<>(loaded);
		final LodStore st = store;
		WORKER.execute(() -> {
			try {
				mesh = LodMesh.build(st, prm);
			} catch (Throwable ignored) {
			} finally {
				building = false;
			}
		});
	}

	// ------------------------------------------------------------------ render
	public static void render(MatrixStack matrices, net.minecraft.client.render.command.OrderedRenderCommandQueue queue, Vec3d cam) {
		FarHorizonsMod m = instance;
		if (m == null || !m.enabled) {
			return;
		}
		renderCalls++;
		LodMesh mesh = m.mesh;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mesh == null || mc.world == null || mc.gameRenderer == null) {
			return;
		}
		// which tiles can be on screen (horizontal cone around where the camera looks)
		float yaw = (float) Math.toRadians(mc.gameRenderer.getCamera().getYaw());
		float fx = (float) -Math.sin(yaw), fz = (float) Math.cos(yaw);
		float aspect = mc.getWindow().getFramebufferWidth() / (float) Math.max(1, mc.getWindow().getFramebufferHeight());
		float half = (float) Math.atan(Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0) * aspect) + 0.35F;
		LodMesh.Tile[] vis = new LodMesh.Tile[mesh.tiles.length];
		int count = 0;
		for (LodMesh.Tile t : mesh.tiles) {
			float dx = mesh.ox + t.cx - (float) cam.x, dz = mesh.oz + t.cz - (float) cam.z;
			float dist = (float) Math.sqrt(dx * dx + dz * dz);
			if (dist > t.r) {
				float cos = (fx * dx + fz * dz) / dist;
				float ang = (float) Math.acos(Math.max(-1F, Math.min(1F, cos)));
				if (ang > half + (float) Math.asin(Math.min(1F, t.r / dist))) {
					continue;
				}
			}
			vis[count++] = t;
		}
		lastTiles = count;
		if (count == 0) {
			return;
		}
		final int n = count;
		final int lt = light(mc);
		matrices.push();
		matrices.translate(mesh.ox - cam.x, -cam.y, mesh.oz - cam.z);
		queue.submitCustom(matrices, RenderLayers.entityCutoutNoCull(WHITE), (e, vc) -> {
			lastDrawn = n;
			draw(vc, e, vis, n, lt);
		});
		matrices.pop();
	}

	private static void draw(VertexConsumer vc, MatrixStack.Entry e, LodMesh.Tile[] vis, int count, int lt) {
		for (int t = 0; t < count; t++) {
			LodMesh.Tile tl = vis[t];
			float[] pos = tl.pos;
			for (int q = 0, o = 0; q < tl.n; q++, o += 12) {
				int c = tl.col[q], f = tl.face[q];
				float nx = f == 1 ? -1F : f == 2 ? 1F : 0F, ny = f == 0 ? 1F : 0F, nz = f == 3 ? -1F : f == 4 ? 1F : 0F;
				for (int v = 0; v < 12; v += 3) {
					vc.vertex(e, pos[o + v], pos[o + v + 1], pos[o + v + 2]).color(c).texture(0.5F, 0.5F).overlay(OverlayTexture.DEFAULT_UV).light(lt).normal(e, nx, ny, nz);
				}
			}
		}
	}


	// ------------------------------------------------------------------ diagnostics
	private String diag() {
		var fl = net.fabricmc.loader.api.FabricLoader.getInstance();
		LodMesh m = mesh;
		return (fl.isModLoaded("sodium") ? "Sodium" : "no Sodium") + (fl.isModLoaded("iris") ? "+Iris" : "")
			+ " | mesh " + (m == null ? "none" : m.tiles.length + " tiles")
			+ " | fog " + fogCalls + " far " + farCalls + " render " + renderCalls
			+ " | visible " + lastTiles + " drawn " + lastDrawn
			+ (lastError.isEmpty() ? "" : " | err " + lastError);
	}

	private void copyDiag() {
		mc.keyboard.setClipboard(diag());
	}

	// ------------------------------------------------------------------ storage
	private void switchWorld(net.minecraft.client.world.ClientWorld w) {
		saveNow();
		lastWorld = w;
		store = new LodStore();
		mesh = null;
		loaded.clear();
		pending.clear();
		requested.clear();
		spiralRadius = -1;
		cursor = 0;
		genCx = Integer.MIN_VALUE;
		builtVersion = -1;
		storeFile = fileFor(w);
		final LodStore st = store;
		final Path f = Path.of(storeFile);
		WORKER.execute(() -> st.load(f));
	}

	private String fileFor(net.minecraft.client.world.ClientWorld w) {
		String key = "unknown";
		try {
			if (mc.isInSingleplayer() && mc.getServer() != null) {
				key = "sp_" + mc.getServer().getSaveProperties().getLevelName();
			} else if (mc.getCurrentServerEntry() != null) {
				key = "mp_" + mc.getCurrentServerEntry().address.toLowerCase(java.util.Locale.ROOT);
			}
		} catch (RuntimeException ignored) {
		}
		String dim = w.getRegistryKey().getValue().toString();
		String safe = key.replaceAll("[^a-zA-Z0-9._-]", "_");
		return FabricLoader.getInstance().getGameDir().resolve("kubo-lod").resolve(safe).resolve(dim.replaceAll("[^a-zA-Z0-9._-]", "_") + ".bin").toString();
	}

	private void saveAsync() {
		if (storeFile.isEmpty()) {
			return;
		}
		final LodStore st = store;
		final Path f = Path.of(storeFile);
		WORKER.execute(() -> st.save(f));
	}

	private void saveNow() {
		if (storeFile.isEmpty() || !store.dirty) {
			return;
		}
		store.save(Path.of(storeFile));
	}

	private void clearCache() {
		store.clear();
		requested.clear();
		cursor = 0;
		mesh = null;
		if (!storeFile.isEmpty()) {
			try {
				java.nio.file.Files.deleteIfExists(Path.of(storeFile));
			} catch (java.io.IOException ignored) {
			}
		}
	}
}
