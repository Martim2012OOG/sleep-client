package dev.solclient.mods;

import dev.solclient.fx.wave.WaveCfg;
import dev.solclient.fx.wave.WaveHandler;
import dev.solclient.option.Option;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Realistic Waves: animated waves roll in and wash up on the shore of lakes, rivers and the sea.
 * Wave code and textures adapted from "Wavify" (Tidal) by Superkat32 - MIT licence (see THIRD-PARTY-LICENSES/wavify-MIT.txt).
 * While the mod is off nothing is scanned, ticked or drawn, so it costs nothing.
 */
public class WavesMod extends Mod {

	private static WavesMod instance;
	private static boolean hooked;

	private final Option.Num range = num("Wave Range", 4, 2, 6, 1, " chunks").tip("How far from you waves are made. Lower = lighter.");
	private final Option.Num maxWaves = num("Max Waves", 24, 6, 60, 2, "").tip("Never more waves than this at once, so it cannot lag.");
	private final Option.Bool fpsGuard = bool("Protect My FPS", true).tip("Pauses new waves while the game is below 30 fps.");

	private WaveHandler handler;
	private ClientLevel handlerWorld;

	public WavesMod() {
		super("Realistic Waves", "realistic_waves", "Waves roll in and wash up on the shore. Credit: Wavify by Superkat32 (MIT).", Category.VISUAL);
		instance = this;
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}

	@Override
	public void init() {
		super.init();
		if (hooked) {
			return;
		}
		hooked = true;
		ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
			WavesMod m = instance;
			if (m != null && m.enabled && m.handler != null && world == m.handlerWorld) {
				m.handler.waterHandler.loadChunk(chunk);
			}
		});
		ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
			WavesMod m = instance;
			if (m != null && m.enabled && m.handler != null && world == m.handlerWorld) {
				m.handler.waterHandler.unloadChunk(chunk);
			}
		});
		LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> {
			long kuboPerfT0 = System.nanoTime();
			try {
			WavesMod m = instance;
			if (m == null || !m.enabled || m.handler == null) {
				return;
			}
			try {
				m.handler.render(ctx.poseStack(), ctx.submitNodeCollector(), ctx.levelState().cameraRenderState.pos, 0F);
			} catch (Throwable t) {
				System.err.println("[Caos] waves render: " + t);
				m.enabled = false; // never spam errors every frame
				m.handler = null;
			}
					} finally {
				dev.solclient.Perf.add("Waves", System.nanoTime() - kuboPerfT0);
			}
		});
	}

	public static void blockUpdate(BlockPos pos, BlockState state) {
		WavesMod m = instance;
		if (m != null && m.enabled && m.handler != null) {
			m.handler.waterHandler.onBlockUpdate(pos, state);
		}
	}

	@Override
	protected void onDisable() {
		handler = null;
		handlerWorld = null;
	}

	@Override
	public void tick() {
		ClientLevel w = mc.level;
		if (w == null || mc.player == null) {
			handler = null;
			handlerWorld = null;
			return;
		}
		WaveCfg.chunkRadius = Math.round(range.f());
		WaveCfg.maxWaves = Math.round(maxWaves.f());
		try {
			if (handler == null || handlerWorld != w) {
				handlerWorld = w;
				handler = new WaveHandler(w);
				int r = Math.max(2, mc.options.renderDistance().get()) + 3;
				ChunkPos pc = mc.player.chunkPosition();
				for (int x = pc.x() - r; x <= pc.x() + r; x++) {
					for (int z = pc.z() - r; z <= pc.z() + r; z++) {
						LevelChunk c = w.getChunkSource().getChunkNow(x, z);
						if (c != null && !c.isEmpty()) {
							handler.waterHandler.loadChunk(c);
						}
					}
				}
			}
			if (fpsGuard.on() && mc.getFps() < 30 && handler.waves.isEmpty()) {
				return;
			}
			handler.tick();
			while (handler.waves.size() > WaveCfg.maxWaves) {
				handler.waves.remove(0);
			}
		} catch (Throwable t) {
			System.err.println("[Caos] waves tick: " + t);
			handler = null;
			handlerWorld = null;
			enabled = false;
		}
	}
}
