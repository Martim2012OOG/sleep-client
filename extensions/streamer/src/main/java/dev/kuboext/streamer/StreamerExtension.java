package dev.kuboext.streamer;

import dev.solclient.mods.StreamerModeMod;
import dev.solclient.mods.TikTokLiveMod;
import dev.solclient.ext.KuboApi;
import dev.solclient.ext.KuboExtension;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

/** Sleep extension "Streamer Tools": Streamer Mode (hide IP, coordinates, messages) and TikTok Live (chat, gifts, sounds). */
public class StreamerExtension implements KuboExtension {

	@Override
	public void register(KuboApi api) {
		api.addMod(new StreamerModeMod());
		api.addMod(new TikTokLiveMod());
		api.addMod(new dev.solclient.mods.TikTokViewersMod());
		// while you are live: full FPS even with OBS in front, optionally locked to a steady number
		api.streamFps(() -> TikTokLiveMod.instance == null ? -1 : TikTokLiveMod.instance.streamFps());
		ClientTickEvents.END_CLIENT_TICK.register(c -> StreamerModeMod.pollKey());
		api.hudFilter(StreamerModeMod::hidesHud);
		api.hideToasts(StreamerModeMod::hideToasts);
		api.onHudLast(c -> {
			if (TikTokLiveMod.instance != null && TikTokLiveMod.instance.enabled) TikTokLiveMod.instance.drawOverlay(c);
			if (StreamerModeMod.on()) StreamerModeMod.instance.drawTag(c);
		});
		api.command("Streamer Mode", "Hide IP, coordinates and messages", "streamer_mode", () -> {
			if (StreamerModeMod.instance != null) StreamerModeMod.instance.toggle();
		});
	}
}
