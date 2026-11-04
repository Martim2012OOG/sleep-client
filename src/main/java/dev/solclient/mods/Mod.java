package dev.solclient.mods;

import dev.solclient.option.Option;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;

public abstract class Mod {

	public enum Category {
		NONE(null), HUD("HUD"), UTILITY("Utility"), VISUAL("Visual");

		public final String label;

		Category(String label) {
			this.label = label;
		}
	}

	public final String id, name, description;
	public final Category category;
	public final List<Option<?>> options = new ArrayList<>();
	public boolean enabled;
	/** Average time this mod costs per frame / tick, in microseconds (for the "What is lagging" readout). */
	public float costUs;
	private long costAcc, costWindowNs;

	/** Adds measured work time; the average is refreshed about twice a second. */
	public void addCost(long ns) {
		costAcc += ns;
		long now = System.nanoTime();
		if (costWindowNs == 0) {
			costWindowNs = now;
		} else if (now - costWindowNs > 500_000_000L) {
			float framesPerWindow = Math.max(1F, mc.getCurrentFps() * ((now - costWindowNs) / 1e9F));
			costUs = costUs * 0.5F + (costAcc / 1000F / framesPerWindow) * 0.5F;
			costAcc = 0;
			costWindowNs = now;
		}
	}
	/** Shown under the heart filter in the mod menu. */
	public boolean favorite;
	/** Locked mods cannot be toggled (e.g. the Sol Client settings card). */
	public boolean locked;
	/** Not a mod in the list: its switch and options are at the end of Sleep Settings (Cinematic Intro...). */
	public boolean inSettings;
	/** The card this mod lives in (Info HUD, Screen Tweaks...), or null for a card of its own. */
	public Mod parent;
	protected final MinecraftClient mc = MinecraftClient.getInstance();

	protected Mod(String name, String id, String description, Category category) {
		this.name = name;
		this.id = id;
		this.description = description;
		this.category = category;
	}

	// ---- option factories (declaration order = order in the settings panel)
	protected Option.Header header(String title) {
		Option.Header h = new Option.Header(title);
		options.add(h);
		return h;
	}

	protected Option.Bool bool(String name, boolean def) {
		Option.Bool o = new Option.Bool(name, def);
		options.add(o);
		return o;
	}

	protected Option.Num num(String name, float def, float min, float max, float step, String suffix) {
		Option.Num o = new Option.Num(name, def, min, max, step, suffix, true);
		options.add(o);
		return o;
	}

	protected Option.Num num(String name, float def, float min, float max, float step, String suffix, boolean show) {
		Option.Num o = new Option.Num(name, def, min, max, step, suffix, show);
		options.add(o);
		return o;
	}

	protected Option.Col colour(String name, int argb) {
		Option.Col o = new Option.Col(name, argb);
		options.add(o);
		return o;
	}

	protected Option.Choice choice(String name, int def, String... names) {
		Option.Choice o = new Option.Choice(name, def, names);
		options.add(o);
		return o;
	}

	protected Option.Action action(String name, String button, Runnable run, java.util.function.Supplier<String> status) {
		Option.Action o = new Option.Action(name, button, run, status);
		options.add(o);
		return o;
	}

	protected Option.Custom custom(String name, Option.Custom.Drawer drawer) {
		Option.Custom o = new Option.Custom(name, drawer);
		options.add(o);
		return o;
	}

	protected Option.Grid grid(String name, int size, String def) {
		Option.Grid o = new Option.Grid(name, size, def);
		options.add(o);
		return o;
	}

	protected Option.Choice tabs(String name, int def, String... names) {
		Option.Choice o = choice(name, def, names);
		o.tabs = true;
		return o;
	}

	protected Option.Text text(String name, String def, int max, String hint) {
		Option.Text o = new Option.Text(name, def, max, hint);
		options.add(o);
		return o;
	}

	protected Option.Key key(String name, int code) {
		Option.Key o = new Option.Key(name, code);
		options.add(o);
		return o;
	}

	// ---- life cycle
	/** A file dragged onto the window while this mod's settings are open. Return true when it was used. */
	public boolean onFileDropped(java.nio.file.Path file) {
		return false;
	}

	public boolean enabledByDefault() {
		return false;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean e) {
		if (locked || e == enabled) {
			return;
		}
		enabled = e;
		if (e) {
			onEnable();
		} else {
			onDisable();
		}
	}

	public void toggle() {
		setEnabled(!enabled);
	}

	/** Called after the config was loaded. */
	public void init() {
		if (enabled) {
			onEnable();
		}
	}

	protected void onEnable() {
	}

	protected void onDisable() {
	}

	/** Every client tick, only while enabled. */
	public void tick() {
	}

	/** Every rendered frame, only while enabled. */
	public void frame() {
	}
}
