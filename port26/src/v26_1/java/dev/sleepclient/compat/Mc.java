package dev.sleepclient.compat;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.pipeline.RenderTarget;

/**
 * Camada de compatibilidade (versao 26.1): acesso ao estado do ecra/HUD/camara do Minecraft.
 * O Mojang moveu isto de sitio entre o 26.1 e o 26.2, por isso cada versao tem a sua copia desta classe
 * (src/v26_1/java). O resto do codigo chama sempre Mc.*.
 */
public final class Mc {
	private Mc() {
	}

	public static Screen screen(Minecraft mc) { return mc.screen; }
	public static void setScreen(Minecraft mc, Screen s) { mc.setScreen(s); }
	public static Overlay overlay(Minecraft mc) { return mc.getOverlay(); }
	public static void setOverlay(Minecraft mc, Overlay o) { mc.setOverlay(o); }
	public static boolean hideGui(Minecraft mc) { return mc.options.hideGui; }
	public static void setHideGui(Minecraft mc, boolean v) { mc.options.hideGui = v; }
	public static ChatComponent chat(Minecraft mc) { return mc.gui.getChat(); }
	public static void overlayMessage(Minecraft mc, Component c, boolean animate) { mc.gui.setOverlayMessage(c, animate); }
	public static Camera camera(Minecraft mc) { return mc.gameRenderer.getMainCamera(); }
	public static RenderTarget mainTarget(Minecraft mc) { return mc.getMainRenderTarget(); }

	public static void allChanged(Minecraft mc) { mc.levelRenderer.allChanged(); }
	public static net.minecraft.client.gui.components.PlayerTabOverlay tabList(Minecraft mc) { return mc.gui.getTabList(); }
	public static net.minecraft.client.gui.components.toasts.ToastManager toasts(Minecraft mc) { return mc.getToastManager(); }
	public static void extractDeferredSubtitles(Minecraft mc) { mc.gui.extractDeferredSubtitles(); }
	public static boolean publish(net.minecraft.client.server.IntegratedServer s, net.minecraft.world.level.GameType gm, boolean cheats, int port) { return s.publishServer(gm, cheats, port); }
	public static net.minecraft.client.telemetry.WorldSessionTelemetryManager worldTelemetry(Minecraft mc) { return mc.getTelemetryManager().createWorldSessionManager(false, java.time.Duration.ZERO, null); }
	public static void submitNameTag(net.minecraft.client.renderer.OrderedSubmitNodeCollector q, com.mojang.blaze3d.vertex.PoseStack m, net.minecraft.world.phys.Vec3 pos, int y, Component tag, boolean notDiscrete, int light, double distSq, net.minecraft.client.renderer.state.level.CameraRenderState cam) { q.submitNameTag(m, pos, y, tag, notDiscrete, light, distSq, cam); }
	public static Object levelObj(Minecraft mc) { return mc.levelRenderer; }
}
