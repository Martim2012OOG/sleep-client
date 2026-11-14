package dev.solclient.mixin.social;

import dev.solclient.fx.Holo;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.input.MouseInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The container screens of the experimental 3D GUI: starts the floating panel and maps the mouse onto it. */
@Mixin(HandledScreen.class)
public abstract class HoloHandledScreenMixin {

	@Shadow
	protected int x;
	@Shadow
	protected int y;
	@Shadow
	protected int backgroundWidth;
	@Shadow
	protected int backgroundHeight;

	private boolean holo$on() {
		return Holo.wanted((Screen) (Object) this);
	}

	@Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("HEAD"), require = 0)
	private void holo$begin(DrawContext ctx, int mx, int my, float delta, CallbackInfo ci) {
		if (holo$on()) {
			Holo.begin(ctx, (Screen) (Object) this, x, y, backgroundWidth, backgroundHeight);
		}
	}

	@ModifyVariable(method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
	private int holo$mx(int v) {
		if (holo$on()) {
			double[] m = Holo.mouseOnPanel();
			return m == null ? v : (int) Math.round(m[0]);
		}
		return v;
	}

	@ModifyVariable(method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("HEAD"), argsOnly = true, ordinal = 1, require = 0)
	private int holo$my(int v) {
		if (holo$on()) {
			double[] m = Holo.mouseOnPanel();
			return m == null ? v : (int) Math.round(m[1]);
		}
		return v;
	}

	private Click holo$click(Click c) {
		if (!holo$on()) {
			return c;
		}
		double[] m = Holo.unproject(c.x(), c.y());
		return m == null ? c : new Click(m[0], m[1], new MouseInput(c.button(), c.modifiers()));
	}

	@ModifyVariable(method = "mouseClicked(Lnet/minecraft/client/gui/Click;Z)Z", at = @At("HEAD"), argsOnly = true, require = 0)
	private Click holo$clicked(Click c) {
		return holo$click(c);
	}

	@ModifyVariable(method = "mouseReleased(Lnet/minecraft/client/gui/Click;)Z", at = @At("HEAD"), argsOnly = true, require = 0)
	private Click holo$released(Click c) {
		return holo$click(c);
	}

	@ModifyVariable(method = "mouseDragged(Lnet/minecraft/client/gui/Click;DD)Z", at = @At("HEAD"), argsOnly = true, require = 0)
	private Click holo$dragged(Click c) {
		return holo$click(c);
	}

	@ModifyVariable(method = "mouseScrolled(DDDD)Z", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
	private double holo$scrollX(double v) {
		if (holo$on()) {
			double[] m = Holo.mouseOnPanel();
			return m == null ? v : m[0];
		}
		return v;
	}

	@ModifyVariable(method = "mouseScrolled(DDDD)Z", at = @At("HEAD"), argsOnly = true, ordinal = 1, require = 0)
	private double holo$scrollY(double v) {
		if (holo$on()) {
			double[] m = Holo.mouseOnPanel();
			return m == null ? v : m[1];
		}
		return v;
	}
}
