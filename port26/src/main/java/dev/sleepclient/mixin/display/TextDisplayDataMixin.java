package dev.sleepclient.mixin.display;

import dev.sleepclient.social.NameInject;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Text display nametags (rank + name + flag that servers put over the head): the Sleep logo goes before the nick of a
 * client user. Isolated in its own mixin config on purpose: it targets the text display's data record by name, so if the
 * name is different in this Minecraft version only this feature is skipped (see latest.log), nothing else breaks.
 */
@Mixin(targets = "net.minecraft.world.entity.Display$TextDisplay$TextRenderState", remap = true)
public abstract class TextDisplayDataMixin {

	@Inject(method = "text", at = @At("RETURN"), cancellable = true, require = 0)
	private void kubo$logo(CallbackInfoReturnable<Component> cir) {
		cir.setReturnValue(NameInject.apply(cir.getReturnValue()));
	}
}
