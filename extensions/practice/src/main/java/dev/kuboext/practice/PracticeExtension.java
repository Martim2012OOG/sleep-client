package dev.kuboext.practice;

import dev.sleepclient.addons.Addons;
import dev.sleepclient.addons.bridge.BridgeHud;
import dev.sleepclient.addons.bridge.BridgePracticeAddon;
import dev.sleepclient.addons.bridge.BridgeSession;
import dev.sleepclient.addons.pvp.FightStats;
import dev.sleepclient.addons.pvp.FightStatsHud;
import dev.sleepclient.addons.pvp.PracticeSession;
import dev.sleepclient.addons.pvp.PracticeWorld;
import dev.sleepclient.addons.pvp.PvpPracticeAddon;
import dev.sleepclient.ext.KuboApi;
import dev.sleepclient.ext.KuboExtension;
import dev.sleepclient.gui.PracticeLoadingScreen;
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
		Addons.register(new dev.sleepclient.addons.ranked.RankedAddon());
		if (hooked) {
			return; // reinstalled in the same session: the hooks below are still there
		}
		hooked = true;
		// /leave: only exists while you are in a Ranked / Duel match
		net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((d, reg) -> d.register(
				net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("leave").requires(src -> on && dev.sleepclient.addons.ranked.Ranked.inMatch())
						.executes(ctx -> {
							dev.sleepclient.addons.ranked.Ranked.leaveMatch(net.minecraft.client.MinecraftClient.getInstance());
							return 1;
						})));
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S().register(dev.sleepclient.addons.ranked.RankedReady.ID, dev.sleepclient.addons.ranked.RankedReady.CODEC);
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(dev.sleepclient.addons.ranked.RankedReady.ID,
				(payload, ctx) -> dev.sleepclient.addons.ranked.RankedServer.markReady(ctx.player()));
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE.register((e, src, base, taken, blocked) -> {
			if (on) {
				dev.sleepclient.addons.ranked.RankedServer.afterDamage(e, src, taken);
			}
		});
		// Ranked PvP: the match on the host's server, eliminations instead of deaths, no griefing the arenas
		ServerTickEvents.END_SERVER_TICK.register(s -> {
			if (on) {
				dev.sleepclient.addons.ranked.RankedServer.tick(s);
			}
		});
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DEATH.register((e, src, amt) -> !on || dev.sleepclient.addons.ranked.RankedServer.allowDeath(e, src, amt));
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register((e, src, amt) -> !on || dev.sleepclient.addons.ranked.RankedServer.allowDamage(e, src, amt));
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((h, srv) -> {
			if (on) {
				dev.sleepclient.addons.ranked.RankedServer.onLeave(h.player);
			}
		});
		net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) -> !on
				|| world.getServer() == null || dev.sleepclient.addons.ranked.RankedServer.canBreak(world.getServer(), pos));
		// what the players place is the only thing that can be broken or blown up in an arena
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (on && !world.isClient()) {
				dev.sleepclient.addons.ranked.RankedServer.notePlace(world, hit.getBlockPos(), hit.getSide());
			}
			return net.minecraft.util.ActionResult.PASS;
		});
		dev.sleepclient.fx.ExplosionGuard.allow = (w, p) -> !on || dev.sleepclient.addons.ranked.RankedServer.explosionMayBreak(w.getServer(), p);
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (on) {
				dev.sleepclient.addons.ranked.Ranked.tick(mc);
				dev.sleepclient.addons.ranked.RankedFx.tick(mc);
			}
		});
		// no commands during a match (the host can't /kick, /ban, /op, /gamemode...); chat commands still work
		net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.ALLOW_COMMAND.register(cmd -> {
			if (!on || !dev.sleepclient.addons.ranked.Ranked.inMatch()) {
				return true;
			}
			String c0 = cmd.split(" ")[0].toLowerCase(java.util.Locale.ROOT);
			if (java.util.Set.of("msg", "tell", "w", "r", "me", "teammsg", "tm", "ranked").contains(c0)) {
				return true;
			}
			dev.sleepclient.social.Social.say(dev.sleepclient.Lang.tr("Commands are off during a match.", "Os comandos estão desligados durante uma partida."), true);
			return false;
		});
		// /ranked opens the menu
		net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((d, reg) -> d.register(
				net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("ranked").executes(ctx -> {
					var mc = net.minecraft.client.MinecraftClient.getInstance();
					mc.send(() -> mc.setScreen(new dev.sleepclient.gui.RankedScreen(mc.currentScreen)));
					return 1;
				})
						// tests, only on your screen: the duel intro against a dummy, and the end screens
						.then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("intro").executes(ctx -> {
							var mc = net.minecraft.client.MinecraftClient.getInstance();
							mc.send(() -> dev.sleepclient.addons.ranked.RankedFx.testIntro(mc, "sword"));
							return 1;
						}))
						.then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("victory").executes(ctx -> {
							var mc = net.minecraft.client.MinecraftClient.getInstance();
							mc.send(() -> dev.sleepclient.addons.ranked.RankedFx.testEnd(mc, true));
							return 1;
						}))
						.then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("defeat").executes(ctx -> {
							var mc = net.minecraft.client.MinecraftClient.getInstance();
							mc.send(() -> dev.sleepclient.addons.ranked.RankedFx.testEnd(mc, false));
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
				dev.sleepclient.addons.ranked.RankedFx.render(c);
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
