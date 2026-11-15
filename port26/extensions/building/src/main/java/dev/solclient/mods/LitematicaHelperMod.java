package dev.solclient.mods;

import dev.solclient.Keys;
import dev.solclient.option.Option;
import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Litematica Helper: one-key shortcuts and one-click buttons for the things people do all the time in Litematica
 * (open the menu, load a schematic, show / hide it, move the layer, easy place), without the M + C / M + ... chords.
 *
 * Litematica is NOT a dependency: everything is done by reflection and fully guarded. If Litematica is not installed
 * (or a future version renamed something) the helper just says so in the action bar and the game keeps working.
 */
public class LitematicaHelperMod extends Mod {

	public static LitematicaHelperMod instance;

	private static final String GUI = "fi.dy.masa.litematica.gui.";
	private static final String CFG = "fi.dy.masa.litematica.config.Configs$";

	// ---- options (declaration order = order in the settings panel)
	private final Option.Bool messages = bool("Show Messages", true);

	private final Option.Header hMenus = header("Menus (buttons)");
	private final Option.Action btnMenu = action("Main menu", "Open", () -> openGui("GuiMainMenu", "Litematica menu"), this::status);
	private final Option.Action btnLoad = action("Load schematic", "Open", () -> openGui("GuiSchematicLoad", "Load schematic"), () -> "");
	private final Option.Action btnLoaded = action("Loaded schematics", "Open", () -> openGui("GuiSchematicLoaded", "Loaded schematics"), () -> "");
	private final Option.Action btnPlacements = action("Placements", "Open", () -> openGui("GuiSchematicPlacementsList", "Placements"), () -> "");
	private final Option.Action btnSelections = action("Area selections", "Open", () -> openGui("GuiAreaSelectionManager", "Area selections"), () -> "");
	private final Option.Action btnSettings = action("Litematica settings", "Open", () -> openGui("GuiConfigs", "Litematica settings"), () -> "");

	private final Option.Header hQuick = header("Quick actions (buttons)");
	private final Option.Action btnRender = action("Show / hide schematic", "Toggle", this::toggleRendering, () -> "");
	private final Option.Action btnEasy = action("Easy Place", "Toggle", this::toggleEasyPlace, () -> "");
	private final Option.Action btnLayerNext = action("Layer up", "+1", () -> moveLayer(1), () -> "");
	private final Option.Action btnLayerPrev = action("Layer down", "-1", () -> moveLayer(-1), () -> "");

	private final Option.Header hKeys = header("One-key shortcuts");
	private final Option.Key keyMenu = key("Open Litematica Menu", 74); // J
	private final Option.Key keyRender = key("Show / Hide Schematic", -1);
	private final Option.Key keyEasy = key("Toggle Easy Place", -1);
	private final Option.Key keyNext = key("Layer Up", 93); // ]
	private final Option.Key keyPrev = key("Layer Down", 91); // [
	private final Option.Key keyLoad = key("Load Schematic", -1);

	private final boolean[] down = new boolean[6];

	public LitematicaHelperMod() {
		super("Litematica Helper", "litematica_helper",
			"Easy shortcuts and buttons for Litematica (needs Litematica installed).", Category.UTILITY);
		instance = this;
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}

	// ---------------------------------------------------------------- status
	public static boolean installed() {
		return FabricLoader.getInstance().isModLoaded("litematica");
	}

	private String status() {
		if (!installed()) {
			return "Litematica not installed";
		}
		return FabricLoader.getInstance().isModLoaded("malilib") ? "Litematica found" : "Litematica found (malilib missing?)";
	}

	private boolean ready() {
		if (installed()) {
			return true;
		}
		say("Litematica is not installed");
		return false;
	}

	private void say(String s) {
		if (messages.on() && mc.player != null) {
			mc.player.sendOverlayMessage(Component.literal("[Litematica Helper] " + s));
		}
	}

	// ---------------------------------------------------------------- keys
	private boolean pressed(int i, Option.Key k) {
		boolean d = dev.solclient.compat.Mc.screen(mc) == null && k.code() > 0 && Keys.down(k.code());
		boolean edge = d && !down[i];
		down[i] = d;
		return edge;
	}

	@Override
	public void tick() {
		if (mc.player == null || !installed()) {
			return;
		}
		if (pressed(0, keyMenu)) openGui("GuiMainMenu", "Litematica menu");
		if (pressed(1, keyRender)) toggleRendering();
		if (pressed(2, keyEasy)) toggleEasyPlace();
		if (pressed(3, keyNext)) moveLayer(1);
		if (pressed(4, keyPrev)) moveLayer(-1);
		if (pressed(5, keyLoad)) openGui("GuiSchematicLoad", "Load schematic");
	}

	// ---------------------------------------------------------------- actions
	private void openGui(String className, String label) {
		if (!ready()) {
			return;
		}
		try {
			Object o = Class.forName(GUI + className).getDeclaredConstructor().newInstance();
			if (!(o instanceof Screen screen)) {
				say("Could not open " + label);
				return;
			}
			// malilib's own opener keeps the "back" button working; fall back to a plain setScreen
			try {
				Class.forName("fi.dy.masa.malilib.gui.GuiBase").getMethod("openGui", Screen.class).invoke(null, screen);
			} catch (Throwable t) {
				dev.solclient.compat.Mc.setScreen(mc, screen);
			}
		} catch (Throwable t) {
			say(label + " is not available in this Litematica version");
		}
	}

	private void toggleRendering() {
		if (ready()) {
			toggleConfig("Visuals", "ENABLE_RENDERING", "Litematica rendering");
		}
	}

	private void toggleEasyPlace() {
		if (ready()) {
			toggleConfig("Generic", "EASY_PLACE_MODE", "Easy Place");
		}
	}

	/** Flips a boolean Litematica config (the same ones its own hotkeys flip). */
	private void toggleConfig(String group, String field, String label) {
		try {
			Object cfg = Class.forName(CFG + group).getField(field).get(null);
			Method get = cfg.getClass().getMethod("getBooleanValue");
			Method set = cfg.getClass().getMethod("setBooleanValue", boolean.class);
			boolean now = (Boolean) get.invoke(cfg);
			set.invoke(cfg, !now);
			say(label + (now ? ": OFF" : ": ON"));
		} catch (Throwable t) {
			say(label + " is not available in this Litematica version");
		}
	}

	private void moveLayer(int amount) {
		if (!ready()) {
			return;
		}
		try {
			Object range = Class.forName("fi.dy.masa.litematica.data.DataManager").getMethod("getRenderLayerRange").invoke(null);
			range.getClass().getMethod("moveLayer", int.class).invoke(range, amount);
			say(amount > 0 ? "Layer up" : "Layer down");
		} catch (Throwable t) {
			say("Layer control is not available in this Litematica version");
		}
	}
}
