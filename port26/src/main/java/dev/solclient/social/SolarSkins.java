package dev.solclient.social;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * Puts the Sleep cape / skin of a player into their skin textures. Used from two places so it works on 1.21.11 where
 * getSkin() moved to an interface: the getSkin() hook and the player renderer (world AND the 3D menu preview).
 */
public final class SolarSkins {

	private SolarSkins() {
	}

	private static RemoteTextures.Tex capeFor(UUID id) {
		return CapeMedia.custom(id) ? CapeMedia.tex(id) : RemoteTextures.get(Social.capeUrl(id));
	}

	/** The elytra made from this player's cape (null = Minecraft decides, as before). */
	private static RemoteTextures.Tex elytraFor(UUID id) {
		return CapeMedia.custom(id) ? CapeMedia.elytra(id) : RemoteTextures.elytra(Social.capeUrl(id));
	}

	private static Optional<ClientAsset.ResourceTexture> asset(RemoteTextures.Tex t) {
		return t == null ? Optional.empty() : Optional.of(new ClientAsset.ResourceTexture(t.assetId()));
	}

	/** base with this player's Sleep skin / cape on top (base itself when there is nothing to change). */
	public static PlayerSkin apply(UUID id, PlayerSkin base) {
		return apply(id, id, base);
	}

	/**
	 * Same, for a player in the world: capes / skins are stored by ACCOUNT uuid, but on offline-mode (cracked)
	 * servers the in-game uuid is another one, so the account uuid is found through presence (by game nick).
	 */
	public static PlayerSkin apply(net.minecraft.world.entity.player.Player p, PlayerSkin base) {
		if (p == null) {
			return base;
		}
		// this runs for every player on every frame: reuse the answer for a moment while the base skin is the same
		long now = System.currentTimeMillis();
		Object[] c = APPLY_CACHE.get(p.getUUID());
		if (c != null && (long) c[0] > now && c[1] == base && !dev.solclient.cosmetic.Cosmetics.inGui) {
			return (PlayerSkin) c[2];
		}
		UUID backend = dev.solclient.social.Presence.backendId(p);
		// a player the site's lookup knows has a cape to show even when they are not in a presence room
		boolean client = SolarBadge.usesClient(p) || Social.isSolar(backend);
		PlayerSkin out = apply(p.getUUID(), backend, base, client);
		if (APPLY_CACHE.size() > 512) {
			APPLY_CACHE.clear();
		}
		APPLY_CACHE.put(p.getUUID(), new Object[] {now + 250L, base, out});
		return out;
	}

	private static final java.util.Map<UUID, Object[]> APPLY_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

	/** entityId = uuid in the world (nick hider); id = uuid the backend knows (capes, skins, your own skin). */
	public static PlayerSkin apply(UUID entityId, UUID id, PlayerSkin base) {
		return apply(entityId, id, base, true);
	}

	/** client = this player uses the client; if not, the Sleep cape / skin is never put on them (vanilla stays). */
	private static PlayerSkin apply(UUID entityId, UUID id, PlayerSkin base, boolean client) {
		if (base == null || id == null) {
			return base;
		}
		var settings = dev.solclient.SolClient.settings;
		boolean capes = settings != null && settings.solarCapes.on();
		// skin changer: your equipped skin shows on you at once
		RemoteTextures.Tex mine = dev.solclient.skin.SkinChanger.selfTexture(id);
		if (mine != null) {
			RemoteTextures.Tex myCape = capes ? capeFor(id) : null;
			net.minecraft.world.entity.player.PlayerModelType model = dev.solclient.skin.SkinChanger.modelType(dev.solclient.skin.SkinChanger.selfSlim());
			return base.with(PlayerSkin.Patch.create(
					Optional.of(new ClientAsset.ResourceTexture(mine.assetId())),
					myCape == null ? Optional.empty() : Optional.of(new ClientAsset.ResourceTexture(myCape.assetId())),
					myCape == null ? Optional.empty() : asset(elytraFor(id)),
					model == null ? Optional.empty() : Optional.of(model)));
		}
		if (!capes) {
			return base;
		}
		if (dev.solclient.mods.NickHiderMod.hideSkin(id)) {
			return net.minecraft.client.resources.DefaultPlayerSkin.get(entityId);
		}
		if (!client) {
			return base;
		}
		RemoteTextures.Tex cape = capeFor(id);
		RemoteTextures.Tex skin = RemoteTextures.get(Social.skinUrl(id));
		if (cape == null && skin == null) {
			return base;
		}
		return base.with(PlayerSkin.Patch.create(
				skin == null ? Optional.empty() : Optional.of(new ClientAsset.ResourceTexture(skin.assetId())),
				cape == null ? Optional.empty() : Optional.of(new ClientAsset.ResourceTexture(cape.assetId())),
				cape == null ? Optional.empty() : asset(elytraFor(id)),
				Optional.empty()));
	}
}
