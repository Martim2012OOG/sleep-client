package dev.solclient.video;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.texture.AbstractTexture;

/** A texture id that points at a browser's current frame (the browser owns the GPU texture, not us). */
final class BrowserTexture extends AbstractTexture {

	BrowserTexture() {
		this.sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
	}

	boolean set(GpuTextureView v) {
		if (v == null || v.isClosed()) {
			this.glTextureView = null;
			this.glTexture = null;
			return false;
		}
		this.glTextureView = v;
		this.glTexture = v.texture();
		return true;
	}

	@Override
	public void close() {
		// never close the browser's texture here
		this.glTextureView = null;
		this.glTexture = null;
	}
}
