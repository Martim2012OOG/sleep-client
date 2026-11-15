package dev.solclient.mods;

import dev.solclient.option.Option;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Totem Pops: next to a player's name tag, how many totems they have popped. Nothing shows at 0. The count is
 * forgotten when the player dies or stays out of your render distance for more than 5 minutes (setting).
 */
public class TotemPopsMod extends Mod {

	private static TotemPopsMod instance;
	private static final class Entry {
		int pops;
		long seen = System.currentTimeMillis();
	}

	private static final Map<UUID, Entry> POPS = new HashMap<>();

	private final Option.Bool self = bool("Show On Yourself", true);
	private final Option.Choice style = choice("Style", 0, "-2", "2 pops", "[2]");
	private final Option.Col colour = colour("Pop Colour", 0xFFFF5555);
	private final Option.Num forget = num("Forget After", 5F, 1F, 30F, 1F, "min");
	private Object lastWorld;

	public TotemPopsMod() {
		super("Totem Pops", "totem_pops",
				"Shows next to a player's name how many totems they popped. It disappears when they die or stay out of your render distance for more than 5 minutes.",
				Category.VISUAL);
		instance = this;
	}

	public static void onStatus(LivingEntity e, byte status) {
		TotemPopsMod m = instance;
		if (m == null || !m.enabled || !(e instanceof Player p)) {
			return;
		}
		if (status == 35) {
			Entry en = POPS.computeIfAbsent(p.getUUID(), k -> new Entry());
			en.pops++;
			en.seen = System.currentTimeMillis();
		} else if (status == 3) {
			POPS.remove(p.getUUID()); // died
		}
	}

	@Override
	public void tick() {
		if (mc.level == null) {
			POPS.clear();
			lastWorld = null;
			return;
		}
		if (mc.level != lastWorld) {
			POPS.clear(); // other world / server
			lastWorld = mc.level;
		}
		long now = System.currentTimeMillis(), max = Math.round(forget.get() * 60_000F);
		for (Iterator<Map.Entry<UUID, Entry>> it = POPS.entrySet().iterator(); it.hasNext(); ) {
			var me = it.next();
			Player p = mc.level.getPlayerByUUID(me.getKey());
			if (p != null) {
				if (p.isDeadOrDying() || p.getHealth() <= 0F) {
					it.remove();
				} else {
					me.getValue().seen = now; // in render distance
				}
			} else if (now - me.getValue().seen > max) {
				it.remove(); // away for too long
			}
		}
	}

	@Override
	protected void onDisable() {
		POPS.clear();
	}

	/** Adds the count at the end of the name tag text. */
	public static Component decorate(Player p, Component name) {
		TotemPopsMod m = instance;
		if (m == null || !m.enabled) {
			return name;
		}
		Entry en = POPS.get(p.getUUID());
		if (en == null || en.pops <= 0 || (!m.self.on() && p == m.mc.player)) {
			return name;
		}
		String t = switch (m.style.idx()) {
			case 1 -> en.pops + (en.pops == 1 ? " pop" : " pops");
			case 2 -> "[" + en.pops + "]";
			default -> "-" + en.pops;
		};
		MutableComponent out = name.copy();
		out.append(Component.literal(" " + t).setStyle(net.minecraft.network.chat.Style.EMPTY.withColor(m.colour.c() & 0xFFFFFF)));
		return out;
	}
}
