package dev.solclient.mods;

import dev.solclient.option.Option;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;

/**
 * EXPERIMENTAL "3D GUI": the inventory / chests float in front of you in perspective (the near edge bigger, the far edge smaller, the
 * world clear behind), like a hologram. The real perspective is applied to the game's GUI pass (see dev.solclient.fx.Holo), the mouse is
 * mapped back onto the tilted panel. Off by default.
 */
public class HologramScreensMod extends Mod {

	public static HologramScreensMod instance;

	final Option.Num angle = num("Angle", 24, 8, 45, 1, "°");
	final Option.Num perspective = num("Perspective", 100, 40, 220, 10, "%");
	final Option.Choice side = choice("Turned Towards", 0, "Left", "Right");
	final Option.Bool floating = bool("Floating Animation", true);
	final Option.Bool glow = bool("Glow Frame", true);
	final Option.Bool clearWorld = bool("Keep The World Clear", true);
	final Option.Bool inventoryOnly = bool("Inventory And Containers Only", true);

	public HologramScreensMod() {
		super("3D GUI (Experimental)", "hologram_screens", "Experimental: the inventory and chests float in front of you in real 3D perspective, like a hologram.", Category.VISUAL);
		instance = this;
		clearWorld.tip("No dark veil and no blur behind the screen, so you see the world around it.");
		inventoryOnly.tip("Only the inventory, chests, crafting table and the other container screens (never the creative inventory or the Sleep menu).");
	}

	public static boolean on() {
		return instance != null && instance.enabled;
	}

	public boolean appliesTo(Screen s) {
		return s instanceof HandledScreen<?> && !(s instanceof CreativeInventoryScreen) && (!inventoryOnly.on() || s instanceof HandledScreen<?>);
	}

	public float angleDeg() {
		return angle.f() * (side.idx() == 0 ? 1F : -1F);
	}

	public float distance() {
		// the lower the distance, the stronger the perspective
		return 520F / Math.max(0.4F, perspective.f() / 100F);
	}

	public boolean floating() {
		return floating.on();
	}

	public boolean glow() {
		return glow.on();
	}

	public boolean clearWorld() {
		return clearWorld.on();
	}

	/** The inventory screen itself (for the player model it needs a little more room). */
	public static boolean isInventory(Screen s) {
		return s instanceof InventoryScreen;
	}
}
