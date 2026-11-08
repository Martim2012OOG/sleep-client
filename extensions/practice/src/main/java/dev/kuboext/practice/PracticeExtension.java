package dev.kuboext.practice;

import dev.solclient.addons.Addons;
import dev.solclient.addons.bridge.BridgeHud;
import dev.solclient.addons.bridge.BridgePracticeAddon;
import dev.solclient.addons.bridge.BridgeSession;
import dev.solclient.addons.pvp.FightStats;
import dev.solclient.addons.pvp.FightStatsHud;
import dev.solclient.addons.pvp.PracticeSession;
import dev.solclient.addons.pvp.PracticeWorld;
import dev.solclient.addons.pvp.PvpPracticeAddon;
import dev.solclient.ext.KuboApi;
import dev.solclient.ext.KuboExtension;
import dev.solclient.gui.PracticeLoadingScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * Sleep extension "Practice": the PvP arena with bots, the Kit Editor and Bridge Practice (they used to be built in).
 * Shows up in the menu's Add-ons tab. Fabric events can't be unregistered, so they are hooked once and do nothing
 * while the extension is uninstalled.
 */
public class PracticeExtension implements KuboExtension {

	private static volatile boolean on;
	private static boolean hooked;

	@Override
	public void register(KuboApi api) {
		on = true;
		Addons.register(new PvpPracticeAddon());
		Addons.register(new BridgePracticeAddon());
		Addons.register(new dev.solclient.addons.ranked.RankedAddon());
		if (hooked) {
			return; // reinstalled in the same session: the hooks below are still there
		}
		hooked = true;
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S().register(dev.solclient.addons.ranked.RankedReady.ID, dev.solclient.addons.ranked.RankedReady.CODEC);
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(dev.solclient.addons.ranked.RankedReady.ID,
				(payload, ctx) -> dev.solclient.addons.ranked.RankedServer.markReady(ctx.player()));
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE.register((e, src, base, taken, blocked) -> {
			if (on) {
				dev.solclient.addons.ranked.RankedServer.afterDamage(e, src, taken);
			}
		});
		// Ranked PvP: the match on the host's server, eliminations instead of deaths, no griefing the arenas
		ServerTickEvents.END_SERVER_TICK.register(s -> {
			if (on) {
				dev.solclient.addons.ranked.RankedServer.tick(s);
			}
		});
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DEATH.register((e, src, amt) -> !on || dev.solclient.addons.ranked.RankedServer.allowDeath(e, src, amt));
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register((e, src, amt) -> !on || dev.solclient.addons.ranked.RankedServer.allowDamage(e, src, amt));
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((h, srv) -> {
			if (on) {
				dev.solclient.addons.ranked.RankedServer.onLeave(h.player);
			}
		});
		net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) -> !on || player.isCreative()
				|| world.getServer() == null || dev.solclient.addons.ranked.RankedServer.canBreak(world.getServer()));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (on) {
				dev.solclient.addons.ranked.Ranked.tick(mc);
				dev.solclient.addons.ranked.RankedFx.tick(mc);
			}
		});
		// no commands during a match (the host can't /kick, /ban, /op, /gamemode...); chat commands still work
		net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.ALLOW_COMMAND.register(cmd -> {
			if (!on || !dev.solclient.addons.ranked.Ranked.inMatch()) {
				return true;
			}
			String c0 = cmd.split(" ")[0].toLowerCase(java.util.Locale.ROOT);
			if (java.util.Set.of("msg", "tell", "w", "r", "me", "teammsg", "tm", "ranked").contains(c0)) {
				return true;
			}
			dev.solclient.social.Social.say(dev.solclient.Lang.tr("Commands are off during a match.", "Os comandos estão desligados durante uma partida."), true);
			return false;
		});
		// /ranked opens the menu
		net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((d, reg) -> d.register(
				net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("ranked").executes(ctx -> {
					var mc = net.minecraft.client.MinecraftClient.getInstance();
					mc.send(() -> mc.setScreen(new dev.solclient.gui.RankedScreen(mc.currentScreen)));
					return 1;
				})
						// tests, only on your screen: the duel intro against a dummy, and the end screens
						.then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("intro").executes(ctx -> {
							var mc = net.minecraft.client.MinecraftClient.getInstance();
							mc.send(() -> dev.solclient.addons.ranked.RankedFx.testIntro(mc, "sword"));
							return 1;
						}))
						.then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("victory").executes(ctx -> {
							var mc = net.minecraft.client.MinecraftClient.getInstance();
							mc.send(() -> dev.solclient.addons.ranked.RankedFx.testEnd(mc, true));
							return 1;
						}))
						.then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("defeat").executes(ctx -> {
							var mc = net.minecraft.client.MinecraftClient.getInstance();
							mc.send(() -> dev.solclient.addons.ranked.RankedFx.testEnd(mc, false));
							return 1;
						}))));
		ServerTickEvents.END_SERVER_TICK.register(s -> {
			if (on) {
				PracticeSession.tick(s);
				BridgeSession.tick(s);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			PracticeSession.stop();
			BridgeSession.stop();
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (on) {
				PracticeWorld.tick(mc);
				BridgeHud.tick(mc);
			}
		});
		api.onHudLast(c -> {
			if (on) {
				dev.solclient.addons.ranked.RankedFx.render(c);
			}
		});
		api.onHud(c -> {
			if (on) {
				BridgeHud.render(c);
				FightStatsHud.render(c);
			}
		});
		api.onLeftClick(() -> {
			if (on) {
				FightStats.click();
			}
		});
		api.screenFilter(s -> on ? PracticeLoadingScreen.intercept(s) : s);
	}

	@Override
	public void unload() {
		on = false;
		PracticeSession.stop();
		BridgeSession.stop();
		Addons.unregister("pvp_practice");
		Addons.unregister("bridge_practice");
		Addons.unregister("ranked_pvp");
	}
}
