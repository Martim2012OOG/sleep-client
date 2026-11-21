package dev.sleepclient.fx.killfx;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.Identifier;

/** 26.2: o MultiBufferSource foi removido do Minecraft; a pre-visualizacao nos menus ainda nao esta portada (nao desenha nada). */
public final class KillFxPreview {
	private KillFxPreview() {
	}

	public static void draw(String id, float t, PoseStack ms, Object p, int light, Identifier skin) {
	}
}
