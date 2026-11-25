package dev.solclient.mods;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.solclient.option.Option;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/** Timers for game events, works with any server that supports Badlion timers. */
public class TimersMod extends HudMod {

	public static TimersMod instance;
	private static final int TIMER_HEIGHT = 19;

	private final Option.Choice alignment = choice("Alignment", 1, "Top", "Middle", "Bottom");
	private final Option.Bool icon = bool("Icon", true);
	private final Option.Bool shadow = bool("Text Shadow", true);
	private final Option.Col nameColour = colour("Name Colour", 0xFFFFFFFF);
	private final Option.Col timeColour = colour("Time Colour", 0xFF7F7F7F);

	private final Map<Long, Timer> timers = new HashMap<>();
	private Object lastWorld;

	public TimersMod() {
		super("Timers", "timers", "Timers for game events.", 0.01F, 0.62F);
		instance = this;
	}

	private static class Timer {
		String name;
		ItemStack item;
		long time;
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}

	@Override
	public int width() {
		return 22 + 55 + 4 + 28;
	}

	@Override
	public int height() {
		return TIMER_HEIGHT * 3;
	}

	@Override
	public int dy() {
		switch (alignment.idx()) {
			case 1: return -(TIMER_HEIGHT * 3) / 2;
			case 2: return -TIMER_HEIGHT * 3;
			default: return 0;
		}
	}

	@Override
	public void tick() {
		if (mc.world != lastWorld) {
			lastWorld = mc.world;
			timers.clear();
		}
		for (Timer t : timers.values()) {
			if (t.time > 0) {
				t.time--;
			}
		}
	}

	/** Handles a raw "TYPE|{json}" message from the server. */
	public static void handle(byte[] data) {
		if (instance == null || !instance.enabled) {
			return;
		}
		try {
			String message = new String(data, StandardCharsets.UTF_8);
			int bar = message.indexOf('|');
			String type = message.substring(0, bar);
			JsonObject json = JsonParser.parseString(message.substring(bar + 1)).getAsJsonObject();
			long id = json.has("id") ? json.get("id").getAsLong() : -1;
			switch (type) {
				case "REMOVE_ALL_TIMERS":
					instance.timers.clear();
					break;
				case "REMOVE_TIMER":
					instance.timers.remove(id);
					break;
				case "SYNC_TIMERS": {
					Timer t = instance.timers.get(id);
					if (t != null) {
						t.time = json.get("time").getAsLong();
					}
					break;
				}
				case "ADD_TIMER": {
					JsonObject item = json.get("item").getAsJsonObject();
					Timer t = new Timer();
					t.name = json.get("name").getAsString();
					t.time = json.get("time").getAsLong();
					String material = item.get("type").getAsString().toLowerCase();
					int amount = item.has("amount") ? item.get("amount").getAsInt() : 1;
					t.item = new ItemStack(Registries.ITEM.get(Identifier.ofVanilla(material)), amount);
					instance.timers.put(id, t);
					break;
				}
				default:
			}
		} catch (RuntimeException e) {
			System.err.println("[Caos] Bad timers message: " + e);
		}
	}

	private static String format(long ticks) {
		long secs = (long) Math.ceil(ticks / 20.0);
		return String.format("%02d:%02d", secs / 60, secs % 60);
	}

	@Override
	protected void renderHud(DrawContext c, boolean edit) {
		List<Timer> list = new ArrayList<>();
		if (edit) {
			String[] names = {"Oven", "Toaster", "Dishwasher"};
			ItemStack[] items = {new ItemStack(Items.FURNACE), new ItemStack(Items.BREAD),
					new ItemStack(Items.WATER_BUCKET)};
			for (int i = 0; i < 3; i++) {
				Timer t = new Timer();
				t.name = names[i];
				t.item = items[i];
				t.time = 20 * (30 + 15 * i);
				list.add(t);
			}
		} else {
			list.addAll(timers.values());
		}
		int y = 0;
		switch (alignment.idx()) {
			case 1: y -= (TIMER_HEIGHT * list.size()) / 2; break;
			case 2: y -= TIMER_HEIGHT * list.size(); break;
			default:
		}
		for (Timer t : list) {
			if (icon.on()) {
				c.drawItem(t.item, 0, y);
			}
			int nx = 22;
			c.drawText(mc.textRenderer, t.name, nx, y + 5, nameColour.c(), shadow.on());
			int tx = nx + mc.textRenderer.getWidth(t.name) + (shadow.on() ? 3 : 4);
			c.drawText(mc.textRenderer, format(t.time), tx, y + 5, timeColour.c(), shadow.on());
			y += TIMER_HEIGHT;
		}
	}
}
