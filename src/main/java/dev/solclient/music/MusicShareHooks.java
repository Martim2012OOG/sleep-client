package dev.solclient.music;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

/** /sleep music listen <player> | accept <id> | decline <id> | leave */
public final class MusicShareHooks {

	private MusicShareHooks() {
	}

	public static void register() {
		try {
			ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
					ClientCommandManager.literal("sleep").then(ClientCommandManager.literal("music")
							.then(ClientCommandManager.literal("listen").then(ClientCommandManager.argument("player", StringArgumentType.word()).executes(c -> {
								String who = StringArgumentType.getString(c, "player");
								MinecraftClient mc = MinecraftClient.getInstance();
								String uuid = null;
								if (mc.world != null) {
									for (PlayerEntity p : mc.world.getPlayers()) {
										if (p.getName().getString().equalsIgnoreCase(who)) {
											uuid = p.getUuid().toString().replace("-", "");
										}
									}
								}
								if (uuid == null) {
									for (MusicShare.Remote r : MusicShare.remotes()) {
										if (r.nick.equalsIgnoreCase(who)) {
											uuid = r.uuid;
										}
									}
								}
								if (uuid != null) {
									MusicShare.request(uuid);
								}
								return 1;
							})))
							.then(ClientCommandManager.literal("accept").then(ClientCommandManager.argument("id", StringArgumentType.word()).executes(c -> {
								MusicShare.accept(StringArgumentType.getString(c, "id"));
								return 1;
							})))
							.then(ClientCommandManager.literal("decline").then(ClientCommandManager.argument("id", StringArgumentType.word()).executes(c -> {
								MusicShare.decline(StringArgumentType.getString(c, "id"));
								return 1;
							})))
							.then(ClientCommandManager.literal("remove").then(ClientCommandManager.argument("id", StringArgumentType.word()).executes(c -> {
								MusicShare.remove(StringArgumentType.getString(c, "id"));
								return 1;
							})))
							.then(ClientCommandManager.literal("stop").executes(c -> {
								MusicShare.stopHosting(); // everybody who listens to me is taken out
								return 1;
							}))
							.then(ClientCommandManager.literal("leave").executes(c -> {
								MusicShare.leave();
								return 1;
							})))));
		} catch (Throwable t) {
			System.err.println("[Sleep] Music Share commands unavailable: " + t);
		}
	}
}
