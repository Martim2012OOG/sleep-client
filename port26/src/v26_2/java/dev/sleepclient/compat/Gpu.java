package dev.sleepclient.compat;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import java.nio.ByteBuffer;

/** Camada de compatibilidade da API de GPU (26.2/26.3): o Mojang mudou os construtores e o mapeamento de buffers no 26.2. */
public final class Gpu {
	private Gpu() {
	}

	public static TextureTarget newTarget(String name, int w, int h, boolean depth) { return new TextureTarget(name, w, h, depth, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM); }

	/** 26.2: a assinatura do blit mudou e nao foi verificada; o Capture cai para a copia da imagem inteira. */
	public static void shrink(RenderTarget src, TextureTarget dst) { throw new UnsupportedOperationException("shrink nao portado para 26.2"); }

	public static void read(GpuBuffer buf, byte[] dst) {
		try (com.mojang.blaze3d.buffers.GpuBufferSlice.MappedView view = buf.map(true, false)) {
			ByteBuffer data = view.data();
			data.get(0, dst, 0, Math.min(dst.length, data.capacity()));
		}
	}
}
