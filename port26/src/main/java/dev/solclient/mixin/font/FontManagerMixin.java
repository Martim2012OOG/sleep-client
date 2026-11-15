package dev.solclient.mixin.font;

import dev.solclient.GameFont;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Settings > Font > "Whole Game Font": every glyph the game draws with the normal font (minecraft:default: chat,
 * inventory, tooltips, signs, names, scoreboard...) is looked up in the chosen Sleep font instead. The Sleep fonts
 * already fall back to the vanilla font for glyphs they don't have, and nothing is reloaded, so it changes instantly.
 * Its own mixin config (required=false): if anything here ever fails to apply, the game just keeps its normal font.
 */
@Mixin(FontManager.class)
public abstract class FontManagerMixin {

	@Shadow private Map<Identifier, FontSet> fontSets;

	@Inject(method = "getFontSetRaw", at = @At("HEAD"), cancellable = true, require = 0)
	private void sleep$wholeGameFont(Identifier id, CallbackInfoReturnable<FontSet> cir) {
		Identifier target = GameFont.target;
		if (target != null && Minecraft.DEFAULT_FONT.equals(id)) {
			FontSet s = this.fontSets.get(target);
			if (s != null) {
				cir.setReturnValue(s);
			}
		}
	}
}
