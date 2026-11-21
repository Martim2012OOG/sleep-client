package dev.sleepclient.addons.pvp;

import dev.sleepclient.gui.LeaveWarningScreen;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.world.ClientWorld;
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
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null) {
			launch(from, name, st);
			return;
		}
		mc.setScreen(new LeaveWarningScreen(from, label, () -> {
			leave(mc);
			launch(new TitleScreen(), name, st);
		}));
	}

	private static void leave(MinecraftClient mc) {
		boolean sp = mc.isInSingleplayer();
		if (mc.world != null) {
			mc.world.disconnect(ClientWorld.QUITTING_MULTIPLAYER_TEXT);
		}
		if (sp) {
			mc.disconnectWithSavingScreen();
		} else {
			mc.disconnectWithProgressScreen();
		}
	}

	private static void launch(Screen parent, String name, Consumer<MinecraftServer> st) {
		MinecraftClient mc = MinecraftClient.getInstance();
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
			mc.setScreen(parent);
		};
		if (exists(name)) {
			mc.createIntegratedServerLoader().start(name, cancel);
		} else {
			CreateWorldScreen.show(mc, cancel);
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
		MinecraftClient mc = MinecraftClient.getInstance();
		launching = false;
		loading = false;
		ready = false;
		starter = null;
		PracticeSession.stop();
		dev.sleepclient.addons.bridge.BridgeSession.stop();
		if (mc.world != null) {
			leave(mc);
		} else {
			mc.setScreen(new TitleScreen());
		}
	}

	/** Client tick: fills in the world name on the Create World screen and starts the session once we are in the world. */
	public static void tick(MinecraftClient mc) {
		if (loading && !launching && mc.world == null) {
			loading = false; // we were in the world and left it: nothing to wait for any more
			ready = false;
		}
		if (loading) {
			// fallback in case the setScreen mixin did not apply: swap vanilla loading screens on the next tick
			Screen cur = mc.currentScreen;
			Screen r = dev.sleepclient.gui.PracticeLoadingScreen.intercept(cur);
			if (r != cur) {
				mc.setScreen(r);
			}
		}
		if (!launching) {
			return;
		}
		if (!prefilled && mc.currentScreen instanceof CreateWorldScreen s) {
			prefilled = true;
			try {
				s.getWorldCreator().setWorldName(worldName);
			} catch (Throwable ignored) {
				// the player can type the name by hand; the session starts in whatever world they create
			}
		}
		var server = mc.getServer();
		var st = starter;
		if (server != null && st != null && mc.player != null && mc.world != null) {
			launching = false;
			starter = null;
			server.execute(() -> st.accept(server));
		}
	}
}
