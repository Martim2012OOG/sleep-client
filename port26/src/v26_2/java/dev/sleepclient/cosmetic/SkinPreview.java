package dev.sleepclient.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.sleepclient.social.RemoteTextures;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 26.2: as pre-visualizacoes 3D nos menus (emotes, cosmeticos, kill effects, personagem) dependiam do MultiBufferSource e do
 * GuiGraphics.skin(PlayerModel), que o Mojang mudou. Ainda NAO estao portadas: devolvem false (quem chama mostra o fallback).
 */
public final class SkinPreview {
	private SkinPreview() {
	}

	public static volatile Object provider;

	public static void menuEmote(dev.sleepclient.emote.EmoteLibrary.Info e) {
	}

	public static boolean menuEmotePlaying() {
		return false;
	}

	public static boolean drawEmoteCard(GuiGraphicsExtractor c, dev.sleepclient.emote.EmoteLibrary.Info info, boolean play, int x, int y, int w, int h) {
		return false;
	}

	public static boolean drawWearCard(GuiGraphicsExtractor c, String key, net.minecraft.resources.Identifier skin, boolean slim, List<CosmeticModels.Def> wear, int x, int y,
			int w, int h) {
		return false;
	}

	public static boolean drawKillCard(GuiGraphicsExtractor c, String effectId, net.minecraft.resources.Identifier skin, boolean play, int x, int y, int w, int h) {
		return false;
	}

	public static void afterModel(Object model, PoseStack matrices, int light) {
	}

	public static boolean draw(GuiGraphicsExtractor c, RemoteTextures.Tex skin, boolean isSlim, float cx, float feetY, float px, float yaw, float pitch,
			List<CosmeticModels.Def> wear) {
		return false;
	}
}
