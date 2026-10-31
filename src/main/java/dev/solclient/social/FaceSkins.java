package dev.solclient.social;

import java.util.ArrayList;
import java.util.List;

/**
 * The skin to draw as a player's face, by Minecraft name. Order: your own skin (equipped / Sleep) -> the skin that
 * friend uploaded to Sleep (cracked names have no Mojang skin) -> the official skin through several mirrors, with a
 * copy kept on disk so faces don't vanish when one site is slow or down. Never blocks: null until a picture is ready.
 */
public final class FaceSkins {

	private FaceSkins() {
	}

	public static boolean validName(String name) {
		return name != null && name.matches("[A-Za-z0-9_]{3,16}");
	}

	public static RemoteTextures.Tex of(String name) {
		return of(name, null);
	}

	/** uuid (with or without dashes) is optional: it adds one more mirror that works by uuid. */
	public static RemoteTextures.Tex of(String name, String uuid) {
		if (!validName(name)) {
			return null;
		}
		var mc = net.minecraft.client.MinecraftClient.getInstance();
		if (name.equalsIgnoreCase(mc.getSession().getUsername())) {
			RemoteTextures.Tex mine = dev.solclient.skin.SkinChanger.myPreview();
			if (mine != null) {
				return mine;
			}
		}
		for (Social.User u : Social.friends) {
			if (name.equalsIgnoreCase(u.mcUsername) || name.equalsIgnoreCase(u.display())) {
				String own = Social.friendSkin(u);
				if (own != null) {
					RemoteTextures.Tex t = RemoteTextures.get(own);
					if (t != null) {
						return t;
					}
				}
				if (uuid == null && u.mcUuid != null) {
					uuid = u.mcUuid;
				}
				break;
			}
		}
		String id = uuid == null ? "" : uuid.replace("-", "");
		List<String> urls = new ArrayList<>();
		urls.add("https://mc-heads.net/skin/" + name);
		urls.add("https://minotar.net/skin/" + name);
		if (id.length() == 32) {
			urls.add("https://crafatar.com/skins/" + id);
		}
		return RemoteTextures.getFirst("name:" + name.toLowerCase(java.util.Locale.ROOT), urls);
	}
}
