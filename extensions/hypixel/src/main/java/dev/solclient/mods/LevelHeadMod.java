package dev.solclient.mods;

import dev.solclient.option.Option;
import dev.solclient.social.HypixelApi;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

/**
 * Level Head: on Hypixel, shows each player's level / BedWars stars / FKDR next to their nametag. Needs your Hypixel
 * API key in Hypixel Additions.
 */
public class LevelHeadMod extends Mod {

	public static LevelHeadMod instance;
	private final Option.Choice show = choice("Show", 1, "Network Level", "BedWars Stars", "BedWars FKDR", "SkyWars Level", "Duels Wins");
	private final Option.Bool onlyHypixel = bool("Only On Hypixel", true);
	private final Option.Bool self = bool("On Yourself (F5)", true);

	public LevelHeadMod() {
		super("Level Head", "level_head", "Hypixel level, BedWars stars or FKDR next to every nametag.", Category.UTILITY);
		instance = this;
		action("Hypixel API key", "Set in Hypixel Additions", () -> {
		}, () -> !HypixelApi.hasKey() ? "No key yet" : HypixelApi.problem != null ? HypixelApi.problem : "Key OK");
	}

	/** Called from the nametag mixin. */
	public static Text decorate(PlayerEntity p, Text name) {
		if (instance == null || !instance.enabled || name == null || !HypixelApi.hasKey()) {
			return name;
		}
		if (instance.onlyHypixel.on() && !HypixelAdditionsMod.onHypixel()) {
			return name;
		}
		var mc = net.minecraft.client.MinecraftClient.getInstance();
		if (p == mc.player && !instance.self.on()) {
			return name;
		}
		HypixelApi.Stats s = HypixelApi.get(p.getUuid());
		if (s == null || !s.loaded) {
			return name;
		}
		String v;
		int col;
		if (s.nicked) {
			v = "NICK";
			col = 0xFFFF5555;
		} else {
			switch (instance.show.idx()) {
				case 0 -> {
					v = "Lv " + (int) s.level;
					col = 0xFF55FFFF;
				}
				case 2 -> {
					v = String.format("%.2f FKDR", s.fkdr());
					col = HypixelApi.threatColour(s.fkdr());
				}
				case 3 -> {
					v = s.swLevel + "★";
					col = 0xFF55FFFF;
				}
				case 4 -> {
					v = s.duelsWins + " W";
					col = HypixelApi.threatColour(s.duelsWlr());
				}
				default -> {
					v = "[" + s.bwStar + "✫]";
					col = HypixelApi.starColour(s.bwStar);
				}
			}
		}
		MutableText out = Text.empty().append(name).append(Text.literal(" "));
		out.append(Text.literal(v).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(col & 0xFFFFFF))));
		return out;
	}
}
