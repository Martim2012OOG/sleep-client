package dev.solclient.fx.wave;

/**
 * Settings of the wave system. Wave code adapted from Wavify / Tidal by Superkat32 (MIT licence, see
 * THIRD-PARTY-LICENSES/wavify-MIT.txt). The values are set by the "Realistic Waves" mod.
 */
public final class WaveCfg {
	/** Chunks around you that are scanned for shores and where waves are made. */
	public static volatile int chunkRadius = 4;
	/** Block changes in one chunk before that chunk is scanned again. */
	public static volatile int chunkUpdatesRescanAmount = 50;
	/** Waves are made on water this many blocks away from the shore. */
	public static final int waveDistFromShore = 8;
	/** Never more than this many waves alive at the same time (keeps the game smooth). */
	public static volatile int maxWaves = 24;

	private WaveCfg() {
	}
}
