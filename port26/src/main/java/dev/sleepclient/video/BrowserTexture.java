package dev.sleepclient.video;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.renderer.texture.AbstractTexture;

/** A texture id that points at a browser's current frame (the browser owns the GPU texture, not us). */
final class BrowserTexture extends AbstractTexture {

	BrowserTexture() {
		this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
	}

	boolean set(GpuTextureView v) {
		if (v == null || v.isClosed()) {
			this.textureView = null;
			this.texture = null;
			return false;
		}
		this.textureView = v;
		this.texture = v.texture();
		return true;
	}

	@Override
	public void close() {
		// never close the browser's texture here
		this.textureView = null;
		this.texture = null;
	}
}
