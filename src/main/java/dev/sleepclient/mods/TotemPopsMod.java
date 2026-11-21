package dev.sleepclient.mods;

import dev.sleepclient.option.Option;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

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
		if (m == null || !m.enabled || !(e instanceof PlayerEntity p)) {
			return;
		}
		if (status == 35) {
			Entry en = POPS.computeIfAbsent(p.getUuid(), k -> new Entry());
			en.pops++;
			en.seen = System.currentTimeMillis();
		} else if (status == 3) {
			POPS.remove(p.getUuid()); // died
		}
	}

	@Override
	public void tick() {
		if (mc.world == null) {
			POPS.clear();
			lastWorld = null;
			return;
		}
		if (mc.world != lastWorld) {
			POPS.clear(); // other world / server
			lastWorld = mc.world;
		}
		long now = System.currentTimeMillis(), max = Math.round(forget.get() * 60_000F);
		for (Iterator<Map.Entry<UUID, Entry>> it = POPS.entrySet().iterator(); it.hasNext(); ) {
			var me = it.next();
			PlayerEntity p = mc.world.getPlayerByUuid(me.getKey());
			if (p != null) {
				if (p.isDead() || p.getHealth() <= 0F) {
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
	public static Text decorate(PlayerEntity p, Text name) {
		TotemPopsMod m = instance;
		if (m == null || !m.enabled) {
			return name;
		}
		Entry en = POPS.get(p.getUuid());
		if (en == null || en.pops <= 0 || (!m.self.on() && p == m.mc.player)) {
			return name;
		}
		String t = switch (m.style.idx()) {
			case 1 -> en.pops + (en.pops == 1 ? " pop" : " pops");
			case 2 -> "[" + en.pops + "]";
			default -> "-" + en.pops;
		};
		MutableText out = name.copy();
		out.append(Text.literal(" " + t).setStyle(net.minecraft.text.Style.EMPTY.withColor(m.colour.c() & 0xFFFFFF)));
		return out;
	}
}
