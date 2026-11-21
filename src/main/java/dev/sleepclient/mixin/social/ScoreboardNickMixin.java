package dev.sleepclient.mixin.social;

import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Nick Hider in the sidebar scoreboard (and every other place the game builds a "team prefix + name + suffix" text). */
@Mixin(Team.class)
public abstract class ScoreboardNickMixin {

	@Inject(method = "decorateName(Lnet/minecraft/scoreboard/AbstractTeam;Lnet/minecraft/text/Text;)Lnet/minecraft/text/MutableText;", at = @At("RETURN"), cancellable = true, require = 0)
	private static void kubo$hideNick(AbstractTeam team, Text name, CallbackInfoReturnable<MutableText> cir) {
		MutableText in = cir.getReturnValue();
		if (in == null) {
			return;
		}
		Text out = dev.sleepclient.mods.NickHiderMod.apply(in);
		if (out != in) {
			cir.setReturnValue(Text.empty().append(out));
		}
	}
}
