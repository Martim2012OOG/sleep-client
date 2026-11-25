package dev.solclient.mods;

import dev.solclient.Lang;
import dev.solclient.option.Option;
import dev.solclient.social.CloudSync;

/** Cloud Settings: save every Caos setting on your account and get them back on any computer. */
public class CloudSyncMod extends Mod {

	public static CloudSyncMod instance;
	private final Option.Bool auto = bool("Save To The Cloud When I Quit", true);

	public CloudSyncMod() {
		super("Cloud Settings", "cloud_sync", "Your Caos settings saved on your account, the same on every computer.", Category.UTILITY);
		instance = this;
		action("Save my settings in the cloud", "Upload", CloudSync::upload, () -> Lang.t(CloudSync.status));
		action("Get my settings from the cloud", "Download", CloudSync::download,
				() -> CloudSync.lastSync.isEmpty() ? "" : Lang.tr("Last: ", "Última: ") + CloudSync.lastSync);
	}

	/** On quit (blocking a moment is fine there). */
	public static void onQuit() {
		if (instance != null && instance.enabled && instance.auto.on()) {
			CloudSync.upload();
		}
	}
}
