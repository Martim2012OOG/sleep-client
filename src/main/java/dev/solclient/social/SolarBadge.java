package dev.solclient.social;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;

/** The Plegumo logo in front of the names (nametags and tab list) of players who use the client. */
public final class SolarBadge {

	private static Text badge;
	private static Method profileId;
	private static Method profileName;

	private SolarBadge() {
	}

	/** The logo as one character of the "sol_client:badge" bitmap font (assets/sol_client/font/badge.json). */
	private static Text badge() {
		if (badge == null) {
			JsonObject o = new JsonObject();
			o.addProperty("text", "");
			o.addProperty("font", "sol_client:badge");
			o.addProperty("color", "white");
			badge = TextCodecs.CODEC.parse(JsonOps.INSTANCE, o).result().orElse(Text.literal(""));
		}
		return badge;
	}

	/** True if this player is using Plegumo right now (in your server's presence room), or you while connected. */
	public static boolean uses(UUID id) {
		return uses(id, null);
	}

	/** Same as uses(UUID), but also matches by game nick (needed on offline-mode servers, where uuids differ). */
	public static boolean uses(UUID id, String nick) {
		if (id == null || dev.solclient.SolClient.settings == null || !dev.solclient.SolClient.settings.nameBadge.on()) {
			return false;
		}
		// Only people using the client RIGHT NOW: connected to the presence Worker in this server's room.
		// (The site's /players/lookup lists everyone who ever made an account, so it is not used here.)
		if (Presence.inServerRoom(id, nick) || Presence.inLobby(id, nick)) {
			return true; // the lobby is the global room: it also finds players who joined this server by another address
		}
		// you: always (you ARE using Plegumo), even while the presence connection is down
		MinecraftClient mc = MinecraftClient.getInstance();
		UUID me = mc.getSession().getUuidOrNull();
		// your in-game UUID differs from your account's on offline-mode servers
		return id.equals(me) || mc.player != null && id.equals(mc.player.getUuid());
	}

	/**
	 * True if this player of the world uses the client right now: you, or someone in your server's presence room - the same
	 * check as the logo in the tab list, but it does not depend on the "name badge" setting. Cosmetics, emotes and capes of
	 * other players are only drawn when this is true. The 3D previews in the menus always count.
	 */
	public static boolean usesClient(net.minecraft.entity.player.PlayerEntity p) {
		if (p == null) {
			return false;
		}
		if (dev.solclient.cosmetic.Cosmetics.inGui) {
			return true;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (p == mc.player) {
			return true;
		}
		String nick = p.getName().getString();
		return Presence.inServerRoom(p.getUuid(), nick) || Presence.inServerRoom(Presence.backendId(p), nick) || Presence.inLobby(p.getUuid(), nick);
	}

	public static Text decorate(Text name) {
		if (name == null || badge().getString().isEmpty()) {
			return name;
		}
		return Text.empty().append(badge()).append(Text.literal(" ")).append(name);
	}

	/** The game nick of a GameProfile (name() in newer authlib, getName() in older ones). */
	public static String nameOf(Object gameProfile) {
		if (gameProfile == null) {
			return null;
		}
		try {
			if (profileName == null) {
				try {
					profileName = gameProfile.getClass().getMethod("name");
				} catch (NoSuchMethodException e) {
					profileName = gameProfile.getClass().getMethod("getName");
				}
			}
			Object v = profileName.invoke(gameProfile);
			return v instanceof String n ? n : null;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	/** GameProfile changed from getId() to a record id() in newer authlib: read it either way. */
	public static UUID idOf(Object gameProfile) {
		if (gameProfile == null) {
			return null;
		}
		try {
			if (profileId == null) {
				try {
					profileId = gameProfile.getClass().getMethod("id");
				} catch (NoSuchMethodException e) {
					profileId = gameProfile.getClass().getMethod("getId");
				}
			}
			Object v = profileId.invoke(gameProfile);
			return v instanceof UUID u ? u : null;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}
}
