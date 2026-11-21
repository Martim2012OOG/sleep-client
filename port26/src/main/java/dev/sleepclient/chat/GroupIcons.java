package dev.sleepclient.chat;

import dev.sleepclient.Gfx;
import dev.sleepclient.Lang;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Group logos the owner can pick (no uploads): a colour gradient with a symbol, drawn from the client's own icons.
 * "" = the members' heads (the old look). The id is stored on the group by the site ("icon").
 */
public final class GroupIcons {

	public record Preset(String id, String en, String pt, int top, int bottom, String icon) {
		public String name() {
			return Lang.tr(en, pt);
		}
	}

	public static final List<Preset> ALL = List.of(
			new Preset("", "Members", "Membros", 0, 0, ""),
			new Preset("p_swords", "Swords", "Espadas", 0xFFFF5E5E, 0xFFB3122E, "pvp_group"),
			new Preset("p_star", "Star", "Estrela", 0xFFFFD86B, 0xFFE58A00, "star_filled"),
			new Preset("p_fire", "Fire", "Fogo", 0xFFFFA24A, 0xFFE5302B, "low_fire"),
			new Preset("p_diamond", "Diamond", "Diamante", 0xFF7AF0FF, 0xFF2C7BE5, "crystal_tweaks"),
			new Preset("p_games", "Games", "Jogos", 0xFFB58CFF, 0xFF5B2FD0, "minigames"),
			new Preset("p_world", "World", "Mundo", 0xFF6EE7A0, 0xFF138A5B, "world_group"),
			new Preset("p_heart", "Heart", "Coração", 0xFFFF8FB8, 0xFFE0306E, "health"),
			new Preset("p_tnt", "TNT", "TNT", 0xFFFF6B6B, 0xFF7A1010, "tnt_timer"),
			new Preset("p_trophy", "Trophy", "Troféu", 0xFFFFE27A, 0xFFB98A0C, "achievements"),
			new Preset("p_snow", "Snow", "Neve", 0xFFE6F6FF, 0xFF7FB8E8, "christmas_world"),
			new Preset("p_bolt", "Speed", "Velocidade", 0xFFFFF27A, 0xFFF59A23, "speedometer"),
			new Preset("p_ghost", "Spooky", "Assustador", 0xFF8A8FA6, 0xFF2A2D3A, "monsters_in_the_closet"),
			new Preset("p_music", "Music", "Música", 0xFF7DF0C8, 0xFF1F9E8A, "sound_mixer"),
			new Preset("p_build", "Builders", "Construtores", 0xFFF5C27A, 0xFF9A5B22, "kubo_builder"),
			new Preset("p_camera", "Clips", "Clips", 0xFF9BB4FF, 0xFF3B4FBF, "recording"),
			new Preset("p_target", "Aim", "Mira", 0xFFFF9C9C, 0xFFD63B3B, "target_marker"),
			new Preset("p_moon", "Night", "Noite", 0xFF6C6FD8, 0xFF1A1C4A, "custom_sky"),
			new Preset("p_shield", "Shield", "Escudo", 0xFFA6B0C2, 0xFF4A5468, "low_shield"),
			new Preset("p_crown", "Royals", "Realeza", 0xFFFFE08A, 0xFF8E44AD, "favourite"),
			new Preset("p_party", "Party", "Festa", 0xFFFF9FF3, 0xFF54A0FF, "party_hud"));

	private GroupIcons() {
	}

	public static Preset of(String id) {
		if (id != null && !id.isEmpty()) {
			for (Preset p : ALL) {
				if (p.id.equals(id)) {
					return p;
				}
			}
		}
		return null;
	}

	/** Draws a preset logo in a square (x, y, s). */
	public static void draw(GuiGraphicsExtractor c, Preset p, int x, int y, int s) {
		int r = Math.max(3, s / 4);
		Gfx.rrect(c, x, y, s, s, r, p.bottom);
		int in = Math.max(1, r / 3);
		Gfx.gradientV(c, x + in, y + in, s - 2 * in, s - 2 * in, p.top, p.bottom);
		Gfx.rrect(c, x, y, s, Math.max(2, s / 2), r, Gfx.withAlpha(0xFFFFFF, 0x1E)); // soft shine on the top half
		int is = Math.round(s * 0.62F);
		Identifier id = Gfx.id("textures/gui/icon/" + p.icon + ".png");
		Gfx.tex(c, id, x + (s - is) / 2 + 1, y + (s - is) / 2 + 1, is, is, 64, 64, 0x55000000); // shadow
		Gfx.tex(c, id, x + (s - is) / 2, y + (s - is) / 2, is, is, 64, 64, 0xFFFFFFFF);
	}
}
