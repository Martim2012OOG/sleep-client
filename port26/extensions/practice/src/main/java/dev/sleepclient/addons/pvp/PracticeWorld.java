package dev.sleepclient.addons.pvp;

import dev.sleepclient.gui.LeaveWarningScreen;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.MinecraftServer;

/**
 * Launches the private worlds used by the add-ons (a normal singleplayer world: nobody else, no server).
 * If the player is connected somewhere it first shows the "you will be removed from the current server" warning,
 * then leaves, then opens the world. First time it opens the vanilla Create World screen with the name filled in
 * (just press Create); after that it loads the saved world directly.
 *
 * Every Minecraft API that is specific to world loading lives here, so if a game update changes it,
 * this is the only file to touch.
 */
public final class PracticeWorld {

	public static final String PVP = "Sleep PvP Practice";
	public static final String BRIDGE = "Sleep Bridge Practice";

	/** True from the moment the world is being opened until the add-on session has begun (or was cancelled). */
	public static volatile boolean launching;
	private static boolean prefilled;
	private static String worldName = PVP;
	private static Consumer<MinecraftServer> starter;

	/** True while the custom loading screen is up: from opening the world until the add-on has the player in place. */
	private static volatile boolean loading;
	private static volatile boolean ready;
	private static volatile boolean loadingPvp;
	private static volatile String status = "Loading the world...";

	private PracticeWorld() {
	}

	public static boolean exists(String name) {
		Path level = FabricLoader.getInstance().getGameDir().resolve("saves").resolve(name).resolve("level.dat");
		return Files.exists(level);
	}

	/**
	 * Entry point for the add-on screens.
	 * @param from  the screen that asked (we come back to it on cancel)
	 * @param label add-on name shown in the warning
	 * @param st    called on the world's server thread once the player is inside
	 */
	public static void go(Screen from, String name, String label, Consumer<MinecraftServer> st) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			launch(from, name, st);
			return;
		}
		dev.sleepclient.compat.Mc.setScreen(mc, new LeaveWarningScreen(from, label, () -> {
			leave(mc);
			launch(new TitleScreen(), name, st);
		}));
	}

	private static void leave(Minecraft mc) {
		boolean sp = mc.isLocalServer();
		if (mc.level != null) {
			mc.level.disconnect(ClientLevel.DEFAULT_QUIT_MESSAGE);
		}
		if (sp) {
			mc.disconnectWithSavingScreen();
		} else {
			mc.disconnectWithProgressScreen();
		}
	}

	private static void launch(Screen parent, String name, Consumer<MinecraftServer> st) {
		Minecraft mc = Minecraft.getInstance();
		launching = true;
		loading = true;
		ready = false;
		loadingPvp = !BRIDGE.equals(name);
		status = "Loading the world...";
		prefilled = false;
		worldName = name;
		starter = st;
		Runnable cancel = () -> {
			launching = false;
			loading = false;
			starter = null;
			dev.sleepclient.compat.Mc.setScreen(mc, parent);
		};
		if (exists(name)) {
			mc.createWorldOpenFlows().openWorld(name, cancel);
		} else {
			CreateWorldScreen.openFresh(mc, cancel);
		}
	}

	// ------------------------------------------------------------------ loading screen state
	public static boolean loading() {
		return loading;
	}

	public static boolean ready() {
		return ready;
	}

	public static boolean loadingIsPvp() {
		return loadingPvp;
	}

	public static String loadingStatus() {
		return status;
	}

	/** Called by the sessions (any thread) to change the text under the loading GIF. */
	public static void setStatus(String text) {
		status = text;
	}

	/** Called by the sessions (any thread) when the arena / course is built and the player is in place. */
	public static void markReady() {
		status = "Ready!";
		ready = true;
	}

	/** Called by the loading screen once it has closed (or timed out). */
	public static void finishLoading() {
		loading = false;
		ready = false;
	}

	/** Esc on the loading screen: stop everything and go back to the menu. */
	public static void abort() {
		Minecraft mc = Minecraft.getInstance();
		launching = false;
		loading = false;
		ready = false;
		starter = null;
		PracticeSession.stop();
		dev.sleepclient.addons.bridge.BridgeSession.stop();
		if (mc.level != null) {
			leave(mc);
		} else {
			dev.sleepclient.compat.Mc.setScreen(mc, new TitleScreen());
		}
	}

	/** Client tick: fills in the world name on the Create World screen and starts the session once we are in the world. */
	public static void tick(Minecraft mc) {
		if (loading && !launching && mc.level == null) {
			loading = false; // we were in the world and left it: nothing to wait for any more
			ready = false;
		}
		if (loading) {
			// fallback in case the setScreen mixin did not apply: swap vanilla loading screens on the next tick
			Screen cur = dev.sleepclient.compat.Mc.screen(mc);
			Screen r = dev.sleepclient.gui.PracticeLoadingScreen.intercept(cur);
			if (r != cur) {
				dev.sleepclient.compat.Mc.setScreen(mc, r);
			}
		}
		if (!launching) {
			return;
		}
		if (!prefilled && dev.sleepclient.compat.Mc.screen(mc) instanceof CreateWorldScreen s) {
			prefilled = true;
			try {
				s.getUiState().setName(worldName);
			} catch (Throwable ignored) {
				// the player can type the name by hand; the session starts in whatever world they create
			}
		}
		var server = mc.getSingleplayerServer();
		var st = starter;
		if (server != null && st != null && mc.player != null && mc.level != null) {
			launching = false;
			starter = null;
			server.execute(() -> st.accept(server));
		}
	}
}
