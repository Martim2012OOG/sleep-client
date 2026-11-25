package dev.solclient.mods;

import dev.solclient.Keys;
import dev.solclient.option.Option;
import dev.solclient.social.ProfileBadges;
import dev.solclient.social.SolarBadge;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Player Card: look at a player who uses Caos and hold the key (or just look, if you choose) to see a small card with his
 * head, name (with his name style), badge and cape. Can be turned off like any other mod.
 */
public class PlayerCardMod extends HudMod {

	private final Option.Key key = key("Card Key", 72);
	private final Option.Bool always = bool("Show Whenever I Look At Him", false);
	private final Option.Bool background = bool("Background", true);
	private final Option.Col backgroundColour = colour("Background Colour", 0xB4101114);

	private Player target;
	private long lastSeen;

	public PlayerCardMod() {
		super("Player Card", "player_card", "Look at a player who uses Caos and hold the key: his badge, cape and name style in a small card.", 0.5F, 0.12F);
	}

	@Override
	public void tick() {
		target = null;
		if (mc.player == null || mc.level == null || dev.solclient.compat.Mc.screen(mc) != null) {
			return;
		}
		if (!always.on() && !Keys.down(key.code())) {
			return;
		}
		Vec3 eye = mc.player.getEyePosition(), look = mc.player.getViewVector(1F);
		double best = Math.cos(Math.toRadians(4.0));
		for (Player p : mc.level.players()) {
			if (p == mc.player || p.isInvisible() || !SolarBadge.usesClient(p)) {
				continue;
			}
			Vec3 to = p.getEyePosition().subtract(eye);
			double d = to.length();
			if (d < 0.5 || d > 40) {
				continue;
			}
			double dot = to.scale(1.0 / d).dot(look);
			if (dot > best) {
				best = dot;
				target = p;
				lastSeen = System.currentTimeMillis();
			}
		}
	}

	@Override
	public boolean visible() {
		return target != null;
	}

	@Override
	public int width() {
		return 160;
	}

	@Override
	public int height() {
		return 46;
	}

	@Override
	protected void renderHud(GuiGraphicsExtractor c, boolean edit) {
		Player p = edit ? mc.player : target;
		if (p == null) {
			return;
		}
		int w = width(), h = height();
		if (background.on()) {
			c.fill(0, 0, w, h, backgroundColour.c());
		}
		try {
			net.minecraft.client.gui.components.PlayerFaceExtractor.extractRenderState(c, ((net.minecraft.client.player.AbstractClientPlayer) p).getSkin(), 6, 7, 32);
		} catch (Throwable ignored) {
			// no skin yet
		}
		String nick = p.getName().getString();
		Component name = ProfileBadges.decorate(p.getUUID(), nick, Component.literal(nick));
		c.text(mc.font, name, 46, 8, 0xFFFFFFFF, true);
		ProfileBadges.Badge b = p == mc.player ? ProfileBadges.equipped() : ProfileBadges.remote(nick);
		String badge = b == null ? dev.solclient.Lang.tr("No badge", "Sem badge") : dev.solclient.Lang.tr(b.en, b.pt);
		c.text(mc.font, dev.solclient.Lang.tr("Badge: ", "Badge: ") + badge, 46, 21, 0xFFB8BDCC, true);
		String capeUrl = dev.solclient.social.Social.capeUrl(p.getUUID());
		c.text(mc.font, dev.solclient.Lang.tr("Cape: ", "Capa: ") + (capeUrl != null ? dev.solclient.Lang.tr("yes", "sim") : dev.solclient.Lang.tr("none", "não")), 46, 32, 0xFFB8BDCC, true);
	}
}
