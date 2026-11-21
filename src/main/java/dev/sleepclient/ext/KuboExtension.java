package dev.sleepclient.ext;

/**
 * A Sleep extension: a small separate jar (downloaded from the Sleep store into the mods folder) that adds mods to the
 * Right Shift menu. Declared in its fabric.mod.json as {@code "entrypoints": { "kubo": ["my.Extension"] }}.
 * {@link #register(KuboApi)} runs at start-up, before the settings are loaded, so its mods keep their settings.
 */
public interface KuboExtension {
	void register(KuboApi api);

	/** Uninstalled from the store while the game runs: switch off whatever register() hooked up (mods are removed for you). */
	default void unload() {
	}
}
