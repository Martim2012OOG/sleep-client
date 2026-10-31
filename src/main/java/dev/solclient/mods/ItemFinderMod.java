package dev.solclient.mods;

import dev.solclient.Gfx;
import dev.solclient.fx.Project;
import dev.solclient.gui.Theme;
import dev.solclient.option.Option;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.Box;

/**
 * Item Finder: dropped items that are further away than a few blocks get a marker on your screen (their picture and
 * the distance), so you always see where they are. Only the nearest ones are drawn, so it costs almost nothing.
 */
public class ItemFinderMod extends Mod {

	public static ItemFinderMod instance;

	private final Option.Num minDist = num("Show From", 5, 1, 30, 1, " blocks");
	private final Option.Num maxDist = num("Max Distance", 48, 10, 96, 2, " blocks");
	private final Option.Bool icon = bool("Item Picture", true);
	private final Option.Bool distance = bool("Distance", true);
	private final Option.Num maxMarkers = num("Max Markers", 12, 3, 30, 1, "");
	private final Option.Col colour = colour("Colour", 0xFFFFD24A);

	public ItemFinderMod() {
		super("Item Finder", "item_finder", "Marks dropped items that are far away, with their picture and distance, so you never lose them.", Category.UTILITY);
		instance = this;
	}

	public void draw(DrawContext c) {
		if (mc.player == null || mc.world == null || !Project.begin()) {
			return;
		}
		double max = maxDist.f(), min = minDist.f();
		Box box = mc.player.getBoundingBox().expand(max);
		List<ItemEntity> items = new ArrayList<>(mc.world.getEntitiesByClass(ItemEntity.class, box, e -> !e.isRemoved()));
		if (items.isEmpty()) {
			return;
		}
		items.sort((a, b) -> Double.compare(a.squaredDistanceTo(mc.player), b.squaredDistanceTo(mc.player)));
		int shown = 0, col = colour.c();
		for (ItemEntity e : items) {
			if (shown >= maxMarkers.i()) {
				break;
			}
			if (e.squaredDistanceTo(mc.player) < min * min) {
				continue;
			}
			if (!Project.to(e.getX(), e.getY() + 0.6, e.getZ())) {
				continue;
			}
			shown++;
			int x = Project.sx, y = Project.sy;
			// a small diamond with a line down to the item
			Gfx.circle(c, x, y, 3.5F, col);
			Gfx.circle(c, x, y, 1.8F, 0xFF101114);
			c.fill(x, y + 4, x + 1, y + 10, Gfx.withAlpha(col & 0xFFFFFF, 150));
			int ty = y - 12;
			if (icon.on()) {
				try {
					c.drawItem(e.getStack(), x - 8, y - 22);
				} catch (Throwable ignored) {
					// item not ready
				}
				ty = y - 6;
			}
			if (distance.on()) {
				String s = Math.round(Project.dist) + "m";
				Gfx.text(c, s, x - Gfx.tw(s) / 2, icon.on() ? y - 31 : ty, Theme.TEXT);
			}
		}
	}
}
