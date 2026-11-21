package dev.sleepclient.render;

import com.mojang.blaze3d.pipeline.RenderTarget;

/**
 * 26.2: o "World Scale" (desenhar o mundo mais pequeno e esticar) usava a API de pipelines/render passes do 26.1, que mudou no 26.2
 * (GpuFormat, amostradores, draw). Ainda NAO esta portado: fica sempre desligado (o Minecraft desenha normalmente).
 */
public final class WorldScale {
	private WorldScale() {
	}

	public static volatile String status = "Off (26.2: nao portado)";

	public static RenderTarget override() {
		return null;
	}

	public static float scale() {
		return 1F;
	}

	public static void begin() {
	}

	public static void end() {
	}

	public static void reset() {
	}
}
