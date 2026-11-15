package dev.solclient.cosmetic;

import dev.solclient.social.Social;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Which 3D cosmetics a player wears: the shop items they equipped (from the Solar site) that have a model in the client. */
public final class Cosmetics {

	private record Cached(long at, List<CosmeticModels.Def> list) {
	}

	private static final Map<UUID, Cached> CACHE = new ConcurrentHashMap<>();

	private Cosmetics() {
	}

	/** Try-on list of the Locker preview (applies to you only while the preview is being drawn). */
	private static volatile List<CosmeticModels.Def> preview;
	/** True only while Minecraft draws an entity inside a menu (set by GuiEntityRenderFlagMixin). */
	public static volatile boolean inGui;
	private static volatile long previewAt;

	/** What you wear changed (equipped / unequipped): the next frame draws it, not 0.4 s later. */
	public static void invalidate() {
		CACHE.clear();
	}

	public static void preview(List<CosmeticModels.Def> wear) {
		preview = wear;
		previewAt = System.currentTimeMillis();
	}

	public static List<CosmeticModels.Def> worn(UUID player) {
		long now = System.currentTimeMillis();
		List<CosmeticModels.Def> pv = preview;
		if (pv != null && inGui && now - previewAt < 300L && player.equals(net.minecraft.client.Minecraft.getInstance().getUser().getProfileId())) {
			return pv;
		}
		Cached c = CACHE.get(player);
		if (c != null && now - c.at < 400L) {
			return c.list;
		}
		List<CosmeticModels.Def> out = new ArrayList<>();
		for (String itemId : Social.wornItems(player)) {
			CosmeticModels.Def d = modelFor(itemId);
			if (d != null && !out.contains(d)) {
				out.add(d);
			}
		}
		if (player.equals(net.minecraft.client.Minecraft.getInstance().getUser().getProfileId())) {
			out.replaceAll(Variants::resolve); // your own colour choices
		} else {
			out.replaceAll(d -> Offsets.remoteVariant(player, d)); // the colour the other player picked (sent through the presence room)
		}
		CACHE.put(player, new Cached(now, out));
		return out;
	}

	/** Shop item -> model: its modelId, else its id, else its name. */
	public static CosmeticModels.Def modelFor(String itemId) {
		Social.Item it = Social.catalogueItem(itemId);
		CosmeticModels.Def d = null;
		if (it != null) {
			d = CosmeticModels.find(it.modelId);
			if (d == null) {
				d = CosmeticModels.find(it.id);
			}
			if (d == null) {
				d = CosmeticModels.find(it.name);
			}
		}
		return d != null ? d : CosmeticModels.find(itemId);
	}

	public static CosmeticModels.Def modelFor(Social.Item it) {
		if (it == null) {
			return null;
		}
		CosmeticModels.Def d = CosmeticModels.find(it.modelId);
		if (d == null) {
			d = CosmeticModels.find(it.id);
		}
		return d != null ? d : CosmeticModels.find(it.name);
	}
}
