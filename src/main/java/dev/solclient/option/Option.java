package dev.solclient.option;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;

/** Base class for every configurable setting of a mod. */
public abstract class Option<T> {

	public final String key;
	public final String name;
	public final T def;
	protected T value;
	/** If set, the option is only shown in the settings while this returns true (e.g. per crosshair mode). */
	public java.util.function.BooleanSupplier visibleIf;
	/** One short sentence shown as a tooltip when the mouse is over the option ("what does this do"). */
	public String tip;

	@SuppressWarnings("unchecked")
	public <O extends Option<T>> O tip(String text) {
		this.tip = text;
		return (O) this;
	}

	public boolean visible() {
		return visibleIf == null || visibleIf.getAsBoolean();
	}

	@SuppressWarnings("unchecked")
	public <O extends Option<T>> O showIf(java.util.function.BooleanSupplier s) {
		this.visibleIf = s;
		return (O) this;
	}

	protected Option(String name, T def) {
		this.name = name;
		this.key = name.toLowerCase().replaceAll("[^a-z0-9]+", "_");
		this.def = def;
		this.value = def;
	}

	public T get() {
		return value;
	}

	public void set(T value) {
		this.value = value;
	}

	/** Puts the option back to its default value. */
	public void reset() {
		value = def;
	}

	public abstract JsonElement save();

	public abstract void load(JsonElement element);

	// ---------------------------------------------------------------- Boolean
	public static class Bool extends Option<Boolean> {
		public Bool(String name, boolean def) {
			super(name, def);
		}

		public boolean on() {
			return value;
		}

		@Override
		public JsonElement save() {
			return new JsonPrimitive(value);
		}

		@Override
		public void load(JsonElement e) {
			value = e.getAsBoolean();
		}
	}

	// ----------------------------------------------------------------- Slider
	public static class Num extends Option<Float> {
		public final float min, max, step;
		public final String suffix;
		public final boolean showValue;

		public Num(String name, float def, float min, float max, float step, String suffix, boolean showValue) {
			super(name, def);
			this.min = min;
			this.max = max;
			this.step = step;
			this.suffix = suffix;
			this.showValue = showValue;
		}

		@Override
		public void set(Float v) {
			float c = Math.max(min, Math.min(max, v));
			if (step > 0) {
				c = Math.round((c - min) / step) * step + min;
			}
			value = Math.max(min, Math.min(max, c));
		}

		public float f() {
			return value;
		}

		public int i() {
			return Math.round(value);
		}

		public String display() {
			String s;
			if (step >= 1 || value == Math.floor(value)) {
				s = Integer.toString(Math.round(value));
			} else {
				s = String.format(java.util.Locale.ROOT, step < 0.1F ? "%.2f" : "%.1f", value);
			}
			return s + suffix;
		}

		@Override
		public JsonElement save() {
			return new JsonPrimitive(value);
		}

		@Override
		public void load(JsonElement e) {
			set(e.getAsFloat());
		}
	}

	// ----------------------------------------------------------------- Colour
	/** ARGB colour. */
	public static class Col extends Option<Integer> {
		public Col(String name, int def) {
			super(name, def);
		}

		public int c() {
			return value;
		}

		@Override
		public JsonElement save() {
			return new JsonPrimitive(value);
		}

		@Override
		public void load(JsonElement e) {
			value = e.getAsInt();
		}
	}

	// ------------------------------------------------------------------- Enum
	public static class Choice extends Option<Integer> {
		public final String[] names;
		/** Drawn as a row of segmented buttons instead of "< value >". */
		public boolean tabs;

		public Choice(String name, int def, String... names) {
			super(name, def);
			this.names = names;
		}

		public int idx() {
			return value;
		}

		public String label() {
			return names[Math.floorMod(value, names.length)];
		}

		/** Runs when the player picks a value in the menu (not when the config loads). */
		public java.util.function.IntConsumer onPick;

		public Choice onPick(java.util.function.IntConsumer c) {
			onPick = c;
			return this;
		}

		public void cycle(int dir) {
			set(Math.floorMod(value + dir, names.length));
		}

		@Override
		public void set(Integer v) {
			super.set(v);
			if (onPick != null) {
				try {
					onPick.accept(v);
				} catch (Throwable t) {
					System.err.println("[Sleep] " + name + ": " + t);
				}
			}
		}

		@Override
		public JsonElement save() {
			return new JsonPrimitive(value);
		}

		@Override
		public void load(JsonElement e) {
			int v = e.getAsInt();
			if (v >= 0 && v < names.length) {
				value = v;
			}
		}
	}

	// -------------------------------------------------------------------- Key
	/** GLFW key code, -1 means unbound. */
	public static class Key extends Option<Integer> {
		public Key(String name, int def) {
			super(name, def);
		}

		public int code() {
			return value;
		}

		@Override
		public JsonElement save() {
			return new JsonPrimitive(value);
		}

		@Override
		public void load(JsonElement e) {
			value = e.getAsInt();
		}
	}

	// ----------------------------------------------------------------- Header
	/** Section title in the settings panel; holds no value. */
	public static class Header extends Option<String> {
		public Header(String name) {
			super(name, name);
		}

		@Override
		public JsonElement save() {
			return JsonNull.INSTANCE;
		}

		@Override
		public void load(JsonElement e) {
		}
	}

	// ----------------------------------------------------------------- Action
	/** A button in the settings panel (runs something, holds no value). */
	public static class Action extends Option<String> {
		public final String button;
		public final Runnable run;
		public final java.util.function.Supplier<String> status;

		public Action(String name, String button, Runnable run, java.util.function.Supplier<String> status) {
			super(name, name);
			this.button = button;
			this.run = run;
			this.status = status;
		}

		@Override
		public JsonElement save() {
			return JsonNull.INSTANCE;
		}

		@Override
		public void load(JsonElement e) {
		}
	}

	// ------------------------------------------------------------------- Text
	/** A single-line text field in the settings panel (a username, a link, an API key...). */
	public static class Text extends Option<String> {
		public final int max;
		public final String hint;
		/** Shown as ***** when not being edited and NOT written to shared profiles (API keys). */
		public boolean secret;

		public Text(String name, String def, int max, String hint) {
			super(name, def == null ? "" : def);
			this.max = max;
			this.hint = hint == null ? "" : hint;
		}

		@Override
		public void set(String v) {
			value = v == null ? "" : v.length() > max ? v.substring(0, max) : v;
		}

		@Override
		public JsonElement save() {
			return new JsonPrimitive(value);
		}

		@Override
		public void load(JsonElement e) {
			set(e.getAsString());
		}
	}

	// ------------------------------------------------------------------- Grid
	/** A square pixel grid (custom crosshair editor). Saved as a string of 0/1. */
	public static class Grid extends Option<String> {
		public final int size;

		public Grid(String name, int size, String def) {
			super(name, def);
			this.size = size;
			if (value == null || value.length() != size * size) {
				value = "0".repeat(size * size);
			}
		}

		public boolean get(int x, int y) {
			return x >= 0 && y >= 0 && x < size && y < size && value.charAt(y * size + x) == '1';
		}

		public void put(int x, int y, boolean on) {
			if (x < 0 || y < 0 || x >= size || y >= size || get(x, y) == on) {
				return;
			}
			char[] ch = value.toCharArray();
			ch[y * size + x] = on ? '1' : '0';
			value = new String(ch);
		}

		public void clear() {
			value = "0".repeat(size * size);
		}

		@Override
		public void reset() {
			value = def != null && def.length() == size * size ? def : "0".repeat(size * size);
		}

		@Override
		public JsonElement save() {
			return new JsonPrimitive(value);
		}

		@Override
		public void load(JsonElement e) {
			String v = e.getAsString();
			if (v.length() == size * size && v.matches("[01]+")) {
				value = v;
			}
		}
	}
}
