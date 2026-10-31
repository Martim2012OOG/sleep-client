package dev.solclient.video;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;

/** Registers the /kubo video commands and the world rendering of the Watch Party screens. */
public final class WatchPartyHooks {

	private WatchPartyHooks() {
	}

	public static void register() {
		try {
			WorldRenderEvents.BEFORE_ENTITIES.register(ctx -> {
				try {
					VideoScreens.render(ctx.matrices(), ctx.commandQueue(), ctx.worldState().cameraRenderState.pos);
				} catch (RuntimeException ignored) {
				}
			});
		} catch (Throwable t) {
			System.err.println("[Sleep] Watch Party rendering unavailable: " + t);
		}
		try {
			ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
					ClientCommandManager.literal("kubo").then(ClientCommandManager.literal("video")
							.then(ClientCommandManager.literal("pause").executes(c -> {
								VideoScreens.pause(true);
								return 1;
							}))
							.then(ClientCommandManager.literal("play").executes(c -> {
								VideoScreens.pause(false);
								return 1;
							}))
							.then(ClientCommandManager.literal("remove").executes(c -> {
								VideoScreens.remove();
								return 1;
							}))
							.then(ClientCommandManager.literal("cancel").executes(c -> {
								VideoScreens.cancelPlacing();
								return 1;
							}))
							.then(ClientCommandManager.literal("panel").executes(c -> {
								net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
								mc.send(() -> mc.setScreen(new WatchPartyScreen()));
								return 1;
							}))
							.then(ClientCommandManager.literal("move").executes(c -> {
								VideoScreens.move();
								return 1;
							}))
							.then(ClientCommandManager.literal("seek").then(ClientCommandManager.argument("time", StringArgumentType.word()).executes(c -> {
								VideoScreens.seek(StringArgumentType.getString(c, "time"));
								return 1;
							})))
							.then(ClientCommandManager.literal("size").then(ClientCommandManager.argument("width", IntegerArgumentType.integer(1, 32)).executes(c -> {
								VideoScreens.size(IntegerArgumentType.getInteger(c, "width"));
								return 1;
							})))
							.then(ClientCommandManager.argument("link", StringArgumentType.greedyString()).executes(c -> {
								VideoScreens.play(StringArgumentType.getString(c, "link"));
								return 1;
							})))));
		} catch (Throwable t) {
			System.err.println("[Sleep] Watch Party commands unavailable: " + t);
		}
	}
}
