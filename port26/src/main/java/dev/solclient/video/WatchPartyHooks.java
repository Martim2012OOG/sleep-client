package dev.solclient.video;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

/** Registers the /kubo video commands and the world rendering of the Watch Party screens. */
public final class WatchPartyHooks {

	private WatchPartyHooks() {
	}

	public static void register() {
		try {
			LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> {
			long kuboPerfT0 = System.nanoTime();
			try {
				try {
					VideoScreens.render(ctx.poseStack(), ctx.submitNodeCollector(), ctx.levelState().cameraRenderState.pos);
				} catch (RuntimeException ignored) {
				}
						} finally {
				dev.solclient.Perf.add("Watch party", System.nanoTime() - kuboPerfT0);
			}
		});
		} catch (Throwable t) {
			System.err.println("[Sleep] Watch Party rendering unavailable: " + t);
		}
		try {
			ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
					ClientCommands.literal("sleep").then(ClientCommands.literal("video")
							.then(ClientCommands.literal("pause").executes(c -> {
								VideoScreens.pause(true);
								return 1;
							}))
							.then(ClientCommands.literal("play").executes(c -> {
								VideoScreens.pause(false);
								return 1;
							}).then(ClientCommands.argument("link", StringArgumentType.greedyString()).executes(c -> {
								VideoScreens.play(StringArgumentType.getString(c, "link"));
								return 1;
							})))
							.then(ClientCommands.literal("live").then(ClientCommands.argument("link", StringArgumentType.greedyString()).executes(c -> {
								VideoScreens.playLive(StringArgumentType.getString(c, "link"));
								return 1;
							})))
							.then(ClientCommands.literal("remove").executes(c -> {
								VideoScreens.remove();
								return 1;
							}))
							.then(ClientCommands.literal("cancel").executes(c -> {
								VideoScreens.cancelPlacing();
								return 1;
							}))
							.then(ClientCommands.literal("panel").executes(c -> {
								net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
								mc.schedule(() -> dev.solclient.compat.Mc.setScreen(mc, new WatchPartyScreen()));
								return 1;
							}))
							.then(ClientCommands.literal("move").executes(c -> {
								VideoScreens.move();
								return 1;
							}))
							.then(ClientCommands.literal("seek").then(ClientCommands.argument("time", StringArgumentType.word()).executes(c -> {
								VideoScreens.seek(StringArgumentType.getString(c, "time"));
								return 1;
							})))
							.then(ClientCommands.literal("size").then(ClientCommands.argument("width", IntegerArgumentType.integer(1, 32)).executes(c -> {
								VideoScreens.size(IntegerArgumentType.getInteger(c, "width"));
								return 1;
							})))
							)));
		} catch (Throwable t) {
			System.err.println("[Sleep] Watch Party commands unavailable: " + t);
		}
	}
}
