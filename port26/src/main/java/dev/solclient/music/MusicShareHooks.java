package dev.solclient.music;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/** /sleep music listen <player> | accept <id> | decline <id> | leave */
public final class MusicShareHooks {

	private MusicShareHooks() {
	}

	public static void register() {
		try {
			ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
					ClientCommands.literal("sleep").then(ClientCommands.literal("music")
							.then(ClientCommands.literal("listen").then(ClientCommands.argument("player", StringArgumentType.word()).executes(c -> {
								String who = StringArgumentType.getString(c, "player");
								Minecraft mc = Minecraft.getInstance();
								String uuid = null;
								if (mc.level != null) {
									for (Player p : mc.level.players()) {
										if (p.getName().getString().equalsIgnoreCase(who)) {
											uuid = p.getUUID().toString().replace("-", "");
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
							.then(ClientCommands.literal("accept").then(ClientCommands.argument("id", StringArgumentType.word()).executes(c -> {
								MusicShare.accept(StringArgumentType.getString(c, "id"));
								return 1;
							})))
							.then(ClientCommands.literal("decline").then(ClientCommands.argument("id", StringArgumentType.word()).executes(c -> {
								MusicShare.decline(StringArgumentType.getString(c, "id"));
								return 1;
							})))
							.then(ClientCommands.literal("remove").then(ClientCommands.argument("id", StringArgumentType.word()).executes(c -> {
								MusicShare.remove(StringArgumentType.getString(c, "id"));
								return 1;
							})))
							.then(ClientCommands.literal("stop").executes(c -> {
								MusicShare.stopHosting(); // everybody who listens to me is taken out
								return 1;
							}))
							.then(ClientCommands.literal("leave").executes(c -> {
								MusicShare.leave();
								return 1;
							})))));
		} catch (Throwable t) {
			System.err.println("[Sleep] Music Share commands unavailable: " + t);
		}
	}
}
