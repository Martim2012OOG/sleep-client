package dev.solclient.mixin.social;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.solclient.fx.Holo;
import java.nio.ByteBuffer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.render.ProjectionMatrix2;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The GUI pass of a frame is drawn in two parts when a container screen is floating (the HUD, then the screen). Before the second part the
 * projection is replaced by "flat projection x perspective transform", so everything of the screen (slots, items, text, the player model)
 * is drawn tilted in perspective. After the pass the flat projection is restored.
 */
@Mixin(GuiRenderer.class)
public abstract class HoloGuiRendererMixin {

	@Shadow
	private ProjectionMatrix2 guiProjectionMatrix;
	@Shadow
	private int blurLayer;

	@Inject(method = "render(Ljava/util/function/Supplier;Lnet/minecraft/client/gl/Framebuffer;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/vertex/VertexFormat$IndexType;II)V",
			at = @At("HEAD"), require = 0)
	private void holo$projection(java.util.function.Supplier<String> name, net.minecraft.client.gl.Framebuffer fb, com.mojang.blaze3d.buffers.GpuBufferSlice a,
			com.mojang.blaze3d.buffers.GpuBufferSlice b, com.mojang.blaze3d.buffers.GpuBuffer ib, com.mojang.blaze3d.vertex.VertexFormat.IndexType t, int from, int to,
			CallbackInfo ci) {
		if (!Holo.pending || !Holo.valid() || from <= 0 || from < blurLayer) {
			return;
		}
		try {
			var w = MinecraftClient.getInstance().getWindow();
			float gw = (float) w.getFramebufferWidth() / w.getScaleFactor(), gh = (float) w.getFramebufferHeight() / w.getScaleFactor();
			HoloProjectionAccessor acc = (HoloProjectionAccessor) guiProjectionMatrix;
			Matrix4f m = new Matrix4f(acc.holo$matrix(gw, gh)).mul(Holo.matrix());
			try (MemoryStack st = MemoryStack.stackPush()) {
				ByteBuffer bb = Std140Builder.onStack(st, RenderSystem.PROJECTION_MATRIX_UBO_SIZE).putMat4f(m).get();
				RenderSystem.getDevice().createCommandEncoder().writeToBuffer(acc.holo$buffer().slice(), bb);
			}
			// the next frame must upload the flat projection again
			acc.holo$setWidth(-1F);
			acc.holo$setHeight(-1F);
		} catch (Throwable ignored) {
			// leave the flat projection: the screen is simply not tilted
		}
	}

	@Inject(method = "renderPreparedDraws(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V", at = @At("RETURN"), require = 0)
	private void holo$done(com.mojang.blaze3d.buffers.GpuBufferSlice fog, CallbackInfo ci) {
		Holo.pending = false;
	}
}
