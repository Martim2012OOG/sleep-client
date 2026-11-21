package dev.sleepclient.compat;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import java.nio.ByteBuffer;

/** Camada de compatibilidade da API de GPU (26.1): o Mojang mudou os construtores e o mapeamento de buffers no 26.2. */
public final class Gpu {
	private Gpu() {
	}

	public static TextureTarget newTarget(String name, int w, int h, boolean depth) { return new TextureTarget(name, w, h, depth); }

	/** Encolhe o framebuffer 'src' para 'dst' na GPU. */
	public static void shrink(RenderTarget src, TextureTarget dst) { src.blitAndBlendToTexture(dst.getColorTextureView()); }

	/** Copia o conteudo (lido pela GPU) do buffer para 'dst'. */
	public static void read(GpuBuffer buf, byte[] dst) {
		try (GpuBuffer.MappedView view = com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder().mapBuffer(buf, true, false)) {
			ByteBuffer data = view.data();
			data.get(0, dst, 0, Math.min(dst.length, data.capacity()));
		}
	}
}
