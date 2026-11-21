package dev.kuboext.streamer;

import dev.sleepclient.mods.StreamerModeMod;
import dev.sleepclient.mods.TikTokLiveMod;
import dev.sleepclient.ext.KuboApi;
import dev.sleepclient.ext.KuboExtension;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

/** Sleep extension "Streamer Tools": Streamer Mode (hide IP, coordinates, messages) and TikTok Live (chat, gifts, sounds). */
public class StreamerExtension implements KuboExtension {

	private static boolean parkourHooked;

	@Override
	public void register(KuboApi api) {
		api.addMod(new StreamerModeMod());
		api.addMod(new TikTokLiveMod());
		api.addMod(new dev.sleepclient.mods.TikTokViewersMod());
		api.addMod(new dev.sleepclient.mods.ParkourLevelMod());
		api.addMod(new dev.sleepclient.mods.ParkourRaceMod());
		// while you are live: full FPS even with OBS in front, optionally locked to a steady number
		api.streamFps(() -> TikTokLiveMod.instance == null ? -1 : TikTokLiveMod.instance.streamFps());
		ClientTickEvents.END_CLIENT_TICK.register(c -> StreamerModeMod.pollKey());
		// Parkour Live: the Parkour Paradise 3 map played by TikTok gifts (Add-ons tab)
		dev.sleepclient.addons.Addons.register(new dev.sleepclient.parkour.ParkourAddon());
		api.addIcon("addon_parkour");
		if (!parkourHooked) {
			parkourHooked = true;
			// the jail cage never stays in the map, even if you close the world while locked up
			net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(dev.sleepclient.parkour.ParkourLive::release);
			// the parkour map can't be broken (buttons, doors and levers still work: they are not breaking); creative mode still can, to fix the map
			net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register(
					(world, player, pos, state, be) -> player.isCreative() || !dev.sleepclient.parkour.ParkourLive.protects(world));
			// /pkrace start | stop : parkour race with your friends in your hosted map
			net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((d, reg) -> d.register(
					net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("pkrace")
							.then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("start").executes(ctx -> {
								dev.sleepclient.parkour.ParkourLive.startRace();
								return 1;
							}))
							.then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("stop").executes(ctx -> {
								dev.sleepclient.parkour.ParkourLive.stopRace();
								return 1;
							}))));
			// /pktest up1 | down1 | up10 | down10 | reset | save | punch | jail : try every gift without being live
			net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((d, reg) -> d.register(
					net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("pktest")
							.then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
									.argument("action", com.mojang.brigadier.arguments.StringArgumentType.word())
									.suggests((ctx, b) -> {
										for (var a : dev.sleepclient.parkour.ParkourLive.Act.values()) {
											b.suggest(a.name().toLowerCase(java.util.Locale.ROOT));
										}
										return b.buildFuture();
									})
									.executes(ctx -> {
										String s = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "action").toUpperCase(java.util.Locale.ROOT);
										for (var a : dev.sleepclient.parkour.ParkourLive.Act.values()) {
											if (a.name().equals(s)) {
												dev.sleepclient.parkour.ParkourLive.test(a);
												return 1;
											}
										}
										ctx.getSource().sendError(net.minecraft.text.Text.literal("Use: up1, down1, up10, down10, reset, save, punch, jail"));
										return 0;
									}))));
		}
		ClientTickEvents.END_CLIENT_TICK.register(c -> {
			try {
				dev.sleepclient.parkour.ParkourLive.tick(c);
			} catch (Throwable t) {
				// never break the game
			}
		});
		api.hudFilter(StreamerModeMod::hidesHud);
		api.hideToasts(StreamerModeMod::hideToasts);
		api.onHudLast(c -> {
			dev.sleepclient.parkour.ParkourLive.render(c);
			if (TikTokLiveMod.instance != null && TikTokLiveMod.instance.enabled) TikTokLiveMod.instance.drawOverlay(c);
			if (StreamerModeMod.on()) StreamerModeMod.instance.drawTag(c);
		});
		api.command("Streamer Mode", "Hide IP, coordinates and messages", "streamer_mode", () -> {
			if (StreamerModeMod.instance != null) StreamerModeMod.instance.toggle();
		});
	}

	@Override
	public void unload() {
		dev.sleepclient.addons.Addons.unregister("parkour_live");
	}
}
