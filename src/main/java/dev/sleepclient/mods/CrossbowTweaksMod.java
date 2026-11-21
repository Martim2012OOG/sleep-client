package dev.sleepclient.mods;

import dev.sleepclient.Gfx;
import dev.sleepclient.option.Option;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

/**
 * Crossbow Tweaks: a charge bar under the crosshair while you load the crossbow (with a tick when it is ready), and
 * when it is loaded, a small icon of what is in it (arrow or firework) next to the crosshair.
 */
public class CrossbowTweaksMod extends Mod {

	public static CrossbowTweaksMod instance;

	private final Option.Bool chargeBar = bool("Charge Bar", true);
	private final Option.Bool loadedIcon = bool("Show What Is Loaded", true);
	private final Option.Bool sound = bool("Sound When Loaded", true);
	private boolean dinged;

	public CrossbowTweaksMod() {
		super("Crossbow Tweaks", "crossbow_tweaks", "Charge bar while loading and an icon of what is loaded.", Category.VISUAL);
		instance = this;
	}

	public void draw(DrawContext c) {
		var p = mc.player;
		if (p == null || mc.options.hudHidden || mc.currentScreen != null) {
			return;
		}
		int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
		ItemStack active = p.isUsingItem() ? p.getActiveItem() : ItemStack.EMPTY;
		if (chargeBar.on() && active.isOf(Items.CROSSBOW)) {
			int need = Math.max(1, CrossbowItem.getPullTime(active, p));
			float k = Math.min(1F, p.getItemUseTime() / (float) need);
			if (k >= 1F && !dinged) {
				dinged = true;
				if (sound.on()) {
					p.playSound(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), 0.5F, 1.8F);
				}
			}
			int w = 34, x = sw / 2 - w / 2, y = sh / 2 + 10;
			Gfx.rrectFree(c, x - 1, y - 1, w + 2, 5, 2.5F, 0x99000000);
			Gfx.rrectFree(c, x, y, Math.max(2, w * k), 3, 1.5F, k >= 1F ? 0xFF4ADE80 : 0xFFE8E8F0);
		} else {
			dinged = false;
		}
		if (loadedIcon.on()) {
			for (Hand h : Hand.values()) {
				ItemStack st = p.getStackInHand(h);
				if (!st.isOf(Items.CROSSBOW) || !CrossbowItem.isCharged(st)) {
					continue;
				}
				ChargedProjectilesComponent cp = st.get(DataComponentTypes.CHARGED_PROJECTILES);
				if (cp == null || cp.isEmpty()) {
					continue;
				}
				ItemStack shown = cp.getProjectiles().get(0);
				var m = c.getMatrices();
				m.pushMatrix();
				m.translate(sw / 2F + 9, sh / 2F + 3);
				m.scale(0.6F, 0.6F);
				c.drawItem(shown, 0, 0);
				m.popMatrix();
				if (cp.getProjectiles().size() > 1) {
					Gfx.textRaw(c, "x" + cp.getProjectiles().size(), sw / 2 + 20, sh / 2 + 6, 0xFFFFFFFF);
				}
				break;
			}
		}
	}
}
