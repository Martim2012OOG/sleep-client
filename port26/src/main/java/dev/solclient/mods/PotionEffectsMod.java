package dev.solclient.mods;

import dev.solclient.Gfx;
import dev.solclient.option.Option;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class PotionEffectsMod extends HudMod {

	private static final int EFFECT_HEIGHT = 33;
	private static final String[] ROMAN = {"", "II", "III", "IV"};

	private final Option.Choice alignment = choice("Alignment", 1, "Top", "Middle", "Bottom");
	private final Option.Bool icon = bool("Icon", true);
	private final Option.Bool background = bool("Background", false);
	private final Option.Bool shadow = bool("Text Shadow", true);
	private final Option.Col titleColour = colour("Title Colour", 0xFFFFFFFF);
	private final Option.Col durationColour = colour("Duration Colour", 0xFF7F7F7F);

	public PotionEffectsMod() {
		super("Potion Effects", "potion_effects", "Display your potion effects on the HUD.", 0.01F, 0.6F);
	}

	@Override
	public int width() {
		return 120;
	}

	@Override
	public int height() {
		return 64;
	}

	@Override
	public int dy() {
		switch (alignment.idx()) {
			case 1: return -33;
			case 2: return -66;
			default: return 0;
		}
	}

	private static String time(MobEffectInstance e) {
		if (e.isInfiniteDuration()) {
			return "**:**";
		}
		int secs = (int) Math.ceil(e.getDuration() / 20.0);
		if (secs >= 6000) {
			return "**:**";
		}
		return String.format("%d:%02d", secs / 60, secs % 60);
	}

	@Override
	protected void renderHud(GuiGraphicsExtractor c, boolean edit) {
		Collection<MobEffectInstance> effects;
		if (edit || mc.player == null) {
			List<MobEffectInstance> sample = new ArrayList<>();
			sample.add(new MobEffectInstance(MobEffects.SPEED, 1200));
			sample.add(new MobEffectInstance(MobEffects.STRENGTH, 600));
			effects = sample;
		} else {
			effects = mc.player.getActiveEffects();
		}
		int y = 0;
		switch (alignment.idx()) {
			case 1: y -= (EFFECT_HEIGHT * effects.size()) / 2; break;
			case 2: y -= EFFECT_HEIGHT * effects.size(); break;
			default:
		}
		// many effects: the list moves up (or down) so it never runs off the screen
		int sh = mc.getWindow().getGuiScaledHeight();
		float sc = s();
		int total = EFFECT_HEIGHT * effects.size();
		float bottom = absY() + (y + total) * sc;
		if (bottom > sh - 2) {
			y -= (int) Math.ceil((bottom - (sh - 2)) / sc);
		}
		if (absY() + y * sc < 2) {
			y = (int) Math.ceil((2 - absY()) / sc);
		}
		for (MobEffectInstance effect : effects) {
			if (background.on()) {
				Gfx.sprite(c, Identifier.withDefaultNamespace("container/inventory/effect_background"), 0, y, 120, 32);
			}
			if (icon.on()) {
				String path = effect.getEffect().unwrapKey().map(k -> k.identifier().getPath()).orElse("speed");
				Gfx.sprite(c, Identifier.withDefaultNamespace("mob_effect/" + path), 6, y + 7, 18, 18);
			}
			String title = effect.getEffect().value().getDisplayName().getString();
			int amp = effect.getAmplifier();
			if (amp > 0 && amp < 4) {
				title += " " + (TweaksMod.arabic() ? Integer.toString(amp + 1) : ROMAN[amp]);
			}
			c.text(mc.font, title, 10 + 18, y + 6, titleColour.c(), shadow.on());
			c.text(mc.font, time(effect), 10 + 18, y + 6 + 10, durationColour.c(), shadow.on());
			y += EFFECT_HEIGHT;
		}
	}
}
