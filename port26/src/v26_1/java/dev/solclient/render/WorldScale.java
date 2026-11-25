package dev.solclient.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Sharp menus, fast world: the 3D world is drawn into a smaller picture and stretched (smoothly) onto the screen, then
 * the menus, the HUD and all text are drawn on top at the full resolution. On a Retina Mac "Auto" draws the world at the
 * same number of pixels as the old fast mode, so the FPS is the same, but the text stays sharp.
 */
public final class WorldScale {

	private static final RenderPipeline UPSCALE = RenderPipelines.register(RenderPipeline.builder()
			.withLocation(Identifier.fromNamespaceAndPath("sol_client", "pipeline/world_upscale"))
			.withVertexShader("core/screenquad")
			.withFragmentShader("core/blit_screen")
			.withSampler("InSampler")
			.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
			.withColorTargetState(new ColorTargetState(java.util.Optional.empty(), ColorTargetState.WRITE_COLOR))
			.withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
			.build());

	private static TextureTarget small;
	private static boolean active;
	/** For the settings page. */
	public static volatile String status = "";

	private WorldScale() {
	}

	/** The framebuffer everything draws into right now: the small one while the world is being drawn. */
	public static RenderTarget override() {
		return active ? small : null;
	}

	/** How big the world picture is compared to the screen (1 = normal). */
	public static float scale() {
		Minecraft mc = Minecraft.getInstance();
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
		Minecraft mc = Minecraft.getInstance();
		RenderTarget main = dev.solclient.compat.Mc.mainTarget(mc);
		int w = Math.max(16, Math.round(main.width * s)), h = Math.max(16, Math.round(main.height * s));
		try {
			if (small == null) {
				small = new TextureTarget("Caos world (scaled)", w, h, true);
			} else if (small.width != w || small.height != h) {
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
		Minecraft mc = Minecraft.getInstance();
		RenderTarget main = dev.solclient.compat.Mc.mainTarget(mc);
		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Caos world upscale", main.getColorTextureView(),
				OptionalInt.empty())) {
			pass.setPipeline(UPSCALE);
			RenderSystem.bindDefaultUniforms(pass);
			pass.bindTexture("InSampler", small.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
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
