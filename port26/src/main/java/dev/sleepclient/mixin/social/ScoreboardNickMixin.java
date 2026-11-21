package dev.sleepclient.mixin.social;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Nick Hider in the sidebar scoreboard (and every other place the game builds a "team prefix + name + suffix" text). */
@Mixin(PlayerTeam.class)
public abstract class ScoreboardNickMixin {

	@Inject(method = "formatNameForTeam(Lnet/minecraft/world/scores/Team;Lnet/minecraft/network/chat/Component;)Lnet/minecraft/network/chat/MutableComponent;", at = @At("RETURN"), cancellable = true, require = 0)
	private static void kubo$hideNick(Team team, Component name, CallbackInfoReturnable<MutableComponent> cir) {
		MutableComponent in = cir.getReturnValue();
		if (in == null) {
			return;
		}
		Component out = dev.sleepclient.mods.NickHiderMod.apply(in);
		if (out != in) {
			cir.setReturnValue(Component.empty().append(out));
		}
	}
}
