package com.baom5.deathanimation.mixin.client;

import com.baom5.deathanimation.client.DeathEffectController;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(MinecraftClient.class)
public abstract class MinecraftGoldInteractionMixin {
   @Unique
   private boolean deathanimations$shellGuessConsumed;

   @Inject(method = "method_1508", at = @At("HEAD"))
   private void deathanimations$readShellGuess(CallbackInfo ci) {
      this.deathanimations$shellGuessConsumed = DeathEffectController.handleShellGameInput((MinecraftClient)(Object)this);
   }

   @Inject(method = "method_1590", at = @At("HEAD"), cancellable = true)
   private void deathanimations$consumeShellAttack(boolean attacking, CallbackInfo ci) {
      if (this.deathanimations$shellGuessConsumed) {
         ci.cancel();
      }
   }

   @Inject(method = "method_1536", at = @At("HEAD"), cancellable = true)
   private void deathanimations$hitGoldStatue(CallbackInfoReturnable<Boolean> cir) {
      MinecraftClient client = (MinecraftClient)(Object)this;
      if (client.player != null && DeathEffectController.interactShellGame(client.player)) {
         cir.setReturnValue(false);
      } else if (client.player != null && DeathEffectController.claimClawPrize(client.player)) {
         cir.setReturnValue(false);
      } else if (client.player != null && DeathEffectController.interactGold(client.player)) {
         cir.setReturnValue(false);
      } else if (client.player != null && DeathEffectController.interactSand(client.player)) {
         cir.setReturnValue(false);
      } else if (client.player != null && DeathEffectController.interactAcrobatics(client.player)) {
         cir.setReturnValue(false);
      }
   }
}
