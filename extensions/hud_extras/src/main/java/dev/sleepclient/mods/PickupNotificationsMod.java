package dev.sleepclient.mods;

import dev.sleepclient.Gfx;
import dev.sleepclient.option.Option;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** Small "+3 Diamond" popups when items enter your inventory. */
public class PickupNotificationsMod extends HudMod {

	private final Option.Num duration = num("Duration", 3, 1, 8, 0.5F, "s");
	private final Option.Num maxRows = num("Max Rows", 5, 1, 10, 1, "");
	private final Option.Bool losses = bool("Show Lost Items", false);
	private final Option.Bool creative = bool("Show In Creative", false);
	private final Option.Col bg = colour("Background Colour", 0x90000000);

	private static final class Row {
		final ItemStack icon;
		int amount;
		long at;

		Row(ItemStack icon, int amount) {
			this.icon = icon;
			this.amount = amount;
			this.at = System.currentTimeMillis();
		}
	}

	private final List<Row> rows = new ArrayList<>();
	private Map<Item, Integer> last;
	private Object lastPlayer;
	private int settle;

	public PickupNotificationsMod() {
		super("Pickup Notifications", "pickup_notifications", "Shows the items you pick up, like +3 Diamond.", 0.80F, 0.55F);
	}

	@Override
	public int width() {
		return 120;
	}

	@Override
	public int height() {
		return maxRows.i() * 18;
	}

	@Override
	public void tick() {
		if (mc.player == null) {
			last = null;
			return;
		}
		if (mc.player != lastPlayer) { // joined a world / respawned
			lastPlayer = mc.player;
			last = null;
			settle = 20;
		}
		var inv = mc.player.getInventory();
		Map<Item, Integer> now = new HashMap<>();
		Map<Item, ItemStack> sample = new HashMap<>();
		for (int i = 0; i < inv.size(); i++) {
			ItemStack st = inv.getStack(i);
			if (!st.isEmpty()) {
				now.merge(st.getItem(), st.getCount(), Integer::sum);
				sample.putIfAbsent(st.getItem(), st);
			}
		}
		ItemStack cursor = mc.player.currentScreenHandler != null ? mc.player.currentScreenHandler.getCursorStack() : ItemStack.EMPTY;
		if (!cursor.isEmpty()) {
			now.merge(cursor.getItem(), cursor.getCount(), Integer::sum);
		}
		if (settle > 0) {
			settle--;
			last = now;
			return;
		}
		if (last != null && (creative.on() || !mc.player.isCreative())) {
			for (var e : now.entrySet()) {
				int d = e.getValue() - last.getOrDefault(e.getKey(), 0);
				if (d > 0) {
					push(sample.getOrDefault(e.getKey(), new ItemStack(e.getKey())), d);
				}
			}
			if (losses.on()) {
				for (var e : last.entrySet()) {
					int d = now.getOrDefault(e.getKey(), 0) - e.getValue();
					if (d < 0) {
						push(new ItemStack(e.getKey()), d);
					}
				}
			}
		}
		last = now;
		long life = (long) (duration.f() * 1000);
		long t = System.currentTimeMillis();
		rows.removeIf(r -> t - r.at > life);
	}

	private void push(ItemStack icon, int amount) {
		for (Row r : rows) {
			if (r.icon.getItem() == icon.getItem() && Integer.signum(r.amount) == Integer.signum(amount)) {
				r.amount += amount;
				r.at = System.currentTimeMillis();
				return;
			}
		}
		ItemStack copy = icon.copy();
		copy.setCount(1);
		rows.add(new Row(copy, amount));
		while (rows.size() > maxRows.i()) {
			rows.remove(0);
		}
	}

	@Override
	protected void renderHud(DrawContext c, boolean edit) {
		List<Row> show = rows;
		if (edit && rows.isEmpty()) {
			show = List.of(new Row(new ItemStack(Items.DIAMOND), 3), new Row(new ItemStack(Items.OAK_LOG), 16), new Row(new ItemStack(Items.ENDER_PEARL), 1));
		}
		long life = (long) (duration.f() * 1000), now = System.currentTimeMillis();
		int y = 0;
		for (int i = show.size() - 1; i >= 0; i--) {
			Row r = show.get(i);
			long left = life - (now - r.at);
			float a = edit ? 1F : Math.max(0F, Math.min(1F, left / 400F));
			if (a <= 0.03F) {
				continue;
			}
			String name = r.icon.getName().getString();
			String txt = (r.amount > 0 ? "+" : "") + r.amount + " " + name;
			int w = Math.min(width(), mc.textRenderer.getWidth(txt) + 26);
			int x = width() - w;
			c.fill(x, y, width(), y + 16, Gfx.withAlpha(bg.c(), Math.round(((bg.c() >>> 24) & 0xFF) * a)));
			c.fill(width() - 1, y, width(), y + 16, Gfx.withAlpha(r.amount > 0 ? 0xFF3DDC84 : 0xFFE5484D, Math.round(255 * a)));
			c.drawItem(r.icon, x + 2, y);
			c.drawText(mc.textRenderer, txt, x + 21, y + 4, Gfx.withAlpha(0xFFFFFF, Math.max(8, Math.round(255 * a))), true);
			y += 18;
		}
	}
}
