package dev.solclient.mixin.display;

import dev.solclient.social.NameInject;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Same as TextDisplayDataMixin, for the other name this record may have (TextData). Only one of the two exists. */
@Mixin(targets = "net.minecraft.entity.decoration.DisplayEntity$TextDisplayEntity$TextData", remap = true)
public abstract class TextDisplayTextDataMixin {

	@Inject(method = "text", at = @At("RETURN"), cancellable = true, require = 0)
	private void kubo$logo(CallbackInfoReturnable<Text> cir) {
		cir.setReturnValue(NameInject.apply(cir.getReturnValue()));
	}
}
