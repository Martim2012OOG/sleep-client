package dev.solclient.gui;

import dev.solclient.Gfx;
import dev.solclient.SolClient;
import dev.solclient.mods.SolClientMod;
import java.util.Set;
import net.minecraft.util.Identifier;

/**
 * Colours of the Sleep UI. Every value below is recomputed from the "Theme & Colours" options of the Sleep settings
 * card by {@link #refresh()}; the initial values are the original look (so a reset gives exactly the old look).
 */
public final class Theme {
	public static int PANEL = 0xF20E0F13;
	/** Same panel colour for the title / pause screens (slightly more opaque). */
	public static int PANEL_UI = 0xF80E0F13;
	public static int PANEL_BORDER = 0xFF23252D;
	public static int CARD = 0xFF15161B;
	public static int CARD_HOVER = 0xFF1D1F26;
	public static int CARD_BORDER = 0xFF23252D;
	public static int CARD_BORDER_HOVER = 0xFF3A3D49;
	public static int ROW = 0xFF15161B;
	public static int FIELD = 0xFF0A0B0E;
	public static int TEXT = 0xFFF1F3F8;
	public static int DIM = 0xFF8C90A0;
	public static int GREEN_BG = 0xFF1B5A2A;
	public static int GREEN_BG_HOVER = 0xFF237436;
	public static int GREEN = 0xFF9BF0AE;
	public static int OFF_BG = 0xFF21232A;
	public static int OFF_BG_HOVER = 0xFF2C2F38;
	public static int RED = 0xFFE5484D;
	/** Surfaces and lines used by the main menu screen. */
	public static int SUB = 0xFF121317, TILE = 0xFF1A1C22, TILE_HOVER = 0xFF22242C, LINE = 0xFF3A3D49;

	/** Preset names shown in the settings. The last one means "your own colours". */
	public static final String[] PRESET_NAMES = {"Sleep Dark", "Midnight", "AMOLED", "Ocean", "Forest", "Sunset", "Rose", "Violet", "Custom"};
	public static final int CUSTOM = 8;

	/** accent, background, card, border, field, text, dim text, toggle on, toggle on highlight, toggle off, danger, button */
	private static final int[][] PRESETS = {
			{0xFF7FB3F5, 0xFF0E0F13, 0xFF15161B, 0xFF23252D, 0xFF0A0B0E, 0xFFF1F3F8, 0xFF8C90A0, 0xFF1B5A2A, 0xFF9BF0AE, 0xFF21232A, 0xFFE5484D, 0xFF323849},
			{0xFF8B7CFF, 0xFF0B0D1A, 0xFF121528, 0xFF252A4A, 0xFF080A14, 0xFFEEF0FF, 0xFF8A90B8, 0xFF2D3A8C, 0xFFA9B4FF, 0xFF1B1F38, 0xFFFF5C7A, 0xFF2B3158},
			{0xFF4DD0E1, 0xFF000000, 0xFF0A0A0C, 0xFF1C1C20, 0xFF000000, 0xFFFFFFFF, 0xFF8A8A94, 0xFF0F5A3A, 0xFF7CFFC4, 0xFF141416, 0xFFFF4D4D, 0xFF1A1A1E},
			{0xFF2DD4BF, 0xFF08141A, 0xFF0E1E26, 0xFF1B3844, 0xFF050E12, 0xFFE8F6FA, 0xFF7FA3B0, 0xFF0F5C66, 0xFF7FF0E0, 0xFF152A33, 0xFFFF6B6B, 0xFF1F4452},
			{0xFF6BCB77, 0xFF0C130E, 0xFF121C15, 0xFF233A29, 0xFF080E0A, 0xFFEAF5EC, 0xFF86A38C, 0xFF1F6B34, 0xFFA8F5B5, 0xFF1A2A1F, 0xFFE5604D, 0xFF2B4A33},
			{0xFFFF9F43, 0xFF160D0D, 0xFF1F1313, 0xFF3A2222, 0xFF0E0808, 0xFFFFF1E8, 0xFFB09088, 0xFF7A3B10, 0xFFFFD27A, 0xFF2A1A1A, 0xFFFF4D6D, 0xFF4A2E2E},
			{0xFFFF6BB5, 0xFF150D12, 0xFF1E131A, 0xFF3A2230, 0xFF0E080B, 0xFFFFEEF6, 0xFFB38AA0, 0xFF7A1F52, 0xFFFFA8D5, 0xFF2A1A23, 0xFFFF5252, 0xFF4A2A3C},
			{0xFFB57BFF, 0xFF110D18, 0xFF181322, 0xFF30254A, 0xFF0B0810, 0xFFF3EEFF, 0xFF9A8CB8, 0xFF4B2A8C, 0xFFD2B4FF, 0xFF211A30, 0xFFFF5C8A, 0xFF3A2F58},
	};

	/** One colour of a preset (used for the default values of the options). */
	public static int presetColour(int preset, int index) {
		return PRESETS[preset][index];
	}

	static final Set<String> ICONS = new java.util.HashSet<>(Set.of("explosion_debris","pack_updater","animated_packs","aspect_ratio","browser","crystal_tweaks","fly_cubes","flying_particles","hit_bubbles","inventory_tweaks","line_glyphs","no_render","smooth_camera","spotify","tps_display","far_horizons","fightcam","monsters_in_the_closet","ghost_item_fix","durability_overlay","tools_items_optimizer","trails","jump_circles","aura","kill_effect","target_marker","item_finder","pickup_burst","damage_tint","target_hud","attack_cooldown","potion_counter","item_cooldowns","death_point","armor_alert","sonar","hit_sounds","fireflies","cave_dust","falling_leaves","water_splashes","water_effects","world_particles","chunk_borders","stable_fov","fov_changer","no_bobbing","totem_animation","body_glow","no_fluid_overlay","auto_respawn","slow_mouse","background_fps","quick_phrases","low_shield","enchantment_glint","custom_hand","baby_mode","hitboxes","waila","tnt_timer","slot_lock","command_guard","dynamic_island","titles","action_bar","auto_hide_hud","minimap","name_tag_style","chat_heads","container_preview","new_item_mark","material_footsteps","hotkeys","skin_totem","1_7_visuals","armour","clock","direction","server_address","memory","session_time","friends_hud","item_counter","low_health","compass_bar","nick_hider","auto_password","cape_physics","no_hurt_cam","weather","day_counter","voice_chat","performance","own_nametag","biome","light_level","entity_count","chunk_pos","health","hunger","xp_level","armour_points","game_time","player_count","target_block","rotation","held_durability","arrow_count","totem_count","chat_timestamps","chat_bubbles","waypoints","fullbright","boss_bar","pack_display","stopwatch","pickup_notifications","inventory_hud","watch_party","chat_search","chat_history","auto_reconnect","block_selection","chat","chunk_animator","colour_saturation","combo_counter","coordinates","cps","crosshair","custom_sky","fps","freelook","hit_colour","hud","hypixel_util","item_physics","keystrokes","language","lock","menu_blur","mods","overlay","motion_blur","new","particles","ping","player","players","potion_effects","quickplay","reach_display","replay","replay_button","scoreboard","search","settings","settings_small","sol_client","speedometer","tab_list","taplook","time_changer","timers","toggle_sprint","tweaks","zoom","low_fire","discord","litematica_helper","tiktok_live","tiktok_viewers","clip_events","trajectories","fight_recap","ruler","daily_quests","photo_mode","cinematic_afk","minigames","font","cinematic_intro","pvp_tweaks","sound_mixer","better_f3","friend_ping","party_hud","health_bars","damage_numbers","level_head","stats_overlay","chat_translator","streamer_mode","session_stats","achievements","auto_profiles","hud_layouts","pack_profiles","cloud_sync","vertical_clips","skin_editor","keybinds","kubo_builder","trident_assist","nametag_health","host_world","name_colour","server_favourites","smooth_fishing_line","tripwire_colour","texture_styles","custom_textures","private_connection","totem_pops","player_card","mace_effects","tool_effects","physics_3d","folder","ext_particles","ext_fun","ext_pvp_extras","ext_hud_extras","ext_practice","extensions","transparent_players","hide_armor","clean_view","hide_inventory_model","speed_fov_limiter","crossbow_tweaks","crosshair_addons","pop_chams","tier_tagger","cobweb_tweaks","info_hud","screen_tweaks","fov","nametags","player_visuals","hand_items","profiles_sync","camera_group","recording","world_group","combat_hud","pvp_group","pat_pat","christmas_world","personas","verity","realistic_liquids","realistic_waves","better_visual","input_optimizer","armor_stand_poses","custom_rain","trust_radar","bactro_tweaks","skin_layers_3d","inventory_buttons","music_share","music_listeners"));

	/** Sleep extensions add the icons of their mods here (the png lives in their own jar, same path). */
	public static void addIcon(String id) {
		ICONS.add(id);
	}

	private Theme() {
	}

	// ------------------------------------------------------------------ live theme
	/** Recomputes every colour from the settings. Cheap; called every frame by the menu and once after loading. */
	public static void refresh() {
		SolClientMod s = SolClient.settings;
		if (s == null) {
			return;
		}
		float glass = s.uiTransparency == null ? 0F : s.uiTransparency.f() / 100F;
		float bgF = (1F - s.bgTransparency.f() / 100F) * (1F - glass);
		float cdF = (1F - s.cardTransparency.f() / 100F) * (1F - glass * 0.6F);
		int bg = s.bgColour.c(), card = s.cardColour.c(), border = s.borderColour.c();
		int on = s.onColour.c(), off = s.offColour.c(), btn = s.buttonColour.c(), acc = s.uiColour.c();
		boolean bgDef = bg == s.bgColour.def, cardDef = card == s.cardColour.def, borderDef = border == s.borderColour.def;

		PANEL = fade(bg, 242F / 255F * bgF);
		PANEL_UI = fade(bg, 248F / 255F * bgF);
		SUB = fade(bgDef ? 0xFF121317 : lighter(bg, 0.0165F), bgF);

		PANEL_BORDER = border;
		CARD_BORDER = border;
		CARD_BORDER_HOVER = borderDef ? 0xFF3A3D49 : lighter(border, 0.115F);
		LINE = CARD_BORDER_HOVER;

		CARD = fade(card, cdF);
		ROW = CARD;
		CARD_HOVER = fade(cardDef ? 0xFF1D1F26 : lighter(card, 0.035F), cdF);
		TILE = fade(cardDef ? 0xFF1A1C22 : lighter(card, 0.022F), cdF);
		TILE_HOVER = fade(cardDef ? 0xFF22242C : lighter(card, 0.055F), cdF);
		FIELD = fade(s.fieldColour.c(), cdF);

		TEXT = s.textColour.c();
		DIM = s.dimColour.c();
		RED = s.dangerColour.c();
		GREEN = s.onGlowColour.c();
		GREEN_BG = on;
		GREEN_BG_HOVER = on == s.onColour.def ? 0xFF237436 : lighter(on, 0.1F);
		OFF_BG = off;
		OFF_BG_HOVER = off == s.offColour.def ? 0xFF2C2F38 : lighter(off, 0.06F);

		boolean btnDef = btn == s.buttonColour.def;
		Gfx.BTN_BODY = btn;
		Gfx.BTN_HOVER = btnDef ? 0xFF3A4155 : lighter(btn, 0.05F);
		Gfx.BTN_PRESSED = btnDef ? 0xFF262A36 : Gfx.blend(btn | 0xFF000000, 0xFF000000, 0.22F);
		Gfx.BTN_EDGE = btnDef ? 0xFF0A0B10 : Gfx.blend(btn | 0xFF000000, 0xFF000000, 0.82F);
		Gfx.BTN_TEXT_ON = acc == s.uiColour.def ? 0xFFB4C6FF : lighter(acc | 0xFF000000, 0.35F);

		// title / pause / accounts screens
		UiScreen.TILE = cardDef ? 0xFF14161B : CARD;
		UiScreen.TILE_HOVER = cardDef ? 0xFF1E2128 : CARD_HOVER;
	}

	/** Multiplies the alpha of a colour. */
	private static int fade(int argb, float f) {
		return Gfx.withAlpha(argb, Math.max(0, Math.min(255, Math.round(((argb >>> 24) & 255) * f))));
	}

	/** Moves the RGB part towards white, keeping the alpha. */
	private static int lighter(int argb, float t) {
		return Gfx.withAlpha(Gfx.blend(argb | 0xFF000000, 0xFFFFFFFF, t), (argb >>> 24) & 255);
	}

	// ------------------------------------------------------------------ icons
	public static int accent() {
		return SolClient.settings.uiColour.c() | 0xFF000000;
	}

	static int accentHover() {
		return Gfx.blend(accent(), 0xFFFFFFFF, 0.18F);
	}

	static Identifier icon(String modId) {
		String n = modId.replace('.', '_');
		return ICONS.contains(n) ? Gfx.id("textures/gui/icon/" + n + ".png") : null;
	}

	public static Identifier iconPublic(String name) {
		return icon2(name);
	}

	static Identifier icon2(String name) {
		return Gfx.id("textures/gui/icon/" + name + ".png");
	}
}
