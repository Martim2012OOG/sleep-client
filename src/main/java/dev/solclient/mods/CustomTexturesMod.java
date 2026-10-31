package dev.solclient.mods;

import dev.solclient.option.Option;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * Custom Textures: custom looks and counters for items. For now only the golden apple: the icon, how many you
 * have and a cooldown that counts down after you eat one (the time is set by you because servers differ).
 */
public class CustomTexturesMod extends HudMod {

	private final Option.Header hGapple = header("Golden Apple");
	private final Option.Bool gapple = bool("Golden Apple Cooldown", true);
	private final Option.Num seconds = num("Cooldown Time", 10F, 1F, 60F, 0.5F, "s");
	private final Option.Bool showCount = bool("Show Count", true);
	private final Option.Bool showBar = bool("Show Bar", true);
	private final Option.Bool background = bool("Background", true);
	private final Option.Col backgroundColour = colour("Background Colour", 0x64000000);
	private final Option.Col textColour = colour("Text Colour", 0xFFFFFFFF);
	private final Option.Col barColour = colour("Bar Colour", 0xFFFFC83C);

	private final Option.Header hTools = header("Tiny Tools");
	private final Option.Bool tiny = bool("Tiny Tools", true);
	private final Option.Num toolSize = num("Tool Size", 0.6F, 0.2F, 1F, 0.05F, "x");

	private static CustomTexturesMod instance;
	private long cdStart, cdEnd;
	private int lastCount, pending;
	private boolean usingPrev;

	public CustomTexturesMod() {
		super("Custom Textures", "custom_textures",
				"Custom looks and counters for items. For now: the golden apple with its cooldown and how many you have.", 0.01F, 0.66F);
		instance = this;
	}

	/** Size of the tool in your hand (1 = normal): swords, pickaxes, axes, shovels and hoes get smaller. */
	public static float toolScale() {
		CustomTexturesMod m = instance;
		if (m == null || !m.enabled || !m.tiny.on()) {
			return 1F;
		}
		var p = m.mc.player;
		if (p == null) {
			return 1F;
		}
		var s = p.getMainHandStack();
		boolean tool = s.isIn(net.minecraft.registry.tag.ItemTags.SWORDS) || s.isIn(net.minecraft.registry.tag.ItemTags.PICKAXES)
				|| s.isIn(net.minecraft.registry.tag.ItemTags.AXES) || s.isIn(net.minecraft.registry.tag.ItemTags.SHOVELS)
				|| s.isIn(net.minecraft.registry.tag.ItemTags.HOES);
		return tool ? m.toolSize.get() : 1F;
	}

	private static boolean isGap(ItemStack s) {
		return s.isOf(Items.GOLDEN_APPLE) || s.isOf(Items.ENCHANTED_GOLDEN_APPLE);
	}

	private int count() {
		if (mc.player == null) {
			return 0;
		}
		var inv = mc.player.getInventory();
		int n = 0;
		for (int i = 0; i < inv.size(); i++) {
			ItemStack s = inv.getStack(i);
			if (isGap(s)) {
				n += s.getCount();
			}
		}
		return n;
	}

	@Override
	public void tick() {
		if (mc.player == null) {
			cdEnd = 0;
			usingPrev = false;
			return;
		}
		int n = count();
		boolean using = mc.player.isUsingItem() && isGap(mc.player.getActiveItem());
		if (usingPrev && !using) {
			pending = 20; // the count drops a moment later on a server
		}
		usingPrev = using;
		if (pending > 0) {
			pending--;
			if (n < lastCount) {
				cdStart = System.currentTimeMillis();
				cdEnd = cdStart + Math.round(seconds.get() * 1000F);
				pending = 0;
			}
		}
		lastCount = n;
	}

	/** Stock colour: 6+ green, 3-5 yellow, 1-2 orange, 0 red. */
	private static int tone(int n) {
		return n >= 6 ? 0xFF55E070 : n >= 3 ? 0xFFFFD34D : n >= 1 ? 0xFFFF9B3D : 0xFFFF4D4D;
	}

	/** 0..1 of the cooldown that is left (our timer, or the server / game cooldown when that is longer). */
	private float left(boolean edit) {
		if (edit) {
			return 0.6F;
		}
		float mine = 0F;
		long now = System.currentTimeMillis();
		if (cdEnd > now && cdEnd > cdStart) {
			mine = (cdEnd - now) / (float) (cdEnd - cdStart);
		}
		float vanilla = 0F;
		if (mc.player != null) {
			vanilla = mc.player.getItemCooldownManager().getCooldownProgress(new ItemStack(Items.GOLDEN_APPLE), 0F);
		}
		return Math.max(mine, vanilla);
	}

	@Override
	public boolean visible() {
		return gapple.on();
	}

	@Override
	public int width() {
		return 84;
	}

	@Override
	public int height() {
		return showBar.on() ? 26 : 22;
	}

	@Override
	protected void renderHud(DrawContext c, boolean edit) {
		if (!gapple.on() && !edit) {
			return;
		}
		int w = width(), h = height();
		if (background.on()) {
			c.fill(0, 0, w, h, backgroundColour.c());
		}
		float left = left(edit);
		int n = edit ? 5 : count();
		ItemStack icon = new ItemStack(Items.GOLDEN_APPLE);
		int tone = tone(n);
		// glow + frame around the apple in the stock colour: green = many, yellow = some, red = almost none
		c.fill(1, 1, 21, 21, (tone & 0x00FFFFFF) | 0x38000000);
		c.fill(1, 1, 21, 2, tone);
		c.fill(1, 20, 21, 21, tone);
		c.fill(1, 2, 2, 20, tone);
		c.fill(20, 2, 21, 20, tone);
		c.drawItem(icon, 3, 3);
		if (left > 0F) {
			// like the game's own cooldown: a light layer that shrinks from the top to the bottom
			int ch = Math.round(16 * left);
			c.fill(3, 3 + 16 - ch, 3 + 16, 3 + 16, 0x77FFFFFF);
		}
		if (showCount.on()) {
			c.drawText(mc.textRenderer, "x" + n, 24, 4, tone, true);
		}
		long ms = edit ? Math.round(seconds.get() * 600F) : Math.max(0L, cdEnd - System.currentTimeMillis());
		String t = left > 0F ? (ms >= 10_000 ? String.valueOf(ms / 1000) : String.format(java.util.Locale.ROOT, "%.1f", ms / 1000F)) + "s" : "";
		if (!t.isEmpty()) {
			c.drawText(mc.textRenderer, t, w - 4 - mc.textRenderer.getWidth(t), 4, 0xFFFFD866, true);
		}
		if (showBar.on()) {
			int bx = 24, bw = w - bx - 5, by = h - 7;
			c.fill(bx, by, bx + bw, by + 3, 0x66000000);
			if (left > 0F) {
				c.fill(bx, by, bx + Math.round(bw * left), by + 3, barColour.c());
			}
		}
	}
}
