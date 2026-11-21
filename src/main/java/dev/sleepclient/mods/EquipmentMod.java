package dev.sleepclient.mods;

import dev.sleepclient.Gfx;
import dev.sleepclient.option.Option;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * Armor HUD (the one and only: it replaces the old "Equipment" and "Durability Overlay"). Your armour and the items in
 * your hands, each with its durability as a number / percentage and a coloured bar (green to red), a flash when
 * something is about to break, vertical or horizontal, with an optional rounded background. It can also put the
 * percentage on damaged hotbar items.
 */
public class EquipmentMod extends HudMod {

	public static EquipmentMod instance;

	private final Option.Header h1 = header("What to show");
	private final Option.Bool armour = bool("Armour", true);
	private final Option.Bool hand = bool("Main Hand", true);
	private final Option.Bool offHand = bool("Off Hand", true);
	private final Option.Bool onlyDamaged = bool("Only Damaged Items", false);
	private final Option.Header h2 = header("Look");
	private final Option.Choice layout = choice("Layout", 0, "Vertical", "Horizontal");
	private final Option.Choice mode = choice("Text", 2, "Off", "Remaining", "Percentage", "Fraction");
	private final Option.Bool bar = bool("Durability Bar", true);
	private final Option.Bool colourText = bool("Colour Text By Durability", true);
	private final Option.Col textColour = colour("Text Colour", 0xFFFFFFFF);
	private final Option.Bool background = bool("Background", true);
	private final Option.Col bgColour = colour("Background Colour", 0x80101114);
	private final Option.Bool shadow = bool("Shadow", true);
	private final Option.Header h3 = header("Warning");
	private final Option.Bool flash = bool("Flash When Low", true);
	private final Option.Num lowAt = num("Low At", 15, 5, 50, 5, "%");
	private final Option.Header h4 = header("Hotbar");
	private final Option.Bool hotbar = bool("Percentage On Hotbar Items", false);

	private ItemStack[] samples;

	public EquipmentMod() {
		super("Armor HUD", "armour", "Your armour and hands with durability numbers, colour bars and a low warning.", 0.01F, 0.5F);
		instance = this;
	}

	private java.util.List<ItemStack> shown(boolean edit) {
		java.util.List<ItemStack> out = new java.util.ArrayList<>(6);
		if (edit || mc.player == null) {
			if (samples == null) {
				samples = new ItemStack[] {new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE),
						new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.DIAMOND_BOOTS), new ItemStack(Items.NETHERITE_SWORD),
						new ItemStack(Items.SHIELD)};
				samples[0].setDamage(20);
				samples[1].setDamage(180);
				samples[2].setDamage(150);
				samples[3].setDamage(400);
				samples[4].setDamage(1900);
				samples[5].setDamage(100);
			}
			if (armour.on()) {
				for (int i = 0; i < 4; i++) {
					out.add(samples[i]);
				}
			}
			if (hand.on()) {
				out.add(samples[4]);
			}
			if (offHand.on()) {
				out.add(samples[5]);
			}
			return out;
		}
		if (armour.on()) {
			for (EquipmentSlot es : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
				add(out, mc.player.getEquippedStack(es));
			}
		}
		if (hand.on()) {
			add(out, mc.player.getMainHandStack());
		}
		if (offHand.on()) {
			add(out, mc.player.getOffHandStack());
		}
		return out;
	}

	private void add(java.util.List<ItemStack> out, ItemStack s) {
		if (s.isEmpty() || onlyDamaged.on() && s.getDamage() <= 0) {
			return;
		}
		out.add(s);
	}

	private int textW() {
		return switch (mode.idx()) {
			case 0 -> 0;
			case 3 -> 54;
			default -> 28;
		};
	}

	@Override
	public int width() {
		int n = Math.max(1, shown(false).size());
		int pad = background.on() ? 4 : 0;
		return layout.idx() == 0 ? pad * 2 + 18 + (bar.on() ? 30 : 0) + (mode.idx() == 0 ? 0 : textW() + 4) : pad * 2 + n * 26 - 4;
	}

	@Override
	public int height() {
		int n = Math.max(1, shown(false).size());
		int pad = background.on() ? 4 : 0;
		return layout.idx() == 0 ? pad * 2 + n * 18 - 2 : pad * 2 + 16 + (bar.on() ? 4 : 0) + (mode.idx() == 0 ? 0 : 9);
	}

	/** Vanilla-like colour for the durability left: green, yellow, red. */
	static int colour(float f) {
		f = Math.max(0F, Math.min(1F, f));
		int r, g;
		if (f > 0.5F) {
			r = Math.round(255 * (1F - f) * 2F);
			g = 255;
		} else {
			r = 255;
			g = Math.round(255 * f * 2F);
		}
		return 0xFF000000 | (r << 16) | (g << 8) | 0x40;
	}

	private String label(ItemStack s) {
		int max = s.getMaxDamage(), left = max - s.getDamage();
		return switch (mode.idx()) {
			case 1 -> Integer.toString(left);
			case 2 -> Math.round(100F * left / max) + "%";
			case 3 -> left + "/" + max;
			default -> "";
		};
	}

	@Override
	protected void renderHud(DrawContext c, boolean edit) {
		java.util.List<ItemStack> items = shown(edit);
		if (items.isEmpty()) {
			return;
		}
		if (background.on()) {
			Gfx.rrectFree(c, 0, 0, width(), height(), 5F, bgColour.c());
		}
		int pad = background.on() ? 4 : 0;
		boolean vertical = layout.idx() == 0;
		for (int i = 0; i < items.size(); i++) {
			ItemStack s = items.get(i);
			int x = pad + (vertical ? 0 : i * 26), y = pad + (vertical ? i * 18 : 0);
			boolean dmg = s.getMaxDamage() > 0;
			float f = dmg ? (s.getMaxDamage() - s.getDamage()) / (float) s.getMaxDamage() : 1F;
			int col = colour(f);
			if (dmg && flash.on() && f * 100F <= lowAt.f()) {
				float pulse = 0.5F + 0.5F * (float) Math.sin(System.currentTimeMillis() / 160.0);
				Gfx.rrectFree(c, x - 1, y - 1, 18, 18, 3F, Gfx.withAlpha(0xFFFF3030, Math.round(40 + 90 * pulse)));
			}
			c.drawItem(s, x, y);
			if (!dmg) {
				continue;
			}
			int tc = colourText.on() ? col : textColour.c();
			String t = label(s);
			if (vertical) {
				int bx = x + 20;
				if (bar.on()) {
					Gfx.rrectFree(c, bx, y + 7, 26, 3, 1.5F, 0xC0202020);
					Gfx.rrectFree(c, bx, y + 7, Math.max(1F, 26 * f), 3, 1.5F, col);
				}
				if (!t.isEmpty()) {
					c.drawText(mc.textRenderer, t, bar.on() ? bx + 30 : bx, y + 4, tc, shadow.on());
				}
			} else {
				if (bar.on()) {
					Gfx.rrectFree(c, x, y + 17, 16, 2.5F, 1.25F, 0xC0202020);
					Gfx.rrectFree(c, x, y + 17, Math.max(1F, 16 * f), 2.5F, 1.25F, col);
				}
				if (!t.isEmpty()) {
					var m = c.getMatrices();
					m.pushMatrix();
					m.translate(x + 8 - mc.textRenderer.getWidth(t) * 0.375F, y + (bar.on() ? 21 : 18));
					m.scale(0.75F, 0.75F);
					c.drawText(mc.textRenderer, t, 0, 0, tc, shadow.on());
					m.popMatrix();
				}
			}
		}
	}

	/** Small percentage on the damaged items of the hotbar. */
	public void drawHotbar(DrawContext c) {
		if (!hotbar.on() || mc.player == null || mc.options.hudHidden) {
			return;
		}
		int w = mc.getWindow().getScaledWidth(), h = mc.getWindow().getScaledHeight();
		var inv = mc.player.getInventory();
		for (int i = 0; i < 9; i++) {
			ItemStack s = inv.getStack(i);
			if (s.isEmpty() || s.getMaxDamage() <= 0 || s.getDamage() <= 0) {
				continue;
			}
			float f = (s.getMaxDamage() - s.getDamage()) / (float) s.getMaxDamage();
			String t = Math.round(100F * f) + "%";
			int x = w / 2 - 91 + i * 20 + 3, y = h - 19;
			c.getMatrices().pushMatrix();
			c.getMatrices().translate(x, y);
			c.getMatrices().scale(0.6F, 0.6F);
			c.drawText(mc.textRenderer, t, 0, 0, colour(f), true);
			c.getMatrices().popMatrix();
		}
	}
}
