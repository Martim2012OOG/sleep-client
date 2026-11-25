package dev.solclient.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.OptionalInt;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

/**
 * Sharp menus, fast world: the 3D world is drawn into a smaller picture and stretched (smoothly) onto the screen, then
 * the menus, the HUD and all text are drawn on top at the full resolution. On a Retina Mac "Auto" draws the world at the
 * same number of pixels as the old fast mode, so the FPS is the same, but the text stays sharp.
 */
public final class WorldScale {

	private static final RenderPipeline UPSCALE = RenderPipelines.register(RenderPipeline.builder()
			.withLocation(Identifier.of("sol_client", "pipeline/world_upscale"))
			.withVertexShader("core/screenquad")
			.withFragmentShader("core/blit_screen")
			.withSampler("InSampler")
			.withoutBlend()
			.withDepthWrite(false)
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.withColorWrite(true, false)
			.withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
			.build());

	private static SimpleFramebuffer small;
	private static boolean active;
	/** For the settings page. */
	public static volatile String status = "";

	private WorldScale() {
	}

	/** The framebuffer everything draws into right now: the small one while the world is being drawn. */
	public static Framebuffer override() {
		return active ? small : null;
	}

	/** How big the world picture is compared to the screen (1 = normal). */
	public static float scale() {
		MinecraftClient mc = MinecraftClient.getInstance();
		float s = dev.solclient.mods.PerformanceMod.worldScale(mc);
		if (s >= 0.999F || irisShaders()) {
			return 1F;
		}
		return Math.max(0.25F, s);
	}

	/** Before the world is drawn. */
	public static void begin() {
		active = false;
		float s;
		try {
			s = scale();
		} catch (Throwable t) {
			return;
		}
		if (s >= 0.999F) {
			status = "100%";
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		Framebuffer main = mc.getFramebuffer();
		int w = Math.max(16, Math.round(main.textureWidth * s)), h = Math.max(16, Math.round(main.textureHeight * s));
		try {
			if (small == null) {
				small = new SimpleFramebuffer("Caos world (scaled)", w, h, true);
			} else if (small.textureWidth != w || small.textureHeight != h) {
				small.resize(w, h);
			}
			active = true;
			status = Math.round(s * 100) + "% (" + w + "x" + h + ")";
		} catch (Throwable t) {
			status = "Off: " + t.getMessage();
		}
	}

	/** After the world is drawn: stretch it onto the real screen. */
	public static void end() {
		if (!active) {
			return;
		}
		active = false;
		MinecraftClient mc = MinecraftClient.getInstance();
		Framebuffer main = mc.getFramebuffer();
		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Caos world upscale", main.getColorAttachmentView(),
				OptionalInt.empty())) {
			pass.setPipeline(UPSCALE);
			RenderSystem.bindDefaultUniforms(pass);
			pass.bindTexture("InSampler", small.getColorAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
			pass.draw(0, 3);
		}
	}

	/** Safety: never leave the small picture on after a crash in the middle of a frame. */
	public static void reset() {
		active = false;
	}

	private static Boolean irisApi;

	private static boolean irisShaders() {
		if (Boolean.FALSE.equals(irisApi)) {
			return false;
		}
		try {
			Class<?> c = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
			Object api = c.getMethod("getInstance").invoke(null);
			irisApi = true;
			return (Boolean) c.getMethod("isShaderPackInUse").invoke(api);
		} catch (Throwable t) {
			irisApi = false;
			return false;
		}
	}
}
