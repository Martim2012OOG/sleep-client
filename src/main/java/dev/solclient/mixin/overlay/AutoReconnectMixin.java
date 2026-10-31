package dev.solclient.mixin.overlay;

import dev.solclient.mods.AutoReconnectMod;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Auto Reconnect mod: a "Reconnecting in 5..." button on the disconnect screen (click = now, right next to it: cancel). */
@Mixin(DisconnectedScreen.class)
public abstract class AutoReconnectMixin extends Screen {

	@Unique
	private ButtonWidget plegumo$button;
	@Unique
	private long plegumo$at;
	@Unique
	private boolean plegumo$cancelled;

	protected AutoReconnectMixin(Text title) {
		super(title);
	}

	@Inject(method = "init", at = @At("TAIL"), require = 0)
	private void plegumo$init(CallbackInfo ci) {
		if (!AutoReconnectMod.on()) {
			return;
		}
		if (plegumo$at == 0) {
			plegumo$at = System.currentTimeMillis() + AutoReconnectMod.delayMs();
		}
		int w = 150, y = height - 28;
		plegumo$button = ButtonWidget.builder(Text.literal("Reconnect"), b -> plegumo$go()).dimensions(width / 2 - w - 2, y, w, 20).build();
		addDrawableChild(plegumo$button);
		addDrawableChild(ButtonWidget.builder(Text.literal("Cancel auto reconnect"), b -> {
			plegumo$cancelled = true;
			plegumo$button.setMessage(Text.literal("Reconnect"));
			b.active = false;
		}).dimensions(width / 2 + 2, y, w, 20).build());
	}

	@Unique
	private void plegumo$go() {
		plegumo$cancelled = true;
		AutoReconnectMod.reconnect(new MultiplayerScreen(new TitleScreen()));
	}

	@Override
	public void tick() {
		super.tick();
		if (plegumo$button == null || plegumo$cancelled) {
			return;
		}
		long left = plegumo$at - System.currentTimeMillis();
		if (left <= 0) {
			plegumo$go();
			return;
		}
		plegumo$button.setMessage(Text.literal("Reconnecting in " + (left / 1000 + 1) + "s"));
	}
}
